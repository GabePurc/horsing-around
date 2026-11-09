//? if >=1.20.2 && <1.21 {
/*package dev.horsingaround.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.models.IEMFModel;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;

// Entity Model Features 3.0 (all there is for 1.20.2 to 1.20.6) has no animation hook: this takes the place of one, wrapping
// the animation a model gets for each of its variants (EmfModelPartRootMixin), and runs the ride after it (EmfRide). Every
// part of the model runs it as it is drawn, and only the first of them each time an entity is drawn does anything, so this
// goes by the same count.
public final class EmfAfterPack implements Runnable {
	private final Runnable animation;
	private final ModelPart root;
	// (One for each variant, which has parts of its own, as the variant in force when this runs is this one's; made once the
	// pack has set them.)
	private @Nullable EmfRide ride;
	private boolean horseModel;
	private long drawn;

	public EmfAfterPack(final Runnable animation, final ModelPart root) {
		this.animation = animation;
		this.root = root;
	}

	@Override
	public void run() {
		this.animation.run();
		final long count = EMFManager.getInstance().entityRenderCount;
		if (count == this.drawn) {
			return;
		}
		this.drawn = count;
		final Object entity = EMFAnimationEntityContext.getEMFEntity();
		if (!EmfRide.managed(entity)) {
			return;
		}
		if (this.ride == null) {
			this.ride = new EmfRide(this.root);
			// (The horse's own model is its renderer's, as EMF 3.3 tells it; the rest are layers'.)
			this.horseModel = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer((Entity) entity) instanceof LivingEntityRenderer<?, ?> renderer
				&& renderer.getModel() instanceof IEMFModel model && model.emf$getEMFRootModel() == this.root;
		}
		this.ride.apply(this.root, entity, this.horseModel);
	}
}
*///?}
