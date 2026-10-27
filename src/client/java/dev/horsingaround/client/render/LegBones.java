package dev.horsingaround.client.render;

import dev.horsingaround.ride.RideState;
import net.minecraft.client.model.geom.ModelPart;
//? if >=1.21.11 {
import net.minecraft.gizmos.Gizmos;
//?}
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * For play-testing (dev tools' F8, and the tests): draws the bones of every horse on the plain model's legs (ridden or
 * not), every frame just as it is drawn (user request, 2026-10-27: to see that the bones work). Per leg: the hip bone's fixed point (white),
 * the hidden hip bone to the shoulder or hip joint (orange), the hidden thigh bone to the elbow or stifle (yellow), and
 * the leg as drawn, from its top to the middle of its sole (aqua; in two pieces, the lower blue below the knee or hock); a red line where the shin's drawn top isn't where the
 * hidden bones put it (the ride's ground work moved the leg). The hoof's spot: green where it stands, magenta where it is
 * going to land. The rest of the horse's bones (see BodyBones): the neck to the poll and the head beyond it (pink),
 * the ears (light pink), the tail's three pieces (purple), and the back along the trunk's three pieces with their joints
 * (white). The right legs' bones are darker, so the far side's tell apart from the near side's where the two
 * overlap (side on, a pair in step). Debug only, so it may allocate. Needs gizmos, which came in 1.21.11; before, it draws nothing.
 */
public final class LegBones {
	/** Whether the bones are drawn. */
	public static boolean on;

	/** Per leg, this frame's chain from Strides (model pixels, in the root's frame): the leg's top, then the hip bone's fixed point, the joint and the shin's top, each against the leg's top (y, z). */
	private static final float[] CHAIN = new float[4 * 9];
	private static final boolean[] HAVE = new boolean[4];
	private static final ModelPart[] PARTS = new ModelPart[4];
	private static final Matrix4f ROOT = new Matrix4f();
	private static final Matrix4f MATRIX = new Matrix4f();
	private static final Vector3f POINT = new Vector3f();
	/** The ride and the moment (its legs' clock) last drawn: a horse is posed again for its saddle and armour in the same frame. */
	private static @Nullable RideState drawnRide;
	private static double drawnAt = Double.NaN;

	private LegBones() {
	}

	/** From Strides' solve, leg {@code i}: where its chain went this frame (see {@link #CHAIN}). */
	static void chain(
		final int i, final float topX, final float topY, final float topZ, final float rootY, final float rootZ, final float hipY, final float hipZ,
		final float jointY, final float jointZ
	) {
		final int at = i * 9;
		CHAIN[at] = topX;
		CHAIN[at + 1] = topY;
		CHAIN[at + 2] = topZ;
		CHAIN[at + 3] = rootY;
		CHAIN[at + 4] = rootZ;
		CHAIN[at + 5] = hipY;
		CHAIN[at + 6] = hipZ;
		CHAIN[at + 7] = jointY;
		CHAIN[at + 8] = jointZ;
		HAVE[i] = true;
	}

	/** Once the legs (front left, front right, hind left, hind right), neck and tail are posed for the frame: draws them. */
	public static void draw(
		final @Nullable RideState ride, final @Nullable ModelPart root, final @Nullable ModelPart leftFront, final @Nullable ModelPart rightFront,
		final @Nullable ModelPart leftHind, final @Nullable ModelPart rightHind, final @Nullable ModelPart headParts, final @Nullable ModelPart tail
	) {
		//? if >=1.21.11 {
		if (!on || ride == null || root == null || !ride.drawnPoseSet || !Strides.posed(ride)) {
			return;
		}
		if (ride.gaitPosedAt == drawnAt && ride == drawnRide) {
			return;
		}
		drawnAt = ride.gaitPosedAt;
		drawnRide = ride;
		ROOT.set(ride.drawnPose)
			.translate(root.x / 16.0F, root.y / 16.0F, root.z / 16.0F)
			.rotateZYX(root.zRot, root.yRot, root.xRot)
			.scale(root.xScale, root.yScale, root.zScale);
		PARTS[0] = leftFront;
		PARTS[1] = rightFront;
		PARTS[2] = leftHind;
		PARTS[3] = rightHind;
		for (int i = 0; i < 4; i++) {
			final ModelPart part = PARTS[i];
			final Legs.Leg leg = part == null ? null : Legs.of(part);
			if (leg == null || !HAVE[i]) {
				continue;
			}
			final int at = i * 9;
			final float x = CHAIN[at];
			final float y = CHAIN[at + 1];
			final float z = CHAIN[at + 2];
			final Vec3 fixed = model(ride, x, y + CHAIN[at + 3], z + CHAIN[at + 4]);
			final Vec3 hip = model(ride, x, y + CHAIN[at + 5], z + CHAIN[at + 6]);
			final Vec3 joint = model(ride, x, y + CHAIN[at + 7], z + CHAIN[at + 8]);
			// The shin as drawn: its part, turned, moved and drawn up as it is.
			MATRIX.set(ROOT)
				.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F)
				.rotateZYX(part.zRot, part.yRot, part.xRot)
				.scale(part.xScale, part.yScale, part.zScale);
			final Vec3 top = drawn(ride, leg.soleX, leg.hipY, leg.z);
			final Vec3 knee = drawn(ride, leg.soleX, leg.hipY + leg.upper, leg.z);
			Legs.lower(MATRIX, part);
			final Vec3 sole = drawn(ride, leg.soleX, leg.soleY, leg.z);
			// (Right legs, 1 and 3, in darker colours.)
			final boolean right = (i & 1) == 1;
			Gizmos.line(fixed, hip, right ? 0xFF8A4A10 : 0xFFFF9020, 3.0F).setAlwaysOnTop();
			Gizmos.line(hip, joint, right ? 0xFF8A7A20 : 0xFFFFE040, 3.0F).setAlwaysOnTop();
			Gizmos.line(top, knee, right ? 0xFF207A8A : 0xFF40E0FF, 4.0F).setAlwaysOnTop();
			if (leg.upper < leg.length) {
				Gizmos.line(knee, sole, right ? 0xFF2A4A9A : 0xFF6080FF, 4.0F).setAlwaysOnTop();
				Gizmos.point(knee, right ? 0xFF207A8A : 0xFF40E0FF, 6.0F).setAlwaysOnTop();
			}
			if (top.distanceToSqr(joint) > 1.0 / (32.0 * 32.0)) {
				Gizmos.line(joint, top, 0xFFFF3030, 2.0F).setAlwaysOnTop();
			}
			Gizmos.point(fixed, right ? 0xFF909090 : 0xFFFFFFFF, 7.0F).setAlwaysOnTop();
			Gizmos.point(hip, right ? 0xFF8A4A10 : 0xFFFF9020, 6.0F).setAlwaysOnTop();
			Gizmos.point(joint, right ? 0xFF8A7A20 : 0xFFFFE040, 6.0F).setAlwaysOnTop();
			if (ride.hoofDown[i]) {
				Gizmos.point(new Vec3(ride.hoofX[i], ride.hoofY[i], ride.hoofZ[i]), 0xFF40FF60, 8.0F).setAlwaysOnTop();
			} else {
				Gizmos.point(new Vec3(ride.landX[i], ride.landY[i], ride.landZ[i]), 0xFFFF40FF, 8.0F).setAlwaysOnTop();
			}
		}
		// The neck from the withers to the poll, the head from there to the muzzle, the ears, and the tail piece by piece.
		if (headParts != null) {
			final Matrix4f neck = child(ROOT, headParts);
			final Vec3 withers = at(ride, neck, 0.0F, 6.0F, 1.5F);
			final Vec3 poll = at(ride, neck, 0.0F, -6.0F, 4.0F);
			Gizmos.line(withers, poll, 0xFFFF80C0, 3.0F).setAlwaysOnTop();
			Gizmos.point(poll, 0xFFFF80C0, 6.0F).setAlwaysOnTop();
			final ModelPart skull = ((PartData) (Object) headParts).horsingaround$children().get("head");
			if (skull != null) {
				final Matrix4f head = child(neck, skull);
				Gizmos.line(poll, at(ride, head, 0.0F, -8.5F, -7.0F), 0xFFFF80C0, 3.0F).setAlwaysOnTop();
				for (final String name : new String[] {"left_ear", "right_ear"}) {
					final ModelPart ear = ((PartData) (Object) skull).horsingaround$children().get(name);
					final List<ModelPart.Cube> cubes = ear == null ? List.of() : ((PartData) (Object) ear).horsingaround$cubes();
					if (!cubes.isEmpty()) {
						final ModelPart.Cube cube = cubes.get(0);
						final Matrix4f frame = child(head, ear);
						final float x = (cube.minX + cube.maxX) * 0.5F;
						final float z = (cube.minZ + cube.maxZ) * 0.5F;
						Gizmos.line(at(ride, frame, x, cube.maxY, z), at(ride, frame, x, cube.minY, z), 0xFFFFC0E0, 3.0F).setAlwaysOnTop();
					}
				}
			}
		}
		final ModelPart body = ((PartData) (Object) root).horsingaround$children().get("body");
		if (body != null) {
			// The back: along the top of the chest, the barrel and the hindquarters, each piece as turned on its joint.
			final Matrix4f barrel = child(ROOT, body);
			final ModelPart chest = ((PartData) (Object) body).horsingaround$children().get(BodyBones.CHEST);
			final ModelPart croup = ((PartData) (Object) body).horsingaround$children().get(BodyBones.CROUP);
			final float top = -8.0F;
			Gizmos.line(at(ride, barrel, 0.0F, top, BodyBones.BARREL_FRONT), at(ride, barrel, 0.0F, top, BodyBones.BARREL_BACK), 0xFFF0F0F0, 3.0F).setAlwaysOnTop();
			if (chest != null && croup != null) {
				final Matrix4f front = child(barrel, chest);
				final Matrix4f back = child(barrel, croup);
				final float pieceTop = top - BodyBones.SPINE_Y;
				Gizmos.line(at(ride, front, 0.0F, pieceTop, -17.0F - BodyBones.BARREL_FRONT), at(ride, front, 0.0F, pieceTop, 0.0F), 0xFFF0F0F0, 3.0F).setAlwaysOnTop();
				Gizmos.line(at(ride, back, 0.0F, pieceTop, 0.0F), at(ride, back, 0.0F, pieceTop, 5.0F - BodyBones.BARREL_BACK), 0xFFF0F0F0, 3.0F).setAlwaysOnTop();
				Gizmos.point(at(ride, barrel, 0.0F, BodyBones.SPINE_Y, BodyBones.BARREL_FRONT), 0xFFF0F0F0, 6.0F).setAlwaysOnTop();
				Gizmos.point(at(ride, barrel, 0.0F, BodyBones.SPINE_Y, BodyBones.BARREL_BACK), 0xFFF0F0F0, 6.0F).setAlwaysOnTop();
			}
		}
		if (tail != null && body != null) {
			Matrix4f frame = child(child(ROOT, body), tail);
			Vec3 from = at(ride, frame, 0.0F, 0.0F, 2.0F);
			ModelPart piece = tail;
			float length = BodyBones.TAIL_DOCK;
			for (final String next : new String[] {BodyBones.TAIL_MID, BodyBones.TAIL_END, null}) {
				final ModelPart below = next == null ? null : ((PartData) (Object) piece).horsingaround$children().get(next);
				final Vec3 to = below != null ? at(ride, child(frame, below), 0.0F, 0.0F, 0.0F) : at(ride, frame, 0.0F, length, piece == tail ? 2.0F : 0.0F);
				Gizmos.line(from, to, 0xFFB060FF, 3.0F).setAlwaysOnTop();
				Gizmos.point(from, 0xFFB060FF, 5.0F).setAlwaysOnTop();
				if (below == null) {
					break;
				}
				frame = child(frame, below);
				from = to;
				piece = below;
				length = BodyBones.TAIL_HAIR;
			}
		}
		//?}
	}

	/** A part's frame in its parent's (debug only: it allocates). */
	private static Matrix4f child(final Matrix4f parent, final ModelPart part) {
		return new Matrix4f(parent).translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F).rotateZYX(part.zRot, part.yRot, part.xRot)
			.scale(part.xScale, part.yScale, part.zScale);
	}

	/** A point in {@code frame} (pixels) in the world. */
	private static Vec3 at(final RideState ride, final Matrix4f frame, final float x, final float y, final float z) {
		final Vector3f point = frame.transformPosition(x / 16.0F, y / 16.0F, z / 16.0F, new Vector3f());
		return new Vec3(ride.drawnCameraX + point.x, ride.drawnCameraY + point.y, ride.drawnCameraZ + point.z);
	}

	/** A point in the model root's frame (pixels) in the world. */
	private static Vec3 model(final RideState ride, final float x, final float y, final float z) {
		ROOT.transformPosition(x / 16.0F, y / 16.0F, z / 16.0F, POINT);
		return new Vec3(ride.drawnCameraX + POINT.x, ride.drawnCameraY + POINT.y, ride.drawnCameraZ + POINT.z);
	}

	/** A point in the leg part's frame (pixels), as the part is drawn ({@link #MATRIX}), in the world. */
	private static Vec3 drawn(final RideState ride, final float x, final float y, final float z) {
		MATRIX.transformPosition(x / 16.0F, y / 16.0F, z / 16.0F, POINT);
		return new Vec3(ride.drawnCameraX + POINT.x, ride.drawnCameraY + POINT.y, ride.drawnCameraZ + POINT.z);
	}
}
