package dev.horsingaround.client.config;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.client.cosmetic.HatColors;
import dev.horsingaround.client.cosmetic.Hats;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.IntConsumer;
import com.mojang.serialization.Codec;
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

	/**
	 * The hat colour as the sliders show it: hue (degrees), richness and brightness (0-1). Kept here and only turned into
	 * the colour (never back), so a slider at its end (grey has no hue) doesn't lose the others, and rounding to 8-bit
	 * channels doesn't drift them.
	 */
	private float[] hsv;

	public HorseSettingsScreen(final Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
		this.hsv = HatColors.toHsv(HorseConfig.get().hatColor);
	}

	/** Opens the settings over whatever is showing now (the key binding). */
	public static void open(final Minecraft minecraft) {
		//? if >=26.2 {
		show(minecraft, minecraft.gui.screen());
		//?} else
		/*show(minecraft, minecraft.screen);*/
	}

	private static void show(final Minecraft minecraft, final Screen parent) {
		//? if >=26.2 {
		minecraft.gui.setScreen(new HorseSettingsScreen(parent));
		//?} else
		/*minecraft.setScreen(new HorseSettingsScreen(parent));*/
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
		this.list.addBig(new OptionInstance<>(
			KEY + "shoulder", tooltip("shoulder"),
			(caption, shoulder) -> Options.genericValueLabel(caption, Component.translatable(KEY + "shoulder." + shoulder.name().toLowerCase(Locale.ROOT))),
			new OptionInstance.Enum<>(Arrays.asList(HorseConfig.Shoulder.values()), Codec.STRING.xmap(HorseConfig.Shoulder::valueOf, HorseConfig.Shoulder::name)),
			c.shoulder,
			shoulder -> {
				c.shoulder = shoulder;
				HorseConfig.apply();
			}
		));
		this.list.addSmall(
			percent("camera_distance", 50, 150, c.cameraDistance, v -> c.cameraDistance = v),
			blocks("camera_height", HorseConfig.CAMERA_HEIGHT_MIN, HorseConfig.CAMERA_HEIGHT_MAX, c.cameraHeight, v -> c.cameraHeight = v)
		);
		this.list.addBig(percent("speed_zoom", 0, 100, c.speedZoom, v -> c.speedZoom = v));

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
			this.hsv = HatColors.toHsv(color);
			Hats.sendOwn();
			// The sliders have to show the new colour, so the widgets are made again, but the list stays where it is.
			this.rebuildKeepingScroll();
		}));
		this.list.addSmall(
			slider("hat_hue", 0, 359, Math.min(Math.round(this.hsv[0]), 359), "degrees", hue -> this.setHsv(c, 0, hue)),
			slider("hat_richness", 0, 100, Math.round(this.hsv[1] * 100.0F), "percent", rich -> this.setHsv(c, 1, rich / 100.0F))
		);
		this.list.addBig(
			slider("hat_brightness", 5, 100, Math.max(Math.round(this.hsv[2] * 100.0F), 5), "percent", bright -> this.setHsv(c, 2, bright / 100.0F))
		);
	}

	/** A hat slider moved: one of hue, richness and brightness changes and the colour follows. */
	private void setHsv(final HorseConfig c, final int part, final float value) {
		this.hsv[part] = value;
		c.hatColor = HatColors.fromHsv(this.hsv[0], this.hsv[1], this.hsv[2]);
	}

	/** Makes the widgets again, with the list scrolled where it was (a plain rebuild jumps back to the top). */
	private void rebuildKeepingScroll() {
		//? if >=1.21.4 {
		final double scroll = this.list.scrollAmount();
		//?} else
		/*final double scroll = this.list.getScrollAmount();*/
		this.rebuildWidgets();
		this.list.setScrollAmount(scroll);
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
		show(this.minecraft, this.lastScreen);
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
