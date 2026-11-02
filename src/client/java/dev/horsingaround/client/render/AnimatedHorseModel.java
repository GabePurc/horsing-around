package dev.horsingaround.client.render;

import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideTuning;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * What the ride adds to a horse model that animates itself (an animation pack's through Entity Model Features, or Icy's
 * Better Horses' own models) once its animation has run, as the vanilla model gets it in AbstractEquineModelMixin:
 * hooves on the ground and the shape of a jump, the neck reaching and the head tossing, the tail, and the stirrups held
 * for the rider's feet. Called for every layer drawn (body, saddle, armour), so they stay together. Render thread only.
 */
public final class AnimatedHorseModel {
	private static final float[] PHASE = new float[1];
	/** Frames the stirrups were held (for tests). */
	public static int stirrupFrames;

	private AnimatedHorseModel() {
	}

	/**
	 * Legs after the model has animated them: hooves on the ground on steps and slopes (upright against the body's tilt,
	 * the pair on higher ground folding onto it), and in the air, the shape of a jump.
	 *
	 * @param pixels model pixels to a block in the legs' frame (16 over the model's scale)
	 */
	public static void legs(
		final ModelPart root, final float pixels, final @Nullable ModelPart leftFront, final @Nullable ModelPart rightFront,
		final @Nullable ModelPart leftHind, final @Nullable ModelPart rightHind, final RideState ride, final float partialTicks
	) {
		final float tilt = ride.pitch(partialTicks) * Mth.DEG_TO_RAD;
		final float air = ride.airLegs(partialTicks);
		final boolean footing = GroundLegs.pose(
			leftFront, rightFront, leftHind, rightHind, tilt, ride.foreLeg(partialTicks), ride.hindLeg(partialTicks), pixels, ride, root,
			air < RideTuning.AIR_GROUNDED || !ride.leapt()
		);
		// (A step down is a short fall, not a jump: with the hooves finding their ground, no jump shape. Landing a jump,
		// the jump's shape eases out, and any hoof it leaves in the ground comes up onto it.)
		final float shaped = air * AirLegs.shape(ride, air <= 0.0F || footing && !ride.leapLegs());
		if (shaped <= 0.0F) {
			return;
		}
		AirLegs.pose(leftFront, rightFront, leftHind, rightHind, shaped, ride.airRise(partialTicks));
		GroundLegs.floor(leftFront, rightFront, leftHind, rightHind, pixels, ride, root);
	}

	/** The tail swings with the horse's motion, on top of the model's own tail animation. */
	public static void tail(final @Nullable ModelPart tail, final RideState ride, final float partialTicks) {
		final float lift = ride.tailLift(partialTicks);
		if (lift != 0.0F && tail != null) {
			tail.xRot += lift;
		}
	}

	/**
	 * Climbing, the neck reaches forward, and further when the rider would be in the way of the head; and a horse that
	 * just ran out of stamina tosses its head. On top of the model's own neck animation.
	 *
	 * @param time the horse's age in ticks, with the partial tick
	 * @return whether the head is tossing
	 */
	public static boolean neck(final @Nullable ModelPart neck, final RideState ride, final float time, final float partialTicks) {
		if (neck == null) {
			return false;
		}
		neck.xRot += RideTuning.neckCounter((ride.pitch(partialTicks) + ride.jumpPitch(partialTicks)) * Mth.DEG_TO_RAD) + ride.neckReach;
		final float shake = ride.headShake(time, PHASE);
		if (shake <= 0.0F) {
			return false;
		}
		neck.yRot += -Mth.sin(PHASE[0]) * RideTuning.HEAD_SHAKE_YAW * shake;
		neck.zRot += Mth.cos(PHASE[0]) * RideTuning.HEAD_SHAKE_ROLL * shake;
		return true;
	}

	/**
	 * Swing the saddle's stirrups forward to meet the seated leg and hold them there against the body's pitch, so the
	 * feet (which the rider pose holds still against the horse) stay in them.
	 */
	public static void stirrups(final @Nullable ModelPart left, final @Nullable ModelPart right, final @Nullable ModelPart body) {
		if (left == null && right == null) {
			return;
		}
		final float swing = -RideTuning.STIRRUP_SWING - (body == null ? 0.0F : body.xRot - body.getInitialPose().xRot());
		stirrupFrames++;
		if (left != null) {
			left.xRot = swing;
			left.zRot = 0.0F;
		}
		if (right != null) {
			right.xRot = swing;
			right.zRot = 0.0F;
		}
	}

	/** The first part named {@code name} under {@code part} (an Entity Model Features copy of it too), or null. */
	public static @Nullable ModelPart find(final ModelPart part, final String name) {
		for (final Map.Entry<String, ModelPart> child : ((PartData) (Object) part).horsingaround$children().entrySet()) {
			if (child.getKey().equals(name) || child.getKey().endsWith("_" + name) && child.getKey().startsWith("EMF")) {
				return child.getValue();
			}
			final ModelPart found = find(child.getValue(), name);
			if (found != null) {
				return found;
			}
		}
		return null;
	}
}
