package dev.horsingaround.net;

import dev.horsingaround.HorsingAround;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client on join (and when the host changes settings): this server runs the mod, and its rules. */
public record RulesPayload(int protocol, String version, boolean rideThroughLeaves) implements CustomPacketPayload {
	public static final Type<RulesPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "rules"));
	public static final StreamCodec<FriendlyByteBuf, RulesPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, RulesPayload::protocol,
		ByteBufCodecs.stringUtf8(64), RulesPayload::version,
		ByteBufCodecs.BOOL, RulesPayload::rideThroughLeaves,
		RulesPayload::new
	);

	@Override
	public Type<RulesPayload> type() {
		return TYPE;
	}
}
