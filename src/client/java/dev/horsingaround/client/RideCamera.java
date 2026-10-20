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
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * RDR2-style riding camera: third person over the rider's shoulder (or centred, the player's choice), a little higher
 * than vanilla, pulls back as the horse speeds up and leans in to aim, filters out vertical jolts. Centred, it eases the
 * view back to a riding pitch when the mouse is left alone. Steering stays on the mouse and A/D, so the camera never
 * fights the rider for yaw. Only while riding a managed horse: on foot the third-person view is vanilla's.
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
	private static final SaddleMotion SADDLE = new SaddleMotion();
	/** Low-passed saddle motion for the current frame. */
	private static float frameLift;
	private static float framePitch;
	private static float frameLimbSpeed;
	private static long lastFrameNanos;
	private static float frameDt;
	private static int pitchIdleTicks;
	/**
	 * Third-person framing, eased per frame: the sideways offset (blocks, + right), the extra height over the shoulder,
	 * the lean-in to aim (0 to 1), and the share (0 to 1) of the offset the camera can travel before a wall.
	 */
	private static float frameSide;
	private static float frameRaise;
	private static float frameAim;
	private static float frameReach = 1.0F;
	/** The next frame takes the framing as it is (just mounted), without easing into it. */
	private static boolean framingFresh = true;
	/** Where the camera was placed this frame. */
	private static double cameraX;
	private static double cameraY;
	private static double cameraZ;

	private RideCamera() {
	}

	/** End of every client tick, after entities have moved. */
	static void tick(final Minecraft minecraft) {
		final LocalPlayer player = minecraft.player;
		final RideState s = player == null ? null : RideStateHolder.managedRide(player.getVehicle());

		if ((s != null) != riding) {
			riding = s != null;
			if (riding) {
				if (AUTO_THIRD_PERSON && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
					CameraMods.setCameraType(minecraft, CameraType.THIRD_PERSON_BACK);
					switchedPerspective = true;
				}
				distance = distanceO = CAMERA_DISTANCE_STILL;
				framingFresh = true;
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
		// (Paused, the world stands still, and so does the view: no easing the pitch while the game menu is open.)
		if (s == null || minecraft.isPaused()) {
			return;
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

		// The view eases back to a riding pitch, except over the shoulder or while another camera places the third-person
		// view (there the pitch is the player's aim), or while the rider aims something (a bow, a spyglass).
		float pitch = player.getXRot();
		if (Math.abs(pitch - lastPitch) > 0.01F || s.speed < GAIT_SPEED[TROT] || aimingView(minecraft) || player.isUsingItem()) {
			pitchIdleTicks = 0;
		} else if (++pitchIdleTicks > PITCH_RECENTER_DELAY) {
			pitch += (PITCH_DEFAULT - pitch) * PITCH_RECENTER_RATE;
			player.setXRot(pitch);
		}
		lastPitch = pitch;
	}

	public static boolean isActive(final Entity cameraEntity, final Minecraft minecraft) {
		// (Still riding as of the last tick, and not got off since: the frames after a dismount don't keep the ride's view.)
		return riding && cameraEntity == minecraft.player && minecraft.player.isPassenger() && ownThirdPerson(minecraft);
	}

	/** Over the shoulder: this mod places the view, off the rider's shoulder, with the centre crosshair as the aim. */
	public static boolean isOverShoulder(final Entity cameraEntity, final Minecraft minecraft) {
		return CAMERA_SIDE != 0.0F && isActive(cameraEntity, minecraft);
	}

	/** This mod places the third-person (back) view: it is on, and no other camera mod has taken it over. */
	private static boolean ownThirdPerson(final Minecraft minecraft) {
		return minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK && !CameraMods.otherCameraActive();
	}

	/** A third-person view whose pitch is the player's aim: over the shoulder, or placed by another camera mod. */
	private static boolean aimingView(final Minecraft minecraft) {
		return CAMERA_SIDE != 0.0F && ownThirdPerson(minecraft)
			|| !minecraft.options.getCameraType().isFirstPerson() && CameraMods.otherCameraActive();
	}

	/**
	 * Once per frame, before the camera is placed: low-pass the saddle motion so view and hand bob have no sharp
	 * edges. Zero when not riding or with View Bobbing off.
	 */
	public static void updateFrame(final Minecraft minecraft, final float partialTicks) {
		SaddleMotion.nextFrame();
		if (ride == null && frameLift == 0.0F && framePitch == 0.0F) {
			// (Not riding, and the last ride's motion has died away.)
			frameLimbSpeed = 0.0F;
			lastFrameNanos = 0L;
			return;
		}
		final long now = System.nanoTime();
		final float dt = lastFrameNanos == 0L ? 0.0F : Math.min((now - lastFrameNanos) * 1.0E-9F, 0.1F);
		lastFrameNanos = now;
		frameDt = dt;
		if (ride != null) {
			frame(minecraft.player);
		}
		float lift = 0.0F;
		float pitch = 0.0F;
		float limbSpeed = 0.0F;
		if (ride != null && minecraft.options.bobView().get() && minecraft.player.getVehicle() instanceof LivingEntity horse) {
			final SaddleMotion saddle = SADDLE.compute(horse, ride, partialTicks);
			lift = saddle.lift;
			pitch = saddle.pitch;
			limbSpeed = saddle.limbSpeed;
		}
		if (!Float.isFinite(lift) || !Float.isFinite(pitch)) {
			// (One bad sample from an animation pack would stay in the filter for good.)
			lift = 0.0F;
			pitch = 0.0F;
		}
		final float k = 1.0F - (float) Math.exp(-dt / FP_SMOOTHING_SECONDS);
		frameLift += (lift - frameLift) * k;
		framePitch += (pitch - framePitch) * k;
		frameLimbSpeed = limbSpeed;
		if (ride == null && Math.abs(frameLift) < 1.0E-4F && Math.abs(framePitch) < 1.0E-3F) {
			frameLift = 0.0F;
			framePitch = 0.0F;
		}
	}

	/** Eases the third-person framing toward where the setting and the rider's aiming put it. */
	private static void frame(final LocalPlayer player) {
		final float side = CAMERA_SIDE;
		final float raise = side != 0.0F ? SHOULDER_RAISE : 0.0F;
		final float aim = side != 0.0F && isAiming(player) ? 1.0F : 0.0F;
		if (framingFresh) {
			framingFresh = false;
			frameSide = side;
			frameRaise = raise;
			frameAim = aim;
			frameReach = 1.0F;
			return;
		}
		final float k = blend(SHOULDER_EASE_RATE);
		frameSide += (side - frameSide) * k;
		frameRaise += (raise - frameRaise) * k;
		frameAim += (aim - frameAim) * k;
	}

	/** Drawing a bow or crossbow, readying a trident or spear, looking through a spyglass, or holding a loaded crossbow. */
	private static boolean isAiming(final LivingEntity rider) {
		if (rider.isUsingItem()) {
			final ItemUseAnimation animation = rider.getUseItem().getUseAnimation();
			return animation == ItemUseAnimation.BOW
				|| animation == ItemUseAnimation.CROSSBOW
				//? if >=1.21.11
				|| animation == ItemUseAnimation.TRIDENT
				|| animation == ItemUseAnimation.SPEAR
				|| animation == ItemUseAnimation.SPYGLASS;
		}
		final ItemStack main = rider.getMainHandItem();
		return main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main);
	}

	/** Share of the way to a target that an easing at {@code ratePerSecond} covers this frame. */
	private static float blend(final float ratePerSecond) {
		return 1.0F - (float) Math.exp(-ratePerSecond * frameDt);
	}

	/**
	 * Places the third-person riding camera for this frame (read it back with {@link #cameraX()} and so on). It looks
	 * exactly where the rider looks ({@code yaw}, {@code pitch}) and orbits the head: a level sideways offset (over the
	 * shoulder), a little up, then back along the view, pulled in by walls (snapping in, easing back out).
	 */
	public static void place(final Entity rider, final float partialTicks, final float yaw, final float pitch) {
		final double px = Mth.lerp(partialTicks, rider.xo, rider.getX());
		final double py = pivotY(partialTicks);
		final double pz = Mth.lerp(partialTicks, rider.zo, rider.getZ());
		final float yawRad = yaw * Mth.DEG_TO_RAD;
		final float pitchRad = pitch * Mth.DEG_TO_RAD;
		final float back = Mth.lerp(frameAim, distance(partialTicks), SHOULDER_AIM_DISTANCE * CAMERA_DISTANCE_SCALE);
		// The view direction is (-sin yaw cos pitch, -sin pitch, cos yaw cos pitch); the rider's right is (-cos yaw, 0, -sin yaw).
		final double ox = -Mth.cos(yawRad) * frameSide + Mth.sin(yawRad) * Mth.cos(pitchRad) * back;
		final double oy = frameRaise + Mth.sin(pitchRad) * back;
		final double oz = -Mth.sin(yawRad) * frameSide - Mth.cos(yawRad) * Mth.cos(pitchRad) * back;
		final float clearance = clearance(rider.level(), rider, px, py, pz, ox, oy, oz);
		frameReach = clearance < frameReach ? clearance : frameReach + (clearance - frameReach) * blend(CAMERA_WALL_RECOVER_RATE);
		cameraX = px + ox * frameReach;
		cameraY = py + oy * frameReach;
		cameraZ = pz + oz * frameReach;
	}

	/**
	 * Share (0 to 1) of the offset the camera can travel from the pivot before a wall, from a few jittered rays as vanilla
	 * does. Leaves don't stop a ridden horse, so they don't push the camera in either.
	 */
	private static float clearance(
		final BlockGetter level, final Entity rider, final double x, final double y, final double z, final double ox, final double oy, final double oz
	) {
		final double length = Math.sqrt(ox * ox + oy * oy + oz * oz);
		if (length < 1.0E-4) {
			return 1.0F;
		}
		double share = 1.0;
		for (int i = 0; i < 8; i++) {
			final Vec3 from = new Vec3(x + ((i & 1) * 2 - 1) * 0.1, y + ((i >> 1 & 1) * 2 - 1) * 0.1, z + ((i >> 2 & 1) * 2 - 1) * 0.1);
			final HitResult hit = level.clip(new ClipContext(from, from.add(ox, oy, oz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rider));
			if (hit.getType() != HitResult.Type.MISS) {
				share = Math.min(share, Math.max((hit.getLocation().distanceTo(from) - CAMERA_WALL_MARGIN) / length, 0.0));
			}
		}
		return (float) share;
	}

	public static double cameraX() {
		return cameraX;
	}

	public static double cameraY() {
		return cameraY;
	}

	public static double cameraZ() {
		return cameraZ;
	}

	/** The camera's sideways offset from the rider's head this frame, blocks (+ right), after walls pull it in. */
	public static float sideOffset() {
		return frameSide * frameReach;
	}

	/** The camera's height over the pivot this frame, besides backing off along the view, after walls pull it in. */
	public static float raiseOffset() {
		return frameRaise * frameReach;
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

	/** The camera distance: the designed distance at this speed, scaled by the player's setting. */
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
		return riding && cameraEntity == minecraft.player && minecraft.player.isPassenger() && minecraft.options.getCameraType().isFirstPerson();
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
