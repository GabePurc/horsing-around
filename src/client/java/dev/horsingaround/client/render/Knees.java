package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.client.mixin.ModelPartAccessor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Jointed legs, turned about the top. A horse model's leg is one rigid box (vanilla's and Fresh Animations' alike), but
 * models pivot it in different places: vanilla at the top of the leg, Fresh Animations near the hoof (it animates the
 * stride by moving the hoof and turning the leg about it). So every swing here turns a leg about the top of its box, moving
 * the pivot to keep the top where the model put it, whatever the model's pivot.
 *
 * <p>The first time a leg is posed its box is cut into the upper leg, which stays on the part the model swings, the
 * cannon on a new child part that bends at the knee, and the hoof on a child of that which bends at the fetlock. The
 * texture is cut with it (each piece capped with the leg's end faces), so the leg looks as it did until it bends; each
 * piece reaches KNEE_OVERLAP pixels up into the one above so no gap opens on the outside of a bend. Then {@link #plant}
 * stands a hoof flat on a point with the knee bent the way a horse's is (front knees jut forward, hind hocks back):
 * two-bone inverse kinematics from the top of the leg to the fetlock. Render thread only.
 */
public final class Knees {
	/** A leg's shape, in the leg part's own frame (model pixels, y down, z toward the tail). */
	public static final class Leg {
		/** The cannon (bends at the knee) and the hoof (at the fetlock); null if the leg's box was too short to cut. */
		final @Nullable ModelPart lower;
		final @Nullable ModelPart hoof;
		/** The sole's middle across, and half its depth along the leg part's z. */
		final float soleX;
		final float soleHalf;
		final float hipY;
		final float hipZ;
		final float kneeY;
		final float kneeZ;
		final float fetlockY;
		final float fetlockZ;
		final float soleY;
		final float soleZ;
		final float upper;
		final float lowerLength;
		final float hoofLength;
		/** Directions (radians from straight down toward the tail) of the upper leg (top to knee) and cannon (knee to fetlock). */
		final float upperAngle;
		final float lowerAngle;

		Leg(
			final @Nullable ModelPart lower, final @Nullable ModelPart hoof, final float soleX, final float soleHalf, final float hipY, final float kneeY,
			final float fetlockY, final float soleY, final float z
		) {
			this.lower = lower;
			this.hoof = hoof;
			this.soleX = soleX;
			this.soleHalf = soleHalf;
			this.hipY = hipY;
			this.hipZ = z;
			this.kneeY = kneeY;
			this.kneeZ = z;
			this.fetlockY = fetlockY;
			this.fetlockZ = z;
			this.soleY = soleY;
			this.soleZ = z;
			this.upper = kneeY - hipY;
			this.lowerLength = fetlockY - kneeY;
			this.hoofLength = soleY - fetlockY;
			this.upperAngle = 0.0F;
			this.lowerAngle = 0.0F;
		}
	}

	/** Legs by part (the part the model swings); a part with no leg box maps to NONE. Models are rebuilt on reload. */
	private static final Map<ModelPart, Leg> LEGS = new WeakHashMap<>();
	private static final Leg NONE = new Leg(null, null, 0.0F, 0.0F, 0.0F, 0.5F, 0.75F, 1.0F, 0.0F);
	/** Legs cut so far, and frames a knee was bent (for tests). */
	public static int cut;
	public static int bentFrames;
	/** The last plant's fetlock target (leg part's parent frame, y and z), its reach and the reach asked for (for tests). */
	public static final float[] TARGET = new float[4];

	private Knees() {
	}

	/** The leg's shape, cutting its knee the first time; null if the part has no leg box. */
	public static @Nullable Leg of(final ModelPart part) {
		Leg leg = LEGS.get(part);
		if (leg == null) {
			leg = find(part, 0.0F, 0.0F, 0.0F, 0);
			LEGS.put(part, leg == null ? NONE : leg);
		}
		return leg == NONE ? null : leg;
	}

	/** Straightens the knee and fetlock (models are shared between horses, and nothing else resets the lower pieces). */
	public static void straighten(final Leg leg) {
		if (leg.lower != null) {
			leg.lower.xRot = 0.0F;
		}
		if (leg.hoof != null) {
			leg.hoof.xRot = 0.0F;
		}
	}

	/** Turns the leg part to {@code xRot} about the top of the leg, which stays where it was. */
	public static void swing(final ModelPart part, final Leg leg, final float xRot) {
		float cos = Mth.cos(part.xRot);
		float sin = Mth.sin(part.xRot);
		final float topY = part.y + leg.hipY * cos - leg.hipZ * sin;
		final float topZ = part.z + leg.hipY * sin + leg.hipZ * cos;
		cos = Mth.cos(xRot);
		sin = Mth.sin(xRot);
		part.xRot = xRot;
		part.y = topY - (leg.hipY * cos - leg.hipZ * sin);
		part.z = topZ - (leg.hipY * sin + leg.hipZ * cos);
	}

	/**
	 * Stands the hoof flat on the ground {@code rise} model pixels up in the world (down in the world being {@code down}
	 * radians from the leg part's frame's down, toward its tail), {@code shift} pixels back along the leg and {@code sink} pixels down the leg part's frame from where the
	 * straight leg has its sole, the top of the
	 * leg staying put: the knee bends (front knees jut forward, hind hocks back), the leg turns at the top to suit, and
	 * the hoof turns at the fetlock to stand upright ({@code level} of the way: 0 leaves it in line with the leg). A leg
	 * without a knee stays as it is.
	 */
	public static void plant(
		final ModelPart part, final Leg leg, final float rise, final float shift, final float down, final float sink, final float level, final boolean front
	) {
		if (!solve(part, leg, rise, shift, down, sink, front)) {
			return;
		}
		swing(part, leg, solvedUpper - leg.upperAngle);
		leg.lower.xRot = solvedLower - part.xRot - leg.lowerAngle;
		leg.hoof.xRot = (down - solvedLower) * level;
		if (solvedBend > 1.0E-3F) {
			bentFrames++;
		}
	}

	/**
	 * Where {@link #plant} would put the knee (the leg part's parent frame, y then z, into {@code out}), without moving
	 * anything; false if it wouldn't bend the leg.
	 */
	public static boolean knee(
		final ModelPart part, final Leg leg, final float rise, final float shift, final float down, final float sink, final boolean front, final float[] out
	) {
		if (!solve(part, leg, rise, shift, down, sink, front)) {
			return false;
		}
		out[0] = solvedKneeY;
		out[1] = solvedKneeZ;
		return true;
	}

	// The last solve (render thread).
	private static float solvedUpper;
	private static float solvedLower;
	private static float solvedBend;
	private static float solvedKneeY;
	private static float solvedKneeZ;

	/** Two-bone IK from the top of the leg to the fetlock over the hoof's ground; false if there is nothing to do. */
	private static boolean solve(
		final ModelPart part, final Leg leg, final float rise, final float shift, final float down, final float sink, final boolean front
	) {
		if (leg.lower == null || leg.hoof == null || rise <= 0.0F && sink <= 0.0F && shift == 0.0F) {
			return false;
		}
		final float cos = Mth.cos(part.xRot);
		final float sin = Mth.sin(part.xRot);
		final float topY = part.y + leg.hipY * cos - leg.hipZ * sin;
		final float topZ = part.z + leg.hipY * sin + leg.hipZ * cos;
		final float soleY = part.y + leg.soleY * cos - leg.soleZ * sin;
		final float soleZ = part.z + leg.soleY * sin + leg.soleZ * cos;
		// The hoof stands on its ground, upright; moved back along the leg by the shift.
		final float targetY = soleY + sink - (rise + leg.hoofLength) * Mth.cos(down) - shift * sin;
		final float targetZ = soleZ - (rise + leg.hoofLength) * Mth.sin(down) + shift * cos;
		final float a = leg.upper;
		final float b = leg.lowerLength;
		final float dy = targetY - topY;
		final float dz = targetZ - topZ;
		final float reach = Mth.clamp(Mth.length(dy, dz), Math.abs(a - b) + 0.01F, a + b);
		// The angle at the top between the line to the fetlock and the upper leg.
		solvedBend = (float) Math.acos(Mth.clamp((a * a + reach * reach - b * b) / (2.0F * a * reach), -1.0F, 1.0F));
		final float toFetlock = (float) Math.atan2(dz, dy);
		// Turned toward the head (front knees jut forward) or the tail (hind hocks jut back).
		solvedUpper = front ? toFetlock - solvedBend : toFetlock + solvedBend;
		solvedKneeY = topY + a * Mth.cos(solvedUpper);
		solvedKneeZ = topZ + a * Mth.sin(solvedUpper);
		solvedLower = (float) Math.atan2(targetZ - solvedKneeZ, targetY - solvedKneeY);
		TARGET[0] = targetY;
		TARGET[1] = targetZ;
		TARGET[2] = reach;
		TARGET[3] = Mth.length(dy, dz);
		return true;
	}

	/**
	 * Finds the leg's box in the part or a part under it (offset {@code dx}, {@code dy}, {@code dz} from the leg part, not
	 * turned): the tallest six-sided box. Cuts its knee if it is long enough.
	 */
	private static @Nullable Leg find(final ModelPart part, final float dx, final float dy, final float dz, final int depth) {
		final ModelPartAccessor access = (ModelPartAccessor) (Object) part;
		final List<ModelPart.Cube> cubes = access.horsingaround$cubes();
		int best = -1;
		float bestHeight = 0.0F;
		for (int i = 0; i < cubes.size(); i++) {
			final ModelPart.Cube cube = cubes.get(i);
			final float height = extent(cube, false, false) - extent(cube, true, false);
			if (cube.polygons.length == 6 && height > bestHeight) {
				best = i;
				bestHeight = height;
			}
		}
		if (best >= 0) {
			return shape(access, cubes, best, dx, dy, dz);
		}
		if (depth < 2) {
			for (final ModelPart child : access.horsingaround$children().values()) {
				final PartPose pose = child.getInitialPose();
				if (pose.xRot() == 0.0F && pose.yRot() == 0.0F && pose.zRot() == 0.0F) {
					final Leg leg = find(child, dx + pose.x(), dy + pose.y(), dz + pose.z(), depth + 1);
					if (leg != null) {
						return leg;
					}
				}
			}
		}
		return null;
	}

	/** A box's least or greatest y (or z, or x), from its corners (an armour layer's box is grown a little). */
	private static float extent(final ModelPart.Cube cube, final boolean least, final boolean z) {
		return extent(cube, least, z ? 2 : 1);
	}

	private static float extent(final ModelPart.Cube cube, final boolean least, final int axis) {
		float out = least ? Float.MAX_VALUE : -Float.MAX_VALUE;
		for (final ModelPart.Polygon face : cube.polygons) {
			for (final ModelPart.Vertex vertex : face.vertices()) {
				final float value = axis == 0 ? vertex.x() : axis == 1 ? vertex.y() : vertex.z();
				out = least ? Math.min(out, value) : Math.max(out, value);
			}
		}
		return out;
	}

	private static Leg shape(
		final ModelPartAccessor access, final List<ModelPart.Cube> cubes, final int index, final float dx, final float dy, final float dz
	) {
		final ModelPart.Cube cube = cubes.get(index);
		final float top = extent(cube, true, false);
		final float bottom = extent(cube, false, false);
		final float middleZ = (extent(cube, true, true) + extent(cube, false, true)) * 0.5F;
		final float soleX = dx + (extent(cube, true, 0) + extent(cube, false, 0)) * 0.5F;
		final float soleHalf = (extent(cube, false, true) - extent(cube, true, true)) * 0.5F;
		// (On whole pixels from the top, so each piece's texture lines up with the box's as Sodium redraws it.)
		final float kneeY = top + Math.round((bottom - top) * KNEE_SHARE);
		final float fetlockY = bottom - HOOF_PIXELS;
		if (bottom - top < KNEE_MIN_LEG) {
			return new Leg(null, null, soleX, soleHalf, dy + top, dy + kneeY, dy + fetlockY, dy + bottom, dz + middleZ);
		}
		// (The upper leg reaches HIP_EXTEND further up into the body, so a big swing at the top shows no gap there.)
		final Texture texture = texture(cube, top);
		final ModelPart.Cube upper = slice(cube, texture, top, bottom, top - HIP_EXTEND, kneeY, 0.0F, 0.0F);
		final ModelPart.Cube cannon = slice(cube, texture, top, bottom, kneeY - KNEE_OVERLAP, fetlockY, kneeY, middleZ);
		final ModelPart.Cube hoof = slice(cube, texture, top, bottom, fetlockY - KNEE_OVERLAP, bottom, fetlockY, middleZ);
		final ModelPart hoofPart = new ModelPart(List.of(hoof), new HashMap<>());
		final PartPose hoofPose = PartPose.offset(0.0F, fetlockY - kneeY, 0.0F);
		hoofPart.setInitialPose(hoofPose);
		hoofPart.loadPose(hoofPose);
		final Map<String, ModelPart> cannonChildren = new HashMap<>();
		cannonChildren.put("horsingaround_hoof", hoofPart);
		final ModelPart cannonPart = new ModelPart(List.of(cannon), cannonChildren);
		final PartPose pose = PartPose.offset(0.0F, kneeY, middleZ);
		cannonPart.setInitialPose(pose);
		cannonPart.loadPose(pose);
		final List<ModelPart.Cube> kept = new ArrayList<>(cubes);
		kept.set(index, upper);
		access.horsingaround$setCubes(kept);
		final Map<String, ModelPart> children = new HashMap<>(access.horsingaround$children());
		children.put("horsingaround_lower_leg", cannonPart);
		access.horsingaround$setChildren(children);
		cut++;
		return new Leg(cannonPart, hoofPart, soleX, soleHalf, dy + top, dy + kneeY, dy + fetlockY, dy + bottom, dz + middleZ);
	}

	/**
	 * The piece of a box (its corners {@code top} to {@code bottom}, model pixels, y down) between heights {@code from}
	 * and {@code to}, its texture cut to match and capped at both ends with the box's own end faces, moved so (0,
	 * {@code originY}, {@code originZ}) is its origin.
	 */
	private static ModelPart.Cube slice(
		final ModelPart.Cube cube, final Texture texture, final float top, final float bottom, final float from, final float to, final float originY,
		final float originZ
	) {
		final ModelPart.Polygon[] faces = new ModelPart.Polygon[cube.polygons.length];
		for (int f = 0; f < faces.length; f++) {
			final ModelPart.Polygon face = cube.polygons[f];
			final ModelPart.Vertex[] vertices = face.vertices();
			boolean allTop = true;
			boolean allBottom = true;
			for (final ModelPart.Vertex vertex : vertices) {
				allTop &= vertex.y() == top;
				allBottom &= vertex.y() == bottom;
			}
			final ModelPart.Vertex[] cut = new ModelPart.Vertex[vertices.length];
			for (int i = 0; i < vertices.length; i++) {
				final ModelPart.Vertex vertex = vertices[i];
				if (allTop || allBottom) {
					// An end: the cap, at this piece's own end.
					cut[i] = new ModelPart.Vertex(vertex.x(), (allTop ? from : to) - originY, vertex.z() - originZ, vertex.u(), vertex.v());
					continue;
				}
				// A side: each corner slides along its edge to the cut, its texture with it.
				final ModelPart.Vertex other = partner(vertices, vertex);
				final ModelPart.Vertex high = vertex.y() <= other.y() ? vertex : other;
				final ModelPart.Vertex low = high == vertex ? other : vertex;
				final float y = vertex.y() == top ? from : to;
				final float t = Mth.clamp((y - high.y()) / (low.y() - high.y()), 0.0F, 1.0F);
				cut[i] = new ModelPart.Vertex(vertex.x(), y - originY, vertex.z() - originZ, Mth.lerp(t, high.u(), low.u()), Mth.lerp(t, high.v(), low.v()));
			}
			faces[f] = new ModelPart.Polygon(cut, face.normal());
		}
		// Built as the box's own slice (its texture net moved down to the slice, so a renderer that redraws boxes from how
		// they were built, as Sodium does, draws the same), then given the exact cut faces.
		final EnumSet<Direction> sides = EnumSet.noneOf(Direction.class);
		for (int i = 0; i < faces.length; i++) {
			sides.add(Direction.values()[i]);
		}
		final ModelPart.Cube piece = new ModelPart.Cube(
			texture.u, texture.v + Math.round(from - top), texture.minX, from - originY + texture.growY, texture.minZ - originZ, texture.width,
			to - from - 2.0F * texture.growY, texture.depth, texture.growX, texture.growY, texture.growZ, texture.mirror, texture.textureWidth,
			texture.textureHeight, sides
		);
		if (piece.polygons.length == faces.length) {
			System.arraycopy(faces, 0, piece.polygons, 0, faces.length);
		}
		return piece;
	}

	/** How a box was built: its texture net's corner, mirrored or not, the texture's size, and how far it was grown. */
	private static final class Texture {
		int u;
		int v;
		boolean mirror;
		float textureWidth;
		float textureHeight;
		float minX;
		float minZ;
		float width;
		float depth;
		float growX;
		float growY;
		float growZ;
	}

	/** Works out how {@code cube} was built (vanilla's box layout) from its corners and texture coordinates. */
	private static Texture texture(final ModelPart.Cube cube, final float top) {
		final Texture texture = new Texture();
		final float x0 = extent(cube, true, 0);
		final float x1 = extent(cube, false, 0);
		final float z0 = extent(cube, true, 2);
		final float z1 = extent(cube, false, 2);
		final float fieldWidth = cube.maxX - cube.minX;
		final float fieldDepth = cube.maxZ - cube.minZ;
		final boolean fields = fieldWidth > 0.0F && fieldDepth > 0.0F && fieldWidth <= x1 - x0 + 1.0E-3F;
		texture.width = fields ? fieldWidth : x1 - x0;
		texture.depth = fields ? fieldDepth : z1 - z0;
		texture.growX = fields ? (x1 - x0 - fieldWidth) * 0.5F : 0.0F;
		texture.growZ = fields ? (z1 - z0 - fieldDepth) * 0.5F : 0.0F;
		texture.growY = texture.growX;
		texture.minX = x0 + texture.growX;
		texture.minZ = z0 + texture.growZ;
		float uMin = Float.MAX_VALUE;
		float vMin = Float.MAX_VALUE;
		float topU0 = Float.MAX_VALUE;
		float topU1 = -Float.MAX_VALUE;
		float topV0 = Float.MAX_VALUE;
		float topV1 = -Float.MAX_VALUE;
		ModelPart.Polygon leftmost = null;
		for (final ModelPart.Polygon face : cube.polygons) {
			boolean atTop = true;
			for (final ModelPart.Vertex vertex : face.vertices()) {
				atTop &= vertex.y() == top;
				if (vertex.u() < uMin) {
					uMin = vertex.u();
					leftmost = face;
				}
				vMin = Math.min(vMin, vertex.v());
			}
			if (atTop) {
				for (final ModelPart.Vertex vertex : face.vertices()) {
					topU0 = Math.min(topU0, vertex.u());
					topU1 = Math.max(topU1, vertex.u());
					topV0 = Math.min(topV0, vertex.v());
					topV1 = Math.max(topV1, vertex.v());
				}
			}
		}
		// The box's top face spans its width and depth on the texture.
		texture.textureWidth = topU1 > topU0 ? texture.width / (topU1 - topU0) : 64.0F;
		texture.textureHeight = topV1 > topV0 ? texture.depth / (topV1 - topV0) : 64.0F;
		texture.u = Math.round(uMin * texture.textureWidth);
		texture.v = Math.round(vMin * texture.textureHeight);
		// The net's leftmost column is the box's west side; mirrored, that side is drawn at the box's east.
		boolean east = leftmost != null;
		if (leftmost != null) {
			for (final ModelPart.Vertex vertex : leftmost.vertices()) {
				east &= vertex.x() == x1;
			}
		}
		texture.mirror = east;
		return texture;
	}

	/** The corner at the other end of a side's vertical edge from {@code vertex}. */
	private static ModelPart.Vertex partner(final ModelPart.Vertex[] vertices, final ModelPart.Vertex vertex) {
		for (final ModelPart.Vertex other : vertices) {
			if (other != vertex && other.x() == vertex.x() && other.z() == vertex.z()) {
				return other;
			}
		}
		return vertex;
	}
}
