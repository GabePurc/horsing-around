package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.util.Mth;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Player settings, saved to {@code config/horsingaround.json} and applied to {@link RideTuning} live. Only comfort and
 * personal preference: how riding looks and sounds to this player (camera, view and hand motion, the mod's extra
 * sounds). How horses ride is the same for everyone and isn't a setting. Multipliers are shares of the designed effect
 * (1.0 = as designed); comfort ones only go down from there.
 */
public final class HorseConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("horsingaround");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("horsingaround.json");
	private static final HorseConfig DEFAULTS = new HorseConfig();
	private static HorseConfig instance = new HorseConfig();
	/** Range of the camera height setting, blocks. */
	public static final float CAMERA_HEIGHT_MIN = -0.5F;
	public static final float CAMERA_HEIGHT_MAX = 1.5F;

	/** Where the third-person riding camera sits: over a shoulder, or centred behind the rider. */
	public enum Shoulder {
		RIGHT(1.0F), LEFT(-1.0F), OFF(0.0F);

		/** + right, - left. */
		public final float sign;

		Shoulder(final float sign) {
			this.sign = sign;
		}
	}

	// Camera.
	public boolean thirdPersonOnMount = true;
	public Shoulder shoulder = Shoulder.RIGHT;
	public float cameraDistance = 1.0F;
	/** Riding camera height above the rider's eyes, blocks. */
	public float cameraHeight = CAMERA_HEIGHT_DEFAULT;
	public float speedZoom = 1.0F;
	// Comfort (first person).
	public float viewBob = 1.0F;
	public float handBob = 1.0F;
	// Sound.
	public float horseSounds = 1.0F;
	// Supporter hat (shown only to and for players on the supporters list).
	public boolean hat = true;
	public int hatColor = 0x8B5A2B;

	public static HorseConfig get() {
		return instance;
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				// Settings from older versions that changed how horses ride are ignored.
				final HorseConfig loaded = GSON.fromJson(reader, HorseConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (final IOException | RuntimeException e) {
				LOGGER.warn("Could not read {}, using defaults", FILE, e);
				keepUnreadable();
			}
		}
		apply();
	}

	/** Moves a file that couldn't be read aside, so the next save doesn't overwrite what the player wrote in it. */
	private static void keepUnreadable() {
		final Path kept = FILE.resolveSibling(FILE.getFileName() + ".bad");
		try {
			Files.move(FILE, kept, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.warn("Kept the unreadable settings as {}", kept);
		} catch (final IOException e) {
			LOGGER.warn("Could not keep a copy of {}", FILE, e);
		}
	}

	/** Written beside the file and moved over it, so quitting mid-write can't leave half a file. */
	public static void save() {
		final Path partial = FILE.resolveSibling(FILE.getFileName() + ".tmp");
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(partial)) {
				GSON.toJson(instance, writer);
			}
			try {
				Files.move(partial, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (final AtomicMoveNotSupportedException e) {
				Files.move(partial, FILE, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (final IOException | RuntimeException e) {
			LOGGER.warn("Could not save {}", FILE, e);
			try {
				Files.deleteIfExists(partial);
			} catch (final IOException ignored) {
			}
		}
	}

	public static void reset() {
		instance = new HorseConfig();
		apply();
	}

	/** The riding camera to the other shoulder (on over the right one, if it was centred), saved. */
	public static void swapShoulder() {
		instance.shoulder = instance.shoulder == Shoulder.RIGHT ? Shoulder.LEFT : Shoulder.RIGHT;
		apply();
		save();
	}

	/** Pushes the settings into the tuning values the camera and sound code read. */
	public static void apply() {
		final HorseConfig c = instance;
		c.cameraDistance = clamp(c.cameraDistance, DEFAULTS.cameraDistance, 0.5F, 1.5F);
		c.cameraHeight = clamp(c.cameraHeight, DEFAULTS.cameraHeight, CAMERA_HEIGHT_MIN, CAMERA_HEIGHT_MAX);
		c.speedZoom = clamp(c.speedZoom, DEFAULTS.speedZoom, 0.0F, 1.0F);
		c.viewBob = clamp(c.viewBob, DEFAULTS.viewBob, 0.0F, 1.0F);
		c.handBob = clamp(c.handBob, DEFAULTS.handBob, 0.0F, 1.0F);
		c.horseSounds = clamp(c.horseSounds, DEFAULTS.horseSounds, 0.0F, 1.0F);
		if (c.shoulder == null) {
			// (Missing, or a value this version doesn't know.)
			c.shoulder = DEFAULTS.shoulder;
		}
		AUTO_THIRD_PERSON = c.thirdPersonOnMount;
		CAMERA_DISTANCE_SCALE = c.cameraDistance;
		CAMERA_HEIGHT = c.cameraHeight;
		CAMERA_SIDE = SHOULDER_SIDE * c.shoulder.sign;
		FOV_GALLOP_BOOST = FOV_GALLOP_BOOST_BASE * c.speedZoom;
		FP_BOUNCE_SCALE = FP_BOUNCE_SCALE_BASE * c.viewBob;
		FP_NOD_SCALE = FP_NOD_SCALE_BASE * c.viewBob;
		FP_HAND_BOB = FP_HAND_BOB_BASE * c.handBob;
		HUFF_VOLUME_MIN = HUFF_VOLUME_MIN_BASE * c.horseSounds;
		HUFF_VOLUME_MAX = HUFF_VOLUME_MAX_BASE * c.horseSounds;
		EXHAUSTED_BREATH_VOLUME = EXHAUSTED_BREATH_VOLUME_BASE * c.horseSounds;
		LEAVES_RUSTLE_VOLUME = LEAVES_RUSTLE_VOLUME_BASE * c.horseSounds;
		REFUSAL_VOLUME = REFUSAL_VOLUME_BASE * c.horseSounds;
		CLIMB_SOUND_VOLUME = CLIMB_SOUND_VOLUME_BASE * c.horseSounds;
		CUT_SOUND_VOLUME = CUT_SOUND_VOLUME_BASE * c.horseSounds;
	}

	/** A setting kept in its range; one that isn't a number (a hand-edited file can hold NaN) counts as missing. */
	private static float clamp(final float value, final float fallback, final float min, final float max) {
		return Float.isFinite(value) ? Mth.clamp(value, min, max) : fallback;
	}
}
