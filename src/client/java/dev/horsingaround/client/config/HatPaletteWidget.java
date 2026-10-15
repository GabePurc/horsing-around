package dev.horsingaround.client.config;

import dev.horsingaround.client.cosmetic.HatColors;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?} else {
/*import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.navigation.CommonInputs;
*///?}
import net.minecraft.network.chat.Component;

/** A row of colour swatches for the hat; the current colour is outlined. Arrow keys move along it, Enter or Space picks. */
final class HatPaletteWidget extends AbstractWidget {
	private static final int SWATCH = 18;
	/** Outline of the swatch the keys are on. */
	private static final int CURSOR_OUTLINE = 0xFFFFFF55;

	private final int selected;
	private final IntConsumer pick;
	/** The swatch the keys are on; starts on the current colour. */
	private int cursor;

	HatPaletteWidget(final int width, final int selected, final IntConsumer pick) {
		super(0, 0, width, SWATCH + 2, Component.translatable("options.horsingaround.hat_palette"));
		this.selected = selected;
		this.pick = pick;
		for (int i = 0; i < HatColors.PALETTE.length; i++) {
			if (HatColors.PALETTE[i] == selected) {
				this.cursor = i;
			}
		}
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
			final int outline = this.isFocused() && i == this.cursor ? CURSOR_OUTLINE
				: color == this.selected ? 0xFFFFFFFF : hovered ? 0xFFA0A0A0 : 0xFF000000;
			graphics.fill(x - 1, y - 1, x + SWATCH + 1, y + SWATCH + 1, outline);
			graphics.fill(x, y, x + SWATCH, y + SWATCH, 0xFF000000 | color);
		}
	}

	//? if >=1.21.9 {
	@Override
	public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
		this.click(event.x());
	}
	//?} else {
	/*@Override
	public void onClick(final double mouseX, final double mouseY) {
		this.click(mouseX);
	}
	*///?}

	//? if >=1.21.9 {
	@Override
	public boolean keyPressed(final KeyEvent event) {
		return this.key(event.isLeft(), event.isRight(), event.isSelection());
	}
	//?} else {
	/*@Override
	public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
		return this.key(keyCode == InputConstants.KEY_LEFT, keyCode == InputConstants.KEY_RIGHT, CommonInputs.selected(keyCode));
	}
	*///?}

	/** Arrows move the cursor (past the ends they are left to the screen's own navigation), Enter or Space picks its colour. */
	private boolean key(final boolean left, final boolean right, final boolean select) {
		if (select) {
			this.playDownSound(Minecraft.getInstance().getSoundManager());
			this.pick.accept(HatColors.PALETTE[this.cursor]);
			return true;
		}
		final int to = this.cursor + (right ? 1 : left ? -1 : 0);
		if (to == this.cursor || to < 0 || to >= HatColors.PALETTE.length) {
			return false;
		}
		this.cursor = to;
		return true;
	}

	private void click(final double mouseX) {
		final int gap = this.gap();
		final int index = (int) ((mouseX - this.getX() - 1) / (SWATCH + gap));
		if (index >= 0 && index < HatColors.PALETTE.length && mouseX - this.getX() - 1 - index * (SWATCH + gap) < SWATCH) {
			this.pick.accept(HatColors.PALETTE[index]);
		}
	}

	@Override
	protected void updateWidgetNarration(final NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
