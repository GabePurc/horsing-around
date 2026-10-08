package dev.horsingaround.gametest;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.horsingaround.client.mixin.ModelPartAccessor;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Measures the ridden horse's legs exactly as they are drawn, after the model and any animation pack (Fresh Animations)
 * and this mod posed them: hooked in after the model renderer draws a model (see the gametest {@code ModelFeatureRendererMixin}),
 * it walks the drawn parts with the same pose the renderer used. Per leg: how far the hoof's sole is off the ground under it,
 * whether the top of the leg is still up inside the body (a gap there is a leg come off), and which way the knee juts.
 * Test-only; nothing here runs in play.
 */
public final class LegProbe {
	/** Front left, front right, hind left, hind right (vanilla's part names, which animation packs keep). */
	public static final String[] LEGS = {"left_front_leg", "right_front_leg", "left_hind_leg", "right_hind_leg"};
	private static final String KNEE = "horsingaround_lower_leg";
	private static final String FETLOCK = "horsingaround_hoof";

	private static volatile boolean armed;
	private static volatile boolean wantTree;
	/** The parts' tree, written once when asked for. */
	public static volatile String tree;

	/** Frames measured since {@link #reset}. */
	public static int frames;
	/** Per leg, the last frame drawn: the hoof's lowest point above the ground under its sole (blocks; negative is sunk in). */
	public static final double[] gap = new double[4];
	/** How far the body's underside is in the ground at its deepest corner (blocks, the shortest way out). */
	public static double bodyInside;
	/** The same for the chest end of it alone (the rest is the hindquarters). */
	public static double chestInside;
	/** How far the rest of the leg (the knee, the cannon) is in the ground at its deepest corner (blocks, the shortest way out). */
	public static final double[] legInside = new double[4];
	/** How far the top of the leg is up inside the body, at its shallowest corner (blocks; negative, a gap shows). */
	public static final double[] hip = new double[4];
	/** How far the knee juts toward the head from the line from the top of the leg to the fetlock (blocks; NaN, no knee). */
	public static final double[] jut = new double[4];
	/** The sole's centre (world). */
	public static final double[][] sole = new double[4][3];
	/** Where the knee and the fetlock are (world; NaN, no knee). */
	public static final double[][] knee = new double[4][3];
	public static final double[][] fetlock = new double[4][3];
	/** The top of the leg (world). */
	public static final double[][] top = new double[4][3];

	// Scratch for a frame (render thread).
	private static final double[] lowest = new double[4];
	private static final double[] soleY = new double[4];
	private static final double[][] soleCorners = new double[4][12];
	private static final double[] topY = new double[4];
	private static final double[][] topCorners = new double[4][12];
	private static final double[] belly = new double[12];
	private static double bellyVolume;
	private static final Vector3f point = new Vector3f();
	private static final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private static final StringBuilder dump = new StringBuilder();
	private static Level level;

	private LegProbe() {
	}

	public static void arm(final boolean on) {
		armed = on;
		reset();
	}

	public static void reset() {
		frames = 0;
	}

	public static void requestTree() {
		tree = null;
		wantTree = true;
	}

	/** After a model is drawn: if it is the ridden horse's body, measures its legs. */
	public static void measure(final Model<?> model, final Object state, final PoseStack.Pose pose) {
		if (!armed || !(state instanceof LivingEntityRenderState s)) {
			return;
		}
		final Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !(mc.player.getVehicle() instanceof AbstractHorse horse) || Math.abs(s.x - horse.getX()) > 1.0
			|| Math.abs(s.z - horse.getZ()) > 1.0 || !(mc.getEntityRenderDispatcher().getRenderer(horse) instanceof LivingEntityRenderer<?, ?, ?> renderer)
			|| renderer.getModel() != model) {
			return;
		}
		final Vec3 cam = mc.gameRenderer.mainCamera().position();
		Arrays.fill(lowest, Double.MAX_VALUE);
		Arrays.fill(soleY, Double.MAX_VALUE);
		Arrays.fill(topY, -Double.MAX_VALUE);
		Arrays.fill(legInside, 0.0);
		level = horse.level();
		for (int i = 0; i < 4; i++) {
			Arrays.fill(knee[i], Double.NaN);
			Arrays.fill(fetlock[i], Double.NaN);
		}
		bellyVolume = 0.0;
		final boolean dumping = wantTree;
		if (dumping) {
			dump.setLength(0);
		}
		final PoseStack stack = new PoseStack();
		stack.last().set(pose);
		walk(model.root(), "root", stack, -1, false, cam, dumping, 0);
		if (dumping) {
			tree = dump.toString();
			wantTree = false;
		}
		final float yaw = horse.getVisualRotationYInDegrees() * Mth.DEG_TO_RAD;
		final double fx = -Mth.sin(yaw);
		final double fz = Mth.cos(yaw);
		bodyInside = 0.0;
		chestInside = 0.0;
		if (bellyVolume > 0.0) {
			final float heading = horse.getVisualRotationYInDegrees() * Mth.DEG_TO_RAD;
			final double hx = -Mth.sin(heading);
			final double hz = Mth.cos(heading);
			final double middle = (belly[0] + belly[3] + belly[6] + belly[9]) * 0.25 * hx + (belly[2] + belly[5] + belly[8] + belly[11]) * 0.25 * hz;
			for (int i = 0; i < 4; i++) {
				final double in = insideAt(level, belly[i * 3], belly[i * 3 + 2], belly[i * 3 + 1]);
				bodyInside = Math.max(bodyInside, in);
				if (belly[i * 3] * hx + belly[i * 3 + 2] * hz > middle) {
					chestInside = Math.max(chestInside, in);
				}
			}
		}
		// The belly's plane (its normal up).
		final double ax = belly[0], ay = belly[1], az = belly[2];
		double nx = (belly[4] - ay) * (belly[8] - az) - (belly[5] - az) * (belly[7] - ay);
		double ny = (belly[5] - az) * (belly[6] - ax) - (belly[3] - ax) * (belly[8] - az);
		double nz = (belly[3] - ax) * (belly[7] - ay) - (belly[4] - ay) * (belly[6] - ax);
		final double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
		final double sign = ny < 0.0 ? -1.0 : 1.0;
		nx *= sign / nl;
		ny *= sign / nl;
		nz *= sign / nl;
		for (int leg = 0; leg < 4; leg++) {
			if (lowest[leg] == Double.MAX_VALUE) {
				gap[leg] = Double.NaN;
				hip[leg] = Double.NaN;
				jut[leg] = Double.NaN;
				continue;
			}
			final double[] c = soleCorners[leg];
			// (The sole's lowest corner: with a knee folded below the hoof, the leg's lowest point isn't the hoof.)
			lowest[leg] = Math.min(Math.min(c[1], c[4]), Math.min(c[7], c[10]));
			double ground = Double.NEGATIVE_INFINITY;
			double inside = 0.0;
			double cx = 0.0, cy = 0.0, cz = 0.0;
			for (int i = 0; i < 4; i++) {
				ground = Math.max(ground, groundAt(level, c[i * 3], c[i * 3 + 2], lowest[leg]));
				inside = Math.max(inside, insideAt(level, c[i * 3], c[i * 3 + 2], lowest[leg]));
				cx += c[i * 3] * 0.25;
				cy += c[i * 3 + 1] * 0.25;
				cz += c[i * 3 + 2] * 0.25;
			}
			ground = Math.max(ground, groundAt(level, cx, cz, lowest[leg]));
			inside = Math.max(inside, insideAt(level, cx, cz, lowest[leg]));
			// In a block: how far in, the shortest way out (a hoof touching a step's face is barely in it); else how high above
			// the ground under it.
			gap[leg] = inside > 1.0E-3 ? -inside : lowest[leg] - ground;
			sole[leg][0] = cx;
			sole[leg][1] = cy;
			sole[leg][2] = cz;
			final double[] t = topCorners[leg];
			double depth = Double.MAX_VALUE;
			double tx = 0.0, ty = 0.0, tz = 0.0;
			for (int i = 0; i < 4; i++) {
				depth = Math.min(depth, (t[i * 3] - ax) * nx + (t[i * 3 + 1] - ay) * ny + (t[i * 3 + 2] - az) * nz);
				tx += t[i * 3] * 0.25;
				ty += t[i * 3 + 1] * 0.25;
				tz += t[i * 3 + 2] * 0.25;
			}
			hip[leg] = bellyVolume > 0.0 ? depth : Double.NaN;
			top[leg][0] = tx;
			top[leg][1] = ty;
			top[leg][2] = tz;
			if (Double.isNaN(knee[leg][0]) || Double.isNaN(fetlock[leg][0])) {
				jut[leg] = Double.NaN;
			} else {
				// In the horse's side view (along its heading, and up): the knee's side of the line from the top to the fetlock.
				final double au = tx * fx + tz * fz;
				final double bu = fetlock[leg][0] * fx + fetlock[leg][2] * fz;
				final double ku = knee[leg][0] * fx + knee[leg][2] * fz;
				final double du = bu - au;
				final double dv = fetlock[leg][1] - ty;
				final double length = Math.sqrt(du * du + dv * dv);
				jut[leg] = length < 1.0E-6 ? 0.0 : (du * (knee[leg][1] - ty) - dv * (ku - au)) / length;
			}
		}
		frames++;
	}

	private static void walk(
		final ModelPart part, final String name, final PoseStack stack, final int legOf, final boolean inBody, final Vec3 cam, final boolean dumping,
		final int depth
	) {
		int leg = legOf;
		if (leg < 0) {
			for (int i = 0; i < LEGS.length; i++) {
				if (LEGS[i].equals(name)) {
					leg = i;
				}
			}
		}
		final boolean body = inBody || "body".equals(name);
		final ModelPartAccessor access = (ModelPartAccessor) (Object) part;
		if (dumping && (leg >= 0 || depth <= 1 || body && depth <= 2)) {
			final PartPose initial = part.getInitialPose();
			dump.append("  ".repeat(depth)).append(String.format(Locale.ROOT,
				"%s [%s]%s pos(%.2f %.2f %.2f) rot(%.1f %.1f %.1f) initial pos(%.2f %.2f %.2f) rot(%.1f %.1f %.1f)", name,
				part.getClass().getSimpleName(), part.visible ? (part.skipDraw ? " skipDraw" : "") : " HIDDEN", part.x, part.y, part.z,
				part.xRot * Mth.RAD_TO_DEG, part.yRot * Mth.RAD_TO_DEG, part.zRot * Mth.RAD_TO_DEG, initial.x(), initial.y(), initial.z(),
				initial.xRot() * Mth.RAD_TO_DEG, initial.yRot() * Mth.RAD_TO_DEG, initial.zRot() * Mth.RAD_TO_DEG));
			for (final ModelPart.Cube cube : access.horsingaround$cubes()) {
				float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
				for (final ModelPart.Polygon polygon : cube.polygons) {
					for (final ModelPart.Vertex vertex : polygon.vertices()) {
						minY = Math.min(minY, vertex.y());
						maxY = Math.max(maxY, vertex.y());
						minZ = Math.min(minZ, vertex.z());
						maxZ = Math.max(maxZ, vertex.z());
					}
				}
				dump.append(String.format(Locale.ROOT, " cube[%d faces y %.2f..%.2f z %.2f..%.2f]", cube.polygons.length, minY, maxY, minZ, maxZ));
			}
			dump.append('\n');
		}
		if (!part.visible) {
			return;
		}
		stack.pushPose();
		part.translateAndRotate(stack);
		final Matrix4f matrix = stack.last().pose();
		if (leg >= 0 && (KNEE.equals(name) || FETLOCK.equals(name))) {
			matrix.transformPosition(0.0F, 0.0F, 0.0F, point);
			final double[] joint = KNEE.equals(name) ? knee[leg] : fetlock[leg];
			joint[0] = point.x + cam.x;
			joint[1] = point.y + cam.y;
			joint[2] = point.z + cam.z;
		}
		if (!part.skipDraw && (leg >= 0 || body)) {
			for (final ModelPart.Cube cube : access.horsingaround$cubes()) {
				if (leg >= 0) {
					leg(cube, matrix, leg, cam, !hasHoof(part) || FETLOCK.equals(name));
				} else {
					belly(cube, matrix, cam);
				}
			}
		}
		for (final Map.Entry<String, ModelPart> child : access.horsingaround$children().entrySet()) {
			walk(child.getValue(), child.getKey(), stack, leg, body, cam, dumping, depth + 1);
		}
		stack.popPose();
	}

	/** A leg's box: its lowest point, its lowest end face (the sole), and its highest end face (the top of the leg). */
	/** Whether a leg's part has the hoof below it (then only the hoof's own end faces count as the sole). */
	private static boolean hasHoof(final ModelPart part) {
		final Map<String, ModelPart> children = ((ModelPartAccessor) (Object) part).horsingaround$children();
		if (children.containsKey(FETLOCK)) {
			return true;
		}
		for (final ModelPart child : children.values()) {
			if (hasHoof(child)) {
				return true;
			}
		}
		return false;
	}

	private static void leg(final ModelPart.Cube cube, final Matrix4f matrix, final int leg, final Vec3 cam, final boolean soleHere) {
		float minY = Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (final ModelPart.Polygon polygon : cube.polygons) {
			for (final ModelPart.Vertex vertex : polygon.vertices()) {
				minY = Math.min(minY, vertex.y());
				maxY = Math.max(maxY, vertex.y());
			}
		}
		for (final ModelPart.Polygon polygon : cube.polygons) {
			final ModelPart.Vertex[] vertices = polygon.vertices();
			boolean atTop = vertices.length == 4;
			boolean atBottom = vertices.length == 4;
			double centreY = 0.0;
			for (final ModelPart.Vertex vertex : vertices) {
				atTop &= vertex.y() == minY;
				atBottom &= vertex.y() == maxY;
				matrix.transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), point);
				lowest[leg] = Math.min(lowest[leg], point.y + cam.y);
				centreY += (point.y + cam.y) / vertices.length;
			}
			if (!soleHere) {
				for (final ModelPart.Vertex vertex : vertices) {
					matrix.transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), point);
					legInside[leg] = Math.max(legInside[leg], insideAt(level, point.x + cam.x, point.z + cam.z, point.y + cam.y));
				}
			}
			if (soleHere && atBottom && centreY < soleY[leg]) {
				soleY[leg] = centreY;
				corners(vertices, matrix, cam, soleCorners[leg]);
			}
			if (atTop && centreY > topY[leg]) {
				topY[leg] = centreY;
				corners(vertices, matrix, cam, topCorners[leg]);
			}
		}
	}

	/** The body's box (the biggest box in the body): its underside. */
	private static void belly(final ModelPart.Cube cube, final Matrix4f matrix, final Vec3 cam) {
		final double volume = (double) (cube.maxX - cube.minX) * (cube.maxY - cube.minY) * (cube.maxZ - cube.minZ);
		if (Math.abs(volume) <= bellyVolume) {
			return;
		}
		float maxY = -Float.MAX_VALUE;
		for (final ModelPart.Polygon polygon : cube.polygons) {
			for (final ModelPart.Vertex vertex : polygon.vertices()) {
				maxY = Math.max(maxY, vertex.y());
			}
		}
		for (final ModelPart.Polygon polygon : cube.polygons) {
			boolean under = polygon.vertices().length == 4;
			for (final ModelPart.Vertex vertex : polygon.vertices()) {
				under &= vertex.y() == maxY;
			}
			if (under) {
				bellyVolume = Math.abs(volume);
				corners(polygon.vertices(), matrix, cam, belly);
				return;
			}
		}
	}

	private static void corners(final ModelPart.Vertex[] vertices, final Matrix4f matrix, final Vec3 cam, final double[] out) {
		for (int i = 0; i < 4; i++) {
			matrix.transformPosition(vertices[i].worldX(), vertices[i].worldY(), vertices[i].worldZ(), point);
			out[i * 3] = point.x + cam.x;
			out[i * 3 + 1] = point.y + cam.y;
			out[i * 3 + 2] = point.z + cam.z;
		}
	}

	/**
	 * How far a point at (x, {@code y}, z) is inside a block's collision shape, the shortest way out (up through its top,
	 * or sideways out of its column); 0 if it isn't in one.
	 */
	private static double insideAt(final Level level, final double x, final double z, final double y) {
		final int bx = Mth.floor(x);
		final int by = Mth.floor(y);
		final int bz = Mth.floor(z);
		final double lx = x - bx;
		final double ly = y - by;
		final double lz = z - bz;
		pos.set(bx, by, bz);
		double most = 0.0;
		for (final AABB box : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
			if (lx > box.minX && lx < box.maxX && ly > box.minY && ly < box.maxY && lz > box.minZ && lz < box.maxZ) {
				// (Out of the top, or sideways only where the neighbour there isn't solid at that height.)
				double out = box.maxY - ly;
				out = Math.min(out, sideways(level, bx, by, bz, ly, lx - box.minX, -1, 0));
				out = Math.min(out, sideways(level, bx, by, bz, ly, box.maxX - lx, 1, 0));
				out = Math.min(out, sideways(level, bx, by, bz, ly, lz - box.minZ, 0, -1));
				out = Math.min(out, sideways(level, bx, by, bz, ly, box.maxZ - lz, 0, 1));
				most = Math.max(most, out);
			}
		}
		return most;
	}

	/** {@code distance} if the neighbour that way leaves room at height {@code ly}, else the way out isn't that way. */
	private static double sideways(final Level level, final int bx, final int by, final int bz, final double ly, final double distance, final int dx, final int dz) {
		pos.set(bx + dx, by, bz + dz);
		for (final AABB box : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
			if (ly > box.minY && ly < box.maxY) {
				return Double.MAX_VALUE;
			}
		}
		return distance;
	}

	/** The top of the highest thing to stand on at (x, z) no more than 0.6 above {@code y}. */
	private static double groundAt(final Level level, final double x, final double z, final double y) {
		final int bx = Mth.floor(x);
		final int bz = Mth.floor(z);
		final double lx = x - bx;
		final double lz = z - bz;
		final int high = Mth.floor(y + 0.6);
		double best = Double.NEGATIVE_INFINITY;
		for (int by = high; by >= high - 3; by--) {
			pos.set(bx, by, bz);
			for (final AABB box : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
				if (lx >= box.minX - 1.0E-4 && lx <= box.maxX + 1.0E-4 && lz >= box.minZ - 1.0E-4 && lz <= box.maxZ + 1.0E-4) {
					final double t = by + box.maxY;
					if (t <= y + 0.6 && t > best) {
						best = t;
					}
				}
			}
		}
		return best;
	}

	/** The last frame, one line. */
	public static String line() {
		final StringBuilder out = new StringBuilder();
		final String[] names = {"FL", "FR", "HL", "HR"};
		for (int i = 0; i < 4; i++) {
			out.append(String.format(Locale.ROOT, "%s gap%+.2f knee%+.2f hip%+.2f jut%+.2f  ", names[i], gap[i], -legInside[i], hip[i], jut[i]));
		}
		out.append(String.format(Locale.ROOT, "body in %.2f (chest %.2f)  ", bodyInside, chestInside));
		return out.toString();
	}
}
