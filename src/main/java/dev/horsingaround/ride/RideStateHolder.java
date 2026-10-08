package dev.horsingaround.ride;

import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.Nullable;

/** Implemented on {@code AbstractHorse} by mixin. */
public interface RideStateHolder {
	RideState horsingaround$ride();

	/** Whether this horse gets the new controls (see {@link Mounts}); false for camels and llamas. */
	boolean horsingaround$managed();

	/** Managed and ridden by a player. */
	boolean horsingaround$playerRidden();

	/** The horse's own angry snort (horse, donkey, mule...), or null. */
	@Nullable SoundEvent horsingaround$angrySound();
}
