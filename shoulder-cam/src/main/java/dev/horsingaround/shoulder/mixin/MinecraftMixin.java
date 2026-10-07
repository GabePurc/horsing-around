package dev.horsingaround.shoulder.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.horsingaround.shoulder.ShoulderAim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Pick the block or entity under the centre crosshair: run vanilla's targeting with the eyes turned toward it. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@WrapOperation(
		method = "pick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;raycastHitResult(FLnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/HitResult;")
	)
	private HitResult horsingaroundShoulder$pickAtCrosshair(final LocalPlayer player, final float partialTicks, final Entity cameraEntity, final Operation<HitResult> original) {
		if (!ShoulderAim.isActive() || cameraEntity != player) {
			return original.call(player, partialTicks, cameraEntity);
		}
		final float yRot = player.getYRot();
		final float xRot = player.getXRot();
		final float yRotO = player.yRotO;
		final float xRotO = player.xRotO;
		player.setYRot(ShoulderAim.yaw());
		player.setXRot(ShoulderAim.pitch());
		player.yRotO = ShoulderAim.yaw();
		player.xRotO = ShoulderAim.pitch();
		try {
			return original.call(player, partialTicks, cameraEntity);
		} finally {
			player.setYRot(yRot);
			player.setXRot(xRot);
			player.yRotO = yRotO;
			player.xRotO = xRotO;
		}
	}
}
