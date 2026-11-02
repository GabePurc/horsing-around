package dev.horsingaround.net;

import dev.horsingaround.HorsingAround;
//? if >=1.20.5 {
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
 * Server to client, from a server with Icy's Better Horses installed too, on join (before the rules) and when the host
 * changes it: whose riding horses use there (see IcyCompat). A payload of its own so the rules keep their format.
 */
//? if >=1.20.5 {
public record StylePayload(boolean icyRides) implements CustomPacketPayload {
	public static final Type<StylePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "riding_style"));
	public static final StreamCodec<FriendlyByteBuf, StylePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, StylePayload::icyRides,
		StylePayload::new
	);

	@Override
	public Type<StylePayload> type() {
		return TYPE;
	}
}
//?} else {
/*public record StylePayload(boolean icyRides) implements FabricPacket {
	public static final PacketType<StylePayload> TYPE = PacketType.create(
		Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "riding_style"), buf -> new StylePayload(buf.readBoolean()));

	@Override
	public void write(final FriendlyByteBuf buf) {
		buf.writeBoolean(icyRides);
	}

	@Override
	public PacketType<StylePayload> getType() {
		return TYPE;
	}
}
*///?}
