package dev.horsingaround.client.mixin;

import dev.horsingaround.client.render.AirLegs;
import dev.horsingaround.client.render.GroundLegs;
import dev.horsingaround.client.render.RidePoseState;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideTuning;
//? if >=1.21.2
import net.minecraft.client.model.Model;
//? if >=1.21.2
import net.minecraft.client.model.animal.equine.AbstractEquineModel;
import net.minecraft.client.model.geom.ModelPart;
//? if >=1.21.2
import net.minecraft.client.renderer.entity.state.EquineRenderState;
//? if <1.21.2 {
/*import dev.horsingaround.client.render.LegacyRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HorseModel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
*///?}
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Neck reaching forward on climbs, hooves on the ground on steps and slopes (legs upright, folding onto higher ground),
 * legs in the shape of a jump in the air, the tail swinging with the horse's motion, and the head toss
 * when the horse runs out of stamina, on the neck so Fresh Animations' animated head and neck (its children) carry it
 * too. The motion matches Fresh Animations' own idle head shake.
 */
//? if >=1.21.2 {
@Mixin(AbstractEquineModel.class)
//?} else
/*@Mixin(HorseModel.class)*/
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
	//? if <1.21.2 {
	/*// (Before 1.21.2 a foal draws legs of its own, which vanilla sets from the grown legs before the ride poses them.)
	@Shadow
	@Final
	private ModelPart rightHindBabyLeg;
	@Shadow
	@Final
	private ModelPart leftHindBabyLeg;
	@Shadow
	@Final
	private ModelPart rightFrontBabyLeg;
	@Shadow
	@Final
	private ModelPart leftFrontBabyLeg;
	*///?}

	//? if >=1.21.2 {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EquineRenderState;)V", at = @At("TAIL"))
	private void horsingaround$headShake(final EquineRenderState state, final CallbackInfo ci) {
		this.horsingaround$pose((RidePoseState) state);
	}
	//?} else {
	/*@Inject(method = "setupAnim(Lnet/minecraft/world/entity/animal/horse/AbstractHorse;FFFFF)V", at = @At("TAIL"))
	private void horsingaround$headShake(
		final AbstractHorse horse, final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float headYaw, final float headPitch,
		final CallbackInfo ci
	) {
		final Object state = LegacyRenderState.of(horse);
		if (state != null) {
			this.horsingaround$pose((RidePoseState) state);
			if (((EntityModel<?>) (Object) this).young) {
				horsingaround$copyPose(this.rightHindLeg, this.rightHindBabyLeg);
				horsingaround$copyPose(this.leftHindLeg, this.leftHindBabyLeg);
				horsingaround$copyPose(this.rightFrontLeg, this.rightFrontBabyLeg);
				horsingaround$copyPose(this.leftFrontLeg, this.leftFrontBabyLeg);
			}
		}
	}

	@Unique
	private static void horsingaround$copyPose(final ModelPart from, final ModelPart to) {
		to.x = from.x;
		to.y = from.y;
		to.z = from.z;
		to.xRot = from.xRot;
		to.yRot = from.yRot;
		to.zRot = from.zRot;
	}

	// Before 1.21.2 one model draws every horse, and the game sets only some of its parts each frame (not where the hind
	// legs sit, nor the head's roll): what the ride moved for one horse stayed for the next one drawn and added up frame
	// on frame (an idle horse's legs drifted off into the sky). Back to the model's own pose before the game poses it.
	@Inject(method = "prepareMobModel(Lnet/minecraft/world/entity/animal/horse/AbstractHorse;FFF)V", at = @At("HEAD"))
	private void horsingaround$resetPose(final AbstractHorse horse, final float limbSwing, final float limbSwingAmount, final float partialTicks, final CallbackInfo ci) {
		this.leftFrontLeg.resetPose();
		this.rightFrontLeg.resetPose();
		this.leftHindLeg.resetPose();
		this.rightHindLeg.resetPose();
		this.headParts.zRot = this.headParts.getInitialPose().zRot;
	}
	*///?}

	@Unique
	private void horsingaround$pose(final RidePoseState pose) {
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
			//? if >=1.21.2 {
			final ModelPart root = ((Model<?>) (Object) this).root();
			//?} else
			/*final ModelPart root = LegacyRenderState.horseRoot(((EntityModel<?>) (Object) this).young);*/
			final float pixels = 16.0F / root.yScale;
			final boolean footing = GroundLegs.pose(
				this.leftFrontLeg, this.rightFrontLeg, this.leftHindLeg, this.rightHindLeg, pose.horsingaround$legTilt(), pose.horsingaround$foreLeg(),
				pose.horsingaround$hindLeg(), pixels, ride, root, air < RideTuning.AIR_GROUNDED || ride != null && !ride.leapt()
			);
			// (A step down is a short fall, not a jump: with the hooves finding their ground, no jump shape. Landing a jump,
			// the jump's shape eases out, and any hoof it leaves in the ground comes up onto it.)
			final float shaped = air * AirLegs.shape(ride, footing && ride != null && !ride.leapLegs());
			if (shaped > 0.0F) {
				AirLegs.pose(this.leftFrontLeg, this.rightFrontLeg, this.leftHindLeg, this.rightHindLeg, shaped, pose.horsingaround$airRise());
				GroundLegs.floor(this.leftFrontLeg, this.rightFrontLeg, this.leftHindLeg, this.rightHindLeg, pixels, ride, root);
			}
		}
		this.tail.xRot += pose.horsingaround$tail();
	}
}
