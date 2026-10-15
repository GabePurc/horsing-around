package dev.horsingaround.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.horsingaround.client.RideCamera;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
//? if <1.21.2
/*import net.minecraft.client.Minecraft;*/
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Widens the view slightly as the horse opens up past a trot. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@ModifyReturnValue(method = "getFieldOfViewModifier", at = @At("RETURN"))
	//? if >=1.21.2 {
	private float horsingaround$gallopFov(final float modifier, @Local(argsOnly = true) final float effectScale) {
	//?} else {
	/*private float horsingaround$gallopFov(final float modifier) {
		final float effectScale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
	*///?}
		// (Not while looking through a spyglass: vanilla gives that its own fixed zoom.)
		final AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
		final RideState s = player.isScoping() ? null : RideStateHolder.managedRide(player.getVehicle());
		return s != null ? modifier * RideCamera.fovScale(s, effectScale) : modifier;
	}
}
