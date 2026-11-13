package dev.horsingaround.client.forge;

import dev.horsingaround.MixinAudit;
import dev.horsingaround.client.HorsingAroundClient;
import dev.horsingaround.client.compat.EpicFightRider;
import dev.horsingaround.client.config.HorseSettingsScreen;
import dev.horsingaround.client.cosmetic.HatLayer;
import dev.horsingaround.client.cosmetic.HatModel;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.forge.HorsingAroundForge;
import dev.horsingaround.net.ServerRules;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//? if >=1.21.6 {
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//?} else {
/*import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.IModBusEvent;
*///?}

/** Forge's client entry point, started by the common one (forge/HorsingAroundForge) on a client. */
public final class HorsingAroundForgeClient {
	//? if >=1.21.6 {
	public HorsingAroundForgeClient(final FMLJavaModLoadingContext context) {
		// (On the main thread once every mod is loaded: the Entity Model Features hook needs that mod set up.)
		FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(event -> event.enqueueWork(HorsingAroundClient::init));
		HorsingAroundForge.clientRules = HorsingAroundClient::acceptRules;
		HorsingAroundForge.clientStyle = ServerRules::accept;
		HorsingAroundForge.clientHats = Hats::receive;
		RegisterKeyMappingsEvent.BUS.addListener(event -> {
			event.register(HorsingAroundClient.SETTINGS_KEY);
			event.register(HorsingAroundClient.SWAP_SHOULDER_KEY);
		});
		EntityRenderersEvent.RegisterLayerDefinitions.BUS.addListener(event -> event.registerLayerDefinition(HatModel.LAYER, HatModel::createLayer));
		EntityRenderersEvent.AddLayers.BUS.addListener(HorsingAroundForgeClient::addHatLayers);
		context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
			() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new HorseSettingsScreen(parent)));
		TickEvent.ClientTickEvent.Post.BUS.addListener(event -> HorsingAroundClient.tick(Minecraft.getInstance()));
		ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> HorsingAroundClient.joining());
		ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> HorsingAroundClient.left());
		if (MixinAudit.ENABLED) {
			// (Once the game is up: its first screen.)
			ScreenEvent.Init.Post.BUS.addListener(event -> {
				MixinAudit.run("client");
				Minecraft.getInstance().stop();
			});
		}
	}

	/** The supporters' cowboy hat, on players and mannequins. */
	private static void addHatLayers(final EntityRenderersEvent.AddLayers event) {
		for (final PlayerModelType skin : event.getModelTypes()) {
			addHatLayer(event.<AvatarRenderer<AbstractClientPlayer>>getPlayerRenderer(skin), event.getEntityModels());
			addHatLayer(event.<AvatarRenderer<ClientMannequin>>getMannequinRenderer(skin), event.getEntityModels());
		}
	}

	private static void addHatLayer(final AvatarRenderer<?> renderer, final EntityModelSet models) {
		if (renderer != null) {
			renderer.addLayer(new HatLayer(renderer, models));
		}
	}
	//?} else {
	/*public HorsingAroundForgeClient(final IEventBus modBus) {
		// (On the main thread once every mod is loaded: the Entity Model Features hook needs that mod set up.)
		onModBus(modBus, FMLClientSetupEvent.class, event -> event.enqueueWork(HorsingAroundClient::init));
		HorsingAroundForge.clientRules = HorsingAroundClient::acceptRules;
		HorsingAroundForge.clientStyle = ServerRules::accept;
		HorsingAroundForge.clientHats = Hats::receive;
		onModBus(modBus, RegisterKeyMappingsEvent.class, event -> {
			event.register(HorsingAroundClient.SETTINGS_KEY);
			event.register(HorsingAroundClient.SWAP_SHOULDER_KEY);
		});
		onModBus(modBus, EntityRenderersEvent.RegisterLayerDefinitions.class, event -> event.registerLayerDefinition(HatModel.LAYER, HatModel::createLayer));
		onModBus(modBus, EntityRenderersEvent.AddLayers.class, HorsingAroundForgeClient::addHatLayers);
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
			() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new HorseSettingsScreen(parent)));
		HorsingAroundForge.listen(TickEvent.ClientTickEvent.class, event -> {
			if (event.phase == TickEvent.Phase.END) {
				HorsingAroundClient.tick(Minecraft.getInstance());
			}
		});
		HorsingAroundForge.listen(ClientPlayerNetworkEvent.LoggingIn.class, event -> HorsingAroundClient.joining());
		HorsingAroundForge.listen(ClientPlayerNetworkEvent.LoggingOut.class, event -> HorsingAroundClient.left());
		if (EpicFightRider.LOADED) {
			MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, RenderLivingEvent.Pre.class, event -> EpicFightRider.before(event.getPoseStack()));
			MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Pre.class, event -> EpicFightRider.after(event.getPoseStack()));
			EpicFightModel.register();
		}
		if (MixinAudit.ENABLED) {
			// (Once the game is up: its first screen.)
			HorsingAroundForge.listen(ScreenEvent.Init.Post.class, event -> {
				MixinAudit.run("client");
				Minecraft.getInstance().stop();
			});
		}
	}

	private static <T extends Event & IModBusEvent> void onModBus(final IEventBus modBus, final Class<T> type, final Consumer<T> listener) {
		modBus.addListener(EventPriority.NORMAL, false, type, listener);
	}

	/^* The supporters' cowboy hat, on players. ^/
	private static void addHatLayers(final EntityRenderersEvent.AddLayers event) {
		for (final String skin : event.getSkins()) {
			if (event.getSkin(skin) instanceof AvatarRenderer<?> player) {
				player.addLayer(new HatLayer(player, event.getEntityModels()));
			}
		}
	}
	*///?}
}
