package dev.horsingaround;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Dev check for each Minecraft version this mod is built for ({@code ./gradlew :<version>:auditMixins}, the built jar in a
 * production game and on a dedicated server): once the game is up, every class this mod's mixins patch is loaded, so a
 * mixin that no longer fits fails here (the run adds {@code -Dmixin.debug.countInjections}, so an injection that finds
 * nothing to inject into fails too), then the game quits. Off unless {@code -Dhorsingaround.audit=true}.
 */
public final class MixinAudit {
	public static final boolean ENABLED = Boolean.getBoolean("horsingaround.audit");
	/** The line the Gradle task looks for in the log. */
	public static final String PASSED = "Horsing Around mixin audit passed";

	private static final Logger LOGGER = LoggerFactory.getLogger("horsingaround");

	private MixinAudit() {
	}

	public static void run(final String side) {
		MixinEnvironment.getCurrentEnvironment().audit();
		LOGGER.info("{} ({})", PASSED, side);
	}

	public static void server(final MinecraftServer server) {
		if (server.isDedicatedServer()) {
			run("server");
			server.halt(false);
		}
	}
}
