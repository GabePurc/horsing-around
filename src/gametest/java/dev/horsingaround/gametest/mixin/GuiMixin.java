package dev.horsingaround.gametest.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
//? if >=26.3 {
import dev.horsingaround.gametest.FilmCamera;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

/**
 * Test-only: a menu doesn't pause the game while a store clip records, so the world goes on behind it (the settings clip).
 * 26.3 only, as the store clips are.
 */
@Mixin(Gui.class)
abstract class GuiMixin {
	//? if >=26.3 {
	@Inject(method = "isPausing", at = @At("HEAD"), cancellable = true)
	private void horsingaroundtest$noPauseWhileFilming(final CallbackInfoReturnable<Boolean> cir) {
		if (FilmCamera.recordingClip()) {
			cir.setReturnValue(false);
		}
	}
	//?}
}
