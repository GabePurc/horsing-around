package dev.horsingaround.client.mixin;

//? if >=1.21.9 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.horsingaround.client.render.BentBody;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
//?}
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
 * read rather than a map lookup per part every frame. From 1.21.9, a horse's body (and what sits on it) is drawn bent on
 * its back's bones (see {@code BentBody}).
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
	@Unique
	private @Nullable Object horsingaround$bones;

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

	@Override
	public @Nullable Object horsingaround$bones() {
		return this.horsingaround$bones;
	}

	@Override
	public void horsingaround$setBones(final Object bones) {
		this.horsingaround$bones = bones;
	}

	//? if >=1.21.9 {
	// (A redirect, not an inject: no callback object for every box of every model drawn.)
	@Redirect(
		method = "compile",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart$Cube;compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V")
	)
	private void horsingaround$bend(final ModelPart.Cube cube, final PoseStack.Pose pose, final VertexConsumer buffer, final int light, final int overlay, final int color) {
		if (!(this.horsingaround$bones instanceof BentBody.Bend bend) || !BentBody.draw(bend, cube, pose, buffer, light, overlay, color)) {
			cube.compile(pose, buffer, light, overlay, color);
		}
	}
	//?}
}
