package dev.horsingaround.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's "Configure" button for Horsing Around. Only loaded when Mod Menu is installed. */
public final class HorseModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return HorseSettingsScreen::new;
	}
}
