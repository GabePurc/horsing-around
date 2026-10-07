package dev.horsingaround.client.mixin;

import static dev.horsingaround.ride.RideTuning.*;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.horsingaround.client.render.RidePoseState;
import dev.horsingaround.client.render.SaddleMotion;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Poses horses to the ground and into turns, and carries their riders with them: the hips ride the saddle (including
 * the gait motion of the horse's animated body) while the torso resists the bank and pitch to stay upright.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	/** Render-thread scratch. */
	@Unique
	private static final Quaternionf HORSE_ROTATION = new Quaternionf();
	@Unique
	private static final Quaternionf RIDER_ROTATION = new Quaternionf();
	@Unique
	private static final Vector3f OFFSET = new Vector3f();
	@Unique
	private static final SaddleMotion SADDLE = new SaddleMotion();
	@Unique
	private static final float[] SHAKE_PHASE = new float[1];

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void horsingaround$extractPose(final LivingEntity entity, final LivingEntityRenderState state, final float partialTicks, final CallbackInfo ci) {
		final RidePoseState pose = (RidePoseState) state;
		final boolean isHorse = entity instanceof RideStateHolder;
		if (!((isHorse ? entity : entity.getVehicle()) instanceof RideStateHolder holder) || !holder.horsingaround$managed()) {
			pose.horsingaround$clearPose();
			pose.horsingaround$setLegs(0.0F, 0.0F);
			pose.horsingaround$setAirLegs(0.0F, 0.0F);
			pose.horsingaround$setTail(0.0F);
			return;
		}
		final LivingEntity horse = (LivingEntity) holder;
		final RideState s = holder.horsingaround$ride();
		final SaddleMotion saddle = SADDLE.compute(horse, s, partialTicks);
		final float horseYaw = Mth.rotLerp(partialTicks, horse.yBodyRotO, horse.yBodyRot);
		final float yaw = horseYaw * Mth.DEG_TO_RAD;
		// Forward and right axes of the horse in world space.
		final float fx = -Mth.sin(yaw);
		final float fz = Mth.cos(yaw);
		final float rx = -fz;
		final float rz = fx;
		final float jump = s.jumpPitch(partialTicks) * Mth.DEG_TO_RAD;
		final float pitch = s.pitch(partialTicks) * Mth.DEG_TO_RAD + jump;
		final float bank = s.lean(partialTicks) * Mth.DEG_TO_RAD;
		// A generated gait motion moves the horse model too; a measured one is already in the animated model.
		final float bodyPitch = saddle.synthetic ? pitch + saddle.pitch * Mth.DEG_TO_RAD : pitch;
		// The jump tilt pivots on the hooves: the hind ones as it lifts its front to take off, the front ones as it lands.
		final float pivot = jump > 0.0F ? -HIND_HOOVES : FORE_HOOVES;
		final float pivotForward = pivot * (1.0F - Mth.cos(jump));
		final float bodyLift = s.heightOffset(partialTicks) + (saddle.synthetic ? saddle.lift : 0.0F) + Math.abs(pivot) * Mth.sin(Math.abs(jump));
		final float shiftX = fx * pivotForward;
		final float shiftZ = fz * pivotForward;
		HORSE_ROTATION.rotationAxis(bank, fx, 0.0F, fz).rotateAxis(bodyPitch, rx, 0.0F, rz);

		if (isHorse) {
			// A pack-animated model gets its neck pose in the Entity Model Features hook, after the pack has run.
			final boolean packAnimated = !saddle.synthetic;
			final float shake = packAnimated ? 0.0F : s.headShake(horse.tickCount + partialTicks, SHAKE_PHASE);
			pose.horsingaround$setHeadShake(
				shake == 0.0F ? 0.0F : -Mth.sin(SHAKE_PHASE[0]) * HEAD_SHAKE_YAW * shake,
				shake == 0.0F ? 0.0F : Mth.cos(SHAKE_PHASE[0]) * HEAD_SHAKE_ROLL * shake
			);
			pose.horsingaround$setNeck(packAnimated ? 0.0F : pitch * NECK_COUNTER_PITCH);
			// Legs on a step; a pack-animated model gets them in the Entity Model Features hook too.
			pose.horsingaround$setLegs(packAnimated ? 0.0F : s.foreLeg(partialTicks), packAnimated ? 0.0F : s.hindLeg(partialTicks));
			pose.horsingaround$setAirLegs(packAnimated ? 0.0F : s.airLegs(partialTicks), s.airRise(partialTicks));
			pose.horsingaround$setTail(packAnimated ? 0.0F : s.tailLift(partialTicks));
			if (bank == 0.0F && bodyPitch == 0.0F && bodyLift == 0.0F) {
				pose.horsingaround$clearPose();
			} else {
				pose.horsingaround$setPose(shiftX, bodyLift, shiftZ, HORSE_ROTATION.x, HORSE_ROTATION.y, HORSE_ROTATION.z, HORSE_ROTATION.w, 0.0F, 0.0F, 0.0F);
			}
			return;
		}

		// Rider: the seat is pinned to the saddle. Take the seat point (where vanilla attaches the rider), carry it
		// through the horse's pose (bank, slope pitch, step smoothing, gait motion), and move the rider so it lands
		// there; every lean below pivots around that same point, so the pelvis never slides on the saddle.
		final float hx = (float) (Mth.lerp(partialTicks, horse.xOld, horse.getX()) - state.x);
		final float hy = (float) (Mth.lerp(partialTicks, horse.yOld, horse.getY()) - state.y);
		final float hz = (float) (Mth.lerp(partialTicks, horse.zOld, horse.getZ()) - state.z);
		HORSE_ROTATION.transform(OFFSET.set(-hx, RIDER_SEAT_HEIGHT - hy, -hz));
		final float measuredLift = saddle.synthetic ? 0.0F : saddle.lift;
		final float measuredForward = saddle.synthetic ? 0.0F : saddle.forward;
		final float measuredSide = saddle.synthetic ? 0.0F : saddle.side;
		final float tx = hx + OFFSET.x + fx * measuredForward + rx * measuredSide + shiftX;
		final float ty = hy + OFFSET.y - RIDER_SEAT_HEIGHT + bodyLift + measuredLift - RIDER_SEAT_DROP;
		final float tz = hz + OFFSET.z + fz * measuredForward + rz * measuredSide + shiftZ;
		// The torso stays closer to upright: resists the bank, leans forward uphill, stays vertical downhill, takes
		// part of the saddle's rocking, sways against the horse's surges and the push of leaves, and folds forward with
		// speed and in jumps.
		final float forwardLean = FORWARD_LEAN_STILL + (FORWARD_LEAN_MOVING - FORWARD_LEAN_STILL) * saddle.moving
			+ (FORWARD_LEAN_GALLOP - FORWARD_LEAN_MOVING) * saddle.gallop;
		final float riderBank = bank * RIDER_BANK_FOLLOW + saddle.roll * RIDER_BANK_FOLLOW * Mth.DEG_TO_RAD;
		// Jumping, they fold forward over the neck with the horse's rise, and sit up again coming down.
		final float jumpFold = s.airLegs(partialTicks) * RIDER_JUMP_FOLD * Mth.DEG_TO_RAD + Math.max(jump, 0.0F) * (RIDER_JUMP_FOLLOW - RIDER_UPHILL_LEAN);
		final float riderPitch = -Math.max(pitch, 0.0F) * RIDER_UPHILL_LEAN - jumpFold
			+ (saddle.pitch * RIDER_SADDLE_PITCH_FOLLOW - forwardLean + s.inertia(partialTicks) + s.leafPush(partialTicks)) * Mth.DEG_TO_RAD;
		RIDER_ROTATION.rotationAxis(riderBank, fx, 0.0F, fz).rotateAxis(riderPitch, rx, 0.0F, rz);
		pose.horsingaround$setPose(tx, ty, tz, RIDER_ROTATION.x, RIDER_ROTATION.y, RIDER_ROTATION.z, RIDER_ROTATION.w, 0.0F, RIDER_SEAT_HEIGHT, 0.0F);

		// The legs hold still against the horse (where the stirrups are held) whatever the torso does.
		final float legPitch = riderPitch - bodyPitch;
		// The stirrups roll with the saddle's sway, so the legs do too.
		final float legRoll = riderBank - bank - (saddle.synthetic ? 0.0F : saddle.roll * Mth.DEG_TO_RAD);
		// At a canter the pelvis rocks a little with the stride while the shoulders stay put; less in a gallop's half seat.
		final float pelvis = -saddle.run * (1.0F - (1.0F - PELVIS_GALLOP_SHARE) * saddle.gallop) * saddle.lift * PELVIS_SLIDE;

		// Hips stay square to the horse; the head and torso turn toward where the rider looks.
		final float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
		state.bodyRot = horseYaw;
		state.yRot = Mth.clamp(Mth.wrapDegrees(headYaw - horseYaw), -LOOK_LIMIT, LOOK_LIMIT);
		final float twist = Mth.clamp(state.yRot * TORSO_TWIST, -TORSO_TWIST_MAX, TORSO_TWIST_MAX) * Mth.DEG_TO_RAD;
		final float lift = saddle.lift * (0.5F + 0.5F * saddle.limbSpeed);
		pose.horsingaround$setRider(-lift * HAND_BOB, legPitch, legRoll, twist, pelvis, s.shield(partialTicks));
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, ordinal = 0)
	)
	private void horsingaround$applyPose(
		final LivingEntityRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState camera, final CallbackInfo ci
	) {
		((RidePoseState) state).horsingaround$applyPose(poseStack);
	}
}
