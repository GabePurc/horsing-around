package dev.horsingaround.client.render;

import dev.horsingaround.client.mixin.ModelPartAccessor;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * A horse model's leg as vanilla draws it: one rigid box, turned about its top. Models pivot legs in different places
 * (vanilla at the top of the leg, Fresh Animations near the hoof, animating the stride by moving the hoof and turning the
 * leg about it), so every swing here turns a leg about the top of its box, moving the pivot to keep the top where the
 * model put it; and a leg is shortened by drawing it up into the body along its own length (the way a horse folds at the
 * shoulder, elbow and knee). The model's boxes are only measured, never changed. Render thread only.
 */
public final class Legs {
	/** A leg's shape, in the leg part's own frame (model pixels, y down, z toward the tail). */
	public static final class Leg {
		/** The middle of the box across, and half its depth along z. */
		final float soleX;
		final float soleHalf;
		/** The middle of the top of the box and of its sole. */
		final float hipY;
		final float soleY;
		final float z;
		final float length;

		Leg(final float soleX, final float soleHalf, final float hipY, final float soleY, final float z) {
			this.soleX = soleX;
			this.soleHalf = soleHalf;
			this.hipY = hipY;
			this.soleY = soleY;
			this.z = z;
			this.length = soleY - hipY;
		}
	}

	/** Legs by part (the part the model swings); a part with no leg box maps to NONE. Models are rebuilt on reload. */
	private static final Map<ModelPart, Leg> LEGS = new WeakHashMap<>();
	private static final Leg NONE = new Leg(0.0F, 0.0F, 0.0F, 1.0F, 0.0F);
	/** Frames a leg was drawn up onto higher ground (for tests). */
	public static int drawnFrames;

	private Legs() {
	}

	/** The leg's shape (measured the first time); null if the part has no leg box. */
	public static @Nullable Leg of(final ModelPart part) {
		Leg leg = LEGS.get(part);
		if (leg == null) {
			leg = find(part, 0.0F, 0.0F, 0.0F, 0);
			LEGS.put(part, leg == null ? NONE : leg);
		}
		return leg == NONE ? null : leg;
	}

	/** Turns the leg part to {@code xRot} about the top of the leg, which stays where it was. */
	public static void swing(final ModelPart part, final Leg leg, final float xRot) {
		float cos = Mth.cos(part.xRot);
		float sin = Mth.sin(part.xRot);
		final float topY = part.y + leg.hipY * cos - leg.z * sin;
		final float topZ = part.z + leg.hipY * sin + leg.z * cos;
		cos = Mth.cos(xRot);
		sin = Mth.sin(xRot);
		part.xRot = xRot;
		part.y = topY - (leg.hipY * cos - leg.z * sin);
		part.z = topZ - (leg.hipY * sin + leg.z * cos);
	}

	/** Draws the leg {@code pixels} up into the body along its own length. */
	public static void draw(final ModelPart part, final float pixels) {
		part.y -= pixels * Mth.cos(part.xRot);
		part.z -= pixels * Mth.sin(part.xRot);
	}

	/**
	 * Finds the leg's box in the part or a part under it (offset {@code dx}, {@code dy}, {@code dz} from the leg part, not
	 * turned): the tallest six-sided box.
	 */
	private static @Nullable Leg find(final ModelPart part, final float dx, final float dy, final float dz, final int depth) {
		final ModelPartAccessor access = (ModelPartAccessor) (Object) part;
		final List<ModelPart.Cube> cubes = access.horsingaround$cubes();
		ModelPart.Cube best = null;
		float bestHeight = 0.0F;
		for (final ModelPart.Cube cube : cubes) {
			final float height = extent(cube, false, 1) - extent(cube, true, 1);
			if (cube.polygons.length == 6 && height > bestHeight) {
				best = cube;
				bestHeight = height;
			}
		}
		if (best != null) {
			final float middleZ = (extent(best, true, 2) + extent(best, false, 2)) * 0.5F;
			return new Leg(
				dx + (extent(best, true, 0) + extent(best, false, 0)) * 0.5F, (extent(best, false, 2) - extent(best, true, 2)) * 0.5F, dy + extent(best, true, 1),
				dy + extent(best, false, 1), dz + middleZ
			);
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

	/**
	 * A box's least or greatest x, y or z (axis 0, 1, 2), as it was built before any growing (an armour layer's box is grown
	 * a little, and every layer's legs must measure the same); from its corners if it doesn't say.
	 */
	private static float extent(final ModelPart.Cube cube, final boolean least, final int axis) {
		if (cube.maxX > cube.minX && cube.maxY > cube.minY && cube.maxZ > cube.minZ) {
			return axis == 0 ? (least ? cube.minX : cube.maxX) : axis == 1 ? (least ? cube.minY : cube.maxY) : least ? cube.minZ : cube.maxZ;
		}
		float out = least ? Float.MAX_VALUE : -Float.MAX_VALUE;
		for (final ModelPart.Polygon face : cube.polygons) {
			for (final ModelPart.Vertex vertex : face.vertices()) {
				final float value = axis == 0 ? vertex.x() : axis == 1 ? vertex.y() : vertex.z();
				out = least ? Math.min(out, value) : Math.max(out, value);
			}
		}
		return out;
	}
}
