package dev.horsingaround.gametest;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Test-only: a mouse pointer drawn into a clip's frames over a menu (the system cursor isn't part of the game's picture),
 * so the settings clip shows what is being clicked. A plain white arrow with a black edge, one pixel a GUI pixel.
 */
public final class ClipCursor {
	private static final String[] ARROW = {
		"#",
		"##",
		"#.#",
		"#..#",
		"#...#",
		"#....#",
		"#.....#",
		"#......#",
		"#.......#",
		"#....####",
		"#.#..#",
		"##.#..#",
		"#  #..#",
		"    #..#",
		"    ####",
	};

	private ClipCursor() {
	}

	public static void draw(final GuiGraphicsExtractor graphics, final int x, final int y) {
		graphics.nextStratum();
		for (int row = 0; row < ARROW.length; row++) {
			final String line = ARROW[row];
			for (int col = 0; col < line.length(); col++) {
				final char c = line.charAt(col);
				if (c != ' ') {
					graphics.fill(x + col, y + row, x + col + 1, y + row + 1, c == '#' ? 0xFF000000 : 0xFFFFFFFF);
				}
			}
		}
	}
}
