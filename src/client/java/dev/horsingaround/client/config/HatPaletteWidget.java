package dev.horsingaround.client.config;

import dev.horsingaround.client.cosmetic.HatColors;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A row of colour swatches for the hat; the current colour is outlined. */
final class HatPaletteWidget extends AbstractWidget {
	private static final int SWATCH = 18;

	private final int selected;
	private final IntConsumer pick;

	HatPaletteWidget(final int width, final int selected, final IntConsumer pick) {
		super(0, 0, width, SWATCH + 2, Component.translatable("options.horsingaround.hat_palette"));
		this.selected = selected;
		this.pick = pick;
	}

	private int gap() {
		return (this.getWidth() - 2 - HatColors.PALETTE.length * SWATCH) / (HatColors.PALETTE.length - 1);
	}

	@Override
	protected void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		final int gap = this.gap();
		for (int i = 0; i < HatColors.PALETTE.length; i++) {
			final int x = this.getX() + 1 + i * (SWATCH + gap);
			final int y = this.getY() + 1;
			final int color = HatColors.PALETTE[i];
			final boolean hovered = mouseX >= x && mouseX < x + SWATCH && mouseY >= y && mouseY < y + SWATCH;
			graphics.fill(x - 1, y - 1, x + SWATCH + 1, y + SWATCH + 1, color == this.selected ? 0xFFFFFFFF : hovered ? 0xFFA0A0A0 : 0xFF000000);
			graphics.fill(x, y, x + SWATCH, y + SWATCH, 0xFF000000 | color);
		}
	}

	@Override
	public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
		final int gap = this.gap();
		final int index = (int) ((event.x() - this.getX() - 1) / (SWATCH + gap));
		if (index >= 0 && index < HatColors.PALETTE.length && event.x() - this.getX() - 1 - index * (SWATCH + gap) < SWATCH) {
			this.pick.accept(HatColors.PALETTE[index]);
		}
	}

	@Override
	protected void updateWidgetNarration(final NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
