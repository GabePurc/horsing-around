package dev.horsingaround.shoulder.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.horsingaround.shoulder.ShoulderAim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Using an item reports the rotation it was used with; point it at the crosshair target too. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@ModifyExpressionValue(method = "lambda$useItem$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
	private float horsingaroundShoulder$useYaw(final float yaw) {
		return ShoulderAim.isActive() ? ShoulderAim.yaw() : yaw;
	}

	@ModifyExpressionValue(method = "lambda$useItem$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getXRot()F"))
	private float horsingaroundShoulder$usePitch(final float pitch) {
		return ShoulderAim.isActive() ? ShoulderAim.pitch() : pitch;
	}
}
