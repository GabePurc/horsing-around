package dev.horsingaround.client;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.net.ServerRules;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;

/**
 * Mounting a horse on a server without the mod (or with a version that rides by different rules), the rider is told
 * once per connection why it rides as in vanilla.
 */
final class ServerNotice {
	private static boolean shown;
	private static Entity lastVehicle;

	private ServerNotice() {
	}

	static void reset() {
		shown = false;
		lastVehicle = null;
	}

	static void tick(final Minecraft minecraft) {
		final Entity vehicle = minecraft.player == null ? null : minecraft.player.getVehicle();
		if (vehicle == lastVehicle) {
			return;
		}
		lastVehicle = vehicle;
		if (shown || ServerRules.present || !(vehicle instanceof AbstractHorse) || vehicle instanceof Camel || vehicle instanceof Llama) {
			return;
		}
		shown = true;
		final String theirs = ServerRules.mismatchedVersion;
		minecraft.gui.hud.setOverlayMessage(theirs == null
			? Component.translatable("message.horsingaround.server_missing")
			: Component.translatable("message.horsingaround.server_mismatch", theirs, HorsingAround.version()), false);
	}
}
