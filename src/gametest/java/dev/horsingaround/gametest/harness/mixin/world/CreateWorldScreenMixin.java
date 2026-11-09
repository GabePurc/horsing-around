/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Changed for Horsing Around (2026): moved into this project's test source set and made loader-neutral, so every
 * loader and Minecraft version runs the same client tests. Originally from Fabric API's fabric-client-gametest-api-v1.
 */

package dev.horsingaround.gametest.harness.mixin.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.storage.PrimaryLevelData;
//? if >=26.1 {
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.SavedDataStorage;
//?}

import dev.horsingaround.gametest.harness.impl.util.ClientGameTestImpl;
import dev.horsingaround.gametest.harness.impl.util.DedicatedServerImplUtil;

@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
	//? if <1.21.2 {
	/*@org.spongepowered.asm.mixin.Shadow
	private net.minecraft.world.level.LevelSettings createLevelSettings(boolean debug) {
		throw new AssertionError();
	}

	*///?}
	@Inject(method = "onCreate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/WorldOpenFlows;confirmWorldCreation(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;Lcom/mojang/serialization/Lifecycle;Ljava/lang/Runnable;Z)V"), cancellable = true)
	//? if >=26.1 {
	private void createLevelDataForServers(CallbackInfo ci, @Local(name = "finalLayers") LayeredRegistryAccess<RegistryLayer> finalLayers, @Local(name = "worldData") PrimaryLevelData worldData, @Local(name = "worldGenSettings") WorldGenSettings worldGenSettings, @Local(name = "gameRules") GameRules gameRules) {
	//?} elif >=1.21.2 {
	/*private void createLevelDataForServers(CallbackInfo ci, @Local LayeredRegistryAccess<RegistryLayer> finalLayers, @Local PrimaryLevelData worldData) {
	*///?} else {
	/*private void createLevelDataForServers(CallbackInfo ci, @Local LayeredRegistryAccess<RegistryLayer> finalLayers, @Local net.minecraft.world.level.levelgen.WorldDimensions.Complete complete, @Local(ordinal = 2) com.mojang.serialization.Lifecycle lifecycle) {
		// (Before 1.21.2 the level data is made once the world is confirmed: made here the same way.)
		PrimaryLevelData worldData = new PrimaryLevelData(this.createLevelSettings(complete.specialWorldProperty() == PrimaryLevelData.SpecialWorldProperty.DEBUG),
			((CreateWorldScreen) (Object) this).getUiState().getSettings().options(), complete.specialWorldProperty(), lifecycle);
	*///?}
		if (DedicatedServerImplUtil.saveLevelDataTo != null) {
			try {
				Path worldPath = DedicatedServerImplUtil.saveLevelDataTo;

				//? if >=26.1 {
				CompoundTag levelDatInner = worldData.createTag(null);
				//?} else
				/*CompoundTag levelDatInner = worldData.createTag(finalLayers.compositeAccess(), null);*/
				var levelDat = new CompoundTag();
				levelDat.put("Data", levelDatInner);

				Files.createDirectories(worldPath);
				//? if >=1.20.3 {
				NbtIo.writeCompressed(levelDat, worldPath.resolve("level.dat"));
				//?} else
				/*NbtIo.writeCompressed(levelDat, worldPath.resolve("level.dat").toFile());*/

				// (Before 26.1 level.dat holds the world settings and game rules too.)
				//? if >=26.1 {
				try (var savedDataStorage = new SavedDataStorage(worldPath.resolve("data"), DataFixers.getDataFixer(), finalLayers.compositeAccess())) {
					savedDataStorage.set(WorldGenSettings.TYPE, worldGenSettings);
					savedDataStorage.set(GameRuleMap.TYPE, ((GameRulesAccessor) gameRules).getRules());
					savedDataStorage.saveAndJoin();
				}
				//?}
			} catch (IOException e) {
				ClientGameTestImpl.LOGGER.error("Failed to save dedicated server level data", e);
			}

			ci.cancel();
		}
	}
}
