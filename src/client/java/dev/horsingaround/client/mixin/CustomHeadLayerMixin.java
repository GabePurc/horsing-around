//? if <1.21.2 {
/*package dev.horsingaround.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.net.HatPayload;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A supporter wearing the cowboy hat: no skull or pumpkin on their head (the helmet is in HumanoidArmorLayerMixin). From
// 1.21.2 the render state of the player is emptied of both instead (see AvatarRendererMixin).
@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin {
	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
	private void horsingaround$hat(
		final PoseStack poseStack, final MultiBufferSource buffers, final int lightCoords, final LivingEntity entity, final float limbSwing,
		final float limbSwingAmount, final float partialTicks, final float ageInTicks, final float headYaw, final float headPitch, final CallbackInfo ci
	) {
		if (entity instanceof Player player && Hats.colorFor(player) != HatPayload.NONE) {
			ci.cancel();
		}
	}
}
*///?}
