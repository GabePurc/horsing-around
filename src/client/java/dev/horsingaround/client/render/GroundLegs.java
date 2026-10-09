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
 * Hooves on the ground on steps, stairs and slopes, on top of whatever posed the legs (vanilla model or an animation
 * pack, so the stride stays): every leg stands upright against the body's tilt, turned about its top (see {@link Knees})
 * and drawn up into the body by that swing so no gap shows at the hip or shoulder, and its knee bends to put the hoof on
 * the ground under it. Each hoof finds its own ground where it is drawn (one of a pair can be on a step while the other
 * is below it), eased so it steps up rather than jumps; without where the model was drawn, a pair comes up together by
 * the ride's own reckoning.
 */
public final class GroundLegs {
	// Scratch; render thread only.
	private static final Matrix4f MATRIX = new Matrix4f();
	private static final Matrix4f ROOT = new Matrix4f();
	private static final Matrix4f INVERSE = new Matrix4f();
	private static final Vector3f POINT = new Vector3f();
	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
	private static final ModelPart[] PARTS = new ModelPart[4];
	private static final Knees.Leg[] LEGS = new Knees.Leg[4];
	private static final float[] RISES = new float[4];
	private static final float[] SHIFTS = new float[4];
	private static final float[] SAMPLES = new float[3];
	/** How far {@link #groundRise} moves the hoof off a step's face (pixels toward the tail along the leg). */
	private static float SHIFT;
	/** Per leg, the last frame: where the straight leg's sole was found (world x, y, z) and the ground it wanted (for tests). */
	public static final double[] SOLES = new double[12];
	public static final float[] WANTED = new float[4];
	public static final float[] PACK_LIFTS = new float[4];
	public static int groundFrames;
	public static float DOWN;
	/** Per leg, the last frame: the knee's fetlock target (world x, y, z), its reach and the reach asked for (for tests). */
	public static final double[] TARGETS = new double[20];

	private GroundLegs() {
	}

	/**
	 * @param tilt     the body's tilt, radians, nose up positive (the legs stand upright against LEG_UPRIGHT of it)
	 * @param fore     how far the front hooves come up, blocks (when each hoof can't find its own ground)
	 * @param hind     how far the hind hooves come up, blocks (same)
	 * @param pixels   model pixels to a block in the legs' frame (16 over the model's scale)
	 * @param ride     the ridden horse's state, with where it was drawn, for each hoof's own ground
	 * @param root     the model's root part (the legs' parent)
	 * @param grounded false in the air (no ground to find; the body's fit to the ground eases off)
	 * @return whether some hoof found its ground (then a step down, which is a short fall, needs no jump shape)
	 */
	public static boolean pose(
		final @Nullable ModelPart leftFront, final @Nullable ModelPart rightFront, final @Nullable ModelPart leftHind, final @Nullable ModelPart rightHind,
		final float tilt, final float fore, final float hind, final float pixels, final @Nullable RideState ride, final @Nullable ModelPart root,
		final boolean grounded
	) {
		final Level level = Minecraft.getInstance().level;
		final boolean drawn = ride != null && root != null && ride.drawnPoseSet && level != null;
		float dt = 0.0F;
		if (drawn) {
			// Game time (ticks and the partial tick), so the legs keep pace with the horse however fast frames come.
			final double now = level.getGameTime() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
			dt = Double.isNaN(ride.legRiseAt) ? 0.0F : (float) Mth.clamp((now - ride.legRiseAt) / 20.0, 0.0, 0.1);
			ride.legRiseAt = now;
		}
		final boolean ground = drawn && grounded;
		final boolean settled = !drawn || ride.drawnFit == 0.0F && ride.legRise[0] == 0.0F && ride.legRise[1] == 0.0F && ride.legRise[2] == 0.0F
			&& ride.legRise[3] == 0.0F && ride.legShift[0] == 0.0F && ride.legShift[1] == 0.0F && ride.legShift[2] == 0.0F && ride.legShift[3] == 0.0F;
		PARTS[0] = leftFront;
		PARTS[1] = rightFront;
		PARTS[2] = leftHind;
		PARTS[3] = rightHind;
		if (tilt == 0.0F && fore == 0.0F && hind == 0.0F && settled || drawn && !grounded) {
			// Level ground (or the air): nothing to do but undo what the last horse drawn did to the knees.
			for (int i = 0; i < 4; i++) {
				final Knees.Leg leg = PARTS[i] == null ? null : Knees.of(PARTS[i]);
				if (leg != null) {
					Knees.straighten(leg);
				}
				if (drawn) {
					ride.legRise[i] = 0.0F;
					ride.legShift[i] = 0.0F;
					ride.legRiseSpeed[i] = 0.0F;
					ride.legShiftSpeed[i] = 0.0F;
					ride.legSoleX[i] = Double.NaN;
				}
			}
			if (drawn) {
				ride.drawnFit = fit(ride.drawnFit, -ride.drawnFit, dt);
			}
			return false;
		}
		// Which way is down in the legs' frame: read off where the model is drawn (the body's tilt, and whatever else turns
		// it), else the ride's tilt.
		float down = tilt;
		if (drawn) {
			ROOT.set(ride.drawnPose)
				.translate(root.x / 16.0F, root.y / 16.0F, root.z / 16.0F)
				.rotateZYX(root.zRot, root.yRot, root.xRot)
				.scale(root.xScale, root.yScale, root.zScale);
			INVERSE.set(ROOT).invert().transformDirection(0.0F, -1.0F, 0.0F, POINT);
			down = (float) Math.atan2(POINT.z, POINT.y);
		}
		DOWN = down;
		final float upright = down * LEG_UPRIGHT;
		final float stride = 1.0F - (1.0F - STRIDE_STEEP) * Mth.clamp(Math.abs(down) / STRIDE_STEEP_TILT, 0.0F, 1.0F);
		// Each leg upright (turned about its top), and where it is drawn, how far the ground under its hoof is above it.
		float standing = Float.POSITIVE_INFINITY;
		float highest = Float.NEGATIVE_INFINITY;
		final float groundY = root == null ? 24.0F : (24.0F - root.y) / root.yScale;
		for (int i = 0; i < 4; i++) {
			final ModelPart part = PARTS[i];
			final Knees.Leg leg = part == null ? null : Knees.of(part);
			LEGS[i] = leg;
			if (leg == null) {
				continue;
			}
			// Models are shared between horses: whatever the last horse drawn did to the knee is undone.
			Knees.straighten(leg);
			// (How high the pack has the hoof, before it is turned upright: lifted mid-stride, it isn't standing.)
			final float packLift = (groundY - (part.y + leg.soleY * Mth.cos(part.xRot) - leg.soleZ * Mth.sin(part.xRot))) / pixels;
			// On steep ground the stride is shorter (a horse picks its way up and down stairs): the pack's swing, turned
			// about the top of the leg, is scaled down; then the leg stands upright against the tilt.
			Knees.swing(part, leg, part.xRot * stride + upright);
			if (ground) {
				final float rise = groundRise(ride, root, part, leg, level, i, dt);
				RISES[i] = rise;
				SHIFTS[i] = SHIFT;
				WANTED[i] = rise;
				PACK_LIFTS[i] = packLift;
				// (A hoof drawn in a step's face, too far down for a knee to bring it up, doesn't move the body.)
				if (!Float.isNaN(rise)) {
					if (packLift < PACK_LIFT && rise > Float.NEGATIVE_INFINITY) {
						standing = Math.min(standing, rise);
					}
					highest = Math.max(highest, rise);
				}
			}
		}
		final boolean levelBody = upright == 0.0F && fore == 0.0F && hind == 0.0F;
		if (ground) {
			groundFrames++;
			// The body comes up (or down) until the standing leg on the lowest ground is straight, but never so low that a
			// leg on higher ground would have to fold past what a knee can. (On level ground it settles back.)
			if (levelBody) {
				ride.drawnFit = fit(ride.drawnFit, -ride.drawnFit, dt);
			} else if (standing != Float.POSITIVE_INFINITY) {
				// (Held while the ride is already moving the body up or down fast, so the two never add up to a jolt.)
				final float hold = Mth.clamp(1.0F - Math.abs(ride.drawnRise) / FIT_HOLD_RISE, 0.0F, 1.0F);
				ride.drawnFit = fit(ride.drawnFit, Math.max(standing, highest - (LEG_RISE_MAX - FIT_MARGIN)), dt * hold);
			}
		}
		// Then each knee bends to bring its hoof onto its ground, eased in and out (never a snap).
		final float drawUp = LEG_HALF_DEPTH * Math.abs(Mth.sin(upright));
		for (int i = 0; i < 4; i++) {
			final ModelPart part = PARTS[i];
			final Knees.Leg leg = LEGS[i];
			if (leg == null) {
				continue;
			}
			float rise = i < 2 ? fore : hind;
			float shift = 0.0F;
			if (ground) {
				// (On level ground, the last hundredths settle to nothing so the legs go back to the pack's own.)
				final float wanted = Float.isNaN(RISES[i]) ? LEG_RISE_MAX
					: levelBody && RISES[i] < LEVEL_DEAD_ZONE ? 0.0F : Mth.clamp(RISES[i], 0.0F, LEG_RISE_MAX);
				ride.legRise[i] = settle(spring(ride.legRise[i], ride.legRiseSpeed, i, wanted, wanted > ride.legRise[i] ? LEG_RISE_SPEED : LEG_DROP_SPEED, dt), wanted);
				rise = ride.legRise[i];
				final float wantedShift = SHIFTS[i] / pixels;
				ride.legShift[i] = settle(spring(ride.legShift[i], ride.legShiftSpeed, i, wantedShift, LEG_SHIFT_SPEED, dt), wantedShift);
				shift = ride.legShift[i] * pixels;
			}
			// Drawn up so the top's corner stays in the body; the knee takes the hoof back down to where it stood, and the hoof
			// stands flat on its ground (fully once the body tilts or the hoof comes up a little).
			part.y -= drawUp;
			final float flat = Mth.clamp(Math.max(Math.abs(upright) / HOOF_LEVEL_TILT, rise / HOOF_LEVEL_RISE), 0.0F, 1.0F);
			Knees.TARGET[0] = Float.NaN;
			// Every knee bends forward (the joint halfway down reads as a knee, front and hind alike; never flipping).
			Knees.plant(part, leg, rise * pixels, shift, down, drawUp, flat, true);
			if (drawn && !Float.isNaN(Knees.TARGET[0])) {
				MATRIX.set(ride.drawnPose)
					.translate(root.x / 16.0F, root.y / 16.0F, root.z / 16.0F)
					.rotateZYX(root.zRot, root.yRot, root.xRot)
					.scale(root.xScale, root.yScale, root.zScale)
					.transformPosition(part.x / 16.0F, Knees.TARGET[0] / 16.0F, Knees.TARGET[1] / 16.0F, POINT);
				TARGETS[i * 5] = ride.drawnCameraX + POINT.x;
				TARGETS[i * 5 + 1] = ride.drawnCameraY + POINT.y;
				TARGETS[i * 5 + 2] = ride.drawnCameraZ + POINT.z;
				TARGETS[i * 5 + 3] = Knees.TARGET[2];
				TARGETS[i * 5 + 4] = Knees.TARGET[3];
			} else {
				TARGETS[i * 5] = Double.NaN;
			}
		}
		return ground && highest > Float.NEGATIVE_INFINITY;
	}

	/**
	 * A critically damped spring from {@code value} toward {@code target} over dt seconds (LEG_EASE a second; its speed,
	 * per leg, in {@code speed}), never faster than {@code most} blocks a second: eases in and out, no overshoot, no snap.
	 * Solved implicitly, so it is steady at any frame rate.
	 */
	private static float spring(final float value, final float[] speed, final int index, final float target, final float most, final float dt) {
		if (dt <= 0.0F) {
			return value;
		}
		final float w = LEG_EASE;
		float v = (speed[index] + w * w * (target - value) * dt) / (1.0F + 2.0F * w * dt + w * w * dt * dt);
		v = Mth.clamp(v, -most, most);
		speed[index] = v;
		return value + v * dt;
	}

	/** Snaps an eased value onto a zero target once it is within a thousandth. */
	private static float settle(final float value, final float target) {
		return target == 0.0F && Math.abs(value) < 1.0E-3F ? 0.0F : value;
	}

	/**
	 * The body's fit moved toward closing {@code error} (blocks): eased over FIT_TIME, never faster than FIT_RATE_MAX, and
	 * snapped to nothing once within a thousandth of it.
	 */
	private static float fit(final float current, final float error, final float dt) {
		final float step = Mth.clamp(error * (1.0F - (float) Math.exp(-dt / FIT_TIME)), -FIT_RATE_MAX * dt, FIT_RATE_MAX * dt);
		final float next = Mth.clamp(current + step, -FIT_DOWN_MAX, FIT_UP_MAX);
		return Math.abs(next) < 1.0E-3F && Math.abs(error + current) < 1.0E-3F ? 0.0F : next;
	}

	/**
	 * How far (blocks) the ground under the straight leg's sole is above it (negative, below it), where the model is drawn:
	 * the highest under its front, middle and back, so a hoof over a step's edge stands on it rather than in it. Searched
	 * from a block above the sole (a hoof drawn deep in a step still finds its top) to a block below. Ground more
	 * than LEG_RISE_MAX above is a step's face the hoof is up against: the hoof moves off it ({@link #SHIFT}, pixels
	 * toward the tail along the leg) onto the ground it can stand on, as far back as LEG_FACE_SEARCH more half-depths if
	 * it is in the face all over. Negative infinity if there is nothing there; NaN if there is nowhere to stand.
	 */
	private static float groundRise(
		final RideState ride, final ModelPart root, final ModelPart part, final Knees.Leg leg, final Level level, final int index, final float dt
	) {
		MATRIX.set(ride.drawnPose)
			.translate(root.x / 16.0F, root.y / 16.0F, root.z / 16.0F)
			.rotateZYX(root.zRot, root.yRot, root.xRot)
			.scale(root.xScale, root.yScale, root.zScale)
			.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F)
			.rotateZYX(part.zRot, part.yRot, part.xRot)
			.scale(part.xScale, part.yScale, part.zScale);
		// Where the hoof will be a moment from now, along the leg's own front-to-back (a swinging hoof gets to a step a
		// little before it is there).
		MATRIX.transformPosition(leg.soleX / 16.0F, leg.soleY / 16.0F, leg.soleZ / 16.0F, POINT);
		final double nowX = ride.drawnCameraX + POINT.x;
		final double nowZ = ride.drawnCameraZ + POINT.z;
		float ahead = 0.0F;
		if (dt > 0.0F && !Double.isNaN(ride.legSoleX[index])) {
			MATRIX.transformDirection(0.0F, 0.0F, 1.0F, POINT);
			final double along = ((nowX - ride.legSoleX[index]) * POINT.x + (nowZ - ride.legSoleZ[index]) * POINT.z) / (POINT.x * POINT.x + POINT.z * POINT.z);
			ahead = Mth.clamp((float) along / dt * LEG_LOOKAHEAD, -LEG_LOOKAHEAD_MAX, LEG_LOOKAHEAD_MAX);
		}
		if (dt > 0.0F) {
			ride.legSoleX[index] = nowX;
			ride.legSoleZ[index] = nowZ;
		}
		MATRIX.transformPosition(leg.soleX / 16.0F, leg.soleY / 16.0F, leg.soleZ / 16.0F, POINT);
		SOLES[index * 3 + 1] = ride.drawnCameraY + POINT.y;
		// Front edge, middle, back edge: the higher of the ground under each now and where it will be (a step's face is
		// judged only where the hoof is: one just ahead is something to come up for, not to back off).
		for (int edge = -1; edge <= 1; edge++) {
			final float now = sample(ride, leg, level, edge * leg.soleHalf);
			final float soon = ahead == 0.0F ? now : sample(ride, leg, level, edge * leg.soleHalf + ahead);
			SAMPLES[edge + 1] = Float.isNaN(now) || Float.isNaN(soon) ? now : Math.max(now, soon);
		}
		SOLES[index * 3] = nowX;
		SOLES[index * 3 + 2] = nowZ;
		final boolean front = Float.isNaN(SAMPLES[0]);
		final boolean middle = Float.isNaN(SAMPLES[1]);
		final boolean back = Float.isNaN(SAMPLES[2]);
		if (!front && !middle && !back) {
			SHIFT = 0.0F;
			return Math.max(SAMPLES[0], Math.max(SAMPLES[1], SAMPLES[2]));
		}
		if (front && !back) {
			// Up against a step's face ahead: back off it.
			SHIFT = middle ? 2.0F * leg.soleHalf : leg.soleHalf;
			return middle ? SAMPLES[2] : Math.max(SAMPLES[1], SAMPLES[2]);
		}
		if (back && !front) {
			SHIFT = middle ? -2.0F * leg.soleHalf : -leg.soleHalf;
			return middle ? SAMPLES[0] : Math.max(SAMPLES[0], SAMPLES[1]);
		}
		// In a step's face all over: back along the leg to the first ground it can stand on, its front edge there.
		for (int k = 1; k <= LEG_FACE_SEARCH; k++) {
			final float rise = sample(ride, leg, level, (1 + k) * leg.soleHalf);
			if (!Float.isNaN(rise)) {
				SHIFT = (2 + k) * leg.soleHalf;
				return rise;
			}
		}
		SHIFT = 0.0F;
		return Float.NaN;
	}

	/**
	 * How far above the sole the ground is {@code along} pixels toward the tail of the straight leg's sole (with
	 * {@link #MATRIX} set to the leg): negative infinity for nothing, NaN for a step's face (more than LEG_RISE_MAX up).
	 */
	private static float sample(final RideState ride, final Knees.Leg leg, final Level level, final float along) {
		MATRIX.transformPosition(leg.soleX / 16.0F, leg.soleY / 16.0F, (leg.soleZ + along) / 16.0F, POINT);
		final double y = ride.drawnCameraY + POINT.y;
		final double ground = surface(level, ride.drawnCameraX + POINT.x, ride.drawnCameraZ + POINT.z, y + 1.0, y - 1.0);
		return ground - y > LEG_RISE_MAX ? Float.NaN : Double.isNaN(ground) ? Float.NEGATIVE_INFINITY : (float) (ground - y);
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
