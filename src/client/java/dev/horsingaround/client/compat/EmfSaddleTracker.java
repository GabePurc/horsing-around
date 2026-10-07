package dev.horsingaround.client.compat;

import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.client.mixin.ModelPartAccessor;
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

	/** Stirrup parts found per saddle model (searched once; pack reloads create new models). */
	private final Map<ModelPart, ModelPart[]> stirrups = new WeakHashMap<>();

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
		if (!root.isMainModel) {
			this.holdStirrups(root);
			return;
		}
		// Climbing, the neck reaches forward; applied after the pack so its own neck logic isn't disturbed.
		final ModelPart neckPart = neck(root);
		if (neckPart != null) {
			final float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
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
