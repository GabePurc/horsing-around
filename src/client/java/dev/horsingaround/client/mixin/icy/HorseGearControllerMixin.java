package dev.horsingaround.client.mixin.icy;

import dev.horsingaround.ride.IcyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Icy's gear keys pick a gait the horse then holds, keys let go. Under this mod's riding (which has gaits of its own)
 * they change nothing, and no gear is ever engaged.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.client.HorseGearController", remap = false)
public abstract class HorseGearControllerMixin {
	@Inject(method = "shift", at = @At("HEAD"), cancellable = true)
	private void horsingaround$noShift(final CallbackInfoReturnable<Integer> cir) {
		if (IcyCompat.oursOnClient()) {
			cir.setReturnValue(0);
		}
	}

	/** The rider counts as not on a horse the gears drive. */
	@ModifyVariable(method = "tick", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private boolean horsingaround$noGears(final boolean eligible) {
		return eligible && !IcyCompat.oursOnClient();
	}
}
