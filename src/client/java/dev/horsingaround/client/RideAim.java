package dev.horsingaround.client;

import static dev.horsingaround.ride.RideTuning.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Aiming over the shoulder, as in most third-person games: the crosshair stays in the centre of the screen and the
 * rider aims at whatever is under it. The camera sits off the rider's head, so the centre of the screen and the rider's
 * eyes look along parallel lines a little apart; this finds the point under the crosshair and the rotation that points
 * the eyes at it. Targeting (what to hit or mine) and the rotation the server hears (which aims arrows and anything
 * thrown) use it; the camera and the rider's own view don't change. Client thread only.
 */
public final class RideAim {
	private static boolean active;
	private static float yaw;
	private static float pitch;

	private RideAim() {
	}

	/**
	 * Before the game picks its target: finds the rotation toward what is under the crosshair, if the camera is over the
	 * shoulder. Returns whether it applies.
	 */
	public static boolean update(final Minecraft minecraft, final float partialTicks) {
		final LocalPlayer player = minecraft.player;
		active = player != null && minecraft.level != null && RideCamera.isOverShoulder(minecraft.getCameraEntity(), minecraft);
		if (!active) {
			return false;
		}
		final float viewYaw = player.getViewYRot(partialTicks);
		final float viewPitch = player.getViewXRot(partialTicks);
		final float yawRad = viewYaw * Mth.DEG_TO_RAD;
		final float pitchRad = viewPitch * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(yawRad) * Mth.cos(pitchRad);
		final double fy = -Mth.sin(pitchRad);
		final double fz = Mth.cos(yawRad) * Mth.cos(pitchRad);
		// The screen centre's line: through the camera, along the view. Start it level with the eyes, so nothing between the
		// camera and the rider (the horse's head) is picked.
		final float side = RideCamera.sideOffset();
		final double ox = Mth.lerp(partialTicks, player.xo, player.getX()) - Mth.cos(yawRad) * side;
		final double oy = RideCamera.pivotY(partialTicks) + RideCamera.raiseOffset();
		final double oz = Mth.lerp(partialTicks, player.zo, player.getZ()) - Mth.sin(yawRad) * side;
		final Vec3 eye = player.getEyePosition(partialTicks);
		final double skip = Math.max((eye.x - ox) * fx + (eye.y - oy) * fy + (eye.z - oz) * fz, 0.0);
		final Vec3 start = new Vec3(ox + fx * skip, oy + fy * skip, oz + fz * skip);
		Vec3 end = start.add(fx * AIM_RANGE, fy * AIM_RANGE, fz * AIM_RANGE);
		final HitResult block = minecraft.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		final Vec3 entityEnd = start.distanceToSqr(end) > AIM_ENTITY_RANGE * AIM_ENTITY_RANGE
			? start.add(fx * AIM_ENTITY_RANGE, fy * AIM_ENTITY_RANGE, fz * AIM_ENTITY_RANGE)
			: end;
		final Entity horse = player.getVehicle();
		final EntityHitResult entity = ProjectileUtil.getEntityHitResult(
			player,
			start,
			entityEnd,
			new AABB(start, entityEnd).inflate(1.0),
			e -> !e.isSpectator() && e.isPickable() && e != horse && !e.isPassengerOfSameVehicle(player),
			start.distanceToSqr(entityEnd)
		);
		final Vec3 target = entity != null ? entity.getLocation() : end;

		final double dx = target.x - eye.x;
		final double dy = target.y - eye.y;
		final double dz = target.z - eye.z;
		yaw = viewYaw + Mth.wrapDegrees((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) - viewYaw);
		pitch = Mth.clamp((float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG), -90.0F, 90.0F);
		return true;
	}

	/** Whether the rider's aim is the crosshair's (as of the last pick). */
	public static boolean isActive() {
		return active;
	}

	/** Rotation from the rider's eyes toward what is under the crosshair, degrees. */
	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}
}
