package dev.horsingaround.client.forge;

import dev.horsingaround.client.compat.EpicFightRider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.client.forgeevent.RenderEpicFightPlayerEvent;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/** Epic Fight's question, whether it draws a player itself (see {@link EpicFightRider}). Only loaded with Epic Fight. */
final class EpicFightModel {
	private EpicFightModel() {
	}

	static void register() {
		MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderEpicFightPlayerEvent.class, EpicFightModel::answer);
	}

	private static void answer(final RenderEpicFightPlayerEvent event) {
		final PlayerPatch<?> patch = event.getPlayerPatch();
		if (event.getShouldRender() && EpicFightRider.posed(patch.getOriginal())) {
			// (An action, or its blend into or out of one: the game's rider comes back once Epic Fight's is sitting again.)
			final DynamicAnimation playing = patch.getClientAnimator().getPlayerFor(null).getAnimation().get();
			if (!playing.isMainFrameAnimation() && !playing.isLinkAnimation()) {
				event.setShouldRender(false);
			}
		}
	}
}
