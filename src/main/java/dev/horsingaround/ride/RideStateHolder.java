package dev.horsingaround.ride;

/** Implemented on {@code AbstractHorse} by mixin. */
public interface RideStateHolder {
	RideState horsingaround$ride();

	/** False for camels and llamas, which keep their vanilla controls. */
	boolean horsingaround$managed();
}
