package dev.horsingaround.client.mixin;

import dev.horsingaround.client.RideCamera;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
//? if >=26.1
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
//? if >=1.21.11 && <26.1
/*import net.minecraft.world.level.Level;*/
//? if <1.21.11
/*import net.minecraft.world.level.BlockGetter;*/
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
	//? if >=26.1 {
	@Shadow
	@Final
	private Minecraft minecraft;
	//?} else {
	/*// Set in the constructor hook below: Mixin drops an initialiser here (javac numbers the constructor's return after it,
	// so Mixin takes it for constructor body), which left this null and crashed the first ridden frame.
	@Unique
	private Minecraft minecraft;
	@Shadow
	private boolean initialized;
	*///?}
	//? if >=1.20.3 && <26.1 {
	/*@Shadow
	private float partialTickTime;
	*///?}
	//? if >=1.21.11 && <26.1 {
	/*@Shadow
	private Level level;
	*///?}
	//? if <1.21.11 {
	/*@Shadow
	private BlockGetter level;
	*///?}
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

	//? if >=1.21 {
	@Shadow
	protected abstract void move(float forwards, float up, float right);

	@Shadow
	private float getMaxZoom(final float cameraDist) {
		throw new AssertionError();
	}
	//?} else {
	/*@Shadow
	protected abstract void move(double forwards, double up, double right);

	@Shadow
	private double getMaxZoom(final double cameraDist) {
		throw new AssertionError();
	}
	*///?}

	/** While riding, the third-person camera passes through leaves like the horse does instead of snapping in. */
	@ModifyExpressionValue(method = "getMaxZoom", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/ClipContext$Block;VISUAL:Lnet/minecraft/world/level/ClipContext$Block;"))
	private ClipContext.Block horsingaround$seeThroughLeaves(final ClipContext.Block original) {
		return RideCamera.isRiding() ? ClipContext.Block.COLLIDER : original;
	}

	//? if <26.1 {
	/*@Inject(method = "<init>", at = @At("RETURN"))
	private void horsingaround$init(final CallbackInfo ci) {
		this.minecraft = Minecraft.getInstance();
	}
	*///?}

	//? if >=26.1 {
	@Inject(method = "update", at = @At("HEAD"))
	private void horsingaround$frame(final DeltaTracker deltaTracker, final CallbackInfo ci) {
		if (this.minecraft.player != null) {
			RideCamera.updateFrame(this.minecraft, deltaTracker.getGameTimeDeltaPartialTick(true));
		}
	}
	//?}

	/**
	 * Places the third-person camera while riding (instead of vanilla's placement, so the wall-collision raycast runs
	 * only once), and in first person carries the eyes with the saddle. Another camera mod that is driving the camera
	 * (see {@link RideCamera#isActive}) is left alone.
	 */
	//? if >=26.1 {
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
		this.horsingaround$firstPerson(partialTicks);
	}
	//?} elif >=1.21.11 {
	/*// Before 26.1 the camera is placed in one method, called once per frame with everything it needs.
	@WrapMethod(method = "setup")
	private void horsingaround$rideCamera(
		final Level level, final Entity e, final boolean detached, final boolean mirrored, final float partialTicks, final Operation<Void> original
	) {
		if (this.minecraft.player != null) {
			RideCamera.updateFrame(this.minecraft, partialTicks);
		}
		if (RideCamera.isActive(e, this.minecraft)) {
			this.level = level;
			this.horsingaround$place(e, partialTicks);
			return;
		}
		original.call(level, e, detached, mirrored, partialTicks);
		this.horsingaround$firstPerson(partialTicks);
	}
	*///?} else {
	/*@WrapMethod(method = "setup")
	private void horsingaround$rideCamera(
		final BlockGetter level, final Entity e, final boolean detached, final boolean mirrored, final float partialTicks, final Operation<Void> original
	) {
		if (this.minecraft.player != null) {
			RideCamera.updateFrame(this.minecraft, partialTicks);
		}
		if (RideCamera.isActive(e, this.minecraft)) {
			this.level = level;
			this.horsingaround$place(e, partialTicks);
			return;
		}
		original.call(level, e, detached, mirrored, partialTicks);
		this.horsingaround$firstPerson(partialTicks);
	}
	*///?}

	//? if <26.1 {
	/*// The third-person ride camera in place of vanilla's setup (the rest of what setup does is set here).
	@Unique
	private void horsingaround$place(final Entity e, final float partialTicks) {
		this.initialized = true;
		this.entity = e;
		this.detached = true;
		this.partialTickTime = partialTicks;
		this.setRotation(e.getViewYRot(partialTicks), e.getViewXRot(partialTicks));
		this.setPosition(Mth.lerp(partialTicks, e.xo, e.getX()), RideCamera.pivotY(partialTicks), Mth.lerp(partialTicks, e.zo, e.getZ()));
		this.move(-this.getMaxZoom(RideCamera.distance(partialTicks)), 0.0F, 0.0F);
	}
	*///?}

	/** In first person the eyes follow the saddle. */
	@Unique
	private void horsingaround$firstPerson(final float partialTicks) {
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
