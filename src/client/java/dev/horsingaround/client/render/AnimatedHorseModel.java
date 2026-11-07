package dev.horsingaround.client.render;

import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideTuning;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
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
	/** The vanilla horse model's legs, pixels: its front hooves ahead of its centre, its hind hooves behind it, a leg's length. */
	private static final float VANILLA_FORE = 9.9F;
	private static final float VANILLA_HIND = 8.0F;
	private static final float VANILLA_LEG = 11.0F;
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

	/** Brings a leg {@code amount} (0..1) of the way back to straight under the body: across it and about its own length. */
	public static void straighten(final ModelPart leg, final float amount) {
		final PartPose pose = leg.getInitialPose();
		leg.x = Mth.lerp(amount, leg.x, pose.x());
		leg.yRot = Mth.lerp(amount, leg.yRot, pose.yRot());
		leg.zRot = Mth.lerp(amount, leg.zRot, pose.zRot());
	}

	/**
	 * Fits the ride to a model built unlike a vanilla horse (another mod's breeds): where its hooves stand and how long its
	 * legs are, the vanilla horse's (RideTuning) scaled by how its legs compare, so the body tilts on steps and slopes
	 * about its own legs. False if the parts have no leg boxes (a tack layer's).
	 */
	public static boolean fitLegs(final ModelPart frontLeft, final ModelPart hindLeft, final RideState ride) {
		return fitLegs(frontLeft, hindLeft, ride, 1.0F);
	}

	/** @param scale how large the model is drawn against the vanilla horse's model (1: the same) */
	public static boolean fitLegs(final ModelPart frontLeft, final ModelPart hindLeft, final RideState ride, final float scale) {
		final Legs.Leg front = Legs.of(frontLeft);
		final Legs.Leg hind = Legs.of(hindLeft);
		if (front == null || hind == null) {
			return false;
		}
		ride.foreHooves = RideTuning.FORE_HOOVES * -(frontLeft.getInitialPose().z() + front.z) / VANILLA_FORE * scale;
		ride.hindHooves = RideTuning.HIND_HOOVES * (hindLeft.getInitialPose().z() + hind.z) / VANILLA_HIND * scale;
		ride.legLength = RideTuning.LEG_LENGTH * (front.length + hind.length) * 0.5F / VANILLA_LEG * scale;
		return true;
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
