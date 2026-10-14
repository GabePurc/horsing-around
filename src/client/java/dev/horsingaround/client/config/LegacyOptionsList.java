//? if <1.20.5 {
/*package dev.horsingaround.client.config;

import com.google.common.collect.ImmutableList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import org.jetbrains.annotations.Nullable;

// The options list of the vanilla screens, taking any widget in a row and not only options (before 1.20.5 the vanilla
// list's rows can't be made outside it). Rows sit in the middle, 310 wide in all, as in the vanilla lists.
final class LegacyOptionsList extends ContainerObjectSelectionList<LegacyOptionsList.Row> {
	private static final int ROW_WIDTH = 310;
	private static final int GAP = 10;

	LegacyOptionsList(final Minecraft minecraft, final int width, final int height) {
		super(minecraft, width, height - 64, 32, 25);
		this.centerListVertically = false;
	}

	void addBig(final OptionInstance<?> option) {
		final Map<OptionInstance<?>, AbstractWidget> options = new IdentityHashMap<>();
		options.put(option, option.createButton(this.minecraft.options, this.left(), 0, ROW_WIDTH));
		this.addEntry(new Row(options, ImmutableList.copyOf(options.values())));
	}

	void addSmall(final OptionInstance<?> first, final @Nullable OptionInstance<?> second) {
		final int half = (ROW_WIDTH - GAP) / 2;
		final Map<OptionInstance<?>, AbstractWidget> options = new IdentityHashMap<>();
		options.put(first, first.createButton(this.minecraft.options, this.left(), 0, half));
		if (second != null) {
			options.put(second, second.createButton(this.minecraft.options, this.left() + half + GAP, 0, half));
		}
		this.addEntry(new Row(options, ImmutableList.copyOf(options.values())));
	}

	// A row of one or two widgets of any kind.
	void addSmall(final AbstractWidget first, final @Nullable AbstractWidget second) {
		first.setX(this.left());
		if (second == null) {
			this.addEntry(new Row(Map.of(), List.of(first)));
			return;
		}
		final int half = (ROW_WIDTH - GAP) / 2;
		first.setWidth(half);
		second.setX(this.left() + half + GAP);
		second.setWidth(half);
		this.addEntry(new Row(Map.of(), List.of(first, second)));
	}

	@Nullable
	AbstractWidget findOption(final OptionInstance<?> option) {
		for (final Row row : this.children()) {
			final AbstractWidget widget = row.options.get(option);
			if (widget != null) {
				return widget;
			}
		}
		return null;
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
		private final Map<OptionInstance<?>, AbstractWidget> options;
		private final List<AbstractWidget> widgets;

		private Row(final Map<OptionInstance<?>, AbstractWidget> options, final List<AbstractWidget> widgets) {
			this.options = options;
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
