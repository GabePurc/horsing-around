package dev.horsingaround.gametest.mixin;

import dev.horsingaround.gametest.FilmCamera;
import net.minecraft.client.Camera;
//? if >=26.1
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only: while {@link FilmCamera} is filming, it has the last word on where the camera is and its lens. */
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

	//? if >=26.1 {
	@Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void horsingaroundtest$film(final DeltaTracker deltaTracker, final CallbackInfo ci) {
		this.horsingaroundtest$place(deltaTracker.getGameTimeDeltaPartialTick(true));
	}
	//?} elif >=1.21.11 {
	/*@Inject(method = "setup", at = @At("RETURN"))
	private void horsingaroundtest$film(final net.minecraft.world.level.Level level, final net.minecraft.world.entity.Entity entity, final boolean detached, final boolean mirrored, final float partialTicks, final CallbackInfo ci) {
		this.horsingaroundtest$place(partialTicks);
	}
	*///?} else {
	/*@Inject(method = "setup", at = @At("RETURN"))
	private void horsingaroundtest$film(final net.minecraft.world.level.BlockGetter level, final net.minecraft.world.entity.Entity entity, final boolean detached, final boolean mirrored, final float partialTicks, final CallbackInfo ci) {
		this.horsingaroundtest$place(partialTicks);
	}
	*///?}

	@Unique
	private void horsingaroundtest$place(final float partialTicks) {
		if (FilmCamera.place(partialTicks, POS, ROT)) {
			this.setRotation(ROT[0], ROT[1]);
			this.setPosition(POS[0], POS[1], POS[2]);
		}
	}

	// (The store clips' own lens: 26.1 on, where the camera works out its field of view. They're 26.3's alone.)
	//? if >=26.1 {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void horsingaroundtest$lens(final float partialTicks, final CallbackInfoReturnable<Float> cir) {
		final float fov = FilmCamera.fov();
		if (fov > 0.0F) {
			cir.setReturnValue(fov);
		}
	}
	//?}
}
