package dev.horsingaround.client.cosmetic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.horsingaround.HorsingAround;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Who supports the project: Minecraft player UUIDs in {@code supporters.json} in the GitHub repository. Downloaded in
 * the background when the game starts (nothing is sent: it's a plain download) and kept in the config folder, so it
 * still works offline. Supporters get the cowboy hat.
 */
public final class Supporters {
	private static final Logger LOGGER = LoggerFactory.getLogger(HorsingAround.MOD_ID);
	private static final URI LIST = URI.create("https://raw.githubusercontent.com/GabePurc/horsing-around/main/supporters.json");
	private static final Path CACHE = FabricLoader.getInstance().getConfigDir().resolve("horsingaround-supporters.json");

	/** Replaced whole when a new list arrives; read from the render thread. */
	private static volatile Set<UUID> supporters = Set.of();

	private Supporters() {
	}

	public static boolean isSupporter(final UUID player) {
		return supporters.contains(player);
	}

	/** Loads the cached list, then fetches the current one in the background. */
	public static void load() {
		try {
			if (Files.exists(CACHE)) {
				supporters = parse(Files.readString(CACHE));
			}
		} catch (final IOException | RuntimeException e) {
			LOGGER.warn("Could not read the cached supporters list", e);
		}
		final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		final HttpRequest request = HttpRequest.newBuilder(LIST).timeout(Duration.ofSeconds(15))
			.header("User-Agent", "HorsingAround/" + HorsingAround.version()).GET().build();
		client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
			if (error != null || response.statusCode() != 200) {
				LOGGER.info("Couldn't refresh the supporters list ({}); using the cached one", error != null ? error.toString() : "HTTP " + response.statusCode());
				return;
			}
			try {
				supporters = parse(response.body());
				Files.writeString(CACHE, response.body());
			} catch (final IOException | RuntimeException e) {
				LOGGER.warn("Couldn't read the supporters list", e);
			}
		});
	}

	private static Set<UUID> parse(final String json) {
		final Set<UUID> ids = new HashSet<>();
		final JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		for (final JsonElement entry : root.getAsJsonArray("supporters")) {
			ids.add(UUID.fromString(entry.getAsJsonObject().get("uuid").getAsString()));
		}
		return Set.copyOf(ids);
	}

	/** For tests: make a player a supporter for this session. */
	public static void addForTesting(final UUID player) {
		final Set<UUID> ids = new HashSet<>(supporters);
		ids.add(player);
		supporters = Set.copyOf(ids);
	}

	/** For tests. */
	public static void removeForTesting(final UUID player) {
		final Set<UUID> ids = new HashSet<>(supporters);
		ids.remove(player);
		supporters = Set.copyOf(ids);
	}
}
