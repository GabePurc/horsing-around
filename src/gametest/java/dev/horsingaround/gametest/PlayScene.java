package dev.horsingaround.gametest;

import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Play runs boot straight into what's being tested (user, 2026-11-06: no taming a horse every time): with {@code
 * -Dhorsingaround.play=<scene>} the game opens a fresh world, always the same, and puts the player on the scene's horse in the
 * hills. For play runs only: a jar of its own (playSceneJar), never the mod's, nor the tests', whose mixins would come with it.
 */
public final class PlayScene implements ClientModInitializer {
	private static final String ICY = "icys-better-horses:";
	private static final String TAG = "play_scene";
	/** The world's name (play runs delete it on launch) and seed. */
	private static final String WORLD = "Scene";
	private static final long SEED = 8675309L;
	/** An open place in that world's windswept hills, and the way the horse and rider face from it. */
	private static final int START_X = 2730;
	private static final int START_Z = 1452;
	private static final float START_YAW = 180.0F;

	/** A scene: the breed to ride, and what it pulls. */
	private record Scene(String breed, boolean cart) {
	}

	private enum Step {
		TITLE, JOINING, ARRIVING, GEARED, RIDING, DONE
	}

	private Scene scene;
	private Step step = Step.TITLE;
	private int ticks;

	@Override
	public void onInitializeClient() {
		final String name = System.getProperty("horsingaround.play", "");
		this.scene = switch (name) {
			case "cart" -> new Scene("american_paint_horse", true);
			case "wagon" -> new Scene("belgian_horse", true);
			case "horse" -> new Scene("american_paint_horse", false);
			default -> null;
		};
		if (this.scene != null) {
			ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		}
	}

	private void tick(final Minecraft mc) {
		switch (this.step) {
			case TITLE -> {
				if (mc.gui.screen() instanceof TitleScreen) {
					mc.createWorldOpenFlows().createFreshLevel(
						WORLD,
						new LevelSettings(WORLD, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
						new WorldOptions(SEED, true, false),
						WorldPresets::createNormalWorldDimensions,
						mc.gui.screen()
					);
					this.step = Step.JOINING;
				}
			}
			case JOINING -> {
				final MinecraftServer server = mc.getSingleplayerServer();
				if (server != null && mc.player != null && mc.level != null && ++this.ticks > 20) {
					server.execute(() -> setUp(server));
					this.step = Step.ARRIVING;
					this.ticks = 0;
				}
			}
			case ARRIVING -> {
				// (Long enough for the chunks there to load: only then can the horse be found.)
				if (++this.ticks > 40) {
					final MinecraftServer server = mc.getSingleplayerServer();
					server.execute(() -> gearUp(server));
					this.step = Step.GEARED;
					this.ticks = 0;
				}
			}
			case GEARED -> {
				// (Long enough for a cart to be hitched.)
				if (++this.ticks > 40) {
					final MinecraftServer server = mc.getSingleplayerServer();
					server.execute(() -> command(server, "ride @p mount @e[tag=" + TAG + ",limit=1]"));
					this.step = Step.RIDING;
					this.ticks = 0;
				}
			}
			case RIDING -> {
				if (mc.player != null && mc.player.getVehicle() != null && ++this.ticks == 1) {
					mc.player.setYRot(START_YAW);
					mc.player.setXRot(10.0F);
				}
				if (mc.player != null && mc.player.getVehicle() != null && this.ticks > 60) {
					System.out.println("[play-scene] riding " + mc.player.getVehicle().getType() + " at " + mc.player.getVehicle().blockPosition());
					if (Boolean.getBoolean("horsingaround.play.check")) {
						Screenshot.grab(mc.gameDirectory, "scene-check.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {
						});
						mc.stop();
					}
					this.step = Step.DONE;
				}
			}
			case DONE -> {
			}
		}
	}

	/** On the server's thread: goes to the hills and brings out the horse. */
	private void setUp(final MinecraftServer server) {
		final ServerLevel level = server.overworld();
		final List<ServerPlayer> players = server.getPlayerList().getPlayers();
		final ServerPlayer player = players.getFirst();
		final BlockPos near = new BlockPos(START_X, 64, START_Z);
		// (Generated first: until then the height there reads as the bottom of the world.)
		final int y = level.getChunkAt(near).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX() & 15, near.getZ() & 15) + 1;
		player.teleportTo(near.getX() + 0.5, y + 1.0, near.getZ() + 0.5);
		command(server, "time set noon");
		command(server, "gamerule advance_time false");
		command(server, String.format(Locale.ROOT, "summon %s%s %d.5 %d %d.5 {Rotation:[180f,0f],NoAI:1b,equipment:{saddle:{id:\"%supgraded_saddle\",count:1}},Tags:[\"%s\"]}", ICY, this.scene.breed, near.getX(), y, near.getZ(), ICY, TAG));
	}

	/** On the server's thread: the horse is the player's, and in its gear. */
	private void gearUp(final MinecraftServer server) {
		final ServerLevel level = server.overworld();
		final AbstractHorse horse = level.getEntities(EntityTypeTest.forClass(AbstractHorse.class), candidate -> candidate.entityTags().contains(TAG)).getFirst();
		horse.tameWithName(server.getPlayerList().getPlayers().getFirst());
		if (this.scene.cart) {
			try {
				final int slot = ((Enum<?>) Class.forName("icy.betterhorses.net.inventory.GearSlot").getField("STABILIZER").get(null)).ordinal();
				final SimpleContainer gear = (SimpleContainer) horse.getClass().getMethod("bh_getGearContainer").invoke(horse);
				gear.setItem(slot, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(ICY + "horse_cart_gear"))));
			} catch (final ReflectiveOperationException e) {
				throw new IllegalStateException("Icy's gear changed", e);
			}
		}
	}

	private static void command(final MinecraftServer server, final String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}
}
