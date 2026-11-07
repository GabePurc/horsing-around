package dev.horsingaround.client.render;

import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideTuning;
import net.minecraft.client.model.geom.ModelPart;
import org.jspecify.annotations.Nullable;

/**
 * What the ride adds to a horse model built as vanilla's is (vanilla's own, and Realistic Horse Genetics'), once the
 * model has posed itself, from the pose RidePoser worked out for it: the neck reaching forward on climbs and the head
 * toss when the horse runs out of stamina, hooves on the ground on steps and slopes (legs upright, folding onto higher
 * ground), legs in the shape of a jump in the air, and the tail swinging with the horse's motion. Render thread only.
 */
public final class HorseModelPose {
	private HorseModelPose() {
	}

	/** On the neck, so whatever hangs from it (the head, an animation pack's animated head and neck) carries it too. */
	public static void neck(final ModelPart neck, final RidePoseState pose) {
		neck.xRot += pose.horsingaround$neckPitch();
		final float yaw = pose.horsingaround$headShakeYaw();
		if (yaw != 0.0F) {
			neck.yRot += yaw;
			neck.zRot += pose.horsingaround$headShakeRoll();
		}
	}

	/** @param root the model's root part, or what stands in for it (the legs' parent) */
	public static void legs(
		final RidePoseState pose, final ModelPart leftFront, final ModelPart rightFront, final ModelPart leftHind, final ModelPart rightHind,
		final ModelPart root
	) {
		// (Legs an animation pack poses get all of this in its hook, after it has run; here they come with nothing to do,
		// and must leave the ride's leg state alone.)
		if (!pose.horsingaround$legsHere()) {
			return;
		}
		final float air = pose.horsingaround$airLegs();
		final RideState ride = pose.horsingaround$rideState();
		final float pixels = 16.0F / root.yScale;
		final boolean footing = GroundLegs.pose(
			leftFront, rightFront, leftHind, rightHind, pose.horsingaround$legTilt(), pose.horsingaround$foreLeg(), pose.horsingaround$hindLeg(), pixels,
			ride, root, air < RideTuning.AIR_GROUNDED || ride != null && !ride.leapt()
		);
		// (A step down is a short fall, not a jump: with the hooves finding their ground, no jump shape. Landing a jump,
		// the jump's shape eases out, and any hoof it leaves in the ground comes up onto it.)
		final float shaped = air * AirLegs.shape(ride, air <= 0.0F || footing && ride != null && !ride.leapLegs());
		if (shaped > 0.0F) {
			AirLegs.pose(leftFront, rightFront, leftHind, rightHind, shaped, pose.horsingaround$airRise());
			GroundLegs.floor(leftFront, rightFront, leftHind, rightHind, pixels, ride, root);
		}
	}

	public static void tail(final @Nullable ModelPart tail, final RidePoseState pose) {
		if (tail != null) {
			tail.xRot += pose.horsingaround$tail();
		}
	}
}
