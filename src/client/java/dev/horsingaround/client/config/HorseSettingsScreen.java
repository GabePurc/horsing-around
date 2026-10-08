package dev.horsingaround.client.config;

import dev.horsingaround.ride.HorseConfig;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Horsing Around settings, in the style of the vanilla options screens. Changes apply live. */
public final class HorseSettingsScreen extends OptionsSubScreen {
	private static final String KEY = "options.horsingaround.";

	public HorseSettingsScreen(final Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
	}

	@Override
	protected void addOptions() {
		final HorseConfig c = HorseConfig.get();
		this.list.addHeader(Component.translatable(KEY + "riding"));
		this.list.addSmall(
			percent("speed", 50, 150, c.speed, v -> c.speed = v),
			percent("acceleration", 50, 200, c.acceleration, v -> c.acceleration = v),
			percent("turn_grip", 50, 200, c.turnGrip, v -> c.turnGrip = v),
			percent("turn_response", 50, 200, c.turnResponse, v -> c.turnResponse = v),
			whole("side_angle", "degrees", 15, 90, c.sideAngle, v -> c.sideAngle = v),
			whole("across_angle", "degrees", 45, 120, c.acrossAngle, v -> c.acrossAngle = v),
			OptionInstance.createBoolean(KEY + "hard_cuts", OptionInstance.cachedConstantTooltip(Component.translatable(KEY + "hard_cuts.tooltip")),
				c.hardCuts, v -> {
					c.hardCuts = v;
					HorseConfig.apply();
				}),
			OptionInstance.createBoolean(KEY + "horse_avoids", OptionInstance.cachedConstantTooltip(Component.translatable(KEY + "horse_avoids.tooltip")),
				c.horseAvoids, v -> {
					c.horseAvoids = v;
					HorseConfig.apply();
				}),
			OptionInstance.createBoolean(KEY + "climb_ledges", OptionInstance.cachedConstantTooltip(Component.translatable(KEY + "climb_ledges.tooltip")),
				c.climbLedges, v -> {
					c.climbLedges = v;
					HorseConfig.apply();
				})
		);
		this.list.addHeader(Component.translatable(KEY + "stamina"));
		this.list.addSmall(
			whole("gallop_seconds", "seconds", 5, 120, c.gallopSeconds, v -> c.gallopSeconds = v),
			percent("recovery", 50, 300, c.recovery, v -> c.recovery = v),
			percent("jump_height", 50, 150, c.jumpHeight, v -> c.jumpHeight = v),
			percent("jump_cost", 0, 20, c.jumpCost, v -> c.jumpCost = v)
		);
		this.list.addHeader(Component.translatable(KEY + "camera"));
		this.list.addSmall(
			OptionInstance.createBoolean(KEY + "third_person", c.thirdPersonOnMount, v -> {
				c.thirdPersonOnMount = v;
				HorseConfig.apply();
			}),
			percent("camera_distance", 50, 150, c.cameraDistance, v -> c.cameraDistance = v),
			percent("speed_fov", 0, 15, c.speedFov, v -> c.speedFov = v),
			percent("view_bob", 0, 200, c.viewBob, v -> c.viewBob = v),
			percent("hand_bob", 0, 200, c.handBob, v -> c.handBob = v),
			percent("horse_lean", 0, 200, c.horseLean, v -> c.horseLean = v),
			percent("rider_lean", 0, 100, c.riderLean, v -> c.riderLean = v)
		);
		this.list.addHeader(Component.translatable(KEY + "world"));
		this.list.addSmall(
			OptionInstance.createBoolean(KEY + "leaves", OptionInstance.cachedConstantTooltip(Component.translatable(KEY + "leaves.tooltip")), c.rideThroughLeaves, v -> {
				c.rideThroughLeaves = v;
				HorseConfig.apply();
			}),
			percent("leaves_slowdown", 0, 60, c.leavesSlowdown, v -> c.leavesSlowdown = v),
			OptionInstance.createBoolean(KEY + "trample", OptionInstance.cachedConstantTooltip(Component.translatable(KEY + "trample.tooltip")), c.trample, v -> {
				c.trample = v;
				HorseConfig.apply();
			}),
			percent("trample_damage", 0, 300, c.trampleDamage, v -> c.trampleDamage = v),
			percent("horse_sounds", 0, 200, c.horseSounds, v -> c.horseSounds = v)
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
	}

	/** Slider in whole percent of a multiplier or fraction. */
	private static OptionInstance<Integer> percent(final String id, final int min, final int max, final float value, final Consumer<Float> set) {
		return new OptionInstance<>(
			KEY + id, OptionInstance.noTooltip(),
			(caption, percent) -> Options.genericValueLabel(caption, Component.translatable(KEY + "percent", percent)),
			new OptionInstance.IntRange(min, max),
			Math.round(value * 100.0F),
			percent -> {
				set.accept(percent / 100.0F);
				HorseConfig.apply();
			}
		);
	}

	/** Slider in whole units (degrees, seconds). */
	private static OptionInstance<Integer> whole(final String id, final String unit, final int min, final int max, final float value, final Consumer<Float> set) {
		return new OptionInstance<>(
			KEY + id, OptionInstance.noTooltip(),
			(caption, amount) -> Options.genericValueLabel(caption, Component.translatable(KEY + unit, amount)),
			new OptionInstance.IntRange(min, max),
			Math.round(value),
			amount -> {
				set.accept((float) amount);
				HorseConfig.apply();
			}
		);
	}
}
