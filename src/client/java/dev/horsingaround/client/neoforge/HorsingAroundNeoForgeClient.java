package dev.horsingaround.client.neoforge;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.MixinAudit;
import dev.horsingaround.client.HorsingAroundClient;
import dev.horsingaround.client.config.HorseSettingsScreen;
import dev.horsingaround.client.cosmetic.HatLayer;
import dev.horsingaround.client.cosmetic.HatModel;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.net.PlayerHatPayload;
import dev.horsingaround.net.RulesPayload;
import dev.horsingaround.net.ServerRules;
import dev.horsingaround.net.StylePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
//? if >=1.21.9 {
import net.minecraft.world.entity.player.PlayerModelType;
//?} else
/*import net.minecraft.client.resources.PlayerSkin;*/
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
//? if >1.21.1 {
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
//?} else
/*import dev.horsingaround.neoforge.HorsingAroundNeoForge;*/
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge's client entry point, next to the common one (neoforge/HorsingAroundNeoForge). */
@Mod(value = HorsingAround.MOD_ID, dist = Dist.CLIENT)
public final class HorsingAroundNeoForgeClient {
	public HorsingAroundNeoForgeClient(final IEventBus modBus, final ModContainer mod) {
		// (On the main thread once every mod is loaded: the Entity Model Features hook needs that mod set up.)
		modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(HorsingAroundClient::init));
		//? if >1.21.1 {
		modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> {
			event.register(RulesPayload.TYPE, (rules, context) -> HorsingAroundClient.acceptRules(rules));
			event.register(StylePayload.TYPE, (style, context) -> ServerRules.accept(style));
			event.register(PlayerHatPayload.TYPE, (hat, context) -> Hats.receive(hat));
		});
		//?} else {
		/*HorsingAroundNeoForge.clientRules = (rules, context) -> HorsingAroundClient.acceptRules(rules);
		HorsingAroundNeoForge.clientStyle = (style, context) -> ServerRules.accept(style);
		HorsingAroundNeoForge.clientHats = (hat, context) -> Hats.receive(hat);
		*///?}
		modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
			//? if >=1.21.9
			event.registerCategory(HorsingAroundClient.KEY_CATEGORY);
			event.register(HorsingAroundClient.SETTINGS_KEY);
			event.register(HorsingAroundClient.SWAP_SHOULDER_KEY);
		});
		modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class, event -> event.registerLayerDefinition(HatModel.LAYER, HatModel::createLayer));
		modBus.addListener(EntityRenderersEvent.AddLayers.class, HorsingAroundNeoForgeClient::addHatLayers);
		mod.registerExtensionPoint(IConfigScreenFactory.class, (container, parent) -> new HorseSettingsScreen(parent));
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> HorsingAroundClient.tick(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> HorsingAroundClient.joining());
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> HorsingAroundClient.left());
		if (MixinAudit.ENABLED) {
			// (Once the game is up: its first screen, the title screen or NeoForge's loading warnings.)
			NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
				MixinAudit.run("client");
				Minecraft.getInstance().stop();
			});
		}
	}

	/** The supporters' cowboy hat, on players and mannequins. */
	private static void addHatLayers(final EntityRenderersEvent.AddLayers event) {
		//? if >=1.21.9 {
		for (final PlayerModelType skin : event.getSkins()) {
			addHatLayer(event.getPlayerRenderer(skin), event.getEntityModels());
			addHatLayer(event.getMannequinRenderer(skin), event.getEntityModels());
		}
		//?} else {
		/*for (final PlayerSkin.Model skin : event.getSkins()) {
			if (event.getSkin(skin) instanceof AvatarRenderer<?> player) {
				addHatLayer(player, event.getEntityModels());
			}
		}
		*///?}
	}

	private static void addHatLayer(final AvatarRenderer<?> renderer, final EntityModelSet models) {
		if (renderer != null) {
			renderer.addLayer(new HatLayer(renderer, models));
		}
	}
}
