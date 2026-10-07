package dev.horsingaround;

import dev.horsingaround.ride.HorseConfig;
import net.fabricmc.api.ModInitializer;

public final class HorsingAround implements ModInitializer {
	public static final String MOD_ID = "horsingaround";

	public static RiderBridge bridge = RiderBridge.NONE;

	@Override
	public void onInitialize() {
		HorseConfig.load();
	}
}
