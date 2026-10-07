package dev.horsingaround.client;

import dev.horsingaround.HorsingAround;
import com.mojang.blaze3d.platform.InputConstants;
import dev.horsingaround.client.compat.EmfSaddleTracker;
import dev.horsingaround.client.config.HorseSettingsScreen;
import dev.horsingaround.client.debug.SteeringOverlay;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class HorsingAroundClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		final RiderKeys keys = new RiderKeys(Minecraft.getInstance());
		HorsingAround.bridge = keys;
		if (FabricLoader.getInstance().isModLoaded("entity_model_features")) {
			EmfSaddleTracker.register();
		}
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
		});
	}
}
