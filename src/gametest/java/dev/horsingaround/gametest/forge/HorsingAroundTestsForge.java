package dev.horsingaround.gametest.forge;

import dev.horsingaround.gametest.harness.impl.ClientGameTestSetup;
import dev.horsingaround.gametest.harness.impl.TestSystemProperties;
import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;
import dev.horsingaround.gametest.harness.impl.util.GameTestSyncPayload;
//? if >=1.20.5
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//? if >=1.20.5 {
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
//?} elif >=1.20.2 {
/*import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
*///?} else {
/*import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
*///?}

/** The test mod on Forge: the harness's setup and its sync payload, as Fabric's entrypoint does (ClientGameTestSetup). */
@Mod(ClientGameTestSetup.MOD_ID)
public final class HorsingAroundTestsForge {
	//? if >=1.20.5 {
	public static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder.named(Identifier.fromNamespaceAndPath(ClientGameTestSetup.MOD_ID, "main"))
		.optional()
		.payloadChannel()
		.play()
		.bidirectional()
		.add(GameTestSyncPayload.TYPE, GameTestSyncPayload.CODEC.cast(), (payload, context) -> {
			ThreadingImpl.networkSyncReceived = true;
			context.setPacketHandled(true);
		})
		.build();
	//?} elif >=1.20.2 {
	/*// (Before payload types: a message of a channel of classes, either way.)
	public static final SimpleChannel CHANNEL = ChannelBuilder.named(Identifier.fromNamespaceAndPath(ClientGameTestSetup.MOD_ID, "main"))
		.optional()
		.simpleChannel()
		.messageBuilder(GameTestSyncPayload.class)
		.encoder((payload, buf) -> {})
		.decoder(buf -> GameTestSyncPayload.INSTANCE)
		.consumerNetworkThread((payload, context) -> {
			ThreadingImpl.networkSyncReceived = true;
			context.setPacketHandled(true);
		})
		.add();
	*///?} else {
	/*// (Forge 1.20.1's older channel of classes.)
	public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named(Identifier.fromNamespaceAndPath(ClientGameTestSetup.MOD_ID, "main"))
		.networkProtocolVersion(() -> "1")
		.clientAcceptedVersions(NetworkRegistry.acceptMissingOr("1"))
		.serverAcceptedVersions(NetworkRegistry.acceptMissingOr("1"))
		.simpleChannel();

	static {
		CHANNEL.messageBuilder(GameTestSyncPayload.class, 0)
			.encoder((payload, buf) -> {})
			.decoder(buf -> GameTestSyncPayload.INSTANCE)
			.consumerNetworkThread((payload, context) -> {
				ThreadingImpl.networkSyncReceived = true;
				context.get().setPacketHandled(true);
			})
			.add();
	}
	*///?}

	//? if >=1.21.6 {
	public HorsingAroundTestsForge(final FMLJavaModLoadingContext context) {
		if (TestSystemProperties.ENABLED) {
			FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(event -> ClientGameTestSetup.init());
		}
	}
	//?} else {
	/*@SuppressWarnings("removal")
	public HorsingAroundTestsForge() {
		if (TestSystemProperties.ENABLED) {
			FMLJavaModLoadingContext.get().getModEventBus().addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, FMLClientSetupEvent.class, event -> ClientGameTestSetup.init());
		}
	}
	*///?}
}
