package dev.horsingaround.client.mixin;

import dev.horsingaround.client.render.RiderPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//? if <1.21.2 {
/*import dev.horsingaround.client.render.LegacyRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.LivingEntity;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player riding a horse this mod manages is posed last (a high priority puts this after other mods' hooks here), so
 * mods that pose riders on any horse don't undo the seat; elsewhere they are untouched.
 */
@Mixin(value = PlayerModel.class, priority = 1500)
public abstract class PlayerModelMixin {
	//? if >=1.21.2 {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
	private void horsingaround$riderPose(final AvatarRenderState state, final CallbackInfo ci) {
		RiderPose.apply((PlayerModel) (Object) this, state);
	}
	//?} else {
	/*@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
	private void horsingaround$riderPose(
		final LivingEntity entity, final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float headYaw, final float headPitch,
		final CallbackInfo ci
	) {
		if (LegacyRenderState.of(entity) instanceof HumanoidRenderState state) {
			final PlayerModel<?> model = (PlayerModel<?>) (Object) this;
			RiderPose.apply(model, state);
			// (The clothing layers are parts of their own here, copies of the body, arms and legs, rather than their children.)
			model.hat.copyFrom(model.head);
			model.jacket.copyFrom(model.body);
			model.leftSleeve.copyFrom(model.leftArm);
			model.rightSleeve.copyFrom(model.rightArm);
			model.leftPants.copyFrom(model.leftLeg);
			model.rightPants.copyFrom(model.rightLeg);
		}
	}
	*///?}
}
