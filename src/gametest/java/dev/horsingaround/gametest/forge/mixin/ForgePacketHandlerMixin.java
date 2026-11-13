package dev.horsingaround.gametest.forge.mixin;

import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ForgePacketHandler;
import net.minecraftforge.network.packets.ConfigData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client takes in the synced configs a server sends. The server test's dedicated server runs inside the test client
 * (ServerModLoaderMixin), so they're its own configs: taking them in would swap the server's (read from their files) for
 * copies with no file, and the next join couldn't be sent them.
 */
@Mixin(value = ForgePacketHandler.class, remap = false)
abstract class ForgePacketHandlerMixin {
	@Inject(method = "handleConfigSync", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/config/ModConfig;acceptSyncedConfig([B)V"), cancellable = true)
	private void horsingaroundTest$sameConfigs(final ConfigData msg, final CustomPayloadEvent.Context ctx, final CallbackInfo ci) {
		if (ThreadingImpl.isServerRunning && ThreadingImpl.unsafeClientInstance != null && ThreadingImpl.unsafeClientInstance.getSingleplayerServer() == null) {
			ci.cancel();
		}
	}
}
