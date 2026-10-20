package dev.horsingaround.client.mixin;

//? if >=1.21 {
import org.spongepowered.asm.mixin.injection.ModifyArg;
import dev.horsingaround.client.RideAim;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.spongepowered.asm.mixin.injection.At;
//?}
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Over the shoulder, using an item (a crossbow, anything thrown) reports the rotation toward what is under the
 * crosshair. Before 1.21 the packet carries no rotation: the server uses the one {@link LocalPlayerMixin} reports.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	//? if >=1.21 {
	@ModifyArg(
		method = "startPrediction",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V")
	)
	private Packet<?> horsingaround$aimUse(final Packet<?> packet) {
		if (!(packet instanceof ServerboundUseItemPacket use) || !RideAim.isActive()) {
			return packet;
		}
		//? if >=26.3 {
		return new ServerboundUseItemPacket(use.hand(), use.sequence(), RideAim.yaw(), RideAim.pitch());
		//?} else
		/*return new ServerboundUseItemPacket(use.getHand(), use.getSequence(), RideAim.yaw(), RideAim.pitch());*/
	}
	//?}
}
