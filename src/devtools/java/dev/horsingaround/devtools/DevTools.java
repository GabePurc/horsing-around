package dev.horsingaround.devtools;

import com.mojang.blaze3d.platform.InputConstants;
import dev.horsingaround.client.render.LegBones;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Horsing Around's dev tools: loaded in dev games (./gradlew runClient) and the client tests, never in the jar players
 * install. In a dev game the leg probe measures every frame of the ridden horse (leaves count as open, a horse goes
 * through them) and the {@link Recorder} keeps the last half minute of it: F7 saves it for study, and every hitch and sunk
 * hoof goes to devtools/events.log as it happens; F8 draws the plain model's leg bones over the horses (see
 * LegBones). The client tests run the probe themselves.
 */
public final class DevTools implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		if (System.getProperty("fabric.client.gametest") != null) {
			return;
		}
		LegProbe.listen(true);
		LegProbe.passLeaves = true;
		Recorder.on = true;
		final KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("horsingaround_devtools", "dev"));
		final KeyMapping save = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.horsingaround_devtools.save", InputConstants.KEY_F7, category));
		final KeyMapping bones = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.horsingaround_devtools.bones", InputConstants.KEY_F8, category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (save.consumeClick()) {
				Recorder.save(minecraft);
			}
			while (bones.consumeClick()) {
				LegBones.on = !LegBones.on;
			}
		});
	}
}
