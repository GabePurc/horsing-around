package dev.horsingaround;

import dev.horsingaround.net.HatRelay;
import dev.horsingaround.net.RulesPayload;
import dev.horsingaround.net.ServerRules;
import dev.horsingaround.net.StylePayload;
import dev.horsingaround.ride.HorseConfig;
import dev.horsingaround.ride.IcyCompat;
import dev.horsingaround.ride.Mounts;
import dev.horsingaround.ride.RideTuning;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The mod's start-up and server-side hooks, called from the loader's entry point (fabric/, neoforge/). */
public final class HorsingAround {
	public static final String MOD_ID = "horsingaround";

	public static RiderBridge bridge = RiderBridge.NONE;

	private static String version = "";

	private HorsingAround() {
	}

	public static void init() {
		version = Platform.modVersion();
		HorseConfig.load();
	}

	/** A player joined: a client that runs the mod hears the riding style and the rules, and everyone's hats. */
	public static void joined(final ServerPlayer player) {
		// (The style first: the rules switch the ride on, and by then it has to be the right one.)
		sendStyle(player);
		if (Platform.canSend(player, RulesPayload.TYPE)) {
			Platform.send(player, rules());
		}
		HatRelay.joined(player);
	}

	public static String version() {
		return version;
	}

	private static RulesPayload rules() {
		return new RulesPayload(ServerRules.PROTOCOL, version, RideTuning.RIDE_THROUGH_LEAVES);
	}

	/** Tells everyone connected the rules again (for tests: a client that lost them gets them back). Server thread. */
	public static void sendRules(final MinecraftServer server) {
		for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (Platform.canSend(player, RulesPayload.TYPE)) {
				Platform.send(player, rules());
			}
		}
	}

	/**
	 * The host changed the riding style (singleplayer or LAN; a dedicated server reads it at start): horses ride the new
	 * way from now, and everyone connected hears it. Server thread.
	 */
	public static void styleChanged(final MinecraftServer server) {
		Mounts.invalidate();
		for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendStyle(player);
		}
	}

	private static void sendStyle(final ServerPlayer player) {
		if (IcyCompat.LOADED && Platform.canSend(player, StylePayload.TYPE)) {
			Platform.send(player, new StylePayload(HorseConfig.get().ridingStyle == HorseConfig.RidingStyle.ICYS));
		}
	}
}
