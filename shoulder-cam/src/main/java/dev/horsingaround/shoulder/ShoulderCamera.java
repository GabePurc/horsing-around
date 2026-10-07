package dev.horsingaround.shoulder;

import static dev.horsingaround.shoulder.ShoulderTuning.*;

import dev.horsingaround.shoulder.config.ShoulderConfig;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Over-the-shoulder framing with smooth transitions between on foot, aiming and riding. The camera always looks
 * exactly where the player looks and orbits the player's head; only the framing values ease. Render thread only.
 */
public final class ShoulderCamera {

	private static boolean initialized;
	private static long lastNanos;
	private static float dt;
	private static float distance;
	private static float side;
	private static float up;
	private static float reach = 1.0F;
	private static double pivotX;
	private static double pivotY;
	private static double pivotZ;

	private ShoulderCamera() {
	}

	public static boolean isActive(final Minecraft minecraft, final Entity cameraEntity) {
		return ShoulderConfig.get().enabled && cameraEntity instanceof LivingEntity && minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
	}

	/** Advances the framing for this frame. {@code eyeY} is the vanilla (crouch-smoothed) eye height. */
	public static void update(final Entity entity, final float partialTicks, final double eyeY) {
		final long now = System.nanoTime();
		dt = initialized ? Math.min((now - lastNanos) * 1.0E-9F, 0.1F) : 0.0F;
		lastNanos = now;

		final ShoulderConfig c = ShoulderConfig.get();
		final boolean riding = HorsingAroundCompat.isRiding();
		final boolean aiming = isAiming(entity);
		final float targetDistance = aiming ? (riding ? c.aimDistance * 1.5F : c.aimDistance) : riding ? HorsingAroundCompat.distance(partialTicks) * c.rideDistanceScale : c.footDistance;
		final float targetSide = (c.leftShoulder ? -1.0F : 1.0F) * (aiming ? c.aimSide : riding ? c.rideSide : c.footSide);
		final float targetUp = riding ? c.rideHeight : c.footHeight;
		final double y = riding ? HorsingAroundCompat.eyeY(partialTicks) + HorsingAroundCompat.bounce() * c.rideBounce : eyeY;

		// The orbit centre is exactly the player's head horizontally; trailing it makes the orbit wander when moving
		// and turning together.
		pivotX = Mth.lerp(partialTicks, entity.xo, entity.getX());
		pivotZ = Mth.lerp(partialTicks, entity.zo, entity.getZ());
		final double dy = y - pivotY;
		if (!initialized || Math.abs(dy) > 3.0) {
			initialized = true;
			distance = targetDistance;
			side = targetSide;
			up = targetUp;
			reach = 1.0F;
			pivotY = y;
			return;
		}
		pivotY = riding ? y : pivotY + dy * blend(c.heightFollow);
		final float transition = blend(c.transitionSpeed);
		distance += (targetDistance - distance) * transition;
		side += (targetSide - side) * transition;
		up += (targetUp - up) * transition;
	}

	/** Snap in to the wall clearance immediately, ease back out. */
	public static float reach(final float clearance) {
		reach = clearance < reach ? clearance : reach + (clearance - reach) * blend(REACH_RECOVER_RATE);
		return reach;
	}

	public static double pivotX() {
		return pivotX;
	}

	public static double pivotY() {
		return pivotY;
	}

	public static double pivotZ() {
		return pivotZ;
	}

	public static float distance() {
		return distance;
	}

	public static float side() {
		return side;
	}

	public static float up() {
		return up;
	}

	static float blend(final float ratePerSecond) {
		return 1.0F - (float) Math.exp(-ratePerSecond * dt);
	}

	private static boolean isAiming(final Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return false;
		}
		if (living.isUsingItem()) {
			final ItemUseAnimation animation = living.getUseItem().getUseAnimation();
			return animation == ItemUseAnimation.BOW
				|| animation == ItemUseAnimation.CROSSBOW
				|| animation == ItemUseAnimation.TRIDENT
				|| animation == ItemUseAnimation.SPEAR
				|| animation == ItemUseAnimation.SPYGLASS;
		}
		final ItemStack main = living.getMainHandItem();
		return main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main);
	}
}
