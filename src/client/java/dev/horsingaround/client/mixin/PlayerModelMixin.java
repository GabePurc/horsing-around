package dev.horsingaround.client.mixin;

import dev.horsingaround.client.render.RiderPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
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
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
	private void horsingaround$riderPose(final AvatarRenderState state, final CallbackInfo ci) {
		RiderPose.apply((PlayerModel) (Object) this, state);
	}
}
