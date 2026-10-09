package dev.horsingaround.client.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the animation-pack hook find custom parts (like Fresh Animations' stirrups) by name, and a leg's box be measured
 * (see {@code Legs}).
 */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
	@Accessor("children")
	Map<String, ModelPart> horsingaround$children();

	@Accessor("cubes")
	List<ModelPart.Cube> horsingaround$cubes();
}
