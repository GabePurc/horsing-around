package dev.horsingaround.client.mixin;

import dev.horsingaround.client.cosmetic.HatState;
import dev.horsingaround.net.HatPayload;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements HatState {
	@Unique
	private int horsingaround$hatColor = HatPayload.NONE;

	@Override
	public int horsingaround$hatColor() {
		return this.horsingaround$hatColor;
	}

	@Override
	public void horsingaround$setHatColor(final int color) {
		this.horsingaround$hatColor = color;
	}
}
