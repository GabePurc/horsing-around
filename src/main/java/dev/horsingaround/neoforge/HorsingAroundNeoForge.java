package dev.horsingaround.neoforge;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.MixinAudit;
import dev.horsingaround.net.HatPayload;
import dev.horsingaround.net.HatRelay;
import dev.horsingaround.net.PlayerHatPayload;
import dev.horsingaround.net.RulesPayload;
import dev.horsingaround.ride.Mounts;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
//? if <=1.21.1
/*import net.neoforged.neoforge.network.handling.IPayloadHandler;*/

/** NeoForge's entry point (the client's is client/neoforge/HorsingAroundNeoForgeClient). */
@Mod(HorsingAround.MOD_ID)
public final class HorsingAroundNeoForge {
	//? if <=1.21.1 {
	/*// The client entry point's handlers for the payloads to the client (NeoForge for 1.21.1 takes them with the types).
	public static IPayloadHandler<RulesPayload> clientRules = (rules, context) -> {};
	public static IPayloadHandler<PlayerHatPayload> clientHats = (hat, context) -> {};
	*///?}

	public HorsingAroundNeoForge(final IEventBus modBus) {
		HorsingAround.init();
		modBus.addListener(RegisterPayloadHandlersEvent.class, HorsingAroundNeoForge::registerPayloads);
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> HorsingAround.joined((ServerPlayer) event.getEntity()));
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> HatRelay.left((ServerPlayer) event.getEntity()));
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> HatRelay.tick(event.getServer()));
		NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> HatRelay.clear());
		NeoForge.EVENT_BUS.addListener(TagsUpdatedEvent.class, event -> Mounts.invalidate());
		if (MixinAudit.ENABLED) {
			NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> MixinAudit.server(event.getServer()));
		}
	}

	private static void registerPayloads(final RegisterPayloadHandlersEvent event) {
		// Optional: players without the mod still join a server with it, and players with it join servers without it
		// (and ride vanilla there). The client's handlers are the client entry point's.
		event.registrar("1").optional()
			//? if >1.21.1 {
			.playToClient(RulesPayload.TYPE, RulesPayload.CODEC)
			.playToClient(PlayerHatPayload.TYPE, PlayerHatPayload.CODEC)
			//?} else {
			/*.playToClient(RulesPayload.TYPE, RulesPayload.CODEC, (rules, context) -> clientRules.handle(rules, context))
			.playToClient(PlayerHatPayload.TYPE, PlayerHatPayload.CODEC, (hat, context) -> clientHats.handle(hat, context))
			*///?}
			.playToServer(HatPayload.TYPE, HatPayload.CODEC, (hat, context) -> HatRelay.receive((ServerPlayer) context.player(), hat));
	}
}
