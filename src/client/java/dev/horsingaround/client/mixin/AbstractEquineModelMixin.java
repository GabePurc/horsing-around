package dev.horsingaround.client.mixin;

import dev.horsingaround.client.render.AirLegs;
import dev.horsingaround.client.render.RidePoseState;
import dev.horsingaround.ride.RideTuning;
import net.minecraft.client.model.animal.equine.AbstractEquineModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Neck reaching forward on climbs, legs folding up onto a step (and reaching down for one, and driving the
 * hindquarters up), legs in the shape of a jump in the air, and the head toss when the horse runs out of stamina, on the neck so Fresh Animations' animated
 * head and neck (its children) carry it too. The motion matches Fresh Animations' own idle head shake.
 */
@Mixin(AbstractEquineModel.class)
public abstract class AbstractEquineModelMixin {
	@Shadow
	@Final
	protected ModelPart headParts;
	@Shadow
	@Final
	protected ModelPart rightHindLeg;
	@Shadow
	@Final
	protected ModelPart leftHindLeg;
	@Shadow
	@Final
	protected ModelPart rightFrontLeg;
	@Shadow
	@Final
	protected ModelPart leftFrontLeg;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EquineRenderState;)V", at = @At("TAIL"))
	private void horsingaround$headShake(final EquineRenderState state, final CallbackInfo ci) {
		final RidePoseState pose = (RidePoseState) state;
		this.headParts.xRot += pose.horsingaround$neckPitch();
		final float yaw = pose.horsingaround$headShakeYaw();
		if (yaw != 0.0F) {
			this.headParts.yRot += yaw;
			this.headParts.zRot += pose.horsingaround$headShakeRoll();
		}
		final float fore = pose.horsingaround$foreLeg();
		if (fore != 0.0F) {
			final float tuck = Math.max(fore, 0.0F);
			final float swing = -tuck * RideTuning.FORE_TUCK_ANGLE + Math.min(fore, 0.0F) * RideTuning.FORE_REACH_ANGLE;
			final float lift = tuck * RideTuning.FORE_TUCK_LIFT;
			this.leftFrontLeg.xRot += swing;
			this.rightFrontLeg.xRot += swing;
			this.leftFrontLeg.y -= lift;
			this.rightFrontLeg.y -= lift;
		}
		final float hind = pose.horsingaround$hindLeg();
		if (hind != 0.0F) {
			final float swing = hind * (hind > 0.0F ? RideTuning.HIND_DRIVE_ANGLE : RideTuning.HIND_GATHER_ANGLE);
			this.leftHindLeg.xRot += swing;
			this.rightHindLeg.xRot += swing;
		}
		AirLegs.pose(this.leftFrontLeg, this.rightFrontLeg, this.leftHindLeg, this.rightHindLeg, pose.horsingaround$airLegs(), pose.horsingaround$airRise());
	}
}
