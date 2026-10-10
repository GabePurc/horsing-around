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
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
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
	private CameraType cameraBeforeMounting = CameraType.FIRST_PERSON;
	private TestServerContext server;

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
			this.server = server;
			server.runCommand("time set noon");
			server.runCommand("tp @a 0.5 -60 0.5 180 10");
			server.runCommand("summon minecraft:horse 0.5 -60 0.5 {Tame:1b,Variant:0,Rotation:[180f,0f],"
				+ "equipment:{saddle:{id:\"minecraft:saddle\",count:1}},"
				+ "attributes:[{id:\"minecraft:movement_speed\",base:" + SPEED_ATTRIBUTE + "d},{id:\"minecraft:jump_strength\",base:" + JUMP_ATTRIBUTE + "d}],"
				+ "Tags:[\"ride_test\"]}");
			// (Another camera mod may already have the view in third person: dismounting should restore whatever it was.)
			this.cameraBeforeMounting = ctx.computeOnClient(mc -> mc.options.getCameraType());
			server.runCommand("ride @p mount @e[type=minecraft:horse,tag=ride_test,limit=1]");
			ctx.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof AbstractHorse);
			world.getConnection().waitForChunksRender();
			final TestInput input = ctx.getInput();
			input.lookAt(180.0F, 10.0F);
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));

			// -Psections=<names> runs only some sections (core, cuts, stairs, picking, steps); all of them by default.
			final String sections = System.getProperty("horsingaround.sections", "");
			if (sections.isEmpty() || sections.contains("core") || sections.contains("icons")) {
				blockItemIcons(ctx);
			}
			if (sections.isEmpty() || sections.contains("core")) {
				mounting(ctx);
				freeLook(ctx, input);
				walk(ctx, input);
				gallop(ctx, input);
				staminaBarAndCameraAddOn(ctx, input);
				turn(ctx, input);
				runningJump(ctx, input);
				exhaustion(ctx, input);
				coastStop(ctx, input);
				brakeAndReverse(ctx, input);
				freeAim(ctx, input);
				standingJump(ctx);
				dismount(ctx, input);
			}
			if (sections.isEmpty() || sections.contains("core") || sections.contains("hands")) {
				riderHands(ctx, input, world);
			}
			if (sections.isEmpty() || sections.contains("ledges")) {
				messyLedges(ctx, input, world);
			}
			if (sections.contains("slopes") && !sections.contains("steps")) {
				// Only on request (it is part of steps): up and down slopes and stairs.
				slopes(ctx, input, world);
			}
			if (sections.contains("hurdles") && !sections.contains("picking")) {
				// Only on request (it is part of picking): jumping fences and walls.
				hurdles(ctx, input, world);
			}
			if (sections.contains("face") && !sections.contains("picking")) {
				// Only on request (it is part of picking): pressing jump right up against a wall or a ledge.
				jumpAtTheFace(ctx, input, world);
			}
			if (sections.isEmpty() || sections.contains("ledges") || sections.contains("spam")) {
				spamJumpAtLedge(ctx, input, world);
			}
			if (sections.contains("drawn")) {
				// Only on request: the legs as drawn, measured and shot up close, flat ground to stairs.
				legsAsDrawn(ctx, input, world);
			}
			if (sections.isEmpty() || sections.contains("climbs")) {
				// 2-block climbs swept by angle, speed, start and what is round them.
				climbs(ctx, input, world);
			}
			if (sections.contains("slowstep")) {
				// Only on request: walking slowly over a single step up and down, every leg watched tick by tick for snaps.
				slowSteps(ctx, input, world);
			}
			if (sections.contains("legs")) {
				// Only on request: close side shots of the legs in a jump, for judging the pose.
				jumpLegShots(ctx, input, world);
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
			if (sections.isEmpty() || sections.contains("stairs") || sections.contains("exits")) {
				waterExits(ctx, input, world);
			}
			if (sections.isEmpty() || sections.contains("cuts") || sections.contains("strafe")) {
				strafeStops(ctx, input, world);
			}
			if (sections.isEmpty() || sections.contains("picking")) {
				pickingItsWay(ctx, input, world);
			} else if (sections.contains("ways")) {
				// Only on request (it is part of picking): going round things and threading trunks (-Pscenario=tree, wall,
				// long, alongside, pillar, row, forest, ledge for one), with every plan logged.
				final String only = System.getProperty("horsingaround.scenario", "");
				ctx.runOnClient(mc -> dev.horsingaround.ride.Awareness.logPlans = true);
				if ("tree".contains(only)) {
					treeInThePath(ctx, input, world);
				}
				if ("wall".contains(only)) {
					wallWithAWayRound(ctx, input, world);
				}
				if ("long".contains(only)) {
					longWall(ctx, input, world);
				}
				if ("alongside".contains(only)) {
					alongsideWall(ctx, input, world);
				}
				if ("pillar".contains(only)) {
					pillar(ctx, input, world);
				}
				if ("row".contains(only)) {
					treesInARow(ctx, input, world);
				}
				if ("forest".contains(only)) {
					forests(ctx, input, world);
				}
				if ("ledge".contains(only)) {
					ledgeStraightAhead(ctx, input, world);
				}
				ctx.runOnClient(mc -> dev.horsingaround.ride.Awareness.logPlans = false);
				section("(end of the ways)");
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
		TestSummary.failed(this.failures, "horsingaround-ride-report.txt");
	}

	/**
	 * Entity Model Features animates more than entities: with Fresh Animations it animates shulker boxes, also drawn as
	 * item icons, and then calls every animation hook with no entity at all (a player's game crashed in the hotbar). Shulker
	 * boxes in the hotbar and in hand for a while, riding, with nothing logged against the hook.
	 */
	private void blockItemIcons(final ClientGameTestContext ctx) {
		section("Shulker boxes drawn as items (Entity Model Features animating with no entity)");
		try (LogWatch watch = LogWatch.begin()) {
			for (int slot = 0; slot < 9; slot++) {
				this.server.runCommand("item replace entity @p hotbar." + slot + " with minecraft:" + (slot % 2 == 0 ? "shulker_box" : "red_shulker_box"));
			}
			ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
			screenshot(ctx, "00b_shulker_boxes");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(10);
			final List<String> errors = watch.matching("horsingaround", "Exception", "animation hook", "EmfSaddleTracker");
			check("drawn for 2 s with no error from the animation hook (" + errors + ")", errors.isEmpty());
		} finally {
			this.server.runCommand("clear @p");
			ctx.waitTicks(2);
		}
	}

	private void mounting(final ClientGameTestContext ctx) {
		section("Mounting");
		check("ridden, the horse's box narrows to its body (width, blocks)", ctx.computeOnClient(mc -> (double) mc.player.getVehicle().getBbWidth()), 0.85, 0.95);
		check("camera switched to third person", ctx.computeOnClient(mc -> mc.options.getCameraType()) == CameraType.THIRD_PERSON_BACK);
		// The pivot sits CAMERA_HEIGHT above the rider's eyes and the camera backs off along the view (10 deg down).
		if (ctx.computeOnClient(mc -> RideCamera.isActive(mc.player, mc))) {
			final double above = cameraAboveEyes(ctx);
			check("riding camera sits low behind the rider (camera above the eyes, standing, blocks)", above, 0.75, 1.05);
			ctx.runOnClient(mc -> {
				HorseConfig.get().cameraHeight = RideTuning.CAMERA_HEIGHT_DEFAULT + 0.5F;
				HorseConfig.apply();
			});
			ctx.waitTicks(2);
			check("the camera height setting raises it (blocks)", cameraAboveEyes(ctx) - above, 0.45, 0.55);
			ctx.runOnClient(mc -> HorseConfig.reset());
			ctx.waitTicks(2);
		} else {
			log("  camera height: skipped (another camera mod places the view)");
		}
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

	/**
	 * The stamina bar holds the jump bar's slot in survival too, where the experience bar wants it (and Better Mount HUD,
	 * in the compatibility runs, hides the jump bar unless jump is held). Then a camera add-on (Over the Shoulder) taking
	 * the third-person view: this mod stops easing the pitch and placing the camera, and its distance setting doesn't
	 * reach the add-on.
	 */
	private void staminaBarAndCameraAddOn(final ClientGameTestContext ctx, final TestInput input) {
		section("Stamina bar in survival, and a camera add-on");
		this.server.runCommand("gamemode survival @a");
		ctx.waitTicks(3);
		final String bar = contextualBar(ctx);
		check("stamina not full after galloping", sample(ctx).stamina < 1.0F);
		check("survival: the stamina bar has the slot (" + bar + ")", bar.equals("JumpableVehicleBar"));
		screenshot(ctx, "03f_stamina_bar_survival");
		this.server.runCommand("gamemode creative @a");

		// Down to a canter (no stamina drain), mouse left alone.
		input.pressKey(o -> o.keyDown);
		input.lookAt(180.0F, 35.0F);
		ctx.waitTicks(75);
		check("idle mouse at a canter: the view eases back toward a riding pitch (deg)", ctx.computeOnClient(mc -> (double) mc.player.getXRot()), 9.0, 30.0);
		ctx.runOnClient(mc -> dev.horsingaround.client.api.RideCameraApi.setThirdPersonClaimed(true));
		input.lookAt(180.0F, 35.0F);
		ctx.waitTicks(75);
		check("an add-on placing the view: the pitch is left to the player (deg)", ctx.computeOnClient(mc -> (double) mc.player.getXRot()), 34.9, 35.1);
		check("an add-on placing the view: this mod doesn't place the camera", ctx.computeOnClient(mc -> !RideCamera.isActive(mc.player, mc)));
		ctx.runOnClient(mc -> {
			HorseConfig.get().cameraDistance = 1.4F;
			HorseConfig.apply();
		});
		check("the add-on gets the designed riding distance, not this mod's distance setting (share)",
			ctx.computeOnClient(mc -> (double) (dev.horsingaround.client.api.RideCameraApi.distance(1.0F) / RideCamera.designedDistance(1.0F))), 0.999, 1.001);
		check("this mod's own camera takes the distance setting (share)", ctx.computeOnClient(mc -> (double) (RideCamera.distance(1.0F) / RideCamera.designedDistance(1.0F))),
			1.399, 1.401);
		ctx.runOnClient(mc -> {
			HorseConfig.reset();
			dev.horsingaround.client.api.RideCameraApi.setThirdPersonClaimed(false);
		});
		input.lookAt(180.0F, 10.0F);
		input.pressKey(o -> o.keySprint);
		ctx.waitTicks(30);
	}

	/** Simple name of the bar in the experience bar's slot (jump bar, experience, locator). */
	private static String contextualBar(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			try {
				final java.lang.reflect.Field field = net.minecraft.client.gui.Hud.class.getDeclaredField("contextualInfoBar");
				field.setAccessible(true);
				return ((com.mojang.datafixers.util.Pair<?, ?>) field.get(mc.gui.hud)).getSecond().getClass().getSimpleName();
			} catch (final ReflectiveOperationException e) {
				return e.toString();
			}
		});
	}

	/** How far the riding camera is above the rider's (smoothed) eyes this frame. */
	private static double cameraAboveEyes(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().position().y - RideCamera.eyeY(1.0F));
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
		// (Counted in the horse's own ticks: a screenshot can let the game run a tick or two between samples.)
		final int startTick = horseTick(ctx);
		input.pressKey(o -> o.keyJump);
		double peak = startY;
		double slowest = Double.MAX_VALUE;
		int airborne = 0;
		float noseUp = 0.0F;
		float noseDown = 0.0F;
		float airLegs = 0.0F;
		float slowestStride = Float.MAX_VALUE;
		float previousFore = Float.NaN;
		float previousHind = Float.NaN;
		float foreStep = 0.0F;
		float hindStep = 0.0F;
		int previousTick = horseTick(ctx);
		boolean tucked = false;
		boolean reaching = false;
		boolean risingShot = false;
		boolean fallingShot = false;
		float tailDown = 0.0F;
		float tailUp = 0.0F;
		float headGap = Float.MAX_VALUE;
		for (int i = 1; i <= 40; i++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			peak = Math.max(peak, s.y);
			headGap = Math.min(headGap, ride(ctx, r -> r.headGap));
			final float legs = ride(ctx, r -> r.airLegs(1.0F));
			final float rise = ride(ctx, r -> r.airRise(1.0F));
			airLegs = Math.max(airLegs, legs);
			final float tail = ride(ctx, r -> r.tailLift(1.0F));
			tailDown = Math.min(tailDown, tail);
			tailUp = Math.max(tailUp, tail);
			// How far the legs' jump shape moves a game tick while they are in it (screenshots can skip ticks).
			final int tick = horseTick(ctx);
			final float fore = dev.horsingaround.client.render.AirLegs.fore(rise);
			final float hind = dev.horsingaround.client.render.AirLegs.hind(rise);
			if (legs > 0.9F && !Float.isNaN(previousFore)) {
				foreStep = Math.max(foreStep, Math.abs(fore - previousFore) / Math.max(tick - previousTick, 1));
				hindStep = Math.max(hindStep, Math.abs(hind - previousHind) / Math.max(tick - previousTick, 1));
			}
			previousFore = legs > 0.9F ? fore : Float.NaN;
			previousHind = hind;
			previousTick = tick;
			if (legs > 0.9F) {
				slowestStride = Math.min(slowestStride, s.limbSpeed);
				tucked |= rise > 0.4F;
				reaching |= rise < -0.4F;
				if (!risingShot && rise > 0.4F) {
					sideScreenshot(ctx, "05c_jump_legs_rising");
					risingShot = true;
				} else if (!fallingShot && rise < -0.4F) {
					sideScreenshot(ctx, "05d_jump_legs_landing");
					fallingShot = true;
				}
			}
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
		check("rider's head clear of the horse's in the jump (closest, centre to centre, blocks)", headGap, 0.45, 10.0);
		check("speed kept in the air (fraction)", slowest / before, 0.85, 1.3);
		check("in the air the legs take the jump's shape (0..1)", airLegs, 0.95, 1.0);
		check("the gallop stride stops in the air (leg-animation speed)", slowestStride, 0.0, 0.3);
		check("front legs fold up rising, then reach for the ground coming down", tucked && reaching);
		check("smooth and floaty in the air: front legs move at most (radians a tick)", foreStep, 0.0, 0.1);
		check("the tail trails down as the horse launches (radians)", tailDown, -0.6, -0.1);
		check("...and floats up coming down (radians)", tailUp, 0.3, 0.9);
		check("...and the hind legs (radians a tick)", hindStep, 0.0, 0.1);
		// Landing: the legs take the fall (the body sinks a little and comes back up), every hoof on the ground, not in it.
		ctx.runOnClient(mc -> LegProbe.arm(true));
		double landing = 0.0;
		double sunk = 0.0;
		double dip = 0.0;
		final StringBuilder touchdown = new StringBuilder();
		for (int i = 0; i < 12; i++) {
			ctx.waitTick();
			if (i < 6) {
				landing = Math.max(landing, sample(ctx).speed);
			}
			final double[] now = ctx.computeOnClient(mc -> new double[] {
				Math.min(Math.min(LegProbe.gap[0], LegProbe.gap[1]), Math.min(LegProbe.gap[2], LegProbe.gap[3])),
				((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().heightOffset(1.0F), LegProbe.frames
			});
			if (now[2] > 0) {
				sunk = Math.min(sunk, now[0]);
			}
			dip = Math.min(dip, now[1]);
			final String line = ctx.computeOnClient(mc -> LegProbe.line());
			touchdown.append(String.format(Locale.ROOT, "off%+.3f %s| ", now[1], line));
		}
		ctx.runOnClient(mc -> LegProbe.arm(false));
		log("  landing: %s", touchdown);
		check("no surge after landing (fastest tick / before)", landing / before, 0.9, 1.12);
		check("landing, the legs take the fall: the body sinks a little (blocks)", -dip, 0.03, 0.3);
		// (A rocking hoof's edge dips about a pixel into the ground, as the animation draws it.)
		check("landing, no hoof sinks into the ground (most, blocks)", sunk, -0.08, 0.0);
		check("the stride picks up again after landing (leg-animation speed)", sample(ctx).limbSpeed, 0.85, 1.0);
		check("legs back in the stride (jump shape, 0..1)", ride(ctx, r -> r.airLegs(1.0F)), 0.0, 0.01);
		ctx.waitTicks(20);
		check("tail settles after landing (radians)", Math.abs((double) ride(ctx, r -> r.tailLift(1.0F))), 0.0, 0.1);
		final float gallopDrain = (horseTick(ctx) - startTick) * RideTuning.STAMINA_DRAIN_GALLOP;
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
		final CameraType camera = ctx.computeOnClient(mc -> mc.options.getCameraType());
		check("camera restored to how it was before mounting (" + this.cameraBeforeMounting + ", now " + camera + ")", camera == this.cameraBeforeMounting);
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
		check("nose pitches up with each step (deg)", maxPitch, 10.0, 40.0);
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
		ctx.runOnClient(mc -> RideTuning.HARD_CUT = false);
		final Turn plain = gallopAndLook(ctx, input, world, 1200, 90.0F, null);
		ctx.runOnClient(mc -> RideTuning.HARD_CUT = true);
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
		final float facing = sample(ctx).horseYaw;
		input.lookAt(180.0F + 150.0F, 10.0F);
		ctx.waitTicks(10);
		check("doesn't turn the horse (free look, deg; mounted facing " + Math.round(facing) + ")", Math.abs(Mth.wrapDegrees(sample(ctx).horseYaw - facing)), 0.0, 1.0);
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
				trace.append(String.format(Locale.ROOT, "%d: t%d z%.2f y%.2f vis%.3f fit%.3f tilt%.1f heave%d | ", i, tick, horseZ(ctx), s.y, s.visualY,
					ride(ctx, r -> r.drawnFit), s.pitch, bankTicks));
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

	/**
	 * Out of the water onto banks like generated rivers' and lakes': wading out of 1-deep water onto ground level with
	 * the water's block layer, a slab on it and a block above it, then swimming out of deep water onto the same. The
	 * body should rise only as far as the bank it ends up on and settle there with no snap: every tick the drawn body
	 * against the bank's top, and frame by frame any snap.
	 */
	private void waterExits(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		ctx.runOnClient(mc -> LegProbe.arm(true));
		// (At a real frame rate: a snap between ticks only shows frame by frame.)
		final int frameLimit = ctx.computeOnClient(mc -> mc.options.framerateLimit().get());
		ctx.runOnClient(mc -> mc.options.framerateLimit().set(120));
		try {
			waterExit(ctx, input, world, 3400, false, 0.0);
			waterExit(ctx, input, world, 3420, false, 0.5);
			waterExit(ctx, input, world, 3440, false, 1.0);
			waterExit(ctx, input, world, 3460, true, 0.0);
			waterExit(ctx, input, world, 3480, true, 0.5);
			waterExit(ctx, input, world, 3500, true, 1.0);
		} finally {
			ctx.runOnClient(mc -> {
				LegProbe.arm(false);
				mc.options.framerateLimit().set(frameLimit);
			});
		}
	}

	/**
	 * Walks north out of water ({@code deep}: three deep, swimming; else one deep, wading) onto a bank {@code rise} blocks
	 * above the top of the water's block layer.
	 */
	private void waterExit(
		final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final int x, final boolean deep, final double rise
	) {
		section((deep ? "Swimming" : "Wading") + " out onto a bank " + (rise == 0.0 ? "level with the water" : rise == 0.5 ? "a slab above the water"
			: "a block above the water"));
		// The ground's top layer is y=-61 (its top at -60); the water's top layer takes its place from z=-2 to the bank.
		final int bankZ = deep ? -25 : -13;
		final double top = -60.0 + rise;
		final List<String> build = new ArrayList<>();
		build.add(String.format(Locale.ROOT, "fill %d %d -2 %d -61 %d minecraft:water", x - 4, deep ? -63 : -61, x + 4, bankZ + 1));
		if (rise > 0.0) {
			build.add(String.format(Locale.ROOT, "fill %d -60 %d %d -60 %d minecraft:%s", x - 4, bankZ, x + 4, bankZ - 12, rise < 1.0 ? "stone_slab" : "stone"));
		}
		lane(ctx, input, world, x + 0.5, -60, "exit_" + x, build.toArray(String[]::new));
		sideCamera(world, x + 6.5, top + 0.8, bankZ + 0.5, 90.0F);
		ctx.runOnClient(mc -> LegProbe.reset());
		input.holdKey(o -> o.keyUp);
		hitboxes(ctx, true);
		double peak = Double.NEGATIVE_INFINITY;
		double peakBox = Double.NEGATIVE_INFINITY;
		double drop = 0.0;
		double jolt = 0.0;
		double sunk = 0.0;
		int climbStart = -1;
		int shots = 0;
		final List<double[]> history = new ArrayList<>();
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 400 && horseZ(ctx) > bankZ - 6.0; i++) {
			ctx.waitTick();
			final double[] now = ctx.computeOnClient(mc -> {
				final AbstractHorse horse = (AbstractHorse) mc.player.getVehicle();
				final RideState r = ((RideStateHolder) horse).horsingaround$ride();
				return new double[] {
					horse.tickCount, horse.getZ(), horse.getY(), horse.getY() + r.heightOffset(1.0F), r.pitch(1.0F), r.drawnFit, horse.onGround() ? 1 : 0,
					horse.isInWater() ? 1 : 0, r.bankTicks, r.swimming ? 1 : 0, Math.min(Math.min(LegProbe.gap[0], LegProbe.gap[1]), Math.min(LegProbe.gap[2], LegProbe.gap[3]))
				};
			});
			if (!history.isEmpty() && now[0] == history.get(history.size() - 1)[0]) {
				continue;
			}
			history.add(now);
			if (now[1] > bankZ + 4.0) {
				continue;
			}
			// From nearing the bank on: how far the body (drawn, and the physics box) gets above the bank's top, how far it
			// falls in a tick coming down onto it, and how sharply its rise changes.
			peak = Math.max(peak, now[3] - top);
			peakBox = Math.max(peakBox, now[2] - top);
			final int n = history.size();
			if (n >= 2 && history.get(n - 2)[0] + 1 == now[0]) {
				drop = Math.max(drop, history.get(n - 2)[3] - now[3]);
			}
			if (n >= 3 && history.get(n - 3)[0] + 2 == now[0]) {
				jolt = Math.max(jolt, Math.abs(now[3] - 2.0 * history.get(n - 2)[3] + history.get(n - 3)[3]));
			}
			if (now[1] < bankZ && now[6] > 0.0 && now[7] == 0.0 && !Double.isNaN(now[10])) {
				sunk = Math.min(sunk, now[10]);
			}
			if (climbStart < 0 && (now[8] > 0.0 || now[2] > (deep ? -61.0 : -60.95))) {
				climbStart = i;
			}
			if (climbStart >= 0 && shots < 6 && (i - climbStart) % 3 == 0) {
				cameraShot(ctx, String.format(Locale.ROOT, "19b_exit_%s_%.1f_%d", deep ? "swim" : "wade", rise, shots++));
			}
			trace.append(String.format(Locale.ROOT, "%n    t%d z%.2f y%.3f drawn%.3f tilt%.1f fit%.3f %s%s%s heave%d gap%+.3f", (int) now[0], now[1], now[2] - top,
				now[3] - top, now[4], now[5], now[6] > 0.0 ? "g" : "a", now[7] > 0.0 ? " wet" : "", now[9] > 0.0 ? " swim" : "", (int) now[8], now[10]));
		}
		hitboxes(ctx, false);
		check("climbs out onto the bank", Math.abs(sample(ctx).y - top) < 0.05 && horseZ(ctx) < bankZ - 1.0);
		check("the drawn body rises no higher than the bank (most above where it stands, blocks)", peak, -1.0, 0.08);
		check("settles onto the bank with no snap (most the drawn body falls in a tick, blocks)", drop, 0.0, 0.08);
		check("no jolt (most the drawn body's rise changes in a tick, blocks/tick)", jolt, 0.0, 0.12);
		check("no hoof in the ground on the bank (most, blocks)", sunk, -0.08, 0.0);
		// (Not checked yet: mid-heave the front soles still jump ~0.34 against the horse in a frame, mostly forward and back,
		// on Fresh Animations and the plain model alike; it was ~0.5 with each hoof popping up onto the bank at its lip.)
		log("  physics box highest above the bank %.3f; most a sole moved against the horse in a frame %.2f %s; frame by frame, the body or a sole"
			+ " jumping (%d frames of %d):%s", peakBox, ctx.computeOnClient(mc -> LegProbe.soleSnapMost), ctx.computeOnClient(mc -> LegProbe.snapLog.toString()),
			ctx.computeOnClient(mc -> LegProbe.spikeCount), ctx.computeOnClient(mc -> LegProbe.frames), ctx.computeOnClient(mc -> LegProbe.spikes.toString()));
		log("  every tick (heights against the bank's top):%s", trace);
		input.releaseKey(o -> o.keyUp);
		stop(ctx, input);
	}

	/**
	 * Riding across the view with D (or A) alone, then letting go: the horse must stop at its normal height, not sink
	 * toward the ground. Every tick until it has stood still a second and a half: the drawn body against where it stood
	 * before setting off, each hoof against the ground, and how hard it cut round; against riding on with W and stopping.
	 */
	private void strafeStops(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		ctx.runOnClient(mc -> LegProbe.arm(true));
		try {
			strafeStop(ctx, input, world, 3600, "D", false);
			strafeStop(ctx, input, world, 3700, "A", false);
			strafeStop(ctx, input, world, 3800, "D at a trot", true);
			strafeStop(ctx, input, world, 3900, "W", false);
		} finally {
			ctx.runOnClient(mc -> LegProbe.arm(false));
		}
	}

	private void strafeStop(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final int x, final String key, final boolean trot) {
		section("Riding with " + key + " alone, then letting go");
		lane(ctx, input, world, x + 0.5, -60, "strafe_" + x);
		ctx.waitTicks(10);
		final double standing = ctx.computeOnClient(mc -> (double) ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().heightOffset(1.0F));
		ctx.runOnClient(mc -> LegProbe.reset());
		final java.util.function.Function<net.minecraft.client.Options, net.minecraft.client.KeyMapping> keyOf =
			key.startsWith("D") ? o -> o.keyRight : key.startsWith("A") ? o -> o.keyLeft : o -> o.keyUp;
		input.holdKey(keyOf);
		ctx.waitTicks(5);
		if (trot) {
			input.pressKey(o -> o.keySprint);
		}
		ctx.waitTicks(35);
		input.releaseKey(keyOf);
		double lowest = Double.POSITIVE_INFINITY;
		double sunk = 0.0;
		double settled = Double.NaN;
		int still = 0;
		int last = -1;
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 160 && still < 30; i++) {
			ctx.waitTick();
			final double[] now = ctx.computeOnClient(mc -> {
				final AbstractHorse horse = (AbstractHorse) mc.player.getVehicle();
				final RideState r = ((RideStateHolder) horse).horsingaround$ride();
				return new double[] {
					horse.tickCount, r.heightOffset(1.0F), r.drawnFit, r.pitch(1.0F), r.cut, LegProbe.gap[0], LegProbe.gap[1], LegProbe.gap[2], LegProbe.gap[3],
					Math.hypot(horse.getX() - horse.xo, horse.getZ() - horse.zo), Mth.wrapDegrees(horse.getYRot() - 180.0F), horse.getY() + 60.0, LegProbe.bodyInside
				};
			});
			if (now[0] == last) {
				continue;
			}
			last = (int) now[0];
			final double body = now[1] + now[11] - standing;
			lowest = Math.min(lowest, body);
			for (int leg = 5; leg <= 8; leg++) {
				if (!Double.isNaN(now[leg])) {
					sunk = Math.min(sunk, now[leg]);
				}
			}
			still = now[9] < 1.0E-4 ? still + 1 : 0;
			if (still == 20) {
				settled = body;
			}
			trace.append(String.format(Locale.ROOT, "%n    t%d body%+.3f fit%+.3f tilt%.1f cut%.2f gaps %+.3f %+.3f %+.3f %+.3f belly%.3f speed%.3f yaw%.0f", (int) now[0], body, now[2],
				now[3], now[4], now[5], now[6], now[7], now[8], now[12], now[9], now[10]));
		}
		check("stopping, the body stays at its standing height (lowest against standing, blocks)", lowest, -0.03, 0.05);
		check("no hoof in the ground as it stops (most, blocks)", sunk, -0.08, 0.0);
		check("stood still, the body is back at its standing height (blocks)", settled, -0.01, 0.01);
		sideScreenshot(ctx, "19c_stopped_after_" + key.replace(' ', '_'));
		log("  every tick from letting go:%s", trace);
	}

	// ---- The rider's hands: the reins, a weapon held ready, swings, a bow drawn ----

	/**
	 * The rider's arms on a standing horse, read off the player model as last drawn: both hands on the reins when empty (or
	 * holding something that isn't a tool or weapon); a sword, an axe or a pickaxe held out ready in the main hand with the
	 * other on the reins, and a swing played from there back to there; mirrored for a left-handed player; drawing a bow
	 * side-on to the aim with the string hand coming back from the bow to the cheek as it charges and flying back on release;
	 * a loaded crossbow held square to the aim. Shots from behind, in front and the side of each, and frames mid-swing and
	 * through the draw.
	 */
	private void riderHands(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Rider's hands: reins, a weapon held ready, swings, a bow drawn");
		lane(ctx, input, world, 4000.5, -60, "hands_test");
		ctx.waitTicks(10);
		hold(ctx, "air");
		float[] arms = riderArms(ctx);
		check("empty hands: right hand on the reins (forward reach, rad)", -arms[0], RideTuning.REINS_ARM_FORWARD - 0.25, RideTuning.REINS_ARM_FORWARD + 0.25);
		check("empty hands: left hand on the reins (forward reach, rad)", -arms[3], RideTuning.REINS_ARM_FORWARD - 0.25, RideTuning.REINS_ARM_FORWARD + 0.25);
		check("empty hands: neither arm held out (most out to the side, rad)", Math.max(Math.abs(arms[2]), Math.abs(arms[5])), 0.0, 0.05);
		handShots(ctx, "20a_hands_empty");
		hold(ctx, "bread");
		arms = riderArms(ctx);
		check("bread: both hands still on the reins (most out to the side, rad)", Math.max(Math.abs(arms[2]), Math.abs(arms[5])), 0.0, 0.05);
		for (final String item : new String[] {"iron_sword", "iron_axe", "iron_pickaxe"}) {
			hold(ctx, item);
			arms = riderArms(ctx);
			check(item + ": right arm off the reins, held out to the side (rad)", arms[2], RideTuning.READY_ARM_OUT - 0.05, RideTuning.READY_ARM_OUT + 0.05);
			check(item + ": right arm reaching forward, ready (rad)", -arms[0], RideTuning.READY_ARM_FORWARD - 0.25, RideTuning.READY_ARM_FORWARD + 0.25);
			check(item + ": left hand on the reins (out to the side, rad)", Math.abs(arms[5]), 0.0, 0.05);
			handShots(ctx, "20b_hands_" + item);
		}
		hold(ctx, "iron_sword");
		swing(ctx, "20c_swing_sword", 0);

		// Left-handed: the same, mirrored.
		ctx.runOnClient(mc -> {
			mc.options.mainHand().set(HumanoidArm.LEFT);
			mc.options.broadcastOptions();
		});
		ctx.waitFor(mc -> mc.player.getMainArm() == HumanoidArm.LEFT, 100);
		ctx.waitTicks(2);
		arms = riderArms(ctx);
		check("left-handed, sword: left arm held out to the side (rad)", -arms[5], RideTuning.READY_ARM_OUT - 0.05, RideTuning.READY_ARM_OUT + 0.05);
		check("left-handed, sword: right hand on the reins (out to the side, rad)", Math.abs(arms[2]), 0.0, 0.05);
		handShots(ctx, "20d_hands_sword_left_handed");
		swing(ctx, "20e_swing_sword_left_handed", 3);
		ctx.runOnClient(mc -> {
			mc.options.mainHand().set(HumanoidArm.RIGHT);
			mc.options.broadcastOptions();
		});
		ctx.waitFor(mc -> mc.player.getMainArm() == HumanoidArm.RIGHT, 100);

		bowDraw(ctx, input, 0.0F, "20f_bow");
		bowDraw(ctx, input, 60.0F, "20g_bow_aim_right");

		// A loaded crossbow: square to the aim, both arms along it.
		this.server.runCommand("item replace entity @p weapon.mainhand with minecraft:crossbow[minecraft:charged_projectiles=[{id:\"minecraft:arrow\"}]]");
		input.lookAt(180.0F + 40.0F, 10.0F);
		ctx.waitTicks(5);
		arms = riderArms(ctx);
		check("loaded crossbow: the torso turns square to the aim (body against the aim, rad)", Math.abs(arms[6] - Mth.clamp(arms[7], -RideTuning.BOW_TWIST_MAX,
			RideTuning.BOW_TWIST_MAX)), 0.0, 0.02);
		check("loaded crossbow: the holding arm points along the aim (rad)", Math.abs(arms[1] - (arms[7] - 0.3F)), 0.0, 0.05);
		handShots(ctx, "20h_crossbow_loaded");
		input.lookAt(180.0F, 10.0F);
		hold(ctx, "air");
		ctx.runOnClient(mc -> FilmCamera.stop());
		ctx.waitTicks(5);
	}

	/** Puts {@code item} in the player's main hand and lets a couple of frames draw. */
	private void hold(final ClientGameTestContext ctx, final String item) {
		this.server.runCommand("item replace entity @p weapon.mainhand with minecraft:" + item);
		ctx.waitTicks(4);
	}

	/**
	 * The rider's arms as last drawn: right arm x/y/z rotation, left arm x/y/z, the torso's turn, the head's yaw and pitch,
	 * then the right and left shoulders' pivots (x, z), radians and model pixels.
	 */
	private static float[] riderArms(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			final PlayerModel model = ((AvatarRenderer<?>) mc.getEntityRenderDispatcher().getRenderer(mc.player)).getModel();
			return new float[] {
				model.rightArm.xRot, model.rightArm.yRot, model.rightArm.zRot, model.leftArm.xRot, model.leftArm.yRot, model.leftArm.zRot, model.body.yRot,
				model.head.yRot, model.head.xRot, model.rightArm.x, model.rightArm.z, model.leftArm.x, model.leftArm.z, model.rightArm.y, model.leftArm.y,
				model.head.x, model.head.y, model.head.z
			};
		});
	}

	/** Close shots of the rider without the HUD: three-quarters in front, three-quarters behind, and the right side. */
	private void handShots(final ClientGameTestContext ctx, final String name) {
		riderView(ctx, 225.0F);
		riderShot(ctx, name + "_front");
		riderView(ctx, 45.0F);
		riderShot(ctx, name + "_back");
		riderView(ctx, 270.0F);
		riderShot(ctx, name + "_side");
		riderView(ctx, 225.0F);
	}

	/**
	 * Films the rider's chest from 3 blocks away, {@code angle} degrees round the horse's heading (0 from behind, 90 from its
	 * left, 180 from in front), with the test camera (the game draws your own player only from its own camera, so a fixed
	 * camera entity can't show the rider).
	 */
	private static void riderView(final ClientGameTestContext ctx, final float angle) {
		riderView(ctx, angle, 10.0F);
	}

	/** {@link #riderView(ClientGameTestContext, float)} looking down at {@code pitch} degrees. */
	private static void riderView(final ClientGameTestContext ctx, final float angle, final float pitch) {
		ctx.runOnClient(mc -> FilmCamera.film(mc.player.getVehicle(), angle, 3.0F, 2.1F, pitch, 0.0F, 0.0F));
	}

	/** A screenshot without the HUD or chat; no tick passes. */
	private void riderShot(final ClientGameTestContext ctx, final String name) {
		ctx.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.hud.toggle();
		});
		screenshot(ctx, name);
		ctx.runOnClient(mc -> mc.gui.hud.toggle());
	}

	/**
	 * Swings the main hand and watches the arm (index {@code arm}: 0 right, 3 left) frame by frame: it should start from
	 * where it is held, rise, and come back to exactly there, with no jump between frames. A shot every other tick.
	 */
	private void swing(final ClientGameTestContext ctx, final String name, final int arm) {
		final float[] before = riderArms(ctx);
		ctx.runOnClient(mc -> mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true));
		float highest = 0.0F;
		float jump = 0.0F;
		float last = before[arm];
		for (int i = 0; i < 10; i++) {
			ctx.waitTick();
			final float[] now = riderArms(ctx);
			highest = Math.max(highest, before[arm] - now[arm]);
			jump = Math.max(jump, Math.abs(now[arm] - last));
			last = now[arm];
			if (i % 2 == 0 && i < 6) {
				riderShot(ctx, name + "_" + i);
			}
		}
		final float[] after = riderArms(ctx);
		check(name + ": the swing raises the arm from where it is held (rad)", highest, 0.6, 3.0);
		check(name + ": and brings it back to where it was held (rad)", Math.abs(after[arm] - before[arm]) + Math.abs(after[arm + 2] - before[arm + 2]), 0.0, 0.08);
		check(name + ": no jump between ticks (most the arm turned in a tick, rad)", jump, 0.0, 1.2);
	}

	/**
	 * Draws a bow for a second and a half looking {@code aim} degrees right of the horse, then looses: the bow arm out along
	 * the aim, the torso side-on to it, the string hand from the bow back to the cheek as it charges, and after the loose
	 * both arms back where they rest. Shots through the draw and the loose.
	 */
	private void bowDraw(final ClientGameTestContext ctx, final TestInput input, final float aim, final String name) {
		hold(ctx, "bow");
		input.lookAt(180.0F + aim, 10.0F);
		ctx.waitTicks(5);
		riderView(ctx, 225.0F);
		final float[] rest = riderArms(ctx);
		check(name + ": a bow in hand is held ready, off the reins (out to the side, rad)", rest[2], RideTuning.READY_ARM_OUT - 0.05, RideTuning.READY_ARM_OUT + 0.05);
		input.holdKey(o -> o.keyUse);
		// (Timed by the draw itself: the press can take a tick or two to start it.)
		double first = Double.NaN;
		double full = Double.NaN;
		float[] drawn = rest;
		int using = 0;
		int shot = 0;
		final int[] stages = {2, 6, 12};
		for (int tick = 1; tick <= 60 && using < 25; tick++) {
			ctx.waitTick();
			using = ctx.computeOnClient(mc -> mc.player.getTicksUsingItem());
			drawn = riderArms(ctx);
			if (Double.isNaN(first) && using >= 2) {
				first = stringToCheek(drawn);
			}
			if (shot < stages.length && using >= stages[shot]) {
				riderShot(ctx, name + "_draw_" + stages[shot]);
				shot++;
			}
		}
		check(name + ": the bow is drawn (ticks drawing)", using, 25, 60);
		full = stringToCheek(drawn);
		log("  %s full draw: head yaw %.3f pitch %.3f, torso %.3f, string arm x %.3f y %.3f, shoulder %.2f %.2f %.2f", name, drawn[7], drawn[8], drawn[6], drawn[3],
			drawn[4], drawn[11], drawn[14], drawn[12]);
		handShots(ctx, name + "_full_draw");
		// (Straight ahead, and from above on the right: the bow arm along the aim and the string hand at the cheek.)
		riderView(ctx, 180.0F + aim);
		riderShot(ctx, name + "_full_draw_ahead");
		riderView(ctx, 250.0F, 50.0F);
		riderShot(ctx, name + "_full_draw_above");
		riderView(ctx, 225.0F);
		check(name + ": the bow arm points along the aim (yaw off it, rad)", Math.abs(drawn[1] - (drawn[7] - RideTuning.BOW_ARM_IN)), 0.0, 0.05);
		check(name + ": and is raised to it (pitch off it, rad)", Math.abs(drawn[0] - (-Mth.HALF_PI + drawn[8])), 0.0, 0.05);
		check(name + ": side-on to the aim (torso against the aim, rad)", Math.abs(drawn[6] - Mth.clamp(drawn[7] - RideTuning.BOW_SIDE_ON, -RideTuning.BOW_TWIST_MAX,
			RideTuning.BOW_TWIST_MAX)), 0.0, 0.02);
		check(name + ": at full draw the string hand is at the cheek (pixels from it)", full, 0.0, 2.0);
		check(name + ": it came back from the bow as the draw charged (pixels nearer the cheek)", first - full, 4.0, 30.0);
		input.releaseKey(o -> o.keyUse);
		float jump = 0.0F;
		float last = drawn[3];
		for (int tick = 1; tick <= (int) RideTuning.BOW_RELEASE_TICKS + 3; tick++) {
			ctx.waitTick();
			final float[] now = riderArms(ctx);
			jump = Math.max(jump, Math.abs(now[3] - last));
			last = now[3];
			if (tick == 1 || tick == 4 || tick == 8) {
				riderShot(ctx, name + "_loosed_" + tick);
			}
		}
		final float[] after = riderArms(ctx);
		check(name + ": loosed, the arms come back to where they rest (rad)", Math.abs(after[0] - rest[0]) + Math.abs(after[3] - rest[3]) + Math.abs(after[5] - rest[5]),
			0.0, 0.1);
		check(name + ": with no jump (most the string arm turned in a tick, rad)", jump, 0.0, 1.0);
	}

	/**
	 * How far the string hand (the arm opposite the bow, the right arm's in {@code arms}) ends from the anchor at the cheek,
	 * pixels: the hand ARM_REACH along the arm from its shoulder against the anchor turned with the head.
	 */
	private static double stringToCheek(final float[] arms) {
		// (A right-handed rider: the bow in the right hand, the string drawn with the left to the right cheek.)
		final float reach = 10.0F;
		final float sx = Mth.sin(arms[3]);
		final double hx = arms[11] + sx * Mth.sin(arms[4]) * reach;
		final double hy = arms[14] + Mth.cos(arms[3]) * reach;
		final double hz = arms[12] + sx * Mth.cos(arms[4]) * reach;
		final org.joml.Vector3f anchor = new org.joml.Vector3f(-RideTuning.BOW_ANCHOR_X, RideTuning.BOW_ANCHOR_Y, RideTuning.BOW_ANCHOR_Z).rotateX(arms[8])
			.rotateY(arms[7]).add(arms[15], arms[16], arms[17]);
		return Math.sqrt((hx - anchor.x) * (hx - anchor.x) + (hy - anchor.y) * (hy - anchor.y) + (hz - anchor.z) * (hz - anchor.z));
	}

	// ---- 2-block climbs, swept (issue #24) ----

	/**
	 * One 2-block climb lane: what is built round it (relative to the lane's x; the rise's face at z = {@code faceZ}, the
	 * rise filling north of it two blocks tall, its top at y=-58), how the horse comes at it (look degrees right of north,
	 * spurs from a walk, jump pressed at the start instead of riding at it), and whether it should end up on top.
	 */
	private record Climb(String name, int faceZ, float look, int spurs, boolean press, boolean up, String... extra) {
		/** Ridden at it until it stops, then jump pressed there ("STUCK_PRESS" in extra). */
		boolean thenPress() {
			return List.of(this.extra).contains("STUCK_PRESS");
		}
	}

	/**
	 * 2-block climbs, swept systematically instead of patched case by case: approach angles, speeds, starting pressed against
	 * the face or out from it, walls beside it (one or both sides, 2 or 3 tall, inside and outside corners), something on
	 * top, slabs and stairs, the reported corner, and noisy mountain staircases. Each lane: the horse ends up on top within
	 * the time, and never rises more than a heave over the lip (a plain jump stacking on the heave went twice as high).
	 */
	private void climbs(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		final List<Climb> lanes = new ArrayList<>();
		for (final float look : new float[] {0.0F, 15.0F, 30.0F, 45.0F, 60.0F}) {
			lanes.add(new Climb(String.format(Locale.ROOT, "walking at %.0f degrees", look), -6, look, 0, false, true));
			lanes.add(new Climb(String.format(Locale.ROOT, "trotting at %.0f degrees", look), -8, look, 1, false, true));
		}
		lanes.add(new Climb("cantering straight at it", -16, 0.0F, 2, false, true));
		lanes.add(new Climb("cantering at 30 degrees", -16, 30.0F, 2, false, true));
		lanes.add(new Climb("riding along the face (rider not asking)", -2, 0.0F, 0, false, false, "ALONG"));
		lanes.add(new Climb("pressed against the face, riding at it", -1, 0.0F, 0, false, true));
		lanes.add(new Climb("pressed against the face, pressing jump", -1, 0.0F, 0, true, true));
		lanes.add(new Climb("a block out, riding at it", -2, 0.0F, 0, false, true));
		lanes.add(new Climb("a block out, pressing jump", -2, 0.0F, 0, true, true));
		lanes.add(new Climb("pressed against the face at 30 degrees, riding at it", -1, 30.0F, 0, false, true));
		// Walls alongside: "R2" a 2-tall wall along the right of the lane up to the face, "R3" 3 tall, "L2" on the left.
		lanes.add(new Climb("a 2-tall wall along the right", -4, 0.0F, 0, false, true, "R2"));
		lanes.add(new Climb("a 3-tall wall along the right", -4, 0.0F, 0, false, true, "R3"));
		lanes.add(new Climb("in a 1-wide notch (walls both sides)", -4, 0.0F, 0, false, true, "R2", "L2"));
		lanes.add(new Climb("in a 1-wide notch, trotting", -6, 0.0F, 1, false, true, "R2", "L2"));
		lanes.add(new Climb("reported: tucked in a corner against the face, a 3-tall wall right, riding at it", -1, 0.0F, 0, false, true, "R3"));
		lanes.add(new Climb("reported: tucked in a corner against the face, a 3-tall wall right, pressing jump", -1, 0.0F, 0, true, true, "R3"));
		lanes.add(new Climb("inside corner, coming in at 30 degrees toward the wall", -5, 30.0F, 0, false, true, "R3"));
		lanes.add(new Climb("inside corner, coming in at 30 degrees away from the wall", -5, -30.0F, 0, false, true, "R3"));
		lanes.add(new Climb("inside corner at 30 degrees toward the wall, stuck there, pressing jump", -5, 30.0F, 0, false, true, "R3", "STUCK_PRESS"));
		lanes.add(new Climb("outside corner, stuck there, pressing jump", -4, 0.0F, 0, false, true, "HALF", "STUCK_PRESS"));
		lanes.add(new Climb("a slab at the foot, stuck there, pressing jump", -4, 0.0F, 0, false, true, "SLAB_FOOT", "STUCK_PRESS"));
		lanes.add(new Climb("outside corner: the rise covers the right half of the horse", -4, 0.0F, 0, false, true, "HALF"));
		lanes.add(new Climb("a block on top where it lands", -4, 0.0F, 0, false, true, "BLOCK_ON_TOP"));
		lanes.add(new Climb("a slab at the foot of the face", -4, 0.0F, 0, false, true, "SLAB_FOOT"));
		lanes.add(new Climb("stairs along the lip", -4, 0.0F, 0, false, true, "STAIRS_LIP"));
		lanes.add(new Climb("snow on the ground and on top", -4, 0.0F, 0, false, true, "SNOW"));
		// A face running diagonally across the world grid ("DIAG": stepping back a block for every block across, so a saw-tooth
		// edge at 45 degrees; "DIAG2": a block back for every two across), met square on (look -45) and at angles.
		lanes.add(new Climb("diagonal face, walking square at it", -4, -45.0F, 0, false, true, "DIAG"));
		lanes.add(new Climb("diagonal face, trotting square at it", -7, -45.0F, 1, false, true, "DIAG"));
		lanes.add(new Climb("diagonal face, cantering square at it", -12, -45.0F, 2, false, true, "DIAG"));
		lanes.add(new Climb("diagonal face, walking north (45 degrees to it)", -4, 0.0F, 0, false, true, "DIAG"));
		lanes.add(new Climb("diagonal face, trotting north (45 degrees to it)", -7, 0.0F, 1, false, true, "DIAG"));
		lanes.add(new Climb("diagonal face, walking at 20 degrees off square", -4, -25.0F, 0, false, true, "DIAG"));
		lanes.add(new Climb("diagonal face, trotting at 20 degrees off square the other way", -7, -65.0F, 1, false, true, "DIAG"));
		lanes.add(new Climb("diagonal face, stuck at it, pressing jump", -4, -45.0F, 0, false, true, "DIAG", "STUCK_PRESS"));
		lanes.add(new Climb("half-diagonal face, walking north", -4, 0.0F, 0, false, true, "DIAG2"));
		lanes.add(new Climb("half-diagonal face, trotting square at it", -7, -27.0F, 1, false, true, "DIAG2"));
		lanes.add(new Climb("a 3-block face (not a climb)", -4, 0.0F, 0, false, false, "TALL"));
		lanes.add(new Climb("a 3-block face, pressing jump against it", -1, 0.0F, 0, true, false, "TALL"));
		final StringBuilder matrix = new StringBuilder();
		// (-Pscenario=<part of a lane's name> rides just those lanes.)
		final String only = System.getProperty("horsingaround.scenario", "");
		for (int i = 0; i < lanes.size(); i++) {
			if (lanes.get(i).name().contains(only)) {
				climb(ctx, input, world, 5000 + i * 60, lanes.get(i), matrix);
			}
		}
		for (int seed = 1; seed <= 3; seed++) {
			if (("mountain staircase " + seed).contains(only)) {
				mountainStaircase(ctx, input, world, 8000 + seed * 40, seed, matrix);
			}
		}
		// Diagonal terraces: 2-block rises with faces running diagonally across the grid, stacked one behind another (the
		// reported case), a few depths apart, ridden square up them and straight north, at a walk and a trot.
		int lane = 0;
		for (final int depth : new int[] {1, 2, 3, 6}) {
			for (final float look : new float[] {-45.0F, 0.0F}) {
				for (final int spurs : new int[] {0, 1}) {
					final String name = String.format(Locale.ROOT, "diagonal terraces %d deep, %s, %s", depth, look == 0.0F ? "riding north" : "square up them",
						spurs == 0 ? "walking" : "trotting");
					if (name.contains(only)) {
						diagonalTerraces(ctx, input, world, 9000 + lane * 60, name, depth, look, spurs, matrix);
					}
					lane++;
				}
			}
		}
		log("== 2-block climbs, all lanes:%s", matrix);
	}

	/**
	 * Terraces of 2-block rises whose faces run diagonally (each a saw-tooth edge at 45 degrees), {@code depth} blocks apart
	 * along z, stacked five high. Ridden up them looking {@code look} degrees right of north: it should reach the top, one
	 * heave a rise, never higher than a heave.
	 */
	private void diagonalTerraces(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final int x, final String name,
		final int depth, final float look, final int spurs, final StringBuilder matrix) {
		section("2-block climb: " + name);
		final int rises = 5;
		final List<String> build = new ArrayList<>();
		for (int n = 0; n < rises; n++) {
			for (int dx = -24; dx <= 24; dx++) {
				final int face = -4 - dx - n * depth;
				build.add(String.format(Locale.ROOT, "fill %d -60 %d %d %d %d minecraft:stone", x + dx, face - 30, x + dx, -59 + 2 * n, face));
			}
		}
		lane(ctx, input, world, x + 0.5, -60, "terraces_" + x, build.toArray(String[]::new));
		final double startY = sample(ctx).y;
		final double topY = startY + 2 * rises;
		final int climbsBefore = ride(ctx, r -> r.ledgeClimbs);
		input.lookAt(180.0F + look, 10.0F);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(2);
		for (int sp = 0; sp < spurs; sp++) {
			input.pressKey(o -> o.keySprint);
			ctx.waitTicks(4);
		}
		double highestOver = Double.NEGATIVE_INFINITY;
		double ground = startY;
		int onTop = -1;
		int stalled = 0;
		int stallLongest = 0;
		final StringBuilder trace = new StringBuilder();
		final int limit = 500;
		for (int t = 0; t < limit; t++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			if (s.onGround) {
				ground = Math.max(ground, s.y);
			}
			highestOver = Math.max(highestOver, s.y - (ground + 2.0));
			if (s.onGround && s.y > topY - 0.1) {
				onTop = t;
				break;
			}
			stalled = s.speed < 0.01 ? stalled + 1 : 0;
			stallLongest = Math.max(stallLongest, stalled);
			if (t % 5 == 0) {
				trace.append(String.format(Locale.ROOT, "%d x%.2f z%.2f y%+.2f yaw%.0f v%.2f l%d %s | ", t, ctx.computeOnClient(mc -> mc.player.getVehicle().getX()) - x,
					horseZ(ctx), s.y - startY, Mth.wrapDegrees(s.horseYaw - 180.0F), s.speed, ride(ctx, r -> r.ledgeTicks),
					dev.horsingaround.ride.Awareness.LEDGE_REASONS[Math.max(dev.horsingaround.ride.Awareness.ledgeRejection, 0)]));
			}
			if (stalled == 30) {
				ctx.runOnClient(mc -> {
					dev.horsingaround.ride.Awareness.climbLog.setLength(0);
					dev.horsingaround.ride.Awareness.logClimbs = true;
				});
				ctx.waitTick();
				ctx.runOnClient(mc -> dev.horsingaround.ride.Awareness.logClimbs = false);
				log("  stuck at x%.2f z%.2f y%+.2f: %s", ctx.computeOnClient(mc -> mc.player.getVehicle().getX()) - x, horseZ(ctx), sample(ctx).y - startY,
					ctx.computeOnClient(mc -> dev.horsingaround.ride.Awareness.climbLog.toString()));
			}
			if (stalled > 80) {
				break;
			}
		}
		input.releaseKey(o -> o.keyUp);
		final int heaves = ride(ctx, r -> r.ledgeClimbs) - climbsBefore;
		matrix.append(String.format(Locale.ROOT, "%n    %-90s %-14s heaves %d of %d, highest %+.2f over a rise, longest stall %d", name,
			onTop >= 0 ? "up in " + onTop + " ticks" : "NOT UP", heaves, rises, highestOver, stallLongest));
		check("reaches the top (ticks)", onTop < 0 ? limit : onTop, 0, limit - 1);
		check("one heave a rise", heaves, rises, rises);
		check("no long stall (longest standing still, ticks)", stallLongest, 0, 40);
		check("never higher than a heave over a rise (blocks)", highestOver, -3.0, RideTuning.LEDGE_CLEARANCE + 0.3);
		if (onTop < 0 || stallLongest > 40) {
			log("  path: %s", trace);
		}
		stop(ctx, input);
	}

	private void climb(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final int x, final Climb lane,
		final StringBuilder matrix) {
		section("2-block climb: " + lane.name());
		final List<String> build = new ArrayList<>();
		final List<String> extra = List.of(lane.extra());
		final int face = lane.faceZ();
		final int topY = extra.contains("TALL") ? -58 : -59;
		final int west = extra.contains("HALF") ? x : x - 12;
		if (extra.contains("ALONG")) {
			build.add(String.format(Locale.ROOT, "fill %d -60 -30 %d %d 4 minecraft:stone", x - 12, x - 1, topY));
		} else if (extra.contains("DIAG") || extra.contains("DIAG2")) {
			for (int dx = -14; dx <= 14; dx++) {
				final int back = extra.contains("DIAG") ? dx : Math.floorDiv(dx, 2);
				build.add(String.format(Locale.ROOT, "fill %d -60 %d %d %d %d minecraft:stone", x + dx, face - back - 16, x + dx, topY, face - back));
			}
		} else {
			build.add(String.format(Locale.ROOT, "fill %d -60 %d %d %d %d minecraft:stone", west, face - 14, x + 30, topY, face));
		}
		for (final String side : new String[] {"R", "L"}) {
			for (final int tall : new int[] {2, 3}) {
				if (extra.contains(side + tall)) {
					final int wx = side.equals("R") ? x + 1 : x - 1;
					build.add(String.format(Locale.ROOT, "fill %d -60 %d %d %d 4 minecraft:light_blue_terracotta", wx, face, wx, -61 + tall));
				}
			}
		}
		if (extra.contains("BLOCK_ON_TOP")) {
			build.add(String.format(Locale.ROOT, "setblock %d -58 %d minecraft:stone", x, face - 1));
		}
		if (extra.contains("SLAB_FOOT")) {
			build.add(String.format(Locale.ROOT, "fill %d -60 %d %d -60 %d minecraft:smooth_stone_slab", x - 8, face + 1, x + 8, face + 1));
		}
		if (extra.contains("STAIRS_LIP")) {
			build.add(String.format(Locale.ROOT, "fill %d -59 %d %d -59 %d minecraft:stone_stairs[facing=north]", x - 8, face, x + 8, face));
		}
		if (extra.contains("SNOW")) {
			build.add(String.format(Locale.ROOT, "fill %d -60 %d %d -60 4 minecraft:snow[layers=2]", x - 8, face + 1, x + 8));
			build.add(String.format(Locale.ROOT, "fill %d -58 %d %d -58 %d minecraft:snow[layers=2]", x - 8, face - 14, x + 8, face));
		}
		// (The outside corner: the horse straddles the rise's west edge, half its width in front of it.)
		lane(ctx, input, world, extra.contains("HALF") ? x : x + 0.5, -60, "climb_" + x, build.toArray(String[]::new));
		final double startY = sample(ctx).y;
		final double top = startY + (extra.contains("TALL") ? 3.0 : 2.0);
		final int climbsBefore = ride(ctx, r -> r.ledgeClimbs);
		input.lookAt(180.0F + lane.look(), 10.0F);
		if (lane.press()) {
			input.pressKey(o -> o.keyJump);
		} else {
			input.holdKey(o -> o.keyUp);
			ctx.waitTicks(2);
			for (int sp = 0; sp < lane.spurs(); sp++) {
				input.pressKey(o -> o.keySprint);
				ctx.waitTicks(4);
			}
		}
		double peak = startY;
		int onTop = -1;
		int stalled = 0;
		int stallLongest = 0;
		int pressedAt = -1;
		final StringBuilder trace = new StringBuilder();
		final int limit = 200;
		for (int t = 0; t < limit; t++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			if (onTop < 0) {
				peak = Math.max(peak, s.y);
			}
			if (onTop < 0 && s.onGround && s.y > top - 0.1) {
				onTop = t;
			}
			if (lane.thenPress() && pressedAt < 0 && stalled >= 10) {
				input.pressKey(o -> o.keyJump);
				pressedAt = t;
			}
			stalled = s.speed < 0.01 && !lane.press() ? stalled + 1 : 0;
			stallLongest = Math.max(stallLongest, stalled);
			if (t % 5 == 0 || t < 12) {
				trace.append(String.format(Locale.ROOT, "%d x%.2f z%.2f y%+.2f yaw%.0f v%.2f l%d %s | ", t, ctx.computeOnClient(mc -> mc.player.getVehicle().getX()) - x,
					horseZ(ctx), s.y - startY, Mth.wrapDegrees(s.horseYaw - 180.0F), s.speed, ride(ctx, r -> r.ledgeTicks),
					dev.horsingaround.ride.Awareness.LEDGE_REASONS[Math.max(dev.horsingaround.ride.Awareness.ledgeRejection, 0)]));
			}
			if (onTop >= 0 && t > onTop + 10) {
				break;
			}
			if ((lane.press() && t == 40 || pressedAt >= 0 && t == pressedAt + 40) && onTop < 0) {
				break;
			}
		}
		input.releaseKey(o -> o.keyUp);
		final int heaves = ride(ctx, r -> r.ledgeClimbs) - climbsBefore;
		// The highest a climb goes over the lip is a heave's clearance (and the push building it); a jump pressed against a
		// face that can't be climbed is a plain jump.
		final double over = peak - top;
		final String result = onTop >= 0 ? "up in " + onTop + " ticks" : "NOT UP";
		matrix.append(String.format(Locale.ROOT, "%n    %-90s %-14s heaves %d, peak %+.2f over the top, longest stall %d%s", lane.name(), result, heaves, over, stallLongest,
			pressedAt >= 0 ? " (jump pressed at tick " + pressedAt + ")" : ""));
		if (lane.up()) {
			check("ends up on top (ticks)", onTop < 0 ? limit : onTop, 0, limit - 1);
			check("one heave", heaves, 1, 1);
		} else {
			check("stays below (not a climb)", onTop < 0);
		}
		if (!lane.thenPress() && lane.up()) {
			check("no stall on the way (longest standing still, ticks)", stallLongest, 0, 20);
		}
		check("never higher than a heave over the lip (most over the top, blocks)", over, -3.5, RideTuning.LEDGE_CLEARANCE + 0.3);
		if ((onTop >= 0) != lane.up() || over > RideTuning.LEDGE_CLEARANCE + 0.3 || lane.up() && stallLongest > 20) {
			log("  path: %s", trace);
		}
		stop(ctx, input);
	}

	/**
	 * A mountainside of 2-block steps like generated ones: each step 2-4 blocks deep, now and then a 1-block step or a
	 * step coming in from the side, its edge wandering a block left and right (seeded, so the same each run). Ridden
	 * straight up at a walk then a trot; it should reach the top, one heave per 2-block step, never higher than a heave.
	 */
	private void mountainStaircase(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final int x, final int seed,
		final StringBuilder matrix) {
		section("2-block climb: mountain staircase " + seed);
		final java.util.Random random = new java.util.Random(seed * 7919L);
		final List<String> build = new ArrayList<>();
		int z = -4;
		int y = -60;
		int twos = 0;
		for (int step = 0; step < 7; step++) {
			final int rise = random.nextInt(5) == 0 ? 1 : 2;
			twos += rise == 2 ? 1 : 0;
			final int depth = 2 + random.nextInt(3);
			// Each step's face wanders: across the lane it is ragged by up to a block either way.
			for (int dx = -6; dx <= 6; dx++) {
				final int jitter = random.nextInt(3) - 1;
				build.add(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", x + dx, y, z - 40 + jitter, x + dx, y + rise - 1, z + jitter));
			}
			y += rise;
			z -= depth;
		}
		final double topY = y;
		lane(ctx, input, world, x + 0.5, -60, "mountain_" + x, build.toArray(String[]::new));
		final double startY = sample(ctx).y;
		final int climbsBefore = ride(ctx, r -> r.ledgeClimbs);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		double highestOver = Double.NEGATIVE_INFINITY;
		double ground = startY;
		int onTop = -1;
		int stalled = 0;
		int stallLongest = 0;
		final StringBuilder trace = new StringBuilder();
		final int limit = 600;
		for (int t = 0; t < limit; t++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			if (s.onGround) {
				ground = Math.max(ground, s.y);
			}
			// Over the ground it last stood on plus the step ahead (2): a heave goes LEDGE_CLEARANCE over that.
			highestOver = Math.max(highestOver, s.y - (ground + 2.0));
			if (onTop < 0 && s.onGround && s.y > topY - 0.1) {
				onTop = t;
				break;
			}
			stalled = s.speed < 0.01 ? stalled + 1 : 0;
			stallLongest = Math.max(stallLongest, stalled);
			if (t % 10 == 0) {
				trace.append(String.format(Locale.ROOT, "%d x%.2f z%.2f y%+.2f v%.2f l%d %s | ", t, ctx.computeOnClient(mc -> mc.player.getVehicle().getX()) - x,
					horseZ(ctx), s.y - startY, s.speed, ride(ctx, r -> r.ledgeTicks),
					dev.horsingaround.ride.Awareness.LEDGE_REASONS[Math.max(dev.horsingaround.ride.Awareness.ledgeRejection, 0)]));
			}
			if (stalled == 30) {
				// Stuck: why each place to land up the step ahead was turned down.
				ctx.runOnClient(mc -> {
					dev.horsingaround.ride.Awareness.climbLog.setLength(0);
					dev.horsingaround.ride.Awareness.logClimbs = true;
				});
				ctx.waitTick();
				ctx.runOnClient(mc -> dev.horsingaround.ride.Awareness.logClimbs = false);
				log("  stuck at x%.2f z%.2f y%+.2f: %s", ctx.computeOnClient(mc -> mc.player.getVehicle().getX()) - x, horseZ(ctx), sample(ctx).y - startY,
					ctx.computeOnClient(mc -> dev.horsingaround.ride.Awareness.climbLog.toString()));
			}
			if (stalled > 100) {
				break;
			}
		}
		input.releaseKey(o -> o.keyUp);
		final int heaves = ride(ctx, r -> r.ledgeClimbs) - climbsBefore;
		matrix.append(String.format(Locale.ROOT, "%n    %-90s %-14s heaves %d of %d two-block steps, highest %+.2f over a step, longest stall %d",
			"mountain staircase " + seed + String.format(Locale.ROOT, " (%.0f blocks up)", topY - startY), onTop >= 0 ? "up in " + onTop + " ticks" : "NOT UP", heaves, twos,
			highestOver, stallLongest));
		check("reaches the top (ticks)", onTop < 0 ? limit : onTop, 0, limit - 1);
		check("no long stall (longest standing still, ticks)", stallLongest, 0, 40);
		check("never higher than a heave over a step (blocks)", highestOver, -3.0, RideTuning.LEDGE_CLEARANCE + 0.3);
		if (onTop < 0 || stallLongest > 40) {
			log("  path: %s", trace);
		}
		stop(ctx, input);
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
			HorseConfig.get().cameraDistance = 1.2F;
			HorseConfig.apply();
		});
		check("a changed setting applies live (riding camera distance scale)", ctx.computeOnClient(mc -> RideTuning.CAMERA_DISTANCE_SCALE), 1.19, 1.21);
		ctx.runOnClient(mc -> HorseConfig.reset());
		check("no setting changes how horses ride (gallop speed multiple)", ctx.computeOnClient(mc -> RideTuning.GAIT_SPEED[RideTuning.GALLOP]), 1.149, 1.151);
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
		jumpAtTheFace(ctx, input, world);
		hurdles(ctx, input, world);
		pillar(ctx, input, world);
		treesInARow(ctx, input, world);
		forests(ctx, input, world);
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
		check("keeps its pace going round (slowest / gallop)", slowest / GALLOP_SPEED, 0.85, 1.1);
		check("heads where the rider looks again after (deg off)", Math.abs(Mth.wrapDegrees(sample(ctx).horseYaw - 180.0F)), 0.0, 3.0);
		check("detour eased out after (deg)", ride(ctx, r -> Math.abs(r.avoidOffset)), 0.0, 0.5);
		check("back on the rider's line after (blocks off it)", Math.abs(horseX(ctx) - 300.5), 0.0, 0.5);
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
		check("keeps moving going round (slowest / gallop)", slowest / GALLOP_SPEED, 0.75, 1.1);
		ctx.waitTicks(40);
		check("then heads where the rider looks again (deg off)", Math.abs(Mth.wrapDegrees(sample(ctx).horseYaw - 180.0F)), 0.0, 5.0);
		stop(ctx, input);
	}

	private void treeGap(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Walking through a 1-block gap between trunks");
		// Two rows of trunks across the path with one 1-block gap, straight ahead.
		lane(ctx, input, world, 620.5, -60, "tree_gap_test", "fill 610 -60 -20 630 -56 -20 minecraft:oak_log", "fill 620 -60 -20 620 -56 -20 minecraft:air");
		input.holdKey(o -> o.keyUp);
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 300 && horseZ(ctx) > -26.0; i++) {
			ctx.waitTick();
			if (horseZ(ctx) < -15.0 && i % 3 == 0) {
				trace.append(String.format(Locale.ROOT, "z%.2f x%.2f y%.2f v%.2f ledge%d | ", horseZ(ctx), horseX(ctx), sample(ctx).y, sample(ctx).speed,
					ride(ctx, r -> r.ledgeTicks)));
			}
		}
		check("fits through the gap", horseZ(ctx) < -25.9);
		if (horseZ(ctx) >= -25.9) {
			log("  path: %s", trace);
		}
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
			// Read on the server, where the damage is done (the client's copy can lag a busy machine).
			final float after = server.computeOnServer(sv -> sv.overworld().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(AbstractHorse.class),
				e -> e.entityTags().contains(tag)).stream().findFirst().map(AbstractHorse::getHealth).orElse(before));
			check("and it costs no more than that (health lost)", before - after, 0.5, 1.0);
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
		final StringBuilder ledgeTrace = new StringBuilder();
		float headGap = Float.MAX_VALUE;
		int frames = 0;
		int takeoffTick = -1;
		int landTick = -1;
		double previousY = -60.0;
		double previousZ = 0.0;
		// The climb, tick by tick: how fast it rises over its first ticks off the ground, and at its fastest.
		double climbY = -60.0;
		int climbTick = -1;
		double groundY = -60.0;
		double takeoffClimb = -1.0;
		double fastestClimb = 0.0;
		sideCamera(world, 405.5, -59.5, -17.5, 90.0F);
		for (int i = 0; i < 300 && horseZ(ctx) > -26.0; i++) {
			ctx.waitTick();
			final Sample s = sample(ctx);
			final double z = horseZ(ctx);
			final int tick = horseTick(ctx);
			headGap = Math.min(headGap, ride(ctx, r -> r.headGap));
			// From the side, from the crouch to walking on from the top (a fixed camera: the horse alone, as the game never
			// draws the local player for another camera), and its trace.
			if (z < -15.0 && frames < 16) {
				ledgeTrace.append(String.format(Locale.ROOT, "t%d z%.2f y%.2f vis%.2f tilt%.1f jump%.1f air%.2f | ",
					tick, z, s.y, s.visualY, s.pitch, ride(ctx, r -> r.jumpPitch(1.0F)), ride(ctx, r -> r.airLegs(1.0F))));
				cameraShot(ctx, String.format(Locale.ROOT, "14s_ledge_seq_%02d", frames));
				if (z < -16.5 && frames % 2 == 0) {
					// And from the riding camera swung to the side, to see the rider.
					sideScreenshot(ctx, String.format(Locale.ROOT, "14r_ledge_rider_%02d", frames), 0.0F);
				}
				frames++;
			}
			// Height, tick, and the ledge jump's takeoff tick read together (separate reads can fall either side of a tick).
			final double[] climbNow = ctx.computeOnClient(mc -> {
				final Entity horse = mc.player.getVehicle();
				final RideState r = ((RideStateHolder) horse).horsingaround$ride();
				return new double[] {horse.getY(), horse.tickCount, r.ledgeClimbs > climbs ? r.ledgeTakeoffTick : -1, horse.onGround() ? 1 : 0};
			});
			if (climbNow[2] < 0.0 && climbNow[3] > 0.0) {
				groundY = climbNow[0];
			} else if (climbNow[2] >= 0.0 && takeoffClimb < 0.0) {
				// Rise a tick, on average, from the ground through its first tick or two in the air.
				takeoffClimb = (climbNow[0] - groundY) / (climbNow[1] - climbNow[2] + 1.0);
			}
			if (climbTick >= 0 && climbNow[1] > climbTick) {
				fastestClimb = Math.max(fastestClimb, (climbNow[0] - climbY) / (climbNow[1] - climbTick));
			}
			climbY = climbNow[0];
			climbTick = (int) climbNow[1];
			if (Double.isNaN(takeoffZ)) {
				if (ride(ctx, r -> r.ledgeClimbs) > climbs) {
					// Where it really took off (samples can skip a tick while screenshots are taken).
					takeoffZ = ride(ctx, r -> r.ledgeTakeoffZ);
					takeoffTick = tick;
					previousY = s.y;
					previousZ = z;
				} else {
					approach = s.speed;
				}
				continue;
			}
			peak = Math.max(peak, s.y);
			if (landTick < 0 && s.onGround && s.y > -58.05) {
				landTick = tick;
			}
			if (landedAt < 0 && s.onGround && s.y > -58.05) {
				landedAt = i;
			}
			if (landedAt >= 0 && i == landedAt + 10) {
				after = s.speed;
			}
			if (risingTravel < 0.0 && s.y >= -58.05) {
				// Where the body crossed the lip's height, between this sample and the last.
				final double t = (-58.05 - previousY) / Math.max(s.y - previousY, 1.0E-6);
				risingTravel = takeoffZ - (previousZ + (z - previousZ) * Mth.clamp(t, 0.0, 1.0));
			}
			previousY = s.y;
			previousZ = z;
			if (!shot && s.y > -59.0) {
				sideScreenshot(ctx, "14_ledge_jump_side");
				shot = true;
			}
		}
		airborne = landTick - takeoffTick;
		log("  jump: %s", ledgeTrace);
		check("jumps up a 2-block ledge (blocks gained)", sample(ctx).y - -60.0, 1.95, 2.05);
		check("one ledge jump", ride(ctx, r -> r.ledgeClimbs) - climbs, 1, 1);
		check("bounds up it: takes off well before the wall (front to face, blocks)", takeoffZ - half(ctx) - -19.0, 1.2, 2.8);
		check("no stop before it (walking speed at takeoff, blocks/tick)", approach / WALK_SPEED, 0.6, 1.2);
		check("an arc, not a pop straight up: forward travel on the way up (blocks)", risingTravel, 1.2, 3.0);
		check("a heave, not a pop: the push builds (rise a tick over its first ticks off the ground, blocks)", takeoffClimb, 0.02, 0.25);
		check("a heave, not a pop: fastest climb (blocks/tick)", fastestClimb, 0.25, 0.45);
		check("in the air like a heave up a bank (ticks)", airborne, 10, 18);
		check("clears the lip without launching (peak above the top, blocks)", peak - -58.0, 0.05, 0.6);
		check("walks on from the top (speed after landing / walk)", after / WALK_SPEED, 0.6, 1.2);
		check("rider's head clear of the horse's (closest, centre to centre, blocks)", headGap, 0.45, 10.0);
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
		final StringBuilder path = new StringBuilder(String.format(Locale.ROOT, "start x%.2f yaw%.1f | ", horseX(ctx), sample(ctx).horseYaw));
		for (int i = 0; i < 300 && horseZ(ctx) > -42.0; i++) {
			ctx.waitTick();
			maxSide = Math.max(maxSide, Math.abs(horseX(ctx) - 1100.75));
			touched |= horseZ(ctx) < -18.0 && ctx.computeOnClient(mc -> mc.player.getVehicle().horizontalCollision);
			if (horseZ(ctx) < -12.0 && horseZ(ctx) > -26.0) {
				final Sample s = sample(ctx);
				path.append(String.format(Locale.ROOT, "z%.1f x%.2f y%.2f v%.2f o%.0f l%d | ", horseZ(ctx), horseX(ctx), s.y, s.speed,
					ride(ctx, r -> r.avoidOffset), ride(ctx, r -> r.ledgeTicks)));
			}
		}
		log("  path: %s", path);
		check("goes round it instead of jumping", ride(ctx, s -> s.ledgeClimbs) == climbs && sample(ctx).y < -59.9);
		check("rides on past it", horseZ(ctx) < -41.9);
		check("without scraping it", !touched);
		check("moves over only as much as it needs (blocks off the line)", maxSide, 0.2, 1.2);
		stop(ctx, input);
	}

	/**
	 * Pressing jump right up against something: at a wall too high to climb, a plain jump with no snort (the safety look
	 * used to read a wall right in front as a bottomless drop and refuse); at a 2-block ledge, standing at its face or a
	 * block out, the ledge jump (a plain jump can't clear it).
	 */
	private void jumpAtTheFace(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Pressing jump walking into a 3-block wall");
		lane(ctx, input, world, 3000.5, -60, "jump_wall_test", "fill 2995 -60 -30 3005 -58 -1 minecraft:stone");
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(15);
		int refusals = ride(ctx, r -> r.refusals);
		double startY = sample(ctx).y;
		check("walking into it", ride(ctx, r -> (int) Math.signum(r.speed)) == 1);
		input.pressKey(o -> o.keyJump);
		double peak = startY;
		for (int i = 0; i < 20; i++) {
			ctx.waitTick();
			peak = Math.max(peak, sample(ctx).y);
		}
		check("no snort: it doesn't refuse a jump against a wall", ride(ctx, r -> r.refusals) == refusals);
		check("it jumps (height, blocks)", peak - startY, 0.9, 3.0);
		stop(ctx, input);

		for (final int gap : new int[] {0, 1}) {
			section("Pressing jump standing " + (gap == 0 ? "at a 2-block ledge's face" : "a block from a 2-block ledge"));
			final int x = 3020 + gap * 20;
			lane(ctx, input, world, x + 0.5, -60, "jump_ledge_test_" + x, String.format(Locale.ROOT, "fill %d -60 -30 %d -59 %d minecraft:stone", x - 5, x + 5, -1 - gap));
			final int climbs = ride(ctx, r -> r.ledgeClimbs);
			refusals = ride(ctx, r -> r.refusals);
			startY = sample(ctx).y;
			input.pressKey(o -> o.keyJump);
			final StringBuilder trace = new StringBuilder();
			for (int i = 0; i < 40; i++) {
				ctx.waitTick();
				trace.append(String.format(Locale.ROOT, "z%.2f y%.2f l%d | ", horseZ(ctx), sample(ctx).y - startY, ride(ctx, r -> r.ledgeTicks)));
			}
			log("  path: %s", trace);
			check("jump asks for the ledge jump", ride(ctx, r -> r.ledgeClimbs) - climbs, 1, 1);
			check("no snort", ride(ctx, r -> r.refusals) == refusals);
			check("up on top (blocks gained)", sample(ctx).y - startY, 1.95, 2.05);
			stop(ctx, input);
		}
	}

	/**
	 * Spamming jump at a 2-block ledge (standing at its face, and walking at it): one heave up onto it, no higher than
	 * the heave goes, and no jump straight off the top the moment it lands. Records the drawn body too, for any pop.
	 */
	private void spamJumpAtLedge(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		for (final boolean walking : new boolean[] {false, true}) {
			section("Spamming jump " + (walking ? "walking at" : "standing at") + " a 2-block ledge");
			final int x = 3060 + (walking ? 20 : 0);
			lane(ctx, input, world, x + 0.5, -60, "spam_ledge_test_" + x,
				String.format(Locale.ROOT, "fill %d -60 -30 %d -59 %d minecraft:stone", x - 5, x + 5, walking ? -6 : -1));
			sideCamera(world, x + 6.5, -58.5, walking ? -6.0 : -1.0, 90.0F);
			final int climbs = ride(ctx, r -> r.ledgeClimbs);
			final double startY = sample(ctx).y;
			if (walking) {
				input.holdKey(o -> o.keyUp);
			}
			double peak = startY;
			double drawnPeak = startY;
			int takeoffs = 0;
			boolean wasOnGround = true;
			int firstLanding = -1;
			int nextTakeoff = -1;
			int hops = 0;
			double onTop = Double.NaN;
			final StringBuilder trace = new StringBuilder();
			for (int i = 0; i < 80; i++) {
				if (i % 2 == 0) {
					input.pressKey(o -> o.keyJump);
				}
				ctx.waitTick();
				final Sample now = sample(ctx);
				if (firstLanding < 0) {
					peak = Math.max(peak, now.y);
					drawnPeak = Math.max(drawnPeak, now.visualY);
				}
				if (wasOnGround && !now.onGround) {
					takeoffs++;
					if (firstLanding >= 0 && nextTakeoff < 0) {
						nextTakeoff = horseTick(ctx);
					} else if (firstLanding < 0 && now.y < startY + 1.0 && ride(ctx, r -> r.ledgeTicks) == 0) {
						// (Left the ground below the ledge and not in the heave: a plain hop.)
						hops++;
					}
				}
				if (!wasOnGround && now.onGround && firstLanding < 0 && now.y > startY + 1.9) {
					firstLanding = horseTick(ctx);
					onTop = now.y;
				}
				wasOnGround = now.onGround;
				trace.append(String.format(Locale.ROOT, "%d t%d y%.2f vis%.2f %s l%d | ", i, horseTick(ctx), now.y - startY, now.visualY - startY, now.onGround ? "g" : "a",
					ride(ctx, r -> r.ledgeTicks)));
				if (i % 8 == 0 && i < 48) {
					cameraShot(ctx, String.format(Locale.ROOT, "21_spam_%s_%02d", walking ? "walk" : "stand", i / 8));
				}
			}
			input.releaseKey(o -> o.keyUp);
			log("  path: %s", trace);
			check("heaves up onto it once", ride(ctx, r -> r.ledgeClimbs) - climbs, 1, 1);
			check("up on top (blocks gained)", onTop - startY, 1.95, 2.05);
			check("no higher than the heave goes, getting up there (most above the start, blocks)", peak - startY, 2.0, 2.0 + RideTuning.LEDGE_CLEARANCE + 0.35);
			check("nor drawn higher (most above the start, blocks)", drawnPeak - startY, 2.0, 2.0 + RideTuning.LEDGE_CLEARANCE + 0.45);
			// (Walking at it from well out, the first press is a plain jump in the open.)
			check("no hop into its face first (plain jumps before the heave)", hops, 0, walking ? 1 : 0);
			check("settles on top before jumping again (ticks from landing to the next jump)", nextTakeoff < 0 ? 99 : nextTakeoff - firstLanding, 12, 99);
			stop(ctx, input);
		}
	}

	/** A hurdle lane: what is built across the lane at z=-6, how the horse comes at it, and what should happen. */
	private record Hurdle(String name, String block, int spurs, boolean jump, String beyond, boolean over) {
	}

	/**
	 * Fences and walls across the lane (1.5 blocks tall, at z=-6): pressing jump within reach clears them at a walk, a trot
	 * and from a standstill, landing beyond and never on top; ridden at without a jump, the horse stops short with a snort;
	 * with lava beyond, it refuses the jump.
	 */
	private void hurdles(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		final List<Hurdle> lanes = List.of(
			new Hurdle("an oak fence at a walk", "oak_fence", 0, true, "", true),
			new Hurdle("a cobblestone wall at a trot", "cobblestone_wall", 1, true, "", true),
			new Hurdle("an oak fence at a canter", "oak_fence", 2, true, "", true),
			new Hurdle("an oak fence from a standstill", "oak_fence", -1, true, "", true),
			new Hurdle("an oak fence at a trot, no jump", "oak_fence", 1, false, "", false),
			new Hurdle("an oak fence with lava beyond", "oak_fence", 0, true, "lava", false)
		);
		for (int i = 0; i < lanes.size(); i++) {
			final Hurdle lane = lanes.get(i);
			section("Hurdle: " + lane.name());
			final int x = 3100 + i * 20;
			final List<String> build = new ArrayList<>();
			// (Fences join up with each other; the line stands on its own.)
			build.add(String.format(Locale.ROOT, "fill %d -60 -6 %d -60 -6 minecraft:%s", x - 3, x + 3, lane.block()));
			if (!lane.beyond().isEmpty()) {
				build.add(String.format(Locale.ROOT, "fill %d -61 -12 %d -61 -7 minecraft:%s", x - 3, x + 3, lane.beyond()));
			}
			lane(ctx, input, world, x + 0.5, -60, "hurdle_test_" + x, build.toArray(String[]::new));
			sideCamera(world, x + 6.5, -59.5, -6.0, 90.0F);
			final int hurdles = ride(ctx, r -> r.hurdles);
			final int refusals = ride(ctx, r -> r.refusals);
			if (lane.spurs() < 0) {
				// From a standstill, half a block short of it.
				ctx.runOnClient(mc -> mc.player.getVehicle().setPos(x + 0.5, -60.0, -4.6));
				ctx.waitTicks(5);
				input.pressKey(o -> o.keyJump);
			} else {
				input.holdKey(o -> o.keyUp);
				ctx.waitTicks(2);
				for (int sp = 0; sp < lane.spurs(); sp++) {
					input.pressKey(o -> o.keySprint);
					ctx.waitTicks(4);
				}
			}
			boolean pressed = lane.spurs() < 0;
			double highestOver = -60.0;
			double slowest = Double.MAX_VALUE;
			int shots = 0;
			final StringBuilder trace = new StringBuilder();
			for (int t = 0; t < 160 && horseZ(ctx) > -14.0; t++) {
				ctx.waitTick();
				final double z = horseZ(ctx);
				final Sample s = sample(ctx);
				if (!pressed && lane.jump() && z - half(ctx) < -4.2) {
					// Jump with the chest about a stride short of it.
					input.pressKey(o -> o.keyJump);
					pressed = true;
				}
				if (z > -8.0 && z < -1.0) {
					trace.append(String.format(Locale.ROOT, "z%.2f y%.2f v%.2f g%s r%d | ", z, s.y, s.speed, s.onGround ? "1" : "0", ride(ctx, r -> r.refusals) - refusals));
					if (shots < 8) {
						cameraShot(ctx, String.format(Locale.ROOT, "19_hurdle_%d_%02d", i, shots++));
					}
				}
				if (Math.abs(z - -5.5) < 0.7) {
					highestOver = Math.max(highestOver, s.y);
				}
				if (z > -4.0 && z < -2.0 && t > 10) {
					slowest = Math.min(slowest, s.speed);
				}
			}
			input.releaseKey(o -> o.keyUp);
			ctx.waitTicks(10);
			log("  path: %s", trace);
			final double end = horseZ(ctx);
			if (lane.over()) {
				check("over it (z beyond -7)", end < -7.0);
				check("one hurdle jump", ride(ctx, r -> r.hurdles) - hurdles, 1, 1);
				check("high enough over it (lowest clear height as it passes, blocks above the ground)", highestOver - -60.0, 1.5, 3.0);
				check("lands on the ground beyond, not on the fence (y)", sample(ctx).y, -60.05, -59.95);
				check("no snort", ride(ctx, r -> r.refusals) == refusals);
			} else {
				check("stays this side of it", end > -6.0);
				check("never on top of it (y)", sample(ctx).y, -60.05, -59.95);
				check("snorts at it", ride(ctx, r -> r.refusals) > refusals);
			}
			stop(ctx, input);
		}
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
		check("keeps its pace (slowest / gallop)", slowest / GALLOP_SPEED, 0.8, 1.1);
		stop(ctx, input);
	}

	/**
	 * Ways through: galloping straight through scattered trunks (seeded, so the same each run), and two trunks staggered
	 * either side of the line. It should thread them at nearly its full pace, never touch one, never sit back into a cut,
	 * and once through come back onto the line the rider was on.
	 */
	private void forests(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		forest(ctx, input, world, "two trunks staggered across the line", 4400, List.of(new int[] {0, -25}, new int[] {2, -31}));
		for (int seed = 1; seed <= 3; seed++) {
			final java.util.Random random = new java.util.Random(seed * 104729L);
			final List<int[]> trunks = new ArrayList<>();
			// A trunk in about a third of 3x3 cells, anywhere in its cell, 10 blocks either side of the line.
			for (int cz = -18; cz >= -56; cz -= 3) {
				for (int cx = -10; cx <= 10; cx += 3) {
					if (random.nextInt(3) == 0) {
						trunks.add(new int[] {cx + random.nextInt(3) - 1, cz - random.nextInt(3)});
					}
				}
			}
			forest(ctx, input, world, "scattered trunks " + seed, 4400 + seed * 40, trunks);
		}
	}

	private void forest(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final String name, final int x,
		final List<int[]> trunks) {
		section("Galloping through " + name);
		final List<String> build = new ArrayList<>();
		for (final int[] t : trunks) {
			build.add(String.format(Locale.ROOT, "fill %d -60 %d %d -57 %d minecraft:oak_log", x + t[0], t[1], x + t[0], t[1]));
		}
		lane(ctx, input, world, x + 0.5, -60, "forest_" + x, build.toArray(String[]::new));
		final int cuts = ride(ctx, r -> r.cuts);
		ctx.runOnClient(mc -> {
			dev.horsingaround.ride.Awareness.plans = 0;
			dev.horsingaround.ride.Awareness.planNanos = 0L;
			dev.horsingaround.ride.Awareness.planNanosMost = 0L;
			dev.horsingaround.ride.RideController.profile = true;
		});
		gallopNorth(ctx, input);
		int touches = 0;
		double slowest = Double.MAX_VALUE;
		double pace = 0.0;
		int paceTicks = 0;
		double maxSide = 0.0;
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 400 && horseZ(ctx) > -82.0; i++) {
			ctx.waitTick();
			final double z = horseZ(ctx);
			if (z < -12.0 && z > -62.0) {
				final double speed = sample(ctx).speed;
				slowest = Math.min(slowest, speed);
				pace += speed;
				paceTicks++;
				maxSide = Math.max(maxSide, Math.abs(horseX(ctx) - (x + 0.5)));
				if (ctx.computeOnClient(mc -> mc.player.getVehicle().horizontalCollision)) {
					touches++;
				}
			}
			if (i % 3 == 0 && z < -10.0) {
				trace.append(String.format(Locale.ROOT, "z%.1f x%.2f v%.2f o%.0f w%d | ", z, horseX(ctx) - x - 0.5, sample(ctx).speed, ride(ctx, r -> r.avoidOffset),
					(int) ride(ctx, r -> r.way)));
			}
		}
		final double offLine = Math.abs(horseX(ctx) - (x + 0.5));
		ctx.runOnClient(mc -> dev.horsingaround.ride.RideController.profile = false);
		final int plans = ctx.computeOnClient(mc -> dev.horsingaround.ride.Awareness.plans);
		final double planMicros = ctx.computeOnClient(mc -> dev.horsingaround.ride.Awareness.planNanos) / 1000.0 / Math.max(plans, 1);
		final double planMostMicros = ctx.computeOnClient(mc -> dev.horsingaround.ride.Awareness.planNanosMost) / 1000.0;
		log("  %d plans, %.0f us each on average, %.0f us at most", plans, planMicros, planMostMicros);
		check("plans its way quickly (average per plan, microseconds)", planMicros, 0.0, 400.0);
		log("  through at %.0f%% of a gallop on average, slowest %.0f%%, %d ticks touching, most %.1f off the line, %.2f off it after; path: %s",
			pace / Math.max(paceTicks, 1) / GALLOP_SPEED * 100.0, slowest / GALLOP_SPEED * 100.0, touches, maxSide, offLine, trace);
		check("rides through", horseZ(ctx) < -81.0);
		check("never touches a trunk (ticks)", touches, 0, 0);
		check("keeps its pace through (average / gallop)", pace / Math.max(paceTicks, 1) / GALLOP_SPEED, 0.85, 1.1);
		check("never slows much (slowest / gallop)", slowest / GALLOP_SPEED, 0.6, 1.1);
		check("doesn't sit back into a cut going round", ride(ctx, r -> r.cuts) == cuts);
		check("back on the rider's line after (blocks off it)", offLine, 0.0, 0.5);
		stop(ctx, input);
	}

	/** A 2-block ledge like the ones in generated worlds: what it is, how it's built, how it's ridden at, and whether it should be jumped. */
	private record Ledge(String name, String[] build, float look, int spurs, double faceZ, boolean jump) {
	}

	/**
	 * 2-block ledges as generated worlds make them: met at an angle, with snow or grass or a bump on top, leaves or a
	 * branch overhead, from a step up, on slabs, from a standstill, at a canter. Each is walked (or cantered) at; the
	 * report says whether the horse jumped and, if it didn't, why the last look at the ledge turned it down.
	 */
	private void messyLedges(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		final List<Ledge> ledges = List.of(
			new Ledge("straight on", new String[] {"fill ~-15 -60 -30 ~15 -59 -11 minecraft:stone"}, 0.0F, 0, -10.0, true),
			new Ledge("at 30 degrees", new String[] {"fill ~-15 -60 -30 ~15 -59 -11 minecraft:stone"}, 30.0F, 0, -10.0, true),
			new Ledge("at 45 degrees", new String[] {"fill ~-15 -60 -30 ~15 -59 -11 minecraft:stone"}, 45.0F, 0, -10.0, true),
			new Ledge("snow on top", new String[] {"fill ~-15 -60 -30 ~15 -59 -11 minecraft:stone", "fill ~-15 -58 -30 ~15 -58 -11 minecraft:snow[layers=2]"}, 0.0F, 0, -10.0, true),
			new Ledge("grass and flowers on top", new String[] {"fill ~-15 -60 -30 ~15 -59 -11 minecraft:grass_block", "fill ~-15 -58 -30 ~15 -58 -11 minecraft:short_grass",
				"fill ~-1 -58 -12 ~1 -58 -12 minecraft:poppy"}, 0.0F, 0, -10.0, true),
			new Ledge("a bump two blocks in", new String[] {"fill ~-15 -60 -30 ~15 -59 -11 minecraft:stone", "fill ~-15 -58 -13 ~15 -58 -13 minecraft:stone"}, 0.0F, 0, -10.0, true),
			new Ledge("leaves overhead", new String[] {"fill ~-15 -60 -30 ~15 -59 -11 minecraft:stone", "fill ~-4 -56 -14 ~4 -55 -5 minecraft:oak_leaves[persistent=true]"}, 0.0F, 0, -10.0, true),
			new Ledge("a branch four blocks up (just room)", new String[] {"fill ~-15 -60 -30 ~15 -59 -11 minecraft:stone", "fill ~-4 -56 -12 ~4 -56 -6 minecraft:oak_log"}, 0.0F, 0, -10.0, true),
			new Ledge("a branch three blocks up (no room)", new String[] {"fill ~-15 -60 -30 ~15 -59 -11 minecraft:stone", "fill ~-4 -57 -12 ~4 -57 -6 minecraft:oak_log"}, 0.0F, 0, -10.0, false),
			new Ledge("snow on top, at a canter at 30 degrees", new String[] {"fill ~-25 -60 -40 ~25 -59 -21 minecraft:stone", "fill ~-25 -58 -40 ~25 -58 -21 minecraft:snow[layers=3]"}, 30.0F, 2, -20.0, true),
			new Ledge("from a step up", new String[] {"fill ~-15 -60 -30 ~15 -58 -11 minecraft:stone", "fill ~-15 -60 -10 ~15 -60 -6 minecraft:stone"}, 0.0F, 0, -10.0, true),
			new Ledge("slabs on top (1.5 up)", new String[] {"fill ~-15 -60 -30 ~15 -60 -11 minecraft:stone", "fill ~-15 -59 -30 ~15 -59 -11 minecraft:smooth_stone_slab"}, 0.0F, 0, -10.0, true),
			new Ledge("rough grass ground", new String[] {"fill ~-15 -60 -30 ~15 -59 -11 minecraft:dirt", "fill ~-15 -61 -10 ~15 -61 0 minecraft:dirt_path"}, 0.0F, 0, -10.0, true),
			new Ledge("at a canter", new String[] {"fill ~-15 -60 -40 ~15 -59 -21 minecraft:stone"}, 0.0F, 2, -20.0, true),
			new Ledge("at a canter, at 30 degrees", new String[] {"fill ~-25 -60 -40 ~25 -59 -21 minecraft:stone"}, 30.0F, 2, -20.0, true),
			new Ledge("a step up right where it lands", new String[] {"fill ~-15 -60 -30 ~15 -59 -11 minecraft:red_concrete", "fill ~-15 -58 -30 ~15 -58 -12 minecraft:red_concrete"}, 0.0F, 0, -10.0, true),
			new Ledge("a staircase of 2-block steps", new String[] {"fill ~-15 -60 -30 ~15 -59 -11 minecraft:red_concrete", "fill ~-15 -58 -30 ~15 -58 -12 minecraft:red_concrete",
				"fill ~-15 -57 -30 ~15 -57 -13 minecraft:red_concrete"}, 0.0F, 0, -10.0, true),
			new Ledge("a step up right where it lands, at a trot", new String[] {"fill ~-15 -60 -30 ~15 -59 -11 minecraft:red_concrete", "fill ~-15 -58 -30 ~15 -58 -12 minecraft:red_concrete"}, 0.0F, 1, -10.0, true),
			new Ledge("from a standstill at the face", new String[] {"fill ~-15 -60 -30 ~15 -59 -2 minecraft:stone"}, 0.0F, 0, -1.0, true)
		);
		for (int i = 0; i < ledges.size(); i++) {
			final Ledge ledge = ledges.get(i);
			section("2-block ledge: " + ledge.name());
			final int x = 1700 + i * 40;
			final String[] build = new String[ledge.build().length];
			for (int b = 0; b < build.length; b++) {
				build[b] = ledge.build()[b].replace("~-25", String.valueOf(x - 25)).replace("~25", String.valueOf(x + 25))
					.replace("~-15", String.valueOf(x - 15)).replace("~15", String.valueOf(x + 15))
					.replace("~-4", String.valueOf(x - 4)).replace("~4", String.valueOf(x + 4))
					.replace("~-1", String.valueOf(x - 1)).replace("~1", String.valueOf(x + 1));
			}
			lane(ctx, input, world, x + 0.5, -60, "messy_ledge_" + i, build);
			final int climbs = ride(ctx, r -> r.ledgeClimbs);
			final double startY = sample(ctx).y;
			input.lookAt(180.0F + ledge.look(), 10.0F);
			input.holdKey(o -> o.keyUp);
			ctx.waitTicks(2);
			for (int sp = 0; sp < ledge.spurs(); sp++) {
				input.pressKey(o -> o.keySprint);
				ctx.waitTicks(4);
			}
			int lastReason = -1;
			double closest = Double.MAX_VALUE;
			final StringBuilder trace = new StringBuilder();
			for (int t = 0; t < 240 && ride(ctx, r -> r.ledgeClimbs) == climbs; t++) {
				ctx.waitTick();
				final double gap = horseZ(ctx) - half(ctx) - ledge.faceZ();
				closest = Math.min(closest, gap);
				if (gap < 3.5) {
					lastReason = dev.horsingaround.ride.Awareness.ledgeRejection;
					if (t % 4 == 0) {
						trace.append(String.format(Locale.ROOT, "gap%.2f v%.2f yaw%.0f %s | ", gap, sample(ctx).speed, sample(ctx).horseYaw,
							dev.horsingaround.ride.Awareness.LEDGE_REASONS[Math.max(lastReason, 0)]));
					}
				}
			}
			final StringBuilder flight = new StringBuilder();
			double previousDrawn = Double.NaN;
			double previousRise = Double.NaN;
			int previousTick = horseTick(ctx);
			double sharpest = 0.0;
			for (int t = 0; t < 20; t++) {
				ctx.waitTick();
				final Sample s = sample(ctx);
				final int tick = horseTick(ctx);
				final int elapsed = Math.max(tick - previousTick, 1);
				previousTick = tick;
				if (!Double.isNaN(previousDrawn)) {
					final double rise = (s.visualY - previousDrawn) / elapsed;
					if (!Double.isNaN(previousRise) && t > 2) {
						sharpest = Math.max(sharpest, rise - previousRise);
					}
					previousRise = rise;
				}
				previousDrawn = s.visualY;
				flight.append(String.format(Locale.ROOT, "y%.2f drawn%.2f %s | ", s.y - startY, s.visualY - startY, s.onGround ? "g" : "a"));
			}
			final boolean jumped = ride(ctx, r -> r.ledgeClimbs) > climbs;
			if (jumped) {
				log("  flight: %s", flight);
				check("no pop in the air or onto the top (sharpest pick-up of the drawn rise, blocks/tick a tick)", sharpest, 0.0, 0.3);
			}
			log("  %s; closest to the face %s; last look: %s", jumped ? "jumped" : "did not jump",
				closest == Double.MAX_VALUE ? "n/a" : String.format(Locale.ROOT, "%.2f", closest),
				lastReason < 0 ? "never looked" : dev.horsingaround.ride.Awareness.LEDGE_REASONS[lastReason]);
			if (!jumped) {
				log("  approach: %s", trace);
			}
			if (ledge.jump()) {
				check("jumps it", jumped);
				check("up on top (blocks gained)", sample(ctx).y - startY, 1.4, ledge.name().contains("staircase") ? 4.1 : 3.1);
			} else {
				check("doesn't try (no room to jump)", !jumped);
			}
			stop(ctx, input);
		}
	}

	/**
	 * Close side shots of the legs through a jump up a 2-block ledge and a running jump on the flat, from a fixed camera
	 * (horse only: the game never draws the local player for another camera). For judging the pose; no checks.
	 */
	private void jumpLegShots(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Jump leg shots");
		lane(ctx, input, world, 1600.5, -60, "leg_shots_ledge", "fill 1595 -60 -40 1605 -59 -20 minecraft:stone");
		sideCamera(world, 1605.5, -58.5, -19.0, 90.0F);
		input.holdKey(o -> o.keyUp);
		int frames = 0;
		for (int i = 0; i < 300 && horseZ(ctx) > -22.0; i++) {
			ctx.waitTick();
			if (ride(ctx, r -> r.airLegs(1.0F)) > 0.3F && frames < 10) {
				cameraShot(ctx, String.format(Locale.ROOT, "20_legs_ledge_%02d", frames++));
			}
		}
		stop(ctx, input);
		lane(ctx, input, world, 1640.5, -60, "leg_shots_run");
		sideCamera(world, 1645.5, -58.8, -25.0, 90.0F);
		gallopNorth(ctx, input);
		frames = 0;
		boolean jumped = false;
		for (int i = 0; i < 300 && horseZ(ctx) > -40.0; i++) {
			ctx.waitTick();
			if (!jumped && horseZ(ctx) < -20.5) {
				input.pressKey(o -> o.keyJump);
				jumped = true;
			}
			if (ride(ctx, r -> r.airLegs(1.0F)) > 0.3F && frames < 10) {
				cameraShot(ctx, String.format(Locale.ROOT, "20_legs_run_%02d", frames++));
			}
		}
		stop(ctx, input);
	}

	/**
	 * Standing still, and walking slowly up and down a single 1-block step, on across the top and then stopping: every tick,
	 * the drawn horse's height, its tilt, its fit to its legs and each leg's draw up, flagging every tick any of them changes
	 * how fast it moves more than a little (a snap, a hitch), and every tick a hoof is in the ground.
	 */
	private void slowSteps(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		ctx.runOnClient(mc -> LegProbe.arm(true));
		// (At a real frame rate: a snap between ticks only shows frame by frame.)
		final int frameLimit = ctx.computeOnClient(mc -> mc.options.framerateLimit().get());
		ctx.runOnClient(mc -> mc.options.framerateLimit().set(120));
		try {
			watch(ctx, input, world, "standing still on flat ground", 3240, -60, Double.NaN, Double.NaN, backdrop(3240));
			// With the front hooves over the edge of a 1-block drop (the box still on the top).
			watch(ctx, input, world, "standing still at the edge of a block", 3260, -59, Double.NaN, Double.NaN,
				String.format(Locale.ROOT, "fill %d -60 0 %d -60 12 minecraft:stone", 3260, 3260), backdrop(3260));
			watch(ctx, input, world, "walking on flat ground, then stopping", 3280, -60, -4.0, -12.0, backdrop(3280));
			watch(ctx, input, world, "walking up a single step, on across the top, then stopping", 3200, -60, -11.0, -16.0,
				String.format(Locale.ROOT, "fill %d -60 -30 %d -60 -6 minecraft:stone", 3200, 3200), backdrop(3200));
			watch(ctx, input, world, "walking down a single step, on, then stopping", 3220, -59, -11.0, -16.0,
				String.format(Locale.ROOT, "fill %d -60 -6 %d -60 12 minecraft:stone", 3220, 3220), backdrop(3220));
		} finally {
			ctx.runOnClient(mc -> {
				LegProbe.arm(false);
				mc.options.framerateLimit().set(frameLimit);
			});
		}
	}

	/**
	 * Rides north from z=0.5 (walking, if {@code release} isn't NaN) and lets go of W once past {@code release}, watching
	 * until standing still for two seconds or past {@code until}.
	 */
	private void watch(
		final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final String what, final int x, final double y,
		final double release, final double until, final String... build
	) {
		section("Watched tick by tick: " + what);
		lane(ctx, input, world, x + 0.5, y, "watch_" + x, build);
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> LegProbe.reset());
		final boolean walking = !Double.isNaN(release);
		if (walking) {
			input.holdKey(o -> o.keyUp);
		}
		final StringBuilder flags = new StringBuilder();
		final StringBuilder rows = new StringBuilder();
		final List<double[]> history = new ArrayList<>();
		boolean held = walking;
		int still = 0;
		int flagged = 0;
		double sunk = 0.0;
		final String[] names = {"FL", "FR", "HL", "HR"};
		for (int i = 0; i < 260; i++) {
			ctx.waitTick();
			final double[] now = ctx.computeOnClient(mc -> {
				final AbstractHorse horse = (AbstractHorse) mc.player.getVehicle();
				final RideState r = ((RideStateHolder) horse).horsingaround$ride();
				return new double[] {
					horse.tickCount, horse.getZ(), horse.getY(), horse.getY() + r.heightOffset(1.0F), r.pitch(1.0F), r.drawnFit, r.legRise[0], r.legRise[1],
					r.legRise[2], r.legRise[3], LegProbe.gap[0], LegProbe.gap[1], LegProbe.gap[2], LegProbe.gap[3], horse.onGround() ? 1 : 0,
					Math.sqrt((horse.getX() - horse.xo) * (horse.getX() - horse.xo) + (horse.getZ() - horse.zo) * (horse.getZ() - horse.zo))
				};
			});
			if (held && now[1] < release) {
				input.releaseKey(o -> o.keyUp);
				held = false;
				rows.append(String.format(Locale.ROOT, "%n    (let go of W at t%d)", (int) now[0]));
			}
			if (!history.isEmpty() && now[0] == history.get(history.size() - 1)[0]) {
				continue;
			}
			history.add(now);
			rows.append(String.format(Locale.ROOT, "%n    t%d z%.2f y%.3f drawn%.4f tilt%.2f fit%.3f draws %.3f %.3f %.3f %.3f gaps %+.3f %+.3f %+.3f %+.3f speed%.3f",
				(int) now[0], now[1], now[2], now[3], now[4], now[5], now[6], now[7], now[8], now[9], now[10], now[11], now[12], now[13], now[15]));
			for (int leg = 0; leg < 4; leg++) {
				if (!Double.isNaN(now[10 + leg]) && now[10 + leg] < -0.06) {
					sunk = Math.min(sunk, now[10 + leg]);
					flags.append(String.format(Locale.ROOT, "%n    t%d %s in the ground %.3f", (int) now[0], names[leg], now[10 + leg]));
				}
			}
			final int n = history.size();
			if (n >= 3 && history.get(n - 3)[0] + 2 == now[0]) {
				final double[] a = history.get(n - 3);
				final double[] b = history.get(n - 2);
				// How much the speed of each changed this tick.
				final double body = now[3] - 2.0 * b[3] + a[3];
				final double tilt = now[4] - 2.0 * b[4] + a[4];
				final double fit = now[5] - 2.0 * b[5] + a[5];
				final StringBuilder what2 = new StringBuilder();
				if (Math.abs(body) > 0.03) {
					what2.append(String.format(Locale.ROOT, " body %+.3f", body));
				}
				if (Math.abs(tilt) > 2.0) {
					what2.append(String.format(Locale.ROOT, " tilt %+.1f", tilt));
				}
				if (Math.abs(fit) > 0.03) {
					what2.append(String.format(Locale.ROOT, " fit %+.3f", fit));
				}
				for (int leg = 0; leg < 4; leg++) {
					final double draw = now[6 + leg] - 2.0 * b[6 + leg] + a[6 + leg];
					if (Math.abs(draw) > 0.06) {
						what2.append(String.format(Locale.ROOT, " %s draw %+.3f", names[leg], draw));
					}
				}
				if (what2.length() > 0) {
					flagged++;
					flags.append(String.format(Locale.ROOT, "%n    t%d z%.2f%s", (int) now[0], now[1], what2));
				}
			}
			still = now[15] < 1.0E-4 ? still + 1 : 0;
			if (!held && (still > 40 || now[1] < until)) {
				break;
			}
		}
		input.releaseKey(o -> o.keyUp);
		ctx.runOnClient(mc -> FilmCamera.stop());
		log("  frame by frame, the body or a sole jumping (%d frames of %d):%s", ctx.computeOnClient(mc -> LegProbe.spikeCount),
			ctx.computeOnClient(mc -> LegProbe.frames), ctx.computeOnClient(mc -> LegProbe.spikes.toString()));
		log("  sudden changes (in how fast the body rises, tilts or fits, or a leg draws up), %d ticks:%s", flagged, flags);
		log("  every tick:%s", rows);
		log("  most a hoof was in the ground %.3f", sunk);
		stop(ctx, input);
	}

	/**
	 * The legs as they are drawn (after the animation pack and this mod posed them; {@link LegProbe}), in the test colours and
	 * shot up close against a white wall: on flat ground first (what the pack itself does, the baseline), then standing and
	 * walking on slopes, stairs and a single step. Per leg, each tick: the hoof's sole against the ground under it, the top of
	 * the leg against the body (a gap there is a leg come off), and whether any of the leg is in a block.
	 */
	private void legsAsDrawn(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		ctx.runOnClient(mc -> LegProbe.arm(true));
		final int fov = ctx.computeOnClient(mc -> mc.options.fov().get());
		ctx.runOnClient(mc -> mc.options.fov().set(55));
		try {
			legLane(ctx, input, world, "flat_walk", 3000, -60, false, false, -14.0, backdrop(3000));
			legLane(ctx, input, world, "flat_trot", 3020, -60, true, false, -20.0, backdrop(3020));
			legLane(ctx, input, world, "up_slope", 3040, -60, false, true, -16.0, slopeBuild(3040, true, false, true));
			legLane(ctx, input, world, "down_slope", 3060, -54, false, true, -16.0, slopeBuild(3060, false, false, true));
			legLane(ctx, input, world, "up_stairs", 3080, -60, false, true, -16.0, slopeBuild(3080, true, true, true));
			legLane(ctx, input, world, "down_stairs", 3100, -54, false, true, -16.0, slopeBuild(3100, false, true, true));
			// Stopping half way up the stairs, and with the front hooves up a single step.
			legLane(ctx, input, world, "stand_stairs", 3120, -60, false, true, -7.6, slopeBuild(3120, true, true, true));
			legLane(ctx, input, world, "stand_step", 3140, -60, false, true, -8.4,
				String.format(Locale.ROOT, "fill %d -60 -30 %d -60 -9 minecraft:stone", 3140, 3140), backdrop(3140));
			legLane(ctx, input, world, "stand_step_down", 3160, -59, false, true, -9.2,
				String.format(Locale.ROOT, "fill %d -60 -8 %d -60 12 minecraft:stone", 3160, 3160), backdrop(3160));
		} finally {
			ctx.runOnClient(mc -> {
				mc.options.fov().set(fov);
				LegProbe.arm(false);
			});
		}
	}

	/**
	 * Rides north from z=0.5 on a lane (built by {@code build}) until z passes {@code until}, then stands; samples the
	 * drawn legs every tick, and shoots them up close every few ticks and once standing. Only flat lanes' numbers are a
	 * baseline (nothing checked but that the legs stay on); the rest are checked.
	 */
	private void legLane(
		final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final String name, final int x, final double y,
		final boolean trot, final boolean checked, final double until, final String... build
	) {
		section("Legs as drawn: " + name.replace('_', ' '));
		lane(ctx, input, world, x + 0.5, y, "legs_" + name, build);
		sideCamera(world, x + 3.5, y - 1.2, 0.5, 90.0F);
		ctx.runOnClient(mc -> LegProbe.reset());
		final boolean firstLane = ctx.computeOnClient(mc -> LegProbe.tree == null);
		if (firstLane) {
			ctx.runOnClient(mc -> LegProbe.requestTree());
		}
		input.holdKey(o -> o.keyUp);
		if (trot) {
			ctx.waitTicks(2);
			input.pressKey(o -> o.keySprint);
		}
		final List<Double> front = new ArrayList<>();
		final List<Double> hind = new ArrayList<>();
		double sunk = Double.MAX_VALUE;
		double hipLow = Double.MAX_VALUE;
		double legIn = 0.0;
		int shots = 0;
		final StringBuilder trace = new StringBuilder();
		boolean riding = true;
		for (int i = 0; i < 400; i++) {
			ctx.waitTick();
			final double z = horseZ(ctx);
			if (riding && z < until) {
				input.releaseKey(o -> o.keyUp);
				riding = false;
			}
			final int still = ticksStill(ctx);
			if (!riding && still > 30) {
				break;
			}
			// Follow beside the horse, level with its legs.
			final double hx = horseX(ctx);
			final double hy = ctx.computeOnClient(mc -> mc.player.getVehicle().getY());
			// (An armour stand's eyes are 1.7775 above its feet: these are a little above the horse's back, 5 blocks off,
			// looking down at the legs so the treads under them show.)
			world.getServer().runCommand(String.format(Locale.ROOT, "tp @e[type=minecraft:armor_stand,tag=test_camera] %.2f %.2f %.2f 90 15", hx + 5.0, hy + 1.8 - 1.7775, z));
			final double[] legs = ctx.computeOnClient(mc -> new double[] {
				LegProbe.gap[0], LegProbe.gap[1], LegProbe.gap[2], LegProbe.gap[3], LegProbe.hip[0], LegProbe.hip[1], LegProbe.hip[2], LegProbe.hip[3],
				LegProbe.legInside[0], LegProbe.legInside[1], LegProbe.legInside[2], LegProbe.legInside[3], LegProbe.frames
			});
			final double[] ride = ctx.computeOnClient(mc -> {
				final RideState r = ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride();
				return new double[] {r.pitch(1.0F), r.foreLeg(1.0F), r.hindLeg(1.0F), mc.player.getVehicle().onGround() ? 1 : 0};
			});
			if (legs[12] == 0 || ride[3] == 0) {
				continue;
			}
			front.add(Math.min(legs[0], legs[1]));
			hind.add(Math.min(legs[2], legs[3]));
			for (int leg = 0; leg < 4; leg++) {
				sunk = Math.min(sunk, legs[leg]);
				if (!Double.isNaN(legs[4 + leg])) {
					hipLow = Math.min(hipLow, legs[4 + leg]);
				}
				legIn = Math.max(legIn, legs[8 + leg]);
			}
			trace.append(String.format(Locale.ROOT, "z%.2f y%.2f tilt%.1f lift%.2f/%.2f %s%s| ", z, hy, ride[0], ride[1], ride[2], ctx.computeOnClient(mc -> LegProbe.line()),
				legDebug(ctx)));
			if ((riding && i % 4 == 0 && shots < 10 || !riding && still == 20) && shots < 12) {
				ctx.waitTick();
				legShot(ctx, String.format(Locale.ROOT, "30_legs_%s_%02d", name, shots++));
			}
		}
		input.releaseKey(o -> o.keyUp);
		if (firstLane) {
			log("  the drawn model's parts (legs, and the body's top levels):%n%s", (Object) ctx.computeOnClient(mc -> LegProbe.tree));
		}
		log("  %s", trace);
		front.sort(null);
		hind.sort(null);
		final double frontTypical = percentile(front, 0.5);
		final double hindTypical = percentile(hind, 0.5);
		final double[] snap = ctx.computeOnClient(mc -> new double[] {LegProbe.soleSnapMost, LegProbe.snaps});
		log("  snaps: most a sole moved against the horse %.2f blocks, %d snaps %s", snap[0], (int) snap[1], ctx.computeOnClient(mc -> LegProbe.snapLog.toString()));
		log("  planted hoof of each pair off the ground (blocks): front median %.3f 90%% %.3f, hind median %.3f 90%% %.3f; most sunk %.3f; "
			+ "leg tops in the body at least %.3f; most a leg is in a block %.3f", frontTypical, percentile(front, 0.9), hindTypical, percentile(hind, 0.9), sunk,
			hipLow, legIn);
		// (Fresh Animations' own stride shows a pixel or two of a leg's top now and then: the flat lanes give the baseline.)
		check("legs stay on the body (least a leg's top is inside it, blocks)", hipLow, -0.13, 1.0);
		if (checked) {
			check("front hooves on the ground (median of the lower one, blocks)", frontTypical, -0.03, 0.06);
			check("hind hooves on the ground (median of the lower one, blocks)", hindTypical, -0.03, 0.06);
			check("front hooves on the ground nearly always (90th percentile, blocks)", percentile(front, 0.9), -0.03, 0.2);
			check("hind hooves on the ground nearly always (90th percentile, blocks)", percentile(hind, 0.9), -0.03, 0.2);
			// (A rocking hoof's edge dips about a pixel into the ground, as the animation draws it.)
			check("no hoof sunk into the ground (most, blocks)", sunk, -0.08, 1.0);
			check("no leg in a block (most, blocks)", legIn, 0.0, 0.06);
		}
	}

	/** What the legs asked for on the last frame drawn (see GroundLegs), for traces. */
	private static String legDebug(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			final RideState r = ((RideStateHolder) mc.player.getVehicle()).horsingaround$ride();
			final StringBuilder soles = new StringBuilder();
			for (int i = 0; i < 4; i++) {
				final double[] at = dev.horsingaround.client.render.GroundLegs.SOLES;
				final double[] t = dev.horsingaround.client.render.GroundLegs.TARGETS;
				soles.append(String.format(Locale.ROOT, " sole%d found %.2f %.2f %.2f drawn %.2f %.2f %.2f ground %.2f up %.1fpx back %.2f", i, at[i * 3],
					at[i * 3 + 1], at[i * 3 + 2], LegProbe.sole[i][0], LegProbe.sole[i][1], LegProbe.sole[i][2], t[i * 5 + 1], t[i * 5 + 3], t[i * 5 + 4]));
			}
			return soles + String.format(Locale.ROOT, " down %.1f", dev.horsingaround.client.render.GroundLegs.DOWN * Mth.RAD_TO_DEG) + String.format(Locale.ROOT, "[fit %.2f, wants %.2f %.2f %.2f %.2f, shifts %.2f %.2f %.2f %.2f, drawn up %.2f %.2f %.2f %.2f, pack lifts %.2f %.2f %.2f %.2f]",
				r.drawnFit, dev.horsingaround.client.render.GroundLegs.WANTED[0], dev.horsingaround.client.render.GroundLegs.WANTED[1],
				dev.horsingaround.client.render.GroundLegs.WANTED[2], dev.horsingaround.client.render.GroundLegs.WANTED[3], r.legShift[0], r.legShift[1], r.legShift[2],
				r.legShift[3], r.legRise[0], r.legRise[1], r.legRise[2], r.legRise[3], dev.horsingaround.client.render.GroundLegs.PACK_LIFTS[0],
				dev.horsingaround.client.render.GroundLegs.PACK_LIFTS[1], dev.horsingaround.client.render.GroundLegs.PACK_LIFTS[2],
				dev.horsingaround.client.render.GroundLegs.PACK_LIFTS[3]);
		});
	}

	private static double percentile(final List<Double> sorted, final double share) {
		return sorted.isEmpty() ? Double.NaN : sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * share)));
	}

	/** Ticks the ridden horse has stood still (0 while it moves). */
	private int stillTicks;
	private double stillX = Double.NaN;
	private double stillZ = Double.NaN;

	private int ticksStill(final ClientGameTestContext ctx) {
		final double x = horseX(ctx);
		final double z = horseZ(ctx);
		this.stillTicks = Math.abs(x - this.stillX) < 1.0E-3 && Math.abs(z - this.stillZ) < 1.0E-3 ? this.stillTicks + 1 : 0;
		this.stillX = x;
		this.stillZ = z;
		return this.stillTicks;
	}

	/** A close shot from the lane's camera, big enough to see each piece of each leg. */
	private void legShot(final ClientGameTestContext ctx, final String name) {
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
		log("  screenshot %s -> %s", name, ctx.takeScreenshot(TestScreenshotOptions.of("horsingaround_" + name).withSize(1280, 960)));
		if (found) {
			ctx.runOnClient(mc -> {
				mc.gui.hud.toggle();
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.setCameraEntity(mc.player);
			});
		}
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
		slopes(ctx, input, world);
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
	 * Up and down a slope of full blocks (one up for every block along) and a staircase of stair blocks, at a walk and a
	 * trot: the body tilts with the slope (nose up climbing, nose down going down), the hooves stay on the ground (the
	 * pair on higher ground folding), and the rider's head stays clear of the horse's. Side shots with the overlay on.
	 */
	private void slopes(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		int lane = 0;
		for (final boolean stairs : new boolean[] {false, true}) {
			for (final boolean up : new boolean[] {true, false}) {
				for (final boolean trot : new boolean[] {false, true}) {
					if (trot && stairs) {
						continue;
					}
					slope(ctx, input, world, 2600 + 20 * lane++, up, stairs, trot);
				}
			}
		}
	}

	private void slope(
		final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final int x, final boolean up, final boolean stairs,
		final boolean trot
	) {
		final String name = (up ? "up" : "down") + (stairs ? "_stairs" : "_slope") + (trot ? "_trot" : "_walk");
		section((trot ? "Trotting " : "Walking ") + (up ? "up " : "down ") + (stairs ? "a staircase of stair blocks" : "a slope of full blocks"));
		final int steps = 10;
		final double end = -5.0 - steps - 4.0;
		lane(ctx, input, world, x + 0.5, up ? -60 : -60 + steps, "slope_test_" + x, slopeBuild(x, up, stairs, false, steps));
		sideCamera(world, x + 6.5, -57.5, -8.0, 90.0F);
		input.holdKey(o -> o.keyUp);
		if (trot) {
			ctx.waitTicks(2);
			input.pressKey(o -> o.keySprint);
		}
		hitboxes(ctx, true);
		ctx.runOnClient(mc -> LegProbe.arm(true));
		final int drawnBefore = ctx.computeOnClient(mc -> dev.horsingaround.client.render.Legs.drawnFrames);
		final List<Double> gaps = new ArrayList<>();
		int sunkFrames = 0;
		int legFrames = 0;
		int phaseFrames = 0;
		int bodyFrames = 0;
		int frames = 0;
		// The drawn body's climb (blocks a tick), and how sharply that changes from tick to tick.
		double previousVisual = Double.NaN;
		double previousRise = Double.NaN;
		double sharpest = 0.0;
		double jerkSum = 0.0;
		int jerks = 0;
		double worstFloat = 0.0;
		double maxTilt = 0.0;
		double maxTiltRate = 0.0;
		double maxFold = 0.0;
		double headGap = Double.MAX_VALUE;
		// The longest a pair of hooves stays more than 0.2 above the ground (a hoof lifted to step is a tick or two).
		int foreUp = 0;
		int hindUp = 0;
		int longestUp = 0;
		float previousPitch = sample(ctx).pitch;
		int previousTick = horseTick(ctx);
		int shots = 0;
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 400 && horseZ(ctx) > end; i++) {
			ctx.waitTick();
			final double[] now = ctx.computeOnClient(mc -> {
				final AbstractHorse horse = (AbstractHorse) mc.player.getVehicle();
				final RideState r = ((RideStateHolder) horse).horsingaround$ride();
				// The legs as drawn (LegProbe): the lower hoof of each pair against the ground under it, and how far any hoof
				// is in the ground; how far the hooves are brought up onto their ground.
				return new double[] {
					horse.getZ(), horse.tickCount, r.pitch(1.0F), Math.min(LegProbe.gap[0], LegProbe.gap[1]), Math.min(LegProbe.gap[2], LegProbe.gap[3]),
					Math.max(r.legRise[0], r.legRise[1]), Math.max(r.legRise[2], r.legRise[3]), r.headGap, horse.onGround() ? 1 : 0,
					Math.min(Math.min(LegProbe.gap[0], LegProbe.gap[1]), Math.min(LegProbe.gap[2], LegProbe.gap[3])),
					Math.max(Math.max(LegProbe.legInside[0], LegProbe.legInside[1]), Math.max(LegProbe.legInside[2], LegProbe.legInside[3])),
					horse.getY() + r.heightOffset(1.0F), LegProbe.bodyInside
				};
			});
			final double z = now[0];
			// The climb, every tick (a tick skipped for a shot spreads over two).
			final int ticks = (int) now[1] - previousTick;
			if (z <= -2.0 && z >= end + 2.0 && ticks > 0) {
				final double rise = (now[11] - previousVisual) / ticks;
				if (!Double.isNaN(previousRise)) {
					final double jerk = Math.abs(rise - previousRise) / ticks;
					sharpest = Math.max(sharpest, jerk);
					jerkSum += jerk * jerk;
					jerks++;
				}
				previousRise = rise;
			}
			previousVisual = now[11];
			if (z > -3.0 || z < end + 2.5 || now[8] == 0.0) {
				if (ticks > 0) {
					previousTick = (int) now[1];
					previousPitch = (float) now[2];
				}
				if (z <= -3.0 && z >= end + 2.5) {
					// (In the air for a moment: just where it is and what the ride makes of it.)
					trace.append(String.format(Locale.ROOT, "air t%d z%.2f y%.2f vis%.3f tilt%.1f %s | ", (int) now[1], z, sample(ctx).y, now[11], now[2],
						ride(ctx, r -> r.debugGround())));
				}
				continue;
			}
			// On the slope: both pairs of hooves, how far off the ground they're drawn.
			gaps.add(Math.abs(now[3]));
			gaps.add(Math.abs(now[4]));
			frames++;
			if (now[9] < -0.1) {
				sunkFrames++;
			}
			if (now[10] > 0.1) {
				legFrames++;
			}
			if (now[9] < -0.05 || now[10] > 0.05) {
				phaseFrames++;
			}
			if (now[12] > 0.05) {
				bodyFrames++;
			}
			worstFloat = Math.max(worstFloat, Math.max(now[3], now[4]));
			foreUp = now[3] > 0.2 ? foreUp + 1 : 0;
			hindUp = now[4] > 0.2 ? hindUp + 1 : 0;
			longestUp = Math.max(longestUp, Math.max(foreUp, hindUp));
			maxTilt = Math.max(maxTilt, (up ? 1 : -1) * now[2]);
			maxFold = Math.max(maxFold, Math.max(Math.abs(now[5]), Math.abs(now[6])));
			headGap = Math.min(headGap, now[7]);
			if (now[1] > previousTick) {
				maxTiltRate = Math.max(maxTiltRate, Math.abs(now[2] - previousPitch) / (now[1] - previousTick));
			}
			previousPitch = (float) now[2];
			previousTick = (int) now[1];
			final String ground = ride(ctx, r -> r.debugGround());
			trace.append(String.format(Locale.ROOT, "t%d z%.2f y%.2f vis%.3f tilt%.1f front%+.2f hind%+.2f fold%.2f/%.2f %s %s%s | ", (int) now[1], z, sample(ctx).y, now[11],
				now[2], now[3], now[4], now[5], now[6], ground, ctx.computeOnClient(mc -> LegProbe.line()), legDebug(ctx)));
			if (i % 6 == 0 && shots < 8) {
				// From close beside the horse, level with it, so the legs show.
				final double cx = ctx.computeOnClient(mc -> mc.player.getVehicle().getX()) + 3.5;
				final double cy = ctx.computeOnClient(mc -> mc.player.getVehicle().getY()) - 0.8;
				final double cz = z;
				world.getServer().runCommand(String.format(Locale.ROOT, "tp @e[type=minecraft:armor_stand,tag=test_camera] %.2f %.2f %.2f 90 10", cx, cy, cz));
				ctx.waitTick();
				cameraShot(ctx, String.format(Locale.ROOT, "17_%s_%02d", name, shots++));
			}
		}
		hitboxes(ctx, false);
		final double[] snap = ctx.computeOnClient(mc -> new double[] {LegProbe.soleSnapMost, LegProbe.snaps});
		ctx.runOnClient(mc -> LegProbe.arm(false));
		input.releaseKey(o -> o.keyUp);
		log("  %s", trace);
		gaps.sort(null);
		final double typical = gaps.isEmpty() ? 1.0 : gaps.get(gaps.size() / 2);
		final double most = gaps.isEmpty() ? 1.0 : gaps.get((int) (gaps.size() * 0.9));
		check("over the slope", horseZ(ctx) < end + 0.1);
		final double share = frames == 0 ? 1.0 : 1.0 / frames;
		log("  climb: sharpest change in the drawn body's rise %.3f blocks/tick a tick, typical (rms) %.3f; frames: a leg in a block %d of %d, the body %d",
			sharpest, jerks == 0 ? 0.0 : Math.sqrt(jerkSum / jerks), phaseFrames, frames, bodyFrames);
		log("  snaps: most a sole moved against the horse %.2f blocks, %d snaps", snap[0], (int) snap[1]);
		check("tilts with the slope, nose " + (up ? "up" : "down") + " (max tilt, deg)", maxTilt, stairs ? 15.0 : 20.0, 40.5);
		check("smoothly (max tilt change per tick, deg)", maxTiltRate, 0.2, 10.0);
		check("hooves on the ground: typically (blocks off it, median)", typical, 0.0, 0.12);
		// (Stepping from block to block, a hoof is lifted or set down for a tick or two.)
		// (On a slope of full blocks, a block up for every block along, the risers are taller than a leg: the horse
		// scrambles, a hoof off the ground on its way up or reaching down for the next block. Off the ground is fine; in it
		// isn't: a known limit, see research.md. Going down, a leg can't reach down and each end of the body falls under
		// gravity once its hooves step off, so a hoof hangs a little longer.)
		final boolean fullBlocks = !stairs;
		check("hooves on the ground: nearly always (blocks off it, 90th percentile)", most, 0.0, fullBlocks ? 0.65 : up ? 0.25 : 0.3);
		check("a hoof is hardly ever in the ground (share of frames one is more than 0.1 in)", frames == 0 ? 1.0 : (double) sunkFrames / frames, 0.0, 0.1);
		check("a leg is hardly ever in the ground (share of frames one is more than 0.1 in)", frames == 0 ? 1.0 : (double) legFrames / frames, 0.0, 0.1);
		check("a hoof lifted to step comes down again (longest more than 0.2 above the ground, ticks)", longestUp, 0, fullBlocks ? 6 : up ? 3 : 4);
		check("never far above it, reaching down for the next step at most (most a hoof floats, blocks)", worstFloat, 0.0, fullBlocks ? (up ? 1.0 : 1.5) : 0.8);
		check("hooves come up onto the higher ground (most, blocks)", maxFold, 0.05, RideTuning.LEG_RISE_MAX + 0.01);
		check("legs draw up onto higher ground on the way (frames)", ctx.computeOnClient(mc -> dev.horsingaround.client.render.Legs.drawnFrames) - drawnBefore > 0);
		check("rider's head clear of the horse's (closest, centre to centre, blocks)", headGap, 0.45, 10.0);
		stop(ctx, input);
	}

	/**
	 * Riding north from z=0.5: six steps of a block from z=-5, the low ground at -60 (tops at -59), the high at -54. One block
	 * wide, so a camera beside it sees the legs on the treads; with a white wall behind it for close shots if {@code backdrop}.
	 */
	private static String[] slopeBuild(final int x, final boolean up, final boolean stairs, final boolean backdrop) {
		return slopeBuild(x, up, stairs, backdrop, 6);
	}

	/** As {@link #slopeBuild(int, boolean, boolean, boolean)}, with {@code steps} steps: the high ground at -60 + steps. */
	private static String[] slopeBuild(final int x, final boolean up, final boolean stairs, final boolean backdrop, final int steps) {
		final List<String> build = new ArrayList<>();
		if (up) {
			build.add(String.format(Locale.ROOT, "fill %d -60 %d %d %d %d minecraft:stone", x, -5 - steps - 14, x, -61 + steps, -5 - steps));
		} else {
			build.add(String.format(Locale.ROOT, "fill %d -60 -4 %d %d 12 minecraft:stone", x, x, -61 + steps));
		}
		for (int i = 0; i < steps; i++) {
			final int z = -5 - i;
			// Top of this step: climbing, one more each block; going down, one less.
			final int top = up ? -59 + i : -61 + steps - i;
			if (stairs) {
				if (top - 2 >= -60) {
					build.add(String.format(Locale.ROOT, "fill %d -60 %d %d %d %d minecraft:stone", x, z, x, top - 2, z));
				}
				build.add(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone_stairs[facing=%s]", x, top - 1, z, x, top - 1, z, up ? "north" : "south"));
			} else if (top - 1 >= -60) {
				build.add(String.format(Locale.ROOT, "fill %d -60 %d %d %d %d minecraft:stone", x, z, x, top - 1, z));
			}
		}
		if (backdrop) {
			build.add(backdrop(x));
		}
		return build.toArray(String[]::new);
	}

	/** A white wall four blocks west of a lane, behind the horse as the close camera (east of it) sees it. */
	private static String backdrop(final int x) {
		return String.format(Locale.ROOT, "fill %d -60 -30 %d -50 12 minecraft:white_concrete", x - 4, x - 4);
	}

	/**
	 * A single 1-block step up (or down) at a walk or trot: the forehand goes first, the body tilting with the step and the
	 * front legs folding up onto it (or, coming down, the hind legs tucking under), then the hindquarters follow. Side shots with
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
		ctx.runOnClient(mc -> LegProbe.arm(true));
		double bodyIn = 0.0;
		double hoofIn = 0.0;
		double legIn = 0.0;
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
			final double[] probe = ctx.computeOnClient(mc -> new double[] {
				LegProbe.bodyInside, Math.min(Math.min(LegProbe.gap[0], LegProbe.gap[1]), Math.min(LegProbe.gap[2], LegProbe.gap[3])),
				Math.max(Math.max(LegProbe.legInside[0], LegProbe.legInside[1]), Math.max(LegProbe.legInside[2], LegProbe.legInside[3])), LegProbe.frames
			});
			if (probe[3] > 0) {
				bodyIn = Math.max(bodyIn, probe[0]);
				hoofIn = Math.min(hoofIn, probe[1]);
				legIn = Math.max(legIn, probe[2]);
			}
			maxTilt = Math.max(maxTilt, (float) (s.pitch * step));
			maxFore = Math.max(maxFore, Math.abs(legs[2]));
			maxHind = Math.max(maxHind, Math.abs(legs[3]));
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
		ctx.runOnClient(mc -> LegProbe.arm(false));
		input.releaseKey(o -> o.keyUp);
		final String ended = ctx.computeOnClient(mc -> String.format(Locale.ROOT, "%.2f %.2f %.2f, camera %s, gait %d",
			mc.player.getVehicle().getX(), mc.player.getVehicle().getY(), mc.player.getVehicle().getZ(), mc.getCameraEntity().getType().toShortString(),
			((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().gait));
		log("  ended at %s", ended);
		ticksUntilStopped(ctx, 60);
		ctx.waitTicks(10);
		check("on the other level (blocks)", (sample(ctx).y - low) * step, 0.95, 1.05);
		check("the forehand goes first (ticks before the hindquarters)", hindHalf - foreHalf, trot ? 2 : 4, trot ? 8 : 12);
		check("tilts with the step (max tilt, deg)", maxTilt, trot ? 8.0 : 15.0, 40.0);
		check("smoothly, never snaps (max tilt change per tick, deg)", maxTiltRate, 0.2, 10.0);
		check("two beats: forehand three quarters " + (up ? "up" : "down") + ", hindquarters still behind (their share of the step)", forehandFirst, 0.0, trot ? 0.5 : 0.35);
		check("hooves come up onto the step (most, blocks)", Math.max(maxFore, maxHind), 0.05, 0.5);
		check("smooth (max rendered height change per tick, blocks)", maxVisualStep, 0.03, trot ? 0.2 : 0.17);
		check("level again after (pitch deg)", Math.abs(sample(ctx).pitch), 0.0, 1.5);
		check("the chest clears the step (most the body is in a block, blocks)", bodyIn, 0.0, 0.05);
		check("no hoof in the step (most, blocks; a rocking hoof's edge dips a pixel)", hoofIn, -0.08, 0.0);
		check("no leg in the step (most, blocks)", legIn, 0.0, 0.06);
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
		final StringBuilder trace = new StringBuilder();
		for (int i = 0; i < 400 && horseZ(ctx) > -36.0; i++) {
			ctx.waitTick();
			final double z = horseZ(ctx);
			if (i % 5 == 0 || z < -27.0 && z > -31.0) {
				trace.append(String.format(Locale.ROOT, "z%.2f x%.3f yaw%.1f view%.1f side%.2f blocked%.2f slips%d keys%s | ", z, horseX(ctx), sample(ctx).horseYaw,
					sample(ctx).playerYaw, ride(ctx, r -> r.sidestep()), ride(ctx, r -> r.blocked), ride(ctx, r -> r.slips),
					ctx.computeOnClient(mc -> (mc.options.keyLeft.isDown() ? "A" : "") + (mc.options.keyRight.isDown() ? "D" : "") + (mc.options.keyUp.isDown() ? "W" : ""))));
			}
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
		if (horseZ(ctx) >= -35.0 || Math.abs(horseX(ctx) - lane) > 0.4) {
			log("  path: %s", trace);
		}
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
			boolean changed = false;
			for (final String id : packs.getAvailableIds()) {
				// (-PvanillaModel: the plain horse model, no animation pack.)
				if (id.contains("FreshAnimations") && !Boolean.getBoolean("horsingaround.vanillaModel")) {
					packs.addPack(id);
					changed = true;
				}
			}
			// The test colours go on top (see installDebugPack in build.gradle).
			for (final String id : packs.getAvailableIds()) {
				if (id.contains("horsingaround-debug") && !Boolean.getBoolean("horsingaround.plainTextures")) {
					packs.addPack(id);
					changed = true;
				}
			}
			if (!changed) {
				return null;
			}
			mc.options.updateResourcePacks(packs);
			return mc.reloadResourcePacks();
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
		// (The plans of the lane before, when they are being noted.)
		if (dev.horsingaround.ride.Awareness.planLog.length() > 0) {
			log("  plans:%s", dev.horsingaround.ride.Awareness.planLog);
			dev.horsingaround.ride.Awareness.planLog.setLength(0);
		}
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
