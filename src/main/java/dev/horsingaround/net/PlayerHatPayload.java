package dev.horsingaround.net;

import dev.horsingaround.HorsingAround;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to clients: a player's supporter hat ({@link HatPayload#NONE} when they take it off or leave). Each client
 * only draws it if that player is on its own copy of the supporters list.
 */
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
