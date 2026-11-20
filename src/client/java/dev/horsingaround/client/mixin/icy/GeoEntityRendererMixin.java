//? if <1.21.2 {
/*package dev.horsingaround.client.mixin.icy;

import com.llamalad7.mixinextras.sugar.Local;
import dev.horsingaround.client.render.IcyCartDrawing;
import dev.horsingaround.ride.CartTrail;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Before 1.21.2 Icy's carts are drawn with GeckoLib 4, whose renderer animates a model's bones and draws them in one
// method, with nothing of Icy's in between: a cart rolling behind a horse this mod rides has its bones set there, once
// the animation has run (see IcyCartDrawing). Anything else GeckoLib draws passes straight through. Only with Icy's mod
// installed (IcyMixinPlugin).
@Pseudo
@Mixin(targets = "software.bernie.geckolib.renderer.GeoEntityRenderer", remap = false)
public abstract class GeoEntityRendererMixin {
	@Inject(
		method = "actuallyRender",
		at = @At(value = "FIELD", target = "Lsoftware/bernie/geckolib/renderer/GeoEntityRenderer;modelRenderTranslations:Lorg/joml/Matrix4f;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
		remap = false
	)
	private void horsingaround$cartBones(final CallbackInfo ci, @Local(argsOnly = true) final Entity drawn) {
		if (drawn instanceof CartTrail.Lean cart && cart.horsingaround$rolling()) {
			IcyCartDrawing.bones(cart, this);
		}
	}
}
*///?}
