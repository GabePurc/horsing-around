package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * backs up, releasing W lets the horse ease to a stop. With the mouse the horse heads where the camera looks; A/D turn
 * the horse and the camera swings around behind it. Space jumps immediately, harder the faster the horse is going.
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
		final boolean forward = in.forward();
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
		// A climb out of the water carries on until the hooves are on the bank.
		final boolean climbing = s.climbSpeed > 0.0F && !horse.onGround();
		s.swimming = !horse.onGround() && (climbing || depth > (s.swimming ? 0.5 : WADE_DEPTH));
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
		float targetYaw = rider.getYRot() + steer * STEER_OFFSET;
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
			swim(horse, s, depth, jumps > 0 || forward && horse.horizontalCollision);
		} else {
			s.climbSpeed = 0.0F;
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

		// Steering: the horse heads where the rider looks, offset 45 degrees left or right while A/D are held; the view
		// itself is never moved. Standing still with no input leaves the rider free to look around. The head leads,
		// the horse commits its weight (banks, side-steps when slow), then the body turns.
		final float speedFraction = gallopFraction(s.speed);
		final float metresPerSecond = Math.abs(s.speed) * (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * (TERMINAL_VELOCITY_FACTOR * 20.0F);
		final float maxTurn = maxTurnRate(metresPerSecond);
		float desired = 0.0F;
		if (forward || back || steer != 0 || Math.abs(s.speed) > 0.02F) {
			desired = Mth.clamp(Mth.wrapDegrees(targetYaw - horse.getYRot()) * TURN_GAIN / maxTurn, -1.0F, 1.0F);
		}
		s.headLead += (desired * Mth.lerp(speedFraction, HEAD_LEAD_STILL, HEAD_LEAD_GALLOP) - s.headLead) * HEAD_LEAD_RESPONSE;
		final float shift = Mth.lerp(speedFraction, WEIGHT_SHIFT_STILL, WEIGHT_SHIFT_GALLOP);
		s.turnIntent += Mth.clamp(desired - s.turnIntent, -shift, shift);
		final float accel = maxTurn * TURN_ACCEL;
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
			|| Math.abs(Mth.wrapDegrees(rider(horse) - s.ledgeYaw)) > 60.0F) {
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
		final float takeoff = pace * rise;
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
		s.ledgeForward = Mth.clamp(Math.min(pace, face / (rise + 1.0F)), LEDGE_MIN_FORWARD, 0.3F);
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

	private static float rider(final AbstractHorse horse) {
		return horse.getControllingPassenger() instanceof Player player ? player.getYRot() : horse.getYRot();
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
		return Math.min(TURN_RATE_STILL, LATERAL_GRIP / Math.max(metresPerSecond, 0.1F) * (Mth.RAD_TO_DEG / 20.0F));
	}

	/**
	 * Out of its depth the horse floats with its back at the waterline and its head up. Vanilla water physics are
	 * bypassed for a ridden horse (see the horse mixin), so this sets the vertical motion each tick before travel: a
	 * settle toward the floating depth, or a steadily building climb up a bank.
	 */
	private static void swim(final AbstractHorse horse, final RideState s, final double depth, final boolean heave) {
		final Vec3 movement = horse.getDeltaMovement();
		// Keep climbing a few ticks after the bank is cleared so the hooves get over its lip.
		s.climbLostTicks = heave ? 0 : s.climbLostTicks + 1;
		if (heave || s.climbSpeed > 0.0F && s.climbLostTicks <= 4) {
			s.climbSpeed = Math.min(s.climbSpeed + SWIM_CLIMB_ACCEL, SWIM_CLIMB_SPEED);
			horse.setDeltaMovement(movement.x, s.climbSpeed, movement.z);
			return;
		}
		s.climbSpeed = 0.0F;
		horse.setDeltaMovement(movement.x, Mth.clamp((depth - SWIM_FLOAT_DEPTH) * SWIM_BUOYANCY, -SWIM_SINK_MAX, SWIM_RISE_MAX), movement.z);
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
		} else if (horse.isInWater()) {
			// Afloat or wading: ease height changes (climbing out, bobbing) in world space, and raise the nose as the
			// horse climbs.
			final float climb = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(horizontalSq) + 1.0E-3)) * AIR_PITCH_SCALE;
			s.pitch += (Mth.clamp(climb, -AIR_PITCH_MAX, AIR_PITCH_MAX) - s.pitch) * PITCH_SMOOTHING;
			final double previous = horse.yo + s.heightOffset;
			s.heightOffset = (float) (previous + (y - previous) * WATER_HEIGHT_SMOOTHING - y);
			s.flying = true;
		} else {
			// Stepping off something no deeper than a step, the hooves stay on the ground; a jump or a bigger drop is a
			// flight, until the horse lands.
			if (onGround) {
				s.flying = false;
			} else if (!s.flying && (dy > 0.0 || s.leapt || groundHeight(horse, horse.getX(), y, horse.getZ()) < y - STEP_REACH)) {
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
		if (onGround || horse.isPassenger() || horse.isInWater()) {
			s.jumpPitch += -s.jumpPitch * JUMP_PITCH_SMOOTHING;
		}
		s.wasOnGround = onGround;
	}

	/**
	 * Steps and slopes in two beats: the front and the back of the body each follow the ground under their own hooves
	 * on a spring, so the forehand goes up (or down) a step first and the hindquarters follow with a push. The body
	 * keeps its weight on the hindquarters and tilts only a little; the legs make up the rest, folding up onto a step
	 * or reaching down for one.
	 *
	 * @param landing fall speed when touching down from a jump or a drop (blocks/tick), else 0
	 */
	private static void steps(final AbstractHorse horse, final RideState s, final float bodyYaw, final float groundSpeed, final boolean probe, final float landing) {
		final double y = horse.getY();
		if (probe) {
			final float yaw = bodyYaw * Mth.DEG_TO_RAD;
			final double fx = -Mth.sin(yaw);
			final double fz = Mth.cos(yaw);
			final double lead = Math.min(groundSpeed * STEP_LEAD_TICKS, STEP_LEAD_MAX);
			s.foreGround = support(horse, fx, fz, FORE_HOOVES, lead, y);
			s.hindGround = support(horse, fx, fz, -HIND_HOOVES, lead, y);
		}
		if (landing > 0.0F) {
			// Touchdown: the body sinks with the impact, then the hooves' springs lift it back.
			final float dip = Math.min(landing * LANDING_DIP, LANDING_DIP_MAX);
			s.fore -= dip;
			s.hind -= dip;
		}
		final float stiffness = Mth.lerp(Math.min(groundSpeed / STEP_SPRING_SPEED, 1.0F), STEP_SPRING_WALK, STEP_SPRING_FAST);
		s.foreVelocity = s.foreVelocity * (1.0F - STEP_DAMPING) + (float) (s.foreGround - s.fore) * stiffness;
		s.hindVelocity = s.hindVelocity * (1.0F - STEP_DAMPING) + (float) (s.hindGround - s.hind) * stiffness;
		s.fore += s.foreVelocity;
		s.hind += s.hindVelocity;

		final double rise = s.fore - s.hind;
		final float tilt = Mth.clamp((float) Math.toDegrees(Math.atan2(rise, FORE_HOOVES + HIND_HOOVES)), -PITCH_MAX, PITCH_MAX);
		final double body = s.hind + rise * (rise > 0.0 ? BODY_RISE_UP : BODY_RISE_DOWN);
		// Crouching for a ledge jump: haunches down, nose up.
		final float crouch = s.ledgeCrouch;
		s.pitch = tilt + crouch * LEDGE_CROUCH_PITCH;
		s.heightOffset = (float) (body - y) - crouch * LEDGE_CROUCH;
		// The legs make up what the tilt doesn't: the front end of the body is below the ground its hooves are going to
		// (fold up onto it) or above it (reach down for it); the hind legs drive while the hindquarters rise.
		final double slope = Math.tan(tilt * Mth.DEG_TO_RAD);
		s.foreLeg = Mth.clamp((float) (s.fore - body - FORE_HOOVES * slope) / LEG_POSE_REACH, -1.0F, 1.0F);
		s.hindLeg = Mth.clamp((float) (body - HIND_HOOVES * slope - s.hind) / LEG_POSE_REACH + Math.max(s.hindVelocity, 0.0F) / HIND_DRIVE_SPEED, -1.0F, 1.0F);
	}

	/**
	 * Height of the ground carrying the hooves {@code reach} blocks along the heading (negative behind): the higher of
	 * where they stand and where they are reaching to, so a step up is reached for early but a step down only taken
	 * once past the edge. Over anything deeper than a step the hooves stay level with the body.
	 */
	private static double support(final AbstractHorse horse, final double fx, final double fz, final float reach, final double lead, final double y) {
		final double x = horse.getX() + fx * reach;
		final double z = horse.getZ() + fz * reach;
		double ground = groundHeight(horse, x, y, z);
		if (lead > 0.05) {
			ground = Math.max(ground, groundHeight(horse, x + fx * lead, y, z + fz * lead));
		}
		return ground < y - STEP_REACH ? y : ground;
	}

	/** Puts both hooves' supports at the drawn body, at rest. */
	private static void carry(final RideState s, final double y) {
		s.fore = y + s.heightOffset;
		s.hind = s.fore;
		s.foreVelocity = 0.0F;
		s.hindVelocity = 0.0F;
	}

	/**
	 * Top of the ground under (x, z), searching from a step above the hooves to 1.5 below. A wall taller than a step
	 * reads as flat ground, so the horse does not rear up against it.
	 */
	private static double groundHeight(final AbstractHorse horse, final double x, final double y, final double z) {
		final BlockPos.MutableBlockPos pos = PROBE;
		final int bx = Mth.floor(x);
		final int bz = Mth.floor(z);
		final double step = y + RIDDEN_STEP_HEIGHT + 0.01;
		final int top = Mth.floor(step);
		final int bottom = Mth.floor(y - 1.5);
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
			return surface > step ? y : surface;
		}
		return y - 1.5;
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
