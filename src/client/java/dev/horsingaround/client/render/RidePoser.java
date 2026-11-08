package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.client.RideCamera;
import dev.horsingaround.ride.CartTrail;
import dev.horsingaround.ride.IcyCompat;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
//? if <1.21.11
/*import net.minecraft.world.entity.HumanoidArm;*/
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.Vec3;
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
	/** This frame's rider and horse, for {@link #room}: where the horse and the rider's seat are. */
	private static final Vector3f ROOM_HORSE = new Vector3f();
	private static final Vector3f ROOM_SEAT = new Vector3f();
	private static final Quaternionf ROOM_ROTATION = new Quaternionf();
	/** Head to head, from the last {@link #room}. */
	private static float roomHeadGap;
	private static final SaddleMotion SADDLE = new SaddleMotion();
	private static final float[] SHAKE_PHASE = new float[1];
	private static final Quaternionf LEAD_ROTATION = new Quaternionf();
	private static final Vector3f LEAD = new Vector3f();
	private static final float[] NOD = new float[1];

	/**
	 * The horse as drawn this frame, from {@link #drawHorse}: its heading (degrees), forward and right axes, angles
	 * (radians) and how far its body is moved; HORSE_ROTATION turns it.
	 */
	private static float horseYaw;
	private static float fx;
	private static float fz;
	private static float rx;
	private static float rz;
	private static float slope;
	private static float jump;
	private static float pitch;
	private static float bank;
	private static float bodyPitch;
	private static float shiftX;
	private static float shiftZ;
	private static float bodyLift;
	/**
	 * The rider as drawn this frame, from {@link #drawSeat}: how far it is moved, its torso's lean about the seat (radians,
	 * before the room's sit-back) and the horse's rear it goes up with.
	 */
	private static float seatX;
	private static float seatY;
	private static float seatZ;
	private static float riderBank;
	private static float riderPitch;
	private static float riderRear;

	/**
	 * The ride a ridden horse or its rider is on, or null (no ride of this mod is on: nothing is posed). A horse pulling
	 * one of Icy's Better Horses' carts is posed, but not its riders: they sit on the cart's bench, seated and rocked by
	 * that mod, not in the saddle.
	 */
	public static @Nullable RideStateHolder holder(final LivingEntity entity) {
		final boolean isHorse = entity instanceof RideStateHolder;
		final Object ridden = isHorse ? entity : entity.getVehicle();
		return ridden instanceof RideStateHolder holder && holder.horsingaround$managed() && (isHorse || !IcyCompat.pullsCart((Entity) ridden)) ? holder : null;
	}

	public static void extract(final LivingEntity entity, final LivingEntityRenderState state, final float partialTicks) {
		final RidePoseState pose = (RidePoseState) state;
		final boolean isHorse = entity instanceof RideStateHolder;
		final RideStateHolder holder = holder(entity);
		if (holder == null) {
			// Render states are reused between entities, so nothing of a ridden horse's pose may carry over.
			pose.horsingaround$clearPose();
			// On one of Icy's Better Horses' carts, seated by that mod (HorseCartEntityMixin moves the seat): the rider leans
			// over with the cart, about the seat. It doesn't know of a lean across.
			final CartTrail.Lean cart = isHorse || entity.getVehicle() == null ? null : IcyCompat.cartLean(entity.getVehicle());
			final float roll = cart == null ? 0.0F : cart.horsingaround$roll(partialTicks);
			if (roll != 0.0F) {
				final float half = roll * 0.5F * Mth.DEG_TO_RAD;
				final float heading = cart.horsingaround$yaw(partialTicks) * Mth.DEG_TO_RAD;
				//? if >=1.20.5 {
				final float seat = (float) entity.getVehicleAttachmentPoint(entity.getVehicle()).y;
				//?} else
				/*final float seat = 0.0F;*/
				pose.horsingaround$setPose(0.0F, 0.0F, 0.0F, -Mth.sin(heading) * Mth.sin(half), 0.0F, Mth.cos(heading) * Mth.sin(half), Mth.cos(half), 0.0F, seat, 0.0F);
			}
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
		final SaddleMotion saddle = SADDLE.compute(horse, s, partialTicks);
		drawHorse(horse, s, saddle, partialTicks);

		pose.horsingaround$setRide(isHorse ? s : null);
		if (isHorse) {
			s.rear = horse instanceof AbstractHorse rearing ? rearing.getStandAnim(partialTicks) : 0.0F;
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

		// Rider: the seat is pinned to the saddle (see drawSeat).
		final float hx = (float) (Mth.lerp(partialTicks, horse.xOld, horse.getX()) - state.x);
		final float hy = (float) (Mth.lerp(partialTicks, horse.yOld, horse.getY()) - state.y);
		final float hz = (float) (Mth.lerp(partialTicks, horse.zOld, horse.getZ()) - state.z);
		drawSeat(entity, horse, s, saddle, partialTicks, hx, hy, hz);
		s.seatForward = (seatX - hx) * fx + (seatZ - hz) * fz;
		s.seatUp = seatY - hy;
		// Room between the horse's head and the rider (a jump, a steep climb): too close, the horse stretches its neck
		// forward, as a jumping horse does, and if that isn't enough the rider folds less over the neck.
		ROOM_HORSE.set(hx + shiftX, hy + bodyLift, hz + shiftZ);
		ROOM_SEAT.set(seatX, seatY + RIDER_SEAT_HEIGHT, seatZ);
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
		pose.horsingaround$setPose(seatX, seatY, seatZ, RIDER_ROTATION.x, RIDER_ROTATION.y, RIDER_ROTATION.z, RIDER_ROTATION.w, 0.0F, RIDER_SEAT_HEIGHT, 0.0F);

		// The legs hold still against the horse (where the stirrups are held) whatever the torso does.
		final float legPitch = seatedPitch - bodyPitch - riderRear * saddle.pitch * Mth.DEG_TO_RAD;
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
		final float release = bowRelease(entity, s, horse.tickCount + partialTicks, partialTicks);
		pose.horsingaround$setRider(-lift * HAND_BOB, legPitch, legRoll, twist, pelvis, s.shield(partialTicks), release, s.bowPull);
		//? if <1.21.11
		/*pose.horsingaround$setHeld(RiderPose.heldBy(entity, HumanoidArm.RIGHT), RiderPose.heldBy(entity, HumanoidArm.LEFT), RiderPose.usingTicks(entity, partialTicks));*/
	}

	/**
	 * Where vanilla ties a lead to a managed horse ({@code offset}, before the renderer turns it to the body's heading),
	 * moved with the body as drawn, so it stays on the neck over a step, on a slope and in a turn. Anything else keeps
	 * vanilla's.
	 */
	public static Vec3 leashOffset(final Entity entity, final Vec3 offset, final float partialTicks) {
		if (!(entity instanceof LivingEntity horse) || !(entity instanceof RideStateHolder) || holder(horse) == null) {
			return offset;
		}
		final RideState s = ((RideStateHolder) horse).horsingaround$ride();
		drawHorse(horse, s, SADDLE.compute(horse, s, partialTicks), partialTicks);
		if (bank == 0.0F && bodyPitch == 0.0F && bodyLift == 0.0F) {
			return offset;
		}
		// Turned the way the renderer turns it, carried through the pose, and turned back.
		final float yaw = Mth.lerp(partialTicks, horse.yBodyRotO, horse.yBodyRot) * Mth.DEG_TO_RAD;
		final float sin = Mth.sin(yaw);
		final float cos = Mth.cos(yaw);
		final float x = (float) offset.x;
		final float z = (float) offset.z;
		HORSE_ROTATION.transform(LEAD.set(x * cos - z * sin, (float) offset.y, z * cos + x * sin)).add(shiftX, bodyLift, shiftZ);
		return new Vec3(LEAD.x * cos + LEAD.z * sin, LEAD.y, LEAD.z * cos - LEAD.x * sin);
	}

	/**
	 * Where a rider of a managed horse holds a lead ({@code hold}, vanilla's point), moved with the rider as drawn: with
	 * the seat, and turned about it with the torso. In first person vanilla holds it to the view, which rises and falls
	 * with the saddle. Anything else keeps vanilla's.
	 */
	public static Vec3 ropeHold(final Entity holder, final Vec3 hold, final float partialTicks) {
		if (!(holder instanceof LivingEntity rider) || holder instanceof RideStateHolder) {
			return hold;
		}
		final RideStateHolder ridden = holder(rider);
		if (ridden == null) {
			return hold;
		}
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getCameraEntity() == rider && RideCamera.isFirstPersonRide(rider, minecraft)) {
			return hold.add(0.0, RideCamera.firstPersonOffset(partialTicks, NOD), 0.0);
		}
		final LivingEntity horse = (LivingEntity) ridden;
		final RideState s = ridden.horsingaround$ride();
		final SaddleMotion saddle = SADDLE.compute(horse, s, partialTicks);
		drawHorse(horse, s, saddle, partialTicks);
		final double x = Mth.lerp(partialTicks, rider.xOld, rider.getX());
		final double y = Mth.lerp(partialTicks, rider.yOld, rider.getY());
		final double z = Mth.lerp(partialTicks, rider.zOld, rider.getZ());
		drawSeat(rider, horse, s, saddle, partialTicks, (float) (Mth.lerp(partialTicks, horse.xOld, horse.getX()) - x),
			(float) (Mth.lerp(partialTicks, horse.yOld, horse.getY()) - y), (float) (Mth.lerp(partialTicks, horse.zOld, horse.getZ()) - z));
		LEAD_ROTATION.rotationAxis(riderBank, fx, 0.0F, fz).rotateAxis(riderPitch + s.sitBack, rx, 0.0F, rz)
			.transform(LEAD.set((float) (hold.x - x), (float) (hold.y - y) - RIDER_SEAT_HEIGHT, (float) (hold.z - z)));
		return new Vec3(x + seatX + LEAD.x, y + seatY + RIDER_SEAT_HEIGHT + LEAD.y, z + seatZ + LEAD.z);
	}

	/** Works out how the horse is drawn this frame (the fields from {@code horseYaw} to {@code bodyLift}, and HORSE_ROTATION). */
	private static void drawHorse(final LivingEntity horse, final RideState s, final SaddleMotion saddle, final float partialTicks) {
		horseYaw = Mth.rotLerp(partialTicks, horse.yBodyRotO, horse.yBodyRot);
		final float yaw = horseYaw * Mth.DEG_TO_RAD;
		// Forward and right axes of the horse in world space.
		fx = -Mth.sin(yaw);
		fz = Mth.cos(yaw);
		rx = -fz;
		rz = fx;
		slope = s.pitch(partialTicks) * Mth.DEG_TO_RAD;
		jump = s.jumpPitch(partialTicks) * Mth.DEG_TO_RAD;
		pitch = slope + jump;
		bank = s.lean(partialTicks) * Mth.DEG_TO_RAD;
		// A generated gait motion moves the horse model too; a measured one is already in the animated model.
		final float tilt = saddle.synthetic ? slope + saddle.pitch * Mth.DEG_TO_RAD : slope;
		bodyPitch = tilt + jump;
		// The ground tilt pivots at the leg joints, so the hooves stay under them; the jump tilt pivots on the hooves: the
		// hind ones as it lifts its front to take off, the front ones as it lands. Then the bank, about the ground.
		final float pivot = jump > 0.0F ? -s.hindHooves : s.foreHooves;
		final float jointForward = s.legLength * Mth.sin(tilt) - pivot;
		final float jointUp = s.legLength * (1.0F - Mth.cos(tilt));
		final float cosJump = Mth.cos(jump);
		final float sinJump = Mth.sin(jump);
		final float moveForward = jointForward * cosJump - jointUp * sinJump + pivot;
		final float moveUp = jointForward * sinJump + jointUp * cosJump;
		final float bankSin = Mth.sin(bank);
		bodyLift = s.heightOffset(partialTicks) + (saddle.synthetic ? saddle.lift : 0.0F) + moveUp * Mth.cos(bank);
		shiftX = fx * moveForward + rx * moveUp * bankSin;
		shiftZ = fz * moveForward + rz * moveUp * bankSin;
		HORSE_ROTATION.rotationAxis(bank, fx, 0.0F, fz).rotateAxis(bodyPitch, rx, 0.0F, rz);
	}

	/**
	 * Works out how a rider is drawn this frame, after {@link #drawHorse} (the fields from {@code seatX} to
	 * {@code riderRear}), {@code hx, hy, hz} being the horse from the rider. The seat point (where vanilla attaches the
	 * rider) is carried through the horse's pose (bank, slope pitch, step smoothing, gait motion) and the rider moved so it
	 * lands there; the torso leans about that same point, so the pelvis never slides on the saddle.
	 */
	private static void drawSeat(
		final LivingEntity rider, final LivingEntity horse, final RideState s, final SaddleMotion saddle, final float partialTicks,
		final float hx, final float hy, final float hz
	) {
		HORSE_ROTATION.transform(OFFSET.set(-hx, RIDER_SEAT_HEIGHT - hy, -hz));
		float measuredLift = saddle.synthetic ? 0.0F : saddle.lift;
		float measuredForward = saddle.synthetic ? 0.0F : saddle.forward;
		// The game already moves riders up and back with a rear, and a pack's measured saddle has the whole rear in it, so what
		// they were moved comes off it. Read from where the first rider really is (every rider gets the same shift), which
		// runs a tick behind the horse's stand. Not on Icy's Better Horses' model: the point measured there sits ahead of its
		// real seat in a rear, and it takes the part of the shift Icy's keeps to land on the saddle (without, the rider is in
		// the neck).
		if (!saddle.synthetic && s.animatedWholeRear) {
			final double horseX = Mth.lerp(partialTicks, horse.xOld, horse.getX());
			final double horseY = Mth.lerp(partialTicks, horse.yOld, horse.getY());
			final double horseZ = Mth.lerp(partialTicks, horse.zOld, horse.getZ());
			final Entity first = horse.getFirstPassenger() != null ? horse.getFirstPassenger() : rider;
			final float placedForward = (float) (Mth.lerp(partialTicks, first.xOld, first.getX()) - horseX) * fx
				+ (float) (Mth.lerp(partialTicks, first.zOld, first.getZ()) - horseZ) * fz;
			final float placedUp = (float) (Mth.lerp(partialTicks, first.yOld, first.getY()) - horseY);
			if (s.rearSeat(partialTicks) <= 0.0F || Float.isNaN(s.restSeatForward)) {
				s.restSeatForward = placedForward;
				s.restSeatUp = placedUp;
			}
			measuredLift -= placedUp - s.restSeatUp;
			measuredForward -= placedForward - s.restSeatForward;
		}
		final float measuredSide = saddle.synthetic ? 0.0F : saddle.side;
		seatX = hx + OFFSET.x + fx * measuredForward + rx * measuredSide + shiftX;
		seatY = hy + OFFSET.y - RIDER_SEAT_HEIGHT + bodyLift + measuredLift - RIDER_SEAT_DROP;
		seatZ = hz + OFFSET.z + fz * measuredForward + rz * measuredSide + shiftZ;
		// The torso stays closer to upright: resists the bank, leans forward uphill, stays vertical downhill, takes
		// part of the saddle's rocking, sways against the horse's surges and the push of leaves, and folds forward with
		// speed and in jumps.
		final float forwardLean = FORWARD_LEAN_STILL + (FORWARD_LEAN_MOVING - FORWARD_LEAN_STILL) * saddle.moving
			+ (FORWARD_LEAN_GALLOP - FORWARD_LEAN_MOVING) * saddle.gallop;
		riderBank = bank * RIDER_BANK_FOLLOW + saddle.roll * RIDER_BANK_FOLLOW * Mth.DEG_TO_RAD;
		// Jumping, they fold forward over the neck with the horse's rise, and sit up again coming down.
		final float jumpFold = s.airLegs(partialTicks) * RIDER_JUMP_FOLD * Mth.DEG_TO_RAD + Math.max(jump, 0.0F) * (RIDER_JUMP_FOLLOW - RIDER_UPHILL_LEAN);
		// Rearing (measured), it goes up with the horse's back: see RIDER_REAR_FOLLOW.
		riderRear = !saddle.synthetic && horse instanceof AbstractHorse rearing ? rearing.getStandAnim(partialTicks) : 0.0F;
		riderPitch = -Math.max(pitch, 0.0F) * RIDER_UPHILL_LEAN - jumpFold
			+ (saddle.pitch * Mth.lerp(riderRear, RIDER_SADDLE_PITCH_FOLLOW, RIDER_REAR_FOLLOW) - forwardLean + s.inertia(partialTicks) + s.leafPush(partialTicks)) * Mth.DEG_TO_RAD;
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
	 * how it is built, and the rider's head and chest, leaning at {@code lean} (back positive). Uses this frame's horse
	 * and rider ({@link #drawHorse}, {@link #drawSeat}) and the ROOM_ fields.
	 */
	private static float room(final float neck, final float lean) {
		final float neckCos = Mth.cos(neck);
		final float neckSin = Mth.sin(neck);
		final float forward = NECK_BASE_FORWARD + HEAD_FORWARD * neckCos + HEAD_UP * neckSin;
		final float up = NECK_BASE_UP - HEAD_FORWARD * neckSin + HEAD_UP * neckCos;
		HORSE_ROTATION.transform(HEAD.set(fx * forward, up, fz * forward)).add(ROOM_HORSE);
		final float headX = HEAD.x;
		final float headY = HEAD.y;
		final float headZ = HEAD.z;
		ROOM_ROTATION.rotationAxis(riderBank, fx, 0.0F, fz).rotateAxis(lean, -fz, 0.0F, fx);
		ROOM_ROTATION.transform(HEAD.set(0.0F, RIDER_HEAD_ABOVE_SEAT, 0.0F)).add(ROOM_SEAT);
		roomHeadGap = Mth.sqrt(Mth.square(HEAD.x - headX) + Mth.square(HEAD.y - headY) + Mth.square(HEAD.z - headZ));
		ROOM_ROTATION.transform(HEAD.set(0.0F, RIDER_CHEST_ABOVE_SEAT, 0.0F)).add(ROOM_SEAT);
		final float chest = Mth.sqrt(Mth.square(HEAD.x - headX) + Mth.square(HEAD.y - headY) + Mth.square(HEAD.z - headZ));
		return Math.min(roomHeadGap - HEAD_ROOM, chest - CHEST_ROOM);
	}

	private RidePoser() {
	}
}
