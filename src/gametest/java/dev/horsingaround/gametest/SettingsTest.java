package dev.horsingaround.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.horsingaround.client.config.HorseSettingsScreen;
import dev.horsingaround.client.cosmetic.HatColors;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.client.cosmetic.Supporters;
import dev.horsingaround.gametest.harness.ClientGameTest;
import dev.horsingaround.gametest.harness.context.ClientGameTestContext;
import dev.horsingaround.gametest.harness.context.TestSingleplayerContext;
import dev.horsingaround.ride.HorseConfig;
import dev.horsingaround.ride.IcyCompat;
import dev.horsingaround.ride.RideTuning;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The settings screen, used as a player would with real mouse clicks: every tab opens and shows its own options, the
 * controls change the settings (and the reset puts them back), the hat tab picks colours for a supporter, and nothing
 * overlaps or leaves the window at the smallest window the game allows or on a big one. Each tab is photographed in a
 * world. Writes {@code horsingaround-settings-report.txt}. Run with {@code ./gradlew runClientGameTest -Ptests=settings}.
 */
public final class SettingsTest implements ClientGameTest {
	private static final String KEY = "options.horsingaround.";
	private static final String REPORT = "horsingaround-settings-report.txt";
	/** The test games' window, the smallest the game lays a screen out for (320 by 240 at its scale of 2), and a 1080p one. */
	private static final int[] WINDOW = {854, 480};
	private static final int[][] OTHER_WINDOWS = {{640, 480}, {1920, 1080}};

	private final List<String> report = new ArrayList<>();
	private int failures;

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!System.getProperty("horsingaround.tests", "settings").contains("settings")) {
			return;
		}
		TestPacks.enable(ctx);
		boolean completed = false;
		try (TestSingleplayerContext world = ctx.worldBuilder()
			.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
			.create()) {
			world.getServer().runCommand("time set 12800");
			world.getConnection().waitForChunksRender();
			ctx.runOnClient(mc -> HorseConfig.reset());
			final UUID me = ctx.computeOnClient(mc -> mc.player.getUUID());

			this.tabs(ctx, "");
			this.controls(ctx);
			this.hat(ctx, me);
			for (final int[] window : OTHER_WINDOWS) {
				ctx.getInput().resizeWindow(window[0], window[1]);
				ctx.waitTicks(3);
				this.tabs(ctx, "_" + window[0] + "x" + window[1]);
			}
			completed = true;
		} finally {
			ctx.getInput().resizeWindow(WINDOW[0], WINDOW[1]);
			ctx.runOnClient(mc -> {
				HorseConfig.reset();
				HorseConfig.save();
			});
			if (!completed) {
				this.failures++;
				this.report.add("INCOMPLETE: the run stopped early (see the log for the exception)");
			}
			this.report.add(this.failures + " failed checks");
			try {
				Files.write(TestEnv.gameDir().resolve(REPORT), this.report);
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		TestSummary.failed(this.failures, REPORT);
	}

	/** Every tab, opened by clicking it: its options are there, laid out clear of each other, and it is photographed. */
	private void tabs(final ClientGameTestContext ctx, final String shot) {
		final String size = shot.isEmpty() ? "" : " (" + shot.substring(1) + " window)";
		ctx.setScreen(() -> new HorseSettingsScreen(null));
		ctx.waitTicks(3);
		this.shows(ctx, "camera" + size, "shoulder", "camera_distance", "camera_height", "third_person");
		this.laidOut(ctx, "camera" + size);
		this.shoot(ctx, "camera" + shot);

		this.click(ctx, KEY + "tab.comfort");
		this.shows(ctx, "comfort" + size, "speed_zoom", "view_bob", "hand_bob", "horse_sounds");
		this.laidOut(ctx, "comfort" + size);
		this.shoot(ctx, "comfort" + shot);

		this.click(ctx, KEY + "tab.hat");
		this.shows(ctx, "hat, not a supporter" + size, "hat.support.note", "hat.support");
		this.laidOut(ctx, "hat, not a supporter" + size);
		this.shoot(ctx, "hat_not_supporter" + shot);

		if (IcyCompat.LOADED) {
			this.click(ctx, KEY + "tab.compatibility");
			this.shows(ctx, "compatibility" + size, "riding_style");
			this.laidOut(ctx, "compatibility" + size);
			this.shoot(ctx, "compatibility" + shot);
		}
		ctx.setScreen(() -> null);
	}

	/** The controls do what they say: a toggle, a value to cycle, a slider, the reset and Done. */
	private void controls(final ClientGameTestContext ctx) {
		ctx.setScreen(() -> new HorseSettingsScreen(null));
		ctx.waitTicks(3);
		this.click(ctx, KEY + "third_person");
		this.check("clicking the mounting toggle turns it off", ctx.computeOnClient(mc -> !HorseConfig.get().thirdPersonOnMount));
		this.check("the toggle reads Off", this.label(ctx, "third_person").endsWith(CommonComponents.OPTION_OFF.getString()));
		this.click(ctx, KEY + "shoulder");
		this.check("clicking the shoulder moves the camera to the left one", ctx.computeOnClient(mc -> HorseConfig.get().shoulder == HorseConfig.Shoulder.LEFT));
		this.click(ctx, KEY + "camera_distance", 0.9);
		final float distance = ctx.computeOnClient(mc -> HorseConfig.get().cameraDistance);
		this.check("clicking far along the distance slider moves the camera back (" + distance + ")", distance > 1.3F && distance <= 1.5F);
		this.check("the slider applies as it moves (riding camera distance scale)",
			Math.abs(ctx.computeOnClient(mc -> RideTuning.CAMERA_DISTANCE_SCALE) - distance) < 0.011);
		this.check("the slider's label follows it", this.label(ctx, "camera_distance").contains(Math.round(distance * 100.0F) + "%"));

		this.click(ctx, KEY + "tab.comfort");
		this.click(ctx, KEY + "speed_zoom", 0.01);
		this.check("a slider at its low end reads Off", this.label(ctx, "speed_zoom").endsWith(CommonComponents.OPTION_OFF.getString()));
		this.click(ctx, KEY + "reset");
		ctx.waitTicks(2);
		this.check("the reset puts every setting back", ctx.computeOnClient(mc -> {
			final HorseConfig c = HorseConfig.get();
			return c.thirdPersonOnMount && c.shoulder == HorseConfig.Shoulder.RIGHT && c.cameraDistance == 1.0F && c.speedZoom == 1.0F;
		}));
		this.shows(ctx, "comfort, after the reset", "speed_zoom", "horse_sounds");
		this.click(ctx, CommonComponents.GUI_DONE.getString());
		ctx.waitTicks(2);
		this.check("Done closes the settings", ctx.computeOnClient(mc -> mc.gui.screen() == null));
	}

	/** A supporter's hat tab: the palette and the sliders both set the colour, and each shows what the other chose. */
	private void hat(final ClientGameTestContext ctx, final UUID me) {
		ctx.runOnClient(mc -> {
			Supporters.addForTesting(me);
			Hats.sendOwn();
		});
		try {
			ctx.setScreen(() -> new HorseSettingsScreen(null, HorseSettingsScreen.Tab.HAT));
			ctx.waitTicks(3);
			this.shows(ctx, "hat, a supporter", "hat", "hat_palette", "hat_hue", "hat_richness", "hat_brightness", "hat_color");
			this.laidOut(ctx, "hat, a supporter");
			this.shoot(ctx, "hat_supporter");

			final int red = 8;
			this.click(ctx, KEY + "hat_palette", ctx.computeOnClient(mc -> {
				// The middle of the ninth swatch (HatPaletteWidget: a pixel of edge, then 18-pixel swatches evenly spaced).
				final AbstractWidget palette = find(mc, Component.translatable(KEY + "hat_palette").getString());
				final int count = HatColors.PALETTE.length;
				final int gap = (palette.getWidth() - 2 - count * 18) / (count - 1);
				return (1 + red * (18 + gap) + 9) / (double) palette.getWidth();
			}));
			this.check("clicking a palette swatch makes the hat that colour", ctx.computeOnClient(mc -> HorseConfig.get().hatColor == HatColors.PALETTE[red]));
			final String hue = Math.round(HatColors.toHsv(HatColors.PALETTE[red])[0]) + "°";
			this.check("the hue slider shows the swatch's hue (" + hue + ")", this.label(ctx, "hat_hue").endsWith(hue));
			this.click(ctx, KEY + "hat_richness", 0.01);
			final int grey = ctx.computeOnClient(mc -> HorseConfig.get().hatColor);
			this.check("the richness slider at its low end makes the hat grey (#" + Integer.toHexString(grey) + ")",
				Math.abs((grey >> 16 & 0xFF) - (grey & 0xFF)) <= 6 && Math.abs((grey >> 8 & 0xFF) - (grey & 0xFF)) <= 6);
			this.shoot(ctx, "hat_grey");
			this.click(ctx, KEY + "hat");
			this.check("clicking the hat toggle takes the hat off", ctx.computeOnClient(mc -> !HorseConfig.get().hat));
			ctx.setScreen(() -> null);
		} finally {
			ctx.runOnClient(mc -> {
				Supporters.removeForTesting(me);
				HorseConfig.reset();
				Hats.sendOwn();
			});
		}
	}

	/** A screenshot with the cursor out of the way, so no tooltip or hover is in it. */
	private void shoot(final ClientGameTestContext ctx, final String name) {
		ctx.getInput().setCursorPos(1.0, 1.0);
		ctx.waitTicks(2);
		ctx.takeScreenshot("horsingaround_settings_" + name);
	}

	private void check(final String what, final boolean passed) {
		this.report.add((passed ? "PASS " : "FAIL ") + what);
		if (!passed) {
			this.failures++;
		}
	}

	private void click(final ClientGameTestContext ctx, final String label) {
		this.click(ctx, label, 0.5);
	}

	/**
	 * A real click on the widget whose label starts with this translation key's text (or with these words), this far
	 * along it.
	 */
	private void click(final ClientGameTestContext ctx, final String label, final double along) {
		final String words = Component.translatable(label).getString();
		final double[] at = ctx.computeOnClient(mc -> {
			final AbstractWidget widget = find(mc, words);
			if (widget == null) {
				throw new AssertionError("Nothing to click for \"" + words + "\"");
			}
			final double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
			return new double[] {(widget.getX() + widget.getWidth() * along) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		ctx.getInput().setCursorPos(at[0], at[1]);
		ctx.waitTicks(1);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
	}

	/** What the widget for this option reads now. */
	private String label(final ClientGameTestContext ctx, final String id) {
		final String caption = Component.translatable(KEY + id).getString();
		return ctx.computeOnClient(mc -> {
			final AbstractWidget widget = find(mc, caption);
			return widget == null ? "" : widget.getMessage().getString();
		});
	}

	/** Each label is on a widget of the tab showing now. */
	private void shows(final ClientGameTestContext ctx, final String tab, final String... ids) {
		for (final String id : ids) {
			final String label = Component.translatable(KEY + id).getString();
			this.check("the " + tab + " tab shows \"" + label + "\"", ctx.computeOnClient(mc -> find(mc, label) != null));
		}
	}

	/** Every widget of the screen is inside the window, and no two of them overlap. */
	private void laidOut(final ClientGameTestContext ctx, final String tab) {
		final String wrong = ctx.computeOnClient(mc -> {
			final List<AbstractWidget> widgets = new ArrayList<>();
			collect(mc.gui.screen().children(), widgets);
			final int width = mc.getWindow().getGuiScaledWidth();
			final int height = mc.getWindow().getGuiScaledHeight();
			final Function<AbstractWidget, String> name = w -> "\"" + w.getMessage().getString() + "\"";
			for (int i = 0; i < widgets.size(); i++) {
				final AbstractWidget a = widgets.get(i);
				if (a.getX() < 0 || a.getY() < 0 || a.getX() + a.getWidth() > width || a.getY() + a.getHeight() > height) {
					return name.apply(a) + " leaves the " + width + " by " + height + " screen";
				}
				for (int j = i + 1; j < widgets.size(); j++) {
					final AbstractWidget b = widgets.get(j);
					if (a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth()
						&& a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight()) {
						return name.apply(a) + " overlaps " + name.apply(b);
					}
				}
			}
			return widgets.size() < 4 ? "only " + widgets.size() + " widgets found" : "";
		});
		this.check("the " + tab + " tab is laid out clear" + (wrong.isEmpty() ? "" : ": " + wrong), wrong.isEmpty());
	}

	/** The widgets themselves, not the lists and rows that hold them. */
	private static void collect(final List<? extends GuiEventListener> children, final List<AbstractWidget> into) {
		for (final GuiEventListener child : children) {
			if (child instanceof ContainerEventHandler container) {
				collect(container.children(), into);
			} else if (child instanceof AbstractWidget widget) {
				into.add(widget);
			}
		}
	}

	private static @Nullable AbstractWidget find(final Minecraft mc, final String label) {
		return mc.gui.screen() == null ? null : find(mc.gui.screen().children(), label);
	}

	private static @Nullable AbstractWidget find(final List<? extends GuiEventListener> children, final String label) {
		for (final GuiEventListener child : children) {
			if (child instanceof AbstractWidget w && w.getMessage().getString().startsWith(label)) {
				return w;
			}
			if (child instanceof ContainerEventHandler container) {
				final AbstractWidget found = find(container.children(), label);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}
}
