package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.ride.RideState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Hooves on the ground, on top of whatever posed the legs (vanilla model or an animation pack, so the stride stays).
 * Every leg stands upright against the body's tilt, turned about its top (see {@link Legs}), and each hoof goes exactly
 * where its ground is, with no easing: the legs take up the ground and the body carries the smoothness (see
 * {@code RideController#steps}). A hoof over higher ground draws its leg up into the body to stand on it; a hoof on the
 * move lifts to clear ground rising ahead of it, the way a swinging hoof arcs over a step's edge (a planted hoof stays
 * put); a hoof that would stand in a step's face swings back off it onto the tread. A leg that would have to draw up
 * further than it can raises the body at once (a hard stop), which then settles back onto its legs.
 */
public final class GroundLegs {
	// Scratch; render thread only.
	private static final Matrix4f ROOT = new Matrix4f();
	private static final Matrix4f MATRIX = new Matrix4f();
	private static final Vector3f POINT = new Vector3f();
	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
	private static final ModelPart[] PARTS = new ModelPart[4];
	/** How far {@link #ground} moves the hoof off a step's face (pixels toward the tail along the leg). */
	private static float shift;
	/**
	 * What {@link #ground} found ahead of a hoof (see {@link #ramp}): the lift a hoof in its swing takes for it, and the
	 * last bit a hoof sliding along the ground takes (the animation's planted hooves slide a little).
	 */
	private static double ahead;
	private static double aheadClose;
	/** Where {@link #ground} found the middle of the sole (world height, before the leg is drawn up). */
	private static double soleHeight;
	/** The stretches of ground under a sole, front to back: where each ends (0..1 of the way) and its top. */
	private static final double[] STRETCH_END = new double[8];
	private static final double[] STRETCH_TOP = new double[8];
	/** Per leg, the last frame: where its sole was (world x, y, z), how far it wanted to draw up (blocks) and how far the pack had lifted it (for tests). */
	public static final double[] SOLES = new double[12];
	public static final float[] WANTED = new float[4];
	public static final float[] PACK_LIFTS = new float[4];
	public static int groundFrames;
	public static float DOWN;
	/** Per leg, the last frame: the ground its hoof stands on (world x, y, z), the pixels it was drawn up and its swing back (for tests). */
	public static final double[] TARGETS = new double[20];

	private GroundLegs() {
	}

	/**
	 * @param tilt     the body's tilt on the ground, radians, nose up positive (the legs stand upright against LEG_UPRIGHT of it)
	 * @param fore     how far the front legs draw up, blocks (only where the model hasn't been drawn yet)
	 * @param hind     how far the hind legs draw up, blocks (same)
	 * @param pixels   model pixels to a block in the legs' frame (16 over the model's scale)
	 * @param ride     the ridden horse's state, with where it was drawn, for each hoof's own ground
	 * @param root     the model's root part (the legs' parent)
	 * @param grounded false in a jump (the hooves still stay out of the ground, but don't raise the body)
	 * @return whether some hoof found its ground (then a step down, which is a short fall, needs no jump shape)
	 */
	public static boolean pose(
		final @Nullable ModelPart leftFront, final @Nullable ModelPart rightFront, final @Nullable ModelPart leftHind, final @Nullable ModelPart rightHind,
		final float tilt, final float fore, final float hind, final float pixels, final @Nullable RideState ride, final @Nullable ModelPart root,
		final boolean grounded
	) {
		PARTS[0] = leftFront;
		PARTS[1] = rightFront;
		PARTS[2] = leftHind;
		PARTS[3] = rightHind;
		boolean legs = false;
		for (final ModelPart part : PARTS) {
			legs |= part != null && Legs.of(part) != null;
		}
		if (!legs) {
			// (A layer with no leg boxes: nothing to pose, and it mustn't touch the ride's leg state.)
			return false;
		}
		final Level level = Minecraft.getInstance().level;
		final boolean drawn = ride != null && root != null && ride.drawnPoseSet && level != null;
		float dt = 0.0F;
		float partial = 1.0F;
		// (Another layer of the same horse this frame (saddle, armour): its legs are posed exactly as the body's were.)
		boolean again = false;
		if (drawn) {
			// Game time (ticks and the partial tick): how long since the legs were last posed, whatever the frame rate.
			partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
			final double now = level.getGameTime() + partial;
			again = now == ride.legRiseAt;
			dt = again || Double.isNaN(ride.legRiseAt) ? 0.0F : (float) Mth.clamp((now - ride.legRiseAt) / 20.0, 0.0, 0.1);
			ride.legRiseAt = now;
		}
		if (again ? ride.legsQuiet
			: tilt == 0.0F && fore == 0.0F && hind == 0.0F && (!drawn || !ride.narrow && !ride.legsActive && ride.drawnFit == 0.0F && !ride.lowered())) {
			// A horse no one rides on level ground, its body at rest and nothing under a hoof last frame: the legs are the
			// model's own. (A ridden horse's legs are always placed, so they never switch over mid-stride.)
			if (drawn && !again) {
				ride.legsQuiet = true;
				for (int i = 0; i < 4; i++) {
					ride.legRise[i] = 0.0F;
					ride.legShift[i] = 0.0F;
					ride.legBack[i] = 0.0F;
				}
			}
			return false;
		}
		if (drawn) {
			ride.legsQuiet = false;
		}
		// Which way is down in the legs' frame: read off where the model is drawn (the body's tilt, and whatever else turns
		// it), else the ride's tilt.
		final float down = drawn ? down(ride, root) : tilt;
		DOWN = down;
		// The legs stand upright against the ground's slope (the ride's tilt), not against the body's own sway with the
		// gait or the breath (that is the animation's: countering it put a hitch in every step).
		final float upright = tilt * LEG_UPRIGHT;
		final float stride = 1.0F - (1.0F - STRIDE_STEEP) * Mth.clamp(Math.abs(tilt) / STRIDE_STEEP_TILT, 0.0F, 1.0F);
		final float groundY = root == null ? 24.0F : (24.0F - root.y) / root.yScale;
		// Moving on, any hoof may be sliding a little along the ground (the animation's stride doesn't match the ground
		// exactly): it takes the last bit of lift to come up onto a step instead of popping up at its edge.
		final float moving = drawn ? Mth.clamp(ride.groundSpeed(partial) / SLIDE_SPEED, 0.0F, 1.0F) : 0.0F;
		// Heaving out of the water up a bank, the body rises onto it on its own: a front hoof that meets the bank's top on
		// the way lifts onto it at a hoof's speed and doesn't jerk the body up (it popped up onto it as it crossed the lip),
		// and no hoof reaching for it lifts the body on top of the heave.
		final boolean heave = drawn && ride.bankTicks > 0;
		// The most any leg would have to draw up past what it can (blocks; the body must come up by that), and the least
		// any standing hoof is above its ground (the body may come down by that, onto its legs).
		double missing = Double.NEGATIVE_INFINITY;
		double hanging = Double.POSITIVE_INFINITY;
		// The most a hoof on its way onto higher ground will need the body up, to land there (blocks).
		double landing = Double.NEGATIVE_INFINITY;
		boolean found = false;
		boolean active = false;
		for (int i = 0; i < 4; i++) {
			final ModelPart part = PARTS[i];
			final Legs.Leg leg = part == null ? null : Legs.of(part);
			if (leg == null) {
				continue;
			}
			// (How high the pack has the hoof, before it is turned upright: lifted mid-stride, it isn't standing.)
			final float packLift = (groundY - (part.y + leg.soleY * Mth.cos(part.xRot) - leg.z * Mth.sin(part.xRot))) / pixels;
			PACK_LIFTS[i] = packLift;
			// On steep ground the stride is shorter (a horse picks its way up and down stairs): the pack's swing, turned
			// about the top of the leg, is scaled down; then the leg stands upright against the tilt, its top's corner drawn
			// up into the body by that swing so no gap shows there (the body is lowered to match).
			Legs.swing(part, leg, part.xRot * stride + upright);
			Legs.draw(part, LEG_HALF_DEPTH * Math.abs(Mth.sin(upright)));
			if (!drawn) {
				up(part, (i < 2 ? fore : hind) * pixels, down);
				continue;
			}
			if (again) {
				final float back = ride.legBack[i];
				if (back != 0.0F) {
					Legs.swing(part, leg, part.xRot + back);
					Legs.draw(part, LEG_HALF_DEPTH * Math.max(Math.abs(Mth.sin(upright + back)) - Math.abs(Mth.sin(upright)), 0.0F));
				}
				up(part, ride.legRise[i] * pixels, down);
				continue;
			}
			// In its swing (the gait has lifted it), a hoof lifts toward what rises ahead of it, the more the higher the gait
			// has it (fully from PACK_LIFT up); a planted hoof stays on its ground.
			final float swing = Mth.clamp(packLift / PACK_LIFT, 0.0F, 1.0F);
			final double now = ground(ride, part, leg, level, i, swing > 0.0F || moving > 0.0F);
			// (A hoof the body has lifted off the ground, in front of a step, is in the air too: as free to lift as a
			// swinging one, fully once HOOF_FREE up.)
			final float free = now == Double.NEGATIVE_INFINITY || Double.isNaN(now) ? 0.0F : Mth.clamp((float) (soleHeight - now) / HOOF_FREE, 0.0F, 1.0F) * moving;
			final double swung = ahead > now ? now + (ahead - now) * swing : now;
			final double target = Math.max(Math.max(swung, ahead > now ? now + (ahead - now) * free : now), aheadClose > now ? now + (aheadClose - now) * moving : now);
			float back = 0.0F;
			if (shift != 0.0F) {
				// Off a step's face: the hoof swings back (or forward) onto the tread, its top's corner kept in the body.
				back = (float) Math.atan2(shift, leg.length);
				Legs.swing(part, leg, part.xRot + back);
				Legs.draw(part, LEG_HALF_DEPTH * Math.max(Math.abs(Mth.sin(upright + back)) - Math.abs(Mth.sin(upright)), 0.0F));
				active = true;
			}
			final double sole = sole(ride, part, leg);
			final double want = Double.isNaN(target) ? LEG_DRAW_MAX : target == Double.NEGATIVE_INFINITY ? 0.0 : target - sole;
			// (Past LEG_DRAW_MAX only for the frame before the body comes up for it, so the hoof isn't in the ground meanwhile.)
			float draw = (float) Mth.clamp(want, 0.0, grounded ? LEG_RISE_MAX : LEG_DRAW_MAX);
			if (dt > 0.0F && !Double.isNaN(target)) {
				// A hoof lifts, and a leg straightens, only so fast; but never leaving the hoof in the ground under it now.
				final float last = ride.legRise[i];
				final float under = now == Double.NEGATIVE_INFINITY || heave && i < 2 ? 0.0F
					: (float) Mth.clamp(now - sole, 0.0, grounded ? LEG_RISE_MAX : LEG_DRAW_MAX);
				draw = Math.max(Mth.clamp(draw, last - LEG_STRAIGHTEN_SPEED * dt, last + HOOF_LIFT_SPEED * dt), under);
			}
			if (swung > now && !heave) {
				// (Only from the gait's swing: a hoof the body lifted mustn't lift the body further, nor one reaching for a bank
				// the heave is lifting it onto.)
				landing = Math.max(landing, swung - sole - LEG_DRAW_MAX);
			}
			if (!Double.isNaN(now) && now != Double.NEGATIVE_INFINITY) {
				// (Only the ground under a hoof holds the body up at once; the lift to clear what is ahead doesn't.)
				final double under = now - sole;
				found = true;
				if (!heave || i >= 2) {
					missing = Math.max(missing, under - LEG_DRAW_MAX);
				}
				// Standing: not lifted by the stride, and over ground in reach (not stepping off a drop, or over the ground
				// behind a ledge).
				if (packLift < PACK_LIFT && under > -HANG_REACH) {
					hanging = Math.min(hanging, -under);
				}
			}
			if (draw > 0.0F) {
				up(part, draw * pixels, down);
				Legs.drawnFrames++;
				active = true;
			}
			ride.legRise[i] = draw;
			ride.legShift[i] = shift / pixels;
			ride.legBack[i] = back;
			WANTED[i] = (float) want;
			TARGETS[i * 5] = SOLES[i * 3];
			TARGETS[i * 5 + 1] = target;
			TARGETS[i * 5 + 2] = SOLES[i * 3 + 2];
			TARGETS[i * 5 + 3] = draw * pixels;
			TARGETS[i * 5 + 4] = back;
		}
		if (again) {
			return ride.legsFound;
		}
		if (drawn) {
			groundFrames++;
			ride.legsActive = active;
			ride.legsFound = grounded && found;
			// The body rests on its legs: where the ride carries it, unless that leaves every standing hoof above its ground
			// (it comes down onto them) or a leg needing to draw up further than it can (it comes up, at once: a hard stop,
			// in a jump too). Otherwise it settles back to where the ride carries it.
			// (A leg can draw up past LEG_DRAW_MAX for a moment, to LEG_RISE_MAX: the body rises smoothly to bring it back, and
			// only comes up at once for a leg that couldn't reach its ground at all.)
			final float floor = missing == Double.NEGATIVE_INFINITY ? -FIT_DOWN_MAX : (float) Math.min(ride.drawnFit + missing, FIT_UP_MAX);
			final float hardFloor = floor - (LEG_RISE_MAX - LEG_DRAW_MAX);
			float wanted = 0.0F;
			if (grounded) {
				if (hanging != Double.POSITIVE_INFINITY) {
					// (Never so low the chest meets a step ahead.)
					wanted = Math.max(Math.min(wanted, ride.drawnFit - (float) hanging), -ride.chestRoom);
				}
				// A hoof about to land on higher ground than its leg can draw up for: the body rises for it as it comes (the
				// other legs pushing), rather than all at once when it lands.
				if (landing > 0.0) {
					wanted = Math.max(wanted, ride.drawnFit + (float) landing);
				}
				wanted = Math.max(wanted, floor);
			}
			wanted = Mth.clamp(wanted, -FIT_DOWN_MAX, FIT_UP_MAX);
			if (dt > 0.0F) {
				// On its legs, a critically damped spring (solved implicitly), coming down no faster than it would fall.
				final float w = 1.0F / FIT_TIME;
				final float velocity = (ride.drawnFitVelocity + w * w * (wanted - ride.drawnFit) * dt) / (1.0F + 2.0F * w * dt + w * w * dt * dt);
				ride.drawnFitVelocity = Math.max(velocity, ride.drawnFitVelocity - FIT_GRAVITY * dt);
				ride.drawnFit += ride.drawnFitVelocity * dt;
			}
			if (grounded && ride.drawnFit < hardFloor) {
				ride.drawnFit = hardFloor;
				ride.drawnFitVelocity = Math.max(ride.drawnFitVelocity, 0.0F);
			}
			if (Math.abs(ride.drawnFit) < 1.0E-3F && wanted == 0.0F && Math.abs(ride.drawnFitVelocity) < 1.0E-2F) {
				ride.drawnFit = 0.0F;
				ride.drawnFitVelocity = 0.0F;
			}
		}
		return drawn && grounded && found;
	}

	/**
	 * After the jump's shape is put on the legs (see {@link AirLegs}): any hoof it leaves in the ground (landing) draws its
	 * leg up into the body onto it.
	 */
	public static void floor(
		final @Nullable ModelPart leftFront, final @Nullable ModelPart rightFront, final @Nullable ModelPart leftHind, final @Nullable ModelPart rightHind,
		final float pixels, final @Nullable RideState ride, final @Nullable ModelPart root
	) {
		final Level level = Minecraft.getInstance().level;
		if (ride == null || root == null || !ride.drawnPoseSet || level == null) {
			return;
		}
		final float down = down(ride, root);
		PARTS[0] = leftFront;
		PARTS[1] = rightFront;
		PARTS[2] = leftHind;
		PARTS[3] = rightHind;
		for (final ModelPart part : PARTS) {
			final Legs.Leg leg = part == null ? null : Legs.of(part);
			if (leg == null) {
				continue;
			}
			final double sole = sole(ride, part, leg);
			final double ground = surface(level, ride.drawnCameraX + POINT.x, ride.drawnCameraZ + POINT.z, sole + 1.0, sole - 1.0);
			if (ground > sole) {
				up(part, (float) Math.min(ground - sole, LEG_RISE_MAX) * pixels, down);
			}
		}
	}

	/**
	 * Draws a leg {@code pixels} straight up in the world into the body ({@code down} is which way is down in the legs'
	 * frame), so its hoof comes straight up onto the ground under it whatever the leg's slant.
	 */
	private static void up(final ModelPart part, final float pixels, final float down) {
		part.y -= pixels * Mth.cos(down);
		part.z -= pixels * Mth.sin(down);
	}

	/** Sets {@link #ROOT} to where the model's root is drawn and returns which way is down in it (radians toward the tail). */
	private static float down(final RideState ride, final ModelPart root) {
		ROOT.set(ride.drawnPose)
			.translate(root.x / 16.0F, root.y / 16.0F, root.z / 16.0F)
			.rotateZYX(root.zRot, root.yRot, root.xRot)
			.scale(root.xScale, root.yScale, root.zScale);
		MATRIX.set(ROOT).invert().transformDirection(0.0F, -1.0F, 0.0F, POINT);
		return (float) Math.atan2(POINT.z, POINT.y);
	}

	/** Sets {@link #MATRIX} to the leg part as drawn. */
	private static void legMatrix(final ModelPart part) {
		MATRIX.set(ROOT)
			.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F)
			.rotateZYX(part.zRot, part.yRot, part.xRot)
			.scale(part.xScale, part.yScale, part.zScale);
	}

	/**
	 * The world height of the middle of the leg's sole as it is posed now, and its camera-relative spot in {@link #POINT}.
	 * (Not its lowest corner: a planted leg rocks through upright in every stride, and the lowest corner switching from
	 * back to front there put a kink in every step. A rocking hoof's edge dips a pixel, as the animation draws it.)
	 */
	private static double sole(final RideState ride, final ModelPart part, final Legs.Leg leg) {
		legMatrix(part);
		MATRIX.transformPosition(leg.soleX / 16.0F, leg.soleY / 16.0F, leg.z / 16.0F, POINT);
		return ride.drawnCameraY + POINT.y;
	}

	/**
	 * The world height a hoof stands on, where the leg is posed now: the ground under its sole, front to back, worked out
	 * exactly (the ground only changes where the half-block grid crosses it): a stretch of higher ground under the sole
	 * holds it up, coming in over the first EDGE_BLEND blocks of it (so a hoof slipping onto a step's edge comes up onto
	 * it, never popping at a single point), searched from a block above the sole to a block below. Ground more than
	 * LEG_RISE_MAX above (or a wall) is a step's face: the hoof moves off it by exactly as far as it is in it
	 * ({@link #shift}, pixels toward the tail along the leg), or if it is in the face all over, back along the leg to the
	 * first ground it can stand on, as far as LEG_FACE_SEARCH more half-depths. Also finds what is ahead ({@link #ahead},
	 * {@link #aheadClose}) if {@code look}. Negative infinity if there is nothing there; NaN if there is nowhere to stand.
	 */
	private static double ground(final RideState ride, final ModelPart part, final Legs.Leg leg, final Level level, final int index, final boolean look) {
		final double y = sole(ride, part, leg);
		soleHeight = y;
		final double x = ride.drawnCameraX + POINT.x;
		final double z = ride.drawnCameraZ + POINT.z;
		SOLES[index * 3] = x;
		SOLES[index * 3 + 1] = y;
		SOLES[index * 3 + 2] = z;
		// The sole's front and back edges, level.
		MATRIX.transformPosition(leg.soleX / 16.0F, leg.soleY / 16.0F, (leg.z - leg.soleHalf) / 16.0F, POINT);
		final double fx = ride.drawnCameraX + POINT.x;
		final double fz = ride.drawnCameraZ + POINT.z;
		MATRIX.transformPosition(leg.soleX / 16.0F, leg.soleY / 16.0F, (leg.z + leg.soleHalf) / 16.0F, POINT);
		final double bx = ride.drawnCameraX + POINT.x;
		final double bz = ride.drawnCameraZ + POINT.z;
		final double length = Math.sqrt((bx - fx) * (bx - fx) + (bz - fz) * (bz - fz));
		// Toward the tail, level.
		final double ux = length < 1.0E-6 ? 0.0 : (bx - fx) / length;
		final double uz = length < 1.0E-6 ? 1.0 : (bz - fz) / length;
		final int stretches = stretches(level, fx, fz, bx, bz, y);
		// A face at the front (or the back): how much of the sole is in it.
		double front = 0.0;
		for (int k = 0; k < stretches && face(STRETCH_TOP[k], y); k++) {
			front = STRETCH_END[k];
		}
		double back = 0.0;
		for (int k = stretches - 1; k >= 0 && face(STRETCH_TOP[k], y); k--) {
			back = 1.0 - (k == 0 ? 0.0 : STRETCH_END[k - 1]);
		}
		double ground;
		if (front >= 1.0 || back >= 1.0 || front > 0.0 && back > 0.0) {
			// In a step's face all over: back along the leg to the first ground it can stand on, its front edge there.
			shift = 0.0F;
			ground = Double.NaN;
			for (int k = 1; k <= LEG_FACE_SEARCH; k++) {
				final float found = sample(ride, leg, level, (1 + k) * leg.soleHalf);
				if (!Float.isNaN(found)) {
					shift = (2 + k) * leg.soleHalf;
					ground = found == Float.NEGATIVE_INFINITY ? Double.NEGATIVE_INFINITY : y + found;
					break;
				}
			}
			if (Double.isNaN(ground)) {
				return Double.NaN;
			}
		} else {
			// Off the face by exactly as far as the sole is in it; then the ground under what is left of the sole.
			shift = (float) ((front - back) * 2.0 * leg.soleHalf);
			final double from = front > 0.0 ? front : 0.0;
			final double to = back > 0.0 ? 1.0 - back : 1.0;
			double low = Double.POSITIVE_INFINITY;
			for (int k = 0; k < stretches; k++) {
				if (!face(STRETCH_TOP[k], y)) {
					low = Math.min(low, base(STRETCH_TOP[k], y));
				}
			}
			ground = low;
			double start = 0.0;
			for (int k = 0; k < stretches; k++) {
				final double end = STRETCH_END[k];
				final double over = (Math.min(end, to) - Math.max(start, from)) * length;
				if (over > 0.0 && !face(STRETCH_TOP[k], y)) {
					// (Coming in over its first EDGE_BLEND blocks.)
					ground = Math.max(ground, low + (base(STRETCH_TOP[k], y) - low) * Math.min(over / EDGE_BLEND, 1.0));
				}
				start = end;
			}
			if (low == Double.POSITIVE_INFINITY || ground <= y - 1.0 && allNothing(stretches)) {
				ground = Double.NEGATIVE_INFINITY;
			}
		}
		// What rises ahead of it, the way the horse is going.
		ahead = Double.NEGATIVE_INFINITY;
		aheadClose = Double.NEGATIVE_INFINITY;
		if (look && ground != Double.NEGATIVE_INFINITY) {
			final boolean forward = ride.speed >= 0.0F;
			MATRIX.transformPosition(leg.soleX / 16.0F, leg.soleY / 16.0F, (leg.z + shift + (forward ? -leg.soleHalf : leg.soleHalf)) / 16.0F, POINT);
			final double sign = forward ? -1.0 : 1.0;
			ramp(level, ride.drawnCameraX + POINT.x, ride.drawnCameraZ + POINT.z, ux * sign, uz * sign, y);
		}
		return ground;
	}

	/**
	 * Splits the level line from (fx, fz) to (bx, bz) where the half-block grid crosses it into {@link #STRETCH_END} and
	 * {@link #STRETCH_TOP} (each stretch's ground, read at its middle, against a sole at {@code y}); returns how many.
	 */
	private static int stretches(final Level level, final double fx, final double fz, final double bx, final double bz, final double y) {
		int count = 0;
		// The crossings, as shares of the way, in order (a sole is a quarter block deep: a crossing or two at most).
		double[] cuts = STRETCH_END;
		final double gx0 = fx * 2.0;
		final double gx1 = bx * 2.0;
		final double gz0 = fz * 2.0;
		final double gz1 = bz * 2.0;
		for (double m = Math.floor(Math.min(gx0, gx1)) + 1.0; m < Math.max(gx0, gx1) && count < 3; m += 1.0) {
			cuts[count++] = (m - gx0) / (gx1 - gx0);
		}
		for (double m = Math.floor(Math.min(gz0, gz1)) + 1.0; m < Math.max(gz0, gz1) && count < 6; m += 1.0) {
			cuts[count++] = (m - gz0) / (gz1 - gz0);
		}
		java.util.Arrays.sort(cuts, 0, count);
		cuts[count++] = 1.0;
		double start = 0.0;
		for (int k = 0; k < count; k++) {
			final double middle = (start + cuts[k]) * 0.5;
			STRETCH_TOP[k] = surface(level, fx + (bx - fx) * middle, fz + (bz - fz) * middle, y + 1.0, y - 1.0);
			start = cuts[k];
		}
		return count;
	}

	/** A stretch's top is a step's face for a sole at {@code y}: a wall, or more than LEG_RISE_MAX up. */
	private static boolean face(final double top, final double y) {
		return Double.isNaN(top) || top - y > LEG_RISE_MAX;
	}

	/** A stretch's top to stand on (nothing there counts as a block below the sole). */
	private static double base(final double top, final double y) {
		return top == Double.NEGATIVE_INFINITY ? y - 1.0 : top;
	}

	/** Whether there is nothing under any of the stretches. */
	private static boolean allNothing(final int stretches) {
		for (int k = 0; k < stretches; k++) {
			if (STRETCH_TOP[k] != Double.NEGATIVE_INFINITY) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The height a hoof at (x, z) (its leading edge) moving along (dx, dz) lifts to, to clear the ground ahead, into
	 * {@link #ahead}: for each stretch of ground within RAMP_REACH blocks, its top less RAMP_SLOPE for every block away it
	 * is, the highest of them (so the hoof comes up steadily as it nears a step, never all at once); and the same with
	 * SLIDE_RAMP_SLOPE into {@link #aheadClose}. The ground only changes where the half-block grid is crossed, so it is
	 * read just past each crossing. Negative infinity if nothing ahead.
	 */
	private static void ramp(final Level level, final double x, final double z, final double dx, final double dz, final double sole) {
		double best = Double.NEGATIVE_INFINITY;
		double close = Double.NEGATIVE_INFINITY;
		final double gx = x * 2.0;
		final double gz = z * 2.0;
		double tx = dx > 1.0E-6 ? (Math.floor(gx) + 1.0 - gx) / (2.0 * dx) : dx < -1.0E-6 ? (Math.ceil(gx) - 1.0 - gx) / (2.0 * dx) : Double.POSITIVE_INFINITY;
		double tz = dz > 1.0E-6 ? (Math.floor(gz) + 1.0 - gz) / (2.0 * dz) : dz < -1.0E-6 ? (Math.ceil(gz) - 1.0 - gz) / (2.0 * dz) : Double.POSITIVE_INFINITY;
		final double stepX = Math.abs(dx) > 1.0E-6 ? 0.5 / Math.abs(dx) : Double.POSITIVE_INFINITY;
		final double stepZ = Math.abs(dz) > 1.0E-6 ? 0.5 / Math.abs(dz) : Double.POSITIVE_INFINITY;
		for (int k = 0; k < 10; k++) {
			final double t = Math.min(tx, tz);
			if (t > RAMP_REACH) {
				break;
			}
			final double ground = surface(level, x + dx * (t + 1.0E-3), z + dz * (t + 1.0E-3), sole + RAMP_HIGHEST, sole - 1.0);
			if (!Double.isNaN(ground) && ground != Double.NEGATIVE_INFINITY) {
				best = Math.max(best, ground - RAMP_SLOPE * t);
				close = Math.max(close, ground - SLIDE_RAMP_SLOPE * t);
			}
			if (tx <= tz) {
				tx += stepX;
			} else {
				tz += stepZ;
			}
		}
		ahead = best;
		aheadClose = close;
	}

	/**
	 * How far above the sole the ground is {@code along} pixels toward the tail of the sole (with {@link #MATRIX} set to the
	 * leg): negative infinity for nothing, NaN for a step's face (a wall, or more than LEG_RISE_MAX up).
	 */
	private static float sample(final RideState ride, final Legs.Leg leg, final Level level, final float along) {
		MATRIX.transformPosition(leg.soleX / 16.0F, leg.soleY / 16.0F, (leg.z + along) / 16.0F, POINT);
		final double y = ride.drawnCameraY + POINT.y;
		final double ground = surface(level, ride.drawnCameraX + POINT.x, ride.drawnCameraZ + POINT.z, y + 1.0, y - 1.0);
		return face(ground, y) ? Float.NaN : ground == Double.NEGATIVE_INFINITY ? Float.NEGATIVE_INFINITY : (float) (ground - y);
	}

	/**
	 * Top of the ground at (x, z) between {@code highest} and {@code lowest}, the shape's top at that very spot (the low
	 * half of a stair reads as itself). NaN if something there reaches above {@code highest} (a wall: the hoof is beside
	 * it, not under it), negative infinity if there is nothing.
	 */
	private static double surface(final Level level, final double x, final double z, final double highest, final double lowest) {
		final int bx = Mth.floor(x);
		final int bz = Mth.floor(z);
		for (int by = Mth.floor(highest); by >= Mth.floor(lowest); by--) {
			final BlockState state = level.getBlockState(POS.set(bx, by, bz));
			if (state.isAir()) {
				continue;
			}
			final VoxelShape shape = state.getCollisionShape(level, POS);
			if (shape.isEmpty()) {
				continue;
			}
			// (For the Y axis the other two coordinates go Z then X.)
			final double height = shape == Shapes.block() ? 1.0 : shape.max(Direction.Axis.Y, z - bz, x - bx);
			if (height == Double.NEGATIVE_INFINITY) {
				continue;
			}
			final double top = by + height;
			return top > highest ? Double.NaN : top;
		}
		return Double.NEGATIVE_INFINITY;
	}
}
