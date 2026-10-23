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

	/**
	 * The wild ride (-Dhorsingaround.smoke.wild=seed): a saddled horse where the player is, on whatever the world has
	 * there, with the player on it.
	 */
	static void wild(final MinecraftServer server) {
		final ServerLevel level = server.overworld();
		for (final String rule : Compat.STILL_RULES) {
			command(server, rule);
		}
		command(server, "time set noon");
		command(server, "weather clear");
		final ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		// (-Dhorsingaround.smoke.wildAt=x,z starts it at a spot of its own: the player's spawn is spread at random.)
		final String at = System.getProperty("horsingaround.smoke.wildAt");
		final int x = at == null ? player.getBlockX() : Integer.parseInt(at.split(",")[0].trim());
		final int z = at == null ? player.getBlockZ() : Integer.parseInt(at.split(",")[1].trim());
		level.getChunk(x >> 4, z >> 4);
		final int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		groundY = ground;
		command(server, "tp @p " + (x + 0.5) + " " + ground + " " + (z - 1.5) + " 0 0");
		final AbstractHorse horse = summon(server, level, AbstractHorse.class, "horse", x + 0.5, ground, z + 0.5, "Variant:3,");
		horse.setTamed(true);
		Compat.saddle(horse);
		if (!player.startRiding(horse)) {
			throw new IllegalStateException("The player could not get on the horse");
		}
	}

	/** Where the rough course starts and ends (z), past where the ride on the flat stops. */
	static final int ROUGH_START = 96;
	static final int ROUGH_END = 196;

	/**
	 * A course of rough ground across the whole run (wider than the horse could go round), ridden after the stop: half
	 * and full steps up and down, a staircase up and down, a hedge of leaves, a stretch of uneven ground, and single steps
	 * at a gallop. Every move the rider's client makes over it must be one the server accepts. This builds as far as
	 * is loaded from where the ride stops; {@link #roughFar} the rest.
	 */
	static void rough(final MinecraftServer server) {
		final int g = groundY;
		// Walking: a half step, a full step, a half more, and down again.
		fill(server, ROUGH_START, ROUGH_START, g, "minecraft:stone_slab[type=bottom]");
		fill(server, 97, 99, g, "minecraft:stone");
		fill(server, 100, 100, g + 1, "minecraft:stone_slab[type=bottom]");
		fill(server, 100, 103, g, "minecraft:stone");
		fill(server, 101, 103, g + 1, "minecraft:stone");
		fill(server, 104, 104, g + 1, "minecraft:stone_slab[type=bottom]");
		fill(server, 104, 106, g, "minecraft:stone");
		// A single full step up and down.
		fill(server, 110, 112, g, "minecraft:stone");
		// A staircase up three, a landing, and down three.
		for (int i = 0; i < 3; i++) {
			fill(server, 116 + i, 116 + i, g + i, "minecraft:stone_stairs[facing=south]");
			if (i > 0) {
				fill(server, 116 + i, 116 + i, g, g + i - 1, "minecraft:stone");
			}
			fill(server, 124 - i, 124 - i, g + i, "minecraft:stone_stairs[facing=north]");
			if (i > 0) {
				fill(server, 124 - i, 124 - i, g, g + i - 1, "minecraft:stone");
			}
		}
		fill(server, 119, 121, g, g + 2, "minecraft:stone");
		// Cantering: a hedge of leaves, then uneven ground (a fixed pattern of slabs and blocks).
		fill(server, 130, 131, g, g + 1, "minecraft:oak_leaves[persistent=true]");
		for (int z = 136; z < 160; z++) {
			final int kind = (z * 7 + 3) % 5;
			if (kind == 1 || kind == 3) {
				fill(server, z, z, g, "minecraft:stone_slab[type=bottom]");
			} else if (kind == 4) {
				fill(server, z, z, g, "minecraft:grass_block");
			}
		}
	}

	/** The rest of the course, galloping: single steps up and down (built once the horse is near, so it is loaded). */
	static void roughFar(final MinecraftServer server) {
		final int g = groundY;
		for (int z = 166; z < ROUGH_END - 6; z += 8) {
			fill(server, z, z + 2, g, "minecraft:stone");
		}
	}

	/** Fills rows {@code z0..z1} across the run, x -7..7, at height {@code y}. */
	private static void fill(final MinecraftServer server, final int z0, final int z1, final int y, final String block) {
		fill(server, z0, z1, y, y, block);
	}

	private static void fill(final MinecraftServer server, final int z0, final int z1, final int y0, final int y1, final String block) {
		command(server, "fill -7 " + y0 + " " + z0 + " 7 " + y1 + " " + z1 + " " + block);
	}

	/** Gets the rider off and over to the lead spot. */
	static void leadSpot(final MinecraftServer server) {
		server.getPlayerList().getPlayers().get(0).stopRiding();
		command(server, "tp @p " + number(LEAD_X) + " " + groundY + " " + number(LEAD_Z) + " 180 0");
	}

	/**
	 * Then the cow on a lead to them, once the ground there is loaded round the player (the ride ends far down the
	 * course, and from 1.21.9 no spawn chunks stay loaded back here).
	 */
	static void leadCow(final MinecraftServer server) {
		final ServerLevel level = server.overworld();
		final ServerPlayer player = server.getPlayerList().getPlayers().get(0);
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
