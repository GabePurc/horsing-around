package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The horse family's own gaits on the plain model (no animation pack posing it), in place of vanilla's one sine swing
 * that looks the same at every speed, from how real horses move (footfalls, stance and swing, limb angles, the body's
 * rise and pitch, the neck: see RideTuning): a four-beat walk, a two-beat trot on diagonal pairs, a three-beat canter
 * and a four-beat gallop on the left lead, blended by the leg-animation speed at the points the saddle's motion changes
 * gait (see {@link SaddleMotion}, whose rise and pitch are timed here too), in time with vanilla's own stride, so the
 * cadence is vanilla's. Each leg stays vanilla's one rigid piece, turned about its top (see {@link Legs}). On the ground
 * it turns the way a leg standing on a planted hoof does as the body passes over it, and folds under the body where it
 * would otherwise reach into the ground (the body sits a little lower while moving: see {@link #crouch}). In the air it
 * folds up (drawn up into the body, as a knee and hock bend), goes on back a little, swings forward, stretches out past
 * its landing and draws back to meet the ground, leaving it and meeting it again at the pace it had on it, so nothing
 * jolts. The neck is carried lower the faster the gait and nods with the stride a beat behind the body, and the tail
 * bounces after it. All of it grows with the speed from nothing (the legs come to rest standing straight) and gives way
 * while the horse rears. The ride's legs on the ground and in the air ({@link GroundLegs}, {@link AirLegs}) go on top of
 * it. Render thread only.
 */
public final class GaitCycle {
	/** Vanilla's legs cycle once per 2pi of (limb swing x LEG_PHASE); in water at WATER_PACE of that. */
	public static final float LEG_PHASE = 0.6662F;
	public static final float WATER_PACE = 0.2F;
	/** Vanilla's head bob (its own beat, out of step with the legs), taken out: from this leg-animation speed up. */
	private static final float VANILLA_BOB_SPEED = 0.2F;
	/** Over how much a leg's folding under the body eases in, model pixels (its hoof is at most half that in the ground). */
	private static final float GROUND_EASE = 0.4F;
	/** This frame's share of each gait: walk, trot, canter, gallop (they add up to 1). */
	private static final float[] SHARE = new float[4];
	/**
	 * The swing's shapes, each s^m (1 - s)^n scaled to peak at 1 (rising from and settling to nothing, with no pace at
	 * either end): the front and hind legs' fold, the flick back and the stretch forward. m, n, scale.
	 */
	private static final float[] FORE_FOLD = shape(GAIT_FORE_FOLD_PEAK);
	private static final float[] HIND_FOLD = shape(GAIT_HIND_FOLD_PEAK);
	private static final float[] FLICK = shape(GAIT_FLICK_PEAK);
	private static final float[] STRETCH = shape(GAIT_STRETCH_PEAK);

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
	 * The body's rise with the stride, -1..1 of each gait's own (times their SADDLE_LIFT_ in the given shares), {@code t}
	 * strides along: twice a stride at a walk and trot, once at a canter and gallop.
	 */
	public static float bodyLift(final float t, final float walk, final float trot, final float canter, final float gallop) {
		return SADDLE_LIFT_WALK * walk * Mth.cos(2.0F * Mth.TWO_PI * (t - GAIT_BODY_HIGH[0]))
			+ SADDLE_LIFT_TROT * trot * Mth.cos(2.0F * Mth.TWO_PI * (t - GAIT_BODY_HIGH[1]))
			+ SADDLE_LIFT_CANTER * canter * Mth.cos(Mth.TWO_PI * (t - GAIT_BODY_HIGH[2]))
			+ SADDLE_LIFT_GALLOP * gallop * Mth.cos(Mth.TWO_PI * (t - GAIT_BODY_HIGH[3]));
	}

	/** The body's pitch with the stride, degrees, nose up positive (see {@link #bodyLift}). */
	public static float bodyPitch(final float t, final float walk, final float trot, final float canter, final float gallop) {
		return SADDLE_PITCH_WALK * walk * Mth.sin(2.0F * Mth.TWO_PI * (t - GAIT_BODY_HIGH[0]))
			+ SADDLE_PITCH_TROT * trot * Mth.sin(2.0F * Mth.TWO_PI * (t - GAIT_BODY_HIGH[1]))
			+ SADDLE_PITCH_CANTER * canter * Mth.cos(Mth.TWO_PI * (t - GAIT_BODY_NOSE_UP[2]))
			+ SADDLE_PITCH_GALLOP * gallop * Mth.cos(Mth.TWO_PI * (t - GAIT_BODY_NOSE_UP[3]));
	}

	/** How much lower the body sits on its legs moving at these shares of each gait, blocks. */
	public static float crouch(final float walk, final float trot, final float canter, final float gallop) {
		return GAIT_CROUCH[0] * walk + GAIT_CROUCH[1] * trot + GAIT_CROUCH[2] * canter + GAIT_CROUCH[3] * gallop;
	}

	/**
	 * Puts this gait on the legs, head and tail in place of vanilla's stride (as vanilla left them, this frame).
	 *
	 * @param position the leg animation's position (vanilla's limb swing)
	 * @param speed    the leg animation's speed, 0..1
	 * @param water    in water (vanilla's legs go at a fifth of the pace)
	 * @param standing how far the horse is reared, 0..1
	 * @param eating   how far its head is down grazing, 0..1
	 * @param tilt     the ground's tilt under the ridden horse, radians
	 * @param lift     how far the gait has the whole horse raised (blocks, negative lowered: the saddle motion's rise less
	 *                 the crouch), which the legs on the ground fold under
	 * @param pixels   model pixels to a block in the legs' frame
	 * @param air      how far the legs are in a jump's shape, 0..1 (in the air nothing stands on the ground)
	 */
	public static void pose(
		final @Nullable ModelPart leftFront, final @Nullable ModelPart rightFront, final @Nullable ModelPart leftHind, final @Nullable ModelPart rightHind,
		final ModelPart head, final ModelPart tail, final float position, final float speed, final boolean water, final float standing, final float eating,
		final float tilt, final float lift, final float pixels, final float air
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
		// On steep ground the legs fold less in their swing, as the stride is shorter there (see GroundLegs): the ride
		// lifts each hoof onto the ground ahead of it.
		final float steep = 1.0F - (1.0F - STRIDE_STEEP) * Mth.clamp(Math.abs(tilt) / STRIDE_STEEP_TILT, 0.0F, 1.0F);
		// How far below its ground the gait's lowering of the body puts a hoof under an upright leg (model pixels); not in a jump.
		final float under = Math.max(-lift * pixels, 0.0F) * (1.0F - smoothstep(air));
		final float foreStance = mix(GAIT_FORE_STANCE);
		final float hindStance = mix(GAIT_HIND_STANCE);
		final float foreReach = mix(GAIT_FORE_REACH);
		final float foreBack = mix(GAIT_FORE_BACK);
		final float foreFlick = mix(GAIT_FORE_FLICK);
		final float foreStretch = mix(GAIT_FORE_STRETCH);
		final float foreFold = mix(GAIT_FORE_FOLD) * steep;
		final float hindReach = mix(GAIT_HIND_REACH);
		final float hindBack = mix(GAIT_HIND_BACK);
		final float hindFlick = mix(GAIT_HIND_FLICK);
		final float hindStretch = mix(GAIT_HIND_STRETCH);
		final float hindFold = mix(GAIT_HIND_FOLD) * steep;
		leg(leftFront, t - touchdown(0), foreStance, foreReach, foreBack, foreFlick, foreStretch, foreFold, FORE_FOLD, size, under, vanilla * 0.8F);
		leg(rightFront, t - touchdown(1), foreStance, foreReach, foreBack, foreFlick, foreStretch, foreFold, FORE_FOLD, size, under, -vanilla * 0.8F);
		leg(leftHind, t - touchdown(2), hindStance, hindReach, hindBack, hindFlick, hindStretch, hindFold, HIND_FOLD, size, under, -vanilla * 0.5F);
		leg(rightHind, t - touchdown(3), hindStance, hindReach, hindBack, hindFlick, hindStretch, hindFold, HIND_FOLD, size, under, vanilla * 0.5F);
		// The neck: carried lower at faster gaits, nodding twice a stride at a walk and trot (lowest at each front leg's
		// mid-stance) and once at a canter and gallop (lowest as the leading front hoof lands), a beat behind the body.
		// The tail bounces after the body, and swings with the walk's hips.
		head.xRot += size * (mix(GAIT_NECK) + beats(GAIT_NOD, GAIT_NOD_LOW, t));
		tail.xRot += size * beats(GAIT_TAIL, GAIT_TAIL_HIGH, t);
		tail.yRot += size * SHARE[0] * GAIT_TAIL_SWAY * Mth.sin(Mth.TWO_PI * (t - GAIT_TOUCHDOWN[0][2]));
	}

	/**
	 * One leg, {@code u} strides past its touch-down: on the ground for the first {@code stance} of the stride, from
	 * {@code reach} forward to {@code back} behind (radians), turning as a leg over a planted hoof does (quickest as it
	 * passes upright), and folded under the body as far as its hoof would otherwise be below the ground ({@code under}
	 * pixels under an upright leg); then in the air, folded up to {@code fold} of its length, flicked {@code flick} on
	 * back, swung forward, stretched {@code stretch} beyond {@code reach} and drawn back onto it as it lands. All of it
	 * times {@code size}.
	 */
	private static void leg(
		final @Nullable ModelPart part, final float u, final float stance, final float reach, final float back, final float flick, final float stretch,
		final float fold, final float[] foldShape, final float size, final float under, final float vanilla
	) {
		final Legs.Leg leg = part == null ? null : Legs.of(part);
		if (leg == null) {
			return;
		}
		final float at = u - Mth.floor(u);
		// (On the ground, the hoof's place under the top of the leg goes back evenly as the body passes over it.)
		final float near = (float) Math.tan(reach);
		final float far = (float) -Math.tan(back);
		final float forward;
		float up = 0.0F;
		if (at < stance) {
			forward = (float) Math.atan(near + (far - near) * (at / stance));
		} else {
			final float s = (at - stance) / (1.0F - stance);
			// Leaving the ground and meeting it again, the leg keeps the pace it had on it (a hoof about to land is drawn
			// back to meet the ground), so neither end has a jolt.
			final float span = (far - near) * (1.0F - stance) / stance;
			forward = hermite(-back, span / (1.0F + far * far), reach, span / (1.0F + near * near), s) - flick * shape(s, FLICK)
				+ stretch * shape(s, STRETCH);
			up = fold * leg.length * shape(s, foldShape);
		}
		final float angle = forward * size;
		// A leg turned out reaches less far down: it folds under the lowered body only by what is left (along itself),
		// eased in where that comes to nothing so its hoof never changes pace at once; its fold in the air goes on top.
		final float cos = Mth.cos(angle);
		final float folded = (under - leg.length * (1.0F - cos)) / cos;
		up = up * size + (folded <= 0.0F ? 0.0F : folded < GROUND_EASE ? folded * folded / (2.0F * GROUND_EASE) : folded - 0.5F * GROUND_EASE);
		// Vanilla's swing out (turned about the leg's own pivot, as vanilla put it in), then this one about its top.
		part.xRot -= vanilla;
		Legs.swing(part, leg, part.xRot - angle);
		if (up > 0.0F) {
			Legs.draw(part, up);
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

	/** s^m (1 - s)^n times its scale (see {@link #shape(float)}): 0 at both ends with no pace there, 1 at its peak. */
	private static float shape(final float s, final float[] shape) {
		return (float) (Math.pow(s, shape[0]) * Math.pow(1.0 - s, shape[1])) * shape[2];
	}

	/** The m, n and scale of a swing shape peaking at {@code peak} of the swing (the lesser exponent 2). */
	private static float[] shape(final float peak) {
		final float m = peak <= 0.5F ? 2.0F : 2.0F * peak / (1.0F - peak);
		final float n = peak <= 0.5F ? 2.0F * (1.0F - peak) / peak : 2.0F;
		return new float[] {m, n, (float) (1.0 / (Math.pow(peak, m) * Math.pow(1.0 - peak, n)))};
	}
}
