package dev.horsingaround.mixin.icy;

import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Icy's swim boost speeds a ridden horse up in water every tick; the horses this mod rides swim at the ride's pace. */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.feature.SwimBoost", remap = false)
public abstract class SwimBoostMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void horsingaround$ridesOwnSwim(final AbstractHorse horse, @Coerce final Object data, final CallbackInfo ci) {
		if (horse.isVehicle() && RideStateHolder.managedRide(horse) != null) {
			ci.cancel();
		}
	}
}
