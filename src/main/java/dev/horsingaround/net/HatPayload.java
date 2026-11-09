package dev.horsingaround.net;

import dev.horsingaround.HorsingAround;
//? if >=1.20.5 {
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} elif fabric {
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
*///?} else
/*import net.minecraft.network.FriendlyByteBuf;*/
import net.minecraft.resources.Identifier;

/** Client to server: this player's supporter hat (an RGB colour, or {@link #NONE} for no hat). */
//? if >=1.20.5 {
public record HatPayload(int color) implements CustomPacketPayload {
	public static final int NONE = -1;
	public static final Type<HatPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "hat"));
	public static final StreamCodec<FriendlyByteBuf, HatPayload> CODEC = StreamCodec.composite(ByteBufCodecs.INT, HatPayload::color, HatPayload::new);

	@Override
	public Type<HatPayload> type() {
		return TYPE;
	}
}
//?} elif fabric {
/*public record HatPayload(int color) implements FabricPacket {
	public static final int NONE = -1;
	public static final PacketType<HatPayload> TYPE = PacketType.create(Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "hat"), buf -> new HatPayload(buf.readInt()));

	@Override
	public void write(final FriendlyByteBuf buf) {
		buf.writeInt(color);
	}

	@Override
	public PacketType<HatPayload> getType() {
		return TYPE;
	}
}
*///?} else {
/*// (Forge: one channel carries every payload, forge/HorsingAroundForge; TYPE names it to Platform.canSend.)
public record HatPayload(int color) {
	public static final int NONE = -1;
	public static final Class<HatPayload> TYPE = HatPayload.class;

	public static HatPayload read(final FriendlyByteBuf buf) {
		return new HatPayload(buf.readInt());
	}

	public void write(final FriendlyByteBuf buf) {
		buf.writeInt(color);
	}
}
*///?}
