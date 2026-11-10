package dev.horsingaround.gametest;

import dev.horsingaround.client.RideCamera;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * How smoothly the ride is drawn, for finding what jolts ({@code -Psmooth=<folder>}): every drawn frame of every ridden
 * horse, each channel (the body's height, its tilt, the camera's height and so on) as it was drawn, and how sharply its
 * rate of change turned from one frame to the next (its jolt, in the channel's units a tick, a tick). Per section of a
 * test, a line per channel: the hardest jolt, the usual one, and when. {@code -PsmoothDump=<channels>} adds the frames
 * round each of those channels' hardest. Test-only; nothing here runs in play.
 */
public final class SmoothProbe {
	private static final String OUT = System.getProperty("horsingaround.smooth", "");
	public static final boolean ON = !OUT.isEmpty();
	private static final String DUMP = "," + System.getProperty("horsingaround.smoothDump", "") + ",";
	private static final String[] NAMES = {
		"pivotY", "bodyY", "seen", "tilt", "lean", "yaw", "path", "camBack", "fpEye", "fpNod", "seatUp", "seatFwd", "sway", "neck", "sitBack",
		"foreLeg", "hindLeg", "airLegs", "airRise", "tail", "limb", "stride", "legFL", "legFR", "legHL", "legHR", "fit", "rPitch", "rRoll", "rLegs", "rPelvis", "rHands", "rSeat", "surge", "swerve"
	};
	private static final int N = NAMES.length;
	/** The last two channels are tick by tick: how much the speed's change, and the turn rate's, changed. */
	private static final int SURGE = N - 2;
	private static final int SWERVE = N - 1;
	private static final int SETTLE_TICKS = 15;
	private static final float[] NOD = new float[1];

	private static String label = "";
	private static int horseId = -1;
	private static double startTime;
	private static double lastTime = Double.NaN;
	private static double lastX;
	private static double lastZ;
	private static float lastYaw;
	private static double yaw;
	private static double path;
	private static int rates;
	private static final double[] last = new double[N];
	private static final double[] rate = new double[N];
	private static final double[] rate2 = new double[N];
	private static final double[] most = new double[N];
	private static final double[] total = new double[N];
	private static final int[] mostAt = new int[N];
	private static final String[] mostWhere = new String[N];
	private static int counted;
	private static final List<double[]> frames = new ArrayList<>();
	private static int lastTick = -1;
	private static double tickSpeed = Double.NaN;
	private static double tickAccel = Double.NaN;
	private static double tickTurn = Double.NaN;
	private static float tickYaw;
	private static double tickTurnAccel = Double.NaN;

	private SmoothProbe() {
	}

	/** A new section of a test begins: writes up the one before. */
	public static void label(final String name) {
		if (ON) {
			flush();
			label = name;
		}
	}

	/** Once a frame, before the next is drawn: the frame just drawn. */
	static void frame() {
		final Minecraft mc = Minecraft.getInstance();
		final AbstractHorse horse = mc.player != null && mc.player.getVehicle() instanceof AbstractHorse ridden ? ridden : null;
		final RideState s = RideStateHolder.managedRide(horse);
		if (s == null || mc.isPaused()) {
			horseId = -1;
			return;
		}
		final float p = RideFeelTest.framePartialTicks(mc);
		final double time = horse.tickCount + p;
		final double x = Mth.lerp(p, horse.xOld, horse.getX());
		final double z = Mth.lerp(p, horse.zOld, horse.getZ());
		final double moved = Math.hypot(x - lastX, z - lastZ);
		final double dt = time - lastTime;
		if (horse.getId() != horseId || !(dt > 0.0) || dt > 1.5 || moved > 1.5) {
			// Another horse, or this one was moved (a lane set up): start over, once it has settled.
			horseId = horse.getId();
			startTime = time;
			rates = 0;
			lastTick = -1;
			tickSpeed = tickAccel = tickTurn = tickTurnAccel = Double.NaN;
			tickYaw = horse.getYRot();
			lastYaw = Mth.rotLerp(p, horse.yBodyRotO, horse.yBodyRot);
			lastTime = time;
			lastX = x;
			lastZ = z;
			return;
		}
		if (dt < 1.0E-3) {
			return;
		}
		final float bodyYaw = Mth.rotLerp(p, horse.yBodyRotO, horse.yBodyRot);
		yaw += Mth.wrapDegrees(bodyYaw - lastYaw);
		lastYaw = bodyYaw;
		path += moved;
		lastX = x;
		lastZ = z;
		lastTime = time;

		final double[] now = new double[N + 5];
		final double pivot = RideCamera.pivotY(p);
		final double body = Mth.lerp(p, horse.yOld, horse.getY()) + s.heightOffset(p);
		now[0] = pivot;
		now[1] = body;
		now[2] = body - pivot;
		now[3] = s.pitch(p) + s.jumpPitch(p);
		now[4] = s.lean(p);
		now[5] = yaw;
		now[6] = path;
		now[7] = RideCamera.distance(p);
		final double firstPerson = RideCamera.firstPersonOffset(p, NOD);
		now[8] = Mth.lerp(p, mc.player.yOld, mc.player.getY()) + firstPerson;
		now[9] = NOD[0];
		now[10] = s.seatUp;
		now[11] = s.seatForward;
		now[12] = s.inertia(p);
		now[13] = s.neckReach * Mth.RAD_TO_DEG;
		now[14] = s.sitBack * Mth.RAD_TO_DEG;
		now[15] = s.foreLeg(p);
		now[16] = s.hindLeg(p);
		now[17] = s.airLegs(p);
		now[18] = s.airRise(p);
		now[19] = s.tailLift(p) * Mth.RAD_TO_DEG;
		now[20] = horse.walkAnimation.speed(p);
		now[21] = horse.walkAnimation.position(p);
		now[22] = s.legRise[0];
		now[23] = s.legRise[1];
		now[24] = s.legRise[2];
		now[25] = s.legRise[3];
		now[26] = s.drawnFit;
		// The rider as drawn: torso, legs, pelvis and hands (degrees), and the seat's height on the horse's body (blocks).
		now[27] = s.riderPitch * Mth.RAD_TO_DEG;
		now[28] = s.riderRoll * Mth.RAD_TO_DEG;
		now[29] = s.riderLegs * Mth.RAD_TO_DEG;
		now[30] = s.riderPelvis * Mth.RAD_TO_DEG;
		now[31] = s.riderHands * Mth.RAD_TO_DEG;
		now[32] = s.seatUp - s.heightOffset(p);
		now[N] = time;
		// (For the dumps: what the ride asks for and where the horse is.)
		now[N + 1] = s.speed;
		now[N + 2] = horse.onGround() ? 1 : 0;
		now[N + 3] = horse.isInWater() ? 1 : 0;
		now[N + 4] = s.gait;

		final boolean settled = time - startTime > SETTLE_TICKS;
		String where = null;
		for (int i = 0; i < SURGE; i++) {
			final double r = (now[i] - last[i]) / dt;
			if (rates >= 2 && settled) {
				final double jolt = Math.max(Math.abs(r - rate[i]), Math.abs(r - rate2[i]));
				total[i] += jolt;
				if (jolt > most[i]) {
					most[i] = jolt;
					mostAt[i] = frames.size();
					mostWhere[i] = where == null ? where = where(horse, s, time) : where;
				}
			}
			rate2[i] = rate[i];
			rate[i] = r;
			last[i] = now[i];
		}
		if (horse.tickCount != lastTick) {
			lastTick = horse.tickCount;
			final double speed = Math.hypot(horse.getX() - horse.xo, horse.getZ() - horse.zo);
			final double accel = speed - tickSpeed;
			// (Not from yRotO: riding sets that to the new heading.)
			final double turn = Mth.wrapDegrees(horse.getYRot() - tickYaw);
			tickYaw = horse.getYRot();
			final double turnAccel = turn - tickTurn;
			if (settled && !Double.isNaN(tickAccel)) {
				tick(SURGE, Math.abs(accel - tickAccel), where == null ? where = where(horse, s, time) : where);
				tick(SWERVE, Math.abs(turnAccel - tickTurnAccel), where == null ? where(horse, s, time) : where);
			}
			tickAccel = accel;
			tickSpeed = speed;
			tickTurnAccel = turnAccel;
			tickTurn = turn;
		}
		now[SURGE] = tickSpeed;
		now[SWERVE] = tickTurn;
		if (rates >= 2 && settled) {
			counted++;
		}
		rates++;
		if (frames.size() < 200_000) {
			frames.add(now);
		}
	}

	private static void tick(final int channel, final double jolt, final String where) {
		total[channel] += jolt * 5.9;
		if (jolt > most[channel]) {
			most[channel] = jolt;
			mostAt[channel] = frames.size();
			mostWhere[channel] = where;
		}
	}

	private static String where(final AbstractHorse horse, final RideState s, final double time) {
		return String.format(Locale.ROOT, "t%.2f gait%d speed%.2f %s x%.1f y%.2f z%.1f", time, s.gait, s.speed, horse.onGround() ? "ground" : "air",
			horse.getX(), horse.getY(), horse.getZ());
	}

	/** Writes up what was ridden since the last, and starts afresh. */
	public static void flush() {
		if (!ON) {
			return;
		}
		if (counted > 20) {
			final StringBuilder out = new StringBuilder();
			out.append(String.format(Locale.ROOT, "== %s (%d frames)%n", label, counted));
			for (int i = 0; i < N; i++) {
				out.append(String.format(Locale.ROOT, "%s\t%s\t%.4f\t%.5f\t%s%n", label, NAMES[i], most[i], total[i] / counted, mostWhere[i]));
			}
			for (int i = 0; i < N; i++) {
				if (!DUMP.contains("," + NAMES[i] + ",") || mostWhere[i] == null) {
					continue;
				}
				out.append(String.format(Locale.ROOT, "-- %s round its hardest (%s): time, value, change since the frame before a tick; ride speed, on the ground, in water, gait%n", NAMES[i], mostWhere[i]));
				for (int f = Math.max(mostAt[i] - 24, 1); f < Math.min(mostAt[i] + 24, frames.size()); f++) {
					final double[] a = frames.get(f - 1);
					final double[] b = frames.get(f);
					out.append(String.format(Locale.ROOT, "   %s%.2f\t%.4f\t%+.4f\t%.3f %d %d %d%n", f == mostAt[i] ? ">" : " ", b[N], b[i], (b[i] - a[i]) / Math.max(b[N] - a[N], 1.0E-3),
						b[N + 1], (int) b[N + 2], (int) b[N + 3], (int) b[N + 4]));
				}
			}
			try {
				final Path folder = Path.of(OUT);
				Files.createDirectories(folder);
				Files.writeString(folder.resolve("smooth-" + ProcessHandle.current().pid() + ".txt"), out, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (final IOException e) {
				throw new RuntimeException("Could not write the smoothness notes to " + OUT, e);
			}
		}
		java.util.Arrays.fill(most, 0.0);
		java.util.Arrays.fill(total, 0.0);
		java.util.Arrays.fill(mostWhere, null);
		counted = 0;
		frames.clear();
		// (The next frame carries on from the last: a section's first jolts are measured against the section before.)
	}
}
