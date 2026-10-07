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

	RiderKeys(final Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public Input input(final Player rider) {
		return rider instanceof LocalPlayer local ? local.input.keyPresses : Input.EMPTY;
	}

	@Override
	public int spurPresses() {
		return drain(this.minecraft.options.keySprint);
	}

	@Override
	public int reinPresses() {
		return drain(this.minecraft.options.keyDown);
	}

	@Override
	public int jumpPresses() {
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

	/** Vanilla never consumes these clicks, so drop them while not riding or they would fire on the next mount. */
	void discardPresses() {
		final Options options = this.minecraft.options;
		drain(options.keySprint);
		drain(options.keyDown);
		drain(options.keyJump);
	}

	private static int drain(final KeyMapping key) {
		int presses = 0;
		while (key.consumeClick()) {
			presses++;
		}
		return presses;
	}
}
