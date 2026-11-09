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

package dev.horsingaround.gametest.harness.impl.context;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import dev.horsingaround.gametest.harness.context.ClientGameTestContext;
import dev.horsingaround.gametest.harness.context.TestServerConnection;
import dev.horsingaround.gametest.harness.context.TestServerContext;
import dev.horsingaround.gametest.harness.context.TestSingleplayerContext;
import dev.horsingaround.gametest.harness.world.TestWorldSave;
import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;

public class TestSingleplayerContextImpl implements TestSingleplayerContext {
	private final ClientGameTestContext context;
	private final TestWorldSave worldSave;
	private final TestServerContextImpl server;
	private final TestServerConnection connection;

	public TestSingleplayerContextImpl(ClientGameTestContext context, TestWorldSave worldSave, MinecraftServer server) {
		this.context = context;
		this.worldSave = worldSave;
		this.server = new TestServerContextImpl(server);
		this.connection = new TestServerConnectionImpl(context, this.server);
	}

	@Override
	public TestWorldSave getWorldSave() {
		return worldSave;
	}

	@Override
	public TestServerConnection getConnection() {
		return connection;
	}

	@Override
	public TestServerContext getServer() {
		return server;
	}

	@Override
	public void close() {
		ThreadingImpl.checkOnGametestThread("close");
		//? if <1.21.2 {
		/*// Before 1.21.2 a server stopping while chunks are still generating can go round unloading them for ever (vanilla
		// puts a chunk that isn't ready back in the queue it is emptying): let what the test set going finish first.
		context.waitTicks(200);
		*///?}

		context.runOnClient(client -> {
			if (client.level == null) {
				throw new IllegalStateException("Exited the world before closing singleplayer context");
			}

			dev.horsingaround.gametest.harness.impl.util.ClientGameTestImpl.leaveWorld(client, Component.translatable("menu.savingLevel"), new GenericMessageScreen(Component.translatable("menu.savingLevel")));
		});

		context.waitFor(client -> !ThreadingImpl.isServerRunning && client.level == null, SharedConstants.TICKS_PER_MINUTE);
		context.waitTicks(2);
		context.setScreen(TitleScreen::new);
	}
}
