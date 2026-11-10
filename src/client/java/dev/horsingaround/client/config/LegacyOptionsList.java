//? if <1.20.5 {
/*package dev.horsingaround.client.config;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import org.jetbrains.annotations.Nullable;

// The options list of the vanilla screens, for rows of this screen's own widgets (before 1.20.5 the vanilla list's rows
// can't be made outside it). Rows sit in the middle, 310 wide in all, as in the vanilla lists.
final class LegacyOptionsList extends ContainerObjectSelectionList<LegacyOptionsList.Row> {
	private static final int ROW_WIDTH = 310;
	private static final int GAP = 10;

	LegacyOptionsList(final Minecraft minecraft, final int width, final int height, final int top) {
		//? if >=1.20.3 {
		super(minecraft, width, height, top, 25);
		//?} else
		/^super(minecraft, width, minecraft.getWindow().getGuiScaledHeight(), top, top + height, 25);^/
		this.centerListVertically = false;
		// (On the frame's boards.)
		this.setRenderBackground(false);
		//? if <1.20.2
		/^this.setRenderTopAndBottom(false);^/
	}

	void clear() {
		this.clearEntries();
	}

	// A row of one or two widgets of any kind.
	void addSmall(final AbstractWidget first, final @Nullable AbstractWidget second) {
		first.setX(this.left());
		if (second == null) {
			this.addEntry(new Row(List.of(first)));
			return;
		}
		final int half = (ROW_WIDTH - GAP) / 2;
		first.setWidth(half);
		second.setX(this.left() + half + GAP);
		second.setWidth(half);
		this.addEntry(new Row(List.of(first, second)));
	}

	private int left() {
		return this.width / 2 - ROW_WIDTH / 2;
	}

	@Override
	public int getRowWidth() {
		return 400;
	}

	@Override
	protected int getScrollbarPosition() {
		return super.getScrollbarPosition() + 32;
	}

	static final class Row extends ContainerObjectSelectionList.Entry<Row> {
		private final List<AbstractWidget> widgets;

		private Row(final List<AbstractWidget> widgets) {
			this.widgets = widgets;
		}

		@Override
		public void render(final GuiGraphics graphics, final int index, final int top, final int left, final int width, final int height, final int mouseX, final int mouseY, final boolean hovered, final float partialTick) {
			for (final AbstractWidget widget : this.widgets) {
				widget.setY(top);
				widget.render(graphics, mouseX, mouseY, partialTick);
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return this.widgets;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return this.widgets;
		}
	}
}
*///?}
