package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The horse family's own gaits on the plain model (no animation pack posing it), in place of vanilla's one sine swing
 * that looks the same at every speed: a four-beat walk, a two-beat trot on diagonal pairs, a three-beat canter and a
 * four-beat gallop on the left lead, blended by the leg-animation speed at the points the saddle's motion changes gait
 * (see {@link SaddleMotion}), and in time with vanilla's own stride, so the body's motion and the legs agree and the
 * cadence is vanilla's. Each leg stays vanilla's one rigid piece, turned about its top (see {@link Legs}): on the ground
 * it sweeps back at an even pace; off it, it lifts (drawn up into the body, the way a leg folds), flicks back, swings
 * forward and stretches out for the ground, leaving it and meeting it again at the pace it had on it, so nothing
 * hitches. The head
 * nods and the tail bounces with the stride. All of it grows with the speed from nothing (the legs come to rest standing
 * straight) and gives way while the horse rears. The ride's legs on the ground and in the air ({@link GroundLegs},
 * {@link AirLegs}) go on top of it. Render thread only.
 */
public final class GaitCycle {
	/** Vanilla's legs cycle once per 2pi of (limb swing x LEG_PHASE); in water at WATER_PACE of that. */
	public static final float LEG_PHASE = 0.6662F;
	public static final float WATER_PACE = 0.2F;
	/** Vanilla's head bob (its own beat, out of step with the legs), taken out: from this leg-animation speed up. */
	private static final float VANILLA_BOB_SPEED = 0.2F;
	/** This frame's share of each gait: walk, trot, canter, gallop (they add up to 1). */
	private static final float[] SHARE = new float[4];
	/** How far the peaks of the lifts, the flick and the stretch are moved along the swing (see {@link #bump}). */
	private static final float FORE_SKEW = skew(GAIT_FORE_LIFT_PEAK);
	private static final float HIND_SKEW = skew(GAIT_HIND_LIFT_PEAK);
	private static final float FLICK_SKEW = skew(GAIT_FLICK_PEAK);
	private static final float STRETCH_SKEW = skew(GAIT_STRETCH_PEAK);

	private GaitCycle() {
	}

	/** How much of the motion is a canter or gallop, 0..1, at leg-animation speed {@code speed}. */
	public static float run(final float speed) {
		return Mth.clamp((speed - RUN_ANIMATION_SPEED + 0.05F) * 10.0F, 0.0F, 1.0F);
	}

	/** How much of the motion is a gallop. */
	public static float gallop(final float speed) {
		return run(speed) * Mth.clamp((speed - GALLOP_ANIMATION_SPEED + 0.03F) * 20.0F, 0.0F, 1.0F);
	}

	/** How much of the motion is a trot. */
	public static float trot(final float speed) {
		return Mth.clamp((speed - TROT_ANIMATION_SPEED + 0.02F) * 10.0F, 0.0F, 1.0F) * (1.0F - run(speed));
	}

	/**
	 * Puts this gait on the legs, head and tail in place of vanilla's stride (as vanilla left them, this frame).
	 *
	 * @param position the leg animation's position (vanilla's limb swing)
	 * @param speed    the leg animation's speed, 0..1
	 * @param water    in water (vanilla's legs go at a fifth of the pace)
	 * @param standing how far the horse is reared, 0..1
	 * @param eating   how far its head is down grazing, 0..1
	 * @param tilt     the ground's tilt under the ridden horse, radians (0 for one the ride doesn't place)
	 */
	public static void pose(
		final @Nullable ModelPart leftFront, final @Nullable ModelPart rightFront, final @Nullable ModelPart leftHind, final @Nullable ModelPart rightHind,
		final ModelPart head, final ModelPart tail, final float position, final float speed, final boolean water, final float standing, final float eating,
		final float tilt
	) {
		final float upright = 1.0F - standing;
		final float stride = position * LEG_PHASE * (water ? WATER_PACE : 1.0F);
		// Vanilla's own swing of each leg, and its head bob, come out (exactly as vanilla put them in).
		final float vanilla = Mth.cos(stride + Mth.PI) * speed * upright;
		if (speed > VANILLA_BOB_SPEED) {
			head.xRot -= Mth.cos(position * 0.8F) * 0.15F * speed * (standing + 1.0F - Math.max(standing, eating));
		}
		final float run = run(speed);
		final float gallop = gallop(speed);
		final float trot = trot(speed);
		SHARE[0] = 1.0F - trot - run;
		SHARE[1] = trot;
		SHARE[2] = run - gallop;
		SHARE[3] = gallop;
		final float size = smoothstep(speed / GAIT_FULL_SWING) * upright;
		final float t = stride * (1.0F / Mth.TWO_PI);
		final float stance = mix(GAIT_STANCE);
		final float foreReach = mix(GAIT_FORE_REACH);
		final float foreBack = mix(GAIT_FORE_BACK);
		final float foreFlick = mix(GAIT_FORE_FLICK);
		final float foreStretch = mix(GAIT_FORE_STRETCH);
		// On steep ground the hooves lift less, as the stride is shorter there (see GroundLegs): the ride lifts each one
		// onto the ground ahead of it.
		final float steep = 1.0F - (1.0F - STRIDE_STEEP) * Mth.clamp(Math.abs(tilt) / STRIDE_STEEP_TILT, 0.0F, 1.0F);
		final float foreLift = mix(GAIT_FORE_LIFT) * steep;
		final float hindReach = mix(GAIT_HIND_REACH);
		final float hindBack = mix(GAIT_HIND_BACK);
		final float hindFlick = mix(GAIT_HIND_FLICK);
		final float hindStretch = mix(GAIT_HIND_STRETCH);
		final float hindLift = mix(GAIT_HIND_LIFT) * steep;
		leg(leftFront, t - touchdown(0), stance, foreReach, foreBack, foreFlick, foreStretch, foreLift, FORE_SKEW, size, vanilla * 0.8F);
		leg(rightFront, t - touchdown(1), stance, foreReach, foreBack, foreFlick, foreStretch, foreLift, FORE_SKEW, size, -vanilla * 0.8F);
		leg(leftHind, t - touchdown(2), stance, hindReach, hindBack, hindFlick, hindStretch, hindLift, HIND_SKEW, size, -vanilla * 0.5F);
		leg(rightHind, t - touchdown(3), stance, hindReach, hindBack, hindFlick, hindStretch, hindLift, HIND_SKEW, size, vanilla * 0.5F);
		// The walk and trot nod twice a stride, with each front hoof's landing; the canter and gallop once, the neck
		// coming down as the front hooves take the weight. The tail bounces after the body, and swings with the walk's hips.
		head.xRot += size * beats(GAIT_NOD, GAIT_NOD_LOW, t);
		tail.xRot += size * beats(GAIT_TAIL, GAIT_TAIL_HIGH, t);
		tail.yRot += size * SHARE[0] * GAIT_TAIL_SWAY * Mth.sin(Mth.TWO_PI * (t - GAIT_TOUCHDOWN[0][2]));
	}

	/**
	 * One leg, {@code u} strides past its touch-down: on the ground for the first {@code stance} of the stride, sweeping
	 * evenly from {@code reach} forward to {@code back} behind (radians); then in the air, flicked {@code flick} further
	 * back, swung forward, stretched {@code stretch} beyond {@code reach} and drawn back onto it as it lands, and lifted up
	 * to {@code lift} of its length. All of it times {@code size}.
	 */
	private static void leg(
		final @Nullable ModelPart part, final float u, final float stance, final float reach, final float back, final float flick, final float stretch,
		final float lift, final float skew, final float size, final float vanilla
	) {
		final Legs.Leg leg = part == null ? null : Legs.of(part);
		if (leg == null) {
			return;
		}
		final float at = u - Mth.floor(u);
		final float forward;
		float up = 0.0F;
		if (at < stance) {
			forward = reach - (reach + back) * (at / stance);
		} else {
			final float s = (at - stance) / (1.0F - stance);
			// Leaving the ground and meeting it again, the leg keeps the pace back it had on it (a hoof about to land is
			// drawn back to meet the ground, as a horse's is), so neither end has a jolt.
			final float pace = -(reach + back) * (1.0F - stance) / stance;
			forward = hermite(-back, pace, reach, pace, s) - flick * bump(s, FLICK_SKEW) + stretch * bump(s, STRETCH_SKEW);
			up = lift * bump(s, skew);
		}
		// Vanilla's swing out (turned about the leg's own pivot, as vanilla put it in), then this one about its top.
		part.xRot -= vanilla;
		Legs.swing(part, leg, part.xRot - forward * size);
		if (up > 0.0F) {
			Legs.draw(part, up * size * leg.length);
		}
	}

	/** Leg {@code i}'s touch-down this frame, blended between the gaits. */
	private static float touchdown(final int i) {
		return SHARE[0] * GAIT_TOUCHDOWN[0][i] + SHARE[1] * GAIT_TOUCHDOWN[1][i] + SHARE[2] * GAIT_TOUCHDOWN[2][i] + SHARE[3] * GAIT_TOUCHDOWN[3][i];
	}

	private static float mix(final float[] values) {
		return SHARE[0] * values[0] + SHARE[1] * values[1] + SHARE[2] * values[2] + SHARE[3] * values[3];
	}

	/** A swing of {@code size[g]} peaking at {@code at[g]} of the stride: twice a stride for the walk and trot, once for the canter and gallop. */
	private static float beats(final float[] size, final float[] at, final float t) {
		return SHARE[0] * size[0] * Mth.cos(2.0F * Mth.TWO_PI * (t - at[0])) + SHARE[1] * size[1] * Mth.cos(2.0F * Mth.TWO_PI * (t - at[1]))
			+ SHARE[2] * size[2] * Mth.cos(Mth.TWO_PI * (t - at[2])) + SHARE[3] * size[3] * Mth.cos(Mth.TWO_PI * (t - at[3]));
	}

	/** From {@code from} to {@code to} as {@code s} goes 0 to 1, leaving at pace {@code fromPace} and arriving at {@code toPace} (per whole of s). */
	private static float hermite(final float from, final float fromPace, final float to, final float toPace, final float s) {
		final float s2 = s * s;
		final float s3 = s2 * s;
		return (2.0F * s3 - 3.0F * s2 + 1.0F) * from + (s3 - 2.0F * s2 + s) * fromPace + (3.0F * s2 - 2.0F * s3) * to + (s3 - s2) * toPace;
	}

	/** 0 to 1 and back over the swing, setting off and arriving with no pace, highest where {@code skew} puts it. */
	private static float bump(final float s, final float skew) {
		final float sin = Mth.sin(Mth.PI * (s + skew * s * (1.0F - s)));
		return sin * sin;
	}

	/** The k in s + k s (1 - s), which moves the middle of the swing (s = 1/2) to {@code peak}. */
	private static float skew(final float peak) {
		return (0.5F - peak) / (peak * (1.0F - peak));
	}
}
