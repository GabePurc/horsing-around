package dev.horsingaround.gametest.mixin;

import dev.horsingaround.gametest.TestClock;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only: starts {@code TestClock} once the game has set the clock it runs on, and tells it of each frame. */
@Mixin(Minecraft.class)
abstract class TestClockMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void horsingaroundtest$installClock(final CallbackInfo ci) {
		TestClock.install();
	}

	@Inject(method = "runTick", at = @At("HEAD"))
	private void horsingaroundtest$frame(final CallbackInfo ci) {
		TestClock.frame();
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void horsingaroundtest$tick(final CallbackInfo ci) {
		TestClock.ticks++;
	}
}
