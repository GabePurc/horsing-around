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

package dev.horsingaround.gametest.harness.impl.util;

//? if >=1.20.5 {
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} elif fabric {
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
*///?}
//? if >=1.20.5 || fabric
import net.minecraft.resources.Identifier;

import dev.horsingaround.gametest.harness.impl.ClientGameTestSetup;

// (Before 1.20.5 there are no payload types: Fabric API's packet type, as the mod's own packets use there; on Forge a
// message of the test mod's channel, gametest/forge.)
//? if >=1.20.5 {
public enum GameTestSyncPayload implements CustomPacketPayload {
	INSTANCE;

	public static final Type<GameTestSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ClientGameTestSetup.MOD_ID, "gametest_sync"));
	public static final StreamCodec<ByteBuf, GameTestSyncPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
//?} elif fabric {
/*public enum GameTestSyncPayload implements FabricPacket {
	INSTANCE;

	public static final PacketType<GameTestSyncPayload> TYPE = PacketType.create(Identifier.fromNamespaceAndPath(ClientGameTestSetup.MOD_ID, "gametest_sync"), buf -> INSTANCE);

	@Override
	public void write(FriendlyByteBuf buf) {
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
*///?} else {
/*public enum GameTestSyncPayload {
	INSTANCE
}
*///?}
