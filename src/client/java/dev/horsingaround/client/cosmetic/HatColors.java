package dev.horsingaround.client.cosmetic;

import net.minecraft.util.Mth;

/** The hat palette and colour maths (hue, richness and brightness, so any colour is reachable from three sliders). */
public final class HatColors {
	/** Western felt colours. */
	public static final int[] PALETTE = {
		0x8B5A2B, 0x5C3A21, 0x3B2A20, 0xC8A27A, 0xE9DCC4, 0xF4F1EA, 0x2B2B2B, 0x6E6E6E,
		0x8E2B2B, 0x2F4A6E, 0x3E5B34, 0x6B4A7A,
	};

	private HatColors() {
	}

	/** Hue in degrees (0-360), saturation and value (0-1) to RGB. */
	public static int fromHsv(final float hue, final float saturation, final float value) {
		final float h = ((hue % 360.0F) + 360.0F) % 360.0F / 60.0F;
		final float c = value * saturation;
		final float x = c * (1.0F - Math.abs(h % 2.0F - 1.0F));
		final float m = value - c;
		final float r;
		final float g;
		final float b;
		switch ((int) h) {
			case 0 -> { r = c; g = x; b = 0.0F; }
			case 1 -> { r = x; g = c; b = 0.0F; }
			case 2 -> { r = 0.0F; g = c; b = x; }
			case 3 -> { r = 0.0F; g = x; b = c; }
			case 4 -> { r = x; g = 0.0F; b = c; }
			default -> { r = c; g = 0.0F; b = x; }
		}
		return channel(r + m) << 16 | channel(g + m) << 8 | channel(b + m);
	}

	/** RGB to {hue degrees, saturation, value}. */
	public static float[] toHsv(final int rgb) {
		final float r = (rgb >> 16 & 0xFF) / 255.0F;
		final float g = (rgb >> 8 & 0xFF) / 255.0F;
		final float b = (rgb & 0xFF) / 255.0F;
		final float max = Math.max(r, Math.max(g, b));
		final float min = Math.min(r, Math.min(g, b));
		final float d = max - min;
		float hue = 0.0F;
		if (d > 0.0F) {
			if (max == r) {
				hue = 60.0F * (((g - b) / d) % 6.0F);
			} else if (max == g) {
				hue = 60.0F * ((b - r) / d + 2.0F);
			} else {
				hue = 60.0F * ((r - g) / d + 4.0F);
			}
		}
		return new float[] {(hue + 360.0F) % 360.0F, max == 0.0F ? 0.0F : d / max, max};
	}

	private static int channel(final float v) {
		return Mth.clamp(Math.round(v * 255.0F), 0, 255);
	}
}
