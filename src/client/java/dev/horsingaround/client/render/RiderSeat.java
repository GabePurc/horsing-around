package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.ride.RideState;
import net.minecraft.util.Mth;

/**
 * Where a rider's hips and legs go as they stand in the stirrups, brace their legs forward or give at the knees (see
 * RiderMotion). A Minecraft leg has no knee, so the legs stay straight, as a medieval rider's did in long stirrups: each
 * foot is in a stirrup hanging from the saddle, and the leg turns about it. Standing, the stirrups swing back toward
 * straight down and the hips come up and forward over them; bracing, the stirrups swing forward and the legs with them
 * while the seat stays. The horse's stirrups (where an animation pack draws them) swing by the same amount, so the feet
 * stay in them. Worked out in the horse's own frame (forward, up), blocks and radians. Render thread only.
 */
public final class RiderSeat {
	/** The last {@link #solve}: how far the hips moved from where they sit, forward and up (blocks). */
	static float hipForward;
	static float hipUp;
	/** The legs' angle forward of straight down and their splay out from the horse (radians), as the model takes them. */
	static float legForward;
	static float legSplay;

	/** Seated: the foot below the hip, and the stirrup bar it hangs from (blocks). */
	private static final float FOOT_FORWARD = RIDER_LEG_REACH * Mth.sin(STIRRUP_LEG_FORWARD);
	private static final float FOOT_DOWN = RIDER_LEG_REACH * Mth.cos(STIRRUP_LEG_FORWARD) * Mth.cos(STIRRUP_LEG_SPLAY);
	private static final float FOOT_OUT = RIDER_LEG_REACH * Mth.cos(STIRRUP_LEG_FORWARD) * Mth.sin(STIRRUP_LEG_SPLAY);
	private static final float BAR_FORWARD = FOOT_FORWARD - STIRRUP_LEATHER * Mth.sin(STIRRUP_SWING);
	private static final float BAR_UP = -FOOT_DOWN + STIRRUP_LEATHER * Mth.cos(STIRRUP_SWING);

	private RiderSeat() {
	}

	/** How far forward the stirrups hang (radians from straight down) as the rider stands and braces. */
	public static float stirrupSwing(final float stand, final float brace) {
		return STIRRUP_SWING - stand * STAND_STIRRUP_BACK + brace * BRACE_STIRRUP_FORWARD;
	}

	/** {@link #stirrupSwing(float, float)} for this frame of a ride. */
	public static float stirrupSwing(final RideState s, final float partialTicks) {
		return stirrupSwing(s.stand(partialTicks), s.brace(partialTicks));
	}

	/**
	 * Places the hips {@code rise} blocks above where they sit (they can only go as far as the straight legs reach) with
	 * the feet in the stirrups as they hang for {@code stand} and {@code brace}: into the fields above.
	 */
	static void solve(final float stand, final float brace, final float rise) {
		final float swing = stirrupSwing(stand, brace);
		final float footForward = BAR_FORWARD + STIRRUP_LEATHER * Mth.sin(swing);
		final float footUp = BAR_UP - STIRRUP_LEATHER * Mth.cos(swing);
		// The foot keeps its place out from the horse, so the legs come in a little as they straighten under the rider.
		final float reach = Mth.sqrt(RIDER_LEG_REACH * RIDER_LEG_REACH - FOOT_OUT * FOOT_OUT);
		final float up = Math.min(rise, footUp + reach * STAND_REACH_MAX);
		final float down = up - footUp;
		final float cos = Math.min(Mth.sqrt(down * down + FOOT_OUT * FOOT_OUT) / RIDER_LEG_REACH, 1.0F);
		final float angle = (float) Math.acos(cos);
		hipUp = up;
		hipForward = footForward - RIDER_LEG_REACH * Mth.sin(angle);
		legForward = angle;
		legSplay = (float) Math.asin(Math.min(FOOT_OUT / (RIDER_LEG_REACH * cos), 1.0F));
	}
}
