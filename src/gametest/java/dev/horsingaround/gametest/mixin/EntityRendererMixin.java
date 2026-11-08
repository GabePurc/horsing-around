package dev.horsingaround.gametest.mixin;

import dev.horsingaround.gametest.LeadProbe;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once a render state is filled, the lead probe notes where its leads go. Tests only. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void horsingaroundtest$leads(final Entity entity, final EntityRenderState state, final float partialTicks, final CallbackInfo ci) {
		LeadProbe.extracted(entity, state);
	}
}
