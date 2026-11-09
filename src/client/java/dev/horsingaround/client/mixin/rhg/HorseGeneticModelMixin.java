package dev.horsingaround.client.mixin.rhg;

import dev.horsingaround.client.render.AnimatedHorseModel;
import dev.horsingaround.client.render.HorseModelPose;
import dev.horsingaround.client.render.LegacyRenderState;
import dev.horsingaround.client.render.Legs;
import dev.horsingaround.client.render.RidePoseState;
import dev.horsingaround.ride.RideState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sekelsta.horse_colors.entity.AbstractHorseGenetic;

/**
 * Realistic Horse Genetics' model is built as vanilla's is, with legs of three parts (thigh or upper leg, shin, hoof),
 * a neck and head side by side, and a tail for horses and one for donkeys; its renderer draws each horse at the size
 * its genes give. It gets what the vanilla model gets (HorseModelPose), its legs measured whole, thigh to hoof, and the
 * ride is fitted to them (where its hooves stand and how long its legs are, at the horse's size), so the body tilts on
 * steps and slopes about its own legs. Runs for each layer it draws (body, saddle, armour), so they stay together.
 *
 * <p>Only built for Forge for 1.20.1, its version. Its own names aren't remapped; a method of its own that overrides the
 * game's keeps its own name too (only the bridge to it is the game's).
 */
@Pseudo
@Mixin(targets = "sekelsta.horse_colors.client.renderer.HorseGeneticModel", remap = false)
public abstract class HorseGeneticModelMixin {
	/** The vanilla horse is drawn at 1.1 times its model's size (its renderer's scale), which the ride's numbers are for. */
	@Unique
	private static final float VANILLA_SCALE = 1.1F;

	@Shadow(remap = false)
	@Final
	private ModelPart neck;
	@Shadow(remap = false)
	@Final
	private ModelPart head;
	@Shadow(remap = false)
	@Final
	private ModelPart frontLeftLeg;
	@Shadow(remap = false)
	@Final
	private ModelPart frontRightLeg;
	@Shadow(remap = false)
	@Final
	private ModelPart backLeftThigh;
	@Shadow(remap = false)
	@Final
	private ModelPart backRightThigh;
	@Shadow(remap = false)
	@Final
	private ModelPart tailBase;
	@Shadow(remap = false)
	@Final
	private ModelPart tailThin;

	// One model draws every horse, and it sets only some of the parts the ride moves each frame (not where the hind legs sit,
	// nor any part's roll): back to the model's own pose before it poses itself, so nothing carries over to the next horse.
	@Inject(method = "prepareMobModel(Lnet/minecraft/world/entity/animal/horse/AbstractHorse;FFF)V", at = @At("HEAD"), remap = false)
	private void horsingaround$resetPose(final AbstractHorse horse, final float limbSwing, final float limbSwingAmount, final float partialTicks, final CallbackInfo ci) {
		this.frontLeftLeg.resetPose();
		this.frontRightLeg.resetPose();
		this.backLeftThigh.resetPose();
		this.backRightThigh.resetPose();
		this.neck.zRot = this.neck.getInitialPose().zRot();
		this.head.zRot = this.head.getInitialPose().zRot();
	}

	// (Its pose is all set in prepareMobModel, which the game calls first; setupAnim only shows and hides parts.)
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/animal/horse/AbstractHorse;FFFFF)V", at = @At("TAIL"), remap = false)
	private void horsingaround$ride(
		final AbstractHorse horse, final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float headYaw, final float headPitch,
		final CallbackInfo ci
	) {
		final Object state = LegacyRenderState.of(horse);
		// (A foal can't be ridden, and is drawn foal-shaped about other points.)
		if (state == null || ((EntityModel<?>) (Object) this).young) {
			return;
		}
		final RidePoseState pose = (RidePoseState) state;
		final RideState ride = pose.horsingaround$rideState();
		Legs.chain(this.frontLeftLeg);
		Legs.chain(this.frontRightLeg);
		Legs.chain(this.backLeftThigh);
		Legs.chain(this.backRightThigh);
		if (ride != null) {
			final float size = horse instanceof AbstractHorseGenetic genetic ? genetic.getProportionalAgeScale() : VANILLA_SCALE;
			AnimatedHorseModel.fitLegs(this.frontLeftLeg, this.backLeftThigh, ride, size / VANILLA_SCALE);
		}
		// (The head is beside the neck, not on it, posed as the neck is: it gets the same.)
		HorseModelPose.neck(this.neck, pose);
		HorseModelPose.neck(this.head, pose);
		HorseModelPose.legs(pose, this.frontLeftLeg, this.frontRightLeg, this.backLeftThigh, this.backRightThigh, LegacyRenderState.horseRoot(false));
		HorseModelPose.tail(this.tailBase, pose);
		HorseModelPose.tail(this.tailThin, pose);
	}
}
