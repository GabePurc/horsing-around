package dev.horsingaround.gametest;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundSource;

/**
 * Test games make no sound, and run light: the lowest graphics settings. Frames are left to {@code TestClock}. Applied as the
 * game starts, before anything loads; the light settings when the run asks for them ({@code horsingaround.lowSettings}; off
 * for the gallery's store screenshots).
 */
public final class LowSettings {
	private LowSettings() {
	}

	public static void apply(final Options options) {
		if (TestClock.ON) {
			// (These are on the real clock, and would only hold the frames back. Vsync worse: with the window out of sight
			// the desktop may show so few frames that the game does a few a second, spinning in the graphics driver.)
			options.framerateLimit().set(260);
			options.enableVsync().set(false);
		}
		// (Every test game, whatever else it runs with: nobody wants them heard.)
		options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
		if (!Boolean.getBoolean("horsingaround.lowSettings")) {
			return;
		}
		options.enableVsync().set(false);
		// (Render distance stays at the test framework's 5: the tests build their lanes by command, which needs the chunks.)
		options.simulationDistance().set(5);
		options.entityDistanceScaling().set(0.5);
		options.mipmapLevels().set(0);
		options.maxAnisotropyBit().set(0);
		options.biomeBlendRadius().set(0);
		options.ambientOcclusion().set(false);
		options.entityShadows().set(false);
		options.improvedTransparency().set(false);
		options.cutoutLeaves().set(false);
		options.particles().set(ParticleStatus.MINIMAL);
		options.cloudStatus().set(CloudStatus.OFF);
		options.weatherRadius().set(3);
		options.menuBackgroundBlurriness().set(0);
		options.chunkSectionFadeInTime().set(0.0);
	}
}
