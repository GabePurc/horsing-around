package dev.horsingaround.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.entity.Entity;

/**
 * The ridden horse at the end of every game tick while armed, for checks that need every tick. A test's own samples can
 * miss some: {@code TestInput.pressKey} runs a tick of its own after the press, and screenshots can run a few.
 * Test-only; nothing here runs in play.
 */
final class HorseTrace {
	/** One tick: the horse's tick count, where the tick started (the old position) and ended, and whether it ended on the ground. */
	record Tick(int count, double xo, double yo, double zo, double x, double y, double z, boolean onGround) {
		double rise() {
			return this.y - this.yo;
		}

		double step() {
			return Math.hypot(this.x - this.xo, this.z - this.zo);
		}

		/** How far this tick started from where {@code before} ended (zero when nothing moved the horse between them). */
		double seam(final Tick before) {
			return Math.sqrt((this.xo - before.x) * (this.xo - before.x) + (this.yo - before.y) * (this.yo - before.y) + (this.zo - before.z) * (this.zo - before.z));
		}
	}

	private static final List<Tick> TICKS = new ArrayList<>();
	private static boolean armed;
	private static boolean registered;

	private HorseTrace() {
	}

	/** Starts (clearing what was traced) or stops tracing. On the client thread. */
	static void arm(final boolean on) {
		if (!registered) {
			registered = true;
			ClientTickEvents.END_CLIENT_TICK.register(mc -> {
				final Entity horse = armed && mc.player != null ? mc.player.getVehicle() : null;
				// (Paused, the level doesn't tick: nothing new to add.)
				if (horse != null && (TICKS.isEmpty() || TICKS.getLast().count() != horse.tickCount)) {
					TICKS.add(new Tick(horse.tickCount, horse.xo, horse.yo, horse.zo, horse.getX(), horse.getY(), horse.getZ(), horse.onGround()));
				}
			});
		}
		if (on) {
			TICKS.clear();
		}
		armed = on;
	}

	/** The ticks traced so far. On the client thread. */
	static List<Tick> ticks() {
		return List.copyOf(TICKS);
	}
}
