package dev.horsingaround.client;

/** A key mapping, counting the times it went down from up (KeyMappingMixin): what tells a press from the OS key repeat. */
public interface KeyPresses {
	/** How many times the key went down since the last call. */
	int horsingaround$takeDowns();
}
