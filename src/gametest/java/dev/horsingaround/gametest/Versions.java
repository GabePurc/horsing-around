package dev.horsingaround.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** What differs between Minecraft versions where several tests need it: one place per difference (renames are in stonecutter.gradle). */
public final class Versions {
	private Versions() {
	}

	/** Whether the HUD is hidden (F1). */
	public static boolean hudHidden(final Minecraft mc) {
		//? if >=26.2 {
		return mc.gui.hud.isHidden();
		//?} else
		/*return mc.options.hideGui;*/
	}

	// ---- What summon commands write, which changed with the versions ----

	/** A saddle on a horse-like mount (horses, camels). */
	public static String saddle() {
		//? if >=1.21.5 {
		return "equipment:{saddle:{id:\"minecraft:saddle\",count:1}}";
		//?} elif >=1.20.5 {
		/*return "SaddleItem:{id:\"minecraft:saddle\",count:1}";
		*///?} else
		/*return "SaddleItem:{id:\"minecraft:saddle\",Count:1b}";*/
	}

	/** A saddle on a pig or a strider. */
	public static String pigSaddle() {
		//? if >=1.21.5 {
		return saddle();
		//?} else
		/*return "Saddle:1b";*/
	}

	/**
	 * Base attribute values, as pairs of name and value ({@code "movement_speed", 0.225}): the names 1.21.2 gives them
	 * (movement_speed, jump_strength, max_health). (The keys went lower case in 1.21: before, a summon with the new ones keeps
	 * the defaults without a word.)
	 */
	public static String attributes(final Object... nameAndValue) {
		final StringBuilder list = new StringBuilder();
		for (int i = 0; i < nameAndValue.length; i += 2) {
			final String name = (String) nameAndValue[i];
			final String value = java.math.BigDecimal.valueOf(((Number) nameAndValue[i + 1]).doubleValue()).toPlainString();
			list.append(i == 0 ? "" : ",");
			//? if >=1.21 {
			list.append("{id:\"").append(attribute(name)).append("\",base:").append(value).append("d}");
			//?} else
			/*list.append("{Name:\"").append(attribute(name)).append("\",Base:").append(value).append("d}");*/
		}
		//? if >=1.21 {
		return "attributes:[" + list + "]";
		//?} else
		/*return "Attributes:[" + list + "]";*/
	}

	/** An attribute's id from the name 1.21.2 gives it ({@code max_health}): before, its group is in front. */
	public static String attribute(final String name) {
		//? if >=1.21.2 {
		return "minecraft:" + name;
		//?} else
		/*return "minecraft:" + (name.equals("jump_strength") ? "horse.jump_strength" : "generic." + name);*/
	}

	/** Tied by a lead to the fence knot at (x, y, z). */
	public static String leash(final int x, final int y, final int z) {
		//? if >=1.21 {
		return "leash:[I;" + x + "," + y + "," + z + "]";
		//?} else
		/*return "Leash:{X:" + x + ",Y:" + y + ",Z:" + z + "}";*/
	}

	/** An effect that never wears off, its particles hidden ({@code effect} as 1.20.2 names it: fire_resistance). */
	public static String endlessEffect(final String effect) {
		//? if >=1.20.2 {
		return "active_effects:[{id:\"minecraft:" + effect + "\",duration:-1,show_particles:0b}]";
		//?} else
		/*return "ActiveEffects:[{Id:" + net.minecraft.world.effect.MobEffect.getId(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(Identifier.withDefaultNamespace(effect))) + ",Duration:-1,ShowParticles:0b}]";*/
	}

	/** What a mob holds and wears on its head. */
	public static String gear(final String mainHand, final String head) {
		//? if >=1.21.5 {
		return "equipment:{mainhand:{id:\"minecraft:" + mainHand + "\",count:1},head:{id:\"minecraft:" + head + "\",count:1}}";
		//?} elif >=1.20.5 {
		/*return "HandItems:[{id:\"minecraft:" + mainHand + "\",count:1},{}],ArmorItems:[{},{},{},{id:\"minecraft:" + head + "\",count:1}]";
		*///?} else
		/*return "HandItems:[{id:\"minecraft:" + mainHand + "\",Count:1b},{}],ArmorItems:[{},{},{},{id:\"minecraft:" + head + "\",Count:1b}]";*/
	}

	/** Whether this version has the entity type {@code id} (minecraft's). */
	public static boolean hasEntity(final String id) {
		return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.withDefaultNamespace(id));
	}

	/** Short grass's id (just grass before 1.20.3). */
	//? if >=1.20.3 {
	public static final String SHORT_GRASS = "short_grass";
	//?} else
	/*public static final String SHORT_GRASS = "grass";*/

	/** Whether this version has the item {@code id} (minecraft's). */
	public static boolean hasItem(final String id) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(Identifier.withDefaultNamespace(id));
	}

	/** A game rule's command, by its 1.21.11 name (spawn_mobs, advance_time). */
	public static String rule(final String name) {
		//? if >=1.21.11 {
		return "gamerule " + name;
		//?} else
		/*return "gamerule " + switch (name) { case "spawn_mobs" -> "doMobSpawning"; case "advance_time" -> "doDaylightCycle"; default -> name; };*/
	}

	/** Hides or shows the HUD, as F1 does. */
	public static void toggleHud(final Minecraft mc) {
		//? if >=26.2 {
		mc.gui.hud.toggle();
		//?} else
		/*mc.options.hideGui = !mc.options.hideGui;*/
	}
}
