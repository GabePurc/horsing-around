package dev.horsingaround.gametest;

import dev.horsingaround.client.RideCamera;
import dev.horsingaround.ride.Foliage;
import dev.horsingaround.ride.HorseConfig;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.ride.RideTuning;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Rides a horse through every mechanic with simulated keys, measures what happens tick by tick, and checks it against
 * the feel targets in mvp_requirements.md. Writes a report and screenshots to the test run folder.
 *
 * <p>Run with {@code ./gradlew runClientGameTest}.
 */
public final class RideFeelTest implements FabricClientGameTest {
	/** Fixed test horse: average speed and jump. */
	private static final double SPEED_ATTRIBUTE = 0.225;
	private static final double JUMP_ATTRIBUTE = 0.7;
	/** Flat-ground top speed per tick at speed multiple 1.0. */
	private static final double TERMINAL = SPEED_ATTRIBUTE * RideTuning.TERMINAL_VELOCITY_FACTOR;
	private static final double WALK_SPEED = TERMINAL * RideTuning.GAIT_SPEED[RideTuning.WALK];
	private static final double GALLOP_SPEED = TERMINAL * RideTuning.GAIT_SPEED[RideTuning.GALLOP];

	private final List<String> report = new ArrayList<>();
	private int failures;

	private record Sample(
		double speed, double y, float horseYaw, float playerYaw, int gait, float stamina, boolean exhausted, float lean, boolean onGround,
		float limbSpeed, double visualY, float pitch
	) {
	}

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!System.getProperty("horsingaround.tests", "ride").contains("ride")) {
			return;
		}
		screenshot(ctx, "00a_title_screen");
		final boolean fresh = enableFreshAnimations(ctx);
		log("Fresh Animations pack: %s, EMF loaded: %s", fresh ? "enabled" : "not found", FabricLoader.getInstance().isModLoaded("entity_model_features"));

		try (TestSingleplayerContext world = ctx.worldBuilder()
			.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
			.create()) {
			final TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("tp @a 0.5 -60 0.5 180 10");
			server.runCommand("summon minecraft:horse 0.5 -60 0.5 {Tame:1b,Variant:0,Rotation:[180f,0f],"
				+ "equipment:{saddle:{id:\"minecraft:saddle\",count:1}},"
				+ "attributes:[{id:\"minecraft:movement_speed\",base:" + SPEED_ATTRIBUTE + "d},{id:\"minecraft:jump_strength\",base:" + JUMP_ATTRIBUTE + "d}],"
				+ "Tags:[\"ride_test\"]}");
			server.runCommand("ride @p mount @e[type=minecraft:horse,tag=ride_test,limit=1]");
			ctx.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof AbstractHorse);
			world.getConnection().waitForChunksRender();
			final TestInput input = ctx.getInput();
			input.lookAt(180.0F, 10.0F);
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));

			// -Psections=<names> runs only some sections (core, cuts, stairs, picking, steps); all of them by default.
			final String sections = System.getProperty("horsingaround.sections", "");
			if (sections.isEmpty() || sections.contains("core")) {
				mounting(ctx);
				freeLook(ctx, input);
				walk(ctx, input);
				gallop(ctx, input);
				turn(ctx, input);
				runningJump(ctx, input);
				exhaustion(ctx, input);
				coastStop(ctx, input);
				brakeAndReverse(ctx, input);
				freeAim(ctx, input);
				standingJump(ctx);
				dismount(ctx, input);
			}
			if (sections.isEmpty() || sections.contains("cuts")) {
				hardCuts(ctx, input, world);
			}
			if (sections.isEmpty() || sections.contains("stairs")) {
				stairs(ctx, input, world);
				forest(ctx, input, world);
				downhill(ctx, input, world);
				trample(ctx, input, world);
				water(ctx, input, world);
				bank(ctx, input, world, 940, 1);
				bank(ctx, input, world, 960, 2);
			}
			if (sections.isEmpty() || sections.contains("picking")) {
				pickingItsWay(ctx, input, world);
			}
			if (sections.isEmpty() || sections.contains("steps")) {
				stepsAndFooting(ctx, input, world);
			}
			if (sections.isEmpty() || sections.contains("core")) {
				horseSettings(ctx);
			}
		} finally {
			writeReport();
		}
		if (this.failures > 0) {
			throw new AssertionError(this.failures + " ride checks failed; see horsingaround-ride-report.txt");
		}
	}

	private void mounting(final ClientGameTestContext ctx) {
		section("Mounting");
		check("ridden, the horse's box narrows to its body (width, blocks)", ctx.computeOnClient(mc -> (double) mc.player.getVehicle().getBbWidth()), 0.85, 0.95);
		check("camera switched to third person", ctx.computeOnClient(mc -> mc.options.getCameraType()) == CameraType.THIRD_PERSON_BACK);
		screenshot(ctx, "01_mounted");
		sideScreenshot(ctx, "01b_seated_side");
	}

	private void freeLook(final ClientGameTestContext ctx, final TestInput input) {
		section("Free look while standing");
		final float before = sample(ctx).horseYaw;
		input.lookAt(90.0F, 10.0F);
		ctx.waitTicks(20);
		final float drift = Math.abs(Mth.wrapDegrees(sample(ctx).horseYaw - before));
		check("horse ignores camera when idle (drift deg)", drift, 0.0, 1.0);
		screenshot(ctx, "02_free_look");
		input.lookAt(180.0F, 10.0F);
		ctx.waitTicks(2);
	}

	private void walk(final ClientGameTestContext ctx, final TestInput input) {
		section("Walk");
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(40);
		check("walk speed (blocks/tick)", averageSpeed(ctx, 10), WALK_SPEED * 0.85, WALK_SPEED * 1.15);
		check("gait is walk", sample(ctx).gait == RideTuning.WALK);
		check("walk leg-animation speed (Fresh Animations walk < 0.4)", sample(ctx).limbSpeed, 0.25, 0.39);
		if (FabricLoader.getInstance().isModLoaded("entity_model_features")) {
			float low = Float.MAX_VALUE;
			float high = -Float.MAX_VALUE;
			for (int i = 0; i < 30; i++) {
				ctx.waitTick();
				final float side = ctx.computeOnClient(mc -> {
					final RideState s = ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride();
					return s.animatedSide - s.animatedSideBase;
				});
				low = Math.min(low, side);
				high = Math.max(high, side);
			}
			check("rider follows the saddle's side-to-side sway at a walk (blocks peak to peak)", high - low, 0.003, 0.2);
		}
		sideScreenshot(ctx, "02b_walk_side");
	}

	private void gallop(final ClientGameTestContext ctx, final TestInput input) {
		section("Spur to gallop");
		int ticks = 0;
		for (int i = 0; i < 3; i++) {
			input.pressKey(o -> o.keySprint);
			ctx.waitTicks(8);
			ticks += 8;
		}
		float surgeLean = 0.0F;
		while (sample(ctx).speed < GALLOP_SPEED * 0.95 && ticks < 200) {
			ctx.waitTick();
			ticks++;
			surgeLean = Math.max(surgeLean, riderInertia(ctx));
		}
		check("rider sways back as the horse surges (deg)", surgeLean, 1.0, 12.0);
		check("ticks from walk to 95% gallop", ticks, 25, 70);
		ctx.waitTicks(10);
		check("gallop speed (blocks/tick)", averageSpeed(ctx, 10), GALLOP_SPEED * 0.9, GALLOP_SPEED * 1.1);
		check("gait is gallop", sample(ctx).gait == RideTuning.GALLOP);
		check("gallop leg-animation speed (Fresh Animations gallop >= 0.8)", sample(ctx).limbSpeed, 0.95, 1.0);
		screenshot(ctx, "03_gallop");
		frontScreenshot(ctx, "03b_gallop_front");
		sideScreenshot(ctx, "03c_gallop_side");
		ctx.waitTicks(2);
		sideScreenshot(ctx, "03e_gallop_side_b");
		saddleSync(ctx);
		firstPersonBob(ctx);
	}

	private void turn(final ClientGameTestContext ctx, final TestInput input) {
		section("D at a gallop: horse angles 45 deg right of the view");
		final Sample start = sample(ctx);
		input.holdKey(o -> o.keyRight);
		float previousYaw = start.horseYaw;
		final float[] lean = new float[40];
		final float[] rate = new float[40];
		for (int i = 0; i < 40; i++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			lean[i] = s.lean;
			rate[i] = Mth.wrapDegrees(s.horseYaw - previousYaw);
			previousYaw = s.horseYaw;
			if (i == 10) {
				screenshot(ctx, "04_turn_right");
				frontScreenshot(ctx, "04b_turn_right_front");
			}
		}
		final Sample end = sample(ctx);
		input.releaseKey(o -> o.keyRight);
		final int banked = firstReaching(lean, 3.0F);
		final int turning = firstReaching(rate, 1.0F);
		check("horse settles 45 deg right of the view (deg)", Mth.wrapDegrees(end.horseYaw - end.playerYaw), 40.0, 50.0);
		check("view not pulled by the turn (deg)", Math.abs(Mth.wrapDegrees(end.playerYaw - start.playerYaw)), 0.0, 0.5);
		check("bank into right turn (deg)", max(lean), 3.0, 15.0);
		check("weight shift: ticks until the body turns at 1 deg/tick", turning, 2, 9);
		check("weight shift: bank (3 deg) comes before the body turns (ticks ahead)", turning - banked, 1, 6);
		ctx.waitTicks(40);
		check("releasing D: horse comes back under the view (deg off)", Math.abs(Mth.wrapDegrees(sample(ctx).horseYaw - sample(ctx).playerYaw)), 0.0, 3.0);
	}

	private static float max(final float[] values) {
		float max = values[0];
		for (final float v : values) {
			max = Math.max(max, v);
		}
		return max;
	}

	private static int firstReaching(final float[] values, final float threshold) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] >= threshold) {
				return i;
			}
		}
		return values.length;
	}

	private void firstPersonBob(final ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		ctx.waitTick();
		final float[] nod = new float[1];
		double low = Double.MAX_VALUE;
		double high = -Double.MAX_VALUE;
		for (int i = 0; i < 20; i++) {
			ctx.waitTick();
			final double bob = ctx.computeOnClient(mc -> RideCamera.firstPersonOffset(1.0F, nod));
			low = Math.min(low, bob);
			high = Math.max(high, bob);
		}
		check("first-person gallop bob is gentle (blocks peak to peak)", high - low, 0.003, 0.06);
		screenshot(ctx, "03d_gallop_first_person");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTick();
	}

	private void runningJump(final ClientGameTestContext ctx, final TestInput input) {
		section("Running jump");
		final double before = averageSpeed(ctx, 3);
		final double startY = sample(ctx).y;
		final float staminaBefore = sample(ctx).stamina;
		int elapsed = 0;
		input.pressKey(o -> o.keyJump);
		double peak = startY;
		double slowest = Double.MAX_VALUE;
		int airborne = 0;
		float noseUp = 0.0F;
		float noseDown = 0.0F;
		for (int i = 1; i <= 40; i++) {
			ctx.waitTick();
			elapsed++;
			final Sample s = sample(ctx);
			peak = Math.max(peak, s.y);
			final float tilt = ride(ctx, r -> r.jumpPitch(1.0F));
			noseUp = Math.max(noseUp, tilt);
			noseDown = Math.min(noseDown, tilt);
			if (!s.onGround) {
				airborne++;
				slowest = Math.min(slowest, s.speed);
			} else if (airborne > 0) {
				break;
			}
			if (i == 6) {
				screenshot(ctx, "05_jump");
				sideScreenshot(ctx, "05b_jump_side");
			}
		}
		check("jump fires instantly (airborne ticks)", airborne, 8, 30);
		check("running jump height (blocks)", peak - startY, 1.5, 2.4);
		check("takes off front first: nose up (deg)", noseUp, 12.0, 26.0);
		check("lands front first: nose down (deg)", -noseDown, 6.0, 16.0);
		check("speed kept in the air (fraction)", slowest / before, 0.85, 1.3);
		double landing = 0.0;
		for (int i = 0; i < 6; i++) {
			ctx.waitTick();
			landing = Math.max(landing, sample(ctx).speed);
		}
		check("no surge after landing (fastest tick / before)", landing / before, 0.9, 1.12);
		final float gallopDrain = elapsed * RideTuning.STAMINA_DRAIN_GALLOP;
		check("jump stamina cost beyond gallop drain", staminaBefore - sample(ctx).stamina - gallopDrain, RideTuning.JUMP_STAMINA_COST - 0.02, RideTuning.JUMP_STAMINA_COST + 0.03);
		ctx.waitTicks(10);
	}

	private void exhaustion(final ClientGameTestContext ctx, final TestInput input) {
		section("Gallop until exhausted");
		int ticks = 0;
		boolean lowShot = false;
		int tiredTicks = 0;
		final int huffsBefore = ctx.computeOnClient(mc -> ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().huffs);
		Sample s = sample(ctx);
		while (!s.exhausted && ticks < 1200) {
			ctx.waitTick();
			ticks++;
			s = sample(ctx);
			if (s.stamina < 0.2F) {
				tiredTicks++;
			}
			if (!lowShot && s.stamina < 0.3F) {
				screenshot(ctx, "06_stamina_low");
				lowShot = true;
			}
		}
		final int huffs = ctx.computeOnClient(mc -> ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().huffs) - huffsBefore;
		check("tired horse huffs every other stride (ticks between huffs)", huffs > 0 ? tiredTicks / (double) huffs : 0.0, 14.0, 28.0);
		final int sinceShake = ctx.computeOnClient(mc -> mc.player.getVehicle().tickCount - ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().headShakeStart);
		check("head shake starts when stamina runs out (ticks ago)", sinceShake, 0, 3);
		ctx.waitTicks(13);
		frontScreenshot(ctx, "07a_head_shake");
		ctx.waitTicks(3);
		frontScreenshot(ctx, "07b_head_shake");
		if (FabricLoader.getInstance().isModLoaded("entity_model_features")) {
			check("head shake drawn on the Fresh Animations model (frames)",
				ctx.computeOnClient(mc -> ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().animatedShakeFrames), 5, 100000);
		}
		check("horse becomes exhausted", s.exhausted);
		check("gallop stamina lasts (seconds, incl. a jump earlier)", ticks / 20.0, 15.0, 30.0);
		check("exhausted horse drops to canter", s.gait == RideTuning.CANTER);
		input.pressKey(o -> o.keySprint);
		ctx.waitTicks(5);
		check("exhausted horse refuses to gallop", sample(ctx).gait == RideTuning.CANTER);
		screenshot(ctx, "07_exhausted");
	}

	private void coastStop(final ClientGameTestContext ctx, final TestInput input) {
		section("Release W to coast");
		input.releaseKey(o -> o.keyUp);
		check("ticks to coast to a stop from canter", ticksUntilStopped(ctx, 120), 20, 50);
	}

	private void brakeAndReverse(final ClientGameTestContext ctx, final TestInput input) {
		section("Brake and back up");
		input.holdKey(o -> o.keyUp);
		input.pressKey(o -> o.keySprint);
		input.pressKey(o -> o.keySprint);
		ctx.waitTicks(60);
		final double cruising = averageSpeed(ctx, 3);
		input.releaseKey(o -> o.keyUp);
		input.holdKey(o -> o.keyDown);
		float brakeLean = 0.0F;
		int brakeTicks = 0;
		while (sample(ctx).speed > 0.01 && brakeTicks < 60) {
			ctx.waitTick();
			brakeTicks++;
			brakeLean = Math.min(brakeLean, riderInertia(ctx));
		}
		check("brake ticks from " + String.format(Locale.ROOT, "%.2f", cruising) + " b/t", brakeTicks, 5, 20);
		check("rider tips forward under braking (deg)", brakeLean, -12.0, -1.5);
		ctx.waitTicks(20);
		check("backs up while S held", sample(ctx).gait == RideTuning.STOP && signedSpeed(ctx) < -0.01);
		input.releaseKey(o -> o.keyDown);
		ticksUntilStopped(ctx, 40);
	}

	private void freeAim(final ClientGameTestContext ctx, final TestInput input) {
		section("Mouse steering and riding at an angle");
		final float heading = sample(ctx).horseYaw;
		input.holdKey(o -> o.keyUp);
		input.lookAt(heading + 90.0F, 10.0F);
		int ticks = 0;
		while (Math.abs(Mth.wrapDegrees(sample(ctx).horseYaw - (heading + 90.0F))) > 3.0F && ticks < 100) {
			ctx.waitTick();
			ticks++;
		}
		check("horse follows the mouse: ticks to turn 90 deg at a walk", ticks, 10, 45);
		sideScreenshot(ctx, "06a_walk_side_reins");

		// Drawing a bow and holding D: the horse angles off while the aim stays exactly where it was put.
		ctx.runOnClient(mc -> {
			mc.player.getInventory().setItem(mc.player.getInventory().getSelectedSlot(), new ItemStack(Items.BOW));
			mc.player.getInventory().add(new ItemStack(Items.ARROW, 16));
		});
		ctx.waitTicks(2);
		input.holdKey(o -> o.keyUse);
		ctx.waitTicks(5);
		final float aim = sample(ctx).playerYaw;
		input.holdKey(o -> o.keyRight);
		ctx.waitTicks(40);
		final Sample aiming = sample(ctx);
		check("aiming with D held: view not moved (deg)", Math.abs(Mth.wrapDegrees(aiming.playerYaw - aim)), 0.0, 0.5);
		check("aiming with D held: horse 45 deg right of the aim (deg)", Mth.wrapDegrees(aiming.horseYaw - aim), 40.0, 50.0);
		frontScreenshot(ctx, "06b_aiming_bow_front");
		input.releaseKey(o -> o.keyRight);
		input.releaseKey(o -> o.keyUse);
		input.releaseKey(o -> o.keyUp);
		ctx.runOnClient(mc -> mc.player.getInventory().setItem(mc.player.getInventory().getSelectedSlot(), ItemStack.EMPTY));
		ticksUntilStopped(ctx, 60);
	}

	private void saddleSync(final ClientGameTestContext ctx) {
		if (!FabricLoader.getInstance().isModLoaded("entity_model_features")) {
			return;
		}
		float low = Float.MAX_VALUE;
		float high = -Float.MAX_VALUE;
		boolean fresh = true;
		for (int i = 0; i < 20; i++) {
			ctx.waitTick();
			final float[] lift = ctx.computeOnClient(mc -> {
				final RideState s = ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride();
				return new float[] {s.animatedLift - s.animatedLiftBase, System.nanoTime() - s.animatedAt < 200_000_000L ? 1 : 0};
			});
			low = Math.min(low, lift[0]);
			high = Math.max(high, lift[0]);
			fresh &= lift[1] == 1.0F;
		}
		check("rider reads the horse's animated body every frame (Fresh Animations)", fresh);
		check("Fresh Animations stirrups are held for the feet", dev.horsingaround.client.compat.EmfSaddleTracker.stirrupFrames > 0);
		check("animated saddle motion at a gallop (blocks peak to peak)", high - low, 0.02, 0.4);
	}

	private void standingJump(final ClientGameTestContext ctx) {
		section("Standing jump");
		ctx.waitTicks(10);
		final double startY = sample(ctx).y;
		ctx.getInput().pressKey(o -> o.keyJump);
		double peak = startY;
		for (int i = 0; i < 20; i++) {
			ctx.waitTick();
			peak = Math.max(peak, sample(ctx).y);
		}
		check("standing jump clears a block (height, blocks)", peak - startY, 1.0, 1.45);
	}

	private void dismount(final ClientGameTestContext ctx, final TestInput input) {
		section("Dismount");
		input.holdKeyFor(o -> o.keyShift, 3);
		ctx.waitTicks(5);
		check("dismounted", ctx.computeOnClient(mc -> mc.player.getVehicle() == null));
		ctx.waitTicks(2);
		check("unridden, it is vanilla size again (width, blocks)", ctx.computeOnClient(mc -> mc.level.getEntitiesOfClass(AbstractHorse.class, mc.player.getBoundingBox().inflate(4.0))
			.stream().mapToDouble(h -> h.getBbWidth()).max().orElse(0.0)), 1.35, 1.45);
		check("camera restored to first person", ctx.computeOnClient(mc -> mc.options.getCameraType()) == CameraType.FIRST_PERSON);
	}

	private void stairs(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		final TestServerContext server = world.getServer();
		section("Climbing a staircase of 1-block steps at a trot");
		// Go there first so the chunks are loaded, then build four steps northward, three blocks deep, and a plateau.
		server.runCommand("tp @p 100.5 -60 0.5 180 10");
		ctx.waitTicks(20);
		world.getConnection().waitForChunksRender();
		server.runCommand("fill 97 -60 -30 104 -55 -3 minecraft:air");
		for (int step = 0; step < 4; step++) {
			final int near = -4 - step * 3;
			server.runCommand("fill 97 -60 " + (near - 2) + " 104 " + (-60 + step) + " " + near + " minecraft:stone");
		}
		server.runCommand("fill 97 -60 -30 104 -57 -16 minecraft:stone");
		server.runCommand("tp @p 100.5 -60 0.5 180 10");
		server.runCommand("summon minecraft:horse 100.5 -60 0.5 {Tame:1b,Variant:2,Rotation:[180f,0f],"
			+ "equipment:{saddle:{id:\"minecraft:saddle\",count:1}},"
			+ "attributes:[{id:\"minecraft:movement_speed\",base:" + SPEED_ATTRIBUTE + "d}],Tags:[\"stairs_test\"]}");
		// The horse spawns in a chunk the player only just reached; give it a moment before mounting.
		for (int attempt = 0; attempt < 10 && !ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse); attempt++) {
			ctx.waitTicks(5);
			server.runCommand("ride @p mount @e[type=minecraft:horse,tag=stairs_test,limit=1]");
		}
		ctx.waitFor(mc -> mc.player.getVehicle() instanceof AbstractHorse);
		input.lookAt(180.0F, 10.0F);
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));

		input.holdKey(o -> o.keyUp);
		input.pressKey(o -> o.keySprint);
		final double startY = sample(ctx).y;
		double maxPhysicsStep = 0.0;
		double maxVisualStep = 0.0;
		float maxPitch = 0.0F;
		Sample previous = sample(ctx);
		for (int i = 0; i < 80; i++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			maxPhysicsStep = Math.max(maxPhysicsStep, s.y - previous.y);
			maxVisualStep = Math.max(maxVisualStep, s.visualY - previous.visualY);
			maxPitch = Math.max(maxPitch, s.pitch);
			if (i == 30) {
				sideScreenshot(ctx, "08_stairs_side");
			}
			if (i == 34) {
				screenshot(ctx, "08b_stairs_back");
			}
			previous = s;
		}
		input.releaseKey(o -> o.keyUp);
		check("climbed the staircase (blocks)", sample(ctx).y - startY, 3.9, 4.1);
		check("physics still steps a block in one tick", maxPhysicsStep, 0.9, 1.1);
		check("rendered horse climbs smoothly (max rise per tick)", maxVisualStep, 0.05, 0.4);
		check("nose pitches up while climbing, but not far (deg)", maxPitch, 4.0, 10.5);
		ticksUntilStopped(ctx, 60);
		ctx.waitTicks(10);
		check("level again on the plateau (pitch deg)", Math.abs(sample(ctx).pitch), 0.0, 2.0);
	}

	private void forest(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Riding through leaves");
		final TestServerContext server = world.getServer();
		server.runCommand("fill 97 -60 -80 104 -57 -31 minecraft:stone");
		// Tall enough that the rider's chest and face are in the leaves too.
		server.runCommand("fill 97 -56 -46 104 -52 -38 minecraft:oak_leaves[persistent=true]");
		ctx.waitTicks(5);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		input.pressKey(o -> o.keySprint);
		input.pressKey(o -> o.keySprint);
		double outside = 0.0;
		int outsideTicks = 0;
		double inside = 0.0;
		int insideTicks = 0;
		boolean shot = false;
		float shield = 0.0F;
		float push = 0.0F;
		int exitShots = 0;
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 300 && sample(ctx).y > -57.5; i++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			final boolean inLeaves = ctx.computeOnClient(mc -> Foliage.leavesIn(mc.player.getVehicle()) != null);
			final float hand = ride(ctx, r -> r.shield(1.0F));
			final float back = ride(ctx, r -> r.leafPush(1.0F));
			shield = Math.max(shield, hand);
			push = Math.max(push, back);
			if (inLeaves || hand > 0.0F) {
				trace.append(String.format(Locale.ROOT, "z%.1f hand%.2f push%.1f | ", horseZ(ctx), hand, back));
			}
			if (inLeaves) {
				inside += s.speed;
				insideTicks++;
				if (!shot && insideTicks == 8) {
					screenshot(ctx, "08c_leaves");
					shot = true;
				}
			} else if (insideTicks == 0 && i > 30) {
				outside += s.speed;
				outsideTicks++;
			}
			// Coming out of the stand, hand still up: the rider from in front and above as the face clears the leaves, then
			// from the side once clear of them (the riding camera swung round: a fixed camera can't show the rider, as the
			// game never draws the local player for another camera).
			if (exitShots == 0 && horseZ(ctx) < -45.8) {
				frontScreenshot(ctx, "08d_leaves_exit_front", -25.0F);
				exitShots++;
			} else if (exitShots == 1 && horseZ(ctx) < -47.2) {
				sideScreenshot(ctx, "08e_leaves_exit_side", 0.0F);
				exitShots++;
			}
			// Carry on well clear of the leaves so later camera checks aren't taken against them.
			if (ctx.computeOnClient(mc -> mc.player.getVehicle().getZ()) < -64.0) {
				break;
			}
		}
		input.releaseKey(o -> o.keyUp);
		final double z = ctx.computeOnClient(mc -> mc.player.getVehicle().getZ());
		check("rode through a 9-block-deep stand of leaves (z beyond -47)", z, -80.0, -47.0);
		check("leaves slow the horse (inside / outside speed)", insideTicks > 0 && outsideTicks > 0 ? (inside / insideTicks) / (outside / outsideTicks) : 0.0, 0.6, 0.9);
		log("  rider: %s", trace);
		check("the rider puts a hand up against the leaves (0..1)", shield, 0.9, 1.0);
		check("the leaves push the rider back (deg)", push, 2.0, 14.0);
		check("hand down again once clear", ride(ctx, r -> r.shield(1.0F)), 0.0, 0.01);
		check("upright again once clear (deg)", Math.abs((double) ride(ctx, r -> r.leafPush(1.0F))), 0.0, 0.5);
		ticksUntilStopped(ctx, 60);
	}

	private void downhill(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping down a staircase of 1-block drops");
		final TestServerContext server = world.getServer();
		// The plateau ends at z=-80; three 1-block drops (3 deep) down to the ground, then flat.
		server.runCommand("fill 97 -60 -83 104 -58 -81 minecraft:stone");
		server.runCommand("fill 97 -60 -86 104 -59 -84 minecraft:stone");
		server.runCommand("fill 97 -60 -89 104 -60 -87 minecraft:stone");
		server.runCommand("fill 97 -57 -130 104 -50 -81 minecraft:air");
		ctx.waitTicks(5);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		for (int i = 0; i < 3; i++) {
			input.pressKey(o -> o.keySprint);
			ctx.waitTicks(4);
		}
		double cruise = 0.0;
		int cruiseTicks = 0;
		double slowest = Double.MAX_VALUE;
		double fastest = 0.0;
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 200; i++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			final double z = ctx.computeOnClient(mc -> mc.player.getVehicle().getZ());
			if (z > -79.0 && z < -72.0) {
				cruise += s.speed;
				cruiseTicks++;
			} else if (z <= -79.0 && z > -96.0) {
				slowest = Math.min(slowest, s.speed);
				fastest = Math.max(fastest, s.speed);
				trace.append(String.format(Locale.ROOT, "%.2f%s ", s.speed, s.onGround ? "" : "^"));
			} else if (z <= -96.0) {
				break;
			}
		}
		input.releaseKey(o -> o.keyUp);
		cruise /= Math.max(cruiseTicks, 1);
		log("  speeds down the steps (^ = airborne): %s", trace);
		check("going down steps: slowest tick vs cruising", slowest / cruise, 0.9, 1.1);
		check("going down steps: fastest tick vs cruising", fastest / cruise, 0.9, 1.12);
		ticksUntilStopped(ctx, 60);
	}

	private void trample(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Trampling small animals");
		final TestServerContext server = world.getServer();
		input.lookAt(180.0F, 10.0F);
		ctx.waitTicks(5);
		server.runCommand("execute at @e[tag=stairs_test,limit=1] run summon minecraft:chicken ~ ~ ~-4 {NoAI:1b,Tags:[\"walk_victim\"]}");
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(40);
		input.releaseKey(o -> o.keyUp);
		ticksUntilStopped(ctx, 40);
		check("walking into a chicken doesn't hurt it", trampled(server, "walk_victim"), 0, 0);

		for (int i = 0; i < 4; i++) {
			server.runCommand("execute at @e[tag=stairs_test,limit=1] run summon minecraft:chicken ~ ~ ~-" + (24 + i * 5) + " {NoAI:1b,Tags:[\"gallop_victim\"]}");
			server.runCommand("execute at @e[tag=stairs_test,limit=1] run summon minecraft:rabbit ~ ~ ~-" + (26 + i * 5) + " {NoAI:1b,Tags:[\"gallop_victim\"]}");
		}
		input.holdKey(o -> o.keyUp);
		for (int i = 0; i < 3; i++) {
			input.pressKey(o -> o.keySprint);
			ctx.waitTicks(5);
		}
		ctx.waitTicks(90);
		input.releaseKey(o -> o.keyUp);
		check("galloping tramples small animals in the path (of 8)", trampled(server, "gallop_victim"), 5, 8);
		check("rider unhurt", ctx.computeOnClient(mc -> mc.player.getHealth() >= mc.player.getMaxHealth()));
		ticksUntilStopped(ctx, 60);
	}

	private void water(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Wading and swimming");
		final TestServerContext server = world.getServer();
		input.lookAt(180.0F, 10.0F);
		ctx.waitTicks(5);
		// Ahead: 12 blocks of dry ground, 16 of chest-deep water over a firm bottom, 20 too deep to stand, then land.
		server.runCommand("execute at @e[tag=stairs_test,limit=1] run fill ~-3 ~-1 ~-13 ~3 ~-1 ~-28 minecraft:water");
		server.runCommand("execute at @e[tag=stairs_test,limit=1] run fill ~-3 ~-3 ~-29 ~3 ~-1 ~-48 minecraft:water");
		ctx.waitTicks(10);
		final double startZ = ctx.computeOnClient(mc -> mc.player.getVehicle().getZ());
		input.holdKey(o -> o.keyUp);
		input.pressKey(o -> o.keySprint);
		input.pressKey(o -> o.keySprint);
		double dry = 0.0;
		int dryTicks = 0;
		double wade = 0.0;
		int wadeTicks = 0;
		double swim = 0.0;
		int swimTicks = 0;
		boolean eyesDry = true;
		double steepestRise = 0.0;
		double steepestVisualRise = 0.0;
		Sample previous = sample(ctx);
		boolean wadeShot = false;
		boolean swimShot = false;
		for (int i = 0; i < 500; i++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			final double travelled = startZ - ctx.computeOnClient(mc -> mc.player.getVehicle().getZ());
			final boolean[] flags = ctx.computeOnClient(mc -> new boolean[] {
				mc.player.getVehicle() != null && ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().swimming,
				mc.player.getVehicle() != null && mc.player.getVehicle().isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
			});
			if (travelled > 4.0 && travelled < 11.0) {
				dry += s.speed;
				dryTicks++;
			} else if (travelled > 15.0 && travelled < 26.0) {
				wade += s.speed;
				wadeTicks++;
				if (!wadeShot) {
					sideScreenshot(ctx, "11a_wading");
					wadeShot = true;
				}
			} else if (travelled > 33.0 && travelled < 45.0) {
				if (flags[0]) {
					swim += s.speed;
					swimTicks++;
				}
				eyesDry &= !flags[1];
				if (!swimShot && flags[0]) {
					sideScreenshot(ctx, "11b_swimming");
					swimShot = true;
				}
			} else if (travelled > 52.0) {
				break;
			}
			if (travelled > 44.0) {
				steepestRise = Math.max(steepestRise, s.y - previous.y);
				steepestVisualRise = Math.max(steepestVisualRise, s.visualY - previous.visualY);
			}
			previous = s;
		}
		input.releaseKey(o -> o.keyUp);
		final double dryAvg = dry / Math.max(dryTicks, 1);
		check("chest-deep wading costs about half the pace (wade / dry speed)", wade / Math.max(wadeTicks, 1) / dryAvg, 0.35, 0.6);
		check("out of its depth the horse swims (ticks swimming)", swimTicks, 20, 400);
		check("swimming speed (blocks/tick)", swim / Math.max(swimTicks, 1), 0.06, 0.16);
		check("swimming horse keeps its head above water", eyesDry);
		check("rider still mounted after the swim", ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse));
		check("climbed out the far side", startZ - ctx.computeOnClient(mc -> mc.player.getVehicle().getZ()), 50.0, 80.0);
		check("climbing out is gradual (fastest rise, blocks/tick)", steepestRise, 0.0, 0.2);
		check("climbing out looks smooth (fastest rendered rise, blocks/tick)", steepestVisualRise, 0.0, 0.2);
		ticksUntilStopped(ctx, 60);
	}

	// ---- Hard cuts: the further the rider looks off, the harder the horse cuts round ----

	/** What a turn toward a view {@code angle} degrees right of a galloping horse looked like. */
	private record Turn(int ticks, double slowest, float lean, float squat, float cut, int cuts) {
	}

	/** Gallops north in its own lane, then looks {@code angle} degrees right and measures the turn. */
	private Turn gallopAndLook(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final int x, final float angle, final String shot) {
		lane(ctx, input, world, x + 0.5, -60, "cut_test_" + x);
		gallopNorth(ctx, input);
		ctx.waitTicks(40);
		final int cuts = ride(ctx, s -> s.cuts);
		final double before = sample(ctx).speed;
		input.lookAt(180.0F + angle, 10.0F);
		double slowest = Double.MAX_VALUE;
		float lean = 0.0F;
		float squat = 0.0F;
		float cut = 0.0F;
		int ticks = 0;
		final StringBuilder trace = new StringBuilder();
		while (Math.abs(Mth.wrapDegrees(sample(ctx).horseYaw - (180.0F + angle))) > 5.0F && ticks < 80) {
			ctx.waitTick();
			ticks++;
			final Sample s = sample(ctx);
			slowest = Math.min(slowest, s.speed);
			lean = Math.max(lean, s.lean);
			squat = Math.max(squat, s.pitch);
			cut = Math.max(cut, ride(ctx, r -> r.cut));
			trace.append(String.format(Locale.ROOT, "%.0f/%.2f/%.2f ", Mth.wrapDegrees(s.horseYaw - 180.0F), s.speed, ride(ctx, r -> r.cut)));
			if (shot != null && ticks == 6) {
				frontScreenshot(ctx, shot);
			}
		}
		log("  heading/speed/cut: %s", trace);
		return new Turn(ticks, slowest / before, lean, squat, cut, ride(ctx, s -> s.cuts) - cuts);
	}

	private void hardCuts(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Hard cuts off: turning the view 90 deg at a gallop (for comparison)");
		ctx.runOnClient(mc -> {
			HorseConfig.get().hardCuts = false;
			HorseConfig.apply();
		});
		final Turn plain = gallopAndLook(ctx, input, world, 1200, 90.0F, null);
		ctx.runOnClient(mc -> HorseConfig.reset());
		check("a plain turn is gradual at a gallop (ticks to come round 90 deg)", plain.ticks, 25, 60);
		check("...and keeps its pace (slowest / before)", plain.slowest, 0.85, 1.05);
		stop(ctx, input);

		section("Looking 20 deg off at a gallop: ordinary steering");
		final Turn small = gallopAndLook(ctx, input, world, 1240, 20.0F, null);
		check("no cut", small.cut, 0.0, 0.01);
		check("keeps its pace (slowest / before)", small.slowest, 0.85, 1.05);
		stop(ctx, input);

		section("Looking 60 deg off at a gallop: cuts a little");
		final Turn half = gallopAndLook(ctx, input, world, 1280, 60.0F, null);
		check("cuts partly (most, 0..1)", half.cut, 0.1, 0.6);
		stop(ctx, input);

		section("Looking 90 deg off at a gallop: sits back and cuts round");
		final Turn hard = gallopAndLook(ctx, input, world, 1320, 90.0F, "05a_hard_cut_front");
		check("cuts hard (most, 0..1)", hard.cut, 0.85, 1.0);
		check("comes round much faster than a plain turn (ticks)", hard.ticks, 10, (int) (plain.ticks * 0.65));
		check("slows to do it (slowest / before)", hard.slowest, 0.5, 0.85);
		check("slows more than for a 60 deg look (slowest, 90 vs 60)", half.slowest - hard.slowest, 0.03, 0.5);
		check("banks hard into it (deg)", hard.lean, 10.0, 15.0);
		check("sits back on its haunches (nose up, deg)", hard.squat, 2.0, 8.0);
		check("scuffs the ground once", hard.cuts, 1, 1);
		ctx.waitTicks(40);
		check("eases out of the cut", ride(ctx, s -> s.cut), 0.0, 0.01);
		check("gallops on after (speed / gallop)", sample(ctx).speed / GALLOP_SPEED, 0.9, 1.1);
		stop(ctx, input);

		section("Looking 150 deg round at a gallop: slows more, cuts tighter");
		final Turn round = gallopAndLook(ctx, input, world, 1360, 150.0F, null);
		check("slows more than for 90 deg (slowest, 90 vs 150)", hard.slowest - round.slowest, 0.05, 0.6);
		check("still comes round promptly (ticks)", round.ticks, 10, 35);
		stop(ctx, input);

		section("A or D alone at a gallop: rides across the view");
		lane(ctx, input, world, 1440.5, -60, "across_test");
		gallopNorth(ctx, input);
		ctx.waitTicks(40);
		input.releaseKey(o -> o.keyUp);
		input.holdKey(o -> o.keyRight);
		ctx.waitTicks(60);
		check("D alone: horse rides 90 deg right of the view (deg)", Mth.wrapDegrees(sample(ctx).horseYaw - sample(ctx).playerYaw), 85.0, 95.0);
		check("...at full pace (speed / gallop)", averageSpeed(ctx, 5) / GALLOP_SPEED, 0.9, 1.1);
		check("...still galloping", sample(ctx).gait == RideTuning.GALLOP);
		check("the view was never pulled (deg)", Math.abs(Mth.wrapDegrees(sample(ctx).playerYaw - 180.0F)), 0.0, 0.5);
		input.releaseKey(o -> o.keyRight);
		input.holdKey(o -> o.keyLeft);
		ctx.waitTicks(80);
		check("A alone: horse rides 90 deg left of the view (deg)", Mth.wrapDegrees(sample(ctx).horseYaw - sample(ctx).playerYaw), -95.0, -85.0);
		check("...at full pace (speed / gallop)", averageSpeed(ctx, 5) / GALLOP_SPEED, 0.9, 1.1);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(40);
		check("W+A: back to 45 deg left of the view (deg)", Mth.wrapDegrees(sample(ctx).horseYaw - sample(ctx).playerYaw), -50.0, -40.0);
		input.releaseKey(o -> o.keyLeft);
		input.releaseKey(o -> o.keyUp);
		check("letting go of everything, it eases to a stop (ticks)", ticksUntilStopped(ctx, 120), 10, 80);

		section("D alone from a standstill: walks off to the right");
		final double sx = horseX(ctx);
		final double sz = horseZ(ctx);
		input.holdKey(o -> o.keyRight);
		ctx.waitTicks(40);
		check("moves off (blocks)", Math.hypot(horseX(ctx) - sx, horseZ(ctx) - sz), 1.0, 20.0);
		check("heading 90 deg right of the view (deg)", Mth.wrapDegrees(sample(ctx).horseYaw - sample(ctx).playerYaw), 85.0, 95.0);
		check("at a walk", sample(ctx).gait == RideTuning.WALK);
		input.releaseKey(o -> o.keyRight);
		ticksUntilStopped(ctx, 60);

		section("Looking round while standing");
		lane(ctx, input, world, 1400.5, -60, "cut_test_still");
		input.lookAt(180.0F + 150.0F, 10.0F);
		ctx.waitTicks(10);
		check("doesn't turn the horse (free look, deg)", Math.abs(Mth.wrapDegrees(sample(ctx).horseYaw - 180.0F)), 0.0, 1.0);
		check("no cut", ride(ctx, s -> s.cut), 0.0, 0.0);
		input.holdKey(o -> o.keyUp);
		final int pivot = ticksToFace(ctx, 330.0F, 60);
		check("W held, it pivots round 150 deg on its haunches (ticks)", pivot, 8, 22);
		stop(ctx, input);
	}

	private static int horseTick(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.getVehicle() == null ? 0 : mc.player.getVehicle().tickCount);
	}

	/** Ticks until the horse heads within 5 degrees of yaw (or the limit). */
	private static int ticksToFace(final ClientGameTestContext ctx, final float yaw, final int limit) {
		int ticks = 0;
		while (Math.abs(Mth.wrapDegrees(sample(ctx).horseYaw - yaw)) > 5.0F && ticks < limit) {
			ctx.waitTick();
			ticks++;
		}
		return ticks;
	}

	/**
	 * Swimming across a deep pool to a bank {@code height} blocks above the top of the water's block layer: one block
	 * is climbed out of in a smooth heave, two are not.
	 */
	private void bank(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final int x, final int height) {
		section("Swimming to a bank " + height + " block" + (height == 1 ? "" : "s") + " above the water");
		// Water three deep (its top block is the ground's top layer, y=-61) from z=-4 to z=-24; the bank beyond.
		lane(ctx, input, world, x + 0.5, -60, "bank_test_" + x,
			String.format(Locale.ROOT, "fill %d -63 -24 %d -61 -4 minecraft:water", x - 4, x + 4),
			String.format(Locale.ROOT, "fill %d -60 -34 %d %d -25 minecraft:stone", x - 4, x + 4, -61 + height));
		sideCamera(world, x + 6.5, -59, -24.5, 90.0F);
		final int climbs = ride(ctx, s -> s.bankClimbs);
		input.holdKey(o -> o.keyUp);
		hitboxes(ctx, true);
		double steepestRise = 0.0;
		double steepestVisualRise = 0.0;
		double steepestTilt = 0.0;
		Sample previous = sample(ctx);
		int previousTick = horseTick(ctx);
		int heaveStart = -1;
		int heaveTicks = -1;
		int shots = 0;
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < (height <= 1 ? 400 : 250) && horseZ(ctx) > -28.0; i++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			final int bankTicks = ride(ctx, r -> r.bankTicks);
			// Per game tick: a screenshot can make the client catch up a tick or two between samples.
			final int tick = horseTick(ctx);
			final int ticks = Math.max(tick - previousTick, 1);
			previousTick = tick;
			if (horseZ(ctx) < -18.0) {
				steepestRise = Math.max(steepestRise, (s.y - previous.y) / ticks);
				steepestVisualRise = Math.max(steepestVisualRise, (s.visualY - previous.visualY) / ticks);
				steepestTilt = Math.max(steepestTilt, Math.abs(s.pitch - previous.pitch) / ticks);
				trace.append(String.format(Locale.ROOT, "%d: z%.2f y%.2f vis%.2f tilt%.1f heave%d | ", i, horseZ(ctx), s.y, s.visualY, s.pitch, bankTicks));
			}
			if (bankTicks > 0 && heaveStart < 0) {
				heaveStart = i;
			}
			if (heaveStart >= 0 && heaveTicks < 0 && s.onGround && s.y > -60.0 + height - 0.05) {
				heaveTicks = i - heaveStart;
			}
			if (heaveStart >= 0 && shots < 5 && (i - heaveStart) % 4 == 0) {
				cameraShot(ctx, String.format(Locale.ROOT, "19_bank_%d_%d", height, shots++));
			}
			previous = s;
		}
		hitboxes(ctx, false);
		log("  climb: %s", trace);
		if (height <= 1) {
			check("climbs out onto the bank", sample(ctx).y > -60.05 + height && horseZ(ctx) < -27.9);
			check("one heave", ride(ctx, s -> s.bankClimbs) - climbs, 1, 1);
			check("heaves out in about a second (ticks from the bank to on top)", heaveTicks, 8, 30);
			check("rises smoothly (fastest rise, blocks/tick)", steepestRise, 0.0, 0.2);
			check("looks smooth (fastest rendered rise, blocks/tick)", steepestVisualRise, 0.0, 0.2);
			check("tilts smoothly (most tilt change in a tick, deg)", steepestTilt, 0.0, 2.5);
			check("rider still mounted", ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse));
		} else {
			check("can't climb a bank 2 blocks above the water (still in the water)", ctx.computeOnClient(mc -> mc.player.getVehicle().isInWater()));
			check("no heave", ride(ctx, s -> s.bankClimbs) - climbs, 0, 0);
		}
		input.releaseKey(o -> o.keyUp);
		ctx.waitTicks(20);
	}

	/** Spawned with the tag minus those still alive at full health. */
	private static int trampled(final TestServerContext server, final String tag) {
		return server.computeOnServer(s -> {
			final java.util.List<? extends net.minecraft.world.entity.LivingEntity> all = s.overworld().getEntities(
				net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.LivingEntity.class), e -> e.entityTags().contains(tag));
			final long unhurt = all.stream().filter(e -> e.isAlive() && e.getHealth() >= e.getMaxHealth()).count();
			return (tag.equals("walk_victim") ? 1 : 8) - (int) unhurt;
		});
	}

	private void horseSettings(final ClientGameTestContext ctx) {
		section("Horse settings (Mod Menu)");
		ctx.setScreen(() -> new net.minecraft.client.gui.screens.PauseScreen(true));
		ctx.waitTicks(3);
		screenshot(ctx, "10a_pause_menu");
		ctx.setScreen(() -> com.terraformersmc.modmenu.api.ModMenuApi.createModsScreen(null));
		ctx.waitTicks(3);
		ctx.getInput().typeChars("Horsing");
		ctx.waitTicks(3);
		screenshot(ctx, "10b_mod_menu_list");
		ctx.setScreen(() -> null);
		check("Mod Menu lists the horse settings", FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class).stream()
			.anyMatch(entry -> entry.getProvider().getMetadata().getId().equals("horsingaround")));
		ctx.runOnClient(mc -> {
			HorseConfig.get().speed = 1.2F;
			HorseConfig.apply();
		});
		check("a changed horse setting applies (gallop speed multiple)", ctx.computeOnClient(mc -> RideTuning.GAIT_SPEED[RideTuning.GALLOP]), 1.37, 1.39);
		ctx.runOnClient(mc -> HorseConfig.reset());
		ctx.setScreen(() -> new dev.horsingaround.client.config.HorseSettingsScreen(null));
		ctx.waitTicks(5);
		screenshot(ctx, "10c_horse_settings");
		ctx.setScreen(() -> null);
	}

	// ---- The horse has a say: it picks its way (each in its own lane, riding north) ----

	private static final double TROT_SPEED = TERMINAL * RideTuning.GAIT_SPEED[RideTuning.TROT];

	private void pickingItsWay(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		treeInThePath(ctx, input, world);
		wallWithAWayRound(ctx, input, world);
		longWall(ctx, input, world);
		alongsideWall(ctx, input, world);
		cliff(ctx, input, world);
		alongTheEdge(ctx, input, world);
		safeDrop(ctx, input, world);
		lava(ctx, input, world);
		gap(ctx, input, world, true);
		gap(ctx, input, world, false);
		treeGap(ctx, input, world);
		mountainside(ctx, input, world, 640, 1);
		mountainside(ctx, input, world, 680, 2);
		hurtingDrop(ctx, input, world, false);
		hurtingDrop(ctx, input, world, true);
		ledgeAtAWalk(ctx, input, world);
		ledgeAtAGallop(ctx, input, world);
		ledgeStraightAhead(ctx, input, world);
		pillar(ctx, input, world);
		treesInARow(ctx, input, world);
		bushes(ctx, input, world);
		fence(ctx, input, world);
		input.releaseKey(o -> o.keyUp);
	}

	private void treeInThePath(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping at a tree in the path");
		lane(ctx, input, world, 300.5, -60, "tree_test", "fill 300 -60 -40 300 -56 -40 minecraft:oak_log");
		final float openOffset = ride(ctx, s -> Math.abs(s.avoidOffset));
		gallopNorth(ctx, input);
		double maxSide = 0.0;
		double slowest = Double.MAX_VALUE;
		boolean touched = false;
		float earlyOffset = 0.0F;
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 300 && horseZ(ctx) > -75.0; i++) {
			ctx.waitTick();
			final double z = horseZ(ctx);
			final double side = Math.abs(horseX(ctx) - 300.5);
			if (z > -15.0) {
				earlyOffset = Math.max(earlyOffset, ride(ctx, s -> Math.abs(s.avoidOffset)));
			}
			if (z < -25.0 && z > -50.0) {
				maxSide = Math.max(maxSide, side);
			}
			if (z < -36.0 && z > -44.0) {
				slowest = Math.min(slowest, sample(ctx).speed);
				touched |= ctx.computeOnClient(mc -> mc.player.getVehicle().horizontalCollision);
			}
			if (z < -20.0 && z > -50.0) {
				trace.append(String.format(Locale.ROOT, "z%.1f x%.2f v%.2f o%.0f | ", z, horseX(ctx), sample(ctx).speed, ride(ctx, r -> r.avoidOffset)));
			}
		}
		log("  path: %s", trace);
		check("no detour in open ground (deg)", Math.max(openOffset, earlyOffset), 0.0, 0.5);
		check("rides past the tree", horseZ(ctx) < -45.0 && sample(ctx).y > -60.1);
		check("never touches the trunk", !touched);
		check("swerves round it, no more than it needs (blocks off the line)", maxSide, 0.9, 2.0);
		check("keeps its pace going round (slowest / gallop)", slowest / GALLOP_SPEED, 0.75, 1.1);
		check("heads where the rider looks again after (deg off)", Math.abs(Mth.wrapDegrees(sample(ctx).horseYaw - 180.0F)), 0.0, 3.0);
		check("detour eased out after (deg)", ride(ctx, r -> Math.abs(r.avoidOffset)), 0.0, 0.5);
		stop(ctx, input);
	}

	private void wallWithAWayRound(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping at a wall with a way round");
		// 13 wide, 3 high; the rider looks straight at its middle.
		lane(ctx, input, world, 320.5, -60, "wall_round_test", "fill 314 -60 -40 326 -58 -40 minecraft:stone");
		gallopNorth(ctx, input);
		double maxSide = 0.0;
		double slowest = Double.MAX_VALUE;
		boolean touched = false;
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 300 && horseZ(ctx) > -60.0; i++) {
			ctx.waitTick();
			final double z = horseZ(ctx);
			maxSide = Math.max(maxSide, Math.abs(horseX(ctx) - 320.5));
			if (z < -20.0 && i % 2 == 0) {
				trace.append(String.format(Locale.ROOT, "z%.1f x%.1f v%.2f o%.0f | ", z, horseX(ctx), sample(ctx).speed, ride(ctx, r -> r.avoidOffset)));
			}
			if (z < -30.0 && z > -45.0) {
				slowest = Math.min(slowest, sample(ctx).speed);
				touched |= ctx.computeOnClient(mc -> mc.player.getVehicle().horizontalCollision);
			}
		}
		log("  path: %s", trace);
		check("goes round it", horseZ(ctx) < -55.0);
		check("never runs into it", !touched);
		check("swings out past its end (blocks off the line; its end is 6.5 out)", maxSide, 7.0, 13.0);
		check("keeps moving going round (slowest / gallop)", slowest / GALLOP_SPEED, 0.4, 1.1);
		ctx.waitTicks(40);
		check("then heads where the rider looks again (deg off)", Math.abs(Mth.wrapDegrees(sample(ctx).horseYaw - 180.0F)), 0.0, 5.0);
		stop(ctx, input);
	}

	private void treeGap(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Walking through a 1-block gap between trunks");
		// Two rows of trunks across the path with one 1-block gap, straight ahead.
		lane(ctx, input, world, 620.5, -60, "tree_gap_test", "fill 610 -60 -20 630 -56 -20 minecraft:oak_log", "fill 620 -60 -20 620 -56 -20 minecraft:air");
		input.holdKey(o -> o.keyUp);
		for (int i = 0; i < 300 && horseZ(ctx) > -26.0; i++) {
			ctx.waitTick();
		}
		check("fits through the gap", horseZ(ctx) < -25.9);
		stop(ctx, input);
	}

	/** A mountainside of steps, {@code drop} blocks down per block forward, from a 15-block-high shoulder. */
	private void mountainside(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final int x, final int drop) {
		section("Galloping down a mountainside (" + drop + " down per block)");
		final List<String> build = new ArrayList<>();
		build.add("fill " + (x - 10) + " -60 -30 " + (x + 10) + " -46 6 minecraft:stone");
		for (int k = 1; -46 - k * drop > -60; k++) {
			build.add("fill " + (x - 10) + " -60 " + (-30 - k) + " " + (x + 10) + " " + (-46 - k * drop) + " " + (-30 - k) + " minecraft:stone");
		}
		lane(ctx, input, world, x + 0.5, -45, "mountain_test_" + drop, build.toArray(String[]::new));
		final float health = ctx.computeOnClient(mc -> ((AbstractHorse) mc.player.getVehicle()).getHealth());
		gallopNorth(ctx, input);
		double longestFall = 0.0;
		double slope = 0.0;
		int slopeTicks = 0;
		for (int i = 0; i < 400 && horseZ(ctx) > -60.0; i++) {
			ctx.waitTick();
			longestFall = Math.max(longestFall, ctx.computeOnClient(mc -> mc.player.getVehicle().fallDistance));
			final double z = horseZ(ctx);
			if (z < -31.0 && sample(ctx).y > -59.5) {
				slope += sample(ctx).speed;
				slopeTicks++;
			}
		}
		ctx.waitTicks(10);
		check("gets down (blocks descended)", -45.0 - sample(ctx).y, 14.5, 15.5);
		check("never falls far enough to get hurt (longest fall, blocks)", longestFall, 0.0, 7.95);
		check("horse unhurt", ctx.computeOnClient(mc -> ((AbstractHorse) mc.player.getVehicle()).getHealth()) >= health);
		check("takes the slope at a safe pace (speed / gallop)", slope / Math.max(slopeTicks, 1) / GALLOP_SPEED, 0.15, drop == 1 ? 1.05 : 0.8);
		stop(ctx, input);
	}

	/** A sheer 9-block drop: one fall-damage point for the horse (horses take half damage past 6 blocks). Healthy, it takes it; hurt, it won't. */
	private void hurtingDrop(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final boolean hurt) {
		section(hurt ? "Galloping at a 9-block drop, horse at 30% health" : "Galloping at a 9-block drop, healthy");
		final int x = hurt ? 740 : 720;
		final String tag = hurt ? "drop7_hurt_test" : "drop7_test";
		lane(ctx, input, world, x + 0.5, -51, tag, "fill " + (x - 5) + " -60 -40 " + (x + 5) + " -52 6 minecraft:stone");
		final TestServerContext server = world.getServer();
		server.runCommand("attribute @e[tag=" + tag + ",limit=1] minecraft:max_health base set 20");
		server.runCommand("data merge entity @e[tag=" + tag + ",limit=1] {Health:" + (hurt ? 6 : 20) + "f}");
		ctx.waitTicks(5);
		log("  horse health %.1f / %.1f, will fall up to %.2f blocks",
			ctx.computeOnClient(mc -> ((AbstractHorse) mc.player.getVehicle()).getHealth()),
			ctx.computeOnClient(mc -> ((AbstractHorse) mc.player.getVehicle()).getMaxHealth()),
			ctx.computeOnClient(mc -> dev.horsingaround.ride.Awareness.acceptableFall((AbstractHorse) mc.player.getVehicle())));
		final float before = ctx.computeOnClient(mc -> ((AbstractHorse) mc.player.getVehicle()).getHealth());
		gallopNorth(ctx, input);
		boolean moving = false;
		for (int i = 0; i < 300 && horseZ(ctx) > -50.0; i++) {
			ctx.waitTick();
			final double speed = sample(ctx).speed;
			moving |= speed > 0.3;
			if (moving && speed < 0.001 && sample(ctx).onGround) {
				break;
			}
		}
		ctx.waitTicks(10);
		if (hurt) {
			check("a hurt horse won't take a fall that hurts (still on top)", sample(ctx).y > -51.05);
		} else {
			check("a healthy horse takes a fall that costs half a heart (landed below)", sample(ctx).y < -59.9);
			check("and it costs no more than that (health lost)", before - ctx.computeOnClient(mc -> ((AbstractHorse) mc.player.getVehicle()).getHealth()), 0.5, 1.0);
		}
		stop(ctx, input);
	}

	private void bushes(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Walking into bushes on a step");
		// A 1-block step with leaves on it and a lone log in the middle of the path: no ledge to jump onto.
		lane(ctx, input, world, 760.5, -60, "bush_test",
			"fill 755 -60 -20 765 -60 -20 minecraft:stone",
			"fill 755 -59 -20 765 -58 -20 minecraft:oak_leaves[persistent=true]",
			"setblock 760 -59 -20 minecraft:oak_log");
		final int climbs = ride(ctx, s -> s.ledgeClimbs);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(160);
		check("doesn't try to jump onto a lone log in a bush", ride(ctx, r -> r.ledgeClimbs) == climbs && sample(ctx).y < -59.9);
		stop(ctx, input);
		section("Walking into a leafy step");
		lane(ctx, input, world, 780.5, -60, "leafy_test",
			"fill 775 -60 -20 785 -60 -20 minecraft:stone",
			"fill 775 -59 -20 785 -58 -20 minecraft:oak_leaves[persistent=true]");
		final int leafyClimbs = ride(ctx, s -> s.ledgeClimbs);
		input.holdKey(o -> o.keyUp);
		for (int i = 0; i < 300 && horseZ(ctx) > -24.0; i++) {
			ctx.waitTick();
		}
		check("steps up through the leaves instead of jumping onto them", ride(ctx, r -> r.ledgeClimbs) == leafyClimbs && horseZ(ctx) < -23.9);
		stop(ctx, input);
	}

	private void longWall(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping at a wall with no way round in reach");
		lane(ctx, input, world, 560.5, -60, "wall_test", "fill 540 -60 -40 580 -58 -40 minecraft:stone");
		final int climbs = ride(ctx, s -> s.ledgeClimbs);
		gallopNorth(ctx, input);
		double contactSpeed = -1.0;
		double maxSide = 0.0;
		for (int i = 0; i < 300 && contactSpeed < 0.0; i++) {
			ctx.waitTick();
			maxSide = Math.max(maxSide, Math.abs(horseX(ctx) - 560.5));
			if (ctx.computeOnClient(mc -> mc.player.getVehicle().horizontalCollision)) {
				contactSpeed = Math.max(sample(ctx).speed, ctx.computeOnClient(mc -> mc.player.getVehicle().getDeltaMovement().horizontalDistance()));
			}
		}
		check("slows to a walk before the wall (speed at contact / walk)", contactSpeed / WALK_SPEED, 0.0, 1.15);
		check("walks right up to it (gap to the wall, blocks)", horseZ(ctx) - half(ctx) - -39.0, -0.01, 0.3);
		check("doesn't veer off along a wall it can't get round (blocks off the line)", maxSide, 0.0, 1.0);
		ctx.waitTicks(40);
		check("doesn't try to climb a 3-block wall", ride(ctx, s -> s.ledgeClimbs) == climbs && sample(ctx).y < -59.9);
		stop(ctx, input);
	}

	private void alongsideWall(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping alongside a wall");
		// The wall's face is at x=499; the horse's flank (half-width 0.45 while ridden) runs 0.05 from it.
		lane(ctx, input, world, 499.5, -60, "beside_test", "fill 498 -60 6 498 -58 -80 minecraft:stone");
		gallopNorth(ctx, input);
		double speed = 0.0;
		int ticks = 0;
		float maxOffset = 0.0F;
		for (int i = 0; i < 300 && horseZ(ctx) > -70.0; i++) {
			ctx.waitTick();
			final double z = horseZ(ctx);
			maxOffset = Math.max(maxOffset, ride(ctx, s -> Math.abs(s.avoidOffset)));
			if (z < -30.0 && z > -65.0) {
				speed += sample(ctx).speed;
				ticks++;
			}
		}
		check("keeps full pace beside a wall (speed / gallop)", speed / Math.max(ticks, 1) / GALLOP_SPEED, 0.92, 1.1);
		check("doesn't shy away from it (deg)", maxOffset, 0.0, 0.5);
		check("stays beside it (blocks off the line)", Math.abs(horseX(ctx) - 499.5), 0.0, 0.3);
		stop(ctx, input);
	}

	private void cliff(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping at a 14-block cliff");
		lane(ctx, input, world, 340.5, -46, "cliff_test", "fill 335 -60 -40 345 -47 6 minecraft:stone");
		final int refusals = ride(ctx, s -> s.refusals);
		gallopNorth(ctx, input);
		boolean moving = false;
		for (int i = 0; i < 300; i++) {
			ctx.waitTick();
			final double speed = sample(ctx).speed;
			moving |= speed > 0.3;
			if (moving && speed < 0.001) {
				break;
			}
		}
		check("stops at the edge (still on top)", sample(ctx).y > -46.05 && sample(ctx).onGround);
		check("stops close to the edge, not early (front to edge, blocks)", horseZ(ctx) - half(ctx) - -40.0, 0.0, 2.5);
		check("snorts and tosses its head", ride(ctx, s -> s.refusals) > refusals);
		frontScreenshot(ctx, "13_cliff_refusal");
		ctx.waitTicks(40);
		check("holding W at the edge, it stays put", sample(ctx).y > -46.05 && horseZ(ctx) - half(ctx) > -40.0);
		input.pressKey(o -> o.keyJump);
		ctx.waitTicks(30);
		check("won't jump off the cliff", sample(ctx).y > -46.05 && sample(ctx).onGround);
		stop(ctx, input);
	}

	private void alongTheEdge(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping along a cliff edge");
		// A 5-wide ridge; the horse runs with its flank hanging 0.25 over the west edge (x=378), then the ridge ends.
		lane(ctx, input, world, 378.2, -46, "edge_test", "fill 378 -60 -60 382 -47 6 minecraft:stone");
		gallopNorth(ctx, input);
		double speed = 0.0;
		int ticks = 0;
		double lowest = 0.0;
		float maxOffset = 0.0F;
		boolean moving = false;
		for (int i = 0; i < 300; i++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			final double z = horseZ(ctx);
			lowest = Math.min(lowest, s.y - -46.0);
			if (z < -20.0 && z > -45.0) {
				speed += s.speed;
				ticks++;
				maxOffset = Math.max(maxOffset, ride(ctx, r -> Math.abs(r.avoidOffset)));
			}
			moving |= s.speed > 0.3;
			if (moving && s.speed < 0.001) {
				break;
			}
		}
		check("keeps full pace along the edge (speed / gallop)", speed / Math.max(ticks, 1) / GALLOP_SPEED, 0.92, 1.1);
		check("doesn't shy from the edge (deg)", maxOffset, 0.0, 0.5);
		check("stays on the ridge (lowest, blocks)", lowest, -0.05, 0.05);
		check("stops where the ridge ends", sample(ctx).y > -46.05 && horseZ(ctx) - half(ctx) > -60.0);
		stop(ctx, input);
	}

	private void safeDrop(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping off a safe 4-block drop");
		lane(ctx, input, world, 360.5, -56, "drop_test", "fill 355 -60 -40 365 -57 6 minecraft:stone");
		final int refusals = ride(ctx, s -> s.refusals);
		gallopNorth(ctx, input);
		double edgeSpeed = 0.0;
		for (int i = 0; i < 300 && horseZ(ctx) > -55.0; i++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			if (s.y > -56.05 && horseZ(ctx) < -36.0) {
				edgeSpeed = s.speed;
			}
		}
		check("rides off it without slowing (speed at the edge / gallop)", edgeSpeed / GALLOP_SPEED, 0.9, 1.1);
		check("no refusal", ride(ctx, s -> s.refusals) == refusals);
		check("landed below", sample(ctx).y < -59.9);
		stop(ctx, input);
	}

	private void lava(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping at a lava lake with no way round");
		lane(ctx, input, world, 600.5, -60, "lava_test", "fill 585 -61 -48 615 -61 -40 minecraft:lava");
		gallopNorth(ctx, input);
		boolean burnt = false;
		boolean moving = false;
		for (int i = 0; i < 300; i++) {
			ctx.waitTick();
			burnt |= ctx.computeOnClient(mc -> mc.player.getVehicle().isInLava());
			final double speed = sample(ctx).speed;
			moving |= speed > 0.3;
			if (moving && speed < 0.001) {
				break;
			}
		}
		check("stops short of lava (never in it)", !burnt && sample(ctx).y > -60.05);
		check("stops close to it, not early (front to lava, blocks)", horseZ(ctx) - half(ctx) - -39.0, 0.0, 2.5);
		stop(ctx, input);
	}

	private void gap(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final boolean jump) {
		section(jump ? "Jumping a 3-block gap at a gallop" : "Galloping at a 3-block gap without jumping");
		final int x = jump ? 460 : 480;
		lane(ctx, input, world, x + 0.5, -46, jump ? "gap_jump_test" : "gap_test",
			"fill " + (x - 5) + " -60 -60 " + (x + 5) + " -47 6 minecraft:stone",
			"fill " + (x - 5) + " -60 -33 " + (x + 5) + " -47 -31 minecraft:air");
		final int refusals = ride(ctx, s -> s.refusals);
		gallopNorth(ctx, input);
		boolean pressed = false;
		boolean moving = false;
		for (int i = 0; i < 300 && horseZ(ctx) > -45.0; i++) {
			ctx.waitTick();
			if (jump && !pressed && horseZ(ctx) < -28.4) {
				input.pressKey(o -> o.keyJump);
				pressed = true;
			}
			final double speed = sample(ctx).speed;
			moving |= speed > 0.3;
			if (!jump && moving && speed < 0.001) {
				break;
			}
		}
		if (jump) {
			ctx.waitTicks(10);
			check("cleared the gap and landed on top", horseZ(ctx) < -45.0 && sample(ctx).y > -46.05);
			check("didn't brake for a gap it can jump", ride(ctx, s -> s.refusals) == refusals);
		} else {
			check("plants its feet at the lip (still on top)", sample(ctx).y > -46.05 && horseZ(ctx) - half(ctx) > -31.0);
		}
		stop(ctx, input);
	}

	private void ledgeAtAWalk(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Walking at a 2-block ledge");
		// The ledge's face is at z=-19.
		lane(ctx, input, world, 400.5, -60, "ledge_test", "fill 395 -60 -40 405 -59 -20 minecraft:stone");
		final int climbs = ride(ctx, s -> s.ledgeClimbs);
		input.holdKey(o -> o.keyUp);
		double approach = 0.0;
		double takeoffZ = Double.NaN;
		int airborne = 0;
		double risingTravel = -1.0;
		double peak = -60.0;
		double after = -1.0;
		int landedAt = -1;
		boolean shot = false;
		for (int i = 0; i < 300 && horseZ(ctx) > -26.0; i++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			final double z = horseZ(ctx);
			if (Double.isNaN(takeoffZ)) {
				if (ride(ctx, r -> r.ledgeClimbs) > climbs) {
					takeoffZ = z;
				} else {
					approach = s.speed;
				}
				continue;
			}
			peak = Math.max(peak, s.y);
			if (!s.onGround) {
				airborne++;
			} else if (landedAt < 0 && s.y > -58.05) {
				landedAt = i;
			}
			if (landedAt >= 0 && i == landedAt + 10) {
				after = s.speed;
			}
			if (risingTravel < 0.0 && s.y >= -58.05) {
				risingTravel = takeoffZ - z;
			}
			if (!shot && s.y > -59.0) {
				sideScreenshot(ctx, "14_ledge_jump_side");
				shot = true;
			}
		}
		check("jumps up a 2-block ledge (blocks gained)", sample(ctx).y - -60.0, 1.95, 2.05);
		check("one ledge jump", ride(ctx, r -> r.ledgeClimbs) - climbs, 1, 1);
		check("takes off in its stride, before the wall (front to face, blocks)", takeoffZ - half(ctx) - -19.0, 0.4, 1.8);
		check("no stop before it (walking speed at takeoff, blocks/tick)", approach / WALK_SPEED, 0.6, 1.2);
		check("an arc, not a climb: forward travel on the way up (blocks)", risingTravel, 0.5, 2.5);
		check("in the air like a jump (ticks)", airborne, 5, 16);
		check("clears the lip without launching (peak above the top, blocks)", peak - -58.0, 0.05, 0.6);
		check("walks on from the top (speed after landing / walk)", after / WALK_SPEED, 0.6, 1.2);
		stop(ctx, input);
	}

	private void ledgeAtAGallop(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping at a 2-block ledge");
		lane(ctx, input, world, 420.5, -60, "ledge_gallop_test", "fill 405 -60 -60 435 -59 -40 minecraft:stone");
		gallopNorth(ctx, input);
		double climbSpeed = -1.0;
		double lowest = Double.MAX_VALUE;
		final int climbs = ride(ctx, s -> s.ledgeClimbs);
		for (int i = 0; i < 300 && horseZ(ctx) > -45.0; i++) {
			ctx.waitTick();
			if (climbSpeed < 0.0 && ride(ctx, s -> s.ledgeClimbs) > climbs) {
				climbSpeed = previousSpeed;
			}
			if (horseZ(ctx) < -30.0 && climbSpeed < 0.0) {
				lowest = Math.min(lowest, sample(ctx).speed);
			}
			previousSpeed = sample(ctx).speed;
		}
		check("slows before jumping up (speed at takeoff / trot)", climbSpeed / TROT_SPEED, 0.0, 1.05);
		check("only to a trot, not a walk (slowest before takeoff / trot)", lowest / TROT_SPEED, 0.85, 1.05);
		check("then jumps up", sample(ctx).y - -60.0, 1.95, 2.05);
		stop(ctx, input);
	}

	/** A 2-block ledge only 5 wide, so there is a way round: ridden straight at, the horse jumps it rather than going round. */
	private void ledgeStraightAhead(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		for (final boolean gallop : new boolean[] {false, true}) {
			section((gallop ? "Galloping" : "Trotting") + " straight at a narrow 2-block ledge with a way round");
			final int x = gallop ? 1080 : 1060;
			lane(ctx, input, world, x + 0.5, -60, "ledge_line_test_" + x, String.format(Locale.ROOT, "fill %d -60 -40 %d -59 -20 minecraft:stone", x - 2, x + 2));
			final int climbs = ride(ctx, s -> s.ledgeClimbs);
			input.lookAt(180.0F, 10.0F);
			input.holdKey(o -> o.keyUp);
			ctx.waitTicks(2);
			for (int i = 0; i < (gallop ? 3 : 1); i++) {
				input.pressKey(o -> o.keySprint);
				ctx.waitTicks(4);
			}
			double maxSide = 0.0;
			float maxOffset = 0.0F;
			for (int i = 0; i < 300 && horseZ(ctx) > -26.0; i++) {
				ctx.waitTick();
				maxSide = Math.max(maxSide, Math.abs(horseX(ctx) - (x + 0.5)));
				maxOffset = Math.max(maxOffset, ride(ctx, s -> Math.abs(s.avoidOffset)));
			}
			check("doesn't go round it (deg of detour)", maxOffset, 0.0, 1.0);
			check("stays on the rider's line (blocks off it)", maxSide, 0.0, 0.3);
			check("jumps up it", ride(ctx, s -> s.ledgeClimbs) - climbs, 1, 1);
			check("on top (blocks gained)", sample(ctx).y - -60.0, 1.95, 2.05);
			stop(ctx, input);
		}

		section("Trotting past a 2-block ledge that only catches the flank");
		// The ledge's west face is 0.25 east of the rider's line: the body overlaps it by 0.2, the line itself is clear.
		lane(ctx, input, world, 1100.75, -60, "ledge_flank_test", "fill 1101 -60 -40 1106 -59 -20 minecraft:stone");
		final int climbs = ride(ctx, s -> s.ledgeClimbs);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(2);
		input.pressKey(o -> o.keySprint);
		boolean touched = false;
		double maxSide = 0.0;
		for (int i = 0; i < 300 && horseZ(ctx) > -42.0; i++) {
			ctx.waitTick();
			maxSide = Math.max(maxSide, Math.abs(horseX(ctx) - 1100.75));
			touched |= horseZ(ctx) < -18.0 && ctx.computeOnClient(mc -> mc.player.getVehicle().horizontalCollision);
		}
		check("goes round it instead of jumping", ride(ctx, s -> s.ledgeClimbs) == climbs && sample(ctx).y < -59.9);
		check("rides on past it", horseZ(ctx) < -41.9);
		check("without scraping it", !touched);
		check("moves over only as much as it needs (blocks off the line)", maxSide, 0.2, 1.2);
		stop(ctx, input);
	}

	/**
	 * Trunks in a row: one dead ahead, then one just right of the line and one further left. Going round the first on the
	 * right (the side the horse would try first) leads into the second; the lane on the left runs clear past all three.
	 */
	private void treesInARow(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping at trunks in a row");
		lane(ctx, input, world, 1500.5, -60, "row_test",
			"fill 1500 -60 -25 1500 -56 -25 minecraft:oak_log",
			"fill 1502 -60 -29 1502 -56 -29 minecraft:oak_log",
			"fill 1497 -60 -33 1497 -56 -33 minecraft:oak_log");
		gallopNorth(ctx, input);
		boolean touched = false;
		double maxSide = 0.0;
		double slowest = Double.MAX_VALUE;
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 300 && horseZ(ctx) > -45.0; i++) {
			ctx.waitTick();
			final double z = horseZ(ctx);
			if (z < -18.0 && z > -40.0) {
				touched |= ctx.computeOnClient(mc -> mc.player.getVehicle().horizontalCollision);
				maxSide = Math.max(maxSide, Math.abs(horseX(ctx) - 1500.5));
				slowest = Math.min(slowest, sample(ctx).speed);
				trace.append(String.format(Locale.ROOT, "z%.1f x%.2f v%.2f o%.0f | ", z, horseX(ctx), sample(ctx).speed, ride(ctx, r -> r.avoidOffset)));
			}
		}
		log("  path: %s", trace);
		check("rides through", horseZ(ctx) < -44.9);
		check("never touches any of them", !touched);
		check("threads them without swinging wide (blocks off the line)", maxSide, 0.5, 2.5);
		check("keeps its pace (slowest / gallop)", slowest / GALLOP_SPEED, 0.6, 1.1);
		stop(ctx, input);
	}

	/** A 2-block pillar dead ahead: nothing to land on, so the horse goes round it. */
	private void pillar(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Trotting at a 2-block pillar");
		lane(ctx, input, world, 1120.5, -60, "pillar_test", "fill 1120 -60 -20 1120 -59 -20 minecraft:stone");
		final int climbs = ride(ctx, s -> s.ledgeClimbs);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(2);
		input.pressKey(o -> o.keySprint);
		boolean touched = false;
		double maxSide = 0.0;
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 300 && horseZ(ctx) > -30.0; i++) {
			ctx.waitTick();
			maxSide = Math.max(maxSide, Math.abs(horseX(ctx) - 1120.5));
			touched |= ctx.computeOnClient(mc -> mc.player.getVehicle().horizontalCollision);
			if (horseZ(ctx) < -10.0) {
				trace.append(String.format(Locale.ROOT, "z%.1f x%.2f v%.2f o%.0f | ", horseZ(ctx), horseX(ctx), sample(ctx).speed, ride(ctx, r -> r.avoidOffset)));
			}
		}
		log("  path: %s", trace);
		check("goes round it (no jump onto it)", ride(ctx, s -> s.ledgeClimbs) == climbs && horseZ(ctx) < -29.9 && sample(ctx).y < -59.9);
		check("never touches it", !touched);
		check("swerves no more than it needs (blocks off the line)", maxSide, 0.9, 2.0);
		stop(ctx, input);
	}

	private double previousSpeed;

	private void fence(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Walking into a fence");
		lane(ctx, input, world, 520.5, -60, "fence_test", "fill 515 -60 -20 525 -60 -20 minecraft:oak_fence");
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(200);
		check("won't climb a fence (pens still hold horses)", sample(ctx).y < -59.9 && horseZ(ctx) > -19.0);
		stop(ctx, input);
	}

	private void stepsAndFooting(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		stepInTwoBeats(ctx, input, world, 800, true, false);
		stepInTwoBeats(ctx, input, world, 810, true, true);
		stepInTwoBeats(ctx, input, world, 820, false, false);
		ditch(ctx, input, world, 840, true);
		ditch(ctx, input, world, 850, false);
		trunkCorner(ctx, input, world, 860, false);
		trunkCorner(ctx, input, world, 870, true);
		leafyLedge(ctx, input, world);
	}

	/**
	 * A single 1-block step up (or down) at a walk or trot: the forehand goes first with the front legs folding up onto
	 * it (or reaching down), the body tilts only a little, then the hindquarters follow with a push. Side shots with
	 * hitboxes and the steering overlay on, every tick or two through the step.
	 */
	private void stepInTwoBeats(
		final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final int x, final boolean up, final boolean trot
	) {
		final String name = (up ? "up" : "down") + "_" + (trot ? "trot" : "walk");
		section((up ? "Up" : "Down") + " a 1-block step at a " + (trot ? "trot" : "walk") + ", in two beats");
		// Riding north from z=0.5, the step's edge is at z=-9.
		final double low = up ? -60.0 : -59.0;
		final double step = up ? 1.0 : -1.0;
		lane(ctx, input, world, x + 0.5, low, "step_test_" + x,
			up ? String.format(Locale.ROOT, "fill %d -60 -30 %d -60 -10 minecraft:stone", x - 3, x + 3)
				: String.format(Locale.ROOT, "fill %d -60 -9 %d -60 12 minecraft:stone", x - 3, x + 3));
		sideCamera(world, x + 4.0, low - 0.6, -9.0, 90.0F);
		input.holdKey(o -> o.keyUp);
		if (trot) {
			ctx.waitTicks(2);
			input.pressKey(o -> o.keySprint);
		}
		hitboxes(ctx, true);
		int foreHalf = -1;
		int hindHalf = -1;
		float maxTilt = 0.0F;
		double maxFore = 0.0;
		double maxHind = 0.0;
		double forehandFirst = Double.NaN;
		double maxVisualStep = 0.0;
		double maxTiltRate = 0.0;
		int shots = 0;
		Sample previous = sample(ctx);
		int previousTick = ctx.computeOnClient(mc -> mc.player.getVehicle().tickCount);
		for (int i = 0; i < 300 && horseZ(ctx) > -14.0; i++) {
			ctx.waitTick();
			final int tick = ctx.computeOnClient(mc -> mc.player.getVehicle().tickCount);
			final double[] legs = ctx.computeOnClient(mc -> {
				final RideState r = ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride();
				return new double[] {r.debugFore(), r.debugHind(), r.foreLeg(1.0F), r.hindLeg(1.0F)};
			});
			final Sample s = sample(ctx);
			// Share of the step the front and the back of the body have made.
			final double fore = (legs[0] - low) / step;
			final double hind = (legs[1] - low) / step;
			if (foreHalf < 0 && fore >= 0.5) {
				foreHalf = tick;
			}
			if (hindHalf < 0 && hind >= 0.5) {
				hindHalf = tick;
			}
			if (Double.isNaN(forehandFirst) && fore >= 0.75) {
				forehandFirst = hind;
			}
			if (fore > 0.02 && (hindHalf < 0 || tick <= hindHalf + 8)) {
				log("    tick %d: physics %.2f body %.3f front %.3f back %.3f tilt %.1f legs %.2f / %.2f",
					tick, (s.y - low) / step, (s.visualY - low) / step, fore, hind, s.pitch, legs[2], legs[3]);
			}
			maxTilt = Math.max(maxTilt, (float) (s.pitch * step));
			maxFore = Math.max(maxFore, legs[2] * step);
			maxHind = Math.max(maxHind, legs[3]);
			// (The test thread now and then sees two ticks at once.)
			if (tick > previousTick) {
				maxVisualStep = Math.max(maxVisualStep, Math.abs(s.visualY - previous.visualY) / (tick - previousTick));
				maxTiltRate = Math.max(maxTiltRate, Math.abs(s.pitch - previous.pitch) / (tick - previousTick));
			}
			previous = s;
			previousTick = tick;
			if (fore > 0.02 && (hindHalf < 0 || tick <= hindHalf + 4) && shots < 14) {
				cameraShot(ctx, String.format(Locale.ROOT, "15_%s_%02d", name, shots++));
			}
		}
		hitboxes(ctx, false);
		input.releaseKey(o -> o.keyUp);
		final String ended = ctx.computeOnClient(mc -> String.format(Locale.ROOT, "%.2f %.2f %.2f, camera %s, gait %d",
			mc.player.getVehicle().getX(), mc.player.getVehicle().getY(), mc.player.getVehicle().getZ(), mc.getCameraEntity().getType().toShortString(),
			((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().gait));
		log("  ended at %s", ended);
		ticksUntilStopped(ctx, 60);
		ctx.waitTicks(10);
		check("on the other level (blocks)", (sample(ctx).y - low) * step, 0.95, 1.05);
		check("the forehand goes first (ticks before the hindquarters)", hindHalf - foreHalf, trot ? 2 : 4, trot ? 8 : 12);
		check("leans no more than a real horse (max tilt, deg)", maxTilt, 3.0, 10.0);
		check("leans gently, never snaps (max tilt change per tick, deg)", maxTiltRate, 0.2, 3.0);
		check("two beats: forehand three quarters " + (up ? "up" : "down") + ", hindquarters still behind (their share of the step)", forehandFirst, 0.0, trot ? 0.5 : 0.35);
		check(up ? "front legs fold up onto the step" : "front legs reach down for it", maxFore, 0.4, 1.0);
		if (up) {
			check("hind legs drive the hindquarters up", maxHind, 0.6, 1.0);
		}
		check("smooth (max rendered height change per tick, blocks)", maxVisualStep, 0.03, trot ? 0.2 : 0.15);
		check("level again after (pitch deg)", Math.abs(sample(ctx).pitch), 0.0, 1.5);
	}

	/**
	 * Across a 1-block-deep ditch two blocks wide: off the near edge the horse is in the air when it meets the far side
	 * (a 1-block rise). It gets a hoof on it and carries on, instead of stopping dead.
	 */
	private void ditch(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final int x, final boolean gallop) {
		section((gallop ? "Galloping" : "Trotting") + " across a 1-block-deep ditch");
		lane(ctx, input, world, x + 0.5, -60, "ditch_test_" + x, String.format(Locale.ROOT, "fill %d -61 -40 %d -61 -39 minecraft:air", x - 3, x + 3));
		if (gallop) {
			gallopNorth(ctx, input);
		} else {
			input.holdKey(o -> o.keyUp);
			ctx.waitTicks(2);
			input.pressKey(o -> o.keySprint);
		}
		sideCamera(world, x + 6.5, -60, -40.0, 90.0F);
		hitboxes(ctx, true);
		final int gait = gallop ? RideTuning.GALLOP : RideTuning.TROT;
		double paceBefore = 0.0;
		double slowest = Double.MAX_VALUE;
		double ridePaceBefore = 0.0;
		double slowestRide = Double.MAX_VALUE;
		int shots = 0;
		for (int i = 0; i < 400 && horseZ(ctx) > -48.0; i++) {
			ctx.waitTick();
			final double z = horseZ(ctx);
			final Sample s = sample(ctx);
			final double ridePace = ride(ctx, r -> (double) r.speed);
			if (z > -37.5) {
				paceBefore = s.speed;
				ridePaceBefore = ridePace;
			} else if (z > -44.0) {
				slowest = Math.min(slowest, s.speed);
				slowestRide = Math.min(slowestRide, ridePace);
				if (shots < 6) {
					cameraShot(ctx, String.format(Locale.ROOT, "16_ditch_%s_%02d", gallop ? "gallop" : "trot", shots++));
				}
			}
		}
		hitboxes(ctx, false);
		check("out the far side (z beyond -47)", horseZ(ctx) < -47.0);
		check("on the far side's level, not in the ditch (y)", sample(ctx).y, -60.05, -59.95);
		check("no crash: still at the same gait", sample(ctx).gait == gait);
		check("keeps its pace (lowest ride speed / before)", slowestRide / ridePaceBefore, 0.9, 1.01);
		check("never stopped against the far side (slowest ground speed / before)", slowest / paceBefore, 0.6, 1.2);
		stop(ctx, input);
	}

	/**
	 * A tree trunk catching the edge of the body. At a walk (where the horse leaves the steering to the rider) it slips
	 * past the corner instead of stopping dead; at a gallop it goes round, or slips past, but never crashes.
	 */
	private void trunkCorner(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final int x, final boolean gallop) {
		section((gallop ? "Galloping" : "Walking") + " past a trunk that catches the shoulder");
		// The box (0.9 wide, centred 0.35 short of the trunk's side) overlaps the trunk by 0.1: too little for the
		// look-ahead's flank lines (just inside the body's edge) to see.
		final double lane = x + 0.65;
		lane(ctx, input, world, lane, -60, "corner_test_" + x, String.format(Locale.ROOT, "fill %d -60 -30 %d -56 -30 minecraft:oak_log", x + 1, x + 1));
		final int slips = ride(ctx, r -> r.slips);
		if (gallop) {
			gallopNorth(ctx, input);
		} else {
			input.holdKey(o -> o.keyUp);
		}
		sideCamera(world, x - 4.5, -60, -30.0, -90.0F);
		hitboxes(ctx, true);
		double slowestRide = Double.MAX_VALUE;
		double ridePaceBefore = 0.0;
		int shots = 0;
		for (int i = 0; i < 400 && horseZ(ctx) > -36.0; i++) {
			ctx.waitTick();
			final double z = horseZ(ctx);
			final double ridePace = ride(ctx, r -> (double) r.speed);
			if (z > -28.0) {
				ridePaceBefore = ridePace;
			} else {
				slowestRide = Math.min(slowestRide, ridePace);
			}
			if (z < -28.3 && shots < 4) {
				cameraShot(ctx, String.format(Locale.ROOT, "17_trunk_%s_%d", gallop ? "gallop" : "walk", shots++));
			}
		}
		hitboxes(ctx, false);
		check("got past the trunk (z beyond -35)", horseZ(ctx) < -35.0);
		if (!gallop) {
			check("slipped past its corner", ride(ctx, r -> r.slips) - slips, 1, 3);
			check("still on its line (blocks off it)", Math.abs(horseX(ctx) - lane), 0.0, 0.4);
		}
		check("no crash: keeps its pace (lowest ride speed / before)", slowestRide / ridePaceBefore, 0.9, 1.2);
		check("no crash: still at the same gait", sample(ctx).gait == (gallop ? RideTuning.GALLOP : RideTuning.WALK));
		stop(ctx, input);
	}

	/** A 2-block ledge with leaves on top of it (a bush on the bank): the horse still jumps up, through the leaves. */
	private void leafyLedge(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Walking at a 2-block ledge with leaves on top");
		lane(ctx, input, world, 890.5, -60, "leafy_ledge_test",
			"fill 885 -60 -40 895 -59 -20 minecraft:stone",
			"fill 885 -58 -40 895 -57 -20 minecraft:oak_leaves[persistent=true]");
		final int climbs = ride(ctx, s -> s.ledgeClimbs);
		sideCamera(world, 896.5, -60, -19.5, 90.0F);
		input.holdKey(o -> o.keyUp);
		hitboxes(ctx, true);
		boolean shot = false;
		for (int i = 0; i < 300 && horseZ(ctx) > -25.0; i++) {
			ctx.waitTick();
			if (!shot && sample(ctx).y > -59.0) {
				cameraShot(ctx, "18_leafy_ledge");
				shot = true;
			}
		}
		hitboxes(ctx, false);
		check("jumps up through the leaves (blocks gained)", sample(ctx).y - -60.0, 1.95, 2.05);
		check("one ledge jump", ride(ctx, r -> r.ledgeClimbs) - climbs, 1, 1);
		stop(ctx, input);
	}

	/** Entity hitboxes (F3+B), which also turn on the steering overlay. */
	private static void hitboxes(final ClientGameTestContext ctx, final boolean on) {
		ctx.runOnClient(mc -> mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, on ? DebugScreenEntryStatus.ALWAYS_ON : DebugScreenEntryStatus.NEVER));
	}

	/**
	 * Puts a fixed camera (an invisible armour stand) beside a lane, at (x, y, z) looking along yaw and a little down,
	 * for {@link #cameraShot}.
	 */
	private static void sideCamera(final TestSingleplayerContext world, final double x, final double y, final double z, final float yaw) {
		final TestServerContext server = world.getServer();
		server.runCommand("kill @e[type=minecraft:armor_stand,tag=test_camera]");
		server.runCommand(String.format(Locale.ROOT,
			"summon minecraft:armor_stand %.2f %.2f %.2f {Invisible:1b,NoGravity:1b,Invulnerable:1b,Tags:[\"test_camera\"],Rotation:[%.1ff,6f]}", x, y, z, yaw));
	}

	/** A shot from the lane's fixed side camera, without the HUD; no tick passes. */
	private void cameraShot(final ClientGameTestContext ctx, final String name) {
		final boolean found = ctx.computeOnClient(mc -> {
			final var stands = mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(16.0), ArmorStand::isInvisible);
			if (stands.isEmpty()) {
				return false;
			}
			mc.setCameraEntity(stands.get(0));
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			mc.gui.hud.toggle();
			return true;
		});
		screenshot(ctx, name);
		if (found) {
			ctx.runOnClient(mc -> {
				mc.gui.hud.toggle();
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.setCameraEntity(mc.player);
			});
		}
	}

	/** Builds a lane with the given commands, then seats the player on a fresh horse at (x, y, 0.5) facing north. */
	private void lane(
		final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final double x, final double y, final String tag,
		final String... build
	) {
		final TestServerContext server = world.getServer();
		input.releaseKey(o -> o.keyUp);
		server.runCommand("ride @p dismount");
		server.runCommand(String.format(Locale.ROOT, "tp @p %.2f -60 12.5 180 10", x));
		ctx.waitTicks(20);
		world.getConnection().waitForChunksRender();
		for (final String command : build) {
			server.runCommand(command);
		}
		// Let the client have the new blocks before riding at them.
		ctx.waitTicks(2);
		world.getConnection().waitForChunksRender();
		server.runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f 0.5 180 10", x, y));
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:horse %.2f %.2f 0.5 {Tame:1b,Variant:1,Rotation:[180f,0f],"
			+ "equipment:{saddle:{id:\"minecraft:saddle\",count:1}},"
			+ "attributes:[{id:\"minecraft:movement_speed\",base:%sd}],Tags:[\"%s\"]}", x, y, SPEED_ATTRIBUTE, tag));
		for (int attempt = 0; attempt < 10 && !ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse); attempt++) {
			ctx.waitTicks(5);
			server.runCommand("ride @p mount @e[type=minecraft:horse,tag=" + tag + ",limit=1]");
		}
		ctx.waitFor(mc -> mc.player.getVehicle() instanceof AbstractHorse);
		input.lookAt(180.0F, 10.0F);
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
	}

	private static void gallopNorth(final ClientGameTestContext ctx, final TestInput input) {
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(2);
		for (int i = 0; i < 3; i++) {
			input.pressKey(o -> o.keySprint);
			ctx.waitTicks(4);
		}
	}

	private static void stop(final ClientGameTestContext ctx, final TestInput input) {
		input.releaseKey(o -> o.keyUp);
		ticksUntilStopped(ctx, 80);
	}

	/** Half the ridden horse's collision box width. */
	private static double half(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.getVehicle().getBbWidth() * 0.5);
	}

	private static double horseX(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.getVehicle().getX());
	}

	private static double horseZ(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.getVehicle().getZ());
	}

	private static <T> T ride(final ClientGameTestContext ctx, final java.util.function.Function<RideState, T> read) {
		return ctx.computeOnClient(mc -> read.apply(((RideStateHolder) mc.player.getVehicle()).horsingaround$ride()));
	}

	// ---- helpers ----

	private static boolean enableFreshAnimations(final ClientGameTestContext ctx) {
		final CompletableFuture<Void> reload = ctx.computeOnClient(mc -> {
			final PackRepository packs = mc.getResourcePackRepository();
			packs.reload();
			for (final String id : packs.getAvailableIds()) {
				if (id.contains("FreshAnimations")) {
					packs.addPack(id);
					mc.options.updateResourcePacks(packs);
					return mc.reloadResourcePacks();
				}
			}
			return null;
		});
		if (reload == null) {
			return false;
		}
		ctx.waitFor(mc -> reload.isDone(), 20 * 120);
		ctx.waitTicks(40);
		return true;
	}

	private static Sample sample(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			final AbstractHorse horse = (AbstractHorse) mc.player.getVehicle();
			if (horse == null) {
				return new Sample(0, mc.player.getY(), 0, mc.player.getYRot(), 0, 1, false, 0, true, 0, mc.player.getY(), 0);
			}
			final RideState s = ((RideStateHolder) horse).horsingaround$ride();
			return new Sample(
				Math.hypot(horse.getX() - horse.xo, horse.getZ() - horse.zo),
				horse.getY(),
				horse.getYRot(),
				mc.player.getYRot(),
				s.gait,
				s.stamina,
				s.exhausted,
				s.lean(1.0F),
				horse.onGround(),
				horse.walkAnimation.speed(),
				horse.getY() + s.heightOffset(1.0F),
				s.pitch(1.0F)
			);
		});
	}

	private static float riderInertia(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().inertia(1.0F));
	}

	private static double signedSpeed(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().speed);
	}

	private static double averageSpeed(final ClientGameTestContext ctx, final int ticks) {
		double total = 0.0;
		for (int i = 0; i < ticks; i++) {
			ctx.waitTick();
			total += sample(ctx).speed;
		}
		return total / ticks;
	}

	private static int ticksUntilStopped(final ClientGameTestContext ctx, final int limit) {
		int ticks = 0;
		while (sample(ctx).speed > 0.01 && ticks < limit) {
			ctx.waitTick();
			ticks++;
		}
		return ticks;
	}

	private void screenshot(final ClientGameTestContext ctx, final String name) {
		log("  screenshot %s -> %s", name, ctx.takeScreenshot("horsingaround_" + name));
	}

	/** {@link #frontScreenshot} with the view pitched for one frame (negative looks up, so the camera looks down from above). */
	private void frontScreenshot(final ClientGameTestContext ctx, final String name, final float viewPitch) {
		final float pitch = ctx.computeOnClient(mc -> mc.player.getXRot());
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		ctx.runOnClient(mc -> setView(mc.player, mc.player.getYRot(), viewPitch));
		screenshot(ctx, name);
		ctx.runOnClient(mc -> {
			setView(mc.player, mc.player.getYRot(), pitch);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
	}

	/** Mirrored third person for a look at the horse's face and legs; the ride camera only replaces the back view. */
	private void frontScreenshot(final ClientGameTestContext ctx, final String name) {
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		ctx.waitTick();
		screenshot(ctx, name);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
	}

	/**
	 * Swings the view 90 degrees for one rendered frame to see the horse broadside. No tick passes, so the horse never
	 * reacts to the camera move.
	 */
	private void sideScreenshot(final ClientGameTestContext ctx, final String name) {
		sideScreenshot(ctx, name, 5.0F);
	}

	private void sideScreenshot(final ClientGameTestContext ctx, final String name, final float viewPitch) {
		final float yaw = ctx.computeOnClient(mc -> mc.player.getYRot());
		final float pitch = ctx.computeOnClient(mc -> mc.player.getXRot());
		ctx.runOnClient(mc -> setView(mc.player, yaw + 90.0F, viewPitch));
		screenshot(ctx, name);
		ctx.runOnClient(mc -> setView(mc.player, yaw, pitch));
	}

	private static void setView(final Entity entity, final float yaw, final float pitch) {
		entity.setYRot(yaw);
		entity.yRotO = yaw;
		entity.setXRot(pitch);
		entity.xRotO = pitch;
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
		System.out.println("[ride-test] " + line);
	}

	private void writeReport() {
		final Path file = FabricLoader.getInstance().getGameDir().resolve("horsingaround-ride-report.txt");
		try {
			Files.write(file, this.report);
		} catch (final IOException e) {
			throw new RuntimeException("Could not write " + file, e);
		}
	}
}
