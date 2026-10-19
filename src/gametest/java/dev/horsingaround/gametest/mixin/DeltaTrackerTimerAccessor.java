package dev.horsingaround.gametest.mixin;

import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Test-only: the game's own frame timer, so the store clips can draw each frame at its own moment. The mod's leg code and
 * Entity Model Features read the partial tick and frame time from this timer, not from the delta a frame is drawn with.
 */
@Mixin(DeltaTracker.Timer.class)
public interface DeltaTrackerTimerAccessor {
	@Accessor("deltaTicks")
	float horsingaroundtest$deltaTicks();

	@Accessor("deltaTicks")
	void horsingaroundtest$setDeltaTicks(float ticks);

	@Accessor("deltaTickResidual")
	float horsingaroundtest$partialTick();

	@Accessor("deltaTickResidual")
	void horsingaroundtest$setPartialTick(float partialTick);

	@Accessor("pausedDeltaTickResidual")
	float horsingaroundtest$pausedPartialTick();

	@Accessor("pausedDeltaTickResidual")
	void horsingaroundtest$setPausedPartialTick(float partialTick);

	@Accessor("realtimeDeltaTicks")
	float horsingaroundtest$realtimeDeltaTicks();

	@Accessor("realtimeDeltaTicks")
	void horsingaroundtest$setRealtimeDeltaTicks(float ticks);
}
