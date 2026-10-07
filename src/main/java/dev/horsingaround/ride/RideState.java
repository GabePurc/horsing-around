package dev.horsingaround.ride;

/** Per-horse riding state. Movement fields are only meaningful on the instance simulating the ride (the rider's client). */
public final class RideState {
	public int gait = RideTuning.STOP;
	/** Ridden by a player, so its collision box is narrowed (see the living-entity mixin); kept in step on every side. */
	public boolean narrow;
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
	/**
	 * Heaving out of the water up a bank: ticks into the heave (0 when not), how long the rise takes, where it started
	 * and the bank's top, and the heading it climbs along.
	 */
	public int bankTicks;
	float bankDuration;
	double bankFrom;
	double bankTop;
	float bankYaw;
	/** Ticks until a swimming horse looks properly at the bank in front of it again. */
	int bankLook;
	/** Banks climbed out onto (for tests). */
	public int bankClimbs;
	/**
	 * How hard the horse is cutting round, 0..1 (from how far off it the rider looks; eases off as it comes round), whether
	 * the next hard cut may scuff the ground, and how far it has sat back for it (0..1, visual).
	 */
	public float cut;
	boolean scuffReady = true;
	float cutSquat;
	/** Hard cuts that scuffed the ground (for tests). */
	public int cuts;
	/** The rider's line meets a 2-block ledge the horse will jump rather than go round. */
	boolean ledgeOnLine;
	int rustleCooldown;
	/** Ground contact at the start of the last ridden tick, to keep speed continuous through drops and landings. */
	boolean wasGrounded = true;
	/** The last takeoff was a jump, which already carried the ground speed into the air. */
	public boolean jumpedOff;
	/** In the air from a jump (the rider's or up a ledge) rather than from stepping off something. */
	boolean leapt;
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
	/** Detour around an obstacle ahead, degrees added to the heading; eased toward avoidTarget, re-planned every few ticks. */
	public float avoidOffset;
	float avoidTarget;
	int avoidReplan;
	/** Distance along the heading to the first danger, and to the first jumpable gap, this tick; MAX_VALUE when none. */
	float dangerAhead = Float.MAX_VALUE;
	/** The danger ahead is a hazard with safe ground just beyond, so the rider may jump it. */
	boolean dangerJumpable;
	float gapAhead = Float.MAX_VALUE;
	/** Tick of the last refusal (snort and head toss), and refusals so far (for tests). */
	int lastRefusal = Integer.MIN_VALUE / 2;
	public int refusals;
	/** Times the edge and hazard guard planted the horse's feet (for tests). */
	public int guardStops;
	public int guardChecks;

	/** For tests: distance to the danger and the wall ahead this tick. */
	public float debugDanger() {
		return this.dangerAhead;
	}

	public float debugWall() {
		return this.wallAhead;
	}

	public float debugGap() {
		return this.gapAhead;
	}

	/** For tests and the steering overlay: world height of the ground carrying the front and the back of the body. */
	public double debugFore() {
		return this.fore;
	}

	public double debugHind() {
		return this.hind;
	}
	/** Distance along the way ahead to a wall this tick; MAX_VALUE when none. */
	float wallAhead = Float.MAX_VALUE;
	/**
	 * Jumping up a ledge: ticks since the ledge was spotted (0 when not), whether it has taken off, the ledge's top, the
	 * heading, the forward speed of the jump, and how far it has sunk onto its haunches (0..1).
	 */
	public int ledgeTicks;
	public boolean ledgeAir;
	double ledgeTop;
	float ledgeYaw;
	float ledgeForward;
	float ledgeCrouch;
	/** Ledge jumps made, and where the last one took off (for tests). */
	public int ledgeClimbs;
	public double ledgeTakeoffX;
	public double ledgeTakeoffZ;

	// Visual body pose, computed on every client for every horse.
	float lean;
	float leanO;
	float lastBodyYaw;
	float pitch;
	float pitchO;
	/** Tilt with the flight while airborne (nose up taking off, down landing), degrees, on top of the terrain pitch. */
	float jumpPitch;
	float jumpPitchO;
	/** Rendered height minus physical height, so block step-ups become a smooth climb. */
	float heightOffset;
	float heightOffsetO;
	boolean wasOnGround = true;
	/**
	 * Pushing through leaves: how far the rider has a hand up in front of their face (0..1), their lean back from the
	 * push (degrees, top-back positive, on a spring), and the last clump of leaves their face met.
	 */
	float shield;
	float shieldO;
	float leafPush;
	float leafPushO;
	float leafPushVelocity;
	long lastClump;
	/** Rider's lean against the horse's surges (degrees, top-back positive), on a spring. */
	float inertia;
	float inertiaO;
	float inertiaVelocity;
	float lastGroundSpeed;
	/**
	 * Steps in two beats: world height of the ground carrying the front and the back of the body, eased in two stages
	 * (the first stage in the *Ease fields), how far the back rose last tick, and the ground under each pair of hooves
	 * (reused while the horse stands still, so probes only run when it moves). NaN until first placed.
	 */
	double fore = Double.NaN;
	double hind = Double.NaN;
	double foreEase;
	double hindEase;
	float hindRise;
	double foreGround;
	double hindGround;
	/** In the air from a jump or a fall bigger than a step: the body follows its flight instead of the ground. */
	boolean flying;
	/**
	 * In the air from a jump or a bigger fall (not swimming or climbing out): the run cycle stops and the legs take the
	 * shape of the jump, by airLegs (0..1); airRise is climbing (1) to falling (-1), for which shape.
	 */
	public boolean inAir;
	float airLegs;
	float airLegsO;
	float airRise;
	float airRiseO;
	float airRiseEase;
	/** Leg poses for steps, -1..1: front legs folded up (+) or reaching down (-); hind legs driving (+) or gathered (-). */
	float foreLeg;
	float foreLegO;
	float hindLeg;
	float hindLegO;
	/** Share of the last move's travel that a collision took (0 when nothing was hit). */
	public float blocked;
	/** Corners slipped past (for tests). */
	public int slips;
	/** Where the rider is asking the horse to go this tick (view plus A/D offset), degrees; for the steering overlay. */
	public float riderYaw;

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

	/** Tilt with the flight while airborne, degrees; positive is nose up. */
	public float jumpPitch(final float partialTicks) {
		return this.jumpPitchO + (this.jumpPitch - this.jumpPitchO) * partialTicks;
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

	/** How far the rider has a hand up against the leaves, 0..1. */
	public float shield(final float partialTicks) {
		return this.shieldO + (this.shield - this.shieldO) * partialTicks;
	}

	/** Rider lean back from pushing through leaves, degrees. */
	public float leafPush(final float partialTicks) {
		return this.leafPushO + (this.leafPush - this.leafPushO) * partialTicks;
	}

	/** Rider lean from the horse speeding up (back, positive) or slowing (forward), degrees. */
	public float inertia(final float partialTicks) {
		return this.inertiaO + (this.inertia - this.inertiaO) * partialTicks;
	}

	public float heightOffset(final float partialTicks) {
		return this.heightOffsetO + (this.heightOffset - this.heightOffsetO) * partialTicks;
	}

	/** Tail lifted by its swing (radians, up positive), its speed, and the drawn body's climb last tick. */
	float tailLift;
	float tailLiftO;
	float tailVelocity;
	double lastDrawnRise;

	/** Tail lifted by its swing with the horse's motion, radians (up positive). */
	public float tailLift(final float partialTicks) {
		return this.tailLiftO + (this.tailLift - this.tailLiftO) * partialTicks;
	}

	/** How far the legs are in the shape of a jump, 0..1. */
	public float airLegs(final float partialTicks) {
		return this.airLegsO + (this.airLegs - this.airLegsO) * partialTicks;
	}

	/** In a jump: climbing (1) to falling (-1). */
	public float airRise(final float partialTicks) {
		return this.airRiseO + (this.airRise - this.airRiseO) * partialTicks;
	}

	/** Front legs on a step, -1..1: folded up onto it (+) or reaching down for it (-). */
	public float foreLeg(final float partialTicks) {
		return this.foreLegO + (this.foreLeg - this.foreLegO) * partialTicks;
	}

	/** Hind legs on a step, -1..1: driving the hindquarters up (+) or gathered under the body going down (-). */
	public float hindLeg(final float partialTicks) {
		return this.hindLegO + (this.hindLeg - this.hindLegO) * partialTicks;
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
		this.avoidOffset = 0.0F;
		this.avoidTarget = 0.0F;
		this.dangerAhead = Float.MAX_VALUE;
		this.gapAhead = Float.MAX_VALUE;
		this.wallAhead = Float.MAX_VALUE;
		this.ledgeTicks = 0;
		this.ledgeAir = false;
		this.ledgeCrouch = 0.0F;
		this.ledgeOnLine = false;
		this.bankTicks = 0;
		this.cut = 0.0F;
		this.cutSquat = 0.0F;
		this.scuffReady = true;
	}
}
