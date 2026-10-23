package dev.horsingaround.gametest;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.horsingaround.client.render.PartData;
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
 * whether the top of the leg is still up inside the body (a gap there is a leg come off), and whether any of the leg is in a
 * block.
 * Test-only; nothing here runs in play.
 */
public final class LegProbe {
	/** Front left, front right, hind left, hind right (vanilla's part names, which animation packs keep). */
	public static final String[] LEGS = {"left_front_leg", "right_front_leg", "left_hind_leg", "right_hind_leg"};

	private static volatile boolean armed;
	private static volatile boolean wantTree;
	/** The parts' tree, written once when asked for. */
	public static volatile String tree;

	/** Frames measured since {@link #reset}. */
	public static int frames;
	/** Snaps since {@link #reset}: the most a sole moved against the horse from one frame to the next (blocks), and how often past SOLE_SNAP. */
	public static double soleSnapMost;
	public static int snaps;
	/** The first snaps since {@link #reset}, what each was. */
	public static final StringBuilder snapLog = new StringBuilder();
	static final double SOLE_SNAP = 0.2;
	/**
	 * Since {@link #reset}, for riding about: how far the soles moved against the horse in all (blocks, the four summed)
	 * over how long (seconds), and the fastest one did (blocks/s): legs moving faster than the gait's own show here; the
	 * deepest a sole was in the ground (blocks) and the frames it was more than a pixel in; the deepest the body's underside
	 * was; and how far the legs were drawn up into the body, at most and on average (a horse drawn down onto its legs).
	 */
	public static double soleTravel;
	public static double soleTime;
	public static double soleSpeedMost;
	public static double sunkMost;
	public static int sunkFrames;
	public static double bodyInMost;
	public static double riseMost;
	public static double riseSum;
	private static double soleLastTime = Double.NaN;
	/** The first times a sole was more than SUNK_LOG in the ground since {@link #reset}: where, in what, and the ride then. */
	public static final StringBuilder sunkLog = new StringBuilder();
	static final double SUNK_LOG = 0.15;
	private static final double[][] lastSole = new double[4][3];
	/** Per leg, last frame: how far it was drawn up and swung back, the pack's own lift, and the ground under it (what a snap changed). */
	private static final double[][] lastLeg = new double[4][4];
	private static boolean haveLast;
	/** Per leg, the last frame drawn: the hoof's lowest point above the ground under its sole (blocks; negative is sunk in). */
	public static final double[] gap = new double[4];
	/** How far the body's underside is in the ground at its deepest corner (blocks, the shortest way out). */
	public static double bodyInside;
	/** The same for the chest end of it alone (the rest is the hindquarters). */
	public static double chestInside;
	/** How far the rest of the leg, above the sole, is in a block at its deepest (blocks, the shortest way out). */
	public static final double[] legInside = new double[4];
	/** How far the middle of the top of the leg is up inside the body (blocks; negative, a gap shows). */
	public static final double[] hip = new double[4];
	/** How deep each leg's box is in a block this frame (blocks, the shortest way out). */
	public static final double[] clipNow = new double[4];
	/** The sole's centre (world). */
	public static final double[][] sole = new double[4][3];
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

	/** Measures the frames drawn from now on, or stops, keeping what it has so far (the store clips measure only their own frames). */
	public static void listen(final boolean on) {
		armed = on;
	}

	/**
	 * Frame by frame: the frames where the body (the middle of its underside) or a sole (against the body) jumped much
	 * faster than it moved the frame before (a snap or a hitch between ticks), what each was, and how many.
	 */
	public static final StringBuilder spikes = new StringBuilder();
	public static int spikeCount;
	private static final double[] lastHeights = new double[5];
	private static final double[] lastSpeeds = new double[5];
	private static double lastTime = Double.NaN;

	/**
	 * The feel since {@link #reset}. Hooves sliding: the most a hoof on the ground (its lowest corner within CONTACT_GAP of
	 * it) moved, its sole's middle along the ground, from where it touched down, in one contact, and the mean over the
	 * contacts that ended. Legs through blocks: the deepest any leg's box was in a block (the shortest way out, worked out
	 * exactly with separating axes, so a step's edge through the side of a leg counts as much as a hoof in the ground), and
	 * the frames any leg was more than a pixel in. Jolts: the most the drawn body (where the ride carries it, at the middle)
	 * and the camera sped up or slowed down, up and down and along, over a tenth of a second (blocks/s^2; the horse falls at
	 * 32), and the body's tilt (degrees/s^2).
	 */
	public static double slideMost;
	public static double slideSum;
	public static int contacts;
	public static double clipMost;
	public static int clipFrames;
	public static final double[] clipLeg = new double[4];
	public static double bodyJolt;
	public static double tiltJolt;
	public static double cameraJolt;
	public static double cameraSurge;
	/** Where (game time) each of those jolts was worst. */
	public static double bodyJoltAt;
	public static double tiltJoltAt;
	public static double cameraJoltAt;
	public static double cameraSurgeAt;
	public static final StringBuilder slideLog = new StringBuilder();
	/** While set, a CSV line a frame: the time, the horse, the drawn body and camera, and per leg its sole, gap, depth in a block and pose. */
	public static StringBuilder record;
	public static final StringBuilder clipLog = new StringBuilder();
	static final double CONTACT_GAP = 0.03;
	static final double CLIP_PIXEL = 1.0 / 16.0;
	private static final double JOLT_SPAN = 0.1;
	private static final boolean[] inContact = new boolean[4];
	private static final double[][] contactFrom = new double[4][2];
	private static final double[] contactSlide = new double[4];
	private static final int[] contactFrames = new int[4];
	private static final int HISTORY = 128;
	private static final double[][] history = new double[HISTORY][6];
	private static int historyCount;
	private static int historyNext;
	private static final double[] cornersScratch = new double[24];
	private static final double[] axesScratch = new double[45];

	/** The mean slide over the contacts that ended (blocks). */
	public static double slideMean() {
		return contacts == 0 ? 0.0 : slideSum / contacts;
	}

	/** The feel numbers, one line. */
	public static String feel() {
		return String.format(Locale.ROOT,
			"hooves slid at most %.3f (mean %.3f over %d contacts); legs in blocks at most %.3f (FL %.3f FR %.3f HL %.3f HR %.3f), %d frames over a pixel; "
				+ "jolts: body %.1f blocks/s^2 (t%.2f), tilt %.0f deg/s^2 (t%.2f), camera up/down %.1f (t%.2f), along %.1f (t%.2f)",
			slideMost, slideMean(), contacts, clipMost, clipLeg[0], clipLeg[1], clipLeg[2], clipLeg[3], clipFrames, bodyJolt, bodyJoltAt, tiltJolt, tiltJoltAt,
			cameraJolt, cameraJoltAt, cameraSurge, cameraSurgeAt);
	}

	public static void reset() {
		slideMost = 0.0;
		slideSum = 0.0;
		contacts = 0;
		clipMost = 0.0;
		clipFrames = 0;
		Arrays.fill(clipLeg, 0.0);
		bodyJolt = 0.0;
		tiltJolt = 0.0;
		cameraJolt = 0.0;
		cameraSurge = 0.0;
		bodyJoltAt = tiltJoltAt = cameraJoltAt = cameraSurgeAt = Double.NaN;
		slideLog.setLength(0);
		clipLog.setLength(0);
		Arrays.fill(inContact, false);
		historyCount = 0;
		historyNext = 0;
		spikes.setLength(0);
		spikeCount = 0;
		lastTime = Double.NaN;
		frames = 0;
		soleSnapMost = 0.0;
		snaps = 0;
		snapLog.setLength(0);
		haveLast = false;
		soleTravel = 0.0;
		soleTime = 0.0;
		soleSpeedMost = 0.0;
		sunkMost = 0.0;
		sunkFrames = 0;
		bodyInMost = 0.0;
		riseMost = 0.0;
		riseSum = 0.0;
		soleLastTime = Double.NaN;
		sunkLog.setLength(0);
	}

	/** The riding-about numbers (see {@link #soleTravel}), one line. */
	public static String about() {
		return String.format(Locale.ROOT,
			"soles against the horse %.2f blocks/s on average (four legs), fastest %.1f; a sole in the ground at most %.3f (%d frames over a pixel); "
				+ "body in the ground at most %.3f; legs drawn up at most %.2f, on average %.3f",
			soleTime > 0.0 ? soleTravel / soleTime : 0.0, soleSpeedMost, sunkMost, sunkFrames, bodyInMost, riseMost, frames > 0 ? riseSum / frames : 0.0);
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
		Arrays.fill(clipNow, 0.0);
		level = horse.level();
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
				continue;
			}
			final double[] c = soleCorners[leg];
			// (The sole's lowest corner.)
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
			double tx = 0.0, ty = 0.0, tz = 0.0;
			for (int i = 0; i < 4; i++) {
				tx += t[i * 3] * 0.25;
				ty += t[i * 3 + 1] * 0.25;
				tz += t[i * 3 + 2] * 0.25;
			}
			// (The middle of the leg's top: it reaches far enough up that a corner may poke out of the belly with no gap.)
			hip[leg] = bellyVolume > 0.0 ? (tx - ax) * nx + (ty - ay) * ny + (tz - az) * nz : Double.NaN;
			top[leg][0] = tx;
			top[leg][1] = ty;
			top[leg][2] = tz;
		}
		// Snaps: where the sole is against the horse, from the last frame.
		final double frameTime = horse.level().getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		final double frameDt = Double.isNaN(soleLastTime) ? 0.0 : (frameTime - soleLastTime) / 20.0;
		soleLastTime = frameTime;
		final dev.horsingaround.ride.RideState riding = ((dev.horsingaround.ride.RideStateHolder) horse).horsingaround$ride();
		boolean sunk = false;
		double rise = 0.0;
		for (int leg = 0; leg < 4; leg++) {
			if (!Double.isNaN(gap[leg]) && gap[leg] < 0.0) {
				sunkMost = Math.max(sunkMost, -gap[leg]);
				sunk |= -gap[leg] > CLIP_PIXEL;
				if (-gap[leg] > SUNK_LOG && sunkLog.length() < 3000) {
					pos.set(Mth.floor(sole[leg][0]), Mth.floor(lowest[leg]), Mth.floor(sole[leg][2]));
					sunkLog.append(String.format(Locale.ROOT, "[t%d %s %.2f in %s at %.1f %.1f %.1f, drawn up %.2f, tilt %.1f, fit %+.2f, speed %.2f] ", horse.tickCount,
						new String[] {"FL", "FR", "HL", "HR"}[leg], -gap[leg], net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath(),
						sole[leg][0], lowest[leg], sole[leg][2], riding.legRise[leg], riding.pitch(1.0F), riding.drawnFit,
						Math.hypot(horse.getX() - horse.xo, horse.getZ() - horse.zo)));
				}
			}
			riseMost = Math.max(riseMost, riding.legRise[leg]);
			rise += riding.legRise[leg] * 0.25;
		}
		if (sunk) {
			sunkFrames++;
		}
		riseSum += rise;
		bodyInMost = Math.max(bodyInMost, bodyInside);
		if (haveLast && frameDt > 0.0 && frameDt < 0.2) {
			soleTime += frameDt;
		}
		for (int leg = 0; leg < 4; leg++) {
			final double rx = sole[leg][0] - s.x, ry = sole[leg][1] - s.y, rz = sole[leg][2] - s.z;
			if (haveLast && !Double.isNaN(gap[leg])) {
				final double dSole = Math.sqrt(Mth.square(rx - lastSole[leg][0]) + Mth.square(ry - lastSole[leg][1]) + Mth.square(rz - lastSole[leg][2]));
				soleSnapMost = Math.max(soleSnapMost, dSole);
				if (frameDt > 0.0 && frameDt < 0.2) {
					soleTravel += dSole;
					// (Over at least a few milliseconds: frames drawn at nearly the same moment divide by almost nothing.)
					if (frameDt > 0.004) {
						soleSpeedMost = Math.max(soleSpeedMost, dSole / frameDt);
					}
				}
				if (dSole > SOLE_SNAP) {
					snaps++;
					if (snapLog.length() < 4000) {
						final dev.horsingaround.ride.RideState ride = ((dev.horsingaround.ride.RideStateHolder) horse).horsingaround$ride();
						snapLog.append(String.format(Locale.ROOT, "[t%d %s sole %.2f drawn up %.2f (was %.2f) back %.2f (was %.2f) pack lift %.2f (was %.2f) ground %+.2f (was %+.2f) tilt %.1f air %.2f] ",
							horse.tickCount, new String[] {"FL", "FR", "HL", "HR"}[leg], dSole, ride.legRise[leg], lastLeg[leg][0], ride.legBack[leg], lastLeg[leg][1],
							dev.horsingaround.client.render.GroundLegs.PACK_LIFTS[leg], lastLeg[leg][2], dev.horsingaround.client.render.GroundLegs.TARGETS[leg * 5 + 1] - s.y,
							lastLeg[leg][3], ride.pitch(1.0F), ride.airLegs(1.0F)));
					}
				}
			}
			final dev.horsingaround.ride.RideState rideNow = ((dev.horsingaround.ride.RideStateHolder) horse).horsingaround$ride();
			lastLeg[leg][0] = rideNow.legRise[leg];
			lastLeg[leg][1] = rideNow.legBack[leg];
			lastLeg[leg][2] = dev.horsingaround.client.render.GroundLegs.PACK_LIFTS[leg];
			lastLeg[leg][3] = dev.horsingaround.client.render.GroundLegs.TARGETS[leg * 5 + 1] - s.y;
			lastSole[leg][0] = rx;
			lastSole[leg][1] = ry;
			lastSole[leg][2] = rz;
		}
		haveLast = true;
		frames++;
		spikes(horse, mc);
		feel(horse, s, mc, cam);
	}

	/** Slides, legs in blocks and jolts this frame (see {@link #slideMost}). */
	private static void feel(final AbstractHorse horse, final LivingEntityRenderState s, final Minecraft mc, final Vec3 cam) {
		final float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		final double time = horse.level().getGameTime() + partial;
		final String[] names = {"FL", "FR", "HL", "HR"};
		boolean over = false;
		for (int leg = 0; leg < 4; leg++) {
			if (clipNow[leg] > clipLeg[leg]) {
				clipLeg[leg] = clipNow[leg];
			}
			if (clipNow[leg] > clipMost) {
				clipMost = clipNow[leg];
			}
			if (clipNow[leg] > CLIP_PIXEL) {
				over = true;
				if (clipLog.length() < 3000) {
					clipLog.append(String.format(Locale.ROOT, "[t%.2f %s %.3f in, sole gap %+.3f] ", time, names[leg], clipNow[leg], gap[leg]));
				}
			}
			final boolean down = !Double.isNaN(gap[leg]) && gap[leg] < CONTACT_GAP;
			if (down) {
				if (!inContact[leg]) {
					inContact[leg] = true;
					contactFrom[leg][0] = sole[leg][0];
					contactFrom[leg][1] = sole[leg][2];
					contactSlide[leg] = 0.0;
					contactFrames[leg] = 0;
				}
				contactFrames[leg]++;
				final double slid = Math.sqrt(Mth.square(sole[leg][0] - contactFrom[leg][0]) + Mth.square(sole[leg][2] - contactFrom[leg][1]));
				contactSlide[leg] = Math.max(contactSlide[leg], slid);
				slideMost = Math.max(slideMost, contactSlide[leg]);
			} else if (inContact[leg]) {
				inContact[leg] = false;
				if (contactFrames[leg] >= 4) {
					contacts++;
					slideSum += contactSlide[leg];
					if (contactSlide[leg] > 0.08 && slideLog.length() < 3000) {
						slideLog.append(String.format(Locale.ROOT, "[t%.2f %s slid %.3f over %d frames] ", time, names[leg], contactSlide[leg], contactFrames[leg]));
					}
				}
			}
		}
		if (over) {
			clipFrames++;
		}
		if (record != null && record.length() < 4_000_000) {
			final dev.horsingaround.ride.RideState r = ((dev.horsingaround.ride.RideStateHolder) horse).horsingaround$ride();
			record.append(String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f,%.4f,%.3f,%.4f,%.4f,%.4f,%.4f,%.4f", time, s.x, s.y, s.z, s.y + r.heightOffset(partial),
				r.pitch(partial), cam.x, cam.y, cam.z, r.debugFore(), r.debugHind()));
			for (int leg = 0; leg < 4; leg++) {
				record.append(String.format(Locale.ROOT, ",%.4f,%.4f,%.4f,%.4f,%.4f,%.3f,%.4f,%.4f,%d,%.3f", sole[leg][0], sole[leg][1], sole[leg][2], gap[leg], clipNow[leg],
					dev.horsingaround.client.render.GroundLegs.PACK_LIFTS[leg], r.legRise[leg], r.legBack[leg],
					!Float.isNaN(r.stepTime[leg]) ? 1 : !Double.isNaN(r.stepHoldX[leg]) ? 2 : 0, r.stepTurn[leg]));
			}
			record.append('\n');
		}
		// Jolts: second differences over JOLT_SPAN, against the frames before.
		final dev.horsingaround.ride.RideState ride = ((dev.horsingaround.ride.RideStateHolder) horse).horsingaround$ride();
		final double[] now = history[historyNext];
		now[0] = time;
		now[1] = s.y + ride.heightOffset(partial);
		now[2] = ride.pitch(partial);
		now[3] = cam.x;
		now[4] = cam.y;
		now[5] = cam.z;
		if (historyCount > 0 && history[(historyNext + HISTORY - 1) % HISTORY][0] >= time) {
			return;
		}
		historyNext = (historyNext + 1) % HISTORY;
		historyCount = Math.min(historyCount + 1, HISTORY);
		final double span = JOLT_SPAN * 20.0;
		final double h2 = JOLT_SPAN * JOLT_SPAN;
		final double body = (now[1] - 2.0 * at(time - span, 1) + at(time - 2.0 * span, 1)) / h2;
		if (Double.isNaN(body)) {
			return;
		}
		final double tilt = (now[2] - 2.0 * at(time - span, 2) + at(time - 2.0 * span, 2)) / h2;
		final double camY = (now[4] - 2.0 * at(time - span, 4) + at(time - 2.0 * span, 4)) / h2;
		final double camX = (now[3] - 2.0 * at(time - span, 3) + at(time - 2.0 * span, 3)) / h2;
		final double camZ = (now[5] - 2.0 * at(time - span, 5) + at(time - 2.0 * span, 5)) / h2;
		final double surge = Math.sqrt(camX * camX + camZ * camZ);
		if (Math.abs(body) > bodyJolt) {
			bodyJolt = Math.abs(body);
			bodyJoltAt = time;
		}
		if (Math.abs(tilt) > tiltJolt) {
			tiltJolt = Math.abs(tilt);
			tiltJoltAt = time;
		}
		if (Math.abs(camY) > cameraJolt) {
			cameraJolt = Math.abs(camY);
			cameraJoltAt = time;
		}
		if (surge > cameraSurge) {
			cameraSurge = surge;
			cameraSurgeAt = time;
		}
	}

	/** Column {@code k} of the history at game time {@code time}, between the frames either side; NaN if it doesn't go back that far. */
	private static double at(final double time, final int k) {
		double[] later = null;
		for (int n = 1; n <= historyCount; n++) {
			final double[] row = history[(historyNext - n + HISTORY) % HISTORY];
			if (row[0] <= time) {
				if (later == null) {
					return row[k];
				}
				final double share = (time - row[0]) / (later[0] - row[0]);
				return row[k] + (later[k] - row[k]) * share;
			}
			later = row;
		}
		return Double.NaN;
	}

	/**
	 * How deep a box (its 8 corners, world, corner i at bit 0 along its x, bit 1 its y, bit 2 its z) is in any block's
	 * collision boxes: the least overlap over the separating axes (the shortest way out), the deepest of the blocks.
	 */
	private static double clipDepth(final double[] c) {
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			minX = Math.min(minX, c[i * 3]);
			maxX = Math.max(maxX, c[i * 3]);
			minY = Math.min(minY, c[i * 3 + 1]);
			maxY = Math.max(maxY, c[i * 3 + 1]);
			minZ = Math.min(minZ, c[i * 3 + 2]);
			maxZ = Math.max(maxZ, c[i * 3 + 2]);
		}
		// The axes: the world's, the box's own, and each crossed with each.
		final double[] a = axesScratch;
		int count = 0;
		final double[] own = new double[9];
		for (int j = 0; j < 3; j++) {
			final int corner = 1 << j;
			double x = c[corner * 3] - c[0], y = c[corner * 3 + 1] - c[1], z = c[corner * 3 + 2] - c[2];
			final double l = Math.sqrt(x * x + y * y + z * z);
			if (l < 1.0E-6) {
				return 0.0;
			}
			own[j * 3] = x / l;
			own[j * 3 + 1] = y / l;
			own[j * 3 + 2] = z / l;
		}
		for (int j = 0; j < 3; j++) {
			a[count * 3] = j == 0 ? 1 : 0;
			a[count * 3 + 1] = j == 1 ? 1 : 0;
			a[count * 3 + 2] = j == 2 ? 1 : 0;
			count++;
			System.arraycopy(own, j * 3, a, count * 3, 3);
			count++;
		}
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				final double wx = i == 0 ? 1 : 0, wy = i == 1 ? 1 : 0, wz = i == 2 ? 1 : 0;
				double x = wy * own[j * 3 + 2] - wz * own[j * 3 + 1];
				double y = wz * own[j * 3] - wx * own[j * 3 + 2];
				double z = wx * own[j * 3 + 1] - wy * own[j * 3];
				final double l = Math.sqrt(x * x + y * y + z * z);
				if (l < 1.0E-4) {
					continue;
				}
				a[count * 3] = x / l;
				a[count * 3 + 1] = y / l;
				a[count * 3 + 2] = z / l;
				count++;
			}
		}
		double deepest = 0.0;
		for (int bx = Mth.floor(minX); bx <= Mth.floor(maxX); bx++) {
			for (int by = Mth.floor(minY); by <= Mth.floor(maxY); by++) {
				for (int bz = Mth.floor(minZ); bz <= Mth.floor(maxZ); bz++) {
					pos.set(bx, by, bz);
					for (final AABB box : boxes(level)) {
						final double cx = bx + (box.minX + box.maxX) * 0.5, cy = by + (box.minY + box.maxY) * 0.5, cz = bz + (box.minZ + box.maxZ) * 0.5;
						final double hx = (box.maxX - box.minX) * 0.5, hy = (box.maxY - box.minY) * 0.5, hz = (box.maxZ - box.minZ) * 0.5;
						double least = Double.MAX_VALUE;
						for (int k = 0; k < count && least > 0.0; k++) {
							final double ax = a[k * 3], ay = a[k * 3 + 1], az = a[k * 3 + 2];
							double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
							for (int i = 0; i < 8; i++) {
								final double d = c[i * 3] * ax + c[i * 3 + 1] * ay + c[i * 3 + 2] * az;
								lo = Math.min(lo, d);
								hi = Math.max(hi, d);
							}
							final double centre = cx * ax + cy * ay + cz * az;
							final double reach = hx * Math.abs(ax) + hy * Math.abs(ay) + hz * Math.abs(az);
							least = Math.min(least, Math.min(hi, centre + reach) - Math.max(lo, centre - reach));
						}
						deepest = Math.max(deepest, least);
					}
				}
			}
		}
		return deepest;
	}

	/** Compares this frame's heights (the body, and each sole against it) with the last two frames' (see {@link #spikes}). */
	private static void spikes(final AbstractHorse horse, final Minecraft mc) {
		if (bellyVolume <= 0.0) {
			return;
		}
		final double time = horse.level().getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		final double body = (belly[1] + belly[4] + belly[7] + belly[10]) * 0.25;
		final double dt = (time - lastTime) / 20.0;
		final String[] names = {"body", "FL", "FR", "HL", "HR"};
		for (int i = 0; i < 5; i++) {
			final double height = i == 0 ? body : sole[i - 1][1] - body;
			if (dt > 1.0E-4 && !Double.isNaN(height) && !Double.isNaN(lastHeights[i])) {
				final double speed = (height - lastHeights[i]) / dt;
				// A jump: moving more than 3 blocks a second faster than the frame before, and by more than a hundredth.
				if (Math.abs(speed - lastSpeeds[i]) > 3.0 && Math.abs(height - lastHeights[i]) > 0.01 && spikes.length() < 4000) {
					spikeCount++;
					final dev.horsingaround.ride.RideState ride = ((dev.horsingaround.ride.RideStateHolder) horse).horsingaround$ride();
					spikes.append(String.format(Locale.ROOT, "%n      t%.2f %s %+.3f in a frame (%.1f -> %.1f blocks/s); tilt %.2f fit %.3f offset %.3f drawn up %.2f %.2f %.2f %.2f",
						time, names[i], height - lastHeights[i], lastSpeeds[i], speed, ride.pitch(1.0F), ride.drawnFit, ride.heightOffset(1.0F) - ride.drawnFit,
						ride.legRise[0], ride.legRise[1], ride.legRise[2], ride.legRise[3]));
				}
				lastSpeeds[i] = speed;
			} else if (dt > 1.0E-4) {
				lastSpeeds[i] = 0.0;
			}
			if (dt > 1.0E-4 || Double.isNaN(lastTime)) {
				lastHeights[i] = height;
			}
		}
		if (dt > 1.0E-4 || Double.isNaN(lastTime)) {
			lastTime = time;
		}
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
		final PartData access = (PartData) (Object) part;
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
		if (!part.skipDraw && (leg >= 0 || body)) {
			for (final ModelPart.Cube cube : access.horsingaround$cubes()) {
				if (leg >= 0) {
					leg(cube, matrix, leg, cam);
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

	/**
	 * A leg's box: its lowest point, its lowest end face (the sole), its highest end face (the top of the leg), and how far
	 * its sides are in a block (read a quarter, half and three quarters of the way down each corner edge).
	 */
	private static void leg(final ModelPart.Cube cube, final Matrix4f matrix, final int leg, final Vec3 cam) {
		if (cube.maxX > cube.minX && cube.maxY > cube.minY && cube.maxZ > cube.minZ) {
			for (int i = 0; i < 8; i++) {
				matrix.transformPosition((i & 1) == 0 ? cube.minX / 16.0F : cube.maxX / 16.0F, (i & 2) == 0 ? cube.minY / 16.0F : cube.maxY / 16.0F,
					(i & 4) == 0 ? cube.minZ / 16.0F : cube.maxZ / 16.0F, point);
				cornersScratch[i * 3] = point.x + cam.x;
				cornersScratch[i * 3 + 1] = point.y + cam.y;
				cornersScratch[i * 3 + 2] = point.z + cam.z;
			}
			clipNow[leg] = Math.max(clipNow[leg], clipDepth(cornersScratch));
		}
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
			if (atBottom && centreY < soleY[leg]) {
				soleY[leg] = centreY;
				corners(vertices, matrix, cam, soleCorners[leg]);
			}
			if (atTop && centreY > topY[leg]) {
				topY[leg] = centreY;
				corners(vertices, matrix, cam, topCorners[leg]);
			}
			if (atBottom) {
				// Up each corner edge from the sole.
				for (final ModelPart.Vertex vertex : vertices) {
					for (int k = 1; k <= 3; k++) {
						final float y = (maxY + (minY - maxY) * k * 0.25F) / 16.0F;
						matrix.transformPosition(vertex.worldX(), y, vertex.worldZ(), point);
						legInside[leg] = Math.max(legInside[leg], insideAt(level, point.x + cam.x, point.z + cam.z, point.y + cam.y));
					}
				}
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
		final java.util.List<AABB> boxes = boxes(level);
		for (final AABB box : boxes) {
			if (lx > box.minX && lx < box.maxX && ly > box.minY && ly < box.maxY && lz > box.minZ && lz < box.maxZ) {
				// (Out of the top, or sideways: into the open within the block (the low half of a stair), or out of it where the
				// neighbour there isn't solid at that height.)
				double out = box.maxY - ly;
				out = Math.min(out, sideways(level, boxes, bx, by, bz, lx, ly, lz, lx - box.minX, -1, 0));
				out = Math.min(out, sideways(level, boxes, bx, by, bz, lx, ly, lz, box.maxX - lx, 1, 0));
				out = Math.min(out, sideways(level, boxes, bx, by, bz, lx, ly, lz, lz - box.minZ, 0, -1));
				out = Math.min(out, sideways(level, boxes, bx, by, bz, lx, ly, lz, box.maxZ - lz, 0, 1));
				most = Math.max(most, out);
			}
		}
		return most;
	}

	/**
	 * {@code distance} if moving that far that way (and a hair more) leaves a point at (lx, ly, lz) in a block in the open:
	 * inside the block, clear of its other boxes; outside it, where the neighbour leaves room at that height.
	 */
	private static double sideways(
		final Level level, final java.util.List<AABB> boxes, final int bx, final int by, final int bz, final double lx, final double ly, final double lz,
		final double distance, final int dx, final int dz
	) {
		final double x = lx + dx * (distance + 1.0E-4);
		final double z = lz + dz * (distance + 1.0E-4);
		if (x > 0.0 && x < 1.0 && z > 0.0 && z < 1.0) {
			for (final AABB other : boxes) {
				if (x > other.minX && x < other.maxX && ly > other.minY && ly < other.maxY && z > other.minZ && z < other.maxZ) {
					return Double.MAX_VALUE;
				}
			}
			return distance;
		}
		return sideways(level, bx, by, bz, ly, distance, dx, dz);
	}

	/** {@code distance} if the neighbour that way leaves room at height {@code ly}, else the way out isn't that way. */
	private static double sideways(final Level level, final int bx, final int by, final int bz, final double ly, final double distance, final int dx, final int dz) {
		pos.set(bx + dx, by, bz + dz);
		for (final AABB box : boxes(level)) {
			if (ly > box.minY && ly < box.maxY) {
				return Double.MAX_VALUE;
			}
		}
		return distance;
	}

	/**
	 * While set, leaves count as open (a ridden horse goes through them: a hoof or the body in a bush is meant), for
	 * riding about; else every block's collision boxes count.
	 */
	public static boolean passLeaves;

	/** The collision boxes of the block at {@link #pos} (none for leaves while {@link #passLeaves}). */
	private static java.util.List<AABB> boxes(final Level level) {
		final net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
		return passLeaves && state.is(net.minecraft.tags.BlockTags.LEAVES) ? java.util.List.of() : state.getCollisionShape(level, pos).toAabbs();
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
			for (final AABB box : boxes(level)) {
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
			out.append(String.format(Locale.ROOT, "%s gap%+.2f leg%+.2f hip%+.2f  ", names[i], gap[i], -legInside[i], hip[i]));
		}
		out.append(String.format(Locale.ROOT, "body in %.2f (chest %.2f)  ", bodyInside, chestInside));
		return out.toString();
	}
}
