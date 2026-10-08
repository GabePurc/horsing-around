package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Rider pose on a horse: feet in the stirrups, hands on the reins bobbing with the saddle, and the torso turning
 * toward where the rider looks. A hand leaves the reins while it swings, uses an item, or holds a two-handed pose, and
 * comes up in front of the face, head tucked, when pushing through leaves. Applied last in the model's setup (for
 * players after the player model and mods that pose riders on any horse, such as Not Enough Animations).
 */
public final class RiderPose {
	/** Shoulder pivot to hip joint, model pixels. */
	private static final float HIP_DEPTH = 12.0F;

	private RiderPose() {
	}

	public static void apply(final HumanoidModel<?> model, final HumanoidRenderState state) {
		if (!state.isPassenger || !(state instanceof RidePoseState rider) || !rider.horsingaround$isRider()) {
			return;
		}
		final float legPitch = rider.horsingaround$legPitch();
		final float legRoll = rider.horsingaround$legRoll();
		model.rightLeg.xRot = -STIRRUP_LEG_FORWARD + legPitch;
		model.rightLeg.yRot = STIRRUP_TOE_OUT;
		model.rightLeg.zRot = STIRRUP_LEG_SPLAY + legRoll;
		model.leftLeg.xRot = -STIRRUP_LEG_FORWARD + legPitch;
		model.leftLeg.yRot = -STIRRUP_TOE_OUT;
		model.leftLeg.zRot = -STIRRUP_LEG_SPLAY + legRoll;

		// Pelvis slide: swing the torso from the shoulders (its pivot) and carry the hips, where the legs attach. The torso
		// is otherwise upright in the saddle (the whole-body lean is applied to the rider model as a whole).
		final float pelvis = rider.horsingaround$pelvis();
		model.body.xRot = pelvis;
		if (pelvis != 0.0F) {
			final float hipDrop = HIP_DEPTH * (Mth.cos(pelvis) - 1.0F);
			final float hipForward = HIP_DEPTH * Mth.sin(pelvis);
			model.rightLeg.y += hipDrop;
			model.rightLeg.z += hipForward;
			model.leftLeg.y += hipDrop;
			model.leftLeg.z += hipForward;
		}

		final boolean swinging = state.swingAnimation > 0.0F && state.currentSwing != null;
		final HumanoidArm swingArm = swinging ? state.currentSwing.hand().asArm(state.mainArm) : null;
		final HumanoidArm usingArm = state.isUsingItem ? state.useItemHand.asArm(state.mainArm) : null;
		final float twist = rider.horsingaround$twist();
		if (!swinging) {
			// Turn the torso and carry the shoulders round with it, as vanilla does for attack swings.
			final float shoulder = 5.0F * state.ageScale;
			model.body.yRot = twist;
			model.rightArm.x = -Mth.cos(twist) * shoulder;
			model.rightArm.z = Mth.sin(twist) * shoulder;
			model.leftArm.x = Mth.cos(twist) * shoulder;
			model.leftArm.z = -Mth.sin(twist) * shoulder;
			model.rightArm.yRot += twist;
			model.leftArm.yRot += twist;
		}
		if (state.rightArmPose.isTwoHanded() || state.leftArmPose.isTwoHanded()) {
			return;
		}
		final float handBob = rider.horsingaround$handBob();
		if (onReins(state.rightArmPose, HumanoidArm.RIGHT, swingArm, usingArm)) {
			model.rightArm.xRot = -REINS_ARM_FORWARD + handBob;
			model.rightArm.yRot = -REINS_ARM_INWARD + twist * 0.5F;
			model.rightArm.zRot = 0.0F;
		}
		if (onReins(state.leftArmPose, HumanoidArm.LEFT, swingArm, usingArm)) {
			model.leftArm.xRot = -REINS_ARM_FORWARD + handBob;
			model.leftArm.yRot = REINS_ARM_INWARD + twist * 0.5F;
			model.leftArm.zRot = 0.0F;
		}

		// Leaves: the off hand (or the other, if that one is busy) comes up in front of the face, and the head tucks down
		// and turns a little away.
		final float shield = rider.horsingaround$shield();
		if (shield > 0.0F) {
			HumanoidArm arm = state.mainArm.getOpposite();
			if (arm == swingArm || arm == usingArm) {
				arm = arm.getOpposite();
			}
			if (arm != swingArm && arm != usingArm) {
				final boolean left = arm == HumanoidArm.LEFT;
				final ModelPart part = left ? model.leftArm : model.rightArm;
				final float inward = left ? SHIELD_ARM_INWARD : -SHIELD_ARM_INWARD;
				part.xRot = Mth.lerp(shield, part.xRot, -SHIELD_ARM_RAISE + handBob * 0.5F);
				part.yRot = Mth.lerp(shield, part.yRot, inward + twist);
				part.zRot = Mth.lerp(shield, part.zRot, 0.0F);
				model.head.xRot += SHIELD_HEAD_TUCK * shield;
				model.head.yRot += (left ? -SHIELD_HEAD_TURN : SHIELD_HEAD_TURN) * shield;
			}
		}
	}

	private static boolean onReins(
		final HumanoidModel.ArmPose pose, final HumanoidArm arm, final HumanoidArm swingArm, final HumanoidArm usingArm
	) {
		return arm != swingArm && arm != usingArm && (pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM);
	}
}
