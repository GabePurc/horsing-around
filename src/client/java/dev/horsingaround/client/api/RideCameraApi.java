package dev.horsingaround.client.api;

import dev.horsingaround.client.RideCamera;

/**
 * For camera add-ons (such as Horsing Around: Over the Shoulder). An add-on that places the third-person camera
 * itself calls {@link #claimThirdPerson()}, and can use the riding values here so it moves the way the horse does.
 */
public final class RideCameraApi {
	private RideCameraApi() {
	}

	/**
	 * While claimed, this mod stops placing the third-person riding camera and the caller does it. Mount/dismount view
	 * switching, height smoothing and the values below keep working.
	 */
	public static void setThirdPersonClaimed(final boolean claimed) {
		RideCamera.setThirdPersonClaimed(claimed);
	}

	/** Whether the local player is riding a horse this mod controls. */
	public static boolean isRiding() {
		return RideCamera.isRiding();
	}

	/** Rider eye height smoothed through step climbs and terrain, for the given partial tick. */
	public static double eyeY(final float partialTicks) {
		return RideCamera.eyeY(partialTicks);
	}

	/** 0 at a standstill, 1 at full gallop. */
	public static float speedFraction() {
		return RideCamera.speedFraction();
	}

	/** Distance the riding camera would sit back at this speed, blocks. */
	public static float distance(final float partialTicks) {
		return RideCamera.distance(partialTicks);
	}

	/** Gentle saddle lift for this frame, blocks; 0 when View Bobbing is off. */
	public static float bounce() {
		return RideCamera.frameLift();
	}
}
