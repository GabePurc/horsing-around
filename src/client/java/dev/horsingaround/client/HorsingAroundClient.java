package dev.horsingaround.client;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.MixinAudit;
import com.mojang.blaze3d.platform.InputConstants;
//? if >=1.21
import dev.horsingaround.client.compat.EmfSaddleTracker;
import dev.horsingaround.client.config.HorseSettingsScreen;
import dev.horsingaround.client.debug.SteeringOverlay;
import dev.horsingaround.client.cosmetic.HatLayer;
import dev.horsingaround.client.cosmetic.HatModel;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.client.cosmetic.Supporters;
import dev.horsingaround.net.PlayerHatPayload;
import dev.horsingaround.net.RulesPayload;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import dev.horsingaround.net.ServerRules;
import dev.horsingaround.ride.HorseConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HorsingAroundClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger(HorsingAround.MOD_ID);

	@Override
	public void onInitializeClient() {
		if (MixinAudit.ENABLED) {
			ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
				MixinAudit.run("client");
				client.stop();
			});
		}
		final RiderKeys keys = new RiderKeys(Minecraft.getInstance());
		HorsingAround.bridge = keys;
		//? if >=1.21 {
		if (FabricLoader.getInstance().isModLoaded("entity_model_features")) {
			try {
				EmfSaddleTracker.register();
			} catch (final LinkageError | RuntimeException e) {
				// A version of Entity Model Features we don't know: the rider follows an imitation of the gait instead.
				LOGGER.warn("Could not hook Entity Model Features animations; riders follow a generated gait motion instead", e);
			}
		}
		//?}
		// The server tells us it runs the mod (and its rules); until then, and on servers without it, horses ride as in vanilla.
		//? if >=1.20.5 {
		ClientPlayNetworking.registerGlobalReceiver(RulesPayload.TYPE, (rules, context) -> acceptRules(rules));
		ClientPlayNetworking.registerGlobalReceiver(PlayerHatPayload.TYPE, (hat, context) -> Hats.receive(hat));
		//?} else {
		/*ClientPlayNetworking.registerGlobalReceiver(RulesPayload.TYPE, (rules, player, sender) -> acceptRules(rules));
		ClientPlayNetworking.registerGlobalReceiver(PlayerHatPayload.TYPE, (hat, player, sender) -> Hats.receive(hat));
		*///?}
		ClientPlayConnectionEvents.INIT.register((handler, minecraft) -> {
			ServerRules.reset();
			ServerNotice.reset();
			Hats.clear();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
			ServerRules.reset();
			Hats.clear();
		});

		// The supporters' cowboy hat.
		Supporters.load();
		ModelLayerRegistry.registerModelLayer(HatModel.LAYER, HatModel::createLayer);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new HatLayer(avatar, context.getModelSet()));
			}
		});
		//? if >=1.21.9 {
		final KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "horses"));
		//?} else
		/*final String category = "key.category.horsingaround.horses";*/
		final KeyMapping settings = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.horsingaround.settings", InputConstants.UNKNOWN.getValue(), category));
		final KeyMapping swapShoulder = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.horsingaround.swap_shoulder", InputConstants.KEY_O, category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			RideCamera.tick(minecraft);
			keys.endTick();
			while (settings.consumeClick()) {
				HorseSettingsScreen.open(minecraft);
			}
			while (swapShoulder.consumeClick()) {
				// (Only while riding: on foot the key does nothing, and other mods may share it.)
				if (RideCamera.isRiding()) {
					HorseConfig.swapShoulder();
				}
			}
			SteeringOverlay.tick(minecraft);
			ServerNotice.tick(minecraft);
		});
	}

	/** The server runs the mod: its rules apply, and it hears which hat this player wears. */
	public static void acceptRules(final RulesPayload rules) {
		ServerRules.accept(rules);
		Hats.sendOwn();
	}
}
