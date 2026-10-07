package dev.horsingaround.shoulder.mixin;

import static dev.horsingaround.shoulder.ShoulderTuning.WALL_MARGIN;

import dev.horsingaround.shoulder.HorsingAroundCompat;
import dev.horsingaround.shoulder.ShoulderAim;
import dev.horsingaround.shoulder.ShoulderCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Places the third-person (back) camera over the shoulder. It looks exactly where the player looks and orbits the
 * player's head: level shoulder offset, world-up height, then back along the view, pulled in by walls.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private @Nullable Entity entity;
	@Shadow
	private @Nullable Level level;
	@Shadow
	private boolean detached;
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	@Final
	private Vector3f forwards;
	@Shadow
	private Vec3 position;
	@Shadow
	private float eyeHeight;
	@Shadow
	private float eyeHeightOld;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Inject(method = "alignWithEntity", at = @At("HEAD"), cancellable = true)
	private void horsingaroundShoulder$place(final float partialTicks, final CallbackInfo ci) {
		final Entity e = this.entity;
		final Level level = this.level;
		if (level == null || !ShoulderCamera.isActive(this.minecraft, e)) {
			ShoulderAim.clear();
			return;
		}
		final double eyeY = Mth.lerp(partialTicks, e.yo, e.getY()) + Mth.lerp(partialTicks, this.eyeHeightOld, this.eyeHeight);
		ShoulderCamera.update(e, partialTicks, eyeY);
		final float yaw = e.getViewYRot(partialTicks);
		this.setRotation(yaw, e.getViewXRot(partialTicks));

		final float yawRad = yaw * Mth.DEG_TO_RAD;
		final float side = ShoulderCamera.side();
		final float distance = ShoulderCamera.distance();
		final double ox = -Mth.cos(yawRad) * side - this.forwards.x * distance;
		final double oy = ShoulderCamera.up() - this.forwards.y * distance;
		final double oz = -Mth.sin(yawRad) * side - this.forwards.z * distance;
		final double px = ShoulderCamera.pivotX();
		final double py = ShoulderCamera.pivotY();
		final double pz = ShoulderCamera.pivotZ();
		final float reach = ShoulderCamera.reach(horsingaroundShoulder$clearance(level, e, px, py, pz, ox, oy, oz));
		this.setPosition(px + ox * reach, py + oy * reach, pz + oz * reach);
		this.detached = true;
		if (e == this.minecraft.player) {
			ShoulderAim.update(level, e, this.position, this.forwards, partialTicks);
		} else {
			ShoulderAim.clear();
		}
		ci.cancel();
	}

	/** Fraction (0..1) of the offset the camera can travel before a wall, from a few jittered rays like vanilla. */
	@Unique
	private static float horsingaroundShoulder$clearance(
		final Level level, final Entity e, final double x, final double y, final double z, final double ox, final double oy, final double oz
	) {
		final double length = Math.sqrt(ox * ox + oy * oy + oz * oz);
		if (length < 1.0E-4) {
			return 1.0F;
		}
		// Riding with Horsing Around, leaves don't stop the horse, so they don't push the camera in either.
		final ClipContext.Block shape = HorsingAroundCompat.isRiding() ? ClipContext.Block.COLLIDER : ClipContext.Block.VISUAL;
		double fraction = 1.0;
		for (int i = 0; i < 8; i++) {
			final Vec3 from = new Vec3(x + ((i & 1) * 2 - 1) * 0.1, y + ((i >> 1 & 1) * 2 - 1) * 0.1, z + ((i >> 2 & 1) * 2 - 1) * 0.1);
			final HitResult hit = level.clip(new ClipContext(from, from.add(ox, oy, oz), shape, ClipContext.Fluid.NONE, e));
			if (hit.getType() != HitResult.Type.MISS) {
				fraction = Math.min(fraction, Math.max((hit.getLocation().distanceTo(from) - WALL_MARGIN) / length, 0.0));
			}
		}
		return (float) fraction;
	}
}
