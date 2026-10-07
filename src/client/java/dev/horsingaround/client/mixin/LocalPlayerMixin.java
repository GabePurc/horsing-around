package dev.horsingaround.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Turns off vanilla's hold-to-charge riding jump; the ride controller jumps on press instead. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PlayerRideableJumping;getJumpCooldown()I"))
	private int horsingaround$noChargedJump(final int cooldown) {
		return ((LocalPlayer) (Object) this).getVehicle() instanceof RideStateHolder holder && holder.horsingaround$managed() ? 1 : cooldown;
	}
}
