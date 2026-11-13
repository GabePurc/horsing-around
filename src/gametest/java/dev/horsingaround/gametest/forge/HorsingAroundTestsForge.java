package dev.horsingaround.gametest.forge;

import dev.horsingaround.gametest.harness.impl.ClientGameTestSetup;
import dev.horsingaround.gametest.harness.impl.TestSystemProperties;
import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;
import dev.horsingaround.gametest.harness.impl.util.GameTestSyncPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;

/** The test mod on Forge: the harness's setup and its sync payload, as Fabric's entrypoint does (ClientGameTestSetup). */
@Mod(ClientGameTestSetup.MOD_ID)
public final class HorsingAroundTestsForge {
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

	public HorsingAroundTestsForge(final FMLJavaModLoadingContext context) {
		if (TestSystemProperties.ENABLED) {
			FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(event -> ClientGameTestSetup.init());
		}
	}
}
