package dev.horsingaround.gametest;

import dev.horsingaround.ride.RideState;
//? if >=1.21.2
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Where leads were drawn last frame, for one horse (its lead's end on it) and one led animal (its lead's end on whoever
 * holds it), as the renderer worked them out; and where points are on a horse's body as drawn. Tests only.
 */
public final class LeadProbe {
	private static int horseId = -1;
	private static int ledId = -1;
	/** The lead's end on the watched horse, and on whoever holds the watched animal; null until drawn. */
	public static Vec3 onHorse;
	public static Vec3 onHolder;
	private static final Matrix4f INVERSE = new Matrix4f();

	private LeadProbe() {
	}

	/** Watches the leads of {@code horse} and {@code led} from the next frame. */
	public static void watch(final Entity horse, final Entity led) {
		horseId = horse.getId();
		ledId = led.getId();
		onHorse = null;
		onHolder = null;
	}

	//? if >=1.21.2 {
	public static void extracted(final Entity entity, final EntityRenderState state) {
		//? if >=1.21.6 {
		if (state.leashStates == null || state.leashStates.isEmpty()) {
			return;
		}
		drawn(entity, state.leashStates.get(0).start, state.leashStates.get(0).end);
		//?} else {
		/*if (state.leashState != null) {
			drawn(entity, state.leashState.start, state.leashState.end);
		}
		*///?}
	}
	//?}

	/** A lead drawn from {@code leashed} ({@code start} on it) to whoever holds it ({@code end}). */
	public static void drawn(final Entity leashed, final Vec3 start, final Vec3 end) {
		if (leashed.getId() == horseId) {
			onHorse = start;
		} else if (leashed.getId() == ledId) {
			onHolder = end;
		}
	}

	/** {@code world} in the frame of the horse's body as last drawn. */
	public static Vector3f onBody(final RideState ride, final Vec3 world) {
		return INVERSE.set(ride.drawnPose).invert().transformPosition(
			(float) (world.x - ride.drawnCameraX), (float) (world.y - ride.drawnCameraY), (float) (world.z - ride.drawnCameraZ), new Vector3f());
	}

	/** Where the point {@code body} on the horse's body was last drawn, in the world. */
	public static Vec3 inWorld(final RideState ride, final Vector3f body) {
		final Vector3f at = ride.drawnPose.transformPosition(body, new Vector3f());
		return new Vec3(at.x + ride.drawnCameraX, at.y + ride.drawnCameraY, at.z + ride.drawnCameraZ);
	}
}
