package dev.horsingaround.gametest.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.sdl.SDLStdinc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Test-only: on Linux, a test game started from a Wayland session's shell uses X11 (Xwayland), as from any other shell.
 * Otherwise it tries Wayland, which fails with some drivers (NVIDIA on Ubuntu): it falls back to another graphics backend, or
 * crashes, depending on its mods. {@code gradle/x11-games.gradle} does this for most games, but Loom's production games
 * can't be given an environment, so they change their own; LWJGL can't see that, so they also get a setting for it.
 */
@Mixin(RenderSystem.class)
abstract class RenderSystemMixin {
	@Inject(method = "initBackendSystem", at = @At("HEAD"))
	private static void horsingaroundtest$useX11(final CallbackInfoReturnable<?> cir) {
		if (System.getProperty("os.name").startsWith("Linux") && System.getenv("DISPLAY") != null) {
			SDLStdinc.SDL_unsetenv_unsafe("WAYLAND_DISPLAY");
			SDLStdinc.SDL_setenv_unsafe("XDG_SESSION_TYPE", "x11", 1);
		}
	}
}
