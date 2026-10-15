package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.*;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * A ridden horse moving faster than a walk tramples small creatures it runs through: harmless at a walk, building to
 * about four hearts at a full gallop, with a knock aside. Never players or the rider's own pets. Server side.
 */
public final class Trample {
	/** Ticks the ground speed is measured over (a power of two). */
	static final int WINDOW = 4;

	private Trample() {
	}

	public static void tick(final AbstractHorse horse, final RideState s, final ServerLevel level) {
		if (!TRAMPLE || !(horse.getControllingPassenger() instanceof Player rider)) {
			s.trampleSamples = 0;
			return;
		}
		// The rider's client moves the horse, and its moves reach the server unevenly (two in one tick, none the next),
		// so measure ground covered over the last few ticks rather than the last one.
		final int slot = s.trampleTick++ & (WINDOW - 1);
		final int span = s.trampleSamples;
		final double x = horse.getX();
		final double z = horse.getZ();
		final int last = (slot - 1) & (WINDOW - 1);
		final double dx = x - s.trampleX[last];
		final double dz = z - s.trampleZ[last];
		final double spanX = x - s.trampleX[(slot - span) & (WINDOW - 1)];
		final double spanZ = z - s.trampleZ[(slot - span) & (WINDOW - 1)];
		s.trampleX[slot] = x;
		s.trampleZ[slot] = z;
		if (span == 0 || dx * dx + dz * dz > 4.0) {
			// Just mounted, or teleported: start measuring from here.
			s.trampleSamples = 1;
			return;
		}
		if (span < WINDOW) {
			s.trampleSamples = span + 1;
		}
		final float speed = (float) (Math.sqrt(spanX * spanX + spanZ * spanZ) / span);
		final float fraction = speed / topSpeed(horse);
		if (fraction <= TRAMPLE_MIN_SPEED) {
			return;
		}
		final float strength = Math.min((fraction - TRAMPLE_MIN_SPEED) / (GAIT_SPEED[GALLOP] - TRAMPLE_MIN_SPEED), 1.0F);
		final float damage = TRAMPLE_DAMAGE_MAX * strength;
		if (damage < 0.5F) {
			return;
		}
		final AABB box = horse.getBoundingBox().expandTowards(dx, 0.0, dz).inflate(0.1, 0.0, 0.1);
		trampler = rider;
		final List<Entity> victims = level.getEntities(horse, box, TRAMPLEABLE);
		trampler = null;
		if (victims.isEmpty()) {
			return;
		}
		final DamageSource source = level.damageSources().mobAttack(horse);
		for (final Entity entity : victims) {
			final LivingEntity victim = (LivingEntity) entity;
			//? if >=1.21.2 {
			if (victim.hurtServer(level, source, damage)) {
			//?} else
			/*if (victim.hurt(source, damage)) {*/
				//? if >=26.2 {
				victim.knockback(TRAMPLE_KNOCKBACK * strength, horse.getX() - victim.getX(), horse.getZ() - victim.getZ(), source, damage);
				//?} else
				/*victim.knockback(TRAMPLE_KNOCKBACK * strength, horse.getX() - victim.getX(), horse.getZ() - victim.getZ());*/
			}
		}
	}

	/** The rider of the horse being looked at (server thread only), so the filter needn't be a new lambda every tick. */
	private static @Nullable Player trampler;
	private static final Predicate<Entity> TRAMPLEABLE = entity -> isTrampleable(entity, trampler);

	private static boolean isTrampleable(final Entity entity, final @Nullable Player rider) {
		return entity instanceof LivingEntity living
			&& living.isAlive()
			&& !(entity instanceof Player)
			&& entity.getBbWidth() <= TRAMPLE_MAX_WIDTH
			&& entity.getBbHeight() <= TRAMPLE_MAX_HEIGHT
			&& !entity.isPassengerOfSameVehicle(rider)
			&& !(entity instanceof OwnableEntity pet && pet.getOwner() == rider);
	}
}
