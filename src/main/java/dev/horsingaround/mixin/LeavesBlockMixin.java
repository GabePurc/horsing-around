package dev.horsingaround.mixin;

import dev.horsingaround.ride.Foliage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

/** Leaves don't stop a player-ridden horse or its rider; everything else still collides as normal. */
@Mixin(LeavesBlock.class)
public abstract class LeavesBlockMixin extends Block {
	protected LeavesBlockMixin(final BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return context instanceof EntityCollisionContext entityContext && Foliage.ridesThrough(entityContext.getEntity())
			? Shapes.empty()
			: super.getCollisionShape(state, level, pos, context);
	}
}
