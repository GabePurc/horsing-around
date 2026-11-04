//? if >=1.20.2 && <1.21 {
/*package dev.horsingaround.client.mixin.emf;

import dev.horsingaround.client.compat.EmfNeckAfterPack;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

// Runs the ride's neck after the pack's animation (see EmfNeckAfterPack). Not required: a release of Entity Model Features
// this doesn't fit leaves the neck as the pack sets it, and the head doesn't clear the rider on climbs.
@Mixin(value = EMFModelPartRoot.class, remap = false)
abstract class EmfModelPartRootMixin {
	@ModifyVariable(method = "receiveAnimations", at = @At("STORE"))
	private Runnable horsingaround$neckAfterPack(final Runnable animation) {
		return new EmfNeckAfterPack(animation, (ModelPart) (Object) this);
	}
}
*///?}
