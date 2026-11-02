package dev.horsingaround.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The ride's pace: the horse goes at its speed times the ride's. Apart from AbstractHorseMixin because it applies before
 * other mods' horse hooks (priority) instead of after: a mod that sets the ridden speed itself at the method's end (Icy's
 * Better Horses' gears) ends the method there, and applied after it this never ran, so the horse went at its full
 * speed in every gait. Applied first, the others adjust the ride's pace instead (and Icy's gears stand down on the
 * horses this mod rides).
 */
@Mixin(value = AbstractHorse.class, priority = 500)
abstract class AbstractHorseSpeedMixin {
	@ModifyReturnValue(method = "getRiddenSpeed", at = @At("RETURN"))
	private float horsingaround$riddenSpeed(final float attributeSpeed) {
		final RideStateHolder holder = (RideStateHolder) this;
		return holder.horsingaround$managed() && ((AbstractHorse) (Object) this).isLocalInstanceAuthoritative()
			? attributeSpeed * Math.abs(holder.horsingaround$ride().speed)
			: attributeSpeed;
	}
}
