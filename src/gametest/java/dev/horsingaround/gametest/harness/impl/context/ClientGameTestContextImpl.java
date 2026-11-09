/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Changed for Horsing Around (2026): moved into this project's test source set and made loader-neutral, so every
 * loader and Minecraft version runs the same client tests. Originally from Fabric API's fabric-client-gametest-api-v1.
 */

package dev.horsingaround.gametest.harness.impl.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import org.apache.commons.lang3.function.FailableConsumer;
import org.apache.commons.lang3.function.FailableFunction;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.Optionull;
import net.minecraft.client.CloudStatus;
//? if >=1.21
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

import dev.horsingaround.gametest.harness.context.ClientGameTestContext;
import dev.horsingaround.gametest.harness.screenshot.TestScreenshotComparisonAlgorithm;
import dev.horsingaround.gametest.harness.screenshot.TestScreenshotComparisonOptions;
import dev.horsingaround.gametest.harness.screenshot.TestScreenshotOptions;
import dev.horsingaround.gametest.harness.world.TestWorldBuilder;
import dev.horsingaround.gametest.harness.impl.TestInputImpl;
import dev.horsingaround.gametest.harness.impl.TestSystemProperties;
import dev.horsingaround.gametest.harness.impl.screenshot.TestScreenshotCommonOptionsImpl;
import dev.horsingaround.gametest.harness.impl.screenshot.TestScreenshotComparisonAlgorithms;
import dev.horsingaround.gametest.harness.impl.screenshot.TestScreenshotComparisonOptionsImpl;
import dev.horsingaround.gametest.harness.impl.screenshot.TestScreenshotOptionsImpl;
import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;
import dev.horsingaround.gametest.harness.impl.world.TestWorldBuilderImpl;
import dev.horsingaround.gametest.harness.mixin.gui.CycleButtonAccessor;
import dev.horsingaround.gametest.harness.mixin.gui.ScreenAccessor;
import dev.horsingaround.gametest.harness.impl.OptionsSnapshot;
//? if >=1.21
import dev.horsingaround.gametest.harness.mixin.screenshot.DeltaTrackerDefaultValueAccessor;

public final class ClientGameTestContextImpl implements ClientGameTestContext {
	private static final Logger LOGGER = LoggerFactory.getLogger("horsingaround-gametest");

	private final TestInputImpl input = new TestInputImpl(this);
	private static int screenshotCounter = 0;

	private static final Map<java.lang.reflect.Field, Object> DEFAULT_GAME_OPTIONS = new HashMap<>();

	public static void initGameOptions(Options options) {
		// When adding to the list of default game options, remember to update the list in module documentation

		// Messes with the consistency of gametests
		options.tutorialStep = TutorialSteps.NONE;
		options.cloudStatus().set(CloudStatus.OFF);
		//? if >=1.21.11 {
		options.maxAnisotropyBit().set(0);
		options.chunkSectionFadeInTime().set(0D);
		//?}

		// Messes with game tests starting
		options.onboardAccessibility = false;

		// Makes chunk rendering finish sooner
		options.renderDistance().set(5);

		// Just annoying
		options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);

		// When adding to the list of default game options, remember to update the list in module documentation

		DEFAULT_GAME_OPTIONS.putAll(OptionsSnapshot.take(options));
	}

	@Override
	public void waitTick() {
		ThreadingImpl.checkOnGametestThread("waitTick");
		ThreadingImpl.runTick();
	}

	@Override
	public void waitTicks(int ticks) {
		ThreadingImpl.checkOnGametestThread("waitTicks");
		Preconditions.checkArgument(ticks >= 0, "ticks cannot be negative");

		for (int i = 0; i < ticks; i++) {
			ThreadingImpl.runTick();
		}
	}

	@Override
	public int waitFor(Predicate<Minecraft> predicate) {
		ThreadingImpl.checkOnGametestThread("waitFor");
		Preconditions.checkNotNull(predicate, "predicate");
		return waitFor(predicate, DEFAULT_TIMEOUT);
	}

	@Override
	public int waitFor(Predicate<Minecraft> predicate, int timeout) {
		ThreadingImpl.checkOnGametestThread("waitFor");
		Preconditions.checkNotNull(predicate, "predicate");

		if (timeout == NO_TIMEOUT) {
			int ticksWaited = 0;

			while (!computeOnClient(predicate::test)) {
				ticksWaited++;
				ThreadingImpl.runTick();
			}

			return ticksWaited;
		} else {
			Preconditions.checkArgument(timeout > 0, "timeout must be positive");

			for (int i = 0; i < timeout; i++) {
				if (computeOnClient(predicate::test)) {
					return i;
				}

				ThreadingImpl.runTick();
			}

			if (!computeOnClient(predicate::test)) {
				throw new AssertionError("Timed out waiting for predicate");
			}

			return timeout;
		}
	}

	@Override
	public int waitForScreen(@Nullable Class<? extends Screen> screenClass) {
		ThreadingImpl.checkOnGametestThread("waitForScreen");

		if (screenClass == null) {
			return waitFor(client -> client.gui.screen() == null);
		} else {
			return waitFor(client -> screenClass.isInstance(client.gui.screen()));
		}
	}

	@Override
	public void setScreen(Supplier<@Nullable Screen> screen) {
		ThreadingImpl.checkOnGametestThread("setScreen");
		runOnClient(client -> client.gui.setScreen(screen.get()));
	}

	@Override
	public void clickScreenButton(String translationKey) {
		ThreadingImpl.checkOnGametestThread("clickScreenButton");
		Preconditions.checkNotNull(translationKey, "translationKey");

		runOnClient(client -> {
			if (!tryClickScreenButtonImpl(client.gui.screen(), translationKey)) {
				throw new AssertionError("Could not find button '%s' in screen '%s'".formatted(
					translationKey,
					Optionull.map(client.gui.screen(), screen -> screen.getClass().getName())
				));
			}
		});
	}

	@Override
	public boolean tryClickScreenButton(String translationKey) {
		ThreadingImpl.checkOnGametestThread("tryClickScreenButton");
		Preconditions.checkNotNull(translationKey, "translationKey");

		return computeOnClient(client -> tryClickScreenButtonImpl(client.gui.screen(), translationKey));
	}

	private static boolean tryClickScreenButtonImpl(@Nullable Screen screen, String translationKey) {
		if (screen == null) {
			return false;
		}

		final String buttonText = Component.translatable(translationKey).getString();
		final ScreenAccessor screenAccessor = (ScreenAccessor) screen;

		for (Renderable renderable : screenAccessor.getRenderables()) {
			if (renderable instanceof AbstractButton button && pressMatchingButton(button, buttonText)) {
				return true;
			}

			if (renderable instanceof LayoutElement layoutElement) {
				MutableBoolean found = new MutableBoolean(false);
				layoutElement.visitWidgets(widget -> {
					if (!found.booleanValue()) {
						found.setValue(pressMatchingButton(widget, buttonText));
					}
				});

				if (found.booleanValue()) {
					return true;
				}
			}
		}

		// Was unable to find the button to press
		return false;
	}

	private static boolean pressMatchingButton(AbstractWidget widget, String text) {
		//? if >=1.21.9
		var clickEvent = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);

		if (widget instanceof Button button) {
			if (text.equals(button.getMessage().getString())) {
				//? if >=1.21.9 {
				button.onPress(clickEvent);
				//?} else
				/*button.onPress();*/
				return true;
			}
		}

		if (widget instanceof CycleButton<?> button) {
			CycleButtonAccessor accessor = (CycleButtonAccessor) button;

			if (text.equals(accessor.getName().getString())) {
				//? if >=1.21.9 {
				button.onPress(clickEvent);
				//?} else
				/*button.onPress();*/
				return true;
			}
		}

		return false;
	}

	@Override
	public Path takeScreenshot(TestScreenshotOptions options) {
		ThreadingImpl.checkOnGametestThread("takeScreenshot");
		Preconditions.checkNotNull(options, "options");

		TestScreenshotOptionsImpl optionsImpl = (TestScreenshotOptionsImpl) options;
		return doTakeScreenshot(optionsImpl, screenshot -> saveScreenshot(screenshot, optionsImpl.name, optionsImpl));
	}

	@Override
	public void assertScreenshotEquals(TestScreenshotComparisonOptions options) {
		ThreadingImpl.checkOnGametestThread("assertScreenshotEquals");
		Preconditions.checkNotNull(options, "options");
		doAssertScreenshotContains(options, (haystackImage, needleImage) -> haystackImage.width() == needleImage.width() && haystackImage.height() == needleImage.height());
	}

	@Override
	public Vector2i assertScreenshotContains(TestScreenshotComparisonOptions options) {
		ThreadingImpl.checkOnGametestThread("assertScreenshotContains");
		Preconditions.checkNotNull(options, "options");
		return doAssertScreenshotContains(options, (haystackImage, needleImage) -> true);
	}

	private Vector2i doAssertScreenshotContains(
			TestScreenshotComparisonOptions options,
			BiPredicate<TestScreenshotComparisonAlgorithm.RawImage<?>, TestScreenshotComparisonAlgorithm.RawImage<?>> preCheck
	) {
		TestScreenshotComparisonOptionsImpl optionsImpl = (TestScreenshotComparisonOptionsImpl) options;
		return doTakeScreenshot(optionsImpl, screenshot -> {
			Rect2i region = optionsImpl.region == null ? new Rect2i(0, 0, screenshot.getWidth(), screenshot.getHeight()) : optionsImpl.region;
			Preconditions.checkState(region.getX() + region.getWidth() <= screenshot.getWidth() && region.getY() + region.getHeight() <= screenshot.getHeight(), "Screenshot comparison region extends outside the screenshot");

			try (NativeImage subScreenshot = new NativeImage(region.getWidth(), region.getHeight(), false)) {
				screenshot.resizeSubRectTo(region.getX(), region.getY(), region.getWidth(), region.getHeight(), subScreenshot);

				if (optionsImpl.savedFileName != null) {
					saveScreenshot(subScreenshot, optionsImpl.savedFileName, optionsImpl);
				}

				Vector2i result;

				if (optionsImpl.grayscale) {
					TestScreenshotComparisonAlgorithm.RawImage<byte[]> templateImage = optionsImpl.getGrayscaleTemplateImage();

					if (templateImage == null) {
						onTemplateImageDoesntExist(subScreenshot, optionsImpl);
						return new Vector2i(region.getX(), region.getY());
					}

					TestScreenshotComparisonAlgorithm.RawImage<byte[]> haystackImage = TestScreenshotComparisonAlgorithms.RawImageImpl.fromGrayscaleNativeImage(subScreenshot);

					if (preCheck.test(haystackImage, templateImage)) {
						result = optionsImpl.algorithm.findGrayscale(haystackImage, templateImage);
					} else {
						result = null;
					}
				} else {
					TestScreenshotComparisonAlgorithm.RawImage<int[]> templateImage = optionsImpl.getColorTemplateImage();

					if (templateImage == null) {
						onTemplateImageDoesntExist(subScreenshot, optionsImpl);
						return new Vector2i(region.getX(), region.getY());
					}

					TestScreenshotComparisonAlgorithm.RawImage<int[]> haystackImage = TestScreenshotComparisonAlgorithms.RawImageImpl.fromColorNativeImage(subScreenshot);

					if (preCheck.test(haystackImage, templateImage)) {
						result = optionsImpl.algorithm.findColor(haystackImage, templateImage);
					} else {
						result = null;
					}
				}

				if (result == null) {
					throw new AssertionError("Screenshot does not contain template" + optionsImpl.getTemplateImagePath().map(" '%s'"::formatted).orElse(""));
				}

				return result.add(region.getX(), region.getY());
			}
		});
	}

	private <T> T doTakeScreenshot(TestScreenshotCommonOptionsImpl<?> options, Function<NativeImage, T> screenshotConsumer) {
		ThreadingImpl.checkOnGametestThread("doTakeScreenshot");
		Vector2i screenshotSize = options.size != null ? options.size : computeOnClient(client -> new Vector2i(client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight()));

		Vector2i prevSize = computeOnClient(client -> {
			int prevWidth = client.getWindow().getWidth();
			int prevHeight = client.getWindow().getHeight();

			if (prevWidth != screenshotSize.x || prevHeight != screenshotSize.y) {
				client.getWindow().setWidth(screenshotSize.x);
				client.getWindow().setHeight(screenshotSize.y);
				//? if >=26.3 {
				client.gameRenderer.resize(screenshotSize.x, screenshotSize.y);
				//?} elif >=1.21.2 {
				/*client.gameRenderer.mainRenderTarget().resize(screenshotSize.x, screenshotSize.y);
				*///?} else
				/*client.gameRenderer.mainRenderTarget().resize(screenshotSize.x, screenshotSize.y, Minecraft.ON_OSX);*/
			}

			return new Vector2i(prevWidth, prevHeight);
		});

		try {
			CompletableFuture<T> future = computeOnClient(client -> {
				//? if >=1.21
				DeltaTracker.DefaultValue deltaTracker = DeltaTrackerDefaultValueAccessor.create(options.deltaTicks);
				//? if >=26.2
				client.gameRenderer.update(deltaTracker);
				//? if >=26.1
				client.gameRenderer.extract(deltaTracker, true);
				//? if >=26.3 {
				client.gameRenderer.render();
				//?} elif >=1.21 {
				/*client.gameRenderer.render(deltaTracker, true);
				*///?} else
				/*client.gameRenderer.render(options.deltaTicks, net.minecraft.Util.getNanos(), true);*/
				//? if >=26.2
				RenderSystem.getDevice().createCommandEncoder().submit();
				CompletableFuture<T> resultFuture = new CompletableFuture<>();

				//? if >=1.21.5 {
				Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), screenshot -> {
					try {
						resultFuture.complete(screenshotConsumer.apply(screenshot));
					} catch (Throwable e) {
						resultFuture.completeExceptionally(e);
					}
				});
				//?} else {
				/*// (Read back at once before 1.21.5.)
				try {
					resultFuture.complete(screenshotConsumer.apply(Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget())));
				} catch (Throwable e) {
					resultFuture.completeExceptionally(e);
				}
				*///?}

				return resultFuture;
			});

			// Keep ticking until the screenshot is done
			while (!future.isDone()) {
				waitTick();
			}

			return future.get();
		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
			if (!prevSize.equals(screenshotSize)) {
				computeOnClient(client -> {
					client.getWindow().setWidth(prevSize.x);
					client.getWindow().setHeight(prevSize.y);
					//? if >=26.3 {
					client.gameRenderer.resize(prevSize.x, prevSize.y);
					//?} elif >=1.21.2 {
					/*client.gameRenderer.mainRenderTarget().resize(prevSize.x, prevSize.y);
					*///?} else
					/*client.gameRenderer.mainRenderTarget().resize(prevSize.x, prevSize.y, Minecraft.ON_OSX);*/
					return null;
				});
			}
		}
	}

	private static Path saveScreenshot(NativeImage screenshot, String fileName, TestScreenshotCommonOptionsImpl<?> options) {
		Path destinationDir = Objects.requireNonNullElseGet(options.destinationDir, () -> ThreadingImpl.unsafeClientInstance.gameDirectory.toPath().resolve("screenshots"));

		try {
			Files.createDirectories(destinationDir);
		} catch (IOException e) {
			throw new AssertionError("Failed to create screenshots directory", e);
		}

		String counterPrefix = options.counterPrefix ? String.format(Locale.ROOT, "%04d_", screenshotCounter++) : "";
		Path screenshotFile = destinationDir.resolve(counterPrefix + fileName + ".png");

		try {
			screenshot.writeToFile(screenshotFile);
		} catch (IOException e) {
			throw new AssertionError("Failed to write screenshot file", e);
		}

		return screenshotFile;
	}

	private static void onTemplateImageDoesntExist(NativeImage subScreenshot, TestScreenshotComparisonOptionsImpl options) {
		if (TestSystemProperties.TEST_MOD_RESOURCES_PATH != null) {
			Path savePath = Path.of(TestSystemProperties.TEST_MOD_RESOURCES_PATH).resolve("templates").resolve(options.getTemplateImagePathOrThrow() + ".png");

			try {
				Files.createDirectories(savePath.getParent());
				subScreenshot.writeToFile(savePath);
			} catch (IOException e) {
				throw new AssertionError("Failed to write screenshot file", e);
			}

			LOGGER.info("Written absent screenshot template to {}", savePath);
		} else {
			LOGGER.error("The template image does not exist. Set the fabric.client.gametest.testModResourcesPath system property to your test mod resources file path to automatically save it");
			throw new AssertionError("Template image does not exist");
		}
	}

	@Override
	public TestInputImpl getInput() {
		return input;
	}

	@Override
	public TestWorldBuilder worldBuilder() {
		return new TestWorldBuilderImpl(this);
	}

	@Override
	public void restoreDefaultGameOptions() {
		ThreadingImpl.checkOnGametestThread("restoreDefaultGameOptions");

		runOnClient(client -> {
			OptionsSnapshot.restore(Minecraft.getInstance().options, DEFAULT_GAME_OPTIONS);
		});
	}

	@Override
	public <E extends Throwable> void runOnClient(FailableConsumer<Minecraft, E> action) throws E {
		ThreadingImpl.checkOnGametestOrClientThread("runOnClient");
		Preconditions.checkNotNull(action, "action");

		if (ThreadingImpl.unsafeClientInstance.isSameThread()) {
			action.accept(Minecraft.getInstance());
		} else {
			ThreadingImpl.runOnClient(() -> action.accept(Minecraft.getInstance()));
		}
	}

	@Override
	public <T, E extends Throwable> T computeOnClient(FailableFunction<Minecraft, T, E> function) throws E {
		ThreadingImpl.checkOnGametestOrClientThread("computeOnClient");
		Preconditions.checkNotNull(function, "function");

		if (ThreadingImpl.unsafeClientInstance.isSameThread()) {
			return function.apply(Minecraft.getInstance());
		} else {
			MutableObject<T> result = new MutableObject<>();
			ThreadingImpl.runOnClient(() -> result.setValue(function.apply(Minecraft.getInstance())));
			return result.getValue();
		}
	}
}
