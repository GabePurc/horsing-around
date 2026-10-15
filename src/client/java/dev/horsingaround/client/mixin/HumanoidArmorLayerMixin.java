//? if <1.21.2 {
/*package dev.horsingaround.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.net.HatPayload;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A supporter wearing the cowboy hat: no helmet on their head (the head item is in CustomHeadLayerMixin), unless they are
// invisible (no hat is drawn then).
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
	@Inject(
		method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V",
		at = @At("HEAD"), cancellable = true
	)
	private void horsingaround$hat(
		final PoseStack poseStack, final MultiBufferSource buffers, final LivingEntity entity, final EquipmentSlot slot, final int lightCoords,
		final HumanoidModel<?> model, final CallbackInfo ci
	) {
		if (slot == EquipmentSlot.HEAD && entity instanceof Player player && !player.isInvisible() && Hats.colorFor(player) != HatPayload.NONE) {
			ci.cancel();
		}
	}
}
*///?}
