package dev.horsingaround.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.datafixers.util.Pair;
import dev.horsingaround.client.config.HorseSettingsScreen;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.client.cosmetic.Supporters;
import dev.horsingaround.ride.HorseConfig;
import dev.horsingaround.ride.RideStateHolder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Not a check: films the store page's clips and stills in a generated world with Fresh Animations, into
 * {@code build/run/clientGameTest/store/} (a folder of frames per clip, stills beside them). Each shot's ground (open
 * savanna and plains, a badlands route that gets a canyon carved across it, a river with low banks) is scouted from the
 * terrain noise and checked on the generated ground, so the same seed always films the same places. Run with
 * {@code ./gradlew runClientGameTest -Ptests=store}, some shots with {@code -Pscenario=gaits,jump} ({@code scout} compares
 * seeds); {@code scripts/store-media.sh} turns the frames into {@code docs/media/}.
 */
public final class StoreMediaTest implements FabricClientGameTest {
	private static final String SEED = "mustang";
	private static final int WIDTH = 1280;
	private static final int HEIGHT = 720;
	/** Clips are widescreen, like a film (2.39:1). */
	private static final int CLIP_HEIGHT = 536;
	/** Clip frames a second of screen time, a film's. */
	private static final int FPS = 24;
	/** Pictures blended into each clip frame (motion blur). */
	private static final int SHUTTER_SAMPLES = 4;
	private static final int CANTER = 2;
	private static final int GALLOP = 3;
	/** Chestnut with white stockings and blaze; bay; dark brown with white spots (variant | markings << 8). */
	private static final int CHESTNUT = 2 | 1 << 8;
	private static final int BAY = 3;
	private static final int DARK = 6 | 3 << 8;
	/** Late afternoon: the sun low in the west (13 degrees up), the light still full. */
	private static final int GOLDEN_HOUR = 11200;

	private enum Ground {
		OPEN, RIVER
	}

	/** Where a shot starts (standing height) and the heading to ride. */
	private record Spot(BlockPos start, float yaw, String biome) {
	}

	/** Ground to scout for and the shots filmed there. */
	private record Want(String name, Ground ground, int run, Predicate<Holder<Biome>> biome, float yawFrom, float yawTo, String... shots) {
	}

	private static final List<Want> WANTS = List.of(
		// Heading roughly south, so filming from the horse's left looks west into the evening sun.
		new Want("savanna", Ground.OPEN, 90, any(Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU), -35.0F, 35.0F, "gaits", "jump", "rider", "hat", "settings"),
		new Want("plains", Ground.OPEN, 110, any(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW), 0.0F, 360.0F, "turn", "stamina"),
		// The sunset shots look west into the sun: heading roughly south, filmed from the horse's left.
		new Want("badlands", Ground.OPEN, 80, any(Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.ERODED_BADLANDS), -35.0F, 35.0F, "sunset", "cliff"),
		new Want("river", Ground.RIVER, 0, any(Biomes.RIVER), 0.0F, 360.0F, "river"));

	private static final Logger LOGGER = LoggerFactory.getLogger("horsingaround-store");
	private final List<String> log = new ArrayList<>();
	private Path out;
	private String only;

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!System.getProperty("horsingaround.tests", "").contains("store")) {
			return;
		}
		this.only = System.getProperty("horsingaround.scenario", "");
		this.out = FabricLoader.getInstance().getGameDir().resolve("store");
		if (this.only.contains("scout")) {
			// How far each shot's ground is from spawn in a few worlds, to pick a seed that keeps the filming close.
			for (final String seed : new String[] {SEED, "saddle up", "happy trails", "giddy up", "tumbleweed", "high noon", "dust devil", "horsing around"}) {
				try (TestSingleplayerContext world = this.world(ctx, seed)) {
					final StringBuilder line = new StringBuilder("seed \"" + seed + "\":");
					for (final Want want : WANTS) {
						final Spot spot = this.scout(world.getServer(), want);
						line.append(spot == null ? " " + want.name + " none;" : String.format(Locale.ROOT, " %s %d;", want.name,
							(int) Math.sqrt(spot.start.distSqr(BlockPos.ZERO.atY(spot.start.getY())))));
					}
					this.log.add(line.toString());
					LOGGER.info("[store] {}", line);
				}
			}
			this.writeLog();
			return;
		}
		GalleryTest.enableFreshAnimations(ctx);
		try (TestSingleplayerContext world = this.world(ctx, SEED)) {
			// Shots at the window's size rather than resized offscreen: shader packs only render to the window properly.
			ctx.getInput().resizeWindow(WIDTH, HEIGHT);
			ctx.runOnClient(mc -> {
				// Far enough that the haze, not the edge of the world, is what the distance fades into.
				mc.options.renderDistance().set(20);
				// The server only sends as far as the client last told it (the test framework's 5 chunks) until told again.
				mc.options.broadcastOptions();
				mc.options.fov().set(60);
				// The run folder's options can carry a light test game's settings; film with the full look, sky and all.
				mc.options.cloudStatus().set(CloudStatus.FANCY);
				mc.options.particles().set(ParticleStatus.ALL);
				mc.options.ambientOcclusion().set(true);
				mc.options.entityShadows().set(true);
				mc.options.biomeBlendRadius().set(2);
				mc.options.mipmapLevels().set(4);
				mc.options.fovEffectScale().set(0.0);
				// The test game is a background window most of the time: don't let it idle down to a few frames a second.
				mc.options.enableVsync().set(false);
				mc.options.framerateLimit().set(260);
				mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
				hud(mc, false);
			});
			for (final Want want : WANTS) {
				if (!this.wanted(want.shots)) {
					continue;
				}
				final Spot spot = this.scout(world.getServer(), want);
				if (spot == null) {
					continue;
				}
				for (final String shot : want.shots) {
					if (!this.wanted(shot)) {
						continue;
					}
					LOGGER.info("[store] filming {}", shot);
					switch (shot) {
						case "gaits" -> this.gaits(ctx, world, spot);
						case "jump" -> this.jump(ctx, world, spot);
						case "rider" -> this.rider(ctx, world, spot);
						case "hat" -> this.hat(ctx, world, spot);
						case "settings" -> this.settings(ctx, world, spot);
						case "turn" -> this.turn(ctx, world, spot);
						case "stamina" -> this.stamina(ctx, world, spot);
						case "sunset" -> this.sunset(ctx, world, spot);
						case "cliff" -> this.cliff(ctx, world, spot);
						case "river" -> this.river(ctx, world, spot);
						default -> {
						}
					}
				}
			}
			ctx.runOnClient(mc -> hud(mc, true));
		}
		this.writeLog();
	}

	private TestSingleplayerContext world(final ClientGameTestContext ctx, final String seed) {
		final TestSingleplayerContext world = ctx.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(settings -> {
				settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				settings.setSeed(seed);
				// No villages or ruined portals wandering into shots (the terrain is the same without them).
				settings.setGenerateStructures(false);
			})
			.create();
		final TestServerContext server = world.getServer();
		for (final String rule : new String[] {"advance_time false", "advance_weather false", "spawn_mobs false", "spawn_wandering_traders false",
			"send_command_feedback false"}) {
			server.runCommand("gamerule " + rule);
		}
		server.runCommand("weather clear");
		return world;
	}

	private void writeLog() {
		try {
			Files.createDirectories(this.out);
			Files.write(this.out.resolve("store-log.txt"), this.log);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// ---- Shots ----

	/**
	 * Walk, trot, canter, gallop: tracking low alongside with the sun behind the horse, swinging round ahead of it as it
	 * picks up, then the camera pulls up and lets it gallop past and away.
	 */
	private void gaits(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final AbstractHorse horse = this.mount(ctx, world, spot, CHESTNUT, GOLDEN_HOUR);
		final TestInput input = ctx.getInput();
		final float sun = intoTheSun(spot);
		ctx.runOnClient(mc -> {
			FilmCamera.shoot(horse, 0.08F);
			FilmCamera.key(0, FilmCamera.Setup.orbit(sun - 8.0F, 5.5F, 1.1F, 1.4F, 0.0F, 40.0F));
			FilmCamera.key(40, FilmCamera.Setup.orbit(sun, 6.0F, 1.1F, 1.4F, 0.3F, 40.0F));
			FilmCamera.key(80, FilmCamera.Setup.orbit(sun + 35.0F, 7.0F, 1.2F, 1.4F, 1.0F, 40.0F));
			FilmCamera.key(112, FilmCamera.Setup.orbit(sun + 60.0F, 8.0F, 1.3F, 1.4F, 1.5F, 38.0F));
			FilmCamera.key(122, FilmCamera.Setup.held(1.4F, 1.5F, 38.0F));
			FilmCamera.key(160, FilmCamera.Setup.held(1.3F, 0.0F, 26.0F));
		});
		input.holdKey(o -> o.keyUp);
		this.record(ctx, "gaits", 160, REAL_TIME, t -> {
			if (t == 40 || t == 75 || t == 110) {
				input.pressKey(o -> o.keySprint);
			}
			input.lookAt(spot.yaw, 12.0F);
		});
		this.still(ctx, "gallery_gaits");
		this.stop(ctx);
	}

	/** A jump at a gallop: low and side on with the sun behind, slowing right down through the jump, then swinging behind as it gallops on. */
	private void jump(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final AbstractHorse horse = this.mount(ctx, world, spot, BAY, GOLDEN_HOUR);
		final TestInput input = ctx.getInput();
		final float sun = intoTheSun(spot);
		this.gait(ctx, GALLOP);
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> {
			FilmCamera.shoot(horse, 0.1F);
			FilmCamera.key(0, FilmCamera.Setup.orbit(sun + 50.0F, 8.0F, 1.1F, 1.4F, 1.0F, 40.0F));
			FilmCamera.key(22, FilmCamera.Setup.orbit(sun, 6.0F, 1.0F, 1.6F, 0.0F, 36.0F));
			FilmCamera.key(40, FilmCamera.Setup.orbit(sun - 5.0F, 6.0F, 1.0F, 1.6F, 0.0F, 36.0F));
			FilmCamera.key(70, FilmCamera.Setup.orbit(sun - 60.0F, 9.0F, 1.8F, 1.4F, 0.0F, 42.0F));
		});
		this.record(ctx, "jump", 70, slowMotion(25.0, 38.0, 0.4, 5.0), t -> {
			if (t == 26) {
				input.pressKey(o -> o.keyJump);
			}
			input.lookAt(spot.yaw, 12.0F);
		});
		this.stop(ctx);
	}

	/** The rider in the saddle at a canter, close up from ahead. */
	private void rider(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final AbstractHorse horse = this.mount(ctx, world, spot, CHESTNUT, 11000);
		this.gait(ctx, CANTER);
		ctx.waitTicks(30);
		FilmCamera.film(horse, 150.0F, 4.2F, 1.7F, 9.0F, 0.0F, 0.15F);
		for (int i = 0; i < 4; i++) {
			this.steer(ctx, spot.yaw, 6);
			this.still(ctx, "rider_" + i);
		}
		this.stop(ctx);
	}

	/** The supporters' cowboy hat on a rider at a canter, close up from ahead. */
	private void hat(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final AbstractHorse horse = this.mount(ctx, world, spot, CHESTNUT, 11000);
		final UUID me = ctx.computeOnClient(mc -> mc.player.getUUID());
		ctx.runOnClient(mc -> {
			Supporters.addForTesting(me);
			HorseConfig.get().hat = true;
			HorseConfig.get().hatColor = 0x6B4226;
			Hats.sendOwn();
		});
		this.gait(ctx, CANTER);
		ctx.waitTicks(30);
		FilmCamera.film(horse, 140.0F, 3.4F, 2.0F, 10.0F, 0.0F, 0.15F);
		for (int i = 0; i < 4; i++) {
			this.steer(ctx, spot.yaw, 6);
			this.still(ctx, "hat_" + i);
		}
		ctx.runOnClient(mc -> {
			HorseConfig.get().hat = false;
			Hats.sendOwn();
			Supporters.removeForTesting(me);
		});
		this.stop(ctx);
	}

	/** The settings screen over the world, sitting on a horse. */
	private void settings(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		this.mount(ctx, world, spot, BAY, 9500);
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.resizeGui();
		});
		ctx.setScreen(() -> new HorseSettingsScreen(null));
		ctx.waitTicks(5);
		this.still(ctx, "settings");
		ctx.setScreen(() -> null);
		this.stop(ctx);
	}

	/**
	 * A gallop that swings wide one way and then the other: from high above and behind, so the arcs show, then cut to low
	 * in front as it leans into the second turn, slowing down through the lean.
	 */
	private void turn(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final AbstractHorse horse = this.mount(ctx, world, spot, CHESTNUT, GOLDEN_HOUR);
		final TestInput input = ctx.getInput();
		this.gait(ctx, GALLOP);
		ctx.waitTicks(25);
		ctx.runOnClient(mc -> {
			// The heading followed slowly, so from above the camera hangs back through the turns and the curve shows.
			FilmCamera.shoot(horse, 0.06F);
			FilmCamera.key(0, FilmCamera.Setup.orbit(0.0F, 7.0F, 7.0F, 0.9F, 2.5F, 42.0F));
			FilmCamera.key(56, FilmCamera.Setup.orbit(-15.0F, 8.0F, 8.0F, 0.9F, 2.5F, 42.0F));
			FilmCamera.cut(58, FilmCamera.Setup.orbit(165.0F, 7.0F, 1.2F, 1.6F, 0.0F, 36.0F));
			FilmCamera.key(105, FilmCamera.Setup.orbit(200.0F, 7.5F, 1.2F, 1.6F, 0.0F, 36.0F));
			FilmCamera.key(130, FilmCamera.Setup.orbit(215.0F, 9.0F, 1.6F, 1.5F, 0.0F, 40.0F));
		});
		final float[] yaw = {spot.yaw};
		this.record(ctx, "turn", 130, slowMotion(78.0, 96.0, 0.5, 6.0), t -> {
			if (t >= 20 && t < 40) {
				yaw[0] += 3.5F;
			} else if (t >= 70 && t < 100) {
				yaw[0] -= 3.5F;
			}
			input.lookAt(yaw[0], 12.0F);
		});
		this.stop(ctx);
	}

	/** The stamina bar running low on a long gallop (the HUD on). */
	private void stamina(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		this.mount(ctx, world, spot, BAY, 10500);
		this.gait(ctx, GALLOP);
		ctx.runOnClient(mc -> {
			((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().stamina = 0.3F;
			hud(mc, true);
		});
		this.steer(ctx, spot.yaw, 25);
		ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		this.still(ctx, "stamina");
		ctx.runOnClient(mc -> hud(mc, false));
		this.stop(ctx);
	}

	/** Wide shots galloping through the badlands at sunset (for the Modrinth gallery). */
	private void sunset(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final AbstractHorse horse = this.mount(ctx, world, spot, CHESTNUT, 12650);
		this.gait(ctx, GALLOP);
		this.steer(ctx, spot.yaw, 30);
		final float[][] angles = {{80.0F, 7.5F}, {95.0F, 9.0F}, {65.0F, 8.0F}, {120.0F, 8.0F}};
		for (int i = 0; i < angles.length; i++) {
			FilmCamera.film(horse, angles[i][0], angles[i][1], 1.6F, 6.0F, 2.0F, 0.1F);
			this.steer(ctx, spot.yaw, 8);
			this.still(ctx, "sunset_" + i);
		}
		this.stop(ctx);
	}

	/**
	 * Galloping at the edge of a canyon carved across the route 38+ blocks ahead (badlands are terracotta all the way down,
	 * so the walls look like any canyon's): a long lens from over the drop as it charges at the edge, widening as it comes,
	 * then, the moment it refuses, a cut to close beside it and slow motion through the head toss.
	 */
	private void cliff(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final AbstractHorse horse = this.mount(ctx, world, spot, DARK, GOLDEN_HOUR);
		final TestServerContext server = world.getServer();
		final double fx = -Mth.sin(spot.yaw * Mth.DEG_TO_RAD);
		final double fz = Mth.cos(spot.yaw * Mth.DEG_TO_RAD);
		final int y = ctx.computeOnClient(mc -> mc.player.getBlockY());
		for (int w = -36; w <= 36; w += 3) {
			final int cx = Mth.floor(spot.start.getX() + 0.5 + fx * 44.0 + fz * w);
			final int cz = Mth.floor(spot.start.getZ() + 0.5 + fz * 44.0 - fx * w);
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", cx - 6, y - 18, cz - 6, cx + 6, y + 10, cz + 6));
		}
		ctx.waitTicks(40);
		final TestInput input = ctx.getInput();
		// Hanging over the drop, a little to the horse's left, looking back up the route.
		final Vec3 overTheDrop = new Vec3(spot.start.getX() + 0.5 + fx * 42.0 + fz * 4.0, y + 2.5, spot.start.getZ() + 0.5 + fz * 42.0 - fx * 4.0);
		final float side = intoTheSun(spot);
		ctx.runOnClient(mc -> {
			FilmCamera.shoot(horse, 0.1F);
			FilmCamera.key(0, FilmCamera.Setup.fixed(overTheDrop, 1.4F, 0.0F, 16.0F));
			FilmCamera.key(60, FilmCamera.Setup.fixed(overTheDrop, 1.4F, 0.0F, 40.0F));
		});
		final int shakeBefore = ctx.computeOnClient(mc -> ((RideStateHolder) horse).horsingaround$ride().headShakeStart);
		final double[] refused = {Double.NaN};
		input.holdKey(o -> o.keyUp);
		this.record(ctx, "cliff", 120, t -> Double.isNaN(refused[0]) ? 1.0 : slowMotion(refused[0] + 4.0, refused[0] + 24.0, 0.35, 4.0).at(t), t -> {
			if (t == 2 || t == 6 || t == 10) {
				input.pressKey(o -> o.keySprint);
			}
			input.lookAt(spot.yaw, 12.0F);
			if (Double.isNaN(refused[0]) && ctx.computeOnClient(mc -> ((RideStateHolder) horse).horsingaround$ride().headShakeStart) != shakeBefore) {
				refused[0] = t;
				ctx.runOnClient(mc -> {
					FilmCamera.cut(t + 1, FilmCamera.Setup.orbit(side, 5.0F, 1.2F, 1.8F, 0.0F, 32.0F));
					FilmCamera.key(t + 40, FilmCamera.Setup.orbit(side - 25.0F, 6.0F, 1.6F, 1.6F, 0.0F, 34.0F));
				});
			}
		});
		this.still(ctx, "gallery_cliff");
		this.stop(ctx);
	}

	/**
	 * Trotting into a river, swimming across with the head up and climbing out the far side: low at the water's edge,
	 * swinging round in front while it swims, then craning up and back over the river as it climbs out.
	 */
	private void river(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final AbstractHorse horse = this.mount(ctx, world, spot, CHESTNUT, GOLDEN_HOUR);
		final TestInput input = ctx.getInput();
		ctx.runOnClient(mc -> {
			FilmCamera.shoot(horse, 0.06F);
			FilmCamera.key(0, FilmCamera.Setup.orbit(80.0F, 7.0F, 1.1F, 1.1F, 1.0F, 40.0F));
			FilmCamera.key(50, FilmCamera.Setup.orbit(95.0F, 6.0F, 0.8F, 1.0F, 0.5F, 38.0F));
			FilmCamera.key(110, FilmCamera.Setup.orbit(140.0F, 6.0F, 0.8F, 1.0F, 0.0F, 36.0F));
			FilmCamera.key(160, FilmCamera.Setup.orbit(150.0F, 7.0F, 1.0F, 1.2F, 0.0F, 38.0F));
			FilmCamera.key(220, FilmCamera.Setup.orbit(60.0F, 11.0F, 6.0F, 1.2F, 1.0F, 45.0F));
		});
		input.holdKey(o -> o.keyUp);
		this.record(ctx, "river", 220, REAL_TIME, t -> {
			if (t == 4) {
				input.pressKey(o -> o.keySprint);
			}
			input.lookAt(spot.yaw, 12.0F);
		});
		this.still(ctx, "gallery_river");
		this.stop(ctx);
	}

	// ---- Riding and filming ----

	/** The orbit angle that films the horse from the side looking west, into the evening sun (the sun crosses east to west). */
	private static float intoTheSun(final Spot spot) {
		return Mth.wrapDegrees(90.0F - spot.yaw);
	}

	private boolean wanted(final String... shots) {
		if (this.only.isEmpty()) {
			return true;
		}
		for (final String shot : shots) {
			if (this.only.contains(shot)) {
				return true;
			}
		}
		return false;
	}

	/** Saddles a tame horse at the spot, faces it down the route and climbs on; the client's copy of it. */
	private AbstractHorse mount(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot, final int variant, final int time) {
		final TestServerContext server = world.getServer();
		final TestInput input = ctx.getInput();
		input.releaseKey(o -> o.keyUp);
		ctx.runOnClient(mc -> FilmCamera.stop());
		server.runCommand("ride @p dismount");
		server.runCommand("kill @e[type=minecraft:horse]");
		server.runCommand("time set " + time);
		final BlockPos p = spot.start;
		server.runCommand(String.format(Locale.ROOT, "tp @p %d %d %d", p.getX(), p.getY() + 3, p.getZ()));
		ctx.waitTicks(20);
		waitForWorld(ctx);
		// Generated ground can have a trunk or a bush right on the start: stand on the ground and clear a little room.
		final int y = server.computeOnServer(s -> standY(s.overworld(), p.getX(), p.getZ()));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air replace #minecraft:logs", p.getX() - 2, y, p.getZ() - 2, p.getX() + 2, y + 4, p.getZ() + 2));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air replace #minecraft:leaves", p.getX() - 2, y, p.getZ() - 2, p.getX() + 2, y + 4, p.getZ() + 2));
		clearRoute(server, spot, y);
		server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f %.1f 0", p.getX() + 0.5, y, p.getZ() + 0.5, spot.yaw));
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:horse %.1f %d %.1f {Tame:1b,Variant:%d,Rotation:[%.1ff,0f],"
			+ "equipment:{saddle:{id:\"minecraft:saddle\",count:1}},attributes:[{id:\"minecraft:movement_speed\",base:0.25d}],Tags:[\"store\"]}",
			p.getX() + 0.5, y, p.getZ() + 0.5, variant, spot.yaw));
		for (int attempt = 0; attempt < 10 && !ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse); attempt++) {
			ctx.waitTicks(5);
			server.runCommand("ride @p mount @e[tag=store,limit=1]");
		}
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		input.lookAt(spot.yaw, 12.0F);
		ctx.waitTicks(10);
		waitForWorld(ctx);
		return ctx.computeOnClient(mc -> (AbstractHorse) mc.player.getVehicle());
	}

	/**
	 * Clears the route for the camera and the horse: two-block plants from a strip 100 blocks down it and 15 either side
	 * (low shots through tall grass saw nothing but grass), and whole trees standing within 12 of it (they came between
	 * the camera and the horse, and the horse weaved round them). Trees further off stay as scenery, and the grass
	 * underfoot stays.
	 */
	private static void clearRoute(final TestServerContext server, final Spot spot, final int y) {
		final double fx = -Mth.sin(spot.yaw * Mth.DEG_TO_RAD);
		final double fz = Mth.cos(spot.yaw * Mth.DEG_TO_RAD);
		for (int along = -10; along <= 100; along += 9) {
			for (int side = -15; side <= 15; side += 10) {
				final int cx = Mth.floor(spot.start.getX() + 0.5 + fx * along + fz * side);
				final int cz = Mth.floor(spot.start.getZ() + 0.5 + fz * along - fx * side);
				for (final String plant : new String[] {"tall_grass", "large_fern", "tall_dry_grass"}) {
					server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air replace minecraft:%s", cx - 6, y - 4, cz - 6, cx + 6, y + 6, cz + 6, plant));
				}
			}
		}
		server.runOnServer(s -> {
			final ServerLevel level = s.overworld();
			final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			final List<BlockPos> trunks = new ArrayList<>();
			for (int along = -10; along <= 100; along++) {
				for (int side = -12; side <= 12; side++) {
					final int x = Mth.floor(spot.start.getX() + 0.5 + fx * along + fz * side);
					final int z = Mth.floor(spot.start.getZ() + 0.5 + fz * along - fx * side);
					// The lowest log in the column: the foot of the trunk.
					for (int dy = -4; dy <= 12; dy++) {
						if (level.getBlockState(at.set(x, y + dy, z)).is(BlockTags.LOGS)) {
							trunks.add(at.immutable());
							break;
						}
					}
				}
			}
			for (final BlockPos trunk : trunks) {
				for (int dx = -5; dx <= 5; dx++) {
					for (int dz = -5; dz <= 5; dz++) {
						for (int dy = -1; dy <= 16; dy++) {
							final BlockState state = level.getBlockState(at.set(trunk.getX() + dx, trunk.getY() + dy, trunk.getZ() + dz));
							if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
								level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
							}
						}
					}
				}
			}
		});
	}

	/**
	 * Waits for the chunks round the player (far from spawn they are still being generated), then gives them a few seconds
	 * to draw. Not {@code waitForChunksRender}: Sodium, which Iris needs, never builds chunks out of sight, so in generated
	 * terrain "every chunk drawn" may never come.
	 */
	private static void waitForWorld(final ClientGameTestContext ctx) {
		final int limit = 20 * 60 * 5;
		final int[] waited = {0};
		final int[] missing = {0};
		ctx.waitFor(mc -> {
			final int cx = mc.player.getBlockX() >> 4;
			final int cz = mc.player.getBlockZ() >> 4;
			final int r = mc.options.getEffectiveRenderDistance() - 1;
			missing[0] = 0;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					// The server sends a circle of chunks, not a square.
					if (dx * dx + dz * dz <= r * r && mc.level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false) == null) {
						missing[0]++;
					}
				}
			}
			return missing[0] == 0 || ++waited[0] > limit;
		}, limit + 20);
		ctx.waitTicks(60);
		LOGGER.info("[store] chunks ready after {} ticks ({} still missing), {} fps", waited[0], missing[0], ctx.computeOnClient(mc -> mc.getFps()));
	}

	/** Holds W and taps sprint up to the gait (1 trot, 2 canter, 3 gallop). */
	private void gait(final ClientGameTestContext ctx, final int taps) {
		final TestInput input = ctx.getInput();
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(2);
		for (int i = 0; i < taps; i++) {
			input.pressKey(o -> o.keySprint);
			ctx.waitTicks(4);
		}
	}

	private void steer(final ClientGameTestContext ctx, final float yaw, final int ticks) {
		for (int t = 0; t < ticks; t++) {
			ctx.getInput().lookAt(yaw, 12.0F);
			ctx.runOnClient(mc -> FilmCamera.tick());
			ctx.waitTick();
		}
	}

	private void stop(final ClientGameTestContext ctx) {
		ctx.getInput().releaseKey(o -> o.keyUp);
		ctx.runOnClient(mc -> FilmCamera.stop());
	}

	/** How fast a clip plays at clip tick {@code t}: 1 is real time, 0.4 two and a half times slower. */
	private interface Speed {
		double at(double t);
	}

	private static final Speed REAL_TIME = t -> 1.0;

	/** Real time, easing down to {@code speed} over the {@code ramp} ticks before {@code from} and back up over those after {@code to}. */
	private static Speed slowMotion(final double from, final double to, final double speed, final double ramp) {
		return t -> {
			final double away = t < from ? (from - t) / ramp : t > to ? (t - to) / ramp : 0.0;
			final double c = Math.min(away, 1.0);
			return Mth.lerp(c * c * (3.0 - 2.0 * c), speed, 1.0);
		};
	}

	/** A frame being exposed: its samples' sum so far. */
	private static final class Exposure {
		private final int index;
		private final double open;
		private final double length;
		/** Samples drawn, and those that came back and were added. */
		private int samples;
		private int added;
		private int @Nullable [] sum;
		private int width;
		private int height;

		private Exposure(final int index, final double open, final double length) {
			this.index = index;
			this.open = open;
			this.length = length;
		}

		/** When sample {@code i} is taken, clip ticks. */
		private double time(final int i) {
			return this.open + this.length * (i + 0.5) / SHUTTER_SAMPLES;
		}

		private void add(final NativeImage image) {
			if (this.sum == null) {
				this.width = image.getWidth();
				this.height = image.getHeight();
				this.sum = new int[this.width * this.height * 3];
			}
			this.added++;
			final int[] s = this.sum;
			for (int y = 0, i = 0; y < this.height; y++) {
				for (int x = 0; x < this.width; x++, i += 3) {
					final int argb = image.getPixel(x, y);
					s[i] += argb >> 16 & 0xFF;
					s[i + 1] += argb >> 8 & 0xFF;
					s[i + 2] += argb & 0xFF;
				}
			}
		}

		private NativeImage develop() {
			final int[] s = this.sum;
			final NativeImage image = new NativeImage(this.width, this.height, false);
			for (int y = 0, i = 0; y < this.height; y++) {
				for (int x = 0; x < this.width; x++, i += 3) {
					final int r = (s[i] + this.added / 2) / this.added;
					final int g = (s[i + 1] + this.added / 2) / this.added;
					final int b = (s[i + 2] + this.added / 2) / this.added;
					image.setPixelABGR(x, y, 0xFF000000 | b << 16 | g << 8 | r);
				}
			}
			return image;
		}
	}

	/**
	 * {@code ticks} of play into store/{@code clip}/ (widescreen) as {@link #FPS} frames a second of screen time, slowed
	 * down where {@code speed} says. Like a film camera's, each frame's shutter is open for half its time: the frame is
	 * {@link #SHUTTER_SAMPLES} pictures over that half blended together, so fast movement blurs the way it does in a film
	 * instead of strobing.
	 */
	private void record(final ClientGameTestContext ctx, final String clip, final int ticks, final Speed speed, final IntConsumer eachTick) {
		final Path dir = this.out.resolve(clip);
		try {
			if (Files.isDirectory(dir)) {
				try (Stream<Path> old = Files.list(dir)) {
					for (final Path file : old.toList()) {
						Files.delete(file);
					}
				}
			}
			Files.createDirectories(dir);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
		ctx.getInput().resizeWindow(WIDTH, CLIP_HEIGHT);
		final double[] from = ctx.computeOnClient(mc -> new double[] {mc.player.getX(), mc.player.getZ()});
		final AtomicInteger saving = new AtomicInteger();
		final List<Exposure> exposing = new ArrayList<>();
		int frames = 0;
		double shutter = 20.0 / FPS * speed.at(0.0) / 2.0;
		double middle = shutter / 2.0;
		for (int t = 0; t < ticks; t++) {
			eachTick.accept(t);
			ctx.waitTick();
			final int tick = t;
			ctx.runOnClient(mc -> {
				FilmCamera.clock(tick);
				FilmCamera.tick();
			});
			// Open the frames whose shutter opens in this tick, then take every sample due in it, oldest first.
			while (middle - shutter / 2.0 < t + 1 && middle + shutter / 2.0 <= ticks) {
				exposing.add(new Exposure(frames++, middle - shutter / 2.0, shutter));
				final double step = 20.0 / FPS * speed.at(middle);
				middle += step;
				shutter = 20.0 / FPS * speed.at(middle) / 2.0;
			}
			for (final Iterator<Exposure> it = exposing.iterator(); it.hasNext();) {
				final Exposure frame = it.next();
				while (frame.samples < SHUTTER_SAMPLES && frame.time(frame.samples) < t + 1) {
					final float partialTick = (float) (frame.time(frame.samples) - t);
					ctx.runOnClient(mc -> drawFrame(mc, partialTick, frame::add));
					frame.samples++;
				}
				if (frame.samples == SHUTTER_SAMPLES) {
					it.remove();
					if (frame.added < SHUTTER_SAMPLES) {
						LOGGER.warn("[store] {} frame {}: {} of {} pictures came back", clip, frame.index, frame.added, SHUTTER_SAMPLES);
						if (frame.added == 0) {
							continue;
						}
					}
					final Path file = dir.resolve(String.format(Locale.ROOT, "%04d.png", frame.index));
					saving.incrementAndGet();
					Util.ioPool().execute(() -> {
						try (NativeImage image = frame.develop()) {
							image.writeToFile(file);
						} catch (final IOException e) {
							LOGGER.warn("[store] couldn't save {}", file, e);
						} finally {
							saving.decrementAndGet();
						}
					});
				}
			}
		}
		ctx.waitFor(mc -> saving.get() == 0, 20 * 60);
		ctx.getInput().resizeWindow(WIDTH, HEIGHT);
		final double travelled = ctx.computeOnClient(mc -> Math.hypot(mc.player.getX() - from[0], mc.player.getZ() - from[1]));
		final String smoothness = ctx.computeOnClient(mc -> FilmCamera.smoothness());
		try {
			final List<String> path = ctx.computeOnClient(mc -> FilmCamera.path());
			Files.write(this.out.resolve(clip + "-camera.csv"), path);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
		final String line = String.format(Locale.ROOT, "%s: %d frames (%.1f s), rode %d blocks; %s", clip, frames, frames / (double) FPS, Math.round(travelled),
			smoothness);
		this.log.add(line);
		LOGGER.info("[store] {}", line);
	}

	/**
	 * Draws the world {@code partialTick} of the way through the current tick and hands the picture to {@code sink}.
	 * Waits for the picture to come back from the graphics card before returning: left to come back later, it sometimes
	 * caught the next frame's drawing, so a clip showed one moment twice and then jumped.
	 */
	private static void drawFrame(final Minecraft mc, final float partialTick, final Consumer<NativeImage> sink) {
		final DeltaTracker delta = new FrameDelta(partialTick);
		FilmCamera.recording(true);
		mc.gameRenderer.update(delta);
		FilmCamera.recording(false);
		mc.gameRenderer.extract(delta, true);
		mc.gameRenderer.render();
		Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
			try (image) {
				sink.accept(image);
			}
		});
		final CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuFence fence = encoder.createFence()) {
			encoder.submit();
			if (!fence.awaitCompletion(TimeUnit.SECONDS.toNanos(10))) {
				LOGGER.warn("[store] a frame took over 10 s to come back from the graphics card");
			}
		}
		RenderSystem.executePendingTasks();
	}

	/** A sample {@code partialTick} into the tick (the time since the sample before only matters to a few effects). */
	private record FrameDelta(float partialTick) implements DeltaTracker {
		@Override
		public float getGameTimeDeltaTicks() {
			return 10.0F / FPS / SHUTTER_SAMPLES;
		}

		@Override
		public float getGameTimeDeltaPartialTick(final boolean ignoreFrozenGame) {
			return this.partialTick;
		}

		@Override
		public float getRealtimeDeltaTicks() {
			return 10.0F / FPS / SHUTTER_SAMPLES;
		}
	}

	private void still(final ClientGameTestContext ctx, final String name) {
		ctx.takeScreenshot(TestScreenshotOptions.of(name).withDeltaTicks(0.5F).disableCounterPrefix().withDestinationDir(this.out));
	}

	private static void hud(final Minecraft mc, final boolean shown) {
		if (mc.gui.hud.isHidden() == shown) {
			mc.gui.hud.toggle();
		}
	}

	// ---- Scouting ----

	@SafeVarargs
	private static Predicate<Holder<Biome>> any(final ResourceKey<Biome>... keys) {
		return holder -> {
			for (final ResourceKey<Biome> key : keys) {
				if (holder.is(key)) {
					return true;
				}
			}
			return false;
		};
	}

	private @Nullable Spot scout(final TestServerContext server, final Want want) {
		final Spot spot = server.computeOnServer(s -> scout(s.overworld(), want.ground, want.run, want.biome, want.yawFrom, want.yawTo));
		final String line = spot == null ? want.name + ": nothing found" : String.format(Locale.ROOT, "%s: %d %d %d heading %.0f (%s)", want.name,
			spot.start.getX(), spot.start.getY(), spot.start.getZ(), spot.yaw, spot.biome);
		this.log.add(line);
		LOGGER.info("[store] {}", line);
		return spot;
	}

	/** Grid spacing of the scouted terrain, blocks. */
	private static final int STEP = 2;
	/** Scouted area half-width, blocks. */
	private static final int REACH = 120;

	/**
	 * The best start and heading near the closest {@code biome} for the ground wanted: the best few by the terrain noise
	 * (the flattest dry run for OPEN, low banks either side of 6-24 blocks of water for RIVER), then the best of
	 * those on the generated ground.
	 */
	private static @Nullable Spot scout(final ServerLevel level, final Ground ground, final int run, final Predicate<Holder<Biome>> biome,
		final float yawFrom, final float yawTo) {
		final Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(biome, new BlockPos(0, 64, 0), 6400, 32, 64);
		if (found == null) {
			return null;
		}
		final ChunkGenerator generator = level.getChunkSource().getGenerator();
		final RandomState random = level.getChunkSource().randomState();
		final int sea = generator.getSeaLevel();
		final int cx = found.getFirst().getX();
		final int cz = found.getFirst().getZ();
		final int n = 2 * REACH / STEP + 1;
		final int[] floor = new int[n * n];
		final boolean[] wet = new boolean[n * n];
		final boolean[] inBiome = new boolean[n * n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				final int x = cx - REACH + i * STEP;
				final int z = cz - REACH + j * STEP;
				final int k = i * n + j;
				floor[k] = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
				wet[k] = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random) > floor[k];
				inBiome[k] = biome.test(level.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(floor[k]), QuartPos.fromBlock(z)));
			}
		}
		final int samples = (ground == Ground.RIVER ? 64 : run) / STEP;
		final int[] path = new int[samples + 1];
		final List<double[]> candidates = new ArrayList<>();
		for (int si = 0; si < n; si += 2) {
			for (int sj = 0; sj < n; sj += 2) {
				for (float yaw = yawFrom; yaw < yawTo; yaw += 15.0F) {
					final double dx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
					final double dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
					boolean inside = true;
					for (int s = 0; s <= samples && inside; s++) {
						final int i = (int) Math.round(si + dx * s);
						final int j = (int) Math.round(sj + dz * s);
						inside = i >= 0 && j >= 0 && i < n && j < n;
						if (inside) {
							path[s] = i * n + j;
						}
					}
					if (!inside) {
						continue;
					}
					final double cost = switch (ground) {
						case OPEN -> open(path, samples, floor, wet, inBiome);
						case RIVER -> river(path, samples, floor, wet, sea);
					};
					if (cost < 1.0e6) {
						candidates.add(new double[] {cost, cx - REACH + si * STEP, floor[path[0]], cz - REACH + sj * STEP, yaw});
					}
				}
			}
		}
		// The noise knows nothing of lakes, trees or villages: try the best few on the generated ground itself.
		candidates.sort((a, b) -> Double.compare(a[0], b[0]));
		double best = Double.MAX_VALUE;
		Spot spot = null;
		for (int c = 0; c < Math.min(40, candidates.size()) && best > 3.0; c++) {
			final double[] candidate = candidates.get(c);
			final BlockPos start = new BlockPos((int) candidate[1], (int) candidate[2], (int) candidate[3]);
			final double cost = onTheGround(level, ground, run, start, (float) candidate[4], sea);
			if (cost < best) {
				best = cost;
				spot = new Spot(start, (float) candidate[4], level.getUncachedNoiseBiome(QuartPos.fromBlock(start.getX()), QuartPos.fromBlock(start.getY()),
					QuartPos.fromBlock(start.getZ())).getRegisteredName());
			}
		}
		return spot;
	}

	/**
	 * A scouted route checked on the generated ground (generating it): never water where the horse should be on land, no
	 * steps of more than a block, few trunks or leaves in the way, and for the side shots nothing between the route and a
	 * camera 3-8 blocks off its left side standing more than 2 blocks above it. Double.MAX_VALUE when it won't do.
	 */
	private static double onTheGround(final ServerLevel level, final Ground ground, final int run, final BlockPos start, final float yaw, final int sea) {
		final int length = ground == Ground.RIVER ? 64 : run;
		final double dx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
		final double dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
		final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		double cost = 0.0;
		int previous = Integer.MIN_VALUE;
		int waterFrom = -1;
		int waterTo = -1;
		for (int s = 0; s <= length; s++) {
			final int x = Mth.floor(start.getX() + 0.5 + dx * s);
			final int z = Mth.floor(start.getZ() + 0.5 + dz * s);
			level.getChunk(x >> 4, z >> 4);
			final int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			final BlockState top = level.getBlockState(pos.set(x, g - 1, z));
			final boolean water = !top.getFluidState().isEmpty();
			if (water) {
				if (ground != Ground.RIVER || (waterTo >= 0 && s > waterTo + 1)) {
					return Double.MAX_VALUE;
				}
				if (waterFrom < 0) {
					waterFrom = s;
				}
				waterTo = s;
			} else {
				if (top.is(BlockTags.LOGS)) {
					cost += 10.0;
				}
				if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) > g) {
					cost += 3.0;
				}
				if (previous != Integer.MIN_VALUE && (waterTo < 0 || s > waterTo + 1)) {
					final int step = Math.abs(g - previous);
					if (step > 1) {
						cost += 10.0 * (step - 1);
					}
					cost += step;
				}
				// The far bank: no more than a block above the water to climb out.
				if (ground == Ground.RIVER && waterTo >= 0 && s == waterTo + 1 && g > sea + 1) {
					return Double.MAX_VALUE;
				}
				previous = g;
			}
			if (s % 2 == 0) {
				// Left of the heading: the side the side shots film from.
				for (int k = 3; k <= 8; k++) {
					final int cx = Mth.floor(start.getX() + 0.5 + dx * s + dz * k);
					final int cz = Mth.floor(start.getZ() + 0.5 + dz * s - dx * k);
					level.getChunk(cx >> 4, cz >> 4);
					if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz) > (water ? sea : g) + 2) {
						cost += 1.5;
					}
				}
			}
		}
		if (ground == Ground.RIVER && (waterFrom < 12 || waterTo - waterFrom + 1 < 6 || waterTo - waterFrom + 1 > 24 || length - waterTo < 12)) {
			return Double.MAX_VALUE;
		}
		return cost;
	}

	/** Bumpiness, steps of more than a block, water and leaving the biome. */
	private static double open(final int[] path, final int samples, final int[] floor, final boolean[] wet, final boolean[] inBiome) {
		double cost = 0.0;
		for (int s = 0; s <= samples; s++) {
			final int k = path[s];
			if (wet[k]) {
				cost += 50.0;
			}
			if (!inBiome[k]) {
				cost += 3.0;
			}
			if (s > 0) {
				final int dh = Math.abs(floor[k] - floor[path[s - 1]]);
				cost += dh + 4.0 * Math.max(0, dh - 1);
			}
		}
		return cost;
	}

	/** Dry ground, then 6-24 blocks of water, then dry ground again, with banks no more than a block above the water. */
	private static double river(final int[] path, final int samples, final int[] floor, final boolean[] wet, final int sea) {
		final int landBefore = 16 / STEP;
		final int landAfter = 14 / STEP;
		int first = -1;
		int last = -1;
		for (int s = 0; s <= samples; s++) {
			if (wet[path[s]]) {
				if (first < 0) {
					first = s;
				} else if (last >= 0 && s > last + 1) {
					return Double.MAX_VALUE;
				}
				last = s;
			}
		}
		if (first < landBefore || last < 0 || last - first + 1 < 6 / STEP || last - first + 1 > 24 / STEP || samples - last < landAfter) {
			return Double.MAX_VALUE;
		}
		if (floor[path[first - 1]] > sea + 1 || floor[path[last + 1]] > sea + 1) {
			return Double.MAX_VALUE;
		}
		double cost = 0.0;
		for (int s = 1; s <= samples; s++) {
			if (!wet[path[s]] && !wet[path[s - 1]]) {
				final int dh = Math.abs(floor[path[s]] - floor[path[s - 1]]);
				cost += dh + 4.0 * Math.max(0, dh - 1);
			}
		}
		return cost;
	}

	/** Standing height at (x, z): on the ground, under any trunk or leaves. */
	private static int standY(final ServerLevel level, final int x, final int z) {
		final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		while (pos.getY() > level.getMinY()) {
			pos.move(0, -1, 0);
			final BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) {
				return pos.getY() + 1;
			}
		}
		return pos.getY();
	}
}
