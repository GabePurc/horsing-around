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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The horse has a say in where it goes. It looks ahead along its path for what would stop it (walls, trunks, ledges)
 * and what would hurt it (drops beyond its safe fall distance, lava, fire, cactus, berry bushes...), and the ride
 * controller uses that to steer round small obstacles, slow for walls, refuse drops and hazards, and climb ledges.
 *
 * <p>Built not to get in the way: at a walk the horse never steers itself and walks right up to walls; it only
 * detours round things it can actually pass; riding alongside walls, fences and edges reads clear; drops it can take
 * safely, water landings and jumpable gaps are left to the rider.
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
	/** Side lines run just inside the body's half-width, so riding alongside a wall or an edge reads clear. */
	private static final float SIDE = 0.55F;
	private static final float HALF_BODY = 0.7F;
	private static final double STEP_UP = 1.0;
	private static final double BODY_HEIGHT = 1.6;
	/** Thin shapes (doors, panes, fence posts) only count where they are, give or take this much. */
	private static final double SHAPE_PAD = 0.2;
	/** The edge guard arms when danger or a gap is this close. */
	private static final float GUARD_RANGE = 4.0F;

	// Column classes.
	private static final int GROUND = 0;
	private static final int BLOCKED = 1;
	private static final int DROP = 2;
	private static final int HAZARD = 3;

	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
	/** What the last probe found (CLEAR, WALL or DANGER), and the first jumpable gap it passed (MAX_VALUE if none). */
	private static int kind;
	private static float gapAt;
	/** Ground height of the last column read as GROUND. */
	private static double columnGround;

	private Awareness() {
	}

	/**
	 * One ridden tick on the ground: plans any detour round an obstacle on the rider's path (new ones from a trot up),
	 * and returns the fastest the horse is willing to go given what lies on the way it is heading.
	 */
	static float look(final AbstractHorse horse, final RideState s, final float targetYaw) {
		final double safeDrop = horse.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		final float blocksPerUnit = (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * TERMINAL_VELOCITY_FACTOR;
		final float range = Mth.clamp(Math.abs(s.speed) * blocksPerUnit * LOOK_AHEAD_TICKS + HALF_BODY + STEP, LOOK_AHEAD_MIN, LOOK_AHEAD_MAX);

		// Detours: started from a trot, so a walk is fully in the rider's hands; one under way is seen through.
		final boolean detouring = s.avoidTarget != 0.0F;
		if (s.speed < (detouring ? 0.05F : GAIT_SPEED[TROT] - 0.05F)) {
			s.avoidTarget = 0.0F;
		} else if (--s.avoidReplan <= 0) {
			s.avoidReplan = AVOID_REPLAN_TICKS;
			s.avoidTarget = detour(horse, s, targetYaw, range, safeDrop);
		}
		s.avoidOffset += Mth.clamp(s.avoidTarget - s.avoidOffset, -AVOID_RATE, AVOID_RATE);

		// Speed: slow to a walk before a wall, stop short of danger, judged on the way the horse is going (while it
		// detours round something, on the detour, braking for its current heading only if the obstacle is upon it).
		float limit = limit(horse, s, s.avoidTarget != 0.0F ? targetYaw + s.avoidTarget : horse.getYRot(), range, safeDrop, blocksPerUnit);
		if (s.avoidTarget != 0.0F) {
			final float dangerAhead = s.dangerAhead;
			final float gapAhead = s.gapAhead;
			final float wallAhead = s.wallAhead;
			limit = Math.min(limit, limit(horse, s, horse.getYRot(), DETOUR_EMERGENCY, safeDrop, blocksPerUnit));
			s.dangerAhead = Math.min(s.dangerAhead, dangerAhead);
			s.gapAhead = Math.min(s.gapAhead, gapAhead);
			s.wallAhead = Math.min(s.wallAhead, wallAhead);
		}
		return limit;
	}

	/** Fastest speed (multiple of the speed attribute) that still stops short of what lies along yaw within range. */
	private static float limit(final AbstractHorse horse, final RideState s, final float yaw, final float range, final double safeDrop, final float blocksPerUnit) {
		final float ahead = probe(horse, yaw, range, safeDrop, SIDE);
		s.dangerAhead = kind == DANGER ? ahead : Float.MAX_VALUE;
		s.wallAhead = kind == WALL ? ahead : Float.MAX_VALUE;
		s.gapAhead = gapAt;
		if (kind == CLEAR) {
			return Float.MAX_VALUE;
		}
		// The obstacle starts somewhere in the last sample step; keep the body clear of it.
		final float room = ahead - STEP - HALF_BODY - STOP_MARGIN;
		final float decel = DECEL_BRAKE * blocksPerUnit * BRAKE_PLAN;
		final float blocksPerTick = room <= 0.0F ? 0.0F
			: decel * (-BRAKE_LAG_TICKS + (float) Math.sqrt(BRAKE_LAG_TICKS * BRAKE_LAG_TICKS + 2.0F * room / decel));
		final float limit = blocksPerTick / blocksPerUnit;
		if (kind == WALL) {
			return Math.max(limit, GAIT_SPEED[WALK]);
		}
		if (s.speed >= GAIT_SPEED[TROT] && limit < s.speed) {
			// It has seen the drop or the fire and starts to pull up.
			refuse(horse, s);
		}
		return limit;
	}

	/** Not looking this tick (walking off, swimming, jumping, climbing): let any detour ease out. */
	static void rest(final RideState s) {
		s.avoidTarget = 0.0F;
		s.avoidOffset += Mth.clamp(-s.avoidOffset, -AVOID_RATE, AVOID_RATE);
		s.dangerAhead = Float.MAX_VALUE;
		s.gapAhead = Float.MAX_VALUE;
		s.wallAhead = Float.MAX_VALUE;
	}

	/**
	 * The smallest turn off the rider's line, up to AVOID_MAX_ANGLE, that gets the horse past what blocks it; keeps to
	 * the side it is already detouring to. A detour only counts if, measured along the rider's line, it gets
	 * DETOUR_CLEARANCE beyond the obstacle: true of trees and rocks, never of a wall too long to get round (any line
	 * into it makes no more progress than straight ahead). Zero when the line is clear or there is no way round (then
	 * the horse slows instead).
	 */
	private static float detour(final AbstractHorse horse, final RideState s, final float targetYaw, final float range, final double safeDrop) {
		final float blocked = probe(horse, targetYaw, range, safeDrop, SIDE);
		if (kind == CLEAR) {
			return 0.0F;
		}
		final float side = s.avoidOffset != 0.0F ? Math.signum(s.avoidOffset) : s.turnIntent < 0.0F ? -1.0F : 1.0F;
		for (float angle = AVOID_ANGLE_STEP; angle <= AVOID_MAX_ANGLE; angle += AVOID_ANGLE_STEP) {
			final float along = Mth.cos(angle * Mth.DEG_TO_RAD);
			if (passes(probe(horse, targetYaw + angle * side, range, safeDrop, SIDE + DETOUR_MARGIN), along, blocked, range)) {
				return angle * side;
			}
			if (passes(probe(horse, targetYaw - angle * side, range, safeDrop, SIDE + DETOUR_MARGIN), along, blocked, range)) {
				return -angle * side;
			}
		}
		return 0.0F;
	}

	/**
	 * Whether a line that reaches {@code reach} at {@code along} = cos(angle) off the rider's line gets past an
	 * obstacle {@code blocked} ahead: clear to the horizon beyond the obstacle's depth, or blocked only well past it.
	 * Lines into a wall too long to get round never do (they stop at its face, no further along than straight ahead).
	 */
	private static boolean passes(final float reach, final float along, final float blocked, final float range) {
		final float progress = reach * along;
		return progress > blocked + STEP && (reach >= range || progress >= blocked + DETOUR_CLEARANCE);
	}

	/**
	 * Walks three lines (centre and both flanks, {@code flank} either side) along yaw from the horse, following the ground. Returns the distance
	 * to the first sample where something would stop the horse ({@code kind} WALL: too high to step, no headroom) or
	 * hurt it ({@code kind} DANGER: a hazard under any line, or a drop beyond its safe fall distance under all three, so
	 * running along an edge is fine), or {@code range} when clear. Gaps with safe ground beyond within GAP_REACH are
	 * jumpable: passed over and noted in {@code gapAt}.
	 */
	private static float probe(final AbstractHorse horse, final float yaw, final float range, final double safeDrop, final float flank) {
		final Level level = horse.level();
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(rad);
		final double fz = Mth.cos(rad);
		final double sx = fz * flank;
		final double sz = -fx * flank;
		final double x0 = horse.getX();
		final double z0 = horse.getZ();
		double centre = horse.getY();
		double left = centre;
		double right = centre;
		gapAt = Float.MAX_VALUE;
		for (float d = STEP; d <= range; d += STEP) {
			final double x = x0 + fx * d;
			final double z = z0 + fz * d;
			final int c = column(level, x, z, centre, safeDrop);
			final double cGround = columnGround;
			final int l = column(level, x - sx, z - sz, left, safeDrop);
			final double lGround = columnGround;
			final int r = column(level, x + sx, z + sz, right, safeDrop);
			if (c == BLOCKED || l == BLOCKED || r == BLOCKED) {
				kind = WALL;
				return d;
			}
			if (c == HAZARD || l == HAZARD || r == HAZARD || c == DROP && l == DROP && r == DROP) {
				final float landing = landing(level, x0, z0, fx, fz, d, centre, safeDrop);
				if (landing < 0.0F) {
					kind = DANGER;
					return d;
				}
				if (gapAt == Float.MAX_VALUE) {
					gapAt = d;
				}
				centre = left = right = columnGround;
				d = landing;
				continue;
			}
			// A line over a drop while the others hold the horse up keeps the edge height.
			if (c == GROUND) {
				centre = cGround;
			}
			if (l == GROUND) {
				left = lGround;
			}
			if (r == GROUND) {
				right = columnGround;
			}
		}
		kind = CLEAR;
		return range;
	}

	/** Where safe ground resumes at about the edge's height within GAP_REACH past {@code from}, or -1. */
	private static float landing(
		final Level level, final double x0, final double z0, final double fx, final double fz, final float from, final double edge, final double safeDrop
	) {
		for (float d = from + STEP; d <= from + GAP_REACH; d += STEP) {
			if (column(level, x0 + fx * d, z0 + fz * d, edge, safeDrop) == GROUND && columnGround >= edge - 1.5) {
				return d;
			}
		}
		return -1.0F;
	}

	/**
	 * Reads one block column for a body arriving at ground height {@code ground}: GROUND (a surface within a step up,
	 * or a safe drop down, with headroom; sets {@link #columnGround}), BLOCKED (nothing the body fits on), DROP (deeper
	 * than the safe fall) or HAZARD. Water anywhere below breaks a fall. Scans down from head height after a step up.
	 */
	private static int column(final Level level, final double x, final double z, final double ground, final double safeDrop) {
		final int bx = Mth.floor(x);
		final int bz = Mth.floor(z);
		final double inX = x - bx;
		final double inZ = z - bz;
		final int top = Mth.floor(ground + STEP_UP + BODY_HEIGHT);
		final int bottom = Mth.floor(ground - safeDrop) - 1;
		final double bodyTop = ground + BODY_HEIGHT;
		double ceiling = Double.MAX_VALUE;
		for (int by = top; by >= bottom; by--) {
			final BlockState state = level.getBlockState(POS.set(bx, by, bz));
			if (state.isAir()) {
				continue;
			}
			final FluidState fluid = state.getFluidState();
			if (!fluid.isEmpty() && fluid.is(FluidTags.LAVA)) {
				return HAZARD;
			}
			if (by <= bodyTop && isHazard(state)) {
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
				if (ground - surface > safeDrop) {
					return DROP;
				}
				columnGround = surface;
				return GROUND;
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
	static boolean isHazard(final BlockState state) {
		return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
			|| state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.COBWEB) || CampfireBlock.isLitCampfire(state)
			|| state.is(AVOIDS);
	}

	/**
	 * Last resort, after steering and speed are settled: if this tick's travel would carry the horse off a drop that
	 * would hurt it or into a hazard (an edge it reached too fast, the lip of a gap without a jump, backing up), it
	 * plants its feet. Only checks when danger is close or backing up.
	 */
	static void guard(final AbstractHorse horse, final RideState s) {
		if (s.speed >= 0.0F && s.dangerAhead > GUARD_RANGE && s.gapAhead > GUARD_RANGE || s.jumpBuffer > 0 || s.pendingJump > 0.0F) {
			return;
		}
		final float rad = s.heading * Mth.DEG_TO_RAD;
		final double push = horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * s.speed;
		final Vec3 movement = horse.getDeltaMovement();
		final double dx = movement.x - Mth.sin(rad) * push;
		final double dz = movement.z + Mth.cos(rad) * push;
		if (!unsafe(horse, dx, dz, horse.getAttributeValue(Attributes.SAFE_FALL_DISTANCE))) {
			return;
		}
		if (s.speed >= GAIT_SPEED[TROT]) {
			refuse(horse, s);
		}
		s.speed = 0.0F;
		horse.setDeltaMovement(0.0, movement.y, 0.0);
	}

	/** Whether the horse's body moved by (dx, dz) would fall further than safeDrop (with no water below) or meet a hazard. */
	private static boolean unsafe(final AbstractHorse horse, final double dx, final double dz, final double safeDrop) {
		final Level level = horse.level();
		final AABB box = horse.getBoundingBox();
		final int x1 = Mth.floor(box.maxX + dx - 1.0E-7);
		final int z1 = Mth.floor(box.maxZ + dz - 1.0E-7);
		final int y0 = Mth.floor(box.minY - 0.5);
		final int y1 = Mth.floor(box.minY + 1.0);
		for (int x = Mth.floor(box.minX + dx); x <= x1; x++) {
			for (int z = Mth.floor(box.minZ + dz); z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					final BlockState state = level.getBlockState(POS.set(x, y, z));
					if (!state.isAir() && (isHazard(state) || state.getFluidState().is(FluidTags.LAVA))) {
						return true;
					}
				}
			}
		}
		final AABB below = new AABB(
			box.minX + dx + 1.0E-7, box.minY - safeDrop - 1.0E-7, box.minZ + dz + 1.0E-7, box.maxX + dx - 1.0E-7, box.minY, box.maxZ + dz - 1.0E-7
		);
		if (!level.noBlockCollision(horse, below)) {
			return false;
		}
		// Nothing to land on within the safe fall: fine only if there is water to land in.
		final int cx = Mth.floor((box.minX + box.maxX) * 0.5 + dx);
		final int cz = Mth.floor((box.minZ + box.maxZ) * 0.5 + dz);
		for (int y = Mth.floor(box.minY); y >= Mth.floor(box.minY - safeDrop) - 1; y--) {
			final FluidState fluid = level.getFluidState(POS.set(cx, y, cz));
			if (!fluid.isEmpty()) {
				return !fluid.is(FluidTags.WATER);
			}
		}
		return true;
	}

	/**
	 * A ledge the horse could jump up, its face within {@code reach} straight ahead along yaw: too high to step, no
	 * higher than LEDGE_HEIGHT, with headroom for the jump and room on top. Never a fence, wall or gate (or anything
	 * taller than a block), so pens still hold horses. Returns the top of the ledge, or NaN; the distance to its face is
	 * left in {@link #ledgeFace}.
	 */
	static double ledge(final AbstractHorse horse, final float yaw, final float reach) {
		final Level level = horse.level();
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(rad);
		final double fz = Mth.cos(rad);
		final AABB box = horse.getBoundingBox();
		float face = -1.0F;
		for (float m = 0.15F; m <= reach + 1.0E-3F; m += 0.15F) {
			if (!level.noBlockCollision(horse, box.move(fx * m, 0.0, fz * m))) {
				face = m;
				break;
			}
		}
		if (face < 0.0F) {
			return Double.NaN;
		}
		final AABB at = box.move(fx * face, 0.0, fz * face);
		if (level.noBlockCollision(horse, at.move(0.0, STEP_UP + 0.05, 0.0))
			|| !level.noBlockCollision(horse, box.move(fx * (face + 0.3), LEDGE_HEIGHT + 0.05, fz * (face + 0.3)))
			|| !level.noBlockCollision(horse, box.expandTowards(0.0, LEDGE_HEIGHT + LEDGE_CLEARANCE + 0.05, 0.0))) {
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
					final VoxelShape shape = state.getCollisionShape(level, POS);
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
		ledgeFace = face;
		return top > box.minY + STEP_UP ? top : Double.NaN;
	}

	/** Distance to the face of the last ledge found, blocks. */
	static float ledgeFace;

	/** Whether a wall is close enough ahead that it could be a ledge to jump up. */
	static boolean nearWall(final RideState s) {
		return s.wallAhead <= LEDGE_REACH + HALF_BODY + STEP;
	}

	/** A jump now would carry the horse off a drop or into a hazard with no safe landing in reach. */
	static boolean refusesJump(final RideState s) {
		return s.dangerAhead <= GAP_REACH;
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
