package dev.horsingaround.client.mixin;

import dev.horsingaround.client.render.RidePoseState;
import net.minecraft.client.model.animal.equine.AbstractEquineModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Neck reaching forward on climbs, and the head toss when the horse runs out of stamina, on the neck so Fresh Animations' animated head and neck (its children)
 * carry it too. The motion matches Fresh Animations' own idle head shake.
 */
@Mixin(AbstractEquineModel.class)
public abstract class AbstractEquineModelMixin {
	@Shadow
	@Final
	protected ModelPart headParts;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EquineRenderState;)V", at = @At("TAIL"))
	private void horsingaround$headShake(final EquineRenderState state, final CallbackInfo ci) {
		final RidePoseState pose = (RidePoseState) state;
		this.headParts.xRot += pose.horsingaround$neckPitch();
		final float yaw = pose.horsingaround$headShakeYaw();
		if (yaw != 0.0F) {
			this.headParts.yRot += yaw;
			this.headParts.zRot += pose.horsingaround$headShakeRoll();
		}
	}
}
