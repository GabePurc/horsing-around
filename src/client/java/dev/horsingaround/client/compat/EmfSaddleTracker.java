package dev.horsingaround.client.compat;

import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.client.mixin.ModelPartAccessor;
import dev.horsingaround.client.render.AirLegs;
import dev.horsingaround.ride.RideTuning;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import traben.entity_model_features.EMFAnimationApi;
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
		} catch (final Exception e) {
			throw new IllegalStateException("Could not hook Entity Model Features animations", e);
		}
	}

	/** Frames the stirrups were held (for tests). */
	public static int stirrupFrames;

	/** The tail part per model: the pack's own (Fresh Animations' "tail2") or vanilla's (searched once). */
	private final Map<ModelPart, ModelPart> tails = new WeakHashMap<>();

	/** The tail swings with the horse's motion, on top of the pack's own tail animation. */
	private void swingTail(final EMFModelPartRoot root, final RideState ride, final float partialTicks) {
		final float lift = ride.tailLift(partialTicks);
		if (lift == 0.0F) {
			return;
		}
		ModelPart tail = this.tails.get(root);
		if (tail == null && !this.tails.containsKey(root)) {
			tail = find(root, "tail2");
			if (tail == null) {
				tail = root.getAllVanillaPartsByNameEMF().get("tail");
			}
			this.tails.put(root, tail);
		}
		if (tail != null) {
			tail.xRot += lift;
		}
	}

	/** Stirrup parts found per saddle model (searched once; pack reloads create new models). */
	private final Map<ModelPart, ModelPart[]> stirrups = new WeakHashMap<>();
	/** Leg parts per model (front left, front right, hind left, hind right; null where the model has none). */
	private final Map<ModelPart, ModelPart[]> legs = new WeakHashMap<>();
	private static final String[] LEG_NAMES = {"left_front_leg", "right_front_leg", "left_hind_leg", "right_hind_leg"};

	/**
	 * Legs on a step, after the pack has animated them: the front legs fold up and forward onto a step or reach down for
	 * one, the hind legs drive the hindquarters up or gather under going down; and in the air, the shape of a jump. On
	 * every layer (body, saddle, armour) so they stay together.
	 */
	private void stepLegs(final EMFModelPartRoot root, final RideState ride, final float partialTicks) {
		final float fore = ride.foreLeg(partialTicks);
		final float hind = ride.hindLeg(partialTicks);
		final float air = ride.airLegs(partialTicks);
		if (fore == 0.0F && hind == 0.0F && air <= 0.0F) {
			return;
		}
		ModelPart[] parts = this.legs.get(root);
		if (parts == null) {
			parts = new ModelPart[LEG_NAMES.length];
			for (int i = 0; i < LEG_NAMES.length; i++) {
				parts[i] = root.getAllVanillaPartsByNameEMF().get(LEG_NAMES[i]);
			}
			this.legs.put(root, parts);
		}
		final float tuck = Math.max(fore, 0.0F);
		final float foreSwing = -tuck * RideTuning.FORE_TUCK_ANGLE + Math.min(fore, 0.0F) * RideTuning.FORE_REACH_ANGLE;
		final float lift = tuck * RideTuning.FORE_TUCK_LIFT;
		final float hindSwing = hind * (hind > 0.0F ? RideTuning.HIND_DRIVE_ANGLE : RideTuning.HIND_GATHER_ANGLE);
		for (int i = 0; i < parts.length; i++) {
			final ModelPart leg = parts[i];
			if (leg != null) {
				if (i < 2) {
					leg.xRot += foreSwing;
					leg.y -= lift;
				} else {
					leg.xRot += hindSwing;
				}
			}
		}
		AirLegs.pose(parts[0], parts[1], parts[2], parts[3], air, ride.airRise(partialTicks));
	}

	private static ModelPart neck(final EMFModelPartRoot root) {
		final ModelPart neck = root.getAllVanillaPartsByNameEMF().get("head_parts");
		return neck != null ? neck : root.getAllVanillaPartsByNameEMF().get("neck");
	}

	/**
	 * Swing the pack's stirrups forward to meet the seated leg and hold them there against the body's pitch, so the
	 * feet (which the rider pose holds still against the horse) stay in them.
	 */
	private void holdStirrups(final EMFModelPartRoot root) {
		ModelPart[] parts = this.stirrups.get(root);
		if (parts == null) {
			parts = new ModelPart[] {root.getAllVanillaPartsByNameEMF().get("body"), find(root, "left_saddle"), find(root, "right_saddle")};
			this.stirrups.put(root, parts);
		}
		if (parts[1] == null && parts[2] == null) {
			return;
		}
		final float bodyPitch = parts[0] == null ? 0.0F : parts[0].xRot - parts[0].getInitialPose().xRot();
		final float swing = -RideTuning.STIRRUP_SWING - bodyPitch;
		stirrupFrames++;
		for (int i = 1; i < 3; i++) {
			if (parts[i] != null) {
				parts[i].xRot = swing;
				parts[i].zRot = 0.0F;
			}
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
		if (!(context.activeState().emfEntity() instanceof RideStateHolder holder) || !holder.horsingaround$managed()) {
			return;
		}
		final EMFModelPartRoot root = context.animatingModelRoot();
		final RideState ride = holder.horsingaround$ride();
		final float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		this.stepLegs(root, ride, partialTicks);
		this.swingTail(root, ride, partialTicks);
		if (!root.isMainModel) {
			this.holdStirrups(root);
			return;
		}
		// Climbing, the neck reaches forward; applied after the pack so its own neck logic isn't disturbed.
		final ModelPart neckPart = neck(root);
		if (neckPart != null) {
			neckPart.xRot += (ride.pitch(partialTicks) + ride.jumpPitch(partialTicks)) * Mth.DEG_TO_RAD * RideTuning.NECK_COUNTER_PITCH;
		}
		final ModelPart body = root.getAllVanillaPartsByNameEMF().get("body");
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

		// The pack re-poses the head after vanilla, so the exhausted head toss goes on here, after it.
		final float shake = s.headShake(((Entity) holder).tickCount + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true), this.phase);
		if (shake > 0.0F) {
			final ModelPart neck = neck(root);
			if (neck != null) {
				neck.yRot += -Mth.sin(this.phase[0]) * RideTuning.HEAD_SHAKE_YAW * shake;
				neck.zRot += Mth.cos(this.phase[0]) * RideTuning.HEAD_SHAKE_ROLL * shake;
				s.animatedShakeFrames++;
			}
		}
	}
}
