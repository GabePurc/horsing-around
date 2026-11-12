package dev.horsingaround.smoke;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;

/**
 * Epic Fight, for -PepicFight runs (the builds it ships for). By reflection: the test compiles on every version, with
 * or without it.
 */
final class EpicFight {
	private EpicFight() {
	}

	/** Epic Fight's side of the local player. */
	private static Object patch(final LocalPlayer player) throws ReflectiveOperationException {
		return Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities").getMethod("getLocalPlayerPatch", LocalPlayer.class).invoke(null, player);
	}

	/** Its other mode, where it leaves drawing the player to the game. */
	static void vanillaMode(final LocalPlayer player) {
		try {
			final Object patch = patch(player);
			patch.getClass().getMethod("toVanillaMode", boolean.class).invoke(patch, true);
		} catch (final ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The mode the player is in, and what Epic Fight has it doing. */
	static String state(final LocalPlayer player) {
		try {
			final Object patch = patch(player);
			final Object state = patch.getClass().getMethod("getEntityState").invoke(patch);
			return patch.getClass().getMethod("getPlayerMode").invoke(patch) + ", " + patch.getClass().getMethod("getCurrentLivingMotion").invoke(patch)
				+ (Boolean.TRUE.equals(state.getClass().getMethod("attacking").invoke(state)) ? ", attacking" : "");
		} catch (final ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Its attack key (the left mouse button, as vanilla's). */
	static KeyMapping attackKey() {
		try {
			return (KeyMapping) Class.forName("yesman.epicfight.client.input.EpicFightKeyMappings").getField("ATTACK").get(null);
		} catch (final ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
