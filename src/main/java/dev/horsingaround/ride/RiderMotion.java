package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.*;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * How the rider carries their weight, a tick at a time (client, every horse being ridden; everyone sees it): standing in
 * the stirrups at speed, bracing the legs forward against a stop or a drop, and giving at the hips when the horse lands.
 * Each is a spring, so it eases in and out and never snaps; the rider's pose reads them (see RidePoser). Purely visual.
 */
public final class RiderMotion {
	private RiderMotion() {
	}

	/** After the horse's body has been placed for this tick. */
	public static void tick(final AbstractHorse horse, final RideState s) {
		s.standO = s.stand;
		s.braceO = s.brace;
		s.settleO = s.settle;
		final float rise = s.drawnRise;
		final float lastRise = s.riderLastRise;
		s.riderLastRise = rise;
		if (!horse.isVehicle()) {
			if (s.stand != 0.0F || s.brace != 0.0F || s.settle != 0.0F || s.standVelocity != 0.0F || s.braceVelocity != 0.0F || s.settleVelocity != 0.0F) {
				s.stand = s.brace = s.settle = 0.0F;
				s.standVelocity = s.braceVelocity = s.settleVelocity = 0.0F;
				s.standO = s.braceO = s.settleO = 0.0F;
			}
			return;
		}
		final boolean grounded = horse.onGround() && !s.inAir;
		final boolean afloat = horse.isInWater() && !horse.onGround() || s.bankTicks > 0;
		final float slope = s.pitch;

		// Bracing: the legs go forward into the stirrups and the seat back against the cantle as the horse pulls up, going
		// down something steep, and coming down out of a jump or off a drop (ready for the landing).
		final float slowing = Math.max(s.lastGroundSpeedO - s.lastGroundSpeed, 0.0F);
		float brace = Math.min(slowing * BRACE_PER_SLOWING, 1.0F);
		brace = Math.max(brace, Mth.clamp(-slope / BRACE_DOWNHILL_FULL, 0.0F, 1.0F) * BRACE_DOWNHILL);
		if (s.inAir) {
			brace = Math.max(brace, Mth.clamp(-rise / BRACE_DROP_SPEED, 0.0F, 1.0F) * BRACE_DROP);
		}
		spring(s, brace, BRACE_RATE, true);

		// Standing in the stirrups: a light seat at a canter, up out of the saddle at a gallop and heaving up a ledge or a
		// steep climb; down again to brace, and sitting when afloat. In the air they stay as they were, and landing too
		// until the horse is back in its stride (its legs out of the jump's shape: the stride, which says the pace, stops
		// in the air).
		float stand;
		if (afloat) {
			stand = 0.0F;
		} else if ((!grounded || s.airLegs > 0.0F) && !s.climbingLedge()) {
			stand = s.stand;
		} else {
			final float limb = horse.walkAnimation.speed();
			final float run = Mth.clamp((limb - RUN_ANIMATION_SPEED + 0.05F) * 10.0F, 0.0F, 1.0F);
			final float gallop = run * Mth.clamp((limb - GALLOP_ANIMATION_SPEED + 0.03F) * 20.0F, 0.0F, 1.0F);
			stand = run * (STAND_CANTER + (1.0F - STAND_CANTER) * gallop);
			stand = Math.max(stand, Mth.clamp(slope / STAND_UPHILL_FULL, 0.0F, 1.0F) * STAND_UPHILL);
			if (s.climbingLedge()) {
				stand = Math.max(stand, STAND_LEDGE);
			}
			stand *= 1.0F - Math.min(s.brace * STAND_BRACE_SIT, 1.0F);
		}
		spring(s, stand, STAND_RATE, false);

		// Landing (and any jolt up from the ground, a step taken at speed): the body is caught from below and the rider gives
		// at the hips and knees, folding a little and sinking, then comes back up.
		if (grounded) {
			s.settleVelocity += Math.min(Math.max(rise - lastRise, 0.0F) * SETTLE_KICK, SETTLE_KICK_MAX);
		}
		s.settleVelocity += -s.settle * SETTLE_STIFFNESS - s.settleVelocity * SETTLE_DAMPING;
		s.settle = Mth.clamp(s.settle + s.settleVelocity, -0.25F, 1.0F);
	}

	/** A critically damped spring toward {@code target} at {@code rate} (radians a tick), for the stand or the brace. */
	private static void spring(final RideState s, final float target, final float rate, final boolean brace) {
		final float value = brace ? s.brace : s.stand;
		float velocity = brace ? s.braceVelocity : s.standVelocity;
		velocity += (target - value) * rate * rate - velocity * 2.0F * rate;
		final float next = Mth.clamp(value + velocity, 0.0F, 1.0F);
		if (brace) {
			s.brace = next;
			s.braceVelocity = velocity;
		} else {
			s.stand = next;
			s.standVelocity = velocity;
		}
	}
}
