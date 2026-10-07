package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Player settings, saved to {@code config/horsingaround.json} and applied to {@link RideTuning} live. Most are
 * multipliers on the tuned defaults (1.0 = as designed). Riding feel is simulated on each rider's own client, so those
 * settings are per player; trampling and leaves are decided by the server's copy.
 */
public final class HorseConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("horsingaround");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("horsingaround.json");
	private static HorseConfig instance = new HorseConfig();

	// Riding.
	public float speed = 1.0F;
	public float acceleration = 1.0F;
	public float turnGrip = 1.0F;
	public float turnResponse = 1.0F;
	public float sideAngle = 45.0F;
	public boolean horseAvoids = true;
	public boolean climbLedges = true;
	// Stamina and jumping.
	public float gallopSeconds = 28.0F;
	public float recovery = 1.0F;
	public float jumpHeight = 1.0F;
	public float jumpCost = 0.04F;
	// Camera and feel.
	public boolean thirdPersonOnMount = true;
	public float cameraDistance = 1.0F;
	public float speedFov = 0.05F;
	public float viewBob = 1.0F;
	public float handBob = 1.0F;
	public float horseLean = 1.0F;
	public float riderLean = 0.3F;
	// World.
	public boolean rideThroughLeaves = true;
	public float leavesSlowdown = 0.25F;
	public boolean trample = true;
	public float trampleDamage = 1.0F;
	// Sound.
	public float horseSounds = 1.0F;

	public static HorseConfig get() {
		return instance;
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
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

	/** Pushes the settings into the tuning values the ride code reads. */
	public static void apply() {
		final HorseConfig c = instance;
		for (int gait = 0; gait < GAIT_SPEED.length; gait++) {
			GAIT_SPEED[gait] = BASE_GAIT_SPEED[gait] * c.speed;
			GAIT_ACCEL[gait] = BASE_GAIT_ACCEL[gait] * c.acceleration;
			STAMINA_REGEN[gait] = BASE_STAMINA_REGEN[gait] * c.recovery;
		}
		DECEL_COAST = 0.03F * c.acceleration;
		DECEL_REIN = 0.035F * c.acceleration;
		DECEL_BRAKE = 0.07F * c.acceleration;
		TURN_RATE_STILL = 6.0F * c.turnGrip;
		LATERAL_GRIP = 9.0F * c.turnGrip;
		WEIGHT_SHIFT_STILL = 0.09F * c.turnResponse;
		WEIGHT_SHIFT_GALLOP = 0.18F * c.turnResponse;
		TURN_ACCEL = 0.25F * c.turnResponse;
		STEER_OFFSET = c.sideAngle;
		AVOID_DANGER = c.horseAvoids;
		LEDGE_CLIMB = c.climbLedges;
		STAMINA_DRAIN_GALLOP = 1.0F / (Math.max(c.gallopSeconds, 1.0F) * 20.0F);
		JUMP_POWER_STILL = 0.55F * c.jumpHeight;
		JUMP_MIN_VELOCITY = 0.42F * c.jumpHeight;
		JUMP_POWER_RUNNING = 0.8F * c.jumpHeight;
		JUMP_STAMINA_COST = c.jumpCost;
		AUTO_THIRD_PERSON = c.thirdPersonOnMount;
		CAMERA_DISTANCE_STILL = 4.0F * c.cameraDistance;
		CAMERA_DISTANCE_GALLOP = 5.5F * c.cameraDistance;
		FOV_GALLOP_BOOST = c.speedFov;
		FP_BOUNCE_SCALE = 0.35F * c.viewBob;
		FP_NOD_SCALE = 0.15F * c.viewBob;
		FP_HAND_BOB = 0.6F * c.handBob;
		LEAN_GAIN = 16.0F * c.horseLean;
		LEAN_MAX = 15.0F * c.horseLean;
		RIDER_BANK_FOLLOW = c.riderLean;
		RIDE_THROUGH_LEAVES = c.rideThroughLeaves;
		LEAVES_SPEED_FACTOR = 1.0F - c.leavesSlowdown;
		TRAMPLE = c.trample;
		TRAMPLE_DAMAGE_MAX = 8.0F * c.trampleDamage;
		HUFF_VOLUME_MIN = 0.12F * c.horseSounds;
		HUFF_VOLUME_MAX = 0.3F * c.horseSounds;
		EXHAUSTED_BREATH_VOLUME = 0.45F * c.horseSounds;
		LEAVES_RUSTLE_VOLUME = 0.6F * c.horseSounds;
		REFUSAL_VOLUME = 0.5F * c.horseSounds;
		CLIMB_SOUND_VOLUME = 0.35F * c.horseSounds;
	}
}
