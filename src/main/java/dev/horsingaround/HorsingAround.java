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
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class HorsingAround implements ModInitializer {
	public static final String MOD_ID = "horsingaround";

	public static RiderBridge bridge = RiderBridge.NONE;

	private static @Nullable MinecraftServer server;
	private static String version = "";

	@Override
	public void onInitialize() {
		version = FabricLoader.getInstance().getModContainer(MOD_ID).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
		HorseConfig.load();
		PayloadTypeRegistry.clientboundPlay().register(RulesPayload.TYPE, RulesPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlayerHatPayload.TYPE, PlayerHatPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HatPayload.TYPE, HatPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(HatPayload.TYPE, (hat, context) -> HatRelay.receive(context.player(), hat));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> {
			if (ServerPlayNetworking.canSend(handler, RulesPayload.TYPE)) {
				sender.sendPacket(rules());
			}
			HatRelay.joined(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, left) -> HatRelay.left(handler.player));
		ServerLifecycleEvents.SERVER_STARTING.register(started -> server = started);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			server = null;
			HatRelay.clear();
		});
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> Mounts.invalidate());
	}

	public static String version() {
		return version;
	}

	private static RulesPayload rules() {
		return new RulesPayload(ServerRules.PROTOCOL, version, RideTuning.RIDE_THROUGH_LEAVES);
	}

	/** The server's settings changed (singleplayer or a LAN host using the settings screen): tell everyone connected. */
	public static void sendRules() {
		final MinecraftServer running = server;
		if (running != null) {
			running.execute(() -> {
				for (final ServerPlayer player : running.getPlayerList().getPlayers()) {
					if (ServerPlayNetworking.canSend(player, RulesPayload.TYPE)) {
						ServerPlayNetworking.send(player, rules());
					}
				}
			});
		}
	}
}
