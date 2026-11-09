package dev.horsingaround.gametest.mixin;

import dev.horsingaround.Platform;
import dev.horsingaround.gametest.ServerRideTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player without the mod, for the server test: while it says so, the server finds none of the mod's channels on the
 * player, as with a vanilla client, so it sends nothing of the mod's. The same on every loader.
 */
@Mixin(value = Platform.class, remap = false)
abstract class PlatformMixin {
	@Inject(method = "canSend", at = @At("HEAD"), cancellable = true)
	private static void horsingaroundTest$withoutMod(final CallbackInfoReturnable<Boolean> cir) {
		if (ServerRideTest.withoutMod) {
			cir.setReturnValue(false);
		}
	}
}
