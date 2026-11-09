package dev.horsingaround.gametest.mixin;

import dev.horsingaround.gametest.LegProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.9 {
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Shadow;
*///?}

/**
 * After a model is drawn (its parts still in the pose they were drawn in), the leg probe measures it. Tests only. From
 * 1.21.9 models are drawn from a queue of submitted ones; before, the living renderer draws its model itself.
 */
//? if >=1.21.9 {
@Mixin(ModelFeatureRenderer.class)
//?} else
/*@Mixin(LivingEntityRenderer.class)*/
public abstract class LegProbeMixin {
	//? if >=26.2 {
	@Inject(method = "prepareModel", at = @At("TAIL"))
	private void horsingaroundtest$measureLegs(final ModelFeatureRenderer.Submit<?> submit, final CallbackInfo ci) {
		LegProbe.measure(submit.model(), submit.state(), submit.pose());
	}
	//?} elif >=1.21.9 {
	/*@Inject(method = "renderModel", at = @At("TAIL"))
	private void horsingaroundtest$measureLegs(final CallbackInfo ci, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) final net.minecraft.client.renderer.SubmitNodeStorage.ModelSubmit<?> submit) {
		LegProbe.measure(submit.model(), submit.state(), submit.pose());
	}
	*///?} elif >=1.21.2 {
	/*@Shadow
	protected EntityModel<?> model;

	@Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", shift = At.Shift.AFTER))
	private void horsingaroundtest$measureLegs(final net.minecraft.client.renderer.entity.state.LivingEntityRenderState state, final PoseStack poseStack, final MultiBufferSource buffers, final int light, final CallbackInfo ci) {
		LegProbe.measure(this.model, state, poseStack.last());
	}
	*///?} elif >=1.21 {
	/*@Shadow
	protected EntityModel<?> model;

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", shift = At.Shift.AFTER))
	private void horsingaroundtest$measureLegs(final net.minecraft.world.entity.LivingEntity entity, final float yaw, final float partialTicks, final PoseStack poseStack, final MultiBufferSource buffers, final int light, final CallbackInfo ci) {
		LegProbe.measure(this.model, net.minecraft.util.Mth.lerp(partialTicks, entity.xo, entity.getX()), net.minecraft.util.Mth.lerp(partialTicks, entity.yo, entity.getY()),
			net.minecraft.util.Mth.lerp(partialTicks, entity.zo, entity.getZ()), net.minecraft.util.Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot), poseStack.last());
	}
	*///?} else {
	/*@Shadow
	protected EntityModel<?> model;

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V", shift = At.Shift.AFTER))
	private void horsingaroundtest$measureLegs(final net.minecraft.world.entity.LivingEntity entity, final float yaw, final float partialTicks, final PoseStack poseStack, final MultiBufferSource buffers, final int light, final CallbackInfo ci) {
		LegProbe.measure(this.model, net.minecraft.util.Mth.lerp(partialTicks, entity.xo, entity.getX()), net.minecraft.util.Mth.lerp(partialTicks, entity.yo, entity.getY()),
			net.minecraft.util.Mth.lerp(partialTicks, entity.zo, entity.getZ()), net.minecraft.util.Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot), poseStack.last());
	}
	*///?}
}
