//? if >=1.21 {
package dev.horsingaround.client.compat;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.client.render.AnimatedHorseModel;
import dev.horsingaround.client.render.PartData;
import dev.horsingaround.client.render.SaddleMotion;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/**
 * Reads the horse's body after Entity Model Features has animated it (e.g. Fresh Animations) and records how the
 * saddle moved, so the rider can ride the real animation instead of an imitation. Only loaded when EMF is present.
 */
public final class EmfSaddleTracker extends EMFAnimationApi.EMFAnimationHook {
	/** Top centre of the vanilla horse body, model pixels: where the saddle sits. */
	private static final float SADDLE_X = 0.0F;
	private static final float SADDLE_Y = 3.0F;
	private static final float SADDLE_Z = -1.0F;

	public static void register() {
		try {
			EMFAnimationApi.registerAnimationHook(new EmfSaddleTracker());
		} catch (final RuntimeException e) {
			throw e;
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(HorsingAround.MOD_ID);
	/** Set once a call into Entity Model Features fails to link. */
	private boolean broken;

	private static final String[] LEG_NAMES = {"left_front_leg", "right_front_leg", "left_hind_leg", "right_hind_leg"};

	/**
	 * The parts this hook moves, kept on the model's root (looked up the first time a model is seen; pack reloads create
	 * new models, and the old ones go with their parts).
	 */
	private static Parts parts(final EMFModelPartRoot root) {
		final PartData data = (PartData) (Object) root;
		if (data.horsingaround$rootParts() instanceof Parts known) {
			return known;
		}
		final Parts parts = new Parts(root);
		data.horsingaround$setRootParts(parts);
		return parts;
	}

	/** One model's parts. */
	private static final class Parts {
		/** Front left, front right, hind left, hind right (null where the model has none). */
		private static final int LEGS = 0;
		/** The pack's own tail (Fresh Animations' "tail2") or vanilla's. */
		private static final int TAIL = 4;
		/**
		 * The part that carries the head: the pack's own neck where it has one (Fresh Animations leaves vanilla's empty and
		 * animates "neck2" inside the body), else vanilla's.
		 */
		private static final int NECK = 5;
		/** The body and the stirrups (on a saddle model). */
		private static final int BODY = 6;
		private static final int LEFT_STIRRUP = 7;
		private static final int RIGHT_STIRRUP = 8;
		/**
		 * The bridle's copy of the neck, where the model draws the bridle and reins itself: Fresh Animations before 1.21.5
		 * keeps them in the body's model on "headpiece_neck", which it sets from "neck2" (later they're in a saddle model, on
		 * a "neck2" of its own, and this is left empty in the body's).
		 */
		private static final int BRIDLE_NECK = 9;

		private final ModelPart[] parts = new ModelPart[10];

		Parts(final EMFModelPartRoot root) {
			final Map<String, ? extends ModelPart> vanilla = root.getAllVanillaPartsByNameEMF();
			for (int i = 0; i < LEG_NAMES.length; i++) {
				this.set(LEGS + i, vanilla.get(LEG_NAMES[i]));
			}
			final ModelPart tail = AnimatedHorseModel.find(root, "tail2");
			this.set(TAIL, tail != null ? tail : vanilla.get("tail"));
			ModelPart neck = AnimatedHorseModel.find(root, "neck2");
			if (neck == null) {
				neck = vanilla.get("head_parts");
			}
			this.set(NECK, neck != null ? neck : vanilla.get("neck"));
			this.set(BODY, vanilla.get("body"));
			this.set(LEFT_STIRRUP, AnimatedHorseModel.find(root, "left_saddle"));
			this.set(RIGHT_STIRRUP, AnimatedHorseModel.find(root, "right_saddle"));
			this.set(BRIDLE_NECK, AnimatedHorseModel.find(root, "headpiece_neck"));
		}

		private void set(final int index, final ModelPart part) {
			this.parts[index] = part;
		}

		ModelPart get(final int index) {
			return this.parts[index];
		}
	}

	@Override
	public void onAnimationEnd(final AnimationContext context, final boolean unused) {
		if (this.broken) {
			return;
		}
		try {
			this.animated(context);
		} catch (final LinkageError e) {
			// (A release of Entity Model Features without something this was built against: stand aside for good, and the
			// horses and riders go on as without an animation pack.)
			this.broken = true;
			LOGGER.warn("Entity Model Features changed under the animation hook; riders follow a generated gait motion instead", e);
		}
	}

	private void animated(final AnimationContext context) {
		// Entity Model Features animates more than entities (with Fresh Animations, a shulker box drawn as an item icon) and
		// then hands every hook no state at all: nothing of ours to do there.
		final EMFEntityRenderState state = context.activeState();
		if (state == null || !(state.emfEntity() instanceof RideStateHolder holder) || !holder.horsingaround$managed()) {
			return;
		}
		final EMFModelPartRoot root = context.animatingModelRoot();
		final RideState ride = holder.horsingaround$ride();
		final float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		final Parts parts = parts(root);
		AnimatedHorseModel.legs(
			root, 16.0F / root.yScale, parts.get(Parts.LEGS), parts.get(Parts.LEGS + 1), parts.get(Parts.LEGS + 2), parts.get(Parts.LEGS + 3), ride,
			partialTicks
		);
		AnimatedHorseModel.tail(parts.get(Parts.TAIL), ride, partialTicks);
		// (Applied after the pack so its own neck logic isn't disturbed, and on every layer: each has its own neck, and
		// armour and bridle follow the head.)
		final ModelPart neck = parts.get(Parts.NECK);
		if (AnimatedHorseModel.neck(neck, ride, ((Entity) holder).tickCount + partialTicks, partialTicks) && root.isMainModel) {
			ride.animatedShakeFrames++;
		}
		// (The pack has already copied the neck to the bridle's, before the neck got what the ride adds.)
		final ModelPart bridleNeck = parts.get(Parts.BRIDLE_NECK);
		if (bridleNeck != null && neck != null) {
			bridleNeck.xRot = neck.xRot;
			bridleNeck.yRot = neck.yRot;
			bridleNeck.zRot = neck.zRot;
		}
		final ModelPart body = parts.get(Parts.BODY);
		// (Stirrups meet a seated rider's feet; a saddled horse no one rides keeps the pack's own. On whichever model has
		// them: Fresh Animations before 1.21.5 keeps them in the body's, later in a saddle model, leaving the body's empty.)
		if (((Entity) holder).isVehicle()) {
			AnimatedHorseModel.stirrups(parts.get(Parts.LEFT_STIRRUP), parts.get(Parts.RIGHT_STIRRUP), body);
		}
		if (!root.isMainModel || body == null) {
			return;
		}
		SaddleMotion.measure(ride, body, SADDLE_X, SADDLE_Y, SADDLE_Z);
	}
}
//?}
