package dev.horsingaround.smoke;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
//? if >=26.2 {
import net.minecraft.world.entity.EntityTypes;
//?} else {
/*import net.minecraft.world.entity.EntityType;
*///?}
//? if >=1.21.5 {
import net.minecraft.world.entity.EquipmentSlot;
//?}
//? if >=1.21.1 {
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//?}
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
//? if >=1.21.11 {
import net.minecraft.world.level.gamerules.GameRules;
//?} else {
/*import net.minecraft.world.level.GameRules;
*///?}
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Everything the smoke test does that differs between Minecraft versions, so the test itself reads the same on all. */
final class Compat {
	/** The game rules that keep the scene still, as commands (renamed in 1.21.11). */
	static final String[] STILL_RULES = {
		//? if >=1.21.11 {
		"gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false"
		//?} else {
		/*"gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false"
		*///?}
	};

	private Compat() {
	}

	/** Starts a creative flat world with cheats and no structures; the game then loads it like a world picked from the list. */
	static void createFlatWorld(final Minecraft minecraft, final String name, final long seed) {
		final WorldOptions options = new WorldOptions(seed, false, false);
		//? if >=26.1 {
		final LevelSettings settings = new LevelSettings(
			name, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT
		);
		//?} elif >=1.21.3 {
		/*final LevelSettings settings = new LevelSettings(
			name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT
		);
		*///?} else {
		/*final LevelSettings settings = new LevelSettings(
			name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT
		);
		*///?}
		//? if >=1.20.3 {
		minecraft.createWorldOpenFlows().createFreshLevel(
			name, settings, options, registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
			screen(minecraft)
		);
		//?} else {
		/*minecraft.createWorldOpenFlows().createFreshLevel(
			name, settings, options, registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions()
		);
		*///?}
	}

	/** A camera that is not part of the world: nothing draws it, the view just stands where it is put. */
	static Marker newMarker(final Level level) {
		//? if >=26.2 {
		return new Marker(EntityTypes.MARKER, level);
		//?} else
		/*return new Marker(EntityType.MARKER, level);*/
	}

	static Screen screen(final Minecraft minecraft) {
		//? if >=26.2 {
		return minecraft.gui.screen();
		//?} else
		/*return minecraft.screen;*/
	}

	/** Whether the start-up loading overlay is still up. */
	static boolean overlayShown(final Minecraft minecraft) {
		//? if >=26.2 {
		return minecraft.gui.overlay() != null;
		//?} else
		/*return minecraft.getOverlay() != null;*/
	}

	/** F1: no HUD and no hand. */
	static void setHudHidden(final Minecraft minecraft, final boolean hidden) {
		//? if >=26.2 {
		if (minecraft.gui.hud.isHidden() != hidden) {
			minecraft.gui.hud.toggle();
		}
		//?} else
		/*minecraft.options.hideGui = hidden;*/
	}

	/** Saves the last drawn frame as screenshots/<name> in the game folder (written in the background). */
	static void screenshot(final Minecraft minecraft, final String name) {
		//? if >=26.2 {
		final RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
		//?} else
		/*final RenderTarget target = minecraft.getMainRenderTarget();*/
		//? if >=1.21.6 {
		Screenshot.grab(minecraft.gameDirectory, name, target, 1, message -> { });
		//?} else
		/*Screenshot.grab(minecraft.gameDirectory, name, target, message -> { });*/
	}

	static void saddle(final AbstractHorse horse) {
		//? if >=1.21.5 {
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		//?} elif >=1.21.1 {
		/*horse.equipSaddle(new ItemStack(Items.SADDLE), null);
		*///?} else {
		/*horse.equipSaddle(null);
		*///?}
	}
}
