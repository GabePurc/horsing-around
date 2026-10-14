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
	public int spurPresses() {
		this.dropStale();
		return drain(this.minecraft.options.keySprint);
	}

	@Override
	public int reinPresses() {
		this.dropStale();
		return drain(this.minecraft.options.keyDown);
	}

	@Override
	public int jumpPresses() {
		this.dropStale();
		return drain(this.minecraft.options.keyJump);
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
			drain(options.keySprint);
			drain(options.keyDown);
			drain(options.keyJump);
		}
		this.readTick = this.tick;
	}

	private static int drain(final KeyMapping key) {
		int presses = 0;
		while (key.consumeClick()) {
			presses++;
		}
		return presses;
	}
}
