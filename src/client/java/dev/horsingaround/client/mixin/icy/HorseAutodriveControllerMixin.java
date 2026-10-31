package dev.horsingaround.client.mixin.icy;

import dev.horsingaround.ride.IcyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Icy's auto-ride holds forward after a double tap of it. Under this mod's riding the same double tap spurs up a gait
 * and keeps the horse riding on (see RideTuning), so Icy's stays off: the rider counts as not on a horse it drives.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.client.HorseAutodriveController", remap = false)
public abstract class HorseAutodriveControllerMixin {
	@ModifyVariable(method = "tick", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private boolean horsingaround$noAutoRide(final boolean eligible) {
		return eligible && !IcyCompat.oursOnClient();
	}
}
