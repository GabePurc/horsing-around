//? if >=1.21 {
package dev.horsingaround.client.compat;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.client.mixin.ModelPartAccessor;
import dev.horsingaround.client.render.AirLegs;
import dev.horsingaround.client.render.GroundLegs;
import dev.horsingaround.client.render.SaddleMotion;
import dev.horsingaround.ride.RideTuning;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
	/** The rest-pose filter's time constant, seconds. */
	private static final float BASELINE_SECONDS = 0.8F;

	private final Quaternionf rotation = new Quaternionf();
	private final Vector3f current = new Vector3f();
	private final Vector3f rest = new Vector3f();
	private final float[] phase = new float[1];

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

	/** Frames the stirrups were held (for tests). */
	public static int stirrupFrames;

	/** The parts this hook moves, per model (looked up the first time a model is seen; pack reloads create new models). */
	private final Map<ModelPart, Parts> models = new WeakHashMap<>();
	private static final String[] LEG_NAMES = {"left_front_leg", "right_front_leg", "left_hind_leg", "right_hind_leg"};

	private Parts parts(final EMFModelPartRoot root) {
		Parts parts = this.models.get(root);
		if (parts == null) {
			parts = new Parts(root);
			this.models.put(root, parts);
		}
		return parts;
	}

	/**
	 * One model's parts. Held weakly: every Entity Model Features part points back at its model, so a part held here
	 * would keep the model (the map's key) alive, and with it every model a resource reload replaced.
	 */
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

		private final WeakReference<?>[] parts = new WeakReference<?>[9];

		Parts(final EMFModelPartRoot root) {
			final Map<String, ? extends ModelPart> vanilla = root.getAllVanillaPartsByNameEMF();
			for (int i = 0; i < LEG_NAMES.length; i++) {
				this.set(LEGS + i, vanilla.get(LEG_NAMES[i]));
			}
			final ModelPart tail = find(root, "tail2");
			this.set(TAIL, tail != null ? tail : vanilla.get("tail"));
			ModelPart neck = find(root, "neck2");
			if (neck == null) {
				neck = vanilla.get("head_parts");
			}
			this.set(NECK, neck != null ? neck : vanilla.get("neck"));
			this.set(BODY, vanilla.get("body"));
			this.set(LEFT_STIRRUP, find(root, "left_saddle"));
			this.set(RIGHT_STIRRUP, find(root, "right_saddle"));
		}

		private void set(final int index, final ModelPart part) {
			this.parts[index] = part == null ? null : new WeakReference<>(part);
		}

		ModelPart get(final int index) {
			final WeakReference<?> part = this.parts[index];
			return part == null ? null : (ModelPart) part.get();
		}
	}

	/** The tail swings with the horse's motion, on top of the pack's own tail animation. */
	private static void swingTail(final Parts parts, final RideState ride, final float partialTicks) {
		final float lift = ride.tailLift(partialTicks);
		final ModelPart tail = parts.get(Parts.TAIL);
		if (lift != 0.0F && tail != null) {
			tail.xRot += lift;
		}
	}

	/**
	 * Legs after the pack has animated them: hooves on the ground on steps and slopes (upright against the body's tilt,
	 * the pair on higher ground folding onto it), and in the air, the shape of a jump. On every layer (body, saddle,
	 * armour) so they stay together.
	 */
	private static void stepLegs(final EMFModelPartRoot root, final Parts parts, final RideState ride, final float partialTicks) {
		final float tilt = ride.pitch(partialTicks) * Mth.DEG_TO_RAD;
		final float fore = ride.foreLeg(partialTicks);
		final float hind = ride.hindLeg(partialTicks);
		final float air = ride.airLegs(partialTicks);
		final ModelPart leftFront = parts.get(Parts.LEGS);
		final ModelPart rightFront = parts.get(Parts.LEGS + 1);
		final ModelPart leftHind = parts.get(Parts.LEGS + 2);
		final ModelPart rightHind = parts.get(Parts.LEGS + 3);
		final boolean footing = GroundLegs.pose(
			leftFront, rightFront, leftHind, rightHind, tilt, fore, hind, 16.0F / root.yScale, ride, root, air < RideTuning.AIR_GROUNDED || !ride.leapt()
		);
		// (A step down is a short fall, not a jump: with the hooves finding their ground, no jump shape. Landing a jump,
		// the jump's shape eases out, and any hoof it leaves in the ground comes up onto it.)
		if (air <= 0.0F || footing && !ride.leapLegs()) {
			return;
		}
		AirLegs.pose(leftFront, rightFront, leftHind, rightHind, air, ride.airRise(partialTicks));
		GroundLegs.floor(leftFront, rightFront, leftHind, rightHind, 16.0F / root.yScale, ride, root);
	}

	/**
	 * Swing the pack's stirrups forward to meet the seated leg and hold them there against the body's pitch, so the
	 * feet (which the rider pose holds still against the horse) stay in them.
	 */
	private static void holdStirrups(final Parts parts) {
		final ModelPart left = parts.get(Parts.LEFT_STIRRUP);
		final ModelPart right = parts.get(Parts.RIGHT_STIRRUP);
		if (left == null && right == null) {
			return;
		}
		final ModelPart body = parts.get(Parts.BODY);
		final float swing = -RideTuning.STIRRUP_SWING - (body == null ? 0.0F : body.xRot - body.getInitialPose().xRot());
		stirrupFrames++;
		if (left != null) {
			left.xRot = swing;
			left.zRot = 0.0F;
		}
		if (right != null) {
			right.xRot = swing;
			right.zRot = 0.0F;
		}
	}

	private static ModelPart find(final ModelPart part, final String name) {
		for (final Map.Entry<String, ModelPart> child : ((ModelPartAccessor) (Object) part).horsingaround$children().entrySet()) {
			if (child.getKey().equals(name) || child.getKey().endsWith("_" + name) && child.getKey().startsWith("EMF")) {
				return child.getValue();
			}
			final ModelPart found = find(child.getValue(), name);
			if (found != null) {
				return found;
			}
		}
		return null;
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
		final Parts parts = this.parts(root);
		stepLegs(root, parts, ride, partialTicks);
		swingTail(parts, ride, partialTicks);
		// Climbing, the neck reaches forward, and further when the rider would be in the way of the head; and a horse that
		// just ran out of stamina tosses its head. Applied after the pack so its own neck logic isn't disturbed, and on
		// every layer (each has its own neck: armour and bridle follow the head).
		final ModelPart neckPart = parts.get(Parts.NECK);
		if (neckPart != null) {
			neckPart.xRot += RideTuning.neckCounter((ride.pitch(partialTicks) + ride.jumpPitch(partialTicks)) * Mth.DEG_TO_RAD) + ride.neckReach;
			final float shake = ride.headShake(((Entity) holder).tickCount + partialTicks, this.phase);
			if (shake > 0.0F) {
				neckPart.yRot += -Mth.sin(this.phase[0]) * RideTuning.HEAD_SHAKE_YAW * shake;
				neckPart.zRot += Mth.cos(this.phase[0]) * RideTuning.HEAD_SHAKE_ROLL * shake;
				if (root.isMainModel) {
					ride.animatedShakeFrames++;
				}
			}
		}
		if (!root.isMainModel) {
			// (Stirrups meet a seated rider's feet; a saddled horse no one rides keeps the pack's own.)
			if (((Entity) holder).isVehicle()) {
				holdStirrups(parts);
			}
			return;
		}
		final ModelPart body = parts.get(Parts.BODY);
		if (body == null) {
			return;
		}
		final PartPose initial = body.getInitialPose();
		final float lx = SADDLE_X - initial.x();
		final float ly = SADDLE_Y - initial.y();
		final float lz = SADDLE_Z - initial.z();
		this.rotation.rotationZYX(body.zRot, body.yRot, body.xRot).transform(this.current.set(lx, ly, lz)).add(body.x, body.y, body.z);
		this.rotation.rotationZYX(initial.zRot(), initial.yRot(), initial.xRot()).transform(this.rest.set(lx, ly, lz)).add(initial.x(), initial.y(), initial.z());

		// Model space is Y-down with the head toward -Z, in pixels; the horse's left is +X.
		final RideState s = holder.horsingaround$ride();
		final long now = System.nanoTime();
		final float lift = -(this.current.y - this.rest.y) / 16.0F;
		final float forward = -(this.current.z - this.rest.z) / 16.0F;
		final float side = -(this.current.x - this.rest.x) / 16.0F;
		final float pitch = -(body.xRot - initial.xRot()) * Mth.RAD_TO_DEG;
		final float roll = -(body.zRot - initial.zRot()) * Mth.RAD_TO_DEG;
		if (now - s.animatedAt > 1_000_000_000L) {
			s.animatedLiftBase = lift;
			s.animatedForwardBase = forward;
			s.animatedSideBase = side;
			s.animatedPitchBase = pitch;
			s.animatedRollBase = roll;
		} else {
			final float k = 1.0F - (float) Math.exp(-(now - s.animatedAt) * 1.0E-9 / BASELINE_SECONDS);
			s.animatedLiftBase += (lift - s.animatedLiftBase) * k;
			s.animatedForwardBase += (forward - s.animatedForwardBase) * k;
			s.animatedSideBase += (side - s.animatedSideBase) * k;
			s.animatedPitchBase += (pitch - s.animatedPitchBase) * k;
			s.animatedRollBase += (roll - s.animatedRollBase) * k;
		}
		s.animatedLift = lift;
		s.animatedForward = forward;
		s.animatedSide = side;
		s.animatedPitch = pitch;
		s.animatedRoll = roll;
		s.animatedAt = now;
		s.animatedFrame = SaddleMotion.frame();
	}
}
//?}
