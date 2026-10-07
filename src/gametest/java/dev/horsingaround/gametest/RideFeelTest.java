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
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
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
			stairs(ctx, input, world);
			forest(ctx, input, world);
			downhill(ctx, input, world);
			trample(ctx, input, world);
			water(ctx, input, world);
			pickingItsWay(ctx, input, world);
			horseSettings(ctx);
		} finally {
			writeReport();
		}
		if (this.failures > 0) {
			throw new AssertionError(this.failures + " ride checks failed; see horsingaround-ride-report.txt");
		}
	}

	private void mounting(final ClientGameTestContext ctx) {
		section("Mounting");
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
		for (int i = 1; i <= 40; i++) {
			ctx.waitTick();
			elapsed++;
			final Sample s = sample(ctx);
			peak = Math.max(peak, s.y);
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
		check("standing jump height (blocks)", peak - startY, 0.2, 1.0);
	}

	private void dismount(final ClientGameTestContext ctx, final TestInput input) {
		section("Dismount");
		input.holdKeyFor(o -> o.keyShift, 3);
		ctx.waitTicks(5);
		check("dismounted", ctx.computeOnClient(mc -> mc.player.getVehicle() == null));
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
		check("nose pitches up while climbing (deg)", maxPitch, 8.0, 15.5);
		ticksUntilStopped(ctx, 60);
		ctx.waitTicks(10);
		check("level again on the plateau (pitch deg)", Math.abs(sample(ctx).pitch), 0.0, 2.0);
	}

	private void forest(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Riding through leaves");
		final TestServerContext server = world.getServer();
		server.runCommand("fill 97 -60 -80 104 -57 -31 minecraft:stone");
		server.runCommand("fill 97 -56 -46 104 -54 -38 minecraft:oak_leaves[persistent=true]");
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
		for (int i = 0; i < 300 && sample(ctx).y > -57.5; i++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			final boolean inLeaves = ctx.computeOnClient(mc -> Foliage.leavesIn(mc.player.getVehicle()) != null);
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
			// Carry on well clear of the leaves so later camera checks aren't taken against them.
			if (ctx.computeOnClient(mc -> mc.player.getVehicle().getZ()) < -64.0) {
				break;
			}
		}
		input.releaseKey(o -> o.keyUp);
		final double z = ctx.computeOnClient(mc -> mc.player.getVehicle().getZ());
		check("rode through a 9-block-deep stand of leaves (z beyond -47)", z, -80.0, -47.0);
		check("leaves slow the horse (inside / outside speed)", insideTicks > 0 && outsideTicks > 0 ? (inside / insideTicks) / (outside / outsideTicks) : 0.0, 0.6, 0.9);
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
		longWall(ctx, input, world);
		alongsideWall(ctx, input, world);
		cliff(ctx, input, world);
		alongTheEdge(ctx, input, world);
		safeDrop(ctx, input, world);
		lava(ctx, input, world);
		gap(ctx, input, world, true);
		gap(ctx, input, world, false);
		ledgeAtAWalk(ctx, input, world);
		ledgeAtAGallop(ctx, input, world);
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
		check("swerves round it (blocks off the line)", maxSide, 1.0, 4.0);
		check("keeps its pace going round (slowest / gallop)", slowest / GALLOP_SPEED, 0.7, 1.1);
		check("heads where the rider looks again after (deg off)", Math.abs(Mth.wrapDegrees(sample(ctx).horseYaw - 180.0F)), 0.0, 3.0);
		check("detour eased out after (deg)", ride(ctx, r -> Math.abs(r.avoidOffset)), 0.0, 0.5);
		stop(ctx, input);
	}

	private void longWall(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping at a long wall");
		lane(ctx, input, world, 320.5, -60, "wall_test", "fill 310 -60 -40 330 -58 -40 minecraft:stone");
		final int climbs = ride(ctx, s -> s.ledgeClimbs);
		gallopNorth(ctx, input);
		double contactSpeed = -1.0;
		double maxSide = 0.0;
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 300 && contactSpeed < 0.0; i++) {
			ctx.waitTick();
			maxSide = Math.max(maxSide, Math.abs(horseX(ctx) - 320.5));
			if (horseZ(ctx) < -25.0) {
				trace.append(String.format(Locale.ROOT, "z%.1f x%.2f v%.2f o%.0f yaw%.0f | ", horseZ(ctx), horseX(ctx), sample(ctx).speed,
					ride(ctx, r -> r.avoidOffset), sample(ctx).horseYaw));
			}
			if (ctx.computeOnClient(mc -> mc.player.getVehicle().horizontalCollision)) {
				contactSpeed = Math.max(sample(ctx).speed, ctx.computeOnClient(mc -> mc.player.getVehicle().getDeltaMovement().horizontalDistance()));
			}
		}
		log("  path: %s", trace);
		check("slows to a walk before the wall (speed at contact / walk)", contactSpeed / WALK_SPEED, 0.0, 1.15);
		check("walks right up to it (gap to the wall, blocks)", horseZ(ctx) - 0.7 - -39.0, -0.01, 0.3);
		check("doesn't veer off along a wall it can't get round (blocks off the line)", maxSide, 0.0, 1.0);
		ctx.waitTicks(40);
		check("doesn't try to climb a 3-block wall", ride(ctx, s -> s.ledgeClimbs) == climbs && sample(ctx).y < -59.9);
		stop(ctx, input);
	}

	private void alongsideWall(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping alongside a wall");
		// The wall's face is at x=499; the horse's flank (half-width 0.7) runs 0.05 from it.
		lane(ctx, input, world, 499.75, -60, "beside_test", "fill 498 -60 6 498 -58 -80 minecraft:stone");
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
		check("stays beside it (blocks off the line)", Math.abs(horseX(ctx) - 499.75), 0.0, 0.3);
		stop(ctx, input);
	}

	private void cliff(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping at a 10-block cliff");
		lane(ctx, input, world, 340.5, -50, "cliff_test", "fill 335 -60 -40 345 -51 6 minecraft:stone");
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
		check("stops at the edge (still on top)", sample(ctx).y > -50.05 && sample(ctx).onGround);
		check("stops close to the edge, not early (front to edge, blocks)", horseZ(ctx) - 0.7 - -40.0, 0.0, 2.5);
		check("snorts and tosses its head", ride(ctx, s -> s.refusals) > refusals);
		frontScreenshot(ctx, "13_cliff_refusal");
		ctx.waitTicks(40);
		check("holding W at the edge, it stays put", sample(ctx).y > -50.05 && horseZ(ctx) - 0.7 > -40.0);
		input.pressKey(o -> o.keyJump);
		ctx.waitTicks(30);
		check("won't jump off the cliff", sample(ctx).y > -50.05 && sample(ctx).onGround);
		stop(ctx, input);
	}

	private void alongTheEdge(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping along a cliff edge");
		// A 5-wide ridge; the horse runs with its left flank hanging 0.3 over the west edge (x=378), then the ridge ends.
		lane(ctx, input, world, 378.4, -50, "edge_test", "fill 378 -60 -60 382 -51 6 minecraft:stone");
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
			lowest = Math.min(lowest, s.y - -50.0);
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
		check("stops where the ridge ends", sample(ctx).y > -50.05 && horseZ(ctx) - 0.7 > -60.0);
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
		section("Galloping at a lava pool");
		lane(ctx, input, world, 440.5, -60, "lava_test", "fill 435 -61 -48 445 -61 -40 minecraft:lava");
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
		check("stops close to it, not early (front to lava, blocks)", horseZ(ctx) - 0.7 - -39.0, 0.0, 2.5);
		stop(ctx, input);
	}

	private void gap(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final boolean jump) {
		section(jump ? "Jumping a 3-block gap at a gallop" : "Galloping at a 3-block gap without jumping");
		final int x = jump ? 460 : 480;
		lane(ctx, input, world, x + 0.5, -50, jump ? "gap_jump_test" : "gap_test",
			"fill " + (x - 5) + " -60 -60 " + (x + 5) + " -51 6 minecraft:stone",
			"fill " + (x - 5) + " -60 -33 " + (x + 5) + " -51 -31 minecraft:air");
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
			check("cleared the gap and landed on top", horseZ(ctx) < -45.0 && sample(ctx).y > -50.05);
			check("didn't brake for a gap it can jump", ride(ctx, s -> s.refusals) == refusals);
		} else {
			check("plants its feet at the lip (still on top)", sample(ctx).y > -50.05 && horseZ(ctx) > -30.75);
		}
		stop(ctx, input);
	}

	private void ledgeAtAWalk(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Walking at a 2-block ledge");
		// The ledge's face is at z=-19.
		lane(ctx, input, world, 400.5, -60, "ledge_test", "fill 395 -60 -40 405 -59 -20 minecraft:stone");
		final int climbs = ride(ctx, s -> s.ledgeClimbs);
		input.holdKey(o -> o.keyUp);
		int gather = 0;
		double takeoffSpeed = 0.0;
		double takeoffZ = Double.NaN;
		int airborne = 0;
		double risingTravel = -1.0;
		double peak = -60.0;
		boolean shot = false;
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 300 && horseZ(ctx) > -24.0; i++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			final double z = horseZ(ctx);
			if (Double.isNaN(takeoffZ)) {
				if (ride(ctx, r -> r.ledgeClimbs) > climbs) {
					takeoffZ = z;
				} else {
					if (ride(ctx, r -> r.ledgeTicks) > 0) {
						gather++;
					}
					takeoffSpeed = s.speed;
				}
				continue;
			}
			trace.append(String.format(Locale.ROOT, "y%.2f z%.2f | ", s.y, z));
			peak = Math.max(peak, s.y);
			if (!s.onGround) {
				airborne++;
			}
			if (risingTravel < 0.0 && s.y >= -58.05) {
				risingTravel = takeoffZ - z;
			}
			if (!shot && s.y > -59.0) {
				sideScreenshot(ctx, "14_ledge_jump_side");
				shot = true;
			}
		}
		log("  after takeoff: %s", trace);
		check("jumps up a 2-block ledge (blocks gained)", sample(ctx).y - -60.0, 1.95, 2.05);
		check("one ledge jump", ride(ctx, r -> r.ledgeClimbs) - climbs, 1, 1);
		check("gathers itself first (ticks)", gather, 3, 8);
		check("from a standstill (speed just before takeoff, blocks/tick)", takeoffSpeed, 0.0, 0.03);
		check("takes off close to the face (front to face, blocks)", takeoffZ - 0.7 - -19.0, 0.0, 1.0);
		check("an arc, not a climb: forward travel on the way up (blocks)", risingTravel, 0.3, 2.0);
		check("in the air like a jump (ticks)", airborne, 5, 16);
		check("clears the lip without launching (peak above the top, blocks)", peak - -58.0, 0.05, 0.6);
		stop(ctx, input);
	}

	private void ledgeAtAGallop(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Galloping at a 2-block ledge");
		lane(ctx, input, world, 420.5, -60, "ledge_gallop_test", "fill 405 -60 -60 435 -59 -40 minecraft:stone");
		gallopNorth(ctx, input);
		double climbSpeed = -1.0;
		for (int i = 0; i < 300 && horseZ(ctx) > -45.0; i++) {
			ctx.waitTick();
			if (climbSpeed < 0.0 && ride(ctx, s -> s.ledgeTicks) > 0) {
				climbSpeed = Math.max(sample(ctx).speed, previousSpeed);
			}
			previousSpeed = sample(ctx).speed;
		}
		check("slows before jumping up (speed when it starts / trot)", climbSpeed / TROT_SPEED, 0.0, 1.0);
		check("then jumps up", sample(ctx).y - -60.0, 1.95, 2.05);
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

	/** Builds a lane with the given commands, then seats the player on a fresh horse at (x, y, 0.5) facing north. */
	private void lane(
		final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final double x, final int y, final String tag,
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
		server.runCommand(String.format(Locale.ROOT, "tp @p %.2f %d 0.5 180 10", x, y));
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:horse %.2f %d 0.5 {Tame:1b,Variant:1,Rotation:[180f,0f],"
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
		final float yaw = ctx.computeOnClient(mc -> mc.player.getYRot());
		final float pitch = ctx.computeOnClient(mc -> mc.player.getXRot());
		ctx.runOnClient(mc -> setView(mc.player, yaw + 90.0F, 5.0F));
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
