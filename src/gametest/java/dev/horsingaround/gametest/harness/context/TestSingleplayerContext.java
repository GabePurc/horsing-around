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

package dev.horsingaround.gametest.harness.context;

import org.jetbrains.annotations.ApiStatus;

import dev.horsingaround.gametest.harness.world.TestWorldSave;

/**
 * Context for a client gametest containing various helpful functions while a singleplayer game is open.
 *
 * <p>Unless otherwise specified, methods in this class can only be called on the client gametest thread.
 */
@ApiStatus.NonExtendable
public interface TestSingleplayerContext extends AutoCloseable {
	/**
	 * Gets the handle for the world save.
	 *
	 * <p>This method can be called from any thread.
	 *
	 * @return The handle for the world save
	 */
	TestWorldSave getWorldSave();

	/**
	 * Gets the handle for the client level.
	 *
	 * <p>This method can be called from any thread.
	 *
	 * @return The handle for the client level
	 */
	TestServerConnection getConnection();

	/**
	 * Gets the handle for the integrated server.
	 *
	 * <p>This method can be called from any thread.
	 *
	 * @return The handle for the integrated server
	 */
	TestServerContext getServer();

	/**
	 * Closes the singleplayer world.
	 */
	@Override
	void close();
}
