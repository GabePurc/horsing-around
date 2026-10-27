package dev.horsingaround.ride;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Per-horse riding state. Movement fields are only meaningful on the instance simulating the ride (the rider's client). */
public final class RideState {
	public int gait = RideTuning.STOP;
	/** Locked into riding on by a double tap of W: rides as if W were held, until S. */
	public boolean runLocked;
	/** Ride ticks since W was last pressed, for the double tap. */
	int sinceForwardTap = Integer.MAX_VALUE;
	/** Ridden by a player, so its collision box is narrowed (see the living-entity mixin); kept in step on every side. */
	public boolean narrow;
	/** Left by its rider where only the narrowed box fits (between two trunks): it keeps that box until it is out. */
	public boolean keptNarrow;
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
	/** The rider's line meets a hurdle (a fence, a wall) the rider can jump: the horse doesn't go round it. */
	boolean hurdleOnLine;
	/**
	 * A hurdle jump: the least climb speed the takeoff needs (blocks/tick; 0 for a plain jump), and the forward speed and
	 * heading it holds in the air until it lands (0 when not hurdling). Hurdles cleared so far (for tests).
	 */
	public float jumpLift;
	public float hurdleForward;
	public float hurdleYaw;
	public int hurdles;
	int rustleCooldown;
	/** Ground contact at the start of the last ridden tick, to keep speed continuous through drops and landings. */
	boolean wasGrounded = true;
	/** The last takeoff was a jump, which already carried the ground speed into the air. */
	public boolean jumpedOff;
	/** In the air from a jump (the rider's or up a ledge) rather than from stepping off something. */
	boolean leapt;

	/** Whether the horse jumped (rather than stepped or fell off something) into the flight it is in. */
	public boolean leapt() {
		return this.leapt;
	}
	/** Stride count at the last huff. */
	int lastHuffStride;
	/**
	 * Server side, for trampling while ridden: where the horse was over the last {@link Trample#WINDOW} ticks (a ring
	 * indexed by {@link #trampleTick}), and how many of those ticks are filled.
	 */
	final double[] trampleX = new double[Trample.WINDOW];
	final double[] trampleZ = new double[Trample.WINDOW];
	int trampleTick;
	int trampleSamples;
	/** Huffs played (for tests). */
	public int huffs;
	/** Client tick when the exhausted head shake started; Integer.MIN_VALUE when none. */
	public int headShakeStart = Integer.MIN_VALUE;
	int jumpRecovery;
	/** Detour around an obstacle ahead, degrees added to the heading; eased toward avoidTarget, re-planned every few ticks. */
	public float avoidOffset;
	float avoidTarget;
	int avoidReplan;
	/**
	 * The way the horse is taking through what is on the rider's line (Awareness.WAY_*), the pace the way allows (speed
	 * units; MAX_VALUE for its full pace), and with no way through in sight, how far the best one goes before it meets
	 * something (blocks) and whether that is a danger.
	 */
	public byte way;
	float wayPace = Float.MAX_VALUE;
	float wayRoom;
	boolean wayDanger;
	/** How the way taken steers: degrees off the line, held for this many ticks (then back for the line). */
	float wayAngle;
	int wayHold;
	/** While it goes round something: how far right of the rider's line it is (blocks), the line's heading, and where the horse was last tick. */
	public float lineOffset;
	float lineYaw;
	/** The rider's view last tick, and ticks it has held still (the line is only kept once it has, and the horse has come round to it). */
	float lineViewYaw;
	int lineSettle;
	double lineLastX = Double.NaN;
	double lineLastZ;
	/** The way planned, for the steering overlay: world x and z of points along it every half block, and how many. */
	public float[] wayX;
	public float[] wayZ;
	public int wayPoints;
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

	/** Ground read under the front and hind hooves, the drawn height offset, and whether in flight (for tests). */
	public String debugGround() {
		return String.format(java.util.Locale.ROOT, "g%.2f/%.2f e%.2f/%.2f off%.2f%s", this.foreGround, this.hindGround, this.fore, this.hind, this.heightOffset,
			this.flying ? " FLY" : "");
	}
	/** Distance along the way ahead to a wall this tick; MAX_VALUE when none. */
	float wallAhead = Float.MAX_VALUE;
	/**
	 * Jumping up a ledge: ticks since the ledge was spotted (0 when not), whether it has taken off, whether the rider
	 * asked for it (jump pressed at the ledge), the ledge's top, the heading, the forward speed of the jump, the climb
	 * speed the hind legs' push builds to, ticks since takeoff, and how far it has sunk onto its haunches (0..1).
	 */
	public int ledgeTicks;
	public boolean ledgeAir;
	boolean ledgeAsked;
	/** How far ahead the ledge was spotted from (blocks): the run-up keeps looking that far. */
	float ledgeReach;
	double ledgeTop;
	float ledgeYaw;
	float ledgeForward;
	float ledgeLift;
	int ledgeAirTicks;
	/** Where the last look for a ledge to climb found none (so standing still against the same face it isn't redone). */
	double ledgeMissX = Double.NaN;
	double ledgeMissY;
	double ledgeMissZ;
	float ledgeMissYaw;
	float ledgeCrouch;
	/**
	 * Room between the horse's head and its rider (render thread): how far the horse stretches its neck forward and the
	 * rider sits back from folding over it (radians), when they were last eased (the horse's age, ticks), and how far apart
	 * the heads were in the last frame drawn (centre to centre, blocks; for tests).
	 */
	public float neckReach;
	public float sitBack;
	public float roomAt = Float.NaN;
	public float headGap = Float.MAX_VALUE;
	/**
	 * How the rider carries their weight (see RiderMotion), each on a spring: standing in the stirrups (0 sitting, 1 up out
	 * of the saddle), bracing the legs forward (0..1), and giving at the hips after a landing (0..1, a little below 0 as it
	 * comes back up); and how fast the drawn body rose the tick before (blocks/tick).
	 */
	float stand;
	float standO;
	float standVelocity;
	float brace;
	float braceO;
	float braceVelocity;
	float settle;
	float settleO;
	float settleVelocity;
	float riderLastRise;

	/** The move in progress is the horse's in the air, with a hoof put down for it (see the entity mixin): steps up only AIR_STEP. */
	public boolean airFooting;

	/** Rising up a ledge (the climb, before it comes down onto the top). */
	public boolean climbingLedge() {
		return this.ledgeAir && this.ledgeLift > 0.0F;
	}

	/** Ledge jumps made, and where and when the last one took off (for tests). */
	public int ledgeClimbs;
	public double ledgeTakeoffX;
	public double ledgeTakeoffZ;
	public int ledgeTakeoffTick;

	// Visual body pose, computed on every client for every horse.
	float lean;
	float leanO;
	float lastBodyYaw;
	float pitch;
	float pitchO;
	/** The pitch a tick before {@link #pitchO}, for the curve through the last three (see {@link #pitch(float)}). */
	float pitchOO;
	/** The tilt of the line between the two ends on its legs, degrees (the drawn tilt follows it, no faster than PITCH_RATE_MAX). */
	float groundTilt;
	/** Tilt with the flight while airborne (nose up taking off, down landing), degrees, on top of the terrain pitch. */
	float jumpPitch;
	float jumpPitchO;
	/** The jump tilt a tick before {@link #jumpPitchO}, for its curve between ticks and how fast it is turning. */
	float jumpPitchOO;
	/** Ticks since the horse left the ground (client only). */
	int flightTicks;
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
	float lastGroundSpeedO;
	/** The pace the rider sits to, 0..1 on the legs' animation speed scale (see RideController.tickVisual). */
	float pace;
	float paceO;
	/**
	 * The body carried on its legs: world height of the front and the back of the body (at hoof level) and how fast each
	 * is moving (blocks/tick; see RideController.steps), and the ground each is carried toward (reused while the horse
	 * stands still, so probes only run when it moves). NaN until first placed.
	 */
	double fore = Double.NaN;
	double hind = Double.NaN;
	float foreVelocity;
	float hindVelocity;
	double foreGround;
	double hindGround;
	/** The ground each end is carried toward: a step up eased in over a few ticks at a run (NaN until first set). */
	double foreRise = Double.NaN;
	double hindRise = Double.NaN;
	/** How fast the drawn body rose last tick, blocks/tick. */
	public float drawnRise;
	/** How far the front of the drawn body could come down before the chest meets the ground ahead of it (blocks). */
	public float chestRoom = Float.MAX_VALUE;
	/** The ground actually under the front and hind hooves (NaN on level ground or where there is none). */
	double foreFoot = Double.NaN;
	double hindFoot = Double.NaN;
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
	/** The legs' jump shape is from a jump (not a fall off a step): it eases out on landing rather than being dropped. */
	boolean leapLegs;
	/**
	 * Where the model hasn't been drawn yet: how far (blocks) the front and the hind pairs of legs draw up onto ground
	 * higher than a straight leg reaches (on top of standing upright against the body's tilt).
	 */
	float foreLeg;
	float foreLegO;
	float hindLeg;
	float hindLegO;
	/** Share of the last move's travel that a collision took (0 when nothing was hit). */
	public float blocked;
	/** The ridden move in progress (see the entity mixin): where it started and what it asked for. */
	public boolean moving;
	public double moveFromX;
	public double moveFromZ;
	public Vec3 moveDelta = Vec3.ZERO;
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
	/** The frame (see the saddle motion) it was last measured in. */
	public int animatedFrame = -10;
	/** Frames the head toss was applied through the animation pack (for tests). */
	public int animatedShakeFrames;
	/**
	 * Where the model was last drawn (the model root's parent frame to camera-relative world) and the camera then, so the
	 * legs can find the ground under each hoof as it is drawn. Client only.
	 */
	public final Matrix4f drawnPose = new Matrix4f();
	public double drawnCameraX;
	public double drawnCameraY;
	public double drawnCameraZ;
	public boolean drawnPoseSet;
	/**
	 * The plain model's own legs (see Strides), client only. The stride: how far through it (strides; it runs backwards
	 * when the horse backs up), when last moved on (game ticks, NaN until the horse is drawn), strides a second, the share
	 * of it each front and hind hoof stands; the horse's ground velocity
	 * (blocks a second, smoothed), where it was, its share of each gait (walk, trot, canter, gallop), how much its legs
	 * stride (0 standing to 1), and how much it stands on the ground (0 in a jump or swimming).
	 */
	public double gaitPhase;
	public double gaitAt = Double.NaN;
	public float gaitRate;
	public float gaitForeStance;
	public float gaitHindStance;
	public double gaitVelocityX;
	public double gaitVelocityZ;
	public double gaitX;
	public double gaitZ;
	public final float[] gaitShare = new float[4];
	public float gaitStride;
	/** The plain model's neck on its spring (see Strides.advance): how far it is swung down from its carriage (radians), and how fast. */
	public float neck;
	public float neckSpeed;
	/** How far the legs sweep on the ground against their gait's full stance (0 standing, 1 full strides; see Strides.advance). */
	public float gaitSweep;
	public float gaitGround;
	/** Every hoof on the ground and under its leg (the horse stood square): with the horse still, the stride stops. */
	public boolean gaitSettled;
	/**
	 * Per leg: whether its hoof is on the ground (else in its swing), where it stands (world); its swing: where it set off
	 * from (world), how far through it it is (0 lifting off, 1 touching down) and whether it set off backing up; how far it
	 * has stood (a share of its stance); how far through its stride it was last frame, and its pose this frame, for every
	 * layer of the model (turned forward and sideways, radians, and folded up, pixels); when the legs were last posed
	 * (game ticks).
	 */
	public final boolean[] hoofDown = new boolean[4];
	public final double[] hoofX = new double[4];
	public final double[] hoofY = new double[4];
	public final double[] hoofZ = new double[4];
	public final double[] hoofFromX = new double[4];
	public final double[] hoofFromY = new double[4];
	public final double[] hoofFromZ = new double[4];
	public final float[] swingThrough = new float[4];
	public final boolean[] swingBack = new boolean[4];
	public final float[] stood = new float[4];
	public final float[] legPhase = new float[4];
	public final float[] gaitLegTurn = new float[4];
	public final float[] gaitLegSide = new float[4];
	/**
	 * Per leg, pixels: how far short of its length its hoof is from where the model puts its top (the body's fit counts it
	 * as drawn up), where its top (the joint with the hidden upper bone) goes from there, down and back (the model's y and
	 * z), and how far it is drawn up along its length.
	 */
	public final float[] gaitLegFold = new float[4];
	public final float[] gaitJointY = new float[4];
	public final float[] gaitJointZ = new float[4];
	public final float[] gaitLegDraw = new float[4];
	/** Per leg in two pieces, how far its knee (front) or hock (hind) is bent, radians, toward the tail positive. */
	public final float[] gaitKnee = new float[4];
	/** The way the body faced last frame (level), and how fast it turns (radians a second, as the facing turns x to z). */
	public double gaitFaceX;
	public double gaitFaceZ;
	public float gaitYawRate;
	/** Per leg, model pixels: how high it holds its corner of the body (see GAIT_BODY_FOLLOW), and did as it lifted. */
	public final float[] gaitCorner = new float[4];
	public final float[] liftCorner = new float[4];
	/** How the body pitches (nose up) and rolls (to its right) with how its legs carry it, radians (see GAIT_BODY_FOLLOW). */
	public float gaitBodyPitch;
	public float gaitBodyRoll;
	/** How the corners stand on average (the same, radians), left out of the above. */
	public float gaitBodyPitchMean;
	public float gaitBodyRollMean;
	/** Per leg, the last frame: how far from the top of its leg its hoof is (blocks), and was as it was set down (or -1). */
	public final float[] hoofReach = new float[4];
	public final float[] plantReach = new float[4];
	/**
	 * Per leg, the last frame: how far out to the side of its leg's top its hoof is (blocks); as it lifted, how far its leg
	 * was turned forward (radians) and where the top of its leg was (world).
	 */
	public final float[] hoofSide = new float[4];
	public final float[] liftTurn = new float[4];
	public final double[] liftHipX = new double[4];
	public final double[] liftHipZ = new double[4];
	/** Per leg in its swing: where it lands (world; x NaN before it has looked), and the spot its swing heads for, after it. */
	public final double[] goalX = new double[4];
	public final double[] goalY = new double[4];
	public final double[] goalZ = new double[4];
	public final double[] landX = new double[4];
	public final double[] landY = new double[4];
	public final double[] landZ = new double[4];
	/** Per leg: where the top of its leg is and how fast it goes (world, blocks a second). */
	public final double[] hipX = new double[4];
	public final double[] hipY = new double[4];
	public final double[] hipZ = new double[4];
	public final double[] hipVelocityX = new double[4];
	public final double[] hipVelocityY = new double[4];
	public final double[] hipVelocityZ = new double[4];
	public double gaitPosedAt = Double.NaN;
	/** Per leg (front left, front right, hind left, hind right), the last frame: how far it was drawn up onto its ground, blocks. */
	public final float[] legRise = new float[4];
	/** Per leg, the last frame: how far its hoof was moved back off a step's face it would stand in (blocks, toward the tail). */
	public final float[] legShift = new float[4];
	/** Per leg, the last frame: how far it was swung back off a step's face (radians). */
	public final float[] legBack = new float[4];
	/**
	 * Per leg, how fast {@link #legRise} (blocks a second) and {@link #legBack} (radians a second) are changing on their
	 * springs.
	 */
	public final float[] legRiseVelocity = new float[4];
	public final float[] legBackVelocity = new float[4];
	/** Per leg, where its sole was last frame as the animation put it (world x and z; NaN, not yet) and how fast it moves, smoothed (blocks a second). */
	public final double[] legSoleX = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
	public final double[] legSoleZ = new double[4];
	public final float[] legSoleVelocityX = new float[4];
	public final float[] legSoleVelocityZ = new float[4];
	/**
	 * Per leg, a step of its own up onto a step (see StepLegs), client only: seconds into it (NaN, none) and how long it
	 * takes; where the hoof lifts from and lands (world x and z); the ground it lifts from, the step's top and the ground it
	 * lands on (world heights).
	 */
	public final float[] stepTime = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
	public final float[] stepLength = new float[4];
	/** How near a step the horse is (0 to 1, client only; see RideController.stepNear), eased. */
	public float stepNear;
	/** How far the ground each end of the body follows is rounded off (blocks; see RideController.steps), eased. */
	public float stepSmooth;
	/** Per leg, how long a step of its own has waited, lifted, for its leg to reach over the edge (seconds). */
	public final float[] stepWaited = new float[4];
	/** Per leg, whether a step of its own began with the hoof already off the ground (the body lifted it): it lifts on with the body. */
	public final boolean[] stepAir = new boolean[4];
	/** Per leg, where along the body a hoof the body has lifted off the ground before a step is carried (blocks; NaN, not carried). */
	public final float[] stepCarry = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
	public final double[] stepFromX = new double[4];
	public final double[] stepFromZ = new double[4];
	public final double[] stepToX = new double[4];
	public final double[] stepToZ = new double[4];
	/** Per leg, where the edge of the step a step of its own goes over is (world x and z, on the hoof's line). */
	public final double[] stepEdgeX = new double[4];
	public final double[] stepEdgeZ = new double[4];
	public final double[] stepFromY = new double[4];
	public final double[] stepTop = new double[4];
	public final double[] stepLand = new double[4];
	/** Per leg, where a hoof put down by StepLegs is held (world x and z; NaN, not held) until the animation's stride comes up to it. */
	public final double[] stepHoldX = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
	public final double[] stepHoldZ = new double[4];
	/** Per leg, how far StepLegs moves the hoof along the heading when it isn't stepping or holding it (blocks): a landing placed, or what is left of a hold let go. */
	public final float[] stepShift = new float[4];
	/**
	 * Per leg, last frame: how far its leg was turned to put its hoof there (radians; on a spring, see STEP_TURN_TIME, its
	 * speed in radians a second), and how high its lower edge was (world).
	 */
	public final float[] stepTurn = new float[4];
	public final float[] stepTurnVelocity = new float[4];
	/** How much of the jump's shape the legs take (0..1, see AirLegs.shape), eased each frame, and when it last was (game time). */
	public float airShape;
	public double airShapeAt = Double.NaN;
	public final double[] stepBottom = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
	/**
	 * Per leg, the animation's stride against the body: where along the body its hoof was last frame (blocks; NaN, not yet),
	 * how fast that moves (blocks a second, smoothed), where the animation lifts it on average (NaN, not seen yet) and its lift
	 * last frame.
	 */
	public final float[] strideRel = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
	public final float[] strideRelRate = new float[4];
	public final float[] strideLiftRel = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
	public final float[] stridePackLift = new float[4];
	/** Whether any leg was drawn up or moved off a face last frame, whether the legs were left as the model's own, and whether some hoof found its ground. */
	public boolean legsActive;
	public boolean legsQuiet;
	public boolean legsFound;
	/** When the legs were last posed (game time in ticks, with the partial tick; NaN, not yet), for how long since. */
	public double legRiseAt = Double.NaN;
	/**
	 * How far the drawn body is raised (blocks) because a leg would otherwise have to draw up further than it can, from
	 * the ground under each hoof where the model is drawn (see GroundLegs); added to {@link #heightOffset(float)}. Client only.
	 */
	public float drawnFit;
	/** How fast {@link #drawnFit} is changing, blocks a second. */
	public float drawnFitVelocity;
	/**
	 * The rider's bow (client only): how far it was drawn last frame (0 when not drawing), and when it was loosed (the
	 * horse's ticks with the partial tick; NaN while drawing or none), for the follow-through.
	 */
	public float bowPull;
	public float bowLoosedAt = Float.NaN;
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
		return (float) curve(this.pitchOO, this.pitchO, this.pitch, partialTicks);
	}

	/**
	 * Between ticks, a value worked out each tick ({@code a}, {@code b}, {@code c} the last three) drawn along a curve
	 * from midpoint to midpoint (a quadratic B-spline): it never changes speed in a jump at a tick. Drawn as straight lines
	 * between ticks, the tilt changing fast juddered at twenty a second, and the rider with it (play-test feedback,
	 * 2026-10-23). Half a tick behind. (Not the body's height: half a tick behind going down a slope, the hooves hung.)
	 */
	static double curve(final double a, final double b, final double c, final float partialTicks) {
		final double p = partialTicks;
		return (1.0 - p) * (1.0 - p) * (a + b) * 0.5 + 2.0 * p * (1.0 - p) * b + p * p * (b + c) * 0.5;
	}

	/** Tilt with the flight while airborne, degrees; positive is nose up. */
	public float jumpPitch(final float partialTicks) {
		return (float) curve(this.jumpPitchOO, this.jumpPitchO, this.jumpPitch, partialTicks);
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
	/** The pace the rider sits to and the saddle rocks with, 0..1 as the legs' animation speed. */
	public float pace(final float partialTicks) {
		return this.paceO + (this.pace - this.paceO) * partialTicks;
	}

	public float inertia(final float partialTicks) {
		return this.inertiaO + (this.inertia - this.inertiaO) * partialTicks;
	}

	/** How far the rider stands in the stirrups, 0 (sitting) to 1 (up out of the saddle). */
	public float stand(final float partialTicks) {
		return this.standO + (this.stand - this.standO) * partialTicks;
	}

	/** How far the rider braces their legs forward in the stirrups, 0..1. */
	public float brace(final float partialTicks) {
		return this.braceO + (this.brace - this.braceO) * partialTicks;
	}

	/** How far the rider gives at the hips after a landing, 0..1 (a little below 0 as they come back up). */
	public float settle(final float partialTicks) {
		return this.settleO + (this.settle - this.settleO) * partialTicks;
	}

	/** The drawn body is below where the horse stands (landing, crouching): the legs take it up. */
	public boolean lowered() {
		return this.heightOffset < -1.0E-3F || this.heightOffsetO < -1.0E-3F;
	}

	/** How fast the horse moves over the ground, blocks/tick. */
	public float groundSpeed(final float partialTicks) {
		return this.lastGroundSpeedO + (this.lastGroundSpeed - this.lastGroundSpeedO) * partialTicks;
	}

	/** The legs' jump shape is from a jump, and eases out on landing. */
	public boolean leapLegs() {
		return this.leapLegs;
	}

	public float heightOffset(final float partialTicks) {
		return this.heightOffsetO + (this.heightOffset - this.heightOffsetO) * partialTicks + this.drawnFit;
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

	/** Front legs drawn up onto higher ground (where the model hasn't been drawn yet), blocks. */
	public float foreLeg(final float partialTicks) {
		return this.foreLegO + (this.foreLeg - this.foreLegO) * partialTicks;
	}

	/** Hind legs drawn up onto higher ground (where the model hasn't been drawn yet), blocks. */
	public float hindLeg(final float partialTicks) {
		return this.hindLegO + (this.hindLeg - this.hindLegO) * partialTicks;
	}

	/** Steered by a rider since the ride state was last reset: when the rider gets off, it is reset once. */
	boolean steered;

	/** The rider's pose on this horse (bow, neck room), cleared once no one is on it so a new ride doesn't pick it up. */
	void resetPose() {
		this.bowPull = 0.0F;
		this.bowLoosedAt = Float.NaN;
		this.neckReach = 0.0F;
		this.sitBack = 0.0F;
		this.roomAt = Float.NaN;
	}

	void resetMotion() {
		this.steered = false;
		this.gait = RideTuning.STOP;
		this.runLocked = false;
		this.sinceForwardTap = Integer.MAX_VALUE;
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
		this.way = 0;
		this.wayPoints = 0;
		this.lineOffset = 0.0F;
		this.lineLastX = Double.NaN;
		this.dangerAhead = Float.MAX_VALUE;
		this.gapAhead = Float.MAX_VALUE;
		this.wallAhead = Float.MAX_VALUE;
		this.ledgeTicks = 0;
		this.ledgeAir = false;
		this.ledgeAsked = false;
		this.ledgeCrouch = 0.0F;
		this.ledgeLift = 0.0F;
		this.leapt = false;
		this.ledgeOnLine = false;
		this.hurdleOnLine = false;
		this.jumpLift = 0.0F;
		this.hurdleForward = 0.0F;
		this.bankTicks = 0;
		this.cut = 0.0F;
		this.cutSquat = 0.0F;
		this.scuffReady = true;
	}
}
