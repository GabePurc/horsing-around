package dev.horsingaround.mixin.icy;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.horsingaround.ride.Mounts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Icy's charge bashes whatever a galloping horse runs into and then slows the horse to 40% for a second and a half. On a
 * horse this mod rides, galloping into small creatures is this mod's trample, and the horse keeps its pace: the charge
 * stands down (the rest of Icy's combat stays).
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.feature.HorseCombat", remap = false)
public abstract class HorseCombatMixin {
	@ModifyReturnValue(method = "charging", at = @At("RETURN"))
	private static boolean horsingaround$noCharge(final boolean charging, @Local(argsOnly = true) final Player rider) {
		final Entity horse = rider.getVehicle();
		return charging && !(horse != null && Mounts.manages(horse));
	}
}
