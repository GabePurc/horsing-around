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

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.dedicated.DedicatedServer;

import dev.horsingaround.gametest.harness.context.ClientGameTestContext;
import dev.horsingaround.gametest.harness.context.TestDedicatedServerConnection;
import dev.horsingaround.gametest.harness.context.TestDedicatedServerContext;
import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;
import dev.horsingaround.gametest.harness.impl.util.ClientGameTestImpl;

public class TestDedicatedServerContextImpl extends TestServerContextImpl implements TestDedicatedServerContext {
	private final ClientGameTestContext context;

	public TestDedicatedServerContextImpl(ClientGameTestContext context, DedicatedServer server) {
		super(server);
		this.context = context;
	}

	@Override
	public TestDedicatedServerConnection connect() {
		ThreadingImpl.checkOnGametestThread("connect");

		context.runOnClient(client -> {
			//? if >=1.20.2 {
			final var serverInfo = new ServerData("localhost", getConnectionAddress(), ServerData.Type.OTHER);
			//?} else
			/*final var serverInfo = new ServerData("localhost", getConnectionAddress(), false);*/
			//? if >=1.20.5 {
			ConnectScreen.startConnecting(client.gui.screen(), client, ServerAddress.parseString(getConnectionAddress()), serverInfo, false, null);
			//?} else
			/*ConnectScreen.startConnecting(client.gui.screen(), client, ServerAddress.parseString(getConnectionAddress()), serverInfo, false);*/
		});

		ClientGameTestImpl.waitForWorldLoad(context);

		return new TestDedicatedServerConnectionImpl(context, this);
	}

	private String getConnectionAddress() {
		return "localhost:" + server.getPort();
	}

	@Override
	public void close() {
		ThreadingImpl.checkOnGametestThread("close");

		if (!ThreadingImpl.isServerRunning || !server.getRunningThread().isAlive()) {
			throw new AssertionError("Stopped the dedicated server before closing the dedicated server context");
		}

		server.halt(false);
		context.waitFor(client -> !ThreadingImpl.isServerRunning && !server.getRunningThread().isAlive());
	}
}
