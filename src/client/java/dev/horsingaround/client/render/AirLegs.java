package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The shape of a horse's legs in a jump, on top of whatever posed them (vanilla model or an animation pack): rising,
 * the front legs fold up under the chest and the hind legs push back; coming down, the front legs reach forward and
 * down for the ground, one ahead of the other, and the hind legs gather under the body.
 */
public final class AirLegs {
	private AirLegs() {
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
		// Folded while climbing, reaching once it starts down; the hind legs push only early in the climb.
		final float tuck = smoothstep((rise + 0.2F) / 0.6F);
		final float push = smoothstep((rise - 0.1F) / 0.6F);
		final float fore = -Mth.lerp(tuck, FORE_AIR_REACH, FORE_AIR_TUCK);
		final float stagger = FORE_AIR_STAGGER * (1.0F - tuck) * 0.5F;
		final float hind = Mth.lerp(push, -HIND_AIR_TUCK, HIND_AIR_PUSH);
		final float hindLift = HIND_AIR_LIFT * (1.0F - push);
		leg(leftFront, air, fore - stagger, FORE_AIR_LIFT * tuck, FORE_AIR_BACK * tuck);
		leg(rightFront, air, fore + stagger, FORE_AIR_LIFT * tuck, FORE_AIR_BACK * tuck);
		leg(leftHind, air, hind, hindLift, 0.0F);
		leg(rightHind, air, hind, hindLift, 0.0F);
	}

	/**
	 * Swings a leg to {@code xRot} and draws it {@code lift} up and {@code back} toward the tail into the body (model
	 * pixels), plus as far up as the swing tips its top's corner out of the body, so no gap shows where it hangs.
	 */
	private static void leg(final @Nullable ModelPart leg, final float air, final float xRot, final float lift, final float back) {
		if (leg != null) {
			leg.xRot = Mth.lerp(air, leg.xRot, xRot);
			leg.y -= (lift + AIR_LEG_HALF_DEPTH * Math.abs(Mth.sin(xRot))) * air;
			leg.z += back * air;
		}
	}

	private static float smoothstep(final float t) {
		final float x = Mth.clamp(t, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
