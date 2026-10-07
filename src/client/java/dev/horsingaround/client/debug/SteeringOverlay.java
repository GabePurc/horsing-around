package dev.horsingaround.client.debug;

import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.ride.RideTuning;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;

/**
 * With entity hitboxes shown (F3+B), draws how the ridden horse is picking its way, for play-testing: its collision box
 * (green, at the tick's position), the rider's line
 * (white), where the horse is heading (yellow), its detour round something (aqua), what its look-ahead found along the
 * rider's line (red: a wall, orange: danger, blue: a jumpable gap), the ledge it is jumping (green), and the ground
 * carrying its front and back on a step (magenta). Debug only, so it may allocate.
 */
public final class SteeringOverlay {
	private static final String[] GAITS = {"stop", "walk", "trot", "canter", "gallop"};

	private SteeringOverlay() {
	}

	/** Called at the end of each client tick, while that tick's gizmos are being collected. */
	public static void tick(final Minecraft minecraft) {
		if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof AbstractHorse horse)
			|| !(horse instanceof RideStateHolder holder) || !holder.horsingaround$managed()
			|| !minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
			return;
		}
		final RideState s = holder.horsingaround$ride();
		Gizmos.cuboid(horse.getBoundingBox(), GizmoStyle.stroke(0xFF80FF80, 2.0F));
		final Vec3 chest = horse.position().add(0.0, 1.0, 0.0);
		arrow(chest, s.riderYaw, 3.0, 0xFFFFFFFF);
		arrow(chest, horse.getYRot(), 1.5 + Math.abs(s.speed) * 2.0, 0xFFFFE040);
		if (s.avoidOffset != 0.0F) {
			arrow(chest.add(0.0, 0.15, 0.0), s.riderYaw + s.avoidOffset, 3.0, 0xFF40E0FF);
		}
		mark(chest, s.riderYaw, s.debugWall(), 0xFFFF4040);
		mark(chest, s.riderYaw, s.debugDanger(), 0xFFFFA020);
		mark(chest, s.riderYaw, s.debugGap(), 0xFF4080FF);
		if (s.ledgeTicks > 0) {
			Gizmos.point(chest.add(direction(horse.getYRot(), 1.0)), 0xFF40FF60, 8.0F).setAlwaysOnTop();
		}
		final Vec3 forward = direction(horse.getYRot(), 1.0);
		final double fore = s.debugFore();
		final double hind = s.debugHind();
		if (!Double.isNaN(fore)) {
			Gizmos.point(new Vec3(horse.getX() + forward.x * RideTuning.FORE_HOOVES, fore, horse.getZ() + forward.z * RideTuning.FORE_HOOVES), 0xFFFF40FF, 6.0F)
				.setAlwaysOnTop();
			Gizmos.point(new Vec3(horse.getX() - forward.x * RideTuning.HIND_HOOVES, hind, horse.getZ() - forward.z * RideTuning.HIND_HOOVES), 0xFFFF40FF, 6.0F)
				.setAlwaysOnTop();
		}
		Gizmos.billboardTextOverMob(horse, 0, String.format(Locale.ROOT, "%s %.2f  blocked %.2f", GAITS[s.gait], s.speed, s.blocked), 0xFFFFFFFF, 0.6F);
	}

	private static Vec3 direction(final float yaw, final double length) {
		final float rad = yaw * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(rad) * length, 0.0, Mth.cos(rad) * length);
	}

	private static void arrow(final Vec3 from, final float yaw, final double length, final int argb) {
		Gizmos.arrow(from, from.add(direction(yaw, length)), argb).setAlwaysOnTop();
	}

	/** A post at {@code distance} along the line, if the look-ahead found something there. */
	private static void mark(final Vec3 from, final float yaw, final float distance, final int argb) {
		if (distance < 100.0F) {
			final Vec3 at = from.add(direction(yaw, distance));
			Gizmos.line(at.add(0.0, -1.0, 0.0), at.add(0.0, 1.0, 0.0), argb, 4.0F).setAlwaysOnTop();
		}
	}
}
