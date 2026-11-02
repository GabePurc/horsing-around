package dev.horsingaround.mixin.icy;

import dev.horsingaround.ride.IcyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Icy's gaits hold a ridden horse to a walk, trot or canter by capping its speed for the gear picked. Under this mod's
 * riding the ride sets the pace, so every gear rides as none (full speed). Asked where the ride is simulated: the
 * rider's client.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.BhGears", remap = false)
public abstract class BhGearsMixin {
	@ModifyVariable(method = "riddenSpeed", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static int horsingaround$noGear(final int gear) {
		return IcyCompat.oursOnClient() ? 0 : gear;
	}
}
