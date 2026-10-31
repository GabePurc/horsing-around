package dev.horsingaround.client.compat;

import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing;
//? if >=1.21.11 {
import com.github.exopandora.shouldersurfing.api.client.Perspective;
//?} elif >=1.21.2 {
/*import com.github.exopandora.shouldersurfing.api.model.Perspective;
*///?} elif >=1.21 {
/*import com.github.exopandora.shouldersurfing.api.client.Perspective;
*///?} elif >=1.20.2 {
/*import com.github.exopandora.shouldersurfing.api.model.Perspective;
*///?} else {
/*import com.github.exopandora.shouldersurfing.api.client.Perspective;
*///?}
import dev.horsingaround.Platform;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;

/**
 * Other camera mods. While one of them is placing the third-person camera, the riding camera steps aside instead of
 * fighting it (the rest of the ride, the rider and the horse are unaffected).
 */
public final class CameraMods {
	/** Shoulder Surfing Reloaded is installed, and its API is one this build knows (off for good once a call doesn't fit). */
	private static boolean shoulderSurfing = Platform.isModLoaded("shouldersurfing");

	private CameraMods() {
	}

	/** Whether another mod is driving the third-person camera right now. */
	public static boolean otherCameraActive() {
		if (shoulderSurfing) {
			try {
				return ShoulderSurfing.active();
			} catch (final LinkageError e) {
				unknownShoulderSurfing(e);
			}
		}
		return false;
	}

	/**
	 * Switches the view between first and third person the way the installed camera mod expects (Shoulder Surfing
	 * keeps its own perspective, and only hands back first person through its API).
	 */
	public static void setCameraType(final Minecraft minecraft, final CameraType type) {
		if (shoulderSurfing) {
			try {
				ShoulderSurfing.setCameraType(minecraft, type);
				return;
			} catch (final LinkageError e) {
				unknownShoulderSurfing(e);
			}
		}
		minecraft.options.setCameraType(type);
	}

	/**
	 * A Shoulder Surfing Reloaded whose API moved from the one this build was made against (its packages and entry point
	 * changed between its releases): the riding camera carries on without it.
	 */
	private static void unknownShoulderSurfing(final LinkageError e) {
		shoulderSurfing = false;
		LoggerFactory.getLogger("horsingaround").warn("Shoulder Surfing Reloaded's API isn't one this version knows; the riding camera won't step aside for it", e);
	}

	/** Whether the view is the one the riding camera switched to (vanilla third person, or Shoulder Surfing's). */
	public static boolean isThirdPersonBack(final Minecraft minecraft) {
		return minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
	}

	/** Only loaded when Shoulder Surfing Reloaded is. */
	private static final class ShoulderSurfing {
		private static IShoulderSurfing instance() {
			//? if >=1.21.11 {
			return IShoulderSurfing.getInstance();
			//?} else
			/*return com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing.getInstance();*/
		}

		static boolean active() {
			return instance().isShoulderSurfing();
		}

		static void setCameraType(final Minecraft minecraft, final CameraType type) {
			if (type == CameraType.FIRST_PERSON) {
				instance().changePerspective(Perspective.FIRST_PERSON);
			} else {
				minecraft.options.setCameraType(type);
			}
		}
	}
}
