package dev.horsingaround.ride;

/** Per-horse riding state. Movement fields are only meaningful on the instance simulating the ride (the rider's client). */
public final class RideState {
	public int gait = RideTuning.STOP;
	/** Current speed as a multiple of the movement-speed attribute; negative when backing up. */
	public float speed;
	public float stamina = 1.0F;
	public boolean exhausted;
	/** Jump power that fired this tick, consumed by the horse mixin; 0 when none. */
	public float pendingJump;
	float yawVelocity;
	float heading;
	/** -1..1: how far the horse has committed its weight to a left/right turn. */
	float turnIntent;
	/** Degrees the head leads the body into the turn. */
	float headLead;
	/** Sideways input while weight is shifting, -1..1 (positive = right). */
	float sidestep;
	/** Intended turn rate for the visual bank; NaN when this instance is not driving the horse. */
	float bankRate = Float.NaN;
	int jumpBuffer;
	/** Out of its depth this tick (floating and swimming). */
	public boolean swimming;
	/** Climbing out of the water: current rise (blocks/tick) and ticks since the bank was last felt. */
	float climbSpeed;
	int climbLostTicks;
	int rustleCooldown;
	/** Ground contact at the start of the last ridden tick, to keep speed continuous through drops and landings. */
	boolean wasGrounded = true;
	/** The last takeoff was a jump, which already carried the ground speed into the air. */
	public boolean jumpedOff;
	/** Stride count at the last huff. */
	int lastHuffStride;
	/** Server side: where the horse was last tick, and its smoothed ground speed, for trampling. */
	double trampleLastX;
	double trampleLastZ;
	float trampleSpeed;
	/** Huffs played (for tests). */
	public int huffs;
	/** Client tick when the exhausted head shake started; Integer.MIN_VALUE when none. */
	public int headShakeStart = Integer.MIN_VALUE;
	int jumpRecovery;

	// Visual body pose, computed on every client for every horse.
	float lean;
	float leanO;
	float lastBodyYaw;
	float pitch;
	float pitchO;
	/** Rendered height minus physical height, so block step-ups become a smooth climb. */
	float heightOffset;
	float heightOffsetO;
	boolean wasOnGround = true;
	/** Rider's lean against the horse's surges (degrees, top-back positive), on a spring. */
	float inertia;
	float inertiaO;
	float inertiaVelocity;
	float lastGroundSpeed;
	/** Last terrain targets; reused while the horse stands still so probes only run when it moves. */
	float pitchTarget;
	double heightTarget;

	// Saddle motion measured from an animation pack (Entity Model Features), written each rendered frame. Client only.
	public float animatedLift;
	public float animatedForward;
	/** Sideways saddle shift, blocks, right positive. */
	public float animatedSide;
	public float animatedPitch;
	public float animatedRoll;
	public long animatedAt;
	/** Frames the head toss was applied through the animation pack (for tests). */
	public int animatedShakeFrames;
	/** Slow averages that remove the pack's rest-pose offset, leaving only the motion. */
	public float animatedLiftBase;
	public float animatedForwardBase;
	public float animatedSideBase;
	public float animatedPitchBase;
	public float animatedRollBase;

	public float heading() {
		return this.heading;
	}

	public float headLead() {
		return this.headLead;
	}

	public float sidestep() {
		return this.sidestep;
	}

	/** Roll into the turn, degrees; positive leans right. */
	public float lean(final float partialTicks) {
		return this.leanO + (this.lean - this.leanO) * partialTicks;
	}

	/** Body pitch, degrees; positive is nose up. */
	public float pitch(final float partialTicks) {
		return this.pitchO + (this.pitch - this.pitchO) * partialTicks;
	}

	/** Head-shake envelope 0..1 at the given time (client ticks), with its phase in radians in {@code phaseOut[0]}. */
	public float headShake(final float ageTicks, final float[] phaseOut) {
		final float t = ageTicks - this.headShakeStart;
		if (t < 0.0F || t > RideTuning.HEAD_SHAKE_TICKS) {
			return 0.0F;
		}
		phaseOut[0] = t * RideTuning.HEAD_SHAKE_RATE;
		final float envelope = (float) Math.sin(Math.PI * t / RideTuning.HEAD_SHAKE_TICKS);
		return envelope * envelope;
	}

	/** Rider lean from the horse speeding up (back, positive) or slowing (forward), degrees. */
	public float inertia(final float partialTicks) {
		return this.inertiaO + (this.inertia - this.inertiaO) * partialTicks;
	}

	public float heightOffset(final float partialTicks) {
		return this.heightOffsetO + (this.heightOffset - this.heightOffsetO) * partialTicks;
	}

	void resetMotion() {
		this.gait = RideTuning.STOP;
		this.speed = 0.0F;
		this.yawVelocity = 0.0F;
		this.turnIntent = 0.0F;
		this.headLead = 0.0F;
		this.sidestep = 0.0F;
		this.bankRate = Float.NaN;
		this.jumpBuffer = 0;
		this.pendingJump = 0.0F;
	}
}
