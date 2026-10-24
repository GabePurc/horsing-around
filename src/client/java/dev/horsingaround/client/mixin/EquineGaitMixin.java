package dev.horsingaround.client.mixin;

import dev.horsingaround.client.compat.AnimationPacks;
import dev.horsingaround.client.render.GaitCycle;
import dev.horsingaround.client.render.RidePoseState;
//? if >=1.21.2 {
import net.minecraft.client.model.Model;
import net.minecraft.client.model.animal.equine.AbstractEquineModel;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
//?} else {
/*import dev.horsingaround.client.render.LegacyRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HorseModel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
*///?}
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The horse family's own gaits on the plain model, for the horses this mod manages, in place of vanilla's leg swing
 * (see {@link GaitCycle}); a model an animation pack animates is left to the pack. A lower priority than the equine model
 * mixin's, so this runs first and the ride's legs (hooves on the ground, the shape of a jump) go on top of it.
 */
//? if >=1.21.2 {
@Mixin(value = AbstractEquineModel.class, priority = 900)
//?} else
/*@Mixin(value = HorseModel.class, priority = 900)*/
public abstract class EquineGaitMixin {
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

	//? if >=1.21.2 {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EquineRenderState;)V", at = @At("TAIL"))
	private void horsingaround$gait(final EquineRenderState state, final CallbackInfo ci) {
		final RidePoseState pose = (RidePoseState) state;
		if (pose.horsingaround$rideState() != null && !AnimationPacks.animates(this)) {
			GaitCycle.pose(
				this.leftFrontLeg, this.rightFrontLeg, this.leftHindLeg, this.rightHindLeg, this.headParts, this.tail, state.walkAnimationPos,
				state.walkAnimationSpeed, state.isInWater, state.standAnimation, state.eatAnimation, pose.horsingaround$legTilt(), pose.horsingaround$gaitLift(),
				16.0F / ((Model<?>) (Object) this).root().yScale, pose.horsingaround$airLegs()
			);
		}
	}
	//?} else {
	/*@Inject(method = "setupAnim(Lnet/minecraft/world/entity/animal/horse/AbstractHorse;FFFFF)V", at = @At("TAIL"))
	private void horsingaround$gait(
		final AbstractHorse horse, final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float headYaw, final float headPitch,
		final CallbackInfo ci
	) {
		final Object state = LegacyRenderState.of(horse);
		if (state != null && !AnimationPacks.animates(this)) {
			final RidePoseState pose = (RidePoseState) state;
			final float partialTicks = ageInTicks - horse.tickCount;
			GaitCycle.pose(
				this.leftFrontLeg, this.rightFrontLeg, this.leftHindLeg, this.rightHindLeg, this.headParts, this.tail, limbSwing, limbSwingAmount,
				horse.isInWater(), horse.getStandAnim(partialTicks), horse.getEatAnim(partialTicks), pose.horsingaround$legTilt(), pose.horsingaround$gaitLift(),
				16.0F / LegacyRenderState.horseRoot(((EntityModel<?>) (Object) this).young).yScale, pose.horsingaround$airLegs()
			);
		}
	}
	*///?}
}
