package dev.horsingaround.gametest.mixin;

import dev.horsingaround.gametest.FilmCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only: while {@link FilmCamera} is filming, it has the last word on where the camera is. */
@Mixin(Camera.class)
abstract class FilmCameraMixin {
	@Unique
	private static final double[] POS = new double[3];
	@Unique
	private static final float[] ROT = new float[2];

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void horsingaroundtest$film(final DeltaTracker deltaTracker, final CallbackInfo ci) {
		if (FilmCamera.place(deltaTracker.getGameTimeDeltaPartialTick(true), POS, ROT)) {
			this.setRotation(ROT[0], ROT[1]);
			this.setPosition(POS[0], POS[1], POS[2]);
		}
	}
}
