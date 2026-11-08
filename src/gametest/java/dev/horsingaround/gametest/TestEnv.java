package dev.horsingaround.gametest;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.Platform;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
//? if fabric {
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
//?} else {
/*import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.modlist.ModListScreen;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
*///?}

/** What the tests need from the mod loader, the same on each: one place per loader for what differs. */
public final class TestEnv {
	private TestEnv() {
	}

	/** The game's folder (the run folder): reports, screenshots. */
	public static Path gameDir() {
		//? if fabric {
		return FabricLoader.getInstance().getGameDir();
		//?} else
		/*return FMLPaths.GAMEDIR.get();*/
	}

	public static boolean modLoaded(final String id) {
		return Platform.isModLoaded(id);
	}

	/** After every client tick, on the client thread. */
	public static void onClientTickEnd(final Consumer<Minecraft> listener) {
		//? if fabric {
		ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
		//?} else
		/*NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> listener.accept(Minecraft.getInstance()));*/
	}

	@FunctionalInterface
	public interface Hurt {
		void hurt(LivingEntity entity, DamageSource source, float taken);
	}

	/** After a living thing on the server takes damage (what it took, armour and the like counted). */
	public static void afterDamage(final Hurt listener) {
		//? if fabric {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> listener.hurt(entity, source, taken));
		//?} else
		/*NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> listener.hurt(event.getEntity(), event.getSource(), event.getInflictedDamage()));*/
	}

	/** The loader's list of mods, where a mod's settings open (Mod Menu on Fabric). */
	public static Screen modsScreen(final Screen parent) {
		//? if fabric {
		return com.terraformersmc.modmenu.api.ModMenuApi.createModsScreen(parent);
		//?} else
		/*return ModListScreen.create(parent);*/
	}

	/** Whether the loader's list of mods opens this mod's settings. */
	public static boolean modsListOpensSettings() {
		//? if fabric {
		return FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class).stream()
			.anyMatch(entry -> entry.getProvider().getMetadata().getId().equals(HorsingAround.MOD_ID));
		//?} else
		/*return ModList.get().getModContainerById(HorsingAround.MOD_ID).flatMap(mod -> mod.getCustomExtension(IConfigScreenFactory.class)).isPresent();*/
	}

	/** A connection for a player no client is behind, which sends nothing (the loader's fake player's). */
	public static ServerGamePacketListenerImpl silentConnection(final ServerPlayer player) {
		//? if fabric {
		try {
			return FakePlayer.get(player.level()).connection.getClass().getConstructor(ServerPlayer.class).newInstance(player);
		} catch (final ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		//?} else {
		/*try {
			final var constructor = FakePlayerFactory.getMinecraft(player.level()).connection.getClass().getDeclaredConstructor(MinecraftServer.class, ServerPlayer.class);
			constructor.setAccessible(true);
			return (ServerGamePacketListenerImpl) constructor.newInstance(player.level().getServer(), player);
		} catch (final ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		*///?}
	}

	/** Sends a payload to the server, from the client thread. */
	public static void sendToServer(final CustomPacketPayload payload) {
		//? if fabric {
		ClientPlayNetworking.send(payload);
		//?} else
		/*ClientPacketDistributor.sendToServer(payload);*/
	}
}
