package dev.horsingaround.gametest;

import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * The resource packs the tests ride with: the Fresh Animations stack (the target setup) and the test colours on top
 * (see installDebugPack in client-tests.gradle). Every test that rides calls {@link #enable} first, so a shard that runs
 * alone (the parallel runner) rides the same way as the whole suite in one game.
 */
final class TestPacks {
	private TestPacks() {
	}

	/** Turns the packs on (once; later calls find them on) and waits for the reload. Returns whether Fresh Animations is on. */
	static boolean enable(final ClientGameTestContext ctx) {
		final CompletableFuture<Void> reload = ctx.computeOnClient(mc -> {
			final PackRepository packs = mc.getResourcePackRepository();
			packs.reload();
			boolean changed = false;
			for (final String id : packs.getAvailableIds()) {
				// (-PvanillaModel: the plain horse model, no animation pack.)
				if (id.contains("FreshAnimations") && !Boolean.getBoolean("horsingaround.vanillaModel") && !packs.getSelectedIds().contains(id)) {
					packs.addPack(id);
					changed = true;
				}
			}
			// The test colours go on top.
			for (final String id : packs.getAvailableIds()) {
				if (id.contains("horsingaround-debug") && !Boolean.getBoolean("horsingaround.plainTextures") && !packs.getSelectedIds().contains(id)) {
					packs.addPack(id);
					changed = true;
				}
			}
			if (!changed) {
				return null;
			}
			mc.options.updateResourcePacks(packs);
			return mc.reloadResourcePacks();
		});
		if (reload != null) {
			ctx.waitFor(mc -> reload.isDone(), 20 * 120);
			ctx.waitTicks(40);
		}
		return ctx.computeOnClient(mc -> {
			for (final String id : mc.getResourcePackRepository().getSelectedIds()) {
				if (id.contains("FreshAnimations")) {
					return true;
				}
			}
			return false;
		});
	}
}
