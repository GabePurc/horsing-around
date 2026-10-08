package dev.horsingaround.net;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side: passes each player's supporter hat on to everyone else on the server, and tells players who join about
 * the hats already being worn. The server doesn't judge who is a supporter; every client checks that against its own
 * copy of the list before drawing a hat.
 */
public final class HatRelay {
	private static final Map<UUID, Integer> HATS = new HashMap<>();

	private HatRelay() {
	}

	public static void receive(final ServerPlayer player, final HatPayload hat) {
		final int color = hat.color() == HatPayload.NONE ? HatPayload.NONE : hat.color() & 0xFFFFFF;
		final UUID id = player.getUUID();
		final Integer previous = color == HatPayload.NONE ? HATS.remove(id) : HATS.put(id, color);
		if (previous == null ? color != HatPayload.NONE : previous != color) {
			broadcast(player.level().getServer(), new PlayerHatPayload(id, color));
		}
	}

	public static void joined(final ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, PlayerHatPayload.TYPE)) {
			HATS.forEach((id, color) -> ServerPlayNetworking.send(player, new PlayerHatPayload(id, color)));
		}
	}

	public static void left(final ServerPlayer player) {
		if (HATS.remove(player.getUUID()) != null) {
			broadcast(player.level().getServer(), new PlayerHatPayload(player.getUUID(), HatPayload.NONE));
		}
	}

	public static void clear() {
		HATS.clear();
	}

	private static void broadcast(final MinecraftServer server, final PlayerHatPayload payload) {
		for (final ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(other, PlayerHatPayload.TYPE)) {
				ServerPlayNetworking.send(other, payload);
			}
		}
	}
}
