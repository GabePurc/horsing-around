package dev.horsingaround.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.horsingaround.ride.Cadenced;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A ridden horse collecting for a step quickens its stride without changing its gait: the stride's position runs on
 * {@code cadence} times its speed (the speed, which picks the gait, is left alone), between ticks too. 1 for everything
 * else, so nothing changes there.
 */
@Mixin(WalkAnimationState.class)
abstract class WalkAnimationStateMixin implements Cadenced {
	@Shadow
	private float speed;
	@Shadow
	private float position;
	//? if >=1.21.2 {
	@Shadow
	private float positionScale;
	//?}
	@Unique
	private float horsingaround$cadence = 1.0F;

	@Override
	public void horsingaround$setCadence(final float cadence) {
		this.horsingaround$cadence = cadence;
	}

	@Inject(method = "update", at = @At("TAIL"))
	private void horsingaround$quicken(final CallbackInfo ci) {
		if (this.horsingaround$cadence != 1.0F) {
			this.position += this.speed * (this.horsingaround$cadence - 1.0F);
		}
	}

	@ModifyReturnValue(method = "position(F)F", at = @At("RETURN"))
	private float horsingaround$quickenBetweenTicks(final float position, final float partialTicks) {
		if (this.horsingaround$cadence == 1.0F) {
			return position;
		}
		//? if >=1.21.2 {
		return position - this.speed * (this.horsingaround$cadence - 1.0F) * (1.0F - partialTicks) * this.positionScale;
		//?} else
		/*return position - this.speed * (this.horsingaround$cadence - 1.0F) * (1.0F - partialTicks);*/
	}
}
