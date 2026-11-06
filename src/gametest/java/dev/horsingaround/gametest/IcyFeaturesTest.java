package dev.horsingaround.gametest;

import com.mojang.authlib.GameProfile;
import dev.horsingaround.HorsingAround;
import dev.horsingaround.ride.HorseConfig;
import dev.horsingaround.client.render.IcyCartDrawing;
import dev.horsingaround.ride.CartTrail;
import dev.horsingaround.ride.IcyCompat;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.ride.RideTuning;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

/**
 * Icy's Better Horses' features on this mod's riding (the icys compat pack only): its cart, plough and wagon, a second
 * rider, the Glidewing Harness, rearing on command, horseshoes, the medkit, mounted mining and the dismount side. Each
 * horse is one of Icy's breeds, kitted out the way a player would (an upgraded saddle, gear in its slots), ridden with
 * simulated keys. Writes {@code horsingaround-icy-report.txt}.
 *
 * <p>Run with {@code ./gradlew runCompatIcys -PcompatTests=icy}.
 */
public final class IcyFeaturesTest implements FabricClientGameTest {
	/** Superflat: grass at y -61, so feet at -60. */
	private static final int GROUND = -60;
	private static final String ICY = IcyCompat.MOD_ID + ":";
	private static final String SADDLE = "Tame:1b,equipment:{saddle:{id:\"minecraft:saddle\",count:1}}";
	private static final String UPGRADED = "Tame:1b,equipment:{saddle:{id:\"" + ICY + "upgraded_saddle\",count:1}}";
	private static final double GALLOP = RideTuning.TERMINAL_VELOCITY_FACTOR * RideTuning.GAIT_SPEED[RideTuning.GALLOP];
	private static final double TROT = RideTuning.TERMINAL_VELOCITY_FACTOR * RideTuning.GAIT_SPEED[RideTuning.TROT];

	private static ServerPlayer pillion;
	private int rearShots;
	/** How far the seat was behind the measured saddle point at the top of the last rear, along the horse, blocks. */
	private float rearSeatBehind;
	private final List<String> report = new ArrayList<>();
	private int failures;
	private int lane;

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!IcyCompat.LOADED || !System.getProperty("horsingaround.tests", "").contains("icy")) {
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
			final TestInput input = ctx.getInput();
			ctx.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				// (Command feedback would cover the shots.)
				mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
			});

			// (-PcompatSections=<names> runs only some, while working on them; all of them by default.)
			final List<String> only = List.of(System.getProperty("horsingaround.sections", "").split(","));
			final boolean all = only.size() == 1 && only.getFirst().isEmpty();
			if (all || only.contains("room")) {
				cartClearance(ctx, input, world);
			}
			if (all || only.contains("cart")) {
				cart(ctx, input, world);
			}
			if (all || only.contains("hill")) {
				cartHill(ctx, input, world, "american_paint_horse");
				cartHill(ctx, input, world, "belgian_horse");
			}
			if (all || only.contains("steep")) {
				cartSteep(ctx, input, world, "american_paint_horse");
				cartSteep(ctx, input, world, "belgian_horse");
			}
			if (all || only.contains("jitter")) {
				cartJitter(ctx, input, world, "american_paint_horse");
				cartJitter(ctx, input, world, "belgian_horse");
			}
			if (all || only.contains("fast")) {
				cartFast(ctx, input, world, "american_paint_horse");
				cartFast(ctx, input, world, "belgian_horse");
			}
			if (all || only.contains("tight")) {
				cartTight(ctx, input, world, "american_paint_horse");
				cartTight(ctx, input, world, "belgian_horse");
			}
			if (all || only.contains("drop")) {
				cartDrop(ctx, input, world, "american_paint_horse");
				cartDrop(ctx, input, world, "belgian_horse");
			}
			if (all || only.contains("rough")) {
				cartRough(ctx, input, world, "american_paint_horse");
				cartRough(ctx, input, world, "belgian_horse");
			}
			if (all || only.contains("ledge")) {
				cartLedge(ctx, input, world, "american_paint_horse");
				cartLedge(ctx, input, world, "belgian_horse");
			}
			if (all || only.contains("jump")) {
				cartJump(ctx, input, world, "american_paint_horse");
				cartJump(ctx, input, world, "belgian_horse");
			}
			if (all || only.contains("lean")) {
				cartLean(ctx, world);
			}
			if (all || only.contains("reverse")) {
				cartBackIntoWall(ctx, input, world);
				cartReverse(ctx, input, world, "american_paint_horse");
				cartReverse(ctx, input, world, "belgian_horse");
			}
			if (all) {
				plough(ctx, input, world);
				wagon(ctx, input, world);
				secondRider(ctx, input, world);
				glider(ctx, input, world);
				rear(ctx, input, world);
				horseshoes(ctx, input, world);
				medkit(ctx, input, world);
				chargeToggle(ctx, input, world);
				comforts(ctx, input, world);
			}

			section("Log");
			final List<String> problems = watch.matching("horsingaround", "Exception", "moved wrongly", "moved too quickly", "was expected to be controlling");
			problems.forEach(line -> log("  %s", line));
			check("no server corrections, exceptions or mod warnings with Icy's gear", problems.isEmpty());
			completed = true;
		} finally {
			ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.FULL));
			if (!completed) {
				this.failures++;
				log("INCOMPLETE: the run stopped early (see the log for the exception)");
			}
			writeReport();
		}
		TestSummary.failed(this.failures, "horsingaround-icy-report.txt");
	}

	// ---- The cart, the plough and the wagon ----

	/** On every breed, the rider's knees on the bench and the cart clear the horse's hindquarters. */
	private void cartClearance(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Icy's carts behind every breed: room behind the horse");
		double closest = Double.MAX_VALUE;
		for (final String breed : new String[] {
			"american_paint_horse", "andalusian_horse", "arabian_horse", "belgian_horse", "clydesdale_horse", "friesian_horse", "haflinger_horse", "icelandic_horse",
			"mustang_horse", "percheron_horse", "quarter_horse", "shire_horse", "thoroughbred_horse", "appaloosa_horse", "morgan_horse"
		}) {
			final int x = nextLane();
			final int id;
			try {
				id = summon(ctx, world, breed, UPGRADED, x, GROUND);
			} catch (final RuntimeException e) {
				log("  (no %s)", breed);
				continue;
			}
			onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
			ctx.waitTicks(10);
			board(ctx, world, x);
			ctx.runOnClient(mc -> LegProbe.arm(true));
			ctx.waitTicks(5);
			final double[] room = ctx.computeOnClient(mc -> new double[] {LegProbe.rump, -forwardOf(mc.player.getVehicle(), mc.player), mc.player.getVehicle().getBbWidth(), mc.player.getVehicle().getBbHeight(), IcyCompat.cartFrontAxle((Entity) call(mc.player.getVehicle(), "bh_getCartEntity"))});
			ctx.runOnClient(mc -> LegProbe.arm(false));
			log("  (%s: body ends %.2f behind its middle, the seat %.2f; %.2f wide, %.2f tall)", breed, room[0], room[1], room[2], room[3]);
			closest = Math.min(closest, room[1] - room[0]);
			sideScreenshot(ctx, "icy_room_" + breed);
			dismount(ctx, input);
		}
		// (A seated rider's knees reach about half a block ahead of the seat.)
		check("on every breed the rider's seat is well clear of the horse's hindquarters (blocks, closest)", closest, 1.1, 2.0);
	}

	/** A cart in an American Paint's harness slot: it rolls behind at this mod's gaits, slowed by Icy's drag. */
	private void cart(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Icy's cart behind a horse, on this mod's riding");
		final int x = nextLane();
		final int id = summon(ctx, world, "american_paint_horse", UPGRADED, x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		final String cartId = world.getServer().computeOnServer(server -> cartUuid(horse(server, id)));
		check("a cart is hitched behind it", !"none".equals(cartId));
		check("the cart drags at its speed (Icy's -30%)", world.getServer().computeOnServer(server -> drag(horse(server, id))), -0.31, -0.29);
		board(ctx, world, x);
		check("this mod rides it", managed(ctx));
		check("the rider sits on the cart's bench (blocks behind the horse's middle)", -ctx.computeOnClient(mc -> forwardOf(mc.player.getVehicle(), mc.player)), 1.0, 2.0);
		sideScreenshot(ctx, "icy_cart_standing");
		final double gallop = gallopFor(ctx);
		check("pulling the cart, it's slower to get going (ticks to a gallop; about 47 alone)", ticksToGallop(ctx, input), 65.0, 130.0);
		ctx.waitTicks(20);
		check("gallops at its own top speed, the drag included (blocks/tick)", averageSpeed(ctx, 10), gallop * 0.85, gallop * 1.15);
		screenshot(ctx, "icy_cart_gallop");
		sideScreenshot(ctx, "icy_cart_gallop_side");
		bedStaysStill(ctx);
		// (Alone it turns as tight as about 6 blocks at a gallop; in the shafts it can bend 20 degrees before the cart holds it.)
		followThroughTurn(ctx, input, "icy_cart", 6.5);
		check("the same cart all along (never dropped and put back)", cartId.equals(world.getServer().computeOnServer(server -> cartUuid(horse(server, id)))));
		stop(ctx, input);
		dismount(ctx, input);
	}

	/**
	 * Up and down a hill of 1-block steps at a canter, then on along the flat at a gallop, watching the cart as drawn: it
	 * rises and tilts smoothly, not a block at a time, and at a gallop on the flat it hardly bounces.
	 */
	private void cartHill(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final String breed) {
		section("Icy's cart up and down a hill, behind a " + breed);
		final int x = nextLane();
		// Steps up every 3 blocks for 5 blocks, a flat top, and down again the same way (the ride heads north, -z).
		for (int step = 0; step < 5; step++) {
			world.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:grass_block", x - 3, GROUND + step, -12 - 3 * step, x + 3, GROUND + step, -60 + 3 * step));
		}
		final int id = summon(ctx, world, breed, UPGRADED, x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		board(ctx, world, x);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		spurs(ctx, input, 2);
		// (A wagon on its own wheels: where the hitch is, ahead of its axles, it moves further than they do.)
		final double riseMost = "belgian_horse".equals(breed) ? 0.45 : 0.3;
		final Kicks kicks = new Kicks();
		double wheelHigh = 0.0;
		double wheelLow = 0.0;
		double riseJump = 0.0;
		double tiltJump = 0.0;
		double slopeTilt = 0.0;
		int slopeTicks = 0;
		double flatLow = Double.MAX_VALUE;
		double flatHigh = -Double.MAX_VALUE;
		double[] was = null;
		for (int i = 0; i < 200; i++) {
			if (i == 120) {
				input.pressKey(o -> o.keySprint);
			}
			ctx.waitTick();
			// The cart as drawn: its height and tilt, and where the horse is.
			final double[] now = ctx.computeOnClient(mc -> {
				final Entity horse = mc.player.getVehicle();
				final Object cart = horse == null ? null : call(horse, "bh_getCartEntity");
				if (cart == null) {
					return null;
				}
				return new double[] {((net.minecraft.world.phys.Vec3) call(cart, "gluedRenderPosition", 1.0F)).y, (float) call(cart, "renderTilt", 1.0F), horse.getZ(), horse.getY()};
			});
			if (now == null) {
				continue;
			}
			final double[] gaps = ctx.computeOnClient(IcyFeaturesTest::wheelGaps);
			for (final double gap : gaps) {
				wheelLow = Math.min(wheelLow, gap);
			}
			wheelHigh = Math.max(wheelHigh, ctx.computeOnClient(IcyFeaturesTest::offRamps));
			kicks.add(now[0], now[1], ctx.computeOnClient(mc -> IcyCompat.cartLean(mc.player.getVehicle()).horsingaround$roll(1.0F)));
			// (Halfway up the slope, steps of 1 in 3: about 18 degrees.)
			if (now[2] < -15.0 && now[2] > -21.0) {
				slopeTilt += now[1];
				slopeTicks++;
			}
			if (was != null) {
				riseJump = Math.max(riseJump, Math.abs(now[0] - was[0]));
				tiltJump = Math.max(tiltJump, Math.abs(now[1] - was[1]));
			}
			// (On past the last step with the whole cart.)
			if (now[2] < -72.0) {
				flatLow = Math.min(flatLow, now[0]);
				flatHigh = Math.max(flatHigh, now[0]);
			}
			was = now;
			if (i == 45 || i == 95) {
				sideScreenshot(ctx, "icy_cart_hill_" + breed + "_" + i);
				// (A shot can take a tick of its own: the next change isn't one tick's.)
				was = null;
				kicks.skip();
			}
		}
		log("  (wheels off the ground, most: %.2f above, %.2f in)", wheelHigh, -wheelLow);
		log("  (kicks up the hill, change in rate from tick to tick, most: height %.3f, tilt %.2f, lean %.2f)", kicks.most[0], kicks.most[1], kicks.most[2]);
		// (How suddenly it changes how fast it moves: most of all as an axle comes down and is caught by its suspension.)
		check("it has weight: its tilt gathers and settles (change in degrees a tick, from tick to tick, most)", kicks.most[1], 0.0, 5.0);
		check("...and its height (blocks)", kicks.most[0], 0.0, 0.2);
		check("up and down the hill it rises smoothly (blocks a tick, most)", riseJump, 0.0, riseMost);
		check("...its wheels on the ground and its ramps (blocks off them, most)", wheelHigh, 0.0, 0.6);
		check("...and not sunk far into it (blocks)", -wheelLow, 0.0, 0.6);
		check("...and tilts smoothly (degrees a tick, most)", tiltJump, 0.0, 7.0);
		// (A little steeper than the slope: the horse's feet climb each step before its middle, where the hitch is, is over it.)
		check("...going up with the slope (degrees, on average; a slope of 1 in 3 is 18)", slopeTilt / Math.max(slopeTicks, 1), 14.0, 25.0);
		check("galloping on the flat it hardly bounces (blocks, top to bottom)", flatHigh - flatLow, 0.0, 0.08);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/**
	 * Down a steep hill, a step of a block for each block along with drops of 2 and 3 in it, and back up: going down, the horse
	 * well below the cart, it doesn't tip up on end (user, 2026-11-06). It stays on its wheels, tilted as far as a cart tilts,
	 * and the shafts reach down to the horse.
	 */
	private void cartSteep(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final String breed) {
		section("Icy's cart down a steep hill, behind a " + breed);
		final int x = nextLane();
		final int top = GROUND + 12;
		world.getServer().runOnServer(server -> {
			final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int bx = x - 8; bx <= x + 8; bx++) {
				for (int bz = -70; bz <= 12; bz++) {
					// (Flat on top, down a block a block, a drop of 3, a block a block again, a drop of 2, and on along the bottom.)
					final int along = -bz;
					final int high = along < 8 ? 12 : along < 12 ? 12 - (along - 7) : along < 16 ? 5 : along < 19 ? 5 - (along - 15) : 2;
					for (int up = 0; up < high; up++) {
						server.overworld().setBlockAndUpdate(pos.set(bx, GROUND + up, bz), Blocks.GRASS_BLOCK.defaultBlockState());
					}
				}
			}
		});
		final int id = summon(ctx, world, breed, UPGRADED + ",NoAI:1b", x, top);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		board(ctx, world, x, top);
		input.lookAt(180.0F, 15.0F);
		input.holdKey(o -> o.keyUp);
		ctx.runOnClient(mc -> IcyCartDrawing.shaftMiss = 0.0);
		double noseDown = 0.0;
		double noseUp = 0.0;
		double wheelIn = 0.0;
		double bedIn = 0.0;
		double below = 0.0;
		int shots = 0;
		double fell = 0.0;
		for (int i = 0; i < 260; i++) {
			ctx.waitTick();
			final double[] now = ctx.computeOnClient(mc -> {
				final Entity horse = mc.player.getVehicle();
				final Object cart = horse == null ? null : call(horse, "bh_getCartEntity");
				return cart == null ? null : new double[] {(float) call(cart, "renderTilt", 1.0F), horse.getY(), ((net.minecraft.world.phys.Vec3) call(cart, "gluedRenderPosition", 1.0F)).y};
			});
			if (now == null) {
				continue;
			}
			noseDown = Math.max(noseDown, -now[0]);
			noseUp = Math.max(noseUp, now[0]);
			below = Math.max(below, now[2] - now[1]);
			fell = Math.max(fell, top - now[1]);
			for (final double gap : ctx.computeOnClient(IcyFeaturesTest::wheelGaps)) {
				wheelIn = Math.max(wheelIn, -gap);
			}
			for (final double gap : ctx.computeOnClient(IcyFeaturesTest::bedGaps)) {
				bedIn = Math.max(bedIn, -gap);
			}
			if (shots < 3 && fell > 2.0 + shots * 3.0) {
				sideScreenshot(ctx, "icy_steep_" + breed + "_" + shots);
				screenshot(ctx, "icy_steep_back_" + breed + "_" + shots);
				shots++;
			}
		}
		log("  (went down %.1f; tilted %.1f down and %.1f up at most; the cart up to %.2f above the horse; in the ground: wheels %.2f, bed %.2f; shafts short %.2f)", fell, noseDown, noseUp, below, wheelIn, bedIn, ctx.computeOnClient(mc -> IcyCartDrawing.shaftMiss));
		check("it goes down the hill (blocks down)", fell, 5.0, 14.0);
		check("it doesn't tip up on end going down (degrees nose down, most)", noseDown, 0.0, 43.0);
		check("...its wheels stay out of the ground (blocks in, most)", wheelIn, 0.0, 0.4);
		check("...and its bed (blocks in, most)", bedIn, 0.0, 0.4);
		// (Off the 3-block drop, the horse nearly 4 below the cart for a moment: further than its shafts stretch.)
		check("...and the shafts on the horse (blocks short of it, most)", ctx.computeOnClient(mc -> IcyCartDrawing.shaftMiss), 0.0, 0.8);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/**
	 * At a steady pace over easy rolling ground: nothing about the cart or its rider jumps between one tick and the next (user,
	 * 2026-11-06: a bit jittery), and its tilt, lean and height change gradually. Each value as drawn at the end of one tick must
	 * be where it is drawn at the start of the next.
	 */
	private void cartJitter(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final String breed) {
		section("Icy's cart at a steady pace over easy ground, behind a " + breed);
		final int x = nextLane();
		world.getServer().runOnServer(server -> {
			final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int bx = x - 8; bx <= x + 8; bx++) {
				for (int bz = -110; bz <= -6; bz++) {
					final int across = bx - x;
					final int high = Mth.floor(1.2F + 0.9F * Mth.sin(bz * 0.13F + across * 0.05F) + 0.6F * Mth.sin(bz * 0.31F - across * 0.11F));
					for (int up = 0; up < high; up++) {
						server.overworld().setBlockAndUpdate(pos.set(bx, GROUND + up, bz), Blocks.GRASS_BLOCK.defaultBlockState());
					}
				}
			}
		});
		final int id = summon(ctx, world, breed, UPGRADED + ",NoAI:1b", x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		board(ctx, world, x);
		input.lookAt(180.0F, 15.0F);
		input.holdKey(o -> o.keyUp);
		spurs(ctx, input, 2);
		final Kicks kicks = new Kicks();
		double seatJump = 0.0;
		double cartJump = 0.0;
		double[] was = null;
		for (int i = 0; i < 220; i++) {
			ctx.waitTick();
			final double[] now = ctx.computeOnClient(mc -> {
				final Entity horse = mc.player.getVehicle();
				final Object cart = horse == null ? null : call(horse, "bh_getCartEntity");
				if (cart == null) {
					return null;
				}
				final net.minecraft.world.phys.Vec3 seat0 = (net.minecraft.world.phys.Vec3) call(cart, "renderSeat", mc.player, 0.0F);
				final net.minecraft.world.phys.Vec3 seat1 = (net.minecraft.world.phys.Vec3) call(cart, "renderSeat", mc.player, 1.0F);
				final net.minecraft.world.phys.Vec3 cart0 = (net.minecraft.world.phys.Vec3) call(cart, "gluedRenderPosition", 0.0F);
				final net.minecraft.world.phys.Vec3 cart1 = (net.minecraft.world.phys.Vec3) call(cart, "gluedRenderPosition", 1.0F);
				return new double[] {
					seat0.x, seat0.y, seat0.z, seat1.x, seat1.y, seat1.z, cart0.x, cart0.y, cart0.z, cart1.x, cart1.y, cart1.z,
					(float) call(cart, "renderTilt", 1.0F), ((CartTrail.Lean) cart).horsingaround$roll(1.0F)
				};
			});
			if (now == null) {
				continue;
			}
			if (was != null) {
				seatJump = Math.max(seatJump, Math.sqrt(Mth.square(now[0] - was[3]) + Mth.square(now[1] - was[4]) + Mth.square(now[2] - was[5])));
				cartJump = Math.max(cartJump, Math.sqrt(Mth.square(now[6] - was[9]) + Mth.square(now[7] - was[10]) + Mth.square(now[8] - was[11])));
			}
			kicks.add(now[10], now[12], now[13]);
			was = now;
		}
		log("  (jumps between ticks: seat %.3f, cart %.3f; kicks: height %.3f, tilt %.2f, lean %.2f)", seatJump, cartJump, kicks.most[0], kicks.most[1], kicks.most[2]);
		check("the rider doesn't jump between ticks (blocks, most)", seatJump, 0.0, 0.01);
		check("...nor the cart", cartJump, 0.0, 0.01);
		// (Most of all as an axle comes down off a step and is caught by its suspension.)
		check("its tilt changes gradually (degrees a tick, from tick to tick, most)", kicks.most[1], 0.0, 6.0);
		check("...its lean", kicks.most[2], 0.0, 4.0);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/**
	 * Flat out up and over hills of 1-block steps: the cart rides over them with its weight, not hopping at every step (user,
	 * 2026-11-06: going fast up and over hills is really jerky). Reads how suddenly the rider's seat and the cart's tilt change
	 * how fast they are moving, at worst and typically.
	 */
	private void cartFast(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final String breed) {
		section("Icy's cart flat out over hills, behind a " + breed);
		final int x = nextLane();
		world.getServer().runOnServer(server -> {
			final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int bx = x - 8; bx <= x + 8; bx++) {
				for (int bz = -170; bz <= -12; bz++) {
					// (Long hills of 1 to 3 blocks, a step every few blocks, on a flat run-up.)
					final int high = Mth.floor(1.6F + 1.6F * Mth.sin(bz * 0.11F) + 0.35F * Mth.sin(bz * 0.37F + (bx - x) * 0.07F));
					for (int up = 0; up < high; up++) {
						server.overworld().setBlockAndUpdate(pos.set(bx, GROUND + up, bz), Blocks.GRASS_BLOCK.defaultBlockState());
					}
				}
			}
		});
		final int id = summon(ctx, world, breed, UPGRADED + ",NoAI:1b", x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		board(ctx, world, x);
		input.lookAt(180.0F, 15.0F);
		input.holdKey(o -> o.keyUp);
		spurs(ctx, input, 3);
		final Kicks kicks = new Kicks();
		double wheelIn = 0.0;
		double farthest = 0.0;
		for (int i = 0; i < 300; i++) {
			ctx.waitTick();
			final double[] now = ctx.computeOnClient(mc -> {
				final Entity horse = mc.player.getVehicle();
				final Object cart = horse == null ? null : call(horse, "bh_getCartEntity");
				return cart == null ? null : new double[] {((net.minecraft.world.phys.Vec3) call(cart, "renderSeat", mc.player, 1.0F)).y, (float) call(cart, "renderTilt", 1.0F), ((CartTrail.Lean) cart).horsingaround$roll(1.0F), horse.getZ()};
			});
			if (now == null || now[3] > -12.0) {
				kicks.skip();
				continue;
			}
			farthest = Math.min(farthest, now[3]);
			kicks.add(now[0], now[1], now[2]);
			for (final double gap : ctx.computeOnClient(IcyFeaturesTest::wheelGaps)) {
				wheelIn = Math.max(wheelIn, -gap);
			}
		}
		log("  (reached z %.1f; seat kicks %.3f at worst, %.3f typically; tilt kicks %.2f at worst, %.2f typically; lean %.2f; wheels in the ground %.2f)", farthest, kicks.most[0], kicks.typical(0), kicks.most[1], kicks.typical(1), kicks.most[2], wheelIn);
		check("it gets over the hills (blocks along)", -farthest, 50.0, 200.0);
		// (Before the body rode on the averaged ground, the wagon: 0.049 and 0.21 for the seat, 1.31 and 3.98 for the tilt.)
		check("the rider isn't jolted at every step (blocks a tick, from tick to tick, typically)", kicks.typical(0), 0.0, 0.03);
		check("...at worst", kicks.most[0], 0.0, 0.2);
		check("its tilt rides over them (degrees a tick, from tick to tick, typically)", kicks.typical(1), 0.0, 0.55);
		check("...at worst", kicks.most[1], 0.0, 2.5);
		check("its wheels stay out of the ground (blocks in, most)", wheelIn, 0.0, 0.4);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/**
	 * How tight it turns at each gait, the rider looking well off to one side: tighter the slower it goes (user, 2026-11-06), and
	 * its shafts still on the horse at the angle that takes.
	 */
	private void cartTight(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final String breed) {
		section("How tight Icy's cart turns, behind a " + breed);
		final int x = nextLane();
		final int id = summon(ctx, world, breed, UPGRADED + ",NoAI:1b", x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		board(ctx, world, x);
		final double[] radius = new double[4];
		final double[] angle = new double[4];
		float view = 180.0F;
		input.lookAt(view, 15.0F);
		input.holdKey(o -> o.keyUp);
		for (int gait = 0; gait < 4; gait++) {
			if (gait > 0) {
				spurs(ctx, input, 1);
			}
			ctx.waitTicks(60);
			ctx.runOnClient(mc -> IcyCartDrawing.shaftMiss = 0.0);
			double tightest = Double.MAX_VALUE;
			double most = 0.0;
			double[] was = null;
			for (int i = 0; i < 90; i++) {
				// (Keeps the view well ahead of the horse round the circle.)
				final float heading = ctx.computeOnClient(mc -> mc.player.getVehicle().getYRot());
				view = heading + 80.0F;
				input.lookAt(view, 15.0F);
				ctx.waitTick();
				final double[] at = ctx.computeOnClient(mc -> {
					final Entity horse = mc.player.getVehicle();
					final Object cart = call(horse, "bh_getCartEntity");
					return new double[] {horse.getX(), horse.getZ(), horse.getYRot(), Math.abs(Mth.wrapDegrees((float) call(cart, "gluedRenderYaw", 1.0F) - ((LivingEntity) horse).yBodyRot))};
				});
				most = Math.max(most, at[3]);
				if (was != null && i > 30) {
					final double turned = Math.abs(Mth.wrapDegrees((float) (at[2] - was[2]))) * Mth.DEG_TO_RAD;
					if (turned > 0.002) {
						tightest = Math.min(tightest, Math.hypot(at[0] - was[0], at[1] - was[1]) / turned);
					}
				}
				was = at;
			}
			radius[gait] = tightest;
			angle[gait] = most;
			final double miss = ctx.computeOnClient(mc -> IcyCartDrawing.shaftMiss);
			log("  (gait %d: tightest turning radius %.1f blocks, %.0f degrees off the cart, shafts short %.2f)", gait + 1, tightest, most, miss);
			if (gait == 0) {
				sideScreenshot(ctx, "icy_tight_" + breed);
				screenshot(ctx, "icy_tight_back_" + breed);
			}
		}
		check("it turns tighter at a walk than at a gallop (blocks tighter)", radius[3] - radius[0], 1.5, 40.0);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/**
	 * Off a 3-block drop at a trot: it comes down with the weight it has, not floating (user, 2026-11-06), and lands on its
	 * suspension instead of stopping dead.
	 */
	private void cartDrop(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final String breed) {
		section("Icy's cart off a 3-block drop, behind a " + breed);
		final int x = nextLane();
		final int top = GROUND + 3;
		// (Not a command: that fails where the lane's chunks aren't loaded yet.)
		world.getServer().runOnServer(server -> {
			final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int bx = x - 8; bx <= x + 8; bx++) {
				for (int bz = -24; bz <= 12; bz++) {
					for (int up = 0; up < 3; up++) {
						server.overworld().setBlockAndUpdate(pos.set(bx, GROUND + up, bz), Blocks.GRASS_BLOCK.defaultBlockState());
					}
				}
			}
		});
		final int id = summon(ctx, world, breed, UPGRADED + ",NoAI:1b", x, top);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		board(ctx, world, x, top);
		input.lookAt(180.0F, 15.0F);
		input.holdKey(o -> o.keyUp);
		spurs(ctx, input, 1);
		double fastest = 0.0;
		double hardest = 0.0;
		double travel = 0.0;
		double rate = Double.NaN;
		double last = Double.NaN;
		double lowest = Double.MAX_VALUE;
		int shots = 0;
		for (int i = 0; i < 220; i++) {
			ctx.waitTick();
			final double[] now = ctx.computeOnClient(mc -> {
				final Entity horse = mc.player.getVehicle();
				final Object cart = horse == null ? null : call(horse, "bh_getCartEntity");
				if (cart == null) {
					return null;
				}
				double most = 0.0;
				for (int wheel = CartTrail.FRONT_LEFT; wheel <= CartTrail.REAR_RIGHT; wheel++) {
					most = Math.max(most, ((CartTrail.Lean) cart).horsingaround$travel(wheel, 1.0F));
				}
				return new double[] {((net.minecraft.world.phys.Vec3) call(cart, "gluedRenderPosition", 1.0F)).y, most, horse.getZ()};
			});
			if (now == null) {
				continue;
			}
			if (!Double.isNaN(last)) {
				final double nowRate = now[0] - last;
				fastest = Math.min(fastest, nowRate);
				if (!Double.isNaN(rate)) {
					hardest = Math.max(hardest, nowRate - rate);
				}
				rate = nowRate;
			}
			last = now[0];
			lowest = Math.min(lowest, now[0]);
			travel = Math.max(travel, now[1]);
			if (shots < 2 && now[2] < -24.0 - shots * 1.5) {
				sideScreenshot(ctx, "icy_drop_" + breed + "_" + shots);
				shots++;
				rate = Double.NaN;
				last = Double.NaN;
			}
		}
		log("  (falls at most %.3f a tick; hardest stop %.3f a tick; wheels up into it %.2f; lowest %.2f)", fastest, hardest, travel, lowest - GROUND);
		check("it comes down with some weight (blocks a tick, fastest)", -fastest, 0.25, 2.0);
		check("...and lands on its suspension, not stopping dead (blocks a tick slower, at most, in a tick)", hardest, 0.0, 0.25);
		check("...its wheels taking it (blocks up into the cart, most)", travel, 0.1, 0.7);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/**
	 * Across a rough hillside, drops of a few blocks under one wheel and all: the cart never goes into the ground (user,
	 * 2026-11-06), its wheels and its bed, and its shafts stay on the horse.
	 */
	private void cartRough(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final String breed) {
		section("Icy's cart across a rough hillside, behind a " + breed);
		final int x = nextLane();
		world.getServer().runOnServer(server -> {
			final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int bx = x - 8; bx <= x + 8; bx++) {
				for (int bz = -80; bz <= -6; bz++) {
					// (Deterministic: ridges and hollows of up to 4 blocks, running across and along the way.)
					// (The same wherever the lane falls.)
					final int across = bx - x;
					final int high = Mth.floor(2.0 + 1.6 * Mth.sin(across * 0.9F + bz * 0.23F) + 1.4 * Mth.sin(bz * 0.47F - across * 0.31F));
					for (int up = 0; up < high; up++) {
						server.overworld().setBlockAndUpdate(pos.set(bx, GROUND + up, bz), Blocks.GRASS_BLOCK.defaultBlockState());
					}
				}
			}
		});
		final int id = summon(ctx, world, breed, UPGRADED, x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		board(ctx, world, x);
		input.lookAt(180.0F, 15.0F);
		input.holdKey(o -> o.keyUp);
		spurs(ctx, input, 1);
		ctx.runOnClient(mc -> IcyCartDrawing.shaftMiss = 0.0);
		double wheelIn = 0.0;
		double bedIn = 0.0;
		double farthest = 0.0;
		final Kicks kicks = new Kicks();
		for (int i = 0; i < 300; i++) {
			ctx.waitTick();
			final double[] now = ctx.computeOnClient(mc -> {
				final Entity horse = mc.player.getVehicle();
				final Object cart = horse == null ? null : call(horse, "bh_getCartEntity");
				return cart == null ? null : new double[] {horse.getZ(), (float) call(cart, "renderTilt", 1.0F), ((CartTrail.Lean) cart).horsingaround$roll(1.0F), ((net.minecraft.world.phys.Vec3) call(cart, "gluedRenderPosition", 1.0F)).y};
			});
			if (now == null) {
				continue;
			}
			farthest = Math.min(farthest, now[0]);
			kicks.add(now[3], now[1], now[2]);
			for (final double gap : ctx.computeOnClient(IcyFeaturesTest::wheelGaps)) {
				wheelIn = Math.max(wheelIn, -gap);
			}
			for (final double gap : ctx.computeOnClient(IcyFeaturesTest::bedGaps)) {
				bedIn = Math.max(bedIn, -gap);
			}
			if (i == 100 || i == 200) {
				sideScreenshot(ctx, "icy_rough_" + breed + "_" + i);
				screenshot(ctx, "icy_rough_back_" + breed + "_" + i);
				kicks.skip();
			}
		}
		log("  (reached z %.1f; wheels in the ground %.2f, bed %.2f at most; kicks: height %.3f, tilt %.2f, lean %.2f)", farthest, wheelIn, bedIn, kicks.most[0], kicks.most[1], kicks.most[2]);
		check("it gets across (blocks along)", -farthest, 15.0, 200.0);
		check("its wheels stay out of the ground (blocks in, most)", wheelIn, 0.0, 0.4);
		check("...and its bed (blocks in, most)", bedIn, 0.0, 0.7);
		check("...and the shafts on the horse (blocks short of it, most)", ctx.computeOnClient(mc -> IcyCartDrawing.shaftMiss), 0.0, 0.6);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/**
	 * Up a 2-block rise at a canter and down the far side: the wheels roll up and down as if on ramps (user, 2026-11-06), the
	 * cart doesn't fly along behind the horse, and the shafts stay on the horse.
	 */
	private void cartLedge(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final String breed) {
		section("Icy's cart up and down two blocks, behind a " + breed);
		final int x = nextLane();
		world.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d -44 %d %d -16 minecraft:grass_block", x - 5, GROUND, x + 5, GROUND + 1));
		final int id = summon(ctx, world, breed, UPGRADED, x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		board(ctx, world, x);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		spurs(ctx, input, 2);
		ctx.runOnClient(mc -> IcyCartDrawing.shaftMiss = 0.0);
		final Kicks kicks = new Kicks();
		double off = 0.0;
		double top = 0.0;
		double shaftOnTop = 0.0;
		int shots = 0;
		for (int i = 0; i < 260; i++) {
			ctx.waitTick();
			final double[] now = ctx.computeOnClient(mc -> {
				final Entity horse = mc.player.getVehicle();
				final Object cart = horse == null ? null : call(horse, "bh_getCartEntity");
				return cart == null ? null : new double[] {horse.getY(), horse.getZ(), ((net.minecraft.world.phys.Vec3) call(cart, "gluedRenderPosition", 1.0F)).y, (float) call(cart, "renderTilt", 1.0F), horse.getY() + ((dev.horsingaround.ride.RideStateHolder) horse).horsingaround$ride().heightOffset(1.0F)};
			});
			if (now == null) {
				continue;
			}
			top = Math.max(top, now[0] - GROUND);
			off = Math.max(off, ctx.computeOnClient(IcyFeaturesTest::offRamps));
			kicks.add(now[2], now[3], now[4]);
			shaftOnTop = ctx.computeOnClient(mc -> IcyCartDrawing.shaftMiss);
			if (shots < 2 && now[1] < (shots == 0 ? -16.0 : -44.5)) {
				sideScreenshot(ctx, "icy_ledge_" + breed + "_" + (shots == 0 ? "up" : "down"));
				shots++;
				kicks.skip();
			}
			if (now[1] < -60.0) {
				break;
			}
		}
		log("  (rose %.2f; wheels off their ramps %.2f at most; shafts short of the horse %.2f; kicks: height %.3f, tilt %.2f, the horse itself %.3f)", top, off, shaftOnTop, kicks.most[0], kicks.most[1], kicks.most[2]);
		check("it goes up two blocks (blocks up)", top, 1.9, 4.5);
		check("the cart stays on its wheels, not flying behind the horse (blocks off the ground and its ramps, most)", off, 0.0, 1.0);
		check("the shafts stay on the horse (blocks short of it, most)", shaftOnTop, 0.0, 0.5);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/**
	 * A jump at a gallop: a wagon's wheels stay on the ground under the horse's leap (its shafts make up the difference)
	 * unless the horse goes too high; a two-wheeled cart hangs from the hitch, so its wheels stay down as far as its tilt lets them.
	 */
	private void cartJump(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final String breed) {
		section("Icy's cart when the horse jumps, behind a " + breed);
		final int x = nextLane();
		final int id = summon(ctx, world, breed, UPGRADED, x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		board(ctx, world, x);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		spurs(ctx, input, 2);
		ctx.waitTicks(40);
		ctx.runOnClient(mc -> IcyCartDrawing.shaftMiss = 0.0);
		final double startY = ctx.computeOnClient(mc -> mc.player.getVehicle().getY());
		input.pressKey(o -> o.keyJump);
		double peak = 0.0;
		double wheelHigh = 0.0;
		double wheelIn = 0.0;
		double bedIn = 0.0;
		boolean shot = false;
		for (int i = 0; i < 30; i++) {
			ctx.waitTick();
			final double up = ctx.computeOnClient(mc -> mc.player.getVehicle().getY()) - startY;
			peak = Math.max(peak, up);
			wheelHigh = Math.max(wheelHigh, ctx.computeOnClient(IcyFeaturesTest::offRamps));
			for (final double gap : ctx.computeOnClient(IcyFeaturesTest::wheelGaps)) {
				wheelIn = Math.max(wheelIn, -gap);
			}
			for (final double gap : ctx.computeOnClient(IcyFeaturesTest::bedGaps)) {
				bedIn = Math.max(bedIn, -gap);
			}
			if (!shot && up > 0.8) {
				shot = true;
				sideScreenshot(ctx, "icy_jump_" + breed);
			}
		}
		log("  (the horse rose %.2f, the wheels at most %.2f; in the ground: wheels %.2f, bed %.2f)", peak, wheelHigh, wheelIn, bedIn);
		check("coming down it doesn't go into the ground (blocks in, most)", wheelIn, 0.0, 0.35);
		check("...its bed neither (blocks in, most)", bedIn, 0.0, 0.2);
		check("the horse jumps (blocks up)", peak, 0.6, 3.0);
		check("its wheels stay near the ground (blocks up, most)", wheelHigh, 0.0, 1.0);
		check("...and the shafts on the horse (blocks short of it, most)", ctx.computeOnClient(mc -> IcyCartDrawing.shaftMiss), 0.0, 0.5);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/**
	 * Each wheel of the cart the rider's horse pulls, as drawn: how far it is above the ground under it (blocks). Worked
	 * out as Icy's renderer turns the cart: tilted about the hitch, then leaned over, and on its suspension.
	 */
	private static double[] wheelGaps(final net.minecraft.client.Minecraft mc) {
		final Entity horse = mc.player.getVehicle();
		final Object cart = horse == null ? null : call(horse, "bh_getCartEntity");
		if (cart == null) {
			return new double[0];
		}
		final double rear = IcyCompat.cartLength((Entity) cart);
		final double front = IcyCompat.cartFrontAxle((Entity) cart);
		final float yaw = (float) call(cart, "gluedRenderYaw", 1.0F) * Mth.DEG_TO_RAD;
		final float tilt = (float) call(cart, "renderTilt", 1.0F) * Mth.DEG_TO_RAD;
		final float roll = ((CartTrail.Lean) cart).horsingaround$roll(1.0F) * Mth.DEG_TO_RAD;
		final net.minecraft.world.phys.Vec3 at = (net.minecraft.world.phys.Vec3) call(cart, "gluedRenderPosition", 1.0F);
		final double[] gaps = new double[front > 0.0 ? 4 : 2];
		for (int i = 0; i < gaps.length; i++) {
			final double behind = i < 2 ? rear : front;
			final double left = i % 2 == 0 ? 1.25 : -1.25;
			final double wx = at.x + Mth.sin(yaw) * behind + Mth.cos(yaw) * left;
			final double wz = at.z - Mth.cos(yaw) * behind + Mth.sin(yaw) * left;
			final double wy = at.y - behind * Mth.sin(tilt) + left * Mth.sin(roll) + ((CartTrail.Lean) cart).horsingaround$travel((i < 2 ? CartTrail.REAR_LEFT : CartTrail.FRONT_LEFT) + i % 2, 1.0F);
			gaps[i] = wy - groundUnder(mc, wx, wz, wy);
		}
		return gaps;
	}

	/** The underside of the cart's bed as drawn, at its corners and middle: how far each is above the ground under it (blocks). */
	private static double[] bedGaps(final net.minecraft.client.Minecraft mc) {
		final Entity horse = mc.player.getVehicle();
		final Object cart = horse == null ? null : call(horse, "bh_getCartEntity");
		if (cart == null) {
			return new double[0];
		}
		final double rear = IcyCompat.cartLength((Entity) cart) + 0.9;
		final double front = 1.0625;
		final float yaw = (float) call(cart, "gluedRenderYaw", 1.0F) * Mth.DEG_TO_RAD;
		final float tilt = (float) call(cart, "renderTilt", 1.0F) * Mth.DEG_TO_RAD;
		final float roll = ((CartTrail.Lean) cart).horsingaround$roll(1.0F) * Mth.DEG_TO_RAD;
		final net.minecraft.world.phys.Vec3 at = (net.minecraft.world.phys.Vec3) call(cart, "gluedRenderPosition", 1.0F);
		final double[] gaps = new double[5];
		for (int i = 0; i < 5; i++) {
			final double behind = i < 2 ? front : i < 4 ? rear : (front + rear) * 0.5;
			final double left = i == 4 ? 0.0 : i % 2 == 0 ? 1.0 : -1.0;
			final double px = at.x + Mth.sin(yaw) * behind + Mth.cos(yaw) * left;
			final double pz = at.z - Mth.cos(yaw) * behind + Mth.sin(yaw) * left;
			final double py = at.y + 0.75 - behind * Mth.sin(tilt) + left * Mth.sin(roll);
			gaps[i] = py - groundUnder(mc, px, pz, py);
		}
		return gaps;
	}

	/** The most any wheel of the cart was left above what it rolls on, the ground with its ramps (blocks). */
	private static double offRamps(final net.minecraft.client.Minecraft mc) {
		final Entity horse = mc.player.getVehicle();
		final CartTrail.Lean cart = horse == null ? null : IcyCompat.cartLean(horse);
		double most = 0.0;
		if (cart != null) {
			for (int i = CartTrail.FRONT_LEFT; i <= CartTrail.REAR_RIGHT; i++) {
				most = Math.max(most, cart.horsingaround$offRamp(i));
			}
		}
		return most;
	}

	private static double groundUnder(final net.minecraft.client.Minecraft mc, final double x, final double z, final double from) {
		final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = Mth.floor(from + 2.0); y > Mth.floor(from - 6.0); y--) {
			pos.set(Mth.floor(x), y, Mth.floor(z));
			final net.minecraft.world.phys.shapes.VoxelShape shape = mc.level.getBlockState(pos).getCollisionShape(mc.level, pos);
			if (!shape.isEmpty()) {
				return y + shape.max(net.minecraft.core.Direction.Axis.Y);
			}
		}
		return from;
	}

	/** Rolling on the flat, Icy's wheel animation doesn't jolt the bed, nor its riders with it (read as Icy's mod reads it for them). */
	private void bedStaysStill(final ClientGameTestContext ctx) {
		double bounce = 0.0;
		double rock = 0.0;
		for (int i = 0; i < 40; i++) {
			ctx.waitTick();
			final double[] bed = ctx.computeOnClient(mc -> {
				final Object cart = mc.player.getVehicle() == null ? null : call(mc.player.getVehicle(), "bh_getCartEntity");
				return cart == null ? new double[2] : new double[] {(float) call(cart, "bedBounce"), (float) call(cart, "bedRock")};
			});
			bounce = Math.max(bounce, Math.abs(bed[0]));
			rock = Math.max(rock, Math.abs(bed[1]));
		}
		check("rolling along, the bed doesn't bounce (blocks, most)", bounce, 0.0, 0.001);
		check("...nor rock (degrees, most)", rock, 0.0, 0.01);
	}

	/**
	 * A cart lies on its wheels (user, 2026-11-06): the plane it lies in, for the shapes of ground that matter; then a wagon
	 * and a cart standing with blocks put and dug under their wheels.
	 */
	private void cartLean(final ClientGameTestContext ctx, final TestSingleplayerContext world) {
		section("Icy's carts lie on their wheels");
		final double[] plane = new double[4];
		CartTrail.level(new double[] {1.0, 0.0, 1.0, 0.0}, plane);
		check("two left wheels on blocks, two right ones a block down: it leans all the way", plane[0] == 1.0 && plane[1] == 0.0 && plane[2] == 1.0 && plane[3] == 0.0);
		CartTrail.level(new double[] {0.0, 0.0, 0.0, -1.0}, plane);
		check("three on the ground: the fourth wheel stays well above the ground under it (blocks)", plane[3] + 1.0, 0.6, 0.9);
		check("...and the cart leans a little towards it (blocks at that corner)", -plane[3], 0.1, 0.3);
		check("...the other three about on the ground", Math.abs(plane[0]) <= 0.3 && Math.abs(plane[1]) <= 0.01 && Math.abs(plane[2]) <= 0.01);
		CartTrail.level(new double[] {1.0, 0.0, 0.0, 0.0}, plane);
		check("one wheel up on a block: it rides up on it", plane[0] == 1.0 && plane[3] == 0.0 && plane[1] == 0.5 && plane[2] == 0.5);

		// A wagon, standing: (world) left is -x, heading north. Behind a Belgian, wheels 2.5 and 4.65 blocks behind the horse
		// (at z 3.0 and 5.15), 1.25 to each side.
		// (No AI: left to itself it wanders off the blocks under its wheels.)
		int x = nextLane();
		int id = summon(ctx, world, "belgian_horse", UPGRADED + ",NoAI:1b", x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		board(ctx, world, x);
		ctx.waitTicks(30);
		screenshot(ctx, "icy_wagon_view_standing");
		check("a wagon on the flat is level (degrees across)", Math.abs(cartRoll(ctx)), 0.0, 0.01);
		check("...and along", Math.abs(cartTilt(ctx)), 0.0, 0.01);
		check("...its wheels on the ground (blocks up)", Math.abs(cartRise(ctx)), 0.0, 0.01);
		// The rear right wheel over a long hole, too long for it to bridge: three on the ground.
		final TestServerContext server = world.getServer();
		server.runCommand(String.format(Locale.ROOT, "fill %d %d 4 %d %d 7 minecraft:air", x + 1, GROUND - 1, x + 1, GROUND - 1));
		ctx.waitTicks(30);
		final double holeRoll = cartRoll(ctx);
		final double holeTilt = cartTilt(ctx);
		log("  (over a hole: lean %.2f, tilt %.2f, rise %.2f)", holeRoll, holeTilt, cartRise(ctx));
		check("three wheels on the ground: it leans a little to the fourth's side (degrees, its left up)", holeRoll, 2.0, 8.0);
		check("...and along, to that end (degrees, nose up)", holeTilt, 2.0, 8.0);
		sideScreenshot(ctx, "icy_wagon_hole");
		server.runCommand(String.format(Locale.ROOT, "fill %d %d 4 %d %d 7 minecraft:grass_block", x + 1, GROUND - 1, x + 1, GROUND - 1));
		// The two left wheels on blocks.
		server.runCommand(String.format(Locale.ROOT, "fill %d %d 2 %d %d 6 minecraft:stone", x - 2, GROUND, x - 1, GROUND));
		ctx.waitTicks(30);
		log("  (left wheels up a block: lean %.2f, tilt %.2f, rise %.2f)", cartRoll(ctx), cartTilt(ctx), cartRise(ctx));
		check("two left wheels up a block: it leans over (degrees, its left up)", cartRoll(ctx), 15.0, 20.5);
		check("...not along it (degrees)", Math.abs(cartTilt(ctx)), 0.0, 1.0);
		sideScreenshot(ctx, "icy_wagon_left_up");
		screenshot(ctx, "icy_wagon_left_up_back");
		server.runCommand(String.format(Locale.ROOT, "fill %d %d 2 %d %d 6 minecraft:air", x - 2, GROUND, x - 1, GROUND));
		ctx.waitTicks(30);
		check("back on the flat it comes level", Math.abs(cartRoll(ctx)), 0.0, 0.5);
		// Both right wheels over a deep drop: it leans as far as it goes, on the left ones, and doesn't sink into the ground for it.
		server.runCommand(String.format(Locale.ROOT, "fill %d %d 2 %d %d 7 minecraft:air", x + 1, GROUND - 3, x + 5, GROUND - 1));
		ctx.waitTicks(30);
		restsOn(ctx, "a wagon with both right wheels over a deep drop");
		sideScreenshot(ctx, "icy_wagon_drop");
		screenshot(ctx, "icy_wagon_drop_back");
		dismount(ctx, ctx.getInput());
		// A two-wheeled cart: its left wheel on a block (2.9 behind the horse, at z 3.4). Not at the hitch: that's on the horse.
		x = nextLane();
		id = summon(ctx, world, "american_paint_horse", UPGRADED + ",NoAI:1b", x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		board(ctx, world, x);
		ctx.waitTicks(10);
		check("a cart on the flat is level (degrees across)", Math.abs(cartRoll(ctx)), 0.0, 0.01);
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d 3 minecraft:stone", x - 1, GROUND));
		ctx.waitTicks(30);
		log("  (cart, left wheel up a block: lean %.2f, tilt %.2f)", cartRoll(ctx), cartTilt(ctx));
		check("its left wheel up a block: it leans over (degrees, its left up)", cartRoll(ctx), 15.0, 20.5);
		sideScreenshot(ctx, "icy_cart_left_up");
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d 3 minecraft:air", x - 1, GROUND));
		ctx.waitTicks(5);
		// Its right wheel over a deep drop (the case seen in play).
		server.runCommand(String.format(Locale.ROOT, "fill %d %d 2 %d %d 7 minecraft:air", x + 1, GROUND - 3, x + 5, GROUND - 1));
		ctx.waitTicks(30);
		restsOn(ctx, "a cart with its right wheel over a deep drop");
		sideScreenshot(ctx, "icy_cart_drop");
		screenshot(ctx, "icy_cart_drop_back");
		dismount(ctx, ctx.getInput());
	}

	/** The cart is on the wheels that have ground under them, leaned as far as it goes towards the others, and in the ground nowhere. */
	private void restsOn(final ClientGameTestContext ctx, final String what) {
		final double[] gaps = ctx.computeOnClient(IcyFeaturesTest::wheelGaps);
		final double[] bed = ctx.computeOnClient(IcyFeaturesTest::bedGaps);
		double wheelIn = 0.0;
		for (final double gap : gaps) {
			wheelIn = Math.max(wheelIn, -gap);
		}
		double bedIn = 0.0;
		for (final double gap : bed) {
			bedIn = Math.max(bedIn, -gap);
		}
		log("  (%s: lean %.2f, tilt %.2f, wheels in the ground %.2f, bed %.2f)", what, cartRoll(ctx), cartTilt(ctx), wheelIn, bedIn);
		check(what + ": it leans over to that side (degrees, its left up)", cartRoll(ctx), 15.0, 20.5);
		check("...its wheels out of the ground (blocks in, most)", wheelIn, 0.0, 0.05);
		check("...and its bed (blocks in, most)", bedIn, 0.0, 0.05);
	}

	private static double cartRoll(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> IcyCompat.cartLean(mc.player.getVehicle()).horsingaround$roll(1.0F));
	}

	private static double cartRise(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> IcyCompat.cartLean(mc.player.getVehicle()).horsingaround$rise(1.0F));
	}

	private static double cartTilt(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> (float) call(call(mc.player.getVehicle(), "bh_getCartEntity"), "renderTilt", 1.0F));
	}

	/**
	 * Backing up with a cart (and a wagon, behind a draft horse): looking straight on, it backs straight; looking off to one
	 * side, the rig comes round to where the rider looks. The cart never jack-knifes, and its wheels don't slide sideways.
	 */
	private void cartReverse(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world, final String breed) {
		section("Backing up with Icy's cart behind a " + breed);
		final int x = nextLane();
		final int id = summon(ctx, world, breed, UPGRADED, x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		board(ctx, world, x);
		input.lookAt(180.0F, 10.0F);
		ctx.waitTicks(5);
		final double[] start = rig(ctx);
		input.holdKey(o -> o.keyDown);
		double straightAngle = 0.0;
		double slip = 0.0;
		double[] was = start;
		for (int i = 0; i < 60; i++) {
			ctx.waitTick();
			final double[] now = rig(ctx);
			if (now == null) {
				continue;
			}
			straightAngle = Math.max(straightAngle, Math.abs(Mth.wrapDegrees((float) (now[2] - now[3]))));
			slip = Math.max(slip, sideSlip(was, now));
			was = now;
		}
		final double[] backed = rig(ctx);
		check("looking straight on, it backs up (blocks)", backed[1] - start[1], 2.0, 30.0);
		check("...straight (blocks off its line)", Math.abs(backed[0] - start[0]), 0.0, 0.2);
		check("...the cart behind it (degrees off the horse, most)", straightAngle, 0.0, 2.0);
		// Looking 40 degrees off: the rig comes round to the view.
		final float view = 220.0F;
		input.lookAt(view, 10.0F);
		double mostOff = 0.0;
		for (int i = 0; i < 160; i++) {
			ctx.waitTick();
			final double[] now = rig(ctx);
			if (now == null) {
				continue;
			}
			mostOff = Math.max(mostOff, Math.abs(Mth.wrapDegrees((float) (now[2] - now[3]))));
			slip = Math.max(slip, sideSlip(was, now));
			was = now;
		}
		final double[] end = rig(ctx);
		check("looking off to one side, it doesn't jack-knife (degrees off the horse, most)", mostOff, 0.0, 19.0);
		check("...and the cart comes round to the view (degrees off it)", Math.abs(Mth.wrapDegrees((float) (end[3] - view))), 0.0, 6.0);
		check("...the horse in line with it (degrees off the horse)", Math.abs(Mth.wrapDegrees((float) (end[2] - end[3]))), 0.0, 6.0);
		check("backing, its wheels don't slide sideways (blocks a tick, most)", slip, 0.0, 0.03);
		screenshot(ctx, "icy_reverse_" + breed);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/** Backing towards a wall: the cart stops short of it instead of going through it. */
	private void cartBackIntoWall(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Backing Icy's cart up to a wall");
		final int x = nextLane();
		final int wall = 12;
		world.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", x - 4, GROUND, wall, x + 5, GROUND + 3, wall));
		final int id = summon(ctx, world, "american_paint_horse", UPGRADED, x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		board(ctx, world, x);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyDown);
		ctx.waitTicks(200);
		input.releaseKey(o -> o.keyDown);
		ctx.waitTicks(5);
		final double[] end = rig(ctx);
		final double gap = wall - (end[1] + end[4]);
		log("  (the rear axle stopped %.2f from the wall)", gap);
		check("backing up to a wall, it stops at it (blocks from the rear axle to the wall)", gap, 0.8, 2.5);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/** The most the rate of change of each of a few values changed from one tick to the next: how sudden the motion is. */
	private static final class Kicks {
		final double[] most = new double[3];
		private final double[] squares = new double[3];
		private final int[] counted = new int[3];
		private final double[] was = {Double.NaN, Double.NaN, Double.NaN};
		private final double[] rate = {Double.NaN, Double.NaN, Double.NaN};

		/** The next value isn't one tick after the last. */
		void skip() {
			java.util.Arrays.fill(this.was, Double.NaN);
			java.util.Arrays.fill(this.rate, Double.NaN);
		}

		/** The root mean square of the same. */
		double typical(final int value) {
			return Math.sqrt(this.squares[value] / Math.max(this.counted[value], 1));
		}

		void add(final double... values) {
			for (int i = 0; i < 3; i++) {
				final double now = values[i] - this.was[i];
				if (!Double.isNaN(this.rate[i])) {
					final double kick = now - this.rate[i];
					if (!Double.isNaN(kick)) {
						this.most[i] = Math.max(this.most[i], Math.abs(kick));
						this.squares[i] += kick * kick;
						this.counted[i]++;
					}
				}
				this.rate[i] = now;
				this.was[i] = values[i];
			}
		}
	}

	/** The rig as it is now: the horse's x, z and heading, the cart's heading as drawn, and how far behind the horse its axle is. */
	private static double[] rig(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			final Entity horse = mc.player.getVehicle();
			final Object cart = horse == null ? null : call(horse, "bh_getCartEntity");
			if (cart == null) {
				return null;
			}
			return new double[] {horse.getX(), horse.getZ(), ((LivingEntity) horse).yBodyRot, (float) call(cart, "gluedRenderYaw", 1.0F), IcyCompat.cartLength((Entity) cart) + ((CartTrail.Lean) cart).horsingaround$behind()};
		});
	}

	/** How far the rig's axle went sideways, across the cart, from one tick to the next (blocks). */
	private static double sideSlip(final double[] was, final double[] now) {
		final double wasX = was[0] + Mth.sin((float) was[3] * Mth.DEG_TO_RAD) * was[4];
		final double wasZ = was[1] - Mth.cos((float) was[3] * Mth.DEG_TO_RAD) * was[4];
		final double nowX = now[0] + Mth.sin((float) now[3] * Mth.DEG_TO_RAD) * now[4];
		final double nowZ = now[1] - Mth.cos((float) now[3] * Mth.DEG_TO_RAD) * now[4];
		final float rad = (float) (was[3] + Mth.wrapDegrees((float) (now[3] - was[3])) * 0.5) * Mth.DEG_TO_RAD;
		return Math.abs((nowX - wasX) * Mth.cos(rad) + (nowZ - wasZ) * Mth.sin(rad));
	}

	/** The small cart with a hoe fitted: ridden over grass, it turns a strip to farmland behind it. */
	private void plough(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Icy's plough");
		final int x = nextLane();
		final int id = summon(ctx, world, "american_paint_horse", UPGRADED, x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		onHorse(world, id, horse -> {
			try {
				final Entity cart = cartOf(horse);
				final Method fit = cart.getClass().getDeclaredMethod("fitPlough", ItemStack.class);
				fit.setAccessible(true);
				fit.invoke(cart, new ItemStack(Items.DIAMOND_HOE));
			} catch (final ReflectiveOperationException e) {
				throw new IllegalStateException("Icy's plough changed", e);
			}
		});
		board(ctx, world, x);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		spurs(ctx, input, 1);
		ctx.waitTicks(80);
		stop(ctx, input);
		final int tilled = world.getServer().computeOnServer(server -> count(server.overworld(), x - 3, GROUND - 3, -40, x + 3, GROUND, 0, Blocks.FARMLAND));
		check("trotted over grass, it ploughs a strip (blocks of farmland)", tilled, 20, 400);
		screenshot(ctx, "icy_plough");
		dismount(ctx, input);
	}

	/** A draft horse gets Icy's covered wagon, and pulls it free of the drag. */
	private void wagon(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Icy's wagon behind a Belgian");
		final int x = nextLane();
		final int id = summon(ctx, world, "belgian_horse", UPGRADED, x, GROUND);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_cart_gear")));
		ctx.waitTicks(10);
		check("a draft horse pulls the wagon", "WAGON".equals(world.getServer().computeOnServer(server -> {
			final Entity cart = cartOf(horse(server, id));
			return cart == null ? null : String.valueOf(call(cart, "type"));
		})));
		check("...free of the drag (Icy's draft haul)", world.getServer().computeOnServer(server -> drag(horse(server, id))), -0.01, 0.01);
		board(ctx, world, x);
		check("this mod rides it", managed(ctx));
		final double gallop = gallopFor(ctx);
		ticksToGallop(ctx, input);
		ctx.waitTicks(20);
		check("gallops at its own top speed (blocks/tick)", averageSpeed(ctx, 10), gallop * 0.85, gallop * 1.15);
		screenshot(ctx, "icy_wagon_gallop");
		bedStaysStill(ctx);
		followThroughTurn(ctx, input, "icy_wagon", 8.0);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/**
	 * Gallops round a gentle bend, then a hard quarter turn with a jump in it, then on straight, watching the cart as it
	 * is drawn (Icy's mod draws it by its own heading, not the entity's, which follows the server's): round the bend it
	 * angles off the horse into the turn, in the hard turn the shafts hold it, and it lines up behind the horse again.
	 */
	private void followThroughTurn(final ClientGameTestContext ctx, final TestInput input, final String shot, final double widest) {
		double bendAngle = 0.0;
		double hardAngle = 0.0;
		double circle = Double.MAX_VALUE;
		final Kicks kicks = new Kicks();
		double[] was = null;
		double last = Double.NaN;
		boolean present = true;
		float view = 180.0F;
		for (int i = 0; i < 110; i++) {
			// 30 ticks of a gentle bend (1.5 degrees a tick), 20 of a hard turn (4.5), then straight on.
			view += i < 30 ? 1.5F : i < 50 ? 4.5F : 0.0F;
			input.lookAt(view, 10.0F);
			if (i == 40) {
				input.pressKey(o -> o.keyJump);
			}
			ctx.waitTick();
			final Double angle = ctx.computeOnClient(mc -> {
				final Entity horse = mc.player.getVehicle();
				final Object cart = horse == null ? null : call(horse, "bh_getCartEntity");
				return cart == null ? null : (double) Math.abs(Mth.wrapDegrees((float) call(cart, "gluedRenderYaw", 1.0F) - ((LivingEntity) horse).yBodyRot));
			});
			// The horse's turning circle: the radius of this tick's arc.
			final double[] at = ctx.computeOnClient(mc -> new double[] {mc.player.getVehicle().getX(), mc.player.getVehicle().getZ(), mc.player.getVehicle().getYRot(), mc.player.getVehicle().onGround() ? 1 : 0});
			if (was != null && i >= 30 && i < 50 && at[3] > 0) {
				final double turned = Math.abs(Mth.wrapDegrees((float) (at[2] - was[2]))) * Mth.DEG_TO_RAD;
				if (turned > 0.005) {
					circle = Math.min(circle, Math.hypot(at[0] - was[0], at[1] - was[1]) / turned);
				}
			}
			kicks.add(at[2], Mth.wrapDegrees((float) (at[2] - (angle == null ? at[2] : at[2]))), angle == null ? 0.0 : angle);
			was = at;
			if (angle == null) {
				present = false;
				continue;
			}
			last = angle;
			if (i < 30) {
				bendAngle = Math.max(bendAngle, angle);
			} else if (i < 50) {
				hardAngle = Math.max(hardAngle, angle);
			}
			if (i == 20 || i == 42) {
				sideScreenshot(ctx, shot + (i == 20 ? "_bend" : "_turn"));
			}
		}
		log("  (kicks through the turns, change in rate from tick to tick, most: horse's heading %.2f, angle off the cart %.2f)", kicks.most[0], kicks.most[2]);
		check("the cart is there all through the turns and the jump", present);
		check("round a bend it angles off the horse into the turn (degrees, most)", bendAngle, 3.0, 28.5);
		check("in a hard turn the shafts hold it (degrees off the horse, most)", hardAngle, 10.0, 28.5);
		check("...and the horse, pulling it round, turns wide (turning radius, blocks, tightest)", circle, widest, 100.0);
		check("galloping on straight, it lines up behind the horse again (degrees)", last, 0.0, 2.0);
	}

	// ---- A second rider ----

	/** Icy's shared riding: a second player climbs on behind; the first keeps the reins and the ride goes on. */
	private void secondRider(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Icy's shared riding: a second player behind");
		final int x = nextLane();
		final int id = summon(ctx, world, "american_paint_horse", SADDLE, x, GROUND);
		board(ctx, world, x);
		final int riders = world.getServer().computeOnServer(server -> {
			final AbstractHorse horse = horse(server, id);
			final ServerPlayer pillion = pillion(server);
			pillion.setPos(horse.getX() + 1.0, horse.getY(), horse.getZ());
			final Object result = horse.mobInteract(pillion, InteractionHand.MAIN_HAND);
			if (horse.getPassengers().size() < 2) {
				final boolean boarded = pillion.startRiding(horse);
				System.out.println("[icy-test]   (Icy's interaction gave " + result + "; boarding it directly: " + boarded + ")");
			}
			return horse.getPassengers().size();
		});
		check("a second player climbs on (riders)", riders, 2, 2);
		check("the first keeps the reins (on the server)", world.getServer().computeOnServer(server -> {
			final LivingEntity controller = horse(server, id).getControllingPassenger();
			return controller instanceof ServerPlayer && controller != pillion(server);
		}));
		check("...and this mod rides it on their client", managed(ctx) && ctx.computeOnClient(mc -> ((AbstractHorse) mc.player.getVehicle()).getControllingPassenger() == mc.player));
		ctx.waitTicks(5);
		final double[] seats = world.getServer().computeOnServer(server -> {
			final AbstractHorse horse = horse(server, id);
			final List<Entity> passengers = horse.getPassengers();
			return passengers.size() < 2 ? new double[2] : new double[] {forwardOf(horse, passengers.get(0)), forwardOf(horse, passengers.get(1))};
		});
		log("  (seats, blocks ahead of the horse's middle: %.2f, %.2f)", seats[0], seats[1]);
		check("the second sits behind the first (blocks)", seats[0] - seats[1], 0.4, 1.2);
		final double gallop = gallopFor(ctx);
		galloping(ctx, input);
		check("gallops two up at its top speed (blocks/tick)", averageSpeed(ctx, 10), gallop * 0.85, gallop * 1.15);
		input.lookAt(240.0F, 10.0F);
		ctx.waitTicks(20);
		check("...and turns (degrees)", Math.abs(Mth.wrapDegrees(ctx.computeOnClient(mc -> mc.player.getVehicle().getYRot()) - 180.0F)), 30.0, 75.0);
		world.getServer().runOnServer(server -> pillion(server).stopRiding());
		ctx.waitTicks(10);
		check("the second gets off; the first rides on (speed / gallop)", averageSpeed(ctx, 10) / gallop, 0.85, 1.15);
		check("...still holding the reins", managed(ctx) && ctx.computeOnClient(mc -> ((AbstractHorse) mc.player.getVehicle()).getControllingPassenger() == mc.player));
		stop(ctx, input);
		dismount(ctx, input);
	}

	/** A second player, on the server only (no client behind it): Fabric's own fake player can't ride. */
	private static ServerPlayer pillion(final MinecraftServer server) {
		if (pillion == null) {
			pillion = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.nameUUIDFromBytes("horsingaround-pillion".getBytes()), "Pillion"), ClientInformation.createDefault());
			try {
				// (Fabric's fake player's connection, which sends nothing.)
				pillion.connection = FakePlayer.get(server.overworld()).connection.getClass().getConstructor(ServerPlayer.class).newInstance(pillion);
			} catch (final ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
		return pillion;
	}

	// ---- The Glidewing Harness ----

	/** Galloped off a 30-block cliff with the harness on: off the edge, the harness opens, it glides, steers, lands unhurt. */
	private void glider(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Icy's Glidewing Harness: off a 30-block cliff");
		final int x = nextLane();
		final int top = GROUND + 30;
		world.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d -45 %d %d 3 minecraft:stone", x - 3, top - 1, x + 3, top - 1));
		final int id = summon(ctx, world, "american_paint_horse", UPGRADED, x, top);
		onHorse(world, id, horse -> gear(horse, "STABILIZER", item("horse_stabilizer_gear")));
		world.getServer().runCommand("gamemode survival @p");
		board(ctx, world, x, top);
		final float[] health = health(world, id);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		spurs(ctx, input, 3);
		double lowest = top;
		double fastestOpen = 0.0;
		int openTicks = 0;
		double headingBefore = Double.NaN;
		double headingAfter = Double.NaN;
		for (int i = 0; i < 400; i++) {
			ctx.waitTick();
			final double[] now = ctx.computeOnClient(mc -> {
				final Entity v = mc.player.getVehicle();
				return v == null ? null : new double[] {v.getY(), v.yo - v.getY(), v.getX() - v.xo, v.getZ() - v.zo, v.onGround() ? 1 : 0,
					"OPEN".equals(String.valueOf(call(v, "bh_getStabilizerState"))) ? 1 : 0};
			});
			if (now == null) {
				break;
			}
			lowest = Math.min(lowest, now[0]);
			if (now[5] > 0) {
				openTicks++;
				if (openTicks > 10) {
					fastestOpen = Math.max(fastestOpen, now[1]);
				}
				if (openTicks == 30 || openTicks == 33 || openTicks == 36) {
					// (Still legs look the same in all three.)
					sideScreenshot(ctx, "icy_glider_legs_" + openTicks);
				}
				if (openTicks == 15) {
					headingBefore = Math.atan2(-now[2], now[3]) * Mth.RAD_TO_DEG;
					input.lookAt(240.0F, 10.0F);
					screenshot(ctx, "icy_glider_open");
				} else if (openTicks == 45) {
					headingAfter = Math.atan2(-now[2], now[3]) * Mth.RAD_TO_DEG;
				}
			}
			if (now[4] > 0 && now[0] < top - 2) {
				break;
			}
		}
		input.releaseKey(o -> o.keyUp);
		ctx.waitTicks(20);
		check("gallops off the cliff with the harness on (blocks below the top)", top - lowest, 25.0, 31.0);
		check("the harness opens (ticks open)", openTicks, 20, 400);
		check("...and it glides (fastest fall, open, blocks/tick)", fastestOpen, 0.05, 0.4);
		check("...steering in the air (degrees turned)", Double.isNaN(headingAfter) ? 0.0 : Math.abs(Mth.wrapDegrees(headingAfter - headingBefore)), 15.0, 90.0);
		final float[] after = health(world, id);
		check("lands unhurt (the horse's health lost)", health[0] - after[0], 0.0, 0.0);
		check("...and the rider's", health[1] - after[1], 0.0, 0.0);
		world.getServer().runCommand("gamemode creative @p");
		stop(ctx, input);
		dismount(ctx, input);
	}

	// ---- Rearing on command ----

	/** Icy's rear key on the ridden horse: it rears, comes down, and rides on as before; standing and at a trot. */
	private void rear(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Icy's rear key");
		final int x = nextLane();
		final int id = summon(ctx, world, "american_paint_horse", SADDLE, x, GROUND);
		board(ctx, world, x);
		ctx.waitTicks(10);
		check("standing still, it rears (ticks up)", rearOnce(ctx, id), 5, 60);
		// (The point measured on Icy's model sits ahead of its real seat in a rear: the part of the game's shift Icy's keeps
		// is what lands the rider on the saddle, and without it they are in the horse's neck.)
		check("the rider keeps Icy's rear shift, clear of the neck (blocks behind the measured point)", this.rearSeatBehind, 0.35, 0.55);
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		spurs(ctx, input, 1);
		ctx.waitTicks(30);
		check("at a trot, it rears (ticks up)", rearOnce(ctx, id), 5, 60);
		ctx.waitTicks(30);
		check("...then trots on (speed / a trot)", averageSpeed(ctx, 10) / (TROT * attribute(ctx)), 0.8, 1.2);
		check("...its ride carried on", managed(ctx) && ride(ctx, s -> s.gait) == RideTuning.TROT);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/** Sends Icy's rear request for the ridden horse: the ticks it then stood, or 0 if it never did. */
	private int rearOnce(final ClientGameTestContext ctx, final int id) {
		final float restForward = ride(ctx, r -> r.seatForward);
		this.rearSeatBehind = 0.0F;
		ctx.runOnClient(mc -> {
			try {
				ClientPlayNetworking.send((CustomPacketPayload) Class.forName("icy.betterhorses.net.network.BhRearPayload").getConstructor(int.class).newInstance(id));
			} catch (final ReflectiveOperationException e) {
				throw new IllegalStateException("Icy's rear changed", e);
			}
		});
		int up = 0;
		for (int i = 0; i < 80; i++) {
			ctx.waitTick();
			if (ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse h && h.isStanding())) {
				up++;
				if (ctx.computeOnClient(mc -> ((AbstractHorse) mc.player.getVehicle()).getStandAnim(0.0F) >= 1.0F)) {
					this.rearSeatBehind = Math.max(this.rearSeatBehind, ride(ctx, r -> r.animatedForward - r.animatedForwardBase - (r.seatForward - restForward)));
				}
				if (this.rearShots < 2 && (up == 6 || up == 14)) {
					sideScreenshot(ctx, "icy_rear_" + this.rearShots++ + "_" + up);
				}
			} else if (up > 0) {
				break;
			}
		}
		return up;
	}

	// ---- Horseshoes and the medkit ----

	/** Shod with Frost Walker: over powder snow it walks on top (not round it), over water on the ice it leaves. */
	private void horseshoes(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Icy's horseshoes: powder snow and frost walking");
		final int x = nextLane();
		world.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d -6 %d %d -18 minecraft:powder_snow", x - 2, GROUND - 1, x + 2, GROUND - 1));
		world.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d -24 %d %d -40 minecraft:water", x - 3, GROUND - 1, x + 3, GROUND - 1));
		final int id = summon(ctx, world, "american_paint_horse", UPGRADED, x, GROUND);
		onHorse(world, id, horse -> {
			final ItemStack shoes = item("horse_hooves_gear");
			shoes.enchant(horse.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FROST_WALKER), 2);
			gear(horse, "HOOVES", shoes);
			// Shod, onto the powder snow.
			horse.snapTo(x + 0.5, GROUND, -11.5, 180.0F, 0.0F);
		});
		ctx.waitTicks(5);
		// (On Icy's own riding it sinks in: there the rider's client, which moves the horse, never hears of the shoes.)
		board(ctx, world, x);
		ctx.waitTicks(20);
		check("ridden, shod, it stands on powder snow (blocks sunk)", GROUND - ctx.computeOnClient(mc -> mc.player.getVehicle().getY()), 0.0, 0.1);
		screenshot(ctx, "icy_horseshoes_snow");
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		spurs(ctx, input, 1);
		double snowLowest = GROUND;
		double waterLowest = GROUND;
		double off = 0.0;
		double furthest = 0.0;
		for (int i = 0; i < 160 && furthest > -44.0; i++) {
			ctx.waitTick();
			final double[] at = ctx.computeOnClient(mc -> {
				final Entity v = mc.player.getVehicle();
				return v == null ? new double[] {0.0, GROUND, 0.0} : new double[] {v.getX(), v.getY(), v.getZ()};
			});
			furthest = Math.min(furthest, at[2]);
			if (Math.abs(at[0] - (x + 0.5)) >= 1.0 && off < 1.0) {
				log("  (off its line at z %.1f, y %.2f)", at[2], at[1]);
			}
			off = Math.max(off, Math.abs(at[0] - (x + 0.5)));
			if (at[2] < -6.5 && at[2] > -17.5) {
				snowLowest = Math.min(snowLowest, at[1]);
			} else if (at[2] < -24.5 && at[2] > -39.5) {
				waterLowest = Math.min(waterLowest, at[1]);
			}
		}
		stop(ctx, input);
		check("trots straight over the powder snow and the water (furthest, blocks)", -furthest, 44.0, 200.0);
		check("...on its line, not round them (blocks off it, most)", off, 0.0, 1.0);
		check("on top of the powder snow (blocks sunk, most)", GROUND - snowLowest, 0.0, 0.1);
		check("over the water on frosted ice (blocks sunk, most)", GROUND - waterLowest, 0.0, 0.1);
		check("...that its shoes froze (blocks of it)", world.getServer().computeOnServer(server -> count(server.overworld(), x - 3, GROUND - 1, -40, x + 3, GROUND - 1, -24, Blocks.FROSTED_ICE)), 10, 200);
		dismount(ctx, input);
	}

	/** A hit that would take the ridden horse below half health uses the medkit up and heals it. */
	private void medkit(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Icy's medkit");
		final int x = nextLane();
		final int id = summon(ctx, world, "american_paint_horse", UPGRADED, x, GROUND);
		onHorse(world, id, horse -> gear(horse, "MEDKIT", item("horse_medkit_gear")));
		board(ctx, world, x);
		final boolean[] used = world.getServer().computeOnServer(server -> {
			final AbstractHorse horse = horse(server, id);
			horse.hurtServer(server.overworld(), server.overworld().damageSources().generic(), horse.getMaxHealth() * 0.6F);
			return new boolean[] {gearOf(horse, "MEDKIT").isEmpty(), horse.hasEffect(MobEffects.REGENERATION)};
		});
		check("a hit to below half uses the medkit up", used[0]);
		check("...and heals the horse (regeneration)", used[1]);
		ctx.waitTicks(30);
		check("this mod still rides it", managed(ctx));
		dismount(ctx, input);
	}

	/** With the charge switched off by its rider (Icy's Toggle Horse Charge key), the trample doesn't bash a hostile mob. */
	private void chargeToggle(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Icy's Toggle Horse Charge key");
		final int x = nextLane();
		summon(ctx, world, "american_paint_horse", SADDLE, x, GROUND);
		world.getServer().runCommand(String.format(Locale.ROOT, "summon minecraft:husk %d.5 %d -40.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"unbashed\"]}", x, GROUND));
		board(ctx, world, x);
		world.getServer().runOnServer(server -> riderCharge(server, false));
		galloping(ctx, input);
		double reached = 0.0;
		for (int i = 0; i < 40; i++) {
			ctx.waitTick();
			reached = Math.min(reached, ctx.computeOnClient(mc -> mc.player.getVehicle().getZ()));
		}
		check("...galloping up to the husk (furthest, blocks)", -reached, 39.5, 200.0);
		final boolean spared = world.getServer().computeOnServer(server -> server.overworld().getEntities(
			net.minecraft.world.level.entity.EntityTypeTest.forClass(LivingEntity.class), h -> h.entityTags().contains("unbashed")
		).stream().allMatch(h -> h.getHealth() >= h.getMaxHealth()));
		world.getServer().runOnServer(server -> riderCharge(server, true));
		check("its rider switched the charge off: galloping through a husk, it doesn't bash it", spared);
		stop(ctx, input);
		dismount(ctx, input);
		world.getServer().runCommand("kill @e[tag=unbashed]");
	}

	/** Icy's Toggle Horse Charge, for the (one) player. */
	private static void riderCharge(final MinecraftServer server, final boolean on) {
		try {
			Class.forName("icy.betterhorses.net.feature.HorseCombat").getMethod("riderCharge", UUID.class, boolean.class)
				.invoke(null, server.getPlayerList().getPlayers().getFirst().getUUID(), on);
		} catch (final ReflectiveOperationException e) {
			throw new IllegalStateException("Icy's charge toggle changed", e);
		}
	}

	// ---- Mounted comforts ----

	/** Mining from the saddle at Icy's 75%, and stepping off on the side the rider looks. */
	private void comforts(final ClientGameTestContext ctx, final TestInput input, final TestSingleplayerContext world) {
		section("Icy's mounted comforts: mining, the dismount side");
		final int x = nextLane();
		summon(ctx, world, "american_paint_horse", SADDLE, x, GROUND);
		board(ctx, world, x);
		ctx.waitTicks(10);
		check("mines from the saddle at Icy's pace (a bare hand's speed on stone)",
			world.getServer().computeOnServer(server -> (double) server.getPlayerList().getPlayers().getFirst().getDestroySpeed(Blocks.STONE.defaultBlockState())), 0.7, 0.8);
		final float horseYaw = ctx.computeOnClient(mc -> mc.player.getVehicle().getYRot());
		input.lookAt(horseYaw + 80.0F, 10.0F);
		ctx.waitTicks(3);
		final int horse = ctx.computeOnClient(mc -> mc.player.getVehicle().getId());
		input.holdKeyFor(o -> o.keyShift, 3);
		ctx.waitTicks(5);
		final double side = ctx.computeOnClient(mc -> {
			final Entity h = mc.level.getEntity(horse);
			final float yaw = h.getYRot() * Mth.DEG_TO_RAD;
			// To the horse's right: (-cos, -sin) of its yaw.
			return (mc.player.getX() - h.getX()) * -Mth.cos(yaw) + (mc.player.getZ() - h.getZ()) * -Mth.sin(yaw);
		});
		check("steps off on the side the rider looks (blocks to the horse's right)", side, 0.5, 3.0);
	}

	/** The host picks a riding style (as from the settings screen in singleplayer). */
	private static void setRidingStyle(final ClientGameTestContext ctx, final TestSingleplayerContext world, final HorseConfig.RidingStyle style) {
		world.getServer().runOnServer(server -> {
			HorseConfig.get().ridingStyle = style;
			HorsingAround.styleChanged(server);
		});
		ctx.waitTicks(3);
	}

	// ---- Icy's mod, by reflection ----

	private static ItemStack item(final String name) {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(ICY + name)));
	}

	private static int gearSlot(final String slot) {
		try {
			return ((Enum<?>) Class.forName("icy.betterhorses.net.inventory.GearSlot").getField(slot).get(null)).ordinal();
		} catch (final ReflectiveOperationException e) {
			throw new IllegalStateException("Icy's gear slots changed", e);
		}
	}

	/** Puts gear in the horse's slot, as the horse screen does (its container syncs the gear's flags). */
	private static void gear(final AbstractHorse horse, final String slot, final ItemStack stack) {
		((SimpleContainer) call(horse, "bh_getGearContainer")).setItem(gearSlot(slot), stack);
	}

	private static ItemStack gearOf(final AbstractHorse horse, final String slot) {
		return ((SimpleContainer) call(horse, "bh_getGearContainer")).getItem(gearSlot(slot));
	}

	private static String cartUuid(final AbstractHorse horse) {
		final Entity cart = cartOf(horse);
		return cart == null ? "none" : cart.getStringUUID();
	}

	private static Entity cartOf(final AbstractHorse horse) {
		final Object uuid = call(horse, "bh_getCartId");
		return uuid instanceof UUID u ? ((ServerLevel) horse.level()).getEntity(u) : null;
	}

	/** Icy's cart drag on the horse's speed (its modifier's amount, 0 without one). */
	private static double drag(final AbstractHorse horse) {
		final AttributeModifier modifier = horse.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Identifier.parse(ICY + "gear/cart"));
		return modifier == null ? 0.0 : modifier.amount();
	}

	/** Calls one of Icy's public methods by name (the first with that many arguments). */
	private static Object call(final Object target, final String name, final Object... args) {
		for (final Method method : target.getClass().getMethods()) {
			if (method.getName().equals(name) && method.getParameterCount() == args.length) {
				try {
					return method.invoke(target, args);
				} catch (final ReflectiveOperationException e) {
					throw new IllegalStateException("Icy's " + name + " failed", e);
				}
			}
		}
		throw new IllegalStateException("Icy's " + name + " is gone");
	}

	// ---- helpers ----

	private int nextLane() {
		return 20 + 24 * this.lane++;
	}

	/** Summons one of Icy's breeds at (x, y, 0) facing north; its entity id. */
	private static int summon(final ClientGameTestContext ctx, final TestSingleplayerContext world, final String breed, final String data, final int x, final int y) {
		final TestServerContext server = world.getServer();
		server.runCommand(String.format(Locale.ROOT, "tp @p %d %d 6 180 10", x, y));
		ctx.waitTicks(5);
		world.getConnection().waitForChunksRender();
		server.runCommand(String.format(Locale.ROOT, "summon %s%s %d.5 %d 0.5 {%s,Rotation:[180f,0f],Tags:[\"icy_%d\"]}", ICY, breed, x, y, data, x));
		ctx.waitTicks(2);
		return server.computeOnServer(s -> s.overworld().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(AbstractHorse.class),
			h -> h.entityTags().contains("icy_" + x)).getFirst().getId());
	}

	private void board(final ClientGameTestContext ctx, final TestSingleplayerContext world, final int x) {
		board(ctx, world, x, GROUND);
	}

	private void board(final ClientGameTestContext ctx, final TestSingleplayerContext world, final int x, final int y) {
		for (int attempt = 0; attempt < 10 && !ctx.computeOnClient(mc -> mc.player.getVehicle() != null); attempt++) {
			world.getServer().runCommand("ride @p mount @e[tag=icy_" + x + ",limit=1]");
			ctx.waitTicks(5);
		}
		ctx.waitFor(mc -> mc.player.getVehicle() != null);
		ctx.waitTicks(5);
	}

	private static AbstractHorse horse(final MinecraftServer server, final int id) {
		return (AbstractHorse) server.overworld().getEntity(id);
	}

	private static void onHorse(final TestSingleplayerContext world, final int id, final java.util.function.Consumer<AbstractHorse> action) {
		world.getServer().runOnServer(server -> action.accept(horse(server, id)));
	}

	/** The horse's health and its rider's, on the server. */
	private static float[] health(final TestSingleplayerContext world, final int id) {
		return world.getServer().computeOnServer(server -> {
			final AbstractHorse horse = horse(server, id);
			final LivingEntity rider = horse.getControllingPassenger();
			return new float[] {horse.getHealth(), rider == null ? 0.0F : rider.getHealth()};
		});
	}

	/** How far ahead of the horse's middle the passenger is, blocks (behind is negative). */
	private static double forwardOf(final Entity horse, final Entity passenger) {
		final float yaw = horse.getYRot() * Mth.DEG_TO_RAD;
		return (passenger.getX() - horse.getX()) * -Mth.sin(yaw) + (passenger.getZ() - horse.getZ()) * Mth.cos(yaw);
	}

	private static int count(final ServerLevel level, final int x0, final int y0, final int z0, final int x1, final int y1, final int z1, final net.minecraft.world.level.block.Block block) {
		int n = 0;
		for (final BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
			if (level.getBlockState(pos).is(block)) {
				n++;
			}
		}
		return n;
	}

	private static boolean managed(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof RideStateHolder holder && holder.horsingaround$managed());
	}

	private static double attribute(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> ((LivingEntity) mc.player.getVehicle()).getAttributeValue(Attributes.MOVEMENT_SPEED));
	}

	private static double gallopFor(final ClientGameTestContext ctx) {
		return attribute(ctx) * GALLOP;
	}

	/** W and three spurs, then on at a gallop for 50 ticks (W stays held). */
	private static void galloping(final ClientGameTestContext ctx, final TestInput input) {
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		spurs(ctx, input, 3);
		ctx.waitTicks(50);
	}

	/** From a standstill, W and three spurs: the ticks until it is at 95% of its gallop (W stays held). */
	private static int ticksToGallop(final ClientGameTestContext ctx, final TestInput input) {
		input.lookAt(180.0F, 10.0F);
		input.holdKey(o -> o.keyUp);
		int ticks = 0;
		while (ticks < 300 && ride(ctx, s -> s.speed) < RideTuning.GAIT_SPEED[RideTuning.GALLOP] * 0.95F) {
			if (ticks >= 2 && ticks < 26 && ticks % 6 == 2) {
				input.pressKey(o -> o.keySprint);
			}
			ctx.waitTick();
			ticks++;
		}
		return ticks;
	}

	/** Spurs on by {@code steps} gaits once W is held (a spur with W not yet down, or a tick after another, doesn't count). */
	private static void spurs(final ClientGameTestContext ctx, final TestInput input, final int steps) {
		ctx.waitTicks(2);
		for (int i = 0; i < steps; i++) {
			input.pressKey(o -> o.keySprint);
			ctx.waitTicks(6);
		}
	}

	private static void stop(final ClientGameTestContext ctx, final TestInput input) {
		input.releaseKey(o -> o.keyUp);
		input.pressKey(o -> o.keyDown);
		ctx.waitTicks(60);
	}

	private void dismount(final ClientGameTestContext ctx, final TestInput input) {
		input.releaseKey(o -> o.keyUp);
		input.holdKeyFor(o -> o.keyShift, 3);
		ctx.waitTicks(5);
		check("dismounts with sneak", ctx.computeOnClient(mc -> mc.player.getVehicle() == null));
	}

	private static <T> T ride(final ClientGameTestContext ctx, final Function<RideState, T> read) {
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
		// (No HUD: the hotbar would hide the horse's legs.)
		ctx.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		log("  screenshot %s -> %s", name, ctx.takeScreenshot("horsingaround_" + name));
		ctx.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	/** Swings the view 90 degrees for one rendered frame to see the horse broadside (no tick passes). */
	private void sideScreenshot(final ClientGameTestContext ctx, final String name) {
		final float yaw = ctx.computeOnClient(mc -> mc.player.getYRot());
		ctx.runOnClient(mc -> {
			mc.player.setYRot(yaw + 90.0F);
			mc.player.yRotO = yaw + 90.0F;
		});
		screenshot(ctx, name);
		ctx.runOnClient(mc -> {
			mc.player.setYRot(yaw);
			mc.player.yRotO = yaw;
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
		System.out.println("[icy-test] " + line);
	}

	private void writeReport() {
		final Path file = FabricLoader.getInstance().getGameDir().resolve("horsingaround-icy-report.txt");
		try {
			Files.write(file, this.report);
		} catch (final IOException e) {
			throw new RuntimeException("Could not write " + file, e);
		}
	}
}
