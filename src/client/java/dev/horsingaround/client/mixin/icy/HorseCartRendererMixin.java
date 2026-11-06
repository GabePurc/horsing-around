package dev.horsingaround.client.mixin.icy;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.horsingaround.client.render.IcyCartDrawing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Icy's carts as drawn, behind a horse this mod rides: see {@link IcyCartDrawing}. */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.client.render.HorseCartRenderer", remap = false)
public abstract class HorseCartRendererMixin {
	// (Just before Icy's mod reads how far the bed has bounced, for its riders: none.)
	@Inject(method = "recordBed", at = @At("HEAD"), remap = false)
	private static void horsingaround$bones(final int id, @Coerce final Object type, @Coerce final Object snapshots, final CallbackInfo ci) {
		IcyCartDrawing.bones(id, snapshots);
	}

	@Inject(method = "applyRotations", at = @At("TAIL"), remap = false)
	private void horsingaround$lean(@Coerce final Object pass, final PoseStack poseStack, final float scale, final CallbackInfo ci) {
		IcyCartDrawing.lean(pass, poseStack);
	}
}
