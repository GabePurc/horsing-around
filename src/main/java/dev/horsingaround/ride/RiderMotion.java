package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.*;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * How the rider carries their weight, a tick at a time (client, every horse being ridden; everyone sees it): bracing the
 * legs forward against a stop or a drop, and giving at the hips when the horse lands. They always sit (user, 2026-10-26:
 * standing in the stirrups at speed looked dumb).
 * Each is a spring, so it eases in and out and never snaps; the rider's pose reads them (see RidePoser). Purely visual.
 */
public final class RiderMotion {
	private RiderMotion() {
	}

	/** After the horse's body has been placed for this tick. */
	public static void tick(final AbstractHorse horse, final RideState s) {
		s.braceO = s.brace;
		s.settleO = s.settle;
		final float rise = s.drawnRise;
		final float lastRise = s.riderLastRise;
		s.riderLastRise = rise;
		if (!horse.isVehicle()) {
			if (s.brace != 0.0F || s.settle != 0.0F || s.braceVelocity != 0.0F || s.settleVelocity != 0.0F) {
				s.brace = s.settle = 0.0F;
				s.braceVelocity = s.settleVelocity = 0.0F;
				s.braceO = s.settleO = 0.0F;
			}
			return;
		}
		final boolean grounded = horse.onGround() && !s.inAir;
		final float slope = s.pitch;

		// Bracing: the legs go forward into the stirrups and the seat back against the cantle as the horse pulls up, going
		// down something steep, and coming down out of a jump or off a drop (ready for the landing).
		final float slowing = Math.max(s.lastGroundSpeedO - s.lastGroundSpeed, 0.0F);
		float brace = Math.min(slowing * BRACE_PER_SLOWING, 1.0F);
		brace = Math.max(brace, Mth.clamp(-slope / BRACE_DOWNHILL_FULL, 0.0F, 1.0F) * BRACE_DOWNHILL);
		if (s.inAir) {
			brace = Math.max(brace, Mth.clamp(-rise / BRACE_DROP_SPEED, 0.0F, 1.0F) * BRACE_DROP);
		}
		// (A critically damped spring at BRACE_RATE, radians a tick.)
		s.braceVelocity += (brace - s.brace) * BRACE_RATE * BRACE_RATE - s.braceVelocity * 2.0F * BRACE_RATE;
		s.brace = Mth.clamp(s.brace + s.braceVelocity, 0.0F, 1.0F);

		// Landing (and any jolt up from the ground, a step taken at speed): the body is caught from below and the rider gives
		// at the hips and knees, folding a little and sinking, then comes back up.
		if (grounded) {
			s.settleVelocity += Math.min(Math.max(rise - lastRise, 0.0F) * SETTLE_KICK, SETTLE_KICK_MAX);
		}
		s.settleVelocity += -s.settle * SETTLE_STIFFNESS - s.settleVelocity * SETTLE_DAMPING;
		s.settle = Mth.clamp(s.settle + s.settleVelocity, -0.25F, 1.0F);
	}
}
