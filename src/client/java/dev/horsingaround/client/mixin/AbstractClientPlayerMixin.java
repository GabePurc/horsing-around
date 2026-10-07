package dev.horsingaround.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.horsingaround.client.RideCamera;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Widens the view slightly as the horse opens up past a trot. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@ModifyReturnValue(method = "getFieldOfViewModifier", at = @At("RETURN"))
	private float horsingaround$gallopFov(final float modifier, @Local(argsOnly = true) final float effectScale) {
		return ((AbstractClientPlayer) (Object) this).getVehicle() instanceof RideStateHolder holder && holder.horsingaround$managed()
			? modifier * RideCamera.fovScale(holder.horsingaround$ride(), effectScale)
			: modifier;
	}
}
