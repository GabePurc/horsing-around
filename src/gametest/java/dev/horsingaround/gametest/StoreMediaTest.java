package dev.horsingaround.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.datafixers.util.Pair;
import dev.horsingaround.client.config.HorseSettingsScreen;
import dev.horsingaround.client.render.LegBones;
import dev.horsingaround.devtools.LegProbe;
import dev.horsingaround.gametest.mixin.DeltaTrackerTimerAccessor;
import dev.horsingaround.gametest.mixin.RideCameraAccessor;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.client.cosmetic.Supporters;
import dev.horsingaround.ride.HorseConfig;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
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
	/** The shots as you play them (the HUD, the riding camera, the settings) are the whole window, 16:9. */
	private static final int PLAY_HEIGHT = HEIGHT;
	/** Clip frames a second of screen time. */
	private static final int FPS = 30;
	private static final int CANTER = 2;
	private static final int GALLOP = 3;
	/** Chestnut with white stockings and blaze; bay; dark brown with white spots (variant | markings << 8). */
	private static final int CHESTNUT = 2 | 1 << 8;
	private static final int BAY = 3;
	private static final int DARK = 6 | 3 << 8;
	/** Late afternoon: the sun low in the west (13 degrees up), the light still full. */
	private static final int GOLDEN_HOUR = 11200;
	/** Midday: the sun high, every leg lit. */
	private static final int NOON = 6000;

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
		new Want("savanna", Ground.OPEN, 90, any(Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU), -35.0F, 35.0F, "gaits", "study", "legs", "jump", "rider", "hat",
			// The camera shot last: it leaves a target standing on the route.
			"seat", "stamina", "settings", "camera"),
		new Want("plains", Ground.OPEN, 110, any(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW), 0.0F, 360.0F, "turn"),
		// The sunset shots look west into the sun: heading roughly south, filmed from the horse's left.
		new Want("badlands", Ground.OPEN, 80, any(Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.ERODED_BADLANDS), -35.0F, 35.0F, "sunset", "cliff"),
		new Want("river", Ground.RIVER, 0, any(Biomes.RIVER), 0.0F, 360.0F, "river"));

	private static final Logger LOGGER = LoggerFactory.getLogger("horsingaround-store");
	/** The clip being recorded's legs as drawn, a line a frame (client thread). */
	private static final List<String> LEGS = new ArrayList<>();
	/** The clip being recorded's rider, a line a frame (client thread). */
	private static final List<String> RIDER = new ArrayList<>();
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
					// (The gait study is for judging the plain model's gaits, not the store: only when asked for.)
					if (!this.wanted(shot) || (shot.equals("study") || shot.equals("legs")) && !this.only.contains(shot)) {
						continue;
					}
					LOGGER.info("[store] filming {}", shot);
					switch (shot) {
						case "gaits" -> this.gaits(ctx, world, spot);
						case "study" -> this.study(ctx, world, spot);
						case "legs" -> this.legs(ctx, world, spot);
						case "jump" -> this.jump(ctx, world, spot);
						case "rider" -> this.rider(ctx, world, spot);
						case "hat" -> this.hat(ctx, world, spot);
						case "seat" -> {
							// (A study of the rider for review, not for the store page: only when asked for by name.)
							if (this.only.contains("seat")) {
								this.seat(ctx, world, spot);
							}
						}
						case "settings" -> this.settings(ctx, world, spot);
						case "turn" -> this.turn(ctx, world, spot);
						case "stamina" -> this.stamina(ctx, world, spot);
						case "camera" -> this.camera(ctx, world, spot);
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
			tap(input, o -> o.keySprint, t, 40, 75, 110);
			input.lookAt(spot.yaw, 12.0F);
		});
		this.still(ctx, "gallery_gaits");
		this.stop(ctx);
	}

	/**
	 * Not for the store: each gait side on at a steady pace, slowed to half speed, a clip each ({@code study_walk} and
	 * so on), to judge the gaits by (with {@code -PvanillaModel}, the plain model's own; {@code -Pscenario=study}).
	 */
	private void study(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final String[] gaits = {"walk", "trot", "canter", "gallop"};
		// About two and a half strides of each (the stride is quicker the faster the gait).
		final int[] ticks = {70, 42, 30, 26};
		for (int g = 0; g < gaits.length; g++) {
			final AbstractHorse horse = this.mount(ctx, world, spot, BAY, NOON);
			// A flat stage of bare grass along the route, so no step or plant gets in the way of the legs.
			final BlockPos p = spot.start;
			final int y = ctx.computeOnClient(mc -> mc.player.getVehicle().getBlockY());
			final float fx = -Mth.sin(spot.yaw * Mth.DEG_TO_RAD);
			final float fz = Mth.cos(spot.yaw * Mth.DEG_TO_RAD);
			final int x0 = Math.min(p.getX(), p.getX() + Math.round(fx * 70.0F)) - 6;
			final int x1 = Math.max(p.getX(), p.getX() + Math.round(fx * 70.0F)) + 6;
			final int z0 = Math.min(p.getZ(), p.getZ() + Math.round(fz * 70.0F)) - 6;
			final int z1 = Math.max(p.getZ(), p.getZ() + Math.round(fz * 70.0F)) + 6;
			final TestServerContext server = world.getServer();
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d dirt", x0, y - 3, z0, x1, y - 2, z1));
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d grass_block", x0, y - 1, z0, x1, y - 1, z1));
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x0, y, z0, x1, y + 5, z1));
			waitForWorld(ctx);
			this.gait(ctx, g);
			this.steer(ctx, spot.yaw, 70);
			ctx.runOnClient(mc -> FilmCamera.film(horse, sunBehind(spot), 5.5F, 0.75F, 2.0F, 0.0F, 0.1F));
			final TestInput input = ctx.getInput();
			this.record(ctx, "study_" + gaits[g], ticks[g], CLIP_HEIGHT, t -> 0.5, t -> input.lookAt(spot.yaw, 12.0F));
			this.stop(ctx);
		}
	}

	/**
	 * Not for the store: the plain model's legs where a steady pace doesn't show them ({@code -Pscenario=legs}): speeding
	 * up through the gaits and slowing to a stop, weaving and circling at a trot and a walk.
	 */
	private void legs(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final TestInput input = ctx.getInput();
		this.mount(ctx, world, spot, BAY, NOON);
		this.flatten(ctx, world, spot);
		input.holdKey(o -> o.keyUp);
		this.closeUp(ctx, 90.0F, 6.0F, 0.6F, 0.3F);
		this.record(ctx, "legs_speeds", 260, CLIP_HEIGHT, REAL_TIME, t -> {
			if (t == 50 || t == 100 || t == 140) {
				input.pressKey(o -> o.keySprint);
			} else if (t == 180) {
				input.releaseKey(o -> o.keyUp);
			}
			input.lookAt(spot.yaw, 12.0F);
		});
		this.stop(ctx);
		// Weaving at a trot, then a walk, from behind (as a rider sees it).
		this.mount(ctx, world, spot, BAY, NOON);
		this.gait(ctx, 1);
		this.closeUp(ctx, 20.0F, 5.0F, 2.2F, 0.4F);
		this.record(ctx, "legs_weave", 200, CLIP_HEIGHT, REAL_TIME, t -> {
			if (t == 100) {
				input.pressKey(o -> o.keyDown);
			}
			input.lookAt(spot.yaw + 35.0F * Mth.sin(t * Mth.TWO_PI / 50.0F), 12.0F);
		});
		this.stop(ctx);
		this.mount(ctx, world, spot, BAY, NOON);
		this.gait(ctx, 1);
		final float[] yaw = {spot.yaw};
		this.closeUp(ctx, 60.0F, 6.0F, 2.5F, 0.5F);
		this.record(ctx, "legs_circle", 200, CLIP_HEIGHT, REAL_TIME, t -> {
			if (t == 100) {
				input.pressKey(o -> o.keyDown);
			}
			yaw[0] += t < 100 ? 4.0F : 3.0F;
			input.lookAt(yaw[0], 12.0F);
		});
		this.stop(ctx);
		// Up a hill of full blocks at a gallop, then a trot, side on.
		for (final int taps : new int[] {GALLOP, 1}) {
			this.mount(ctx, world, spot, BAY, NOON);
			final float heading = this.hill(ctx, world, spot);
			this.steer(ctx, heading, 20);
			this.closeUp(ctx, 90.0F, 7.0F, 1.0F, 0.3F);
			this.gait(ctx, taps);
			this.record(ctx, taps == GALLOP ? "legs_climb_gallop" : "legs_climb_trot", 140, CLIP_HEIGHT, REAL_TIME, t -> input.lookAt(heading, 12.0F));
			this.stop(ctx);
		}
		// The bones drawn over the legs (LegBones, F8 in a dev game), side on: walking, then trotting, then stopping.
		ctx.runOnClient(mc -> LegBones.on = true);
		this.mount(ctx, world, spot, BAY, NOON);
		this.flatten(ctx, world, spot);
		input.holdKey(o -> o.keyUp);
		this.closeUp(ctx, 90.0F, 5.0F, 0.6F, 0.3F);
		this.record(ctx, "legs_bones", 200, CLIP_HEIGHT, REAL_TIME, t -> {
			if (t == 90) {
				input.pressKey(o -> o.keySprint);
			} else if (t == 150) {
				input.releaseKey(o -> o.keyUp);
			}
			input.lookAt(spot.yaw, 12.0F);
		});
		this.stop(ctx);
		ctx.runOnClient(mc -> LegBones.on = false);
	}

	/**
	 * A hill of full blocks ahead of the spot, along its heading turned to the nearest axis (returned): flat for 6 blocks, a
	 * step every two blocks for 20, every block for 10, then flat on top.
	 */
	private float hill(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		this.flatten(ctx, world, spot);
		final float yaw = Math.round(spot.yaw / 90.0F) * 90.0F;
		final int fx = Math.round(-Mth.sin(yaw * Mth.DEG_TO_RAD));
		final int fz = Math.round(Mth.cos(yaw * Mth.DEG_TO_RAD));
		final int y = ctx.computeOnClient(mc -> mc.player.getVehicle().getBlockY());
		final TestServerContext server = world.getServer();
		for (int d = 6; d < 80; d++) {
			final int h = Math.min(d < 26 ? (d - 6) / 2 + 1 : d - 15, 20);
			final int x = spot.start.getX() + fx * d;
			final int z = spot.start.getZ() + fz * d;
			final int ax = 3 * Math.abs(fz);
			final int az = 3 * Math.abs(fx);
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d grass_block", x - ax, y, z - az, x + ax, y + h - 1, z + az));
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x - ax, y + h, z - az, x + ax, y + h + 6, z + az));
		}
		waitForWorld(ctx);
		return yaw;
	}

	/** Close on the legs: {@code angle} round the horse's heading (0 behind, 90 its left), following its heading at {@code follow} a tick. */
	private void closeUp(final ClientGameTestContext ctx, final float angle, final float distance, final float height, final float follow) {
		ctx.runOnClient(mc -> {
			FilmCamera.shoot(mc.player.getVehicle(), follow);
			FilmCamera.key(0, FilmCamera.Setup.orbit(angle, distance, height, 0.8F, 0.0F, 34.0F));
		});
	}

	/** A flat stage of bare grass around the spot and along its route, so no step or plant gets in the way of the legs. */
	private void flatten(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final BlockPos p = spot.start;
		final int y = ctx.computeOnClient(mc -> mc.player.getVehicle().getBlockY());
		final float fx = -Mth.sin(spot.yaw * Mth.DEG_TO_RAD);
		final float fz = Mth.cos(spot.yaw * Mth.DEG_TO_RAD);
		final int x0 = Math.min(p.getX(), p.getX() + Math.round(fx * 70.0F)) - 12;
		final int x1 = Math.max(p.getX(), p.getX() + Math.round(fx * 70.0F)) + 12;
		final int z0 = Math.min(p.getZ(), p.getZ() + Math.round(fz * 70.0F)) - 12;
		final int z1 = Math.max(p.getZ(), p.getZ() + Math.round(fz * 70.0F)) + 12;
		final TestServerContext server = world.getServer();
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d dirt", x0, y - 3, z0, x1, y - 2, z1));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d grass_block", x0, y - 1, z0, x1, y - 1, z1));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x0, y, z0, x1, y + 5, z1));
		waitForWorld(ctx);
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
			// Aimed above the saddle and a little wide, so the rider stays in the frame at the top of the leap.
			FilmCamera.key(22, FilmCamera.Setup.orbit(sun, 6.0F, 1.0F, 2.1F, 0.0F, 42.0F));
			FilmCamera.key(40, FilmCamera.Setup.orbit(sun - 5.0F, 6.0F, 1.0F, 2.1F, 0.0F, 42.0F));
			FilmCamera.key(70, FilmCamera.Setup.orbit(sun - 60.0F, 9.0F, 1.8F, 1.4F, 0.0F, 42.0F));
		});
		this.record(ctx, "jump", 70, slowMotion(25.0, 38.0, 0.4, 5.0), t -> {
			tap(input, o -> o.keyJump, t, 26);
			input.lookAt(spot.yaw, 12.0F);
		});
		this.stop(ctx);
	}

	/**
	 * The rider at a canter, close: feet in the stirrups and hands on the reins, leaning into a turn and back, then drawing
	 * a bow like an archer and loosing, slowed down through the draw. From the horse's right, the low sun behind the camera
	 * lighting the rider.
	 */
	private void rider(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final AbstractHorse horse = this.mount(ctx, world, spot, CHESTNUT, GOLDEN_HOUR);
		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:bow");
		final TestInput input = ctx.getInput();
		final float lit = sunBehind(spot);
		this.gait(ctx, CANTER);
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> {
			FilmCamera.shoot(horse, 0.08F);
			FilmCamera.key(0, FilmCamera.Setup.orbit(lit - 60.0F, 4.6F, 1.9F, 2.0F, 0.4F, 34.0F));
			FilmCamera.key(45, FilmCamera.Setup.orbit(lit - 45.0F, 4.3F, 1.8F, 2.0F, 0.3F, 32.0F));
			FilmCamera.key(85, FilmCamera.Setup.orbit(lit - 15.0F, 4.0F, 1.8F, 2.1F, 0.0F, 30.0F));
			FilmCamera.key(125, FilmCamera.Setup.orbit(lit + 10.0F, 4.4F, 1.9F, 2.0F, 0.0F, 30.0F));
			FilmCamera.key(150, FilmCamera.Setup.orbit(lit + 25.0F, 6.0F, 2.0F, 1.8F, 0.5F, 36.0F));
		});
		final float[] yaw = {spot.yaw};
		this.record(ctx, "rider", 150, slowMotion(68.0, 110.0, 0.45, 6.0), t -> {
			if (t >= 10 && t < 30) {
				yaw[0] += 1.5F;
			} else if (t >= 35 && t < 55) {
				yaw[0] -= 1.5F;
			}
			if (t == 70) {
				input.holdKey(o -> o.keyUse);
			} else if (t == 104) {
				input.releaseKey(o -> o.keyUse);
			}
			input.lookAt(yaw[0], 6.0F);
		});
		this.still(ctx, "gallery_rider");
		this.stop(ctx);
	}

	/**
	 * The rider's seat, side on and lit, for review ({@code -Pscenario=seat}): walk, trot, canter and gallop and a hard stop
	 * ({@code seat_gaits}, real time, and again through the riding camera as {@code seat_play}); a jump at a gallop ({@code seat_jump}, slowed through the jump); and cantering off a
	 * 3-block ledge built on the route ({@code seat_drop}, slowed through the drop and the landing).
	 */
	private void seat(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		// Square on from the horse's right (heading roughly south, the low sun in the west lights that side), with the grass
		// along the route cut so it doesn't hide the legs.
		final float side = -90.0F;
		final TestInput input = ctx.getInput();
		final TestServerContext server = world.getServer();
		final double fx = -Mth.sin(spot.yaw * Mth.DEG_TO_RAD);
		final double fz = Mth.cos(spot.yaw * Mth.DEG_TO_RAD);
		final int ground = server.computeOnServer(s -> standY(s.overworld(), spot.start.getX(), spot.start.getZ()));
		for (int along = -8; along <= 120; along += 8) {
			final int x = Mth.floor(spot.start.getX() + 0.5 + fx * along);
			final int z = Mth.floor(spot.start.getZ() + 0.5 + fz * along);
			for (final String plant : new String[] {"short_grass", "tall_grass", "short_dry_grass", "tall_dry_grass", "fern", "large_fern", "bush"}) {
				server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air replace minecraft:%s", x - 7, ground - 6, z - 7, x + 7, ground + 6, z + 7, plant));
			}
		}
		AbstractHorse horse = this.mount(ctx, world, spot, CHESTNUT, GOLDEN_HOUR);
		final AbstractHorse walking = horse;
		ctx.runOnClient(mc -> {
			FilmCamera.shoot(walking, 0.08F);
			FilmCamera.key(0, FilmCamera.Setup.orbit(side, 4.5F, 1.6F, 1.6F, 0.0F, 36.0F));
		});
		input.holdKey(o -> o.keyUp);
		final IntConsumer gaitsAndStop = t -> {
			tap(input, o -> o.keySprint, t, 50, 95, 140);
			if (t == 205) {
				input.releaseKey(o -> o.keyUp);
				input.holdKey(o -> o.keyDown);
			} else if (t == 228) {
				input.releaseKey(o -> o.keyDown);
			}
			input.lookAt(spot.yaw, 12.0F);
		};
		this.record(ctx, "seat_gaits", 250, REAL_TIME, gaitsAndStop);
		this.stop(ctx);
		// The same through the riding camera, as it is played.
		this.mount(ctx, world, spot, CHESTNUT, GOLDEN_HOUR);
		input.holdKey(o -> o.keyUp);
		this.record(ctx, "seat_play", 250, REAL_TIME, gaitsAndStop);
		this.stop(ctx);

		horse = this.mount(ctx, world, spot, BAY, GOLDEN_HOUR);
		this.gait(ctx, GALLOP);
		ctx.waitTicks(40);
		final AbstractHorse jumping = horse;
		// Held beside where it jumps (it takes off about 13 blocks on) and turning with it, so the camera's lag in following
		// the horse up and down doesn't take the rider out of the frame.
		final double[] from = ctx.computeOnClient(mc -> new double[] {jumping.getX(), jumping.getZ()});
		final Vec3 besideJump = beside(server, from[0] + fx * 16.0, from[1] + fz * 16.0, fx, fz, 7.0);
		ctx.runOnClient(mc -> {
			FilmCamera.shoot(jumping, 0.1F);
			FilmCamera.key(0, FilmCamera.Setup.fixed(besideJump, 1.7F, 0.0F, 40.0F));
		});
		this.record(ctx, "seat_jump", 70, slowMotion(18.0, 52.0, 0.35, 5.0), t -> {
			tap(input, o -> o.keyJump, t, 20);
			input.lookAt(spot.yaw, 12.0F);
		});
		this.stop(ctx);

		// A ledge 3 blocks high along the route, from just behind the start to EDGE blocks ahead: mounted on top of it.
		final int edge = 22;
		for (int along = -4; along < edge; along++) {
			for (int across = -2; across <= 2; across++) {
				final int x = Mth.floor(spot.start.getX() + 0.5 + fx * along + fz * across);
				final int z = Mth.floor(spot.start.getZ() + 0.5 + fz * along - fx * across);
				server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:coarse_dirt", x, ground - 2, z, x, ground + 2, z));
				server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:grass_block", x, ground + 2, z, x, ground + 2, z));
			}
		}
		horse = this.mount(ctx, world, spot, DARK, GOLDEN_HOUR);
		final AbstractHorse dropping = horse;
		final double startX = spot.start.getX() + 0.5;
		final double startZ = spot.start.getZ() + 0.5;
		final double[] slow = {Double.NaN};
		final Vec3 besideDrop = beside(server, startX + fx * (edge + 2.0), startZ + fz * (edge + 2.0), fx, fz, 7.0);
		ctx.runOnClient(mc -> {
			FilmCamera.shoot(dropping, 0.1F);
			FilmCamera.key(0, FilmCamera.Setup.fixed(besideDrop, 1.5F, 0.0F, 40.0F));
		});
		this.gait(ctx, CANTER);
		this.record(ctx, "seat_drop", 110, t -> Double.isNaN(slow[0]) ? 1.0 : slowMotion(slow[0], slow[0] + 30.0, 0.35, 4.0).at(t), t -> {
			input.lookAt(spot.yaw, 12.0F);
			final double along = ctx.computeOnClient(mc -> (dropping.getX() - startX) * fx + (dropping.getZ() - startZ) * fz);
			if (Double.isNaN(slow[0]) && along > edge - 4.5) {
				slow[0] = t + 5;
			}
		});
		this.stop(ctx);
	}

	/** A camera place {@code out} blocks to the right of a heading ({@code fx}, {@code fz}) from a point, at eye height over the ground there. */
	private static Vec3 beside(final TestServerContext server, final double x, final double z, final double fx, final double fz, final double out) {
		final double cx = x - fz * out;
		final double cz = z + fx * out;
		final int y = server.computeOnServer(s -> standY(s.overworld(), Mth.floor(cx), Mth.floor(cz)));
		return new Vec3(cx, y + 1.6, cz);
	}

	/** The supporters' cowboy hat on a rider at a canter: close on the face from ahead, round to the side and back, a little slowed. */
	private void hat(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final AbstractHorse horse = this.mount(ctx, world, spot, BAY, GOLDEN_HOUR);
		final TestInput input = ctx.getInput();
		final UUID me = ctx.computeOnClient(mc -> mc.player.getUUID());
		ctx.runOnClient(mc -> {
			Supporters.addForTesting(me);
			HorseConfig.get().hat = true;
			HorseConfig.get().hatColor = 0x6B4226;
			Hats.sendOwn();
		});
		this.gait(ctx, CANTER);
		ctx.waitTicks(30);
		final float lit = sunBehind(spot);
		ctx.runOnClient(mc -> {
			FilmCamera.shoot(horse, 0.08F);
			FilmCamera.key(0, FilmCamera.Setup.orbit(lit - 75.0F, 3.6F, 2.3F, 2.35F, 0.2F, 28.0F));
			FilmCamera.key(50, FilmCamera.Setup.orbit(lit - 45.0F, 3.6F, 2.3F, 2.35F, 0.1F, 28.0F));
			FilmCamera.key(90, FilmCamera.Setup.orbit(lit - 5.0F, 4.8F, 2.3F, 2.1F, 0.0F, 34.0F));
		});
		this.record(ctx, "hat", 90, t -> 0.6, t -> input.lookAt(spot.yaw, 6.0F));
		this.still(ctx, "gallery_hat");
		ctx.runOnClient(mc -> {
			HorseConfig.get().hat = false;
			Hats.sendOwn();
			Supporters.removeForTesting(me);
		});
		this.stop(ctx);
	}

	/**
	 * The settings as you play (16:9): opened from the saddle, the camera swapped from shoulder to shoulder and pulled
	 * further back, then closed and ridden off. The game doesn't pause behind the menu while filming, so each change
	 * shows on the camera behind it; the cursor is drawn in.
	 */
	private void settings(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		this.mount(ctx, world, spot, BAY, GOLDEN_HOUR);
		final TestInput input = ctx.getInput();
		final int blur = ctx.computeOnClient(mc -> {
			playView(mc, true);
			final int was = mc.options.menuBackgroundBlurriness().get();
			// Unblurred, so the camera's moves behind the menu show.
			mc.options.menuBackgroundBlurriness().set(0);
			return was;
		});
		this.steer(ctx, spot.yaw, 20);
		final String shoulder = Component.translatable("options.horsingaround.shoulder").getString();
		final String distance = Component.translatable("options.horsingaround.camera_distance").getString();
		final String done = CommonComponents.GUI_DONE.getString();
		// The cursor's moves (clip tick, x, y in window coordinates) and its clicks (the ticks the left button goes down and up; 26.3 numbers
		// mouse buttons from 1), once the menu is laid out. Dragging the slider is the cursor moving with the button down.
		final List<double[]> path = new ArrayList<>();
		final List<int[]> clicks = new ArrayList<>();
		this.record(ctx, "settings", 200, PLAY_HEIGHT, REAL_TIME, t -> {
			if (t == 15) {
				ctx.runOnClient(HorseSettingsScreen::open);
			} else if (t == 16) {
				ctx.runOnClient(mc -> settingsMoves(mc, shoulder, distance, done, path, clicks));
			} else if (t == 162) {
				input.holdKey(o -> o.keyUp);
			}
			for (final int[] click : clicks) {
				if (t == click[0]) {
					input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
				} else if (t == click[1]) {
					input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
				}
			}
			tap(input, o -> o.keySprint, t, 174);
			input.lookAt(spot.yaw, 6.0F);
		}, time -> {
			// Not from the moment it clicks Done on: once the menu closes, moving the cursor would turn the view.
			if (path.size() > 1 && time < path.getLast()[0]) {
				final double[] at = glide(path, time);
				input.setCursorPos(at[0], at[1]);
			}
		});
		this.still(ctx, "gallery_settings");
		ctx.setScreen(() -> null);
		ctx.runOnClient(mc -> {
			HorseConfig.reset();
			HorseConfig.save();
			mc.options.menuBackgroundBlurriness().set(blur);
			playView(mc, false);
		});
		this.stop(ctx);
	}

	/** The settings clip's slider is taken here and let go here (clip ticks). */
	private static final int DRAG_START = 104;
	private static final int DRAG_END = 126;

	/**
	 * The settings clip's cursor moves: from the middle of the window (where the game leaves it when a menu opens) to the
	 * shoulder button, clicked through left, centred and back to right, to the camera distance handle, dragged to the far
	 * end, and to Done. Window coordinates; the shoulder button is skipped where it doesn't exist.
	 */
	private static void settingsMoves(final Minecraft mc, final String shoulder, final String distance, final String done, final List<double[]> path,
		final List<int[]> clicks) {
		final Screen screen = mc.gui.screen();
		final double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
		final AbstractWidget side = screen == null ? null : widget(screen.children(), shoulder);
		final AbstractWidget slider = screen == null ? null : widget(screen.children(), distance);
		final AbstractWidget close = screen == null ? null : widget(screen.children(), done);
		path.add(new double[] {16, mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0});
		path.add(new double[] {22, mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0});
		if (side != null) {
			path.add(new double[] {32, (side.getX() + side.getWidth() * 0.62) * scale, (side.getY() + side.getHeight() / 2.0) * scale});
			path.add(new double[] {88, (side.getX() + side.getWidth() * 0.62) * scale, (side.getY() + side.getHeight() / 2.0) * scale});
			clicks.add(new int[] {36, 37});
			clicks.add(new int[] {60, 61});
			clicks.add(new int[] {84, 85});
		}
		if (slider != null) {
			final double y = (slider.getY() + slider.getHeight() / 2.0) * scale;
			// The handle at 100% (the slider runs 50% to 150%), then dragged to the far end.
			path.add(new double[] {100, (slider.getX() + 4 + (slider.getWidth() - 8) * 0.5) * scale, y});
			path.add(new double[] {DRAG_START, (slider.getX() + 4 + (slider.getWidth() - 8) * 0.5) * scale, y});
			path.add(new double[] {DRAG_END - 4, (slider.getX() + slider.getWidth() - 2) * scale, y});
			path.add(new double[] {138, (slider.getX() + slider.getWidth() - 2) * scale, y});
			clicks.add(new int[] {DRAG_START, DRAG_END});
		}
		if (close != null) {
			path.add(new double[] {150, (close.getX() + close.getWidth() * 0.45) * scale, (close.getY() + close.getHeight() / 2.0) * scale});
			path.add(new double[] {156, (close.getX() + close.getWidth() * 0.45) * scale, (close.getY() + close.getHeight() / 2.0) * scale});
			clicks.add(new int[] {156, 157});
		}
	}

	/** The widget among {@code children} (and theirs) whose label starts with {@code label}. */
	private static @Nullable AbstractWidget widget(final List<? extends GuiEventListener> children, final String label) {
		for (final GuiEventListener child : children) {
			if (child instanceof AbstractWidget w && w.getMessage().getString().startsWith(label)) {
				return w;
			}
			if (child instanceof ContainerEventHandler container) {
				final AbstractWidget found = widget(container.children(), label);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** Where along {@code path} (clip tick, x, y) the cursor is at {@code time}: easing in and out of each move. */
	private static double[] glide(final List<double[]> path, final double time) {
		for (int i = 1; i < path.size(); i++) {
			final double[] a = path.get(i - 1);
			final double[] b = path.get(i);
			if (time <= b[0]) {
				final double u = Mth.clamp((time - a[0]) / (b[0] - a[0]), 0.0, 1.0);
				final double e = u * u * (3.0 - 2.0 * u);
				return new double[] {Mth.lerp(e, a[1], b[1]), Mth.lerp(e, a[2], b[2])};
			}
		}
		final double[] last = path.getLast();
		return new double[] {last[1], last[2]};
	}

	/**
	 * The riding camera as you play it (16:9, the HUD on): over the right shoulder with the crosshair, leaning in as a bow
	 * draws and the arrow going where the crosshair points (a target off the route), a swap to the left shoulder with the
	 * swap key, then first person.
	 */
	private void camera(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		final AbstractHorse horse = this.mount(ctx, world, spot, BAY, GOLDEN_HOUR);
		final TestServerContext server = world.getServer();
		final TestInput input = ctx.getInput();
		kit(server);
		this.gait(ctx, CANTER);
		this.steer(ctx, spot.yaw, 20);
		final double[] from = ctx.computeOnClient(mc -> new double[] {horse.getX(), horse.getZ()});
		this.steer(ctx, spot.yaw, 5);
		final double[] now = ctx.computeOnClient(mc -> new double[] {horse.getX(), horse.getZ()});
		final double pace = Math.hypot(now[0] - from[0], now[1] - from[1]) / 5.0;
		// The target on two hay bales 4 blocks right of the route, about 14 blocks off when the arrow goes (close, so it barely drops).
		final double fx = -Mth.sin(spot.yaw * Mth.DEG_TO_RAD);
		final double fz = Mth.cos(spot.yaw * Mth.DEG_TO_RAD);
		final double ahead = pace * (5 + LOOSE) + 14.0;
		final int tx = Mth.floor(now[0] + fx * ahead - fz * 4.0);
		final int tz = Mth.floor(now[1] + fz * ahead + fx * 4.0);
		final int ty = server.computeOnServer(s -> standY(s.overworld(), tx, tz));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:hay_block", tx, ty, tz, tx, ty + 1, tz));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:target", tx, ty + 2, tz));
		this.steer(ctx, spot.yaw, 5);
		// The crosshair on the target's upper half: the arrow flies at what is under it and drops a little on the way.
		final Vec3 target = new Vec3(tx + 0.5, ty + 2.85, tz + 0.5);
		final KeyMapping swap = ctx.computeOnClient(mc -> keyNamed(mc, "key.horsingaround.swap_shoulder"));
		// 26.3's Friends List key is O by default too, and the game takes it first: unbound while filming, so O swaps.
		final InputConstants.Key friends = ctx.computeOnClient(mc -> {
			final InputConstants.Key was = mc.options.keyFriends.getDefaultKey();
			mc.options.keyFriends.setKey(InputConstants.UNKNOWN);
			KeyMapping.resetMapping();
			return was;
		});
		ctx.runOnClient(mc -> playView(mc, true));
		this.record(ctx, "camera", 190, PLAY_HEIGHT, REAL_TIME, t -> {
			if (t == DRAW) {
				input.holdKey(o -> o.keyUse);
			} else if (t == LOOSE) {
				input.releaseKey(o -> o.keyUse);
			} else if (t == 135) {
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			}
			if (swap != null) {
				tap(input, o -> swap, t, 95);
			}
			// Aimed from the camera, so the crosshair sits on the target.
			final float[] aim = ctx.computeOnClient(mc -> {
				final Vec3 cam = mc.gameRenderer.mainCamera().position();
				final double dx = target.x - cam.x;
				final double dz = target.z - cam.z;
				final double flat = Math.sqrt(dx * dx + dz * dz);
				final double dy = target.y - cam.y;
				return new float[] {(float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG), (float) (-Mth.atan2(dy, flat) * Mth.RAD_TO_DEG)};
			});
			final float onTarget = t < DRAW ? 0.0F : t < DRAW + 14 ? ease((t - DRAW) / 14.0F) : t < LOOSE + 6 ? 1.0F
				: t < LOOSE + 26 ? 1.0F - ease((t - LOOSE - 6) / 20.0F) : 0.0F;
			// After the shot, a little left of the route, to ride past the target.
			final float route = t < LOOSE ? spot.yaw : spot.yaw - 8.0F;
			input.lookAt(Mth.rotLerp(onTarget, route, aim[0]), Mth.lerp(onTarget, 6.0F, aim[1]));
		});
		this.still(ctx, "gallery_camera");
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", tx, ty, tz, tx, ty + 2, tz));
		ctx.runOnClient(mc -> {
			mc.options.keyFriends.setKey(friends);
			KeyMapping.resetMapping();
			playView(mc, false);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			HorseConfig.reset();
			HorseConfig.save();
		});
		unkit(server);
		this.stop(ctx);
	}

	/** The camera clip's bow: drawn from this clip tick and loosed at this one. */
	private static final int DRAW = 30;
	private static final int LOOSE = 54;

	private static float ease(final float u) {
		final float c = Mth.clamp(u, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
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

	/**
	 * The stamina bar as you play (16:9, the HUD on): galloping it down until it runs out, the head toss and the drop to a
	 * canter, then the bar filling again.
	 */
	private void stamina(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Spot spot) {
		this.mount(ctx, world, spot, BAY, GOLDEN_HOUR);
		final TestInput input = ctx.getInput();
		kit(world.getServer());
		this.gait(ctx, GALLOP);
		this.steer(ctx, spot.yaw, 25);
		// Enough to run out about six seconds in.
		ctx.runOnClient(mc -> {
			((RideStateHolder) mc.player.getVehicle()).horsingaround$ride().stamina = 0.22F;
			playView(mc, true);
		});
		this.record(ctx, "stamina", 190, PLAY_HEIGHT, REAL_TIME, t -> input.lookAt(spot.yaw + 10.0F * Mth.sin(t * Mth.TWO_PI / 190.0F), 6.0F));
		this.still(ctx, "gallery_stamina");
		ctx.runOnClient(mc -> playView(mc, false));
		unkit(world.getServer());
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
			tap(input, o -> o.keySprint, t, 2, 6, 10);
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
			tap(input, o -> o.keySprint, t, 4);
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

	/** The orbit angle that films the horse from the side with the sun behind the camera, lighting it (its right, heading south). */
	private static float sunBehind(final Spot spot) {
		return Mth.wrapDegrees(intoTheSun(spot) + 180.0F);
	}

	/** Survival with a few things in the hotbar and the bow in hand, so the HUD looks as it does in play. */
	private static void kit(final TestServerContext server) {
		server.runCommand("gamemode survival @p");
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:bow");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:iron_sword");
		server.runCommand("item replace entity @p hotbar.2 with minecraft:bread 12");
		server.runCommand("item replace entity @p hotbar.3 with minecraft:lead");
		server.runCommand("item replace entity @p hotbar.8 with minecraft:arrow 32");
	}

	private static void unkit(final TestServerContext server) {
		server.runCommand("clear @p");
		server.runCommand("gamemode creative @p");
	}

	/** The game as you play it, for the 16:9 shots: the HUD on at the size a 720p window has and nothing popped up over it; or the HUD off again. */
	private static void playView(final Minecraft mc, final boolean on) {
		if (on) {
			mc.options.guiScale().set(3);
			mc.resizeGui();
		}
		hud(mc, on);
		mc.gui.hud.getChat().clearMessages(false);
		// The recipes the kit unlocks pop up for a few seconds after it.
		mc.gui.toastManager().clear();
	}

	/** The key bound to {@code name}, or null (looked up by name, so these shots build where a key doesn't exist yet). */
	private static @Nullable KeyMapping keyNamed(final Minecraft mc, final String name) {
		for (final KeyMapping key : mc.options.keyMappings) {
			if (key.getName().equals(name)) {
				return key;
			}
		}
		return null;
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
		// Arrows from a shot before, and what the horse trampled (the rider picks it up and the hotbar fills).
		server.runCommand("kill @e[type=minecraft:arrow]");
		server.runCommand("kill @e[type=minecraft:item]");
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
	 * (low shots through tall grass saw nothing but grass), and whole trees standing within 20 of it (they came between
	 * the camera and the horse, the horse weaved round them, and the camera's floor rose over any within its reach). Trees
	 * further off stay as scenery, and the grass underfoot stays.
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
			for (int along = -10; along <= 110; along++) {
				for (int side = -20; side <= 20; side++) {
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

	/**
	 * A tap of {@code key} during a clip, at each of the clip ticks {@code at}: held for one tick and let go the next. Not
	 * {@code pressKey}, which runs a game tick of its own: inside {@link #record} that tick was never filmed, so the clip
	 * skipped a moment at every press (a lurch at the jump's takeoff, a hitch at each sprint tap).
	 */
	private static void tap(final TestInput input, final Function<Options, KeyMapping> key, final int t, final int... at) {
		for (final int tick : at) {
			if (t == tick) {
				input.holdKey(key);
			} else if (t == tick + 1) {
				input.releaseKey(key);
			}
		}
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

	/**
	 * {@code ticks} of play into store/{@code clip}/ (widescreen) as {@link #FPS} frames a second of screen time, slowed
	 * down where {@code speed} says: each frame one picture of its own moment, sharp like the game's own frames.
	 */
	private void record(final ClientGameTestContext ctx, final String clip, final int ticks, final Speed speed, final IntConsumer eachTick) {
		this.record(ctx, clip, ticks, CLIP_HEIGHT, speed, eachTick, time -> {
		});
	}

	private void record(final ClientGameTestContext ctx, final String clip, final int ticks, final int height, final Speed speed, final IntConsumer eachTick) {
		this.record(ctx, clip, ticks, height, speed, eachTick, time -> {
		});
	}

	/** As above, {@code height} pixels tall, running {@code eachFrame} before each frame with its moment in clip ticks. */
	private void record(final ClientGameTestContext ctx, final String clip, final int ticks, final int height, final Speed speed, final IntConsumer eachTick,
		final DoubleConsumer eachFrame) {
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
		ctx.getInput().resizeWindow(WIDTH, height);
		final double[] from = ctx.computeOnClient(mc -> new double[] {mc.player.getX(), mc.player.getZ()});
		ctx.runOnClient(mc -> {
			FilmCamera.clip(true);
			LegProbe.arm(true);
			LegProbe.listen(false);
			LEGS.clear();
			LEGS.add("# time (clip ticks), partial tick, then per leg (FL, FR, HL, HR) the sole against the top of the leg: x, y, z; the horse's heading (degrees) and where its model is drawn against it (blocks up); its stride (see below)");
			RIDER.clear();
			RIDER.add("time,stand,brace,settle,inertia,ground speed,body pitch,jump pitch,head gap");
		});
		int frames = 0;
		// The next frame's moment and the one before, in clip ticks.
		double next = 0.0;
		double last = -20.0 / FPS * speed.at(0.0);
		for (int t = 0; t < ticks; t++) {
			eachTick.accept(t);
			ctx.waitTick();
			final int tick = t;
			ctx.runOnClient(mc -> {
				FilmCamera.clock(tick);
				FilmCamera.tick();
			});
			for (; next < t + 1; next += 20.0 / FPS * speed.at(next)) {
				final float partialTick = (float) (next - t);
				final float step = (float) (next - last);
				final Path file = dir.resolve(String.format(Locale.ROOT, "%04d.png", frames++));
				final double at = next;
				eachFrame.accept(at);
				ctx.runOnClient(mc -> {
					drawFrame(mc, partialTick, step, file);
					final StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "%.4f,%.4f", at, partialTick));
					for (int leg = 0; leg < 4; leg++) {
						for (int i = 0; i < 3; i++) {
							row.append(String.format(Locale.ROOT, ",%.4f", LegProbe.sole[leg][i] - LegProbe.top[leg][i]));
						}
					}
					if (mc.player.getVehicle() instanceof AbstractHorse h) {
						final RideState ride = ((RideStateHolder) h).horsingaround$ride();
						row.append(String.format(Locale.ROOT, ",%.2f,%.4f", Mth.rotLerp(partialTick, h.yBodyRotO, h.yBodyRot),
							ride.drawnCameraY + ride.drawnPose.m31() - Mth.lerp(partialTick, h.yo, h.getY())));
						// (The plain model's stride: ground speed, strides a second, front and hind stance, which hooves stand, sweep;
						// per leg how far through its stance or swing and its stride it is; the neck on its spring; per leg how far it
						// turns forward and out to the side, radians, and is drawn up into the body, pixels; per leg where its top goes, down
						// and back, pixels.)
						row.append(String.format(Locale.ROOT, ",%.3f,%.3f,%.3f,%.3f,%d%d%d%d,%.3f", Math.sqrt(ride.gaitVelocityX * ride.gaitVelocityX
							+ ride.gaitVelocityZ * ride.gaitVelocityZ), ride.gaitRate, ride.gaitForeStance, ride.gaitHindStance, ride.hoofDown[0] ? 1 : 0,
							ride.hoofDown[1] ? 1 : 0, ride.hoofDown[2] ? 1 : 0, ride.hoofDown[3] ? 1 : 0, ride.gaitSweep));
						for (int leg = 0; leg < 4; leg++) {
							row.append(String.format(Locale.ROOT, ",%.3f,%.3f", ride.hoofDown[leg] ? ride.stood[leg] : ride.swingThrough[leg], ride.legPhase[leg]));
						}
						row.append(String.format(Locale.ROOT, ",%.4f", ride.neck));
						for (int leg = 0; leg < 4; leg++) {
							row.append(String.format(Locale.ROOT, ",%.3f,%.3f,%.2f", ride.gaitLegTurn[leg], ride.gaitLegSide[leg], ride.gaitLegDraw[leg] - ride.gaitJointY[leg]));
						}
						for (int leg = 0; leg < 4; leg++) {
							row.append(String.format(Locale.ROOT, ",%.2f,%.2f", ride.gaitJointY[leg], ride.gaitJointZ[leg]));
						}
						// (The body's pitch and roll on its legs, and its lean into a turn, degrees.)
						row.append(String.format(Locale.ROOT, ",%.2f,%.2f,%.2f", ride.gaitBodyPitch * Mth.RAD_TO_DEG, ride.gaitBodyRoll * Mth.RAD_TO_DEG,
							ride.lean(partialTick)));
					}
					LEGS.add(row.toString());
					if (mc.player.getVehicle() instanceof RideStateHolder holder) {
						final RideState s = holder.horsingaround$ride();
						RIDER.add(String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f,%.3f,%.4f,%.3f,%.3f,%.3f", at, s.stand(partialTick), s.brace(partialTick),
							s.settle(partialTick), s.inertia(partialTick), s.groundSpeed(partialTick), s.pitch(partialTick), s.jumpPitch(partialTick), s.headGap));
					}
				});
				last = next;
			}
		}
		ctx.runOnClient(mc -> FilmCamera.clip(false));
		ctx.getInput().resizeWindow(WIDTH, HEIGHT);
		// The legs as drawn, frame to frame: a sole or the body jumping much faster than the frame before is a hitch.
		final String legs = ctx.computeOnClient(mc -> String.format(Locale.ROOT, "legs: %d frames measured, %d hitches%s, soles moved at most %.2f a frame",
			LegProbe.frames, LegProbe.spikeCount, LegProbe.spikeCount == 0 ? "" : " (" + LegProbe.spikes.toString().strip().replace('\n', ';') + ")",
			LegProbe.soleSnapMost));
		ctx.runOnClient(mc -> LegProbe.arm(false));
		final double travelled = ctx.computeOnClient(mc -> Math.hypot(mc.player.getX() - from[0], mc.player.getZ() - from[1]));
		final String smoothness = ctx.computeOnClient(mc -> FilmCamera.smoothness());
		try {
			final List<String> path = ctx.computeOnClient(mc -> FilmCamera.path());
			Files.write(this.out.resolve(clip + "-camera.csv"), path);
			final List<String> legPath = ctx.computeOnClient(mc -> new ArrayList<>(LEGS));
			Files.write(this.out.resolve(clip + "-legs.csv"), legPath);
			final List<String> rider = ctx.computeOnClient(mc -> new ArrayList<>(RIDER));
			Files.write(this.out.resolve(clip + "-rider.csv"), rider);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
		final String line = String.format(Locale.ROOT, "%s: %d frames (%.1f s), rode %d blocks; %s; %s", clip, frames, frames / (double) FPS,
			Math.round(travelled), smoothness, legs);
		this.log.add(line);
		LOGGER.info("[store] {}", line);
	}

	/**
	 * Draws the world {@code partialTick} of the way through the current tick, {@code step} ticks after the frame before,
	 * and saves it to {@code file}.
	 * <ul>
	 * <li>The moment goes on the game's own frame timer for the frame, not only into the delta the renderer is handed: the
	 * mod's legs and Entity Model Features' animations read the timer, and without this every frame in a tick drew the legs
	 * as they were at the tick's last real frame, so they only moved once a tick (stuttering in slow motion).</li>
	 * <li>It waits for the picture to come back from the graphics card before returning: left to come back later, it
	 * sometimes caught the next frame's drawing, so a clip showed one moment twice and then jumped.</li>
	 * </ul>
	 */
	static void drawFrame(final Minecraft mc, final float partialTick, final float step, final Path file) {
		final DeltaTracker timer = mc.getDeltaTracker();
		final DeltaTrackerTimerAccessor clock = (DeltaTrackerTimerAccessor) timer;
		final float deltaTicks = clock.horsingaroundtest$deltaTicks();
		final float partial = clock.horsingaroundtest$partialTick();
		final float pausedPartial = clock.horsingaroundtest$pausedPartialTick();
		final float realtime = clock.horsingaroundtest$realtimeDeltaTicks();
		clock.horsingaroundtest$setDeltaTicks(step);
		clock.horsingaroundtest$setPartialTick(partialTick);
		clock.horsingaroundtest$setPausedPartialTick(partialTick);
		clock.horsingaroundtest$setRealtimeDeltaTicks(step);
		// The riding camera eases its framing on the real clock between frames: give it the frame's own step instead.
		RideCameraAccessor.horsingaroundtest$setLastFrameNanos(System.nanoTime() - (long) (step * 50_000_000.0F));
		try {
			FilmCamera.drawingClip(true);
			FilmCamera.recording(true);
			mc.gameRenderer.update(timer);
			FilmCamera.recording(false);
			mc.gameRenderer.extract(timer, true);
			LegProbe.listen(true);
			// (Collecting the frame's debug lines as the game's own frames do, so what is drawn with the horse, the leg bones,
			// is in this frame and only this one.)
			try (Gizmos.TemporaryCollection gizmos = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
				mc.gameRenderer.render();
			}
		} finally {
			LegProbe.listen(false);
			FilmCamera.drawingClip(false);
			FilmCamera.recording(false);
			clock.horsingaroundtest$setDeltaTicks(deltaTicks);
			clock.horsingaroundtest$setPartialTick(partial);
			clock.horsingaroundtest$setPausedPartialTick(pausedPartial);
			clock.horsingaroundtest$setRealtimeDeltaTicks(realtime);
		}
		Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
			try (image) {
				image.writeToFile(file);
			} catch (final IOException e) {
				LOGGER.warn("[store] couldn't save {}", file, e);
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
		if (!Files.isRegularFile(file)) {
			LOGGER.warn("[store] {} didn't come back from the graphics card", file);
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
