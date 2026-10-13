package dev.horsingaround.ride;

import net.minecraft.world.entity.LivingEntity;
//? if >=1.20.5 {
import net.minecraft.world.entity.ai.attributes.Attributes;
//?} else {
/*import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
*///?}

/**
 * Numbers vanilla keeps as attributes from 1.20.5 on (gravity, how far a fall is safe, how much of it hurts) and as
 * fixed values before; one place, so the ride's physics reads the same on every version.
 */
final class Vanilla {
	private Vanilla() {
	}

	/** Downward pull per tick (the extra gravity of the ride is the ride's own). */
	static double gravity(final LivingEntity entity) {
		//? if >=1.20.5 {
		return entity.getGravity();
		//?} else
		/*return entity.isNoGravity() ? 0.0 : LivingEntity.DEFAULT_BASE_GRAVITY;*/
	}

	/** The fall (blocks) that does no damage. */
	static double safeFall(final LivingEntity entity) {
		//? if >=1.20.5 {
		return entity.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		//?} else {
		/*if (entity instanceof AbstractHorse) {
			return 6.0;
		}
		final MobEffectInstance boost = entity.getEffect(MobEffects.JUMP);
		return boost == null ? 3.0 : 4.0 + boost.getAmplifier();
		*///?}
	}

	/** What is left of a fall past the safe distance, as damage per block. */
	static double fallDamageMultiplier(final LivingEntity entity) {
		//? if >=1.20.5 {
		return entity.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
		//?} else
		/*return entity instanceof AbstractHorse ? 0.5 : 1.0;*/
	}
}
