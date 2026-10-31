package dev.horsingaround;

import dev.horsingaround.net.HatRelay;
import dev.horsingaround.net.RulesPayload;
import dev.horsingaround.net.ServerRules;
import dev.horsingaround.ride.HorseConfig;
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

	/** A player joined: a client that runs the mod hears the rules, and everyone's hats. */
	public static void joined(final ServerPlayer player) {
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
}
