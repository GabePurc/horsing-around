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

package dev.horsingaround.gametest.harness.mixin.lifecycle;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;

import dev.horsingaround.gametest.harness.impl.ClientGameTestRunner;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Unique
	private boolean startedClientGametests = false;

	@Inject(method = "tick", at = @At("HEAD"))
	private void onTick(CallbackInfo ci) {
		//? if >=26.2 {
		boolean loading = ((Minecraft) (Object) this).gui.overlay() != null;
		//?} else
		/*boolean loading = ((Minecraft) (Object) this).getOverlay() != null;*/
		if (!startedClientGametests && !loading) {
			startedClientGametests = true;
			ClientGameTestRunner.start();
		}
	}

	//? if <26.2 {
	/*// Failed tests end the game with an error code. (Before 26.2 the game exits from destroy(), and some versions' main,
	// 1.20.1's, logs what the game threw and shuts down as if all went well.)
	@org.spongepowered.asm.mixin.injection.ModifyArg(method = "destroy", at = @At(value = "INVOKE", target = "Ljava/lang/System;exit(I)V", remap = false))
	private int exitWithFailure(int code) {
		return dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl.testFailureException != null ? 1 : code;
	}
	*///?}
}
