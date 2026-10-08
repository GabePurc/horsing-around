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
	 */
	public static void pose(
		final @Nullable ModelPart leftFront, final @Nullable ModelPart rightFront, final @Nullable ModelPart leftHind, final @Nullable ModelPart rightHind,
		final float tilt, final float fore, final float hind, final float pixels, final @Nullable RideState ride, final @Nullable ModelPart root,
		final boolean grounded
	) {
		final float upright = tilt * LEG_UPRIGHT;
		final Level level = Minecraft.getInstance().level;
		final boolean drawn = ride != null && root != null && ride.drawnPoseSet && level != null;
		float dt = 0.0F;
		if (drawn) {
			final long now = System.nanoTime();
			dt = ride.legRiseAt == 0L ? 0.0F : Math.min((now - ride.legRiseAt) * 1.0E-9F, 0.1F);
			ride.legRiseAt = now;
		}
		final boolean ground = drawn && grounded;
		final boolean settled = !drawn || ride.drawnFit == 0.0F && ride.legRise[0] == 0.0F && ride.legRise[1] == 0.0F && ride.legRise[2] == 0.0F
			&& ride.legRise[3] == 0.0F && ride.legShift[0] == 0.0F && ride.legShift[1] == 0.0F && ride.legShift[2] == 0.0F && ride.legShift[3] == 0.0F;
		PARTS[0] = leftFront;
		PARTS[1] = rightFront;
		PARTS[2] = leftHind;
		PARTS[3] = rightHind;
		if (upright == 0.0F && fore == 0.0F && hind == 0.0F && settled || drawn && !grounded) {
			// Level ground (or the air): nothing to do but undo what the last horse drawn did to the knees.
			for (int i = 0; i < 4; i++) {
				final Knees.Leg leg = PARTS[i] == null ? null : Knees.of(PARTS[i]);
				if (leg != null) {
					Knees.straighten(leg);
				}
				if (drawn) {
					ride.legRise[i] = 0.0F;
					ride.legShift[i] = 0.0F;
					ride.legSoleX[i] = Double.NaN;
				}
			}
			if (drawn) {
				ride.drawnFit = Math.abs(ride.drawnFit) < 1.0E-3F ? 0.0F : ease(ride.drawnFit, 0.0F, dt);
			}
			return;
		}
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
			Knees.swing(part, leg, part.xRot + upright);
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
				ride.drawnFit = Math.abs(ride.drawnFit) < 1.0E-3F ? 0.0F : ease(ride.drawnFit, 0.0F, dt);
			} else if (standing != Float.POSITIVE_INFINITY) {
				final float error = Math.max(standing, highest - (LEG_RISE_MAX - FIT_MARGIN));
				ride.drawnFit = Mth.clamp(ride.drawnFit + error * (1.0F - (float) Math.exp(-dt / FIT_TIME)), -FIT_DOWN_MAX, FIT_UP_MAX);
			}
		}
		// Then each knee bends to bring its hoof onto its ground, stepping up quickly and down more gently.
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
				ride.legRise[i] = settle(ride.legRise[i] + Mth.clamp(wanted - ride.legRise[i], -LEG_RISE_DOWN * dt, LEG_RISE_UP * dt), wanted);
				rise = ride.legRise[i];
				final float wantedShift = SHIFTS[i] / pixels;
				ride.legShift[i] = settle(ride.legShift[i] + Mth.clamp(wantedShift - ride.legShift[i], -LEG_SHIFT_RATE * dt, LEG_SHIFT_RATE * dt), wantedShift);
				shift = ride.legShift[i] * pixels;
			}
			// Drawn up so the top's corner stays in the body; the knee takes the hoof back down to where it stood, and the hoof
			// stands flat on its ground (fully once the body tilts or the hoof comes up a little).
			part.y -= drawUp;
			final float flat = Mth.clamp(Math.max(Math.abs(upright) / HOOF_LEVEL_TILT, rise / HOOF_LEVEL_RISE), 0.0F, 1.0F);
			Knees.plant(part, leg, rise * pixels, shift, tilt, drawUp, flat, i < 2);
		}
	}

	/** Snaps an eased value onto a zero target once it is within a thousandth. */
	private static float settle(final float value, final float target) {
		return target == 0.0F && Math.abs(value) < 1.0E-3F ? 0.0F : value;
	}

	private static float ease(final float value, final float target, final float dt) {
		return value + (target - value) * (1.0F - (float) Math.exp(-dt / FIT_TIME));
	}

	/**
	 * How far (blocks) the ground under the straight leg's sole is above it (negative, below it), where the model is drawn:
	 * the highest under its front, middle and back, so a hoof over a step's edge stands on it rather than in it. Searched
	 * from a block above the sole (a hoof drawn deep in a step still finds its top) to a block below. Ground more
	 * than LEG_RISE_MAX above is a step's face the hoof is up against: the hoof moves off it ({@link #SHIFT}, pixels
	 * toward the tail along the leg) onto the ground it can stand on. Negative infinity if there is nothing there; NaN if
	 * the hoof is in a step's face all over.
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
		// Front edge, middle, back edge.
		for (int edge = -1; edge <= 1; edge++) {
			MATRIX.transformPosition(leg.soleX / 16.0F, leg.soleY / 16.0F, (leg.soleZ + edge * leg.soleHalf + ahead) / 16.0F, POINT);
			final double x = ride.drawnCameraX + POINT.x;
			final double y = ride.drawnCameraY + POINT.y;
			final double z = ride.drawnCameraZ + POINT.z;
			if (edge == 0) {
				SOLES[index * 3] = x;
				SOLES[index * 3 + 1] = y;
				SOLES[index * 3 + 2] = z;
			}
			final double ground = surface(level, x, z, y + 1.0, y - 1.0);
			SAMPLES[edge + 1] = ground - y > LEG_RISE_MAX ? Float.NaN : Double.isNaN(ground) ? Float.NEGATIVE_INFINITY : (float) (ground - y);
		}
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
		SHIFT = 0.0F;
		return Float.NaN;
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
