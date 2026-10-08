package dev.horsingaround.client.compat;

import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing;
import com.github.exopandora.shouldersurfing.api.client.Perspective;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * Other camera mods. While one of them is placing the third-person camera, the riding camera steps aside instead of
 * fighting it (the rest of the ride, the rider and the horse are unaffected).
 */
public final class CameraMods {
	private static final boolean SHOULDER_SURFING = FabricLoader.getInstance().isModLoaded("shouldersurfing");

	private CameraMods() {
	}

	/** Whether another mod is driving the third-person camera right now. */
	public static boolean otherCameraActive() {
		return SHOULDER_SURFING && ShoulderSurfing.active();
	}

	/**
	 * Switches the view between first and third person the way the installed camera mod expects (Shoulder Surfing
	 * keeps its own perspective, and only hands back first person through its API).
	 */
	public static void setCameraType(final Minecraft minecraft, final CameraType type) {
		if (SHOULDER_SURFING) {
			ShoulderSurfing.setCameraType(minecraft, type);
		} else {
			minecraft.options.setCameraType(type);
		}
	}

	/** Whether the view is the one the riding camera switched to (vanilla third person, or Shoulder Surfing's). */
	public static boolean isThirdPersonBack(final Minecraft minecraft) {
		return minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
	}

	/** Only loaded when Shoulder Surfing Reloaded is. */
	private static final class ShoulderSurfing {
		static boolean active() {
			return IShoulderSurfing.getInstance().isShoulderSurfing();
		}

		static void setCameraType(final Minecraft minecraft, final CameraType type) {
			if (type == CameraType.FIRST_PERSON) {
				IShoulderSurfing.getInstance().changePerspective(Perspective.FIRST_PERSON);
			} else {
				minecraft.options.setCameraType(type);
			}
		}
	}
}
