package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Gait, momentum, stamina, steering and jumping for a player-ridden horse. Runs once per tick on the instance that
 * simulates the ride (the rider's client), before vanilla applies rotation and travel.
 *
 * <p>Controls: W rides at the current gait, sprint taps spur up a gait, S taps rein down a gait, S held brakes then
 * backs up, letting go lets the horse ease to a stop. The horse heads where the camera looks; with W, A/D angle it 45
 * degrees off the view, and A or D alone ride it across the view (90 degrees off), at the gait too. The view is never
 * moved. Space jumps immediately, harder the faster the horse is going.
 */
public final class RideController {
	/** Scratch position for ground probes; visuals only tick on the client thread. */
	private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();

	/** Test hook: while set, how long each ride tick took is left in {@link #lastTickNanos}. */
	public static boolean profile;
	public static long lastTickNanos;

	private RideController() {
	}

	/**
	 * @param in     keys currently held
	 * @param spurs  sprint presses since last tick
	 * @param reins  back presses since last tick
	 * @param jumps  jump presses since last tick
	 */
	public static void tick(
		final AbstractHorse horse, final RideState s, final Player rider, final Input in, final int spurs, final int reins, final int jumps
	) {
		final long started = profile ? System.nanoTime() : 0L;
		// A or D alone ride on at the gait as well, across the view; with W they angle the horse off it.
		final boolean across = !in.forward() && !in.backward() && in.left() != in.right();
		final boolean forward = in.forward() || across;
		if (horse.onGround()) {
			s.leapt = false;
		}
		final boolean back = in.backward();
		keepSpeedThroughDrops(horse, s);

		// Gait and speed.
		float target;
		float rate;
		if (forward) {
			if (s.gait == STOP) {
				s.gait = gaitForSpeed(s.speed);
			}
			if (reins > 0) {
				s.gait = Math.max(s.gait - reins, WALK);
			} else if (spurs > 0) {
				s.gait = Math.min(s.gait + spurs, s.exhausted ? CANTER : GALLOP);
			}
			// Committing weight to a turn costs some speed, more so the faster the horse is going.
			target = GAIT_SPEED[s.gait] * (1.0F - TURN_SPEED_LOSS * Math.abs(s.turnIntent) * gallopFraction(s.speed));
			rate = s.speed < target ? GAIT_ACCEL[s.gait] : DECEL_REIN;
		} else {
			s.gait = STOP;
			if (back && s.speed <= 0.01F) {
				target = REVERSE_SPEED;
				rate = REVERSE_ACCEL;
			} else {
				target = 0.0F;
				rate = back ? DECEL_BRAKE : s.speed < 0.0F ? REVERSE_ACCEL : DECEL_COAST;
			}
		}

		// Water: wade while the hooves reach the bottom (slower the deeper it is), swim once they don't.
		final double depth = horse.isInWater() ? horse.getFluidHeight(FluidTags.WATER) : 0.0;
		// Start swimming once out of depth; keep swimming until the hooves find the bottom (no flicker at the threshold).
		// A heave out of the water carries on until the hooves are on the bank.
		s.swimming = !horse.onGround() && (s.bankTicks > 0 || depth > (s.swimming ? 0.5 : WADE_DEPTH));
		if (s.swimming) {
			// Deep water takes the way off quickly.
			target = Math.min(target, SWIM_SPEED);
			rate = Math.max(rate, SWIM_DECEL);
		} else if (depth > 0.0 && target > 0.0F) {
			target *= 1.0F - WADE_DRAG * (float) Math.pow(Math.min(depth / WADE_DEPTH, 1.0), WADE_DRAG_CURVE);
		}
		final BlockState leaves = RIDE_THROUGH_LEAVES ? Foliage.leavesIn(horse) : null;
		if (leaves != null) {
			target *= LEAVES_SPEED_FACTOR;
			rustle(horse, s, leaves);
		}

		// The horse has a say: on solid ground it slows for walls, stops short of drops that would hurt it and of
		// hazards, and from a trot steers round obstacles it can pass. Swimming, jumps and climbs are left alone.
		final int steer = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
		float targetYaw = rider.getYRot() + steer * (across ? ACROSS_OFFSET : STEER_OFFSET);
		s.riderYaw = targetYaw;
		if (AVOID_DANGER && horse.onGround() && !s.swimming && s.ledgeTicks == 0 && (forward || s.speed > 0.02F)) {
			final float limit = Awareness.look(horse, s, targetYaw, forward);
			target = Math.min(target, limit);
			if (s.speed > limit) {
				rate = Math.max(rate, DECEL_BRAKE);
			}
		} else {
			Awareness.rest(s);
		}
		targetYaw += s.avoidOffset;

		// Hard cuts: the further off it the rider looks, the more the horse sits back and slows to cut round tighter.
		final float turn = Math.abs(Mth.wrapDegrees(targetYaw - horse.getYRot()));
		final boolean footing = horse.onGround() && !s.swimming && s.ledgeTicks == 0 && (forward || back || steer != 0 || Math.abs(s.speed) > 0.02F);
		final float ask = HARD_CUT && footing ? smoothstep((turn - CUT_START) / (CUT_FULL - CUT_START)) : 0.0F;
		s.cut = ask >= s.cut ? ask : Math.max(ask, s.cut - CUT_RELEASE);
		if (s.cut > 0.0F && s.speed > 0.0F) {
			// The speed at which its grip, cutting this hard, brings it round in about CUT_TURN_TICKS.
			final float wanted = Math.min(turn / CUT_TURN_TICKS, TURN_RATE_STILL * Mth.lerp(s.cut, 1.0F, CUT_RATE_SCALE)) * (Mth.DEG_TO_RAD * 20.0F);
			final float metresPerUnit = (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * (TERMINAL_VELOCITY_FACTOR * 20.0F);
			target = Math.min(target, LATERAL_GRIP * Mth.lerp(s.cut, 1.0F, CUT_GRIP) / Math.max(wanted, 1.0E-3F) / metresPerUnit);
			if (s.speed > target) {
				rate = Math.max(rate, Mth.lerp(s.cut, DECEL_REIN, CUT_DECEL));
			}
		}
		if (s.cut > 0.5F && s.scuffReady) {
			s.scuffReady = false;
			s.cuts++;
			scuff(horse, s, Mth.wrapDegrees(targetYaw - horse.getYRot()) > 0.0F ? 1 : -1);
		} else if (s.cut < 0.2F) {
			s.scuffReady = true;
		}
		s.cutSquat += (s.cut - s.cutSquat) * CUT_SQUAT_EASE;

		s.speed = s.speed < target ? Math.min(s.speed + rate, target) : Math.max(s.speed - rate, target);

		// Only a real hit sheds momentum: one that stopped most of the last tick's travel, not a scrape along a trunk.
		if (s.speed > CRASH_SPEED && s.blocked > CRASH_BLOCKED && !horse.isInWater()) {
			s.speed *= CRASH_KEEP;
			if (s.gait > TROT) {
				s.gait = TROT;
			}
		}

		// Stamina.
		final boolean wasExhausted = s.exhausted;
		if (s.gait == GALLOP) {
			spend(s, STAMINA_DRAIN_GALLOP);
		} else if (s.swimming) {
			spend(s, STAMINA_DRAIN_SWIM);
		} else {
			regenerate(s, s.gait);
		}
		tiredness(horse, s, wasExhausted);

		if (s.swimming) {
			swim(horse, s, depth, forward || jumps > 0);
		} else if (s.bankTicks > 0) {
			// Hooves on the bank: out of the water.
			s.bankTicks = 0;
			s.bankClimbs++;
		}

		// Ledges up to 2 blocks: riding at one at a walk or trot, the horse jumps up it in its stride.
		if (s.ledgeTicks > 0) {
			ledgeJump(horse, s, forward);
		} else if (LEDGE_CLIMB && forward && horse.onGround() && !s.swimming && s.jumpBuffer == 0 && s.speed > 0.0F
			&& s.speed <= GAIT_SPEED[TROT] + 0.05F && Awareness.ledgeAhead(horse)) {
			final double top = Awareness.ledge(horse, horse.getYRot(), LEDGE_REACH);
			if (!Double.isNaN(top)) {
				s.ledgeTicks = 1;
				s.ledgeAir = false;
				s.ledgeTop = top;
				s.ledgeYaw = horse.getYRot();
				ledgeJump(horse, s, forward);
			}
		}

		// Jumping: fires on press (or on landing if pressed just before), no charging.
		if (jumps > 0 && !s.swimming && s.ledgeTicks == 0) {
			s.jumpBuffer = JUMP_BUFFER_TICKS;
		}
		if (horse.onGround()) {
			if (s.jumpRecovery > 0) {
				s.jumpRecovery--;
			} else if (s.jumpBuffer > 0) {
				float power = JUMP_POWER_STILL + (JUMP_POWER_RUNNING - JUMP_POWER_STILL) * Math.min(Math.max(s.speed, 0.0F) / GAIT_SPEED[CANTER], 1.0F);
				if (s.exhausted) {
					power *= JUMP_POWER_EXHAUSTED;
				}
				if (AVOID_DANGER && Awareness.refusesJump(horse, s, power)) {
					// No leaping off a cliff, into lava, or so far down a slope it would get hurt (a gap is fine).
					s.jumpBuffer = 0;
					Awareness.refuse(horse, s);
					power = 0.0F;
				}
				s.pendingJump = power;
				if (power > 0.0F) {
					s.leapt = true;
					s.jumpBuffer = 0;
					s.jumpRecovery = JUMP_RECOVERY_TICKS;
					final boolean exhaustedBeforeJump = s.exhausted;
					spend(s, JUMP_STAMINA_COST);
					tiredness(horse, s, exhaustedBeforeJump);
				}
			}
		}
		if (s.jumpBuffer > 0) {
			s.jumpBuffer--;
		}

		// Steering: the horse heads where the rider looks, offset 45 degrees left or right while A/D are held with W (90,
		// across the view, with A or D alone); the view itself is never moved. Standing still with no input leaves the rider free to look around. The head leads,
		// the horse commits its weight (banks, side-steps when slow), then the body turns.
		// Cutting hard, it has more grip and turns, commits and checks its turn faster.
		final float cut = s.cut;
		final float speedFraction = gallopFraction(s.speed);
		final float metresPerSecond = Math.abs(s.speed) * (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * (TERMINAL_VELOCITY_FACTOR * 20.0F);
		final float maxTurn = maxTurnRate(metresPerSecond, cut);
		float desired = 0.0F;
		if (forward || back || steer != 0 || Math.abs(s.speed) > 0.02F) {
			desired = Mth.clamp(Mth.wrapDegrees(targetYaw - horse.getYRot()) * Mth.lerp(cut, TURN_GAIN, CUT_TURN_GAIN) / maxTurn, -1.0F, 1.0F);
		}
		s.headLead += (desired * Mth.lerp(speedFraction, HEAD_LEAD_STILL, HEAD_LEAD_GALLOP) - s.headLead) * HEAD_LEAD_RESPONSE;
		final float shift = Mth.lerp(cut, Mth.lerp(speedFraction, WEIGHT_SHIFT_STILL, WEIGHT_SHIFT_GALLOP), CUT_WEIGHT_SHIFT);
		s.turnIntent += Mth.clamp(desired - s.turnIntent, -shift, shift);
		final float accel = maxTurn * Mth.lerp(cut, TURN_ACCEL, CUT_TURN_ACCEL);
		s.yawVelocity += Mth.clamp(maxTurn * s.turnIntent * Math.abs(s.turnIntent) - s.yawVelocity, -accel, accel);
		s.heading = horse.getYRot() + s.yawVelocity;
		s.bankRate = s.turnIntent * maxTurn;
		// Weight committed ahead of the rotation pushes the horse a little sideways into the turn.
		s.sidestep = (s.turnIntent - s.yawVelocity / maxTurn) * SIDESTEP * (1.0F - Math.min(speedFraction * 1.4F, 1.0F));

		// The guard also watches a drop off a step (not a jump: a leap is meant to clear things).
		if (AVOID_DANGER && !s.swimming && s.ledgeTicks == 0 && (horse.onGround() || !s.leapt)) {
			Awareness.guard(horse, s);
		}
		if (profile) {
			lastTickNanos = System.nanoTime() - started;
		}
	}

	/**
	 * Jumping up a ledge in its stride: the horse keeps coming at its pace, sinks onto its haunches over the last few
	 * ticks, and takes off where its arc brings the body above the lip just as its chest reaches the face; in the air it
	 * keeps that forward speed, then walks on from the top. Letting go of forward or turning away calls it off.
	 */
	private static void ledgeJump(final AbstractHorse horse, final RideState s, final boolean forward) {
		s.ledgeTicks++;
		final float yaw = s.ledgeYaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(yaw);
		final double fz = Mth.cos(yaw);
		if (s.ledgeAir) {
			if (horse.onGround() || s.ledgeTicks > LEDGE_APPROACH_TICKS + 40) {
				endLedge(s);
				return;
			}
			horse.setDeltaMovement(fx * s.ledgeForward, horse.getDeltaMovement().y, fz * s.ledgeForward);
			return;
		}
		// The run-up.
		final double top = Awareness.ledge(horse, s.ledgeYaw, LEDGE_REACH);
		if (!forward || !horse.onGround() || Double.isNaN(top) || s.ledgeTicks > LEDGE_APPROACH_TICKS
			|| Math.abs(Mth.wrapDegrees(s.riderYaw - s.ledgeYaw)) > 60.0F) {
			endLedge(s);
			return;
		}
		s.speed = Math.min(s.speed, GAIT_SPEED[TROT]);
		// This tick's travel: the carried velocity plus this tick's push.
		final Vec3 movement = horse.getDeltaMovement();
		final float push = (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * s.speed;
		final float pace = Math.max((float) (movement.x * fx + movement.z * fz) + push, LEDGE_MIN_FORWARD);
		final double height = top - horse.getY();
		final double gravity = horse.getGravity();
		final float launch = launchSpeed(height + LEDGE_CLEARANCE, gravity);
		final int rise = ticksToRise(launch, height + 0.05, gravity);
		// It bounds up: at least LEDGE_BOUND a tick forward through the air, taking off far enough out that its chest
		// reaches the face near the top of the arc, so it sails over the lip and lands a stride onto the top, instead of
		// popping straight up beside the face.
		final float bound = Math.min(Math.max(pace, LEDGE_BOUND), LEDGE_MAX_FORWARD);
		final float takeoff = bound * ticksToRise(launch, height + LEDGE_CLEARANCE * LEDGE_CROSS_HEIGHT, gravity);
		final float face = Awareness.ledgeFace;
		s.ledgeCrouch = Mth.clamp(1.0F - (face - takeoff) / (pace * LEDGE_CROUCH_TICKS), 0.0F, 1.0F);
		if (face > takeoff + pace * 0.5F && !horse.horizontalCollision) {
			return;
		}
		s.ledgeAir = true;
		s.leapt = true;
		s.ledgeCrouch = 0.0F;
		s.ledgeTop = top;
		// No faster than gets the chest to the face just as the body clears the lip, so it never meets the face rising.
		s.ledgeForward = Mth.clamp(Math.min(bound, face / (rise + 1.0F)), LEDGE_MIN_FORWARD, LEDGE_MAX_FORWARD);
		s.ledgeClimbs++;
		// The push isn't folded into the velocity on leaving the ground; the jump sets the motion itself.
		s.jumpedOff = true;
		final boolean exhaustedBefore = s.exhausted;
		spend(s, LEDGE_STAMINA_COST);
		tiredness(horse, s, exhaustedBefore);
		hoofSound(horse, SoundEvents.HORSE_JUMP);
		horse.setDeltaMovement(fx * s.ledgeForward, launch, fz * s.ledgeForward);
	}

	private static void endLedge(final RideState s) {
		s.ledgeTicks = 0;
		s.ledgeAir = false;
		s.ledgeCrouch = 0.0F;
	}

	/** Takeoff speed whose arc (vanilla gravity and drag) peaks at least {@code height} up. */
	private static float launchSpeed(final double height, final double gravity) {
		for (float launch = 0.3F; launch < 1.5F; launch += 0.01F) {
			double y = 0.0;
			double vy = launch;
			while (vy > 0.0) {
				y += vy;
				vy = (vy - gravity) * 0.98;
			}
			if (y >= height) {
				return launch;
			}
		}
		return 1.5F;
	}

	/** Ticks for an arc launched at {@code launch} to climb {@code height}. */
	private static int ticksToRise(final float launch, final double height, final double gravity) {
		double y = 0.0;
		double vy = launch;
		int ticks = 1;
		while ((y += vy) < height && vy > 0.0) {
			vy = (vy - gravity) * 0.98;
			ticks++;
		}
		return ticks;
	}

	/** The jump sound of a ledge jump, heard by the rider. */
	private static void hoofSound(final AbstractHorse horse, final SoundEvent sound) {
		if (CLIMB_SOUND_VOLUME > 0.0F) {
			horse.level().playLocalSound(
				horse.getX(), horse.getY(), horse.getZ(), sound, horse.getSoundSource(), CLIMB_SOUND_VOLUME, 0.9F + horse.getRandom().nextFloat() * 0.2F, false
			);
		}
	}

	/** Turn rate limit, degrees per tick, at a ground speed in metres (blocks) per second: grip-limited above a walk. */
	static float maxTurnRate(final float metresPerSecond) {
		return maxTurnRate(metresPerSecond, 0.0F);
	}

	/** {@link #maxTurnRate(float)} cutting this hard (0..1): sat back on its haunches, the horse has more grip and pivots faster. */
	private static float maxTurnRate(final float metresPerSecond, final float cut) {
		return Math.min(TURN_RATE_STILL * Mth.lerp(cut, 1.0F, CUT_RATE_SCALE),
			LATERAL_GRIP * Mth.lerp(cut, 1.0F, CUT_GRIP) / Math.max(metresPerSecond, 0.1F) * (Mth.RAD_TO_DEG / 20.0F));
	}

	/**
	 * Where the horse's steering takes it: how far to the right (blocks; negative is left) of the line along
	 * {@code lineYaw} through where it is now it has got by the time it is {@code distance} along that line, if it goes
	 * round something {@code offset} degrees off the line from now on. The same weight-shift model as the ride tick (hard
	 * cuts included), at the current speed, starting from how it is turning now. For planning ways round (Awareness); no
	 * allocation.
	 */
	static float sideAfter(final AbstractHorse horse, final RideState s, final float lineYaw, final float offset, final float distance) {
		final float blocksPerUnit = (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * TERMINAL_VELOCITY_FACTOR;
		final float speed = Math.max(s.speed, GAIT_SPEED[WALK]) * blocksPerUnit;
		final float shift = Mth.lerp(gallopFraction(s.speed), WEIGHT_SHIFT_STILL, WEIGHT_SHIFT_GALLOP);
		float heading = Mth.wrapDegrees(horse.getYRot() - lineYaw);
		float avoid = s.avoidOffset;
		float intent = s.turnIntent;
		float velocity = s.yawVelocity;
		float cut = s.cut;
		float along = 0.0F;
		float side = 0.0F;
		for (int tick = 0; tick < 60 && along < distance; tick++) {
			avoid += Mth.clamp(offset - avoid, -AVOID_RATE, AVOID_RATE);
			final float ask = HARD_CUT ? smoothstep((Math.abs(avoid - heading) - CUT_START) / (CUT_FULL - CUT_START)) : 0.0F;
			cut = ask >= cut ? ask : Math.max(ask, cut - CUT_RELEASE);
			final float maxTurn = maxTurnRate(speed * 20.0F, cut);
			final float accel = maxTurn * Mth.lerp(cut, TURN_ACCEL, CUT_TURN_ACCEL);
			final float desired = Mth.clamp((avoid - heading) * Mth.lerp(cut, TURN_GAIN, CUT_TURN_GAIN) / maxTurn, -1.0F, 1.0F);
			intent += Mth.clamp(desired - intent, -Mth.lerp(cut, shift, CUT_WEIGHT_SHIFT), Mth.lerp(cut, shift, CUT_WEIGHT_SHIFT));
			velocity += Mth.clamp(maxTurn * intent * Math.abs(intent) - velocity, -accel, accel);
			heading += velocity;
			along += speed * Mth.cos(heading * Mth.DEG_TO_RAD);
			side += speed * Mth.sin(heading * Mth.DEG_TO_RAD);
		}
		return side;
	}

	/** A hard cut from a trot or faster scuffs up the ground under the hind hooves, thrown out of the turn ({@code side} 1 right). */
	private static void scuff(final AbstractHorse horse, final RideState s, final int side) {
		if (s.speed < GAIT_SPEED[TROT] * 0.8F) {
			return;
		}
		final BlockState ground = horse.getBlockStateOn();
		if (ground.isAir() || ground.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE) {
			return;
		}
		final Level level = horse.level();
		final RandomSource random = horse.getRandom();
		if (CUT_SOUND_VOLUME > 0.0F) {
			level.playLocalSound(
				horse.getX(), horse.getY(), horse.getZ(), ground.getSoundType().getStepSound(), horse.getSoundSource(), CUT_SOUND_VOLUME, 0.7F + random.nextFloat() * 0.1F, false
			);
		}
		final float yaw = horse.getYRot() * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(yaw);
		final double fz = Mth.cos(yaw);
		// Thrown out of the turn: a cut to the right (larger yaw) throws the dirt to the left, (cos, sin) of the yaw.
		final double ox = Mth.cos(yaw) * side;
		final double oz = Mth.sin(yaw) * side;
		final BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (int i = 0; i < 10; i++) {
			final double spread = random.nextDouble() * 0.6 - 0.3;
			level.addParticle(particle,
				horse.getX() - fx * (HIND_HOOVES - spread), horse.getY() + 0.1, horse.getZ() - fz * (HIND_HOOVES - spread),
				ox * (0.15 + random.nextDouble() * 0.15), 0.1 + random.nextDouble() * 0.15, oz * (0.15 + random.nextDouble() * 0.15));
		}
	}

	/**
	 * Out of its depth the horse floats with its back at the waterline and its head up. Vanilla water physics are
	 * bypassed for a ridden horse (see the horse mixin), so this sets the vertical motion each tick before travel: a
	 * settle toward the floating depth, or a heave up a bank no more than a block above the water.
	 *
	 * @param pressing riding forward (or jumping) at whatever is ahead
	 */
	private static void swim(final AbstractHorse horse, final RideState s, final double depth, final boolean pressing) {
		final Vec3 movement = horse.getDeltaMovement();
		if (s.bankTicks == 0 && pressing && --s.bankLook <= 0 && (horse.horizontalCollision || Awareness.bankAhead(horse))) {
			// Something to climb out onto ahead; a bank too high is looked at again only every few ticks.
			s.bankLook = 4;
			final double top = Awareness.bank(horse, horse.getYRot(), depth);
			if (!Double.isNaN(top)) {
				s.bankTicks = 1;
				s.bankFrom = horse.getY();
				s.bankTop = top + BANK_CLEARANCE;
				s.bankYaw = horse.getYRot();
				s.bankDuration = BANK_HEAVE_TICKS + BANK_HEAVE_TICKS_PER_BLOCK * (float) (s.bankTop - s.bankFrom);
			}
		}
		if (s.bankTicks > 0) {
			heave(horse, s);
			return;
		}
		horse.setDeltaMovement(movement.x, Mth.clamp((depth - SWIM_FLOAT_DEPTH) * SWIM_BUOYANCY, -SWIM_SINK_MAX, SWIM_RISE_MAX), movement.z);
	}

	/**
	 * Heaving out up a bank: the body rises to the bank's top in one smooth motion (eased in and out) while it presses
	 * forward against the bank, so it moves onto the top as soon as it clears the lip; then it keeps pressing forward
	 * until its hooves are on the bank (or gives up after BANK_OVER_TICKS and drops back into the water).
	 */
	private static void heave(final AbstractHorse horse, final RideState s) {
		final float t = s.bankTicks++;
		final float yaw = s.bankYaw * Mth.DEG_TO_RAD;
		final double forwardX = -Mth.sin(yaw) * BANK_FORWARD;
		final double forwardZ = Mth.cos(yaw) * BANK_FORWARD;
		if (t >= s.bankDuration + BANK_OVER_TICKS) {
			s.bankTicks = 0;
			return;
		}
		// Over the top, it settles onto the bank.
		double vy = -horse.getGravity();
		if (t < s.bankDuration) {
			// Smoothstep from where it started to the top: the rise this tick.
			vy = (s.bankTop - s.bankFrom) * (smoothstep((t + 1.0F) / s.bankDuration) - smoothstep(t / s.bankDuration));
			// Stay on the curve even if the bank held the body back a little.
			vy += Mth.clamp(s.bankFrom + (s.bankTop - s.bankFrom) * smoothstep(t / s.bankDuration) - horse.getY(), -0.05, 0.05);
		}
		horse.setDeltaMovement(forwardX, vy, forwardZ);
	}

	private static float smoothstep(final float t) {
		final float x = Mth.clamp(t, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/**
	 * On the ground part of each tick's travel comes from that tick's push, which is lost in the air, and an airborne
	 * horse lands with velocity the ground then pushes on top of. Fold the push in on leaving the ground and take it
	 * back out on landing, so walking off a drop or landing a jump keeps the horse's pace instead of dipping and
	 * surging. (Jump takeoffs already fold it in.)
	 */
	private static void keepSpeedThroughDrops(final AbstractHorse horse, final RideState s) {
		final boolean grounded = horse.onGround();
		if (grounded == s.wasGrounded) {
			return;
		}
		s.wasGrounded = grounded;
		if (!grounded && s.jumpedOff) {
			s.jumpedOff = false;
			return;
		}
		s.jumpedOff = false;
		final float push = horse.getSpeed() * (1.0F - AIR_CONTROL) * Math.signum(s.speed);
		if (push == 0.0F) {
			return;
		}
		final float yaw = horse.getYRot() * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(yaw);
		final double fz = Mth.cos(yaw);
		final Vec3 movement = horse.getDeltaMovement();
		double change = grounded ? -push : push;
		if (grounded) {
			// Never take out more than the horse is carrying along its heading.
			final double along = (movement.x * fx + movement.z * fz) * Math.signum(s.speed);
			change = -Math.min(Math.abs(push), Math.max(along, 0.0)) * Math.signum(s.speed);
		}
		horse.setDeltaMovement(movement.x + fx * change, movement.y, movement.z + fz * change);
	}

	/**
	 * A tiring horse huffs on the down of each stride while running; running out of stamina makes it toss its head with
	 * a snort. Heard by the rider (the ride is simulated on their client).
	 */
	private static void tiredness(final AbstractHorse horse, final RideState s, final boolean wasExhausted) {
		final Level level = horse.level();
		if (s.exhausted && !wasExhausted) {
			s.headShakeStart = horse.tickCount;
			level.playLocalSound(
				horse.getX(), horse.getEyeY(), horse.getZ(), SoundEvents.HORSE_BREATHE, horse.getSoundSource(),
				EXHAUSTED_BREATH_VOLUME, 0.95F + horse.getRandom().nextFloat() * 0.1F, false
			);
		}
		final int stride = Mth.floor(horse.walkAnimation.position() * (0.6662F / Mth.TWO_PI) - HUFF_STRIDE_PHASE);
		if (stride == s.lastHuffStride) {
			return;
		}
		s.lastHuffStride = stride;
		if ((s.stamina < HUFF_STAMINA || s.exhausted) && s.speed >= GAIT_SPEED[TROT] && Math.floorMod(stride, HUFF_EVERY_STRIDES) == 0) {
			final float tired = s.exhausted ? 1.0F : 1.0F - s.stamina / HUFF_STAMINA;
			s.huffs++;
			level.playLocalSound(
				horse.getX(), horse.getEyeY(), horse.getZ(), SoundEvents.HORSE_BREATHE, horse.getSoundSource(),
				Mth.lerp(tired, HUFF_VOLUME_MIN, HUFF_VOLUME_MAX), 0.95F + horse.getRandom().nextFloat() * 0.1F, false
			);
		}
	}

	/** Leaf sounds and bits while pushing through foliage, more often the faster the horse goes. */
	private static void rustle(final AbstractHorse horse, final RideState s, final BlockState leaves) {
		if (Math.abs(s.speed) < 0.05F || --s.rustleCooldown > 0) {
			return;
		}
		s.rustleCooldown = 3 + (int) ((1.0F - gallopFraction(s.speed)) * 6.0F);
		final Level level = horse.level();
		final RandomSource random = horse.getRandom();
		// The soft rustle rabbits make hopping through grass.
		level.playLocalSound(
			horse.getX(), horse.getY() + 1.2, horse.getZ(), SoundEvents.RABBIT_JUMP, SoundSource.BLOCKS,
			LEAVES_RUSTLE_VOLUME, ((random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F) * 0.8F, false
		);
		final BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, leaves);
		for (int i = 0; i < 4; i++) {
			level.addParticle(particle, horse.getRandomX(0.8), horse.getY() + 0.8 + random.nextDouble() * 1.2, horse.getRandomZ(0.8), 0.0, 0.0, 0.0);
		}
	}

	/** Called every tick a horse has no rider. */
	public static void tickUnridden(final RideState s) {
		if (s.gait != STOP || s.speed != 0.0F) {
			s.resetMotion();
		}
		if (s.stamina < 1.0F) {
			regenerate(s, STOP);
		}
	}

	/**
	 * Called every client tick for every horse: bank into turns, and take steps and slopes in two beats (forehand, then
	 * hindquarters) with the body pitched to the ground under the hooves. Purely visual; physics is untouched.
	 */
	public static void tickVisual(final AbstractHorse horse, final RideState s) {
		final double dx = horse.getX() - horse.xo;
		final double dy = horse.getY() - horse.yo;
		final double dz = horse.getZ() - horse.zo;
		final double horizontalSq = dx * dx + dz * dz;
		final float bodyYaw = horse.yBodyRot;

		// Bank: the driving client knows where the horse is committing; everyone else infers it from the yaw change.
		final float yawDelta = Mth.wrapDegrees(bodyYaw - s.lastBodyYaw);
		final boolean moved = horizontalSq > 1.0E-6 || dy != 0.0 || yawDelta != 0.0F;
		s.lastBodyYaw = bodyYaw;
		final float turnRate = Float.isNaN(s.bankRate) ? yawDelta : s.bankRate;
		s.bankRate = Float.NaN;
		s.leanO = s.lean;
		s.lean += (Mth.clamp(turnRate * (float) horizontalSq * LEAN_GAIN, -LEAN_MAX, LEAN_MAX) - s.lean) * LEAN_SMOOTHING;

		// The rider sways against surges: back as the horse speeds up, forward as it slows.
		final float groundSpeed = (float) Math.sqrt(horizontalSq);
		final float surge = groundSpeed - s.lastGroundSpeed;
		s.lastGroundSpeed = groundSpeed;
		s.inertiaO = s.inertia;
		final float inertiaTarget = horse.isVehicle() ? Mth.clamp(surge * INERTIA_GAIN, -INERTIA_MAX, INERTIA_MAX) : 0.0F;
		s.inertiaVelocity += (inertiaTarget - s.inertia) * INERTIA_STIFFNESS - s.inertiaVelocity * INERTIA_DAMPING;
		s.inertia = Mth.clamp(s.inertia + s.inertiaVelocity, -INERTIA_MAX, INERTIA_MAX);
		brush(horse, s, groundSpeed, bodyYaw);

		s.pitchO = s.pitch;
		s.jumpPitchO = s.jumpPitch;
		s.heightOffsetO = s.heightOffset;
		s.foreLegO = s.foreLeg;
		s.hindLegO = s.hindLeg;
		final boolean onGround = horse.onGround();
		final double y = horse.getY();
		if (Math.abs(s.heightOffset) > 1.5F || Double.isNaN(s.hind)) {
			// First seen, or moved a long way at once: stand where the body is.
			s.heightOffset = 0.0F;
			s.flying = !onGround;
			carry(s, y);
			s.foreGround = y;
			s.hindGround = y;
		}
		final boolean wasFlying = s.flying;
		if (horse.isPassenger()) {
			s.pitch += -s.pitch * PITCH_SMOOTHING;
			s.heightOffset *= AIR_OFFSET_DECAY;
			s.flying = true;
		} else if (s.bankTicks > 0) {
			// Heaving out of the water: the forehand gets onto the bank first, so the nose is up most mid-heave, and the
			// body levels as the hindquarters come up after it.
			final float progress = Math.min((s.bankTicks - 1) / s.bankDuration, 1.0F);
			s.pitch += (HEAVE_PITCH * Mth.sin(progress * Mth.PI) - s.pitch) * HEAVE_PITCH_EASE;
			final double previous = horse.yo + s.heightOffset;
			s.heightOffset = (float) (previous + (y - previous) * WATER_HEIGHT_SMOOTHING - y);
			s.flying = true;
		} else if (horse.isInWater()) {
			// Afloat or wading: ease height changes (climbing out, bobbing) in world space, and raise the nose as the
			// horse climbs.
			final float climb = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(horizontalSq) + 1.0E-3)) * AIR_PITCH_SCALE;
			s.pitch += (Mth.clamp(climb, -AIR_PITCH_MAX, AIR_PITCH_MAX) - s.pitch) * WATER_PITCH_EASE;
			final double previous = horse.yo + s.heightOffset;
			s.heightOffset = (float) (previous + (y - previous) * WATER_HEIGHT_SMOOTHING - y);
			s.flying = true;
		} else {
			// Stepping off something no deeper than a step, the hooves stay on the ground; a jump or a bigger drop is a
			// flight, until the horse lands.
			if (onGround) {
				s.flying = false;
			} else if (!s.flying && (dy > 0.0 || s.leapt || groundHeight(horse, horse.getX(), y, horse.getZ(), y) < y - STEP_REACH)) {
				s.flying = true;
			}
			if (!s.flying) {
				steps(horse, s, bodyYaw, (float) Math.sqrt(horizontalSq), moved || !s.wasOnGround, wasFlying && dy < -0.15 ? (float) -dy : 0.0F);
			} else {
				// In the air the body tilts with its flight: nose up taking off, level over the top, nose down to land.
				final float flight = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(horizontalSq) + 1.0E-3)) * JUMP_PITCH_SCALE;
				s.jumpPitch += (Mth.clamp(flight, -JUMP_PITCH_DOWN, JUMP_PITCH_UP) - s.jumpPitch) * JUMP_PITCH_SMOOTHING;
				s.pitch += -s.pitch * PITCH_SMOOTHING;
				s.heightOffset -= Mth.clamp(s.heightOffset * (1.0F - AIR_OFFSET_DECAY), -AIR_OFFSET_MAX_STEP, AIR_OFFSET_MAX_STEP);
			}
		}
		if (s.flying) {
			// Off the ground the hooves follow the body, so the next step starts from where it is drawn.
			carry(s, y);
			s.foreLeg *= 0.5F;
			s.hindLeg *= 0.5F;
		}
		if (onGround || horse.isPassenger() || horse.isInWater() || s.bankTicks > 0) {
			s.jumpPitch += -s.jumpPitch * JUMP_PITCH_SMOOTHING;
		}
		// In a jump (or a bigger fall) the legs take its shape: folded up climbing, reaching for the ground coming down.
		s.inAir = s.flying && !onGround && !horse.isPassenger() && !horse.isInWater() && s.bankTicks == 0;
		s.airLegsO = s.airLegs;
		s.airRiseO = s.airRise;
		s.airLegs += Mth.clamp((s.inAir ? 1.0F : 0.0F) - s.airLegs, -AIR_LEGS_OUT, AIR_LEGS_IN);
		s.airRiseEase += (Mth.clamp((float) dy / AIR_LEG_RISE, -1.0F, 1.0F) - s.airRiseEase) * AIR_LEG_PHASE_EASE;
		s.airRise += (s.airRiseEase - s.airRise) * AIR_LEG_PHASE_EASE;
		s.wasOnGround = onGround;
	}

	/**
	 * Pushing through leaves at the rider's chest and face: the branches push the rider back, more the faster the horse
	 * goes, with a shove each time the face meets a new clump, and the rider puts a hand up in front of their face. Looks
	 * at the rider's face and chest just ahead (a few block reads), only for a horse a player rides; everyone sees it.
	 */
	private static void brush(final AbstractHorse horse, final RideState s, final float groundSpeed, final float bodyYaw) {
		s.shieldO = s.shield;
		s.leafPushO = s.leafPush;
		final boolean ridden = horse.getControllingPassenger() instanceof Player;
		if (!ridden && s.shield == 0.0F && s.leafPush == 0.0F && s.leafPushVelocity == 0.0F) {
			return;
		}
		boolean face = false;
		boolean chest = false;
		final float pace = Math.min(groundSpeed / ((float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * TERMINAL_VELOCITY_FACTOR), 1.0F);
		if (RIDE_THROUGH_LEAVES && pace > LEAF_BRUSH_MIN_PACE && horse.getControllingPassenger() instanceof Player rider) {
			final float yaw = bodyYaw * Mth.DEG_TO_RAD;
			final double fx = -Mth.sin(yaw);
			final double fz = Mth.cos(yaw);
			final double eye = rider.getEyeY();
			final int faceY = Mth.floor(eye);
			final int chestY = Mth.floor(eye - LEAF_CHEST_BELOW_EYE);
			for (int i = 0; i < 2; i++) {
				final float ahead = i == 0 ? LEAF_LOOK_NEAR : LEAF_LOOK_FAR;
				final int x = Mth.floor(rider.getX() + fx * ahead);
				final int z = Mth.floor(rider.getZ() + fz * ahead);
				if (!face && horse.level().getBlockState(PROBE.set(x, faceY, z)).is(BlockTags.LEAVES)) {
					face = true;
					// The face meets a new clump: a shove.
					final long clump = BlockPos.asLong(x, faceY, z);
					if (i == 0 && clump != s.lastClump) {
						s.lastClump = clump;
						s.leafPushVelocity += LEAF_PUSH_KICK * pace;
					}
				}
				chest |= horse.level().getBlockState(PROBE.set(x, chestY, z)).is(BlockTags.LEAVES);
			}
		}
		// The hand goes up quickly and comes down once clear.
		final float shield = face ? 1.0F : chest ? LEAF_SHIELD_CHEST : 0.0F;
		s.shield += Mth.clamp(shield - s.shield, -LEAF_SHIELD_DOWN, LEAF_SHIELD_UP);
		final float push = face || chest ? LEAF_PUSH_MAX * pace : 0.0F;
		s.leafPushVelocity += (push - s.leafPush) * LEAF_PUSH_STIFFNESS - s.leafPushVelocity * LEAF_PUSH_DAMPING;
		s.leafPush = Mth.clamp(s.leafPush + s.leafPushVelocity, -LEAF_PUSH_LIMIT * 0.25F, LEAF_PUSH_LIMIT);
	}

	/**
	 * Steps and slopes in two beats: the front and the back of the body each ease toward the ground under their own
	 * hooves, so the forehand goes up (or down) a step first and the hindquarters follow. Each eases in two stages, so
	 * the motion starts and stops softly and never overshoots. The body keeps its weight on the hindquarters and tilts
	 * only a little; the legs make up the rest, folding up onto a step or reaching down for one.
	 *
	 * @param landing fall speed when touching down from a jump or a drop (blocks/tick), else 0
	 */
	private static void steps(final AbstractHorse horse, final RideState s, final float bodyYaw, final float groundSpeed, final boolean probe, final float landing) {
		final double y = horse.getY();
		final float ease = Math.min(STEP_EASE + groundSpeed * STEP_EASE_PER_SPEED, STEP_EASE_MAX);
		if (probe) {
			final float yaw = bodyYaw * Mth.DEG_TO_RAD;
			final double fx = -Mth.sin(yaw);
			final double fz = Mth.cos(yaw);
			// Read the ground as far ahead as the easing lags (two stages: 2(1 - e)/e ticks), so each end of the body
			// moves as its own hooves get to a step, and keeps up on a slope.
			final double lead = Math.min(groundSpeed * 2.0 * (1.0 - ease) / ease, STEP_LEAD_MAX);
			s.foreGround = support(horse, fx, fz, FORE_HOOVES + lead, y);
			s.hindGround = support(horse, fx, fz, lead - HIND_HOOVES, y);
		}
		if (landing > 0.0F) {
			// Touchdown: the body sinks with the impact, then eases back up.
			final float dip = Math.min(landing * LANDING_DIP, LANDING_DIP_MAX) * 2.0F;
			s.foreEase -= dip;
			s.hindEase -= dip;
		}
		s.foreEase += (s.foreGround - s.foreEase) * ease;
		s.fore += (s.foreEase - s.fore) * ease;
		s.hindEase += (s.hindGround - s.hindEase) * ease;
		final double hindBefore = s.hind;
		s.hind += (s.hindEase - s.hind) * ease;
		s.hindRise = (float) (s.hind - hindBefore);

		final double rise = s.fore - s.hind;
		final float tilt = PITCH_MAX * (float) Math.tanh(rise / PITCH_RISE);
		final double body = s.hind + rise * (rise > 0.0 ? BODY_RISE_UP : BODY_RISE_DOWN);
		// Crouching for a ledge jump: haunches down, nose up.
		// Sitting back into a cut does the same, a little less.
		final float crouchPitch = s.ledgeCrouch * LEDGE_CROUCH_PITCH + s.cutSquat * CUT_SQUAT_PITCH;
		// The tilt eases once more on its own, so quick bumps at speed rock the body gently instead of jolting it.
		s.pitch += (tilt + crouchPitch - s.pitch) * TILT_EASE;
		s.heightOffset = (float) (body - y) - s.ledgeCrouch * LEDGE_CROUCH - s.cutSquat * CUT_SQUAT;
		// The legs make up what the tilt doesn't: the front end of the body is below the ground its hooves are going to
		// (fold up onto it) or above it (reach down for it); the hind legs drive while the hindquarters rise.
		final double slope = Math.tan((s.pitch - crouchPitch) * Mth.DEG_TO_RAD);
		s.foreLeg = Mth.clamp((float) (s.fore - body - FORE_HOOVES * slope) / LEG_POSE_REACH, -1.0F, 1.0F);
		s.hindLeg = Mth.clamp((float) (body - HIND_HOOVES * slope - s.hind) / LEG_POSE_REACH + Math.max(s.hindRise, 0.0F) / HIND_DRIVE_SPEED, -1.0F, 1.0F);
	}

	/**
	 * Height of the ground carrying a pair of hooves {@code reach} blocks along the heading (negative behind), averaged
	 * over STEP_FOOTPRINT so a step comes in over a stretch rather than at a point. Ahead of the body the ground may rise
	 * a step for every block (a slope it will climb); over anything deeper than a step the hooves stay level with it.
	 */
	private static double support(final AbstractHorse horse, final double fx, final double fz, final double reach, final double y) {
		final double half = STEP_FOOTPRINT * 0.5;
		final double climb = Math.max(reach - FORE_HOOVES, 0.0);
		return (supportAt(horse, fx, fz, reach - half, y, climb) + supportAt(horse, fx, fz, reach + half, y, climb)) * 0.5;
	}

	private static double supportAt(final AbstractHorse horse, final double fx, final double fz, final double reach, final double y, final double climb) {
		final double ground = groundHeight(horse, horse.getX() + fx * reach, y + climb, horse.getZ() + fz * reach, y);
		return ground < y - STEP_REACH ? y : ground;
	}

	/** Puts both hooves' supports at the drawn body, at rest. */
	private static void carry(final RideState s, final double y) {
		s.fore = y + s.heightOffset;
		s.hind = s.fore;
		s.foreEase = s.fore;
		s.hindEase = s.fore;
		s.hindRise = 0.0F;
	}

	/**
	 * Top of the ground under (x, z), searching from a step above {@code y} down to 1.5 below the hooves (at
	 * {@code feet}). A wall taller than that reads as level with the hooves, so the horse does not rear up against it.
	 */
	private static double groundHeight(final AbstractHorse horse, final double x, final double y, final double z, final double feet) {
		final BlockPos.MutableBlockPos pos = PROBE;
		final int bx = Mth.floor(x);
		final int bz = Mth.floor(z);
		final double step = y + RIDDEN_STEP_HEIGHT + 0.01;
		final int top = Mth.floor(step);
		final int bottom = Mth.floor(feet - 1.5);
		for (int by = top; by >= bottom; by--) {
			final BlockState state = horse.level().getBlockState(pos.set(bx, by, bz));
			if (state.isAir()) {
				continue;
			}
			final VoxelShape shape = state.getCollisionShape(horse.level(), pos);
			if (shape.isEmpty()) {
				continue;
			}
			final double surface = by + shape.max(Direction.Axis.Y);
			return surface > step ? feet : surface;
		}
		return feet - 1.5;
	}

	private static void spend(final RideState s, final float amount) {
		s.stamina -= amount;
		if (s.stamina <= 0.0F) {
			s.stamina = 0.0F;
			s.exhausted = true;
			if (s.gait == GALLOP) {
				s.gait = CANTER;
			}
		}
	}

	private static void regenerate(final RideState s, final int gait) {
		s.stamina = Math.min(s.stamina + STAMINA_REGEN[gait], 1.0F);
		if (s.exhausted && s.stamina >= STAMINA_RECOVERED) {
			s.exhausted = false;
		}
	}

	/** Re-engaging forward while still moving resumes the gait that matches current momentum. */
	private static int gaitForSpeed(final float speed) {
		int gait = GALLOP;
		while (gait > WALK && GAIT_SPEED[gait] > speed + 0.05F) {
			gait--;
		}
		return gait;
	}
}
