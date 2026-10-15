package dev.horsingaround;

import dev.horsingaround.net.HatPayload;
import dev.horsingaround.net.HatRelay;
import dev.horsingaround.net.PlayerHatPayload;
import dev.horsingaround.net.RulesPayload;
import dev.horsingaround.net.ServerRules;
import dev.horsingaround.ride.HorseConfig;
import dev.horsingaround.ride.Mounts;
import dev.horsingaround.ride.RideTuning;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//? if >=1.20.5
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class HorsingAround implements ModInitializer {
	public static final String MOD_ID = "horsingaround";

	public static RiderBridge bridge = RiderBridge.NONE;

	private static String version = "";

	@Override
	public void onInitialize() {
		version = FabricLoader.getInstance().getModContainer(MOD_ID).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
		HorseConfig.load();
		//? if >=1.20.5 {
		PayloadTypeRegistry.clientboundPlay().register(RulesPayload.TYPE, RulesPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlayerHatPayload.TYPE, PlayerHatPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HatPayload.TYPE, HatPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(HatPayload.TYPE, (hat, context) -> HatRelay.receive(context.player(), hat));
		//?} else {
		/*// (Before 1.20.5 a packet knows its own type and codec; there is nothing to register but the receiver.)
		ServerPlayNetworking.registerGlobalReceiver(HatPayload.TYPE, (hat, player, sender) -> HatRelay.receive(player, hat));
		*///?}
		ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> {
			if (ServerPlayNetworking.canSend(handler, RulesPayload.TYPE)) {
				sender.sendPacket(rules());
			}
			HatRelay.joined(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, left) -> HatRelay.left(handler.player));
		ServerTickEvents.END_SERVER_TICK.register(HatRelay::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> HatRelay.clear());
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> Mounts.invalidate());
		if (MixinAudit.ENABLED) {
			ServerLifecycleEvents.SERVER_STARTED.register(MixinAudit::server);
		}
	}

	public static String version() {
		return version;
	}

	private static RulesPayload rules() {
		return new RulesPayload(ServerRules.PROTOCOL, version, RideTuning.RIDE_THROUGH_LEAVES);
	}

	/** Tells everyone connected the rules again (for tests: a client that lost them gets them back). Server thread. */
	public static void sendRules(final MinecraftServer server) {
		for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, RulesPayload.TYPE)) {
				ServerPlayNetworking.send(player, rules());
			}
		}
	}
}
