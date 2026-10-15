package dev.horsingaround.gametest;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundSource;

/**
 * Test games run light: the lowest graphics settings and no sound. The frame rate is left at the game's default: the
 * checks that sample what is drawn frame by frame (legs, body tilt, screenshots between ticks) are tuned at it, and at
 * one frame a tick several of them read the wrong moment. Applied as the game starts, before anything loads, when the
 * run asks for it ({@code horsingaround.lowSettings}; off for the gallery's store screenshots).
 */
public final class LowSettings {
	private LowSettings() {
	}

	public static void apply(final Options options) {
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
		options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
	}
}
