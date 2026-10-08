package dev.horsingaround.client.api;

import dev.horsingaround.client.RideCamera;

/**
 * Public contract for camera add-ons (such as Horsing Around: Over the Shoulder, which keeps a compile-only copy of
 * these signatures; change both together). An add-on that places the third-person camera
 * itself calls {@link #claimThirdPerson()}, and can use the riding values here so it moves the way the horse does.
 */
public final class RideCameraApi {
	private RideCameraApi() {
	}

	/**
	 * While claimed, this mod stops doing anything to the third-person riding view and the caller does it all: it stops
	 * placing the camera, its camera height and distance settings don't apply, and it no longer eases the pitch back to
	 * a riding angle. Mount/dismount view switching, height smoothing and the values below keep working.
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

	/**
	 * Distance the riding camera is designed to sit back at this speed (falling behind as the horse speeds up), blocks;
	 * this mod's own camera distance setting doesn't apply (the caller has its own).
	 */
	public static float distance(final float partialTicks) {
		return RideCamera.designedDistance(partialTicks);
	}

	/** Gentle saddle lift for this frame, blocks; 0 when View Bobbing is off. */
	public static float bounce() {
		return RideCamera.frameLift();
	}
}
