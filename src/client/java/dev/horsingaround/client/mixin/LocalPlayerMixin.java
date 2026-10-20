package dev.horsingaround.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.horsingaround.client.RideAim;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Turns off vanilla's hold-to-charge riding jump; the ride controller jumps on press instead. Over the shoulder, reports
 * the rotation toward what is under the crosshair (the server aims arrows and anything thrown along it); the rider's own
 * view is untouched. Riding, the rotation goes out from {@code tick} (from {@code sendChanges} in 26.3).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PlayerRideableJumping;getJumpCooldown()I"), require = 1)
	private int horsingaround$noChargedJump(final int cooldown) {
		return RideStateHolder.managedRide(((LocalPlayer) (Object) this).getVehicle()) != null ? 1 : cooldown;
	}

	//? if >=26.3 {
	@ModifyExpressionValue(method = {"sendPosition", "sendChanges"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
	//?} else
	/*@ModifyExpressionValue(method = {"sendPosition", "tick"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))*/
	private float horsingaround$reportYaw(final float yaw) {
		return RideAim.isActive() ? RideAim.yaw() : yaw;
	}

	//? if >=26.3 {
	@ModifyExpressionValue(method = {"sendPosition", "sendChanges"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
	//?} else
	/*@ModifyExpressionValue(method = {"sendPosition", "tick"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))*/
	private float horsingaround$reportPitch(final float pitch) {
		return RideAim.isActive() ? RideAim.pitch() : pitch;
	}
}
