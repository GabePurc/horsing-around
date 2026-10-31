package dev.horsingaround.gametest.mixin;

import dev.horsingaround.gametest.LegProbe;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After a model is drawn (its parts still in the pose they were drawn in), the leg probe measures it. Tests only. */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At("TAIL"))
	private void horsingaroundtest$measureLegs(final ModelFeatureRenderer.Submit<?> submit, final CallbackInfo ci) {
		LegProbe.measure(submit.model(), submit.state(), submit.pose());
	}
}
