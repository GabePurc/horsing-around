package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.ride.RideState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The shape of a horse's legs in a jump, on top of whatever posed them (vanilla model or an animation pack): rising,
 * the front legs fold up under the chest and the hind legs push off behind; coming down, the front legs reach forward
 * and down for the ground and the hind legs gather under the hindquarters. One leg of each pair leads the other.
 */
public final class AirLegs {
	private AirLegs() {
	}

	/**
	 * How much of the jump's shape the legs take this frame (0..1, times {@link RideState#airLegs}): none while a hoof
	 * finds its ground under it ({@code footing}, a step down is a short fall, not a jump), all of it otherwise, eased in
	 * and out (AIR_SHAPE_IN_TIME, AIR_SHAPE_OUT_TIME). Switched each frame, falling past a slope (where a hoof found its ground one frame
	 * and not the next) the legs flicked between the jump's shape and their stride, a flutter (play-test, 2026-10-23). The
	 * same for every layer drawn in a frame.
	 */
	public static float shape(final @Nullable RideState ride, final boolean footing) {
		final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		if (ride == null || minecraft.level == null) {
			return footing ? 0.0F : 1.0F;
		}
		final double now = (double) minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		if (now == ride.airShapeAt) {
			return ride.airShape;
		}
		final float dt = Double.isNaN(ride.airShapeAt) ? 1.0F : (float) Mth.clamp((now - ride.airShapeAt) / 20.0, 0.0, 0.1);
		ride.airShapeAt = now;
		final float target = footing ? 0.0F : 1.0F;
		final float k = 1.0F - (float) Math.exp(-dt / (target > ride.airShape ? AIR_SHAPE_IN_TIME : AIR_SHAPE_OUT_TIME));
		ride.airShape += (target - ride.airShape) * k;
		return ride.airShape;
	}

	/**
	 * @param air  how far the legs are in the jump's shape, 0..1
	 * @param rise climbing (1) to falling (-1)
	 */
	public static void pose(
		final @Nullable ModelPart leftFront, final @Nullable ModelPart rightFront, final @Nullable ModelPart leftHind, final @Nullable ModelPart rightHind,
		final float air, final float rise
	) {
		if (air <= 0.0F) {
			return;
		}
		final float fore = fore(rise);
		final float hind = hind(rise);
		// Eased into and out of, so the legs leave the stride and come back to it without a jolt.
		final float blend = smoothstep(air);
		final float foreLead = FORE_AIR_STAGGER * 0.5F;
		final float hindLead = HIND_AIR_STAGGER * 0.5F;
		final float foreLift = FORE_AIR_LIFT * tuck(rise);
		leg(leftFront, blend, fore - foreLead, foreLift, FORE_AIR_FORWARD);
		leg(rightFront, blend, fore + foreLead, foreLift, FORE_AIR_FORWARD);
		leg(leftHind, blend, hind - hindLead, 0.0F, HIND_AIR_FORWARD);
		leg(rightHind, blend, hind + hindLead, 0.0F, HIND_AIR_FORWARD);
	}

	/**
	 * Front legs' swing at a point in the flight (climbing 1 to falling -1): folded up most just after takeoff, sweeping
	 * smoothly forward to reach for the ground by the end. Negative swings the hoof forward.
	 */
	public static float fore(final float rise) {
		return -Mth.lerp(tuck(rise), FORE_AIR_REACH, FORE_AIR_TUCK);
	}

	/** Hind legs' swing: pushing off behind early in the climb, gathering under the hindquarters by the top and after. */
	public static float hind(final float rise) {
		return Mth.lerp(smoothstep((rise + 0.1F) / 1.0F), -HIND_AIR_TUCK, HIND_AIR_PUSH);
	}

	private static float tuck(final float rise) {
		return smoothstep((rise + 0.5F) / 1.1F);
	}

	/**
	 * Swings a leg to {@code xRot} about its top (wherever the model pivots it; see {@link Legs}), draws it {@code lift}
	 * up into the body and nudges it {@code forward} (model pixels), plus as far up as the swing tips its top's corner out
	 * of the body, so no gap shows where it hangs.
	 */
	private static void leg(final @Nullable ModelPart part, final float air, final float xRot, final float lift, final float forward) {
		if (part == null) {
			return;
		}
		final Legs.Leg leg = Legs.of(part);
		final float swing = Mth.lerp(air, part.xRot, xRot);
		if (leg != null) {
			Legs.swing(part, leg, swing);
		} else {
			part.xRot = swing;
		}
		part.y -= (lift + AIR_LEG_HALF_DEPTH * Math.abs(Mth.sin(xRot))) * air;
		part.z -= forward * air;
	}
}
