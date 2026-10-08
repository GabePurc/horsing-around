package dev.horsingaround.client;

import dev.horsingaround.HorsingAround;
import com.mojang.blaze3d.platform.InputConstants;
import dev.horsingaround.client.compat.EmfSaddleTracker;
import dev.horsingaround.client.config.HorseSettingsScreen;
import dev.horsingaround.client.debug.SteeringOverlay;
import dev.horsingaround.net.RulesPayload;
import dev.horsingaround.net.ServerRules;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HorsingAroundClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger(HorsingAround.MOD_ID);

	@Override
	public void onInitializeClient() {
		final RiderKeys keys = new RiderKeys(Minecraft.getInstance());
		HorsingAround.bridge = keys;
		if (FabricLoader.getInstance().isModLoaded("entity_model_features")) {
			try {
				EmfSaddleTracker.register();
			} catch (final LinkageError | RuntimeException e) {
				// A version of Entity Model Features we don't know: the rider follows an imitation of the gait instead.
				LOGGER.warn("Could not hook Entity Model Features animations; riders follow a generated gait motion instead", e);
			}
		}
		// The server tells us it runs the mod (and its rules); until then, and on servers without it, horses ride as in vanilla.
		ClientPlayNetworking.registerGlobalReceiver(RulesPayload.TYPE, (rules, context) -> ServerRules.accept(rules));
		ClientPlayConnectionEvents.INIT.register((handler, minecraft) -> {
			ServerRules.reset();
			ServerNotice.reset();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> ServerRules.reset());
		final KeyMapping settings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.horsingaround.settings", InputConstants.UNKNOWN.getValue(),
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "horses"))
		));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!RideCamera.tick(minecraft)) {
				keys.discardPresses();
			}
			while (settings.consumeClick()) {
				minecraft.gui.setScreen(new HorseSettingsScreen(minecraft.gui.screen()));
			}
			SteeringOverlay.tick(minecraft);
			ServerNotice.tick(minecraft);
		});
	}
}
