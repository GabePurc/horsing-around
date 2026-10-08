package dev.horsingaround.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.horsingaround.ride.Foliage;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Leaves don't stop a player-ridden horse or its rider; everything else still collides as normal. */
@Mixin(BlockBehaviour.class)
abstract class BlockBehaviourMixin {
	@ModifyReturnValue(method = "getCollisionShape", at = @At("RETURN"))
	private VoxelShape horsingaround$leavesOpen(final VoxelShape shape, @Local(argsOnly = true) final CollisionContext context) {
		return (Object) this instanceof LeavesBlock && context instanceof EntityCollisionContext entityContext && Foliage.ridesThrough(entityContext.getEntity())
			? Shapes.empty()
			: shape;
	}
}
