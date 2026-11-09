package dev.horsingaround.gametest.neoforge.mixin;

import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;
import net.neoforged.neoforge.client.ClientNeoForgeMod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's client unloads the synced configs as it leaves a server. The server test's dedicated server runs inside the
 * test client (ServerModLoaderMixin) and reads the same configs: leaving it between two joins would take them away while it
 * runs (it then crashes ticking an entity).
 */
@Mixin(value = ClientNeoForgeMod.class, remap = false)
abstract class ClientNeoForgeModMixin {
	//? if >=26.1 {
	@Inject(method = "lambda$new$0(Lnet/neoforged/neoforge/client/event/ClientPlayerNetworkEvent$LoggingOut;)V", at = @At("HEAD"), cancellable = true)
	//?} else
	/*@Inject(method = "lambda$new$1(Lnet/neoforged/neoforge/client/event/ClientPlayerNetworkEvent$LoggingOut;)V", at = @At("HEAD"), cancellable = true)*/
	private static void horsingaroundTest$keepTheServersConfigs(final ClientPlayerNetworkEvent.LoggingOut event, final CallbackInfo ci) {
		if (ThreadingImpl.isServerRunning && ThreadingImpl.unsafeClientInstance != null && ThreadingImpl.unsafeClientInstance.getSingleplayerServer() == null) {
			ci.cancel();
		}
	}
}
