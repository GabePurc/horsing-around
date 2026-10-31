package dev.horsingaround.fabric;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.MixinAudit;
import dev.horsingaround.net.HatPayload;
import dev.horsingaround.net.HatRelay;
import dev.horsingaround.net.PlayerHatPayload;
import dev.horsingaround.net.RulesPayload;
import dev.horsingaround.ride.Mounts;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//? if >=1.20.5
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Fabric's entry point (fabric.mod.json). */
public final class HorsingAroundFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		HorsingAround.init();
		//? if >=1.20.5 {
		PayloadTypeRegistry.clientboundPlay().register(RulesPayload.TYPE, RulesPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlayerHatPayload.TYPE, PlayerHatPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HatPayload.TYPE, HatPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(HatPayload.TYPE, (hat, context) -> HatRelay.receive(context.player(), hat));
		//?} else {
		/*// (Before 1.20.5 a packet knows its own type and codec; there is nothing to register but the receiver.)
		ServerPlayNetworking.registerGlobalReceiver(HatPayload.TYPE, (hat, player, sender) -> HatRelay.receive(player, hat));
		*///?}
		ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> HorsingAround.joined(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, left) -> HatRelay.left(handler.player));
		ServerTickEvents.END_SERVER_TICK.register(HatRelay::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> HatRelay.clear());
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> Mounts.invalidate());
		if (MixinAudit.ENABLED) {
			ServerLifecycleEvents.SERVER_STARTED.register(MixinAudit::server);
		}
	}
}
