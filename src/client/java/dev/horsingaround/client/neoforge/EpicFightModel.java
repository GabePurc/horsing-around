package dev.horsingaround.client.neoforge;

//? if <1.21.2 {
/*import dev.horsingaround.client.compat.EpicFightRider;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.client.event.EpicFightClientEventHooks;
import yesman.epicfight.api.client.event.types.render.ValidatePlayerModelEvent;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;
*///?}

/** Epic Fight's question, whether it draws a player itself (see {@code EpicFightRider}). Only loaded with Epic Fight. */
final class EpicFightModel {
	private EpicFightModel() {
	}

	//? if <1.21.2 {
	/*static void register() {
		EpicFightClientEventHooks.Render.VALIDATE_PLAYER_MODEL_TO_RENDER.registerEvent(EpicFightModel::answer);
	}

	private static void answer(final ValidatePlayerModelEvent event) {
		final AbstractClientPlayerPatch<?> patch = event.getPlayerPatch();
		if (event.getShouldRender() && EpicFightRider.posed(patch.getOriginal())) {
			// (An action, or its blend into or out of one: the game's rider comes back once Epic Fight's is sitting again.)
			final DynamicAnimation playing = patch.getClientAnimator().getPlayerFor(null).getAnimation().get();
			if (!playing.isMainFrameAnimation() && !playing.isLinkAnimation()) {
				event.setShouldRender(false);
			}
		}
	}
	*///?}
}
