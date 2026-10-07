package dev.horsingaround;

import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;

/**
 * Client hooks the common ride code needs. Only the rider's own client simulates a player-ridden horse, so the
 * server never calls these; the client entrypoint installs the real implementation.
 */
public interface RiderBridge {
	RiderBridge NONE = new RiderBridge() {
		@Override
		public Input input(final Player rider) {
			return Input.EMPTY;
		}

		@Override
		public int spurPresses() {
			return 0;
		}

		@Override
		public int reinPresses() {
			return 0;
		}

		@Override
		public int jumpPresses() {
			return 0;
		}

		@Override
		public void onJump(final Player rider, final float power) {
		}
	};

	/** Raw key state of the rider driving the horse. */
	Input input(Player rider);

	/**
	 * Presses of the sprint, back and jump keys since the last call. Counting presses rather than sampling held state
	 * catches taps shorter than a tick and works with toggle-sprint.
	 */
	int spurPresses();

	int reinPresses();

	int jumpPresses();

	/** A ridden jump just fired locally; tell the server so everyone hears it. */
	void onJump(Player rider, float power);
}
