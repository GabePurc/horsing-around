package dev.horsingaround.gametest;

import dev.horsingaround.gametest.mixin.UtilAccessor;
import net.minecraft.util.Util;

/**
 * Test games spend most of a run waiting: a tick is 50 ms of the game's clock however little of that it needs. With this on
 * ({@code horsingaround.frameClock}) that clock counts drawn frames instead of real time, each frame the time it takes in a
 * game at its default limit. So a tick gets the same frames on a loaded machine or an idle one, and a run goes as fast as
 * the machine can draw them. Ticks are still ticks, in lockstep with the server, which stops waiting for the
 * next one once its tasks are done ({@code ServerClockMixin}).
 *
 * <p>Runs that measure real time (perf) or draw for people (gallery, store) leave it off.
 */
public final class TestClock {
	public static final boolean ON = Boolean.getBoolean("horsingaround.frameClock");
	private static final long NANOS_PER_TICK = 50_000_000L;
	/**
	 * 5.9 a tick, what a game at its default limit measures: not a whole number, so frames fall at different moments of each
	 * tick and in time cover all of it, as they do in play.
	 */
	private static final long FRAME_NANOS_TIMES_59 = 10L * NANOS_PER_TICK;

	private static final long STALL_MILLIS = 120_000L;

	public static long ticks;
	private static final long REAL_START = System.nanoTime();
	private static long start;
	private static volatile long frames;

	private TestClock() {
	}

	/** {@code EventLoopMixin} adds this to every event loop: the method is protected, on the server's superclass. */
	public interface Tasks {
		void horsingaroundtest$runAllTasks();
	}

	/** After the game has set its own clock, which it does while it starts up; carries on from that clock's reading. */
	public static void install() {
		if (ON) {
			start = Util.getNanos();
			UtilAccessor.horsingaroundtest$setTimeSource(TestClock::nanos);
			watchForStall();
		}
	}

	/** A game whose frames stop waits for ever with its window up: have it say where it stuck and leave, failing the run. */
	private static void watchForStall() {
		final Thread watch = new Thread(() -> {
			long seen = -1L;
			while (true) {
				try {
					Thread.sleep(STALL_MILLIS);
				} catch (final InterruptedException e) {
					return;
				}
				if (frames == seen) {
					System.out.println("[test-clock] no frame for " + STALL_MILLIS / 1000L + " s, stopping the game. Threads:");
					Thread.getAllStackTraces().forEach((thread, stack) -> {
						System.out.println(thread.getName());
						for (final StackTraceElement line : stack) {
							System.out.println("\tat " + line);
						}
					});
					Runtime.getRuntime().halt(1);
				}
				seen = frames;
			}
		}, "Test clock watch");
		watch.setDaemon(true);
		watch.start();
	}

	/** Called by the render thread alone. */
	public static void frame() {
		frames = frames + 1;
		if (SmoothProbe.ON) {
			SmoothProbe.frame();
		}
	}

	/** The game's clock in these runs: for code that times itself on {@link System#nanoTime()} between frames. */
	public static long nanos() {
		return ON ? start + frames * FRAME_NANOS_TIMES_59 / 59L : System.nanoTime();
	}

	/** The run's pace, for the log. */
	static String pace() {
		final double seconds = (System.nanoTime() - REAL_START) * 1.0E-9;
		return String.format(java.util.Locale.ROOT, "frame clock %s: %d ticks, %.0f a second", ON, ticks, ticks / seconds);
	}
}
