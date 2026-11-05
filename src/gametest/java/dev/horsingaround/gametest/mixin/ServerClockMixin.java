package dev.horsingaround.gametest.mixin;

import dev.horsingaround.gametest.TestClock;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Test-only, with {@code TestClock}: the client's frames move the clock, and the test framework holds the client until the
 * server reaches each tick, so a server waiting on that clock would wait for ever. It runs its tasks, chunks' included,
 * and goes on, as in a {@code /tick sprint}.
 */
@Mixin(MinecraftServer.class)
abstract class ServerClockMixin {
	@Unique
	private boolean horsingaroundtest$running;

	@Inject(method = "waitUntilNextTick", at = @At("HEAD"), cancellable = true)
	private void horsingaroundtest$noWait(final CallbackInfo ci) {
		if (TestClock.ON) {
			this.horsingaroundtest$running = true;
			((TestClock.Tasks) (Object) this).horsingaroundtest$runAllTasks();
			this.horsingaroundtest$running = false;
			Thread.yield();
			ci.cancel();
		}
	}

	@Inject(method = "haveTime", at = @At("HEAD"), cancellable = true)
	private void horsingaroundtest$haveTime(final CallbackInfoReturnable<Boolean> cir) {
		if (TestClock.ON) {
			cir.setReturnValue(this.horsingaroundtest$running);
		}
	}
}
