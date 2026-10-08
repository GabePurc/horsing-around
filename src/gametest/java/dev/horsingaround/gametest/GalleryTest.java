package dev.horsingaround.gametest;

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
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * Not a check: rides the natural-terrain scenery with Fresh Animations and takes clean 1080p shots (no HUD) for the
 * store page, into {@code build/run/clientGameTest/gallery/}. Run with {@code ./gradlew runClientGameTest -Ptests=gallery}.
 */
public final class GalleryTest implements FabricClientGameTest {
	private record Scene(String name, TerrainRideTest.Kind kind, long seed, int time, int variant) {
	}

	private static final Scene[] SCENES = {
		new Scene("forest", TerrainRideTest.Kind.FOREST, 11, 1500, 0),
		new Scene("hills", TerrainRideTest.Kind.HILLS, 31, 12300, 2),
		new Scene("river", TerrainRideTest.Kind.RIVER, 51, 4000, 4),
		new Scene("badlands", TerrainRideTest.Kind.BADLANDS, 61, 10500, 1),
		new Scene("dark_forest", TerrainRideTest.Kind.DENSE_FOREST, 13, 6000, 5),
	};

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!System.getProperty("horsingaround.tests", "").contains("gallery")) {
			return;
		}
		enableFreshAnimations(ctx);
		try (TestSingleplayerContext world = ctx.worldBuilder()
			.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
			.create()) {
			final TestServerContext server = world.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			ctx.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			for (int i = 0; i < SCENES.length; i++) {
				this.scene(ctx, world, SCENES[i], 3000 + i * 100);
			}
			ctx.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
	}

	private void scene(final ClientGameTestContext ctx, final TestSingleplayerContext world, final Scene scene, final int ox) {
		final TestServerContext server = world.getServer();
		final TestInput input = ctx.getInput();
		input.releaseKey(o -> o.keyUp);
		server.runCommand("ride @p dismount");
		server.runCommand("kill @e[type=minecraft:horse]");
		server.runCommand("time set " + scene.time);
		server.runCommand(String.format(Locale.ROOT, "tp @p %d -60 6", ox + 32));
		ctx.waitTicks(20);
		world.getConnection().waitForChunksRender();
		final TerrainRideTest.Route route = server.computeOnServer(s -> TerrainRideTest.build(s.overworld(),
			new TerrainRideTest.Scenario(scene.name, scene.kind, scene.seed, false, 0.0, 0), ox, 0));
		final BlockPos start = route.start();
		final BlockPos target = route.target();
		server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f", start.getX() + 0.5, start.getY(), start.getZ() + 0.5));
		ctx.waitTicks(20);
		world.getConnection().waitForChunksRender();
		server.runCommand(String.format(Locale.ROOT, "execute at @p run summon minecraft:horse ~ ~ ~ {Tame:1b,Variant:%d,"
			+ "equipment:{saddle:{id:\"minecraft:saddle\",count:1}},attributes:[{id:\"minecraft:movement_speed\",base:0.25d}],Tags:[\"gallery\"]}", scene.variant));
		for (int attempt = 0; attempt < 10 && !ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse); attempt++) {
			ctx.waitTicks(5);
			server.runCommand("ride @p mount @e[tag=gallery,limit=1]");
		}
		final float yaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - start.getX()), target.getZ() - start.getZ()));
		input.lookAt(yaw, 12.0F);
		input.holdKey(o -> o.keyUp);
		ctx.waitTicks(2);
		for (int i = 0; i < 3; i++) {
			input.pressKey(o -> o.keySprint);
			ctx.waitTicks(4);
		}
		ctx.waitTicks(40);
		this.shot(ctx, scene.name + "_1_behind");
		this.side(ctx, scene.name + "_2_side", 90.0F);
		ctx.waitTicks(25);
		input.pressKey(o -> o.keyJump);
		ctx.waitTicks(6);
		this.side(ctx, scene.name + "_3_jump", -70.0F);
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		this.shot(ctx, scene.name + "_4_front");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		input.releaseKey(o -> o.keyUp);
		ctx.waitTicks(40);
	}

	/** The riding camera swung round the rider for one rendered frame (no tick passes). */
	private void side(final ClientGameTestContext ctx, final String name, final float swing) {
		final float yaw = ctx.computeOnClient(mc -> mc.player.getYRot());
		final float pitch = ctx.computeOnClient(mc -> mc.player.getXRot());
		ctx.runOnClient(mc -> {
			mc.player.setYRot(yaw + swing);
			mc.player.yRotO = yaw + swing;
			mc.player.setXRot(8.0F);
			mc.player.xRotO = 8.0F;
		});
		this.shot(ctx, name);
		ctx.runOnClient(mc -> {
			mc.player.setYRot(yaw);
			mc.player.yRotO = yaw;
			mc.player.setXRot(pitch);
			mc.player.xRotO = Mth.clamp(pitch, -90.0F, 90.0F);
		});
	}

	private void shot(final ClientGameTestContext ctx, final String name) {
		ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(1920, 1080).disableCounterPrefix()
			.withDestinationDir(FabricLoader.getInstance().getGameDir().resolve("gallery")));
	}

	static void enableFreshAnimations(final ClientGameTestContext ctx) {
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
		if (reload != null) {
			ctx.waitFor(mc -> reload.isDone(), 20 * 120);
			ctx.waitTicks(40);
		}
	}
}
