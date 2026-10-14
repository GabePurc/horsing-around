package dev.horsingaround.smoke;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The scene the smoke test rides through, built on the integrated server's thread: a saddled horse and its rider at the
 * start of a straight run south (+Z), and, behind them, a row of mobs each on a lead to a fence post of its own.
 */
final class Scene {
	static final double START_X = 0.5;
	static final double START_Z = 0.5;
	/** Fence posts of the row (z), the mobs' side of it (z), and the posts' x. */
	static final int ROW_Z = -12;
	static final double ROW_MOB_Z = ROW_Z + 2.5;
	static final int[] ROW_X = {-8, -4, 0, 4, 8};
	private static final String[] ROW_MOBS = {"cow", "pig", "sheep", "horse", "llama"};
	/** The rider's stand for the lead shots, and the cow on the rider's own lead. */
	static final double LEAD_X = 20.5;
	static final double LEAD_Z = -2.5;
	static final double LEAD_COW_X = LEAD_X + 3.0;

	/** The y the horses stand at on the flat ground. */
	static volatile int groundY;

	private Scene() {
	}

	/** Rules, time and weather; the ground; the row of leashed mobs; the horse, with the player on it. */
	static void build(final MinecraftServer server) {
		final ServerLevel level = server.overworld();
		for (final String rule : Compat.STILL_RULES) {
			command(server, rule);
		}
		command(server, "time set noon");
		command(server, "weather clear");
		final int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
		groundY = ground;

		for (int i = 0; i < ROW_X.length; i++) {
			final BlockPos post = new BlockPos(ROW_X[i], ground, ROW_Z);
			command(server, "setblock " + ROW_X[i] + " " + ground + " " + ROW_Z + " minecraft:oak_fence");
			if (!level.getBlockState(post).is(Blocks.OAK_FENCE)) {
				throw new IllegalStateException("The fence post at " + post + " was not placed: " + level.getBlockState(post));
			}
			final Mob mob = summon(server, level, Mob.class, ROW_MOBS[i], ROW_X[i] + 0.5, ground, ROW_MOB_Z, "NoAI:1b,");
			mob.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(level, post), true);
		}

		final AbstractHorse horse = summon(server, level, AbstractHorse.class, "horse", START_X, ground, START_Z, "Variant:3,");
		horse.setTamed(true);
		Compat.saddle(horse);
		command(server, "tp @p " + number(START_X) + " " + ground + " " + number(START_Z - 2.0) + " 0 0");
		final ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		if (!player.startRiding(horse)) {
			throw new IllegalStateException("The player could not get on the horse");
		}
	}

	/** Gets the rider off and over to the lead spot, with a cow on a lead to them. */
	static void leadCow(final MinecraftServer server) {
		final ServerLevel level = server.overworld();
		final ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		player.stopRiding();
		command(server, "tp @p " + number(LEAD_X) + " " + groundY + " " + number(LEAD_Z) + " 180 0");
		final Mob cow = summon(server, level, Mob.class, "cow", LEAD_COW_X, groundY, LEAD_Z, "NoAI:1b,");
		cow.setLeashedTo(player, true);
	}

	private static <T extends Entity> T summon(
		final MinecraftServer server, final ServerLevel level, final Class<T> type, final String id, final double x, final double y, final double z, final String nbt
	) {
		command(server, "summon minecraft:" + id + " " + number(x) + " " + y + " " + number(z) + " {" + nbt + "Rotation:[0.0f,0.0f]}");
		final List<T> found = level.getEntitiesOfClass(type, new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 2.0, z + 0.5));
		if (found.size() != 1) {
			throw new IllegalStateException("Expected one " + id + " at " + x + " " + y + " " + z + ", found " + found.size());
		}
		return found.get(0);
	}

	private static void command(final MinecraftServer server, final String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}

	private static String number(final double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
