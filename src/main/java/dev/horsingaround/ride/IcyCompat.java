package dev.horsingaround.ride;

import dev.horsingaround.Platform;
import dev.horsingaround.net.ServerRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Icy's Better Horses (user request, 2026-10-31). With it installed too, the server picks whose riding horses use
 * ({@link HorseConfig#ridingStyle}: the host's setting in singleplayer, the server's own file on a server):
 * <ul>
 * <li>this mod's (the default): it rides Icy's breeds as well as vanilla's horses (the managed tag lists them), and Icy's
 * own riding that would fight it (gaits, double-tap auto-ride, swim boost) is turned off by the mixins in mixin/icy and
 * client/mixin/icy. Everything else of Icy's stays: breeds, bond, tack, carts, combat, rearing, its models.</li>
 * <li>Icy's: this mod leaves every horse alone.</li>
 * </ul>
 * Clients hear the server's choice in {@code StylePayload}.
 */
public final class IcyCompat {
	public static final String MOD_ID = "icys-better-horses";
	public static final boolean LOADED = Platform.isModLoaded(MOD_ID);
	/** The horses Icy's mod rides: vanilla's horse, donkey, mule and undead horses, and its breeds. */
	private static final TagKey<net.minecraft.world.entity.EntityType<?>> ICY_HORSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "horses"));

	private IcyCompat() {
	}

	/** Whether Icy's riding is the one horses use, on this side: the server's setting, or what it told this client. */
	public static boolean icyRides(final Level level) {
		return LOADED && (level.isClientSide() ? ServerRules.icyRides : HorseConfig.get().ridingStyle == HorseConfig.RidingStyle.ICYS);
	}

	/** On this client: Icy's mod is installed, but the server rides horses with this mod. */
	public static boolean oursOnClient() {
		return LOADED && ServerRules.present && !ServerRules.icyRides;
	}

	/** A horse Icy's mod has its own hooks on. */
	public static boolean icyHorse(final Entity horse) {
		//? if >=26.1 {
		return LOADED && horse.is(ICY_HORSES);
		//?} else
		/*return LOADED && horse.getType().is(ICY_HORSES);*/
	}
}
