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
import net.minecraft.world.phys.shapes.Shapes;
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

		// Water: wade while the hooves reach the bottom (slower the deeper it is), swim once they don't. A horse that
		// doesn't float (a skeleton horse) keeps vanilla's water physics and walks along the bottom.
		final double depth = horse.isInWater() && Mounts.floats(horse) ? horse.getFluidHeight(FluidTags.WATER) : 0.0;
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
		final BlockState leaves = Foliage.leavesOpen(horse.level()) ? Foliage.leavesIn(horse) : null;
		if (leaves != null) {
			target *= LEAVES_SPEED_FACTOR;
			rustle(horse, s, leaves);
		}

		// The horse has a say: on solid ground it slows for walls, stops short of drops that would hurt it and of
		// hazards, and from a trot steers round obstacles it can pass. Swimming, jumps and climbs are left alone.
		final int steer = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
		float targetYaw = rider.getYRot() + steer * (across ? ACROSS_OFFSET : STEER_OFFSET);
		s.riderYaw = targetYaw;
		if (AVOID_DANGER && horse.onGround() && !s.swimming && s.ledgeTicks == 0 && s.bankTicks == 0 && (forward || s.speed > 0.02F)) {
			final float limit = Awareness.look(horse, s, targetYaw, forward);
			target = Math.min(target, limit);
			if (s.speed > limit) {
				rate = Math.max(rate, DECEL_BRAKE);
			}
		} else {
			Awareness.rest(s);
		}
		targetYaw += s.avoidOffset;

		// Hard cuts: the further off it the rider looks, the more the horse sits back and slows to cut round tighter. Only
		// riding on: a horse let go of (or braked) comes round to the view as it stops, but doesn't sit back to cut.
		final float turn = Math.abs(Mth.wrapDegrees(targetYaw - horse.getYRot()));
		final boolean footing = horse.onGround() && !s.swimming && s.ledgeTicks == 0 && forward;
		// (Going round something is the horse's own doing, planned at a pace it can turn at: heading off the rider's line
		// for it doesn't sit it back into a cut, only the rider looking away does.)
		final float cutTurn = Math.min(turn, Math.abs(Mth.wrapDegrees(s.riderYaw - horse.getYRot())));
		final float ask = HARD_CUT && footing ? smoothstep((cutTurn - CUT_START) / (CUT_FULL - CUT_START)) : 0.0F;
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
		// (Let go of, it stands up out of the cut as it stops instead of standing squatted while the cut eases off.)
		s.cutSquat += ((forward ? s.cut : 0.0F) - s.cutSquat) * CUT_SQUAT_EASE;

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
		} else if (depth > 0.0 && (forward || jumps > 0)) {
			// Wading at a bank too high to step up onto: it heaves itself out as a swimming horse does (no leap out of the water).
			if (lookForBank(horse, s, depth, true)) {
				heave(horse, s);
			}
		}

		// Ledges up to 2 blocks: riding at one at a walk or trot, the horse jumps up it in its stride; pressing jump at one
		// (standing at its face, say) asks for the same jump, since a plain jump can't clear it. Not out of the water.
		final boolean asked = (jumps > 0 || s.jumpBuffer > 0) && s.jumpRecovery == 0;
		// (Asked while riding at it, from further out: the press waits for the ledge's jump instead of hopping into its face.)
		final float reach = asked && forward && s.speed >= GAIT_SPEED[WALK] * 0.5F ? LEDGE_ASKED_REACH : LEDGE_REACH;
		if (s.ledgeTicks > 0) {
			ledgeJump(horse, s, forward);
		} else if (LEDGE_CLIMB && horse.onGround() && !s.swimming && depth == 0.0 && s.bankTicks == 0
			&& (asked || forward && s.speed > 0.0F && s.speed <= GAIT_SPEED[TROT] + 0.05F) && Awareness.ledgeAhead(horse, reach)) {
			final double top = Awareness.ledge(horse, horse.getYRot(), reach);
			if (!Double.isNaN(top)) {
				s.ledgeTicks = 1;
				s.ledgeAir = false;
				s.ledgeAsked = asked;
				s.ledgeReach = reach;
				s.ledgeTop = top;
				// (Toward where it lands: straight on, a little to one side, or straight up the face.)
				s.ledgeYaw = Awareness.ledgeClimbYaw;
				s.jumpBuffer = 0;
				ledgeJump(horse, s, forward);
			}
		}

		// Jumping: fires on press (or on landing if pressed just before), no charging.
		if (jumps > 0 && !s.swimming && s.ledgeTicks == 0 && s.bankTicks == 0) {
			s.jumpBuffer = JUMP_BUFFER_TICKS;
		}
		if (horse.onGround() && s.bankTicks == 0) {
			if (s.jumpRecovery > 0) {
				s.jumpRecovery--;
			} else if (s.jumpBuffer > 0) {
				float power = JUMP_POWER_STILL + (JUMP_POWER_RUNNING - JUMP_POWER_STILL) * Math.min(Math.max(s.speed, 0.0F) / GAIT_SPEED[CANTER], 1.0F);
				if (s.exhausted) {
					power *= JUMP_POWER_EXHAUSTED;
				}
				// A fence, a wall, anything up to a hurdle's height in reach: high enough and far enough to clear it.
				final double hurdle = Awareness.hurdle(horse, horse.getYRot(), HURDLE_REACH);
				if (!Double.isNaN(hurdle)) {
					final double gravity = horse.getGravity();
					final float lift = launchSpeed(hurdle - horse.getY() + HURDLE_CLEARANCE, gravity);
					// It can reach the face at any time, but only gets past it while above the top: from when it rises past
					// the top until it comes back down to it.
					final int over = ticksAbove(lift, hurdle - horse.getY(), gravity);
					final int up = ticksToClimbBallistic(lift, hurdle - horse.getY(), gravity);
					final float rad = horse.getYRot() * Mth.DEG_TO_RAD;
					final Vec3 movement = horse.getDeltaMovement();
					final float pace = (float) (movement.x * -Mth.sin(rad) + movement.z * Mth.cos(rad))
						+ (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * Math.max(s.speed, 0.0F);
					s.jumpLift = lift;
					final float needed = Math.max((Awareness.hurdleClear + HURDLE_MARGIN) / Math.max(over, 1),
						(Awareness.hurdleClear - Awareness.hurdleFace + HURDLE_MARGIN) / Math.max(over - up, 1));
					s.hurdleForward = Mth.clamp(needed, pace, HURDLE_MAX_FORWARD);
					s.hurdleYaw = horse.getYRot();
					s.hurdles++;
				} else if (AVOID_DANGER && (Awareness.hurdleUnsafe || Awareness.refusesJump(horse, s, power))) {
					// No leaping off a cliff, into lava, or so far down a slope it would get hurt (a gap is fine), nor over
					// a fence into any of those.
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
		// Over a hurdle, held at its speed until it lands, so meeting the face as it rises doesn't stop it dead (and should
		// it come down on top of the fence or wall, on until it is off it).
		if (s.hurdleForward > 0.0F) {
			if (horse.onGround() && !s.leapt && !Awareness.onHurdle(horse)) {
				s.hurdleForward = 0.0F;
			} else {
				final float rad = s.hurdleYaw * Mth.DEG_TO_RAD;
				final double over = Math.max(s.hurdleForward - (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * Math.max(s.speed, 0.0F) * AIR_CONTROL, 0.0F);
				horse.setDeltaMovement(-Mth.sin(rad) * over, horse.getDeltaMovement().y, Mth.cos(rad) * over);
			}
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
		if (AVOID_DANGER && !s.swimming && s.ledgeTicks == 0 && s.bankTicks == 0 && (horse.onGround() || !s.leapt)) {
			Awareness.guard(horse, s);
		}
		if (profile) {
			lastTickNanos = System.nanoTime() - started;
		}
	}

	/**
	 * Jumping up a ledge in its stride: the horse keeps coming at its pace, sinks onto its haunches over the last few
	 * ticks, and takes off where its climb brings the body above the lip just as its chest reaches the face. The climb is
	 * a heave, not a pop: the hind legs' push builds it over a few ticks and it slows gently toward the top, then the
	 * horse comes down onto the ledge and walks on. In the air it keeps its forward speed. Letting go of forward or
	 * turning away calls it off, unless the rider asked for it with jump; asked from a standstill, it gathers itself
	 * where it stands and goes.
	 */
	private static void ledgeJump(final AbstractHorse horse, final RideState s, final boolean forward) {
		s.ledgeTicks++;
		final float yaw = s.ledgeYaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(yaw);
		final double fz = Mth.cos(yaw);
		// The rider's push still acts in the air (air control); the jump's forward speed includes it.
		final float airPush = (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * Math.max(s.speed, 0.0F) * AIR_CONTROL;
		final double lighter = horse.getGravity() * LEDGE_LIFT_GRAVITY;
		if (s.ledgeAir) {
			if (horse.onGround() || s.ledgeTicks > LEDGE_APPROACH_TICKS + 40) {
				endLedge(s);
				// Up on top it settles before it will jump again, and a press made on the way up is spent.
				s.jumpRecovery = LEDGE_SETTLE_TICKS;
				s.jumpBuffer = 0;
				return;
			}
			double vy = horse.getDeltaMovement().y;
			if (s.ledgeLift > 0.0F) {
				final double climb = climbSpeed(s.ledgeLift, ++s.ledgeAirTicks, lighter);
				// Over the top (or a head knocked on something): its full weight brings it down onto the ledge.
				if (climb <= 0.0 || horse.verticalCollision) {
					s.ledgeLift = 0.0F;
				} else {
					vy = climb;
				}
			}
			final double forwardSpeed = Math.max(s.ledgeForward - airPush, 0.0F);
			horse.setDeltaMovement(fx * forwardSpeed, vy, fz * forwardSpeed);
			return;
		}
		// The run-up.
		final double top = Awareness.ledge(horse, s.ledgeYaw, Math.max(s.ledgeReach, LEDGE_REACH));
		if (!(forward || s.ledgeAsked) || !horse.onGround() || Double.isNaN(top) || s.ledgeTicks > LEDGE_APPROACH_TICKS
			|| !s.ledgeAsked && Math.abs(Mth.wrapDegrees(s.riderYaw - s.ledgeYaw)) > LEDGE_FACE_ANGLE + 10.0F) {
			endLedge(s);
			return;
		}
		s.speed = Math.min(s.speed, GAIT_SPEED[TROT]);
		// This tick's travel: the carried velocity plus this tick's push.
		final Vec3 movement = horse.getDeltaMovement();
		final float push = (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * s.speed;
		final float pace = Math.max((float) (movement.x * fx + movement.z * fz) + push, LEDGE_MIN_FORWARD);
		final double height = top - horse.getY();
		final float lift = liftFor(height + LEDGE_CLEARANCE, lighter);
		final int rise = ticksToClimb(lift, height + 0.05, lighter);
		// It bounds up: at least LEDGE_BOUND a tick forward through the air, taking off far enough out that its chest
		// reaches the face once the body is over the lip, so it carries over the lip and lands a stride onto the top,
		// instead of rising straight up beside the face.
		final float bound = Math.min(Math.max(pace, LEDGE_BOUND), LEDGE_MAX_FORWARD);
		final float takeoff = bound * ticksToClimb(lift, height + LEDGE_CLEARANCE * LEDGE_CROSS_HEIGHT, lighter);
		final float face = Awareness.ledgeFace;
		// Asked for from (nearly) a standstill, it gathers itself where it stands.
		final boolean standing = s.ledgeAsked && s.speed < GAIT_SPEED[WALK] * 0.5F;
		final boolean there = standing || face <= takeoff + pace * 0.5F || horse.horizontalCollision;
		// It sinks onto its haunches over its last LEDGE_CROUCH_TICKS of run-up (and never faster), and goes once down.
		final float crouch = there ? 1.0F : Mth.clamp(1.0F - (face - takeoff) / (pace * LEDGE_CROUCH_TICKS), 0.0F, 1.0F);
		s.ledgeCrouch = Math.min(crouch, s.ledgeCrouch + 1.0F / LEDGE_CROUCH_TICKS);
		if (!there || s.ledgeCrouch < 0.999F) {
			return;
		}
		s.ledgeAir = true;
		s.leapt = true;
		s.ledgeCrouch = 0.0F;
		s.ledgeTop = top;
		s.ledgeLift = lift;
		s.ledgeAirTicks = 0;
		// No faster than gets the chest to the face just as the body clears the lip, so it never meets the face rising.
		s.ledgeForward = Mth.clamp(Math.min(bound, face / (rise + 1.0F)), LEDGE_MIN_FORWARD, LEDGE_MAX_FORWARD);
		s.ledgeClimbs++;
		s.ledgeTakeoffX = horse.getX();
		s.ledgeTakeoffZ = horse.getZ();
		s.ledgeTakeoffTick = horse.tickCount;
		// The push isn't folded into the velocity on leaving the ground; the jump sets the motion itself.
		s.jumpedOff = true;
		final boolean exhaustedBefore = s.exhausted;
		spend(s, LEDGE_STAMINA_COST);
		tiredness(horse, s, exhaustedBefore);
		hoofSound(horse, SoundEvents.HORSE_JUMP);
		final double forwardSpeed = Math.max(s.ledgeForward - airPush, 0.0F);
		horse.setDeltaMovement(fx * forwardSpeed, climbSpeed(lift, 0, lighter), fz * forwardSpeed);
	}

	private static void endLedge(final RideState s) {
		s.ledgeTicks = 0;
		s.ledgeAir = false;
		s.ledgeAsked = false;
		s.ledgeCrouch = 0.0F;
		s.ledgeLift = 0.0F;
	}

	/** Takeoff speed whose arc (gravity and drag as vanilla's) peaks at least {@code height} up. */
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

	/** Ticks for an arc launched at {@code launch} to rise {@code height} (all of its rise, if it never gets there). */
	private static int ticksToClimbBallistic(final float launch, final double height, final double gravity) {
		double y = 0.0;
		double vy = launch;
		int tick = 0;
		while (tick < 60 && vy > 0.0 && y < height) {
			y += vy;
			vy = (vy - gravity) * 0.98;
			tick++;
		}
		return tick;
	}

	/** Ticks an arc launched at {@code launch} stays at least {@code height} up (counted to when it comes back down to it). */
	private static int ticksAbove(final float launch, final double height, final double gravity) {
		double y = 0.0;
		double vy = launch;
		int tick = 0;
		while (tick < 60 && (vy > 0.0 || y >= height)) {
			y += vy;
			vy = (vy - gravity) * 0.98;
			tick++;
		}
		return tick;
	}

	/**
	 * Climb speed (blocks/tick) {@code tick} ticks after taking off up a ledge: the push builds to {@code lift} over
	 * LEDGE_THRUST_TICKS, then the climb slows by {@code lighter} a tick. Zero or less once over the top.
	 */
	private static double climbSpeed(final float lift, final int tick, final double lighter) {
		return tick < LEDGE_THRUST_TICKS ? lift * (tick + 1.0) / LEDGE_THRUST_TICKS : lift - (tick - LEDGE_THRUST_TICKS + 1) * lighter;
	}

	/** Height a climb building to {@code lift} reaches at its top. */
	private static double climbHeight(final float lift, final double lighter) {
		// The push: lift (1 + 2 + ... + T) / T; then lift - k * lighter for every tick it is still rising.
		final int slowing = (int) Math.ceil(lift / lighter) - 1;
		return lift * (LEDGE_THRUST_TICKS + 1) * 0.5 + slowing * (lift - lighter * (slowing + 1) * 0.5);
	}

	/** The least climb speed whose climb tops out at least {@code height} up. */
	private static float liftFor(final double height, final double lighter) {
		float lo = 0.0F;
		float hi = 1.5F;
		for (int i = 0; i < 16; i++) {
			final float mid = (lo + hi) * 0.5F;
			if (climbHeight(mid, lighter) >= height) {
				hi = mid;
			} else {
				lo = mid;
			}
		}
		return hi;
	}

	/** Ticks for a climb building to {@code lift} to get {@code height} up (or to its top, if lower). */
	private static int ticksToClimb(final float lift, final double height, final double lighter) {
		double y = 0.0;
		int tick = 0;
		for (double vy = climbSpeed(lift, 0, lighter); vy > 0.0 && tick < 60; vy = climbSpeed(lift, tick, lighter)) {
			y += vy;
			tick++;
			if (y >= height) {
				break;
			}
		}
		return tick;
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
		if (s.bankTicks > 0 || pressing && lookForBank(horse, s, depth, false)) {
			heave(horse, s);
			return;
		}
		final Vec3 movement = horse.getDeltaMovement();
		horse.setDeltaMovement(movement.x, Mth.clamp((depth - SWIM_FLOAT_DEPTH) * SWIM_BUOYANCY, -SWIM_SINK_MAX, SWIM_RISE_MAX), movement.z);
	}

	/**
	 * Something to climb out onto ahead, no more than a block above the water: starts the heave out up it. A bank too high
	 * is looked at again only every few ticks. Wading, a bank it can step up onto is walked up instead.
	 */
	private static boolean lookForBank(final AbstractHorse horse, final RideState s, final double depth, final boolean wading) {
		if (--s.bankLook > 0 || !(horse.horizontalCollision || Awareness.bankAhead(horse))) {
			return false;
		}
		s.bankLook = 4;
		final double top = Awareness.bank(horse, horse.getYRot(), depth);
		if (Double.isNaN(top) || wading && top - horse.getY() <= RIDDEN_STEP_HEIGHT) {
			return false;
		}
		s.bankTicks = 1;
		s.bankFrom = horse.getY();
		s.bankTop = top + BANK_CLEARANCE;
		s.bankYaw = horse.getYRot();
		s.bankDuration = BANK_HEAVE_TICKS + BANK_HEAVE_TICKS_PER_BLOCK * (float) (s.bankTop - s.bankFrom);
		s.jumpBuffer = 0;
		return true;
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
		s.lastGroundSpeedO = s.lastGroundSpeed;
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
		if (Math.abs(s.heightOffset) > STEP_SNAP || Double.isNaN(s.hind)) {
			// First seen, or teleported: stand where the body is.
			s.heightOffset = 0.0F;
			s.flying = !onGround;
			carry(s, y, 0.0F);
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
			// body levels as the hindquarters come up after it. It rises no higher than the bank it ends up standing on (the
			// lift past it that gets the box over the lip doesn't show).
			final float progress = Math.min((s.bankTicks - 1) / s.bankDuration, 1.0F);
			s.pitch += (HEAVE_PITCH * Mth.sin(progress * Mth.PI) - s.pitch) * HEAVE_PITCH_EASE;
			final double previous = horse.yo + s.heightOffset;
			s.heightOffset = (float) (Math.min(previous + (y - previous) * WATER_HEIGHT_SMOOTHING, s.bankTop - BANK_CLEARANCE) - y);
			s.flying = true;
		} else if (horse.isInWater() && !onGround) {
			// Afloat: ease height changes (climbing out, bobbing) in world space, and raise the nose as the horse climbs.
			// (Wading, its hooves are on the bottom: steps and slopes as on dry land, and out up a step onto the bank.)
			final float climb = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(horizontalSq) + 1.0E-3)) * AIR_PITCH_SCALE;
			s.pitch += (Mth.clamp(climb, -AIR_PITCH_MAX, AIR_PITCH_MAX) - s.pitch) * WATER_PITCH_EASE;
			final double previous = horse.yo + s.heightOffset;
			s.heightOffset = (float) (previous + (y - previous) * WATER_HEIGHT_SMOOTHING - y);
			s.flying = true;
		} else {
			// Stepping off something no deeper than a step, the hooves stay on the ground; a jump or a bigger drop is a
			// flight, until the horse lands. (A bigger drop under the hind hooves too, from where the body is drawn: down a
			// slope of full blocks the wide box stays on each block's edge and then drops, even a few blocks at a trot, while
			// the drawn body walks on down the slope.)
			final double drawn = horse.yo + s.heightOffsetO;
			final double fx = -Mth.sin(bodyYaw * Mth.DEG_TO_RAD);
			final double fz = Mth.cos(bodyYaw * Mth.DEG_TO_RAD);
			if (onGround && !runningOff(horse, s, drawn, fx, fz, (float) Math.sqrt(horizontalSq))) {
				s.flying = false;
			} else if (!s.flying && (dy > 0.0 || s.leapt || groundHeight(horse, horse.getX(), drawn, horse.getZ(), drawn) < drawn - STEP_REACH
				&& groundHeight(horse, horse.getX() - fx * HIND_HOOVES, drawn, horse.getZ() - fz * HIND_HOOVES, drawn) < drawn - STEP_REACH
				|| runningOff(horse, s, drawn, fx, fz, (float) Math.sqrt(horizontalSq)))) {
				s.flying = true;
			}
			if (!s.flying) {
				steps(horse, s, bodyYaw, (float) Math.sqrt(horizontalSq), moved || !s.wasOnGround, wasFlying ? (float) Math.max(-dy, 0.0) : -1.0F);
			} else {
				// In the air the body tilts with its flight: nose up taking off, level over the top, nose down to land.
				final float flight = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(horizontalSq) + 1.0E-3)) * JUMP_PITCH_SCALE;
				s.jumpPitch += (Mth.clamp(flight, -JUMP_PITCH_DOWN, JUMP_PITCH_UP) - s.jumpPitch) * JUMP_PITCH_SMOOTHING;
				// The ground tilt (a slope, the crouch before a ledge) lets go gently while the flight's tilt takes over.
				s.pitch -= Mth.clamp(s.pitch * PITCH_SMOOTHING, -AIR_PITCH_RELEASE, AIR_PITCH_RELEASE);
				s.heightOffset -= Mth.clamp(s.heightOffset * (1.0F - AIR_OFFSET_DECAY), -AIR_OFFSET_MAX_STEP, AIR_OFFSET_MAX_STEP);
			}
		}
		if (s.flying) {
			// Off the ground the hooves follow the body, so the next step starts from where it is drawn, moving as it moves:
			// at the drawn body's own speed (afloat or heaving out it is eased, and a step up out of the water lifted the box
			// a whole block in a tick, which as a speed launched the body blocks high once it landed).
			carry(s, y, (float) (y + s.heightOffset - horse.yo - s.heightOffsetO));
			s.chestRoom = Float.MAX_VALUE;
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
		if (s.leapt) {
			s.leapLegs = true;
		} else if (s.airLegs <= 0.0F) {
			s.leapLegs = false;
		}
		tail(horse, s, groundSpeed);
		s.wasOnGround = onGround;
		s.drawnRise = (float) (y + s.heightOffset - horse.yo - s.heightOffsetO);
	}

	/**
	 * Running off a drop bigger than a step at a canter or faster (RUN_OFF_SPEED): the front hooves and the front of the
	 * body are over it, so the body carries over the edge in one piece, level, as a short leap, instead of the forehand
	 * pitching down into it while the hindquarters are still on the edge.
	 */
	private static boolean runningOff(final AbstractHorse horse, final RideState s, final double drawn, final double fx, final double fz, final float groundSpeed) {
		return groundSpeed >= RUN_OFF_SPEED && !s.leapt
			&& groundHeight(horse, horse.getX() + fx * FORE_HOOVES, drawn, horse.getZ() + fz * FORE_HOOVES, drawn) < drawn - STEP_REACH
			&& groundHeight(horse, horse.getX() + fx * FORE_HOOVES * 0.5, drawn, horse.getZ() + fz * FORE_HOOVES * 0.5, drawn) < drawn - STEP_REACH;
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
		if (pace > LEAF_BRUSH_MIN_PACE && Foliage.leavesOpen(horse.level()) && horse.getControllingPassenger() instanceof Player rider) {
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
	 * The tail swings on its own weight with the drawn body's motion: it trails down as the body rises and floats up as
	 * it falls, goes light when the horse is weightless and heavy when it is thrown up or caught, and flicks on landing,
	 * on a spring. In the air it keeps the lift the stride gave it.
	 */
	private static void tail(final AbstractHorse horse, final RideState s, final float groundSpeed) {
		s.tailLiftO = s.tailLift;
		final double rise = horse.getY() + s.heightOffset - horse.yo - s.heightOffsetO;
		final double gravity = Math.max(horse.getGravity(), 1.0E-3);
		final double weightless = Mth.clamp(-(rise - s.lastDrawnRise) / gravity, -1.0, 1.0);
		s.lastDrawnRise = rise;
		final float topSpeed = (float) horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * TERMINAL_VELOCITY_FACTOR;
		final float pace = Math.min(groundSpeed / Math.max(topSpeed, 1.0E-3F), 1.0F);
		final float target = Mth.clamp((float) (TAIL_WEIGHT * weightless - TAIL_DRAG * rise) + s.airLegs * TAIL_STREAM * pace * pace, -TAIL_MAX_DOWN, TAIL_MAX_UP);
		s.tailVelocity += (target - s.tailLift) * TAIL_SPRING - s.tailVelocity * TAIL_DAMPING;
		s.tailLift = Mth.clamp(s.tailLift + s.tailVelocity, -TAIL_MAX_DOWN, TAIL_MAX_UP);
	}

	/**
	 * Steps, stairs and slopes: the body is a mass carried on its legs. The front and the back of the body each follow the
	 * ground under their own hooves on a critically damped spring (the legs giving and pushing), so the forehand goes up
	 * (or down) a step first and the hindquarters follow, and each moves smoothly however the ground jumps; the ground is
	 * read as far ahead as the spring lags, and the forehand also rises to carry the chest clear of a step before it gets
	 * there. Legs can only give so far: the body never sinks further than that below the ground actually under either pair
	 * of hooves (a hard stop). The body tilts along the line between the two, pivoting at the leg joints; the hooves then
	 * find their own ground under it (see GroundLegs). Touching down from a jump or a drop, the body keeps coming down
	 * with the horse and its legs take the fall, so it sinks a little and comes back up.
	 *
	 * @param landing fall speed when touching down from a jump or a drop this tick (blocks/tick), else negative
	 */
	private static void steps(final AbstractHorse horse, final RideState s, final float bodyYaw, final float groundSpeed, final boolean probe, final float landing) {
		final double y = horse.getY();
		final float w = Math.min(STEP_FREQUENCY + groundSpeed * STEP_FREQUENCY_PER_SPEED, STEP_FREQUENCY_MAX);
		final float yaw = bodyYaw * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(yaw);
		final double fz = Mth.cos(yaw);
		// The underside of the chest, at the body's tilt: how far ahead of the middle it is, and how far above the front hooves.
		final float tiltSin = Mth.sin(s.pitch * Mth.DEG_TO_RAD);
		final double chestAhead = CHEST_AHEAD * Mth.cos(s.pitch * Mth.DEG_TO_RAD);
		final double chestAbove = BELLY_HEIGHT + (CHEST_AHEAD - FORE_HOOVES) * tiltSin;
		if (probe) {
			// Going up, read the ground as far ahead as the spring lags (a target moving steadily is followed (2 / w + 1) ticks
			// behind), so each end of the body rises as its own hooves get to a step, and keeps up on a slope. Going down,
			// each end stays up on the ground under its hooves until they step off (then it falls).
			final double lead = Math.min(groundSpeed * (2.0 / w + 1.0), STEP_LEAD_MAX);
			s.foreGround = Math.max(Math.max(support(horse, fx, fz, FORE_HOOVES + lead, y), support(horse, fx, fz, FORE_HOOVES, y)),
				support(horse, fx, fz, chestAhead + lead, y) - chestAbove);
			s.hindGround = Math.max(support(horse, fx, fz, lead - HIND_HOOVES, y), support(horse, fx, fz, -HIND_HOOVES, y));
		}
		if (landing >= 0.0F) {
			// Touchdown: the body is where the horse landed, still coming down as fast as it fell; the legs take it up.
			carry(s, y, s.foreVelocity);
		}
		// Each end on its legs, a critically damped spring, solved implicitly (steady at any stiffness, no overshoot): the
		// legs push it up as hard as they need to, but nothing pulls it down faster than it falls (where the ground drops
		// away under the hooves, it falls under gravity until they meet it again).
		final float damping = 1.0F + 2.0F * w + w * w;
		final float gravity = (float) Math.max(horse.getGravity(), 0.0);
		s.foreVelocity = Math.max((float) ((s.foreVelocity + w * w * (s.foreGround - s.fore)) / damping), s.foreVelocity - gravity);
		s.fore += s.foreVelocity;
		s.hindVelocity = Math.max((float) ((s.hindVelocity + w * w * (s.hindGround - s.hind)) / damping), s.hindVelocity - gravity);
		s.hind += s.hindVelocity;
		// The legs give no further than LEG_GIVE below the ground actually under each pair (read again only when the horse
		// moves and the ground isn't level).
		final float cos = Mth.cos(s.pitch * Mth.DEG_TO_RAD);
		if (probe) {
			final boolean level = Math.abs(s.foreGround - y) < 1.0E-3 && Math.abs(s.hindGround - y) < 1.0E-3 && Math.abs(s.pitch) < 0.5F;
			// (Only ground a hoof could stand on: a wall the horse is up against isn't.)
			s.foreFoot = level ? y : footing(horse, fx, fz, FORE_HOOVES * cos, y + RIDDEN_STEP_HEIGHT + 0.01);
			s.hindFoot = level ? y : footing(horse, fx, fz, -HIND_HOOVES * cos, y + RIDDEN_STEP_HEIGHT + 0.01);
		}
		// (Nothing in reach under a pair, a drop: nothing to stop it.) The legs push it up by no more than LEG_PUSH a tick
		// (they draw up into the body meanwhile, see GroundLegs), and it carries on rising as fast as they pushed.
		final double foreStop = s.foreFoot - LEG_GIVE;
		if (s.fore < foreStop) {
			final double push = Math.min(foreStop - s.fore, LEG_PUSH);
			s.fore += push;
			s.foreVelocity = Math.max(s.foreVelocity, (float) push);
		}
		final double hindStop = s.hindFoot - LEG_GIVE;
		if (s.hind < hindStop) {
			final double push = Math.min(hindStop - s.hind, LEG_PUSH);
			s.hind += push;
			s.hindVelocity = Math.max(s.hindVelocity, (float) push);
		}
		// Tilt along the line between the two ends; crouching for a ledge jump (haunches down, nose up) or sitting back
		// into a cut adds to it.
		final float tilt = Mth.clamp((float) Math.toDegrees(Math.atan2(s.fore - s.hind, HOOF_SPAN)) * SLOPE_SHARE, -SLOPE_PITCH_MAX, SLOPE_PITCH_MAX);
		s.pitch = tilt + s.ledgeCrouch * LEDGE_CROUCH_PITCH + s.cutSquat * CUT_SQUAT_PITCH;

		// The body sits on the lower end (the pair of legs at the other draws up to fit), but never so low that they would
		// have to draw up more than LEG_SIT (past the steepest tilt, the lower pair reaches down instead): the joints are
		// FORE_HOOVES / HIND_HOOVES from the pivot, and an upright leg's top is drawn up into the body by its swing.
		final float sin = Mth.sin(s.pitch * Mth.DEG_TO_RAD);
		final float tuck = LEG_HALF_DEPTH / 16.0F * Math.abs(Mth.sin(s.pitch * LEG_UPRIGHT * Mth.DEG_TO_RAD));
		final double foreBody = s.fore - y - FORE_HOOVES * sin;
		final double hindBody = s.hind - y + HIND_HOOVES * sin;
		final double offset = Math.max(Math.min(foreBody, hindBody), Math.max(foreBody, hindBody) - LEG_SIT) - tuck - s.ledgeCrouch * LEDGE_CROUCH
			- s.cutSquat * CUT_SQUAT;
		s.heightOffset = (float) offset;
		if (probe) {
			s.chestRoom = (float) (y + offset + tuck + FORE_HOOVES * sin - (support(horse, fx, fz, chestAhead, y) - chestAbove));
		}
		// Where the model isn't drawn yet (so no hoof can find its own ground), the pair over higher ground draws up onto it.
		s.foreLeg = drawUp(s.foreLeg, s.foreFoot - y - FORE_HOOVES * sin, offset + tuck);
		s.hindLeg = drawUp(s.hindLeg, s.hindFoot - y + HIND_HOOVES * sin, offset + tuck);
	}

	/**
	 * How far (blocks) a pair of hooves comes up from where a straight leg puts them, onto its ground {@code ground}
	 * (relative to the horse, along its tilt, as the reaches above) with the body at {@code body}; eased from
	 * {@code current}.
	 */
	private static float drawUp(final float current, final double ground, final double body) {
		final float wanted = Double.isNaN(ground) ? 0.0F : (float) Mth.clamp(ground - body, 0.0, LEG_DRAW_MAX);
		return current + Mth.clamp(wanted - current, -LEG_LIFT_RATE, LEG_LIFT_RATE);
	}

	/**
	 * The ground a hoof stands on {@code along} blocks ahead of the horse's centre, no higher than its joint
	 * ({@code joint}). NaN where there is none (a wall, or a drop beyond a slope).
	 */
	private static double footing(final AbstractHorse horse, final double fx, final double fz, final double along, final double joint) {
		final double ground = surface(horse, horse.getX() + fx * along, horse.getZ() + fz * along, joint, horse.getY() - STEP_REACH - Math.abs(along));
		return ground == Double.NEGATIVE_INFINITY ? Double.NaN : ground;
	}

	/**
	 * How far above the ground under it a pair of the drawn horse's hooves is (negative: sunk into it), from the pose
	 * {@link #steps} gave the body and legs, against the ground at that very spot (the higher of the hoof's edges). For
	 * tests and the steering overlay.
	 */
	public static double hoofGap(final AbstractHorse horse, final RideState s, final boolean front) {
		final float tilt = s.pitch * Mth.DEG_TO_RAD;
		final float along = front ? FORE_HOOVES : -HIND_HOOVES;
		final double hoofY = horse.getY() + s.heightOffset + along * Mth.sin(tilt) + LEG_HALF_DEPTH / 16.0F * Math.abs(Mth.sin(tilt * LEG_UPRIGHT))
			+ (front ? s.foreLeg : s.hindLeg);
		final float yaw = horse.yBodyRot * Mth.DEG_TO_RAD;
		final double ground = footing(horse, -Mth.sin(yaw), Mth.cos(yaw), along * Mth.cos(tilt), hoofY + RIDDEN_STEP_HEIGHT);
		return Double.isNaN(ground) ? 0.0 : hoofY - ground;
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
		// Down a slope the ground falls away a block for every block along (half a block more where the steps fall), so
		// further out ahead it may be further down; a wall or a deeper drop reads as level. Behind, only a step down counts
		// (just up a ledge, the ground at its foot isn't where the hind hooves stand).
		final double drop = reach < 0.0 ? STEP_REACH : STEP_REACH + reach + 0.5;
		final double ground = surface(horse, horse.getX() + fx * reach, horse.getZ() + fz * reach, y + climb + RIDDEN_STEP_HEIGHT + 0.01, y - drop);
		return Double.isNaN(ground) || ground < y - drop ? y : ground;
	}

	/**
	 * Puts the front and the back of the body where the drawn body is, at its tilt, moving up or down at {@code velocity}
	 * (blocks/tick).
	 */
	private static void carry(final RideState s, final double y, final float velocity) {
		final double body = y + s.heightOffset;
		final double rise = HOOF_SPAN * Math.tan(Mth.clamp(s.pitch / SLOPE_SHARE, -SLOPE_PITCH_MAX, SLOPE_PITCH_MAX) * Mth.DEG_TO_RAD);
		s.fore = body + rise * (FORE_HOOVES / HOOF_SPAN);
		s.hind = body - rise * (HIND_HOOVES / HOOF_SPAN);
		s.foreVelocity = velocity;
		s.hindVelocity = velocity;
	}

	/**
	 * Top of the ground at (x, z), searching from a step above {@code y} down to 1.5 below the hooves (at
	 * {@code feet}). A wall taller than that reads as level with the hooves, so the horse does not rear up against it.
	 */
	private static double groundHeight(final AbstractHorse horse, final double x, final double y, final double z, final double feet) {
		final double surface = surface(horse, x, z, y + RIDDEN_STEP_HEIGHT + 0.01, feet - 1.5);
		return Double.isNaN(surface) ? feet : surface == Double.NEGATIVE_INFINITY ? feet - 1.5 : surface;
	}

	/**
	 * Top of the ground at (x, z) between {@code highest} and {@code lowest}: the shape's top at that very spot, so the
	 * low half of a stair or a slab beside a full block reads as itself. NaN if something there reaches above
	 * {@code highest} (a wall), negative infinity if there is nothing down to {@code lowest}.
	 */
	private static double surface(final AbstractHorse horse, final double x, final double z, final double highest, final double lowest) {
		final BlockPos.MutableBlockPos pos = PROBE;
		final int bx = Mth.floor(x);
		final int bz = Mth.floor(z);
		final int top = Mth.floor(highest);
		final int bottom = Mth.floor(lowest);
		// Under water, hooves go no deeper than a wade from the surface (deeper, the horse swims).
		double wade = Double.NEGATIVE_INFINITY;
		for (int by = top; by >= bottom; by--) {
			final BlockState state = horse.level().getBlockState(pos.set(bx, by, bz));
			if (state.isAir()) {
				continue;
			}
			if (wade == Double.NEGATIVE_INFINITY && state.getFluidState().is(FluidTags.WATER)) {
				wade = by + state.getFluidState().getOwnHeight() - WADE_DEPTH;
			}
			final VoxelShape shape = state.getCollisionShape(horse.level(), pos);
			if (shape.isEmpty()) {
				if (by <= wade) {
					return wade;
				}
				continue;
			}
			// (For the Y axis the other two coordinates go Z then X.)
			final double height = shape == Shapes.block() ? 1.0 : shape.max(Direction.Axis.Y, z - bz, x - bx);
			if (height == Double.NEGATIVE_INFINITY) {
				continue;
			}
			final double surface = Math.max(by + height, wade);
			return surface > highest ? Double.NaN : surface;
		}
		return wade;
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
