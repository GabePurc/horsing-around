package dev.horsingaround.gametest;

import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import dev.horsingaround.gametest.harness.ClientGameTest;
import dev.horsingaround.gametest.harness.TestInput;
import dev.horsingaround.gametest.harness.context.ClientGameTestContext;
import dev.horsingaround.gametest.harness.context.TestServerContext;
import dev.horsingaround.gametest.harness.context.TestSingleplayerContext;
import dev.horsingaround.gametest.harness.world.TestWorldBuilder;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Riding over steps in first person with Real Camera, which puts the view on the rider's drawn head: the view has to stay on
 * that head (drawn eased over each step), never inside the horse or a block. Runs in the realcamera compat pack.
 */
public final class RealCameraTest implements ClientGameTest {
	private static final String SPEED_ATTRIBUTE = "0.225d";
	/** Lanes start here and head north; the first step is this far along, once up to speed. */
	private static final int FIRST_STEP = -30;
	/** How far the view may leave where it sat at rest against the rider's drawn eyes (lean and bob), blocks. */
	private static final double OFF_THE_HEAD = 0.4;
	/** In a jump the rider folds over the neck, and the head goes with them. */
	private static final double OFF_THE_HEAD_JUMPING = 0.8;

	private final List<String> report = new ArrayList<>();
	private int failures;
	private int lanes;

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!System.getProperty("horsingaround.tests", "").contains("realcamera")) {
			return;
		}
		TestPacks.enable(ctx);
		boolean completed = false;
		try (LogWatch watch = LogWatch.begin();
			TestSingleplayerContext world = ctx.worldBuilder()
				.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			final TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_mobs false");
			world.getConnection().waitForChunksRender();
			// (Command feedback would cover the shots.)
			ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));

			// (Without Real Camera, as a look at another first-person mod in its pack: only the plain first-person lanes.)
			if (TestEnv.modLoaded("realcamera")) {
				setConfig("enabled", true);
				setConfig("isClassic", false);
				section("Bound to the rider's head (Real Camera's default mode)");
				rideAll(ctx, world, "bound");
				setConfig("isClassic", true);
				section("Classic (Real Camera moves the view off the eyes)");
				rideAll(ctx, world, "classic");
				setConfig("enabled", false);
			}
			section("Plain first person");
			rideAll(ctx, world, "off");

			section("Log");
			final List<String> problems = watch.matching("horsingaround", "Exception", "moved wrongly", "moved too quickly", "was expected to be controlling");
			problems.forEach(line -> log("  %s", line));
			check("no server corrections, exceptions or mod warnings on the whole run", problems.isEmpty());
			completed = true;
		} finally {
			if (!completed) {
				this.failures++;
				log("INCOMPLETE: the run stopped early (see the log for the exception)");
			}
			writeReport();
		}
		TestSummary.failed(this.failures, "horsingaround-realcamera-report.txt");
	}

	private void rideAll(final ClientGameTestContext ctx, final TestSingleplayerContext world, final String mode) {
		// Long treads: a step at a time, up and back down. Short treads: a staircase, up and back down. Then 2-block ledges.
		ride(ctx, world, mode, "walk", 0, 6, 1, 4, 0.4);
		ride(ctx, world, mode, "trot", 1, 6, 1, 4, 0.4);
		ride(ctx, world, mode, "gallop", 3, 6, 1, 4, 0.4);
		ride(ctx, world, mode, "stairs", 3, 2, 1, 6, 0.85);
		ride(ctx, world, mode, "ledges", 3, 8, 2, 2, 0.85);
		jump(ctx, world, mode);
	}

	private void ride(
		final ClientGameTestContext ctx, final TestSingleplayerContext world, final String mode, final String name, final int spurs, final int tread, final int rise,
		final int height, final double fastestAllowed
	) {
		final String what = mode + " " + name;
		final TestInput input = ctx.getInput();
		final int x = 40 * this.lanes++;
		final int end = FIRST_STEP - tread * height * 2 - 4;
		mount(ctx, world, x, tread, rise, height);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		// (Real Camera's first frames find the head on the model.)
		ctx.waitTicks(20);
		if (!"off".equals(mode)) {
			check(what + ": Real Camera places the view", ctx.computeOnClient(mc -> realCameraActive()));
		}
		final double rest = ctx.computeOnClient(RealCameraTest::offDrawnEyes);

		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(2);
		for (int i = 0; i < spurs; i++) {
			input.pressKey(o -> o.keySprint);
			ctx.waitTicks(4);
		}
		double worst = 0.0;
		double worstEase = 0.0;
		double fastest = 0.0;
		double lastY = Double.NaN;
		int inBlock = 0;
		boolean shotUp = false;
		boolean shotDown = false;
		for (int tick = 0; tick < 900 && ctx.computeOnClient(mc -> mc.player.getZ()) > end; tick++) {
			ctx.waitTick();
			// {view off the drawn eyes, step easing, view height, in a solid block, z}
			final double[] s = ctx.computeOnClient(RealCameraTest::sample);
			final double off = Math.abs(s[0] - rest);
			worst = Math.max(worst, off);
			if (off > OFF_THE_HEAD) {
				log("    z %.2f: view %.2f off the rider's drawn eyes (the step's easing %.2f)", s[4], s[0] - rest, s[1]);
			}
			worstEase = Math.abs(s[1]) > Math.abs(worstEase) ? s[1] : worstEase;
			if (!Double.isNaN(lastY)) {
				fastest = Math.max(fastest, Math.abs(s[2] - lastY));
			}
			lastY = s[2];
			if (s[3] > 0.0) {
				inBlock++;
				log("    z %.2f: view in a block at height %.2f", s[4], s[2]);
			}
			if (!shotUp && s[1] < -0.3) {
				shotUp = true;
				ctx.takeScreenshot("horsingaround_realcamera_" + mode + "_" + name + "_up");
			}
			if (!shotDown && s[1] > 0.3) {
				shotDown = true;
				ctx.takeScreenshot("horsingaround_realcamera_" + mode + "_" + name + "_down");
			}
		}
		input.releaseKey(o -> o.keyUp);
		log("  %s: at rest %.3f off the drawn eyes, most step easing %.3f, view moved at most %.3f in a tick", what, rest, worstEase, fastest);
		check(what + ": the lane was ridden to its end", ctx.computeOnClient(mc -> mc.player.getZ()) <= end + 1.0);
		check(what + ": the steps were eased (else this rode nothing)", Math.abs(worstEase) >= 0.1);
		check(what + ": view stays on the rider's drawn head (blocks off)", worst, 0.0, OFF_THE_HEAD);
		check(what + ": view doesn't snap (blocks in a tick)", fastest, 0.0, fastestAllowed);
		check(what + ": view stays out of blocks (ticks in one)", inBlock, 0.0, 0.0);
	}

	/** A running jump on flat ground: the rider folds over the neck, and the view that goes with their head stays out of things. */
	private void jump(final ClientGameTestContext ctx, final TestSingleplayerContext world, final String mode) {
		final String what = mode + " jump";
		final TestInput input = ctx.getInput();
		mount(ctx, world, 40 * this.lanes++, 1, 1, 0);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		ctx.waitTicks(20);
		final double rest = ctx.computeOnClient(RealCameraTest::offDrawnEyes);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(2);
		for (int i = 0; i < 3; i++) {
			input.pressKey(o -> o.keySprint);
			ctx.waitTicks(4);
		}
		ctx.waitTicks(20);
		input.pressKey(o -> o.keyJump);
		double worst = 0.0;
		int inBlock = 0;
		for (int tick = 0; tick < 30; tick++) {
			ctx.waitTick();
			final double[] s = ctx.computeOnClient(RealCameraTest::sample);
			worst = Math.max(worst, Math.abs(s[0] - rest));
			inBlock += s[3] > 0.0 ? 1 : 0;
			if (tick == 5) {
				ctx.takeScreenshot("horsingaround_realcamera_" + mode + "_jump");
			}
		}
		input.releaseKey(o -> o.keyUp);
		check(what + ": view stays on the rider's drawn head (blocks off)", worst, 0.0, OFF_THE_HEAD_JUMPING);
		check(what + ": view stays out of blocks (ticks in one)", inBlock, 0.0, 0.0);
	}

	/** A pyramid across the lane, its steps {@code tread} long and {@code rise} high, and a horse saddled at its foot with the rider on. */
	private void mount(final ClientGameTestContext ctx, final TestSingleplayerContext world, final int x, final int tread, final int rise, final int height) {
		final TestServerContext server = world.getServer();
		server.runCommand("ride @p dismount");
		server.runCommand(String.format(Locale.ROOT, "tp @p %d.5 -60 12.5 180 10", x));
		ctx.waitTicks(20);
		world.getConnection().waitForChunksRender();
		final int far = FIRST_STEP - tread * height * 2;
		for (int level = 0; level < height; level++) {
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone",
				x - 4, -60 + level * rise, FIRST_STEP - tread * level, x + 4, -60 + level * rise + rise - 1, far + tread * level));
		}
		ctx.waitTicks(2);
		world.getConnection().waitForChunksRender();
		final String tag = "camera_" + x;
		server.runCommand(String.format(Locale.ROOT, "tp @p %d.5 -60 0.5 180 10", x));
		server.runCommand(String.format(Locale.ROOT, "summon " + TestHorse.TYPE + " %d.5 -60 0.5 {Tame:1b,Variant:1,Rotation:[180f,0f],NoAI:1b,"
			+ "equipment:{saddle:{id:\"minecraft:saddle\",count:1}},attributes:[{id:\"minecraft:movement_speed\",base:%s}],Tags:[\"%s\"]}", x, SPEED_ATTRIBUTE, tag));
		for (int attempt = 0; attempt < 10 && !ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse); attempt++) {
			ctx.waitTicks(5);
			server.runCommand("data merge entity @e[type=" + TestHorse.TYPE + ",tag=" + tag + ",limit=1] {NoAI:0b}");
			server.runCommand("ride @p mount @e[type=" + TestHorse.TYPE + ",tag=" + tag + ",limit=1]");
		}
		ctx.waitFor(mc -> mc.player.getVehicle() instanceof AbstractHorse);
		ctx.getInput().lookAt(180.0F, 10.0F);
		ctx.waitTicks(10);
	}

	/** How far the view is above the rider's eyes as drawn: where the eyes are, plus the step's easing that the model is drawn with. */
	private static double offDrawnEyes(final Minecraft mc) {
		final float partialTicks = RideFeelTest.framePartialTicks(mc);
		final RideState ride = RideStateHolder.managedRide(mc.player.getVehicle());
		final double eye = Mth.lerp(partialTicks, mc.player.yOld, mc.player.getY()) + mc.player.getEyeHeight();
		return mc.gameRenderer.mainCamera().position().y - eye - (ride == null ? 0.0 : ride.heightOffset(partialTicks));
	}

	private static double[] sample(final Minecraft mc) {
		final float partialTicks = RideFeelTest.framePartialTicks(mc);
		final RideState ride = RideStateHolder.managedRide(mc.player.getVehicle());
		final Vec3 camera = mc.gameRenderer.mainCamera().position();
		final BlockPos at = BlockPos.containing(camera);
		final VoxelShape shape = mc.level.getBlockState(at).getCollisionShape(mc.level, at);
		final boolean inBlock = !shape.isEmpty() && shape.bounds().move(at).contains(camera);
		return new double[] {offDrawnEyes(mc), ride == null ? 0.0 : ride.heightOffset(partialTicks), camera.y, inBlock ? 1.0 : 0.0, mc.player.getZ()};
	}

	/** Real Camera's own classes aren't on the tests' classpath, and only this test reads them. */
	private static boolean realCameraActive() {
		try {
			return (boolean) Class.forName("com.xtracr.realcamera.RealCameraCore").getMethod("isActive").invoke(null);
		} catch (final ReflectiveOperationException e) {
			throw new AssertionError("Real Camera's core moved", e);
		}
	}

	private static void setConfig(final String field, final boolean value) {
		try {
			final Object config = Class.forName("com.xtracr.realcamera.config.ConfigFile").getMethod("config").invoke(null);
			config.getClass().getField(field).setBoolean(config, value);
		} catch (final ReflectiveOperationException e) {
			throw new AssertionError("Real Camera's settings moved", e);
		}
	}

	private void section(final String name) {
		log("== %s", name);
	}

	private void check(final String name, final boolean ok) {
		if (!ok) {
			this.failures++;
		}
		log("  %s  %s", ok ? "PASS" : "FAIL", name);
	}

	private void check(final String name, final double value, final double min, final double max) {
		final boolean ok = value >= min && value <= max;
		if (!ok) {
			this.failures++;
		}
		log("  %s  %s = %.3f  (target %.3f..%.3f)", ok ? "PASS" : "FAIL", name, value, min, max);
	}

	private void log(final String format, final Object... args) {
		final String line = String.format(Locale.ROOT, format, args);
		this.report.add(line);
		System.out.println("[realcamera-test] " + line);
	}

	private void writeReport() {
		final Path file = TestEnv.gameDir().resolve("horsingaround-realcamera-report.txt");
		try {
			Files.write(file, this.report);
		} catch (final IOException e) {
			throw new RuntimeException("Could not write " + file, e);
		}
	}
}
