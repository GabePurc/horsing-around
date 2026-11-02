package dev.horsingaround.gametest;

import com.mojang.authlib.GameProfile;
import dev.horsingaround.HorsingAround;
import dev.horsingaround.ride.HorseConfig;
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

			cart(ctx, input, world);
			plough(ctx, input, world);
			wagon(ctx, input, world);
			secondRider(ctx, input, world);
			glider(ctx, input, world);
			rear(ctx, input, world);
			horseshoes(ctx, input, world);
			medkit(ctx, input, world);
			chargeToggle(ctx, input, world);
			comforts(ctx, input, world);

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
		galloping(ctx, input);
		check("gallops at its own top speed, the drag included (blocks/tick)", averageSpeed(ctx, 10), gallop * 0.85, gallop * 1.15);
		screenshot(ctx, "icy_cart_gallop");
		sideScreenshot(ctx, "icy_cart_gallop_side");
		// (Icy's cart swings up to 8 degrees behind the body yaw it last saw, which may be a tick old.)
		check("...swinging round with it (degrees behind the horse's body, most)", followThroughTurn(ctx, input, "icy_cart"), 0.0, 20.0);
		check("the same cart all along (never dropped and put back)", cartId.equals(world.getServer().computeOnServer(server -> cartUuid(horse(server, id)))));
		stop(ctx, input);
		dismount(ctx, input);
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
		galloping(ctx, input);
		check("gallops at its own top speed (blocks/tick)", averageSpeed(ctx, 10), gallop * 0.85, gallop * 1.15);
		screenshot(ctx, "icy_wagon_gallop");
		check("...swinging round with it as on a cart (degrees behind the horse's body, most)", followThroughTurn(ctx, input, "icy_wagon"), 0.0, 20.0);
		stop(ctx, input);
		dismount(ctx, input);
	}

	/** Gallops through a quarter turn with a jump in it: how far the client's cart swings behind the horse's body, most (degrees). */
	private double followThroughTurn(final ClientGameTestContext ctx, final TestInput input, final String shot) {
		double turn = 0.0;
		double lag = 0.0;
		boolean present = true;
		for (int i = 0; i < 40; i++) {
			input.lookAt(180.0F + Math.min(i, 20) * 4.5F, 10.0F);
			if (i == 25) {
				input.pressKey(o -> o.keyJump);
			}
			ctx.waitTick();
			final double[] follow = ctx.computeOnClient(mc -> {
				final Entity horse = mc.player.getVehicle();
				final Object cart = horse == null ? null : call(horse, "bh_getCartEntity");
				if (!(cart instanceof Entity c)) {
					return null;
				}
				final LivingEntity h = (LivingEntity) horse;
				// (The cart takes the horse's body yaw on its own tick, before or after the horse's: Icy's slack is behind one of them.)
				return new double[] {Math.abs(Mth.wrapDegrees(h.yBodyRot - h.yBodyRotO)),
					Math.min(Math.abs(Mth.wrapDegrees(c.getYRot() - h.yBodyRot)), Math.abs(Mth.wrapDegrees(c.getYRot() - h.yBodyRotO)))};
			});
			if (follow == null) {
				present = false;
				continue;
			}
			turn = Math.max(turn, follow[0]);
			lag = Math.max(lag, follow[1]);
			if (i == 12) {
				sideScreenshot(ctx, shot + "_turn");
			}
		}
		check("the cart is there all through the turn and the jump", present);
		log("  (the cart swung up to %.1f degrees behind the horse's body, which turned up to %.1f degrees a tick)", lag, turn);
		return lag;
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
	private static int rearOnce(final ClientGameTestContext ctx, final int id) {
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
		log("  screenshot %s -> %s", name, ctx.takeScreenshot("horsingaround_" + name));
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
