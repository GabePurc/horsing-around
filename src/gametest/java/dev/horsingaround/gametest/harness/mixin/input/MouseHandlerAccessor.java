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

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.MouseHandler;
//? if >=1.21.9
import net.minecraft.client.input.MouseButtonInfo;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
	//? if >=1.21.9 {
	@Invoker
	void invokeOnButton(long window, MouseButtonInfo arg, int action);
	//?} else
	/*@Invoker("onPress") void invokeOnButton(long window, int button, int action, int modifiers);*/

	@Invoker
	void invokeOnScroll(long window, double horizontal, double vertical);

	@Invoker
	//? if >=26.3 {
	void invokeOnMove(long window, double x, double y, double deltaX, double deltaY);
	//?} else
	/*void invokeOnMove(long window, double x, double y);*/
}
