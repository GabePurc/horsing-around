package dev.horsingaround.gametest.mixin;

import dev.horsingaround.gametest.ClipCursor;
import dev.horsingaround.gametest.FilmCamera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only: while a store clip records, a menu draws the mouse pointer over itself ({@link ClipCursor}). */
@Mixin(Screen.class)
abstract class ScreenMixin {
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
	private void horsingaroundtest$cursor(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a, final CallbackInfo ci) {
		if (FilmCamera.recordingClip()) {
			ClipCursor.draw(graphics, mouseX, mouseY);
		}
	}
}
