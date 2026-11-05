package dev.horsingaround.gametest.mixin;

import net.minecraft.util.TimeSource;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Test-only: the game's clock, which everything but the frame limiter reads ({@code TestClock}). */
@Mixin(Util.class)
public interface UtilAccessor {
	@Accessor("timeSource")
	static void horsingaroundtest$setTimeSource(final TimeSource.NanoTimeSource source) {
		throw new AssertionError();
	}
}
