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

import java.util.Objects;
import java.util.UUID;

import com.google.common.base.Preconditions;

import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import dev.horsingaround.gametest.harness.context.ClientGameTestContext;
import dev.horsingaround.gametest.harness.context.TestServerConnection;
import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;
import dev.horsingaround.gametest.harness.impl.util.GameTestSyncPayload;
import dev.horsingaround.gametest.harness.mixin.ClientLevelAccessor;

public class TestServerConnectionImpl implements TestServerConnection {
	protected final ClientGameTestContext context;
	private final TestServerContextImpl serverContext;

	public TestServerConnectionImpl(ClientGameTestContext context, TestServerContextImpl serverContext) {
		this.context = context;
		this.serverContext = serverContext;
	}

	@Override
	public int waitForChunksDownload(int timeout) {
		ThreadingImpl.checkOnGametestThread("waitForChunksDownload");

		return context.waitFor(TestServerConnectionImpl::areChunksLoaded, timeout);
	}

	@Override
	public int waitForChunksRender(boolean waitForDownload, int timeout) {
		ThreadingImpl.checkOnGametestThread("waitForChunksRender");

		try {
			return context.waitFor(client -> (!waitForDownload || areChunksLoaded(client)) && areChunksRendered(client), timeout);
		} catch (AssertionError e) {
			// (Say which part never finished.)
			throw new AssertionError("Timed out waiting for chunks to render: " + context.computeOnClient(client -> {
				int missing = 0;
				int renderDistance = client.options.getEffectiveRenderDistance();
				int viewCenterX = SectionPos.blockToSectionCoord(client.player.getBlockX());
				int viewCenterZ = SectionPos.blockToSectionCoord(client.player.getBlockZ());
				for (int dz = -renderDistance; dz <= renderDistance; dz++) {
					for (int dx = -renderDistance; dx <= renderDistance; dx++) {
						if (dx * dx + dz * dz <= renderDistance * renderDistance && client.level.getChunk(viewCenterX + dx, viewCenterZ + dz, ChunkStatus.FULL, false) == null) {
							missing++;
						}
					}
				}
				//? if >=1.20.2 {
				boolean sections = client.levelRenderer.hasRenderedAllSections();
				//?} else
				/*boolean sections = client.levelRenderer.hasRenderedAllChunks();*/
				return missing + " chunks missing within " + renderDistance + " of " + viewCenterX + "," + viewCenterZ
					+ ", light updates queued " + ((ClientLevelAccessor) client.level).getLightUpdateQueue().size() + ", sections compiled " + sections;
			}), e);
		}
	}

	private static boolean areChunksLoaded(Minecraft client) {
		int renderDistance = client.options.getEffectiveRenderDistance();
		ClientLevel level = Objects.requireNonNull(client.level);
		// (Round the player's chunk, which the client's view centre follows: ClientChunkCache.Storage isn't public.)
		int viewCenterX = SectionPos.blockToSectionCoord(Objects.requireNonNull(client.player).getBlockX());
		int viewCenterZ = SectionPos.blockToSectionCoord(client.player.getBlockZ());

		// (Within the view distance as a circle: some versions' servers never send the square's corners.)
		for (int dz = -renderDistance; dz <= renderDistance; dz++) {
			for (int dx = -renderDistance; dx <= renderDistance; dx++) {
				if (dx * dx + dz * dz <= renderDistance * renderDistance && level.getChunk(viewCenterX + dx, viewCenterZ + dz, ChunkStatus.FULL, false) == null) {
					return false;
				}
			}
		}

		return true;
	}

	private static boolean areChunksRendered(Minecraft client) {
		ClientLevel level = Objects.requireNonNull(client.level);
		//? if >=1.20.2 {
		return ((ClientLevelAccessor) level).getLightUpdateQueue().isEmpty() && client.levelRenderer.hasRenderedAllSections();
		//?} else
		/*return ((ClientLevelAccessor) level).getLightUpdateQueue().isEmpty() && client.levelRenderer.hasRenderedAllChunks();*/
	}

	@Override
	public void waitForClientboundPackets() {
		ThreadingImpl.checkOnGametestThread("waitForClientboundPackets");

		serverContext.runOnServer(server -> dev.horsingaround.gametest.TestEnv.sendSync(getServerPlayer()));

		try {
			context.waitFor(ignored -> ThreadingImpl.networkSyncReceived);
		} finally {
			ThreadingImpl.networkSyncReceived = false;
		}
	}

	@Override
	public void waitForServerboundPackets() {
		ThreadingImpl.checkOnGametestThread("waitForServerboundPackets");

		context.runOnClient(ignored -> dev.horsingaround.gametest.TestEnv.sendToServer(GameTestSyncPayload.INSTANCE));

		try {
			serverContext.waitFor(ignored -> ThreadingImpl.networkSyncReceived);
		} finally {
			ThreadingImpl.networkSyncReceived = false;
		}
	}

	@Override
	public void waitForClientboundEntityUpdates(EntityType<?> entityType, EntityType<?>... moreEntityTypes) {
		ThreadingImpl.checkOnGametestThread("waitForClientboundEntityUpdates");
		Preconditions.checkNotNull(entityType, "entityType");
		Preconditions.checkNotNull(moreEntityTypes, "moreEntityTypes");

		for (int i = 0; i < moreEntityTypes.length; i++) {
			Preconditions.checkNotNull(moreEntityTypes[i], "moreEntityTypes[" + i + "]");
		}

		int maxUpdateInterval = Math.max(0, entityType.updateInterval());

		for (EntityType<?> et : moreEntityTypes) {
			maxUpdateInterval = Math.max(maxUpdateInterval, et.updateInterval());
		}

		context.waitTicks(maxUpdateInterval);
		waitForClientboundPackets();
	}

	@Override
	public LocalPlayer getClientPlayer() {
		ThreadingImpl.checkOnClientThread("getClientPlayer");

		return Objects.requireNonNull(Minecraft.getInstance().player, "Not in world!");
	}

	@Override
	public ClientLevel getClientLevel() {
		ThreadingImpl.checkOnClientThread("getClientLevel");

		return Objects.requireNonNull(Minecraft.getInstance().level, "Not in world!");
	}

	@Override
	public ServerPlayer getServerPlayer() {
		ThreadingImpl.checkOnServerThread("getServerPlayer", serverContext.server);

		//? if >=1.21.9 {
		UUID uuid = Minecraft.getInstance().getGameProfile().id();
		//?} elif >=1.20.2 {
		/*UUID uuid = Minecraft.getInstance().getGameProfile().getId();
		*///?} else
		/*UUID uuid = Minecraft.getInstance().getUser().getProfileId();*/
		return Objects.requireNonNull(serverContext.server.getPlayerList().getPlayer(uuid), "No corresponding player on server!");
	}

	@Override
	public ServerLevel getServerLevel() {
		ThreadingImpl.checkOnServerThread("getServerLevel", serverContext.server);

		ClientLevel clientLevel = Objects.requireNonNull(Minecraft.getInstance().level, "Not in world!");
		return Objects.requireNonNull(serverContext.server.getLevel(clientLevel.dimension()), "No corresponding level on server!");
	}
}
