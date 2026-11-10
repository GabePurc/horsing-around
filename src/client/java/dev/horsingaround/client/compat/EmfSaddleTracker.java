//? if >=1.21 || =1.20.1 {
package dev.horsingaround.client.compat;

import dev.horsingaround.HorsingAround;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/**
 * Entity Model Features' animation hook: runs the ride on a horse's models once the pack has animated them (EmfRide). EMF
 * 3.0, all there is for 1.20.2 to 1.20.6, has no hook: EmfAfterPack instead. Only loaded when EMF is present.
 */
public final class EmfSaddleTracker extends EMFAnimationApi.EMFAnimationHook {
	private static final Logger LOGGER = LoggerFactory.getLogger(HorsingAround.MOD_ID);
	/** Set once a call into Entity Model Features fails to link. */
	private boolean broken;

	public static void register() {
		try {
			EMFAnimationApi.registerAnimationHook(new EmfSaddleTracker());
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
			// Entity Model Features animates more than entities (with Fresh Animations, a shulker box drawn as an item icon)
			// and then hands every hook no state at all: nothing of ours to do there.
			final EMFEntityRenderState state = context.activeState();
			if (state == null || !EmfRide.managed(state.emfEntity())) {
				return;
			}
			final EMFModelPartRoot root = context.animatingModelRoot();
			EmfRide.of(root).apply(root, state.emfEntity(), root.isMainModel);
		} catch (final LinkageError e) {
			// (A release of Entity Model Features without something this was built against: stand aside for good, and the
			// horses and riders go on as without an animation pack.)
			this.broken = true;
			LOGGER.warn("Entity Model Features changed under the animation hook; riders follow a generated gait motion instead", e);
		}
	}
}
//?}
