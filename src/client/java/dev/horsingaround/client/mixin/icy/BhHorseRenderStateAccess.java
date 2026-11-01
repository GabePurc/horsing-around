package dev.horsingaround.client.mixin.icy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How far Icy's horse model banks into a turn (0..1), see BhHorseModelMixin. */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.client.render.BhHorseRenderState", remap = false)
public interface BhHorseRenderStateAccess {
	@Accessor(value = "bankWeight", remap = false)
	void horsingaround$setBankWeight(float bank);
}
