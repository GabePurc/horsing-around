package dev.horsingaround.shoulder;

/** Fixed camera constants. Player-tweakable framing lives in {@link dev.horsingaround.shoulder.config.ShoulderConfig}. */
public final class ShoulderTuning {
	/** The camera snaps in when a wall comes between it and the player, and eases back out at this rate per second. */
	public static final float REACH_RECOVER_RATE = 4.0F;
	/** Keep the camera this far off walls. */
	public static final float WALL_MARGIN = 0.15F;
	/** How far the centre-screen target is searched for; aiming and shooting go there. */
	public static final float AIM_RANGE = 96.0F;
	/** Entities count as the target within this range (beyond it, blocks only). */
	public static final float AIM_ENTITY_RANGE = 48.0F;

	private ShoulderTuning() {
	}
}
