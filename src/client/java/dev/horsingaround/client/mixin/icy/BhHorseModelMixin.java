package dev.horsingaround.client.mixin.icy;

import dev.horsingaround.client.render.RidePoseState;
import dev.horsingaround.client.render.SaddleMotion;
import dev.horsingaround.ride.RideState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Icy's horse models animate their own gaits: the body bobs and pitches with the stride, and banks into turns. Under
 * this mod's riding the horse banks with this mod's lean alone (Icy's on top of it leaned nearly twice as far), and the
 * stride's motion is Icy's alone: it is measured here, as under an animation pack, so the rider rides it and no
 * vanilla-timed motion is laid over it.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.client.render.BhHorseModel", remap = false)
public abstract class BhHorseModelMixin {
	/** Where the saddle sits: above and ahead of the body's pivot, model pixels at Icy's frame scale 1 (as on vanilla's). */
	private static final float SADDLE_UP = 8.0F;
	private static final float SADDLE_AHEAD = 6.0F;

	@Shadow(remap = false)
	@Final
	private ModelPart body;
	@Shadow(remap = false)
	private float frameScale;

	@Inject(method = "setupAnim(Licy/betterhorses/net/client/render/BhHorseRenderState;)V", at = @At("HEAD"))
	private void horsingaround$ourBank(@Coerce final Object state, final CallbackInfo ci) {
		if (((RidePoseState) state).horsingaround$rideState() != null) {
			((BhHorseRenderStateAccess) state).horsingaround$setBankWeight(0.0F);
		}
	}

	@Inject(method = "setupAnim(Licy/betterhorses/net/client/render/BhHorseRenderState;)V", at = @At("TAIL"))
	private void horsingaround$measureSaddle(@Coerce final Object state, final CallbackInfo ci) {
		final RideState ride = ((RidePoseState) state).horsingaround$rideState();
		if (ride != null) {
			final PartPose initial = this.body.getInitialPose();
			SaddleMotion.measure(ride, this.body, initial.x(), initial.y() - SADDLE_UP * this.frameScale, initial.z() - SADDLE_AHEAD * this.frameScale);
		}
	}
}
