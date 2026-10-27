package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.ride.RideState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The rest of the horse's bones on the plain model (user direction, 2026-10-27: the whole horse should move as a real one
 * does, not stiffly): the tail a chain of three (the dock and two lengths of hair, cut from vanilla's tail by the equine
 * model mixin), the head on a poll joint at the top of the neck, and each ear on its own. Each is a damped spring swung by
 * how the body moves (its rise and fall and pitch through the stride, speeding up and slowing down, turning): a bone
 * further out lags the one it hangs from, swings through and settles, as hair and a head and ears do. Render thread only;
 * the state is the horse's own (RideState), and the parts each model's bones move are found once.
 */
public final class BodyBones {
	/** The tail's two lower pieces (children, in turn, of vanilla's tail part, which keeps the dock). */
	public static final String TAIL_MID = "horsingaround_tail_mid";
	public static final String TAIL_END = "horsingaround_tail_end";
	/** How long the dock and each length of hair are, pixels (vanilla's tail is 14). */
	public static final float TAIL_DOCK = 4.0F;
	public static final float TAIL_HAIR = 5.0F;
	/** The poll, where the head turns on the neck, in the neck's (head parts') frame, pixels: the top of the neck, at its back. */
	private static final float POLL_Y = -6.0F;
	private static final float POLL_Z = 4.0F;

	/** What a model's neck carries: the parts that go with the head (all but the mane), and the ears and where each turns. */
	private static final class Head {
		final ModelPart[] parts;
		final @Nullable ModelPart leftEar;
		final @Nullable ModelPart rightEar;
		/** Each ear's base (x, y, z, in the head's frame, pixels), left then right. */
		final float[] base = new float[6];

		Head(final ModelPart[] parts, final @Nullable ModelPart leftEar, final @Nullable ModelPart rightEar) {
			this.parts = parts;
			this.leftEar = leftEar;
			this.rightEar = rightEar;
		}
	}

	/** A tail cut in three: its two lower pieces (null for a tail in one piece). */
	private static final class Tail {
		final @Nullable ModelPart mid;
		final @Nullable ModelPart end;

		Tail(final @Nullable ModelPart mid, final @Nullable ModelPart end) {
			this.mid = mid;
			this.end = end;
		}
	}

	private BodyBones() {
	}

	/**
	 * Moves the bones' springs on by {@code dt} seconds (once a frame, from Strides' advance): {@code rise} is how fast the
	 * stride is pushing the body up (blocks/s^2) and {@code nod} how fast it is turning it nose up (radians/s^2); the rest
	 * (speeding up, turning, the neck's swing) is read off the ride.
	 */
	static void advance(final RideState s, final int id, final double now, final float dt, final float rise, final float nod) {
		if (dt <= 0.0F) {
			return;
		}
		final float forward = (float) Math.sqrt(s.gaitVelocityX * s.gaitVelocityX + s.gaitVelocityZ * s.gaitVelocityZ);
		final float k = 1.0F - (float) Math.exp(-dt / BONE_SMOOTH_TIME);
		// Speeding up or slowing down, and the turn and how fast it changes (smoothed: a frame's own jitter is no push).
		s.boneSurge += ((forward - s.boneSpeed) / dt - s.boneSurge) * k;
		s.boneSpeed = forward;
		s.boneYawAccel += ((s.gaitYawRate - s.boneYawRate) / dt - s.boneYawAccel) * k;
		s.boneYawRate = s.gaitYawRate;
		final float neckAccel = (s.neckSpeed - s.boneNeckSpeed) / dt;
		s.boneNeckSpeed = s.neckSpeed;
		// Out to the side of a turn (blocks/s^2, toward the horse's right while it turns right).
		final float side = forward * s.gaitYawRate;
		// The tail streams out behind with speed, and hangs down standing; the hair more than the dock.
		final float pace = Mth.clamp(forward / TAIL_STREAM_SPEED, 0.0F, 1.0F);
		final int steps = Math.min(8, Mth.ceil(Math.min(dt, 0.1F) * 240.0F));
		final float h = Math.min(dt, 0.1F) / steps;
		for (int n = 0; n < steps; n++) {
			// The tail, dock first: each piece's turn against the one it hangs from is a spring toward its rest, pushed by the
			// body's rise (more weight on it: it hangs), its surge (left behind: it lifts) and the turn (out to the side), and
			// left behind as the piece above it turns (it keeps its own way until the spring brings it round).
			float above = -nod;
			float aboveSide = s.boneYawAccel;
			for (int j = 0; j < 3; j++) {
				final float swing = Mth.TWO_PI * TAIL_BONE_SWING[j];
				final float rest = TAIL_CURL[j] + (TAIL_HAIR_STREAM[j] - TAIL_CURL[j]) * pace;
				final float out = 1.0F + TAIL_BONE_OUTER * j;
				final float pitch = -swing * swing * (s.tailPitch[j] - rest) - 2.0F * TAIL_BONE_DAMPING * swing * s.tailPitchV[j]
					+ out * (-TAIL_RISE * rise + TAIL_SURGE * s.boneSurge) - above;
				final float yaw = -swing * swing * s.tailYaw[j] - 2.0F * TAIL_BONE_DAMPING * swing * s.tailYawV[j] + out * TAIL_SIDE * side - aboveSide;
				s.tailPitchV[j] += pitch * h;
				s.tailYawV[j] += yaw * h;
				s.tailPitch[j] = Mth.clamp(s.tailPitch[j] + s.tailPitchV[j] * h, -TAIL_BONE_MOST, TAIL_BONE_MOST);
				s.tailYaw[j] = Mth.clamp(s.tailYaw[j] + s.tailYawV[j] * h, -TAIL_BONE_MOST, TAIL_BONE_MOST);
				above += pitch;
				aboveSide += yaw;
			}
			// The head on its poll: against the neck's swing (so the eyes stay steadier), lagging it as the neck turns.
			final float poll = Mth.TWO_PI * POLL_SWING;
			final float pollAccel = -poll * poll * (s.poll + POLL_COUNTER * s.neck) - 2.0F * POLL_DAMPING * poll * s.pollV - neckAccel * POLL_LAG;
			s.pollV += pollAccel * h;
			s.poll = Mth.clamp(s.poll + s.pollV * h, -POLL_MOST, POLL_MOST);
			// The ears: pricked forward on the move and toward a turn, flopping as the head swings, now and then one flicked back.
			final float ear = Mth.TWO_PI * EAR_SWING;
			final float moving = Mth.clamp(forward / EAR_MOVING_SPEED, 0.0F, 1.0F);
			for (int e = 0; e < 2; e++) {
				final float flick = flick(id, e, now);
				final float target = EAR_FORWARD * moving - EAR_FLICK * flick;
				final float accel = -ear * ear * (s.earPitch[e] - target) - 2.0F * EAR_DAMPING * ear * s.earPitchV[e]
					- (neckAccel + pollAccel) * EAR_FLOP;
				s.earPitchV[e] += accel * h;
				s.earPitch[e] = Mth.clamp(s.earPitch[e] + s.earPitchV[e] * h, -EAR_MOST, EAR_MOST);
			}
			final float turnTo = -Mth.clamp(s.gaitYawRate * EAR_TURN, -EAR_MOST, EAR_MOST);
			final float earYaw = -ear * ear * (s.earYaw - turnTo) - 2.0F * EAR_DAMPING * ear * s.earYawV;
			s.earYawV += earYaw * h;
			s.earYaw = Mth.clamp(s.earYaw + s.earYawV * h, -EAR_MOST, EAR_MOST);
		}
	}

	/** How far ear {@code ear} of horse {@code id} is flicked back at game time {@code now} (ticks): 0 to 1, now and then. */
	private static float flick(final int id, final int ear, final double now) {
		// (Each horse and ear on a beat of its own: a quick flick back and up again once in every EAR_FLICK_EVERY ticks or so.)
		final double cycle = now / EAR_FLICK_EVERY + (id * 0.618034 + ear * 0.37) % 1.0;
		final double into = (cycle - Math.floor(cycle)) * EAR_FLICK_EVERY;
		return into < EAR_FLICK_TICKS ? Mth.sin((float) (into / EAR_FLICK_TICKS) * Mth.PI) : 0.0F;
	}

	/** Puts the bones on a model's parts this frame (after vanilla and the gait have posed them), {@code upright} of the way. */
	static void pose(final ModelPart headParts, final ModelPart tailPart, final RideState s, final float upright) {
		final Tail tail = tail(tailPart);
		tailPart.xRot += s.tailPitch[0] * upright;
		tailPart.yRot += s.tailYaw[0] * upright;
		if (tail.mid != null) {
			tail.mid.xRot += s.tailPitch[1] * upright;
			tail.mid.yRot += s.tailYaw[1] * upright;
		}
		if (tail.end != null) {
			tail.end.xRot += s.tailPitch[2] * upright;
			tail.end.yRot += s.tailYaw[2] * upright;
		}
		final Head head = head(headParts);
		// The head turns about the poll: every part the neck carries but the mane, its place swung round the poll too.
		final float poll = s.poll * upright;
		if (poll != 0.0F) {
			final float cos = Mth.cos(poll);
			final float sin = Mth.sin(poll);
			for (final ModelPart part : head.parts) {
				final float y = part.y - POLL_Y;
				final float z = part.z - POLL_Z;
				part.y = POLL_Y + y * cos - z * sin;
				part.z = POLL_Z + y * sin + z * cos;
				part.xRot += poll;
			}
		}
		ear(head.leftEar, head.base, 0, s.earPitch[0] * upright, s.earYaw * upright);
		ear(head.rightEar, head.base, 3, s.earPitch[1] * upright, s.earYaw * upright);
	}

	/** Turns an ear about its base: pricked forward by {@code pitch}, toward the right by {@code yaw} (radians). */
	private static void ear(final @Nullable ModelPart ear, final float[] base, final int at, final float pitch, final float yaw) {
		if (ear == null || pitch == 0.0F && yaw == 0.0F) {
			return;
		}
		final float bx = base[at];
		final float by = base[at + 1];
		final float bz = base[at + 2];
		// (Its place swung about its base: forward about x, then round about y.)
		float y = ear.y - by;
		float z = ear.z - bz;
		final float cp = Mth.cos(pitch);
		final float sp = Mth.sin(pitch);
		final float y1 = y * cp - z * sp;
		final float z1 = y * sp + z * cp;
		final float x = ear.x - bx;
		final float cy = Mth.cos(yaw);
		final float sy = Mth.sin(yaw);
		ear.x = bx + x * cy + z1 * sy;
		ear.y = by + y1;
		ear.z = bz - x * sy + z1 * cy;
		ear.xRot += pitch;
		ear.yRot += yaw;
	}

	/** Hides the tail's lower pieces on a model an animation pack draws (it draws a tail of its own round vanilla's). */
	public static void hide(final ModelPart tailPart) {
		final ModelPart mid = ((PartData) (Object) tailPart).horsingaround$children().get(TAIL_MID);
		if (mid != null) {
			mid.visible = false;
		}
	}

	private static Tail tail(final ModelPart part) {
		final PartData data = (PartData) (Object) part;
		if (data.horsingaround$bones() instanceof Tail tail) {
			return tail;
		}
		final ModelPart mid = data.horsingaround$children().get(TAIL_MID);
		final ModelPart end = mid == null ? null : ((PartData) (Object) mid).horsingaround$children().get(TAIL_END);
		final Tail tail = new Tail(mid, end);
		data.horsingaround$setBones(tail);
		return tail;
	}

	private static Head head(final ModelPart headParts) {
		final PartData data = (PartData) (Object) headParts;
		if (data.horsingaround$bones() instanceof Head head) {
			return head;
		}
		final List<ModelPart> parts = new ArrayList<>();
		ModelPart skull = null;
		for (final Map.Entry<String, ModelPart> child : data.horsingaround$children().entrySet()) {
			if (!child.getKey().equals("mane")) {
				parts.add(child.getValue());
			}
			if (child.getKey().equals("head")) {
				skull = child.getValue();
			}
		}
		final Map<String, ModelPart> ears = skull == null ? Map.of() : ((PartData) (Object) skull).horsingaround$children();
		final Head head = new Head(parts.toArray(ModelPart[]::new), ears.get("left_ear"), ears.get("right_ear"));
		base(head.leftEar, head.base, 0);
		base(head.rightEar, head.base, 3);
		data.horsingaround$setBones(head);
		return head;
	}

	/** Where an ear turns, in the head's frame: where the model puts it, or (put at the head's own origin) its box's bottom middle. */
	private static void base(final @Nullable ModelPart ear, final float[] base, final int at) {
		if (ear == null) {
			return;
		}
		final PartPose pose = ear.getInitialPose();
		if (pose.x() != 0.0F || pose.y() != 0.0F || pose.z() != 0.0F) {
			base[at] = pose.x();
			base[at + 1] = pose.y();
			base[at + 2] = pose.z();
			return;
		}
		final List<ModelPart.Cube> cubes = ((PartData) (Object) ear).horsingaround$cubes();
		if (cubes.isEmpty()) {
			return;
		}
		final ModelPart.Cube cube = cubes.get(0);
		base[at] = (cube.minX + cube.maxX) * 0.5F;
		base[at + 1] = cube.maxY;
		base[at + 2] = (cube.minZ + cube.maxZ) * 0.5F;
	}
}
