package dev.horsingaround.client.mixin.icy;

import dev.horsingaround.client.render.AnimatedHorseModel;
import dev.horsingaround.client.render.RidePoseState;
import dev.horsingaround.client.render.SaddleMotion;
import dev.horsingaround.ride.RideState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Icy's horse models animate their own gaits: the body bobs and pitches with the stride, and banks into turns. On a
 * horse this mod rides they are treated as an animation pack's are: the horse banks with this mod's lean alone (Icy's on
 * top of it leaned nearly twice as far), the stride's motion is Icy's alone (measured here, so the rider rides it and no
 * vanilla-timed motion is laid over it), the ride fits its legs (where the hooves stand, how long the legs are), and adds
 * what it adds to any self-animated model (AnimatedHorseModel: hooves on the ground, jumps, neck, tail, stirrups). Runs for each layer Icy draws (its tack layers animate their own
 * copy of the model), so they stay together.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.client.render.BhHorseModel", remap = false)
public abstract class BhHorseModelMixin {
	/** Where the saddle sits: this far above the body's pivot, model pixels (where Icy's own models seat the rider). */
	@Unique
	private static final float SADDLE_UP = 5.0F;

	@Shadow(remap = false)
	@Final
	private ModelPart body;
	@Shadow(remap = false)
	@Final
	private ModelPart neck;
	@Shadow(remap = false)
	@Final
	private ModelPart tail;
	@Shadow(remap = false)
	@Final
	private ModelPart frontLeftLeg;
	@Shadow(remap = false)
	@Final
	private ModelPart frontRightLeg;
	@Shadow(remap = false)
	@Final
	private ModelPart backLeftLeg;
	@Shadow(remap = false)
	@Final
	private ModelPart backRightLeg;
	@Shadow(remap = false)
	@Final
	private ModelPart rootPart;

	/** The saddle layer's stirrups (looked up the first time; other layers have none). */
	@Unique
	private @Nullable ModelPart horsingaround$leftStirrup;
	@Unique
	private @Nullable ModelPart horsingaround$rightStirrup;
	@Unique
	private boolean horsingaround$stirrupsLooked;

	/**
	 * Only the ride's lean banks the horse, and only the ride's jump tilt (nose up taking off, down to land) tilts it in a
	 * jump: Icy's jumps tilt its body too, which leant it back twice as far (user, 2026-11-02). Its jumps' legs, neck and
	 * head stay. And in the air its legs keep still: Icy's model paddles them in a panic all through a glide in its
	 * Glidewing Harness, and flails them in a long fall; they hold the jump's shape instead, as on any horse this mod rides.
	 */
	@Inject(method = "setupAnim(Licy/betterhorses/net/client/render/BhHorseRenderState;)V", at = @At("HEAD"))
	private void horsingaround$ourBank(@Coerce final Object state, final CallbackInfo ci) {
		if (((RidePoseState) state).horsingaround$rideState() != null) {
			final BhHorseRenderStateAccess icy = (BhHorseRenderStateAccess) state;
			icy.horsingaround$setBankWeight(0.0F);
			icy.horsingaround$setJumpPitchScale(0.0F);
			icy.horsingaround$setPanicWeight(0.0F);
			icy.horsingaround$setJumpFlail(0.0F);
		}
	}

	// (At every return: drawing the same model again in a frame, Icy's restores the pose it saved and returns early.)
	@Inject(method = "setupAnim(Licy/betterhorses/net/client/render/BhHorseRenderState;)V", at = @At("RETURN"))
	private void horsingaround$ride(@Coerce final Object state, final CallbackInfo ci) {
		final RideState ride = ((RidePoseState) state).horsingaround$rideState();
		if (ride == null) {
			return;
		}
		// Its jump clip moves the hind legs across the body and turns one about its length (play-test, 2026-11-06: they
		// went sideways for a moment in every jump). The ride's jump shape only swings legs fore and aft, so that stayed.
		final float clip = ((BhHorseRenderStateAccess) state).horsingaround$jumpLegWeight();
		if (clip > 0.0F) {
			AnimatedHorseModel.straighten(this.frontLeftLeg, clip);
			AnimatedHorseModel.straighten(this.frontRightLeg, clip);
			AnimatedHorseModel.straighten(this.backLeftLeg, clip);
			AnimatedHorseModel.straighten(this.backRightLeg, clip);
		}
		final PartPose initial = this.body.getInitialPose();
		SaddleMotion.measure(ride, this.body, initial.x(), initial.y() - SADDLE_UP, initial.z());
		ride.animatedWholeRear = false;
		AnimatedHorseModel.fitLegs(this.frontLeftLeg, this.backLeftLeg, ride);
		//? if >=1.21.2 {
		final float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		AnimatedHorseModel.legs(
			this.rootPart, 16.0F / this.rootPart.yScale, this.frontLeftLeg, this.frontRightLeg, this.backLeftLeg, this.backRightLeg, ride, partialTicks
		);
		AnimatedHorseModel.tail(this.tail, ride, partialTicks);
		if (AnimatedHorseModel.neck(this.neck, ride, ((net.minecraft.client.renderer.entity.state.LivingEntityRenderState) state).ageInTicks, partialTicks)) {
			ride.animatedShakeFrames++;
		}
		if (!this.horsingaround$stirrupsLooked) {
			this.horsingaround$leftStirrup = AnimatedHorseModel.find(this.rootPart, "left_saddle");
			this.horsingaround$rightStirrup = AnimatedHorseModel.find(this.rootPart, "right_saddle");
			this.horsingaround$stirrupsLooked = true;
		}
		AnimatedHorseModel.stirrups(this.horsingaround$leftStirrup, this.horsingaround$rightStirrup, this.body);
		//?}
	}
}
