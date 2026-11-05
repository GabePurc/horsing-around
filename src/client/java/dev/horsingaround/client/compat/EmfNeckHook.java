//? if =1.20.1 {
/*package dev.horsingaround.client.compat;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.client.render.PartData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

// 1.20.1's Entity Model Features has the animation hook of later versions: the ride's neck goes on there (see EmfNeck).
// 1.20.2 to 1.20.6's doesn't, see EmfNeckAfterPack.
public final class EmfNeckHook extends EMFAnimationApi.EMFAnimationHook {
	private static final Logger LOGGER = LoggerFactory.getLogger(HorsingAround.MOD_ID);
	// Set once a call into Entity Model Features fails to link.
	private boolean broken;

	public static void register() {
		try {
			EMFAnimationApi.registerAnimationHook(new EmfNeckHook());
		} catch (final RuntimeException e) {
			throw e;
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void onAnimationEnd(final AnimationContext context, final boolean unused) {
		if (this.broken) {
			return;
		}
		try {
			final EMFEntityRenderState state = context.activeState();
			if (state == null) {
				return;
			}
			final EMFModelPartRoot root = context.animatingModelRoot();
			final PartData data = (PartData) (Object) root;
			if (data.horsingaround$rootParts() instanceof EmfNeck neck) {
				neck.apply(root, state.emfEntity());
			} else {
				final EmfNeck neck = new EmfNeck();
				data.horsingaround$setRootParts(neck);
				neck.apply(root, state.emfEntity());
			}
		} catch (final LinkageError e) {
			this.broken = true;
			LOGGER.warn("Entity Model Features changed under the animation hook; the neck on an animation pack's horse stays as the pack sets it", e);
		}
	}
}
*///?}
