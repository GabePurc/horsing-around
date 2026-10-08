package dev.horsingaround.gametest;

import dev.horsingaround.client.config.HorseSettingsScreen;
import dev.horsingaround.client.cosmetic.HatState;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.client.cosmetic.Supporters;
import dev.horsingaround.net.HatPayload;
import dev.horsingaround.ride.HorseConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import com.mojang.authlib.GameProfile;
import dev.horsingaround.net.PlayerHatPayload;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * The supporters' cowboy hat: drawn in the chosen colour, the helmet hidden (but still worn), passed on through the
 * server, gone when switched off, and never drawn for players who aren't supporters. Writes
 * {@code horsingaround-hat-report.txt} and screenshots. Run with {@code ./gradlew runClientGameTest -Ptests=hat}.
 */
public final class HatTest implements FabricClientGameTest {
	private static final int RED = 0x8E2B2B;

	private final List<String> report = new ArrayList<>();
	private int failures;

	private record Drawn(int hat, boolean helmetDrawn) {
	}

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!System.getProperty("horsingaround.tests", "hat").contains("hat")) {
			return;
		}
		boolean completed = false;
		try (TestSingleplayerContext world = ctx.worldBuilder()
			.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
			.create()) {
			final TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("item replace entity @p armor.head with minecraft:diamond_helmet");
			world.getConnection().waitForChunksRender();
			final UUID me = ctx.computeOnClient(mc -> mc.player.getUUID());

			section("Not a supporter");
			ctx.runOnClient(mc -> {
				HorseConfig.get().hat = true;
				HorseConfig.get().hatColor = RED;
				Hats.sendOwn();
			});
			ctx.waitTicks(5);
			Drawn drawn = drawn(ctx);
			check("no hat drawn", drawn.hat == HatPayload.NONE);
			check("helmet drawn", drawn.helmetDrawn);
			ctx.setScreen(() -> new HorseSettingsScreen(null));
			ctx.waitTicks(3);
			scrollSettingsToBottom(ctx);
			screenshot(ctx, "hat_settings_not_supporter");
			ctx.setScreen(() -> null);

			section("A supporter wearing the hat");
			ctx.runOnClient(mc -> {
				Supporters.addForTesting(me);
				Hats.sendOwn();
			});
			ctx.waitTicks(5);
			drawn = drawn(ctx);
			check("hat drawn in the chosen colour", drawn.hat == RED);
			check("helmet not drawn", !drawn.helmetDrawn);
			check("helmet still worn", ctx.computeOnClient(mc -> !mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()));
			check("the server passes the hat on to everyone", ctx.computeOnClient(mc -> Hats.relayed(me)) == RED);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(2);
			screenshot(ctx, "hat_front");
			// Close up from three quarters, in a few colours: another supporter (in a diamond helmet) seen from a fixed camera
			// (an invisible armour stand), which also shows what other players see.
			server.runCommand("tp @p 0.5 -60 8 180 0");
			server.runCommand("summon minecraft:armor_stand 1.6 -59.95 -1.1 {Invisible:1b,NoGravity:1b,Tags:[\"hat_camera\"],Rotation:[34f,12f]}");
			ctx.waitTicks(5);
			final UUID friend = UUID.fromString("00000000-0000-4000-8000-00000000cafe");
			ctx.runOnClient(mc -> {
				Supporters.addForTesting(friend);
				final RemotePlayer other = new RemotePlayer(mc.level, new GameProfile(friend, "Friend"));
				other.setId(Integer.MAX_VALUE - 7);
				other.snapTo(0.5, -60.0, 0.5, 180.0F, 0.0F);
				other.yHeadRot = 180.0F;
				other.yBodyRot = 180.0F;
				other.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
				mc.level.addEntity(other);
			});
			for (final int color : new int[] {0x8B5A2B, RED, 0x2B2B2B, 0xF4F1EA, 0x2F4A6E, 0xC8A27A}) {
				ctx.runOnClient(mc -> {
					Hats.receive(new PlayerHatPayload(friend, color));
					mc.setCameraEntity(mc.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
						new net.minecraft.world.phys.AABB(-4, -64, -4, 5, -55, 5), net.minecraft.world.entity.decoration.ArmorStand::isInvisible).get(0));
					mc.options.setCameraType(CameraType.FIRST_PERSON);
					if (!mc.gui.hud.isHidden()) {
						mc.gui.hud.toggle();
					}
				});
				ctx.waitTicks(1);
				screenshot(ctx, String.format(Locale.ROOT, "hat_close_%06x", color));
			}
			check("another supporter's hat is drawn in their colour", ctx.computeOnClient(mc -> {
				final net.minecraft.world.entity.player.Player other = mc.level.getPlayerByUUID(friend);
				return other != null && Hats.colorFor(other) == 0xC8A27A;
			}));
			ctx.runOnClient(mc -> {
				Hats.receive(new PlayerHatPayload(friend, HatPayload.NONE));
				mc.setCameraEntity(mc.player);
				mc.gui.hud.toggle();
				final net.minecraft.world.entity.player.Player other = mc.level.getPlayerByUUID(friend);
				if (other != null) {
					other.discard();
				}
				Supporters.removeForTesting(friend);
			});
			server.runCommand("kill @e[tag=hat_camera]");
			server.runCommand("tp @p 0.5 -60 0.5 180 0");
			ctx.setScreen(() -> new HorseSettingsScreen(null));
			ctx.waitTicks(3);
			scrollSettingsToBottom(ctx);
			screenshot(ctx, "hat_settings_supporter");
			ctx.setScreen(() -> null);

			section("Riding in the hat");
			server.runCommand("summon minecraft:horse ~ ~ ~2 {Tame:1b,equipment:{saddle:{id:\"minecraft:saddle\",count:1}},Tags:[\"hat_horse\"]}");
			server.runCommand("ride @p mount @e[tag=hat_horse,limit=1]");
			ctx.waitFor(mc -> mc.player.getVehicle() instanceof AbstractHorse);
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(2);
			screenshot(ctx, "hat_riding_front");
			check("hat drawn while riding", drawn(ctx).hat == RED);
			server.runCommand("ride @p dismount");

			section("Hat off");
			ctx.runOnClient(mc -> {
				HorseConfig.get().hat = false;
				Hats.sendOwn();
			});
			ctx.waitTicks(5);
			drawn = drawn(ctx);
			check("no hat drawn", drawn.hat == HatPayload.NONE);
			check("helmet drawn again", drawn.helmetDrawn);
			check("the server passes that on too", ctx.computeOnClient(mc -> Hats.relayed(me)) == HatPayload.NONE);
			ctx.runOnClient(mc -> {
				HorseConfig.reset();
				Supporters.removeForTesting(me);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			completed = true;
		} finally {
			if (!completed) {
				this.failures++;
				log("INCOMPLETE: the run stopped early (see the log for the exception)");
			}
			writeReport();
		}
		TestSummary.failed(this.failures, "horsingaround-hat-report.txt");
	}

	/** Builds the local player's render state the way the game does each frame. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Drawn drawn(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			final EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(mc.player);
			final AvatarRenderState state = (AvatarRenderState) renderer.createRenderState(mc.player, 1.0F);
			return new Drawn(((HatState) state).horsingaround$hatColor(), !state.headEquipment.isEmpty());
		});
	}

	private static void scrollSettingsToBottom(final ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.gui.screen().children().forEach(child -> {
			if (child instanceof net.minecraft.client.gui.components.AbstractScrollArea area) {
				area.setScrollAmount(area.maxScrollAmount());
			}
		}));
		ctx.waitTicks(1);
	}

	private void screenshot(final ClientGameTestContext ctx, final String name) {
		log("  screenshot %s -> %s", name, ctx.takeScreenshot("horsingaround_" + name));
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

	private void log(final String format, final Object... args) {
		final String line = String.format(Locale.ROOT, format, args);
		this.report.add(line);
		System.out.println("[hat-test] " + line);
	}

	private void writeReport() {
		final Path file = FabricLoader.getInstance().getGameDir().resolve("horsingaround-hat-report.txt");
		try {
			Files.write(file, this.report);
		} catch (final IOException e) {
			throw new RuntimeException("Could not write " + file, e);
		}
	}
}
