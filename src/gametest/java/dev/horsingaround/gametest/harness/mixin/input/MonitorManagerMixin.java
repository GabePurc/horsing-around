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

package dev.horsingaround.gametest.harness.mixin.input;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import dev.horsingaround.gametest.harness.impl.util.WindowHooks;

/** Picks the monitor by the window's real size, not the test size (before 26.3; registered by ClientGameTestMixinConfigPlugin). */
//? if >=26.2 {
@Mixin(targets = "com.mojang.blaze3d.platform.MonitorManager")
//?} else
/*@Mixin(targets = "com.mojang.blaze3d.platform.ScreenManager")*/
public class MonitorManagerMixin {
	@ModifyExpressionValue(method = "findBestMonitor(Lcom/mojang/blaze3d/platform/Window;)Lcom/mojang/blaze3d/platform/Monitor;", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getScreenWidth()I"))
	private int getRealWidth(int original, Window window) {
		return ((WindowHooks) (Object) window).fabric_getRealWidth();
	}

	@ModifyExpressionValue(method = "findBestMonitor(Lcom/mojang/blaze3d/platform/Window;)Lcom/mojang/blaze3d/platform/Monitor;", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getScreenHeight()I"))
	private int getRealHeight(int original, Window window) {
		return ((WindowHooks) (Object) window).fabric_getRealHeight();
	}
}
