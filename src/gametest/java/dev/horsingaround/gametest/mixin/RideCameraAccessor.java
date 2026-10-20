package dev.horsingaround.gametest.mixin;

import dev.horsingaround.client.RideCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Test-only: when the riding camera last eased its framing. It times its easing on the real clock between frames; a store
 * clip draws its frames slowly, one at a time, so it sets this to put each frame its own step after the last.
 */
@Mixin(RideCamera.class)
public interface RideCameraAccessor {
	@Accessor("lastFrameNanos")
	static void horsingaroundtest$setLastFrameNanos(final long nanos) {
		throw new AssertionError();
	}
}
