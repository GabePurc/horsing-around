package dev.horsingaround.net;

import dev.horsingaround.ride.Mounts;

/**
 * Client side: whether the server this client is connected to runs this mod, and the rules it rides by. Riding is
 * simulated on the rider's client and checked by the server, so anything that moves the horse differently from vanilla
 * (leaves, the narrower box, steps in the air) only switches on when the server agrees; on a server without the mod,
 * or with a version that rides by different rules, horses ride as in vanilla.
 */
public final class ServerRules {
	/** Changes whenever what the client and server must agree on changes. */
	public static final int PROTOCOL = 1;

	public static boolean present;
	public static boolean rideThroughLeaves;
	/** The server's mod version when it runs a different protocol (for the notice), else null. */
	public static String mismatchedVersion;

	private ServerRules() {
	}

	public static void accept(final RulesPayload rules) {
		present = rules.protocol() == PROTOCOL;
		mismatchedVersion = present ? null : rules.version();
		rideThroughLeaves = rules.rideThroughLeaves();
		Mounts.invalidate();
	}

	public static void reset() {
		present = false;
		rideThroughLeaves = false;
		mismatchedVersion = null;
		Mounts.invalidate();
	}
}
