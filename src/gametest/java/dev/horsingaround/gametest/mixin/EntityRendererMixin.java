package dev.horsingaround.gametest.mixin;

import dev.horsingaround.gametest.LeadProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.Entity;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//?} else {
/*import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.phys.Vec3;
*///?}

/**
 * Once a render state is filled, the lead probe notes where its leads go; before 1.21.2, where the lead drawing code puts
 * its ends (the entity renderer from 1.21, the mob renderer before). Tests only.
 */
//? if >=1.21.2 {
@Mixin(EntityRenderer.class)
//?} elif >=1.21 {
/*@Mixin(net.minecraft.client.renderer.entity.EntityRenderer.class)
*///?} else
/*@Mixin(net.minecraft.client.renderer.entity.MobRenderer.class)*/
abstract class EntityRendererMixin {
	//? if >=1.21.2 {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void horsingaroundtest$leads(final Entity entity, final EntityRenderState state, final float partialTicks, final CallbackInfo ci) {
		LeadProbe.extracted(entity, state);
	}
	//?} elif >=1.21 {
	/*// (The lead's ends as drawn: the holder's end, then the leashed entity's lerped place plus its turned offset.)
	@Inject(method = "renderLeash", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0))
	private void horsingaroundtest$leads(final CallbackInfo ci, @Local(argsOnly = true, ordinal = 0) final Entity leashed, @Local(ordinal = 0) final Vec3 end,
		@Local(ordinal = 3) final double startX, @Local(ordinal = 4) final double startY, @Local(ordinal = 5) final double startZ) {
		LeadProbe.drawn(leashed, new Vec3(startX, startY, startZ), end);
	}
	*///?} else {
	/*@Inject(method = "renderLeash", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0))
	private void horsingaroundtest$leads(final CallbackInfo ci, @Local(argsOnly = true) final net.minecraft.world.entity.Mob leashed, @Local(ordinal = 0) final Vec3 end,
		@Local(ordinal = 3) final double startX, @Local(ordinal = 4) final double startY, @Local(ordinal = 5) final double startZ) {
		LeadProbe.drawn(leashed, new Vec3(startX, startY, startZ), end);
	}
	*///?}
}
