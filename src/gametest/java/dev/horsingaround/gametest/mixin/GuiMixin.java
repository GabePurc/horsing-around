package dev.horsingaround.gametest.mixin;

import dev.horsingaround.gametest.FilmCamera;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only: a menu doesn't pause the game while a store clip records, so the world goes on behind it (the settings clip). */
@Mixin(Gui.class)
abstract class GuiMixin {
	@Inject(method = "isPausing", at = @At("HEAD"), cancellable = true)
	private void horsingaroundtest$noPauseWhileFilming(final CallbackInfoReturnable<Boolean> cir) {
		if (FilmCamera.recordingClip()) {
			cir.setReturnValue(false);
		}
	}
}
