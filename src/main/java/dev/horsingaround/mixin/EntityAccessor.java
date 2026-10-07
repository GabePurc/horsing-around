package dev.horsingaround.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
	/** Sets the ground flag alone, without vanilla's search for the supporting block (a move right after redoes both). */
	@Accessor("onGround")
	void horsingaround$setOnGroundFlag(boolean onGround);
}
