package dev.horsingaround.client;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.Platform;
import com.mojang.blaze3d.platform.InputConstants;
//? if >=1.21
import dev.horsingaround.client.compat.EmfSaddleTracker;
import dev.horsingaround.client.config.HorseSettingsScreen;
import dev.horsingaround.client.debug.SteeringOverlay;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.client.cosmetic.Supporters;
import dev.horsingaround.net.RulesPayload;
import dev.horsingaround.net.ServerRules;
import dev.horsingaround.ride.HorseConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The mod's client start-up and hooks, called from the loader's client entry point (client/fabric/, client/neoforge/). */
public final class HorsingAroundClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(HorsingAround.MOD_ID);

	//? if >=1.21.9 && fabric {
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "horses"));
	//?} elif >=1.21.9 {
	/*// (NeoForge registers it with the key mappings.)
	public static final KeyMapping.Category KEY_CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "horses"));
	*///?} else
	/*private static final String KEY_CATEGORY = "key.category.horsingaround.horses";*/
	/** The loader registers these (they're left unbound or on O, the defaults). */
	public static final KeyMapping SETTINGS_KEY = new KeyMapping("key.horsingaround.settings", InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
	public static final KeyMapping SWAP_SHOULDER_KEY = new KeyMapping("key.horsingaround.swap_shoulder", InputConstants.KEY_O, KEY_CATEGORY);

	private static RiderKeys keys;

	private HorsingAroundClient() {
	}

	public static void init() {
		keys = new RiderKeys(Minecraft.getInstance());
		HorsingAround.bridge = keys;
		//? if >=1.21 {
		if (Platform.isModLoaded("entity_model_features")) {
			try {
				EmfSaddleTracker.register();
			} catch (final LinkageError | RuntimeException e) {
				// A version of Entity Model Features we don't know: the rider follows an imitation of the gait instead.
				LOGGER.warn("Could not hook Entity Model Features animations; riders follow a generated gait motion instead", e);
			}
		}
		//?}
		// The supporters' cowboy hat.
		Supporters.load();
	}

	/** End of each client tick. */
	public static void tick(final Minecraft minecraft) {
		if (keys == null) {
			// (Still starting up: NeoForge ticks the game while mods load.)
			return;
		}
		RideCamera.tick(minecraft);
		keys.endTick();
		while (SETTINGS_KEY.consumeClick()) {
			HorseSettingsScreen.open(minecraft);
		}
		while (SWAP_SHOULDER_KEY.consumeClick()) {
			// (Only while riding: on foot the key does nothing, and other mods may share it.)
			if (RideCamera.isRiding()) {
				HorseConfig.swapShoulder();
			}
		}
		SteeringOverlay.tick(minecraft);
		ServerNotice.tick(minecraft);
	}

	/**
	 * Joining a server. It tells us it runs the mod (and its rules) after this; until then, and on servers without it,
	 * horses ride as in vanilla.
	 */
	public static void joining() {
		ServerRules.reset();
		ServerNotice.reset();
		Hats.clear();
	}

	public static void left() {
		ServerRules.reset();
		Hats.clear();
	}

	/** The server runs the mod: its rules apply, and it hears which hat this player wears. */
	public static void acceptRules(final RulesPayload rules) {
		ServerRules.accept(rules);
		Hats.sendOwn();
	}
}
