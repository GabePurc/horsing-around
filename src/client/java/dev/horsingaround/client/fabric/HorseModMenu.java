package dev.horsingaround.client.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import dev.horsingaround.client.config.HorseSettingsScreen;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's "Configure" button for Horsing Around. Only loaded when Mod Menu is installed. */
public final class HorseModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return HorseSettingsScreen::new;
	}
}
