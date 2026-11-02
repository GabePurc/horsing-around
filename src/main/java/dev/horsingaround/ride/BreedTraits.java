package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * Icy's Better Horses' speed abilities on this mod's riding (user decision, 2026-11-01). Icy swings the horse's speed
 * mid-ride for them, which this mod's riding can't take (it sets its gaits from that speed; see mixin/icy), so they are
 * done here instead on the ride's own acceleration, gallop and stamina: the Quarter Horse's burst from a standstill, the
 * Thoroughbred's top end, the Arabian's endurance. They grow with Icy's bond tiers and follow Icy's switch for each.
 * Every other horse rides with all of them neutral. Simulated with the rest of the ride, on the rider's client.
 */
public final class BreedTraits {
	static final int NONE = 0;
	static final int QUARTER = 1;
	static final int THOROUGHBRED = 2;
	static final int ARABIAN = 3;
	/** Not worked out yet for this ride state. */
	static final int UNKNOWN = -1;
	/** Ride speed (a share of the horse's own) above which it counts as moving off. */
	private static final float STILL = 0.01F;

	private BreedTraits() {
	}

	/** Once a ride tick, before the gait and stamina are worked out: sets the ride's trait scales for this tick. */
	static void tick(final AbstractHorse horse, final RideState s) {
		if (s.trait == UNKNOWN) {
			s.trait = of(horse);
		}
		if (s.trait == NONE) {
			return;
		}
		final boolean on = IcyCompat.abilityOn(s.trait);
		final int tier = on ? IcyCompat.bondTier(horse) : 0;
		switch (s.trait) {
			case QUARTER -> burst(s, on, tier);
			case THOROUGHBRED -> topEnd(s, on, tier);
			case ARABIAN -> {
				s.drainScale = on ? (tier >= 1 ? ENDURANCE_DRAIN_BONDED : ENDURANCE_DRAIN) : 1.0F;
				s.regenScale = on ? (tier >= 1 ? ENDURANCE_REGEN_BONDED : ENDURANCE_REGEN) : 1.0F;
			}
			default -> {
			}
		}
	}

	private static int of(final AbstractHorse horse) {
		if (!IcyCompat.LOADED) {
			return NONE;
		}
		final Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(horse.getType());
		if (!IcyCompat.MOD_ID.equals(id.getNamespace())) {
			return NONE;
		}
		return switch (id.getPath()) {
			case "quarter_horse" -> QUARTER;
			case "thoroughbred_horse" -> THOROUGHBRED;
			case "arabian_horse" -> ARABIAN;
			default -> NONE;
		};
	}

	private static void burst(final RideState s, final boolean on, final int tier) {
		if (s.burstCooldown > 0) {
			s.burstCooldown--;
		}
		if (s.burstTicks > 0) {
			s.burstTicks--;
		}
		final boolean moving = s.speed > STILL;
		if (!moving) {
			s.standTicks = Math.min(s.standTicks + 1, BURST_STAND_TICKS);
		} else {
			if (on && s.standTicks >= BURST_STAND_TICKS && s.burstCooldown == 0) {
				s.burstTicks = BURST_TICKS;
				s.burstCooldown = BURST_TICKS + (tier >= 2 ? BURST_COOLDOWN_BONDED : BURST_COOLDOWN);
			}
			s.standTicks = 0;
		}
		s.accelScale = s.burstTicks > 0 ? (tier >= 1 ? BURST_ACCEL_BONDED : BURST_ACCEL) : 1.0F;
	}

	private static void topEnd(final RideState s, final boolean on, final int tier) {
		final boolean flatOut = on && s.gait == GALLOP && !s.exhausted && s.speed > GAIT_SPEED[CANTER] && Math.abs(s.pitch) < TOP_END_LEVEL;
		s.topEndTicks = flatOut ? s.topEndTicks + 1 : 0;
		final float target = s.topEndTicks >= TOP_END_TICKS ? 1.0F : 0.0F;
		s.topEnd = Mth.approach(s.topEnd, target, TOP_END_EASE);
		s.gallopBonus = s.topEnd * (tier >= 1 ? TOP_END_BONUS_BONDED : TOP_END_BONUS);
		s.drainScale = 1.0F + s.topEnd * (TOP_END_DRAIN - 1.0F);
	}
}
