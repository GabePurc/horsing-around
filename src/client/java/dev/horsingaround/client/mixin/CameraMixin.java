package dev.horsingaround.client.mixin;

import dev.horsingaround.client.RideCamera;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private @Nullable Entity entity;
	@Shadow
	private boolean detached;
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	private Vec3 position;
	@Shadow
	private float xRot;
	@Shadow
	private float yRot;

	/** Render-thread scratch for the first-person nod. */
	@Unique
	private static final float[] NOD = new float[1];

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void move(float forwards, float up, float right);

	@Shadow
	private float getMaxZoom(final float cameraDist) {
		throw new AssertionError();
	}

	/** While riding, the third-person camera passes through leaves like the horse does instead of snapping in. */
	@ModifyExpressionValue(method = "getMaxZoom", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/ClipContext$Block;VISUAL:Lnet/minecraft/world/level/ClipContext$Block;"))
	private ClipContext.Block horsingaround$seeThroughLeaves(final ClipContext.Block original) {
		return RideCamera.isRiding() ? ClipContext.Block.COLLIDER : original;
	}

	@Inject(method = "update", at = @At("HEAD"))
	private void horsingaround$frame(final DeltaTracker deltaTracker, final CallbackInfo ci) {
		if (this.minecraft.player != null) {
			RideCamera.updateFrame(this.minecraft, deltaTracker.getGameTimeDeltaPartialTick(true));
		}
	}

	/**
	 * Places the third-person camera while riding (instead of vanilla's placement, so the wall-collision raycast runs
	 * only once), and in first person carries the eyes with the saddle. Another camera mod that is driving the camera
	 * (see {@link RideCamera#isActive}) is left alone.
	 */
	@WrapMethod(method = "alignWithEntity")
	private void horsingaround$rideCamera(final float partialTicks, final Operation<Void> original) {
		final Entity e = this.entity;
		if (RideCamera.isActive(e, this.minecraft)) {
			this.setRotation(e.getViewYRot(partialTicks), e.getViewXRot(partialTicks));
			this.setPosition(Mth.lerp(partialTicks, e.xo, e.getX()), RideCamera.pivotY(partialTicks), Mth.lerp(partialTicks, e.zo, e.getZ()));
			this.detached = true;
			this.move(-this.getMaxZoom(RideCamera.distance(partialTicks)), 0.0F, 0.0F);
			return;
		}
		original.call(partialTicks);
		if (this.detached || !RideCamera.isFirstPersonRide(this.entity, this.minecraft)) {
			return;
		}
		final double lift = RideCamera.firstPersonOffset(partialTicks, NOD);
		if (NOD[0] != 0.0F) {
			this.setRotation(this.yRot, this.xRot + NOD[0]);
		}
		if (lift != 0.0) {
			this.setPosition(this.position.x, this.position.y + lift, this.position.z);
		}
	}
}
