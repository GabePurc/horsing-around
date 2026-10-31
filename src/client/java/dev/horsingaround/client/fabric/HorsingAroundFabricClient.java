package dev.horsingaround.client.fabric;

import dev.horsingaround.MixinAudit;
import dev.horsingaround.client.HorsingAroundClient;
import dev.horsingaround.client.cosmetic.HatLayer;
import dev.horsingaround.client.cosmetic.HatModel;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.net.PlayerHatPayload;
import dev.horsingaround.net.RulesPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

/** Fabric's client entry point (fabric.mod.json). */
public final class HorsingAroundFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		if (MixinAudit.ENABLED) {
			ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
				MixinAudit.run("client");
				client.stop();
			});
		}
		HorsingAroundClient.init();
		//? if >=1.20.5 {
		ClientPlayNetworking.registerGlobalReceiver(RulesPayload.TYPE, (rules, context) -> HorsingAroundClient.acceptRules(rules));
		ClientPlayNetworking.registerGlobalReceiver(PlayerHatPayload.TYPE, (hat, context) -> Hats.receive(hat));
		//?} else {
		/*ClientPlayNetworking.registerGlobalReceiver(RulesPayload.TYPE, (rules, player, sender) -> HorsingAroundClient.acceptRules(rules));
		ClientPlayNetworking.registerGlobalReceiver(PlayerHatPayload.TYPE, (hat, player, sender) -> Hats.receive(hat));
		*///?}
		ClientPlayConnectionEvents.INIT.register((handler, minecraft) -> HorsingAroundClient.joining());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> HorsingAroundClient.left());
		ModelLayerRegistry.registerModelLayer(HatModel.LAYER, HatModel::createLayer);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new HatLayer(avatar, context.getModelSet()));
			}
		});
		KeyMappingHelper.registerKeyMapping(HorsingAroundClient.SETTINGS_KEY);
		KeyMappingHelper.registerKeyMapping(HorsingAroundClient.SWAP_SHOULDER_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(HorsingAroundClient::tick);
	}
}
