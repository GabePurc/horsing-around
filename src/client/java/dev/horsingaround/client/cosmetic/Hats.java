package dev.horsingaround.client.cosmetic;

import dev.horsingaround.net.HatPayload;
import dev.horsingaround.net.PlayerHatPayload;
import dev.horsingaround.net.ServerRules;
import dev.horsingaround.ride.HorseConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Who wears a cowboy hat, in what colour: your own from your settings, everyone else's as the server passes it on.
 * Only players on the supporters list are drawn with one. Client thread.
 */
public final class Hats {
	private static final Map<UUID, Integer> WORN = new HashMap<>();

	private Hats() {
	}

	/** The hat colour to draw on this player, or {@link HatPayload#NONE}. */
	public static int colorFor(final Player player) {
		if (player instanceof LocalPlayer) {
			return canWear() ? ownHat() : HatPayload.NONE;
		}
		if (!Supporters.isSupporter(player.getUUID())) {
			return HatPayload.NONE;
		}
		final Integer color = WORN.get(player.getUUID());
		return color == null ? HatPayload.NONE : color;
	}

	/** Whether the local player can wear the hat (is on the supporters list). */
	public static boolean canWear() {
		final Minecraft minecraft = Minecraft.getInstance();
		return Supporters.isSupporter(minecraft.getUser().getProfileId())
			|| minecraft.player != null && Supporters.isSupporter(minecraft.player.getUUID());
	}

	private static int ownHat() {
		final HorseConfig c = HorseConfig.get();
		return c.hat ? c.hatColor & 0xFFFFFF : HatPayload.NONE;
	}

	public static void receive(final PlayerHatPayload hat) {
		if (hat.color() == HatPayload.NONE) {
			WORN.remove(hat.player());
		} else {
			WORN.put(hat.player(), hat.color());
		}
	}

	/** Tells the server what we're wearing (on joining and after changing it in the settings). */
	public static void sendOwn() {
		if (ServerRules.present && ClientPlayNetworking.canSend(HatPayload.TYPE)) {
			ClientPlayNetworking.send(new HatPayload(canWear() ? ownHat() : HatPayload.NONE));
		}
	}

	public static void clear() {
		WORN.clear();
	}

	/** For tests: the colour the server last passed on for this player. */
	public static int relayed(final UUID player) {
		final Integer color = WORN.get(player);
		return color == null ? HatPayload.NONE : color;
	}
}
