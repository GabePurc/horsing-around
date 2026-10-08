package dev.horsingaround.gametest;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Test-only camera for store-page clips: films an entity from a set angle (beside it, ahead of it, slowly orbiting) while
 * the rider still steers by looking. It follows the subject's heading smoothed over a few ticks, so the shot glides
 * instead of twitching with every correction of the steering. Placed after vanilla and this mod have placed the camera
 * ({@code FilmCameraMixin}); off unless a test turns it on.
 */
public final class FilmCamera {
	private static @Nullable Entity subject;
	/** Camera yaw relative to the followed heading, degrees: 0 looks along it from behind, 90 looks across it from its left. */
	private static float angle;
	private static float angleO;
	private static float spin;
	private static float distance;
	private static float height;
	private static float pitch;
	/** Blocks the framing leads the subject by along its heading (room ahead of a moving horse). */
	private static float lead;
	/** How fast the followed heading catches up with the subject's, per tick (0 holds the start heading). */
	private static float follow;
	private static float heading;
	private static float headingO;

	private FilmCamera() {
	}

	/** Start filming {@code entity} (client copy) from {@code angle} round its heading. */
	public static void film(final Entity entity, final float angle, final float distance, final float height, final float pitch, final float lead,
		final float follow) {
		subject = entity;
		FilmCamera.angle = angle;
		angleO = angle;
		spin = 0.0F;
		FilmCamera.distance = distance;
		FilmCamera.height = height;
		FilmCamera.pitch = pitch;
		FilmCamera.lead = lead;
		FilmCamera.follow = follow;
		heading = entity.getYRot();
		headingO = heading;
	}

	/** Degrees per tick the camera circles the subject. */
	public static void spin(final float degreesPerTick) {
		spin = degreesPerTick;
	}

	public static void stop() {
		subject = null;
	}

	/** Once per game tick, before the tick runs (client thread). */
	public static void tick() {
		final Entity e = subject;
		if (e == null) {
			return;
		}
		headingO = heading;
		heading += Mth.wrapDegrees(e.getYRot() - heading) * follow;
		angleO = angle;
		angle += spin;
	}

	/** The camera for this frame into {@code pos} (x, y, z) and {@code rot} (yaw, pitch); false when not filming. */
	public static boolean place(final float partialTicks, final double[] pos, final float[] rot) {
		final Entity e = subject;
		if (e == null || e.isRemoved()) {
			return false;
		}
		final float h = Mth.rotLerp(partialTicks, headingO, heading);
		final float yaw = h + Mth.lerp(partialTicks, angleO, angle);
		final float hr = h * Mth.DEG_TO_RAD;
		final float yr = yaw * Mth.DEG_TO_RAD;
		final float pr = pitch * Mth.DEG_TO_RAD;
		final float cosPitch = Mth.cos(pr);
		final double tx = Mth.lerp(partialTicks, e.xo, e.getX()) - Mth.sin(hr) * lead;
		final double ty = Mth.lerp(partialTicks, e.yo, e.getY()) + height;
		final double tz = Mth.lerp(partialTicks, e.zo, e.getZ()) + Mth.cos(hr) * lead;
		pos[0] = tx + Mth.sin(yr) * cosPitch * distance;
		pos[1] = ty + Mth.sin(pr) * distance;
		pos[2] = tz - Mth.cos(yr) * cosPitch * distance;
		rot[0] = yaw;
		rot[1] = pitch;
		return true;
	}
}
