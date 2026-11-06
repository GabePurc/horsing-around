package dev.horsingaround.gametest.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.horsingaround.gametest.TestClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Test-only, with {@code TestClock}: the game holds its frames down when it sees it's minimized or nobody has touched it for a
 * while, and frames are this clock's time: 10 a second leaves about 2 ticks a second. (Not seen on Ubuntu under Xwayland, where
 * the game never sees it's minimized and the tests' keys count as touching it; desktops that do report it would crawl.)
 */
@Mixin(FramerateLimitTracker.class)
abstract class FramerateLimitTrackerMixin {
	@Inject(method = "getThrottleReason", at = @At("HEAD"), cancellable = true)
	private void horsingaroundtest$neverThrottle(final CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> cir) {
		if (TestClock.ON) {
			cir.setReturnValue(FramerateLimitTracker.FramerateThrottleReason.NONE);
		}
	}
}
