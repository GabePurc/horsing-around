package dev.horsingaround.net;

import dev.horsingaround.HorsingAround;
import java.util.UUID;
//? if >=1.20.5 {
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
*///?}
import net.minecraft.resources.Identifier;

/**
 * Server to clients: a player's supporter hat ({@link HatPayload#NONE} when they take it off or leave). Each client
 * only draws it if that player is on its own copy of the supporters list.
 */
//? if >=1.20.5 {
public record PlayerHatPayload(UUID player, int color) implements CustomPacketPayload {
	public static final Type<PlayerHatPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "player_hat"));
	public static final StreamCodec<FriendlyByteBuf, PlayerHatPayload> CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, PlayerHatPayload::player,
		ByteBufCodecs.INT, PlayerHatPayload::color,
		PlayerHatPayload::new
	);

	@Override
	public Type<PlayerHatPayload> type() {
		return TYPE;
	}
}
//?} else {
/*public record PlayerHatPayload(UUID player, int color) implements FabricPacket {
	public static final PacketType<PlayerHatPayload> TYPE = PacketType.create(
		Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "player_hat"), buf -> new PlayerHatPayload(buf.readUUID(), buf.readInt()));

	@Override
	public void write(final FriendlyByteBuf buf) {
		buf.writeUUID(player);
		buf.writeInt(color);
	}

	@Override
	public PacketType<PlayerHatPayload> getType() {
		return TYPE;
	}
}
*///?}
