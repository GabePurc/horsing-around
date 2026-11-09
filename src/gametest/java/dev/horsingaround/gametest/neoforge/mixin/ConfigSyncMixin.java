package dev.horsingaround.gametest.neoforge.mixin;

import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;
import net.neoforged.neoforge.network.ConfigSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client takes in the synced configs a server sends. The server test's dedicated server runs inside the test client
 * (ServerModLoaderMixin), so they're its own configs: taking them in would swap the server's (read from their files) for
 * copies with no file, and the next join couldn't be sent them.
 */
@Mixin(value = ConfigSync.class, remap = false)
abstract class ConfigSyncMixin {
	@Inject(method = "receiveSyncedConfig", at = @At("HEAD"), cancellable = true)
	private static void horsingaroundTest$sameConfigs(final byte[] contents, final String fileName, final CallbackInfo ci) {
		if (ThreadingImpl.isServerRunning && ThreadingImpl.unsafeClientInstance != null && ThreadingImpl.unsafeClientInstance.getSingleplayerServer() == null) {
			ci.cancel();
		}
	}
}
