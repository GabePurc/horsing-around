package dev.horsingaround.smoke.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smoke test games never take the cursor (user request, 2026-10-22: it locked the mouse to the game while tests ran,
 * and moving it threw them off). The ride turns the view by setting it, never with the mouse.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
	private void horsingaroundSmoke$keepCursor(final CallbackInfo ci) {
		ci.cancel();
	}
}
