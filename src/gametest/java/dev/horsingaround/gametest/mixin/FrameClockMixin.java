package dev.horsingaround.gametest.mixin;

import dev.horsingaround.client.RideCamera;
import dev.horsingaround.client.render.SaddleMotion;
import io.github.gabepurc.spawnproof.test.GameClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Test-only: the mod's frame-to-frame easing keeps pace with the sped-up game (Spawnproof's {@code GameClock}). */
@Mixin({RideCamera.class, SaddleMotion.class})
abstract class FrameClockMixin {
	@Redirect(method = {"updateFrame", "measure"}, at = @At(value = "INVOKE", target = "Ljava/lang/System;nanoTime()J"), require = 1)
	private static long horsingaroundtest$nanoTime() {
		return GameClock.nanos();
	}
}
