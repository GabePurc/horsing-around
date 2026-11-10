package dev.horsingaround.client;

import dev.horsingaround.RiderBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;

/** Client side of {@link RiderBridge}: reads the local player's keys and reports jumps to the server. */
final class RiderKeys implements RiderBridge {
	/** Options do not exist yet when the client entrypoint runs, so resolve them per call. */
	private final Minecraft minecraft;
	/** Client ticks so far, and the last one the ride read the presses on. */
	private int tick;
	private int readTick = Integer.MIN_VALUE;
	private static final int FORWARD = 0;
	private static final int SPUR = 1;
	private static final int REIN = 2;
	private static final int JUMP = 3;
	/** Whether each key was down at the last read. */
	private final boolean[] wasDown = new boolean[4];

	RiderKeys(final Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	//? if >=1.21.2 {
	@Override
	public Input input(final Player rider) {
		return rider instanceof LocalPlayer local ? local.input.keyPresses : Input.EMPTY;
	}
	//?} else {
	/*// Before 1.21.2 the keys are fields of the player's input object, kept for as long as the player is; a view of them is
	// made once for it.
	private LocalInput view;

	@Override
	public Input input(final Player rider) {
		if (!(rider instanceof LocalPlayer local)) {
			return Input.EMPTY;
		}
		LocalInput input = this.view;
		if (input == null || input.source != local.input) {
			input = this.view = new LocalInput(local.input);
		}
		return input;
	}

	private static final class LocalInput implements Input {
		private final net.minecraft.client.player.Input source;

		private LocalInput(final net.minecraft.client.player.Input source) {
			this.source = source;
		}

		@Override
		public boolean forward() {
			return this.source.up;
		}

		@Override
		public boolean backward() {
			return this.source.down;
		}

		@Override
		public boolean left() {
			return this.source.left;
		}

		@Override
		public boolean right() {
			return this.source.right;
		}
	}
	*///?}

	@Override
	public int forwardPresses() {
		return this.presses(FORWARD, this.minecraft.options.keyUp);
	}

	@Override
	public int spurPresses() {
		final Options options = this.minecraft.options;
		if (options.toggleSprint().get()) {
			// (Toggle Sprint flips the key's held state on each press, so it says nothing of the key: every click counts.)
			this.dropStale();
			this.forget(SPUR, options.keySprint);
			return drain(options.keySprint);
		}
		return this.presses(SPUR, options.keySprint);
	}

	@Override
	public int reinPresses() {
		return this.presses(REIN, this.minecraft.options.keyDown);
	}

	@Override
	public int jumpPresses() {
		return this.presses(JUMP, this.minecraft.options.keyJump);
	}

	/**
	 * Presses of a key since the last read. Vanilla also clicks for the OS key repeat while a key is held, so of a key
	 * that was held or went down, only as many clicks as times it went down are presses (taps shorter than a tick too).
	 */
	private int presses(final int which, final KeyMapping key) {
		this.dropStale();
		final int clicks = drain(key);
		final int downs = ((KeyPresses) key).horsingaround$takeDowns();
		final boolean held = this.wasDown[which];
		this.wasDown[which] = key.isDown();
		// (Clicks of a key that never went down are something else's presses for it, another mod's say, not a repeat.)
		return !held && downs == 0 ? clicks : Math.min(clicks, downs);
	}

	@Override
	public void onJump(final Player rider, final float power) {
		if (rider instanceof LocalPlayer local) {
			// The server plays the jump sound for everyone (including us) on this vanilla packet.
			local.connection.send(new ServerboundPlayerCommandPacket(
				local, ServerboundPlayerCommandPacket.Action.START_RIDING_JUMP, Math.max(1, Math.round(power * 100.0F))
			));
		}
	}

	/** End of every client tick. */
	void endTick() {
		this.tick++;
	}

	/**
	 * Vanilla never consumes these clicks, so on the first ride tick after a break (a new mount, say) drop the ones made
	 * before it, or they would fire then. Left alone while not riding, for anything else that reads them.
	 */
	private void dropStale() {
		if (this.readTick < this.tick - 1) {
			final Options options = this.minecraft.options;
			drain(options.keyUp);
			this.forget(FORWARD, options.keyUp);
			drain(options.keySprint);
			this.forget(SPUR, options.keySprint);
			drain(options.keyDown);
			this.forget(REIN, options.keyDown);
			drain(options.keyJump);
			this.forget(JUMP, options.keyJump);
		}
		this.readTick = this.tick;
	}

	/** Starts a key's count of times it went down afresh, from its state now. */
	private void forget(final int which, final KeyMapping key) {
		((KeyPresses) key).horsingaround$takeDowns();
		this.wasDown[which] = key.isDown();
	}

	private static int drain(final KeyMapping key) {
		int presses = 0;
		while (key.consumeClick()) {
			presses++;
		}
		return presses;
	}
}
