package dev.horsingaround.client.render;

//? if >=1.21.9 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
//?}

/**
 * The horse's trunk bent along its back as a real one bends (user direction, 2026-10-27: no stiff body, and no gaps where
 * it bends): vanilla's one body box, drawn in slices along its length, each slice's corners turned with the chest where
 * they are the chest, with the hindquarters where they are the hindquarters, and blended smoothly between over each joint,
 * as a skinned model's are. One surface, so a bend never opens a gap or shows an edge, and the texture is vanilla's. What
 * sits straight on the body (the saddle, a donkey's chests) bends with it. The bones are BodyBones' chest and croup parts,
 * turned by BodyBones.carry each frame and back to straight with the rest of the model's pose; a horse standing straight
 * is drawn by vanilla. From 1.21.9; render thread only.
 */
public final class BentBody {
	private BentBody() {
	}

	//? if >=1.21.9 {
	/** How long each slice of a face along the body is, pixels. */
	private static final float SLICE = 1.0F;
	/**
	 * Where the bend blends from one bone to the next along the body (its frame, pixels): all chest in front of CHEST_FROM,
	 * none behind CHEST_TO; none of the hindquarters in front of CROUP_FROM, all behind CROUP_TO, so the front legs, the
	 * neck, the hind legs and the tail sit wholly on their bone, as BodyBones.carry moves them.
	 */
	private static final float CHEST_FROM = BodyBones.BARREL_FRONT - 3.0F;
	private static final float CHEST_TO = BodyBones.BARREL_FRONT + 3.0F;
	private static final float CROUP_FROM = BodyBones.BARREL_BACK - 4.0F;
	private static final float CROUP_TO = BodyBones.BARREL_BACK + 2.0F;

	/** What a part bends on: the back's bones, and where the part sits in the body (pixels). */
	public static final class Bend {
		final ModelPart chest;
		final ModelPart croup;
		final float x;
		final float y;
		final float z;

		Bend(final ModelPart chest, final ModelPart croup, final float x, final float y, final float z) {
			this.chest = chest;
			this.croup = croup;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	// Scratch for draw; render thread only.
	private static final Matrix3f CHEST_TURN = new Matrix3f();
	private static final Matrix3f CROUP_TURN = new Matrix3f();
	private static final Vector3f TURNED = new Vector3f();
	private static final Vector3f NORMAL = new Vector3f();
	private static final Vector3f OUT = new Vector3f();

	/** Marks a model's body, and what sits straight on it, to be drawn bent on the back's bones. Once, as the model is built. */
	public static void attach(final ModelPart root) {
		final ModelPart body = ((PartData) (Object) root).horsingaround$children().get("body");
		if (body == null) {
			return;
		}
		final Map<String, ModelPart> parts = ((PartData) (Object) body).horsingaround$children();
		final ModelPart chest = parts.get(BodyBones.CHEST);
		final ModelPart croup = parts.get(BodyBones.CROUP);
		if (chest == null || croup == null) {
			return;
		}
		((PartData) (Object) body).horsingaround$setBones(new Bend(chest, croup, 0.0F, 0.0F, 0.0F));
		// (Not the tail: it turns with the hindquarters whole, see BodyBones.carry.)
		for (final Map.Entry<String, ModelPart> entry : parts.entrySet()) {
			final ModelPart part = entry.getValue();
			final PartPose at = part.getInitialPose();
			if (part != chest && part != croup && !"tail".equals(entry.getKey()) && at.xRot() == 0.0F && at.yRot() == 0.0F && at.zRot() == 0.0F
				&& !((PartData) (Object) part).horsingaround$cubes().isEmpty()) {
				((PartData) (Object) part).horsingaround$setBones(new Bend(chest, croup, at.x(), at.y(), at.z()));
			}
		}
	}

	/**
	 * Draws {@code cube}, of a part that bends with the trunk, bent as the back's bones are turned this frame (in place of
	 * vanilla's drawing of it); false, drawing nothing, while they are straight.
	 */
	public static boolean draw(
		final Bend bend, final ModelPart.Cube cube, final PoseStack.Pose pose, final VertexConsumer buffer, final int light, final int overlay, final int color
	) {
		final ModelPart chest = bend.chest;
		final ModelPart croup = bend.croup;
		if (chest.xRot == 0.0F && chest.yRot == 0.0F && chest.zRot == 0.0F && croup.xRot == 0.0F && croup.yRot == 0.0F && croup.zRot == 0.0F) {
			return false;
		}
		CHEST_TURN.rotationZYX(chest.zRot, chest.yRot, chest.xRot);
		CROUP_TURN.rotationZYX(croup.zRot, croup.yRot, croup.xRot);
		final Matrix4f matrix = pose.pose();
		for (final ModelPart.Polygon polygon : cube.polygons) {
			final ModelPart.Vertex[] corners = polygon.vertices();
			final Vector3fc normal = polygon.normal();
			// (A face along the body in slices, each bent where it is; a face across it, an end, whole.)
			final boolean along = Math.abs(normal.z()) < 0.5F;
			float near = Float.MAX_VALUE;
			float far = -Float.MAX_VALUE;
			for (final ModelPart.Vertex corner : corners) {
				near = Math.min(near, corner.z());
				far = Math.max(far, corner.z());
			}
			final int slices = along ? Math.max(1, Mth.ceil((far - near) / SLICE)) : 1;
			for (int k = 0; k < slices; k++) {
				for (int c = 0; c < corners.length; c++) {
					final ModelPart.Vertex corner = corners[c];
					float z = corner.z();
					float u = corner.u();
					float v = corner.v();
					if (along) {
						// (The corner's edge along the body, from its near end to its far end: the slice's corner on it.)
						final ModelPart.Vertex other = partner(corners, c);
						final boolean nearEnd = corner.z() < other.z();
						final ModelPart.Vertex from = nearEnd ? corner : other;
						final ModelPart.Vertex to = nearEnd ? other : corner;
						final float t = (float) (nearEnd ? k : k + 1) / slices;
						z = Mth.lerp(t, from.z(), to.z());
						u = Mth.lerp(t, from.u(), to.u());
						v = Mth.lerp(t, from.v(), to.v());
					}
					final float x = corner.x() + bend.x;
					final float y = corner.y() + bend.y;
					final float bodyZ = z + bend.z;
					final float chestShare = 1.0F - share(bodyZ, CHEST_FROM, CHEST_TO);
					final float croupShare = share(bodyZ, CROUP_FROM, CROUP_TO);
					float bentX = x;
					float bentY = y;
					float bentZ = bodyZ;
					NORMAL.set(normal);
					if (chestShare > 0.0F) {
						CHEST_TURN.transform(TURNED.set(x - chest.x, y - chest.y, bodyZ - chest.z));
						bentX += chestShare * (TURNED.x + chest.x - x);
						bentY += chestShare * (TURNED.y + chest.y - y);
						bentZ += chestShare * (TURNED.z + chest.z - bodyZ);
						CHEST_TURN.transform(TURNED.set(normal));
						NORMAL.add(TURNED.sub(normal).mul(chestShare));
					}
					if (croupShare > 0.0F) {
						CROUP_TURN.transform(TURNED.set(x - croup.x, y - croup.y, bodyZ - croup.z));
						bentX += croupShare * (TURNED.x + croup.x - x);
						bentY += croupShare * (TURNED.y + croup.y - y);
						bentZ += croupShare * (TURNED.z + croup.z - bodyZ);
						CROUP_TURN.transform(TURNED.set(normal));
						NORMAL.add(TURNED.sub(normal).mul(croupShare));
					}
					pose.transformNormal(NORMAL.normalize(), OUT);
					final float nx = OUT.x;
					final float ny = OUT.y;
					final float nz = OUT.z;
					matrix.transformPosition((bentX - bend.x) / 16.0F, (bentY - bend.y) / 16.0F, (bentZ - bend.z) / 16.0F, OUT);
					buffer.addVertex(OUT.x, OUT.y, OUT.z, color, u, v, overlay, light, nx, ny, nz);
				}
			}
		}
		return true;
	}

	/** The corner of a face along the body at the other end of {@code corners[c]}'s edge along it. */
	private static ModelPart.Vertex partner(final ModelPart.Vertex[] corners, final int c) {
		final ModelPart.Vertex corner = corners[c];
		for (int o = 0; o < corners.length; o++) {
			final ModelPart.Vertex other = corners[o];
			if (o != c && Math.abs(other.x() - corner.x()) < 1.0E-4F && Math.abs(other.y() - corner.y()) < 1.0E-4F) {
				return other;
			}
		}
		return corner;
	}

	/** How far {@code z} is from {@code from} to {@code to}, eased in and out (0 before, 1 after). */
	private static float share(final float z, final float from, final float to) {
		final float t = Mth.clamp((z - from) / (to - from), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}
	//?}
}
