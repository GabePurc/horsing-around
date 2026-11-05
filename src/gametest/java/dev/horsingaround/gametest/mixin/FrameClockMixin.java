package dev.horsingaround.gametest.mixin;

import dev.horsingaround.client.RideCamera;
import dev.horsingaround.client.render.SaddleMotion;
import dev.horsingaround.gametest.TestClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Test-only: the mod's frame-to-frame easing keeps pace with the sped-up game ({@code TestClock}). */
@Mixin({RideCamera.class, SaddleMotion.class})
abstract class FrameClockMixin {
	@Redirect(method = {"updateFrame", "measure"}, at = @At(value = "INVOKE", target = "Ljava/lang/System;nanoTime()J"), require = 1)
	private static long horsingaroundtest$nanoTime() {
		return TestClock.nanos();
	}
}
