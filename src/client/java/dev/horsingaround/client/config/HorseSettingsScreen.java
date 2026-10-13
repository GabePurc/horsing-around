package dev.horsingaround.client.config;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.client.RideCamera;
import dev.horsingaround.client.cosmetic.HatColors;
import dev.horsingaround.client.cosmetic.Hats;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import dev.horsingaround.ride.HorseConfig;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
//? if >=1.20.5
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.StringWidget;
//? if >=1.20.5
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
//? if <1.20.5 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?} elif <1.21 {
/*import net.minecraft.client.gui.components.OptionsList;
*///?}
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
//? if <1.20.3
/*import net.minecraft.network.chat.Style;*/
import net.minecraft.resources.Identifier;

/**
 * Horsing Around settings: only how riding looks and sounds to you (how horses ride is the same for everyone). In the
 * style of the vanilla options screens, with the mod's icon in the title, a tooltip on every option, and changes applied
 * live.
 */
public final class HorseSettingsScreen extends OptionsSubScreen {
	private static final String KEY = "options.horsingaround.";
	private static final Identifier ICON = Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "icon.png");
	/** Section headings: saddle-leather tan. */
	private static final int SECTION_COLOR = 0xE3B778;
	private static final URI SUPPORT = URI.create("https://buymeacoffee.com/blintzbug");

	public HorseSettingsScreen(final Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
	}

	//? if <1.20.5 {
	/*// Before 1.20.5 the options screens make their own list and place everything by hand (the base class has neither a
	// layout nor hooks for them).
	private LegacyOptionsList list;

	@Override
	protected void init() {
		this.list = this.addRenderableWidget(new LegacyOptionsList(this.minecraft, this.width, this.height));
		this.addOptions();
		this.addFooter();
	}

	private void drawTitle(final GuiGraphics graphics) {
		final int left = (this.width - 22 - this.font.width(this.title)) / 2;
		graphics.blit(ICON, left, 14, 0.0F, 0.0F, 16, 16, 16, 16);
		graphics.drawString(this.font, this.title, left + 22, 20, 0xFFFFFF);
	}
	*///?}
	//? if <1.20.2 {
	/*@Override
	public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
		this.renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		this.drawTitle(graphics);
	}
	*///?} elif <1.20.5 {
	/*@Override
	public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		this.drawTitle(graphics);
	}
	*///?}
	//? if >=1.20.3 && <1.20.5 {
	/*@Override
	public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
		this.renderDirtBackground(graphics);
	}
	*///?}
	//? if >=1.20.5 && <1.21 {
	/*// Before 1.21 the options screens put their lists in themselves (and the base class has no hook for it).
	private OptionsList list;

	@Override
	protected void init() {
		this.list = this.addRenderableWidget(new OptionsList(this.minecraft, this.width, this.height, this));
		this.addOptions();
		super.init();
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();
		this.list.updateSize(this.width, this.layout);
	}
	*///?}

	//? if >=1.20.5 {
	@Override
	protected void addTitle() {
		final LinearLayout title = LinearLayout.horizontal().spacing(6);
		title.defaultCellSetting().alignVerticallyMiddle();
		title.addChild(ImageWidget.texture(16, 16, ICON, 16, 16));
		title.addChild(new StringWidget(this.title, this.font));
		this.layout.addToHeader(title);
	}
	//?}

	//? if >=1.21 {
	@Override
	//?}
	protected void addOptions() {
		final HorseConfig c = HorseConfig.get();
		this.addHeader(Component.translatable(KEY + "subtitle").withStyle(ChatFormatting.GRAY));

		this.addHeader(section("camera"));
		this.list.addBig(OptionInstance.createBoolean(KEY + "third_person", tooltip("third_person"), c.thirdPersonOnMount, v -> {
			c.thirdPersonOnMount = v;
			HorseConfig.apply();
		}));
		final OptionInstance<Integer> distance = percent("camera_distance", 50, 150, c.cameraDistance, v -> c.cameraDistance = v);
		final OptionInstance<Integer> height = blocks("camera_height", HorseConfig.CAMERA_HEIGHT_MIN, HorseConfig.CAMERA_HEIGHT_MAX, c.cameraHeight, v -> c.cameraHeight = v);
		this.list.addSmall(distance, height);
		this.list.addBig(percent("speed_zoom", 0, 100, c.speedZoom, v -> c.speedZoom = v));
		if (RideCamera.isThirdPersonClaimed()) {
			// Over the Shoulder places the third-person riding camera, with its own distance and height.
			for (final OptionInstance<Integer> option : List.of(distance, height)) {
				final AbstractWidget widget = this.list.findOption(option);
				if (widget != null) {
					widget.active = false;
					widget.setTooltip(Tooltip.create(Component.translatable(KEY + "camera_claimed.tooltip")));
				}
			}
		}

		this.addHeader(section("comfort"));
		this.list.addSmall(
			percent("view_bob", 0, 100, c.viewBob, v -> c.viewBob = v),
			percent("hand_bob", 0, 100, c.handBob, v -> c.handBob = v)
		);

		this.addHeader(section("sound"));
		this.list.addBig(percent("horse_sounds", 0, 100, c.horseSounds, v -> c.horseSounds = v));

		this.addHatOptions(c);
	}

	/** Supporters wear a cowboy hat and pick its colour; everyone else hears how to get one. */
	private void addHatOptions(final HorseConfig c) {
		if (!Hats.canWear()) {
			this.addHeader(section("hat"));
			//? if >=1.21 {
			final Button.OnPress open = ConfirmLinkScreen.confirmLink(this, SUPPORT, true);
			//?} elif >=1.20.3 {
			/*final Button.OnPress open = ConfirmLinkScreen.confirmLink(this, SUPPORT.toString());
			*///?} else
			/*final Button.OnPress open = ConfirmLinkScreen.confirmLink(SUPPORT.toString(), this, true);*/
			this.addWide(Button.builder(Component.translatable(KEY + "hat.support"), open)
				.tooltip(Tooltip.create(Component.translatable(KEY + "hat.support.tooltip"))).build());
			return;
		}
		this.addHeader(section("hat_supporter"));
		this.list.addBig(OptionInstance.createBoolean(KEY + "hat", tooltip("hat"), c.hat, v -> {
			c.hat = v;
			Hats.sendOwn();
		}));
		this.addWide(new HatPaletteWidget(310, c.hatColor, color -> {
			c.hatColor = color;
			Hats.sendOwn();
			this.rebuildWidgets();
		}));
		final float[] hsv = HatColors.toHsv(c.hatColor);
		this.list.addSmall(
			slider("hat_hue", 0, 359, Math.round(hsv[0]), "degrees", hue -> c.hatColor = HatColors.fromHsv(hue, HatColors.toHsv(c.hatColor)[1], HatColors.toHsv(c.hatColor)[2])),
			slider("hat_richness", 0, 100, Math.round(hsv[1] * 100.0F), "percent", rich -> c.hatColor = HatColors.fromHsv(HatColors.toHsv(c.hatColor)[0], rich / 100.0F, HatColors.toHsv(c.hatColor)[2]))
		);
		this.list.addBig(
			slider("hat_brightness", 5, 100, Math.round(hsv[2] * 100.0F), "percent", bright -> c.hatColor = HatColors.fromHsv(HatColors.toHsv(c.hatColor)[0], HatColors.toHsv(c.hatColor)[1], bright / 100.0F))
		);
	}

	/** A heading row (before 1.21.11 the list has no headings; the text goes in a row of its own). */
	private void addHeader(final Component text) {
		//? if >=1.21.11 {
		this.list.addHeader(text);
		//?} else {
		/*this.list.addSmall(new StringWidget(310, 20, text, this.font), null);
		*///?}
	}

	/** A full-width widget in its own row (before 26.2 the list only took options as big rows, so it is a lone small one). */
	private void addWide(final AbstractWidget widget) {
		//? if >=26.2 {
		this.list.addBig(widget);
		//?} else {
		/*widget.setWidth(310);
		this.list.addSmall(widget, null);
		*///?}
	}

	/** A hat colour slider: changes the colour live (sent to the server when the screen closes). */
	private static OptionInstance<Integer> slider(final String id, final int min, final int max, final int value, final String unit, final IntConsumer set) {
		return new OptionInstance<>(
			KEY + id, tooltip(id),
			(caption, amount) -> Options.genericValueLabel(caption, Component.translatable(KEY + unit, amount)),
			new OptionInstance.IntRange(min, max),
			value,
			set::accept
		);
	}

	//? if >=1.20.5 {
	@Override
	protected void addFooter() {
		final LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable(KEY + "reset"), button -> this.reset()).width(150).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(150).build());
	}
	//?} else {
	/*private void addFooter() {
		final int top = this.height - 27;
		this.addRenderableWidget(Button.builder(Component.translatable(KEY + "reset"), button -> this.reset()).bounds(this.width / 2 - 154, top, 150, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).bounds(this.width / 2 + 4, top, 150, 20).build());
	}
	*///?}

	private void reset() {
		HorseConfig.reset();
		//? if >=26.2 {
		this.minecraft.gui.setScreen(new HorseSettingsScreen(this.lastScreen));
		//?} else
		/*this.minecraft.setScreen(new HorseSettingsScreen(this.lastScreen));*/
	}

	@Override
	public void removed() {
		super.removed();
		HorseConfig.save();
		Hats.sendOwn();
	}

	private static Component section(final String id) {
		//? if >=1.20.3 {
		return Component.translatable(KEY + "section." + id).withColor(SECTION_COLOR);
		//?} else
		/*return Component.translatable(KEY + "section." + id).withStyle(Style.EMPTY.withColor(SECTION_COLOR));*/
	}

	private static <T> OptionInstance.TooltipSupplier<T> tooltip(final String id) {
		return OptionInstance.cachedConstantTooltip(Component.translatable(KEY + id + ".tooltip"));
	}

	/** Slider in hundredths of a block, shown as "0.2 blocks". */
	private static OptionInstance<Integer> blocks(final String id, final float min, final float max, final float value, final Consumer<Float> set) {
		return new OptionInstance<>(
			KEY + id, tooltip(id),
			(caption, hundredths) -> Options.genericValueLabel(caption, Component.translatable(KEY + "blocks", trim(hundredths / 100.0F))),
			new OptionInstance.IntRange(Math.round(min * 100.0F), Math.round(max * 100.0F)),
			Math.round(value * 100.0F),
			hundredths -> {
				set.accept(hundredths / 100.0F);
				HorseConfig.apply();
			}
		);
	}

	/** 0.20 -> "0.2", -0.25 -> "-0.25", 1.00 -> "1". */
	private static String trim(final float value) {
		String text = String.format(Locale.ROOT, "%.2f", value);
		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
	}

	/** Slider in whole percent of the designed effect; 0 reads "Off". */
	private static OptionInstance<Integer> percent(final String id, final int min, final int max, final float value, final Consumer<Float> set) {
		return new OptionInstance<>(
			KEY + id, tooltip(id),
			(caption, percent) -> percent == 0
				? Options.genericValueLabel(caption, CommonComponents.OPTION_OFF)
				: Options.genericValueLabel(caption, Component.translatable(KEY + "percent", percent)),
			new OptionInstance.IntRange(min, max),
			Math.round(value * 100.0F),
			percent -> {
				set.accept(percent / 100.0F);
				HorseConfig.apply();
			}
		);
	}
}
