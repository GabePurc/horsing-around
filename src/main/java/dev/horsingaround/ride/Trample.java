package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.*;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * A ridden horse moving faster than a walk tramples small creatures it runs through: harmless at a walk, building to
 * about four hearts at a full gallop, with a knock aside. Never players or the rider's own pets. Server side.
 */
public final class Trample {
	private Trample() {
	}

	public static void tick(final AbstractHorse horse, final RideState s, final ServerLevel level) {
		// The rider's client moves the horse between server ticks, so measure ground covered per tick here.
		final double dx = horse.getX() - s.trampleLastX;
		final double dz = horse.getZ() - s.trampleLastZ;
		s.trampleLastX = horse.getX();
		s.trampleLastZ = horse.getZ();
		final double moved = Math.sqrt(dx * dx + dz * dz);
		s.trampleSpeed = moved > 2.0 ? 0.0F : s.trampleSpeed * 0.5F + (float) moved * 0.5F;
		if (!TRAMPLE || !(horse.getControllingPassenger() instanceof Player rider)) {
			return;
		}
		final float fraction = s.trampleSpeed / (float) (horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * TERMINAL_VELOCITY_FACTOR);
		if (fraction <= TRAMPLE_MIN_SPEED) {
			return;
		}
		final float strength = Math.min((fraction - TRAMPLE_MIN_SPEED) / (GAIT_SPEED[GALLOP] - TRAMPLE_MIN_SPEED), 1.0F);
		final float damage = TRAMPLE_DAMAGE_MAX * strength;
		if (damage < 0.5F) {
			return;
		}
		final AABB box = horse.getBoundingBox().expandTowards(dx, 0.0, dz).inflate(0.1, 0.0, 0.1);
		final List<Entity> victims = level.getEntities(horse, box, e -> isTrampleable(e, rider));
		for (final Entity entity : victims) {
			final LivingEntity victim = (LivingEntity) entity;
			final DamageSource source = level.damageSources().mobAttack(horse);
			if (victim.hurtServer(level, source, damage)) {
				victim.knockback(TRAMPLE_KNOCKBACK * strength, horse.getX() - victim.getX(), horse.getZ() - victim.getZ(), source, damage);
			}
		}
	}

	private static boolean isTrampleable(final Entity entity, final Player rider) {
		return entity instanceof LivingEntity living
			&& living.isAlive()
			&& !(entity instanceof Player)
			&& entity.getBbWidth() <= TRAMPLE_MAX_WIDTH
			&& entity.getBbHeight() <= TRAMPLE_MAX_HEIGHT
			&& !entity.isPassengerOfSameVehicle(rider)
			&& !(entity instanceof OwnableEntity pet && pet.getOwner() == rider);
	}
}
