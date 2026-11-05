package dev.horsingaround.gametest.mixin;

import dev.horsingaround.gametest.TestClock;
import net.minecraft.client.Minecraft;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Test-only, with {@code TestClock}: the client sometimes blocks on the clock with no frames drawn (loading a world waits a
 * moment that way), and no frames would mean the clock never moves. Each idle wait there counts as a frame.
 */
@Mixin(BlockableEventLoop.class)
public abstract class EventLoopMixin implements TestClock.Tasks {
	@Shadow
	protected abstract void runAllTasks();

	@Override
	public void horsingaroundtest$runAllTasks() {
		this.runAllTasks();
	}

	@Inject(method = "waitForTasks", at = @At("HEAD"))
	private void horsingaroundtest$idleFrame(final CallbackInfo ci) {
		if (TestClock.ON && (Object) this instanceof Minecraft) {
			TestClock.frame();
		}
	}
}
