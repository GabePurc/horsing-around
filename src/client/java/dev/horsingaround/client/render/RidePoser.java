package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
//? if <1.21.11
/*import net.minecraft.world.entity.HumanoidArm;*/
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemUseAnimation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Poses horses to the ground and into turns, and carries their riders with them: the hips ride the saddle (including
 * the gait motion of the horse's animated body) while the torso resists the bank and pitch to stay upright. Worked out
 * as each horse and rider's render state is filled (see the living-entity renderer mixin). Render thread.
 */
public final class RidePoser {
	/** Render-thread scratch. */
	private static final Quaternionf HORSE_ROTATION = new Quaternionf();
	private static final Quaternionf RIDER_ROTATION = new Quaternionf();
	private static final Vector3f OFFSET = new Vector3f();
	private static final Vector3f HEAD = new Vector3f();
	/** This frame's rider and horse, for {@link #room}: where the horse and the rider's seat are, and how they face. */
	private static final Vector3f ROOM_HORSE = new Vector3f();
	private static final Vector3f ROOM_SEAT = new Vector3f();
	private static final Quaternionf ROOM_ROTATION = new Quaternionf();
	private static float roomFx;
	private static float roomFz;
	private static float roomBank;
	/** Head to head, from the last {@link #room}. */
	private static float roomHeadGap;
	private static final SaddleMotion SADDLE = new SaddleMotion();
	private static final float[] SHAKE_PHASE = new float[1];

	/** The ride a ridden horse or its rider is on, or null (no ride of this mod is on: nothing is posed). */
	public static @Nullable RideStateHolder holder(final LivingEntity entity) {
		final Object ridden = entity instanceof RideStateHolder ? entity : entity.getVehicle();
		return ridden instanceof RideStateHolder holder && holder.horsingaround$managed() ? holder : null;
	}

	public static void extract(final LivingEntity entity, final LivingEntityRenderState state, final float partialTicks) {
		final RidePoseState pose = (RidePoseState) state;
		final boolean isHorse = entity instanceof RideStateHolder;
		final RideStateHolder holder = holder(entity);
		if (holder == null) {
			// Render states are reused between entities, so nothing of a ridden horse's pose may carry over.
			pose.horsingaround$clearPose();
			pose.horsingaround$setNeck(0.0F);
			pose.horsingaround$setHeadShake(0.0F, 0.0F);
			pose.horsingaround$setLegs(false, 0.0F, 0.0F, 0.0F);
			pose.horsingaround$setRide(null);
			pose.horsingaround$setAirLegs(0.0F, 0.0F);
			pose.horsingaround$setTail(0.0F);
			return;
		}
		final LivingEntity horse = (LivingEntity) holder;
		final RideState s = holder.horsingaround$ride();
		// (The plain model's own stride, moved on once a frame; an animation pack's horse has its own.)
		if (SaddleMotion.frame() - s.animatedFrame > 1) {
			Strides.advance(horse, s, partialTicks);
		}
		final SaddleMotion saddle = SADDLE.compute(horse, s, partialTicks);
		final float horseYaw = Mth.rotLerp(partialTicks, horse.yBodyRotO, horse.yBodyRot);
		final float yaw = horseYaw * Mth.DEG_TO_RAD;
		// Forward and right axes of the horse in world space.
		final float fx = -Mth.sin(yaw);
		final float fz = Mth.cos(yaw);
		final float rx = -fz;
		final float rz = fx;
		final float slope = s.pitch(partialTicks) * Mth.DEG_TO_RAD;
		final float jump = s.jumpPitch(partialTicks) * Mth.DEG_TO_RAD;
		final float pitch = slope + jump;
		final float bank = s.lean(partialTicks) * Mth.DEG_TO_RAD;
		// A generated gait motion moves the horse model too; a measured one is already in the animated model.
		final float tilt = saddle.synthetic ? slope + saddle.pitch * Mth.DEG_TO_RAD : slope;
		final float bodyPitch = tilt + jump;
		// The ground tilt pivots at the leg joints, so the hooves stay under them; the jump tilt pivots on the hooves: the
		// hind ones as it lifts its front to take off, the front ones as it lands. Then the bank, about the ground.
		final float pivot = jump > 0.0F ? -HIND_HOOVES : FORE_HOOVES;
		final float jointForward = LEG_LENGTH * Mth.sin(tilt) - pivot;
		final float jointUp = LEG_LENGTH * (1.0F - Mth.cos(tilt));
		final float cosJump = Mth.cos(jump);
		final float sinJump = Mth.sin(jump);
		final float moveForward = jointForward * cosJump - jointUp * sinJump + pivot;
		final float moveUp = jointForward * sinJump + jointUp * cosJump;
		final float bankSin = Mth.sin(bank);
		final float bodyLift = s.heightOffset(partialTicks) + (saddle.synthetic ? saddle.lift - saddle.crouch : 0.0F) + moveUp * Mth.cos(bank);
		final float shiftX = fx * moveForward + rx * moveUp * bankSin;
		final float shiftZ = fz * moveForward + rz * moveUp * bankSin;
		HORSE_ROTATION.rotationAxis(bank, fx, 0.0F, fz).rotateAxis(bodyPitch, rx, 0.0F, rz);

		pose.horsingaround$setRide(isHorse ? s : null);
		if (isHorse) {
			// A pack-animated model gets its neck pose in the Entity Model Features hook, after the pack has run.
			final boolean packAnimated = !saddle.synthetic;
			final float shake = packAnimated ? 0.0F : s.headShake(horse.tickCount + partialTicks, SHAKE_PHASE);
			pose.horsingaround$setHeadShake(
				shake == 0.0F ? 0.0F : -Mth.sin(SHAKE_PHASE[0]) * HEAD_SHAKE_YAW * shake,
				shake == 0.0F ? 0.0F : Mth.cos(SHAKE_PHASE[0]) * HEAD_SHAKE_ROLL * shake
			);
			//? if <26.3 {
			/*// (Before 26.3 a rider seen in first person isn't drawn, so nothing works out the room: the neck and seat let go
			// instead of staying where they were.)
			final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
			if (minecraft.options.getCameraType().isFirstPerson() && minecraft.getCameraEntity() != null && horse.hasPassenger(minecraft.getCameraEntity())) {
				final float dt = roomStep(s, horse.tickCount + partialTicks);
				s.neckReach = ease(s.neckReach, 0.0F, dt);
				s.sitBack = ease(s.sitBack, 0.0F, dt);
			}
			*///?}
			pose.horsingaround$setNeck(packAnimated ? 0.0F : neckCounter(pitch) + s.neckReach);
			// Hooves on the ground; a pack-animated model gets them in the Entity Model Features hook too.
			if (packAnimated) {
				pose.horsingaround$setLegs(false, 0.0F, 0.0F, 0.0F);
			} else {
				pose.horsingaround$setLegs(true, slope, s.foreLeg(partialTicks), s.hindLeg(partialTicks));
			}
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
		final float measuredLift = saddle.synthetic ? 0.0F : saddle.lift;
		final float measuredForward = saddle.synthetic ? 0.0F : saddle.forward;
		final float measuredSide = saddle.synthetic ? 0.0F : saddle.side;
		// Standing in the stirrups, the hips come up off the saddle and forward over the stirrups, and the legs take most of
		// the saddle's bounce (and sink a little as the rider gives on landing); braced, the feet go forward and the seat
		// stays. In the horse's own frame, like the seat.
		final float stand = s.stand(partialTicks);
		final float brace = s.brace(partialTicks);
		final float settle = s.settle(partialTicks);
		RiderSeat.solve(stand, brace, stand * (STAND_RISE - saddle.lift * STAND_ABSORB - Math.max(settle, 0.0F) * SETTLE_SINK));
		HORSE_ROTATION.transform(OFFSET.set(fx * RiderSeat.hipForward, RiderSeat.hipUp, fz * RiderSeat.hipForward));
		final float seatX = OFFSET.x;
		final float seatY = OFFSET.y;
		final float seatZ = OFFSET.z;
		HORSE_ROTATION.transform(OFFSET.set(-hx, RIDER_SEAT_HEIGHT - hy, -hz));
		final float tx = hx + OFFSET.x + fx * measuredForward + rx * measuredSide + shiftX + seatX;
		final float ty = hy + OFFSET.y - RIDER_SEAT_HEIGHT + bodyLift + measuredLift - RIDER_SEAT_DROP + seatY;
		final float tz = hz + OFFSET.z + fz * measuredForward + rz * measuredSide + shiftZ + seatZ;
		// The torso stays closer to upright: resists the bank, leans forward uphill, stays vertical downhill, takes
		// part of the saddle's rocking (less standing), sways against the horse's surges and the push of leaves, leans a
		// little forward moving and standing, back when bracing, and folds forward in jumps and as the rider gives on landing.
		final float seatedLean = FORWARD_LEAN_STILL + (FORWARD_LEAN_MOVING - FORWARD_LEAN_STILL) * saddle.moving;
		final float forwardLean = seatedLean + (STAND_LEAN - seatedLean) * stand - brace * BRACE_LEAN_BACK + settle * SETTLE_FOLD;
		final float riderBank = bank * RIDER_BANK_FOLLOW + saddle.roll * RIDER_BANK_FOLLOW * Mth.DEG_TO_RAD;
		// Jumping, they fold forward over the neck with the horse's rise, and sit up again coming down.
		final float jumpFold = s.airLegs(partialTicks) * RIDER_JUMP_FOLD * Mth.DEG_TO_RAD + Math.max(jump, 0.0F) * (RIDER_JUMP_FOLLOW - RIDER_UPHILL_LEAN);
		final float rocking = saddle.pitch * RIDER_SADDLE_PITCH_FOLLOW * (1.0F - STAND_QUIET * stand);
		final float riderPitch = -Math.max(pitch, 0.0F) * RIDER_UPHILL_LEAN - jumpFold
			+ (rocking - forwardLean + s.inertia(partialTicks) + s.leafPush(partialTicks)) * Mth.DEG_TO_RAD;
		// Room between the horse's head and the rider (a jump, a steep climb): too close, the horse stretches its neck
		// forward, as a jumping horse does, and if that isn't enough the rider folds less over the neck.
		ROOM_HORSE.set(hx + shiftX, hy + bodyLift, hz + shiftZ);
		ROOM_SEAT.set(tx, ty + RIDER_SEAT_HEIGHT, tz);
		roomFx = fx;
		roomFz = fz;
		roomBank = riderBank;
		final float neck = neckCounter(pitch);
		final float step = 1.0F / ROOM_STEPS;
		float reach = NECK_REACH_MAX * Mth.DEG_TO_RAD;
		for (int k = 0; k <= ROOM_STEPS; k++) {
			if (room(neck + reach * k * step, riderPitch) >= 0.0F) {
				reach *= k * step;
				break;
			}
		}
		final float dt = roomStep(s, horse.tickCount + partialTicks);
		s.neckReach = ease(s.neckReach, reach, dt);
		float sitBack = SIT_BACK_MAX * Mth.DEG_TO_RAD;
		for (int k = 0; k <= ROOM_STEPS; k++) {
			if (room(neck + s.neckReach, riderPitch + sitBack * k * step) >= 0.0F) {
				sitBack *= k * step;
				break;
			}
		}
		s.sitBack = ease(s.sitBack, sitBack, dt);
		final float seatedPitch = riderPitch + s.sitBack;
		room(neck + s.neckReach, seatedPitch);
		s.headGap = roomHeadGap;
		RIDER_ROTATION.rotationAxis(riderBank, fx, 0.0F, fz).rotateAxis(seatedPitch, rx, 0.0F, rz);
		pose.horsingaround$setPose(tx, ty, tz, RIDER_ROTATION.x, RIDER_ROTATION.y, RIDER_ROTATION.z, RIDER_ROTATION.w, 0.0F, RIDER_SEAT_HEIGHT, 0.0F);

		// The legs hold still against the horse (where the stirrups are held) whatever the torso does, lying as the seat has
		// them.
		final float legPitch = seatedPitch - bodyPitch;
		// The stirrups roll with the saddle's sway, so the legs do too.
		final float legRoll = riderBank - bank - (saddle.synthetic ? 0.0F : saddle.roll * Mth.DEG_TO_RAD);
		// At a canter the pelvis rocks a little with the stride while the shoulders stay put; less the more the rider stands.
		final float pelvis = -saddle.run * saddle.lift * PELVIS_SLIDE * (1.0F - stand);

		// Hips stay square to the horse; the head and torso turn toward where the rider looks.
		final float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
		state.bodyRot = horseYaw;
		state.yRot = Mth.clamp(Mth.wrapDegrees(headYaw - horseYaw), -LOOK_LIMIT, LOOK_LIMIT);
		final float twist = Mth.clamp(state.yRot * TORSO_TWIST, -TORSO_TWIST_MAX, TORSO_TWIST_MAX) * Mth.DEG_TO_RAD;
		final float lift = saddle.lift * (0.5F + 0.5F * saddle.limbSpeed);
		final float release = bowRelease(entity, s, horse.tickCount + partialTicks, partialTicks);
		pose.horsingaround$setRider(
			-lift * HAND_BOB + Math.max(settle, 0.0F) * SETTLE_HANDS, RiderSeat.legForward, RiderSeat.legSplay, legPitch, legRoll, twist, pelvis,
			s.shield(partialTicks), release, s.bowPull
		);
		//? if <1.21.11
		/*pose.horsingaround$setHeld(RiderPose.heldBy(entity, HumanoidArm.RIGHT), RiderPose.heldBy(entity, HumanoidArm.LEFT), RiderPose.usingTicks(entity, partialTicks));*/
	}

	/** Seconds of game time since the room was last eased (none twice in a frame), and stamps it. */
	private static float roomStep(final RideState s, final float now) {
		final float dt = Float.isNaN(s.roomAt) ? 1.0F : Mth.clamp((now - s.roomAt) * 0.05F, 0.0F, 0.1F);
		s.roomAt = now;
		return dt;
	}

	private static float ease(final float value, final float target, final float dt) {
		return value + (target - value) * (1.0F - (float) Math.exp(-dt / (target > value ? ROOM_IN_SECONDS : ROOM_OUT_SECONDS)));
	}

	/**
	 * The rider's bow: while it is drawn, how far is noted; once loosed (or let down), how far through the follow-through
	 * the rider is, 1 just loosed to 0 (also 0 with no bow loosed).
	 */
	private static float bowRelease(final LivingEntity rider, final RideState s, final float now, final float partialTicks) {
		if (rider.isUsingItem() && rider.getUseItem().getUseAnimation() == ItemUseAnimation.BOW) {
			s.bowPull = RiderPose.pull(RiderPose.usingTicks(rider, partialTicks));
			s.bowLoosedAt = Float.NaN;
			return 0.0F;
		}
		if (s.bowPull <= 0.0F) {
			return 0.0F;
		}
		if (Float.isNaN(s.bowLoosedAt)) {
			s.bowLoosedAt = now;
		}
		final float release = 1.0F - (now - s.bowLoosedAt) / BOW_RELEASE_TICKS;
		if (release <= 0.0F) {
			s.bowPull = 0.0F;
			return 0.0F;
		}
		return release;
	}

	/**
	 * Room to spare (blocks; negative: too close) between the horse's head, its neck at {@code neck} radians forward of
	 * how it is built, and the rider's head and chest, leaning at {@code riderPitch} (back positive). Uses this frame's
	 * horse pose ({@code HORSE_ROTATION}) and the ROOM_ fields.
	 */
	private static float room(final float neck, final float riderPitch) {
		final float fx = roomFx;
		final float fz = roomFz;
		final float neckCos = Mth.cos(neck);
		final float neckSin = Mth.sin(neck);
		final float forward = NECK_BASE_FORWARD + HEAD_FORWARD * neckCos + HEAD_UP * neckSin;
		final float up = NECK_BASE_UP - HEAD_FORWARD * neckSin + HEAD_UP * neckCos;
		HORSE_ROTATION.transform(HEAD.set(fx * forward, up, fz * forward)).add(ROOM_HORSE);
		final float headX = HEAD.x;
		final float headY = HEAD.y;
		final float headZ = HEAD.z;
		ROOM_ROTATION.rotationAxis(roomBank, fx, 0.0F, fz).rotateAxis(riderPitch, -fz, 0.0F, fx);
		ROOM_ROTATION.transform(HEAD.set(0.0F, RIDER_HEAD_ABOVE_SEAT, 0.0F)).add(ROOM_SEAT);
		roomHeadGap = Mth.sqrt(Mth.square(HEAD.x - headX) + Mth.square(HEAD.y - headY) + Mth.square(HEAD.z - headZ));
		ROOM_ROTATION.transform(HEAD.set(0.0F, RIDER_CHEST_ABOVE_SEAT, 0.0F)).add(ROOM_SEAT);
		final float chest = Mth.sqrt(Mth.square(HEAD.x - headX) + Mth.square(HEAD.y - headY) + Mth.square(HEAD.z - headZ));
		return Math.min(roomHeadGap - HEAD_ROOM, chest - CHEST_ROOM);
	}

	private RidePoser() {
	}
}
