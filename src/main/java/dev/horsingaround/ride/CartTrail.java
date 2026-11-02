package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.CART_SWING_MAX;

import net.minecraft.util.Mth;

/**
 * Icy's Better Horses' carts behind a horse this mod rides turn as a real cart does (user request, 2026-11-02). Icy's mod
 * swings a cart round the horse's middle towards the horse's heading, a little every tick, so it slid round behind the
 * horse even when the horse only turned on the spot. A cart's wheels roll but don't slide sideways: it turns only as it
 * is pulled along. So its axle follows the hitch (the horse's middle, where Icy's mod pivots the cart) at the cart's
 * length, which cuts inside the horse's line on a bend and leaves the cart where it is while the horse turns standing.
 * The shafts let it angle only CART_SWING_MAX off the horse; past that the horse drags it round.
 */
public final class CartTrail {
	private CartTrail() {
	}

	/** Where one cart's axle is, on the ground plan. */
	public static final class Axle {
		double x;
		double z;
		boolean placed;

		/** Next time, the axle starts straight behind the horse again. */
		public void lift() {
			this.placed = false;
		}
	}

	/** Moves the axle after the hitch, now at (hitchX, hitchZ), and returns the cart's heading (degrees, like an entity's yaw). */
	public static float follow(final Axle axle, final double hitchX, final double hitchZ, final float horseYaw, final double length) {
		float yaw = horseYaw;
		final double dx = hitchX - axle.x;
		final double dz = hitchZ - axle.z;
		final double distanceSq = dx * dx + dz * dz;
		// (Just hitched, or the horse jumped far in a tick, a teleport: straight behind it.)
		if (axle.placed && distanceSq > 1.0E-8 && distanceSq < 4.0 * length * length) {
			yaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
			final float swing = Mth.wrapDegrees(yaw - horseYaw);
			if (Math.abs(swing) > CART_SWING_MAX) {
				yaw = horseYaw + Math.copySign(CART_SWING_MAX, swing);
			}
		}
		final float rad = yaw * Mth.DEG_TO_RAD;
		axle.x = hitchX + Mth.sin(rad) * length;
		axle.z = hitchZ - Mth.cos(rad) * length;
		axle.placed = true;
		return yaw;
	}
}
