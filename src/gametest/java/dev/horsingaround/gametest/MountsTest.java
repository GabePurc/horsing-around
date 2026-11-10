package dev.horsingaround.gametest;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.client.RideCamera;
import dev.horsingaround.client.render.SaddleMotion;
import dev.horsingaround.net.ServerRules;
import dev.horsingaround.ride.HorseConfig;
import dev.horsingaround.ride.IcyCompat;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.ride.RideTuning;
import java.io.IOException;
import java.lang.reflect.Field;
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
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
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
public final class MountsTest implements ClientGameTest {
	/** Superflat: grass at y -61, so feet at -60. */
	private static final int GROUND = -60;

	private final List<String> report = new ArrayList<>();
	private int failures;
	private int lane;

	/** A mount to ride: its id, summon data, what the rider holds to steer it, and where it rides. */
	private record Mount(String id, String data, String hold, Arena arena) {
	}

	private enum Arena {
		/** SURFACE: summoned 2 up in 4-deep water, under the channel's glass lid (a horse summoned on the bottom throws its rider, eyes under water, before it can float up). */
		GROUND, LAVA, WATER, SURFACE, AIR
	}

	private static final String SADDLE = Versions.saddle();
	private static final String FIREPROOF = Versions.endlessEffect("fire_resistance");

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
		new Mount("pig", Versions.pigSaddle(), "carrot_on_a_stick", Arena.GROUND),
		new Mount("strider", Versions.pigSaddle(), "warped_fungus_on_a_stick", Arena.LAVA),
		new Mount("happy_ghast", "equipment:{body:{id:\"minecraft:white_harness\",count:1}}", null, Arena.AIR),
		new Mount("nautilus", "Tame:1b," + SADDLE, null, Arena.WATER),
		new Mount("zombie_nautilus", "Tame:1b," + SADDLE, null, Arena.WATER)
	);

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!System.getProperty("horsingaround.tests", "mounts").contains("mounts")) {
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
			server.runCommand(Versions.rule("spawn_mobs") + " false");
			world.getConnection().waitForChunksRender();
			final TestInput input = ctx.getInput();

			section("The server handshake");
			check("the client knows singleplayer's server runs the mod", ctx.computeOnClient(mc -> ServerRules.present));

			for (final Mount mount : HORSES) {
				horseFamily(ctx, input, world, mount);
			}
			skeletonHorseUnderwater(ctx, input, world);
			// (Every one this version has.)
			for (final Mount mount : VANILLA) {
				if (Versions.hasEntity(mount.id)) {
					vanillaMount(ctx, input, world, mount);
				}
			}
			llama(ctx, input, world);
			vehicles(ctx, input, world);
			mobRiders(ctx, world);
			serverWithoutTheMod(ctx, input, world);
			if (IcyCompat.LOADED) {
				icysBreeds(ctx, input, world);
				icyTraits(ctx, input, world);
			}

			section("Log");
			final List<String> problems = watch.matching("horsingaround", "Exception", "moved wrongly", "moved too quickly", "was expected to be controlling");
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
			final Entity horse = mount(ctx, world, new Mount(id, "Tame:1b," + SADDLE + "," + FIREPROOF, null, id.equals("horse") ? Arena.SURFACE : Arena.WATER), x);
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
		// Where the rider sits on the mount, from the side: moving, then standing.
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTick();
		sideScreenshot(ctx, "mount_" + mount.id + "_side_moving");
		input.releaseKey(o -> o.keyUp);
		final double travelled = ctx.computeOnClient(mc -> mc.player.getVehicle() == null ? 0.0 : mc.player.getVehicle().position().distanceTo(start));
		// (A nautilus goes by dashes, and in a pack of mods some runs catch it after a short one.)
		check("rides forward with vanilla controls (blocks)", travelled, mount.id.contains("nautilus") ? 0.4 : 1.0, 80.0);
		check("rider still mounted", ctx.computeOnClient(mc -> mc.player.getVehicle() != null));
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTick();
		screenshot(ctx, "mount_" + mount.id);
		ctx.waitTicks(20);
		sideScreenshot(ctx, "mount_" + mount.id + "_side");
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
		// (Dismount first: taking the carrot or fungus on a stick away while riding makes the server expect no control.)
		dismount(ctx, input);
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
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
		// (Boats were one type before 1.21.2.)
		for (final String id : new String[] {Versions.hasEntity("oak_boat") ? "oak_boat" : "boat", "minecart"}) {
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
		// A glass pen each, closed in front of the player: riders that meet fight (a zombie horseman's spear knocks whatever
		// it hits off its mount, and a skeleton's stray arrow starts a fight it can lose), and through glass none sees the
		// player to go for them.
		for (final int wall : new int[] {x - 6, x - 2, x + 2, x + 6}) {
			server.runCommand(String.format(Locale.ROOT, "fill %d %d -12 %d %d 5 minecraft:glass", wall, GROUND, wall, GROUND + 3));
		}
		server.runCommand(String.format(Locale.ROOT, "fill %d %d -12 %d %d -12 minecraft:glass", x - 6, GROUND, x + 6, GROUND + 3));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d 5 %d %d 5 minecraft:glass", x - 6, GROUND, x + 6, GROUND + 3));
		ctx.waitTicks(10);
		// (Helmets on the riders and fire resistance on the horses: the undead burn, and flee, in the sun. Tame, as vanilla's
		// horsemen are: before 26.2 an untamed horse throws even a mob off now and then.)
		server.runCommand(String.format(Locale.ROOT,
			"summon minecraft:zombie_horse %d %d 0 {Tags:[\"horseman\"],Tame:1b,PersistenceRequired:1b,%s,Passengers:[{id:\"minecraft:zombie\",PersistenceRequired:1b,%s,"
				+ Versions.gear(Versions.hasItem("iron_spear") ? "iron_spear" : "iron_sword", "iron_helmet") + "}]}", x - 4, GROUND, FIREPROOF, FIREPROOF));
		server.runCommand(String.format(Locale.ROOT,
			"summon minecraft:skeleton_horse %d %d 0 {Tags:[\"horseman\"],Tame:1b,PersistenceRequired:1b,%s,Passengers:[{id:\"minecraft:skeleton\",PersistenceRequired:1b,%s,"
				+ Versions.gear("bow", "iron_helmet") + "}]}", x, GROUND, FIREPROOF, FIREPROOF));
		// (Camel husks are 1.21.11 on.)
		final int pairs = Versions.hasEntity("camel_husk") ? 3 : 2;
		if (pairs == 3) {
			server.runCommand(String.format(Locale.ROOT,
				"summon minecraft:camel_husk %d %d 0 {Tags:[\"horseman\"],Tame:1b,PersistenceRequired:1b,Passengers:[{id:\"minecraft:husk\",PersistenceRequired:1b}]}", x + 4, GROUND));
		}
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
		check("every mob rider still mounted after 5 s", counts[0], pairs, pairs);
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
		// (Its own type's width, unnarrowed: with Icy's Better Horses the vanilla horse has become one of its breeds.)
		check("and the box is the horse's own (width over its type's)",
			//? if >=1.20.5 {
			ctx.computeOnClient(mc -> (double) (mc.player.getVehicle().getBbWidth() / mc.player.getVehicle().getType().getDimensions().width())), 0.99, 1.01);
			//?} else
			/*ctx.computeOnClient(mc -> (double) (mc.player.getVehicle().getBbWidth() / mc.player.getVehicle().getType().getDimensions().width)), 0.99, 1.01);*/
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(40);
		final double vanillaSpeed = averageSpeed(ctx, 10);
		input.releaseKey(o -> o.keyUp);
		final double attribute = ctx.computeOnClient(mc -> ((LivingEntity) mc.player.getVehicle()).getAttributeValue(Attributes.MOVEMENT_SPEED));
		check("rides at vanilla's top speed (blocks/tick)", vanillaSpeed, attribute * 1.5, attribute * 5.0);
		ctx.waitTicks(20);
		world.getServer().runOnServer(HorsingAround::sendRules);
		ctx.waitTicks(3);
		check("the server's rules switch it back on", ctx.computeOnClient(mc -> ((RideStateHolder) mc.player.getVehicle()).horsingaround$managed()));
		dismount(ctx, input);
	}

	// ---- Icy's Better Horses (installed in the icys compat pack) ----

	/**
	 * With Icy's Better Horses installed the world's riding style picks whose riding horses use. This mod's (the default):
	 * it rides Icy's breeds too, at its own gaits, and Icy's gear keys and auto-ride do nothing. Icy's: this mod leaves
	 * the horse alone on both sides, and Icy's gears hold it to their paces again.
	 */
	private void icysBreeds(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Icy's Better Horses: a breed horse, this mod's riding");
		firstPerson(ctx);
		final Entity horse = mount(ctx, world, new Mount(IcyCompat.MOD_ID + ":friesian_horse", "Tame:1b," + SADDLE, null, Arena.GROUND), nextLane());
		check("the client heard the riding style is this mod's", !ctx.computeOnClient(mc -> ServerRules.icyRides));
		final boolean managed = horse instanceof RideStateHolder holder && holder.horsingaround$managed();
		check("the breed horse gets the new controls", managed);
		if (!managed) {
			dismount(ctx, input);
			return;
		}
		check("on the server too", managedOnServer(world, horse.getId()));
		check("Icy's gear keys pick no gear", ctx.computeOnClient(mc -> icyShift(mc.player.getVehicle(), 1)) == 0);
		check("Icy's gears cap no speed", icyGearSpeed(ctx, 1), 0.99, 1.01);
		final double attribute = ctx.computeOnClient(mc -> ((LivingEntity) mc.player.getVehicle()).getAttributeValue(Attributes.MOVEMENT_SPEED));
		final double gallop = attribute * RideTuning.TERMINAL_VELOCITY_FACTOR * RideTuning.GAIT_SPEED[RideTuning.GALLOP];
		input.lookAt(180.0F, 10.0F);
		input.pressKey(o -> o.keyUp);
		ctx.waitTicks(2);
		input.pressKey(o -> o.keyUp);
		ctx.waitTicks(5);
		check("a double tap of W leaves Icy's auto-ride off", !ctx.computeOnClient(mc -> icyAutoRide()));
		input.holdKey(o -> o.keyUp);
		for (int i = 0; i < 3; i++) {
			input.pressKey(o -> o.keySprint);
			ctx.waitTicks(6);
		}
		ctx.waitTicks(50);
		check("gallops at its own top speed (blocks/tick)", averageSpeed(ctx, 10), gallop * 0.85, gallop * 1.15);
		screenshot(ctx, "mount_icys_friesian_gallop");
		// Through a turn at a gallop: this mod's lean alone (Icy's model banks too, which doubled it), and the stride's
		// motion Icy's own, measured for the rider.
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		input.lookAt(240.0F, 10.0F);
		float lean = 0.0F;
		float icyBank = 0.0F;
		boolean measured = true;
		for (int i = 0; i < 12; i++) {
			ctx.waitTick();
			final float turnLean = ride(ctx, s -> s.lean(1.0F));
			lean = Math.max(lean, Math.abs(turnLean));
			icyBank = Math.max(icyBank, ctx.computeOnClient(mc -> icyModelBank(mc.player.getVehicle())));
			measured &= ctx.computeOnClient(mc -> SaddleMotion.frame() - ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().animatedFrame <= 1);
		}
		check("leans into the turn (degrees)", lean, 2.0, 20.0);
		check("Icy's model adds no lean of its own (degrees)", icyBank, 0.0, 0.5);
		check("the rider rides Icy's own stride motion, none generated", measured);
		screenshot(ctx, "mount_icys_friesian_turn");
		input.lookAt(180.0F, 10.0F);
		ctx.waitTicks(20);
		firstPerson(ctx);
		input.pressKey(o -> o.keyJump);
		double highest = 0.0;
		boolean reared = false;
		final double groundY = ctx.computeOnClient(mc -> mc.player.getVehicle().getY());
		for (int i = 0; i < 15; i++) {
			ctx.waitTick();
			highest = Math.max(highest, ctx.computeOnClient(mc -> mc.player.getVehicle().getY()) - groundY);
			reared |= ctx.computeOnClient(mc -> ((AbstractHorse) mc.player.getVehicle()).isStanding());
		}
		check("jumps on a press (height, blocks)", highest, 0.8, 3.0);
		check("without rearing", !reared);
		input.releaseKey(o -> o.keyUp);
		input.pressKey(o -> o.keyDown);
		ctx.waitTicks(60);

		section("Icy's Better Horses: switched to Icy's riding");
		setRidingStyle(ctx, world, HorseConfig.RidingStyle.ICYS);
		check("the client hears the switch", ctx.computeOnClient(mc -> ServerRules.icyRides));
		check("the horse has Icy's controls, not this mod's", !ctx.computeOnClient(mc -> ((RideStateHolder) mc.player.getVehicle()).horsingaround$managed()));
		check("on the server too", !managedOnServer(world, horse.getId()));
		check("Icy's gear keys pick a gear again", ctx.computeOnClient(mc -> icyShift(mc.player.getVehicle(), 1)) == 1);
		check("Icy's walking gear caps the speed again", icyGearSpeed(ctx, 1), 0.0, 0.1);
		ctx.computeOnClient(mc -> icyShift(mc.player.getVehicle(), -1));
		setRidingStyle(ctx, world, HorseConfig.RidingStyle.HORSING_AROUND);
		check("switched back, this mod rides it again", ctx.computeOnClient(mc -> ((RideStateHolder) mc.player.getVehicle()).horsingaround$managed()));
		dismount(ctx, input);
	}

	/**
	 * Icy's breeds' speed abilities on this mod's riding (BreedTraits), each against a breed without one (an American
	 * Paint): the Quarter Horse's burst from a standstill, the Thoroughbred's top end, the Arabian's endurance; and Icy's
	 * charge, now the trample's, bashing a hostile mob too big to trample.
	 */
	private void icyTraits(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Icy's Better Horses: breed traits on this mod's riding");
		firstPerson(ctx);
		final String breed = IcyCompat.MOD_ID + ":%s_horse";
		int x = nextLane();
		mount(ctx, world, new Mount(String.format(Locale.ROOT, breed, "american_paint"), "Tame:1b," + SADDLE, null, Arena.GROUND), x);
		world.getServer().runCommand(String.format(Locale.ROOT, "summon minecraft:husk %d.5 %d -40.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"bash\"]}", x, GROUND));
		final int plainLaunch = ticksToGallop(ctx, input);
		final float[] plain = gallopFor(ctx, input, 60);
		check("a plain breed has no trait (gallop bonus, stamina scale)", plain[1] == 0.0F && plain[2] == 1.0F);
		// (Bashed hard enough, it is gone.)
		final boolean bashed = world.getServer().computeOnServer(server -> server.overworld().getEntities(
			net.minecraft.world.level.entity.EntityTypeTest.forClass(LivingEntity.class), h -> h.entityTags().contains("bash")
		).stream().noneMatch(h -> h.getHealth() >= h.getMaxHealth()));
		check("galloping through a husk, Icy's breed bashes it (too big to trample)", bashed);
		dismount(ctx, input);

		mount(ctx, world, new Mount(String.format(Locale.ROOT, breed, "quarter"), "Tame:1b," + SADDLE, null, Arena.GROUND), nextLane());
		ctx.waitTicks(30);
		final int burstLaunch = ticksToGallop(ctx, input);
		check("Quarter Horse: from a standstill it reaches its gallop in about half the time (ticks, / a plain breed's)",
			burstLaunch / (double) plainLaunch, 0.35, 0.75);
		dismount(ctx, input);

		mount(ctx, world, new Mount(String.format(Locale.ROOT, breed, "thoroughbred"), "Tame:1b," + SADDLE, null, Arena.GROUND), nextLane());
		ticksToGallop(ctx, input);
		final float[] topEnd = gallopFor(ctx, input, 150);
		check("Thoroughbred: flat out, its gallop comes on (bonus)", topEnd[1], 0.09, 0.11);
		check("...and it goes faster (top speed / its gallop)", topEnd[3] / RideTuning.GAIT_SPEED[RideTuning.GALLOP], 1.05, 1.12);
		check("...tiring faster meanwhile (stamina scale)", topEnd[2], 1.4, 1.5);
		dismount(ctx, input);

		mount(ctx, world, new Mount(String.format(Locale.ROOT, breed, "arabian"), "Tame:1b," + SADDLE, null, Arena.GROUND), nextLane());
		ticksToGallop(ctx, input);
		final float[] endurance = gallopFor(ctx, input, 60);
		check("Arabian: galloping tires it slower (stamina used / a plain breed's)", endurance[0] / plain[0], 0.6, 0.8);
		dismount(ctx, input);
	}

	/** From where the horse is, W and three spurs: the ticks until it is at 95% of its gallop (the keys stay held). */
	private int ticksToGallop(final ClientGameTestContext ctx, final TestInput input) {
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		int ticks = 0;
		while (ticks < 300 && ride(ctx, s -> s.speed) < RideTuning.GAIT_SPEED[RideTuning.GALLOP] * 0.95F) {
			// (A spur before W is held, or a tick after another, doesn't count: four, six ticks apart once W is down.)
			if (ticks >= 2 && ticks < 26 && ticks % 6 == 2) {
				input.pressKey(o -> o.keySprint);
			}
			ctx.waitTick();
			ticks++;
		}
		final int took = ticks;
		log("  to a gallop: %d ticks; gait %d, speed %.3f, stamina %.2f, exhausted %s, accel x%.1f", took, ride(ctx, s -> s.gait), ride(ctx, s -> s.speed),
			ride(ctx, s -> s.stamina), ride(ctx, s -> s.exhausted), ride(ctx, s -> s.accelScale));
		return ticks;
	}

	/** Gallops on for {@code ticks}, then stops: the stamina it used, its gallop bonus and stamina scale at the end, and its top speed. */
	private static float[] gallopFor(final ClientGameTestContext ctx, final TestInput input, final int ticks) {
		final float before = ride(ctx, s -> s.stamina);
		float top = 0.0F;
		for (int i = 0; i < ticks; i++) {
			ctx.waitTick();
			top = Math.max(top, ride(ctx, s -> s.speed));
		}
		final float[] out = {before - ride(ctx, s -> s.stamina), ride(ctx, s -> s.gallopBonus), ride(ctx, s -> s.drainScale), top};
		input.releaseKey(o -> o.keyUp);
		input.pressKey(o -> o.keyDown);
		ctx.waitTicks(60);
		return out;
	}

	/** The host picks a riding style (as from the settings screen in singleplayer). */
	private static void setRidingStyle(final ClientGameTestContext ctx, final TestSingleplayerContext world, final HorseConfig.RidingStyle style) {
		world.getServer().runOnServer(server -> {
			HorseConfig.get().ridingStyle = style;
			HorsingAround.styleChanged(server);
		});
		ctx.waitTicks(3);
	}

	/** How far Icy's model of this horse banks, degrees (its root part's roll, as last drawn). */
	private static float icyModelBank(final Entity horse) {
		//? if >=1.21.2 {
		final Object model = ((LivingEntityRenderer<?, ?, ?>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(horse)).getModel();
		//?} else
		/*final Object model = ((LivingEntityRenderer<?, ?>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(horse)).getModel();*/
		try {
			final Field root = Class.forName("icy.betterhorses.net.client.render.BhHorseModel").getDeclaredField("rootPart");
			root.setAccessible(true);
			final ModelPart part = (ModelPart) root.get(model);
			return Math.abs(part.zRot - part.getInitialPose().zRot()) * Mth.RAD_TO_DEG;
		} catch (final ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean managedOnServer(final TestSingleplayerContext world, final int id) {
		return world.getServer().computeOnServer(server -> server.overworld().getEntity(id) instanceof RideStateHolder holder && holder.horsingaround$managed());
	}

	/** Presses Icy's gear key (up by one gear, or down) for this horse; the gear it lands in. */
	private static int icyShift(final Entity horse, final int by) {
		try {
			final Class<?> gears = Class.forName("icy.betterhorses.net.client.HorseGearController");
			return (int) gears.getMethod("shift", AbstractHorse.class, int.class).invoke(gears.getField("INSTANCE").get(null), horse, by);
		} catch (final ReflectiveOperationException e) {
			throw new IllegalStateException("Icy's gear controller changed", e);
		}
	}

	private static boolean icyAutoRide() {
		try {
			final Class<?> autoRide = Class.forName("icy.betterhorses.net.client.HorseAutodriveController");
			return (boolean) autoRide.getMethod("isActive").invoke(autoRide.getField("INSTANCE").get(null));
		} catch (final ReflectiveOperationException e) {
			throw new IllegalStateException("Icy's auto-ride changed", e);
		}
	}

	/** The share of full speed Icy's gaits let a horse in this gear go, as the rider's client sees it. */
	private static double icyGearSpeed(final ClientGameTestContext ctx, final int gear) {
		return ctx.computeOnClient(mc -> {
			try {
				return (double) (float) Class.forName("icy.betterhorses.net.BhGears").getMethod("riddenSpeed", int.class, float.class).invoke(null, gear, 1.0F);
			} catch (final ReflectiveOperationException e) {
				throw new IllegalStateException("Icy's gaits changed", e);
			}
		});
	}

	// ---- helpers ----

	private int nextLane() {
		return 20 + 16 * this.lane++;
	}

	/** Summons the mount at (x, feet height, 0) facing north and seats the player on it. */
	private static Entity mount(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Mount mount, final int x) {
		final TestServerContext server = world.getServer();
		final double y = mount.arena == Arena.AIR ? GROUND + 6 : mount.arena == Arena.LAVA ? GROUND - 1 : mount.arena == Arena.SURFACE ? GROUND + 2 : GROUND;
		server.runCommand(String.format(Locale.ROOT, "tp @p %d %d 6 180 10", x, GROUND));
		ctx.waitTicks(5);
		world.getConnection().waitForChunksRender();
		final String tag = "mount_" + x;
		final String data = mount.data.isEmpty() ? "" : mount.data + ",";
		final String id = mount.id.contains(":") ? mount.id : "minecraft:" + mount.id;
		server.runCommand(String.format(Locale.ROOT, "summon %s %d.5 %.1f 0.5 {%sRotation:[180f,0f],Tags:[\"%s\"]}", id, x, y, data, tag));
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
		final Path file = TestEnv.gameDir().resolve("horsingaround-mounts-report.txt");
		try {
			Files.write(file, this.report);
		} catch (final IOException e) {
			throw new RuntimeException("Could not write " + file, e);
		}
	}
}
