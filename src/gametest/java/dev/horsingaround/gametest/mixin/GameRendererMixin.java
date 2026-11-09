package dev.horsingaround.gametest.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
//? if >=26.3 {
import dev.horsingaround.gametest.FilmCamera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

/**
 * Test-only: while a store clip records, the window's own frames don't draw the world, only the clip's frames do.
 * Animation that carries over from frame to frame (Fresh Animations' smoothed variables, this mod's leg easing) then
 * moves on with the clip's frames alone; the window's frame between ticks, drawn at whatever moment the real clock
 * gave it, used to push it on by a jump every tick. 26.3 only, as the store clips are.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	//? if >=26.3 {
	@Inject(method = "update", at = @At("HEAD"), cancellable = true)
	private void horsingaroundtest$clipOnlyUpdate(final DeltaTracker deltaTracker, final CallbackInfo ci) {
		if (FilmCamera.windowFramesHeld()) {
			ci.cancel();
		}
	}

	@Inject(method = "extract", at = @At("HEAD"), cancellable = true)
	private void horsingaroundtest$clipOnlyExtract(final DeltaTracker deltaTracker, final boolean advanceGameTime, final CallbackInfo ci) {
		if (FilmCamera.windowFramesHeld()) {
			ci.cancel();
		}
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void horsingaroundtest$clipOnlyRender(final CallbackInfo ci) {
		if (FilmCamera.windowFramesHeld()) {
			ci.cancel();
		}
	}
	//?}
}
