package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.ride.RideController;
import dev.horsingaround.ride.RideState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Steps of a hoof's own at a step, on top of the gait's stride, which keeps its own pace (quicker legs looked absurd: user
 * direction, 2026-10-23). The animation's planted hooves slide along a little (its stride doesn't match the ground
 * exactly), and at a step that slid them up the step's face. Instead a planted hoof doesn't creep up a step (see
 * {@link GroundLegs}): when it comes to a step's face it steps up onto it, straight up off its spot, over the edge and down
 * clear past it, and is held there until the stride comes up to it. A hoof the gait is putting down across a step's edge
 * moves to land wholly on one side of it. Everything here moves the hoof along the heading (its leg turns about its top
 * to put it there) or says how high a step of its own carries it; render thread only.
 */
public final class StepLegs {
	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
	/** Out: how far to move the hoof along the heading (blocks, toward the head). */
	static float shift;
	/** Out: whether the hoof is in a step of its own, and then the height its lower edge goes to (world). */
	static boolean stepping;
	static double low;
	/** Out: while a step of its own lifts the hoof and carries it over (not setting it down), how high it must get (world); else NaN. */
	static double need;
	/** What {@link #face} found: the top of the step. */
	private static double faceTop;
	/** What {@link #edge} found: how far along the edge is, and whether the ground beyond it is the higher. */
	private static double edgeAlong;
	private static boolean edgeUp;

	private StepLegs() {
	}

	/** Forgets the leg's steps and holds (a horse whose legs are left as the model's own). */
	static void reset(final RideState ride, final int i) {
		ride.stepTime[i] = Float.NaN;
		ride.stepHoldX[i] = Double.NaN;
		ride.stepCarry[i] = Float.NaN;
		ride.stepShift[i] = 0.0F;
		ride.stepTurn[i] = 0.0F;
		ride.stepBottom[i] = Double.NaN;
		ride.strideRel[i] = Float.NaN;
		ride.strideRelRate[i] = 0.0F;
	}

	/**
	 * One leg, one frame.
	 *
	 * @param rel      where the animation has the sole along the body (blocks from the model's origin, toward the head)
	 * @param x        the middle of the sole as the animation has it (world x, then height and z)
	 * @param half     half the sole's depth (blocks)
	 * @param fx       the heading (level, unit; then z)
	 * @param packLift how high the animation has the hoof lifted (blocks)
	 * @param travel   how fast the horse moves along the heading (blocks a second; negative, backing)
	 * @param airborne in a jump or a fall
	 */
	static void step(
		final RideState ride, final Level level, final int i, final float rel, final double x, final double y, final double z, final double half,
		final double fx, final double fz, final float packLift, final float travel, final float dt, final boolean airborne
	) {
		shift = 0.0F;
		stepping = false;
		low = Double.NaN;
		need = Double.NaN;
		final double along = x * fx + z * fz;
		final float liftBefore = ride.stridePackLift[i];
		if (dt > 0.0F) {
			// The stride against the body: how fast the animation draws the hoof back, and where it lifts it on average.
			if (!Float.isNaN(ride.strideRel[i])) {
				final float k = 1.0F - (float) Math.exp(-dt / HOOF_VELOCITY_TIME);
				ride.strideRelRate[i] += ((rel - ride.strideRel[i]) / dt - ride.strideRelRate[i]) * k;
			}
			if (packLift >= PACK_LIFT && liftBefore < PACK_LIFT) {
				ride.strideLiftRel[i] = Float.isNaN(ride.strideLiftRel[i]) ? rel : ride.strideLiftRel[i] + (rel - ride.strideLiftRel[i]) * 0.5F;
			}
			ride.strideRel[i] = rel;
			ride.stridePackLift[i] = packLift;
		}
		final boolean planted = packLift < PACK_LIFT;
		final boolean onward = !airborne && travel > STEP_STILL;
		// (Only near a single step: on a hillside or a staircase the legs climb as the gait has them, see stepNear.)
		final boolean near = ride.stepNear >= STEP_NEAR_LEGS;
		final float release = dt > 0.0F ? 1.0F - (float) Math.exp(-dt / STEP_RELEASE_TIME) : 0.0F;

		if (!Float.isNaN(ride.stepTime[i])) {
			double fromAlong = ride.stepFromX[i] * fx + ride.stepFromZ[i] * fz;
			final double toAlong = ride.stepToX[i] * fx + ride.stepToZ[i] * fz;
			if (airborne || travel < -STEP_STILL) {
				// (A jump or backing off: let it go from where it is.)
				ride.stepTime[i] = Float.NaN;
				ride.stepCarry[i] = Float.NaN;
				ride.stepShift[i] = (float) (path(ride, i, fromAlong, toAlong) - along);
			} else {
				final double top = ride.stepTop[i] + STEP_CLEAR;
				float t = ride.stepTime[i];
				if (ride.stepAir[i] && t < STEP_RISE * ride.stepLength[i]) {
					// (Off the ground already: it lifts on with the body, where it was carried.)
					// (Never up to the step's face before it is over the top.)
					final double edge = ride.stepEdgeX[i] * fx + ride.stepEdgeZ[i] * fz;
					final double carried = Math.min(Mth.clamp(ride.stepCarry[i] - rel, -STEP_HOLD_MAX, STEP_HOLD_MAX), edge - STEP_FACE - half - along);
					ride.stepFromX[i] = x + fx * carried;
					ride.stepFromZ[i] = z + fz * carried;
				}
				// (Not on over the edge until the leg has it up there: the body may still be coming up for it. If it doesn't get
				// there, the step is given up, the hoof left where it is.)
				final boolean shy = ride.stepBottom[i] < top - STEP_CLEAR * 0.5;
				if (t >= STEP_RISE * ride.stepLength[i] && t < STEP_SET * ride.stepLength[i] && shy && ride.stepWaited[i] >= STEP_WAIT) {
					ride.stepTime[i] = Float.NaN;
					ride.stepCarry[i] = Float.NaN;
					ride.stepShift[i] = (float) (path(ride, i, fromAlong, toAlong) - along);
					stepping = false;
					low = Double.NaN;
					need = Double.NaN;
					shift = ride.stepShift[i];
					return;
				}
				if (t >= STEP_RISE * ride.stepLength[i] && t < STEP_SET * ride.stepLength[i] && shy) {
					ride.stepWaited[i] += dt;
				} else {
					t += dt;
				}
				ride.stepTime[i] = t;
				fromAlong = ride.stepFromX[i] * fx + ride.stepFromZ[i] * fz;
				if (t < ride.stepLength[i]) {
					stepping = true;
					shift = (float) (path(ride, i, fromAlong, toAlong) - along);
					if (t < STEP_RISE * ride.stepLength[i]) {
						shift = guard(level, x, z, fx, fz, half, ride.stepBottom[i], shift);
					}
					return;
				}
				// Down: held where it landed.
				ride.stepTime[i] = Float.NaN;
				ride.stepCarry[i] = Float.NaN;
				ride.stepHoldX[i] = ride.stepToX[i];
				ride.stepHoldZ[i] = ride.stepToZ[i];
				ride.stepShift[i] = 0.0F;
			}
		}

		if (!Double.isNaN(ride.stepHoldX[i])) {
			final double held = ride.stepHoldX[i] * fx + ride.stepHoldZ[i] * fz - along;
			if (held > 0.0 && held <= STEP_HOLD_MAX && planted && !airborne && travel >= -STEP_STILL) {
				shift = (float) held;
				return;
			}
			// The stride has come up to it (or lifts it, or it would be left too far behind): back onto the stride.
			ride.stepHoldX[i] = Double.NaN;
			ride.stepShift[i] = (float) held;
		}

		final double soleX = x + fx * ride.stepShift[i];
		final double soleZ = z + fz * ride.stepShift[i];
		if (planted && onward && near && liftBefore < PACK_LIFT) {
			// A planted hoof at a step's face steps up onto it; so does one the body has lifted off the ground in front of a step,
			// carried along with the body until it can reach.
			final double standing = y + ride.legRise[i];
			final double ground = RideController.surface(level, POS, soleX, soleZ, standing + STEP_MIN * 0.5, standing - 1.0, false);
			final boolean air = ride.stepBottom[i] - ground >= STEP_AIR;
			final double toFace = Double.isNaN(ground) || ground == Double.NEGATIVE_INFINITY ? Double.POSITIVE_INFINITY
				: face(level, soleX + fx * half, soleZ + fz * half, fx, fz, ground, air ? STEP_CARRY_LOOK : STEP_FACE);
			if (air && toFace <= STEP_CARRY_LOOK) {
				if (Float.isNaN(ride.stepCarry[i])) {
					ride.stepCarry[i] = rel + ride.stepShift[i];
				}
			} else {
				ride.stepCarry[i] = Float.NaN;
			}
			if (!Float.isNaN(ride.stepCarry[i])) {
				ride.stepShift[i] = (float) Mth.clamp(ride.stepCarry[i] - rel, -STEP_HOLD_MAX, STEP_HOLD_MAX);
			}
			if (toFace <= (air ? STEP_AIR_REACH : STEP_FACE) && soon(ride, i, travel) > STEP_LIFT_SOON) {
				// (A lower step is quicker.)
				final float length = STEP_TIME / (1.0F + travel / STEP_QUICKEN) * (float) (0.5 + 0.5 * Math.min(faceTop - ground, 1.0));
				final double from = along + ride.stepShift[i];
				// (Its toe already over the step: the edge is under the sole somewhere; from the toe.)
				final double edge = from + half + Math.max(toFace, 0.0);
				// Heel STEP_ROOM past the edge, or where the stride would have the hoof by the time it is down if that is
				// further (so it is held, not left behind), at most STEP_CARRY along; short of any step beyond.
				double to = Math.max(edge + STEP_ROOM + half, along + (travel + ride.strideRelRate[i]) * length);
				if (!air) {
					to = Math.min(to, from + STEP_CARRY);
				}
				final double top = faceTop;
				final double beyond = face(level, x + fx * (to - along + half), z + fz * (to - along + half), fx, fz, top, STEP_FACE);
				if (beyond < 0.0) {
					to = edge + STEP_ROOM + half;
				}
				final double landX = x + fx * (to - along);
				final double landZ = z + fz * (to - along);
				final double land = RideController.surface(level, POS, landX, landZ, top + STEP_MIN, top - 1.0, false);
				ride.stepTime[i] = 0.0F;
				ride.stepLength[i] = length;
				ride.stepWaited[i] = 0.0F;
				ride.stepFromX[i] = x + fx * ride.stepShift[i];
				ride.stepFromZ[i] = z + fz * ride.stepShift[i];
				ride.stepToX[i] = landX;
				ride.stepToZ[i] = landZ;
				ride.stepEdgeX[i] = x + fx * (edge - along);
				ride.stepEdgeZ[i] = z + fz * (edge - along);
				ride.stepFromY[i] = air ? ride.stepBottom[i] : ground;
				ride.stepTop[i] = top;
				ride.stepLand[i] = Double.isNaN(land) || land == Double.NEGATIVE_INFINITY ? top : land;
				ride.stepAir[i] = air;
				// (Off the ground, it keeps where it is against the stride while it lifts on.)
				ride.stepShift[i] = air ? (float) (from - along) : 0.0F;
				stepping = true;
				shift = (float) (path(ride, i, from, to) - along);
				return;
			}
		}

		if (!near || !planted || !onward) {
			ride.stepCarry[i] = Float.NaN;
		}
		// A landing placed clear of a step's edge, or what is left of a hold let go: kept while the hoof is down (it moves with
		// the stride), eased back onto the stride once it is up in the air.
		float target = 0.0F;
		float ease = planted ? 0.0F : release;
		if (!planted && packLift < PLACE_LIFT && packLift < liftBefore && !airborne && near) {
			final double move = place(level, soleX, y + ride.legRise[i], soleZ, fx, fz, half);
			target = ride.stepShift[i] + (float) move;
			ease = dt > 0.0F ? 1.0F - (float) Math.exp(-dt / PLACE_TIME) : 0.0F;
		} else if (planted && liftBefore >= PACK_LIFT && ride.stepShift[i] > 0.01F && onward) {
			// Put down a little ahead of the stride (onto a step): held there until the stride comes up to it.
			ride.stepHoldX[i] = soleX;
			ride.stepHoldZ[i] = soleZ;
			shift = ride.stepShift[i];
			ride.stepShift[i] = 0.0F;
			return;
		}
		ride.stepShift[i] += (target - ride.stepShift[i]) * ease;
		if (Math.abs(ride.stepShift[i]) < 1.0E-4F && target == 0.0F) {
			ride.stepShift[i] = 0.0F;
		}
		shift = guard(level, x, z, fx, fz, half, ride.stepBottom[i], ride.stepShift[i]);
	}

	/**
	 * {@code shift}, but never so far forward that the toe of a sole at (x, z) as the animation has it comes within
	 * STEP_FACE / 2 of ground higher than its lower edge ({@code bottom}, world) ahead: a hoof moved forward doesn't go into a
	 * step's face it hasn't lifted over yet.
	 */
	private static float guard(
		final Level level, final double x, final double z, final double fx, final double fz, final double half, final double bottom, final float shift
	) {
		if (shift <= 0.0F || Double.isNaN(bottom)) {
			return shift;
		}
		final double toFace = face(level, x + fx * half, z + fz * half, fx, fz, bottom + 0.02 - STEP_MIN, shift + STEP_FACE, true);
		return toFace == Double.POSITIVE_INFINITY ? shift : (float) Math.min(shift, Math.max(toFace - STEP_FACE * 0.5, 0.0));
	}

	/**
	 * Where a step of its own has the hoof along the heading now (sets {@link #low}, and {@link #need} until it sets down):
	 * straight up off its spot, along over the edge in a low arc, then down.
	 */
	private static double path(final RideState ride, final int i, final double from, final double to) {
		final float p = Mth.clamp(ride.stepTime[i] / ride.stepLength[i], 0.0F, 1.0F);
		final double top = ride.stepTop[i] + STEP_CLEAR;
		if (p < STEP_RISE) {
			need = top;
			low = ride.stepFromY[i] + (top - ride.stepFromY[i]) * smooth(p / STEP_RISE);
			return from;
		}
		if (p < STEP_SET) {
			final float q = (p - STEP_RISE) / (STEP_SET - STEP_RISE);
			need = top;
			low = top + STEP_ARC * Mth.sin(q * Mth.PI);
			return from + (to - from) * smooth(q);
		}
		final float q = (p - STEP_SET) / (1.0F - STEP_SET);
		low = ride.stepLand[i] + (top - ride.stepLand[i]) * (1.0 - smooth(q));
		return to;
	}

	/** Smoothstep: 0 to 1 starting and ending at rest. */
	private static double smooth(final double q) {
		final double c = Mth.clamp(q, 0.0, 1.0);
		return c * c * (3.0 - 2.0 * c);
	}

	/**
	 * How long until the gait lifts this hoof, as a share of a step's time: from where it lifts it on average and how fast
	 * it draws it back (a large number if that isn't known yet or it isn't being drawn back).
	 */
	private static float soon(final RideState ride, final int i, final float travel) {
		final float rate = ride.strideRelRate[i];
		if (Float.isNaN(ride.strideLiftRel[i]) || Float.isNaN(ride.strideRel[i]) || rate > -0.05F) {
			return Float.MAX_VALUE;
		}
		final float length = STEP_TIME / (1.0F + travel / STEP_QUICKEN);
		return Math.max(ride.strideRel[i] - ride.strideLiftRel[i], 0.0F) / -rate / length;
	}

	/**
	 * How far along (fx, fz) from (x, z), up to {@code reach} and a bit, the first ground between STEP_MIN and STEP_MAX above
	 * {@code ground} is (a step to step up onto; its top into {@link #faceTop}): read at (x, z) and just past each crossing of
	 * the half-block grid. Infinity if there is none first (or a wall); negative if (x, z) is already over it.
	 */
	private static double face(
		final Level level, final double x, final double z, final double fx, final double fz, final double ground, final double within
	) {
		return face(level, x, z, fx, fz, ground, within, false);
	}

	/** As above; with {@code walls}, a wall counts as a face too (else there is no step to step up onto there). */
	private static double face(
		final Level level, final double x, final double z, final double fx, final double fz, final double ground, final double within, final boolean walls
	) {
		final double reach = within + 0.05;
		final double gx = x * 2.0;
		final double gz = z * 2.0;
		double tx = fx > 1.0E-6 ? (Math.floor(gx) + 1.0 - gx) / (2.0 * fx) : fx < -1.0E-6 ? (Math.ceil(gx) - 1.0 - gx) / (2.0 * fx) : Double.POSITIVE_INFINITY;
		double tz = fz > 1.0E-6 ? (Math.floor(gz) + 1.0 - gz) / (2.0 * fz) : fz < -1.0E-6 ? (Math.ceil(gz) - 1.0 - gz) / (2.0 * fz) : Double.POSITIVE_INFINITY;
		final double stepX = Math.abs(fx) > 1.0E-6 ? 0.5 / Math.abs(fx) : Double.POSITIVE_INFINITY;
		final double stepZ = Math.abs(fz) > 1.0E-6 ? 0.5 / Math.abs(fz) : Double.POSITIVE_INFINITY;
		double t = 0.0;
		for (int k = 0; k < 10; k++) {
			final double read = RideController.surface(level, POS, x + fx * (t + 1.0E-3), z + fz * (t + 1.0E-3), ground + STEP_MAX, ground - 1.0, false);
			if (Double.isNaN(read)) {
				return !walls ? Double.POSITIVE_INFINITY : k == 0 ? -1.0 : t;
			}
			if (read - ground >= STEP_MIN) {
				faceTop = read;
				return k == 0 ? -1.0 : t;
			}
			t = Math.min(tx, tz);
			if (t > reach) {
				break;
			}
			if (tx <= tz) {
				tx += stepX;
			} else {
				tz += stepZ;
			}
		}
		return Double.POSITIVE_INFINITY;
	}

	/**
	 * How far to move a sole at (x, z), {@code half} deep, standing at height {@code y}, along (fx, fz) to land wholly on
	 * one side of a step's edge under it (ground differing by STEP_MIN or more): onto the step if it can reach, else back
	 * off it, STEP_ROOM clear (in front of a step's face, STEP_FACE); at most PLACE_REACH. 0 if it isn't across one.
	 */
	private static double place(final Level level, final double x, final double y, final double z, final double fx, final double fz, final double half) {
		if (!edge(level, x - fx * half, z - fz * half, fx, fz, 2.0 * half, y)) {
			return 0.0;
		}
		final double heel = -half;
		final double toEdge = edgeAlong + heel;
		// Wholly past the edge (beyond it, heel clear) or wholly short of it (toe clear).
		final double forward = toEdge + STEP_ROOM + half;
		final double back = toEdge - (edgeUp ? STEP_FACE : STEP_ROOM) - half;
		if (edgeUp && forward <= PLACE_REACH) {
			return forward;
		}
		final double best = forward < -back ? forward : back;
		return Math.abs(best) <= PLACE_REACH ? best : 0.0;
	}

	/**
	 * Whether the ground changes by STEP_MIN or more along (fx, fz) within {@code length} of (x, z) (a sole at height
	 * {@code y}): then how far along the first such change is ({@link #edgeAlong}) and whether it goes up ({@link #edgeUp}).
	 */
	private static boolean edge(final Level level, final double x, final double z, final double fx, final double fz, final double length, final double y) {
		final double gx = x * 2.0;
		final double gz = z * 2.0;
		double tx = fx > 1.0E-6 ? (Math.floor(gx) + 1.0 - gx) / (2.0 * fx) : fx < -1.0E-6 ? (Math.ceil(gx) - 1.0 - gx) / (2.0 * fx) : Double.POSITIVE_INFINITY;
		double tz = fz > 1.0E-6 ? (Math.floor(gz) + 1.0 - gz) / (2.0 * fz) : fz < -1.0E-6 ? (Math.ceil(gz) - 1.0 - gz) / (2.0 * fz) : Double.POSITIVE_INFINITY;
		final double stepX = Math.abs(fx) > 1.0E-6 ? 0.5 / Math.abs(fx) : Double.POSITIVE_INFINITY;
		final double stepZ = Math.abs(fz) > 1.0E-6 ? 0.5 / Math.abs(fz) : Double.POSITIVE_INFINITY;
		double before = RideController.surface(level, POS, x + fx * 1.0E-3, z + fz * 1.0E-3, y + STEP_MAX, y - STEP_MAX, false);
		for (int k = 0; k < 3; k++) {
			final double t = Math.min(tx, tz);
			if (t >= length) {
				return false;
			}
			final double after = RideController.surface(level, POS, x + fx * (t + 1.0E-3), z + fz * (t + 1.0E-3), y + STEP_MAX, y - STEP_MAX, false);
			if (!Double.isNaN(before) && !Double.isNaN(after) && before != Double.NEGATIVE_INFINITY && after != Double.NEGATIVE_INFINITY
				&& Math.abs(after - before) >= STEP_MIN) {
				edgeAlong = t;
				edgeUp = after > before;
				return true;
			}
			before = after;
			if (tx <= tz) {
				tx += stepX;
			} else {
				tz += stepZ;
			}
		}
		return false;
	}
}
