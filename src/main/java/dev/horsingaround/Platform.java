package dev.horsingaround;

import java.nio.file.Path;
import net.minecraft.server.level.ServerPlayer;
//? if fabric {
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
//?} else {
/*import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
*///?}
//? if >=1.20.5 {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
*///?}

/** What the common code asks of the mod loader it runs on, Fabric or NeoForge (the entry points are in fabric/ and neoforge/). */
public final class Platform {
	private Platform() {
	}

	public static Path configDir() {
		//? if fabric {
		return FabricLoader.getInstance().getConfigDir();
		//?} else
		/*return FMLPaths.CONFIGDIR.get();*/
	}

	public static boolean isModLoaded(final String id) {
		//? if fabric {
		return FabricLoader.getInstance().isModLoaded(id);
		//?} else
		/*return ModList.get().isLoaded(id);*/
	}

	/** This mod's version, as the player sees it in the mod list. */
	public static String modVersion() {
		//? if fabric {
		return FabricLoader.getInstance().getModContainer(HorsingAround.MOD_ID).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
		//?} else
		/*return ModList.get().getModContainerById(HorsingAround.MOD_ID).map(mod -> mod.getModInfo().getVersion().toString()).orElse("");*/
	}

	//? if >=1.20.5 {
	/** Whether the player's client runs the mod (it said it takes this payload). */
	public static boolean canSend(final ServerPlayer player, final CustomPacketPayload.Type<?> type) {
		//? if fabric {
		return ServerPlayNetworking.canSend(player, type);
		//?} else
		/*return player.connection.hasChannel(type);*/
	}

	public static void send(final ServerPlayer player, final CustomPacketPayload payload) {
		//? if fabric {
		ServerPlayNetworking.send(player, payload);
		//?} else
		/*PacketDistributor.sendToPlayer(player, payload);*/
	}
	//?} else {
	/*public static boolean canSend(final ServerPlayer player, final PacketType<?> type) {
		return ServerPlayNetworking.canSend(player, type);
	}

	public static void send(final ServerPlayer player, final FabricPacket payload) {
		ServerPlayNetworking.send(player, payload);
	}
	*///?}
}
