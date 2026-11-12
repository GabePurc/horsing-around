package dev.horsingaround.forge;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.MixinAudit;
import dev.horsingaround.net.HatPayload;
import dev.horsingaround.net.HatRelay;
import dev.horsingaround.net.PlayerHatPayload;
import dev.horsingaround.net.RulesPayload;
import dev.horsingaround.net.StylePayload;
import dev.horsingaround.ride.Mounts;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
//? if >=1.21.6 {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
//?} else {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
*///?}

/**
 * Forge's entry point (it starts the client's, client/forge/HorsingAroundForgeClient, on a client). Forge for 1.21.6 on
 * has payload types and its rewritten event bus (a bus per event); before, one channel of classes and the shared buses.
 */
@Mod(HorsingAround.MOD_ID)
public final class HorsingAroundForge {
	// The client entry point's handlers for the payloads to the client (the channel takes them with the payloads).
	public static Consumer<RulesPayload> clientRules = rules -> {};
	public static Consumer<StylePayload> clientStyle = style -> {};
	public static Consumer<PlayerHatPayload> clientHats = hat -> {};

	//? if >=1.21.6 {
	/**
	 * Every payload, on one channel. Optional both ways: players without the mod still join a server with it, and players
	 * with it join servers without it (and ride vanilla there).
	 */
	public static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder.named(Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "main"))
		.optional()
		.payloadChannel()
		.play()
		.clientbound()
		.addMain(RulesPayload.TYPE, RulesPayload.CODEC.cast(), (rules, context) -> clientRules.accept(rules))
		.addMain(StylePayload.TYPE, StylePayload.CODEC.cast(), (style, context) -> clientStyle.accept(style))
		.addMain(PlayerHatPayload.TYPE, PlayerHatPayload.CODEC.cast(), (hat, context) -> clientHats.accept(hat))
		.serverbound()
		.addMain(HatPayload.TYPE, HatPayload.CODEC.cast(), (hat, context) -> HatRelay.receive(context.getSender(), hat))
		.build();

	public HorsingAroundForge(final FMLJavaModLoadingContext context) {
		HorsingAround.init();
		PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> HorsingAround.joined((ServerPlayer) event.getEntity()));
		PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> HatRelay.left((ServerPlayer) event.getEntity()));
		TickEvent.ServerTickEvent.Post.BUS.addListener(event -> HatRelay.tick(event.server()));
		ServerStartedEvent.BUS.addListener(event -> HorsingAround.serverStarted(event.getServer()));
		ServerStoppedEvent.BUS.addListener(event -> HatRelay.clear());
		TagsUpdatedEvent.BUS.addListener(event -> Mounts.invalidate());
		if (MixinAudit.ENABLED) {
			ServerStartedEvent.BUS.addListener(event -> MixinAudit.server(event.getServer()));
		}
		if (FMLEnvironment.dist == Dist.CLIENT) {
			// (The client's classes can't be named from the common ones.)
			try {
				Class.forName("dev.horsingaround.client.forge.HorsingAroundForgeClient").getConstructor(FMLJavaModLoadingContext.class).newInstance(context);
			} catch (final ReflectiveOperationException e) {
				throw new IllegalStateException("Horsing Around's client entry point is missing", e);
			}
		}
	}
	//?} else {
	/*private static final String PROTOCOL = "1";
	/^*
	 * Every payload, on one channel. Optional both ways: players without the mod still join a server with it, and players
	 * with it join servers without it (and ride vanilla there).
	 ^/
	public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named(Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "main"))
		.networkProtocolVersion(() -> PROTOCOL)
		.clientAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
		.serverAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
		.simpleChannel();

	public HorsingAroundForge() {
		final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		HorsingAround.init();
		registerPayloads();
		listen(PlayerEvent.PlayerLoggedInEvent.class, event -> HorsingAround.joined((ServerPlayer) event.getEntity()));
		listen(PlayerEvent.PlayerLoggedOutEvent.class, event -> HatRelay.left((ServerPlayer) event.getEntity()));
		listen(TickEvent.ServerTickEvent.class, event -> {
			if (event.phase == TickEvent.Phase.END) {
				HatRelay.tick(event.getServer());
			}
		});
		listen(ServerStartedEvent.class, event -> HorsingAround.serverStarted(event.getServer()));
		listen(ServerStoppedEvent.class, event -> HatRelay.clear());
		listen(TagsUpdatedEvent.class, event -> Mounts.invalidate());
		if (MixinAudit.ENABLED) {
			listen(ServerStartedEvent.class, event -> MixinAudit.server(event.getServer()));
		}
		if (FMLEnvironment.dist == Dist.CLIENT) {
			// (The client's classes can't be named from the common ones.)
			try {
				Class.forName("dev.horsingaround.client.forge.HorsingAroundForgeClient").getConstructor(IEventBus.class).newInstance(modBus);
			} catch (final ReflectiveOperationException e) {
				throw new IllegalStateException("Horsing Around's client entry point is missing", e);
			}
		}
	}

	/^* A listener on Forge's game event bus (by class: Forge can't always read a lambda's event type). ^/
	public static <T extends Event> void listen(final Class<T> type, final Consumer<T> listener) {
		MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, listener);
	}

	private static void registerPayloads() {
		int id = 0;
		CHANNEL.messageBuilder(RulesPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(RulesPayload::write).decoder(RulesPayload::read)
			.consumerMainThread((rules, context) -> clientRules.accept(rules)).add();
		CHANNEL.messageBuilder(StylePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(StylePayload::write).decoder(StylePayload::read)
			.consumerMainThread((style, context) -> clientStyle.accept(style)).add();
		CHANNEL.messageBuilder(PlayerHatPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(PlayerHatPayload::write).decoder(PlayerHatPayload::read)
			.consumerMainThread((hat, context) -> clientHats.accept(hat)).add();
		CHANNEL.messageBuilder(HatPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
			.encoder(HatPayload::write).decoder(HatPayload::read)
			.consumerMainThread((hat, context) -> HatRelay.receive(context.get().getSender(), hat)).add();
	}
	*///?}
}
