package dev.horsingaround.shoulder;

import static dev.horsingaround.shoulder.ShoulderTuning.*;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Camera-based aiming, as in most over-the-shoulder games: the crosshair stays in the centre of the screen and the
 * player aims at whatever is under it. Each frame this finds that point and the rotation that points the player's
 * eyes at it. Targeting and the rotation sent to the server use it; the camera and the player's view do not change.
 * Render thread only.
 */
public final class ShoulderAim {
	private static boolean active;
	private static float yaw;
	private static float pitch;
	private static Vec3 target = Vec3.ZERO;

	private ShoulderAim() {
	}

	/** Called after the camera is placed: cast from the camera through the screen centre, starting level with the eyes. */
	public static void update(final Level level, final Entity viewer, final Vec3 camera, final Vector3fc forward, final float partialTicks) {
		final Vec3 eye = viewer.getEyePosition(partialTicks);
		final double fx = forward.x();
		final double fy = forward.y();
		final double fz = forward.z();
		final double skip = Math.max((eye.x - camera.x) * fx + (eye.y - camera.y) * fy + (eye.z - camera.z) * fz, 0.0);
		final Vec3 start = new Vec3(camera.x + fx * skip, camera.y + fy * skip, camera.z + fz * skip);
		Vec3 end = start.add(fx * AIM_RANGE, fy * AIM_RANGE, fz * AIM_RANGE);
		final HitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		final Vec3 entityEnd = start.distanceToSqr(end) > AIM_ENTITY_RANGE * AIM_ENTITY_RANGE
			? start.add(fx * AIM_ENTITY_RANGE, fy * AIM_ENTITY_RANGE, fz * AIM_ENTITY_RANGE)
			: end;
		final Entity vehicle = viewer.getVehicle();
		final EntityHitResult entity = ProjectileUtil.getEntityHitResult(
			viewer,
			start,
			entityEnd,
			new AABB(start, entityEnd).inflate(1.0),
			e -> EntitySelector.CAN_BE_PICKED.test(e) && e != vehicle && !e.isPassengerOfSameVehicle(viewer),
			start.distanceToSqr(entityEnd)
		);
		target = entity != null ? entity.getLocation() : end;

		final double dx = target.x - eye.x;
		final double dy = target.y - eye.y;
		final double dz = target.z - eye.z;
		final float viewYaw = viewer.getViewYRot(partialTicks);
		yaw = viewYaw + Mth.wrapDegrees((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) - viewYaw);
		pitch = Mth.clamp((float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG), -90.0F, 90.0F);
		active = true;
	}

	public static void clear() {
		active = false;
	}

	public static boolean isActive() {
		return active;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	public static Vec3 target() {
		return target;
	}
}
