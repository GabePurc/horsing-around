package dev.horsingaround.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Test-only camera for store-page clips and close shots: films an entity while the rider still steers by looking. A shot
 * is a list of camera setups at clip ticks: the camera eases from one to the next (or cuts), so it can track alongside,
 * swing round, crane up or sit still and pan as the horse goes by. It follows the subject's heading smoothed over a few
 * ticks, so the shot glides instead of twitching with every correction of the steering, and it never goes below the
 * ground or water under it. Placed after vanilla and this mod have placed the camera ({@code FilmCameraMixin}); off
 * unless a test turns it on.
 */
public final class FilmCamera {
	/** How far above the ground or water under it the lens stays, blocks; and above grass and flowers, which it may skim. */
	private static final double CLEARANCE = 0.6;
	private static final double PLANT_CLEARANCE = 0.15;
	/** How far round each column the floor reaches, and the two blurs that turn it into gentle slopes (blocks). */
	private static final int FLOOR_REACH = 6;
	private static final int FLOOR_BLUR = 2;
	/** How softly the camera settles onto the floor when the shot would take it lower (blocks). */
	private static final double FLOOR_SOFTNESS = 1.0;
	/** How far ahead the camera looks for something coming between it and the subject (ticks), and the most it cranes up to see past (blocks). */
	private static final int LOOK_AHEAD = 12;
	private static final double LIFT_MAX = 8.0;
	/**
	 * How quickly the crane goes up to see past something and settles back down after (a spring's stiffness, radians a
	 * tick): it eases into the move and out of it, never starting or stopping with a jolt.
	 */
	private static final double LIFT_RISE = 0.2;
	private static final double LIFT_FALL = 0.1;
	/** A held camera brakes to a stop over about this many ticks, like a camera car pulling up. */
	private static final double BRAKING = 6.0;
	/** How quickly the followed height catches up with the subject's (a spring, radians a tick): a step up or a landing doesn't jolt the shot. */
	private static final double RISE = 0.35;
	/**
	 * How quickly the followed position takes up a change in the subject's pace (a spring that also matches its speed, so
	 * there is no lag at a steady gallop): a surge, a hard stop or a stumble eases in like a camera car, not a jolt.
	 */
	private static final double GLIDE = 0.22;
	/** How much of a change in the subject's pace reaches the camera each tick: a one-tick lurch is mostly ironed out. */
	private static final double PACE = 0.5;
	/** Marks a held setup's place. */
	private static final Vec3 STAY = new Vec3(0.0, 0.0, 0.0);

	/**
	 * One camera setup. Orbiting ({@code at} null): {@code angle} degrees round the subject's followed heading (0 looks
	 * along it from behind, 90 looks across it from its left), {@code distance} out and {@code height} above its feet.
	 * Fixed: at {@code at} in the world. Either way it aims {@code aimHeight} above the subject's feet, the whole setup
	 * {@code lead} blocks ahead of it along its heading (room in front of a moving horse), through a lens of {@code fov}
	 * degrees from top to bottom (0: the player's setting).
	 */
	public record Setup(@Nullable Vec3 at, float angle, float distance, float height, float aimHeight, float lead, float fov) {
		public static Setup orbit(final float angle, final float distance, final float height, final float aimHeight, final float lead, final float fov) {
			return new Setup(null, angle, distance, height, aimHeight, lead, fov);
		}

		public static Setup fixed(final Vec3 at, final float aimHeight, final float lead, final float fov) {
			return new Setup(at, 0.0F, 0.0F, 0.0F, aimHeight, lead, fov);
		}

		/** Pulls up where the camera is when it gets to this key and turns to keep the subject in view. */
		public static Setup held(final float aimHeight, final float lead, final float fov) {
			return new Setup(STAY, 0.0F, 0.0F, 0.0F, aimHeight, lead, fov);
		}

		private boolean holds() {
			return this.at == STAY;
		}
	}

	/** The camera reaches {@code setup} at clip tick {@code tick}, easing from the key before, or jumping to it there ({@code cut}). */
	private record Key(float tick, Setup setup, boolean cut) {
	}

	private static @Nullable Entity subject;
	/** A plain set-up ({@link #film}): placed straight off the subject each frame, without the smoothing, the floor or the crane. */
	private static boolean plain;
	private static final List<Key> KEYS = new ArrayList<>();
	/** The floor's columns as worked out so far this shot: solid ground and water, then plants as well (each column's reach, then blurred). */
	private static final Floor GROUND = new Floor(Heightmap.Types.MOTION_BLOCKING, CLEARANCE);
	private static final Floor PLANTS = new Floor(Heightmap.Types.WORLD_SURFACE, PLANT_CLEARANCE);
	/** How fast the followed heading catches up with the subject's, per tick (0 holds the start heading). */
	private static float follow;
	/**
	 * Where the camera follows, the last three ticks (x, y, z, heading): the subject's position, its height and heading
	 * eased. Frames draw a smooth curve through them, so the camera doesn't change speed with a kick at every tick.
	 */
	private static final double[][] TRACK = new double[3][4];
	/** How far up the camera is craned to see past something, the last three ticks (drawn through as smoothly as the track). */
	private static final double[] LIFT = new double[3];
	/** The crane's and the followed height's speeds, blocks a tick (both move like springs). */
	private static double liftSpeed;
	private static double riseSpeed;
	/** The followed position's speed, the subject's pace as the camera takes it, and where the subject was a tick before (x, z). */
	private static double glideX;
	private static double glideZ;
	private static double paceX;
	private static double paceZ;
	private static double subjectX;
	private static double subjectZ;
	/** Whether the shot has just started: the crane starts where it needs to be rather than rising into the first frames. */
	private static boolean fresh;
	/** The clip tick being drawn (frames add their partial tick to it). */
	private static int clock;
	private static float fov;
	/** Whether the frame being drawn is one a clip records (the window's own frames in between don't move the camera on). */
	private static boolean recording;
	/** Whether a clip is recording, and whether one of its frames is being drawn (client thread). */
	private static boolean clip;
	private static boolean drawingClip;
	/** The two recorded frames before: when (clip ticks) and where the shot put the camera (before the floor and the crane). */
	private static float lastTime;
	private static float prevTime;
	private static final double[] LAST = new double[3];
	private static final double[] PREV = new double[3];
	/** Every recorded camera position this shot (time in clip ticks, x, y, z), to check the path is smooth. */
	private static final List<double[]> PATH = new ArrayList<>();
	private static final List<String> SUBJECT = new ArrayList<>();
	/** While held: since when, where it pulled up from and how fast it was going (blocks a tick). */
	private static boolean holding;
	private static float heldSince;
	private static final double[] HELD = new double[3];
	private static final double[] HELD_SPEED = new double[3];

	private FilmCamera() {
	}

	/** Start filming {@code entity} (client copy), following its heading at {@code follow} a tick; add the setups with {@link #key}. */
	public static void shoot(final Entity entity, final float follow) {
		subject = entity;
		plain = false;
		KEYS.clear();
		GROUND.clear();
		PLANTS.clear();
		FilmCamera.follow = follow;
		for (final double[] at : TRACK) {
			at[0] = entity.getX();
			at[1] = entity.getY();
			at[2] = entity.getZ();
			at[3] = entity.getYRot();
		}
		LIFT[0] = 0.0;
		LIFT[1] = 0.0;
		LIFT[2] = 0.0;
		liftSpeed = 0.0;
		riseSpeed = 0.0;
		glideX = entity.getX() - entity.xo;
		glideZ = entity.getZ() - entity.zo;
		paceX = glideX;
		paceZ = glideZ;
		subjectX = entity.getX();
		subjectZ = entity.getZ();
		fresh = true;
		clock = 0;
		fov = 0.0F;
		holding = false;
		PATH.clear();
		SUBJECT.clear();
		lastTime = Float.NaN;
		prevTime = Float.NaN;
	}

	/** The camera at {@code setup} by clip tick {@code tick}, easing there from the setup before. */
	public static void key(final float tick, final Setup setup) {
		KEYS.add(new Key(tick, setup, false));
	}

	/** A cut to {@code setup} at clip tick {@code tick}. */
	public static void cut(final float tick, final Setup setup) {
		KEYS.add(new Key(tick, setup, true));
	}

	/**
	 * One fixed set-up, the way the ride test's close shots and the stills use it: {@code pitch} degrees down onto the point
	 * {@code height} above the subject, exactly there every frame.
	 */
	public static void film(final Entity entity, final float angle, final float distance, final float height, final float pitch, final float lead,
		final float follow) {
		shoot(entity, follow);
		plain = true;
		final float pr = pitch * Mth.DEG_TO_RAD;
		key(0.0F, Setup.orbit(angle, distance * Mth.cos(pr), height + distance * Mth.sin(pr), height, lead, 0.0F));
	}

	public static void stop() {
		subject = null;
		KEYS.clear();
	}

	/** Once per game tick, after the tick ran and the clock was set to it (client thread). */
	public static void tick() {
		final Entity e = subject;
		if (e == null) {
			return;
		}
		SUBJECT.add(String.format(Locale.ROOT, "%d,%.4f,%.4f,%.4f,%.2f,%.4f,%.4f,xo=%.4f,yo=%.4f,zo=%.4f", clock, e.getX(), e.getY(), e.getZ(), e.getYRot(), TRACK[2][3], LIFT[2], e.xo, e.yo, e.zo));
		final double[] oldest = TRACK[0];
		TRACK[0] = TRACK[1];
		TRACK[1] = TRACK[2];
		final double[] last = TRACK[1];
		paceX += (e.getX() - subjectX - paceX) * PACE;
		paceZ += (e.getZ() - subjectZ - paceZ) * PACE;
		glideX += GLIDE * GLIDE * (e.getX() - last[0]) + 2.0 * GLIDE * (paceX - glideX);
		glideZ += GLIDE * GLIDE * (e.getZ() - last[2]) + 2.0 * GLIDE * (paceZ - glideZ);
		subjectX = e.getX();
		subjectZ = e.getZ();
		oldest[0] = last[0] + glideX;
		riseSpeed += RISE * RISE * (e.getY() - last[1]) - 2.0 * RISE * riseSpeed;
		oldest[1] = last[1] + riseSpeed;
		oldest[2] = last[2] + glideZ;
		oldest[3] = last[3] + Mth.wrapDegrees(e.getYRot() - last[3]) * follow;
		TRACK[2] = oldest;
		final double needed = KEYS.isEmpty() || plain ? 0.0 : needed();
		if (fresh) {
			fresh = false;
			LIFT[0] = needed;
			LIFT[1] = needed;
			LIFT[2] = needed;
			return;
		}
		final double was = LIFT[2];
		final double stiffness = needed > was ? LIFT_RISE : LIFT_FALL;
		liftSpeed += stiffness * stiffness * (needed - was) - 2.0 * stiffness * liftSpeed;
		LIFT[0] = LIFT[1];
		LIFT[1] = was;
		LIFT[2] = was + liftSpeed;
	}

	/**
	 * How far up the camera has to be, over the next {@link #LOOK_AHEAD} ticks of the shot, to see the subject past
	 * whatever is in the way (a tree, a bank): the crane starts up before it gets there. 0 when nothing is in the way, or
	 * when nothing within {@link #LIFT_MAX} would clear it.
	 */
	private static double needed() {
		final Level level = Minecraft.getInstance().level;
		if (level == null) {
			return 0.0;
		}
		final double[] cam = new double[3];
		final double[] aim = new double[3];
		double needed = 0.0;
		for (int ahead = 0; ahead <= LOOK_AHEAD; ahead += 2) {
			final float time = clock + ahead;
			if (keyBefore(time).setup.holds() && !holding) {
				continue;
			}
			// The subject carried on at its current pace.
			final double x = track(1.0F, 0) + (TRACK[2][0] - TRACK[1][0]) * ahead;
			final double y = track(1.0F, 1) + (TRACK[2][1] - TRACK[1][1]) * ahead;
			final double z = track(1.0F, 2) + (TRACK[2][2] - TRACK[1][2]) * ahead;
			final float h = (float) (track(1.0F, 3) + (TRACK[2][3] - TRACK[1][3]) * ahead);
			shot(time, x, y, z, h, cam, aim);
			floor(level, cam);
			final double base = cam[1];
			for (double up = 0.0; up <= LIFT_MAX; up += 0.5) {
				cam[1] = base + up;
				if (seen(level, aim, cam)) {
					needed = Math.max(needed, up);
					break;
				}
			}
		}
		return needed;
	}

	/** Whether nothing solid or leafy lies between {@code from} and {@code to}. */
	private static boolean seen(final Level level, final double[] from, final double[] to) {
		final double dx = to[0] - from[0];
		final double dy = to[1] - from[1];
		final double dz = to[2] - from[2];
		final int steps = Mth.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.25);
		final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 1; i < steps; i++) {
			final double f = (double) i / steps;
			at.set(from[0] + dx * f, from[1] + dy * f, from[2] + dz * f);
			final BlockState state = level.getBlockState(at);
			if (state.is(BlockTags.LEAVES) || !state.getCollisionShape(level, at).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** The crane's height {@code u} of the way through the current tick. */
	private static double lift(final float u) {
		final double v = 1.0 - u;
		return 0.5 * v * v * LIFT[0] + (0.5 + u * v) * LIFT[1] + 0.5 * u * u * LIFT[2];
	}

	/** Coordinate {@code i} of the followed track {@code u} of the way through the current tick: a curve smooth in speed too (a quadratic B-spline). */
	private static double track(final float u, final int i) {
		final double v = 1.0 - u;
		return 0.5 * v * v * TRACK[0][i] + (0.5 + u * v) * TRACK[1][i] + 0.5 * u * u * TRACK[2][i];
	}

	/** The clip tick the next frames are drawn in. */
	public static void clock(final int tick) {
		clock = tick;
	}

	/**
	 * How smooth the camera's recorded path was: the sharpest change of speed, up and down and along the ground, in
	 * blocks a tick per tick, and when (cuts left out). A gallop alongside changes speed by a few hundredths.
	 */
	public static String smoothness() {
		double up = 0.0;
		double along = 0.0;
		double upAt = 0.0;
		double alongAt = 0.0;
		final List<Double> ups = new ArrayList<>();
		final List<Double> alongs = new ArrayList<>();
		for (int i = 1; i + 1 < PATH.size(); i++) {
			final double[] a = PATH.get(i - 1);
			final double[] b = PATH.get(i);
			final double[] c = PATH.get(i + 1);
			final double dt1 = b[0] - a[0];
			final double dt2 = c[0] - b[0];
			if (dt1 <= 1.0E-6 || dt2 <= 1.0E-6 || cutBetween(a[0], c[0])) {
				continue;
			}
			final double span = (c[0] - a[0]) / 2.0;
			final double ay = Math.abs((c[2] - b[2]) / dt2 - (b[2] - a[2]) / dt1) / span;
			final double ax = ((c[1] - b[1]) / dt2 - (b[1] - a[1]) / dt1) / span;
			final double az = ((c[3] - b[3]) / dt2 - (b[3] - a[3]) / dt1) / span;
			final double ah = Math.sqrt(ax * ax + az * az);
			ups.add(ay);
			alongs.add(ah);
			if (ay > up) {
				up = ay;
				upAt = b[0];
			}
			if (ah > along) {
				along = ah;
				alongAt = b[0];
			}
		}
		ups.sort(null);
		alongs.sort(null);
		final double up99 = ups.isEmpty() ? 0.0 : ups.get((int) (ups.size() * 0.99));
		final double along99 = alongs.isEmpty() ? 0.0 : alongs.get((int) (alongs.size() * 0.99));
		return String.format(Locale.ROOT, "camera's change of speed: up/down at most %.3f (tick %.1f), 99%% under %.3f; along at most %.3f (tick %.1f), 99%% under %.3f",
			up, upAt, up99, along, alongAt, along99);
	}

	/** The recorded camera path, one line a sample: time (clip ticks), x, y, z; then the subject a tick at a time. */
	public static List<String> path() {
		final List<String> lines = new ArrayList<>(SUBJECT);
		lines.add(0, "# subject: tick,x,y,z,yRot,followed heading,lift,xo,yo,zo");
		lines.add("# camera: time,x,y,z");
		for (final double[] at : PATH) {
			lines.add(String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f", at[0], at[1], at[2], at[3]));
		}
		return lines;
	}

	private static boolean cutBetween(final double from, final double to) {
		for (final Key key : KEYS) {
			if (key.cut && key.tick > from && key.tick <= to) {
				return true;
			}
		}
		return false;
	}

	/** A clip starts or stops recording: meanwhile only its own frames draw the world (see {@code GameRendererMixin}). */
	public static void clip(final boolean on) {
		clip = on;
	}

	/** Whether a clip is recording (client thread). */
	public static boolean recordingClip() {
		return clip;
	}

	/** Around each of a clip's frames. */
	public static void drawingClip(final boolean on) {
		drawingClip = on;
	}

	/** Whether the window's own frame should leave the world undrawn: a clip is recording and this isn't one of its frames. */
	public static boolean windowFramesHeld() {
		return clip && !drawingClip;
	}

	/** Whether the frames drawn next are a clip's (see {@link #recording}). */
	public static void recording(final boolean on) {
		recording = on;
	}

	/** The lens for this frame, degrees, or 0 for the player's setting (read after {@link #place}). */
	public static float fov() {
		return subject == null ? 0.0F : fov;
	}

	/** The camera for this frame into {@code pos} (x, y, z) and {@code rot} (yaw, pitch); false when not filming. */
	public static boolean place(final float partialTicks, final double[] pos, final float[] rot) {
		final Entity e = subject;
		if (e == null || e.isRemoved() || KEYS.isEmpty()) {
			return false;
		}
		final float time = clock + partialTicks;
		final float h = plain ? Mth.rotLerp(partialTicks, (float) TRACK[1][3], (float) TRACK[2][3]) : (float) track(partialTicks, 3);
		final double ex = plain ? Mth.lerp(partialTicks, e.xo, e.getX()) : track(partialTicks, 0);
		final double ey = plain ? Mth.lerp(partialTicks, e.yo, e.getY()) : track(partialTicks, 1);
		final double ez = plain ? Mth.lerp(partialTicks, e.zo, e.getZ()) : track(partialTicks, 2);
		final Setup a = keyBefore(time).setup;
		if (recording && a.holds() && !holding) {
			holding = true;
			// From where the camera last was, at the speed it was going: it carries on from there without a hitch.
			heldSince = Float.isNaN(lastTime) ? time : lastTime;
			final float dt = lastTime - prevTime;
			for (int i = 0; i < 3; i++) {
				HELD[i] = Float.isNaN(lastTime) ? 0.0 : LAST[i];
				HELD_SPEED[i] = dt > 0.0F ? (LAST[i] - PREV[i]) / dt : 0.0;
			}
		} else if (recording && !a.holds()) {
			holding = false;
		}
		final double[] cam = new double[3];
		final double[] aim = new double[3];
		final float lens = shot(time, ex, ey, ez, h, cam, aim);
		final double[] raw = cam.clone();
		final Level level = Minecraft.getInstance().level;
		if (level != null && !plain) {
			floor(level, cam);
			cam[1] += lift(partialTicks);
		}
		if (recording) {
			PATH.add(new double[] {time, cam[0], cam[1], cam[2]});
			System.arraycopy(LAST, 0, PREV, 0, 3);
			System.arraycopy(raw, 0, LAST, 0, 3);
			prevTime = lastTime;
			lastTime = time;
		}
		fov = lens;
		final double dx = aim[0] - cam[0];
		final double dy = aim[1] - cam[1];
		final double dz = aim[2] - cam[2];
		pos[0] = cam[0];
		pos[1] = cam[1];
		pos[2] = cam[2];
		rot[0] = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		rot[1] = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
		return true;
	}

	/** The key the camera has reached by {@code time} (the first if none yet). */
	private static Key keyBefore(final float time) {
		int next = 0;
		while (next < KEYS.size() && KEYS.get(next).tick <= time) {
			next++;
		}
		return KEYS.get(Math.max(next - 1, 0));
	}

	/** Where the shot puts the camera and its aim at {@code time} for the subject at (x, y, z) heading {@code h}, before the floor; its lens. */
	private static float shot(final float time, final double x, final double y, final double z, final float h, final double[] cam, final double[] aim) {
		int next = 0;
		while (next < KEYS.size() && KEYS.get(next).tick <= time) {
			next++;
		}
		final Key from = KEYS.get(Math.max(next - 1, 0));
		final Key to = next < KEYS.size() && next > 0 && !KEYS.get(next).cut ? KEYS.get(next) : from;
		final float w = to == from ? 0.0F : smooth((time - from.tick) / (to.tick - from.tick));
		final Setup a = from.setup;
		final Setup b = to.setup;
		if (a.at == null && b.at == null) {
			// Two orbits: ease round the circle, not across it.
			final Setup s = Setup.orbit(a.angle + Mth.wrapDegrees(b.angle - a.angle) * w, Mth.lerp(w, a.distance, b.distance),
				Mth.lerp(w, a.height, b.height), Mth.lerp(w, a.aimHeight, b.aimHeight), Mth.lerp(w, a.lead, b.lead), 0.0F);
			setup(s, x, y, z, h, time, cam, aim);
		} else {
			final double[] camB = new double[3];
			final double[] aimB = new double[3];
			setup(a, x, y, z, h, time, cam, aim);
			setup(b, x, y, z, h, time, camB, aimB);
			if (b.holds() && !a.holds()) {
				// Pulling up happens at the held key; until then the camera keeps going.
				System.arraycopy(cam, 0, camB, 0, 3);
			}
			for (int i = 0; i < 3; i++) {
				cam[i] = Mth.lerp(w, cam[i], camB[i]);
				aim[i] = Mth.lerp(w, aim[i], aimB[i]);
			}
		}
		return Mth.lerp(w, a.fov, b.fov);
	}

	/** Raises {@code cam} onto the floor where the shot would take it lower: never below the ground or into the grass. */
	private static void floor(final Level level, final double[] cam) {
		final double floor = Math.max(GROUND.at(level, cam[0], cam[2]), PLANTS.at(level, cam[0], cam[2]));
		// A smooth maximum: always at least the floor, and no corner where the floor takes over.
		final double d = cam[1] - floor;
		cam[1] = (cam[1] + floor + Math.sqrt(d * d + FLOOR_SOFTNESS * FLOOR_SOFTNESS)) / 2.0;
	}

	/** Where {@code s} puts the camera and its aim for the subject at (x, y, z) heading {@code h}. */
	private static void setup(final Setup s, final double x, final double y, final double z, final float h, final float time, final double[] cam,
		final double[] aim) {
		final float hr = h * Mth.DEG_TO_RAD;
		final double cx = x - Mth.sin(hr) * s.lead;
		final double cz = z + Mth.cos(hr) * s.lead;
		aim[0] = cx;
		aim[1] = y + s.aimHeight;
		aim[2] = cz;
		if (s.holds()) {
			final double braked = holding ? BRAKING * (1.0 - Math.exp(-(time - heldSince) / BRAKING)) : 0.0;
			for (int i = 0; i < 3; i++) {
				cam[i] = HELD[i] + HELD_SPEED[i] * braked;
			}
			return;
		}
		if (s.at != null) {
			cam[0] = s.at.x;
			cam[1] = s.at.y;
			cam[2] = s.at.z;
			return;
		}
		final float yr = (h + s.angle) * Mth.DEG_TO_RAD;
		cam[0] = cx + Mth.sin(yr) * s.distance;
		cam[1] = y + s.height;
		cam[2] = cz - Mth.cos(yr) * s.distance;
	}

	/**
	 * The floor under the lens from one heightmap, as gentle slopes: each column's top raised to the highest within
	 * {@link #FLOOR_REACH}, blurred twice over {@link #FLOOR_BLUR} either side, and drawn through as a smooth surface (a
	 * cubic B-spline over the column centres). A step up becomes a ramp the camera starts up a few blocks before it,
	 * smooth in speed and in how the speed changes, and the floor is never below the top of a column the lens is over
	 * (the reach covers the blurs and the spline).
	 */
	private static final class Floor {
		private final Heightmap.Types type;
		private final double clearance;
		private final Map<Long, Double> reach = new HashMap<>();
		private final Map<Long, Double> blurred = new HashMap<>();
		private final Map<Long, Double> smoothed = new HashMap<>();

		private Floor(final Heightmap.Types type, final double clearance) {
			this.type = type;
			this.clearance = clearance;
		}

		private void clear() {
			this.reach.clear();
			this.blurred.clear();
			this.smoothed.clear();
		}

		/** The lowest the lens may be over (x, z). */
		private double at(final Level level, final double x, final double z) {
			final double fx = x - 0.5;
			final double fz = z - 0.5;
			final int x0 = Mth.floor(fx);
			final int z0 = Mth.floor(fz);
			final double ax = fx - x0;
			final double az = fz - z0;
			double sum = 0.0;
			for (int i = 0; i < 4; i++) {
				final double wz = spline(az, i);
				for (int j = 0; j < 4; j++) {
					sum += wz * spline(ax, j) * this.smoothed(level, x0 - 1 + j, z0 - 1 + i);
				}
			}
			return sum + this.clearance;
		}

		/** Cubic B-spline weight of node {@code i} (0 to 3) at {@code t} between nodes 1 and 2. */
		private static double spline(final double t, final int i) {
			final double t2 = t * t;
			final double t3 = t2 * t;
			return switch (i) {
				case 0 -> (1.0 - t) * (1.0 - t) * (1.0 - t) / 6.0;
				case 1 -> (3.0 * t3 - 6.0 * t2 + 4.0) / 6.0;
				case 2 -> (-3.0 * t3 + 3.0 * t2 + 3.0 * t + 1.0) / 6.0;
				default -> t3 / 6.0;
			};
		}

		private double smoothed(final Level level, final int x, final int z) {
			return this.smoothed.computeIfAbsent(ChunkPos.pack(x, z), k -> this.average(level, x, z, true));
		}

		private double blurred(final Level level, final int x, final int z) {
			return this.blurred.computeIfAbsent(ChunkPos.pack(x, z), k -> this.average(level, x, z, false));
		}

		/** The mean round (x, z) of the blurred floor ({@code twice}) or of the reach. */
		private double average(final Level level, final int x, final int z, final boolean twice) {
			double sum = 0.0;
			for (int dx = -FLOOR_BLUR; dx <= FLOOR_BLUR; dx++) {
				for (int dz = -FLOOR_BLUR; dz <= FLOOR_BLUR; dz++) {
					sum += twice ? this.blurred(level, x + dx, z + dz) : this.reach(level, x + dx, z + dz);
				}
			}
			return sum / ((2 * FLOOR_BLUR + 1) * (2 * FLOOR_BLUR + 1));
		}

		private double reach(final Level level, final int x, final int z) {
			return this.reach.computeIfAbsent(ChunkPos.pack(x, z), k -> {
				int top = level.getMinY();
				for (int dx = -FLOOR_REACH; dx <= FLOOR_REACH; dx++) {
					for (int dz = -FLOOR_REACH; dz <= FLOOR_REACH; dz++) {
						top = Math.max(top, level.getHeight(this.type, x + dx, z + dz));
					}
				}
				return (double) top;
			});
		}
	}

	private static float smooth(final float t) {
		final float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}
}
