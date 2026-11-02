package dev.horsingaround.ride;

import dev.horsingaround.Platform;
import dev.horsingaround.net.ServerRules;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Icy's Better Horses (user request, 2026-10-31). With it installed too, the server picks whose riding horses use
 * ({@link HorseConfig#ridingStyle}: the host's setting in singleplayer, the server's own file on a server):
 * <ul>
 * <li>this mod's (the default): it rides Icy's breeds as well as vanilla's horses (the managed tag lists them), and Icy's
 * own riding that would change how they ride (gaits, double-tap auto-ride, swim boost, its speed abilities and charge) is
 * turned off by the mixins in mixin/icy and client/mixin/icy; its breeds' speed abilities come back on this mod's gaits
 * and stamina (BreedTraits). Everything else of Icy's stays: breeds, bond, tack, carts, combat, rearing, its models.</li>
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

	/** Icy's bond tier for the horse (0, 1, 2...; 0 if its mod changed and the bond can't be read). */
	public static int bondTier(final Entity horse) {
		if (Api.BOND == null) {
			return 0;
		}
		try {
			return (int) Api.BOND_TIER.invokeExact((int) Api.BOND.invokeExact(horse));
		} catch (final Throwable e) {
			return 0;
		}
	}

	/** Whether Icy's own switch for a breed ability (BhAbility, by name) is on: its config (on if it can't be read). */
	public static boolean abilityOn(final int trait) {
		final MethodHandle on = trait < Api.ABILITY_ON.length ? Api.ABILITY_ON[trait] : null;
		if (on == null) {
			return true;
		}
		try {
			return (boolean) on.invokeExact();
		} catch (final Throwable e) {
			return true;
		}
	}

	/**
	 * Whether this is one of Icy's breeds with its charge switched on, by the server and by the rider (Icy's Toggle Horse
	 * Charge key): the trample then also bashes hostile mobs, as the charge did.
	 */
	public static boolean bashes(final Entity horse, final Entity rider) {
		if (!LOADED || Api.BREED_KEY == null || Api.CHARGE_ON == null) {
			return false;
		}
		try {
			return (boolean) Api.CHARGE_ON.invokeExact() && (Object) Api.BREED_KEY.invokeExact(horse) != null
				&& (Api.CHARGE_OFF == null || !Api.CHARGE_OFF.contains(rider.getUUID()));
		} catch (final Throwable e) {
			return false;
		}
	}

	/**
	 * Icy's charge, for the trample (see {@link #bashes}): its damage at full pace (Icy's: by the breed's class and its
	 * armour) and its knockback (1 is the trample's own), into out. False if they can't be read.
	 */
	public static boolean bash(final AbstractHorse horse, final double[] out) {
		try {
			final Object archetype = Api.ARCHETYPE.invoke(Api.BREED_DATA.invoke((Object) Api.BREED_KEY.invokeExact((Entity) horse)));
			final String path = BuiltInRegistries.ENTITY_TYPE.getKey(horse.getType()).getPath();
			final double barding = Math.min("clydesdale_horse".equals(path) ? 5.0 : 3.0, horse.getAttributeValue(Attributes.ARMOR) * 0.25);
			out[0] = Math.min(30.0, 7.0 * (double) Api.BASH_DAMAGE.invoke(archetype) + barding);
			out[1] = (double) Api.BASH_KNOCKBACK.invoke(archetype);
			return true;
		} catch (final Throwable e) {
			return false;
		}
	}

	/** Whether the horse pulls one of Icy's carts: its riders sit on the cart's bench, which Icy's mod seats and rocks. */
	public static boolean pullsCart(final Entity horse) {
		if (!LOADED || Gear.CART == null) {
			return false;
		}
		try {
			return (boolean) Gear.CART.invokeExact(horse);
		} catch (final Throwable e) {
			return false;
		}
	}

	/** Whether the horse wears Icy's Glidewing Harness, switched on and charged: it glides down any drop and lands unhurt. */
	public static boolean glides(final Entity horse) {
		if (!LOADED || Gear.GLIDES == null) {
			return false;
		}
		try {
			return (boolean) Gear.GLIDES.invokeExact(horse);
		} catch (final Throwable e) {
			return false;
		}
	}

	/** How far behind the horse's middle one of Icy's carts has its axle, blocks (the small cart's if it can't be read). */
	public static double cartLength(final Entity cart) {
		if (Gear.AXLE != null) {
			try {
				return (double) Gear.AXLE.invokeExact(cart);
			} catch (final Throwable e) {
				// (The small cart's, below.)
			}
		}
		return 2.375;
	}

	/**
	 * Whether the horse wears Icy's horseshoes, switched on: they let it walk on powder snow. Read from the gear flags
	 * Icy's mod sends clients, so it holds on the rider's client too (the shoes themselves are only on the server).
	 */
	public static boolean shod(final Entity horse) {
		if (!LOADED || Gear.SHOD == null || !(horse instanceof AbstractHorse)) {
			return false;
		}
		try {
			return (boolean) Gear.HOOVES_ON.invokeExact() && (boolean) Gear.SHOD.invokeExact(horse);
		} catch (final Throwable e) {
			return false;
		}
	}

	/**
	 * Icy's gear, looked up the first time it's needed: (Entity) boolean, a cart in the harness slot; a working harness;
	 * shoes in the hoof slot; () boolean, the shoes' switch; and (Entity) double, a cart's axle behind the horse.
	 */
	private static final class Gear {
		static final @Nullable MethodHandle CART;
		static final @Nullable MethodHandle GLIDES;
		static final @Nullable MethodHandle SHOD;
		static final @Nullable MethodHandle HOOVES_ON;
		static final @Nullable MethodHandle AXLE;

		static {
			MethodHandle cart = null;
			MethodHandle glides = null;
			MethodHandle shod = null;
			MethodHandle hoovesOn = null;
			MethodHandle axle = null;
			try {
				final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				final Class<?> horseData = Class.forName("icy.betterhorses.net.IHorseData");
				cart = lookup.findVirtual(horseData, "bh_hasCartGear", MethodType.methodType(boolean.class))
					.asType(MethodType.methodType(boolean.class, Entity.class));
				glides = lookup.findStatic(Class.forName("icy.betterhorses.net.feature.Stabilizer"), "hasStabilizerGear", MethodType.methodType(boolean.class, horseData))
					.asType(MethodType.methodType(boolean.class, Entity.class));
				final Class<?> slot = Class.forName("icy.betterhorses.net.inventory.GearSlot");
				shod = MethodHandles.insertArguments(lookup.findVirtual(horseData, "bh_hasGear", MethodType.methodType(boolean.class, slot)), 1, slot.getField("HOOVES").get(null))
					.asType(MethodType.methodType(boolean.class, Entity.class));
				hoovesOn = lookup.findStatic(Class.forName("icy.betterhorses.net.BhConfig"), "hoovesEnabled", MethodType.methodType(boolean.class));
				final Class<?> type = Class.forName("icy.betterhorses.net.entity.CartType");
				axle = MethodHandles.filterReturnValue(
					lookup.findVirtual(Class.forName("icy.betterhorses.net.entity.HorseCartEntity"), "type", MethodType.methodType(type)),
					lookup.findVirtual(type, "axleBehind", MethodType.methodType(double.class))
				).asType(MethodType.methodType(double.class, Entity.class));
			} catch (final ReflectiveOperationException e) {
				cart = null;
				glides = null;
				shod = null;
				hoovesOn = null;
				axle = null;
			}
			CART = cart;
			GLIDES = glides;
			SHOD = shod;
			HOOVES_ON = hoovesOn;
			AXLE = axle;
		}
	}

	/** Icy's API, looked up the first time it's needed (only ever with Icy's mod installed). */
	private static final class Api {
		/** (Entity) int: the horse's bond, and (int) int: its tier. */
		static final @Nullable MethodHandle BOND;
		static final @Nullable MethodHandle BOND_TIER;
		/** () boolean, by BreedTraits' trait: the switches for the Quarter Horse's burst, the Thoroughbred's top end, the Arabian's endurance. */
		static final @Nullable MethodHandle[] ABILITY_ON = new MethodHandle[4];
		/** (Entity) Object: the breed key; Object: its data, that's archetype, and its bash damage and knockback; () boolean: the charge's switch. */
		static final @Nullable MethodHandle BREED_KEY;
		static final @Nullable MethodHandle BREED_DATA;
		static final @Nullable MethodHandle ARCHETYPE;
		static final @Nullable MethodHandle BASH_DAMAGE;
		static final @Nullable MethodHandle BASH_KNOCKBACK;
		static final @Nullable MethodHandle CHARGE_ON;
		/** The riders who switched the charge off. */
		static final @Nullable Set<?> CHARGE_OFF;

		static {
			MethodHandle bond = null;
			MethodHandle tier = null;
			MethodHandle breedKey = null;
			MethodHandle breedData = null;
			MethodHandle archetype = null;
			MethodHandle bashDamage = null;
			MethodHandle bashKnockback = null;
			MethodHandle chargeOn = null;
			try {
				final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				bond = lookup.findVirtual(Class.forName("icy.betterhorses.net.IHorseData"), "bh_getBond", MethodType.methodType(int.class))
					.asType(MethodType.methodType(int.class, Entity.class));
				tier = lookup.findStatic(Class.forName("icy.betterhorses.net.BhHorseTraits"), "bondTier", MethodType.methodType(int.class, int.class));
				final Class<?> ability = Class.forName("icy.betterhorses.net.BhAbility");
				final MethodHandle on = lookup.findVirtual(ability, "on", MethodType.methodType(boolean.class));
				final String[] names = {null, "QUARTER_BURST", "THOROUGHBRED_TOP_END", "ARABIAN_ENDURANCE"};
				for (int i = 1; i < names.length; i++) {
					ABILITY_ON[i] = on.bindTo(ability.getField(names[i]).get(null));
				}
				final Class<?> horseData = Class.forName("icy.betterhorses.net.IHorseData");
				breedKey = lookup.findVirtual(horseData, "bh_getBreedKey", MethodType.methodType(net.minecraft.resources.ResourceKey.class))
					.asType(MethodType.methodType(Object.class, Entity.class));
				final Class<?> data = Class.forName("icy.betterhorses.net.BhBreedData");
				final Class<?> type = Class.forName("icy.betterhorses.net.registry.ArchetypeType");
				breedData = lookup.findStatic(data, "of", MethodType.methodType(data, net.minecraft.resources.ResourceKey.class));
				archetype = lookup.findVirtual(data, "archetype", MethodType.methodType(type));
				bashDamage = lookup.findVirtual(type, "bashDamage", MethodType.methodType(double.class));
				bashKnockback = lookup.findVirtual(type, "bashKnockback", MethodType.methodType(double.class));
				final Class<?> feature = Class.forName("icy.betterhorses.net.BhFeature");
				chargeOn = lookup.findVirtual(feature, "on", MethodType.methodType(boolean.class)).bindTo(feature.getField("HORSE_CHARGE").get(null));
			} catch (final ReflectiveOperationException | ClassCastException e) {
				bond = null;
				tier = null;
				breedKey = null;
				chargeOn = null;
			}
			BOND = bond;
			BOND_TIER = tier;
			BREED_KEY = breedKey;
			BREED_DATA = breedData;
			ARCHETYPE = archetype;
			BASH_DAMAGE = bashDamage;
			BASH_KNOCKBACK = bashKnockback;
			CHARGE_ON = chargeOn;
			Set<?> chargeOff = null;
			try {
				final Field off = Class.forName("icy.betterhorses.net.feature.HorseCombat").getDeclaredField("chargeOff");
				off.setAccessible(true);
				chargeOff = (Set<?>) off.get(null);
			} catch (final ReflectiveOperationException | RuntimeException e) {
				chargeOff = null;
			}
			CHARGE_OFF = chargeOff;
		}
	}
}
