package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.ride.RideState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * How the saddle is moving this frame relative to the horse's resting pose. When an animation pack moves the horse's
 * body (Fresh Animations through Entity Model Features), this is measured from the animated model so the rider rides
 * the real motion. Otherwise it is generated here from the leg cycle and applied to the horse as well, so both move
 * together. Reused per caller; render thread only.
 */
public final class SaddleMotion {
	/** Frames drawn (counted as each frame's camera is placed): motion measured in an earlier frame than the last is stale. */
	private static int frame;

	/** Saddle height change, blocks. */
	public float lift;
	/** Saddle shift along the horse's heading, blocks. */
	public float forward;
	/** Saddle shift to the horse's right, blocks (the side-to-side sway of a walk or trot). */
	public float side;
	/** Saddle pitch, degrees, nose up positive. */
	public float pitch;
	/** Saddle roll, degrees, right positive. */
	public float roll;
	/**
	 * Generated (the plain model, see Strides): how much lower the body sits on its legs while moving, blocks. Not in the
	 * lift, which the camera's bounce follows.
	 */
	public float crouch;
	/** Generated here, so the horse model must be moved by it too. */
	public boolean synthetic;
	/** Leg-animation speed, 0..1. */
	public float limbSpeed;
	/** 0..1 share of the gallop (vs canter) cycle. */
	public float gallop;
	/** 0..1 how much of the motion is a canter or gallop. */
	public float run;
	/** 0..1 how much the horse is moving at all. */
	public float moving;

	/** Once a frame, before anything is drawn. */
	public static void nextFrame() {
		frame++;
	}

	public static int frame() {
		return frame;
	}

	public SaddleMotion compute(final LivingEntity horse, final RideState s, final float partialTicks) {
		final float limbSpeed = horse.walkAnimation.speed(partialTicks);
		// The gait shares go by the pace the horse goes at (the legs' animation speed on the ground), not the legs: in the
		// air their stride stops, and the rider sat up out of a gallop's forward seat in a tick.
		final float pace = s.pace(partialTicks);
		final float run = Strides.run(pace);
		final float gallop = Strides.gallop(pace);
		this.limbSpeed = limbSpeed;
		this.gallop = gallop;
		this.run = run;
		this.moving = Math.min(pace * 5.0F, 1.0F);

		// (Measured this frame or the last: by frames, not time, so a hitch or a low frame rate doesn't make a horse a pack
		// animates look unanimated, and get the vanilla model's motion on top of the pack's.)
		if (frame - s.animatedFrame <= 1) {
			this.synthetic = false;
			this.lift = s.animatedLift - s.animatedLiftBase;
			this.forward = s.animatedForward - s.animatedForwardBase;
			this.side = s.animatedSide - s.animatedSideBase;
			this.pitch = s.animatedPitch - s.animatedPitchBase;
			this.roll = s.animatedRoll - s.animatedRollBase;
			this.crouch = 0.0F;
			return this;
		}

		this.synthetic = true;
		// (In time with the plain model's own stride and its blend of gaits, as much as its strides sweep; see Strides.)
		final float[] share = s.gaitShare;
		final float t = Strides.phase(s);
		final float sweep = horse.onGround() ? s.gaitSweep : 0.0F;
		this.lift = sweep * Strides.bodyLift(t, share[0], share[1], share[2], share[3]);
		this.pitch = sweep * Strides.bodyPitch(t, share[0], share[1], share[2], share[3]);
		// Lower on its legs while moving: as far as a leg turned to the end of its stance comes down.
		this.crouch = s.gaitSweep * Strides.crouch(share[0], share[1], share[2], share[3]);
		this.forward = 0.0F;
		this.side = 0.0F;
		this.roll = 0.0F;
		return this;
	}
}
