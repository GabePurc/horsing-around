package dev.horsingaround.gametest;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.Platform;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.20.5 {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} elif fabric
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;*/
import net.minecraft.server.level.ServerLevel;
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
//?} elif neoforge {
/*import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
*///?} else {
/*import dev.horsingaround.forge.HorsingAroundForge;
import dev.horsingaround.gametest.forge.HorsingAroundTestsForge;
import dev.horsingaround.gametest.harness.impl.util.GameTestSyncPayload;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
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
		//?} elif neoforge {
		/*NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> listener.accept(Minecraft.getInstance()));
		*///?} elif >=1.21.6 {
		/*TickEvent.ClientTickEvent.Post.BUS.addListener(event -> listener.accept(Minecraft.getInstance()));
		*///?} else {
		/*HorsingAroundForge.listen(TickEvent.ClientTickEvent.class, event -> {
			if (event.phase == TickEvent.Phase.END) {
				listener.accept(Minecraft.getInstance());
			}
		});
		*///?}
	}

	@FunctionalInterface
	public interface Hurt {
		void hurt(LivingEntity entity, DamageSource source, float taken);
	}

	//? if fabric && <1.21 {
	/*// (Called by LivingEntityHurtMixin: Fabric API has no after-damage event before 1.21.)
	public static final java.util.List<Hurt> HURT = new java.util.concurrent.CopyOnWriteArrayList<>();
	*///?}

	/** After a living thing on the server takes damage (what it took, armour and the like counted). */
	public static void afterDamage(final Hurt listener) {
		//? if fabric && >=1.21 {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> listener.hurt(entity, source, taken));
		//?} elif fabric {
		/*HURT.add(listener);
		*///?} elif forge {
		/*// (Forge's event comes as the damage is about to land, armour and the like already counted.)
		//? if >=1.21.6 {
		LivingDamageEvent.BUS.addListener(event -> {
			listener.hurt(event.getEntity(), event.getSource(), event.getAmount());
		});
		//?} else
		/^HorsingAroundForge.listen(LivingDamageEvent.class, event -> listener.hurt(event.getEntity(), event.getSource(), event.getAmount()));^/
		*///?} elif >=26.1 {
		/*NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> listener.hurt(event.getEntity(), event.getSource(), event.getInflictedDamage()));
		*///?} else
		/*NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> listener.hurt(event.getEntity(), event.getSource(), event.getNewDamage()));*/
	}

	/** The loader's list of mods, where a mod's settings open (Mod Menu on Fabric). */
	public static Screen modsScreen(final Screen parent) {
		//? if fabric {
		return com.terraformersmc.modmenu.api.ModMenuApi.createModsScreen(parent);
		//?} elif forge {
		/*return new net.minecraftforge.client.gui.ModListScreen(parent);
		*///?} elif >=26.2 {
		/*return net.neoforged.neoforge.client.gui.modlist.ModListScreen.create(parent);
		*///?} else
		/*return new net.neoforged.neoforge.client.gui.ModListScreen(parent);*/
	}

	/** Whether the loader's list of mods opens this mod's settings. */
	public static boolean modsListOpensSettings() {
		//? if fabric {
		return FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class).stream()
			.anyMatch(entry -> entry.getProvider().getMetadata().getId().equals(HorsingAround.MOD_ID));
		//?} elif neoforge {
		/*return ModList.get().getModContainerById(HorsingAround.MOD_ID).flatMap(mod -> mod.getCustomExtension(IConfigScreenFactory.class)).isPresent();
		*///?} elif >=26.1 {
		/*return ModList.getModContainerById(HorsingAround.MOD_ID).flatMap(mod -> mod.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class)).isPresent();
		*///?} else
		/*return ModList.get().getModContainerById(HorsingAround.MOD_ID).flatMap(mod -> mod.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class)).isPresent();*/
	}

	/** A connection for a player no client is behind, which sends nothing (the loader's fake player's). */
	public static ServerGamePacketListenerImpl silentConnection(final ServerPlayer player) {
		//? if fabric {
		try {
			return FakePlayer.get((ServerLevel) player.level()).connection.getClass().getConstructor(ServerPlayer.class).newInstance(player);
		} catch (final ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		//?} elif forge {
		/*// (Forge has no fake player from 1.21.6; only tests of mods with no Forge build ask for one.)
		throw new UnsupportedOperationException("no fake player on Forge");
		*///?} else {
		/*try {
			final var constructor = FakePlayerFactory.getMinecraft((ServerLevel) player.level()).connection.getClass().getDeclaredConstructor(MinecraftServer.class, ServerPlayer.class);
			constructor.setAccessible(true);
			return (ServerGamePacketListenerImpl) constructor.newInstance(player.level().getServer(), player);
		} catch (final ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		*///?}
	}

	/** Sends the harness's sync payload to a player, from the server thread. */
	public static void sendSync(final ServerPlayer player) {
		//? if forge && >=1.20.2 {
		/*HorsingAroundTestsForge.CHANNEL.send(GameTestSyncPayload.INSTANCE, PacketDistributor.PLAYER.with(player));
		*///?} elif forge {
		/*HorsingAroundTestsForge.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), GameTestSyncPayload.INSTANCE);
		*///?} else
		Platform.send(player, dev.horsingaround.gametest.harness.impl.util.GameTestSyncPayload.INSTANCE);
	}

	/** Sends a payload to the server, from the client thread. */
	//? if >=1.20.5 {
	public static void sendToServer(final CustomPacketPayload payload) {
	//?} elif fabric {
	/*public static void sendToServer(final FabricPacket payload) {
	*///?} else
	/*public static void sendToServer(final Object payload) {*/
		//? if fabric {
		ClientPlayNetworking.send(payload);
		//?} elif forge && >=1.20.2 {
		/*// (A Forge channel sends only its own payloads: the harness's, or the mod's.)
		(payload == GameTestSyncPayload.INSTANCE ? HorsingAroundTestsForge.CHANNEL : HorsingAroundForge.CHANNEL).send(payload, Minecraft.getInstance().getConnection().getConnection());
		*///?} elif forge {
		/*(payload == GameTestSyncPayload.INSTANCE ? HorsingAroundTestsForge.CHANNEL : HorsingAroundForge.CHANNEL).sendToServer(payload);
		*///?} elif >=26.1 {
		/*net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
		*///?} else
		/*net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);*/
	}
}
