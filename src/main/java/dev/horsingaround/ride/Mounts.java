package dev.horsingaround.ride;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.net.ServerRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;

/**
 * Which mounts get the new controls: horse types in the {@code horsingaround:managed} entity type tag (horse, donkey,
 * mule, skeleton and zombie horse by default; datapacks and other mods can add their own horses), and on a client only
 * while connected to a server that runs this mod. Camels and llamas keep their vanilla controls.
 */
public final class Mounts {
	public static final TagKey<net.minecraft.world.entity.EntityType<?>> MANAGED = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "managed"));

	/** Bumped whenever the answer may have changed (tags loaded, server joined or left); horses cache against it. */
	private static int generation;

	private Mounts() {
	}

	public static int generation() {
		return generation;
	}

	public static void invalidate() {
		generation++;
	}

	public static boolean manages(final Entity horse) {
		return horse.is(MANAGED) && (!horse.level().isClientSide() || ServerRules.present);
	}

	/**
	 * Whether a ridden horse floats and swims in deep water (vanilla's {@code can_float_while_ridden}). Skeleton horses
	 * don't: as in vanilla they walk along the bottom with their rider.
	 */
	public static boolean floats(final Entity horse) {
		return horse.is(EntityTypeTags.CAN_FLOAT_WHILE_RIDDEN);
	}
}
