package dev.horsingaround.shoulder;

import com.mojang.blaze3d.platform.InputConstants;
import dev.horsingaround.shoulder.config.ShoulderConfig;
import dev.horsingaround.shoulder.config.ShoulderSettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class ShoulderCamClient implements ClientModInitializer {
	public static final String MOD_ID = "horsingaround_shoulder";

	@Override
	public void onInitializeClient() {
		final KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "camera"));
		final KeyMapping swap = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key." + MOD_ID + ".swap", InputConstants.KEY_O, category)
		);
		final KeyMapping toggle = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key." + MOD_ID + ".toggle", InputConstants.UNKNOWN.getValue(), category)
		);
		final KeyMapping settings = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key." + MOD_ID + ".settings", InputConstants.UNKNOWN.getValue(), category)
		);
		ShoulderConfig.load();
		setEnabled(ShoulderConfig.get().enabled);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (swap.consumeClick()) {
				swapShoulder();
			}
			while (toggle.consumeClick()) {
				setEnabled(!ShoulderConfig.get().enabled);
				ShoulderConfig.save();
			}
			while (settings.consumeClick()) {
				minecraft.gui.setScreen(new ShoulderSettingsScreen(minecraft.gui.screen()));
			}
		});
	}

	public static void swapShoulder() {
		ShoulderConfig.get().leftShoulder = !ShoulderConfig.get().leftShoulder;
	}

	public static void setEnabled(final boolean enabled) {
		ShoulderConfig.get().enabled = enabled;
		HorsingAroundCompat.setClaimed(enabled);
	}
}
