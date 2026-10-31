package dev.horsingaround.client.cosmetic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.horsingaround.HorsingAround;
import dev.horsingaround.Platform;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
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
	private static final Path CACHE = Platform.configDir().resolve("horsingaround-supporters.json");
	/** The list is a few KB; anything over this is not it. */
	private static final int MAX_BYTES = 1 << 20;

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
		HttpClient client = null;
		try {
			// (Follows redirects: GitHub answers with one when the repository is renamed or moved.)
			client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
			final HttpRequest request = HttpRequest.newBuilder(LIST).timeout(Duration.ofSeconds(15))
				.header("User-Agent", "HorsingAround/" + HorsingAround.version()).GET().build();
			final String body;
			final HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream in = response.body()) {
				if (response.statusCode() != 200) {
					LOGGER.info("Couldn't refresh the supporters list (HTTP {}); using the cached one", response.statusCode());
					return;
				}
				final byte[] bytes = in.readNBytes(MAX_BYTES + 1);
				if (bytes.length > MAX_BYTES) {
					LOGGER.warn("The supporters list is over {} bytes; using the cached one", MAX_BYTES);
					return;
				}
				body = new String(bytes, StandardCharsets.UTF_8);
			}
			if (body.equals(cached)) {
				return;
			}
			setListed(parse(body));
			writeCache(body);
			// A list that arrives after joining a server would otherwise leave the hat unannounced until the next change.
			Minecraft.getInstance().execute(Hats::sendOwn);
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
			// (One entry written wrong doesn't cost everyone else their hat.)
			try {
				ids.add(uuid(entry.getAsJsonObject().get("uuid").getAsString()));
			} catch (final RuntimeException e) {
				LOGGER.warn("Skipping a supporters list entry that isn't a player id: {}", entry);
			}
		}
		return Set.copyOf(ids);
	}

	/** A player id, with or without its dashes (Mojang's own lookup gives it without). */
	private static UUID uuid(final String text) {
		final String id = text.trim();
		if (id.length() == 32 && id.indexOf('-') < 0) {
			return new UUID(Long.parseUnsignedLong(id.substring(0, 16), 16), Long.parseUnsignedLong(id.substring(16), 16));
		}
		if (id.length() != 36) {
			throw new IllegalArgumentException(id);
		}
		return UUID.fromString(id);
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
