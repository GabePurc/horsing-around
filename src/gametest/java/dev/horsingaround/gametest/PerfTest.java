package dev.horsingaround.gametest;

import dev.horsingaround.ride.Awareness;
import dev.horsingaround.ride.RideController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * Load: a herd of every managed kind of horse around a pen of fences and leaves, and the player galloping circles round
 * it, so every per-tick and per-frame path runs at once (ridden: planning, steps, camera, rider pose; unridden: the
 * visual tick, the model hooks). Logs the server's tick time and the client's frame rate. Not a pass/fail test: run it
 * with {@code -Pprofile} and read the recording (docs/testing.md).
 *
 * <p>Run with {@code ./gradlew runClientGameTest -Ptests=perf -Pprofile}.
 */
public final class PerfTest implements FabricClientGameTest {
	private static final int GROUND = -60;
	private static final String[] KINDS = {"horse", "horse", "horse", "horse", "horse", "donkey", "mule", "skeleton_horse"};
	private static final int HERD = 56;
	private static final int RADIUS = 26;
	private static final int TICKS = 1800;

	private final List<String> report = new ArrayList<>();

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!System.getProperty("horsingaround.tests", "").contains("perf")) {
			return;
		}
		TestPacks.enable(ctx);
		try (TestSingleplayerContext world = ctx.worldBuilder()
			.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
			.create()) {
			final TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule advance_time false");
			server.runCommand(String.format(Locale.ROOT, "tp @a %d %d 0 180 10", RADIUS, GROUND));
			world.getConnection().waitForChunksRender();

			// The pen: a ring of fence with gaps, and clumps of leaves, for the herd to mill about in and the rider to pass.
			server.runCommand(String.format(Locale.ROOT, "fill -12 %d -12 12 %d 12 minecraft:oak_fence outline", GROUND, GROUND));
			server.runCommand(String.format(Locale.ROOT, "fill -12 %d -2 -12 %d 2 minecraft:air", GROUND, GROUND));
			server.runCommand(String.format(Locale.ROOT, "fill 12 %d -2 12 %d 2 minecraft:air", GROUND, GROUND));
			final Random random = new Random(7L);
			for (int i = 0; i < 10; i++) {
				final double angle = random.nextDouble() * Math.PI * 2.0;
				final int x = (int) Math.round(Math.cos(angle) * (RADIUS + 4 + random.nextInt(6)));
				final int z = (int) Math.round(Math.sin(angle) * (RADIUS + 4 + random.nextInt(6)));
				server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:oak_leaves[persistent=true]", x - 1, GROUND, z - 1, x + 1, GROUND + 2, z + 1));
			}
			for (int i = 0; i < HERD; i++) {
				final String kind = KINDS[i % KINDS.length];
				final double x = (random.nextDouble() * 2.0 - 1.0) * 10.0;
				final double z = (random.nextDouble() * 2.0 - 1.0) * 10.0;
				server.runCommand(String.format(Locale.ROOT, "summon minecraft:%s %.1f %d %.1f {Tame:1b}", kind, x, GROUND, z));
			}

			server.runCommand(String.format(Locale.ROOT, "summon minecraft:horse %d.5 %d 0.5 {Tame:1b,Rotation:[180f,0f],"
				+ "equipment:{saddle:{id:\"minecraft:saddle\",count:1}},attributes:[{id:\"minecraft:movement_speed\",base:0.225d}],Tags:[\"perf\"]}", RADIUS, GROUND));
			for (int attempt = 0; attempt < 10 && !ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse); attempt++) {
				ctx.waitTicks(5);
				server.runCommand("ride @p mount @e[tag=perf,limit=1]");
			}
			ctx.waitFor(mc -> mc.player.getVehicle() instanceof AbstractHorse);

			final TestInput input = ctx.getInput();
			float yaw = 180.0F;
			input.lookAt(yaw, 10.0F);
			input.holdKey(o -> o.keyUp);
			for (int i = 0; i < 3; i++) {
				input.pressKey(o -> o.keySprint);
				ctx.waitTicks(6);
			}
			ctx.runOnClient(mc -> {
				Awareness.plans = 0;
				Awareness.planNanos = 0L;
				Awareness.planNanosMost = 0L;
				RideController.profile = true;
			});
			long tickNanos = 0L;
			long fps = 0L;
			int samples = 0;
			for (int i = 0; i < TICKS; i++) {
				// Circles round the pen, left about.
				yaw -= 1.8F;
				input.lookAt(yaw, 10.0F);
				ctx.waitTick();
				if (i % 20 == 19) {
					tickNanos += server.computeOnServer(s -> s.getAverageTickTimeNanos());
					fps += ctx.computeOnClient(mc -> mc.getFps());
					samples++;
				}
			}
			input.releaseKey(o -> o.keyUp);
			ctx.runOnClient(mc -> RideController.profile = false);
			final int plans = ctx.computeOnClient(mc -> Awareness.plans);
			log("%d horses, %d ticks galloping round them", HERD + 1, TICKS);
			log("server tick: %.2f ms on average", tickNanos / 1.0E6 / Math.max(samples, 1));
			log("client: %d frames a second on average", fps / Math.max(samples, 1));
			log("plans: %d, %.0f us each on average, %.0f us at most", plans,
				ctx.computeOnClient(mc -> Awareness.planNanos) / 1000.0 / Math.max(plans, 1), ctx.computeOnClient(mc -> Awareness.planNanosMost) / 1000.0);
		} finally {
			writeReport();
		}
	}

	private void log(final String format, final Object... args) {
		final String line = String.format(Locale.ROOT, format, args);
		this.report.add(line);
		System.out.println("[perf] " + line);
	}

	private void writeReport() {
		final Path file = FabricLoader.getInstance().getGameDir().resolve("horsingaround-perf-report.txt");
		try {
			Files.write(file, this.report);
		} catch (final IOException e) {
			throw new RuntimeException("Could not write " + file, e);
		}
	}
}
