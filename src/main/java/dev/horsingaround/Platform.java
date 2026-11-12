package dev.horsingaround;

import java.nio.file.Path;
import net.minecraft.server.level.ServerPlayer;
//? if fabric {
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
//?} elif neoforge {
/*import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
*///?} else {
/*import dev.horsingaround.forge.HorsingAroundForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
*///?}
//? if >=1.20.5 {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} elif fabric {
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
*///?}

/**
 * What the common code asks of the mod loader it runs on, Fabric, NeoForge or Forge (the entry points are in fabric/,
 * neoforge/ and forge/).
 */
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
		//?} elif forge && >=1.21.6 {
		/*return ModList.isLoaded(id);
		*///?} else
		/*return ModList.get().isLoaded(id);*/
	}

	/** This mod's version, as the player sees it in the mod list. */
	public static String modVersion() {
		//? if fabric {
		return FabricLoader.getInstance().getModContainer(HorsingAround.MOD_ID).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
		//?} elif forge && >=1.21.6 {
		/*return ModList.getModContainerById(HorsingAround.MOD_ID).map(mod -> mod.getModInfo().getVersion().toString()).orElse("");
		*///?} else
		/*return ModList.get().getModContainerById(HorsingAround.MOD_ID).map(mod -> mod.getModInfo().getVersion().toString()).orElse("");*/
	}

	//? if >=1.20.5 {
	/** Whether the player's client runs the mod (it said it takes this payload). */
	public static boolean canSend(final ServerPlayer player, final CustomPacketPayload.Type<?> type) {
		//? if fabric {
		return ServerPlayNetworking.canSend(player, type);
		//?} elif neoforge {
		/*return player.connection.hasChannel(type);
		*///?} else
		/*return HorsingAroundForge.CHANNEL.isRemotePresent(player.connection.getConnection());*/
	}

	public static void send(final ServerPlayer player, final CustomPacketPayload payload) {
		//? if fabric {
		ServerPlayNetworking.send(player, payload);
		//?} elif neoforge {
		/*PacketDistributor.sendToPlayer(player, payload);
		*///?} else
		/*HorsingAroundForge.CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));*/
	}
	//?} elif fabric {
	/*public static boolean canSend(final ServerPlayer player, final PacketType<?> type) {
		return ServerPlayNetworking.canSend(player, type);
	}

	public static void send(final ServerPlayer player, final FabricPacket payload) {
		ServerPlayNetworking.send(player, payload);
	}
	*///?} else {
	/*// (Forge before payload types: one channel carries every payload; a payload's TYPE is its class.)
	public static boolean canSend(final ServerPlayer player, final Class<?> type) {
		return HorsingAroundForge.CHANNEL.isRemotePresent(player.connection.connection);
	}

	public static void send(final ServerPlayer player, final Object payload) {
		HorsingAroundForge.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
	}
	*///?}
}
