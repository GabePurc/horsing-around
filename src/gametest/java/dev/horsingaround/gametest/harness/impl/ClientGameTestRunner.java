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

package dev.horsingaround.gametest.harness.impl;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import dev.horsingaround.gametest.harness.ClientGameTest;
import dev.horsingaround.gametest.harness.context.ClientGameTestContext;
import dev.horsingaround.gametest.harness.impl.context.ClientGameTestContextImpl;
import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;
import dev.horsingaround.gametest.harness.impl.util.WindowHooks;

public class ClientGameTestRunner {
	public static ClientGameTest currentlyRunningGameTest = null;

	public static void start() {
		// make the game think the window is focused
		((WindowHooks) (Object) Minecraft.getInstance().getWindow()).fabric_focus();

		List<ClientGameTest> gameTests = dev.horsingaround.gametest.Tests.all();

		ThreadingImpl.runTestThread(() -> {
			ClientGameTestContextImpl context = new ClientGameTestContextImpl();

			for (ClientGameTest gameTest : gameTests) {
				currentlyRunningGameTest = gameTest;

				try {
					setupInitialGameTestState(context);
					gameTest.runTest(context);
					setupAndCheckFinalGameTestState(context);
				} finally {
					currentlyRunningGameTest = null;
				}
			}
		});
	}

	private static void setupInitialGameTestState(ClientGameTestContext context) {
		context.restoreDefaultGameOptions();
	}

	private static void setupAndCheckFinalGameTestState(ClientGameTestContextImpl context) {
		context.getInput().clearKeysDown();
		context.runOnClient(client -> ((WindowHooks) (Object) client.getWindow()).fabric_resetSize());
		context.getInput().setCursorPos(context.computeOnClient(client -> client.getWindow().getScreenWidth()) * 0.5, context.computeOnClient(client -> client.getWindow().getScreenHeight()) * 0.5);

		if (ThreadingImpl.isServerRunning) {
			throw new AssertionError("Client gametest %s finished while a server is still running".formatted(currentlyRunningGameTest.getClass().getName()));
		}

		context.runOnClient(client -> {
			if (client.level != null) {
				throw new AssertionError("Client gametest %s finished while still connected to a server".formatted(currentlyRunningGameTest.getClass().getName()));
			}

			if (!(client.gui.screen() instanceof TitleScreen)) {
				throw new AssertionError("Client gametest %s did not finish on the title screen. Current screen %s".formatted(currentlyRunningGameTest.getClass().getName(), client.gui.screen().getClass().getName()));
			}
		});
	}
}
