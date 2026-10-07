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
		final boolean forward = in.forward();
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
			target = Math.min(target, SWIM_SPEED);
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
		if (AVOID_DANGER && horse.onGround() && !s.swimming && s.ledgeTicks == 0 && (forward || s.speed > 0.02F)) {
			final float limit = Awareness.look(horse, s, targetYaw);
			target = Math.min(target, limit);
			if (s.speed > limit) {
				rate = Math.max(rate, DECEL_BRAKE);
			}
		} else {
			Awareness.rest(s);
		}
		targetYaw += s.avoidOffset;
		s.speed = s.speed < target ? Math.min(s.speed + rate, target) : Math.max(s.speed - rate, target);

		if (s.speed > CRASH_SPEED && horse.horizontalCollision && !horse.minorHorizontalCollision) {
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

		// Ledges up to 2 blocks: riding at one at a walk or trot, the horse gathers itself and jumps up.
		if (s.ledgeTicks > 0) {
			ledgeJump(horse, s, forward);
		} else if (LEDGE_CLIMB && forward && horse.onGround() && !s.swimming && s.jumpBuffer == 0 && s.speed > 0.0F
			&& s.speed <= GAIT_SPEED[TROT] + 0.05F && (Awareness.nearWall(s) || horse.horizontalCollision)) {
			final double top = Awareness.ledge(horse, horse.getYRot(), LEDGE_REACH);
			if (!Double.isNaN(top)) {
				s.ledgeTicks = 1;
				s.ledgeTop = top;
				s.ledgeYaw = horse.getYRot();
			}
		}

		// Jumping: fires on press (or on landing if pressed just before), no charging.
		if (jumps > 0 && !s.swimming && s.ledgeTicks == 0) {
			s.jumpBuffer = JUMP_BUFFER_TICKS;
		}
		if (horse.onGround()) {
			if (s.jumpRecovery > 0) {
				s.jumpRecovery--;
			} else if (s.jumpBuffer > 0 && AVOID_DANGER && Awareness.refusesJump(s)) {
				// No leaping off a cliff or into lava (a gap with ground beyond is fine).
				s.jumpBuffer = 0;
				Awareness.refuse(horse, s);
			} else if (s.jumpBuffer > 0) {
				float power = JUMP_POWER_STILL + (JUMP_POWER_RUNNING - JUMP_POWER_STILL) * Math.min(Math.max(s.speed, 0.0F) / GAIT_SPEED[CANTER], 1.0F);
				if (s.exhausted) {
					power *= JUMP_POWER_EXHAUSTED;
				}
				s.pendingJump = power;
				s.jumpBuffer = 0;
				s.jumpRecovery = JUMP_RECOVERY_TICKS;
				final boolean exhaustedBeforeJump = s.exhausted;
				spend(s, JUMP_STAMINA_COST);
				tiredness(horse, s, exhaustedBeforeJump);
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
		final float maxTurn = Math.min(TURN_RATE_STILL, LATERAL_GRIP / Math.max(metresPerSecond, 0.1F) * (Mth.RAD_TO_DEG / 20.0F));
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

		if (AVOID_DANGER && horse.onGround() && !s.swimming && s.ledgeTicks == 0) {
			Awareness.guard(horse, s);
		}
	}

	/**
	 * Jumping up a ledge: the horse halts at it and gathers itself for LEDGE_GATHER_TICKS, then jumps in an arc that
	 * peaks LEDGE_CLEARANCE above the lip, carried forward so the hooves come over the edge as it gets there. Letting
	 * go of forward or turning away before takeoff calls it off.
	 */
	private static void ledgeJump(final AbstractHorse horse, final RideState s, final boolean forward) {
		final int tick = ++s.ledgeTicks;
		final float yaw = s.ledgeYaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(yaw);
		final double fz = Mth.cos(yaw);
		s.speed = 0.0F;
		if (tick <= LEDGE_GATHER_TICKS) {
			if (!forward || Math.abs(Mth.wrapDegrees(rider(horse) - s.ledgeYaw)) > 60.0F) {
				s.ledgeTicks = 0;
				return;
			}
			// It pulls up at the ledge to gather itself.
			final Vec3 movement = horse.getDeltaMovement();
			horse.setDeltaMovement(movement.x * 0.3, movement.y, movement.z * 0.3);
			return;
		}
		if (tick == LEDGE_GATHER_TICKS + 1) {
			final double top = Awareness.ledge(horse, s.ledgeYaw, LEDGE_REACH + 0.3F);
			if (Double.isNaN(top) || !horse.onGround()) {
				s.ledgeTicks = 0;
				return;
			}
			final double height = top - horse.getY();
			final double gravity = horse.getGravity();
			final float launch = launchSpeed(height + LEDGE_CLEARANCE, gravity);
			s.ledgeForward = Mth.clamp((Awareness.ledgeFace + 0.3F) / ticksToRise(launch, height + 0.05, gravity), 0.06F, 0.3F);
			s.ledgeTop = top;
			s.ledgeClimbs++;
			// The push isn't folded into the velocity on leaving the ground; the jump sets the motion itself.
			s.jumpedOff = true;
			final boolean exhaustedBefore = s.exhausted;
			spend(s, LEDGE_STAMINA_COST);
			tiredness(horse, s, exhaustedBefore);
			hoofSound(horse, SoundEvents.HORSE_JUMP);
			horse.setDeltaMovement(fx * s.ledgeForward, launch, fz * s.ledgeForward);
			return;
		}
		if (horse.onGround() || tick > LEDGE_GATHER_TICKS + 40) {
			s.ledgeTicks = 0;
			return;
		}
		final Vec3 movement = horse.getDeltaMovement();
		horse.setDeltaMovement(fx * s.ledgeForward, movement.y, fz * s.ledgeForward);
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
	 * Called every client tick for every horse: bank into turns, pitch to the terrain under the hooves, and turn
	 * block step-ups into a smooth climb. Purely visual; physics is untouched.
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
		s.heightOffsetO = s.heightOffset;
		final boolean onGround = horse.onGround();
		if (Math.abs(s.heightOffset) > 1.5F) {
			s.heightOffset = 0.0F;
		}
		if (horse.isPassenger()) {
			s.pitch += -s.pitch * PITCH_SMOOTHING;
			s.heightOffset *= AIR_OFFSET_DECAY;
		} else if (horse.isInWater()) {
			// Afloat or wading: ease height changes (climbing out, bobbing) in world space, and raise the nose as the
			// horse climbs.
			final float climb = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(horizontalSq) + 1.0E-3)) * AIR_PITCH_SCALE;
			s.pitch += (Mth.clamp(climb, -AIR_PITCH_MAX, AIR_PITCH_MAX) - s.pitch) * PITCH_SMOOTHING;
			final double previous = horse.yo + s.heightOffset;
			s.heightOffset = (float) (previous + (horse.getY() - previous) * STEP_SMOOTHING - horse.getY());
		} else if (onGround) {
			if (!s.wasOnGround && dy < -0.15) {
				// Touchdown: the body sinks with the impact, then the ground smoothing lifts it back.
				s.heightOffset -= Math.min((float) -dy * LANDING_DIP, LANDING_DIP_MAX);
			}
			if (moved || !s.wasOnGround || Math.abs(s.heightTarget - horse.getY()) > 1.5) {
				final float yaw = bodyYaw * Mth.DEG_TO_RAD;
				final double fx = -Mth.sin(yaw) * HOOF_REACH;
				final double fz = Mth.cos(yaw) * HOOF_REACH;
				final double y = horse.getY();
				final double front = groundHeight(horse, horse.getX() + fx, y, horse.getZ() + fz);
				final double back = groundHeight(horse, horse.getX() - fx, y, horse.getZ() - fz);
				s.pitchTarget = Mth.clamp((float) Math.toDegrees(Math.atan2(front - back, 2.0 * HOOF_REACH)), -PITCH_MAX, PITCH_MAX);
				s.heightTarget = y + Mth.clamp((front - y) * STEP_ANTICIPATION, -0.3, STEP_ANTICIPATION);
			}
			// Gathering for a ledge jump: haunches down, nose up.
			final float crouch = s.ledgeTicks > 0 && s.ledgeTicks <= LEDGE_GATHER_TICKS ? (float) s.ledgeTicks / LEDGE_GATHER_TICKS : 0.0F;
			s.pitch += (s.pitchTarget + crouch * LEDGE_CROUCH_PITCH - s.pitch) * PITCH_SMOOTHING;
			// Smooth in world space so a one-tick step-up of the physics body becomes a climb.
			final double previous = horse.yo + s.heightOffset;
			s.heightOffset = (float) (previous + (s.heightTarget - crouch * LEDGE_CROUCH - previous) * STEP_SMOOTHING - horse.getY());
		} else {
			final float flight = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(horizontalSq) + 1.0E-3)) * AIR_PITCH_SCALE;
			s.pitch += (Mth.clamp(flight, -AIR_PITCH_MAX, AIR_PITCH_MAX) - s.pitch) * PITCH_SMOOTHING;
			s.heightOffset -= Mth.clamp(s.heightOffset * (1.0F - AIR_OFFSET_DECAY), -AIR_OFFSET_MAX_STEP, AIR_OFFSET_MAX_STEP);
		}
		s.wasOnGround = onGround;
	}

	/**
	 * Top of the ground under (x, z), searching from one block above the hooves to 1.5 below. A wall taller than a
	 * step reads as flat ground, so the horse does not rear up against it.
	 */
	private static double groundHeight(final AbstractHorse horse, final double x, final double y, final double z) {
		final BlockPos.MutableBlockPos pos = PROBE;
		final int bx = Mth.floor(x);
		final int bz = Mth.floor(z);
		final int top = Mth.floor(y + 1.05);
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
			return surface > y + 1.05 ? y : surface;
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
