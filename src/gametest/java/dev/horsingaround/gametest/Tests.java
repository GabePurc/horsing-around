package dev.horsingaround.gametest;

import dev.horsingaround.gametest.harness.ClientGameTest;
import java.util.List;

/**
 * Every client test, in the order a game runs them (each picks itself from -Dhorsingaround.tests); the summary last. The
 * harness (src/gametest/.../harness) runs them on every loader.
 */
public final class Tests {
	private Tests() {
	}

	public static List<ClientGameTest> all() {
		// (Icy's, Real Camera's and the store's and gallery's are 26.3's alone until their compat packs move, issue 127.)
		return List.of(
			new RideFeelTest(), new TerrainRideTest(), new PerfTest(), new MountsTest(), new ServerRideTest(), new HatTest(),
			//? if >=26.3
			new IcyFeaturesTest(), new RealCameraTest(), new GalleryTest(), new StoreMediaTest(),
			new TestSummary()
		);
	}
}
