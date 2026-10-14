package dev.horsingaround.ride;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.net.ServerRules;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
//? if <1.21.11
/*import net.minecraft.world.entity.EntityType;*/

/**
 * Which mounts get the new controls: horse types in the {@code horsingaround:managed} entity type tag (horse, donkey,
 * mule, skeleton and zombie horse by default; datapacks and other mods can add their own horses), and on a client only
 * while connected to a server that runs this mod. Camels and llamas keep their vanilla controls.
 */
public final class Mounts {
	public static final TagKey<net.minecraft.world.entity.EntityType<?>> MANAGED = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "managed"));

	/**
	 * Bumped whenever the answer may have changed (tags loaded, server joined or left); horses cache against it. Atomic:
	 * in singleplayer the integrated server's thread and the client's both bump and read it.
	 */
	private static final AtomicInteger GENERATION = new AtomicInteger();

	private Mounts() {
	}

	public static int generation() {
		return GENERATION.get();
	}

	public static void invalidate() {
		GENERATION.incrementAndGet();
	}

	public static boolean manages(final Entity horse) {
		//? if >=26.1 {
		return horse.is(MANAGED) && (!horse.level().isClientSide() || ServerRules.present);
		//?} else
		/*return horse.getType().is(MANAGED) && (!horse.level().isClientSide() || ServerRules.present);*/
	}

	/**
	 * Whether a ridden horse floats and swims in deep water (vanilla's {@code can_float_while_ridden}). Skeleton horses
	 * don't: as in vanilla they walk along the bottom with their rider. (Before 1.21.11 vanilla horses sink; the ride
	 * floats every horse but the skeleton horse there.)
	 */
	public static boolean floats(final Entity horse) {
		//? if >=26.1 {
		return horse.is(EntityTypeTags.CAN_FLOAT_WHILE_RIDDEN);
		//?} elif >=1.21.11 {
		/*return horse.getType().is(EntityTypeTags.CAN_FLOAT_WHILE_RIDDEN);
		*///?} else
		/*return horse.getType() != EntityType.SKELETON_HORSE;*/
	}
}
