package dev.horsingaround.net;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side: passes each player's supporter hat on to everyone else on the server, and tells players who join about
 * the hats already being worn. The server doesn't judge who is a supporter; every client checks that against its own
 * copy of the list before drawing a hat. A player's changes are passed on at most every {@link #CHANGE_INTERVAL} ticks
 * (the latest one when the wait is up), so a client can't flood everyone with them.
 */
public final class HatRelay {
	/** Least time between two of a player's hat changes being passed on, ticks. */
	private static final int CHANGE_INTERVAL = 10;

	private static final Map<UUID, Integer> HATS = new HashMap<>();
	/** Server tick from which each player's next change may be passed on. */
	private static final Map<UUID, Integer> NEXT_CHANGE = new HashMap<>();
	/** Players whose latest change came too soon and waits to be passed on. */
	private static final Set<UUID> WAITING = new HashSet<>();

	private HatRelay() {
	}

	public static void receive(final ServerPlayer player, final HatPayload hat) {
		final int color = hat.color() == HatPayload.NONE ? HatPayload.NONE : hat.color() & 0xFFFFFF;
		final UUID id = player.getUUID();
		final Integer previous = color == HatPayload.NONE ? HATS.remove(id) : HATS.put(id, color);
		if (previous == null ? color == HatPayload.NONE : previous == color) {
			return;
		}
		final MinecraftServer server = player.level().getServer();
		final Integer next = NEXT_CHANGE.get(id);
		if (next != null && server.getTickCount() < next) {
			WAITING.add(id);
		} else {
			passOn(server, id);
		}
	}

	/** End of every server tick: pass on the changes that had to wait, once their wait is up. */
	public static void tick(final MinecraftServer server) {
		if (WAITING.isEmpty()) {
			return;
		}
		final int now = server.getTickCount();
		for (final Iterator<UUID> waiting = WAITING.iterator(); waiting.hasNext(); ) {
			final UUID id = waiting.next();
			if (now >= NEXT_CHANGE.get(id)) {
				waiting.remove();
				passOn(server, id);
			}
		}
	}

	public static void joined(final ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, PlayerHatPayload.TYPE)) {
			HATS.forEach((id, color) -> ServerPlayNetworking.send(player, new PlayerHatPayload(id, color)));
		}
	}

	public static void left(final ServerPlayer player) {
		final UUID id = player.getUUID();
		final boolean worn = HATS.remove(id) != null;
		// (A change still waiting may have been taking the hat off, after everyone was told it was on.)
		final boolean waiting = WAITING.remove(id);
		NEXT_CHANGE.remove(id);
		if (worn || waiting) {
			broadcast(player.level().getServer(), new PlayerHatPayload(id, HatPayload.NONE));
		}
	}

	public static void clear() {
		HATS.clear();
		NEXT_CHANGE.clear();
		WAITING.clear();
	}

	private static void passOn(final MinecraftServer server, final UUID id) {
		NEXT_CHANGE.put(id, server.getTickCount() + CHANGE_INTERVAL);
		broadcast(server, new PlayerHatPayload(id, HATS.getOrDefault(id, HatPayload.NONE)));
	}

	private static void broadcast(final MinecraftServer server, final PlayerHatPayload payload) {
		for (final ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(other, PlayerHatPayload.TYPE)) {
				ServerPlayNetworking.send(other, payload);
			}
		}
	}
}
