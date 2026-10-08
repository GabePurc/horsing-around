package dev.horsingaround.client.cosmetic;

/** Implemented on the player render state by mixin: the hat colour to draw, or {@code HatPayload.NONE}. */
public interface HatState {
	int horsingaround$hatColor();

	void horsingaround$setHatColor(int color);
}
