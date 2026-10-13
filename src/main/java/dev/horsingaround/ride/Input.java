//? if <1.21.2 {
/*package dev.horsingaround.ride;

// Before 1.21.2 the keys a player holds are only in a client class (net.minecraft.client.player.Input), which common code
// can't name; this carries the four the ride reads (the client entrypoint hands over a view of the real thing).
public interface Input {
	Input EMPTY = new Input() {
	};

	default boolean forward() {
		return false;
	}

	default boolean backward() {
		return false;
	}

	default boolean left() {
		return false;
	}

	default boolean right() {
		return false;
	}
}
*///?}
