//? if >=1.20.2 && <1.21 {
/*package dev.horsingaround.client.compat;

import net.minecraft.client.model.geom.ModelPart;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;

// Entity Model Features 3.0 (all there is for 1.20.2 to 1.20.6) has no animation hook: this takes the place of one, wrapping
// the animation a model gets for each of its variants (EmfModelPartRootMixin). Every part of the model runs it as it is
// drawn, and only the first of them each time an entity is drawn does anything, so this goes by the same count.
public final class EmfNeckAfterPack implements Runnable {
	private final Runnable animation;
	private final ModelPart root;
	// (One for each variant, which has parts of its own, as the variant in force when this runs is this one's.)
	private final EmfNeck neck = new EmfNeck();
	private long drawn;

	public EmfNeckAfterPack(final Runnable animation, final ModelPart root) {
		this.animation = animation;
		this.root = root;
	}

	@Override
	public void run() {
		this.animation.run();
		final long count = EMFManager.getInstance().entityRenderCount;
		if (count != this.drawn) {
			this.drawn = count;
			this.neck.apply(this.root, EMFAnimationEntityContext.getEMFEntity());
		}
	}
}
*///?}
