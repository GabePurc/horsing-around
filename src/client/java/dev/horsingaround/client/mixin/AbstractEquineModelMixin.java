package dev.horsingaround.client.mixin;

import dev.horsingaround.client.compat.AnimationPacks;
import dev.horsingaround.client.render.AirLegs;
import dev.horsingaround.client.render.GroundLegs;
import dev.horsingaround.client.render.LegBones;
import dev.horsingaround.client.render.RidePoseState;
import dev.horsingaround.client.render.Strides;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideTuning;
//? if >=1.21.2
import net.minecraft.client.model.Model;
//? if >=1.21.2 {
import dev.horsingaround.client.render.Legs;
import net.minecraft.client.model.animal.equine.AbstractEquineModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}
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
	/**
	 * Each grown horse-family leg in two pieces (user request, 2026-10-27): the upper LEG_UPPER pixels of vanilla's box,
	 * and below a knee (front legs) or hock (hind legs) the rest, a part of its own hinged at the front edge of a front leg
	 * and the back edge of a hind one, so the lower piece folds back under a front leg and forward under a hind one. The
	 * same size and texture as vanilla's box; straight, as everything but these legs leaves it, it looks the same.
	 */
	@Inject(method = "createBodyMesh", at = @At("RETURN"))
	private static void horsingaround$knees(final CubeDeformation g, final CallbackInfoReturnable<MeshDefinition> cir) {
		final PartDefinition root = cir.getReturnValue().getRoot();
		horsingaround$knee(root, "left_hind_leg", true, -3.0F, -1.0F, 4.0F, 7.0F, false, g);
		horsingaround$knee(root, "right_hind_leg", false, -1.0F, -1.0F, -4.0F, 7.0F, false, g);
		horsingaround$knee(root, "left_front_leg", true, -3.0F, -1.9F, 4.0F, -10.0F, true, g);
		horsingaround$knee(root, "right_front_leg", false, -1.0F, -1.9F, -4.0F, -10.0F, true, g);
	}

	@Unique
	private static void horsingaround$knee(
		final PartDefinition root, final String name, final boolean mirror, final float boxX, final float boxZ, final float x, final float z, final boolean fore,
		final CubeDeformation g
	) {
		final float top = -1.01F;
		final CubeListBuilder upper = CubeListBuilder.create().texOffs(48, 21);
		final CubeListBuilder lower = CubeListBuilder.create().texOffs(48, 21 + (int) Legs.LEG_UPPER);
		if (mirror) {
			upper.mirror();
			lower.mirror();
		}
		upper.addBox(boxX, top, boxZ, 4.0F, Legs.LEG_UPPER, 4.0F, g);
		// (Its hinge on the front or back edge, where the lower piece's box starts.)
		final float hinge = fore ? boxZ : boxZ + 4.0F;
		lower.addBox(boxX, 0.0F, fore ? 0.0F : -4.0F, 4.0F, 11.0F - Legs.LEG_UPPER, 4.0F, g);
		root.addOrReplaceChild(name, upper, PartPose.offset(x, 14.0F, z))
			.addOrReplaceChild(Legs.KNEE, lower, PartPose.offset(0.0F, top + Legs.LEG_UPPER, hinge));
	}

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
		//? if >=1.21.2 {
		if (AnimationPacks.animates(this)) {
			// (An animation pack builds its model on vanilla's, the legs' lower pieces and all, and draws legs of its own:
			// measuring a leg once hides its lower piece there, see Legs.of.)
			Legs.of(this.leftFrontLeg);
			Legs.of(this.rightFrontLeg);
			Legs.of(this.leftHindLeg);
			Legs.of(this.rightHindLeg);
		}
		//?}
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
			// (The plain model's own legs already stand upright in the world, see Strides: no tilt to stand them against.)
			final boolean footing = GroundLegs.pose(
				this.leftFrontLeg, this.rightFrontLeg, this.leftHindLeg, this.rightHindLeg, Strides.posed(ride) ? 0.0F : pose.horsingaround$legTilt(), pose.horsingaround$foreLeg(),
				pose.horsingaround$hindLeg(), pixels, ride, root, air < RideTuning.AIR_GROUNDED || ride != null && !ride.leapt()
			);
			// (A step down is a short fall, not a jump: with the hooves finding their ground, no jump shape. Landing a jump,
			// the jump's shape eases out, and any hoof it leaves in the ground comes up onto it.)
			final float shaped = air * AirLegs.shape(ride, footing && ride != null && !ride.leapLegs());
			if (shaped > 0.0F) {
				AirLegs.pose(this.leftFrontLeg, this.rightFrontLeg, this.leftHindLeg, this.rightHindLeg, shaped, pose.horsingaround$airRise());
				GroundLegs.floor(this.leftFrontLeg, this.rightFrontLeg, this.leftHindLeg, this.rightHindLeg, pixels, ride, root);
			}
			LegBones.draw(ride, root, this.leftFrontLeg, this.rightFrontLeg, this.leftHindLeg, this.rightHindLeg);
		}
		this.tail.xRot += pose.horsingaround$tail();
	}
}
