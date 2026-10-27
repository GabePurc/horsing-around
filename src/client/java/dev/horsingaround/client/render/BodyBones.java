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
 * does, not stiffly): the trunk in three (chest, barrel and hindquarters, from 1.21.9: the back rounds and stretches with
 * the legs, bends round turns and the hips sway, the chest carrying the front legs and the neck, the hindquarters the
 * hind legs and the tail), the tail a chain of three (the dock and two lengths of hair, cut from vanilla's tail by the equine
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
	/** The trunk's two end pieces (children of vanilla's body part, which keeps the barrel under the saddle). */
	public static final String CHEST = "horsingaround_chest";
	public static final String CROUP = "horsingaround_croup";
	/**
	 * Where the barrel starts and ends along the body and where its joints are, in the body part's frame (pixels; vanilla's
	 * body runs from -17 to 5 along it), and how far each end piece reaches into it.
	 */
	public static final float BARREL_FRONT = -10.0F;
	public static final float BARREL_BACK = 0.0F;
	public static final float SPINE_Y = -3.0F;
	public static final float OVERLAP = 1.0F;
	/** Where the body part sits in the model (vanilla's). */
	private static final float BODY_Y = 11.0F;
	private static final float BODY_Z = 5.0F;

	/** The trunk's pieces in a model (null for a body in one piece). */
	private static final class Trunk {
		final @Nullable ModelPart body;
		final @Nullable ModelPart chest;
		final @Nullable ModelPart croup;

		Trunk(final @Nullable ModelPart body, final @Nullable ModelPart chest, final @Nullable ModelPart croup) {
			this.body = body;
			this.chest = chest;
			this.croup = croup;
		}
	}

	/** Scratch for {@link #turn}; render thread only. */
	private static final float[] TURNED = new float[3];

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
		// (A spring thrown off by a frame that wasn't a number starts over from rest, rather than staying lost.)
		if (Float.isNaN(s.spinePitch[0] + s.spinePitch[1] + s.spineYaw[0] + s.spineYaw[1] + s.hipRoll + s.poll + s.earYaw + s.earPitch[0] + s.earPitch[1]
			+ s.tailPitch[0] + s.tailPitch[1] + s.tailPitch[2] + s.tailYaw[0] + s.tailYaw[1] + s.tailYaw[2] + s.boneSurge + s.boneYawAccel)) {
			rest(s);
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
		// The trunk: the back rounds as the hind legs reach under and opens as they push (the chest a little with the front
		// legs), bends round a turn, and at a walk the hindquarters swing and the hips roll with each hind leg's swing.
		final float flex = s.gaitShare[0] * SPINE_FLEX[0] + s.gaitShare[1] * SPINE_FLEX[1] + s.gaitShare[2] * SPINE_FLEX[2] + s.gaitShare[3] * SPINE_FLEX[3];
		final float bend = Mth.clamp(s.gaitYawRate * SPINE_TURN, -SPINE_BEND_MOST, SPINE_BEND_MOST) * Mth.clamp(forward, 0.0F, 1.0F);
		final float walking = s.gaitShare[0] * s.gaitSweep;
		// (A leg that couldn't be worked out this frame, its hoof not yet placed, counts as upright.)
		final float frontLeft = sound(s.gaitLegTurn[0]);
		final float frontRight = sound(s.gaitLegTurn[1]);
		final float hindLeft = sound(s.gaitLegTurn[2]);
		final float hindRight = sound(s.gaitLegTurn[3]);
		final float pitchTo0 = SPINE_FORE * flex * (frontLeft + frontRight) * 0.5F;
		final float pitchTo1 = SPINE_HIND * flex * (hindLeft + hindRight) * 0.5F;
		final float yawTo0 = bend;
		final float yawTo1 = -bend + SPINE_WALK * walking * (hindLeft - hindRight);
		final float rollTo = SPINE_HIP_ROLL * walking * (lifted(s, 2) - lifted(s, 3));
		final float spine = Mth.TWO_PI * SPINE_SWING;
		for (int n = 0; n < steps; n++) {
			s.spinePitchV[0] += (-spine * spine * (s.spinePitch[0] - pitchTo0) - 2.0F * SPINE_DAMPING * spine * s.spinePitchV[0]) * h;
			s.spinePitchV[1] += (-spine * spine * (s.spinePitch[1] - pitchTo1) - 2.0F * SPINE_DAMPING * spine * s.spinePitchV[1]) * h;
			s.spineYawV[0] += (-spine * spine * (s.spineYaw[0] - yawTo0) - 2.0F * SPINE_DAMPING * spine * s.spineYawV[0]) * h;
			s.spineYawV[1] += (-spine * spine * (s.spineYaw[1] - yawTo1) - 2.0F * SPINE_DAMPING * spine * s.spineYawV[1]) * h;
			s.hipRollV += (-spine * spine * (s.hipRoll - rollTo) - 2.0F * SPINE_DAMPING * spine * s.hipRollV) * h;
			for (int j = 0; j < 2; j++) {
				s.spinePitch[j] = Mth.clamp(s.spinePitch[j] + s.spinePitchV[j] * h, -SPINE_MOST, SPINE_MOST);
				s.spineYaw[j] = Mth.clamp(s.spineYaw[j] + s.spineYawV[j] * h, -SPINE_MOST, SPINE_MOST);
			}
			s.hipRoll = Mth.clamp(s.hipRoll + s.hipRollV * h, -SPINE_MOST, SPINE_MOST);
		}
	}

	/** {@code value}, or 0 for one that isn't a number. */
	private static float sound(final float value) {
		return Float.isNaN(value) ? 0.0F : value;
	}

	/** How far through its swing hoof {@code i} is lifted (0 standing, 1 at the top of its swing). */
	private static float lifted(final RideState s, final int i) {
		return s.hoofDown[i] ? 0.0F : Mth.sin(Mth.clamp(s.swingThrough[i], 0.0F, 1.0F) * Mth.PI);
	}

	/**
	 * Turns the trunk's end pieces on their joints this frame, and carries what hangs from each with it: the front legs and
	 * the neck with the chest, the hind legs and the tail with the hindquarters (the legs only where they are, so the legs'
	 * own work, after this, reaches the hooves from their tops as moved). Before the legs are worked out. {@code legs}: front
	 * left, front right, hind left, hind right.
	 */
	static void carry(final ModelPart root, final ModelPart[] legs, final ModelPart headParts, final ModelPart tailPart, final RideState s, final float upright) {
		final Trunk trunk = trunk(root);
		if (trunk.chest == null || trunk.croup == null || trunk.body == null || upright <= 0.0F) {
			return;
		}
		final float chestPitch = s.spinePitch[0] * upright;
		final float chestYaw = s.spineYaw[0] * upright;
		final float hindPitch = s.spinePitch[1] * upright;
		final float hindYaw = s.spineYaw[1] * upright;
		final float hipRoll = s.hipRoll * upright;
		trunk.chest.xRot += chestPitch;
		trunk.chest.yRot += chestYaw;
		trunk.croup.xRot += hindPitch;
		trunk.croup.yRot += hindYaw;
		trunk.croup.zRot += hipRoll;
		// (Each joint in the model's frame, where the body part puts it.)
		final float bodyX = trunk.body.x;
		final float bodyY = trunk.body.y;
		final float bodyZ = trunk.body.z;
		for (int i = 0; i < 4; i++) {
			if (legs[i] != null) {
				if (i < 2) {
					move(legs[i], bodyX, bodyY + SPINE_Y, bodyZ + BARREL_FRONT, chestPitch, chestYaw, 0.0F);
				} else {
					move(legs[i], bodyX, bodyY + SPINE_Y, bodyZ + BARREL_BACK, hindPitch, hindYaw, hipRoll);
				}
			}
		}
		move(headParts, bodyX, bodyY + SPINE_Y, bodyZ + BARREL_FRONT, chestPitch, chestYaw, 0.0F);
		headParts.xRot += chestPitch;
		headParts.yRot += chestYaw;
		// (The tail hangs from the body part: its joint in the body's frame.)
		move(tailPart, 0.0F, SPINE_Y, BARREL_BACK, hindPitch, hindYaw, hipRoll);
		tailPart.xRot += hindPitch;
		tailPart.yRot += hindYaw;
		tailPart.zRot += hipRoll;
	}

	/** Moves a part's place round the joint at (x, y, z) by a turn of the piece it hangs from (as a model part turns: z, y, x). */
	private static void move(final ModelPart part, final float x, final float y, final float z, final float pitch, final float yaw, final float roll) {
		turn(part.x - x, part.y - y, part.z - z, pitch, yaw, roll);
		part.x = x + TURNED[0];
		part.y = y + TURNED[1];
		part.z = z + TURNED[2];
	}

	/** (x, y, z) turned by pitch about x, then yaw about y, then roll about z, into {@link #TURNED}. */
	private static void turn(final float x, final float y, final float z, final float pitch, final float yaw, final float roll) {
		final float cp = Mth.cos(pitch);
		final float sp = Mth.sin(pitch);
		final float y1 = y * cp - z * sp;
		final float z1 = y * sp + z * cp;
		final float cy = Mth.cos(yaw);
		final float sy = Mth.sin(yaw);
		final float x2 = x * cy + z1 * sy;
		final float z2 = -x * sy + z1 * cy;
		final float cr = Mth.cos(roll);
		final float sr = Mth.sin(roll);
		TURNED[0] = x2 * cr - y1 * sr;
		TURNED[1] = x2 * sr + y1 * cr;
		TURNED[2] = z2;
	}

	//? if >=1.21.9 {
	/**
	 * Gives the trunk's three pieces vanilla's body texture exactly as on its one box (each piece's own box would map the
	 * whole texture onto itself): every corner takes the texture coordinates vanilla's box has at that spot on that face;
	 * the faces where the pieces meet, inside the body, are left out. Once, as the model is built.
	 */
	public static void remap(final ModelPart root) {
		final Trunk trunk = trunk(root);
		if (trunk.body == null || trunk.chest == null || trunk.croup == null) {
			return;
		}
		final ModelPart[] pieces = {trunk.body, trunk.chest, trunk.croup};
		// The three together are vanilla's box (grown as it is): its extent, from their corners in the body's frame.
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		float maxZ = -Float.MAX_VALUE;
		for (final ModelPart piece : pieces) {
			final PartPose at = piece == trunk.body ? PartPose.ZERO : piece.getInitialPose();
			for (final ModelPart.Cube cube : ((PartData) (Object) piece).horsingaround$cubes()) {
				for (final ModelPart.Polygon polygon : cube.polygons) {
					for (final ModelPart.Vertex vertex : polygon.vertices()) {
						minX = Math.min(minX, vertex.x() + at.x());
						minY = Math.min(minY, vertex.y() + at.y());
						minZ = Math.min(minZ, vertex.z() + at.z());
						maxX = Math.max(maxX, vertex.x() + at.x());
						maxY = Math.max(maxY, vertex.y() + at.y());
						maxZ = Math.max(maxZ, vertex.z() + at.z());
					}
				}
			}
		}
		final float width = 10.0F;
		final float height = 10.0F;
		final float depth = 22.0F;
		final ModelPart.Cube whole = new ModelPart.Cube(0, 32, -5.0F, -8.0F, -17.0F, width, height, depth, (maxX - minX - width) * 0.5F,
			(maxY - minY - height) * 0.5F, (maxZ - minZ - depth) * 0.5F, false, 64.0F, 64.0F, java.util.EnumSet.allOf(net.minecraft.core.Direction.class));
		for (final ModelPart piece : pieces) {
			final PartPose at = piece == trunk.body ? PartPose.ZERO : piece.getInitialPose();
			for (final ModelPart.Cube cube : ((PartData) (Object) piece).horsingaround$cubes()) {
				for (final ModelPart.Polygon polygon : cube.polygons) {
					final ModelPart.Vertex[] vertices = polygon.vertices();
					final float faceZ = vertices[0].z() + at.z();
					if (Math.abs(polygon.normal().z()) > 0.5F && faceZ > minZ + 0.01F && faceZ < maxZ - 0.01F) {
						// (A face inside the body, where the pieces meet: none.)
						for (int v = 1; v < vertices.length; v++) {
							vertices[v] = vertices[0];
						}
						continue;
					}
					final ModelPart.Polygon like = facing(whole, polygon);
					if (like == null) {
						continue;
					}
					for (int v = 0; v < vertices.length; v++) {
						vertices[v] = uv(like, vertices[v], at.x(), at.y(), at.z());
					}
				}
			}
		}
	}

	/** The face of {@code cube} that faces the way {@code polygon} does. */
	private static ModelPart.@Nullable Polygon facing(final ModelPart.Cube cube, final ModelPart.Polygon polygon) {
		for (final ModelPart.Polygon face : cube.polygons) {
			if (face.normal().dot(polygon.normal()) > 0.9F) {
				return face;
			}
		}
		return null;
	}

	/**
	 * A corner of a piece (offset by its place in the body) with the texture coordinates {@code face}, a face of vanilla's
	 * box, has at that spot: across a box face the coordinates go straight from one edge to the other.
	 */
	private static ModelPart.Vertex uv(final ModelPart.Polygon face, final ModelPart.Vertex vertex, final float dx, final float dy, final float dz) {
		final ModelPart.Vertex[] corners = face.vertices();
		final ModelPart.Vertex origin = corners[0];
		float u = origin.u();
		float v = origin.v();
		final float[] at = {vertex.x() + dx, vertex.y() + dy, vertex.z() + dz};
		final float[] from = {origin.x(), origin.y(), origin.z()};
		// (Along each of the face's two edges from its first corner: the corner that differs from it in that one way.)
		for (int axis = 0; axis < 3; axis++) {
			for (final ModelPart.Vertex corner : corners) {
				final float[] to = {corner.x(), corner.y(), corner.z()};
				final float along = to[axis] - from[axis];
				if (Math.abs(along) > 1.0E-4F && Math.abs(to[(axis + 1) % 3] - from[(axis + 1) % 3]) < 1.0E-4F
					&& Math.abs(to[(axis + 2) % 3] - from[(axis + 2) % 3]) < 1.0E-4F) {
					final float share = (at[axis] - from[axis]) / along;
					u += (corner.u() - origin.u()) * share;
					v += (corner.v() - origin.v()) * share;
					break;
				}
			}
		}
		return vertex.remap(u, v);
	}
	//?}

	/** Hides the trunk's end pieces on a model an animation pack draws (it draws a body of its own round vanilla's). */
	public static void hideTrunk(final ModelPart root) {
		final Trunk trunk = trunk(root);
		if (trunk.chest != null) {
			trunk.chest.visible = false;
		}
		if (trunk.croup != null) {
			trunk.croup.visible = false;
		}
	}

	private static Trunk trunk(final ModelPart root) {
		final PartData data = (PartData) (Object) root;
		if (data.horsingaround$bones() instanceof Trunk trunk) {
			return trunk;
		}
		final ModelPart body = data.horsingaround$children().get("body");
		final Map<String, ModelPart> pieces = body == null ? Map.of() : ((PartData) (Object) body).horsingaround$children();
		final Trunk trunk = new Trunk(body, pieces.get(CHEST), pieces.get(CROUP));
		data.horsingaround$setBones(trunk);
		return trunk;
	}

	/** All the bones' springs at rest. */
	private static void rest(final RideState s) {
		for (int j = 0; j < 3; j++) {
			s.tailPitch[j] = s.tailPitchV[j] = s.tailYaw[j] = s.tailYawV[j] = 0.0F;
		}
		for (int j = 0; j < 2; j++) {
			s.spinePitch[j] = s.spinePitchV[j] = s.spineYaw[j] = s.spineYawV[j] = 0.0F;
			s.earPitch[j] = s.earPitchV[j] = 0.0F;
		}
		s.hipRoll = s.hipRollV = s.poll = s.pollV = s.earYaw = s.earYawV = 0.0F;
		s.boneSurge = s.boneYawAccel = 0.0F;
		s.boneSpeed = 0.0F;
		s.boneYawRate = 0.0F;
		s.boneNeckSpeed = 0.0F;
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
