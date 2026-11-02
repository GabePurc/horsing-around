package dev.horsingaround.client.mixin.icy;

import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Icy's horse model hands the rider its saddle's motion, which Icy's renderer then moves the rider by. On a horse this
 * mod rides, the rider already rides that motion (measured in BhHorseModelMixin), so Icy's is withheld: on top, the
 * rider bobbed and leaned twice.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.client.render.BhRiderMotion", remap = false)
public abstract class BhRiderMotionMixin {
	@Shadow(remap = false)
	public static void remove(final int id) {
		throw new AssertionError();
	}

	@Inject(method = "publish", at = @At("HEAD"), cancellable = true)
	private static void horsingaround$riddenByThisMod(final int horseId, @Coerce final Object motion, final CallbackInfo ci) {
		final ClientLevel level = Minecraft.getInstance().level;
		if (level != null && level.getEntity(horseId) instanceof RideStateHolder holder && holder.horsingaround$managed()) {
			remove(horseId);
			ci.cancel();
		}
	}
}
