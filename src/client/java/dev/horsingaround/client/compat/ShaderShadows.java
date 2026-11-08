package dev.horsingaround.client.compat;

import dev.horsingaround.Platform;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * A shader pack's shadows (Iris, or Oculus on Forge): each frame every entity is drawn once more from the sun, with the
 * sun's view in the pose stack. Where the legs meet the ground is worked out from where the model is drawn, so in that
 * pass they keep what the camera's pass worked out. (Up to 1.21.11 the shadows come first in a frame: the legs were worked
 * out there, against ground somewhere else entirely, and the camera's pass kept that; issue 121.) Asked through Iris's
 * public API, which both keep.
 */
public final class ShaderShadows {
	/** Iris's {@code isRenderingShadowPass}, bound to its API; null without Iris. */
	private static final @Nullable MethodHandle SHADOW_PASS = find();

	private ShaderShadows() {
	}

	/** Whether a shader pack is drawing its shadows now. */
	public static boolean drawing() {
		if (SHADOW_PASS == null) {
			return false;
		}
		try {
			return (boolean) SHADOW_PASS.invokeExact();
		} catch (final Throwable e) {
			return false;
		}
	}

	private static @Nullable MethodHandle find() {
		if (!Platform.isModLoaded("iris") && !Platform.isModLoaded("oculus")) {
			return null;
		}
		try {
			final Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			final Object instance = api.getMethod("getInstance").invoke(null);
			return MethodHandles.publicLookup().findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class)).bindTo(instance);
		} catch (final ReflectiveOperationException | LinkageError | RuntimeException e) {
			LoggerFactory.getLogger("horsingaround").warn("Iris's API isn't one this version knows; hooves may not meet the ground with shader shadows on", e);
			return null;
		}
	}
}
