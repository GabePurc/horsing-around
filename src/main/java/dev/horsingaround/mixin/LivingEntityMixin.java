package dev.horsingaround.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.ride.RideTuning;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player-ridden horse's collision box is narrowed to about its body's real width (vanilla's is a 1.4-block square). */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
	private EntityDimensions horsingaround$riddenWidth(final EntityDimensions dimensions) {
		if ((Object) this instanceof RideStateHolder holder && holder.horsingaround$ride().narrow) {
			return new EntityDimensions(dimensions.width() * RideTuning.RIDDEN_WIDTH_SCALE, dimensions.height(), dimensions.eyeHeight(), dimensions.attachments(), dimensions.fixed());
		}
		return dimensions;
	}
}
