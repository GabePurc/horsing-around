package dev.horsingaround.ride;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/** Implemented on {@code AbstractHorse} by mixin. */
public interface RideStateHolder {
	/** The ride of {@code entity} if it is a horse this mod manages (a player's vehicle, say), else null. */
	static @Nullable RideState managedRide(final @Nullable Entity entity) {
		return entity instanceof RideStateHolder holder && holder.horsingaround$managed() ? holder.horsingaround$ride() : null;
	}

	RideState horsingaround$ride();

	/** Whether this horse gets the new controls (see {@link Mounts}); false for camels and llamas. */
	boolean horsingaround$managed();

	/** Managed and ridden by a player. */
	boolean horsingaround$playerRidden();

	/** The horse's own angry snort (horse, donkey, mule...), or null. */
	@Nullable SoundEvent horsingaround$angrySound();

	//? if <1.21 {
	/*// (The block under the horse that sets its footing; protected in the entity class until 1.21.)
	net.minecraft.core.BlockPos horsingaround$blockBelow();
	*///?}
}
