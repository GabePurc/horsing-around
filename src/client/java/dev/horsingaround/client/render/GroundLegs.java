package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Hooves on the ground on steps, stairs and slopes, on top of whatever posed the legs (vanilla model or an animation
 * pack, so the stride stays): every leg stands upright against the body's tilt, and the pair on higher ground swings
 * forward to fold onto it (see {@code RideController#steps}). Each leg's top is drawn up into the body by its swing, so
 * no gap shows at the hip or shoulder.
 */
public final class GroundLegs {
	private GroundLegs() {
	}

	/**
	 * @param upright swing that stands the legs upright against the body's tilt, radians (hoof back positive)
	 * @param fore    front legs' fold, radians (hoof forward)
	 * @param hind    hind legs' fold, radians (hoof forward)
	 */
	public static void pose(
		final @Nullable ModelPart leftFront, final @Nullable ModelPart rightFront, final @Nullable ModelPart leftHind, final @Nullable ModelPart rightHind,
		final float upright, final float fore, final float hind
	) {
		if (upright == 0.0F && fore == 0.0F && hind == 0.0F) {
			return;
		}
		leg(leftFront, upright - fore);
		leg(rightFront, upright - fore);
		leg(leftHind, upright - hind);
		leg(rightHind, upright - hind);
	}

	private static void leg(final @Nullable ModelPart leg, final float swing) {
		if (leg != null) {
			leg.xRot += swing;
			leg.y -= LEG_HALF_DEPTH * Math.abs(Mth.sin(swing));
		}
	}
}
