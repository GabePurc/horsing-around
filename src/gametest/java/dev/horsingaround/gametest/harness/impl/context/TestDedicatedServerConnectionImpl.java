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

import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import dev.horsingaround.gametest.harness.context.ClientGameTestContext;
import dev.horsingaround.gametest.harness.context.TestDedicatedServerConnection;
import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;

public class TestDedicatedServerConnectionImpl extends TestServerConnectionImpl implements TestDedicatedServerConnection {
	public TestDedicatedServerConnectionImpl(ClientGameTestContext context, TestServerContextImpl serverContext) {
		super(context, serverContext);
	}

	@Override
	public void close() {
		ThreadingImpl.checkOnGametestThread("close");

		context.runOnClient(client -> {
			if (client.level == null) {
				throw new AssertionError("Disconnected from server before closing the test server connection");
			}

			client.level.disconnect(Component.literal("Disconnecting"));
			client.disconnectWithSavingScreen();
		});

		context.waitFor(client -> client.level == null);
		context.waitTicks(2);
		context.setScreen(TitleScreen::new);
	}
}
