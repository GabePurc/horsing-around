package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.ride.RideController;
import dev.horsingaround.ride.RideState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * A hoof's stride as drawn, on top of the animation's, keeping its timing and the shape of its swing. As far as the horse
 * is collected for a step ({@link RideState#collect}; on open ground the animation's stride is left as it is):
 * <ul>
 * <li>where a hoof is put down it stays put while it is on the ground;</li>
 * <li>each swing follows a path of its own to where it lands, clear of an edge (wholly on one level, and not right up
 * against the foot of a step): straight up off the ground, along in an arc clear of what it passes over, never past the
 * foot of a step until it is up over the edge (it waits in front of it, lifted), and straight down onto its spot;</li>
 * <li>a planted hoof whose leg would meet a step's edge lifts early, in a swing of its own, onto the step.</li>
 * </ul>
 * Also measures how fast the animation draws a planted hoof back against how fast the horse moves over the ground
 * ({@link RideState#strideMatch}), which sets the cadence when collected. Render thread only.
 */
final class Stride {
	/** What {@link #step} decided for the leg: how far to move its hoof along the body (blocks, toward the head). */
	static float shift;
	/** How high to lift the hoof above its path (blocks): its arc in a collected swing. */
	static float lift;
	/** Whether the hoof is off the ground in a swing of its own (not the animation's). */
	static boolean ownSwing;
	/**
	 * Collected, in its swing: the highest ground it will pass over before it lands (world height; NaN: none worked out),
	 * and how far above that to keep (blocks).
	 */
	static double pathTop;
	static float clear;

	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
	/** The ground along a line (see {@link #profile}): where each stretch ends (blocks along) and its top. */
	private static final double[] PROFILE_END = new double[24];
	private static final double[] PROFILE_TOP = new double[24];
	private static int profileCount;
	private static double profileFrom;

	private Stride() {
	}

	/**
	 * One frame of leg {@code i}'s stride.
	 *
	 * @param rel      where the animation has the hoof, blocks along the body ahead of the model's origin
	 * @param soleX    where the animation has the middle of the sole (world x, y, z)
	 * @param topX     the top of the leg (world x, y, z)
	 * @param half     half the sole's depth (blocks)
	 * @param fx       the body's heading (world, level, unit)
	 * @param packLift how high the animation has the hoof (blocks)
	 * @param travel   how fast the horse moves along its heading (blocks a second, negative backing up)
	 * @param tilt     the body's tilt (radians)
	 * @param dt       seconds since the last frame (0: a layer drawn again this frame)
	 */
	static void step(
		final RideState ride, final Level level, final int i, final float rel, final double soleX, final double soleY, final double soleZ, final double topX,
		final double topY, final double topZ, final double half, final double fx, final double fz, final float packLift, final float travel, final float tilt,
		final float dt
	) {
		final float previous = ride.footRel[i];
		lift = 0.0F;
		pathTop = Double.NaN;
		clear = 0.0F;
		if (Float.isNaN(previous) || dt <= 0.0F) {
			if (Float.isNaN(previous)) {
				// First seen: planted where the animation has it.
				ride.footRel[i] = rel;
				ride.footDown[i] = true;
				ride.footX[i] = soleX;
				ride.footZ[i] = soleZ;
				ride.footShift[i] = 0.0F;
				ride.footTime[i] = 0.0F;
				ride.footDrawnBack[i] = 0.0F;
				ride.footOwnSwing[i] = 0.0F;
				ride.footLandX[i] = Double.NaN;
			}
			shift = ride.footShift[i];
			ownSwing = ride.footOwnSwing[i] > 0.0F;
			return;
		}
		ride.footRel[i] = rel;
		// Toward the head or the tail of the way the horse is going (standing, as if forward).
		final float way = travel < -STRIDE_STILL ? -1.0F : 1.0F;
		final float relVelocity = (rel - previous) / dt * way;
		final float keep = ride.collect;
		final float gap = Float.isNaN(ride.footGap[i]) ? STRIDE_CLEAR : ride.footGap[i];
		final float near = 1.0F - Mth.clamp(gap / STRIDE_CLEAR, 0.0F, 1.0F);
		final double soleAlong = soleX * fx + soleZ * fz;
		ride.footTime[i] += dt;
		if (ride.footDown[i]) {
			// Planted: the hoof stays where it was put down, as far as the horse is collected and while it is on the ground;
			// otherwise it goes along with the animation (the plant moves with it).
			final double drift = (1.0F - keep * smooth(near)) * (rel - previous + travel * dt);
			ride.footX[i] += fx * drift;
			ride.footZ[i] += fz * drift;
			float held = (float) (ride.footX[i] * fx + ride.footZ[i] * fz - soleAlong);
			ride.footDrawnBack[i] -= (rel - previous) * way;
			if (keep > STRIDE_KEEP_OWN && travel > STRIDE_STILL
				&& ownSwingNeeded(ride, level, i, soleX + fx * held, soleY, soleZ + fz * held, topX, topY, topZ, half, fx, fz, travel)) {
				ownLiftOff(ride, i, rel, held, soleAlong + held, fx, fz);
			} else if (packLift > STRIDE_LIFT_OFF || relVelocity > STRIDE_SWING_SPEED && ride.footTime[i] > STRIDE_PHASE_MIN) {
				// Lifted by the animation, or swinging forward without lifting (a model that only swings its legs).
				liftOff(ride, i, rel, held, travel, tilt);
			} else if (Math.abs(held) > STRIDE_SHIFT_MAX) {
				// Held further from the animation's stride than a leg reaches: it gives (slides) rather than reach further.
				final float give = held - Math.copySign(STRIDE_SHIFT_MAX, held);
				ride.footX[i] -= fx * give;
				ride.footZ[i] -= fz * give;
				held -= give;
			}
			ride.footShift[i] = held;
		} else {
			// Swinging, as far through as the animation's own swing is (a swing of its own: by time, whatever the animation
			// does meanwhile, so it doesn't start over).
			final boolean own = ride.footOwnSwing[i] > 0.0F;
			final float span = (ride.footRelLand[i] - ride.footRelLift[i]) * way;
			float through = own ? ride.footTime[i] / ride.footOwnSwing[i]
				: span > STRIDE_SPAN_MIN ? ((rel - ride.footRelLift[i]) * way) / span : ride.footTime[i] / Math.max(ride.footSwingTime[i], 0.05F);
			through = Math.max(ride.footSwing[i], Mth.clamp(through, 0.0F, 1.0F));
			ride.footSwing[i] = through;
			// Open ground: the animation's own swing, from where it lifted (moved by footShiftLift) to where the animation puts it.
			final float free = own ? 0.0F : ride.footShiftLift[i] * (1.0F - smooth(through));
			if (!own && keep > 0.0F) {
				landing(ride, level, i, rel, soleX, soleY, soleZ, half, fx, fz, way, travel, through);
			}
			boolean arrived = true;
			boolean waiting = false;
			if ((own || keep > 0.0F) && !Double.isNaN(ride.footLandX[i])) {
				// Collected: a path of its own, from where it lifted to where it lands: straight up off the ground, along, then
				// straight down onto its spot; never past the foot of a step until it is up over its edge, within reach of the top
				// of the leg, at a hoof's pace; lifted clear of everything under the rest of the way.
				final double from = ride.footX[i] * fx + ride.footZ[i] * fz;
				final double to = ride.footLandX[i] * fx + ride.footLandZ[i] * fz;
				double along = from + (to - from) * smooth(Mth.clamp((through - STRIDE_PATH_START) / (STRIDE_PATH_END - STRIDE_PATH_START), 0.0F, 1.0F));
				final double was = Double.isNaN(ride.footPath[i]) ? from : ride.footPath[i];
				final double toward = (to - was) * way;
				profile(level, ride.footX[i] + fx * (was - from), ride.footZ[i] + fz * (was - from), fx * way, fz * way, -half, Math.max(toward, 0.0) + half,
					Double.isNaN(ride.footBottom[i]) ? soleY : ride.footBottom[i]);
				final double under = topAt(0.0);
				double top = Double.NEGATIVE_INFINITY;
				double ahead = Double.NEGATIVE_INFINITY;
				for (int k = 0; k < profileCount; k++) {
					// (Ground ahead counts less the further it is: the hoof rises toward it as it comes, the way a swinging hoof arcs
					// up a step's edge, rather than all at once.)
					top = Math.max(top, PROFILE_TOP[k] - RAMP_SLOPE * Math.max((k == 0 ? profileFrom : PROFILE_END[k - 1]) - half, 0.0));
					if ((k == 0 ? profileFrom : PROFILE_END[k - 1]) < half + STRIDE_LIFT_FIRST + Math.abs(travel) * STRIDE_LIFT_LOOK) {
						ahead = Math.max(ahead, PROFILE_TOP[k]);
					}
					final double start = k == 0 ? profileFrom : PROFILE_END[k - 1];
					if (start > 0.0 && PROFILE_TOP[k] > under + STRIDE_RISE && ride.footBottom[i] < PROFILE_TOP[k] + STRIDE_EDGE_CLEAR) {
						// The foot of a step it isn't up over yet: it waits in front of it, lifted.
						final double limit = was + way * (start - half - STRIDE_EDGE_MARGIN);
						final double held = way > 0.0F ? Math.min(along, Math.max(limit, was)) : Math.max(along, Math.min(limit, was));
						waiting = held != along;
						along = held;
						break;
					}
				}
				if (!Double.isNaN(ride.footBottom[i]) && ride.footBottom[i] < ahead + STRIDE_SWING_CLEAR * 0.5 && through < 1.0F) {
					// Not clear of the ground just ahead yet: up first, then along (moving on, it would drag).
					along = was;
					waiting = true;
				}
				final double hip = topX * fx + topZ * fz;
				along = way > 0.0F ? Math.min(along, hip + STRIDE_REACH) : Math.max(along, hip - STRIDE_REACH);
				final double pace = (STRIDE_PATH_SPEED + STRIDE_PATH_SPEED_PER_TRAVEL * Math.abs(travel)) * dt;
				along = was + Mth.clamp(along - was, -pace, pace);
				// Never further from where the animation has it than a leg reaches (held further, it would only slide along).
				along = Mth.clamp(along, soleAlong - STRIDE_SHIFT_MAX, soleAlong + STRIDE_SHIFT_MAX);
				ride.footPath[i] = along;
				pathTop = top == Double.NEGATIVE_INFINITY ? Double.NaN : top;
				final float path = (float) (along - soleAlong);
				ride.footShift[i] = own ? path : free + (path - free) * keep;
				arrived = Math.abs(to - along) < STRIDE_ARRIVED;
				// In an arc, by how far along its path it has got (the animation may have set it down already): up early,
				// carried along, down onto its spot as it gets there; kept clear of what it passes over till then.
				final float gone = Math.abs(to - from) < 1.0E-3 ? through : (float) Mth.clamp((along - from) / (to - from), 0.0, 1.0);
				lift = Math.max(lift, STRIDE_OWN_LIFT * Math.min(STRIDE_ARC_RISE * Mth.sin(gone * Mth.PI), 1.0F));
				clear = STRIDE_SWING_CLEAR * (1.0F - smooth(Mth.clamp((gone - STRIDE_CLEAR_UNTIL) / (1.0F - STRIDE_CLEAR_UNTIL), 0.0F, 1.0F)));
			} else {
				ride.footPath[i] = Double.NaN;
				ride.footShift[i] = free;
			}
			// Down when its swing is over (the animation's, or its own once it is near the ground), where it has got to unless
			// it is still waiting to get up over a step; but never waiting longer than STRIDE_WAIT_MAX (it lands where it is),
			// nor at all once the horse stands.
			final boolean over = own ? through >= 1.0F : through >= STRIDE_LAND_THROUGH && packLift < STRIDE_TOUCH_DOWN && relVelocity <= STRIDE_SWING_SPEED * 0.5F;
			if (over) {
				ride.footWaited[i] += dt;
			}
			if (over && ((arrived || !waiting) && (!own || gap < STRIDE_CLEAR) || ride.footWaited[i] > STRIDE_WAIT_MAX || Math.abs(travel) < STRIDE_STILL)) {
				touchDown(ride, i, rel, soleX, soleZ, fx, fz, span, own);
				ride.footOwnSwing[i] = 0.0F;
				ride.footLandX[i] = Double.NaN;
				ride.footPath[i] = Double.NaN;
			}
		}
		if (keep <= 0.0F && ride.footOwnSwing[i] <= 0.0F) {
			// Open ground: back to the animation's stride exactly (easing out of what was left of the collected one, so nothing
			// jumps), and the next collected one starts from it.
			final float fade = (float) Math.exp(-dt / STRIDE_OPEN_EASE);
			ride.footShift[i] = Math.abs(ride.footShift[i]) < 1.0E-3F ? 0.0F : ride.footShift[i] * fade;
			ride.footShiftLift[i] = Math.abs(ride.footShiftLift[i]) < 1.0E-3F ? 0.0F : ride.footShiftLift[i] * fade;
			if (ride.footDown[i]) {
				ride.footX[i] = soleX + fx * ride.footShift[i];
				ride.footZ[i] = soleZ + fz * ride.footShift[i];
			}
		}
		shift = ride.footShift[i];
		ownSwing = ride.footOwnSwing[i] > 0.0F;
	}

	/** Touch-down: planted where it is drawn now. {@code span} is the swing's span, if it was the animation's ({@code own} false). */
	private static void touchDown(final RideState ride, final int i, final float rel, final double soleX, final double soleZ, final double fx, final double fz,
		final float span, final boolean own) {
		ride.footDown[i] = true;
		ride.footWaited[i] = 0.0F;
		ride.footX[i] = soleX + fx * ride.footShift[i];
		ride.footZ[i] = soleZ + fz * ride.footShift[i];
		if (!own && (span > STRIDE_SPAN_MIN || Float.isNaN(span))) {
			ride.footRelLand[i] = rel;
		}
		if (!own) {
			ride.footSwingTime[i] = ride.footTime[i];
		}
		ride.footTime[i] = 0.0F;
		ride.footDrawnBack[i] = 0.0F;
	}

	private static void liftOff(final RideState ride, final int i, final float rel, final float held, final float travel, final float tilt) {
		// How fast the animation drew it back while it was down, against how fast the horse moved: the cadence that would
		// have matched (at the cadence it ran at). Only on level ground (on a slope the stride is shortened).
		final float stance = ride.footTime[i];
		final float speed = Math.abs(travel);
		if (stance > STRIDE_PHASE_MIN && speed > STRIDE_MEASURE_SPEED && ride.footDrawnBack[i] > 0.05F && Math.abs(tilt) < STRIDE_MEASURE_TILT) {
			final float match = ride.cadence * speed * stance / ride.footDrawnBack[i];
			ride.strideMatch = Float.isNaN(ride.strideMatch) ? match : ride.strideMatch + (match - ride.strideMatch) * STRIDE_MATCH_EASE;
		}
		ride.footDown[i] = false;
		ride.footShiftLift[i] = held;
		ride.footRelLift[i] = rel;
		ride.footTime[i] = 0.0F;
		ride.footSwing[i] = 0.0F;
		ride.footLandX[i] = Double.NaN;
		ride.footPath[i] = Double.NaN;
	}

	/** Off on a swing of its own, from where it is (along: how far along the heading, world) to footLandX/Z, over a time to suit how far. */
	private static void ownLiftOff(final RideState ride, final int i, final float rel, final float held, final double along, final double fx, final double fz) {
		final double to = ride.footLandX[i] * fx + ride.footLandZ[i] * fz;
		ride.footDown[i] = false;
		ride.footShiftLift[i] = held;
		ride.footRelLift[i] = rel;
		ride.footTime[i] = 0.0F;
		ride.footSwing[i] = 0.0F;
		ride.footPath[i] = Double.NaN;
		ride.footOwnSwing[i] = (float) Mth.clamp(Math.abs(to - along) / STRIDE_OWN_SPEED, STRIDE_OWN_TIME_MIN, STRIDE_OWN_TIME_MAX);
	}

	/**
	 * Whether a planted hoof (its sole at x, y, z) should lift early onto the step ahead (within STRIDE_OWN_LOOK): its leg,
	 * leaning with its top ahead of the hoof, would meet the step's edge within STRIDE_FACE_LOOK seconds. (Not for a leg
	 * the rising body has lifted off its ground: on a hillside that is every leg, every stride; it waits for its own swing.)
	 * If so, where it should land is in footLandX/Z: wholly on the step and STRIDE_TREAD_IN in.
	 */
	private static boolean ownSwingNeeded(
		final RideState ride, final Level level, final int i, final double x, final double y, final double z, final double topX, final double topY, final double topZ,
		final double half, final double fx, final double fz, final float travel
	) {
		profile(level, x, z, fx, fz, -half, STRIDE_OWN_LOOK, y);
		final double ground = topAt(0.0);
		if (Double.isNaN(ground)) {
			return false;
		}
		// The foot of the step ahead: where the ground first rises more than a step's worth.
		double face = Double.NaN;
		double faceTop = Double.NaN;
		for (int k = 0; k < profileCount; k++) {
			final double from = k == 0 ? profileFrom : PROFILE_END[k - 1];
			if (from > half * 0.5 && PROFILE_TOP[k] > ground + STRIDE_RISE) {
				face = from;
				faceTop = PROFILE_TOP[k];
				break;
			}
		}
		if (Double.isNaN(face)) {
			return false;
		}
		// The leg's front, leaning with its top ahead of the hoof, against the step's edge (or its whole height, below the edge).
		final double lean = (topX - x) * fx + (topZ - z) * fz;
		final double up = topY - y;
		final double front = half + (up > 1.0E-3 && lean > 0.0 ? lean * Math.min((faceTop - y) / up, 1.0) : 0.0);
		if (front + Math.max(travel, 0.0F) * STRIDE_FACE_LOOK < face) {
			return false;
		}
		// Where to land: wholly on the step, STRIDE_TREAD_IN in (the path waits in front of the step until it can get over it).
		for (double t = face + half + STRIDE_TREAD_IN; t <= PROFILE_END[profileCount - 1] - half; t += STRIDE_PLACE_STEP) {
			final double top = flatAround(t, half + STRIDE_EDGE_MARGIN);
			if (!Double.isNaN(top) && top > ground + STRIDE_RISE && !faceAhead(t + half, top)) {
				ride.footLandX[i] = x + fx * t;
				ride.footLandZ[i] = z + fz * t;
				return true;
			}
		}
		return false;
	}

	/**
	 * Where a swinging hoof lands (footLandX/Z): where the animation will put it down, moved as little as it can so it
	 * lands clear of an edge: wholly on one level, and not right up against the foot of a step (with STRIDE_FACE_ROOM
	 * before it). Picked once and kept (unless the stride changes so much it is twice STRIDE_PLACE_REACH from that) until
	 * STRIDE_PLACE_UNTIL through the swing, then held where it was.
	 */
	private static void landing(
		final RideState ride, final Level level, final int i, final float rel, final double soleX, final double soleY, final double soleZ, final double half,
		final double fx, final double fz, final float way, final float travel, final float through
	) {
		if (Float.isNaN(ride.footRelLand[i])) {
			return;
		}
		// Where the animation will put it down: as far on as its last touch-down was, the horse moving on meanwhile.
		final float left = (1.0F - through) * Math.max(ride.footSwingTime[i], 0.05F);
		final double ahead = (ride.footRelLand[i] - rel) + travel * left;
		final double px = soleX + fx * ahead;
		final double pz = soleZ + fz * ahead;
		if (!Double.isNaN(ride.footLandX[i])) {
			// (Kept once picked, unless the stride has changed so much it is well out of reach.)
			final double kept = (ride.footLandX[i] - px) * fx + (ride.footLandZ[i] - pz) * fz;
			if (through >= STRIDE_PLACE_UNTIL || Math.abs(kept) <= 2.0 * STRIDE_PLACE_REACH) {
				return;
			}
		}
		if (through >= STRIDE_PLACE_UNTIL) {
			return;
		}
		profile(level, px, pz, fx * way, fz * way, -STRIDE_PLACE_REACH - half - STRIDE_EDGE_MARGIN, STRIDE_PLACE_REACH + half + STRIDE_EDGE_MARGIN + STRIDE_FACE_ROOM,
			soleY);
		double best = 0.0;
		boolean found = false;
		for (int n = 0; n <= 2 * (int) (STRIDE_PLACE_REACH / STRIDE_PLACE_STEP); n++) {
			final double t = ((n & 1) == 0 ? 1 : -1) * ((n + 1) / 2) * STRIDE_PLACE_STEP;
			final double top = flatAround(t, half + STRIDE_EDGE_MARGIN);
			if (!Double.isNaN(top) && !faceAhead(t + half + STRIDE_EDGE_MARGIN, top)) {
				best = t;
				found = true;
				break;
			}
		}
		if (!found) {
			// Nowhere clear within reach: where the animation puts it.
			best = 0.0;
		}
		ride.footLandX[i] = px + fx * way * best;
		ride.footLandZ[i] = pz + fz * way * best;
	}

	/**
	 * Reads the ground along the level line through (x, z) along (dx, dz), from {@code from} to {@code to} blocks along,
	 * into stretches that end where the half-block grid is crossed (the ground only changes there), each with its top
	 * (a wall: positive infinity; nothing within 1.6 below {@code y}: 1.6 below).
	 */
	private static void profile(final Level level, final double x, final double z, final double dx, final double dz, final double from, final double to, final double y) {
		profileCount = 0;
		profileFrom = from;
		final double sx = x + dx * from;
		final double sz = z + dz * from;
		final double gx = sx * 2.0;
		final double gz = sz * 2.0;
		double tx = dx > 1.0E-6 ? (Math.floor(gx) + 1.0 - gx) / (2.0 * dx) : dx < -1.0E-6 ? (Math.ceil(gx) - 1.0 - gx) / (2.0 * dx) : Double.POSITIVE_INFINITY;
		double tz = dz > 1.0E-6 ? (Math.floor(gz) + 1.0 - gz) / (2.0 * dz) : dz < -1.0E-6 ? (Math.ceil(gz) - 1.0 - gz) / (2.0 * dz) : Double.POSITIVE_INFINITY;
		final double stepX = Math.abs(dx) > 1.0E-6 ? 0.5 / Math.abs(dx) : Double.POSITIVE_INFINITY;
		final double stepZ = Math.abs(dz) > 1.0E-6 ? 0.5 / Math.abs(dz) : Double.POSITIVE_INFINITY;
		double start = 0.0;
		final double length = to - from;
		while (profileCount < PROFILE_END.length) {
			final double end = Math.min(Math.min(tx, tz), length);
			final double middle = (start + end) * 0.5;
			final double top = RideController.surface(level, POS, sx + dx * middle, sz + dz * middle, y + STRIDE_PROFILE_UP, y - STRIDE_PROFILE_DOWN, false);
			PROFILE_END[profileCount] = from + end;
			PROFILE_TOP[profileCount] = Double.isNaN(top) ? Double.POSITIVE_INFINITY : top == Double.NEGATIVE_INFINITY ? y - STRIDE_PROFILE_DOWN : top;
			profileCount++;
			if (end >= length) {
				break;
			}
			start = end;
			if (tx <= tz) {
				tx += stepX;
			} else {
				tz += stepZ;
			}
		}
	}

	/** The top of the ground {@code t} blocks along the last profile (NaN outside it). */
	private static double topAt(final double t) {
		if (t < profileFrom) {
			return Double.NaN;
		}
		for (int k = 0; k < profileCount; k++) {
			if (t <= PROFILE_END[k]) {
				return PROFILE_TOP[k];
			}
		}
		return Double.NaN;
	}

	/** The top of the ground under all of {@code t} ± {@code reach} along the last profile if it is one level there, else NaN. */
	private static double flatAround(final double t, final double reach) {
		if (t - reach < profileFrom || t + reach > PROFILE_END[profileCount - 1]) {
			return Double.NaN;
		}
		double top = Double.NaN;
		double from = profileFrom;
		for (int k = 0; k < profileCount; k++) {
			if (PROFILE_END[k] > t - reach && from < t + reach) {
				if (Double.isInfinite(PROFILE_TOP[k]) || !Double.isNaN(top) && Math.abs(PROFILE_TOP[k] - top) > STRIDE_LEVEL) {
					return Double.NaN;
				}
				top = PROFILE_TOP[k];
			}
			from = PROFILE_END[k];
		}
		return top;
	}

	/** Whether the ground rises more than a step's worth above {@code top} within STRIDE_FACE_ROOM after {@code t} along the last profile. */
	private static boolean faceAhead(final double t, final double top) {
		double from = profileFrom;
		for (int k = 0; k < profileCount; k++) {
			if (PROFILE_END[k] > t && from < t + STRIDE_FACE_ROOM && PROFILE_TOP[k] > top + STRIDE_RISE) {
				return true;
			}
			from = PROFILE_END[k];
		}
		return false;
	}

	private static float smooth(final float t) {
		return t * t * (3.0F - 2.0F * t);
	}
}
