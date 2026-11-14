package dev.horsingaround.gametest.forge.mixin;

import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;
//? if >=1.20.2 {
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ForgePacketHandler;
import net.minecraftforge.network.packets.ConfigData;
//?} else {
/*import java.util.function.Supplier;
import net.minecraftforge.network.ConfigSync;
import net.minecraftforge.network.HandshakeMessages;
import net.minecraftforge.network.NetworkEvent;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client takes in the synced configs a server sends. The server test's dedicated server runs inside the test client
 * (ServerModLoaderMixin), so they're its own configs: taking them in would swap the server's (read from their files) for
 * copies with no file, and the next join couldn't be sent them. (Forge 1.20.1 takes them in elsewhere.)
 */
//? if >=1.20.2 {
@Mixin(value = ForgePacketHandler.class, remap = false)
abstract class ForgePacketHandlerMixin {
	@Inject(method = "handleConfigSync", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/config/ModConfig;acceptSyncedConfig([B)V"), cancellable = true)
	private void horsingaroundTest$sameConfigs(final ConfigData msg, final CustomPayloadEvent.Context ctx, final CallbackInfo ci) {
		if (ThreadingImpl.isServerRunning && ThreadingImpl.unsafeClientInstance != null && ThreadingImpl.unsafeClientInstance.getSingleplayerServer() == null) {
			ci.cancel();
		}
	}
}
//?} else {
/*@Mixin(value = ConfigSync.class, remap = false)
abstract class ForgePacketHandlerMixin {
	@Inject(method = "receiveSyncedConfig", at = @At("HEAD"), cancellable = true)
	private void horsingaroundTest$sameConfigs(final HandshakeMessages.S2CConfigData msg, final Supplier<NetworkEvent.Context> ctx, final CallbackInfo ci) {
		if (ThreadingImpl.isServerRunning && ThreadingImpl.unsafeClientInstance != null && ThreadingImpl.unsafeClientInstance.getSingleplayerServer() == null) {
			ci.cancel();
		}
	}
}
*///?}
