package dev.horsingaround.client.mixin;

import dev.horsingaround.client.cosmetic.HatState;
import dev.horsingaround.client.cosmetic.Hats;
import dev.horsingaround.net.HatPayload;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A supporter wearing the cowboy hat: draw the hat, and not their helmet or whatever else is on their head. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void horsingaround$hat(final Avatar entity, final AvatarRenderState state, final float partialTicks, final CallbackInfo ci) {
		final int color = entity instanceof Player player ? Hats.colorFor(player) : HatPayload.NONE;
		((HatState) state).horsingaround$setHatColor(color);
		if (color != HatPayload.NONE) {
			state.headEquipment = ItemStack.EMPTY;
			state.headItem.clear();
			state.wornHeadType = null;
			state.wornHeadProfile = null;
		}
	}
}
