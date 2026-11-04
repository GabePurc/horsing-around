package dev.horsingaround.client.config;

import dev.horsingaround.HorsingAround;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
//? if >=1.21.9
import net.minecraft.client.input.InputWithModifiers;
//? if >=1.21.6 {
import net.minecraft.client.renderer.RenderPipelines;
//?} elif >=1.21.2 {
/*import net.minecraft.client.renderer.RenderType;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The settings screen's tack room: wood boards, stitched saddle leather, leather straps and brass, drawn from the small
 * tiling textures in {@code textures/gui/settings/}. The widgets only change how the vanilla ones look: clicks, keys and
 * narration are still the game's own.
 */
final class Tack {
	static final Identifier WOOD = texture("wood");
	static final Identifier DEEP = texture("deep");
	private static final Identifier HIDE = texture("hide");
	private static final Identifier HIDE_DARK = texture("hide_dark");
	private static final Identifier STRAP = texture("strap");
	/** Boards are 64 by 32, leather 32 by 32; one texture pixel is one GUI pixel. */
	static final int BOARD_WIDTH = 64;
	static final int BOARD_HEIGHT = 32;
	private static final int LEATHER = 32;

	static final int EDGE = 0xFF120A07;
	static final int LIGHT_WOOD_EDGE = 0xFF5A3A2A;
	private static final int TEXT = 0xFFF4E8D8;
	private static final int TEXT_QUIET = 0xFFD9C4AE;
	private static final int TEXT_OFF = 0xFF8E7A6C;
	private static final int AMBER = 0xFFE7B463;
	private static final int BRASS = 0xFFC89A4A;
	private static final int BRASS_LIGHT = 0xFFEBC87A;
	private static final int BRASS_DARK = 0xFF7A5526;

	private Tack() {
	}

	private static Identifier texture(final String name) {
		return Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "textures/gui/settings/" + name + ".png");
	}

	static void image(final GuiGraphicsExtractor graphics, final Identifier texture, final int x, final int y, final float u, final float v, final int width,
		final int height, final int textureWidth, final int textureHeight) {
		//? if >=1.21.6 {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
		//?} elif >=1.21.2 {
		/*graphics.blit(RenderType::guiTextured, texture, x, y, u, v, width, height, textureWidth, textureHeight);
		*///?} else
		/*graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);*/
	}

	/** Fills a box with a tiling texture, from the box's corner. */
	static void tile(final GuiGraphicsExtractor graphics, final Identifier texture, final int x, final int y, final int width, final int height, final int tileWidth,
		final int tileHeight) {
		for (int dy = 0; dy < height; dy += tileHeight) {
			for (int dx = 0; dx < width; dx += tileWidth) {
				image(graphics, texture, x + dx, y + dy, 0.0F, 0.0F, Math.min(tileWidth, width - dx), Math.min(tileHeight, height - dy), tileWidth, tileHeight);
			}
		}
	}

	static void boards(final GuiGraphicsExtractor graphics, final Identifier texture, final int x, final int y, final int width, final int height) {
		tile(graphics, texture, x, y, width, height, BOARD_WIDTH, BOARD_HEIGHT);
	}

	static void text(final GuiGraphicsExtractor graphics, final Font font, final Component text, final int x, final int y, final int color) {
		//? if >=26.1 {
		graphics.text(font, text, x, y, color);
		//?} else
		/*graphics.drawString(font, text, x, y, color);*/
	}

	private static void centred(final GuiGraphicsExtractor graphics, final Font font, final Component text, final int centre, final int y, final int color) {
		//? if >=26.1 {
		graphics.centeredText(font, text, centre, y, color);
		//?} else
		/*graphics.drawCenteredString(font, text, centre, y, color);*/
	}

	/** A one-pixel box around the inside edge of an area. */
	private static void frame(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final int height, final int color) {
		graphics.fill(x, y, x + width, y + 1, color);
		graphics.fill(x, y + height - 1, x + width, y + height, color);
		graphics.fill(x, y + 1, x + 1, y + height - 1, color);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	/** A small brass rivet or stud. */
	static void stud(final GuiGraphicsExtractor graphics, final int x, final int y, final int size) {
		graphics.fill(x, y, x + size, y + size, BRASS);
		graphics.fill(x, y, x + size, y + 1, BRASS_LIGHT);
		graphics.fill(x, y + 1, x + 1, y + size, BRASS_LIGHT);
		graphics.fill(x + size - 1, y + 1, x + size, y + size, BRASS_DARK);
		graphics.fill(x + 1, y + size - 1, x + size - 1, y + size, BRASS_DARK);
	}

	/** A line of stitches, two pixels on and two off. */
	private static void stitchAcross(final GuiGraphicsExtractor graphics, final int fromX, final int toX, final int y, final int color) {
		for (int x = fromX; x < toX; x += 4) {
			graphics.fill(x, y, Math.min(x + 2, toX), y + 1, color);
		}
	}

	private static void stitchDown(final GuiGraphicsExtractor graphics, final int x, final int fromY, final int toY, final int color) {
		for (int y = fromY; y < toY; y += 4) {
			graphics.fill(x, y, x + 1, Math.min(y + 2, toY), color);
		}
	}

	/** A leather button or tag: hide, a raised edge, an outline (brass-amber when it's the one in hand), stitching. */
	private static void patch(final GuiGraphicsExtractor graphics, final AbstractWidget widget, final boolean bright, final boolean highlighted, final boolean stitchBottom) {
		final int x = widget.getX();
		final int y = widget.getY();
		final int width = widget.getWidth();
		final int height = widget.getHeight();
		tile(graphics, bright ? HIDE : HIDE_DARK, x, y, width, height, LEATHER, LEATHER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + 2, bright ? 0xFFB98550 : 0xFF7A5433);
		graphics.fill(x + 1, y + 2, x + 2, y + height - 1, bright ? 0xFFB98550 : 0xFF7A5433);
		graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 1, bright ? 0xFF5C3A1E : 0xFF2E1A0E);
		if (stitchBottom) {
			graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, bright ? 0xFF5C3A1E : 0xFF2E1A0E);
		}
		if (widget.isActive()) {
			final int stitch = bright ? 0xB0F4E0B8 : 0x60D9B98A;
			stitchAcross(graphics, x + 4, x + width - 4, y + 3, stitch);
			stitchDown(graphics, x + 3, y + 4, y + height - 3, stitch);
			stitchDown(graphics, x + width - 4, y + 4, y + height - 3, stitch);
			if (stitchBottom) {
				stitchAcross(graphics, x + 4, x + width - 4, y + height - 4, stitch);
			}
		}
		frame(graphics, x, y, width, height, highlighted ? AMBER : EDGE);
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	/** What the leather buttons share: only how they're told they were pressed and to draw differ between versions. */
	private abstract static class Leather extends AbstractButton {
		Leather(final int width, final Component label) {
			super(0, 0, width, 20, label);
		}

		abstract void pressed();

		abstract void draw(GuiGraphicsExtractor graphics);

		//? if >=1.21.9 {
		@Override
		public void onPress(final InputWithModifiers input) {
			this.pressed();
		}
		//?} else {
		/*@Override
		public void onPress() {
			this.pressed();
		}
		*///?}

		//? if >=1.21.11 {
		@Override
		protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
			this.draw(graphics);
		}
		//?} else {
		/*@Override
		public void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
			this.draw(graphics);
		}
		*///?}

		@Override
		protected void updateWidgetNarration(final NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	/** A stitched leather button: a toggle, a value to cycle through, or an action. */
	static final class Patch extends Leather {
		private final Supplier<Component> label;
		private final Runnable press;
		private final boolean bright;

		Patch(final int width, final Supplier<Component> label, final @Nullable Component tooltip, final boolean bright, final Runnable press) {
			super(width, label.get());
			this.label = label;
			this.press = press;
			this.bright = bright;
			if (tooltip != null) {
				this.setTooltip(Tooltip.create(tooltip));
			}
		}

		@Override
		void pressed() {
			this.press.run();
			this.setMessage(this.label.get());
		}

		@Override
		void draw(final GuiGraphicsExtractor graphics) {
			patch(graphics, this, this.bright, this.active && this.isHoveredOrFocused(), true);
			centred(graphics, font(), this.getMessage(), this.getX() + this.getWidth() / 2, this.getY() + 6, this.active ? TEXT : TEXT_OFF);
		}
	}

	/** A leather tag hanging over the panel; the selected one is lighter and runs into it. */
	static final class TabTag extends Leather {
		private final Runnable press;
		boolean selected;

		TabTag(final Component label, final boolean selected, final Runnable press) {
			super(80, label);
			this.selected = selected;
			this.press = press;
		}

		@Override
		void pressed() {
			this.press.run();
		}

		@Override
		void draw(final GuiGraphicsExtractor graphics) {
			patch(graphics, this, this.selected, !this.selected && this.isHoveredOrFocused(), false);
			if (this.selected) {
				// Over its own outline's bottom, so the tag and the panel read as one.
				graphics.fill(this.getX() + 1, this.getY() + this.getHeight() - 1, this.getX() + this.getWidth() - 1, this.getY() + this.getHeight(), 0xFF7A4A26);
			}
			final int color = this.selected || this.isHoveredOrFocused() ? TEXT : TEXT_QUIET;
			centred(graphics, font(), this.getMessage(), this.getX() + this.getWidth() / 2, this.getY() + 6, color);
		}
	}

	/** A slider: a leather strap, lighter up to a brass buckle that slides along it. */
	static final class Strap extends AbstractSliderButton {
		private final int min;
		private final int max;
		private final IntFunction<Component> label;
		private final IntConsumer set;

		Strap(final int width, final int min, final int max, final int value, final IntFunction<Component> label, final Component tooltip, final IntConsumer set) {
			super(0, 0, width, 20, label.apply(value), (value - min) / (double) (max - min));
			this.min = min;
			this.max = max;
			this.label = label;
			this.set = set;
			this.setTooltip(Tooltip.create(tooltip));
		}

		private int amount() {
			return this.min + (int) Math.round(this.value * (this.max - this.min));
		}

		@Override
		protected void updateMessage() {
			this.setMessage(this.label.apply(this.amount()));
		}

		@Override
		protected void applyValue() {
			this.set.accept(this.amount());
		}

		@Override
		public void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
			final int x = this.getX();
			final int y = this.getY();
			final int width = this.getWidth();
			final int height = this.getHeight();
			final int buckle = x + (int) (this.value * (width - 8));
			tile(graphics, STRAP, x, y, width, height, LEATHER, LEATHER);
			tile(graphics, HIDE_DARK, x, y, buckle - x, height, LEATHER, LEATHER);
			graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFF170D08);
			graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xFF54351F);
			frame(graphics, x, y, width, height, this.isHoveredOrFocused() ? AMBER : EDGE);
			graphics.fill(buckle, y, buckle + 8, y + height, EDGE);
			graphics.fill(buckle + 1, y + 1, buckle + 7, y + height - 1, BRASS);
			graphics.fill(buckle + 1, y + 1, buckle + 7, y + 2, BRASS_LIGHT);
			graphics.fill(buckle + 1, y + 2, buckle + 2, y + height - 1, BRASS_LIGHT);
			graphics.fill(buckle + 6, y + 2, buckle + 7, y + height - 1, BRASS_DARK);
			graphics.fill(buckle + 2, y + height - 2, buckle + 6, y + height - 1, BRASS_DARK);
			graphics.fill(buckle + 3, y + 7, buckle + 5, y + height - 7, 0xFF2A1811);
			centred(graphics, font(), this.getMessage(), x + width / 2, y + 6, TEXT);
		}
	}

	/**
	 * A few lines of plain words, centred. The list's rows are all one height, so it draws down over as many rows as
	 * {@link #rows()} says; the screen leaves the rest of them empty.
	 */
	static final class Note extends AbstractWidget {
		private static final int ROW = 25;
		private static final int LINE = 11;
		private final List<FormattedCharSequence> lines;

		Note(final int width, final Component text) {
			super(0, 0, width, 20, text);
			this.active = false;
			this.lines = font().split(text, width - 20);
		}

		int rows() {
			return Math.max(1, (this.lines.size() * LINE + ROW - 1) / ROW);
		}

		@Override
		protected void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
			final int centre = this.getX() + this.getWidth() / 2;
			int y = this.getY() + (this.rows() * ROW - 5 - this.lines.size() * LINE) / 2 + 1;
			for (final FormattedCharSequence line : this.lines) {
				//? if >=26.1 {
				graphics.centeredText(font(), line, centre, y, TEXT_QUIET);
				//?} else
				/*graphics.drawCenteredString(font(), line, centre, y, TEXT_QUIET);*/
				y += LINE;
			}
		}

		@Override
		protected void updateWidgetNarration(final NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	/** A section heading, amber between two brass studs; nothing to click or stop on. */
	static final class Heading extends AbstractWidget {
		Heading(final int width, final Component text) {
			super(0, 0, width, 20, text);
			this.active = false;
		}

		@Override
		protected void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
			final Font font = font();
			final int centre = this.getX() + this.getWidth() / 2;
			final int half = font.width(this.getMessage()) / 2;
			final int y = this.getY() + 8;
			centred(graphics, font, this.getMessage(), centre, y, AMBER);
			stud(graphics, centre - half - 9, y + 1, 4);
			stud(graphics, centre + half + 5, y + 1, 4);
		}

		@Override
		protected void updateWidgetNarration(final NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
