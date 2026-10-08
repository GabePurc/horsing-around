package dev.horsingaround.net;

import dev.horsingaround.HorsingAround;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: this player's supporter hat (an RGB colour, or {@link #NONE} for no hat). */
public record HatPayload(int color) implements CustomPacketPayload {
	public static final int NONE = -1;
	public static final Type<HatPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "hat"));
	public static final StreamCodec<FriendlyByteBuf, HatPayload> CODEC = StreamCodec.composite(ByteBufCodecs.INT, HatPayload::color, HatPayload::new);

	@Override
	public Type<HatPayload> type() {
		return TYPE;
	}
}
