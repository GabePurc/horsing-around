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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

	/** Everyone who gets a hat: {@link #listed} plus {@link #testing}. Replaced whole on a change; read from the render thread. */
	private static volatile Set<UUID> supporters = Set.of();
	/** The downloaded (or cached) list. */
	private static Set<UUID> listed = Set.of();
	/** Players the tests made supporters; kept apart so a download arriving mid-test can't drop them. */
	private static Set<UUID> testing = Set.of();

	private Supporters() {
	}

	public static boolean isSupporter(final UUID player) {
		return supporters.contains(player);
	}

	/** Loads the cached list, then fetches the current one in the background. */
	public static void load() {
		String cached = null;
		try {
			if (Files.exists(CACHE)) {
				final String text = Files.readString(CACHE);
				setListed(parse(text));
				// Only a cache that parsed lets an identical download be skipped.
				cached = text;
			}
		} catch (final IOException | RuntimeException e) {
			LOGGER.warn("Could not read the cached supporters list", e);
		}
		final String known = cached;
		final Thread refresh = new Thread(() -> refresh(known), "Horsing Around supporters");
		refresh.setDaemon(true);
		refresh.start();
	}

	/** Downloads the current list (background thread); {@code cached} is the cache's text, or null. */
	private static void refresh(final String cached) {
		final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		try {
			final HttpRequest request = HttpRequest.newBuilder(LIST).timeout(Duration.ofSeconds(15))
				.header("User-Agent", "HorsingAround/" + HorsingAround.version()).GET().build();
			final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				LOGGER.info("Couldn't refresh the supporters list (HTTP {}); using the cached one", response.statusCode());
				return;
			}
			final String body = response.body();
			if (body.equals(cached)) {
				return;
			}
			setListed(parse(body));
			writeCache(body);
		} catch (final IOException e) {
			LOGGER.info("Couldn't refresh the supporters list ({}); using the cached one", e.toString());
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (final RuntimeException e) {
			LOGGER.warn("Couldn't read the supporters list", e);
		} finally {
			// Stops the client's own thread now instead of whenever it is collected (Java 21 and up; Java 17 has no close).
			if (client instanceof AutoCloseable closeable) {
				try {
					closeable.close();
				} catch (final Exception ignored) {
				}
			}
		}
	}

	/** Written beside the cache and moved over it, so quitting mid-write can't leave half a list. */
	private static void writeCache(final String body) {
		final Path partial = CACHE.resolveSibling(CACHE.getFileName() + ".tmp");
		try {
			Files.writeString(partial, body);
			try {
				Files.move(partial, CACHE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (final AtomicMoveNotSupportedException e) {
				Files.move(partial, CACHE, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (final IOException e) {
			LOGGER.warn("Couldn't cache the supporters list", e);
			try {
				Files.deleteIfExists(partial);
			} catch (final IOException ignored) {
			}
		}
	}

	private static synchronized void setListed(final Set<UUID> ids) {
		listed = ids;
		publish();
	}

	/** Called with the class lock held. */
	private static void publish() {
		if (testing.isEmpty()) {
			supporters = listed;
			return;
		}
		final Set<UUID> all = new HashSet<>(listed);
		all.addAll(testing);
		supporters = Set.copyOf(all);
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
	public static synchronized void addForTesting(final UUID player) {
		final Set<UUID> ids = new HashSet<>(testing);
		ids.add(player);
		testing = Set.copyOf(ids);
		publish();
	}

	/** For tests. */
	public static synchronized void removeForTesting(final UUID player) {
		final Set<UUID> ids = new HashSet<>(testing);
		ids.remove(player);
		testing = Set.copyOf(ids);
		publish();
	}
}
