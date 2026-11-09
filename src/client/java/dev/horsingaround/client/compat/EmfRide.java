package dev.horsingaround.client.compat;

import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.client.render.AnimatedHorseModel;
import dev.horsingaround.client.render.PartData;
import dev.horsingaround.client.render.SaddleMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
//? if >=1.21 || =1.20.1
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/**
 * What the ride adds to a horse an animation pack (e.g. Fresh Animations) animates through Entity Model Features, once the
 * pack has run, on each layer's model as AbstractEquineModelMixin does on vanilla's: hooves on the ground, the tail, the neck
 * with the bridle after it, the stirrups. On the horse's own model it also records how the saddle moved, so the rider rides
 * the real animation instead of an imitation. One per model (or per variant of one), holding its parts; run by
 * EmfSaddleTracker, Entity Model Features' animation hook, or EmfAfterPack where it has none. Only loaded with EMF.
 */
public final class EmfRide {
	/** Top centre of the vanilla horse body, model pixels: where the saddle sits. */
	private static final float SADDLE_X = 0.0F;
	private static final float SADDLE_Y = 3.0F;
	private static final float SADDLE_Z = -1.0F;
	//? if >=1.21.2 && <1.21.4 {
	/*// Where 1.21.4 has a horse model's root, as it builds the horse 1.1 times larger (pixels).
	private static final float SCALED_ROOT_Y = 24.016F * (1.0F - 1.1F);
	*///?}

	private final @Nullable ModelPart leftFront;
	private final @Nullable ModelPart rightFront;
	private final @Nullable ModelPart leftHind;
	private final @Nullable ModelPart rightHind;
	/** The pack's own tail (Fresh Animations' "tail2") or vanilla's. */
	private final @Nullable ModelPart tail;
	/**
	 * The part that carries the head: the pack's own neck where it has one (Fresh Animations leaves vanilla's empty and
	 * animates "neck2" inside the body), else vanilla's.
	 */
	private final @Nullable ModelPart neck;
	private final @Nullable ModelPart body;
	/** The stirrups, on whichever model has them: Fresh Animations before 1.21.5 keeps them in the body's, later in a saddle model's. */
	private final @Nullable ModelPart leftStirrup;
	private final @Nullable ModelPart rightStirrup;
	/**
	 * The bridle's copy of the neck, where the model draws the bridle and reins itself: Fresh Animations before 1.21.5 keeps
	 * them in the body's model on "headpiece_neck", which it sets from "neck2" (later they're in a saddle model, on a "neck2"
	 * of its own, and this is left empty in the body's).
	 */
	private final @Nullable ModelPart bridleNeck;
	//? if >=1.21.2 && <1.21.4 {
	/*// Whether the pack puts this model's root where 1.21.4 has it (seen once, on land; see apply), and where the root was
	// last put back to (the pack hasn't set it again while it's still there).
	private boolean scaledRoot;
	private float rootPutBack = Float.NaN;
	*///?}

	/** Looks up the model's parts: once the pack has set them (on 1.20.2 to 1.20.6, those of the variant in force). */
	public EmfRide(final ModelPart root) {
		this.leftFront = vanilla(root, "left_front_leg");
		this.rightFront = vanilla(root, "right_front_leg");
		this.leftHind = vanilla(root, "left_hind_leg");
		this.rightHind = vanilla(root, "right_hind_leg");
		final ModelPart tail = AnimatedHorseModel.find(root, "tail2");
		this.tail = tail != null ? tail : vanilla(root, "tail");
		ModelPart neck = AnimatedHorseModel.find(root, "neck2");
		if (neck == null) {
			neck = vanilla(root, "head_parts");
		}
		this.neck = neck != null ? neck : vanilla(root, "neck");
		this.body = vanilla(root, "body");
		this.leftStirrup = AnimatedHorseModel.find(root, "left_saddle");
		this.rightStirrup = AnimatedHorseModel.find(root, "right_saddle");
		this.bridleNeck = AnimatedHorseModel.find(root, "headpiece_neck");
	}

	/**
	 * The one for a model, kept on its root (looked up the first time the model is seen; pack reloads create new models, and
	 * the old ones go with their parts).
	 */
	public static EmfRide of(final ModelPart root) {
		final PartData data = (PartData) (Object) root;
		if (data.horsingaround$rootParts() instanceof EmfRide known) {
			return known;
		}
		final EmfRide ride = new EmfRide(root);
		data.horsingaround$setRootParts(ride);
		return ride;
	}

	/** Whether the entity a model is drawn for is a horse the ride runs. */
	public static boolean managed(final @Nullable Object entity) {
		return entity instanceof RideStateHolder holder && holder.horsingaround$managed();
	}

	/**
	 * Once the pack has animated the model, each time it does.
	 *
	 * @param entity a horse the ride runs ({@link #managed})
	 * @param horseModel whether this is the horse's own model, not a layer's (armour, saddle)
	 */
	public void apply(final ModelPart root, final Object entity, final boolean horseModel) {
		final RideStateHolder holder = (RideStateHolder) entity;
		final RideState ride = holder.horsingaround$ride();
		final float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		//? if >=1.21.2 && <1.21.4 {
		/*// Fresh Animations' horse files for 1.21.2 on put the root where 1.21.4 has it, 2.4 pixels up as 1.21.4 builds the
		// horse's 1.1 size into the model; 1.21.2 and 1.21.3 scale the horse as they draw it and keep the root at 0, so there
		// the pack's horse floats a sixth of a block off the ground. Back down onto it, always once seen: the pack adds to the
		// root as the horse swims, and the horse mustn't pop up when it starts. (Not its skeleton and zombie horses, nor
		// donkeys and mules: their roots sit elsewhere.)
		if (root.yScale == 1.0F) {
			this.scaledRoot |= Math.abs(root.y - SCALED_ROOT_Y) < 0.01F;
			if (this.scaledRoot && root.y != this.rootPutBack) {
				root.y -= SCALED_ROOT_Y;
				this.rootPutBack = root.y;
			}
		}
		*///?}
		AnimatedHorseModel.legs(root, 16.0F / root.yScale, this.leftFront, this.rightFront, this.leftHind, this.rightHind, ride, partialTicks);
		AnimatedHorseModel.tail(this.tail, ride, partialTicks);
		// (Applied after the pack so its own neck logic isn't disturbed, and on every layer: each has its own neck, and
		// armour and bridle follow the head.)
		if (AnimatedHorseModel.neck(this.neck, ride, ((Entity) entity).tickCount + partialTicks, partialTicks) && horseModel) {
			ride.animatedShakeFrames++;
		}
		// (The pack has already copied the neck to the bridle's, before the neck got what the ride adds.)
		if (this.bridleNeck != null && this.neck != null) {
			this.bridleNeck.xRot = this.neck.xRot;
			this.bridleNeck.yRot = this.neck.yRot;
			this.bridleNeck.zRot = this.neck.zRot;
		}
		// (Stirrups meet a seated rider's feet; a saddled horse no one rides keeps the pack's own.)
		if (((Entity) entity).isVehicle()) {
			AnimatedHorseModel.stirrups(this.leftStirrup, this.rightStirrup, this.body);
		}
		if (!horseModel || this.body == null) {
			return;
		}
		SaddleMotion.measure(ride, this.body, SADDLE_X, SADDLE_Y, SADDLE_Z);
		ride.animatedWholeRear = true;
	}

	/** The model's own (vanilla) part named {@code name}, not the pack's. */
	private static @Nullable ModelPart vanilla(final ModelPart root, final String name) {
		//? if >=1.21 || =1.20.1 {
		return ((EMFModelPartRoot) root).getAllVanillaPartsByNameEMF().get(name);
		//?} else
		/*return AnimatedHorseModel.find(root, name, false);*/
	}
}
