package dev.horsingaround.gametest;

import dev.horsingaround.client.RideCamera;
import dev.horsingaround.gametest.harness.ClientGameTest;
import dev.horsingaround.gametest.harness.TestInput;
import dev.horsingaround.gametest.harness.context.ClientGameTestContext;
import dev.horsingaround.gametest.harness.context.TestServerContext;
import dev.horsingaround.gametest.harness.context.TestSingleplayerContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * Riding in Leawind's Third Person, which places its own camera and turns the rider itself, on Perspective API (which also
 * takes over F5): mounting from first person turns to its view, the riding camera steps aside while it's on, the horse
 * heads where its camera looks (A and D off it as usual), and getting off gives the view back. Needs the mod
 * (-Pleawind, on a version it has a build for).
 */
public final class ThirdPersonTest implements ClientGameTest {
	private static final String LEAWIND = "leawind_third_person.third_person";
	private static final String FIRST_PERSON = "perspective_api.first_person";
	private static final String VANILLA_BACK = "perspective_api.third_person_back";
	/** How far off the expected heading the horse may end a steering lane, degrees. */
	private static final double HEADING_SLACK = 12.0;

	private final List<String> report = new ArrayList<>();
	private int failures;
	private int lanes;

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!System.getProperty("horsingaround.tests", "").contains("thirdperson")) {
			return;
		}
		boolean completed = false;
		try (LogWatch watch = LogWatch.begin();
			TestSingleplayerContext world = ctx.worldBuilder()
				.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			if (!TestEnv.modLoaded("leawind_third_person")) {
				check("Leawind's Third Person is loaded (run with -Pleawind)", false);
				completed = true;
				return;
			}
			final TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand(Versions.rule("spawn_mobs") + " false");
			world.getConnection().waitForChunksRender();
			ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
			ctx.waitTicks(20);

			section("Mounting from first person");
			check("on foot in first person, Perspective API's", isCurrent(FIRST_PERSON) && ctx.computeOnClient(mc -> mc.options.getCameraType()) == CameraType.FIRST_PERSON);
			mount(ctx, world);
			check("mounting turns to Leawind's view", isCurrent(LEAWIND));
			check("the game's camera type agrees with Perspective API's view", viewsAgree(ctx));
			check("the riding camera steps aside", !ctx.computeOnClient(mc -> RideCamera.isActive(mc.player, mc)));
			ctx.takeScreenshot("horsingaround_thirdperson_mounted");
			if (!isCurrent(LEAWIND)) {
				// (So the steering below still rides in Leawind's view.)
				log("  (F5 to Leawind's view for the rest)");
				switchTo(ctx, LEAWIND);
			}

			section("Steering by Leawind's camera");
			settle(ctx);
			final float before = ctx.computeOnClient(ThirdPersonTest::cameraYaw);
			final float riderBefore = ctx.computeOnClient(mc -> mc.player.getYRot());
			turnView(ctx, 60.0F);
			settle(ctx);
			check("the mouse turns Leawind's camera (degrees)", Mth.wrapDegrees(ctx.computeOnClient(ThirdPersonTest::cameraYaw) - before), 55.0, 65.0);
			check("and not the rider (degrees)", Math.abs(Mth.wrapDegrees(ctx.computeOnClient(mc -> mc.player.getYRot()) - riderBefore)), 0.0, 5.0);
			steer(ctx, world, "W", true, false, false, 0.0F);
			steer(ctx, world, "W+D", true, false, true, 45.0F);
			steer(ctx, world, "W+A", true, true, false, -45.0F);
			steer(ctx, world, "D", false, false, true, 90.0F);
			steer(ctx, world, "A", false, true, false, -90.0F);

			section("Getting off");
			mount(ctx, world);
			server.runCommand("ride @p dismount");
			ctx.waitTicks(20);
			check("first person again", isCurrent(FIRST_PERSON));
			check("the game's camera type agrees with Perspective API's view", viewsAgree(ctx));

			section("F5 while riding");
			mount(ctx, world);
			final boolean leawindOnMount = isCurrent(LEAWIND);
			ctx.getInput().holdKeyFor(o -> o.keyTogglePerspective, 2);
			ctx.waitTicks(20);
			final String picked = current();
			log("  F5 picked %s", picked);
			check("F5 changes the view (Perspective API's switcher)", !leawindOnMount || !isCurrent(LEAWIND));
			check("the game's camera type agrees with Perspective API's view", viewsAgree(ctx));
			server.runCommand("ride @p dismount");
			ctx.waitTicks(20);
			check("getting off keeps the view the rider picked (" + current() + ")", picked.equals(current()));
			check("the game's camera type agrees with Perspective API's view", viewsAgree(ctx));

			section("Riding in Perspective API's own third person");
			switchTo(ctx, VANILLA_BACK);
			mount(ctx, world);
			check("mounting in third person keeps it", isCurrent(VANILLA_BACK));
			check("the riding camera places the view", ctx.computeOnClient(mc -> RideCamera.isActive(mc.player, mc)));
			ctx.takeScreenshot("horsingaround_thirdperson_vanilla_back");
			server.runCommand("ride @p dismount");
			ctx.waitTicks(10);
			switchTo(ctx, FIRST_PERSON);

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
		TestSummary.failed(this.failures, "horsingaround-thirdperson-report.txt");
	}

	/**
	 * A lane of its own: from a standstill facing south, the camera turned 60 degrees, the keys held for 4 s. The horse
	 * should end up heading the camera's way plus {@code offset}.
	 */
	private void steer(
		final ClientGameTestContext ctx, final TestSingleplayerContext world, final String keys, final boolean forward, final boolean left, final boolean right,
		final float offset
	) {
		mount(ctx, world);
		if (!isCurrent(LEAWIND)) {
			switchTo(ctx, LEAWIND);
		}
		// Lined up facing south, then turned 60 degrees. (Leawind's camera eases in real time, which the test outruns: each
		// turn is waited out.)
		settle(ctx);
		turnView(ctx, Mth.wrapDegrees(0.0F - ctx.computeOnClient(ThirdPersonTest::cameraYaw)));
		settle(ctx);
		turnView(ctx, 60.0F);
		settle(ctx);
		final TestInput input = ctx.getInput();
		final double startX = ctx.computeOnClient(mc -> mc.player.getX());
		final double startZ = ctx.computeOnClient(mc -> mc.player.getZ());
		final float view = ctx.computeOnClient(ThirdPersonTest::cameraYaw);
		if (forward) {
			input.holdKey(o -> o.keyUp);
		}
		if (left) {
			input.holdKey(o -> o.keyLeft);
		}
		if (right) {
			input.holdKey(o -> o.keyRight);
		}
		// The horse turning under it never turns the camera (only the mouse does).
		double drift = 0.0;
		final StringBuilder trace = new StringBuilder();
		for (int tick = 0; tick < 80; tick++) {
			ctx.waitTick();
			final float[] t = ctx.computeOnClient(mc -> new float[] {cameraYaw(mc), mc.player.getVehicle().getYRot(), mc.player.getYRot()});
			drift = Math.max(drift, Math.abs(Mth.wrapDegrees(t[0] - view)));
			if (tick % 10 == 0) {
				trace.append(String.format(Locale.ROOT, " [%.0f %.0f %.0f]", t[0], t[1], t[2]));
			}
		}
		log("  %s: from camera %.1f; every 10 ticks [camera horse rider]:%s", keys, view, trace);
		check(keys + ": the camera stays where the mouse left it (degrees)", drift, 0.0, 2.0);
		final float[] s = ctx.computeOnClient(mc -> new float[] {
			cameraYaw(mc), mc.player.getVehicle().getYRot(), mc.player.getYRot(),
			(float) Math.hypot(mc.player.getX() - startX, mc.player.getZ() - startZ)
		});
		input.releaseKey(o -> o.keyUp);
		input.releaseKey(o -> o.keyLeft);
		input.releaseKey(o -> o.keyRight);
		ctx.takeScreenshot("horsingaround_thirdperson_" + keys.replace('+', '_').toLowerCase(Locale.ROOT));
		log("  %s: camera %.1f, horse %.1f, rider %.1f, rode %.1f blocks", keys, s[0], s[1], s[2], s[3]);
		check(keys + ": the horse rode off (blocks)", s[3], 2.0, 1000.0);
		check(keys + ": the horse heads " + (offset == 0.0F ? "where the camera looks" : Math.abs(offset) + " degrees " + (offset > 0 ? "right" : "left") + " of it")
			+ " (degrees off)", Math.abs(Mth.wrapDegrees(s[1] - s[0] - offset)), 0.0, HEADING_SLACK);
	}

	/** On a fresh horse of its own, out of the way of the last lane's, facing south. */
	private void mount(final ClientGameTestContext ctx, final TestSingleplayerContext world) {
		final TestServerContext server = world.getServer();
		server.runCommand("ride @p dismount");
		server.runCommand("kill @e[type=" + TestHorse.TYPE + ",tag=thirdperson]");
		final int x = 60 * this.lanes++;
		server.runCommand(String.format(Locale.ROOT, "tp @p %d.5 -60 0.5 0 10", x));
		ctx.waitTicks(10);
		world.getConnection().waitForChunksRender();
		server.runCommand(String.format(Locale.ROOT, "summon %s %d.5 -60 0.5 {Tame:1b,Variant:0,Rotation:[0f,0f],%s,%s,Tags:[\"thirdperson\"]}",
			TestHorse.TYPE, x, Versions.saddle(), Versions.attributes("movement_speed", 0.225)));
		ctx.waitTicks(2);
		server.runCommand("ride @p mount @e[type=" + TestHorse.TYPE + ",tag=thirdperson,limit=1]");
		ctx.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof AbstractHorse);
		ctx.waitTicks(20);
	}

	/** Mouse movement: what the game does with it (Leawind's view turns its own camera instead of the rider). */
	private static void turnView(final ClientGameTestContext ctx, final float degrees) {
		ctx.runOnClient(mc -> mc.player.turn(degrees / 0.15, 0.0));
	}

	/** Until the camera stops turning (up to 10 s). */
	private static void settle(final ClientGameTestContext ctx) {
		float last = ctx.computeOnClient(ThirdPersonTest::cameraYaw);
		for (int i = 0; i < 40; i++) {
			ctx.waitTicks(5);
			final float now = ctx.computeOnClient(ThirdPersonTest::cameraYaw);
			if (Math.abs(Mth.wrapDegrees(now - last)) < 0.05F) {
				return;
			}
			last = now;
		}
	}

	/** F5 until Perspective API shows {@code id} (its switcher's order is the player's to set). */
	private void switchTo(final ClientGameTestContext ctx, final String id) {
		for (int press = 0; press < 6 && !isCurrent(id); press++) {
			ctx.getInput().holdKeyFor(o -> o.keyTogglePerspective, 2);
			ctx.waitTicks(10);
		}
		if (!isCurrent(id)) {
			check("F5 reaches " + id, false);
		}
	}

	private static float cameraYaw(final Minecraft mc) {
		//? if >=1.21.11 {
		return mc.gameRenderer.mainCamera().yRot();
		//?} else
		/*return mc.gameRenderer.mainCamera().getYRot();*/
	}

	/** Perspective API's view and the game's camera type say the same (first person, or third from behind). */
	private boolean viewsAgree(final ClientGameTestContext ctx) {
		final CameraType type = ctx.computeOnClient(mc -> mc.options.getCameraType());
		final String view = current();
		final boolean agree = isCurrent(FIRST_PERSON) == (type == CameraType.FIRST_PERSON);
		if (!agree) {
			log("  (Perspective API shows %s, the game's camera type is %s)", view, type);
		}
		return agree;
	}

	private static boolean isCurrent(final String id) {
		try {
			return (boolean) Class.forName("io.github.leawind.perspectiveapi.api.PerspectiveAPI").getMethod("isCurrent", String.class).invoke(null, id);
		} catch (final ReflectiveOperationException e) {
			throw new AssertionError("Perspective API's isCurrent moved", e);
		}
	}

	private static String current() {
		for (final String id : new String[] {FIRST_PERSON, VANILLA_BACK, "perspective_api.third_person_front", LEAWIND}) {
			if (isCurrent(id)) {
				return id;
			}
		}
		return "another view";
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
		System.out.println("[thirdperson-test] " + line);
	}

	private void writeReport() {
		final Path file = TestEnv.gameDir().resolve("horsingaround-thirdperson-report.txt");
		try {
			Files.write(file, this.report);
		} catch (final IOException e) {
			throw new RuntimeException("Could not write " + file, e);
		}
	}
}
