package dev.horsingaround.shoulder;

import dev.horsingaround.client.api.RideCameraApi;
import net.fabricmc.loader.api.FabricLoader;

/** Riding integration; only calls into Horsing Around when it is installed. */
public final class HorsingAroundCompat {
	static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("horsingaround");

	private HorsingAroundCompat() {
	}

	static void setClaimed(final boolean claimed) {
		if (PRESENT) {
			RideCameraApi.setThirdPersonClaimed(claimed);
		}
	}

	public static boolean isRiding() {
		return PRESENT && RideCameraApi.isRiding();
	}

	static double eyeY(final float partialTicks) {
		return RideCameraApi.eyeY(partialTicks);
	}

	static float distance(final float partialTicks) {
		return RideCameraApi.distance(partialTicks);
	}

	static float bounce() {
		return RideCameraApi.bounce();
	}
}
