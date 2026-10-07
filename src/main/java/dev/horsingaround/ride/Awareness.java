package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.HorsingAround;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The horse has a say in where it goes. It looks ahead along its path for what would stop it (walls, trunks, ledges)
 * and what would hurt it (falls it won't take, lava, fire, cactus, berry bushes...), and the ride controller uses that
 * to go round obstacles when there is a way, slow for walls when there isn't, take slopes at a pace it can land
 * safely, refuse drops and hazards, and jump up ledges.
 *
 * <p>Built not to get in the way: at a walk the horse never steers itself and walks right up to walls; riding beside
 * walls and along edges reads clear; drops it can take, water landings and jumpable gaps are left to the rider.
 *
 * <p>Runs on the instance simulating the ride (the rider's client thread) only, so it shares scratch state.
 */
public final class Awareness {
	/** Extra blocks horses avoid, for data packs and other mods (vanilla hazards are built in). */
	public static final TagKey<Block> AVOIDS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "horse_avoids"));

	static final int CLEAR = 0;
	static final int WALL = 1;
	static final int DANGER = 2;

	/** Spacing of the look-ahead samples, blocks. */
	private static final float STEP = 0.5F;
	/** Half the body's width, from its collision box, and the side lines just inside it (so riding alongside a wall or an edge reads clear). */
	private static float half = 0.45F;
	private static float side = 0.3F;
	private static final double STEP_UP = RIDDEN_STEP_HEIGHT;
	private static final double BODY_HEIGHT = 1.6;
	/** How much longer the horse's body is than its (square) collision box, nose to tail. */
	private static final double BODY_LENGTH_EXTRA = 1.0;
	/** Thin shapes (doors, panes, fence posts) only count where they are, give or take this much. */
	private static final double SHAPE_PAD = 0.2;
	/** The edge guard arms when danger or a gap is this close. */
	private static final float GUARD_RANGE = 4.0F;
	/**
	 * Within this of an obstacle, with no way round at an angle, the horse looks for one by stepping aside along it,
	 * checking the step with lines this far ahead and behind its middle (inside the body, which slides along the face).
	 */
	private static final float DETOUR_CLOSE = 1.5F;
	private static final float SLIDE_FLANK = 0.2F;
	/** A drop between samples bigger than this can put a moving horse in the air. */
	private static final double EDGE = 0.4;

	// Column classes.
	private static final int GROUND = 0;
	private static final int BLOCKED = 1;
	private static final int DROP = 2;
	private static final int HAZARD = 3;

	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
	/** What the last probe found (CLEAR, WALL or DANGER), and the first jumpable gap it passed (MAX_VALUE if none). */
	private static int kind;
	private static float gapAt;
	/** The last probe's danger was a hazard with safe ground just beyond it. */
	private static boolean hazardJumpable;
	/** Ground height of the last column read as GROUND, and under the centre line where the last probe ended. */
	private static double columnGround;
	private static double endGround;
	/** The last column read as BLOCKED was the face of a ledge (topped within LEDGE_HEIGHT of the ground). */
	private static boolean columnLedge;
	/** The last probe's wall was a ledge across the centre line (and anything the flanks met was ledge too). */
	private static boolean wallLedge;
	/**
	 * Ground under the body along the last probe: PROFILE[i] at (i + 1) * STEP ahead (highest of the three lines that
	 * hold the horse up), NaN over a jumpable gap; profileOrigin at the horse.
	 */
	private static final double[] PROFILE = new double[(int) (LOOK_AHEAD_MAX / STEP) + 2];
	private static int profileLength;
	private static double profileOrigin;
	/** Whether the ground falls away past the end of the profile (it ended at a drop) rather than carrying on. */
	private static boolean profileFallsAway;
	/** Distance to the face of the last ledge found, blocks. */
	static float ledgeFace;
	/** Distance to the first step down along the last profile looked at for slopes (MAX_VALUE if none). */
	private static float firstEdge = Float.MAX_VALUE;

	private Awareness() {
	}

	private static void body(final AbstractHorse horse) {
		half = horse.getBbWidth() * 0.5F;
		side = Math.max(half - 0.01F, 0.2F);
		final AABB box = horse.getBoundingBox();
		inMinX = Mth.floor(box.minX);
		inMaxX = Mth.floor(box.maxX - 1.0E-7);
		inMinZ = Mth.floor(box.minZ);
		inMaxZ = Mth.floor(box.maxZ - 1.0E-7);
		inMinY = Mth.floor(box.minY);
		inMaxY = Mth.floor(box.maxY);
	}

	/** Block cells the body is in now: a hazard already there is behind it, not ahead (it can walk back out). */
	private static int inMinX;
	private static int inMaxX;
	private static int inMinZ;
	private static int inMaxZ;
	private static int inMinY;
	private static int inMaxY;

	private static boolean occupied(final int x, final int y, final int z) {
		return x >= inMinX && x <= inMaxX && z >= inMinZ && z <= inMaxZ && y >= inMinY && y <= inMaxY;
	}

	/**
	 * One ridden tick on the ground: finds a way round anything on the rider's path (when asked for a trot or more),
	 * and returns the fastest the horse is willing to go given what lies on the way it is heading.
	 */
	static float look(final AbstractHorse horse, final RideState s, final float targetYaw, final boolean forward) {
		body(horse);
		final double fall = acceptableFall(horse);
		final float blocksPerUnit = (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * TERMINAL_VELOCITY_FACTOR;
		final float range = Mth.clamp(Math.abs(s.speed) * blocksPerUnit * LOOK_AHEAD_TICKS + half + STEP, LOOK_AHEAD_MIN, LOOK_AHEAD_MAX);

		// Ways round: when the rider asks for a trot or more, never at a walk, so a walk is fully in their hands. Kept
		// up while the horse slows for the obstacle, so it goes round instead of walking into it.
		if (!forward || s.gait < TROT) {
			s.avoidTarget = 0.0F;
			s.ledgeOnLine = false;
		} else if (--s.avoidReplan <= 0) {
			s.avoidTarget = detour(horse, s, targetYaw, range, fall);
			// Nothing in the way, a way round, or a ledge to jump: look again soon. Blocked with no way round: no need
			// every tick.
			s.avoidReplan = s.avoidTarget != 0.0F || kind == CLEAR || s.ledgeOnLine ? AVOID_REPLAN_TICKS : AVOID_REPLAN_TICKS * 3;
		}
		s.avoidOffset += Mth.clamp(s.avoidTarget - s.avoidOffset, -AVOID_RATE, AVOID_RATE);

		// Speed: judged on the way the horse is going (while it goes round something, on that way, braking for its
		// current heading only if the obstacle is upon it).
		final boolean detouring = s.avoidTarget != 0.0F;
		float limit = limit(horse, s, detouring ? targetYaw + s.avoidTarget : horse.getYRot(), range, fall, blocksPerUnit, true);
		if (s.avoidOffset != 0.0F) {
			// Turned well off the rider's line, it keeps a pace it can come back round from without swinging wide.
			limit = Math.min(limit, Math.max(turnableSpeed(Math.abs(s.avoidOffset), DETOUR_RETURN_ROOM) / blocksPerUnit, GAIT_SPEED[TROT]));
		}
		if (detouring) {
			// It must be able to swing round before it reaches what is in front of it now: at speed its turns are wide,
			// so it slows as much as it takes to make the turn in the room it has.
			final float ahead = probe(horse, horse.getX(), horse.getY(), horse.getZ(), horse.getYRot(), range, fall, side);
			if (kind != CLEAR) {
				final float turn = Math.abs(Mth.wrapDegrees(targetYaw + s.avoidTarget - horse.getYRot()));
				final float turnable = turnableSpeed(turn, ahead - STEP - half - STOP_MARGIN) / blocksPerUnit;
				limit = Math.min(limit, kind == WALL ? Math.max(turnable, GAIT_SPEED[WALK]) : turnable);
				if (kind == DANGER) {
					s.dangerAhead = Math.min(s.dangerAhead, ahead);
				} else {
					s.wallAhead = Math.min(s.wallAhead, ahead);
				}
			}
		}
		return limit;
	}

	/**
	 * Fastest speed (blocks/tick) at which the horse can come round {@code turn} degrees within {@code room} blocks: it
	 * takes TURN_LAG_TICKS to shift its weight, then turns at its grip-limited rate (on average TURN_EFFICIENCY of it).
	 */
	private static float turnableSpeed(final float turn, final float room) {
		if (room <= 0.0F) {
			return 0.0F;
		}
		float lo = 0.0F;
		float hi = 1.0F;
		for (int i = 0; i < 8; i++) {
			final float v = (lo + hi) * 0.5F;
			final float ticks = TURN_LAG_TICKS + turn / (TURN_EFFICIENCY * RideController.maxTurnRate(v * 20.0F));
			if (v * ticks <= room) {
				lo = v;
			} else {
				hi = v;
			}
		}
		return lo;
	}

	/** Not looking this tick (walking off, swimming, jumping, climbing): let any detour ease out. */
	static void rest(final RideState s) {
		s.avoidTarget = 0.0F;
		s.ledgeOnLine = false;
		s.avoidOffset += Mth.clamp(-s.avoidOffset, -AVOID_RATE, AVOID_RATE);
		s.dangerAhead = Float.MAX_VALUE;
		s.gapAhead = Float.MAX_VALUE;
		s.wallAhead = Float.MAX_VALUE;
	}

	/**
	 * The straightest way round what blocks the rider's line, found the way a rider would: look along the obstacle,
	 * either side, for the least the horse has to move over for the rider's line to be clear past it, and steer a
	 * straight line for that point just past the obstacle (DETOUR_PAST beyond its near face), grazing it with
	 * DETOUR_MARGIN to spare either side of the body, the rider's line from there clear for DETOUR_CLEARANCE more; and
	 * better, a lane clear on through anything else in a row behind it (DETOUR_LANE or to the end of the look-ahead), if
	 * one is in reach. In DETOUR_STEP steps first, then halving down to DETOUR_RESOLUTION. Aiming past the obstacle rather than at its near
	 * corner keeps the angle small, so a fast horse doesn't swerve late and swing wide coming back. Replanned every few
	 * ticks from where the horse is. Prefers the side it is already going round on, and keeps going round until the
	 * rider's line is clear with room to spare. Zero when the line is clear, when it meets a 2-block ledge the horse
	 * will jump ({@code s.ledgeOnLine}), or when there is no way round in reach (then the horse slows instead).
	 */
	private static float detour(final AbstractHorse horse, final RideState s, final float targetYaw, final float range, final double fall) {
		final boolean going = s.avoidTarget != 0.0F;
		final float flank = side + DETOUR_MARGIN;
		final float blocked = probe(horse, horse.getX(), horse.getY(), horse.getZ(), targetYaw, range, fall, going ? flank : side);
		s.ledgeOnLine = false;
		if (kind == CLEAR) {
			return 0.0F;
		}
		if (kind == WALL && wallLedge && LEDGE_CLIMB && jumpableLedge(horse, targetYaw, blocked)) {
			// Ridden straight at a ledge it can jump up: it jumps it rather than going round.
			s.ledgeOnLine = true;
			return 0.0F;
		}
		// The obstacle's near face is within the last sample step. Aim just past it, on a straight line that grazes its
		// near corner; where that line meets something else (a crowded forest), dogleg instead: out to beside the near
		// face, then along the rider's line past the obstacle.
		final float face = blocked - STEP;
		final float far = face + DETOUR_PAST;
		final float near = Math.max(face - half, STEP);
		// Past the obstacle the rider's line must be clear DETOUR_CLEARANCE on (from either aim)...
		final float pastFar = DETOUR_CLEARANCE + half;
		final float pastNear = far - near + pastFar;
		// ...and better, a lane: clear on through whatever else is in a row behind it, so going round one thing doesn't
		// lead into the next. Taken if it is no more than DETOUR_LANE_EXTRA further over than the nearest way past.
		final float lane = Math.max(range - far, DETOUR_LANE);
		final float preferred = s.avoidOffset != 0.0F ? Math.signum(s.avoidOffset) : s.turnIntent < 0.0F ? -1.0F : 1.0F;
		float pastOnly = -1.0F;
		float pastOnlySign = 0.0F;
		float pastOnlyAhead = 0.0F;
		float pastOnlyLength = 0.0F;
		for (float across = DETOUR_STEP; across <= DETOUR_REACH + 1.0E-3F; across += DETOUR_STEP) {
			if (pastOnly >= 0.0F && across > pastOnly + DETOUR_LANE_EXTRA) {
				break;
			}
			for (int i = 0; i < 2; i++) {
				final float sign = i == 0 ? preferred : -preferred;
				float ahead = far;
				float past = pastFar;
				if (!wayPast(horse, targetYaw, far, across, sign, fall, flank, pastFar)) {
					ahead = near;
					past = pastNear;
					if (!wayPast(horse, targetYaw, near, across, sign, fall, flank, pastNear)) {
						continue;
					}
				}
				final float laneLength = past - pastFar + lane;
				if (wayPast(horse, targetYaw, ahead, across, sign, fall, flank, laneLength)) {
					return detourAngle(horse, s, targetYaw, sign, ahead, face, least(horse, targetYaw, ahead, across, sign, fall, flank, laneLength));
				}
				if (pastOnly < 0.0F) {
					pastOnly = across;
					pastOnlySign = sign;
					pastOnlyAhead = ahead;
					pastOnlyLength = past;
				}
			}
		}
		if (pastOnly >= 0.0F) {
			return detourAngle(horse, s, targetYaw, pastOnlySign, pastOnlyAhead, face,
				least(horse, targetYaw, pastOnlyAhead, pastOnly, pastOnlySign, fall, flank, pastOnlyLength));
		}
		if (blocked <= DETOUR_CLOSE) {
			// Nose to it, any line out at an angle clips it: step aside along it, if the rider's line is clear past it from
			// there, and turn hard that way (the body slides along the face as it comes round).
			final float rad = targetYaw * Mth.DEG_TO_RAD;
			final double fx = -Mth.sin(rad);
			final double fz = Mth.cos(rad);
			for (float across = DETOUR_STEP; across <= DETOUR_REACH * 0.5F + 1.0E-3F; across += DETOUR_STEP) {
				for (int i = 0; i < 2; i++) {
					final float sign = i == 0 ? preferred : -preferred;
					probe(horse, horse.getX(), horse.getY(), horse.getZ(), targetYaw + 90.0F * sign, across, fall, SLIDE_FLANK);
					if (kind != CLEAR) {
						continue;
					}
					probe(horse, horse.getX() - fz * across * sign, endGround, horse.getZ() + fx * across * sign, targetYaw, far + pastFar, fall, side);
					if (kind == CLEAR) {
						return AVOID_MAX_ANGLE * sign;
					}
				}
			}
		}
		kind = WALL;
		return 0.0F;
	}

	/**
	 * The least the horse has to move over (to within DETOUR_RESOLUTION) for the way past to be clear, knowing it is
	 * clear {@code across} over and not a step less.
	 */
	private static float least(
		final AbstractHorse horse, final float targetYaw, final float ahead, final float across, final float sign, final double fall, final float flank,
		final float past
	) {
		float clear = across;
		float stuck = across - DETOUR_STEP;
		while (clear - stuck > DETOUR_RESOLUTION) {
			final float mid = (clear + stuck) * 0.5F;
			if (wayPast(horse, targetYaw, ahead, mid, sign, fall, flank, past)) {
				clear = mid;
			} else {
				stuck = mid;
			}
		}
		return clear;
	}

	/**
	 * How far off the rider's line (degrees, toward {@code sign}) to head for the way past {@code clear} over at
	 * {@code ahead}: the least angle that gets the horse as far over as that line is at the obstacle's near face
	 * ({@code face} along the line) by the time its chest gets there, given how it turns (it lags the heading it is
	 * asked for, and carries on round for a while after). A horse already swinging round far enough needs none: it
	 * heads back for the rider's line now (its momentum still carries it past the corner, with room to spare by the far
	 * side), so it doesn't swing wide. If no angle gets it there in time, the straight line.
	 */
	private static float detourAngle(
		final AbstractHorse horse, final RideState s, final float targetYaw, final float sign, final float ahead, final float face, final float clear
	) {
		final float across = ahead > face ? clear * face / ahead : clear;
		final float distance = Math.max(face - half, STEP);
		if (RideController.sideAfter(horse, s, targetYaw, 0.0F, distance) * sign >= across - DETOUR_MARGIN
			&& RideController.sideAfter(horse, s, targetYaw, 0.0F, distance + DETOUR_PAST) * sign >= across) {
			return 0.0F;
		}
		float lo = 0.0F;
		float hi = AVOID_MAX_ANGLE;
		if (RideController.sideAfter(horse, s, targetYaw, hi * sign, distance) * sign < across) {
			return (float) Math.toDegrees(Math.atan2(clear, ahead)) * sign;
		}
		for (int i = 0; i < 7; i++) {
			final float mid = (lo + hi) * 0.5F;
			if (RideController.sideAfter(horse, s, targetYaw, mid * sign, distance) * sign >= across) {
				hi = mid;
			} else {
				lo = mid;
			}
		}
		return hi * sign;
	}

	/**
	 * Whether the horse can get to the point {@code across} to the {@code sign} side of the rider's line, {@code ahead}
	 * along it, and carry on from there along the rider's line past the obstacle, all clear with {@code flank} either side.
	 */
	private static boolean wayPast(
		final AbstractHorse horse, final float targetYaw, final float ahead, final float across, final float sign, final double fall, final float flank,
		final float past
	) {
		final float angle = (float) Math.toDegrees(Math.atan2(across, ahead));
		if (angle > AVOID_MAX_ANGLE) {
			return false;
		}
		// The way out to the side...
		probe(horse, horse.getX(), horse.getY(), horse.getZ(), targetYaw + angle * sign, Mth.sqrt(across * across + ahead * ahead), fall, flank);
		if (kind != CLEAR) {
			return false;
		}
		// ...and from there, past the obstacle along the rider's line. (Turning to a larger yaw heads toward (-fz, fx).)
		final float rad = targetYaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(rad);
		final double fz = Mth.cos(rad);
		final double x = horse.getX() + fx * ahead - fz * across * sign;
		final double z = horse.getZ() + fz * ahead + fx * across * sign;
		probe(horse, x, endGround, z, targetYaw, past, fall, flank);
		return kind == CLEAR;
	}

	/**
	 * Whether the wall the last probe met {@code blocked} along yaw (with {@link #endGround} under the centre line just
	 * short of it) is a ledge the horse will jump up when it gets there: the full ledge check, from a body placed just
	 * short of it.
	 */
	private static boolean jumpableLedge(final AbstractHorse horse, final float yaw, final float blocked) {
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double start = Math.max(blocked - STEP - half - 1.0F, 0.0F);
		final AABB box = horse.getBoundingBox().move(-Mth.sin(rad) * start, endGround - horse.getY(), Mth.cos(rad) * start);
		return !Double.isNaN(ledge(horse, box, yaw, 2.0F));
	}

	/** Fastest speed (multiple of the speed attribute) that still stops short of, or lands safely past, what lies along yaw. */
	private static float limit(
		final AbstractHorse horse, final RideState s, final float yaw, final float range, final double fall, final float blocksPerUnit, final boolean slopes
	) {
		final float ahead = probe(horse, horse.getX(), horse.getY(), horse.getZ(), yaw, range, fall, side);
		s.dangerAhead = kind == DANGER ? ahead : Float.MAX_VALUE;
		s.dangerJumpable = kind == DANGER && hazardJumpable;
		s.wallAhead = kind == WALL ? ahead : Float.MAX_VALUE;
		s.gapAhead = gapAt;
		final float decel = DECEL_BRAKE * blocksPerUnit * BRAKE_PLAN;
		firstEdge = Float.MAX_VALUE;
		final float limit = slopes ? descent(horse, s, Math.min(fall, harmlessFall(horse)), decel, blocksPerUnit) : Float.MAX_VALUE;
		if (kind == CLEAR) {
			return limit;
		}
		if (kind == WALL && wallLedge && s.ledgeOnLine && Math.abs(Mth.wrapDegrees(yaw - s.riderYaw)) < LEDGE_LINE_ANGLE) {
			// The ledge on the rider's line it will jump: down to a trot by the time it is in reach, then it jumps.
			return Math.min(limit, brakeTo(ahead - STEP - half - LEDGE_REACH, GAIT_SPEED[TROT] * blocksPerUnit, decel) / blocksPerUnit);
		}
		// The obstacle starts somewhere in the last sample step; keep the body clear of it. Past a step down the horse
		// may be in the air, so slowing for what lies beyond has to be done by then.
		final float stop = brakeTo(Math.min(ahead - STEP - half - STOP_MARGIN, firstEdge), 0.0F, decel) / blocksPerUnit;
		if (kind == WALL) {
			return Math.min(limit, Math.max(stop, GAIT_SPEED[WALK]));
		}
		if (s.speed >= GAIT_SPEED[TROT] && stop < s.speed) {
			// It has seen the drop or the fire and starts to pull up.
			refuse(horse, s);
		}
		return Math.min(limit, stop);
	}

	/**
	 * Slopes: at speed a horse going over a step down sails on before it lands, and down a steep mountainside that can
	 * add up to a long fall. For each step down on the profile ahead, finds the fastest the horse can go over it and
	 * still land without getting hurt ({@code fall}; or the step's own height, if that's more and the horse takes it),
	 * and returns the speed that brakes to that in time. A slope is taken at a pace, never at a cost. Quiet: no snort.
	 */
	private static float descent(final AbstractHorse horse, final RideState s, final double fall, final float decel, final float blocksPerUnit) {
		final double gravity = horse.getGravity();
		final float speed = Math.max(s.speed, 0.0F) * blocksPerUnit;
		float limit = Float.MAX_VALUE;
		double previous = profileOrigin;
		// It can only brake with its hooves down: past the first step it may be in the air, so every step down ahead
		// has to be made safe by the time it reaches the first one.
		for (int i = 0; i < profileLength; i++) {
			final double next = PROFILE[i];
			if (!Double.isNaN(previous) && !Double.isNaN(next) && previous - next > EDGE) {
				final float edge = i * STEP + STEP * 0.5F;
				firstEdge = Math.min(firstEdge, edge);
				final double allowed = Math.max(fall - FALL_MARGIN, previous - next);
				if (flight(edge + half, previous, speed, 0.0, gravity) > allowed) {
					float safe = 0.0F;
					float unsafe = speed;
					for (int k = 0; k < 6; k++) {
						final float mid = (safe + unsafe) * 0.5F;
						if (flight(edge + half, previous, mid, 0.0, gravity) > allowed) {
							unsafe = mid;
						} else {
							safe = mid;
						}
					}
					limit = Math.min(limit, brakeTo(Math.min(edge, firstEdge), safe, decel) / blocksPerUnit);
				}
			}
			previous = next;
		}
		return limit;
	}

	/**
	 * How far the horse falls (from the top of its flight to where its body lands on the profile) when its centre is
	 * at {@code start} ahead, at height {@code top}, moving {@code speed} blocks/tick forward and {@code vy0} up.
	 */
	private static double flight(final double start, final double top, final float speed, final double vy0, final double gravity) {
		double x = start;
		double y = top;
		double vy = vy0;
		double peak = top;
		for (int tick = 0; tick < 80; tick++) {
			x += speed;
			y += vy;
			vy = (vy - gravity) * 0.98;
			peak = Math.max(peak, y);
			final double ground = groundUnder(x);
			if (y <= ground) {
				return peak - ground;
			}
		}
		return peak - y;
	}

	/** Highest ground under a body centred at {@code x} along the profile. */
	private static double groundUnder(final double x) {
		// A little narrower than the body: the profile's samples are coarse, so don't count on ground at its very edge.
		final double reach = Math.max(half - 0.25, 0.1);
		final double end = profileLength * STEP;
		if (x - reach > end) {
			return profileFallsAway || profileLength == 0 ? Double.NEGATIVE_INFINITY : PROFILE[profileLength - 1];
		}
		double ground = x - reach <= 0.0 ? profileOrigin : Double.NEGATIVE_INFINITY;
		final int first = Math.max(Mth.ceil((x - reach) / STEP) - 1, 0);
		final int last = Math.min(Mth.floor((x + reach) / STEP) - 1, profileLength - 1);
		for (int i = first; i <= last; i++) {
			if (!Double.isNaN(PROFILE[i])) {
				ground = Math.max(ground, PROFILE[i]);
			}
		}
		return ground;
	}

	/** Speed (blocks/tick) from which braking at {@code decel} (with the body's lag) gets down to {@code end} within {@code room} blocks. */
	private static float brakeTo(final float room, final float end, final float decel) {
		if (room <= 0.0F) {
			return end;
		}
		return decel * (-BRAKE_LAG_TICKS + (float) Math.sqrt(BRAKE_LAG_TICKS * BRAKE_LAG_TICKS + 2.0F * room / decel + end * end / (decel * decel)));
	}

	/**
	 * Walks three lines (centre and both flanks, {@code flank} either side) along yaw from (x0, y0, z0), following the
	 * ground. Returns the distance to the first sample where something would stop the horse ({@code kind} WALL: too
	 * high to step, no headroom) or hurt it ({@code kind} DANGER: a hazard under any line, or a fall it won't take under
	 * all three, so running along an edge is fine), or {@code range} when clear. Gaps with safe ground beyond within
	 * GAP_REACH are jumpable: passed over and noted in {@code gapAt}. Records the ground profile.
	 */
	private static float probe(
		final AbstractHorse horse, final double x0, final double y0, final double z0, final float yaw, final float range, final double fall, final float flank
	) {
		final Level level = horse.level();
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(rad);
		final double fz = Mth.cos(rad);
		// The collision box doesn't turn with the horse: across a diagonal heading it reaches up to 1.41x as far out.
		final double spread = flank * (Math.abs(fx) + Math.abs(fz));
		final double sx = fz * spread;
		final double sz = -fx * spread;
		double centre = y0;
		double left = y0;
		double right = y0;
		gapAt = Float.MAX_VALUE;
		profileOrigin = y0;
		profileLength = 0;
		profileFallsAway = false;
		for (float d = STEP; d <= range; d += STEP) {
			final double x = x0 + fx * d;
			final double z = z0 + fz * d;
			final int c = column(level, x, z, centre, fall);
			final double cGround = columnGround;
			final boolean cLedge = columnLedge;
			final int l = column(level, x - sx, z - sz, left, fall);
			final double lGround = columnGround;
			final boolean lLedge = columnLedge;
			final int r = column(level, x + sx, z + sz, right, fall);
			final double rGround = columnGround;
			if (c == BLOCKED || l == BLOCKED || r == BLOCKED) {
				kind = WALL;
				wallLedge = c == BLOCKED && cLedge && (l != BLOCKED || lLedge) && (r != BLOCKED || columnLedge);
				endGround = centre;
				return d;
			}
			if (c == HAZARD || l == HAZARD || r == HAZARD) {
				// Something to go round or stop for; the rider may still jump it if there is ground just beyond.
				hazardJumpable = landing(level, x0, z0, fx, fz, d, centre, fall) >= 0.0F;
				kind = DANGER;
				endGround = centre;
				return d;
			}
			if (c == DROP && l == DROP && r == DROP) {
				final float landing = landing(level, x0, z0, fx, fz, d, centre, fall);
				hazardJumpable = false;
				if (landing < 0.0F) {
					kind = DANGER;
					endGround = centre;
					profileFallsAway = true;
					return d;
				}
				if (gapAt == Float.MAX_VALUE) {
					gapAt = d;
				}
				centre = left = right = columnGround;
				while (profileLength < PROFILE.length && (profileLength + 1) * STEP < landing - 0.01F) {
					PROFILE[profileLength++] = Double.NaN;
				}
				record(centre);
				d = landing;
				continue;
			}
			// A line over a drop while the others hold the horse up keeps the edge height.
			double support = Double.NEGATIVE_INFINITY;
			if (c == GROUND) {
				centre = cGround;
				support = cGround;
			}
			if (l == GROUND) {
				left = lGround;
				support = Math.max(support, lGround);
			}
			if (r == GROUND) {
				right = rGround;
				support = Math.max(support, rGround);
			}
			record(support);
		}
		kind = CLEAR;
		endGround = centre;
		return range;
	}

	private static void record(final double ground) {
		if (profileLength < PROFILE.length) {
			PROFILE[profileLength++] = ground;
		}
	}

	/** Where safe ground resumes at about the edge's height within GAP_REACH past {@code from}, or -1. */
	private static float landing(
		final Level level, final double x0, final double z0, final double fx, final double fz, final float from, final double edge, final double fall
	) {
		for (float d = from + STEP; d <= from + GAP_REACH; d += STEP) {
			if (column(level, x0 + fx * d, z0 + fz * d, edge, fall) == GROUND && columnGround >= edge - 1.5) {
				return d;
			}
		}
		return -1.0F;
	}

	/**
	 * Reads one block column for a body arriving at ground height {@code ground}: GROUND (a surface within a step up,
	 * or a fall it will take down, with headroom; sets {@link #columnGround}), BLOCKED (nothing the body fits on), DROP
	 * (a longer fall) or HAZARD. Water anywhere below breaks a fall. Scans down from head height after a step up.
	 */
	private static int column(final Level level, final double x, final double z, final double ground, final double fall) {
		final int bx = Mth.floor(x);
		final int bz = Mth.floor(z);
		final double inX = x - bx;
		final double inZ = z - bz;
		final int top = Mth.floor(ground + STEP_UP + BODY_HEIGHT);
		final int bottom = Mth.floor(ground - fall) - 1;
		final double bodyTop = ground + BODY_HEIGHT;
		double ceiling = Double.MAX_VALUE;
		columnLedge = false;
		for (int by = top; by >= bottom; by--) {
			final BlockState state = level.getBlockState(POS.set(bx, by, bz));
			if (state.isAir()) {
				continue;
			}
			final FluidState fluid = state.getFluidState();
			if (!fluid.isEmpty() && fluid.is(FluidTags.LAVA)) {
				return HAZARD;
			}
			if (by <= bodyTop && isHazard(state) && !occupied(bx, by, bz)) {
				return HAZARD;
			}
			final VoxelShape shape = RIDE_THROUGH_LEAVES && state.is(BlockTags.LEAVES) ? Shapes.empty() : state.getCollisionShape(level, POS);
			if (shape.isEmpty() || shape != Shapes.block() && !covers(shape, inX, inZ)) {
				if (!fluid.isEmpty() && fluid.is(FluidTags.WATER)) {
					// Swimming is fine, and water breaks any fall.
					columnGround = Math.min(by + fluid.getOwnHeight(), ground);
					return GROUND;
				}
				continue;
			}
			final double surface = by + shape.max(Direction.Axis.Y);
			if (surface <= ground + STEP_UP + 0.01 && ceiling - surface >= BODY_HEIGHT) {
				if (ground - surface > fall) {
					return DROP;
				}
				columnGround = surface;
				return GROUND;
			}
			if (ceiling == Double.MAX_VALUE) {
				// The first thing met coming down: the top of whatever is in the way, a ledge if no more than 2 up.
				columnLedge = surface <= ground + LEDGE_HEIGHT + 0.01;
			}
			ceiling = by + shape.min(Direction.Axis.Y);
		}
		return ceiling == Double.MAX_VALUE ? DROP : BLOCKED;
	}

	private static boolean covers(final VoxelShape shape, final double inX, final double inZ) {
		return inX >= shape.min(Direction.Axis.X) - SHAPE_PAD && inX <= shape.max(Direction.Axis.X) + SHAPE_PAD
			&& inZ >= shape.min(Direction.Axis.Z) - SHAPE_PAD && inZ <= shape.max(Direction.Axis.Z) + SHAPE_PAD;
	}

	/** Blocks that hurt or trap a horse walking into or onto them (lava is checked as a fluid). */
	public static boolean isHazard(final BlockState state) {
		return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
			|| state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.COBWEB) || CampfireBlock.isLitCampfire(state)
			|| state.is(AVOIDS);
	}

	/**
	 * The longest fall the horse will take. Anything that doesn't hurt it (horses take half damage past 6 blocks, so up to
	 * about 8), and beyond that only while the hurt stays small: up to FALL_HURT_ALLOWANCE for the horse and
	 * RIDER_FALL_HURT_ALLOWANCE for its rider, who takes the fall with it (vanilla passes a hurting fall on to riders,
	 * at full damage from 3 blocks). The less health either has left, the more careful it is: the allowances shrink to
	 * nothing at CAUTIOUS_HEALTH.
	 */
	public static double acceptableFall(final AbstractHorse horse) {
		final double safe = horse.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		final double multiplier = Math.max(horse.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 1.0E-3);
		final double horseAllowance = allowance(horse);
		final LivingEntity rider = horse.getControllingPassenger();
		final double riderAllowance = rider == null ? 0.0 : allowance(rider) * RIDER_FALL_HURT_ALLOWANCE / FALL_HURT_ALLOWANCE;
		double fall = safe;
		for (int hurt = 0; hurt <= 20 && hurt <= horseAllowance; hurt++) {
			// The longest fall that hurts the horse this much (damage is rounded down).
			final double candidate = safe + (hurt + 1) / multiplier - 0.05;
			if (hurt > 0 && rider != null && riderHurt(rider, candidate) > riderAllowance) {
				break;
			}
			fall = candidate;
		}
		return fall;
	}

	/** The longest fall that doesn't hurt the horse at all (damage is rounded down), so a rider isn't hurt either. */
	static double harmlessFall(final AbstractHorse horse) {
		return horse.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) + 1.0 / Math.max(horse.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 1.0E-3) - 0.05;
	}

	private static double riderHurt(final LivingEntity rider, final double fall) {
		return Math.floor(Math.max(fall - rider.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), 0.0) * rider.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
	}

	/** Hurt (health points) a fall may cost: all of FALL_HURT_ALLOWANCE when healthy, shrinking to none when low. */
	private static double allowance(final LivingEntity entity) {
		final float health = entity.getHealth() / Math.max(entity.getMaxHealth(), 1.0F);
		return FALL_HURT_ALLOWANCE * Mth.clamp((health - CAUTIOUS_HEALTH) / (CONFIDENT_HEALTH - CAUTIOUS_HEALTH), 0.0F, 1.0F);
	}

	/**
	 * Last resort, after steering and speed are settled, on where this tick's travel (carried velocity, push and any
	 * side-step) would take the body: if that steps into a hazard it isn't already in (from any side, turning in a tight
	 * spot included), or off a drop it won't take (an edge reached too fast, the lip of a gap without a jump, backing
	 * up), it plants its feet. The hazard look is a handful of block reads; the drop look only runs when danger is close.
	 */
	static void guard(final AbstractHorse horse, final RideState s) {
		body(horse);
		s.guardChecks++;
		if (s.jumpBuffer > 0 || s.pendingJump > 0.0F || s.speed == 0.0F && horse.getDeltaMovement().horizontalDistanceSqr() < 1.0E-6) {
			return;
		}
		final float rad = s.heading * Mth.DEG_TO_RAD;
		final float sin = Mth.sin(rad);
		final float cos = Mth.cos(rad);
		// The ridden input: forward (or back), with the side-step to the left as positive x, normalised like vanilla.
		final float sideways = s.speed > 0.0F ? -s.sidestep() : 0.0F;
		final float length = Math.max(Mth.sqrt(sideways * sideways + 1.0F), 1.0F);
		final double push = horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * s.speed / length;
		final Vec3 movement = horse.getDeltaMovement();
		final double dx = movement.x + (sideways * cos - sin) * push;
		final double dz = movement.z + (cos + sideways * sin) * push;
		final boolean intoHazard = entersHazard(horse, dx, dz);
		final boolean overEdge = !intoHazard && horse.onGround() && (s.speed < 0.0F || s.dangerAhead <= GUARD_RANGE || s.gapAhead <= GUARD_RANGE)
			&& fallsOff(horse, dx, dz, acceptableFall(horse));
		if (!intoHazard && !overEdge) {
			return;
		}
		s.guardStops++;
		if (s.speed >= GAIT_SPEED[TROT]) {
			refuse(horse, s);
		}
		s.speed = 0.0F;
		s.sidestep = 0.0F;
		// Brushing past a hazard, it slides along it on whichever axis stays clear (as vanilla does at an edge).
		if (intoHazard && !entersHazard(horse, dx, 0.0)) {
			horse.setDeltaMovement(dx, movement.y, 0.0);
		} else if (intoHazard && !entersHazard(horse, 0.0, dz)) {
			horse.setDeltaMovement(0.0, movement.y, dz);
		} else {
			horse.setDeltaMovement(0.0, movement.y, 0.0);
		}
	}

	/** Whether the body moved by (dx, dz) would overlap a hazard block (or lava) it doesn't overlap now. */
	private static boolean entersHazard(final AbstractHorse horse, final double dx, final double dz) {
		final Level level = horse.level();
		final AABB box = horse.getBoundingBox();
		final int x0 = Mth.floor(box.minX + Math.min(dx, 0.0));
		final int x1 = Mth.floor(box.maxX + Math.max(dx, 0.0) - 1.0E-7);
		final int z0 = Mth.floor(box.minZ + Math.min(dz, 0.0));
		final int z1 = Mth.floor(box.maxZ + Math.max(dz, 0.0) - 1.0E-7);
		// From the layer under the hooves (stepping down into it) to the body.
		final int y0 = Mth.floor(box.minY - 1.0);
		final int y1 = Mth.floor(box.minY + 1.0);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				final boolean overNow = x >= Mth.floor(box.minX) && x <= Mth.floor(box.maxX - 1.0E-7) && z >= Mth.floor(box.minZ) && z <= Mth.floor(box.maxZ - 1.0E-7);
				final boolean overNext = x >= Mth.floor(box.minX + dx) && x <= Mth.floor(box.maxX + dx - 1.0E-7)
					&& z >= Mth.floor(box.minZ + dz) && z <= Mth.floor(box.maxZ + dz - 1.0E-7);
				if (!overNext) {
					continue;
				}
				for (int y = y0; y <= y1; y++) {
					// Already in this cell (not just above it): moving on in it is how it gets back out.
					if (overNow && y >= Mth.floor(box.minY)) {
						continue;
					}
					final BlockState state = level.getBlockState(POS.set(x, y, z));
					if (!state.isAir() && (isHazard(state) || state.getFluidState().is(FluidTags.LAVA))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Whether the body moved by (dx, dz) would fall further than {@code fall}, with no water below to land in. */
	private static boolean fallsOff(final AbstractHorse horse, final double dx, final double dz, final double fall) {
		final Level level = horse.level();
		final AABB box = horse.getBoundingBox();
		final AABB below = new AABB(
			box.minX + dx + 1.0E-7, box.minY - fall - 1.0E-7, box.minZ + dz + 1.0E-7, box.maxX + dx - 1.0E-7, box.minY, box.maxZ + dz - 1.0E-7
		);
		if (!level.noBlockCollision(horse, below)) {
			return false;
		}
		final int cx = Mth.floor((box.minX + box.maxX) * 0.5 + dx);
		final int cz = Mth.floor((box.minZ + box.maxZ) * 0.5 + dz);
		for (int y = Mth.floor(box.minY); y >= Mth.floor(box.minY - fall) - 1; y--) {
			final FluidState fluid = level.getFluidState(POS.set(cx, y, cz));
			if (!fluid.isEmpty()) {
				return !fluid.is(FluidTags.WATER);
			}
		}
		return true;
	}

	/**
	 * Cheap look for a ledge's face ahead: something solid at the second block up, within LEDGE_REACH of the chest. No
	 * allocation; {@link #ledge} then checks properly.
	 */
	static boolean ledgeAhead(final AbstractHorse horse) {
		body(horse);
		final Level level = horse.level();
		final float rad = horse.getYRot() * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(rad);
		final double fz = Mth.cos(rad);
		final int y = Mth.floor(horse.getY() + 1.5);
		for (float d = half + 0.25F; d <= half + LEDGE_REACH; d += 0.5F) {
			final BlockState state = level.getBlockState(POS.set(Mth.floor(horse.getX() + fx * d), y, Mth.floor(horse.getZ() + fz * d)));
			if (!state.isAir() && !(RIDE_THROUGH_LEAVES && state.is(BlockTags.LEAVES)) && !state.getCollisionShape(level, POS).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A ledge the horse could jump up, its face within {@code reach} of the chest straight ahead along yaw: too high to
	 * step, no higher than LEDGE_HEIGHT, with headroom for the jump, and a solid top under most of where it would land
	 * (not leaves it falls through, not a lone log in a bush). Never a fence, wall or gate (or anything taller than a
	 * block), so pens still hold horses. Returns the top of the ledge, or NaN; the face's distance goes in {@link #ledgeFace}.
	 */
	static double ledge(final AbstractHorse horse, final float yaw, final float reach) {
		return ledge(horse, horse.getBoundingBox(), yaw, reach);
	}

	/** {@link #ledge(AbstractHorse, float, float)} for the body at {@code box}. */
	private static double ledge(final AbstractHorse horse, final AABB box, final float yaw, final float reach) {
		final Level level = horse.level();
		final CollisionContext context = CollisionContext.of(horse);
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(rad);
		final double fz = Mth.cos(rad);
		float face = -1.0F;
		for (float m = 0.0F; m <= reach + 1.0E-3F; m += 0.25F) {
			if (!level.noBlockCollision(horse, box.move(fx * (m + 0.05), 0.0, fz * (m + 0.05)))) {
				face = m;
				break;
			}
		}
		if (face < 0.0F) {
			return Double.NaN;
		}
		final AABB at = box.move(fx * (face + 0.05), 0.0, fz * (face + 0.05));
		if (level.noBlockCollision(horse, at.move(0.0, STEP_UP + 0.05, 0.0))) {
			return Double.NaN;
		}
		double top = Double.NEGATIVE_INFINITY;
		final int x1 = Mth.floor(at.maxX - 1.0E-7);
		final int z1 = Mth.floor(at.maxZ - 1.0E-7);
		final int y0 = Mth.floor(box.minY + 0.5);
		final int y1 = Mth.floor(box.minY + LEDGE_HEIGHT - 0.01);
		for (int x = Mth.floor(at.minX); x <= x1; x++) {
			for (int z = Mth.floor(at.minZ); z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					final BlockState state = level.getBlockState(POS.set(x, y, z));
					if (state.isAir()) {
						continue;
					}
					if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCE_GATES)) {
						return Double.NaN;
					}
					final VoxelShape shape = state.getCollisionShape(level, POS, context);
					if (shape.isEmpty()) {
						continue;
					}
					final double height = shape.max(Direction.Axis.Y);
					if (height > 1.0) {
						return Double.NaN;
					}
					top = Math.max(top, y + height);
				}
			}
		}
		if (!(top > box.minY + STEP_UP)) {
			return Double.NaN;
		}
		// Room to jump and to land, and solid ground to land on.
		final AABB landing = box.move(fx * (face + 1.0), top - box.minY + 0.01, fz * (face + 1.0));
		// The body is about two blocks long whatever its collision box: it needs that much ledge to land on.
		if (!level.noBlockCollision(horse, landing) || !level.noBlockCollision(horse, box.expandTowards(0.0, top - box.minY + LEDGE_CLEARANCE + 0.05, 0.0))
			|| support(level, context, landing.expandTowards(fx * BODY_LENGTH_EXTRA, 0.0, fz * BODY_LENGTH_EXTRA), top) < LEDGE_SUPPORT) {
			return Double.NaN;
		}
		ledgeFace = face;
		return top;
	}

	/**
	 * Cheap look for a bank in front of a swimming horse: anything solid at its chest or below it, at the water's
	 * surface, within BANK_REACH ahead. No allocation; {@link #bank} then checks properly.
	 */
	static boolean bankAhead(final AbstractHorse horse) {
		body(horse);
		final Level level = horse.level();
		final float rad = horse.getYRot() * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(rad);
		final double fz = Mth.cos(rad);
		final int y0 = Mth.floor(horse.getY() + 0.5);
		final int y1 = Mth.floor(horse.getY() + 1.5);
		for (float d = half + 0.1F; d <= half + BANK_REACH + 1.0E-3F; d += 0.25F) {
			final int x = Mth.floor(horse.getX() + fx * d);
			final int z = Mth.floor(horse.getZ() + fz * d);
			for (int y = y0; y <= y1; y++) {
				final BlockState state = level.getBlockState(POS.set(x, y, z));
				if (!state.isAir() && !state.getCollisionShape(level, POS).isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A bank a swimming horse can heave itself out onto, its face within BANK_REACH of the chest along yaw: its top no
	 * more than BANK_MAX_ABOVE_WATER above the top of the water's block layer ({@code depth} is how far the water comes
	 * up the body), room to rise beside it and to stand on top, and solid ground on top under part of the body. Never a
	 * fence, wall or gate. Returns the top, or NaN.
	 */
	static double bank(final AbstractHorse horse, final float yaw, final double depth) {
		final Level level = horse.level();
		final CollisionContext context = CollisionContext.of(horse);
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(rad);
		final double fz = Mth.cos(rad);
		final AABB box = horse.getBoundingBox();
		float face = -1.0F;
		for (float m = 0.0F; m <= BANK_REACH + 1.0E-3F; m += 0.2F) {
			if (!level.noBlockCollision(horse, box.move(fx * (m + 0.05), 0.0, fz * (m + 0.05)))) {
				face = m;
				break;
			}
		}
		if (face < 0.0F) {
			return Double.NaN;
		}
		final double highest = Math.ceil(horse.getY() + depth - 1.0E-4) + BANK_MAX_ABOVE_WATER;
		final AABB at = box.move(fx * (face + 0.05), 0.0, fz * (face + 0.05));
		// The top of what the body runs into, looking a block above the highest it may climb so a taller bank shows.
		double top = Double.NEGATIVE_INFINITY;
		final int x1 = Mth.floor(at.maxX - 1.0E-7);
		final int z1 = Mth.floor(at.maxZ - 1.0E-7);
		final int y1 = Mth.floor(highest + 1.0);
		for (int x = Mth.floor(at.minX); x <= x1; x++) {
			for (int z = Mth.floor(at.minZ); z <= z1; z++) {
				for (int y = Mth.floor(box.minY); y <= y1; y++) {
					final BlockState state = level.getBlockState(POS.set(x, y, z));
					if (state.isAir()) {
						continue;
					}
					if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCE_GATES)) {
						return Double.NaN;
					}
					final VoxelShape shape = state.getCollisionShape(level, POS, context);
					if (!shape.isEmpty()) {
						top = Math.max(top, y + shape.max(Direction.Axis.Y));
					}
				}
			}
		}
		if (!(top > box.minY + 0.1) || top > highest + 0.01) {
			return Double.NaN;
		}
		final AABB landing = box.move(fx * (face + 0.6), top - box.minY + 0.01, fz * (face + 0.6));
		if (!level.noBlockCollision(horse, landing) || !level.noBlockCollision(horse, box.expandTowards(0.0, top - box.minY + BANK_CLEARANCE + 0.05, 0.0))
			|| support(level, context, landing, top) < 0.3) {
			return Double.NaN;
		}
		return top;
	}

	/** Share of the box's footprint over solid ground whose top is at {@code top}. */
	private static double support(final Level level, final CollisionContext context, final AABB box, final double top) {
		final int y = Mth.floor(top - 0.01);
		final int x1 = Mth.floor(box.maxX - 1.0E-7);
		final int z1 = Mth.floor(box.maxZ - 1.0E-7);
		double area = 0.0;
		for (int x = Mth.floor(box.minX); x <= x1; x++) {
			for (int z = Mth.floor(box.minZ); z <= z1; z++) {
				final BlockState state = level.getBlockState(POS.set(x, y, z));
				if (state.isAir()) {
					continue;
				}
				final VoxelShape shape = state.getCollisionShape(level, POS, context);
				if (!shape.isEmpty() && Math.abs(y + shape.max(Direction.Axis.Y) - top) < 0.05) {
					area += (Math.min(box.maxX, x + 1) - Math.max(box.minX, x)) * (Math.min(box.maxZ, z + 1) - Math.max(box.minZ, z));
				}
			}
		}
		return area / ((box.maxX - box.minX) * (box.maxZ - box.minZ));
	}

	/**
	 * A jump now would carry the horse off a drop or into a hazard with no safe landing in reach, or (down a slope)
	 * land it further down than it will fall.
	 */
	static boolean refusesJump(final AbstractHorse horse, final RideState s, final float power) {
		body(horse);
		if (s.dangerAhead <= GAP_REACH && !s.dangerJumpable) {
			return true;
		}
		final double fall = acceptableFall(horse);
		final float blocksPerUnit = (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * TERMINAL_VELOCITY_FACTOR;
		final float speed = Math.max(s.speed, 0.0F) * blocksPerUnit;
		probe(horse, horse.getX(), horse.getY(), horse.getZ(), horse.getYRot(), LOOK_AHEAD_MAX, fall, side);
		final float forward = speed > 0.0F ? speed + JUMP_FORWARD_BOOST * power : 0.0F;
		final double launch = Math.max(horse.getAttributeValue(Attributes.JUMP_STRENGTH) * power, JUMP_MIN_VELOCITY);
		return flight(0.0, horse.getY(), forward, launch, horse.getGravity()) > fall;
	}

	/** The horse plants its feet: a snort and a toss of the head, not too often. */
	static void refuse(final AbstractHorse horse, final RideState s) {
		if (horse.tickCount - s.lastRefusal < REFUSAL_COOLDOWN_TICKS) {
			return;
		}
		s.lastRefusal = horse.tickCount;
		s.refusals++;
		s.headShakeStart = horse.tickCount;
		final SoundEvent snort = ((RideStateHolder) horse).horsingaround$angrySound();
		if (snort != null && REFUSAL_VOLUME > 0.0F) {
			horse.level().playLocalSound(
				horse.getX(), horse.getEyeY(), horse.getZ(), snort, horse.getSoundSource(), REFUSAL_VOLUME, 0.95F + horse.getRandom().nextFloat() * 0.1F, false
			);
		}
	}
}
