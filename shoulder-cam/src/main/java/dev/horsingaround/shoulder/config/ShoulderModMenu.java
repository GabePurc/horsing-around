package dev.horsingaround.shoulder.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's "Configure" button for this mod. Only loaded when Mod Menu is installed. */
public final class ShoulderModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ShoulderSettingsScreen::new;
	}
}
