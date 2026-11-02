package dev.horsingaround.client.mixin.icy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * What Icy's horse model draws from, see BhHorseModelMixin: how far it banks into a turn (0..1), how far its jumps tilt
 * its body, and how much its legs flail in the air (in a glide, and in a long fall).
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.client.render.BhHorseRenderState", remap = false)
public interface BhHorseRenderStateAccess {
	@Accessor(value = "bankWeight", remap = false)
	void horsingaround$setBankWeight(float bank);

	@Accessor(value = "jumpPitchScale", remap = false)
	void horsingaround$setJumpPitchScale(float scale);

	@Accessor(value = "panicWeight", remap = false)
	void horsingaround$setPanicWeight(float panic);

	@Accessor(value = "jumpFlail", remap = false)
	void horsingaround$setJumpFlail(float flail);
}
