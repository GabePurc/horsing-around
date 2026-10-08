package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
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
	private static HorseConfig instance = new HorseConfig();
	/** Range of the camera height setting, blocks. */
	public static final float CAMERA_HEIGHT_MIN = -0.5F;
	public static final float CAMERA_HEIGHT_MAX = 1.5F;

	// Camera.
	public boolean thirdPersonOnMount = true;
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
			}
		}
		apply();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(instance, writer);
			}
		} catch (final IOException e) {
			LOGGER.warn("Could not save {}", FILE, e);
		}
	}

	public static void reset() {
		instance = new HorseConfig();
		apply();
	}

	/** Pushes the settings into the tuning values the camera and sound code read. */
	public static void apply() {
		final HorseConfig c = instance;
		c.cameraDistance = Mth.clamp(c.cameraDistance, 0.5F, 1.5F);
		c.cameraHeight = Mth.clamp(c.cameraHeight, CAMERA_HEIGHT_MIN, CAMERA_HEIGHT_MAX);
		c.speedZoom = Mth.clamp(c.speedZoom, 0.0F, 1.0F);
		c.viewBob = Mth.clamp(c.viewBob, 0.0F, 1.0F);
		c.handBob = Mth.clamp(c.handBob, 0.0F, 1.0F);
		c.horseSounds = Mth.clamp(c.horseSounds, 0.0F, 1.0F);
		AUTO_THIRD_PERSON = c.thirdPersonOnMount;
		CAMERA_DISTANCE_SCALE = c.cameraDistance;
		CAMERA_HEIGHT = c.cameraHeight;
		FOV_GALLOP_BOOST = 0.05F * c.speedZoom;
		FP_BOUNCE_SCALE = 0.35F * c.viewBob;
		FP_NOD_SCALE = 0.15F * c.viewBob;
		FP_HAND_BOB = 0.6F * c.handBob;
		HUFF_VOLUME_MIN = 0.12F * c.horseSounds;
		HUFF_VOLUME_MAX = 0.3F * c.horseSounds;
		EXHAUSTED_BREATH_VOLUME = 0.45F * c.horseSounds;
		LEAVES_RUSTLE_VOLUME = 0.6F * c.horseSounds;
		REFUSAL_VOLUME = 0.5F * c.horseSounds;
		CLIMB_SOUND_VOLUME = 0.35F * c.horseSounds;
		CUT_SOUND_VOLUME = 0.6F * c.horseSounds;
	}
}
