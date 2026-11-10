package dev.horsingaround.gametest.neoforge;

import dev.horsingaround.gametest.harness.impl.ClientGameTestSetup;
import dev.horsingaround.gametest.harness.impl.TestSystemProperties;
import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;
import dev.horsingaround.gametest.harness.impl.util.GameTestSyncPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
//? if >=26.1
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The test mod on NeoForge: the harness's setup and its sync payload, as Fabric's entrypoint does (ClientGameTestSetup). */
@Mod(value = ClientGameTestSetup.MOD_ID, dist = Dist.CLIENT)
public final class HorsingAroundTestsNeoForge {
	public HorsingAroundTestsNeoForge(final IEventBus modBus) {
		if (!TestSystemProperties.ENABLED) {
			return;
		}
		modBus.addListener(FMLClientSetupEvent.class, event -> ClientGameTestSetup.init());
		// (The server's handler here, the client's on the client's own event, as NeoForge asks from 26.1; one handler for both
		// before.)
		modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1").optional()
			.playBidirectional(GameTestSyncPayload.TYPE, GameTestSyncPayload.CODEC, (payload, context) -> ThreadingImpl.networkSyncReceived = true));
		//? if >=26.1
		modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(GameTestSyncPayload.TYPE, (payload, context) -> ThreadingImpl.networkSyncReceived = true));
	}
}
