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
	private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
	/** (Where Icy's wagon model has its front axle.) */
	private static final double WAGON_FRONT_AXLE = 1.8125;

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
		if (Api.BOND == null || Api.BOND_TIER == null) {
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
		if (!LOADED || Api.BREED_KEY == null || Api.CHARGE_ON == null || Api.BREED_DATA == null || Api.ARCHETYPE == null
			|| Api.BASH_DAMAGE == null || Api.BASH_KNOCKBACK == null) {
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

	/**
	 * The heading of the cart the horse pulls, as drawn (degrees; NaN if it pulls none): on the rider's client, the one
	 * the ride steers by (CartTrail turns it).
	 */
	public static float cartYaw(final Entity horse) {
		if (!LOADED || Gear.CART_ENTITY == null || Gear.CART_YAW == null || !pullsCart(horse)) {
			return Float.NaN;
		}
		try {
			final Object cart = (Object) Gear.CART_ENTITY.invokeExact(horse);
			return cart == null ? Float.NaN : (float) Gear.CART_YAW.invokeExact(cart);
		} catch (final Throwable e) {
			return Float.NaN;
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

	/** How far behind the horse's middle a wagon has its front axle, blocks: 0 for a two-wheeled cart. */
	public static double cartFrontAxle(final Entity cart) {
		if (Gear.LARGE != null) {
			try {
				return (boolean) Gear.LARGE.invokeExact(cart) ? WAGON_FRONT_AXLE : 0.0;
			} catch (final Throwable e) {
				// (Two wheels, below.)
			}
		}
		return 0.0;
	}

	/** The cart a rider sits in, or that a horse it rides pulls, if any: {@code vehicle} is the cart or the horse. */
	public static CartTrail.@Nullable Lean cartLean(final Entity vehicle) {
		if (!LOADED) {
			return null;
		}
		if (vehicle instanceof CartTrail.Lean cart) {
			return cart;
		}
		// (Any rider of anything asks: only a horse has Icy's gear to look up.)
		if (!(vehicle instanceof AbstractHorse) || Gear.CART_ENTITY == null || !pullsCart(vehicle)) {
			return null;
		}
		try {
			return (Object) Gear.CART_ENTITY.invokeExact(vehicle) instanceof CartTrail.Lean cart ? cart : null;
		} catch (final Throwable e) {
			return null;
		}
	}

	/** Whether the cart the horse pulls has something too tall to roll over right behind it. */
	public static boolean cartBlockedBehind(final Entity horse) {
		final CartTrail.Lean cart = cartLean(horse);
		return cart != null && cart.horsingaround$blockedBehind();
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
			// (The switch on if it can't be read.)
			return (Gear.HOOVES_ON == null || (boolean) Gear.HOOVES_ON.invokeExact()) && (boolean) Gear.SHOD.invokeExact(horse);
		} catch (final Throwable e) {
			return false;
		}
	}

	/**
	 * Icy's gear, looked up the first time it's needed: (Entity) boolean, a cart in the harness slot; a working harness;
	 * shoes in the hoof slot; () boolean, the shoes' switch; (Entity) double, a cart's axle behind the horse; and
	 * (Entity) Object, the horse's cart, and (Object) float, its heading as drawn.
	 */
	private static final class Gear {
		private static final @Nullable Class<?> HORSE_DATA = find(() -> Class.forName("icy.betterhorses.net.IHorseData"));
		private static final @Nullable Class<?> CART_CLASS = find(() -> Class.forName("icy.betterhorses.net.entity.HorseCartEntity"));
		static final @Nullable MethodHandle CART = find(() -> LOOKUP.findVirtual(HORSE_DATA, "bh_hasCartGear", MethodType.methodType(boolean.class))
			.asType(MethodType.methodType(boolean.class, Entity.class)));
		static final @Nullable MethodHandle GLIDES = find(() -> LOOKUP.findStatic(Class.forName("icy.betterhorses.net.feature.Stabilizer"), "hasStabilizerGear", MethodType.methodType(boolean.class, HORSE_DATA))
			.asType(MethodType.methodType(boolean.class, Entity.class)));
		static final @Nullable MethodHandle SHOD = find(() -> {
			final Class<?> slot = Class.forName("icy.betterhorses.net.inventory.GearSlot");
			return MethodHandles.insertArguments(LOOKUP.findVirtual(HORSE_DATA, "bh_hasGear", MethodType.methodType(boolean.class, slot)), 1, slot.getField("HOOVES").get(null))
				.asType(MethodType.methodType(boolean.class, Entity.class));
		});
		static final @Nullable MethodHandle HOOVES_ON = find(() -> LOOKUP.findStatic(Class.forName("icy.betterhorses.net.BhConfig"), "hoovesEnabled", MethodType.methodType(boolean.class)));
		static final @Nullable MethodHandle AXLE = find(() -> {
			final Class<?> type = Class.forName("icy.betterhorses.net.entity.CartType");
			return MethodHandles.filterReturnValue(
				LOOKUP.findVirtual(CART_CLASS, "type", MethodType.methodType(type)),
				LOOKUP.findVirtual(type, "axleBehind", MethodType.methodType(double.class))
			).asType(MethodType.methodType(double.class, Entity.class));
		});
		static final @Nullable MethodHandle LARGE = find(() -> {
			final Class<?> type = Class.forName("icy.betterhorses.net.entity.CartType");
			return MethodHandles.filterReturnValue(
				LOOKUP.findVirtual(CART_CLASS, "type", MethodType.methodType(type)),
				LOOKUP.findVirtual(type, "isLarge", MethodType.methodType(boolean.class))
			).asType(MethodType.methodType(boolean.class, Entity.class));
		});
		static final @Nullable MethodHandle CART_ENTITY = find(() -> LOOKUP.findVirtual(HORSE_DATA, "bh_getCartEntity", MethodType.methodType(CART_CLASS))
			.asType(MethodType.methodType(Object.class, Entity.class)));
		static final @Nullable MethodHandle CART_YAW = find(() -> MethodHandles.insertArguments(LOOKUP.findVirtual(CART_CLASS, "gluedRenderYaw", MethodType.methodType(float.class, float.class)), 1, 1.0F)
			.asType(MethodType.methodType(float.class, Object.class)));
	}

	/** Icy's API, looked up the first time it's needed (only ever with Icy's mod installed). */
	private static final class Api {
		private static final @Nullable Class<?> HORSE_DATA = find(() -> Class.forName("icy.betterhorses.net.IHorseData"));
		/** (Entity) int: the horse's bond, and (int) int: its tier. */
		static final @Nullable MethodHandle BOND = find(() -> LOOKUP.findVirtual(HORSE_DATA, "bh_getBond", MethodType.methodType(int.class))
			.asType(MethodType.methodType(int.class, Entity.class)));
		static final @Nullable MethodHandle BOND_TIER = find(() -> LOOKUP.findStatic(Class.forName("icy.betterhorses.net.BhHorseTraits"), "bondTier", MethodType.methodType(int.class, int.class)));
		/** () boolean, by BreedTraits' trait: the switches for the Quarter Horse's burst, the Thoroughbred's top end, the Arabian's endurance. */
		static final @Nullable MethodHandle[] ABILITY_ON = {null, ability("QUARTER_BURST"), ability("THOROUGHBRED_TOP_END"), ability("ARABIAN_ENDURANCE")};
		/** (Entity) Object: the breed key; Object: its data, that's archetype, and its bash damage and knockback; () boolean: the charge's switch. */
		static final @Nullable MethodHandle BREED_KEY = find(() -> LOOKUP.findVirtual(HORSE_DATA, "bh_getBreedKey", MethodType.methodType(net.minecraft.resources.ResourceKey.class))
			.asType(MethodType.methodType(Object.class, Entity.class)));
		private static final @Nullable Class<?> BREED_DATA_CLASS = find(() -> Class.forName("icy.betterhorses.net.BhBreedData"));
		private static final @Nullable Class<?> ARCHETYPE_CLASS = find(() -> Class.forName("icy.betterhorses.net.registry.ArchetypeType"));
		static final @Nullable MethodHandle BREED_DATA = find(() -> LOOKUP.findStatic(BREED_DATA_CLASS, "of", MethodType.methodType(BREED_DATA_CLASS, net.minecraft.resources.ResourceKey.class)));
		static final @Nullable MethodHandle ARCHETYPE = find(() -> LOOKUP.findVirtual(BREED_DATA_CLASS, "archetype", MethodType.methodType(ARCHETYPE_CLASS)));
		static final @Nullable MethodHandle BASH_DAMAGE = find(() -> LOOKUP.findVirtual(ARCHETYPE_CLASS, "bashDamage", MethodType.methodType(double.class)));
		static final @Nullable MethodHandle BASH_KNOCKBACK = find(() -> LOOKUP.findVirtual(ARCHETYPE_CLASS, "bashKnockback", MethodType.methodType(double.class)));
		static final @Nullable MethodHandle CHARGE_ON = find(() -> {
			final Class<?> feature = Class.forName("icy.betterhorses.net.BhFeature");
			return LOOKUP.findVirtual(feature, "on", MethodType.methodType(boolean.class)).bindTo(feature.getField("HORSE_CHARGE").get(null));
		});
		/** The riders who switched the charge off. */
		static final @Nullable Set<?> CHARGE_OFF = find(() -> {
			final Field off = Class.forName("icy.betterhorses.net.feature.HorseCombat").getDeclaredField("chargeOff");
			off.setAccessible(true);
			return (Set<?>) off.get(null);
		});

		private static @Nullable MethodHandle ability(final String name) {
			return find(() -> {
				final Class<?> ability = Class.forName("icy.betterhorses.net.BhAbility");
				return LOOKUP.findVirtual(ability, "on", MethodType.methodType(boolean.class)).bindTo(ability.getField(name).get(null));
			});
		}
	}

	/** A lookup into Icy's mod, which may throw if that mod changed. */
	private interface Find<T> {
		T find() throws ReflectiveOperationException;
	}

	/**
	 * What {@code lookup} finds, or null if Icy's mod doesn't have it (any more), or has it but its class won't load. Each is
	 * looked up on its own, so a change in that mod turns off only the part of the compat that uses what changed.
	 */
	private static <T> @Nullable T find(final Find<T> lookup) {
		try {
			return lookup.find();
		} catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}
}
