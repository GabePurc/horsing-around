package dev.horsingaround.ride;

/**
 * Every number that shapes how riding feels, in one place. Non-final values are defaults that the player settings
 * ({@link HorseConfig}) scale or replace at runtime. Speeds are multiples of the horse's own movement-speed
 * attribute, so better horses stay better; 1.0 equals vanilla's only riding speed.
 */
public final class RideTuning {
	public static final int STOP = 0;
	public static final int WALK = 1;
	public static final int TROT = 2;
	public static final int CANTER = 3;
	public static final int GALLOP = 4;

	// ---- Gaits and momentum ----

	/** Target speed for each gait. */
	static final float[] BASE_GAIT_SPEED = {0.0F, 0.35F, 0.6F, 0.85F, 1.15F};
	public static final float[] GAIT_SPEED = BASE_GAIT_SPEED.clone();
	/** Acceleration toward each gait per tick; higher gaits build up more slowly. */
	static final float[] BASE_GAIT_ACCEL = {0.0F, 0.05F, 0.035F, 0.025F, 0.018F};
	static final float[] GAIT_ACCEL = BASE_GAIT_ACCEL.clone();
	/** Forward key released: horse eases to a stop (~2s from a gallop). */
	static float DECEL_COAST = 0.03F;
	/** Dropped a gait while still moving forward. */
	static float DECEL_REIN = 0.035F;
	/** Back key held while moving: hard stop (~0.8s from a gallop). */
	static float DECEL_BRAKE = 0.07F;
	static final float REVERSE_SPEED = -0.2F;
	static final float REVERSE_ACCEL = 0.03F;
	/**
	 * Wading: while the horse can touch the bottom it keeps its footing and slows with depth: by up to this share at
	 * WADE_DEPTH (water this many blocks above the hooves, about chest-deep), less in the shallows.
	 */
	static final float WADE_DEPTH = 1.15F;
	static final float WADE_DRAG = 0.7F;
	/** How quickly the drag builds with depth (below 1 slows the shallows more). */
	static final float WADE_DRAG_CURVE = 1.1F;
	/** Swimming: deeper than WADE_DEPTH the horse floats with its back at the waterline and head up, and swims slowly. */
	static final float SWIM_FLOAT_DEPTH = 1.15F;
	static final float SWIM_SPEED = 0.22F;
	/** Buoyancy: how firmly the horse settles to its floating depth (vertical blocks/tick per block off, and limits). */
	static final float SWIM_BUOYANCY = 0.3F;
	static final float SWIM_SINK_MAX = 0.06F;
	static final float SWIM_RISE_MAX = 0.12F;
	/**
	 * Pushing against a bank (or pressing jump) while swimming, the horse climbs: its rise builds by SWIM_CLIMB_ACCEL
	 * per tick up to SWIM_CLIMB_SPEED (blocks/tick) until it can walk out, instead of popping up.
	 */
	static final float SWIM_CLIMB_SPEED = 0.14F;
	static final float SWIM_CLIMB_ACCEL = 0.035F;
	/** Swimming is tiring: a full stamina bar lasts this long in deep water. */
	static final float STAMINA_DRAIN_SWIM = 1.0F / (40 * 20);
	/** Pushing through leaves holds the horse to this share of its gait speed. */
	static float LEAVES_SPEED_FACTOR = 0.75F;
	/** Volume of the rustle while pushing through leaves (the rabbit hop sound). */
	static float LEAVES_RUSTLE_VOLUME = 0.6F;

	/** Hitting a wall above this speed sheds momentum and drops the horse to a trot. */
	static final float CRASH_SPEED = 0.45F;
	static final float CRASH_KEEP = 0.5F;

	// ---- Steering: the horse shifts its weight first, then turns ----

	/** Turn rate limit at low speed, degrees per tick (real horses manage ~90-120 deg/s at a walk). */
	static float TURN_RATE_STILL = 6.0F;
	/**
	 * Sideways grip, m/s^2. Real horses top out around 6; a bit more keeps Minecraft terrain playable. Max turn rate is
	 * grip / speed, so the turning circle widens with speed: ~2 blocks at a walk, ~4 trot, ~8 canter, ~14 gallop.
	 */
	static float LATERAL_GRIP = 9.0F;
	/**
	 * The horse heads where the rider looks. A/D don't move the view: they set the horse this many degrees left or
	 * right of it, so the rider can ride at an angle while looking or aiming straight at something.
	 */
	static float STEER_OFFSET = 45.0F;
	/** Fraction of the heading error the horse tries to correct each tick. */
	static final float TURN_GAIN = 0.3F;
	/**
	 * How fast the horse commits its weight to a turn (fraction of a full turn per tick). Turn rate grows with the
	 * square of this commitment, so the body comes around ~0.3s after the head at a walk and ~0.2s at a gallop
	 * (about half a stride).
	 */
	static float WEIGHT_SHIFT_STILL = 0.09F;
	static float WEIGHT_SHIFT_GALLOP = 0.18F;
	/** Max change in turn rate per tick, as a fraction of the current max turn rate. */
	static float TURN_ACCEL = 0.25F;
	/** Speed scrubbed off at full turn commitment at a gallop. */
	static final float TURN_SPEED_LOSS = 0.12F;
	/** The head and neck lead into the turn: up to 30 degrees at a walk, 15 at a gallop, reacting within ~0.1s. */
	static final float HEAD_LEAD_STILL = 30.0F;
	static final float HEAD_LEAD_GALLOP = 15.0F;
	static final float HEAD_LEAD_RESPONSE = 0.5F;
	/** Below a canter the horse side-steps toward the turn while its weight shifts (~0.1-0.2 blocks at a walk). */
	static final float SIDESTEP = 0.35F;
	/** How far the rider model's head and torso turn toward the view, degrees either side of the horse. */
	public static final float LOOK_LIMIT = 110.0F;

	// ---- Stamina ----

	/** Full stamina lasts ~28s of galloping. */
	public static float STAMINA_DRAIN_GALLOP = 1.0F / (28 * 20);
	/** Stamina regained per tick at each gait (galloping never regenerates). */
	static final float[] BASE_STAMINA_REGEN = {1.0F / (6 * 20), 1.0F / (6 * 20), 1.0F / (10 * 20), 1.0F / (25 * 20), 0.0F};
	static final float[] STAMINA_REGEN = BASE_STAMINA_REGEN.clone();
	/** An exhausted horse refuses to gallop until stamina is back to this level. */
	static final float STAMINA_RECOVERED = 0.3F;
	/** Below this stamina (or while exhausted) a running horse huffs softly every other stride, a little louder as it tires. */
	static final float HUFF_STAMINA = 0.2F;
	static final int HUFF_EVERY_STRIDES = 2;
	static float HUFF_VOLUME_MIN = 0.12F;
	static float HUFF_VOLUME_MAX = 0.3F;
	/** The breath that goes with the head toss when stamina runs out. */
	static float EXHAUSTED_BREATH_VOLUME = 0.45F;
	/** Where in the stride the huff lands (0..1 of the leg cycle): on the down of the stride. */
	static final float HUFF_STRIDE_PHASE = 0.67F;
	/** Running out of stamina, the horse tosses its head (Fresh Animations' shake motion), over this many ticks. */
	public static final int HEAD_SHAKE_TICKS = 32;
	/** Head toss: side-to-side yaw and neck roll amplitudes (radians), cycling once per ~7.5 ticks. */
	public static final float HEAD_SHAKE_YAW = 0.33F;
	public static final float HEAD_SHAKE_ROLL = 0.125F;
	public static final float HEAD_SHAKE_RATE = 1.0F / 1.2F;

	// ---- Jumping: instant on press, power from momentum ----

	/** Jump strength at a standstill and at a canter or faster (vanilla full charge = 1.0). */
	static float JUMP_POWER_STILL = 0.4F;
	static float JUMP_POWER_RUNNING = 0.8F;
	static final float JUMP_POWER_EXHAUSTED = 0.75F;
	/** Extra forward push on takeoff at full power, blocks/tick (vanilla 0.4). */
	public static final float JUMP_FORWARD_BOOST = 0.1F;
	public static float JUMP_STAMINA_COST = 0.04F;
	/** A jump pressed this many ticks before landing still fires on touchdown. */
	static final int JUMP_BUFFER_TICKS = 8;
	/** Ticks on the ground after a jump before the next one. */
	static final int JUMP_RECOVERY_TICKS = 4;
	/** Air acceleration relative to the ridden speed (vanilla 0.1). 0.2 holds flat-ground speed through a jump. */
	public static final float AIR_CONTROL = 0.2F;

	// ---- The horse has a say: it picks its way (see Awareness) ----

	/**
	 * The horse looks ahead and won't run into trouble: it steers around obstacles it can pass, slows to a walk for
	 * walls, and stops short of drops that would hurt it (beyond its safe fall distance, 6 blocks for vanilla horses)
	 * and of hazards (lava, fire, cactus, berry bushes, powder snow...). The rider's intent always wins otherwise.
	 */
	public static boolean AVOID_DANGER = true;
	/** How far ahead the horse looks: this many ticks of travel, within limits (blocks). */
	static final float LOOK_AHEAD_TICKS = 20.0F;
	static final float LOOK_AHEAD_MIN = 2.0F;
	static final float LOOK_AHEAD_MAX = 12.0F;
	/**
	 * Steering round obstacles starts above a walk and is full from a trot, so at a walk the rider has precise control.
	 * The horse tries detours this many degrees apart, up to the max, and takes the first (smallest) that passes the
	 * obstacle by DETOUR_CLEARANCE blocks; a wall it can't get round within that angle it slows for instead of veering.
	 */
	static final float AVOID_ANGLE_STEP = 10.0F;
	static final float AVOID_MAX_ANGLE = 40.0F;
	static final float DETOUR_CLEARANCE = 2.0F;
	/**
	 * A detour line must be clear this much wider than the body on each side, so the horse turns early and wide enough
	 * despite the time it takes to shift its weight and come round.
	 */
	static final float DETOUR_MARGIN = 0.6F;
	/** While detouring, the horse only brakes for its current heading when the obstacle is this close (blocks). */
	static final float DETOUR_EMERGENCY = 2.5F;
	/** The detour eases in and out by this many degrees per tick; detours are re-planned every few ticks. */
	static final float AVOID_RATE = 5.0F;
	static final int AVOID_REPLAN_TICKS = 2;
	/**
	 * Slowing for what's ahead: braking is planned at this share of the full brake (so it starts early and smooth),
	 * allows for the ~1.5 ticks the body lags its speed, and stops this many blocks short.
	 */
	static final float BRAKE_PLAN = 0.8F;
	static final float BRAKE_LAG_TICKS = 1.5F;
	static final float STOP_MARGIN = 0.2F;
	/** Gaps up to this wide (with safe ground at about the same height beyond) stay jumpable: no braking, no refusal. */
	static final float GAP_REACH = 4.0F;
	/** Refusing (a drop or hazard from a trot or faster, or a jump off a cliff): a snort and a head toss, at most this often. */
	static final int REFUSAL_COOLDOWN_TICKS = 60;
	static float REFUSAL_VOLUME = 0.5F;

	/**
	 * Ledges up to 2 blocks high: riding toward one at a walk or trot, the horse halts at it, gathers itself for a moment
	 * (haunches down, nose up) and jumps up onto it in an arc that clears the lip by LEDGE_CLEARANCE. Not at a canter or
	 * gallop (it slows for the wall first), and never over fences, walls or gates, so pens still hold horses.
	 */
	public static boolean LEDGE_CLIMB = true;
	static final float LEDGE_HEIGHT = 2.0F;
	/** It takes off when the ledge's face is this close to its chest, blocks. */
	static final float LEDGE_REACH = 0.9F;
	public static final int LEDGE_GATHER_TICKS = 5;
	static final float LEDGE_CLEARANCE = 0.3F;
	/** Gathering: the body sinks this far (blocks) and the nose lifts this much (degrees). */
	static final float LEDGE_CROUCH = 0.12F;
	static final float LEDGE_CROUCH_PITCH = 6.0F;
	static final float LEDGE_STAMINA_COST = 0.04F;
	static float CLIMB_SOUND_VOLUME = 0.35F;

	// ---- Visuals (client) ----

	/** Visual bank into turns: degrees per (deg/tick of turn x (blocks/tick)^2 of speed), capped. */
	static float LEAN_GAIN = 16.0F;
	static float LEAN_MAX = 15.0F;
	static final float LEAN_SMOOTHING = 0.4F;
	/** Ground is probed this far ahead of and behind the horse's centre, roughly where the hooves are. */
	static final float HOOF_REACH = 0.65F;
	static final float PITCH_MAX = 15.0F;
	/**
	 * Climbing, the neck reaches forward/down by this share of the body's nose-up pitch (and the reverse downhill),
	 * keeping the head out of the rider's way, as real horses do.
	 */
	public static final float NECK_COUNTER_PITCH = 0.6F;
	static final float PITCH_SMOOTHING = 0.3F;
	/** In the air the body follows its flight path, scaled down. */
	static final float AIR_PITCH_SCALE = 0.4F;
	static final float AIR_PITCH_MAX = 12.0F;
	/** How much the body rises (or dips) early as the front hooves reach higher (or lower) ground. */
	static final float STEP_ANTICIPATION = 0.5F;
	/** World-space follow rate for the body height on the ground: turns block step-ups into a climb. */
	static final float STEP_SMOOTHING = 0.35F;
	/** Airborne, any leftover height offset fades this fast relative to the physics body (no lag in flight). */
	static final float AIR_OFFSET_DECAY = 0.5F;
	/** ...but by no more than this per tick, so a lag built up climbing out of water eases out instead of snapping. */
	static final float AIR_OFFSET_MAX_STEP = 0.05F;
	/** Touching down, the body sinks this many blocks per block/tick of fall speed (max LANDING_DIP_MAX), then recovers. */
	static final float LANDING_DIP = 0.18F;
	static final float LANDING_DIP_MAX = 0.2F;

	/**
	 * Leg animation speed for a ridden horse = fraction of vanilla top speed. Fresh Animations switches to its trot at
	 * 0.4 and its gallop at 0.8, so walk ~0.35, trot ~0.6, canter ~0.85 and gallop 1.0 each get the matching gait.
	 */
	public static final float ANIMATION_SPEED_FACTOR = 1.0F;

	/** Hoof sounds switch to the gallop clip at this fraction of vanilla top speed. */
	public static final float GALLOP_SOUND_SPEED = 0.72F;
	/** Flat-ground top speed per tick is ~2.2x the speed attribute (friction 0.6 * drag 0.91). */
	public static final float TERMINAL_VELOCITY_FACTOR = 2.2F;

	// ---- Rider (client) ----

	/** The rider resists the horse's bank to stay upright: fraction of the bank the torso follows. */
	public static float RIDER_BANK_FOLLOW = 0.3F;
	/** Uphill the rider leans forward by this fraction of the horse's pitch; downhill they stay vertical. */
	public static final float RIDER_UPHILL_LEAN = 0.25F;
	/** The hips ride the saddle exactly; the torso takes this share of the saddle's rocking. */
	public static final float RIDER_SADDLE_PITCH_FOLLOW = 0.5F;
	/** Seat height above the rider's origin; the rider pivots here. */
	public static final float RIDER_SEAT_HEIGHT = 0.6F;
	/** The rider sinks this far into the saddle (vanilla seats them for a legs-out pose), blocks. */
	public static final float RIDER_SEAT_DROP = 0.12F;
	/** Forward fold over the neck, degrees: a little at any gait, crouched at a gallop (RDR2 riders: ~5-10 / 20-30). */
	public static final float FORWARD_LEAN_STILL = 4.0F;
	public static final float FORWARD_LEAN_MOVING = 6.0F;
	public static final float FORWARD_LEAN_GALLOP = 18.0F;
	/** The torso turns toward where the rider looks by this share of the head's turn, up to the max (degrees). */
	public static final float TORSO_TWIST = 0.4F;
	public static final float TORSO_TWIST_MAX = 40.0F;
	/**
	 * Seated legs: thighs well forward and wrapped round the barrel, feet ending where stirrups hang (just behind the
	 * horse's shoulder), toes a little out. Radians. A Minecraft leg has no knee, so the forward angle is what reads as
	 * sitting; much less and the rider looks like they are standing.
	 */
	public static final float STIRRUP_LEG_FORWARD = 0.75F;
	public static final float STIRRUP_LEG_SPLAY = 0.45F;
	public static final float STIRRUP_TOE_OUT = 0.12F;
	/** Hands on the reins in front of the saddle: forward reach and inward angle, radians. */
	public static final float REINS_ARM_FORWARD = 0.55F;
	public static final float REINS_ARM_INWARD = 0.28F;
	/**
	 * Loose hands lag the body: they drop as the horse lands and lift as it rises (radians per block of saddle lift,
	 * applied against the lift).
	 */
	public static final float HAND_BOB = 2.0F;
	/**
	 * At a canter or gallop the rider's pelvis slides forward and back with the stride (the "hump" of following the
	 * horse's back) while the shoulders stay put: torso swing from the shoulders, radians per block of saddle lift.
	 */
	public static final float PELVIS_SLIDE = 1.7F;
	/**
	 * The rider's body answers the horse's surges: leaning back as it speeds up and forward as it slows, on a spring
	 * so it settles naturally. Degrees per block/tick^2 of acceleration, spring stiffness and damping per tick, limit.
	 */
	public static final float INERTIA_GAIN = 250.0F;
	public static final float INERTIA_STIFFNESS = 0.25F;
	public static final float INERTIA_DAMPING = 0.45F;
	public static final float INERTIA_MAX = 12.0F;
	/**
	 * Fresh Animations' stirrups are swung forward to meet the seated leg (its leathers hang from the saddle's front
	 * bar), and held there against the body's motion so the feet stay in them. Radians.
	 */
	public static final float STIRRUP_SWING = 0.62F;

	/**
	 * Saddle motion used when no animation pack moves the horse's body (vanilla model): applied to the horse and the
	 * rider alike so they stay in step. Lift in blocks, pitch in degrees. Walk and trot twice per stride, canter and
	 * gallop once.
	 */
	public static final float SADDLE_LIFT_WALK = 0.03F;
	public static final float SADDLE_LIFT_TROT = 0.06F;
	public static final float SADDLE_LIFT_CANTER = 0.07F;
	public static final float SADDLE_LIFT_GALLOP = 0.05F;
	public static final float SADDLE_PITCH_WALK = 0.8F;
	public static final float SADDLE_PITCH_TROT = 1.0F;
	public static final float SADDLE_PITCH_CANTER = 3.0F;
	public static final float SADDLE_PITCH_GALLOP = 2.5F;
	/** Leg-animation speeds where the motion blends into trot, into canter, and into gallop. */
	public static final float TROT_ANIMATION_SPEED = 0.4F;
	public static final float RUN_ANIMATION_SPEED = 0.8F;
	public static final float GALLOP_ANIMATION_SPEED = 0.95F;

	// ---- Camera (client) ----

	/** Switch to third person on mounting and back to first person on dismounting. */
	public static boolean AUTO_THIRD_PERSON = true;
	public static float CAMERA_DISTANCE_STILL = 4.0F;
	public static float CAMERA_DISTANCE_GALLOP = 5.5F;
	/** Low value = the camera falls behind as the horse speeds up and catches up as it slows. */
	public static final float CAMERA_DISTANCE_SMOOTHING = 0.08F;
	/** Pivot height above the rider's eyes. */
	public static final float CAMERA_HEIGHT = 0.5F;
	/** Vertical follow rate; filters out the jolt of stepping up blocks and floats a little through jumps. */
	public static final float CAMERA_HEIGHT_SMOOTHING = 0.6F;
	/** FOV widening at a full gallop (scaled by the FOV Effects option). */
	public static float FOV_GALLOP_BOOST = 0.05F;
	/** After this many ticks without vertical mouse movement while moving, the view eases back to a riding pitch. */
	public static final int PITCH_RECENTER_DELAY = 40;
	public static final float PITCH_DEFAULT = 10.0F;
	public static final float PITCH_RECENTER_RATE = 0.04F;
	/**
	 * First-person ride motion (off when the vanilla View Bobbing option is off): share of the saddle lift the eyes
	 * get, and share of the saddle rocking that nods the view. Kept gentle; low-passed so there are no sharp edges.
	 */
	public static float FP_BOUNCE_SCALE = 0.35F;
	public static float FP_NOD_SCALE = 0.15F;
	public static final float FP_SMOOTHING_SECONDS = 0.08F;
	/** First-person hands bob with the saddle, more at speed: share of the saddle lift. */
	public static float FP_HAND_BOB = 0.6F;
	/** Share of the saddle lift the third-person camera takes. */
	public static final float THIRD_PERSON_BOUNCE_SCALE = 0.12F;

	// ---- World ----

	/** A player-ridden horse (and rider) pushes through leaves instead of being stopped. */
	public static boolean RIDE_THROUGH_LEAVES = true;
	/** A horse moving faster than a walk tramples small creatures in its path. */
	public static boolean TRAMPLE = true;
	/** Trampling starts above this share of vanilla top speed (a walk is 0.35, a trot 0.6). */
	static final float TRAMPLE_MIN_SPEED = 0.5F;
	/** Damage at a full gallop, half-hearts; scales down to nothing at TRAMPLE_MIN_SPEED. */
	static float TRAMPLE_DAMAGE_MAX = 8.0F;
	/** Knock aside strength at a full gallop. */
	static final float TRAMPLE_KNOCKBACK = 0.6F;
	/** "Small" means no wider or taller than this, blocks (chickens, rabbits, cats, foxes, frogs, baby animals...). */
	static final float TRAMPLE_MAX_WIDTH = 0.8F;
	static final float TRAMPLE_MAX_HEIGHT = 1.0F;

	private RideTuning() {
	}

	/** 0 at a standstill, 1 at full gallop. */
	public static float gallopFraction(final float speed) {
		return Math.min(Math.abs(speed) * (1.0F / GAIT_SPEED[GALLOP]), 1.0F);
	}
}
