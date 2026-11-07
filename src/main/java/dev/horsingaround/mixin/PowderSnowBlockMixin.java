package dev.horsingaround.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.horsingaround.ride.IcyCompat;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Icy's Better Horses lets a horse in its horseshoes walk on powder snow, but it looks at the shoes themselves, which
 * only the server has. A ridden horse moves on its rider's client, so there it sank in (on Icy's own riding too). On the
 * horses this mod rides the shoes hold on the client as well, read from the gear Icy's mod tells clients a horse wears.
 */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {
	@ModifyReturnValue(method = "canEntityWalkOnPowderSnow", at = @At("RETURN"))
	private static boolean horsingaround$shod(final boolean walks, @Local(argsOnly = true) final Entity entity) {
		return walks || IcyCompat.LOADED && RideStateHolder.managedRide(entity) != null && IcyCompat.shod(entity);
	}
}
