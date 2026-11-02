package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.CART_AXLE_EASE;
import static dev.horsingaround.ride.RideTuning.CART_AXLE_RISE;
import static dev.horsingaround.ride.RideTuning.CART_SWING_MAX;
import static dev.horsingaround.ride.RideTuning.CART_TILT_EASE;
import static dev.horsingaround.ride.RideTuning.CART_TILT_MAX;
import static dev.horsingaround.ride.RideTuning.CART_TILT_RATE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Icy's Better Horses' carts behind a horse this mod rides turn as a real cart does (user request, 2026-11-02). Icy's mod
 * swings a cart round the horse's middle towards the horse's heading, a little every tick, so it slid round behind the
 * horse even when the horse only turned on the spot. A cart's wheels roll but don't slide sideways: it turns only as it
 * is pulled along. So its axle follows the hitch (the horse's middle, where Icy's mod pivots the cart) at the cart's
 * length, which cuts inside the horse's line on a bend and leaves the cart where it is while the horse turns standing.
 * The shafts let it angle only CART_SWING_MAX off the horse; past that the horse drags it round. Going up and down
 * steps it rises with the horse as drawn (the ride's step easing) and its wheels roll over them, so it tilts smoothly
 * instead of a block at a time.
 */
public final class CartTrail {
	private CartTrail() {
	}

	/** (Half an axle's width: where Icy's mod has the wheels.) */
	private static final double WHEEL_SIDE = 1.16;

	/** Where one cart's axle is: on the ground plan, and its height as its wheels roll (NaN: on the ground under it). */
	public static final class Axle {
		double x;
		double z;
		double y = Double.NaN;
		boolean placed;
		/** (Its own: carts tick on the server's thread and the client's.) */
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		/** Next time, the axle starts straight behind the horse again, on the ground. */
		public void lift() {
			this.placed = false;
			this.y = Double.NaN;
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
		if (!axle.placed) {
			axle.y = Double.NaN;
		}
		axle.x = hitchX + Mth.sin(rad) * length;
		axle.z = hitchZ - Mth.cos(rad) * length;
		axle.placed = true;
		return yaw;
	}

	/**
	 * Rolls the axle (after {@link #follow}) up or down to the ground under its wheels and returns the cart's tilt eased
	 * from {@code tilt} (degrees, up at the front positive): from the hitch, at {@code hitchY}, down to the axle.
	 */
	public static float tilt(final Axle axle, final Level level, final double hitchY, final float yaw, final double length, final float tilt) {
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double sideX = Mth.cos(rad) * WHEEL_SIDE;
		final double sideZ = Mth.sin(rad) * WHEEL_SIDE;
		final double ground = Math.max(ground(level, axle.pos, axle.x + sideX, axle.z + sideZ, hitchY), ground(level, axle.pos, axle.x - sideX, axle.z - sideZ, hitchY));
		axle.y = Double.isNaN(axle.y) ? ground : axle.y + Mth.clamp((ground - axle.y) * CART_AXLE_EASE, -CART_AXLE_RISE, CART_AXLE_RISE);
		final float target = Mth.clamp((float) (Mth.atan2(hitchY - axle.y, length) * Mth.RAD_TO_DEG), -CART_TILT_MAX, CART_TILT_MAX);
		return tilt + Mth.clamp((target - tilt) * CART_TILT_EASE, -CART_TILT_RATE, CART_TILT_RATE);
	}

	/** The top of the ground under (x, z), from a block above {@code from} down to 3 below it (or that far down). */
	private static double ground(final Level level, final BlockPos.MutableBlockPos pos, final double x, final double z, final double from) {
		final int bx = Mth.floor(x);
		final int bz = Mth.floor(z);
		final int bottom = Mth.floor(from - 3.0);
		for (int by = Mth.floor(from + 1.0); by >= bottom; by--) {
			final BlockState state = level.getBlockState(pos.set(bx, by, bz));
			if (state.isAir()) {
				continue;
			}
			final VoxelShape shape = state.getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				return by + shape.max(Direction.Axis.Y);
			}
		}
		return from - 3.0;
	}
}
