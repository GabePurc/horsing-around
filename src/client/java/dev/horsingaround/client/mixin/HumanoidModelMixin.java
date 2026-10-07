package dev.horsingaround.client.mixin;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.client.render.RidePoseState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rider pose on a horse: feet in the stirrups, hands on the reins bobbing with the saddle, and the torso turning
 * toward where the rider looks. A hand leaves the reins while it swings, uses an item, or holds a two-handed pose, and
 * comes up in front of the face, head tucked, when pushing through leaves.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	/** Shoulder pivot to hip joint, model pixels. */
	@Unique
	private static final float HIP_DEPTH = 12.0F;

	@Shadow
	@Final
	public ModelPart head;
	@Shadow
	@Final
	public ModelPart body;
	@Shadow
	@Final
	public ModelPart rightArm;
	@Shadow
	@Final
	public ModelPart leftArm;
	@Shadow
	@Final
	public ModelPart rightLeg;
	@Shadow
	@Final
	public ModelPart leftLeg;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void horsingaround$riderPose(final HumanoidRenderState state, final CallbackInfo ci) {
		if (!state.isPassenger || !(state instanceof RidePoseState rider) || !rider.horsingaround$isRider()) {
			return;
		}
		final float legPitch = rider.horsingaround$legPitch();
		final float legRoll = rider.horsingaround$legRoll();
		this.rightLeg.xRot = -STIRRUP_LEG_FORWARD + legPitch;
		this.rightLeg.yRot = STIRRUP_TOE_OUT;
		this.rightLeg.zRot = STIRRUP_LEG_SPLAY + legRoll;
		this.leftLeg.xRot = -STIRRUP_LEG_FORWARD + legPitch;
		this.leftLeg.yRot = -STIRRUP_TOE_OUT;
		this.leftLeg.zRot = -STIRRUP_LEG_SPLAY + legRoll;

		// Pelvis slide: swing the torso from the shoulders (its pivot) and carry the hips, where the legs attach.
		final float pelvis = rider.horsingaround$pelvis();
		if (pelvis != 0.0F) {
			final float hipDrop = HIP_DEPTH * (Mth.cos(pelvis) - 1.0F);
			final float hipForward = HIP_DEPTH * Mth.sin(pelvis);
			this.body.xRot += pelvis;
			this.rightLeg.y += hipDrop;
			this.rightLeg.z += hipForward;
			this.leftLeg.y += hipDrop;
			this.leftLeg.z += hipForward;
		}

		final boolean swinging = state.swingAnimation > 0.0F && state.currentSwing != null;
		final HumanoidArm swingArm = swinging ? state.currentSwing.hand().asArm(state.mainArm) : null;
		final HumanoidArm usingArm = state.isUsingItem ? state.useItemHand.asArm(state.mainArm) : null;
		final float twist = rider.horsingaround$twist();
		if (!swinging) {
			// Turn the torso and carry the shoulders round with it, as vanilla does for attack swings.
			final float shoulder = 5.0F * state.ageScale;
			this.body.yRot = twist;
			this.rightArm.x = -Mth.cos(twist) * shoulder;
			this.rightArm.z = Mth.sin(twist) * shoulder;
			this.leftArm.x = Mth.cos(twist) * shoulder;
			this.leftArm.z = -Mth.sin(twist) * shoulder;
			this.rightArm.yRot += twist;
			this.leftArm.yRot += twist;
		}
		if (state.rightArmPose.isTwoHanded() || state.leftArmPose.isTwoHanded()) {
			return;
		}
		final float handBob = rider.horsingaround$handBob();
		if (horsingaround$onReins(state.rightArmPose, HumanoidArm.RIGHT, swingArm, usingArm)) {
			this.rightArm.xRot = -REINS_ARM_FORWARD + handBob;
			this.rightArm.yRot = -REINS_ARM_INWARD + twist * 0.5F;
			this.rightArm.zRot = 0.0F;
		}
		if (horsingaround$onReins(state.leftArmPose, HumanoidArm.LEFT, swingArm, usingArm)) {
			this.leftArm.xRot = -REINS_ARM_FORWARD + handBob;
			this.leftArm.yRot = REINS_ARM_INWARD + twist * 0.5F;
			this.leftArm.zRot = 0.0F;
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
				final ModelPart part = left ? this.leftArm : this.rightArm;
				final float inward = left ? SHIELD_ARM_INWARD : -SHIELD_ARM_INWARD;
				part.xRot = Mth.lerp(shield, part.xRot, -SHIELD_ARM_RAISE + handBob * 0.5F);
				part.yRot = Mth.lerp(shield, part.yRot, inward + twist);
				part.zRot = Mth.lerp(shield, part.zRot, 0.0F);
				this.head.xRot += SHIELD_HEAD_TUCK * shield;
				this.head.yRot += (left ? -SHIELD_HEAD_TURN : SHIELD_HEAD_TURN) * shield;
			}
		}
	}

	@Unique
	private static boolean horsingaround$onReins(
		final HumanoidModel.ArmPose pose, final HumanoidArm arm, final HumanoidArm swingArm, final HumanoidArm usingArm
	) {
		return arm != swingArm && arm != usingArm && (pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM);
	}
}
