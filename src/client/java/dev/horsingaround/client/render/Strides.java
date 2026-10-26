package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.ride.RideController;
import dev.horsingaround.ride.RideState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The horse family's own legs on the plain model (no animation pack posing it): procedural, the way a real horse's work.
 * A hoof on the ground stays exactly where it landed while the body goes over it, so it never slides, and its leg (still
 * vanilla's one rigid piece) turns about its top to point at it, folding up where the ground stands higher. A leg lifts
 * and swings forward when the stride's timing says so, to land ahead of where its top will be by half the ground a hoof
 * stands for, on the ground actually there (steps and slopes are just ground), keeping the hoof's pace through lift-off
 * and touch-down. The stride's timing is each gait's footfalls (walk, trot, canter, gallop, from published kinematics,
 * blended by speed); its cadence follows the horse's real ground speed, as quick as a horse this size steps at that
 * speed and no quicker: past that the hooves stand for a shorter share of the stride, as at speed they do. The body
 * rises, pitches and sits lower with the stride (see {@link SaddleMotion}), the neck is carried lower the faster the gait
 * and nods a beat behind the body, and the tail follows it. Backing up runs the stride backwards. Render thread only;
 * the state is the horse's own ({@link RideState}), allocated once.
 */
public final class Strides {
	/** Vanilla's legs cycle once per 2pi of (limb swing x LEG_PHASE); in water at WATER_PACE of that. */
	private static final float LEG_PHASE = 0.6662F;
	private static final float WATER_PACE = 0.2F;
	/** Vanilla's head bob (its own beat, out of step with the legs), taken out: from this leg-animation speed up. */
	private static final float VANILLA_BOB_SPEED = 0.2F;
	/** Longer than this without being drawn (game ticks), the legs start over from where the horse is. */
	private static final double RESET_TICKS = 10.0;
	/** A planted hoof this far from the top of its leg (blocks) has been left behind (a teleport): it is put back under it. */
	private static final double LOST = 2.5;
	/** How quickly the legs go from standing on the ground to hanging in the air and back (seconds). */
	private static final float GROUND_TIME = 0.06F;
	/** A leg turned out stands on the corner of its sole: how much of the sole's half-depth it comes up by for each of tilt. */
	private static final float CORNER = 1.25F;
	/** Through upright that comes in smoothly (a leg passing upright mid-stance would otherwise kink its hoof's path). */
	private static final float CORNER_EASE = 0.15F;
	/** The swing's lift shapes, s^m (1 - s)^n scaled to peak at 1 (see {@link #shape(float)}). */
	private static final float[] FORE_LIFT = shape(GAIT_FORE_LIFT_PEAK);
	private static final float[] HIND_LIFT = shape(GAIT_HIND_LIFT_PEAK);
	private static final float[] FLICK = shape(0.25F);
	private static final float[] REACH = shape(0.75F);

	// Scratch; render thread only.
	private static final Matrix4f ROOT = new Matrix4f();
	private static final Matrix4f INVERSE = new Matrix4f();
	private static final Vector3f POINT = new Vector3f();
	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
	/** The most a leg folds, a share of its length (the rest is the ride's ground work, see GroundLegs). */
	private static final float FOLD_MOST = 0.6F;
	/** Where {@link #tread} found its ground (world x, z). */
	private static double TREAD_X;
	private static double TREAD_Z;
	/** What {@link #aim} found. */
	private static float AIM_TURN;
	private static float AIM_SIDE;
	private static float AIM_REACH;
	private static float AIM_OUT;
	private static final ModelPart[] PARTS = new ModelPart[4];
	/** How much of vanilla's swing each leg has (front left, front right, hind left, hind right). */
	private static final float[] VANILLA_SWING = {0.8F, -0.8F, -0.5F, 0.5F};
	/** The ride state whose legs were posed here for the model being set up now (see {@link #posed}). */
	private static @Nullable RideState posed;

	private Strides() {
	}

	/** How much of the motion is a canter or gallop, 0..1, at leg-animation speed {@code speed}. */
	public static float run(final float speed) {
		return Mth.clamp((speed - RUN_ANIMATION_SPEED + 0.05F) * 10.0F, 0.0F, 1.0F);
	}

	/** How much of the motion is a gallop. */
	public static float gallop(final float speed) {
		return run(speed) * Mth.clamp((speed - GALLOP_ANIMATION_SPEED + 0.03F) * 20.0F, 0.0F, 1.0F);
	}

	/** How much of the motion is a trot. */
	public static float trot(final float speed) {
		return Mth.clamp((speed - TROT_ANIMATION_SPEED + 0.02F) * 10.0F, 0.0F, 1.0F) * (1.0F - run(speed));
	}

	/** How far through its stride the horse is, 0..1 (see {@link #advance}). */
	public static float phase(final RideState s) {
		return (float) (s.gaitPhase - Math.floor(s.gaitPhase));
	}

	/**
	 * The body's rise with the stride, blocks, {@code t} strides along at these shares of each gait (times their
	 * SADDLE_LIFT_): twice a stride at a walk and trot, once at a canter and gallop.
	 */
	public static float bodyLift(final float t, final float walk, final float trot, final float canter, final float gallop) {
		return SADDLE_LIFT_WALK * walk * Mth.cos(2.0F * Mth.TWO_PI * (t - GAIT_BODY_HIGH[0]))
			+ SADDLE_LIFT_TROT * trot * Mth.cos(2.0F * Mth.TWO_PI * (t - GAIT_BODY_HIGH[1]))
			+ SADDLE_LIFT_CANTER * canter * Mth.cos(Mth.TWO_PI * (t - GAIT_BODY_HIGH[2]))
			+ SADDLE_LIFT_GALLOP * gallop * Mth.cos(Mth.TWO_PI * (t - GAIT_BODY_HIGH[3]));
	}

	/** The body's pitch with the stride, degrees, nose up positive (see {@link #bodyLift}). */
	public static float bodyPitch(final float t, final float walk, final float trot, final float canter, final float gallop) {
		return SADDLE_PITCH_WALK * walk * Mth.sin(2.0F * Mth.TWO_PI * (t - GAIT_BODY_HIGH[0]))
			+ SADDLE_PITCH_TROT * trot * Mth.sin(2.0F * Mth.TWO_PI * (t - GAIT_BODY_HIGH[1]))
			+ SADDLE_PITCH_CANTER * canter * Mth.cos(Mth.TWO_PI * (t - GAIT_BODY_NOSE_UP[2]))
			+ SADDLE_PITCH_GALLOP * gallop * Mth.cos(Mth.TWO_PI * (t - GAIT_BODY_NOSE_UP[3]));
	}

	/**
	 * How much lower the body sits on its legs at these shares of each gait (blocks): enough that a leg turned as far as
	 * it goes on the ground still reaches it.
	 */
	public static float crouch(final float walk, final float trot, final float canter, final float gallop) {
		return GAIT_LEG * (walk * (1.0F - Mth.cos(GAIT_STANCE_ANGLE[0])) + trot * (1.0F - Mth.cos(GAIT_STANCE_ANGLE[1]))
			+ canter * (1.0F - Mth.cos(GAIT_STANCE_ANGLE[2])) + gallop * (1.0F - Mth.cos(GAIT_STANCE_ANGLE[3])));
	}

	/**
	 * Moves the horse's stride on to this frame (once a frame, as its render state is filled; again the same frame does
	 * nothing): its ground velocity, its gait, its cadence and how long each hoof stands.
	 */
	public static void advance(final LivingEntity horse, final RideState s, final float partialTicks) {
		final double now = horse.level().getGameTime() + (double) partialTicks;
		final double x = Mth.lerp(partialTicks, horse.xOld, horse.getX());
		final double z = Mth.lerp(partialTicks, horse.zOld, horse.getZ());
		if (Double.isNaN(s.gaitAt) || now < s.gaitAt - RESET_TICKS || now - s.gaitAt > RESET_TICKS) {
			// (First drawn, or back after a while: start from standing where it is.)
			s.gaitAt = now;
			s.gaitX = x;
			s.gaitZ = z;
			s.gaitVelocityX = 0.0;
			s.gaitVelocityZ = 0.0;
			s.gaitPosedAt = Double.NaN;
			s.gaitGround = grounded(horse, s, partialTicks) ? 1.0F : 0.0F;
			return;
		}
		final float dt = (float) ((now - s.gaitAt) * 0.05);
		if (dt <= 0.0F) {
			return;
		}
		s.gaitAt = now;
		final float k = 1.0F - (float) Math.exp(-dt / GAIT_SPEED_TIME);
		s.gaitVelocityX += ((x - s.gaitX) / dt - s.gaitVelocityX) * k;
		s.gaitVelocityZ += ((z - s.gaitZ) / dt - s.gaitVelocityZ) * k;
		s.gaitX = x;
		s.gaitZ = z;
		final float yaw = Mth.rotLerp(partialTicks, horse.yBodyRotO, horse.yBodyRot) * Mth.DEG_TO_RAD;
		final double forward = -Mth.sin(yaw) * s.gaitVelocityX + Mth.cos(yaw) * s.gaitVelocityZ;
		final float speed = (float) Math.sqrt(s.gaitVelocityX * s.gaitVelocityX + s.gaitVelocityZ * s.gaitVelocityZ);
		// The gait by the speed against the horse's top speed (the scale the ride's gaits and the leg animation go by),
		// going over to it in GAIT_BLEND_TIME.
		final float pace = speed * 0.05F / topSpeed(horse);
		final float run = run(pace);
		final float gallop = gallop(pace);
		final float trot = trot(pace);
		final float blend = s.gaitShare[0] + s.gaitShare[1] + s.gaitShare[2] + s.gaitShare[3] < 0.5F ? 1.0F : 1.0F - (float) Math.exp(-dt / GAIT_BLEND_TIME);
		s.gaitShare[0] += (1.0F - trot - run - s.gaitShare[0]) * blend;
		s.gaitShare[1] += (trot - s.gaitShare[1]) * blend;
		s.gaitShare[2] += (run - gallop - s.gaitShare[2]) * blend;
		s.gaitShare[3] += (gallop - s.gaitShare[3]) * blend;
		s.gaitStride = smoothstep(speed / GAIT_FULL_SPEED);
		s.gaitGround += ((grounded(horse, s, partialTicks) ? 1.0F : 0.0F) - s.gaitGround) * (1.0F - (float) Math.exp(-dt / GROUND_TIME));
		// Each gait's cadence: quick enough that the ground a hoof stands for fits its leg's reach, between a real horse's
		// at this speed and that a horse this size would go; then how long each hoof stands, at most its gait's share.
		float rate = 0.0F;
		float fore = 0.0F;
		float hind = 0.0F;
		for (int g = 0; g < 4; g++) {
			final float share = s.gaitShare[g];
			if (share <= 0.0F) {
				continue;
			}
			final float reach = 2.0F * GAIT_LEG * Mth.sin(GAIT_STANCE_ANGLE[g]);
			final float real = GAIT_CADENCE[g] + GAIT_CADENCE_RISE[g] * speed;
			final float cadence = Mth.clamp(speed * GAIT_FORE_STANCE[g] / reach, real, real * GAIT_CADENCE_SIZE);
			rate += share * cadence;
			fore += share * Math.min(GAIT_FORE_STANCE[g], speed > 1.0E-3F ? reach * cadence / speed : 1.0F);
			hind += share * Math.min(GAIT_HIND_STANCE[g], speed > 1.0E-3F ? reach * cadence / speed : 1.0F);
		}
		// (Standing, squared up: the stride stops until the horse moves on.)
		if (speed < GAIT_STILL_SPEED && s.gaitSettled) {
			rate = 0.0F;
		}
		s.gaitRate = rate;
		s.gaitForeStance = fore;
		s.gaitHindStance = hind;
		// How far the strides sweep: as far as a leg turns either side of upright on the ground (the ground a hoof stands
		// for, against its leg), for how much the body rises, pitches and sits lower (1 - cos of it), against the gait's most.
		final float half = rate > 0.0F ? Math.min(speed * 0.25F * (fore + hind) / rate / GAIT_LEG, 1.0F) : 0.0F;
		final float sweep = Mth.clamp((1.0F - Mth.sqrt(1.0F - half * half)) / (1.0F - Mth.cos(mix(s, GAIT_STANCE_ANGLE))), 0.0F, 1.0F);
		s.gaitSweep += (sweep - s.gaitSweep) * k;
		s.gaitPhase += (forward < 0.0 ? -rate : rate) * dt;
	}

	/**
	 * Poses the legs (front left, front right, hind left, hind right), head and tail, from vanilla's pose of them this
	 * frame. Returns whether the legs were posed (then the ride's own legs on the ground, GroundLegs, stand aside).
	 *
	 * @param position vanilla's limb swing and its speed, whether in water, how far reared and grazing: to take vanilla's
	 *                 own leg swing and head bob out exactly as it put them in
	 * @param root     the model's root part (the legs' parent)
	 */
	public static boolean pose(
		final @Nullable ModelPart leftFront, final @Nullable ModelPart rightFront, final @Nullable ModelPart leftHind, final @Nullable ModelPart rightHind,
		final ModelPart head, final ModelPart tail, final RideState s, final ModelPart root, final float position, final float speed, final boolean water,
		final float standing, final float eating
	) {
		final Level level = Minecraft.getInstance().level;
		if (!s.drawnPoseSet || level == null || Double.isNaN(s.gaitAt)) {
			return false;
		}
		PARTS[0] = leftFront;
		PARTS[1] = rightFront;
		PARTS[2] = leftHind;
		PARTS[3] = rightHind;
		// Vanilla's own swing of each leg, and its head bob, come out.
		final float upright = 1.0F - standing;
		final float vanilla = Mth.cos(position * LEG_PHASE * (water ? WATER_PACE : 1.0F) + Mth.PI) * speed * upright;
		if (speed > VANILLA_BOB_SPEED) {
			head.xRot -= Mth.cos(position * 0.8F) * 0.15F * speed * (standing + 1.0F - Math.max(standing, eating));
		}
		final double now = level.getGameTime() + (double) Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		if (now != s.gaitPosedAt) {
			solve(s, root, level, now);
		}
		final float stride = s.gaitStride * upright;
		for (int i = 0; i < 4; i++) {
			final ModelPart part = PARTS[i];
			final Legs.Leg leg = part == null ? null : Legs.of(part);
			if (leg == null) {
				continue;
			}
			part.xRot -= vanilla * VANILLA_SWING[i];
			Legs.swing(part, leg, part.xRot + s.gaitLegTurn[i] * upright);
			part.zRot += s.gaitLegSide[i] * upright;
			final float fold = s.gaitLegFold[i] * upright;
			if (fold > 0.0F) {
				Legs.draw(part, fold);
			}
		}
		// The neck: carried lower at faster gaits, nodding twice a stride at a walk and trot (lowest at each front leg's
		// mid-stance) and once at a canter and gallop (lowest as the leading front hoof lands), a beat behind the body.
		// The tail bounces after the body, and swings with the walk's hips.
		final float t = phase(s);
		head.xRot += stride * (mix(s, GAIT_NECK) + beats(s, GAIT_NOD, GAIT_NOD_LOW, t));
		tail.xRot += stride * beats(s, GAIT_TAIL, GAIT_TAIL_HIGH, t);
		tail.yRot += stride * s.gaitShare[0] * GAIT_TAIL_SWAY * Mth.sin(Mth.TWO_PI * (t - GAIT_TOUCHDOWN[0][2]));
		posed = s;
		return true;
	}

	/** Whether these legs posed the model being set up now (for {@code ride}): then the ride's legs on the ground stand aside. */
	public static boolean posed(final @Nullable RideState ride) {
		return ride != null && posed == ride;
	}

	/** Before each model is set up: nothing posed yet. */
	public static void clear() {
		posed = null;
	}

	/** Works out each leg's pose this frame (see the class): where its hoof is, and how its leg turns and folds to it. */
	private static void solve(final RideState s, final ModelPart root, final Level level, final double now) {
		final boolean seed = Double.isNaN(s.gaitPosedAt);
		final float dt = seed ? 0.0F : (float) Mth.clamp((now - s.gaitPosedAt) * 0.05, 0.0, 0.1);
		s.gaitPosedAt = now;
		ROOT.set(s.drawnPose)
			.translate(root.x / 16.0F, root.y / 16.0F, root.z / 16.0F)
			.rotateZYX(root.zRot, root.yRot, root.xRot)
			.scale(root.xScale, root.yScale, root.zScale);
		INVERSE.set(ROOT).invert();
		final double cx = s.drawnCameraX;
		final double cy = s.drawnCameraY;
		final double cz = s.drawnCameraZ;
		// (The stride's pace for where a hoof lands: at least a slow walk's, so one setting off from standing lands close.)
		final float rate = Math.max(s.gaitRate, GAIT_CADENCE[0]);
		final double phase = s.gaitPhase;
		// (Back from a step's face: against the way the horse is going.)
		final double going = Math.sqrt(s.gaitVelocityX * s.gaitVelocityX + s.gaitVelocityZ * s.gaitVelocityZ);
		final double backX = going > 1.0E-3 ? -s.gaitVelocityX / going : 0.0;
		final double backZ = going > 1.0E-3 ? -s.gaitVelocityZ / going : 0.0;
		boolean settled = true;
		// (Hooves standing, and how many at least stay down while one lifts: one at a walk; faster gaits have moments in the
		// air.)
		int standing = 0;
		for (int i = 0; i < 4; i++) {
			standing += s.hoofDown[i] ? 1 : 0;
		}
		final int support = s.gaitShare[0] > 0.5F ? 1 : 0;
		for (int i = 0; i < 4; i++) {
			final ModelPart part = PARTS[i];
			final Legs.Leg leg = part == null ? null : Legs.of(part);
			if (leg == null) {
				continue;
			}
			final boolean fore = i < 2;
			final float stance = fore ? s.gaitForeStance : s.gaitHindStance;
			// The top of the leg (model pixels, the leg as built) and in the world.
			final float topX = part.x + leg.soleX;
			final float topY = part.y + leg.hipY;
			final float topZ = part.z + leg.z;
			ROOT.transformPosition(topX / 16.0F, topY / 16.0F, topZ / 16.0F, POINT);
			final double hipX = cx + POINT.x;
			final double hipY = cy + POINT.y;
			final double hipZ = cz + POINT.z;
			final float length = leg.length / 16.0F;
			// How fast the top of the leg goes (the body's speed, and its swing round as the horse turns).
			if (seed) {
				s.hipVelocityX[i] = s.gaitVelocityX;
				s.hipVelocityZ[i] = s.gaitVelocityZ;
			} else if (dt > 0.0F) {
				final float k = 1.0F - (float) Math.exp(-dt / GAIT_SPEED_TIME);
				s.hipVelocityX[i] += ((hipX - s.hipX[i]) / dt - s.hipVelocityX[i]) * k;
				s.hipVelocityZ[i] += ((hipZ - s.hipZ[i]) / dt - s.hipVelocityZ[i]) * k;
			}
			s.hipX[i] = hipX;
			s.hipZ[i] = hipZ;
			double vx = s.hipVelocityX[i];
			double vz = s.hipVelocityZ[i];
			// Where it lands: half the ground a hoof stands for ahead of its leg's top, the way the top of its leg is going, on
			// the ground there.
			final double ahead = stance / (2.0 * rate);
			// How far through its stride it is by its gait's footfalls.
			final double at = phase - touchdown(s, i);
			final float p = (float) (at - Math.floor(at));
			final float dp = wrap(p - s.legPhase[i]);
			final boolean backing = dp < 0.0F;
			s.legPhase[i] = p;
			final boolean lost = s.hoofDown[i] && Mth.square(s.hoofX[i] - hipX) + Mth.square(s.hoofZ[i] - hipZ) > LOST * LOST;
			if (seed || lost) {
				// Starting over: a hoof on the ground where it would be this far into its stance; one in the air sets off from
				// where it would have lifted.
				vx = s.hipVelocityX[i] = s.gaitVelocityX;
				vz = s.hipVelocityZ[i] = s.gaitVelocityZ;
				final boolean down = p < stance;
				final double back = (down ? p / stance : 1.0) * 2.0 * ahead;
				s.hoofY[i] = tread(level, hipX + vx * (ahead - back), hipZ + vz * (ahead - back), backX, backZ, hipY, length, s.gaitGround);
				s.hoofX[i] = TREAD_X;
				s.hoofZ[i] = TREAD_Z;
				s.hoofDown[i] = down;
				s.plantReach[i] = -1.0F;
				s.stood[i] = down ? p / stance : 1.0F;
				s.swingThrough[i] = down ? 0.0F : (p - stance) / (1.0F - stance);
				s.swingBack[i] = false;
				s.hoofFromX[i] = s.hoofX[i];
				s.hoofFromY[i] = s.hoofY[i];
				s.hoofFromZ[i] = s.hoofZ[i];
				s.liftHipX[i] = hipX;
				s.liftHipZ[i] = hipZ;
				s.liftTurn[i] = 0.0F;
			} else if (s.hoofDown[i]) {
				// On the ground: it stands for its stance's share of the stride (backing up too), a little quicker or slower by
				// how far it is behind or ahead of its gait's footfalls (so a change of gait, or a step out of turn, comes back
				// into step over a stride or so), and lifts. One the body has moved out of its reach (up a step, or on past it)
				// or out to its side (turning) steps on at once. Either waits while too few other hooves stand (setting off from
				// standing square, they would all go at once), unless the body is far out of its reach.
				s.stood[i] += Math.abs(dp) / Math.max(stance, 0.05F) * keepStep(backing ? stance * (1.0F - s.stood[i]) - p : p - s.stood[i] * stance);
				final float reach = Math.max(s.plantReach[i], length);
				final boolean reached = s.stood[i] >= GAIT_STEP_STOOD && (s.hoofReach[i] > reach + GAIT_STEP_REACH || Math.abs(s.hoofSide[i]) > GAIT_SIDE_STEP);
				if (s.hoofReach[i] > reach + GAIT_STEP_FAR || (reached || s.stood[i] >= 1.0F) && standing > support) {
					standing--;
					s.liftTurn[i] = s.gaitLegTurn[i];
					s.liftHipX[i] = hipX;
					s.liftHipZ[i] = hipZ;
					s.hoofFromX[i] = s.hoofX[i];
					s.hoofFromY[i] = hoofHeight(s, i, hipY, length);
					s.hoofFromZ[i] = s.hoofZ[i];
					s.swingThrough[i] = 0.0F;
					s.swingBack[i] = backing;
					s.hoofDown[i] = false;
				}
			} else {
				// In the air: it goes through its swing as its stride goes on (backwards when it set off backing up), keeping step
				// as it stands, taking at most GAIT_SWING_TIME (at a crawl a leg doesn't drift through the air); going back past
				// where it set off, it stands there again.
				final float swung = s.swingThrough[i] * (1.0F - stance);
				final float through = (s.swingBack[i] ? -dp : dp) / Math.max(1.0F - stance, 0.05F);
				s.swingThrough[i] += through >= 0.0F
					? Math.max(through * keepStep(s.swingBack[i] ? 1.0F - swung - p : p - stance - swung), dt / swingTime(s, stance, rate)) : through;
				if (s.swingThrough[i] >= 1.0F) {
					// Touch-down: it stands where its swing brought it (where the top of its leg was as it landed, this frame or a
					// moment before).
					final double past = (s.swingThrough[i] - 1.0F) * swingTime(s, stance, rate);
					s.hoofY[i] = tread(level, hipX + vx * (ahead - past), hipZ + vz * (ahead - past), backX, backZ, hipY, length, s.gaitGround);
					s.hoofX[i] = TREAD_X;
					s.hoofZ[i] = TREAD_Z;
					s.hoofDown[i] = true;
					s.stood[i] = 0.0F;
					s.plantReach[i] = -1.0F;
				} else if (s.swingThrough[i] < 0.0F) {
					s.hoofX[i] = s.hoofFromX[i];
					s.hoofY[i] = s.hoofFromY[i];
					s.hoofZ[i] = s.hoofFromZ[i];
					s.hoofDown[i] = true;
					s.stood[i] = 0.0F;
					s.plantReach[i] = -1.0F;
				}
			}
			if (s.hoofDown[i]) {
				// On the ground: the leg turns about its top to point at its hoof, and folds by what is short of its length (drawn
				// up into the body; the ride's ground work, GroundLegs, does the rest: a hoof still in the ground, and the body's
				// fit onto its legs).
				aim(leg, s.hoofX[i] - cx, hoofHeight(s, i, hipY, length) - cy, s.hoofZ[i] - cz, topX, topY, topZ);
				s.gaitLegTurn[i] = AIM_TURN;
				s.gaitLegSide[i] = AIM_SIDE;
				s.gaitLegFold[i] = Mth.clamp(leg.length - AIM_REACH, 0.0F, FOLD_MOST * leg.length);
				s.hoofReach[i] = AIM_REACH / 16.0F;
				s.hoofSide[i] = AIM_OUT / 16.0F;
				if (s.plantReach[i] < 0.0F) {
					s.plantReach[i] = AIM_REACH / 16.0F;
				}
			} else {
				// The swing: the hoof goes from where it lifted to where it lands, along the ground each against the top of its
				// leg (so it goes on with the body, the leg swinging about its top between the two), leaving and arriving at
				// rest (as a hoof on the ground is): it lets go of the spot it lifted from over GAIT_SWING_LET_GO of the swing,
				// sets off a moment after lifting and is over its landing spot a moment before the end, in one arc clear of the
				// ground. The leg flicks back as it lifts and reaches forward before it lands. Onto higher ground it is up by
				// the time it is most of the way there; onto lower, it comes down once it is over it.
				final float sw = Mth.clamp(s.swingThrough[i], 0.0F, 1.0F);
				// (Where it lands: where its leg's top will be then, the swing's time left on, and half a stance ahead of that.)
				final double left = (1.0F - sw) * swingTime(s, stance, rate);
				final double toY = tread(level, hipX + vx * (ahead + left), hipZ + vz * (ahead + left), backX, backZ, hipY, length, s.gaitGround);
				final float first = mix(s, GAIT_SWING_LIFT_FIRST);
				final float there = mix(s, GAIT_SWING_OVER);
				final float over = smoothstep((sw - first) / (there - first));
				final float held = smoothstep(sw / GAIT_SWING_LET_GO);
				final double hoofX = Mth.lerp(held, s.hoofFromX[i], hipX + Mth.lerp(over, s.hoofFromX[i] - s.liftHipX[i], TREAD_X - hipX - vx * left));
				final double hoofZ = Mth.lerp(held, s.hoofFromZ[i], hipZ + Mth.lerp(over, s.hoofFromZ[i] - s.liftHipZ[i], TREAD_Z - hipZ - vz * left));
				// (Its lift: an arc, highest early in the swing, over the ground on the way: onto lower ground, coming down as it
				// goes over it.)
				final double alongX = TREAD_X - s.hoofFromX[i];
				final double alongZ = TREAD_Z - s.hoofFromZ[i];
				final double gone = ((hoofX - s.hoofFromX[i]) * alongX + (hoofZ - s.hoofFromZ[i]) * alongZ) / Math.max(alongX * alongX + alongZ * alongZ, 1.0E-4);
				final float down = toY > s.hoofFromY[i] ? smoothstep(sw / (first + 0.6F * (there - first))) : smoothstep((float) gone);
				final double lift = (mix(s, fore ? GAIT_FORE_LIFT : GAIT_HIND_LIFT) + Math.abs(s.hoofFromY[i] - toY) * 0.5) * s.gaitGround
					* shape(sw, fore ? FORE_LIFT : HIND_LIFT);
				final double hoofY = s.hoofFromY[i] + (toY - s.hoofFromY[i]) * down + lift;
				aim(leg, hoofX - cx, hoofY - cy, hoofZ - cz, topX, topY, topZ);
				final float sweep = mix(s, GAIT_SWING_FLICK) * shape(sw, FLICK) - mix(s, GAIT_SWING_REACH) * shape(sw, REACH);
				s.gaitLegTurn[i] = Mth.clamp(AIM_TURN + Math.signum(s.liftTurn[i]) * s.gaitGround * sweep, -GAIT_LEG_MOST, GAIT_LEG_MOST);
				s.gaitLegSide[i] = AIM_SIDE;
				s.gaitLegFold[i] = Mth.clamp(leg.length - AIM_REACH, 0.0F, FOLD_MOST * leg.length);
				s.hoofReach[i] = AIM_REACH / 16.0F;
				s.hoofSide[i] = AIM_OUT / 16.0F;
			}
			settled &= s.hoofDown[i] && Mth.square(s.hoofX[i] - hipX) + Mth.square(s.hoofZ[i] - hipZ) < GAIT_SQUARE * GAIT_SQUARE;
		}
		s.gaitSettled = settled;
	}

	/**
	 * Whether hoof {@code i} of these legs is in its swing, or about to step (the body has gone out of its reach), for the
	 * ride's ground work: such a hoof doesn't hold the body down onto it. (GroundLegs goes by how high an animation has a
	 * hoof; these legs lower the body as they go, so that would read planted hooves as lifted.)
	 */
	public static boolean lifted(final RideState s, final int i) {
		return !s.hoofDown[i] || s.hoofReach[i] > Math.max(s.plantReach[i], GAIT_LEG) + GAIT_HANG_SLACK;
	}

	/**
	 * Whether the hooves are on their ground: not in a jump (stepping or falling off something, they find the ground
	 * under them, as GroundLegs has it) nor swimming.
	 */
	private static boolean grounded(final LivingEntity horse, final RideState s, final float partialTicks) {
		return (s.airLegs(partialTicks) < AIR_GROUNDED || !s.leapt()) && !horse.isInWater();
	}

	/** Where hoof {@code i} stands (world height): on its ground, or off the ground (a jump, swimming), hanging from its leg. */
	private static double hoofHeight(final RideState s, final int i, final double hipY, final float length) {
		return s.gaitGround >= 1.0F ? s.hoofY[i] : Mth.lerp(s.gaitGround, hipY - length, s.hoofY[i]);
	}

	/**
	 * How the leg turns about its top (topX, topY, topZ, model pixels) to point at the camera-relative world point (x, y,
	 * z), in AIM_: forward and out to the side (radians, at most GAIT_SIDE_MOST: a hoof further out turns on the ground
	 * under it), how far along it the hoof is and how far out to the side the point is (pixels). A leg turned out stands on the corner of
	 * its sole: the point comes up by as much, so none of the hoof is in the ground (in the air too, so nothing jumps as it
	 * lands or lifts).
	 */
	private static void aim(final Legs.Leg leg, final double x, final double y, final double z, final float topX, final float topY, final float topZ) {
		modelPoint(x, y, z, topX, topY, topZ);
		final float relX = POINT.x;
		final float relZ = POINT.z;
		final float out = (relX * relX + relZ * relZ) / Math.max(relX * relX + POINT.y * POINT.y + relZ * relZ, 1.0E-6F);
		final float relY = POINT.y - leg.soleHalf * CORNER * (Mth.sqrt(out + CORNER_EASE * CORNER_EASE) - CORNER_EASE);
		AIM_REACH = Mth.sqrt(relX * relX + relY * relY + relZ * relZ);
		AIM_TURN = AIM_REACH < 1.0E-3F ? 0.0F : Mth.clamp((float) Math.asin(Mth.clamp(relZ / AIM_REACH, -1.0F, 1.0F)), -GAIT_LEG_MOST, GAIT_LEG_MOST);
		final float side = relY <= 0.0F ? 0.0F : (float) Math.atan2(-relX, relY);
		AIM_SIDE = Mth.clamp(side, -GAIT_SIDE_MOST, GAIT_SIDE_MOST);
		if (AIM_SIDE != side) {
			// (Leaning out less than the point: as far along as the point is deep, so the hoof stands level with it.)
			AIM_REACH = Math.min(AIM_REACH, relY / (Mth.cos(AIM_SIDE) * Mth.cos(AIM_TURN)));
		}
		AIM_OUT = relX;
	}

	/** Leaves in {@link #POINT} the camera-relative world point (x, y, z) in the legs' frame, model pixels from (topX, topY, topZ). */
	private static void modelPoint(final double x, final double y, final double z, final float topX, final float topY, final float topZ) {
		INVERSE.transformPosition((float) x, (float) y, (float) z, POINT);
		POINT.set(POINT.x * 16.0F - topX, POINT.y * 16.0F - topY, POINT.z * 16.0F - topZ);
	}

	/**
	 * The ground a hoof stands on at (x, z), world height: searched down from LEG_RISE_MAX above where its leg reaches
	 * straight down to a block below that; a step's face higher than that, back from it along (backX, backZ) (where, in
	 * TREAD_X and TREAD_Z); with no ground (a drop), as low as the leg reaches. Off the ground, hanging from its leg.
	 */
	private static double tread(
		final Level level, final double x, final double z, final double backX, final double backZ, final double hipY, final float length, final float onGround
	) {
		TREAD_X = x;
		TREAD_Z = z;
		final double hanging = hipY - length;
		if (onGround <= 0.0F) {
			return hanging;
		}
		double top = Double.NaN;
		for (int k = 0; k <= GAIT_FACE_STEPS && Double.isNaN(top); k++) {
			top = RideController.surface(level, POS, x + backX * k * GAIT_FACE_STEP, z + backZ * k * GAIT_FACE_STEP, hanging + LEG_RISE_MAX, hanging - 1.0, false);
			if (!Double.isNaN(top)) {
				TREAD_X = x + backX * k * GAIT_FACE_STEP;
				TREAD_Z = z + backZ * k * GAIT_FACE_STEP;
			}
		}
		final double y = Double.isNaN(top) || top == Double.NEGATIVE_INFINITY ? hanging : top;
		return onGround >= 1.0F ? y : Mth.lerp(onGround, hanging, y);
	}

	/**
	 * How long a swing takes, seconds: the stride's time out of stance, at most GAIT_SWING_TIME as far as its hoof has the
	 * reach to stand the longer for it (at a crawl; never where the stride is as long as the legs reach).
	 */
	private static float swingTime(final RideState s, final float stance, final float rate) {
		final float swing = (1.0F - stance) / rate;
		final float speed = (float) Math.sqrt(s.gaitVelocityX * s.gaitVelocityX + s.gaitVelocityZ * s.gaitVelocityZ);
		final float spare = 2.0F * GAIT_LEG * Mth.sin(mix(s, GAIT_STANCE_ANGLE)) - speed * stance / rate;
		return Math.max(Math.min(swing, GAIT_SWING_TIME), swing - Math.max(spare, 0.0F) / Math.max(speed, 1.0E-3F));
	}

	/** How much quicker a leg goes through its stance or swing, {@code behind} its gait's footfalls by that share of the stride. */
	private static float keepStep(final float behind) {
		return Mth.clamp(1.0F + GAIT_KEEP_STEP * wrap(behind), 1.0F - GAIT_KEEP_STEP_MOST, 1.0F + GAIT_KEEP_STEP_MOST);
	}

	/** {@code x} wrapped into -0.5..0.5 (a share of the stride either way). */
	private static float wrap(final float x) {
		return x - Math.round(x);
	}

	/** Leg {@code i}'s touch-down in the stride this frame, blended between the gaits. */
	private static float touchdown(final RideState s, final int i) {
		return s.gaitShare[0] * GAIT_TOUCHDOWN[0][i] + s.gaitShare[1] * GAIT_TOUCHDOWN[1][i] + s.gaitShare[2] * GAIT_TOUCHDOWN[2][i]
			+ s.gaitShare[3] * GAIT_TOUCHDOWN[3][i];
	}

	private static float mix(final RideState s, final float[] values) {
		return s.gaitShare[0] * values[0] + s.gaitShare[1] * values[1] + s.gaitShare[2] * values[2] + s.gaitShare[3] * values[3];
	}

	/** A swing of {@code size[g]} peaking at {@code at[g]} of the stride: twice a stride for the walk and trot, once for the canter and gallop. */
	private static float beats(final RideState s, final float[] size, final float[] at, final float t) {
		return s.gaitShare[0] * size[0] * Mth.cos(2.0F * Mth.TWO_PI * (t - at[0])) + s.gaitShare[1] * size[1] * Mth.cos(2.0F * Mth.TWO_PI * (t - at[1]))
			+ s.gaitShare[2] * size[2] * Mth.cos(Mth.TWO_PI * (t - at[2])) + s.gaitShare[3] * size[3] * Mth.cos(Mth.TWO_PI * (t - at[3]));
	}

	/** s^m (1 - s)^n times its scale: 0 at both ends with no pace there, 1 at its peak. */
	private static float shape(final float s, final float[] shape) {
		return (float) (Math.pow(s, shape[0]) * Math.pow(1.0 - s, shape[1])) * shape[2];
	}

	/** The m, n and scale of a shape peaking at {@code peak} (the lesser exponent 2). */
	private static float[] shape(final float peak) {
		final float m = peak <= 0.5F ? 2.0F : 2.0F * peak / (1.0F - peak);
		final float n = peak <= 0.5F ? 2.0F * (1.0F - peak) / peak : 2.0F;
		return new float[] {m, n, (float) (1.0 / (Math.pow(peak, m) * Math.pow(1.0 - peak, n)))};
	}
}
