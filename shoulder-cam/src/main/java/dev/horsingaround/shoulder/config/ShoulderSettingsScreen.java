package dev.horsingaround.shoulder.config;

import dev.horsingaround.shoulder.ShoulderCamClient;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

/** Settings for the over-the-shoulder camera, in the style of the vanilla options screens. Changes apply live. */
public final class ShoulderSettingsScreen extends OptionsSubScreen {
	private static final String KEY = "options.horsingaround_shoulder.";

	public ShoulderSettingsScreen(final Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
	}

	@Override
	protected void addOptions() {
		final ShoulderConfig c = ShoulderConfig.get();
		this.list.addSmall(
			OptionInstance.createBoolean(KEY + "enabled", c.enabled, value -> ShoulderCamClient.setEnabled(value)),
			OptionInstance.createBoolean(
				KEY + "shoulder", OptionInstance.noTooltip(),
				(caption, left) -> Component.translatable(KEY + (left ? "left" : "right")),
				c.leftShoulder, value -> c.leftShoulder = value
			)
		);
		this.list.addHeader(Component.translatable(KEY + "on_foot"));
		this.list.addSmall(
			blocks("foot_distance", 1.0F, 6.0F, c.footDistance, v -> c.footDistance = v),
			blocks("foot_side", 0.0F, 1.5F, c.footSide, v -> c.footSide = v),
			blocks("foot_height", -0.5F, 1.0F, c.footHeight, v -> c.footHeight = v),
			perSecond("height_follow", 5.0F, 60.0F, c.heightFollow, v -> c.heightFollow = v)
		);
		this.list.addHeader(Component.translatable(KEY + "aiming"));
		this.list.addSmall(
			blocks("aim_distance", 0.8F, 4.0F, c.aimDistance, v -> c.aimDistance = v),
			blocks("aim_side", 0.0F, 1.2F, c.aimSide, v -> c.aimSide = v)
		);
		this.list.addHeader(Component.translatable(KEY + "riding"));
		this.list.addSmall(
			percent("ride_distance", 0.5F, 1.5F, c.rideDistanceScale, v -> c.rideDistanceScale = v),
			blocks("ride_side", 0.0F, 1.5F, c.rideSide, v -> c.rideSide = v),
			blocks("ride_height", 0.0F, 2.0F, c.rideHeight, v -> c.rideHeight = v),
			percent("ride_bounce", 0.0F, 0.5F, c.rideBounce, v -> c.rideBounce = v)
		);
		this.list.addHeader(Component.translatable(KEY + "motion"));
		this.list.addSmall(perSecond("transition_speed", 1.0F, 20.0F, c.transitionSpeed, v -> c.transitionSpeed = v), null);
	}

	@Override
	protected void addFooter() {
		final LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable(KEY + "reset"), button -> {
			ShoulderConfig.reset();
			ShoulderCamClient.setEnabled(ShoulderConfig.get().enabled);
			this.minecraft.gui.setScreen(new ShoulderSettingsScreen(this.lastScreen));
		}).width(150).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(150).build());
	}

	@Override
	public void removed() {
		super.removed();
		ShoulderConfig.save();
	}

	/** Slider in hundredths of a block. */
	private static OptionInstance<Integer> blocks(final String id, final float min, final float max, final float value, final Consumer<Float> apply) {
		return new OptionInstance<>(
			KEY + id, OptionInstance.noTooltip(),
			(caption, hundredths) -> Options.genericValueLabel(caption, Component.literal(String.format("%.2f", hundredths / 100.0F))),
			new OptionInstance.IntRange(Math.round(min * 100.0F), Math.round(max * 100.0F)),
			Math.round(value * 100.0F),
			hundredths -> apply.accept(hundredths / 100.0F)
		);
	}

	/** Slider in whole percent. */
	private static OptionInstance<Integer> percent(final String id, final float min, final float max, final float value, final Consumer<Float> apply) {
		return new OptionInstance<>(
			KEY + id, OptionInstance.noTooltip(),
			(caption, percent) -> Options.genericValueLabel(caption, Component.translatable(KEY + "percent", percent)),
			new OptionInstance.IntRange(Math.round(min * 100.0F), Math.round(max * 100.0F)),
			Math.round(value * 100.0F),
			percent -> apply.accept(percent / 100.0F)
		);
	}

	/** Slider for a follow rate; shown as how snappy it is. */
	private static OptionInstance<Integer> perSecond(final String id, final float min, final float max, final float value, final Consumer<Float> apply) {
		return new OptionInstance<>(
			KEY + id, OptionInstance.noTooltip(),
			(caption, rate) -> Options.genericValueLabel(caption, rate),
			new OptionInstance.IntRange(Math.round(min), Math.round(max)),
			Math.round(value),
			rate -> apply.accept((float) rate)
		);
	}
}
