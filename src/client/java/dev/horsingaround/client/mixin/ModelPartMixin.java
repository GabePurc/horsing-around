package dev.horsingaround.client.mixin;

import dev.horsingaround.client.render.Legs;
import dev.horsingaround.client.render.PartData;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Lets the animation-pack hook find custom parts (like Fresh Animations' stirrups) by name, and a leg's box be measured
 * (see {@code Legs}); and keeps what was measured or found on the part itself, so drawing a horse finds it with a field
 * read rather than a map lookup per part every frame.
 */
@Mixin(ModelPart.class)
abstract class ModelPartMixin implements PartData {
	@Shadow
	@Final
	private Map<String, ModelPart> children;
	@Shadow
	@Final
	private List<ModelPart.Cube> cubes;

	@Unique
	private Legs.@Nullable Leg horsingaround$leg;
	@Unique
	private @Nullable Object horsingaround$rootParts;

	@Override
	public Map<String, ModelPart> horsingaround$children() {
		return this.children;
	}

	@Override
	public List<ModelPart.Cube> horsingaround$cubes() {
		return this.cubes;
	}

	@Override
	public Legs.@Nullable Leg horsingaround$leg() {
		return this.horsingaround$leg;
	}

	@Override
	public void horsingaround$setLeg(final Legs.Leg leg) {
		this.horsingaround$leg = leg;
	}

	@Override
	public @Nullable Object horsingaround$rootParts() {
		return this.horsingaround$rootParts;
	}

	@Override
	public void horsingaround$setRootParts(final Object parts) {
		this.horsingaround$rootParts = parts;
	}
}
