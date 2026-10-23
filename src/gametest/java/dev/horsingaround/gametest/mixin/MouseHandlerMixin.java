package dev.horsingaround.gametest.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Test games never take the cursor (user request, 2026-10-22: it locked the mouse to the game while tests ran, and moving
 * it threw them off). The tests turn the view by setting it, never with the mouse, so they don't need it.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
	private void horsingaroundTest$keepCursor(final CallbackInfo ci) {
		ci.cancel();
	}
}
