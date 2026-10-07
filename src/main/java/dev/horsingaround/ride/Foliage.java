package dev.horsingaround.ride;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** A player-ridden horse (and its rider) pushes through leaves instead of being stopped by them. */
public final class Foliage {
	private static final BlockPos.MutableBlockPos SCAN = new BlockPos.MutableBlockPos();

	private Foliage() {
	}

	/** Whether leaves should not block this entity: a horse ridden by a player, or that horse's rider. */
	public static boolean ridesThrough(final @Nullable Entity entity) {
		if (entity == null || !RideTuning.RIDE_THROUGH_LEAVES) {
			return false;
		}
		final Entity horse = entity instanceof RideStateHolder ? entity : entity.getVehicle();
		return horse instanceof RideStateHolder holder && holder.horsingaround$managed() && horse.getControllingPassenger() instanceof Player;
	}

	/** The first leaves block the horse's body overlaps, or null. Ride simulation thread only. */
	public static @Nullable BlockState leavesIn(final Entity horse) {
		final AABB box = horse.getBoundingBox();
		final int x1 = Mth.floor(box.maxX);
		final int y1 = Mth.floor(box.maxY);
		final int z1 = Mth.floor(box.maxZ);
		for (int x = Mth.floor(box.minX); x <= x1; x++) {
			for (int y = Mth.floor(box.minY); y <= y1; y++) {
				for (int z = Mth.floor(box.minZ); z <= z1; z++) {
					final BlockState state = horse.level().getBlockState(SCAN.set(x, y, z));
					if (state.is(BlockTags.LEAVES)) {
						return state;
					}
				}
			}
		}
		return null;
	}
}
