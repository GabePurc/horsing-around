package dev.horsingaround.gametest;

import dev.horsingaround.net.RulesPayload;
import dev.horsingaround.net.ServerRules;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.ride.RideTuning;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.phys.Vec3;

/**
 * The real multiplayer setup: a dedicated server with the mod (like a family server) and a player joining it. Rides a
 * course through every mechanic that moves the horse differently from vanilla (gallop and jumps, leaves, steps and a
 * ledge, a ditch, swimming and climbing out, trampling) and checks the server accepted every move: no corrections
 * (rubber-banding), client and server agree where the horse is, the rider is never thrown off or kicked. Then joins
 * again as a player without the mod and rides the same course with vanilla controls. Writes
 * {@code horsingaround-server-report.txt}.
 *
 * <p>Run with {@code ./gradlew runClientGameTest -Ptests=server}.
 */
public final class ServerRideTest implements FabricClientGameTest {
	private static final int GROUND = -60;

	private final List<String> report = new ArrayList<>();
	private int failures;
	private int lane;

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!System.getProperty("horsingaround.tests", "server").contains("server")) {
			return;
		}
		boolean completed = false;
		try (LogWatch watch = LogWatch.begin(); TestDedicatedServerContext server = ctx.worldBuilder().createServer()) {
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("difficulty peaceful");

			try (TestDedicatedServerConnection connection = server.connect()) {
				section("Joining a dedicated server with the mod");
				connection.waitForChunksRender();
				check("the server tells the client it runs the mod", ctx.computeOnClient(mc -> ServerRules.present));
				check("with its leaves rule", ctx.computeOnClient(mc -> ServerRules.rideThroughLeaves) == RideTuning.RIDE_THROUGH_LEAVES);
				server.runCommand("gamemode creative @a");
				course(ctx, server, connection, watch, true);
			}

			// The same server joined by a player without the mod (simulated by not listening for the server's rules).
			ClientPlayNetworking.unregisterGlobalReceiver(RulesPayload.TYPE.id());
			try (TestDedicatedServerConnection connection = server.connect()) {
				section("Joining the same server without the mod");
				connection.waitForChunksRender();
				check("the client doesn't hear from the server's mod", !ctx.computeOnClient(mc -> ServerRules.present));
				course(ctx, server, connection, watch, false);
			} finally {
				ClientPlayNetworking.registerGlobalReceiver(RulesPayload.TYPE, (rules, context) -> ServerRules.accept(rules));
			}

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
		TestSummary.failed(this.failures, "horsingaround-server-report.txt");
	}

	private void course(
		final ClientGameTestContext ctx, final TestDedicatedServerContext server, final TestServerConnection connection, final LogWatch watch, final boolean modded
	) {
		final TestInput input = ctx.getInput();

		// Flat gallop, a running jump, and stopping.
		int x = this.nextLane();
		this.mount(ctx, server, connection, x);
		check("horse gets the new controls only with the mod on both sides",
			ctx.computeOnClient(mc -> ((RideStateHolder) mc.player.getVehicle()).horsingaround$managed()) == modded);
		final double serverWidth = server.computeOnServer(s -> {
			final Entity horse = connection.getServerPlayer().getVehicle();
			return horse == null ? 0.0 : (double) horse.getBbWidth();
		});
		// The server narrows a player-ridden horse's box either way: only more permissive than a vanilla client's.
		check("server's box for the ridden horse (width, blocks)", serverWidth, 0.85, 0.95);
		gallop(ctx, input, modded);
		if (modded) {
			check("gallops", ctx.computeOnClient(mc -> ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().gait) == RideTuning.GALLOP);
			final double attribute = ctx.computeOnClient(mc -> ((LivingEntity) mc.player.getVehicle()).getAttributeValue(Attributes.MOVEMENT_SPEED));
			final double top = attribute * RideTuning.TERMINAL_VELOCITY_FACTOR * RideTuning.GAIT_SPEED[RideTuning.GALLOP];
			check("at its top speed (blocks/tick)", averageSpeed(ctx, 10), top * 0.85, top * 1.15);
		}
		input.pressKey(o -> o.keyJump);
		ctx.waitTicks(30);
		this.stopAndCompare(ctx, input, server, connection, watch, "flat gallop and a running jump");

		// A leafy hedge across the way.
		x = this.nextLane();
		server.runCommand(String.format(Locale.ROOT, "fill %d %d -30 %d %d -32 minecraft:oak_leaves[persistent=true]", x - 3, GROUND, x + 3, GROUND + 3));
		this.mount(ctx, server, connection, x);
		gallop(ctx, input, modded);
		ctx.waitTicks(60);
		final double past = -ctx.computeOnClient(mc -> mc.player.getVehicle().getZ());
		if (modded) {
			check("rides through a leafy hedge (blocks past the start)", past, 34.0, 200.0);
		} else {
			check("without the mod, the hedge stops the horse (blocks past the start)", past, 0.0, 30.0);
		}
		this.stopAndCompare(ctx, input, server, connection, watch, "leaves");

		// Steps up and down, a 2-block ledge, a ditch.
		x = this.nextLane();
		server.runCommand(String.format(Locale.ROOT, "fill %d %d -14 %d %d -20 minecraft:stone", x - 3, GROUND, x + 3, GROUND));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d -36 %d %d -60 minecraft:stone", x - 3, GROUND, x + 3, GROUND + 1));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d -44 %d %d -44 minecraft:air", x - 3, GROUND, x + 3, GROUND + 1));
		this.mount(ctx, server, connection, x);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(5);
		input.pressKey(o -> o.keySprint);
		final StringBuilder trace = new StringBuilder();
		double highest = GROUND;
		for (int i = 0; i < 250; i++) {
			ctx.waitTick();
			highest = Math.max(highest, ctx.computeOnClient(mc -> mc.player.getVehicle() == null ? GROUND : mc.player.getVehicle().getY()));
			if (i % 10 != 9) {
				continue;
			}
			trace.append(ctx.<String, RuntimeException>computeOnClient(mc -> {
				final Entity h = mc.player.getVehicle();
				return h == null ? "- " : String.format(Locale.ROOT, "z%.1f y%.1f v%.2f | ", h.getZ(), h.getY(), Math.hypot(h.getX() - h.xo, h.getZ() - h.zo));
			}));
		}
		log("    trace: %s", trace);
		ctx.takeScreenshot("horsingaround_server_steps");
		final double far = -ctx.computeOnClient(mc -> mc.player.getVehicle().getZ());
		if (modded) {
			check("up and down a step, up a 2-block ledge and over a ditch (blocks along)", far, 40.0, 200.0);
			check("went up the ledge (highest feet, blocks above the ground)", highest - GROUND, 1.9, 2.5);
		}
		this.stopAndCompare(ctx, input, server, connection, watch, "steps, ledge and ditch");

		// Deep water: swim across and climb out.
		x = this.nextLane();
		server.runCommand(String.format(Locale.ROOT, "fill %d %d -10 %d %d -30 minecraft:air", x - 3, GROUND - 4, x + 3, GROUND - 1));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d -10 %d %d -30 minecraft:water", x - 3, GROUND - 4, x + 3, GROUND - 1));
		this.mount(ctx, server, connection, x);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		input.pressKey(o -> o.keySprint);
		ctx.waitTicks(400);
		final double across = -ctx.computeOnClient(mc -> mc.player.getVehicle() == null ? 0.0 : mc.player.getVehicle().getZ());
		if (modded) {
			check("swims across deep water and climbs out (blocks along)", across, 32.0, 400.0);
			check("rider still mounted after the water", ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse));
		}
		this.stopAndCompare(ctx, input, server, connection, watch, "deep water");

		// Trampling (decided by the server): chickens in the way of a gallop.
		x = this.nextLane();
		this.mount(ctx, server, connection, x);
		for (int i = 0; i < 4; i++) {
			server.runCommand(String.format(Locale.ROOT, "summon minecraft:chicken %d.5 %d %d {NoAI:1b,Tags:[\"victim\"]}", x, GROUND, -30 - i * 6));
		}
		gallop(ctx, input, modded);
		ctx.waitTicks(80);
		final int hurt = 4 - server.computeOnServer(s -> {
			int unhurt = 0;
			for (final Entity e : s.overworld().getAllEntities()) {
				if (e instanceof Chicken chicken && e.entityTags().contains("victim") && chicken.isAlive() && chicken.getHealth() >= chicken.getMaxHealth()) {
					unhurt++;
				}
			}
			return unhurt;
		});
		check("galloping tramples chickens in the way (of 4)", hurt, 2, 4);
		server.runCommand("kill @e[type=minecraft:chicken]");
		this.stopAndCompare(ctx, input, server, connection, watch, "trampling");
	}

	/** Stops, lets the server catch up, and checks it accepted every move and agrees where the horse is. */
	private void stopAndCompare(
		final ClientGameTestContext ctx, final TestInput input, final TestDedicatedServerContext server, final TestServerConnection connection, final LogWatch watch,
		final String what
	) {
		input.releaseKey(o -> o.keyUp);
		ctx.waitTicks(60);
		connection.waitForServerboundPackets();
		final Vec3 client = ctx.computeOnClient(mc -> mc.player.getVehicle() == null ? Vec3.ZERO : mc.player.getVehicle().position());
		final Vec3 onServer = server.computeOnServer(s -> {
			final Entity horse = connection.getServerPlayer().getVehicle();
			return horse == null ? Vec3.ZERO : horse.position();
		});
		check(what + ": client and server agree where the horse is (blocks apart)", client.distanceTo(onServer), 0.0, 0.1);
		final List<String> corrections = watch.corrections();
		corrections.forEach(line -> log("    %s", line));
		check(what + ": the server accepted every move (corrections)", corrections.size(), 0, 0);
		watch.clear();
		check(what + ": rider still mounted", ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse));
	}

	private int nextLane() {
		return 16 * this.lane++;
	}

	private static void gallop(final ClientGameTestContext ctx, final TestInput input, final boolean modded) {
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(2);
		if (modded) {
			for (int i = 0; i < 3; i++) {
				input.pressKey(o -> o.keySprint);
				ctx.waitTicks(6);
			}
		}
		ctx.waitTicks(50);
	}

	/** Summons a horse at (x, ground, 0) facing north and seats the player on it. */
	private void mount(final ClientGameTestContext ctx, final TestDedicatedServerContext server, final TestServerConnection connection, final int x) {
		final TestInput input = ctx.getInput();
		input.releaseKey(o -> o.keyUp);
		server.runCommand("ride @p dismount");
		server.runCommand("kill @e[type=minecraft:horse]");
		server.runCommand(String.format(Locale.ROOT, "tp @a %d %d 6 180 10", x, GROUND));
		ctx.waitTicks(10);
		connection.waitForChunksRender();
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:horse %d.5 %d 0.5 {Tame:1b,Rotation:[180f,0f],"
			+ "equipment:{saddle:{id:\"minecraft:saddle\",count:1}},attributes:[{id:\"minecraft:movement_speed\",base:0.225d}],Tags:[\"server_test\"]}", x, GROUND));
		for (int attempt = 0; attempt < 10 && !ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse); attempt++) {
			ctx.waitTicks(5);
			server.runCommand("ride @p mount @e[type=minecraft:horse,tag=server_test,limit=1]");
		}
		ctx.waitFor(mc -> mc.player.getVehicle() instanceof AbstractHorse);
		input.lookAt(180.0F, 10.0F);
		ctx.waitTicks(10);
	}

	private static double averageSpeed(final ClientGameTestContext ctx, final int ticks) {
		double total = 0.0;
		for (int i = 0; i < ticks; i++) {
			ctx.waitTick();
			total += ctx.computeOnClient(mc -> {
				final Entity v = mc.player.getVehicle();
				return v == null ? 0.0 : Math.hypot(v.getX() - v.xo, v.getZ() - v.zo);
			});
		}
		return total / ticks;
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
		System.out.println("[server-test] " + line);
	}

	private void writeReport() {
		final Path file = FabricLoader.getInstance().getGameDir().resolve("horsingaround-server-report.txt");
		try {
			Files.write(file, this.report);
		} catch (final IOException e) {
			throw new RuntimeException("Could not write " + file, e);
		}
	}
}
