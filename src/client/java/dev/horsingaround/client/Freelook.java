package dev.horsingaround.client;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.client.compat.CameraMods;
import dev.horsingaround.ride.HorseConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

/**
 * Freelook while riding (user, 2026-11-20): while its key is held, or toggled on (the player's setting), the horse keeps
 * the heading it had and the mouse turns only the view. The view stays the rider's own facing, so they look, aim, attack,
 * shoot and use things where it points as ever: only what the horse heads for is held. Let go, and the view swings back
 * to that heading (the mouse steering as usual meanwhile), so the horse never turns to where the rider was looking. Only
 * on a horse this mod rides; client thread only.
 */
public final class Freelook {
	/** Toggle mode: on until the key is pressed again or the rider gets off. */
	private static boolean toggled;
	/** What the horse heads for while freelook holds it (on, or the view swinging back), degrees; NaN while it doesn't. */
	private static float heading = Float.NaN;
	/** The view is swinging back to {@link #heading}, turning at {@code returnSpeed} degrees a second. */
	private static boolean returning;
	private static float returnSpeed;
	/** The rider's yaw as the swing left it last frame: any turn since is the mouse's. */
	private static float leftYaw;
	private static long lastNanos;

	private Freelook() {
	}

	/** End of every client tick. */
	static void tick(final Minecraft minecraft) {
		// (Nothing reads its clicks: toggling goes by the times it went down, which the key repeat doesn't add to.)
		while (HorsingAroundClient.FREELOOK_KEY.consumeClick()) {
		}
		update(minecraft);
	}

	/**
	 * Once per frame, before the camera is placed (after the mouse turned the rider): brings freelook up to date with the
	 * key, and swings the view back once it is let go.
	 */
	static void frame(final Minecraft minecraft) {
		final long now = Util.getNanos();
		final float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) * 1.0E-9F, 0.1F);
		lastNanos = now;
		update(minecraft);
		final LocalPlayer player = minecraft.player;
		if (!returning || player == null || minecraft.isPaused()) {
			return;
		}
		if (CameraMods.lookIsCamera(minecraft, player)) {
			// (Leawind's camera is where the rider looks, and only that mod turns it: nothing here can bring it back.)
			release();
			return;
		}
		final float yaw = player.getYRot();
		heading += Mth.wrapDegrees(yaw - leftYaw);
		final float off = Mth.wrapDegrees(yaw - heading);
		// One step of a critically damped spring toward the heading, exact for any frame time.
		final float decay = (float) Math.exp(-FREELOOK_RETURN_RATE * dt);
		final float push = (returnSpeed + FREELOOK_RETURN_RATE * off) * dt;
		float left = (off + push) * decay;
		returnSpeed = (returnSpeed - FREELOOK_RETURN_RATE * push) * decay;
		if (Math.abs(left) < FREELOOK_RETURN_DONE) {
			left = 0.0F;
		}
		// Turned as the mouse turns the rider, the last tick's rotation with it, so the view doesn't lag a tick behind.
		player.setYRot(yaw + left - off);
		player.yRotO += left - off;
		leftYaw = player.getYRot();
		if (left == 0.0F) {
			release();
		}
	}

	/** Where the horse heads for: the held heading while freelook holds it, else where the rider looks. */
	public static float lookYaw(final Minecraft minecraft, final Player rider) {
		return !Float.isNaN(heading) && rider == minecraft.player ? heading : CameraMods.lookYaw(minecraft, rider);
	}

	/** Freelook holds the horse's heading now (on, or the view swinging back). */
	public static boolean holding() {
		return !Float.isNaN(heading);
	}

	private static void update(final Minecraft minecraft) {
		final LocalPlayer player = minecraft.player;
		final int downs = ((KeyPresses) HorsingAroundClient.FREELOOK_KEY).horsingaround$takeDowns();
		final boolean toggle = HorseConfig.get().freelook == HorseConfig.FreelookKey.TOGGLE;
		if (player == null || !RideCamera.isRiding()) {
			toggled = false;
			release();
			return;
		}
		toggled = toggle && toggled != (downs % 2 == 1);
		if (toggle ? toggled : HorsingAroundClient.FREELOOK_KEY.isDown()) {
			if (Float.isNaN(heading)) {
				heading = CameraMods.lookYaw(minecraft, player);
			}
			// (On again while swinging back: the horse keeps the heading it held.)
			returning = false;
		} else if (!Float.isNaN(heading) && !returning) {
			returning = true;
			returnSpeed = 0.0F;
			leftYaw = player.getYRot();
		}
	}

	private static void release() {
		heading = Float.NaN;
		returning = false;
	}
}
