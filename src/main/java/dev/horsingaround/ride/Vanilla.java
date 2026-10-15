package dev.horsingaround.ride;

import net.minecraft.world.entity.LivingEntity;
//? if >=1.20.5 {
import net.minecraft.world.entity.ai.attributes.Attributes;
//?} else {
/*import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
*///?}
//? if <1.20.2 {
/*import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
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

	/**
	 * The longest fall (blocks) that costs the entity at most {@code hurt} health points, less a margin for error. From
	 * 1.21.5 vanilla rounds fall damage down; before, it rounded it up, so any fall past the safe distance hurts.
	 */
	static double longestFall(final LivingEntity entity, final int hurt) {
		final double multiplier = Math.max(fallDamageMultiplier(entity), 1.0E-3);
		//? if >=1.21.5 {
		return safeFall(entity) + (hurt + 1) / multiplier - 0.05;
		//?} else
		/*return safeFall(entity) + hurt / multiplier - 0.05;*/
	}

	/**
	 * Health points a fall of {@code fall} blocks costs the horse's rider. From 1.21.5 the rider takes the fall themselves
	 * (from their own safe distance, rounded down); before, the horse handed its own damage on to its riders.
	 */
	static double riderFallHurt(final LivingEntity horse, final LivingEntity rider, final double fall) {
		//? if >=1.21.5 {
		return Math.floor(Math.max(fall - safeFall(rider), 0.0) * fallDamageMultiplier(rider));
		//?} else
		/*return Math.ceil(Math.max(fall - safeFall(horse), 0.0) * fallDamageMultiplier(horse));*/
	}

	//? if <1.20.2 {
	/*// (Before 1.20.2 only the version that looks at entities too exists; the blocks alone are what vanilla's later one looks at.)
	static boolean noBlockCollision(final CollisionGetter level, final Entity entity, final AABB box) {
		for (final VoxelShape shape : level.getBlockCollisions(entity, box)) {
			if (!shape.isEmpty()) {
				return false;
			}
		}
		return true;
	}
	*///?}
}
