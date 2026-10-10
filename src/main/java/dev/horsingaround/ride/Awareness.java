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
	/** Stepping aside along something it is nose to, the step is checked with lines this far ahead and behind its middle (inside the body, which slides along the face). */
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
	/** The heading the last ledge found is climbed along, toward where it lands (degrees). */
	static float ledgeClimbYaw;
	/** Test hook: while set, each climb looked at notes why each place to land was turned down in {@link #climbLog}. */
	public static boolean logClimbs;
	public static final StringBuilder climbLog = new StringBuilder();
	/** Why the last ledge looked at can't be jumped (for tests and the steering overlay); LEDGE_OK when it can. */
	public static int ledgeRejection;
	public static final int LEDGE_OK = 0;
	public static final int LEDGE_NO_FACE = 1;
	public static final int LEDGE_ONLY_A_STEP = 2;
	public static final int LEDGE_FENCE = 3;
	public static final int LEDGE_TOO_HIGH = 4;
	public static final int LEDGE_NO_ROOM_ON_TOP = 5;
	public static final int LEDGE_NO_HEADROOM = 6;
	public static final int LEDGE_NOTHING_TO_LAND_ON = 7;
	public static final String[] LEDGE_REASONS = {"ok", "no face", "only a step", "fence or taller than a block", "too high", "no room on top",
		"no headroom", "nothing to land on"};
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
	 * One ridden tick on the ground: plans a way through anything on the rider's line (when asked for a trot or more),
	 * and returns the fastest the horse is willing to go given what lies on the way it is going.
	 */
	static float look(final AbstractHorse horse, final RideState s, final float targetYaw, final boolean forward) {
		body(horse);
		final double fall = acceptableFall(horse);
		final float blocksPerUnit = (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * TERMINAL_VELOCITY_FACTOR;
		final float range = Mth.clamp(Math.abs(s.speed) * blocksPerUnit * LOOK_AHEAD_TICKS + half + STEP, LOOK_AHEAD_MIN, LOOK_AHEAD_MAX);
		followLine(horse, s, targetYaw);
		s.wayRoom -= Math.max(s.speed, 0.0F) * blocksPerUnit;

		// Ways through: when the rider asks for a trot or more, never at a walk, so a walk is fully in their hands.
		if (!forward || s.gait < TROT) {
			s.avoidTarget = 0.0F;
			s.ledgeOnLine = false;
			s.way = WAY_NONE;
			s.wayPoints = 0;
		} else if (--s.avoidReplan <= 0) {
			s.avoidTarget = plan(horse, s, targetYaw, range, fall, blocksPerUnit);
			s.avoidReplan = AVOID_REPLAN_TICKS;
		}
		s.avoidOffset += Mth.clamp(s.avoidTarget - s.avoidOffset, -AVOID_RATE, AVOID_RATE);

		// Speed. On a way planned through, the way is clear at the pace it was planned at: only slopes along where it is
		// heading still slow it (not the thing it is turning away from). Otherwise all of what lies where it is heading.
		final boolean planned = s.way == WAY_CLEAR || s.way == WAY_PARTIAL;
		float limit = limit(horse, s, horse.getYRot(), range, fall, blocksPerUnit, !planned);
		if (s.way == WAY_CLEAR) {
			limit = Math.min(limit, s.wayPace);
		} else if (s.way == WAY_PARTIAL) {
			// No way through in sight yet: on along the one that gets furthest, able to stop short of where it meets something.
			final float stop = brakeTo(s.wayRoom - PATH_CHECK - STOP_MARGIN, 0.0F, DECEL_BRAKE * blocksPerUnit * BRAKE_PLAN) / blocksPerUnit;
			limit = Math.min(limit, s.wayDanger ? stop : Math.max(stop, GAIT_SPEED[WALK]));
		}
		return limit;
	}

	/** Not looking this tick (walking off, swimming, jumping, climbing): let any detour ease out. */
	static void rest(final RideState s) {
		s.avoidTarget = 0.0F;
		s.ledgeOnLine = false;
		s.way = WAY_NONE;
		s.wayPoints = 0;
		s.lineOffset = 0.0F;
		s.lineLastX = Double.NaN;
		s.avoidOffset += Mth.clamp(-s.avoidOffset, -AVOID_RATE, AVOID_RATE);
		s.dangerAhead = Float.MAX_VALUE;
		s.gapAhead = Float.MAX_VALUE;
		s.wallAhead = Float.MAX_VALUE;
	}

	/**
	 * Keeps the rider's line while the horse goes round something: how far right of it the horse is (blocks), so that past
	 * the obstacle it comes back onto the line the rider was on rather than carrying on beside it. The line turns with the
	 * rider's view (about the point beside the horse); a rider who turns well away from it (LINE_FORGET_ANGLE, or A/D) has
	 * picked a new line, through where the horse is.
	 */
	private static void followLine(final AbstractHorse horse, final RideState s, final float targetYaw) {
		if (s.way != WAY_NONE && !Double.isNaN(s.lineLastX) && Math.abs(Mth.wrapDegrees(targetYaw - s.lineYaw)) <= LINE_FORGET_ANGLE) {
			final float rad = targetYaw * Mth.DEG_TO_RAD;
			// (Right of a line along yaw is (-cos, -sin) of the yaw.)
			s.lineOffset -= (float) ((horse.getX() - s.lineLastX) * Mth.cos(rad) + (horse.getZ() - s.lineLastZ) * Mth.sin(rad));
			if (Math.abs(s.lineOffset) > PATH_REACH) {
				s.lineOffset = 0.0F;
				s.lineYaw = targetYaw;
			}
		} else {
			s.lineOffset = 0.0F;
			s.lineYaw = targetYaw;
		}
		s.lineLastX = horse.getX();
		s.lineLastZ = horse.getZ();
	}

	// ---- Ways through: planning ----

	/** The way the horse is taking ({@link RideState#way}). */
	static final byte WAY_NONE = 0;
	/** A way through, clear as far as it looks, at {@link RideState#wayPace}. */
	static final byte WAY_CLEAR = 1;
	/** No way through in sight yet: on along the one that gets furthest, slowing to stop short of what it meets. */
	static final byte WAY_PARTIAL = 2;
	/** No way round in reach: on along the line, slowing for what is on it. */
	static final byte WAY_BLOCKED = 3;
	/** Nose to it with every way out at an angle clipping it: stepping aside along it. */
	static final byte WAY_ASIDE = 4;

	/** Block columns read while planning, kept for one plan in a window round the horse (no allocation). */
	private static final int CELLS = 56;
	private static final int[] CELL_STAMP = new int[CELLS * CELLS];
	private static final int[] CELL_REF = new int[CELLS * CELLS];
	private static final byte[] CELL_KIND = new byte[CELLS * CELLS];
	private static final double[] CELL_GROUND = new double[CELLS * CELLS];
	private static int cellX0;
	private static int cellZ0;
	private static int planStamp;
	private static double cellGround;
	/** The ground under the last footprint looked at, and whether something was within the margin round the body. */
	private static double footGround;
	private static boolean footTight;
	/** The plan's frame: the rider's line through (wayX, wayZ), beside the horse, along (wayFx, wayFz); right of it is (-wayFz, wayFx). */
	private static double wayX;
	private static double wayZ;
	private static float wayFx;
	private static float wayFz;
	/** The share of its speed the horse keeps each tick on the ground under it, and how it is moving now (along, across the line). */
	private static float wayGrip;
	private static float wayVelU;
	private static float wayVelV;
	/** The last way played out that met something: how far along the line it got, how far it went, and what it met (WALL or DANGER). */
	private static float rollReach;
	private static float rollRoom;
	private static int rollKind;
	private static int rollPoints;

	/**
	 * The way on, picked the way a rider picks a line through: when something is on the rider's line (or the horse is off
	 * it, coming back after going round something), the horse plays out candidate ways with its own steering (the weight
	 * shift, the grip-limited turn rate at the pace it is going), each turning off the line by one of PATH_ANGLES for one of
	 * PATH_HOLD_TICKS and then heading back for it, and takes the cheapest: the one that keeps the most of its pace
	 * (turning least hard), stays nearest the rider's line, ends back on it, and stays clear as far as it looks (a way that
	 * meets something sooner costs PATH_COST_SHORT for every block short, and must leave room to stop before it). The best
	 * is then fine-tuned (a little more and less angle, a shorter and a longer hold). Replanned every couple of ticks from
	 * where the horse is, so the way bends as it goes, round one thing and then the next. If the best way meets something,
	 * a slower pace with a way clear all along is taken instead when that costs less (PATH_COST_PACE for all of the pace).
	 * With no way that leaves room to stop, it slows on the line (a long wall). A 2-block ledge or a hurdle the rider rides
	 * straight at isn't gone round: it is theirs to jump. Returns the degrees off the rider's line to steer now.
	 */
	private static float plan(final AbstractHorse horse, final RideState s, final float targetYaw, final float range, final double fall, final float blocksPerUnit) {
		final boolean going = s.way != WAY_NONE;
		// (Looking as far along the line as it plans, so it starts round something big early enough to keep its pace.)
		final float horizon = Mth.clamp(range * PATH_AHEAD_FACTOR, PATH_AHEAD_MIN, PATH_AHEAD_MAX);
		final float blocked = probe(horse, horse.getX(), horse.getY(), horse.getZ(), targetYaw, horizon, fall, going ? side + PATH_MARGIN : side);
		s.ledgeOnLine = false;
		s.hurdleOnLine = false;
		if (kind == WALL && wallLedge && LEDGE_CLIMB && jumpableLedge(horse, targetYaw, blocked)) {
			// Ridden straight at a ledge it can jump up: it jumps it rather than going round.
			s.ledgeOnLine = true;
			s.way = WAY_NONE;
			s.wayPoints = 0;
			return 0.0F;
		}
		if (kind == WALL && wallLedge && jumpableHurdle(horse, targetYaw, blocked)) {
			// Ridden straight at a fence or a wall the rider can jump: it is theirs to jump, not to go round.
			s.hurdleOnLine = true;
			s.way = WAY_NONE;
			s.wayPoints = 0;
			return 0.0F;
		}
		final float heading = Mth.wrapDegrees(horse.getYRot() - targetYaw);
		if (kind == CLEAR && Math.abs(s.lineOffset) < LINE_SNAP && Math.abs(heading) < LINE_SNAP_ANGLE) {
			s.way = WAY_NONE;
			s.wayPoints = 0;
			return 0.0F;
		}
		final boolean lineBlocked = kind != CLEAR;
		final long started = RideController.profile ? System.nanoTime() : 0L;
		final float rad = targetYaw * Mth.DEG_TO_RAD;
		wayFx = -Mth.sin(rad);
		wayFz = Mth.cos(rad);
		wayX = horse.getX() + wayFz * s.lineOffset;
		wayZ = horse.getZ() - wayFx * s.lineOffset;
		cellX0 = Mth.floor(horse.getX()) - CELLS / 2;
		cellZ0 = Mth.floor(horse.getZ()) - CELLS / 2;
		planStamp++;
		// How the ground under it grips (as vanilla moves an entity), and how it is moving now, along and across the line.
		wayGrip = horse.level().getBlockState(horse.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.91F;
		final Vec3 moving = horse.getDeltaMovement();
		wayVelU = (float) (moving.x * wayFx + moving.z * wayFz);
		wayVelV = (float) (-moving.x * wayFz + moving.z * wayFx);
		final float unitSpeed = Math.max(s.speed, GAIT_SPEED[WALK]);
		final float speed = unitSpeed * blocksPerUnit;
		if (s.wayX == null) {
			s.wayX = new float[PATH_POINTS];
			s.wayZ = new float[PATH_POINTS];
		}
		if (!lineBlocked) {
			// Past what it went round, nothing on the line: straight back onto it, if that way is clear.
			rollout(horse, s, heading, 0.0F, 0, speed, unitSpeed, horizon, fall, Float.MAX_VALUE, s.wayX, s.wayZ);
			if (rollClear) {
				s.way = WAY_CLEAR;
				s.wayPace = Float.MAX_VALUE;
				s.wayPoints = rollPoints;
				planned(started);
				return back(s.lineOffset, speed);
			}
		}
		// Going round on one side already (or turning that way): that side first.
		final float preferred = s.avoidTarget != 0.0F ? Math.signum(s.avoidTarget) : s.turnIntent != 0.0F ? Math.signum(s.turnIntent) : s.lineOffset > 0.0F ? -1.0F : 1.0F;
		// A way that meets something must leave room to stop short of it.
		final float commit = stopping(speed, DECEL_BRAKE * blocksPerUnit * BRAKE_PLAN) + PATH_COMMIT;
		bestCost = Float.MAX_VALUE;
		furthest = -1.0F;
		search(horse, s, heading, unitSpeed, 1.0F, horizon, fall, blocksPerUnit, preferred, commit, false);
		final float straightReach = lineReach;
		if (bestCost == Float.MAX_VALUE || !bestClear && bestRoom < stopping(speed, DECEL_BRAKE * blocksPerUnit * BRAKE_PLAN) * PATH_SLOW_ROOM) {
			// No way at this pace, or the best meets something getting close: a slower pace clear all along, if that loses
			// less, or one with room to stop from the pace it is going. (Further off, it keeps its pace: there is time yet
			// to find a way on.)
			final float cost = bestCost;
			final float angle = bestAngle;
			final int hold = bestHold;
			final float room = bestRoom;
			final int what = bestKind;
			for (final float scale : PATH_SLOWER) {
				search(horse, s, heading, unitSpeed, scale, horizon, fall, blocksPerUnit, preferred, commit, false);
			}
			if (cost < Float.MAX_VALUE && (bestScale == 1.0F || !bestClear)) {
				bestCost = cost;
				bestAngle = angle;
				bestHold = hold;
				bestScale = 1.0F;
				bestRoom = room;
				bestKind = what;
				bestClear = false;
			}
		}
		planned(started);
		if (bestCost == Float.MAX_VALUE && furthest > straightReach + PATH_PARTIAL_GAIN) {
			// No way that leaves room to stop: along the one that gets furthest on, braking, rather than straight on into
			// what is there. (Not along a wall it can't get round: that gets no further on.)
			bestCost = 0.0F;
			bestAngle = furthestAngle;
			bestHold = furthestHold;
			bestScale = 1.0F;
			bestClear = false;
			bestRoom = furthestRoom;
			bestKind = furthestKind;
		}
		if (bestCost == Float.MAX_VALUE) {
			if (logPlans) {
				planLog.append(String.format(java.util.Locale.ROOT, "%n    z%.1f x%.2f off%.2f: NONE (furthest %.1f, line %.1f, horizon %.1f, blocked %.1f)", horse.getZ(), horse.getX(),
					s.lineOffset, furthest, straightReach, horizon, blocked));
			}
			s.wayPoints = 0;
			if (lineBlocked && blocked <= ASIDE_CLOSE) {
				final float aside = stepAside(horse, targetYaw, blocked, fall, preferred);
				if (aside != 0.0F) {
					s.way = WAY_ASIDE;
					return aside;
				}
			}
			s.way = WAY_BLOCKED;
			return 0.0F;
		}
		if (logPlans) {
			planLog.append(String.format(java.util.Locale.ROOT, "%n    z%.1f x%.2f off%.2f: %s a%.0f h%d pace%.2f cost%.1f room%.1f (furthest %.1f, line %.1f, horizon %.1f)", horse.getZ(),
				horse.getX(), s.lineOffset, bestClear ? "CLEAR" : "PARTIAL", bestAngle, bestHold, bestScale, bestCost, bestRoom, furthest, straightReach, horizon));
		}
		s.way = bestClear ? WAY_CLEAR : WAY_PARTIAL;
		s.wayPace = bestScale < 1.0F ? unitSpeed * bestScale : Float.MAX_VALUE;
		s.wayRoom = bestRoom;
		s.wayDanger = bestKind == DANGER;
		s.wayAngle = bestAngle;
		s.wayHold = bestHold;
		final float bestSpeed = unitSpeed * bestScale * blocksPerUnit;
		rollout(horse, s, heading, bestAngle, bestHold, bestSpeed, unitSpeed * bestScale, horizon, fall, Float.MAX_VALUE, s.wayX, s.wayZ);
		s.wayPoints = rollPoints;
		return bestHold > 0 ? bestAngle : back(s.lineOffset, bestSpeed);
	}

	/** Playing out the way already being taken. */
	private static boolean keeping;

	/** The best way found so far in this plan: its cost, how it steers, its pace, whether it is clear all along, and if not where it meets what. */
	private static float bestCost;
	private static float bestAngle;
	private static int bestHold;
	private static float bestScale;
	private static boolean bestClear;
	private static float bestRoom;
	private static int bestKind;
	/** At the full pace: the way that gets furthest along the line before it meets something, whatever it costs, and how far straight back for the line gets. */
	private static float furthest;
	private static float furthestAngle;
	private static int furthestHold;
	private static float furthestRoom;
	private static int furthestKind;
	private static float lineReach;

	/**
	 * Plays out the candidate ways at {@code scale} of the pace, the way taken last time first (a good bound to cut the
	 * rest short), then back for the line, then every angle and hold, then fine-tunes round the best; keeps the best in
	 * {@link #bestCost} and the rest. A way that meets something counts only with {@code commit} blocks of room before it,
	 * and not at all if {@code clearOnly}.
	 */
	private static void search(
		final AbstractHorse horse, final RideState s, final float heading, final float unitSpeed, final float scale, final float horizon, final double fall,
		final float blocksPerUnit, final float preferred, final float commit, final boolean clearOnly
	) {
		final float speed = unitSpeed * scale * blocksPerUnit;
		if (scale == 1.0F && (s.way == WAY_CLEAR || s.way == WAY_PARTIAL)) {
			// The way it is on, carried on from where it has got to (its hold counted down), kept unless another is clearly
			// better: switching ways mid-turn costs the turn it has started, so it doesn't dither between two.
			keeping = true;
			tryWay(horse, s, heading, s.wayAngle, Math.max(s.wayHold - AVOID_REPLAN_TICKS, 0), speed, unitSpeed, scale, horizon, fall, commit, clearOnly);
			keeping = false;
		}
		tryWay(horse, s, heading, 0.0F, 0, speed, unitSpeed, scale, horizon, fall, commit, clearOnly);
		for (final float a : PATH_ANGLES) {
			for (int side = 0; side < (a == 0.0F ? 1 : 2); side++) {
				final float angle = side == 0 ? a * preferred : -a * preferred;
				for (final int hold : PATH_HOLD_TICKS) {
					tryWay(horse, s, heading, angle, hold, speed, unitSpeed, scale, horizon, fall, commit, clearOnly);
				}
			}
		}
		if (bestCost < Float.MAX_VALUE && bestScale == scale && bestHold > 0) {
			final float angle = bestAngle;
			final int hold = bestHold;
			tryWay(horse, s, heading, angle * 0.8F, hold, speed, unitSpeed, scale, horizon, fall, commit, clearOnly);
			tryWay(horse, s, heading, angle * 1.2F, hold, speed, unitSpeed, scale, horizon, fall, commit, clearOnly);
			tryWay(horse, s, heading, angle, Math.max(Math.round(hold * 0.7F), 1), speed, unitSpeed, scale, horizon, fall, commit, clearOnly);
			tryWay(horse, s, heading, angle, Math.round(hold * 1.4F), speed, unitSpeed, scale, horizon, fall, commit, clearOnly);
		}
	}

	private static void tryWay(
		final AbstractHorse horse, final RideState s, final float heading, final float angle, final int hold, final float speed, final float unitSpeed,
		final float scale, final float horizon, final double fall, final float commit, final boolean clearOnly
	) {
		// (Changing sides costs more the further it has gone over to the side it is on.)
		final float extra = (1.0F - scale) * PATH_COST_PACE
			+ (s.avoidTarget * angle < 0.0F ? PATH_COST_SWITCH * Mth.clamp(Math.abs(s.lineOffset), 0.5F, 1.0F) : 0.0F) - (keeping ? PATH_COST_KEEP : 0.0F);
		// (At the full pace every way is played out to the end while none is good enough, to know which leaves the most room.)
		final boolean full = scale == 1.0F;
		float cost = rollout(horse, s, heading, angle, hold, speed, unitSpeed * scale, horizon, fall, full && bestCost == Float.MAX_VALUE ? Float.MAX_VALUE
			: bestCost - extra, null, null);
		if (cost == Float.MAX_VALUE) {
			return;
		}
		if (full) {
			if (hold == 0) {
				lineReach = rollReach;
			}
			if (rollReach > furthest) {
				furthest = rollReach;
				furthestAngle = angle;
				furthestHold = hold;
				furthestRoom = rollRoom;
				furthestKind = rollKind;
			}
		}
		if (!rollClear) {
			if (clearOnly || rollRoom < commit) {
				return;
			}
			// (Meeting something at all costs: it will have to go round it after all, the sooner the more.)
			cost += PATH_COST_MEET + (horizon - rollReach) * PATH_COST_SHORT;
		}
		if (cost + extra < bestCost) {
			bestCost = cost + extra;
			bestAngle = angle;
			bestHold = hold;
			bestScale = scale;
			bestClear = rollClear;
			bestRoom = rollRoom;
			bestKind = rollKind;
		}
	}

	/** Blocks it takes to stop from {@code speed} blocks/tick braking at {@code decel}, with the body's lag. */
	private static float stopping(final float speed, final float decel) {
		return speed * BRAKE_LAG_TICKS + speed * speed / (2.0F * decel);
	}

	/** Test hook: while set, each plan's choice is noted in {@link #planLog}. */
	public static boolean logPlans;
	public static final StringBuilder planLog = new StringBuilder();

	/** Test hook (while {@link RideController#profile} is set): plans made, and the time they took in all and at most (nanoseconds). */
	public static int plans;
	public static long planNanos;
	public static long planNanosMost;

	private static void planned(final long started) {
		if (RideController.profile) {
			final long took = System.nanoTime() - started;
			plans++;
			planNanos += took;
			planNanosMost = Math.max(planNanosMost, took);
		}
	}

	/** Degrees off the line to head back onto it from {@code offset} right of it, at {@code speed} blocks/tick: aiming for a point on it a little ahead. */
	private static float back(final float offset, final float speed) {
		return (float) Math.toDegrees(Math.atan2(-offset, Mth.clamp(speed * PATH_BACK_TICKS, PATH_BACK_MIN, PATH_BACK_MAX)));
	}

	/**
	 * Plays out one candidate way: steering {@code angle} degrees off the rider's line for {@code hold} ticks and then back
	 * for the line, with the horse's own steering (as the ride tick: the detour easing in, the weight shift, the
	 * grip-limited turn rate) at {@code speed} blocks/tick, from how it is turning now, the body checked against the world
	 * every PATH_CHECK blocks, until it is {@code horizon} along the line ({@link #rollClear}) or meets something (where:
	 * {@link #rollReach} along the line, {@link #rollRoom} along the way, {@link #rollKind} what). Returns its cost so far:
	 * how far off the line it is and how hard it turns along the way, how close it passes things, and how far off the line
	 * it ends up; MAX_VALUE as soon as that is more than {@code bound}. With {@code trailX}, records where the body goes
	 * ({@link #rollPoints}).
	 */
	private static float rollout(
		final AbstractHorse horse, final RideState s, final float heading, final float angle, final int hold, final float speed, final float unitSpeed,
		final float horizon, final double fall, final float bound, final float[] trailX, final float[] trailZ
	) {
		final Level level = horse.level();
		final float maxTurn = RideController.maxTurnRate(speed * 20.0F);
		final float accel = maxTurn * TURN_ACCEL;
		final float shift = Mth.lerp(gallopFraction(unitSpeed), WEIGHT_SHIFT_STILL, WEIGHT_SHIFT_GALLOP);
		final float lookBack = Mth.clamp(speed * PATH_BACK_TICKS, PATH_BACK_MIN, PATH_BACK_MAX);
		final float longest = horizon * PATH_LONGEST + 2.0F;
		// (Slower than a gallop, weight committed ahead of the turn pushes it a little sideways into it, as in the ride tick.)
		final float sidestep = SIDESTEP * (1.0F - Math.min(gallopFraction(unitSpeed) * 1.4F, 1.0F));
		// Its way follows its momentum as vanilla moves it: each tick a push where it faces, then the ground's grip takes off
		// part of the speed, so through a turn it drifts a little wide of where it faces (more on ice).
		final float keep = wayGrip;
		final float push = speed * (1.0F - keep);
		float velU = wayVelU;
		float velV = wayVelV;
		float h = heading;
		float avoid = s.avoidOffset;
		float intent = s.turnIntent;
		float velocity = s.yawVelocity;
		float u = 0.0F;
		float v = s.lineOffset;
		double ground = horse.getY();
		float travelled = 0.0F;
		float checked = 0.0F;
		float cost = 0.0F;
		rollPoints = 0;
		rollClear = false;
		for (int t = 0; travelled < longest && t < 400; t++) {
			final float command = t < hold ? angle : (float) Math.toDegrees(Math.atan2(-v, lookBack));
			avoid += Mth.clamp(command - avoid, -AVOID_RATE, AVOID_RATE);
			final float desired = Mth.clamp((avoid - h) * TURN_GAIN / maxTurn, -1.0F, 1.0F);
			intent += Mth.clamp(desired - intent, -shift, shift);
			velocity += Mth.clamp(maxTurn * intent * Math.abs(intent) - velocity, -accel, accel);
			h += velocity;
			final float hr = h * Mth.DEG_TO_RAD;
			final float aside = (intent - velocity / maxTurn) * sidestep;
			final float on = push * Mth.invSqrt(1.0F + aside * aside);
			final float cos = Mth.cos(hr);
			final float sin = Mth.sin(hr);
			velU += on * (cos - aside * sin);
			velV += on * (sin + aside * cos);
			u += velU;
			v += velV;
			travelled += Mth.sqrt(velU * velU + velV * velV);
			velU *= keep;
			velV *= keep;
			cost += speed * (Math.min(Math.abs(v), PATH_OFF_LINE_CAP) * PATH_COST_OFF_LINE + Math.abs(intent) * PATH_COST_TURN);
			if (Math.abs(v) > PATH_REACH || Math.abs(h) > 120.0F) {
				// Too far off the line, or turned back on itself: not a way on.
				return met(u, travelled, OUT, cost, v);
			}
			final boolean end = u >= horizon;
			if (travelled - checked >= PATH_CHECK || end) {
				checked = travelled;
				final double x = wayX + wayFx * u - wayFz * v;
				final double z = wayZ + wayFz * u + wayFx * v;
				final int at = footprint(level, x, z, ground, fall);
				if (at != CLEAR) {
					return met(u, travelled, at, cost, v);
				}
				ground = footGround;
				if (footTight) {
					cost += PATH_COST_TIGHT;
				}
				if (trailX != null && rollPoints < trailX.length) {
					trailX[rollPoints] = (float) x;
					trailZ[rollPoints] = (float) z;
					rollPoints++;
				}
			}
			if (cost > bound) {
				return Float.MAX_VALUE;
			}
			if (end) {
				rollClear = true;
				rollReach = u;
				rollRoom = travelled;
				return cost + Math.min(Math.abs(v), PATH_OFF_LINE_CAP) * PATH_COST_END + Math.abs(Mth.wrapDegrees(h)) * PATH_COST_END_TURN;
			}
		}
		return met(u, travelled, OUT, cost, v);
	}

	/** A way played out that ran out of reach or of way before getting far enough along the line (no better than a wall). */
	private static final int OUT = 3;

	/** Whether the last way played out stayed clear all along. */
	private static boolean rollClear;

	private static float met(final float reach, final float room, final int what, final float cost, final float v) {
		rollReach = reach;
		rollRoom = room;
		rollKind = what;
		return cost + Math.min(Math.abs(v), PATH_OFF_LINE_CAP) * PATH_COST_END;
	}

	/**
	 * The body at (x, z) arriving on ground at {@code ground}: WALL or DANGER if its box (PATH_CLEAR wider) would be in
	 * something, or over a fall it won't take (all of it: half over an edge it still stands), else CLEAR, with the ground
	 * it stands on in {@link #footGround} and whether anything is within PATH_MARGIN of it in {@link #footTight}.
	 */
	private static int footprint(final Level level, final double x, final double z, final double ground, final double fall) {
		final double body = half + PATH_CLEAR;
		final double margin = half + PATH_MARGIN;
		final int x1 = Mth.floor(x + margin);
		final int z1 = Mth.floor(z + margin);
		final int z0 = Mth.floor(z - margin);
		int cells = 0;
		int drops = 0;
		double top = Double.NEGATIVE_INFINITY;
		boolean tight = false;
		for (int bx = Mth.floor(x - margin); bx <= x1; bx++) {
			for (int bz = z0; bz <= z1; bz++) {
				final boolean in = bx + 1 > x - body && bx < x + body && bz + 1 > z - body && bz < z + body;
				final int c = cell(level, bx, bz, ground, fall);
				if (c == BLOCKED || c == HAZARD) {
					if (in) {
						return c == BLOCKED ? WALL : DANGER;
					}
					tight = true;
				} else if (in) {
					cells++;
					if (c == DROP) {
						drops++;
					} else {
						top = Math.max(top, cellGround);
					}
				}
			}
		}
		if (drops == cells) {
			return DANGER;
		}
		footGround = top;
		footTight = tight;
		return CLEAR;
	}

	/** {@link #column} for a whole block column (anything in it counts, however thin), kept for the plan. */
	private static int cell(final Level level, final int bx, final int bz, final double ground, final double fall) {
		final int ix = bx - cellX0;
		final int iz = bz - cellZ0;
		final int ref = (int) Math.floor(ground * 16.0 + 0.5);
		final boolean kept = ix >= 0 && ix < CELLS && iz >= 0 && iz < CELLS;
		final int i = kept ? ix + iz * CELLS : 0;
		if (kept && CELL_STAMP[i] == planStamp && CELL_REF[i] == ref) {
			cellGround = CELL_GROUND[i];
			return CELL_KIND[i];
		}
		final int c = column(level, bx + 0.5, bz + 0.5, ground, fall, true);
		cellGround = columnGround;
		if (kept) {
			CELL_STAMP[i] = planStamp;
			CELL_REF[i] = ref;
			CELL_KIND[i] = (byte) c;
			CELL_GROUND[i] = columnGround;
		}
		return c;
	}

	/**
	 * Nose to it, any line out at an angle clips it: steps aside along it, if the rider's line is clear past it from there,
	 * and turns hard that way (the body slides along the face as it comes round). Degrees off the line, or 0.
	 */
	private static float stepAside(final AbstractHorse horse, final float targetYaw, final float blocked, final double fall, final float preferred) {
		final float rad = targetYaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(rad);
		final double fz = Mth.cos(rad);
		final float past = blocked + ASIDE_PAST + half;
		for (float across = STEP; across <= ASIDE_REACH + 1.0E-3F; across += STEP) {
			for (int i = 0; i < 2; i++) {
				final float sign = i == 0 ? preferred : -preferred;
				probe(horse, horse.getX(), horse.getY(), horse.getZ(), targetYaw + 90.0F * sign, across, fall, SLIDE_FLANK);
				if (kind != CLEAR) {
					continue;
				}
				probe(horse, horse.getX() - fz * across * sign, endGround, horse.getZ() + fx * across * sign, targetYaw, past, fall, side);
				if (kind == CLEAR) {
					return AVOID_MAX_ANGLE * sign;
				}
			}
		}
		return 0.0F;
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
		return !Double.isNaN(ledge(horse, box, yaw, 2.0F, false));
	}

	/** Whether the wall the last probe met {@code blocked} along yaw is a hurdle the rider can jump from just short of it. */
	private static boolean jumpableHurdle(final AbstractHorse horse, final float yaw, final float blocked) {
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double start = Math.max(blocked - STEP - half - 1.0F, 0.0F);
		final AABB box = horse.getBoundingBox().move(-Mth.sin(rad) * start, endGround - horse.getY(), Mth.cos(rad) * start);
		return !Double.isNaN(hurdle(horse, box, yaw, 2.0F));
	}

	/**
	 * Fastest speed (multiple of the speed attribute) that still stops short of, or lands safely past, what lies along yaw;
	 * without {@code obstacles}, only slopes count (on a way planned round what is there).
	 */
	private static float limit(
		final AbstractHorse horse, final RideState s, final float yaw, final float range, final double fall, final float blocksPerUnit, final boolean obstacles
	) {
		final float ahead = probe(horse, horse.getX(), horse.getY(), horse.getZ(), yaw, range, fall, side);
		s.dangerAhead = kind == DANGER ? ahead : Float.MAX_VALUE;
		s.dangerJumpable = kind == DANGER && hazardJumpable;
		s.wallAhead = kind == WALL ? ahead : Float.MAX_VALUE;
		s.gapAhead = gapAt;
		final float decel = DECEL_BRAKE * blocksPerUnit * BRAKE_PLAN;
		firstEdge = Float.MAX_VALUE;
		final float limit = descent(horse, s, Math.min(fall, harmlessFall(horse)), decel, blocksPerUnit);
		if (kind == CLEAR || !obstacles) {
			return limit;
		}
		if (kind == WALL && wallLedge && s.ledgeOnLine && Math.abs(Mth.wrapDegrees(yaw - s.riderYaw)) < LEDGE_LINE_ANGLE) {
			// The ledge on the rider's line it will jump: down to a trot by the time it is in reach, then it jumps.
			return Math.min(limit, brakeTo(ahead - STEP - half - LEDGE_REACH, GAIT_SPEED[TROT] * blocksPerUnit, decel) / blocksPerUnit);
		}
		if (kind == WALL && wallLedge && s.hurdleOnLine && Math.abs(Mth.wrapDegrees(yaw - s.riderYaw)) < LEDGE_LINE_ANGLE) {
			// A hurdle on the rider's line: down to a trot by the time it is in jumping reach; then, unless the rider
			// jumps, it stops short of it (with a snort, if the rider is still pushing on when it gets there).
			final float room = ahead - STEP - half;
			final float trot = brakeTo(room - HURDLE_REACH, GAIT_SPEED[TROT] * blocksPerUnit, decel) / blocksPerUnit;
			final float stop = brakeTo(room - STOP_MARGIN, 0.0F, decel) / blocksPerUnit;
			if (room < STOP_MARGIN + 0.25F && s.gait != STOP && s.jumpBuffer == 0) {
				refuse(horse, s);
			}
			return Math.min(limit, Math.min(trot, stop));
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
			// Past the end: nothing if it ended at a drop; if it ended at a wall the horse can't get past it, so it comes down
			// short of it (with a wall right in front, where it stands: before, that read as a bottomless drop and a jump
			// pressed close to a wall or ledge was refused).
			return profileFallsAway ? Double.NEGATIVE_INFINITY : profileLength == 0 ? profileOrigin : PROFILE[profileLength - 1];
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
				// Every line that met something met a ledge (met at an angle, a flank gets there before the centre does).
				wallLedge = (c != BLOCKED || cLedge) && (l != BLOCKED || lLedge) && (r != BLOCKED || columnLedge);
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
		return column(level, x, z, ground, fall, false);
	}

	/** {@link #column}; {@code whole}: anything in the block counts, however thin and wherever in it (planning by whole blocks). */
	private static int column(final Level level, final double x, final double z, final double ground, final double fall, final boolean whole) {
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
			final VoxelShape shape = Foliage.leavesOpen(level) && state.is(BlockTags.LEAVES) ? Shapes.empty() : state.getCollisionShape(level, POS);
			if (shape.isEmpty() || shape != Shapes.block() && !whole && !covers(shape, inX, inZ)) {
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
				// The first thing met coming down: the top of whatever is in the way, a ledge if no more than 2 up (and a
				// layer of snow or a carpet on top).
				columnLedge = surface <= ground + LEDGE_HEIGHT + LEDGE_TOP_LAYER;
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
		return ledgeAhead(horse, LEDGE_REACH);
	}

	/**
	 * Something in the way above a step's height and up to a ledge's within {@code reach} of the chest straight ahead
	 * (both block rows, so standing on a slab or a snow layer the face still shows).
	 */
	static boolean ledgeAhead(final AbstractHorse horse, final float reach) {
		body(horse);
		final Level level = horse.level();
		final float rad = horse.getYRot() * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(rad);
		final double fz = Mth.cos(rad);
		final int y0 = Mth.floor(horse.getY() + STEP_UP + 0.05);
		final int y1 = Mth.floor(horse.getY() + LEDGE_HEIGHT - 0.05);
		for (float d = half + 0.25F; d <= half + reach; d += 0.5F) {
			final int x = Mth.floor(horse.getX() + fx * d);
			final int z = Mth.floor(horse.getZ() + fz * d);
			for (int y = y0; y <= y1; y++) {
				final BlockState state = level.getBlockState(POS.set(x, y, z));
				if (!state.isAir() && !(Foliage.leavesOpen(level) && state.is(BlockTags.LEAVES)) && !state.getCollisionShape(level, POS).isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A ledge the horse could jump up, its face within {@code reach} of the chest straight ahead along yaw (met square
	 * enough: not ridden along or glanced): too high to step, no higher than LEDGE_HEIGHT where the body meets it, with
	 * headroom for the jump, and room and a solid top under most of where it would land (not leaves it falls through, not
	 * a lone log in a bush). Where landing straight ahead won't do (a wall beside it, a corner, a ragged edge, too little
	 * of the top under it), it looks a little to either side and nearer or further in, and then climbs straight up the
	 * face instead of along its heading. Never a fence, wall or gate (or anything taller than a block), so pens still hold
	 * horses. Returns the top of the ledge, or NaN; the face's distance goes in {@link #ledgeFace} and the heading to climb
	 * along (toward where it lands) in {@link #ledgeClimbYaw}.
	 */
	static double ledge(final AbstractHorse horse, final float yaw, final float reach) {
		return ledge(horse, horse.getBoundingBox(), yaw, reach, true);
	}

	/**
	 * {@link #ledge(AbstractHorse, float, float)} for the body at {@code box}; {@code wide}: looking to either side and up
	 * the face too (at the face itself), else only straight on (planning ahead whether a ledge is on the rider's line: one
	 * that only catches the flank is gone round, not climbed).
	 */
	private static double ledge(final AbstractHorse horse, final AABB box, final float yaw, final float reach, final boolean wide) {
		final Level level = horse.level();
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(rad);
		final double fz = Mth.cos(rad);
		final float face = face(level, horse, box, fx, fz, reach);
		if (face < 0.0F) {
			return reject(LEDGE_NO_FACE);
		}
		if (level.noBlockCollision(horse, box.move(fx * (face + 0.05), STEP_UP + 0.05, fz * (face + 0.05)))) {
			return reject(LEDGE_ONLY_A_STEP);
		}
		// Met square enough to climb it: not ridden along it, glancing it (which way it faces: whichever way alone stops the box).
		final boolean stopsX = !level.noBlockCollision(horse, box.move(fx * (face + 0.05), 0.0, 0.0));
		final boolean stopsZ = !level.noBlockCollision(horse, box.move(0.0, 0.0, fz * (face + 0.05)));
		final double square = stopsX && !stopsZ ? Math.abs(fx) : stopsZ && !stopsX ? Math.abs(fz) : Math.max(Math.abs(fx), Math.abs(fz));
		if (square < Mth.cos(LEDGE_FACE_ANGLE * Mth.DEG_TO_RAD)) {
			return reject(LEDGE_NO_FACE);
		}
		final double top = climb(level, horse, box, yaw, face, wide ? LAND_SIDE : STRAIGHT_ON);
		if (!wide || !Double.isNaN(top) || ledgeRejection == LEDGE_FENCE || ledgeRejection == LEDGE_NO_HEADROOM) {
			return top;
		}
		// Straight up the face instead (which way it faces; a corner, whichever way the heading is nearer).
		final float normal = stopsX && (!stopsZ || Math.abs(fx) >= Math.abs(fz)) ? (fx > 0.0 ? -90.0F : 90.0F) : (fz > 0.0 ? 0.0F : 180.0F);
		if (Math.abs(Mth.wrapDegrees(normal - yaw)) < 5.0F) {
			return top;
		}
		final int why = ledgeRejection;
		final float nrad = normal * Mth.DEG_TO_RAD;
		final float normalFace = face(level, horse, box, -Mth.sin(nrad), Mth.cos(nrad), reach);
		final double up = normalFace < 0.0F ? Double.NaN : climb(level, horse, box, normal, normalFace, LAND_SIDE);
		if (Double.isNaN(up)) {
			// (The reason it couldn't climb along its heading, which is what the rider asked for.)
			return reject(why);
		}
		return up;
	}

	/** How far along (fx, fz) the box first meets something, to a few hundredths (0 touching it); -1 if nothing within {@code reach}. */
	private static float face(final Level level, final AbstractHorse horse, final AABB box, final double fx, final double fz, final float reach) {
		float face = -1.0F;
		for (float m = 0.0F; m <= reach + 1.0E-3F; m += 0.25F) {
			if (!level.noBlockCollision(horse, box.move(fx * (m + 0.05), 0.0, fz * (m + 0.05)))) {
				face = m;
				break;
			}
		}
		// Pin it down, so where it lands and when it takes off don't jump about as it walks in.
		float clear = Math.max(face - 0.25F, -0.05F);
		for (int i = 0; i < 3 && face > 0.0F; i++) {
			final float mid = (clear + face) * 0.5F;
			if (level.noBlockCollision(horse, box.move(fx * (mid + 0.05), 0.0, fz * (mid + 0.05)))) {
				clear = mid;
			} else {
				face = mid;
			}
		}
		return face;
	}

	/** Sideways offsets (blocks, right positive) and distances past the face (blocks) where a climb may land, the likeliest first. */
	private static final double[] LAND_SIDE = {0.0, 0.3, -0.3, 0.6, -0.6, 1.0, -1.0};
	private static final double[] STRAIGHT_ON = {0.0};
	private static final double[] LAND_IN = {1.0, 0.6, 1.4};

	/**
	 * A climb up the face {@code face} blocks along {@code yaw}: where the body meets it, it rises to a top no higher than
	 * a ledge, with headroom over the horse, and somewhere straight on or a little to either side, nearer or further in,
	 * the body gets onto it with solid ground to land on. Where it gets to is found the way the jump will take it: lifted
	 * over the lip where it stands and carried toward the spot, stopping at whatever it meets (so it settles into a notch
	 * or the corner of a one-block step, and a taller wall right there keeps it off the top). The top, or NaN; on success
	 * {@link #ledgeFace} and {@link #ledgeClimbYaw} (toward where it lands).
	 */
	private static double climb(final Level level, final AbstractHorse horse, final AABB box, final float yaw, final float face, final double[] sides) {
		final CollisionContext context = CollisionContext.of(horse);
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(rad);
		final double fz = Mth.cos(rad);
		// Right of the heading.
		final double rx = -fz;
		final double rz = fx;
		// Up to a thin layer (snow, a carpet) on top of the second block.
		final double highest = box.minY + LEDGE_HEIGHT + LEDGE_TOP_LAYER;
		final double centreX = (box.minX + box.maxX) * 0.5;
		final double centreZ = (box.minZ + box.maxZ) * 0.5;
		int why = LEDGE_TOO_HIGH;
		boolean headroomChecked = false;
		for (final double side : sides) {
			final AABB at = box.move(fx * (face + 0.05) + rx * side, 0.0, fz * (face + 0.05) + rz * side);
			final double top = top(level, context, at, box.minY + 0.5, highest);
			if (Double.isNaN(top)) {
				return reject(LEDGE_FENCE);
			}
			if (!(top > box.minY + STEP_UP)) {
				continue;
			}
			if (!headroomChecked) {
				headroomChecked = true;
				if (!level.noBlockCollision(horse, box.expandTowards(0.0, top - box.minY + LEDGE_CLEARANCE + 0.05, 0.0))) {
					return reject(LEDGE_NO_HEADROOM);
				}
			}
			// (Lifted where it stands: the headroom just checked is room for that.)
			final AABB lifted = box.move(0.0, top - box.minY + 0.01, 0.0);
			for (final double in : LAND_IN) {
				final AABB landing = sweep(level, horse, lifted, fx * (face + in) + rx * side, fz * (face + in) + rz * side);
				// The body is about two blocks long whatever its collision box: it needs that much ledge to land on. (Ground a
				// step higher counts: a horse that lands with a step in front walks up it; so does another ledge it can climb
				// next, its chest against that face, as up a staircase of one-block steps.)
				// (And the box itself has to get onto the top, not stop at a taller wall on the way.)
				final boolean onTop = support(level, context, landing, top, STEP_UP) >= LEDGE_SUPPORT;
				if (!onTop || support(level, context, landing.expandTowards(fx * BODY_LENGTH_EXTRA, 0.0, fz * BODY_LENGTH_EXTRA), top, LEDGE_HEIGHT + LEDGE_TOP_LAYER)
					< LEDGE_SUPPORT) {
					why = onTop ? LEDGE_NOTHING_TO_LAND_ON : LEDGE_NO_ROOM_ON_TOP;
					if (logClimbs) {
						climbLog.append(String.format(java.util.Locale.ROOT, "[yaw%.0f side%.1f in%.1f top%.2f got to %.2f,%.2f %s] ", yaw, side, in, top - box.minY,
							(landing.minX + landing.maxX) * 0.5 - centreX, (landing.minZ + landing.maxZ) * 0.5 - centreZ, LEDGE_REASONS[why]));
					}
					continue;
				}
				ledgeFace = face;
				ledgeClimbYaw = (float) (Mth.atan2(-((landing.minX + landing.maxX) * 0.5 - centreX), (landing.minZ + landing.maxZ) * 0.5 - centreZ) * Mth.RAD_TO_DEG);
				ledgeRejection = LEDGE_OK;
				return top;
			}
		}
		return reject(why);
	}

	/** Where {@code box} gets to moved (dx, dz) through the world, stopping at what it meets: the longer way first, as entities move. */
	private static AABB sweep(final Level level, final AbstractHorse horse, final AABB box, final double dx, final double dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			final AABB moved = box.move(clip(level, horse, box, Direction.Axis.X, dx), 0.0, 0.0);
			return moved.move(0.0, 0.0, clip(level, horse, moved, Direction.Axis.Z, dz));
		}
		final AABB moved = box.move(0.0, 0.0, clip(level, horse, box, Direction.Axis.Z, dz));
		return moved.move(clip(level, horse, moved, Direction.Axis.X, dx), 0.0, 0.0);
	}

	private static double clip(final Level level, final AbstractHorse horse, final AABB box, final Direction.Axis axis, final double distance) {
		if (Math.abs(distance) < 1.0E-7) {
			return 0.0;
		}
		final AABB reach = axis == Direction.Axis.X ? box.expandTowards(distance, 0.0, 0.0) : box.expandTowards(0.0, 0.0, distance);
		return Shapes.collide(axis, box, level.getBlockCollisions(horse, reach), distance);
	}

	/**
	 * The top of what is in {@code at} from {@code from} up to {@code highest} (blocks reaching higher are left out:
	 * the caller checks nothing is above the top), or NaN where there is a fence, a wall, a gate or anything taller than a
	 * block; negative infinity for nothing.
	 */
	private static double top(final Level level, final CollisionContext context, final AABB at, final double from, final double highest) {
		double top = Double.NEGATIVE_INFINITY;
		final int x1 = Mth.floor(at.maxX - 1.0E-7);
		final int z1 = Mth.floor(at.maxZ - 1.0E-7);
		final int y0 = Mth.floor(from);
		final int y1 = Mth.floor(highest);
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
					if (y + height <= highest) {
						top = Math.max(top, y + height);
					}
				}
			}
		}
		return top;
	}

	/** Whether the horse is standing on top of a fence, a wall or a gate. */
	static boolean onHurdle(final AbstractHorse horse) {
		final AABB box = horse.getBoundingBox();
		final int y = Mth.floor(box.minY - 0.6);
		for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX - 1.0E-7); x++) {
			for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ - 1.0E-7); z++) {
				final BlockState state = horse.level().getBlockState(POS.set(x, y, z));
				if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCE_GATES)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The last hurdle found: its face and how far the body must go to be clear past it (blocks), and whether one was too risky to land beyond. */
	static float hurdleFace;
	static float hurdleClear;
	static boolean hurdleUnsafe;

	/**
	 * A hurdle straight ahead along yaw, its face within {@code reach} of the chest: something HURDLE_LOW to HURDLE_HEIGHT
	 * tall (a fence, a wall, a gate; not a step it walks up, nor a ledge it climbs), no deeper than HURDLE_DEPTH, with
	 * headroom over it and ground beyond within the fall the horse takes. Returns its top, or NaN (with
	 * {@link #hurdleUnsafe} set when it was only the landing that ruled it out).
	 */
	static double hurdle(final AbstractHorse horse, final float yaw, final float reach) {
		return hurdle(horse, horse.getBoundingBox(), yaw, reach);
	}

	private static double hurdle(final AbstractHorse horse, final AABB box, final float yaw, final float reach) {
		hurdleUnsafe = false;
		final Level level = horse.level();
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(rad);
		final double fz = Mth.cos(rad);
		float face = -1.0F;
		for (float m = 0.0F; m <= reach + 1.0E-3F; m += 0.125F) {
			if (!level.noBlockCollision(horse, box.move(fx * (m + 0.02), 0.0, fz * (m + 0.02)))) {
				face = m;
				break;
			}
		}
		if (face < 0.0F) {
			return Double.NaN;
		}
		final AABB at = box.move(fx * (face + 0.05), 0.0, fz * (face + 0.05));
		// Not a step it walks up, and nothing taller than a hurdle (or overhead) there.
		if (level.noBlockCollision(horse, at.move(0.0, HURDLE_LOW, 0.0)) || !level.noBlockCollision(horse, at.move(0.0, HURDLE_HEIGHT + 0.01, 0.0))) {
			return Double.NaN;
		}
		// Its top: the least lift that clears it.
		double low = HURDLE_LOW;
		double high = HURDLE_HEIGHT + 0.01;
		for (int i = 0; i < 6; i++) {
			final double mid = (low + high) * 0.5;
			if (level.noBlockCollision(horse, at.move(0.0, mid, 0.0))) {
				high = mid;
			} else {
				low = mid;
			}
		}
		final double top = box.minY + high;
		// Where the body is clear past it, at its own height.
		float clear = -1.0F;
		for (float m = face + 0.125F; m <= face + HURDLE_DEPTH + 2.0F * half + 1.0E-3F; m += 0.125F) {
			if (level.noBlockCollision(horse, box.move(fx * m, 0.0, fz * m))) {
				clear = m;
				break;
			}
		}
		if (clear < 0.0F || !level.noBlockCollision(horse, box.move(fx * face, high + HURDLE_CLEARANCE, fz * face).expandTowards(fx * (clear - face), 0.0, fz * (clear - face)))) {
			return Double.NaN;
		}
		// Ground to land on beyond, no further down than it falls.
		final AABB beyond = box.move(fx * clear, 0.0, fz * clear);
		final double fall = acceptableFall(horse);
		if (level.noBlockCollision(horse, beyond.expandTowards(0.0, -fall, 0.0)) || entersHazard(horse, fx * clear, fz * clear)) {
			hurdleUnsafe = true;
			return Double.NaN;
		}
		hurdleFace = face;
		hurdleClear = clear;
		return top;
	}

	private static double reject(final int why) {
		ledgeRejection = why;
		return Double.NaN;
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
		return support(level, context, box, top, 0.0);
	}

	/**
	 * Share of the box's footprint over solid ground whose top is at {@code top} or up to {@code rise} above it (a step
	 * up it can walk on from there), and no higher.
	 */
	private static double support(final Level level, final CollisionContext context, final AABB box, final double top, final double rise) {
		final int y0 = Mth.floor(top - 0.01);
		final int y1 = Mth.floor(top + rise);
		final double highest = top + rise + 0.01;
		final int x1 = Mth.floor(box.maxX - 1.0E-7);
		final int z1 = Mth.floor(box.maxZ - 1.0E-7);
		double area = 0.0;
		for (int x = Mth.floor(box.minX); x <= x1; x++) {
			for (int z = Mth.floor(box.minZ); z <= z1; z++) {
				// The top of the ground, from below: a gap above it (a branch overhead) is room, not more ground.
				double surface = Double.NEGATIVE_INFINITY;
				for (int y = y0; y <= y1; y++) {
					final BlockState state = level.getBlockState(POS.set(x, y, z));
					final VoxelShape shape = state.isAir() ? Shapes.empty() : state.getCollisionShape(level, POS, context);
					if (!shape.isEmpty()) {
						surface = Math.max(surface, y + shape.max(Direction.Axis.Y));
					} else if (surface > Double.NEGATIVE_INFINITY && y >= surface) {
						break;
					}
				}
				if (surface >= top - 0.05 && surface <= highest) {
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
