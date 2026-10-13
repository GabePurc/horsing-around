package dev.horsingaround.client.mixin;

import dev.horsingaround.client.render.RiderPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
//? if <1.21.2 {
/*import dev.horsingaround.client.render.LegacyRenderState;
import net.minecraft.world.entity.LivingEntity;
*///?}
//? if >=1.21.2 && <1.21.4 {
/*import dev.horsingaround.client.render.ArmPoseSource;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Shadow;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mob riders (zombie and skeleton horsemen) sit their horses like players do; players are posed in the player model mixin. */
//? if >=1.21.4 || <1.21.2 {
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
//?} else {
/*@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends HumanoidRenderState> implements ArmPoseSource {
	@Shadow
	protected abstract HumanoidModel.ArmPose getArmPose(T state, HumanoidArm arm);

	@Override
	@SuppressWarnings("unchecked")
	public HumanoidModel.ArmPose horsingaround$armPose(final HumanoidRenderState state, final HumanoidArm arm) {
		return this.getArmPose((T) state, arm);
	}

*///?}
	//? if >=1.21.2 {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void horsingaround$riderPose(final HumanoidRenderState state, final CallbackInfo ci) {
		if (!((Object) this instanceof PlayerModel)) {
			RiderPose.apply((HumanoidModel<?>) (Object) this, state);
		}
	}
	//?} else {
	/*@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void horsingaround$riderPose(
		final LivingEntity entity, final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float headYaw, final float headPitch,
		final CallbackInfo ci
	) {
		if (!((Object) this instanceof PlayerModel) && LegacyRenderState.of(entity) instanceof HumanoidRenderState state) {
			final HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
			RiderPose.apply(model, state);
			// (The hat is a part of its own here, a copy of the head, rather than a child of it.)
			model.hat.copyFrom(model.head);
		}
	}
	*///?}
}
