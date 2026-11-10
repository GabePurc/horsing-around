package dev.horsingaround.client.config;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.client.cosmetic.HatColors;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.client.cosmetic.Supporters;
import java.net.URI;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if >=1.20.5
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import dev.horsingaround.net.ServerRules;
import dev.horsingaround.ride.HorseConfig;
import dev.horsingaround.ride.IcyCompat;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
//? if >=1.20.5
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Horsing Around settings: only how riding looks and sounds to you (how horses ride is the same for everyone), a tack
 * room at sunset, in tabs by what a player comes looking for. A tooltip on every option, and changes applied live.
 */
public final class HorseSettingsScreen extends OptionsSubScreen {
	/** What a player comes for. The compatibility tab only shows with a mod it has settings for. */
	public enum Tab {
		CAMERA, COMFORT, HAT, COMPATIBILITY;

		private boolean shown() {
			return this != COMPATIBILITY || IcyCompat.LOADED;
		}

		private Component label() {
			return Component.translatable(KEY + "tab." + this.name().toLowerCase(Locale.ROOT));
		}
	}

	private static final String KEY = "options.horsingaround.";
	private static final Identifier ICON = Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "icon.png");
	private static final URI SUPPORT = URI.create("https://buymeacoffee.com/blintzbug");
	private static final int WIDE = 310;
	private static final int SMALL = 150;
	/** The whole of the wooden frame, and most of it the tabs may take. */
	private static final int PANEL_WIDTH = 350;
	private static final int TABS_WIDTH = 330;
	private static final int TAB_GAP = 4;
	private static final int FRAME_MARGIN = 4;
	/** The frame's side posts where its boards are left out. */
	private static final int POST = 3;
	/** The title beam, then the tabs on their strap, which ends where the options start. */
	private static final int BEAM_BOTTOM = 30;
	private static final int TABS_BOTTOM = 56;
	private static final int HEADER_HEIGHT = TABS_BOTTOM + Tack.RAIL;
	private static final int FOOTER_HEIGHT = 33;

	/** The tabs showing, in order. */
	private final Map<Tab, Tack.TabTag> tabTags = new EnumMap<>(Tab.class);
	private Tab tab;
	/**
	 * The hat colour as the sliders show it: hue (degrees), richness and brightness (0-1). Kept here and only turned into
	 * the colour (never back), so a slider at its end (grey has no hue) doesn't lose the others, and rounding to 8-bit
	 * channels doesn't drift them.
	 */
	private float[] hsv;

	public HorseSettingsScreen(final Screen parent) {
		this(parent, Tab.CAMERA);
	}

	public HorseSettingsScreen(final Screen parent, final Tab tab) {
		super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
		this.tab = tab;
		this.hsv = HatColors.toHsv(HorseConfig.get().hatColor);
		Supporters.refreshSoon();
	}

	/** Opens the settings over whatever is showing now (the key binding). */
	public static void open(final Minecraft minecraft) {
		open(minecraft, Tab.CAMERA);
	}

	public static void open(final Minecraft minecraft, final Tab tab) {
		//? if >=26.2 {
		show(minecraft, minecraft.gui.screen(), tab);
		//?} else
		/*show(minecraft, minecraft.screen, tab);*/
	}

	private static void show(final Minecraft minecraft, final Screen parent, final Tab tab) {
		//? if >=26.2 {
		minecraft.gui.setScreen(new HorseSettingsScreen(parent, tab));
		//?} else
		/*minecraft.setScreen(new HorseSettingsScreen(parent, tab));*/
	}

	//? if >=1.20.5 {
	/** The options list, on the frame's boards rather than the game's own list background and separators. */
	private static final class TackList extends OptionsList {
		//? if >=1.21 {
		TackList(final Minecraft minecraft, final int width, final OptionsSubScreen screen) {
			super(minecraft, width, screen);
		}
		//?} else {
		/*TackList(final Minecraft minecraft, final int width, final int height, final OptionsSubScreen screen) {
			super(minecraft, width, height, screen);
		}
		*///?}

		void clear() {
			this.clearEntries();
		}

		@Override
		protected void extractListBackground(final GuiGraphicsExtractor graphics) {
		}

		@Override
		protected void extractListSeparators(final GuiGraphicsExtractor graphics) {
		}
	}
	//?}

	//? if <1.20.5 {
	/*// Before 1.20.5 the options screens make their own list and place everything by hand (the base class has neither a
	// layout nor hooks for them).
	private LegacyOptionsList list;

	@Override
	protected void init() {
		this.addTabs();
		this.list = this.addRenderableWidget(new LegacyOptionsList(this.minecraft, this.width, this.height - HEADER_HEIGHT - FOOTER_HEIGHT, HEADER_HEIGHT));
		this.addOptions();
		this.addFooter();
		this.placeTabs();
	}
	*///?}
	//? if <1.20.2 {
	/*@Override
	public void render(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		if (!this.showsWorld()) {
			this.extractBackground(graphics);
		}
		this.drawFrame(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
	}
	*///?}
	//? if >=1.20.5 && <1.21 {
	/*// Before 1.21 the options screens put their lists in themselves (and the base class has no hook for it).
	private OptionsList list;

	@Override
	protected void init() {
		this.list = this.addRenderableWidget(new TackList(this.minecraft, this.width, this.height, this));
		this.addOptions();
		super.init();
	}
	*///?}

	//? if >=1.20.5 {
	@Override
	protected void addTitle() {
		// (The title itself goes on the beam, with the frame.)
		this.layout.setHeaderHeight(HEADER_HEIGHT);
		this.addTabs();
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();
		//? if >=1.20.5 && <1.21
		/*this.list.updateSize(this.width, this.layout);*/
		this.placeTabs();
	}
	//?}

	//? if >=1.21 {
	@Override
	protected void addContents() {
		this.list = this.layout.addToContents(new TackList(this.minecraft, this.width, this));
		this.addOptions();
	}
	//?}

	private void addTabs() {
		this.tabTags.clear();
		for (final Tab each : Tab.values()) {
			if (each.shown()) {
				this.tabTags.put(each, this.addRenderableWidget(new Tack.TabTag(each.label(), each == this.tab, () -> this.select(each))));
			}
		}
	}

	private void placeTabs() {
		final int total = Math.min(TABS_WIDTH, this.width - 2 * FRAME_MARGIN);
		final int each = (total - (this.tabTags.size() - 1) * TAB_GAP) / this.tabTags.size();
		int x = (this.width - total) / 2;
		for (final Tack.TabTag tag : this.tabTags.values()) {
			tag.setWidth(each);
			tag.setX(x);
			tag.setY(TABS_BOTTOM - tag.getHeight());
			x += each + TAB_GAP;
		}
	}

	/** Shows another tab: its options replace the list's, and the tabs and footer stay as they are. */
	private void select(final Tab chosen) {
		this.tab = chosen;
		this.tabTags.forEach((each, tag) -> tag.selected = each == chosen);
		this.refill();
		this.list.setScrollAmount(0.0);
	}

	private void refill() {
		//? if >=1.20.5 {
		((TackList) this.list).clear();
		//?} else
		/*this.list.clear();*/
		this.addOptions();
	}

	//? if >=1.20.2 {
	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		if (!this.showsWorld()) {
			super.extractBackground(graphics, mouseX, mouseY, a);
		}
		this.drawFrame(graphics);
	}
	//?}

	/**
	 * Whether the world shows behind the options, sharp and undimmed, with the frame's boards left out there: on the camera
	 * tab in a world, so each change shows on the camera as it's made (user, 2026-11-10).
	 */
	private boolean showsWorld() {
		return this.tab == Tab.CAMERA && Minecraft.getInstance().level != null;
	}

	/** The wooden frame: a beam with the title, boards behind the tabs and options, a board for the footer. */
	private void drawFrame(final GuiGraphicsExtractor graphics) {
		final int width = Math.min(PANEL_WIDTH, this.width);
		final int left = (this.width - width) / 2;
		final int footer = this.height - FOOTER_HEIGHT;
		final int bottom = this.height - FRAME_MARGIN;
		final boolean open = this.showsWorld();
		if (open) {
			// Open between the tabs' strap and the footer, with a post down each side.
			graphics.fill(left - 1, FRAME_MARGIN - 1, left + width + 1, HEADER_HEIGHT, Tack.EDGE);
			graphics.fill(left - 1, footer, left + width + 1, bottom + 1, Tack.EDGE);
			post(graphics, left, HEADER_HEIGHT, footer);
			post(graphics, left + width - POST, HEADER_HEIGHT, footer);
		} else {
			graphics.fill(left - 1, FRAME_MARGIN - 1, left + width + 1, bottom + 1, Tack.EDGE);
		}
		Tack.boards(graphics, Tack.DEEP, left, BEAM_BOTTOM, width, (open ? TABS_BOTTOM : footer) - BEAM_BOTTOM);
		Tack.boards(graphics, Tack.WOOD, left, FRAME_MARGIN, width, BEAM_BOTTOM - FRAME_MARGIN);
		graphics.fill(left, FRAME_MARGIN, left + width, FRAME_MARGIN + 1, Tack.LIGHT_WOOD_EDGE);
		graphics.fill(left, BEAM_BOTTOM - 1, left + width, BEAM_BOTTOM, Tack.EDGE);
		Tack.boards(graphics, Tack.WOOD, left, footer, width, bottom - footer);
		graphics.fill(left, footer, left + width, footer + 1, Tack.EDGE);
		graphics.fill(left, footer + 1, left + width, footer + 2, Tack.LIGHT_WOOD_EDGE);
		Tack.rail(graphics, left, TABS_BOTTOM, width);
		final int rivet = (FRAME_MARGIN + BEAM_BOTTOM) / 2 - 2;
		Tack.stud(graphics, left + 5, rivet, 4);
		Tack.stud(graphics, left + width - 9, rivet, 4);
		final int titleLeft = (this.width - 21 - this.font.width(this.title)) / 2;
		Tack.image(graphics, ICON, titleLeft, FRAME_MARGIN + 5, 0.0F, 0.0F, 16, 16, 16, 16);
		Tack.text(graphics, this.font, this.title, titleLeft + 21, FRAME_MARGIN + 9, 0xFFFFF6E6);
	}

	private static void post(final GuiGraphicsExtractor graphics, final int x, final int top, final int bottom) {
		graphics.fill(x - 1, top, x + POST + 1, bottom, Tack.EDGE);
		Tack.boards(graphics, Tack.WOOD, x, top, POST, bottom - top);
	}

	//? if >=1.21 {
	@Override
	//?}
	protected void addOptions() {
		final HorseConfig c = HorseConfig.get();
		switch (this.tab) {
			case CAMERA -> this.addCamera(c);
			case COMFORT -> this.addComfort(c);
			case HAT -> this.addHat(c);
			case COMPATIBILITY -> this.addCompatibility(c);
		}
	}

	private void addCamera(final HorseConfig c) {
		this.addHeading("mounting");
		this.addWide(toggle("third_person", () -> c.thirdPersonOnMount, v -> {
			c.thirdPersonOnMount = v;
			HorseConfig.apply();
		}));
		this.addHeading("camera_position");
		this.addWide(new Tack.Patch(WIDE, () -> value("shoulder", Component.translatable(KEY + "shoulder." + c.shoulder.name().toLowerCase(Locale.ROOT))), tooltip("shoulder"), false, () -> {
			final HorseConfig.Shoulder[] all = HorseConfig.Shoulder.values();
			c.shoulder = all[(c.shoulder.ordinal() + 1) % all.length];
			HorseConfig.apply();
		}));
		this.list.addSmall(
			percent("camera_distance", SMALL, 50, 150, c.cameraDistance, v -> c.cameraDistance = v),
			blocks("camera_height", SMALL, HorseConfig.CAMERA_HEIGHT_MIN, HorseConfig.CAMERA_HEIGHT_MAX, c.cameraHeight, v -> c.cameraHeight = v)
		);
	}

	private void addComfort(final HorseConfig c) {
		this.addHeading("motion");
		this.addWide(percent("speed_zoom", WIDE, 0, 100, c.speedZoom, v -> c.speedZoom = v));
		this.list.addSmall(
			percent("view_bob", SMALL, 0, 100, c.viewBob, v -> c.viewBob = v),
			percent("hand_bob", SMALL, 0, 100, c.handBob, v -> c.handBob = v)
		);
		this.addHeading("sound");
		this.addWide(percent("horse_sounds", WIDE, 0, 100, c.horseSounds, v -> c.horseSounds = v));
	}

	/**
	 * Whose riding horses use with Icy's Better Horses installed too. The host's choice: set here for one's own worlds
	 * (singleplayer and LAN, live in the open one); on someone else's server it shows that server's, greyed out.
	 */
	private void addCompatibility(final HorseConfig c) {
		this.addHeading("icys");
		if (this.minecraft.level != null && !this.minecraft.hasSingleplayerServer()) {
			final HorseConfig.RidingStyle style = ServerRules.icyRides ? HorseConfig.RidingStyle.ICYS : HorseConfig.RidingStyle.HORSING_AROUND;
			final Tack.Patch shown = new Tack.Patch(WIDE, () -> value("riding_style", ridingStyle(style)), Component.translatable(KEY + "riding_style.server.tooltip"), false, () -> {});
			shown.active = false;
			this.addWide(shown);
			return;
		}
		this.addWide(new Tack.Patch(WIDE, () -> value("riding_style", ridingStyle(c.ridingStyle)), tooltip("riding_style"), false, () -> {
			final HorseConfig.RidingStyle[] all = HorseConfig.RidingStyle.values();
			c.ridingStyle = all[(c.ridingStyle.ordinal() + 1) % all.length];
			final MinecraftServer server = this.minecraft.getSingleplayerServer();
			if (server != null) {
				server.execute(() -> HorsingAround.styleChanged(server));
			}
		}));
	}

	private static Component ridingStyle(final HorseConfig.RidingStyle style) {
		return Component.translatable(KEY + "riding_style." + style.name().toLowerCase(Locale.ROOT));
	}

	/** Supporters wear a cowboy hat and pick its colour; everyone else hears how to get one. */
	private void addHat(final HorseConfig c) {
		if (!Hats.canWear()) {
			this.addHeading("hat");
			this.addNote(Component.translatable(KEY + "hat.support.note"));
			//? if >=1.21 {
			final Button.OnPress open = ConfirmLinkScreen.confirmLink(this, SUPPORT, true);
			//?} elif >=1.20.3 {
			/*final Button.OnPress open = ConfirmLinkScreen.confirmLink(this, SUPPORT.toString());
			*///?} else
			/*final Button.OnPress open = ConfirmLinkScreen.confirmLink(SUPPORT.toString(), this, true);*/
			// (The game's handler never looks at which button was pressed.)
			this.addWide(new Tack.Patch(WIDE, () -> Component.translatable(KEY + "hat.support"), Component.translatable(KEY + "hat.support.tooltip"), true, () -> open.onPress(null)));
			return;
		}
		this.addHeading("hat_supporter");
		this.addWide(toggle("hat", () -> c.hat, v -> {
			c.hat = v;
			Hats.sendOwn();
		}));
		this.addWide(new HatPaletteWidget(WIDE, () -> c.hatColor, color -> {
			c.hatColor = color;
			this.hsv = HatColors.toHsv(color);
			Hats.sendOwn();
			// The sliders have to show the new colour, so the rows are made again, but the list stays where it is.
			this.refillKeepingScroll();
		}));
		this.list.addSmall(
			slider("hat_hue", SMALL, 0, 359, Math.min(Math.round(this.hsv[0]), 359), "degrees", hue -> this.setHsv(c, 0, hue)),
			slider("hat_richness", SMALL, 0, 100, Math.round(this.hsv[1] * 100.0F), "percent", rich -> this.setHsv(c, 1, rich / 100.0F))
		);
		// (Beside the last slider, the colour they make: the hat itself may be out of sight.)
		this.list.addSmall(
			slider("hat_brightness", SMALL, 5, 100, Math.max(Math.round(this.hsv[2] * 100.0F), 5), "percent", bright -> this.setHsv(c, 2, bright / 100.0F)),
			new Tack.Swatch(SMALL, Component.translatable(KEY + "hat_color"), () -> c.hatColor)
		);
	}

	/** A hat slider moved: one of hue, richness and brightness changes and the colour follows. */
	private void setHsv(final HorseConfig c, final int part, final float value) {
		this.hsv[part] = value;
		c.hatColor = HatColors.fromHsv(this.hsv[0], this.hsv[1], this.hsv[2]);
	}

	private void refillKeepingScroll() {
		//? if >=1.21.4 {
		final double scroll = this.list.scrollAmount();
		//?} else
		/*final double scroll = this.list.getScrollAmount();*/
		this.refill();
		this.list.setScrollAmount(scroll);
	}

	private void addHeading(final String section) {
		this.addWide(new Tack.Heading(WIDE, Component.translatable(KEY + "section." + section), this.showsWorld()));
	}

	/** A few lines of words, with empty rows after them for the lines that run past the first row. */
	private void addNote(final Component text) {
		final Tack.Note note = new Tack.Note(WIDE, text);
		this.addWide(note);
		for (int row = 1; row < note.rows(); row++) {
			this.addWide(new Tack.Note(WIDE, CommonComponents.EMPTY));
		}
	}

	/** A full-width widget in its own row (before 26.2 the list only took options as big rows, so it is a lone small one). */
	private void addWide(final AbstractWidget widget) {
		//? if >=26.2 {
		this.list.addBig(widget);
		//?} else {
		/*widget.setWidth(WIDE);
		this.list.addSmall(widget, null);
		*///?}
	}

	/** A hat colour slider: changes the colour live (sent to the server when the screen closes). */
	private static Tack.Strap slider(final String id, final int width, final int min, final int max, final int value, final String unit, final IntConsumer set) {
		return new Tack.Strap(width, min, max, value, amount -> value(id, Component.translatable(KEY + unit, amount)), tooltip(id), set);
	}

	//? if >=1.20.5 {
	@Override
	protected void addFooter() {
		final LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(new Tack.Patch(SMALL, () -> Component.translatable(KEY + "reset"), null, false, this::reset));
		footer.addChild(new Tack.Patch(SMALL, () -> CommonComponents.GUI_DONE, null, true, this::onClose));
	}
	//?} else {
	/*private void addFooter() {
		final int top = this.height - 27;
		final Tack.Patch reset = new Tack.Patch(SMALL, () -> Component.translatable(KEY + "reset"), null, false, this::reset);
		reset.setX(this.width / 2 - 154);
		reset.setY(top);
		this.addRenderableWidget(reset);
		final Tack.Patch done = new Tack.Patch(SMALL, () -> CommonComponents.GUI_DONE, null, true, this::onClose);
		done.setX(this.width / 2 + 4);
		done.setY(top);
		this.addRenderableWidget(done);
	}
	*///?}

	private void reset() {
		HorseConfig.reset();
		show(this.minecraft, this.lastScreen, this.tab);
	}

	@Override
	public void removed() {
		super.removed();
		HorseConfig.save();
		Hats.sendOwn();
	}

	private static Component tooltip(final String id) {
		return Component.translatable(KEY + id + ".tooltip");
	}

	/** "Caption: value", as the game's own options read. */
	private static Component value(final String id, final Component value) {
		return Options.genericValueLabel(Component.translatable(KEY + id), value);
	}

	private static Tack.Patch toggle(final String id, final java.util.function.BooleanSupplier get, final Consumer<Boolean> set) {
		return new Tack.Patch(WIDE, () -> value(id, get.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF), tooltip(id), false, () -> set.accept(!get.getAsBoolean()));
	}

	/** Slider in hundredths of a block, shown as "0.2 blocks". */
	private static Tack.Strap blocks(final String id, final int width, final float min, final float max, final float value, final Consumer<Float> set) {
		return new Tack.Strap(width, Math.round(min * 100.0F), Math.round(max * 100.0F), Math.round(value * 100.0F),
			hundredths -> value(id, Component.translatable(KEY + "blocks", trim(hundredths / 100.0F))), tooltip(id), hundredths -> {
				set.accept(hundredths / 100.0F);
				HorseConfig.apply();
			});
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
	private static Tack.Strap percent(final String id, final int width, final int min, final int max, final float value, final Consumer<Float> set) {
		return new Tack.Strap(width, min, max, Math.round(value * 100.0F),
			percent -> value(id, percent == 0 ? CommonComponents.OPTION_OFF : Component.translatable(KEY + "percent", percent)), tooltip(id), percent -> {
				set.accept(percent / 100.0F);
				HorseConfig.apply();
			});
	}
}
