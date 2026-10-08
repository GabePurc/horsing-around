package dev.horsingaround.client.config;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.client.cosmetic.HatColors;
import dev.horsingaround.client.cosmetic.Hats;
import java.net.URI;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import dev.horsingaround.ride.HorseConfig;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
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

	@Override
	protected void addTitle() {
		final LinearLayout title = LinearLayout.horizontal().spacing(6);
		title.defaultCellSetting().alignVerticallyMiddle();
		title.addChild(ImageWidget.texture(16, 16, ICON, 16, 16));
		title.addChild(new StringWidget(this.title, this.font));
		this.layout.addToHeader(title);
	}

	@Override
	protected void addOptions() {
		final HorseConfig c = HorseConfig.get();
		this.list.addHeader(Component.translatable(KEY + "subtitle").withStyle(ChatFormatting.GRAY));

		this.list.addHeader(section("camera"));
		this.list.addBig(OptionInstance.createBoolean(KEY + "third_person", tooltip("third_person"), c.thirdPersonOnMount, v -> {
			c.thirdPersonOnMount = v;
			HorseConfig.apply();
		}));
		this.list.addSmall(
			percent("camera_distance", 50, 150, c.cameraDistance, v -> c.cameraDistance = v),
			percent("speed_zoom", 0, 100, c.speedZoom, v -> c.speedZoom = v)
		);

		this.list.addHeader(section("comfort"));
		this.list.addSmall(
			percent("view_bob", 0, 100, c.viewBob, v -> c.viewBob = v),
			percent("hand_bob", 0, 100, c.handBob, v -> c.handBob = v)
		);

		this.list.addHeader(section("sound"));
		this.list.addBig(percent("horse_sounds", 0, 100, c.horseSounds, v -> c.horseSounds = v));

		this.addHatOptions(c);
	}

	/** Supporters wear a cowboy hat and pick its colour; everyone else hears how to get one. */
	private void addHatOptions(final HorseConfig c) {
		if (!Hats.canWear()) {
			this.list.addHeader(section("hat"));
			this.list.addBig(Button.builder(Component.translatable(KEY + "hat.support"), ConfirmLinkScreen.confirmLink(this, SUPPORT, true))
				.tooltip(Tooltip.create(Component.translatable(KEY + "hat.support.tooltip"))).build());
			return;
		}
		this.list.addHeader(section("hat_supporter"));
		this.list.addBig(OptionInstance.createBoolean(KEY + "hat", tooltip("hat"), c.hat, v -> {
			c.hat = v;
			Hats.sendOwn();
		}));
		this.list.addBig(new HatPaletteWidget(310, c.hatColor, color -> {
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

	@Override
	protected void addFooter() {
		final LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable(KEY + "reset"), button -> {
			HorseConfig.reset();
			this.minecraft.gui.setScreen(new HorseSettingsScreen(this.lastScreen));
		}).width(150).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(150).build());
	}

	@Override
	public void removed() {
		super.removed();
		HorseConfig.save();
		Hats.sendOwn();
	}

	private static Component section(final String id) {
		return Component.translatable(KEY + "section." + id).withColor(SECTION_COLOR);
	}

	private static <T> OptionInstance.TooltipSupplier<T> tooltip(final String id) {
		return OptionInstance.cachedConstantTooltip(Component.translatable(KEY + id + ".tooltip"));
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
