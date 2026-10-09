package dev.horsingaround.client.mixin;

import dev.horsingaround.client.render.AirLegs;
import dev.horsingaround.client.render.GroundLegs;
import dev.horsingaround.client.render.RidePoseState;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideTuning;
import net.minecraft.client.model.Model;
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
 * Neck reaching forward on climbs, hooves on the ground on steps and slopes (legs upright, folding onto higher ground),
 * legs in the shape of a jump in the air, the tail swinging with the horse's motion, and the head toss
 * when the horse runs out of stamina, on the neck so Fresh Animations' animated head and neck (its children) carry it
 * too. The motion matches Fresh Animations' own idle head shake.
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
	@Shadow
	@Final
	protected ModelPart tail;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EquineRenderState;)V", at = @At("TAIL"))
	private void horsingaround$headShake(final EquineRenderState state, final CallbackInfo ci) {
		final RidePoseState pose = (RidePoseState) state;
		this.headParts.xRot += pose.horsingaround$neckPitch();
		final float yaw = pose.horsingaround$headShakeYaw();
		if (yaw != 0.0F) {
			this.headParts.yRot += yaw;
			this.headParts.zRot += pose.horsingaround$headShakeRoll();
		}
		// (Legs an animation pack poses get all of this in its hook, after it has run; here they come with nothing to do,
		// and must leave the ride's leg state alone.)
		if (pose.horsingaround$legsHere()) {
			final float air = pose.horsingaround$airLegs();
			final RideState ride = pose.horsingaround$rideState();
			final float pixels = 16.0F / ((Model<?>) (Object) this).root().yScale;
			final ModelPart root = ((Model<?>) (Object) this).root();
			final boolean footing = GroundLegs.pose(
				this.leftFrontLeg, this.rightFrontLeg, this.leftHindLeg, this.rightHindLeg, pose.horsingaround$legTilt(), pose.horsingaround$foreLeg(),
				pose.horsingaround$hindLeg(), pixels, ride, root, air < RideTuning.AIR_GROUNDED || ride != null && !ride.leapt()
			);
			// (A step down is a short fall, not a jump: with the hooves finding their ground, no jump shape. Landing a jump,
			// the jump's shape eases out, and any hoof it leaves in the ground comes up onto it.)
			if (air > 0.0F && !(footing && ride != null && !ride.leapLegs())) {
				AirLegs.pose(this.leftFrontLeg, this.rightFrontLeg, this.leftHindLeg, this.rightHindLeg, air, pose.horsingaround$airRise());
				GroundLegs.floor(this.leftFrontLeg, this.rightFrontLeg, this.leftHindLeg, this.rightHindLeg, pixels, ride, root);
			}
		}
		this.tail.xRot += pose.horsingaround$tail();
	}
}
