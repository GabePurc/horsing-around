package dev.horsingaround.ride;

import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.Nullable;

/** Implemented on {@code AbstractHorse} by mixin. */
public interface RideStateHolder {
	RideState horsingaround$ride();

	/** False for camels and llamas, which keep their vanilla controls. */
	boolean horsingaround$managed();

	/** The horse's own angry snort (horse, donkey, mule...), or null. */
	@Nullable SoundEvent horsingaround$angrySound();
}
