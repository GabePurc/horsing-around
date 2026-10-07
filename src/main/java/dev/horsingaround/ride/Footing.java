package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.*;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a ridden horse's hooves and shoulders do when its box meets something, so a brush with the world doesn't stop
 * it dead: in the air it gets a hoof on anything within a step (see the horse mixin's move), a shoulder caught on a
 * corner slips past it, and only a real head-on hit counts as a crash. Runs on the instance simulating the ride.
 */
public final class Footing {
	private Footing() {
	}

	/**
	 * After a ridden horse's move by {@code delta} from ({@code x}, {@code z}): records how much of the travel a
	 * collision took, and if the horse just ran nearly straight into the corner of something that only caught the edge
	 * of its body, slips it sideways past the corner and keeps its pace.
	 */
	public static void afterMove(final AbstractHorse horse, final RideState s, final Vec3 delta, final double x, final double z) {
		final double wanted = delta.x * delta.x + delta.z * delta.z;
		if (!horse.horizontalCollision || wanted < 1.0E-6) {
			s.blocked = 0.0F;
			return;
		}
		final float blocked = Mth.clamp(1.0F - (float) (((horse.getX() - x) * delta.x + (horse.getZ() - z) * delta.z) / wanted), 0.0F, 1.0F);
		// Only at the moment of impact (not every tick pushed against a wall), and only when moving with purpose.
		if (blocked > CORNER_SLIP_BLOCKED && s.blocked <= CORNER_SLIP_BLOCKED && wanted > CORNER_SLIP_MIN_SPEED * CORNER_SLIP_MIN_SPEED
			&& slip(horse, delta, Math.sqrt(wanted))) {
			horse.setDeltaMovement(delta.x, horse.getDeltaMovement().y, delta.z);
			s.blocked = 0.0F;
			s.slips++;
			return;
		}
		s.blocked = blocked;
	}

	/** Shifts the horse sideways by the least that lets it carry on along {@code delta}, up to CORNER_SLIP. */
	private static boolean slip(final AbstractHorse horse, final Vec3 delta, final double length) {
		final Level level = horse.level();
		final AABB box = horse.getBoundingBox();
		final double sideX = -delta.z / length;
		final double sideZ = delta.x / length;
		for (double shift = CORNER_SLIP_STEP; shift <= CORNER_SLIP + 1.0E-6; shift += CORNER_SLIP_STEP) {
			for (int sign = -1; sign <= 1; sign += 2) {
				final double sx = sideX * shift * sign;
				final double sz = sideZ * shift * sign;
				final AABB moved = box.move(sx, 0.0, sz);
				if (level.noCollision(horse, moved.expandTowards(delta.x, 0.0, delta.z))) {
					horse.setPos(horse.getX() + sx, horse.getY(), horse.getZ() + sz);
					return true;
				}
			}
		}
		return false;
	}
}
