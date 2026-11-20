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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Supplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * Other camera mods. While one of them is placing the third-person camera, the riding camera steps aside instead of
 * fighting it (the rest of the ride, the rider and the horse are unaffected).
 */
public final class CameraMods {
	/** Shoulder Surfing Reloaded is installed, and its API is one this build knows (off for good once a call doesn't fit). */
	private static boolean shoulderSurfing = Platform.isModLoaded("shouldersurfing");
	/**
	 * Perspective API is installed (Leawind's Third Person runs on it), with an API this build knows. It owns the view: F5
	 * cycles its views, and it sets the game's camera type to match the one it shows.
	 */
	private static boolean perspectiveApi = Platform.isModLoaded("perspective_api") && PerspectiveApi.IS_CURRENT != null;
	private static final boolean LEAWIND = Platform.isModLoaded("leawind_third_person");
	/** F5 was down at the end of the last tick. */
	private static boolean viewKeyDown;

	private CameraMods() {
	}

	/** Client start-up. */
	public static void init() {
		if (perspectiveApi) {
			try {
				PerspectiveApi.register();
			} catch (final Throwable e) {
				unknownPerspectiveApi(e);
			}
		}
	}

	/** End of every client tick. */
	public static void tick(final Minecraft minecraft) {
		if (perspectiveApi) {
			// F5 let go while the view mounting turned to is held: the rider picked one with Perspective API's switcher
			// (a tap steps through its views, holding opens its wheel), which shows from now on.
			final boolean down = minecraft.options.keyTogglePerspective.isDown();
			if (viewKeyDown && !down) {
				PerspectiveApi.held = null;
			}
			viewKeyDown = down;
		}
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
		return leawindView();
	}

	/**
	 * Where the rider looks, which the horse heads for: their own facing, or Leawind's camera while its view is on (the
	 * mouse turns that camera, and Leawind's turns the rider toward where they ride).
	 */
	public static float lookYaw(final Minecraft minecraft, final Player rider) {
		if (rider != minecraft.getCameraEntity() || !leawindView()) {
			return rider.getYRot();
		}
		//? if >=1.21.11 {
		return minecraft.gameRenderer.mainCamera().yRot();
		//?} else
		/*return minecraft.gameRenderer.mainCamera().getYRot();*/
	}

	/**
	 * Switches the view between first and third person the way the installed camera mod expects. Shoulder Surfing keeps
	 * its own perspective, and only hands back first person through its API. Perspective API is asked to show a view until
	 * the rider picks another or this hands it back: Leawind's, if installed (as Shoulder Surfing's is what third person
	 * shows with it), else its plain third person.
	 */
	public static void setCameraType(final Minecraft minecraft, final CameraType type) {
		if (perspectiveApi) {
			PerspectiveApi.held = type == CameraType.FIRST_PERSON ? null : LEAWIND ? PerspectiveApi.LEAWIND : PerspectiveApi.THIRD_PERSON_BACK;
			return;
		}
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
	 * Whether the view is still the one the riding camera switched to (vanilla third person, or Shoulder Surfing's; with
	 * Perspective API, the view it was asked to show, until the rider picked another).
	 */
	public static boolean isThirdPersonBack(final Minecraft minecraft) {
		return perspectiveApi ? PerspectiveApi.held != null : minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
	}

	/** Leawind's Third Person shows its view, placing the camera itself. */
	private static boolean leawindView() {
		if (!LEAWIND || !perspectiveApi) {
			return false;
		}
		try {
			return PerspectiveApi.isCurrent(PerspectiveApi.LEAWIND);
		} catch (final Throwable e) {
			unknownPerspectiveApi(e);
			return false;
		}
	}

	/**
	 * A Shoulder Surfing Reloaded whose API moved from the one this build was made against (its packages and entry point
	 * changed between its releases): the riding camera carries on without it.
	 */
	private static void unknownShoulderSurfing(final LinkageError e) {
		shoulderSurfing = false;
		LoggerFactory.getLogger("horsingaround").warn("Shoulder Surfing Reloaded's API isn't one this version knows; the riding camera won't step aside for it", e);
	}

	/** Likewise a Perspective API whose API moved. */
	private static void unknownPerspectiveApi(final Throwable e) {
		perspectiveApi = false;
		PerspectiveApi.held = null;
		LoggerFactory.getLogger("horsingaround").warn("Perspective API's API isn't one this version knows; the riding camera won't step aside for Leawind's Third Person", e);
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

	/**
	 * Perspective API, found by name: its public API is the same on every Minecraft version and loader, unlike its jars.
	 * Its views are picked by an override chain over its own switcher (F5): this adds a link that shows {@link #held}.
	 */
	private static final class PerspectiveApi {
		static final String THIRD_PERSON_BACK = "perspective_api.third_person_back";
		static final String LEAWIND = "leawind_third_person.third_person";
		/** Above the player's switcher (the lowest), below any mod that forces a view of its own. */
		private static final int PRIORITY = 1000;
		private static final String API = "io.github.leawind.perspectiveapi.api.";
		/** {@code PerspectiveAPI.isCurrent(String)}, or null if it isn't there. */
		static final @Nullable MethodHandle IS_CURRENT = find();
		/** The view mounting turned to, shown until the rider picks another or gets off; null for none. */
		static volatile @Nullable String held;

		private static @Nullable MethodHandle find() {
			try {
				return MethodHandles.publicLookup().findStatic(Class.forName(API + "PerspectiveAPI"), "isCurrent", MethodType.methodType(boolean.class, String.class));
			} catch (final ReflectiveOperationException | LinkageError e) {
				return null;
			}
		}

		static boolean isCurrent(final String id) throws Throwable {
			return (boolean) IS_CURRENT.invokeExact(id);
		}

		static void register() throws ReflectiveOperationException {
			final Class<?> api = Class.forName(API + "PerspectiveAPI");
			final java.lang.reflect.Method chain = api.getMethod("getOverrideChain");
			final java.lang.reflect.Method add = Class.forName(API + "PerspectiveOverrideChain").getMethod("register", int.class, Supplier.class);
			final Supplier<@Nullable String> view = () -> held;
			// (Its runtime may not be up yet: it runs this once it is.)
			api.getMethod("runWhenReady", String.class, Runnable.class).invoke(null, "horsingaround:mount_view", (Runnable) () -> {
				try {
					add.invoke(chain.invoke(null), PRIORITY, view);
				} catch (final ReflectiveOperationException | RuntimeException e) {
					unknownPerspectiveApi(e);
				}
			});
		}
	}
}
