package dev.horsingaround.gametest;

import dev.horsingaround.ride.RideController;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.ride.RideTuning;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rides through natural-looking terrain built procedurally in the test world, the kind players meet in generated
 * worlds: messy forests with trunks of every size, bushes, fallen logs and boulders; mountains with uneven slopes,
 * shelves and cliffs (down and up); rolling hills; taiga with berry bushes, powder snow, campfires, cactus and lava; a
 * river to cross; badlands terraces. Several seeded layouts per kind, so nothing is a hand-placed perfect case. The
 * rider steers like a player: heads for a point, turns away when stuck. Checks what has to hold anywhere: no damage
 * from hazards, no fall that hurts more than the horse allows, never in lava, few crashes and dead ends, steady
 * progress, and what the ride code costs per tick.
 *
 * <p>Run with {@code ./gradlew runClientGameTest} (or {@code -Ptests=terrain} for just this one).
 */
public final class TerrainRideTest implements FabricClientGameTest {
	private static final String TAG = "terrain_test";
	/** Top block of the flat test world; horses stand at GROUND + 1. */
	private static final int GROUND = -61;
	private static final int SIZE = 64;
	private static final double SPEED_ATTRIBUTE = 0.225;
	private static final double GALLOP_SPEED = SPEED_ATTRIBUTE * RideTuning.TERMINAL_VELOCITY_FACTOR * RideTuning.GAIT_SPEED[RideTuning.GALLOP];
	/** Running head-on into something (more than CRASH_BLOCKED of the travel stopped) faster than this (a slow trot) is a crash. */
	private static final double CRASH_SPEED = SPEED_ATTRIBUTE * RideTuning.TERMINAL_VELOCITY_FACTOR * 0.45;
	private static final int MAX_TICKS = 600;
	/** Damage a horse or rider must never take on a ride. */
	private static final List<String> NEVER = List.of("cactus", "sweetBerryBush", "inFire", "onFire", "lava", "hotFloor", "freeze", "inWall", "drown", "campfire");

	private enum Kind {
		FOREST, DENSE_FOREST, MOUNTAIN_DOWN, MOUNTAIN_UP, HILLS, TAIGA, RIVER, BADLANDS
	}

	/** How the rider heads for the target: straight at it, or zigzagging like someone exploring. */
	private record Scenario(String name, Kind kind, long seed, boolean zigzag, double minPace, int maxStuck) {
	}

	private static final List<Scenario> SCENARIOS = List.of(
		new Scenario("mixed forest, layout 1", Kind.FOREST, 11, false, 0.3, 4),
		new Scenario("mixed forest, layout 2", Kind.FOREST, 12, false, 0.3, 4),
		new Scenario("dense dark-oak forest", Kind.DENSE_FOREST, 13, false, 0.2, 6),
		new Scenario("down a mountain, layout 1", Kind.MOUNTAIN_DOWN, 21, false, 0.15, 4),
		new Scenario("down a mountain, layout 2", Kind.MOUNTAIN_DOWN, 22, false, 0.15, 4),
		new Scenario("up a mountain", Kind.MOUNTAIN_UP, 23, false, 0.1, 6),
		new Scenario("rolling hills", Kind.HILLS, 31, false, 0.6, 1),
		new Scenario("taiga with berry bushes, powder snow, campfires, cactus and lava", Kind.TAIGA, 41, true, 0.25, 5),
		new Scenario("river crossing", Kind.RIVER, 51, false, 0.15, 3),
		new Scenario("badlands terraces", Kind.BADLANDS, 61, true, 0.15, 6)
	);

	/** Where a ride starts and where the rider is heading, both standing heights. */
	private record Route(BlockPos start, BlockPos target) {
	}

	private record Hurt(boolean horse, String type, float amount, float healthBefore, float maxHealth) {
	}

	private static final List<Hurt> HURTS = new CopyOnWriteArrayList<>();
	private static boolean listening;

	private final List<String> report = new ArrayList<>();
	private final List<Long> tickNanos = new ArrayList<>();
	private int failures;

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!System.getProperty("horsingaround.tests", "terrain").contains("terrain")) {
			return;
		}
		listen();
		boolean completed = false;
		try (TestSingleplayerContext world = ctx.worldBuilder()
			.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
			.create()) {
			final TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("difficulty normal");
			RideController.profile = true;
			int crashes = 0;
			final String only = System.getProperty("horsingaround.scenario", "");
			for (int i = 0; i < SCENARIOS.size(); i++) {
				if (SCENARIOS.get(i).name().contains(only)) {
					crashes += ride(ctx, world, SCENARIOS.get(i), 3000 + i * 100, 0);
				}
			}
			RideController.profile = false;
			summary(crashes);
			completed = true;
		} finally {
			RideController.profile = false;
			if (!completed) {
				this.failures++;
				log("");
				log("INCOMPLETE: the run stopped early (see the log for the exception)");
			}
			writeReport();
		}
		if (this.failures > 0) {
			throw new AssertionError(this.failures + " terrain ride checks failed; see horsingaround-terrain-report.txt");
		}
	}

	/** Builds the scenario's terrain, then rides it like a player. Returns the crashes. */
	private int ride(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Scenario scenario, final int ox, final int oz) {
		section(scenario.name());
		final TestServerContext server = world.getServer();
		final TestInput input = ctx.getInput();
		input.releaseKey(o -> o.keyUp);
		server.runCommand("ride @p dismount");
		server.runCommand("kill @e[tag=" + TAG + "]");
		// Arrive first so the chunks are loaded, then build.
		server.runCommand(String.format(Locale.ROOT, "tp @p %d %d %d", ox + SIZE / 2, GROUND + 1, oz + 6));
		ctx.waitTicks(20);
		world.getConnection().waitForChunksRender();
		final Route route = server.computeOnServer(s -> build(s.overworld(), scenario, ox, oz));
		final BlockPos start = route.start();
		final BlockPos target = route.target();
		server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f", start.getX() + 0.5, start.getY(), start.getZ() + 0.5));
		ctx.waitTicks(20);
		world.getConnection().waitForChunksRender();
		server.runCommand("effect give @p minecraft:instant_health 1 10 true");
		server.runCommand("effect give @p minecraft:saturation infinite 0 true");
		server.runCommand("execute at @p run summon minecraft:horse ~ ~ ~ {Tame:1b,Variant:2,Health:20f,"
			+ "equipment:{saddle:{id:\"minecraft:saddle\",count:1}},"
			+ "attributes:[{id:\"minecraft:movement_speed\",base:" + SPEED_ATTRIBUTE + "d},{id:\"minecraft:jump_strength\",base:0.7d},"
			+ "{id:\"minecraft:max_health\",base:20d}],Tags:[\"" + TAG + "\"]}");
		for (int attempt = 0; attempt < 10 && !ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse); attempt++) {
			ctx.waitTicks(5);
			server.runCommand("ride @p mount @e[tag=" + TAG + ",limit=1]");
		}
		if (!ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse)) {
			check("mounted", false);
			return 0;
		}
		ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		HURTS.clear();
		final Random random = new Random(scenario.seed());
		float yaw = yawTo(start.getX() + 0.5, start.getZ() + 0.5, target);
		input.lookAt(yaw, 10.0F);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(2);
		for (int i = 0; i < 3; i++) {
			input.pressKey(o -> o.keySprint);
			ctx.waitTicks(3);
		}

		final double distance = Math.hypot(target.getX() - start.getX(), target.getZ() - start.getZ());
		double travelled = 0.0;
		double previousSpeed = 0.0;
		double longestFall = 0.0;
		double lowest = start.getY();
		double highest = start.getY();
		int crashes = 0;
		int stuck = 0;
		int stopped = 0;
		int escape = 0;
		float escapeYaw = 0.0F;
		float zig = 0.0F;
		int detourTicks = 0;
		int lavaTicks = 0;
		final int refusals = ride(ctx, s -> s.refusals);
		final int ledges = ride(ctx, s -> s.ledgeClimbs);
		boolean arrived = false;
		int ticks = 0;
		final java.util.ArrayDeque<String> recent = new java.util.ArrayDeque<>();
		float previousHealth = ctx.computeOnClient(mc -> ((AbstractHorse) mc.player.getVehicle()).getHealth());
		int traced = 0;
		double previousPitch = 0.0;
		double previousTick = -1.0;
		double maxTiltRate = 0.0;
		// How far the drawn horse and the riding camera move up or down in a game tick: a step should be a climb, not a pop.
		double previousVisual = Double.NaN;
		double previousEye = Double.NaN;
		double maxVisualStep = 0.0;
		double maxEyeStep = 0.0;
		double previousVisualStep = Double.NaN;
		double previousEyeStep = Double.NaN;
		String worstStep = "";
		final java.util.ArrayList<Double> tiltRates = new java.util.ArrayList<>();
		for (; ticks < MAX_TICKS; ticks++) {
			ctx.waitTick();
			// Diagnostics: when the horse gets hurt, say how it was moving and what it is in.
			final String state = ctx.computeOnClient(mc -> {
				final Entity horse = mc.player.getVehicle();
				if (horse == null) {
					return "";
				}
				final RideState r = ((RideStateHolder) horse).horsingaround$ride();
				final StringBuilder inside = new StringBuilder();
				final var box = horse.getBoundingBox();
				for (int bx = Mth.floor(box.minX); bx <= Mth.floor(box.maxX - 1.0E-7); bx++) {
					for (int by = Mth.floor(box.minY); by <= Mth.floor(box.maxY); by++) {
						for (int bz = Mth.floor(box.minZ); bz <= Mth.floor(box.maxZ - 1.0E-7); bz++) {
							final BlockState b = mc.level.getBlockState(new BlockPos(bx, by, bz));
							if (!b.isAir()) {
								inside.append(b.getBlock().getDescriptionId().replace("block.minecraft.", "")).append(' ');
							}
						}
					}
				}
				// What is just in front, at the hooves and the chest.
				final StringBuilder ahead = new StringBuilder();
				final double fx = -Mth.sin(horse.getYRot() * Mth.DEG_TO_RAD);
				final double fz = Mth.cos(horse.getYRot() * Mth.DEG_TO_RAD);
				for (int dy = 0; dy <= 1; dy++) {
					final BlockPos front = BlockPos.containing(horse.getX() + fx * (horse.getBbWidth() * 0.5 + 0.3), horse.getY() + dy + 0.1, horse.getZ() + fz * (horse.getBbWidth() * 0.5 + 0.3));
					ahead.append(mc.level.getBlockState(front).getBlock().getDescriptionId().replace("block.minecraft.", "")).append(dy == 0 ? "/" : "");
				}
				return String.format(Locale.ROOT, "pos %.2f %.2f %.2f drawn %+.2f%s v(%.3f %.3f %.3f) ground %s speed %.2f side %.2f yaw %.0f ledge %d%s guard %d/%d danger %.1f wall %.1f detour %.0f in [%s] ahead %s",
					horse.getX(), horse.getY(), horse.getZ(), r.heightOffset(1.0F), r.inAir ? " air" : "", horse.getDeltaMovement().x, horse.getDeltaMovement().y, horse.getDeltaMovement().z,
					horse.onGround(), r.speed, r.sidestep(), horse.getYRot(), r.ledgeTicks, r.ledgeAir ? "air" : "", r.guardStops, r.guardChecks, Math.min(r.debugDanger(), 99.0F), Math.min(r.debugWall(), 99.0F), r.avoidOffset, inside.toString().trim(), ahead);
			});
			final float health = ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse h ? h.getHealth() : 0.0F);
			recent.addLast(state);
			if (recent.size() > 6) {
				recent.removeFirst();
			}
			if (health < previousHealth && traced < 3) {
				traced++;
				log("  hurt at tick %d, the last ticks:", ticks);
				recent.forEach(line -> log("    %s", line));
			}
			previousHealth = health;
			final double[] now = ctx.computeOnClient(mc -> {
				final Entity horse = mc.player.getVehicle();
				if (!(horse instanceof AbstractHorse)) {
					return null;
				}
				final RideState s = ((RideStateHolder) horse).horsingaround$ride();
				return new double[] {
					Math.hypot(horse.getX() - horse.xo, horse.getZ() - horse.zo),
					s.blocked > RideTuning.CRASH_BLOCKED ? 1 : 0,
					horse.fallDistance,
					horse.isInLava() ? 1 : 0,
					s.avoidOffset,
					horse.getX(),
					horse.getY(),
					horse.getZ(),
					RideController.lastTickNanos,
					horse.isInWater() ? 1 : 0,
					s.pitch(1.0F),
					horse.tickCount,
					horse.getY() + s.heightOffset(1.0F),
					dev.horsingaround.client.RideCamera.eyeY(1.0F),
					horse.onGround() ? 1 : 0
				};
			});
			if (now == null) {
				check("still riding (tick " + ticks + ")", false);
				break;
			}
			final double speed = now[0];
			// How fast the body tilts on the ground model (jump tilt is separate), per game tick.
			if (now[11] > previousTick && previousTick >= 0) {
				final double rate = Math.abs(now[10] - previousPitch) / (now[11] - previousTick);
				maxTiltRate = Math.max(maxTiltRate, rate);
				tiltRates.add(rate);
			}
			if (now[11] > previousTick && previousTick >= 0 && !Double.isNaN(previousVisual)) {
				final double elapsed = now[11] - previousTick;
				// A pop is a sudden jump in how fast the drawn horse rises from one tick to the next, on the ground (a steady
				// climb up a steep staircase rises fast but evenly; jumps and falls are real flights).
				final double visualRise = (now[12] - previousVisual) / elapsed;
				final double eyeRise = (now[13] - previousEye) / elapsed;
				if (now[9] == 0 && now[14] > 0 && !Double.isNaN(previousVisualStep)) {
					final double visualJerk = visualRise - previousVisualStep;
					if (visualJerk > maxVisualStep) {
						maxVisualStep = visualJerk;
						final StringBuilder lines = new StringBuilder();
						recent.forEach(line -> lines.append("\n      ").append(line));
						worstStep = String.format(Locale.ROOT, "tick %d: rising %.2f a tick after %.2f, physics y %.2f; the last ticks:%s", ticks, visualRise,
							previousVisualStep, now[6], lines);
					}
					maxEyeStep = Math.max(maxEyeStep, eyeRise - previousEyeStep);
				}
				previousVisualStep = now[14] > 0 ? visualRise : Double.NaN;
				previousEyeStep = eyeRise;
			}
			previousVisual = now[12];
			previousEye = now[13];
			previousPitch = now[10];
			previousTick = now[11];
			travelled += speed;
			// (Swimming into a bank is not a crash.)
			if (now[1] > 0 && previousSpeed > CRASH_SPEED && now[9] == 0) {
				crashes++;
				if (crashes <= 3) {
					log("  crash at tick %d at %.2f b/t, the last ticks:", ticks, previousSpeed);
					recent.forEach(line -> log("    %s", line));
				}
			}
			previousSpeed = speed;
			longestFall = Math.max(longestFall, now[2]);
			lavaTicks += (int) now[3];
			detourTicks += now[4] != 0.0 ? 1 : 0;
			lowest = Math.min(lowest, now[6]);
			highest = Math.max(highest, now[6]);
			if (ticks >= 40) {
				this.tickNanos.add((long) now[8]);
			}
			if (Math.hypot(target.getX() + 0.5 - now[5], target.getZ() + 0.5 - now[7]) < 4.0) {
				arrived = true;
				break;
			}
			// The rider: heads for the target (zigzagging if exploring); stuck against something, turns away for a bit.
			stopped = speed < 0.03 ? stopped + 1 : 0;
			if (stopped >= 20) {
				stuck++;
				stopped = 0;
				if (stuck <= 2) {
					log("  stuck at tick %d, the last ticks:", ticks);
					recent.forEach(line -> log("    %s", line));
				}
				escape = 30;
				final float toward = yawTo(now[5], now[7], target);
				escapeYaw = ctx.computeOnClient(mc -> openest(mc.level, mc.player.getVehicle(), toward));
			}
			if (scenario.zigzag() && ticks % 60 == 0) {
				zig = random.nextFloat() * 60.0F - 30.0F;
			}
			if (escape > 0) {
				escape--;
				yaw = escapeYaw;
			} else {
				yaw = yawTo(now[5], now[7], target) + zig + (random.nextFloat() - 0.5F) * 4.0F;
			}
			input.lookAt(yaw, 10.0F);
			if (ticks == 120) {
				log("  screenshot -> %s", ctx.takeScreenshot("horsingaround_terrain_" + scenario.name().replaceAll("[^a-z0-9]+", "_")));
			}
		}
		input.releaseKey(o -> o.keyUp);
		ctx.waitTicks(20);
		final double left = ctx.computeOnClient(mc -> mc.player.getVehicle() == null ? distance
			: Math.hypot(target.getX() + 0.5 - mc.player.getVehicle().getX(), target.getZ() + 0.5 - mc.player.getVehicle().getZ()));

		log("  %s in %d ticks: rode %.0f blocks, %.0f of %.0f left, climbed %.0f, descended %.0f, %d refusals, %d ledge jumps, %d ticks going round things, longest fall %.1f",
			arrived ? "arrived" : "ran out of time", ticks, travelled, left, distance, highest - start.getY(), start.getY() - lowest,
			ride(ctx, s -> s.refusals) - refusals, ride(ctx, s -> s.ledgeClimbs) - ledges, detourTicks, longestFall);
		for (final Hurt hurt : HURTS) {
			log("  %s took %.1f %s damage (%.1f/%.1f health before)", hurt.horse() ? "horse" : "rider", hurt.amount(), hurt.type(), hurt.healthBefore(), hurt.maxHealth());
		}
		check("no damage from hazards, fire, lava, freezing, suffocation or drowning", HURTS.stream().noneMatch(h -> NEVER.contains(h.type())));
		check("never in lava (ticks)", lavaTicks, 0, 0);
		check("falls stay within what the horse accepts (horse <= 2 hearts, rider <= 3 a fall)", HURTS.stream()
			.filter(h -> h.type().equals("fall"))
			.allMatch(h -> h.amount() <= (h.horse() ? 4.0F : 6.0F) + 0.01F));
		check("no hurting falls while low on health", HURTS.stream()
			.filter(h -> h.type().equals("fall"))
			.noneMatch(h -> h.healthBefore() / h.maxHealth() < 0.4F));
		check("gets there (share of the way covered)", 1.0 - left / distance, 0.8, 1.0);
		check("keeps moving (pace / gallop)", travelled / Math.max(ticks, 1) / GALLOP_SPEED, scenario.minPace(), 1.2);
		check("crashes into things at speed", crashes, 0, 2);
		check("dead ends the rider had to turn away from", stuck, 0, scenario.maxStuck());
		tiltRates.sort(null);
		log("  body tilt change per tick: 99th percentile %.2f deg, most %.2f deg",
			tiltRates.isEmpty() ? 0.0 : tiltRates.get((int) (tiltRates.size() * 0.99)), maxTiltRate);
		check("smooth: the body never snaps into a tilt (max change per tick, deg)", maxTiltRate, 0.0, 3.5);
		log("  sharpest pick-up in the drawn horse's rise: %s", worstStep);
		check("smooth: the drawn horse climbs steps, never pops up them (sharpest pick-up in rise, blocks/tick a tick)", maxVisualStep, 0.0, 0.3);
		check("smooth: the riding camera too (sharpest pick-up in rise, blocks/tick a tick)", maxEyeStep, 0.0, 0.3);
		server.runCommand("ride @p dismount");
		server.runCommand("kill @e[tag=" + TAG + "]");
		return crashes;
	}

	/**
	 * Where a stuck player would look to get going again: of the headings round the way they want to go, the one with
	 * the most open ground ahead (no step up of more than a block, no wall at head height), nearest their way first.
	 */
	private static float openest(final net.minecraft.world.level.Level level, final Entity horse, final float toward) {
		float best = toward + 180.0F;
		int bestRun = -1;
		for (final float turn : new float[] {45.0F, -45.0F, 90.0F, -90.0F, 135.0F, -135.0F, 180.0F}) {
			final float yaw = toward + turn;
			final double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
			final double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
			// A path as wide as the horse (and a little more): the middle and both flanks.
			int run = 12;
			for (final double offset : new double[] {0.0, -0.7, 0.7}) {
				run = Math.min(run, openRun(level, horse, horse.getX() + fz * offset, horse.getZ() - fx * offset, fx, fz));
			}
			if (run > bestRun) {
				bestRun = run;
				best = yaw;
			}
		}
		return best;
	}

	/** Steps of open, safe ground along a line: no step up of more than a block, no wall at head height, no hazard. */
	private static int openRun(final net.minecraft.world.level.Level level, final Entity horse, final double x0, final double z0, final double fx, final double fz) {
		int ground = Mth.floor(horse.getY());
		for (int d = 1; d <= 12; d++) {
			final int x = Mth.floor(x0 + fx * d * 0.5);
			final int z = Mth.floor(z0 + fz * d * 0.5);
			int y = ground + 2;
			while (y > ground - 4 && level.getBlockState(new BlockPos(x, y - 1, z)).getCollisionShape(level, new BlockPos(x, y - 1, z)).isEmpty()
				&& !dev.horsingaround.ride.Awareness.isHazard(level.getBlockState(new BlockPos(x, y - 1, z)))) {
				y--;
			}
			final BlockState feet = level.getBlockState(new BlockPos(x, y, z));
			final BlockState under = level.getBlockState(new BlockPos(x, y - 1, z));
			if (y > ground + 1 || !level.getBlockState(new BlockPos(x, y + 1, z)).getCollisionShape(level, new BlockPos(x, y + 1, z)).isEmpty()
				|| dev.horsingaround.ride.Awareness.isHazard(feet) || dev.horsingaround.ride.Awareness.isHazard(under)
				|| feet.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
				return d - 1;
			}
			ground = y;
		}
		return 12;
	}

	private static float yawTo(final double x, final double z, final BlockPos target) {
		return (float) Math.toDegrees(Math.atan2(-(target.getX() + 0.5 - x), target.getZ() + 0.5 - z));
	}

	// ---- Terrain ----

	/** Builds the scenario in the SIZE x SIZE area north of (ox, oz) (server thread). */
	private static Route build(final ServerLevel level, final Scenario scenario, final int ox, final int oz) {
		final Terrain t = new Terrain(level, ox, oz, new Random(scenario.seed()));
		final int mid = SIZE / 2;
		return switch (scenario.kind()) {
			case FOREST, DENSE_FOREST -> {
				final boolean dense = scenario.kind() == Kind.DENSE_FOREST;
				t.heights((x, z) -> t.noise(x / 9.0, z / 9.0) > 0.62 ? 1 : 0);
				t.columns(Blocks.DIRT, Blocks.GRASS_BLOCK);
				t.keepClear(mid, 1, 4);
				t.keepClear(mid, SIZE - 2, 4);
				t.forest(dense ? 480 : 380, dense ? 3.0 : 3.2, dense ? 0.55 : 0.2);
				t.bushes(dense ? 40 : 28);
				t.fallenLogs(dense ? 10 : 8);
				t.boulders(6);
				t.cover(0.15);
				yield t.route(mid, 1, mid, SIZE - 2);
			}
			case MOUNTAIN_DOWN, MOUNTAIN_UP -> {
				t.heights((x, z) -> {
					final double r = Math.hypot(x - mid, z - mid);
					double h = 28.0 * Math.pow(Math.max(0.0, 1.0 - r / 31.0), 1.25) + 4.0 * (t.fractal(x / 8.0, z / 8.0) - 0.5);
					// Shelves and the cliffs between them.
					if (t.noise(x / 11.0 + 40.0, z / 11.0 + 40.0) > 0.66) {
						h += 3.0;
					}
					return Math.max(0, (int) h);
				});
				t.mountainColumns();
				t.forest(18, 4.0, 0.0);
				final int[] peak = t.highest();
				final double angle = t.random.nextDouble() * Math.PI * 2.0;
				final int ex = Mth.clamp((int) (mid + Math.cos(angle) * 30.0), 1, SIZE - 2);
				final int ez = Mth.clamp((int) (mid + Math.sin(angle) * 30.0), 1, SIZE - 2);
				t.keepClear(peak[0], peak[1], 2);
				yield scenario.kind() == Kind.MOUNTAIN_DOWN ? t.route(peak[0], peak[1], ex, ez) : t.route(ex, ez, peak[0], peak[1]);
			}
			case HILLS -> {
				t.heights((x, z) -> (int) Math.round(7.0 * t.fractal(x / 18.0, z / 18.0)));
				t.columns(Blocks.DIRT, Blocks.GRASS_BLOCK);
				t.keepClear(mid, 1, 4);
				t.forest(14, 6.0, 0.0);
				t.cover(0.1);
				yield t.route(mid, 1, mid, SIZE - 2);
			}
			case TAIGA -> {
				t.heights((x, z) -> t.noise(x / 9.0, z / 9.0) > 0.6 ? 1 : 0);
				t.columns(Blocks.DIRT, Blocks.PODZOL);
				t.keepClear(mid, 1, 4);
				t.keepClear(mid, SIZE - 2, 3);
				t.spruces(35);
				t.hazards();
				yield t.route(mid, 1, mid, SIZE - 2);
			}
			case RIVER -> {
				// Banks like generated rivers': mostly level with the water or a block above it (climbable), here and there
				// two (not), rising further back from the water.
				t.heights((x, z) -> {
					final double channel = Math.abs(z - (mid + 4.0 * Math.sin(x / 9.0)));
					if (channel < 3.0) {
						return -3;
					}
					final double f = t.fractal(x / 12.0, z / 12.0);
					return channel < 5.0 ? (int) Math.round(1.8 * f) : 1 + (int) Math.round(2.5 * f);
				});
				t.columns(Blocks.DIRT, Blocks.GRASS_BLOCK);
				t.water();
				t.keepClear(mid, 1, 4);
				t.forest(20, 5.0, 0.0);
				yield t.route(mid, 1, mid, SIZE - 2);
			}
			case BADLANDS -> {
				// Terraces stepping down 4 blocks at a time from the far corner, broken up by noise, with a few spires.
				t.heights((x, z) -> 4 * Mth.clamp((int) ((x + z) / 32.0 + 1.6 * (t.fractal(x / 10.0, z / 10.0) - 0.5) + (t.noise(x / 4.0, z / 4.0) > 0.85 ? 2 : 0)), 0, 4));
				t.badlandsColumns();
				final int[] top = t.highest();
				yield t.route(top[0], top[1], 3, 3);
			}
		};
	}

	/** Procedural terrain written straight into the level, in area coordinates (x east, z north from the origin). */
	private static final class Terrain {
		private static final int FLAGS = 2 | 16;
		final ServerLevel level;
		final int ox;
		final int oz;
		final Random random;
		final long seed;
		final int[][] height = new int[SIZE][SIZE];
		final List<int[]> trunks = new ArrayList<>();
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		Terrain(final ServerLevel level, final int ox, final int oz, final Random random) {
			this.level = level;
			this.ox = ox;
			this.oz = oz;
			this.random = random;
			this.seed = random.nextLong();
		}

		/** World block at area (x, z), y. North is -z in the world. */
		void set(final int x, final int y, final int z, final BlockState state) {
			this.level.setBlock(this.pos.set(this.ox + x, y, this.oz - z), state, FLAGS);
		}

		BlockState get(final int x, final int y, final int z) {
			return this.level.getBlockState(this.pos.set(this.ox + x, y, this.oz - z));
		}

		/** Surface (top block) y at area (x, z). */
		int top(final int x, final int z) {
			return GROUND + this.height[x][z];
		}

		void heights(final java.util.function.IntBinaryOperator h) {
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					this.height[x][z] = h.applyAsInt(x, z);
				}
			}
		}

		void columns(final net.minecraft.world.level.block.Block fill, final net.minecraft.world.level.block.Block surface) {
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					final int top = top(x, z);
					for (int y = Math.min(top, GROUND) - 1; y <= top; y++) {
						set(x, y, z, y == top ? surface.defaultBlockState() : y >= top - 2 ? fill.defaultBlockState() : Blocks.STONE.defaultBlockState());
					}
					if (top < GROUND) {
						for (int y = top + 1; y <= GROUND; y++) {
							set(x, y, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}

		void mountainColumns() {
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					final int h = this.height[x][z];
					final int steep = Math.max(Math.max(Math.abs(h - at(x + 1, z)), Math.abs(h - at(x - 1, z))), Math.max(Math.abs(h - at(x, z + 1)), Math.abs(h - at(x, z - 1))));
					final BlockState surface = h > 22 ? Blocks.SNOW_BLOCK.defaultBlockState() : steep >= 2 ? Blocks.STONE.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
					for (int y = GROUND; y <= GROUND + h; y++) {
						set(x, y, z, y == GROUND + h ? surface : Blocks.STONE.defaultBlockState());
					}
				}
			}
		}

		void badlandsColumns() {
			final BlockState[] bands = {Blocks.TERRACOTTA.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
				Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState()};
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					final int h = this.height[x][z];
					for (int y = GROUND; y <= GROUND + h; y++) {
						set(x, y, z, y == GROUND + h ? Blocks.RED_SAND.defaultBlockState() : bands[Math.floorMod(y, bands.length)]);
					}
				}
			}
		}

		private int at(final int x, final int z) {
			return this.height[Mth.clamp(x, 0, SIZE - 1)][Mth.clamp(z, 0, SIZE - 1)];
		}

		/** Water in every column below the flat ground level (the river), up to the ground level. */
		void water() {
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					for (int y = top(x, z) + 1; y <= GROUND; y++) {
						set(x, y, z, Blocks.WATER.defaultBlockState());
					}
				}
			}
		}

		/** Mark a circle that trees and bushes avoid (start and target). */
		void keepClear(final int x, final int z, final int radius) {
			this.trunks.add(new int[] {x, z, -radius});
		}

		private boolean crowded(final int x, final int z, final double spacing) {
			for (final int[] t : this.trunks) {
				final double room = t[2] < 0 ? -t[2] : spacing;
				if (Math.hypot(t[0] - x, t[1] - z) < room) {
					return true;
				}
			}
			return false;
		}

		/** Trees: oaks and birches with round crowns (some hanging low), and 2x2 dark oaks. */
		void forest(final int tries, final double spacing, final double darkOakShare) {
			for (int i = 0; i < tries; i++) {
				final int x = 2 + this.random.nextInt(SIZE - 4);
				final int z = 2 + this.random.nextInt(SIZE - 4);
				if (crowded(x, z, spacing) || this.height[x][z] < 0) {
					continue;
				}
				final double kind = this.random.nextDouble();
				final boolean dark = kind < darkOakShare;
				final int size = dark ? 2 : 1;
				final BlockState log = (dark ? Blocks.DARK_OAK_LOG : kind < darkOakShare + (1.0 - darkOakShare) * 0.7 ? Blocks.OAK_LOG : Blocks.BIRCH_LOG).defaultBlockState();
				final BlockState leaves = (dark ? Blocks.DARK_OAK_LEAVES : log.is(Blocks.BIRCH_LOG) ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES).defaultBlockState()
					.setValue(LeavesBlock.PERSISTENT, true);
				final int base = top(x, z) + 1;
				final int trunk = 5 + this.random.nextInt(dark ? 4 : 3);
				for (int dx = 0; dx < size; dx++) {
					for (int dz = 0; dz < size; dz++) {
						if (x + dx < SIZE && z + dz < SIZE) {
							for (int y = Math.min(base, top(x + dx, z + dz) + 1); y < base + trunk; y++) {
								set(x + dx, y, z + dz, log);
							}
						}
					}
				}
				this.trunks.add(new int[] {x, z, 0});
				final int radius = dark ? 3 : 2;
				final int low = this.random.nextDouble() < 0.25 ? 2 : trunk - 2;
				for (int dx = -radius; dx <= radius + size - 1; dx++) {
					for (int dz = -radius; dz <= radius + size - 1; dz++) {
						for (int y = base + low; y <= base + trunk + 1; y++) {
							final double r = Math.hypot(dx - (size - 1) * 0.5, dz - (size - 1) * 0.5) + (y > base + trunk ? 1.0 : 0.0);
							if (r <= radius + 0.3 && x + dx >= 0 && x + dx < SIZE && z + dz >= 0 && z + dz < SIZE && get(x + dx, y, z + dz).isAir()) {
								set(x + dx, y, z + dz, leaves);
							}
						}
					}
				}
			}
		}

		/** Spruces: tall trunks with cone-shaped crowns down to near the ground. */
		void spruces(final int count) {
			final BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
			final BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
			int placed = 0;
			for (int i = 0; i < count * 4 && placed < count; i++) {
				final int x = 2 + this.random.nextInt(SIZE - 4);
				final int z = 2 + this.random.nextInt(SIZE - 4);
				if (crowded(x, z, 3.5)) {
					continue;
				}
				placed++;
				this.trunks.add(new int[] {x, z, 0});
				final int base = top(x, z) + 1;
				final int trunk = 7 + this.random.nextInt(3);
				for (int y = base; y < base + trunk; y++) {
					set(x, y, z, log);
				}
				for (int y = base + 2; y <= base + trunk; y++) {
					final int r = Math.max(0, (base + trunk - y + 1) / 2);
					for (int dx = -r; dx <= r; dx++) {
						for (int dz = -r; dz <= r; dz++) {
							if (Math.abs(dx) + Math.abs(dz) <= r + 1 && x + dx >= 0 && x + dx < SIZE && z + dz >= 0 && z + dz < SIZE && get(x + dx, y, z + dz).isAir()) {
								set(x + dx, y, z + dz, leaves);
							}
						}
					}
				}
			}
		}

		/** Leafy bushes on the ground, some round a stub of log. */
		void bushes(final int count) {
			final BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
			for (int i = 0; i < count; i++) {
				final int x = 1 + this.random.nextInt(SIZE - 2);
				final int z = 1 + this.random.nextInt(SIZE - 2);
				if (crowded(x, z, 1.5)) {
					continue;
				}
				final int y = top(x, z) + 1;
				if (this.random.nextDouble() < 0.3) {
					set(x, y, z, Blocks.OAK_LOG.defaultBlockState());
				}
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (this.random.nextDouble() < 0.7 && x + dx >= 0 && x + dx < SIZE && z + dz >= 0 && z + dz < SIZE && get(x + dx, y, z + dz).isAir()) {
							set(x + dx, y, z + dz, leaves);
						}
					}
				}
				if (this.random.nextBoolean() && get(x, y + 1, z).isAir()) {
					set(x, y + 1, z, leaves);
				}
			}
		}

		/** Fallen trunks lying across the ground. */
		void fallenLogs(final int count) {
			for (int i = 0; i < count; i++) {
				final boolean alongX = this.random.nextBoolean();
				final BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
				final int x = 2 + this.random.nextInt(SIZE - 8);
				final int z = 2 + this.random.nextInt(SIZE - 8);
				if (crowded(x, z, 2.0)) {
					continue;
				}
				final int length = 3 + this.random.nextInt(3);
				for (int k = 0; k < length; k++) {
					final int lx = alongX ? x + k : x;
					final int lz = alongX ? z : z + k;
					if (get(lx, top(lx, lz) + 1, lz).isAir()) {
						set(lx, top(lx, lz) + 1, lz, log);
					}
				}
			}
		}

		/** Mossy boulders, 2x2 and one or two high. */
		void boulders(final int count) {
			for (int i = 0; i < count; i++) {
				final int x = 2 + this.random.nextInt(SIZE - 5);
				final int z = 2 + this.random.nextInt(SIZE - 5);
				if (crowded(x, z, 2.5)) {
					continue;
				}
				this.trunks.add(new int[] {x, z, 0});
				final int tall = 1 + this.random.nextInt(2);
				for (int dx = 0; dx < 2; dx++) {
					for (int dz = 0; dz < 2; dz++) {
						for (int y = 1; y <= tall; y++) {
							set(x + dx, top(x + dx, z + dz) + y, z + dz, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
						}
					}
				}
			}
		}

		/** Grass and ferns (nothing to collide with). */
		void cover(final double share) {
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					final int y = top(x, z) + 1;
					if (this.random.nextDouble() < share && get(x, y, z).isAir() && get(x, y - 1, z).is(Blocks.GRASS_BLOCK)) {
						set(x, y, z, (this.random.nextBoolean() ? Blocks.SHORT_GRASS : Blocks.FERN).defaultBlockState());
					}
				}
			}
		}

		/** Taiga dangers: berry bush patches, powder snow hollows, lit campfires, cactus on sand, and a lava pool. */
		void hazards() {
			final BlockState berries = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3);
			for (int i = 0; i < 9; i++) {
				final int x = 2 + this.random.nextInt(SIZE - 4);
				final int z = 4 + this.random.nextInt(SIZE - 8);
				for (int k = 0; k < 1 + this.random.nextInt(4); k++) {
					final int bx = Mth.clamp(x + this.random.nextInt(3) - 1, 0, SIZE - 1);
					final int bz = Mth.clamp(z + this.random.nextInt(3) - 1, 0, SIZE - 1);
					if (!crowded(bx, bz, 1.0) && get(bx, top(bx, bz) + 1, bz).isAir()) {
						set(bx, top(bx, bz) + 1, bz, berries);
					}
				}
			}
			for (int i = 0; i < 3; i++) {
				final int x = 2 + this.random.nextInt(SIZE - 5);
				final int z = 6 + this.random.nextInt(SIZE - 12);
				for (int dx = 0; dx < 2; dx++) {
					for (int dz = 0; dz < 2; dz++) {
						set(x + dx, top(x + dx, z + dz), z + dz, Blocks.POWDER_SNOW.defaultBlockState());
					}
				}
			}
			for (int i = 0; i < 2; i++) {
				final int x = 2 + this.random.nextInt(SIZE - 4);
				final int z = 6 + this.random.nextInt(SIZE - 12);
				set(x, top(x, z) + 1, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
			}
			for (int i = 0; i < 3; i++) {
				final int x = 2 + this.random.nextInt(SIZE - 4);
				final int z = 6 + this.random.nextInt(SIZE - 12);
				set(x, top(x, z), z, Blocks.SAND.defaultBlockState());
				for (int y = 1; y <= 2; y++) {
					set(x, top(x, z) + y, z, Blocks.CACTUS.defaultBlockState());
				}
			}
			final int lx = 4 + this.random.nextInt(SIZE - 12);
			final int lz = 20 + this.random.nextInt(SIZE - 40);
			for (int dx = 0; dx < 4; dx++) {
				for (int dz = 0; dz < 3; dz++) {
					set(lx + dx, top(lx + dx, lz + dz), lz + dz, Blocks.LAVA.defaultBlockState());
				}
			}
		}

		/** The highest column, as {x, z}. */
		int[] highest() {
			int[] best = {SIZE / 2, SIZE / 2};
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					if (this.height[x][z] > this.height[best[0]][best[1]]) {
						best = new int[] {x, z};
					}
				}
			}
			return best;
		}

		Route route(final int sx, final int sz, final int tx, final int tz) {
			return new Route(new BlockPos(this.ox + sx, standing(sx, sz), this.oz - sz), new BlockPos(this.ox + tx, standing(tx, tz), this.oz - tz));
		}

		/** Height a horse stands at on area (x, z): on top of whatever is there. */
		private int standing(final int x, final int z) {
			int y = Math.max(top(x, z), GROUND) + 1;
			while (!get(x, y, z).isAir() && y < GROUND + 80) {
				y++;
			}
			return y;
		}

		double noise(final double x, final double z) {
			final int x0 = Mth.floor(x);
			final int z0 = Mth.floor(z);
			final double fx = smooth(x - x0);
			final double fz = smooth(z - z0);
			return Mth.lerp(fz, Mth.lerp(fx, lattice(x0, z0), lattice(x0 + 1, z0)), Mth.lerp(fx, lattice(x0, z0 + 1), lattice(x0 + 1, z0 + 1)));
		}

		/** Three octaves of value noise, 0..1. */
		double fractal(final double x, final double z) {
			return (noise(x, z) * 4.0 + noise(x * 2.0 + 17.0, z * 2.0 + 31.0) * 2.0 + noise(x * 4.0 + 53.0, z * 4.0 + 71.0)) / 7.0;
		}

		private double lattice(final int x, final int z) {
			long h = this.seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
			h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
			h ^= h >>> 29;
			return (h >>> 11) * 0x1.0p-53;
		}

		private static double smooth(final double t) {
			return t * t * (3.0 - 2.0 * t);
		}
	}

	// ---- Bookkeeping ----

	private void summary(final int crashes) {
		section("All rides");
		final long[] sorted = this.tickNanos.stream().mapToLong(Long::longValue).sorted().toArray();
		if (sorted.length == 0) {
			check("rides measured", false);
			return;
		}
		final double average = Arrays.stream(sorted).average().orElse(0.0) / 1.0E6;
		final double p99 = sorted[(int) (sorted.length * 0.99)] / 1.0E6;
		log("  ride tick cost over %d ticks: average %.3f ms, 99th percentile %.3f ms, worst %.3f ms", sorted.length, average, p99, sorted[sorted.length - 1] / 1.0E6);
		check("ride tick cost on average (ms; a tick has 50)", average, 0.0, 0.3);
		check("ride tick cost, 99th percentile (ms)", p99, 0.0, 2.0);
		log("  crashes in all: %d", crashes);
	}

	private static void listen() {
		if (listening) {
			return;
		}
		listening = true;
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken <= 0.0F) {
				return;
			}
			final boolean horse = entity instanceof AbstractHorse && entity.entityTags().contains(TAG);
			final boolean rider = entity instanceof Player && entity.getVehicle() instanceof AbstractHorse mount && mount.entityTags().contains(TAG);
			if (horse || rider) {
				HURTS.add(new Hurt(horse, source.getMsgId(), taken, entity.getHealth() + taken, entity.getMaxHealth()));
			}
		});
	}

	private static <T> T ride(final ClientGameTestContext ctx, final Function<RideState, T> read) {
		return ctx.computeOnClient(mc -> read.apply(((RideStateHolder) mc.player.getVehicle()).horsingaround$ride()));
	}

	private void section(final String name) {
		this.report.add("");
		this.report.add("== " + name);
	}

	private void check(final String name, final boolean ok) {
		this.report.add((ok ? "  PASS  " : "  FAIL  ") + name);
		if (!ok) {
			this.failures++;
		}
	}

	private void check(final String name, final double value, final double min, final double max) {
		final boolean ok = value >= min && value <= max;
		this.report.add(String.format(Locale.ROOT, "  %s  %s = %.3f  (target %.3f..%.3f)", ok ? "PASS" : "FAIL", name, value, min, max));
		if (!ok) {
			this.failures++;
		}
	}

	private void log(final String format, final Object... args) {
		this.report.add(String.format(Locale.ROOT, format, args));
	}

	private void writeReport() {
		final Path file = FabricLoader.getInstance().getGameDir().resolve("horsingaround-terrain-report.txt");
		this.report.add("");
		this.report.add(this.failures == 0 ? "ALL TERRAIN RIDE CHECKS PASSED" : this.failures + " TERRAIN RIDE CHECKS FAILED");
		try {
			Files.write(file, this.report);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}
}
