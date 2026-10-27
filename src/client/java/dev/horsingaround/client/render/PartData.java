package dev.horsingaround.client.render;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.jspecify.annotations.Nullable;

/**
 * What the mod reads from a model part and keeps on it (every {@link ModelPart} is one, see ModelPartMixin). Kept on
 * the part, it lives exactly as long as the model does (a resource reload's new models start over). Render thread only.
 */
public interface PartData {
	Map<String, ModelPart> horsingaround$children();

	List<ModelPart.Cube> horsingaround$cubes();

	/** The part's leg as {@link Legs} measured it (or its marker for no leg); null until measured. */
	Legs.@Nullable Leg horsingaround$leg();

	void horsingaround$setLeg(Legs.Leg leg);

	/** A model root's parts as the animation-pack hook found them; null until looked up. */
	@Nullable Object horsingaround$rootParts();

	void horsingaround$setRootParts(Object parts);

	/** The parts a model's bones move from this one (see BodyBones), found once; null until looked up. */
	@Nullable Object horsingaround$bones();

	void horsingaround$setBones(Object bones);
}
