package dev.horsingaround.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.horsingaround.client.config.HorseSettingsScreen;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.client.cosmetic.Supporters;
import dev.horsingaround.ride.HorseConfig;
import dev.horsingaround.ride.IcyCompat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.UUID;
import dev.horsingaround.gametest.harness.ClientGameTest;
import dev.horsingaround.gametest.harness.TestInput;
import dev.horsingaround.gametest.harness.context.ClientGameTestContext;
import dev.horsingaround.gametest.harness.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The settings screen: every tab opens by clicking it and shows its own options, and each is photographed in a world (and
 * the hat tab for a supporter too). Writes {@code horsingaround-settings-report.txt}. Run with
 * {@code ./gradlew runClientGameTest -Ptests=settings}.
 */
public final class SettingsTest implements ClientGameTest {
	private static final String KEY = "options.horsingaround.";
	private static final String REPORT = "horsingaround-settings-report.txt";

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
			ctx.setScreen(() -> new HorseSettingsScreen(null));
			ctx.waitTicks(3);
			this.shows(ctx, "camera", "shoulder", "camera_distance", "camera_height", "third_person");
			ctx.takeScreenshot("horsingaround_settings_camera");

			this.click(ctx, "tab.comfort");
			ctx.waitTicks(2);
			this.shows(ctx, "comfort", "speed_zoom", "view_bob", "hand_bob", "horse_sounds");
			ctx.takeScreenshot("horsingaround_settings_comfort");

			this.click(ctx, "tab.hat");
			ctx.waitTicks(2);
			this.shows(ctx, "hat, not a supporter", "hat.support.note", "hat.support");
			ctx.takeScreenshot("horsingaround_settings_hat_not_supporter");

			if (IcyCompat.LOADED) {
				this.click(ctx, "tab.compatibility");
				ctx.waitTicks(2);
				this.shows(ctx, "compatibility", "riding_style");
				ctx.takeScreenshot("horsingaround_settings_compatibility");
			}
			ctx.setScreen(() -> null);

			final UUID me = ctx.computeOnClient(mc -> mc.player.getUUID());
			ctx.runOnClient(mc -> {
				Supporters.addForTesting(me);
				Hats.sendOwn();
			});
			ctx.setScreen(() -> new HorseSettingsScreen(null, HorseSettingsScreen.Tab.HAT));
			ctx.waitTicks(3);
			this.shows(ctx, "hat, a supporter", "hat", "hat_hue", "hat_richness", "hat_brightness");
			ctx.takeScreenshot("horsingaround_settings_hat_supporter");
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> {
				Supporters.removeForTesting(me);
				HorseConfig.reset();
				HorseConfig.save();
			});
			completed = true;
		} finally {
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

	/** A real click on the middle of the widget with this label. */
	private void click(final ClientGameTestContext ctx, final String id) {
		final String label = Component.translatable(KEY + id).getString();
		final double[] at = ctx.computeOnClient(mc -> {
			final AbstractWidget widget = find(mc.gui.screen().children(), label);
			if (widget == null) {
				throw new AssertionError("Nothing to click for \"" + label + "\"");
			}
			final double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
			return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		final TestInput input = ctx.getInput();
		input.setCursorPos(at[0], at[1]);
		ctx.waitTicks(1);
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	/** Each label is on a widget of the tab showing now. */
	private void shows(final ClientGameTestContext ctx, final String tab, final String... ids) {
		for (final String id : ids) {
			final String label = Component.translatable(KEY + id).getString();
			final boolean found = ctx.computeOnClient(mc -> find(mc.gui.screen().children(), label) != null);
			this.report.add((found ? "PASS " : "FAIL ") + "the " + tab + " tab shows \"" + label + "\"");
			if (!found) {
				this.failures++;
			}
		}
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
