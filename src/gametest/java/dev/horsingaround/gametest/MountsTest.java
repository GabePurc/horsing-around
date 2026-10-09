package dev.horsingaround.gametest;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.client.RideCamera;
import dev.horsingaround.net.ServerRules;
import dev.horsingaround.ride.RideState;
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
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Every vanilla mount, ridden with simulated keys: the horse family (horse, donkey, mule, skeleton and zombie horse)
 * gets the new controls; camels, camel husks, llamas, pigs, striders, happy ghasts, nautiluses, boats and minecarts must
 * ride exactly as in vanilla with nothing of this mod (camera, controls, rider pose) getting in the way. Also mob
 * riders (zombie and skeleton horsemen, a husk on a camel husk), a skeleton horse walking the bottom of a lake as in
 * vanilla, and the switch-off when the server doesn't run the mod. Writes {@code horsingaround-mounts-report.txt}.
 *
 * <p>Run with {@code ./gradlew runClientGameTest -Ptests=mounts}.
 */
public final class MountsTest implements FabricClientGameTest {
	/** Superflat: grass at y -61, so feet at -60. */
	private static final int GROUND = -60;

	private final List<String> report = new ArrayList<>();
	private int failures;
	private int lane;

	/** A mount to ride: its id, summon data, what the rider holds to steer it, and where it rides. */
	private record Mount(String id, String data, String hold, Arena arena) {
	}

	private enum Arena {
		GROUND, LAVA, WATER, AIR
	}

	private static final String SADDLE = "equipment:{saddle:{id:\"minecraft:saddle\",count:1}}";
	private static final String FIREPROOF = "active_effects:[{id:\"minecraft:fire_resistance\",duration:-1,show_particles:0b}]";

	private static final List<Mount> HORSES = List.of(
		new Mount("horse", "Tame:1b," + SADDLE, null, Arena.GROUND),
		new Mount("donkey", "Tame:1b," + SADDLE, null, Arena.GROUND),
		new Mount("mule", "Tame:1b," + SADDLE, null, Arena.GROUND),
		// (Undead horses burn in the sun.)
		new Mount("skeleton_horse", "Tame:1b," + SADDLE + "," + FIREPROOF, null, Arena.GROUND),
		new Mount("zombie_horse", "Tame:1b," + SADDLE + "," + FIREPROOF, null, Arena.GROUND)
	);

	private static final List<Mount> VANILLA = List.of(
		new Mount("camel", "Tame:1b," + SADDLE, null, Arena.GROUND),
		new Mount("camel_husk", "Tame:1b," + SADDLE, null, Arena.GROUND),
		new Mount("pig", SADDLE, "carrot_on_a_stick", Arena.GROUND),
		new Mount("strider", SADDLE, "warped_fungus_on_a_stick", Arena.LAVA),
		new Mount("happy_ghast", "equipment:{body:{id:\"minecraft:white_harness\",count:1}}", null, Arena.AIR),
		new Mount("nautilus", "Tame:1b," + SADDLE, null, Arena.WATER),
		new Mount("zombie_nautilus", "Tame:1b," + SADDLE, null, Arena.WATER)
	);

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!System.getProperty("horsingaround.tests", "mounts").contains("mounts")) {
			return;
		}
		boolean completed = false;
		try (LogWatch watch = LogWatch.begin();
			TestSingleplayerContext world = ctx.worldBuilder()
				.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			final TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_mobs false");
			world.getConnection().waitForChunksRender();
			final TestInput input = ctx.getInput();

			section("The server handshake");
			check("the client knows singleplayer's server runs the mod", ctx.computeOnClient(mc -> ServerRules.present));

			for (final Mount mount : HORSES) {
				horseFamily(ctx, input, world, mount);
			}
			skeletonHorseUnderwater(ctx, input, world);
			for (final Mount mount : VANILLA) {
				vanillaMount(ctx, input, world, mount);
			}
			llama(ctx, input, world);
			vehicles(ctx, input, world);
			mobRiders(ctx, world);
			serverWithoutTheMod(ctx, input, world);

			section("Log");
			final List<String> problems = watch.matching("horsingaround", "Exception", "moved wrongly", "moved too quickly");
			problems.forEach(line -> log("  %s", line));
			check("no server corrections, exceptions or mod warnings while riding every mount", problems.isEmpty());
			completed = true;
		} finally {
			if (!completed) {
				this.failures++;
				log("INCOMPLETE: the run stopped early (see the log for the exception)");
			}
			writeReport();
		}
		TestSummary.failed(this.failures, "horsingaround-mounts-report.txt");
	}

	// ---- The horse family: the new controls ----

	private void horseFamily(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final Mount mount) {
		section("Riding a " + mount.id + " (new controls)");
		firstPerson(ctx);
		final Entity vehicle = mount(ctx, world, mount, nextLane());
		final boolean managed = vehicle instanceof RideStateHolder holder && holder.horsingaround$managed();
		check("gets the new controls", managed);
		if (!managed) {
			dismount(ctx, input);
			return;
		}
		check("camera switched to third person", ctx.computeOnClient(mc -> mc.options.getCameraType()) == CameraType.THIRD_PERSON_BACK);
		check("ridden, the box narrows to the body (width, blocks)", ctx.computeOnClient(mc -> (double) mc.player.getVehicle().getBbWidth()), 0.6, 1.0);
		final double attribute = ctx.computeOnClient(mc -> ((LivingEntity) mc.player.getVehicle()).getAttributeValue(Attributes.MOVEMENT_SPEED));
		final double gallop = attribute * RideTuning.TERMINAL_VELOCITY_FACTOR * RideTuning.GAIT_SPEED[RideTuning.GALLOP];
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(2);
		for (int i = 0; i < 3; i++) {
			input.pressKey(o -> o.keySprint);
			ctx.waitTicks(6);
		}
		ctx.waitTicks(50);
		final double speed = averageSpeed(ctx, 10);
		check("gallops at its own top speed (blocks/tick)", speed, gallop * 0.85, gallop * 1.15);
		check("gait is gallop", ride(ctx, s -> s.gait) == RideTuning.GALLOP);
		check("stamina drains at a gallop", ride(ctx, s -> s.stamina), 0.0, 0.99);
		screenshot(ctx, "mount_" + mount.id + "_gallop");
		input.pressKey(o -> o.keyJump);
		double highest = 0.0;
		final double groundY = ctx.computeOnClient(mc -> mc.player.getVehicle().getY());
		for (int i = 0; i < 15; i++) {
			ctx.waitTick();
			highest = Math.max(highest, ctx.computeOnClient(mc -> mc.player.getVehicle().getY()) - groundY);
		}
		check("jumps on a press (height, blocks)", highest, 0.8, 3.0);
		input.releaseKey(o -> o.keyUp);
		ctx.waitTicks(60);
		dismount(ctx, input);
		check("camera back to first person after dismounting", ctx.computeOnClient(mc -> mc.options.getCameraType()) == CameraType.FIRST_PERSON);
	}

	/**
	 * Vanilla skeleton horses don't float: they walk along the bottom with their rider (and aren't slowed much). A
	 * horse in the same water floats and swims.
	 */
	private void skeletonHorseUnderwater(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("A skeleton horse walks the bottom of a lake (as in vanilla); a horse swims");
		for (final String id : new String[] {"skeleton_horse", "horse"}) {
			final int x = nextLane();
			final TestServerContext server = world.getServer();
			// A glass-walled channel 4 deep, 40 long, from z 2 northward.
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:glass", x - 3, GROUND, -40, x + 3, GROUND + 4, 4));
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:water", x - 2, GROUND, -40, x + 2, GROUND + 3, 3));
			final Entity horse = mount(ctx, world, new Mount(id, "Tame:1b," + SADDLE + "," + FIREPROOF, null, Arena.WATER), x);
			final double startZ = horse.getZ();
			input.lookAt(180.0F, 10.0F);
			input.holdKey(o -> o.keyUp);
			double lowest = Double.MAX_VALUE;
			double highest = -Double.MAX_VALUE;
			for (int i = 0; i < 80; i++) {
				ctx.waitTick();
				final double y = ctx.computeOnClient(mc -> mc.player.getVehicle() == null ? Double.NaN : mc.player.getVehicle().getY());
				if (i > 20 && !Double.isNaN(y)) {
					lowest = Math.min(lowest, y);
					highest = Math.max(highest, y);
				}
			}
			final double travelled = startZ - ctx.computeOnClient(mc -> mc.player.getVehicle() == null ? startZ : mc.player.getVehicle().getZ());
			if (id.equals("skeleton_horse")) {
				sideScreenshot(ctx, "mount_skeleton_horse_underwater");
				check("skeleton horse stays on the bottom (highest feet above the floor, blocks)", highest - GROUND, -0.1, 1.0);
				check("skeleton horse keeps going underwater (blocks in 4 s)", travelled, 6.0, 60.0);
			} else {
				check("horse floats up to swim (lowest feet above the floor, blocks)", lowest - GROUND, 1.0, 4.0);
				check("horse swims on (blocks in 4 s)", travelled, 3.0, 60.0);
			}
			check("rider still mounted", ctx.computeOnClient(mc -> mc.player.getVehicle() != null));
			input.releaseKey(o -> o.keyUp);
			dismount(ctx, input);
		}
	}

	// ---- Everything else: exactly vanilla ----

	private void vanillaMount(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final Mount mount) {
		section("Riding a " + mount.id + " (vanilla controls)");
		firstPerson(ctx);
		final int x = nextLane();
		final TestServerContext server = world.getServer();
		if (mount.arena == Arena.LAVA) {
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:lava", x - 2, GROUND - 1, -30, x + 2, GROUND - 1, 2));
		} else if (mount.arena == Arena.WATER) {
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:glass", x - 3, GROUND, -30, x + 3, GROUND + 5, 4));
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:water", x - 2, GROUND, -30, x + 2, GROUND + 4, 3));
		}
		if (mount.hold != null) {
			server.runCommand("item replace entity @p weapon.mainhand with minecraft:" + mount.hold);
		}
		final Entity vehicle = mount(ctx, world, mount, x);
		check("no new controls", !(vehicle instanceof RideStateHolder holder) || !holder.horsingaround$managed());
		check("camera left in first person", ctx.computeOnClient(mc -> mc.options.getCameraType()) == CameraType.FIRST_PERSON);
		check("riding camera not engaged", !ctx.computeOnClient(mc -> RideCamera.isRiding()));
		check("box not narrowed", !(vehicle instanceof RideStateHolder holder) || !holder.horsingaround$ride().narrow);
		final Vec3 start = ctx.computeOnClient(mc -> mc.player.getVehicle().position());
		input.lookAt(180.0F, mount.arena == Arena.AIR || mount.arena == Arena.WATER ? 0.0F : 10.0F);
		ctx.waitTicks(mount.arena == Arena.AIR ? 30 : 5);
		input.holdKey(o -> o.keyUp);
		// A camel that just sat down takes about 5 s to sit and stand up again before it walks on.
		ctx.waitTicks(mount.id.startsWith("camel") ? 160 : 80);
		input.releaseKey(o -> o.keyUp);
		final double travelled = ctx.computeOnClient(mc -> mc.player.getVehicle() == null ? 0.0 : mc.player.getVehicle().position().distanceTo(start));
		check("rides forward with vanilla controls (blocks)", travelled, 1.0, 80.0);
		check("rider still mounted", ctx.computeOnClient(mc -> mc.player.getVehicle() != null));
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTick();
		screenshot(ctx, "mount_" + mount.id);
		firstPerson(ctx);
		if (mount.id.startsWith("camel")) {
			// Vanilla's camel dash: hold jump to charge, let go to dash.
			input.holdKey(o -> o.keyUp);
			ctx.waitTicks(10);
			input.holdKey(o -> o.keyJump);
			ctx.waitTicks(15);
			input.releaseKey(o -> o.keyJump);
			double fastest = 0.0;
			for (int i = 0; i < 10; i++) {
				ctx.waitTick();
				fastest = Math.max(fastest, ctx.computeOnClient(mc -> {
					final Entity v = mc.player.getVehicle();
					return v == null ? 0.0 : Math.hypot(v.getX() - v.xo, v.getZ() - v.zo);
				}));
			}
			input.releaseKey(o -> o.keyUp);
			check("camel still dashes with a charged jump (fastest blocks/tick)", fastest, 0.4, 3.0);
			ctx.waitTicks(40);
		}
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
		dismount(ctx, input);
	}

	/** Llamas can be sat on but not steered. */
	private void llama(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Sitting on a llama (can't be steered, as in vanilla)");
		firstPerson(ctx);
		final Entity llama = mount(ctx, world, new Mount("llama", "Tame:1b", null, Arena.GROUND), nextLane());
		check("no new controls", !((RideStateHolder) llama).horsingaround$managed());
		check("camera left in first person", ctx.computeOnClient(mc -> mc.options.getCameraType()) == CameraType.FIRST_PERSON);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(40);
		input.releaseKey(o -> o.keyUp);
		check("rider still sitting", ctx.computeOnClient(mc -> mc.player.getVehicle() != null));
		dismount(ctx, input);
	}

	private void vehicles(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Boats and minecarts");
		for (final String id : new String[] {"oak_boat", "minecart"}) {
			firstPerson(ctx);
			mount(ctx, world, new Mount(id, "", null, Arena.GROUND), nextLane());
			input.holdKey(o -> o.keyUp);
			ctx.waitTicks(20);
			input.releaseKey(o -> o.keyUp);
			check(id + ": camera left in first person", ctx.computeOnClient(mc -> mc.options.getCameraType()) == CameraType.FIRST_PERSON);
			check(id + ": still aboard", ctx.computeOnClient(mc -> mc.player.getVehicle() != null));
			dismount(ctx, input);
		}
	}

	// ---- Mob riders ----

	/**
	 * Zombie and skeleton horsemen, and a husk on a camel husk: the mob steers (vanilla AI), the horse keeps its full
	 * box, and the riders sit in the saddle (see the screenshot).
	 */
	private void mobRiders(final ClientGameTestContext ctx, final TestSingleplayerContext world) {
		section("Mob riders");
		final TestServerContext server = world.getServer();
		server.runCommand("difficulty easy");
		final int x = nextLane();
		server.runCommand(String.format(Locale.ROOT, "tp @p %d %d 7 180 15", x, GROUND));
		// Glass between them: a zombie horseman's spear knocks whatever it hits off its mount (vanilla), and wandering into the
		// skeleton horseman it did, now and then.
		server.runCommand(String.format(Locale.ROOT, "fill %d %d -12 %d %d 6 minecraft:glass", x - 2, GROUND, x - 2, GROUND + 3));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d -12 %d %d 6 minecraft:glass", x + 2, GROUND, x + 2, GROUND + 3));
		ctx.waitTicks(10);
		// (Helmets on the riders and fire resistance on the horses: the undead burn, and flee, in the sun.)
		server.runCommand(String.format(Locale.ROOT,
			"summon minecraft:zombie_horse %d %d 0 {Tags:[\"horseman\"],PersistenceRequired:1b,%s,Passengers:[{id:\"minecraft:zombie\",PersistenceRequired:1b,%s,"
				+ "equipment:{mainhand:{id:\"minecraft:iron_spear\",count:1},head:{id:\"minecraft:iron_helmet\",count:1}}}]}", x - 4, GROUND, FIREPROOF, FIREPROOF));
		server.runCommand(String.format(Locale.ROOT,
			"summon minecraft:skeleton_horse %d %d 0 {Tags:[\"horseman\"],PersistenceRequired:1b,%s,Passengers:[{id:\"minecraft:skeleton\",PersistenceRequired:1b,%s,"
				+ "equipment:{mainhand:{id:\"minecraft:bow\",count:1},head:{id:\"minecraft:iron_helmet\",count:1}}}]}", x, GROUND, FIREPROOF, FIREPROOF));
		server.runCommand(String.format(Locale.ROOT,
			"summon minecraft:camel_husk %d %d 0 {Tags:[\"horseman\"],PersistenceRequired:1b,Passengers:[{id:\"minecraft:husk\",PersistenceRequired:1b}]}", x + 4, GROUND));
		ctx.waitTicks(100);
		ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		screenshot(ctx, "mount_mob_riders");
		final StringBuilder riders = new StringBuilder();
		final int[] counts = server.computeOnServer(running -> {
			int ridden = 0;
			int narrow = 0;
			for (final Entity e : running.overworld().getAllEntities()) {
				if (e.entityTags().contains("horseman")) {
					riders.append(e.getType().toShortString()).append(e.isVehicle() ? " ridden by " + e.getFirstPassenger().getType().toShortString() : " empty").append("; ");
					if (e.isVehicle()) {
						ridden++;
					}
					if (e instanceof RideStateHolder holder && holder.horsingaround$ride().narrow) {
						narrow++;
					}
				}
			}
			return new int[] {ridden, narrow};
		});
		log("  %s", riders);
		check("all three mob riders still mounted after 5 s", counts[0], 3, 3);
		check("mob-ridden horses keep their full box", counts[1], 0, 0);
		server.runCommand("kill @e[tag=horseman]");
		server.runCommand("kill @e[type=minecraft:zombie]");
		server.runCommand("kill @e[type=minecraft:skeleton]");
		server.runCommand("kill @e[type=minecraft:husk]");
		server.runCommand("difficulty peaceful");
	}

	// ---- A server without the mod ----

	/**
	 * A server without the mod never sends its rules, so the client keeps horses vanilla. Simulated here by dropping
	 * the rules on the client mid-ride, then the server sending them again (as when the host changes settings).
	 */
	private void serverWithoutTheMod(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Switching off when the server doesn't run the mod");
		firstPerson(ctx);
		mount(ctx, world, HORSES.get(0), nextLane());
		check("managed with the server's rules", ctx.computeOnClient(mc -> ((RideStateHolder) mc.player.getVehicle()).horsingaround$managed()));
		ctx.runOnClient(mc -> ServerRules.reset());
		ctx.waitTicks(2);
		check("without them, the horse has vanilla controls", !ctx.computeOnClient(mc -> ((RideStateHolder) mc.player.getVehicle()).horsingaround$managed()));
		check("and the riding camera lets go", !ctx.computeOnClient(mc -> RideCamera.isRiding()));
		check("and the box is vanilla", ctx.computeOnClient(mc -> (double) mc.player.getVehicle().getBbWidth()), 1.3, 1.5);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(40);
		final double vanillaSpeed = averageSpeed(ctx, 10);
		input.releaseKey(o -> o.keyUp);
		final double attribute = ctx.computeOnClient(mc -> ((LivingEntity) mc.player.getVehicle()).getAttributeValue(Attributes.MOVEMENT_SPEED));
		check("rides at vanilla's top speed (blocks/tick)", vanillaSpeed, attribute * 1.5, attribute * 5.0);
		ctx.waitTicks(20);
		world.getServer().runOnServer(server -> HorsingAround.sendRules());
		ctx.waitTicks(3);
		check("the server's rules switch it back on", ctx.computeOnClient(mc -> ((RideStateHolder) mc.player.getVehicle()).horsingaround$managed()));
		dismount(ctx, input);
	}

	// ---- helpers ----

	private int nextLane() {
		return 20 + 16 * this.lane++;
	}

	/** Summons the mount at (x, feet height, 0) facing north and seats the player on it. */
	private static Entity mount(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Mount mount, final int x) {
		final TestServerContext server = world.getServer();
		final double y = mount.arena == Arena.AIR ? GROUND + 6 : mount.arena == Arena.LAVA ? GROUND - 1 : GROUND;
		server.runCommand(String.format(Locale.ROOT, "tp @p %d %d 6 180 10", x, GROUND));
		ctx.waitTicks(5);
		world.getConnection().waitForChunksRender();
		final String tag = "mount_" + x;
		final String data = mount.data.isEmpty() ? "" : mount.data + ",";
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:%s %d.5 %.1f 0.5 {%sRotation:[180f,0f],Tags:[\"%s\"]}", mount.id, x, y, data, tag));
		for (int attempt = 0; attempt < 10 && !ctx.computeOnClient(mc -> mc.player.getVehicle() != null); attempt++) {
			ctx.waitTicks(5);
			server.runCommand("ride @p mount @e[tag=" + tag + ",limit=1]");
		}
		ctx.waitFor(mc -> mc.player.getVehicle() != null);
		ctx.waitTicks(5);
		return ctx.computeOnClient(mc -> mc.player.getVehicle());
	}

	private void dismount(final ClientGameTestContext ctx, final TestInput input) {
		input.releaseKey(o -> o.keyUp);
		input.holdKeyFor(o -> o.keyShift, 3);
		ctx.waitTicks(5);
		check("dismounts with sneak", ctx.computeOnClient(mc -> mc.player.getVehicle() == null));
	}

	private static void firstPerson(final ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private static <T> T ride(final ClientGameTestContext ctx, final java.util.function.Function<RideState, T> read) {
		return ctx.computeOnClient(mc -> read.apply(((RideStateHolder) mc.player.getVehicle()).horsingaround$ride()));
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

	private void screenshot(final ClientGameTestContext ctx, final String name) {
		log("  screenshot %s -> %s", name, ctx.takeScreenshot("horsingaround_" + name));
	}

	/** Swings the view 90 degrees for one rendered frame to see the mount broadside (no tick passes). */
	private void sideScreenshot(final ClientGameTestContext ctx, final String name) {
		final CameraType camera = ctx.computeOnClient(mc -> mc.options.getCameraType());
		final float yaw = ctx.computeOnClient(mc -> mc.player.getYRot());
		ctx.runOnClient(mc -> {
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			mc.player.setYRot(yaw + 90.0F);
			mc.player.yRotO = yaw + 90.0F;
		});
		screenshot(ctx, name);
		ctx.runOnClient(mc -> {
			mc.player.setYRot(yaw);
			mc.player.yRotO = yaw;
			mc.options.setCameraType(camera);
		});
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
		System.out.println("[mounts-test] " + line);
	}

	private void writeReport() {
		final Path file = FabricLoader.getInstance().getGameDir().resolve("horsingaround-mounts-report.txt");
		try {
			Files.write(file, this.report);
		} catch (final IOException e) {
			throw new RuntimeException("Could not write " + file, e);
		}
	}
}
