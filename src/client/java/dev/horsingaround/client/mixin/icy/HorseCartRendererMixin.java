package dev.horsingaround.client.mixin.icy;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.horsingaround.client.render.IcyCartDrawing;
//? if <1.21.2
/*import net.minecraft.world.entity.Entity;*/
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
	//? if >=1.21.2 {
	// (Just before Icy's mod reads how far the bed has bounced, for its riders: none.)
	@Inject(method = "recordBed", at = @At("HEAD"), remap = false)
	private static void horsingaround$bones(final int id, @Coerce final Object type, @Coerce final Object snapshots, final CallbackInfo ci) {
		IcyCartDrawing.bones(id, snapshots);
	}

	@Inject(method = "applyRotations", at = @At("TAIL"), remap = false)
	private void horsingaround$lean(@Coerce final Object pass, final PoseStack poseStack, final float scale, final CallbackInfo ci) {
		IcyCartDrawing.lean(pass, poseStack);
	}
	//?} elif >=1.21 {
	/*// (Before 1.21.2 the cart is drawn with GeckoLib 4, which hands the renderer the cart itself and has no bones to hold.)
	@Inject(method = "applyRotations", at = @At("TAIL"), remap = false)
	private void horsingaround$lean(
		@Coerce final Entity cart, final PoseStack poseStack, final float ageInTicks, final float yaw, final float partialTick, final float scale, final CallbackInfo ci
	) {
		IcyCartDrawing.lean(cart, poseStack, partialTick);
	}
	*///?} else {
	/*@Inject(method = "applyRotations", at = @At("TAIL"), remap = false)
	private void horsingaround$lean(
		@Coerce final Entity cart, final PoseStack poseStack, final float ageInTicks, final float yaw, final float partialTick, final CallbackInfo ci
	) {
		IcyCartDrawing.lean(cart, poseStack, partialTick);
	}
	*///?}
}
