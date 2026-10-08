package dev.horsingaround.client;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.client.compat.CameraMods;
import dev.horsingaround.client.render.SaddleMotion;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * RDR2-style riding camera: third person, a little higher than vanilla, pulls back as the horse speeds up, filters
 * out vertical jolts, and eases the view back to a riding pitch when the mouse is left alone. Steering stays on the
 * mouse and A/D, so the camera never fights the rider for yaw.
 */
public final class RideCamera {
	private static boolean riding;
	private static boolean switchedPerspective;
	private static float distance;
	private static float distanceO;
	private static double eyeY;
	private static double eyeYO;
	/** First stage of the two-stage height follow. */
	private static double eyeYEase;
	private static float lastPitch;
	private static RideState ride;
	/**
	 * Set by a camera add-on that places the third-person camera itself (Over the Shoulder): then everything this mod does
	 * to the third-person view steps aside for it (placement, height and distance settings, easing the pitch back).
	 */
	static boolean thirdPersonClaimed;
	private static final SaddleMotion SADDLE = new SaddleMotion();
	/** Low-passed saddle motion for the current frame. */
	private static float frameLift;
	private static float framePitch;
	private static float frameLimbSpeed;
	private static long lastFrameNanos;
	private static int pitchIdleTicks;

	private RideCamera() {
	}

	public static void setThirdPersonClaimed(final boolean claimed) {
		thirdPersonClaimed = claimed;
	}

	/** End of every client tick, after entities have moved. Returns whether the player is riding a managed horse. */
	static boolean tick(final Minecraft minecraft) {
		final LocalPlayer player = minecraft.player;
		final RideState s = player != null && player.getVehicle() instanceof RideStateHolder holder && holder.horsingaround$managed()
			? holder.horsingaround$ride()
			: null;

		if ((s != null) != riding) {
			riding = s != null;
			if (riding) {
				if (AUTO_THIRD_PERSON && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
					CameraMods.setCameraType(minecraft, CameraType.THIRD_PERSON_BACK);
					switchedPerspective = true;
				}
				distance = distanceO = CAMERA_DISTANCE_STILL;
				eyeY = eyeYO = eyeYEase = player.getEyeY() + s.heightOffset(1.0F);
				lastPitch = player.getXRot();
				pitchIdleTicks = 0;
			} else {
				if (switchedPerspective && CameraMods.isThirdPersonBack(minecraft)) {
					CameraMods.setCameraType(minecraft, CameraType.FIRST_PERSON);
				}
				switchedPerspective = false;
			}
		}
		ride = s;
		if (s == null) {
			return false;
		}

		distanceO = distance;
		distance += (Mth.lerp(gallopFraction(s.speed), CAMERA_DISTANCE_STILL, CAMERA_DISTANCE_GALLOP) - distance) * CAMERA_DISTANCE_SMOOTHING;
		eyeYO = eyeY;
		final double eye = player.getEyeY() + s.heightOffset(1.0F);
		if (Math.abs(eye - eyeY) > CAMERA_HEIGHT_SNAP) {
			eyeY = eyeYO = eyeYEase = eye;
		}
		eyeYEase += (eye - eyeYEase) * CAMERA_HEIGHT_SMOOTHING;
		eyeY += (eyeYEase - eyeY) * CAMERA_HEIGHT_SMOOTHING;

		// The view eases back to a riding pitch, except while another camera places the third-person view (there the
		// pitch is the player's aim).
		float pitch = player.getXRot();
		if (Math.abs(pitch - lastPitch) > 0.01F || s.speed < GAIT_SPEED[TROT] || otherThirdPerson(minecraft)) {
			pitchIdleTicks = 0;
		} else if (++pitchIdleTicks > PITCH_RECENTER_DELAY) {
			pitch += (PITCH_DEFAULT - pitch) * PITCH_RECENTER_RATE;
			player.setXRot(pitch);
		}
		lastPitch = pitch;
		return true;
	}

	public static boolean isActive(final Entity cameraEntity, final Minecraft minecraft) {
		return riding && cameraEntity == minecraft.player && ownThirdPerson(minecraft);
	}

	/** This mod places the third-person (back) view: it is on, and no add-on or other camera mod has taken it over. */
	private static boolean ownThirdPerson(final Minecraft minecraft) {
		return !thirdPersonClaimed && minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK && !CameraMods.otherCameraActive();
	}

	/** A third-person view that an add-on or another camera mod places. */
	private static boolean otherThirdPerson(final Minecraft minecraft) {
		return !minecraft.options.getCameraType().isFirstPerson() && (thirdPersonClaimed || CameraMods.otherCameraActive());
	}

	/** Whether a camera add-on (Over the Shoulder) has taken over the third-person view. */
	public static boolean isThirdPersonClaimed() {
		return thirdPersonClaimed;
	}

	/**
	 * Once per frame, before the camera is placed: low-pass the saddle motion so view and hand bob have no sharp
	 * edges. Zero when not riding or with View Bobbing off.
	 */
	public static void updateFrame(final Minecraft minecraft, final float partialTicks) {
		final long now = System.nanoTime();
		final float dt = lastFrameNanos == 0L ? 0.0F : Math.min((now - lastFrameNanos) * 1.0E-9F, 0.1F);
		lastFrameNanos = now;
		float lift = 0.0F;
		float pitch = 0.0F;
		float limbSpeed = 0.0F;
		if (ride != null && minecraft.options.bobView().get() && minecraft.player.getVehicle() instanceof LivingEntity horse) {
			final SaddleMotion saddle = SADDLE.compute(horse, ride, partialTicks);
			lift = saddle.lift;
			pitch = saddle.pitch;
			limbSpeed = saddle.limbSpeed;
		}
		final float k = 1.0F - (float) Math.exp(-dt / FP_SMOOTHING_SECONDS);
		frameLift += (lift - frameLift) * k;
		framePitch += (pitch - framePitch) * k;
		frameLimbSpeed = limbSpeed;
	}

	/** Smoothed saddle lift this frame, blocks (0 with View Bobbing off). */
	public static float frameLift() {
		return frameLift;
	}

	public static float frameLimbSpeed() {
		return frameLimbSpeed;
	}

	public static boolean isRiding() {
		return riding;
	}

	public static float speedFraction() {
		return ride == null ? 0.0F : gallopFraction(ride.speed);
	}

	/** Smoothed rider eye height (follows the horse's smoothed climb), without the camera's height offset. */
	public static double eyeY(final float partialTicks) {
		return Mth.lerp(partialTicks, eyeYO, eyeY);
	}

	/** Third person takes a small share of the saddle motion (when View Bobbing is on) but none of the horse's roll. */
	public static double pivotY(final float partialTicks) {
		return eyeY(partialTicks) + CAMERA_HEIGHT + frameLift * THIRD_PERSON_BOUNCE_SCALE;
	}

	/** This mod's own camera distance: the designed distance at this speed, scaled by the player's setting. */
	public static float distance(final float partialTicks) {
		return designedDistance(partialTicks) * CAMERA_DISTANCE_SCALE;
	}

	/** Designed distance at this speed (falling behind as the horse speeds up), before the player's setting. */
	public static float designedDistance(final float partialTicks) {
		return Mth.lerp(partialTicks, distanceO, distance);
	}

	/**
	 * First person: the eyes follow the horse's smoothed height and, with View Bobbing on, a gentle share of the
	 * saddle's lift and rocking. The view never pitches or rolls with the horse; the rider holds their head level.
	 */
	public static boolean isFirstPersonRide(final Entity cameraEntity, final Minecraft minecraft) {
		return riding && cameraEntity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
	}

	/** Returns the eye height offset; writes the view nod (degrees, positive looks down) into {@code nodOut[0]}. */
	public static double firstPersonOffset(final float partialTicks, final float[] nodOut) {
		final RideState s = ride;
		if (s == null) {
			nodOut[0] = 0.0F;
			return 0.0;
		}
		nodOut[0] = -framePitch * FP_NOD_SCALE;
		return s.heightOffset(partialTicks) + frameLift * FP_BOUNCE_SCALE;
	}

	/** FOV multiplier, 1 below a trot rising to 1 + boost at full gallop. */
	public static float fovScale(final RideState s, final float effectScale) {
		final float t = (s.speed - GAIT_SPEED[TROT]) / (GAIT_SPEED[GALLOP] - GAIT_SPEED[TROT]);
		return t <= 0.0F ? 1.0F : 1.0F + FOV_GALLOP_BOOST * Math.min(t, 1.0F) * effectScale;
	}
}
