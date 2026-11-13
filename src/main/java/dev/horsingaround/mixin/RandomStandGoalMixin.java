package dev.horsingaround.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.world.entity.ai.goal.RandomStandGoal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * No random rear under a rider. Before 1.21.5 the server runs a horse's own behaviour while a player rides it, and a
 * rearing horse on the ground doesn't move: every few minutes the horse stopped dead for a second with the ride still
 * at pace. (From 1.21.5 the game leaves a player's mount alone, and this never comes up.)
 */
@Mixin(RandomStandGoal.class)
abstract class RandomStandGoalMixin {
	@Shadow
	@Final
	private AbstractHorse horse;

	@ModifyReturnValue(method = "canUse", at = @At("RETURN"))
	private boolean horsingaround$notUnderARider(final boolean rear) {
		return rear && !((RideStateHolder) this.horse).horsingaround$playerRidden();
	}
}
