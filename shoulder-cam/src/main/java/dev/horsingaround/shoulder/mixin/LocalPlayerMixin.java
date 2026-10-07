package dev.horsingaround.shoulder.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.horsingaround.shoulder.ShoulderAim;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The server aims projectiles (arrows, tridents, anything thrown) along the rotation the client reports, so report
 * the rotation toward the crosshair target. The player's own view is untouched.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@ModifyExpressionValue(method = {"sendPosition", "sendChanges"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
	private float horsingaroundShoulder$reportYaw(final float yaw) {
		return ShoulderAim.isActive() ? ShoulderAim.yaw() : yaw;
	}

	@ModifyExpressionValue(method = {"sendPosition", "sendChanges"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
	private float horsingaroundShoulder$reportPitch(final float pitch) {
		return ShoulderAim.isActive() ? ShoulderAim.pitch() : pitch;
	}
}
