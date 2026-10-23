package dev.horsingaround.devtools;

import dev.horsingaround.client.render.GroundLegs;
import dev.horsingaround.ride.RideController;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;

/**
 * The play-test recorder, in dev games (see {@link DevTools}): every frame the ridden horse is drawn, what {@link LegProbe}
 * measured (the body, the camera, the keys held, and each leg as drawn) goes into a ring of the last FRAMES frames. The
 * save key writes them to {@code devtools/<time>/frames.csv} in the game folder, with the ground around the horse
 * ({@code ground.csv}) and the hitches and sunk hooves among them ({@code events.txt}). Every hitch and sunk hoof is also
 * appended to {@code devtools/events.log} as it happens, so a whole play session can be read back afterwards. Render
 * thread only; nothing allocated per frame but the events written.
 */
public final class Recorder {
	/** About 30 seconds at 260 frames a second. */
	static final int FRAMES = 8000;
	private static final String[] BODY = {"time", "fps", "x", "y", "z", "yaw", "speed", "gait", "keys", "body", "tilt", "fit", "near", "camx", "camy", "camz",
		"camyaw", "campitch"};
	private static final String[] LEG = {"x", "y", "z", "gap", "clip", "lift", "rise", "back", "turn", "step"};
	private static final int COLUMNS = BODY.length + 4 * LEG.length;
	private static final double[][] RING = new double[FRAMES][COLUMNS];
	private static int next;
	private static int count;
	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
	/** Events this second, so a run of them doesn't flood the log. */
	private static long eventSecond;
	private static int eventsThisSecond;
	static boolean on;

	private Recorder() {
	}

	/** After LegProbe has measured a frame of the ridden horse. */
	static void frame(final AbstractHorse horse, final LivingEntityRenderState s, final Minecraft mc, final Vec3 cam, final double time, final float partial) {
		if (!on) {
			return;
		}
		final RideState ride = ((RideStateHolder) horse).horsingaround$ride();
		final double[] row = RING[next];
		int c = 0;
		row[c++] = time;
		row[c++] = mc.getFps();
		row[c++] = s.x;
		row[c++] = s.y;
		row[c++] = s.z;
		row[c++] = horse.getVisualRotationYInDegrees();
		row[c++] = Math.hypot(horse.getX() - horse.xo, horse.getZ() - horse.zo);
		row[c++] = ride.gait;
		row[c++] = (mc.options.keyUp.isDown() ? 1 : 0) | (mc.options.keyDown.isDown() ? 2 : 0) | (mc.options.keyLeft.isDown() ? 4 : 0)
			| (mc.options.keyRight.isDown() ? 8 : 0) | (mc.options.keySprint.isDown() ? 16 : 0) | (mc.options.keyJump.isDown() ? 32 : 0);
		row[c++] = s.y + ride.heightOffset(partial);
		row[c++] = ride.pitch(partial);
		row[c++] = ride.drawnFit;
		row[c++] = ride.stepNear;
		row[c++] = cam.x;
		row[c++] = cam.y;
		row[c++] = cam.z;
		row[c++] = mc.gameRenderer.mainCamera().yRot();
		row[c++] = mc.gameRenderer.mainCamera().xRot();
		for (int leg = 0; leg < 4; leg++) {
			row[c++] = LegProbe.sole[leg][0];
			row[c++] = LegProbe.sole[leg][1];
			row[c++] = LegProbe.sole[leg][2];
			row[c++] = LegProbe.gap[leg];
			row[c++] = LegProbe.clipNow[leg];
			row[c++] = GroundLegs.PACK_LIFTS[leg];
			row[c++] = ride.legRise[leg];
			row[c++] = ride.legBack[leg];
			row[c++] = ride.stepTurn[leg];
			row[c++] = !Float.isNaN(ride.stepTime[leg]) ? 1 : !Double.isNaN(ride.stepHoldX[leg]) ? 2 : !Float.isNaN(ride.stepCarry[leg]) ? 3 : 0;
		}
		next = (next + 1) % FRAMES;
		count = Math.min(count + 1, FRAMES);
	}

	/** A hitch or a sunk hoof (from LegProbe): into devtools/events.log, with the wall-clock time. */
	static void event(final Minecraft mc, final String what) {
		if (!on) {
			return;
		}
		final long second = System.currentTimeMillis() / 1000L;
		if (second != eventSecond) {
			eventSecond = second;
			eventsThisSecond = 0;
		}
		if (++eventsThisSecond > 20) {
			return;
		}
		try {
			final Path dir = mc.gameDirectory.toPath().resolve("devtools");
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("events.log"), LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME) + " " + what + "\n", StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (final IOException ignored) {
			// (A dev log: losing a line is fine.)
		}
	}

	/** Writes the frames in the ring, the ground around the horse and the events among them; tells the player where. */
	static void save(final Minecraft mc) {
		if (count == 0 || mc.level == null) {
			mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Horsing Around dev: nothing recorded yet (ride a horse)."));
			return;
		}
		final String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
		final Path dir = mc.gameDirectory.toPath().resolve("devtools").resolve(name);
		final StringBuilder csv = new StringBuilder(count * COLUMNS * 9);
		csv.append(String.join(",", BODY));
		for (final String leg : new String[] {"fl", "fr", "hl", "hr"}) {
			for (final String column : LEG) {
				csv.append(',').append(leg).append('_').append(column);
			}
		}
		csv.append('\n');
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE, y = 0.0;
		for (int k = 0; k < count; k++) {
			final double[] row = RING[(next - count + k + FRAMES) % FRAMES];
			for (int c = 0; c < COLUMNS; c++) {
				if (c > 0) {
					csv.append(',');
				}
				csv.append(String.format(Locale.ROOT, "%.4f", row[c]));
			}
			csv.append('\n');
			minX = Math.min(minX, row[2]);
			maxX = Math.max(maxX, row[2]);
			minZ = Math.min(minZ, row[4]);
			maxZ = Math.max(maxZ, row[4]);
			y = row[3];
		}
		// The ground along the way: the top under the middle of each block (and what it is), within 3 blocks of where the
		// horse went, from 6 above to 10 below where it last was.
		final StringBuilder ground = new StringBuilder("x,z,top,block\n");
		for (int bx = Mth.floor(minX) - 3; bx <= Mth.floor(maxX) + 3; bx++) {
			for (int bz = Mth.floor(minZ) - 3; bz <= Mth.floor(maxZ) + 3; bz++) {
				final double top = RideController.surface(mc.level, POS, bx + 0.5, bz + 0.5, y + 6.0, y - 10.0, false);
				POS.set(bx, Mth.floor(Double.isNaN(top) || top == Double.NEGATIVE_INFINITY ? y : top - 1.0E-3), bz);
				ground.append(bx).append(',').append(bz).append(',').append(String.format(Locale.ROOT, "%.3f", top)).append(',')
					.append(BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(POS).getBlock()).getPath()).append('\n');
			}
		}
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("frames.csv"), csv);
			Files.writeString(dir.resolve("ground.csv"), ground);
			Files.writeString(dir.resolve("events.txt"), LegProbe.hitches() + "\n" + LegProbe.about() + "\n\n" + LegProbe.kinkLog + "\n\n" + LegProbe.sunkLog + "\n");
		} catch (final IOException e) {
			mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Horsing Around dev: couldn't save (" + e.getMessage() + ")"));
			return;
		}
		mc.gui.hud.getChat().addClientSystemMessage(Component.literal(String.format(Locale.ROOT, "Horsing Around dev: saved the last %.0f s of riding to devtools/%s",
			count / Math.max(mc.getFps(), 1.0), name)));
		// (The probe's logs start afresh, so each save's events are its own.)
		LegProbe.reset();
	}
}
