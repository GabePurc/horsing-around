package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.ride.RideState;
import net.minecraft.util.Mth;

/**
 * The plain model's body carried by its legs (user direction, 2026-10-27: the horse should shift its weight as a real one
 * does): each hoof on the ground pushes the body up through its stance, hardest in the middle of it, the front hooves
 * carrying WEIGHT_FORE of the horse and the hind ones the rest. Where the pushes and gravity don't balance the body rises
 * and falls (highest with every hoof in the air, lowest as they push hardest), and it pitches as the front hooves push its
 * nose up and the hind ones push it down: a trot's bounce, a canter's and a gallop's rock, and the float of a gallop's
 * moment in the air, each from the hooves actually standing. The running gaits only (a walk vaults over its legs: see
 * Strides.bodyLift). Render thread only; the state is the horse's own (RideState).
 */
final class BodyWeight {
	/** The longest step the body's motion is worked out over, seconds. */
	private static final float STEP = 1.0F / 240.0F;

	private BodyWeight() {
	}

	/** Moves the body on its legs by {@code dt} seconds, once the hooves are worked out for the frame; {@code seed}: starting over. */
	static void advance(final RideState s, final float dt, final boolean seed) {
		// (Each hoof's push: a half sine through its stance, as a horse's hooves push on a force plate, each pair carrying its
		// share of the horse over a stride: the longer a hoof stands, the less hard it pushes.)
		float carryFront = 0.0F;
		float carryHind = 0.0F;
		for (int i = 0; i < 4; i++) {
			if (!s.hoofDown[i]) {
				continue;
			}
			final float push = Mth.sin(Mth.PI * Mth.clamp(s.stood[i], 0.0F, 1.0F)) * Mth.HALF_PI;
			if (i < 2) {
				carryFront += WEIGHT_FORE * 0.5F * push / Math.max(s.gaitForeStance, WEIGHT_STANCE_LEAST);
			} else {
				carryHind += (1.0F - WEIGHT_FORE) * 0.5F * push / Math.max(s.gaitHindStance, WEIGHT_STANCE_LEAST);
			}
		}
		// (What they carry, and how they turn it about the middle of its weight: the front legs (1 - WEIGHT_FORE) of the span
		// ahead of it, the hind ones the rest behind, so standing still the two balance.)
		final float carry = carryFront + carryHind;
		final float turn = WEIGHT_SPAN * ((1.0F - WEIGHT_FORE) * carryFront - WEIGHT_FORE * carryHind);
		if (seed || !Float.isFinite(s.weightLift + s.weightLiftV + s.weightPitch + s.weightPitchV + s.weightMean[0] + s.weightMean[1])) {
			// (Starting over: at rest, carried level.)
			s.weightLift = s.weightLiftV = s.weightPitch = s.weightPitchV = 0.0F;
			s.weightMean[0] = 1.0F;
			s.weightMean[1] = 0.0F;
			return;
		}
		if (dt <= 0.0F) {
			return;
		}
		// (Each against its own average over the last strides, so what's left over as the stride changes (a hoof lifting early,
		// a gait changing) never adds up into the body drifting off.)
		final float settle = Math.min(dt / WEIGHT_MEAN_TIME, 1.0F);
		s.weightMean[0] += (carry - s.weightMean[0]) * settle;
		s.weightMean[1] += (turn - s.weightMean[1]) * settle;
		// (How much of the body's motion is this: the running gaits, as far as the strides sweep, on the ground.)
		final float running = (1.0F - s.gaitShare[0]) * s.gaitSweep * s.gaitGround;
		final float rise = running * WEIGHT_GRAVITY * (carry - s.weightMean[0]);
		final float nose = running * WEIGHT_GRAVITY * (turn - s.weightMean[1]) / (WEIGHT_TURN_RADIUS * WEIGHT_TURN_RADIUS);
		final float centre = Mth.TWO_PI * WEIGHT_CENTRE;
		final int steps = Math.min(Mth.ceil(Math.min(dt, 0.1F) / STEP), 24);
		final float h = Math.min(dt, 0.1F) / steps;
		for (int n = 0; n < steps; n++) {
			s.weightLiftV += (rise - centre * centre * s.weightLift - 2.0F * WEIGHT_CENTRE_DAMPING * centre * s.weightLiftV) * h;
			s.weightLift = Mth.clamp(s.weightLift + s.weightLiftV * h, -WEIGHT_LIFT_MOST, WEIGHT_LIFT_MOST);
			s.weightPitchV += (nose - centre * centre * s.weightPitch - 2.0F * WEIGHT_CENTRE_DAMPING * centre * s.weightPitchV) * h;
			s.weightPitch = Mth.clamp(s.weightPitch + s.weightPitchV * h, -WEIGHT_PITCH_MOST, WEIGHT_PITCH_MOST);
		}
	}
}
