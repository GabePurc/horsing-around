package dev.horsingaround.mixin;

//? if <1.21.11
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;*/
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.horsingaround.ride.Cadenced;
import dev.horsingaround.ride.Mounts;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.ride.RideTuning;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Physics of a player-ridden horse that vanilla declares on {@link LivingEntity}. Hooked here, as result changes, so
 * they chain with other mods' hooks instead of competing with them as overrides; anything that isn't a ridden managed
 * horse falls through after one type check. Applied after other mods' hooks here, like the horse mixin.
 */
@Mixin(value = LivingEntity.class, priority = 1500)
abstract class LivingEntityMixin {
	/** A player-ridden horse's collision box is narrowed to about its body's real width (vanilla's is a 1.4-block square). */
	//? if >=1.20.5 {
	@ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
	//?} else
	/*@ModifyReturnValue(method = "getDimensions", at = @At("RETURN"))*/
	private EntityDimensions horsingaround$riddenWidth(final EntityDimensions dimensions) {
		if ((Object) this instanceof RideStateHolder holder && (holder.horsingaround$ride().narrow || holder.horsingaround$ride().keptNarrow)) {
			//? if >=1.20.5 {
			return new EntityDimensions(dimensions.width() * RideTuning.RIDDEN_WIDTH_SCALE, dimensions.height(), dimensions.eyeHeight(), dimensions.attachments(), dimensions.fixed());
			//?} else
			/*return new EntityDimensions(dimensions.width * RideTuning.RIDDEN_WIDTH_SCALE, dimensions.height, dimensions.fixed);*/
		}
		return dimensions;
	}

	/**
	 * Leg animation speed tracks the gait instead of vanilla's distance x 4, which maxes out at a slow trot. Keeps
	 * cadence believable and lets Fresh Animations pick the matching walk, trot or gallop cycle. In the air (a jump or a
	 * bigger fall) the stride stops: the legs take the jump's shape instead (see the equine model mixin).
	 */
	@ModifyArg(
		method = "updateWalkAnimation",
		//? if >=1.21.2 {
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/WalkAnimationState;update(FFF)V"),
		//?} else
		/*at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/WalkAnimationState;update(FF)V"),*/
		index = 0
	)
	private float horsingaround$gaitStride(final float targetSpeed, @Local(argsOnly = true) final float distance) {
		if (!((Object) this instanceof RideStateHolder holder) || !holder.horsingaround$playerRidden()) {
			if ((Object) this instanceof RideStateHolder) {
				((Cadenced) ((LivingEntity) (Object) this).walkAnimation).horsingaround$setCadence(1.0F);
			}
			return targetSpeed;
		}
		final LivingEntity horse = (LivingEntity) (Object) this;
		// (Collecting for a step, the stride quickens; see WalkAnimationStateMixin.)
		((Cadenced) horse.walkAnimation).horsingaround$setCadence(holder.horsingaround$ride().cadence);
		if (holder.horsingaround$ride().inAir && !horse.onGround()) {
			return 0.0F;
		}
		return Math.min(distance / RideTuning.topSpeed(horse) * RideTuning.ANIMATION_SPEED_FACTOR, 1.0F);
	}

	@ModifyArg(
		method = "updateWalkAnimation",
		//? if >=1.21.2 {
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/WalkAnimationState;update(FFF)V"),
		//?} else
		/*at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/WalkAnimationState;update(FF)V"),*/
		index = 1
	)
	private float horsingaround$airStride(final float factor) {
		return (Object) this instanceof RideStateHolder holder && holder.horsingaround$ride().inAir && holder.horsingaround$playerRidden()
			&& !((LivingEntity) (Object) this).onGround() ? RideTuning.AIR_STRIDE_STOP : factor;
	}

	/**
	 * A player-ridden horse that floats handles water itself (ride controller): it keeps its footing while wading and
	 * floats when swimming, instead of vanilla's water physics, which stop it dead in any depth. A skeleton horse keeps
	 * vanilla's and walks along the bottom.
	 */
	//? if >=1.21.11 {
	@ModifyReturnValue(method = "shouldTravelInFluid", at = @At("RETURN"))
	//?} else
	/*@ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isAffectedByFluids()Z"))*/
	private boolean horsingaround$ownWaterPhysics(final boolean vanilla) {
		if (!vanilla || !((Object) this instanceof RideStateHolder holder) || !holder.horsingaround$playerRidden()) {
			return vanilla;
		}
		final LivingEntity horse = (LivingEntity) (Object) this;
		return !(horse.isInWater() && !horse.isInLava() && Mounts.floats(horse));
	}

	/** Vanilla air control is half of what is needed to hold ground speed, so every jump bled momentum. */
	@ModifyReturnValue(method = "getFlyingSpeed", at = @At("RETURN"))
	private float horsingaround$airControl(final float vanilla) {
		return (Object) this instanceof RideStateHolder holder && holder.horsingaround$playerRidden()
			? ((LivingEntity) (Object) this).getSpeed() * RideTuning.AIR_CONTROL
			: vanilla;
	}

	/**
	 * Ridden, a full block is a step even from a path, farmland or mud, or onto snow; in the air, a hoof put down gets up
	 * only a little; jumping a hurdle, nothing is a step (taking off right at a fence, a step up would put the horse on
	 * top of it).
	 */
	@ModifyReturnValue(method = "maxUpStep", at = @At("RETURN"))
	private float horsingaround$riddenStep(final float step) {
		if (!((Object) this instanceof RideStateHolder holder) || !holder.horsingaround$ride().narrow) {
			return step;
		}
		final RideState s = holder.horsingaround$ride();
		return s.hurdleForward > 0.0F ? 0.0F : s.airFooting ? RideTuning.AIR_STEP : Math.max(step, RideTuning.RIDDEN_STEP_HEIGHT);
	}
}
