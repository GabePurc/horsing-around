package dev.horsingaround.client.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the animation-pack hook find custom parts (like Fresh Animations' stirrups) by name, and the knees be cut into a
 * leg's box (see {@code Knees}).
 */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
	@Accessor("children")
	Map<String, ModelPart> horsingaround$children();

	@Mutable
	@Accessor("children")
	void horsingaround$setChildren(Map<String, ModelPart> children);

	@Accessor("cubes")
	List<ModelPart.Cube> horsingaround$cubes();

	@Mutable
	@Accessor("cubes")
	void horsingaround$setCubes(List<ModelPart.Cube> cubes);
}
