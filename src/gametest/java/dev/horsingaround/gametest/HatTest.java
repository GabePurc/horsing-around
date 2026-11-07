package dev.horsingaround.gametest;

import com.mojang.authlib.GameProfile;
import dev.horsingaround.client.config.HorseSettingsScreen;
import dev.horsingaround.client.cosmetic.HatState;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.client.cosmetic.Supporters;
import dev.horsingaround.net.HatPayload;
import dev.horsingaround.net.PlayerHatPayload;
import dev.horsingaround.ride.HorseConfig;
import io.github.gabepurc.spawnproof.test.SpawnproofTest;
import io.github.gabepurc.spawnproof.test.TestContext;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The supporters' cowboy hat: drawn in the chosen colour, the helmet hidden (but still worn), passed on through the
 * server, gone when switched off, and never drawn for players who aren't supporters. A Spawnproof test: its checks and
 * screenshots are in the run's {@code spawnproof/} folder. Run with {@code ./gradlew runClientGameTest -Ptests=hat}.
 */
public final class HatTest implements SpawnproofTest {
	private static final int RED = 0x8E2B2B;

	private record Drawn(int hat, boolean helmetDrawn) {
	}

	@Override
	public void run(final TestContext t) {
		final ClientGameTestContext ctx = t.game();
		TestPacks.enable(ctx);
		try (TestSingleplayerContext world = ctx.worldBuilder()
			.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
			.create()) {
			final TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("item replace entity @p armor.head with minecraft:diamond_helmet");
			world.getConnection().waitForChunksRender();
			final UUID me = ctx.computeOnClient(mc -> mc.player.getUUID());

			t.section("Not a supporter");
			ctx.runOnClient(mc -> {
				HorseConfig.get().hat = true;
				HorseConfig.get().hatColor = RED;
				Hats.sendOwn();
			});
			ctx.waitTicks(5);
			Drawn drawn = drawn(ctx);
			t.checkEqual("no hat drawn", "none", colour(drawn.hat));
			t.check("helmet drawn", drawn.helmetDrawn);
			ctx.setScreen(() -> new HorseSettingsScreen(null));
			ctx.waitTicks(3);
			scrollSettingsToBottom(ctx);
			t.screenshot("settings_not_supporter");
			ctx.setScreen(() -> null);

			t.section("A supporter wearing the hat");
			ctx.runOnClient(mc -> {
				Supporters.addForTesting(me);
				Hats.sendOwn();
			});
			ctx.waitTicks(5);
			drawn = drawn(ctx);
			t.checkEqual("hat drawn in the chosen colour", colour(RED), colour(drawn.hat));
			t.check("helmet not drawn", !drawn.helmetDrawn);
			t.check("helmet still worn", ctx.computeOnClient(mc -> !mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()));
			t.checkEqual("the server passes the hat on to everyone", colour(RED), colour(ctx.computeOnClient(mc -> Hats.relayed(me))));
			// Quick changes wait out the server's half second between them, and the latest one is passed on. The first
			// change is awaited so the half second starts at a known time.
			ctx.runOnClient(mc -> {
				HorseConfig.get().hatColor = 0x2B8E2B;
				Hats.sendOwn();
			});
			ctx.waitFor(mc -> Hats.relayed(me) == 0x2B8E2B, 40);
			ctx.runOnClient(mc -> {
				HorseConfig.get().hatColor = 0x8E2B8E;
				Hats.sendOwn();
				HorseConfig.get().hatColor = 0x2B2B8E;
				Hats.sendOwn();
			});
			ctx.waitTicks(2);
			t.checkEqual("quick hat changes wait", colour(0x2B8E2B), colour(ctx.computeOnClient(mc -> Hats.relayed(me))));
			t.check("then the latest one is passed on", relayedWithin(ctx, me, 0x2B2B8E, 20));
			ctx.runOnClient(mc -> {
				HorseConfig.get().hatColor = RED;
				Hats.sendOwn();
			});
			t.check("and the hat can change back", relayedWithin(ctx, me, RED, 20));
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(2);
			t.screenshot("front");
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
				t.screenshot(String.format(Locale.ROOT, "close_%06x", color));
			}
			t.check("another supporter's hat is drawn in their colour", ctx.computeOnClient(mc -> {
				final net.minecraft.world.entity.player.Player other = mc.level.getPlayerByUUID(friend);
				return other != null && Hats.colorFor(other) == 0xC8A27A;
			}));
			t.check("a hat from someone not on our list waits, and asks once for a fresh list", ctx.computeOnClient(mc -> {
				final net.minecraft.world.entity.player.Player other = mc.level.getPlayerByUUID(friend);
				Supporters.removeForTesting(friend);
				Supporters.allowRefreshForTesting();
				final int before = Supporters.refreshesStarted();
				Hats.receive(new PlayerHatPayload(friend, RED));
				Hats.receive(new PlayerHatPayload(friend, RED));
				return other != null && Hats.colorFor(other) == HatPayload.NONE && Supporters.refreshesStarted() == before + 1;
			}));
			t.check("once they're on the list, the hat they announced is drawn", ctx.computeOnClient(mc -> {
				final net.minecraft.world.entity.player.Player other = mc.level.getPlayerByUUID(friend);
				Supporters.addForTesting(friend);
				return other != null && Hats.colorFor(other) == RED;
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
			t.screenshot("settings_supporter");
			ctx.setScreen(() -> null);

			t.section("Riding in the hat");
			server.runCommand("summon minecraft:horse ~ ~ ~2 {Tame:1b,equipment:{saddle:{id:\"minecraft:saddle\",count:1}},Tags:[\"hat_horse\"]}");
			server.runCommand("ride @p mount @e[tag=hat_horse,limit=1]");
			ctx.waitFor(mc -> mc.player.getVehicle() instanceof AbstractHorse);
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(2);
			t.screenshot("riding_front");
			t.checkEqual("hat drawn while riding", colour(RED), colour(drawn(ctx).hat));
			server.runCommand("ride @p dismount");

			t.section("Hat off");
			ctx.runOnClient(mc -> {
				HorseConfig.get().hat = false;
				Hats.sendOwn();
			});
			ctx.waitTicks(5);
			drawn = drawn(ctx);
			t.checkEqual("no hat drawn", "none", colour(drawn.hat));
			t.check("helmet drawn again", drawn.helmetDrawn);
			t.checkEqual("the server passes that on too", "none", colour(ctx.computeOnClient(mc -> Hats.relayed(me))));
			ctx.runOnClient(mc -> {
				HorseConfig.reset();
				Supporters.removeForTesting(me);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}

	/** A hat colour as the report shows it: "#8e2b2b", or "none". */
	private static String colour(final int hat) {
		return hat == HatPayload.NONE ? "none" : String.format(Locale.ROOT, "#%06x", hat & 0xFFFFFF);
	}

	/** Waits up to {@code ticks} for the server to pass on {@code colour} for {@code player}; whether it did. */
	private static boolean relayedWithin(final ClientGameTestContext ctx, final UUID player, final int colour, final int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (ctx.computeOnClient(mc -> Hats.relayed(player)) == colour) {
				return true;
			}
			ctx.waitTick();
		}
		return ctx.computeOnClient(mc -> Hats.relayed(player)) == colour;
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
		ctx.waitTick();
	}
}
