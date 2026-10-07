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
	/** Out of its depth the horse loses its way quickly (speed multiple per tick), and doesn't crash into banks. */
	static final float SWIM_DECEL = 0.12F;
	/** Buoyancy: how firmly the horse settles to its floating depth (vertical blocks/tick per block off, and limits). */
	static final float SWIM_BUOYANCY = 0.3F;
	static final float SWIM_SINK_MAX = 0.06F;
	static final float SWIM_RISE_MAX = 0.12F;
	/**
	 * Climbing out: swimming at a bank (or pressing jump at one) whose top is no more than BANK_MAX_ABOVE_WATER above the
	 * top of the water's block layer, within BANK_REACH of the chest, with room on top, the horse heaves itself out: it
	 * rises to the top in one smooth motion (eased in and out, over BANK_HEAVE_TICKS plus BANK_HEAVE_TICKS_PER_BLOCK for
	 * each block of rise) while pressing forward at BANK_FORWARD blocks/tick, and walks on from the top. Higher banks
	 * can't be climbed; the rider has to find a lower one.
	 */
	static final double BANK_MAX_ABOVE_WATER = 1.125;
	static final float BANK_REACH = 0.6F;
	static final float BANK_HEAVE_TICKS = 6.0F;
	static final float BANK_HEAVE_TICKS_PER_BLOCK = 6.0F;
	/** The heave lifts the hooves this far above the bank's top, so the body moves onto it a few ticks before it settles. */
	static final double BANK_CLEARANCE = 0.15;
	static final float BANK_FORWARD = 0.12F;
	/** Ticks the heave keeps pressing forward over the top for the hooves to find the bank, before giving up. */
	static final int BANK_OVER_TICKS = 8;
	/** Heaving out, the body tilts nose up by up to this much mid-heave (forehand on the bank first), eased this fast. */
	static final float HEAVE_PITCH = 14.0F;
	static final float HEAVE_PITCH_EASE = 0.3F;
	/** Swimming is tiring: a full stamina bar lasts this long in deep water. */
	static final float STAMINA_DRAIN_SWIM = 1.0F / (40 * 20);
	/** Pushing through leaves holds the horse to this share of its gait speed. */
	static float LEAVES_SPEED_FACTOR = 0.75F;
	/** Volume of the rustle while pushing through leaves (the rabbit hop sound). */
	static float LEAVES_RUSTLE_VOLUME = 0.6F;

	/**
	 * Hitting a wall above this speed sheds momentum and drops the horse to a trot, when the hit takes more than
	 * CRASH_BLOCKED of the tick's travel (head-on, not a scrape along a trunk or the corner of a block).
	 */
	static final float CRASH_SPEED = 0.45F;
	static final float CRASH_KEEP = 0.5F;
	public static final float CRASH_BLOCKED = 0.65F;
	/**
	 * Ridden, the horse steps up anything this high (vanilla 1.0), so a full block is a step from a path, farmland,
	 * mud or soul sand, and onto a block with snow on it. In the air (off a drop, or a jump a little short) it gets a
	 * hoof on anything within a step of its hooves and carries on, instead of stopping dead against it.
	 */
	public static final float RIDDEN_STEP_HEIGHT = 1.125F;
	/**
	 * Running nearly straight (more than CORNER_SLIP_BLOCKED of the travel stopped) into the corner of something that
	 * only catches the edge of its body, at more than CORNER_SLIP_MIN_SPEED blocks/tick, the horse slips sideways past
	 * it, by up to CORNER_SLIP blocks in CORNER_SLIP_STEP steps, and keeps its pace.
	 */
	static final float CORNER_SLIP_BLOCKED = 0.9F;
	static final float CORNER_SLIP_MIN_SPEED = 0.05F;
	static final double CORNER_SLIP = 0.3;
	static final double CORNER_SLIP_STEP = 0.1;

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
	/** A or D alone (no W or S) ride the horse on at its gait, this many degrees left or right of the view: across it. */
	static float ACROSS_OFFSET = 90.0F;
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

	// ---- Hard cuts: the further the rider looks off, the harder the horse cuts ----

	/**
	 * The further off where the horse is going the rider looks, the harder it cuts round: from CUT_START degrees off
	 * (nothing) to CUT_FULL (all of it), eased, it sits back on its haunches, takes up to CUT_GRIP times its usual grip and
	 * CUT_RATE_SCALE times its usual top turn rate, commits its weight up to CUT_WEIGHT_SHIFT a tick, and slows (braking
	 * up to CUT_DECEL) to the speed at which that grip brings it round in about CUT_TURN_TICKS. Once committed it eases
	 * out of the cut by CUT_RELEASE a tick as it comes round. 90 degrees at a gallop: ~0.85s at ~65% pace (a plain turn
	 * took ~1.9s); 150: ~1s at ~45%; a look of 40 degrees or less (riding at an angle with A/D included) is ordinary
	 * steering, so the horse still shifts its weight before it turns.
	 */
	public static boolean HARD_CUT = true;
	static final float CUT_START = 40.0F;
	static final float CUT_FULL = 100.0F;
	static final float CUT_TURN_TICKS = 10.0F;
	static final float CUT_RELEASE = 0.05F;
	static final float CUT_GRIP = 2.2F;
	static final float CUT_RATE_SCALE = 2.0F;
	static final float CUT_TURN_GAIN = 0.6F;
	static final float CUT_WEIGHT_SHIFT = 0.35F;
	static final float CUT_TURN_ACCEL = 0.5F;
	static final float CUT_DECEL = 0.1F;
	/** Sitting back for a cut: the haunches drop this far (blocks) and the nose lifts this much (degrees), eased in and out. */
	static final float CUT_SQUAT = 0.1F;
	static final float CUT_SQUAT_PITCH = 5.0F;
	static final float CUT_SQUAT_EASE = 0.35F;
	/** Cutting hard (past halfway) from a trot or faster scuffs the ground: the block's step sound at this volume and a spray of it. */
	static float CUT_SOUND_VOLUME = 0.6F;

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
	static float JUMP_POWER_STILL = 0.55F;
	static float JUMP_POWER_RUNNING = 0.8F;
	/**
	 * Every jump takes off at least this fast (blocks/tick, about 1.25 blocks high, like a player's jump), so a weak
	 * horse or a standing jump still clears a block.
	 */
	public static float JUMP_MIN_VELOCITY = 0.42F;
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

	/**
	 * While ridden, the horse's collision box is this share of vanilla's (1.4 -> 0.9 blocks wide, about its body's real
	 * width), so it threads 1-block gaps between trees. Unridden horses keep vanilla's box.
	 */
	public static final float RIDDEN_WIDTH_SCALE = 0.9F / 1.3964844F;

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
	 * Going round obstacles: when the rider asks for a trot or more (never at a walk, so the rider has precise control)
	 * and there is a way round in reach, the horse takes the straightest one: it looks along the obstacle, up to
	 * DETOUR_REACH blocks either side, for the least it has to move over (to within DETOUR_RESOLUTION) for the rider's
	 * line to be clear past it (preferring a lane clear through anything else in a row behind it), and steers a straight
	 * line for the point beside it DETOUR_PAST beyond its near face (the rider's line clear DETOUR_CLEARANCE further), no
	 * more than AVOID_MAX_ANGLE off the rider's line; once the
	 * rider's line is clear it heads where the rider looks again. A
	 * wall with no way round in reach it slows for instead of veering along it. A 2-block ledge the rider rides straight
	 * at, that it can jump, it doesn't go round: it jumps it.
	 */
	static final float DETOUR_REACH = 12.0F;
	static final float AVOID_MAX_ANGLE = 85.0F;
	static final float DETOUR_PAST = 1.5F;
	static final float DETOUR_CLEARANCE = 1.0F;
	static final float DETOUR_STEP = 0.5F;
	static final float DETOUR_RESOLUTION = 0.125F;
	/**
	 * Things in a row: the way round one is better a lane clear on through what lies behind it, at least DETOUR_LANE
	 * blocks past it (or to the end of what the horse can see), taken if no more than DETOUR_LANE_EXTRA further over than
	 * the nearest way just past it. So going round one thing doesn't take the horse into the next.
	 */
	static final float DETOUR_LANE = 5.0F;
	static final float DETOUR_LANE_EXTRA = 2.0F;
	/**
	 * A way round must be clear this much wider than the body on each side, so the horse doesn't clip the obstacle
	 * despite the time it takes to shift its weight and come round.
	 */
	static final float DETOUR_MARGIN = 0.2F;
	/**
	 * Swinging round at speed: the horse needs ~TURN_LAG_TICKS to shift its weight, then turns at about TURN_EFFICIENCY
	 * of its grip-limited rate; it slows as much as that takes to come round before what is ahead.
	 */
	static final float TURN_LAG_TICKS = 4.0F;
	/** Turned off the rider's line going round something, it keeps a pace that can come back round within this, blocks. */
	static final float DETOUR_RETURN_ROOM = 6.0F;
	static final float TURN_EFFICIENCY = 0.7F;
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
	/**
	 * Falls: the horse takes any fall that doesn't hurt it (vanilla horses: up to ~8 blocks), and beyond that one that
	 * costs it up to FALL_HURT_ALLOWANCE health points (2 hearts) and its rider up to RIDER_FALL_HURT_ALLOWANCE (3). The
	 * allowances are full above CONFIDENT_HEALTH and shrink to nothing at CAUTIOUS_HEALTH (fractions of max health), so
	 * a hurt horse or rider is careful. At full health that is drops up to ~10 blocks.
	 */
	static final float FALL_HURT_ALLOWANCE = 4.0F;
	/** The rider takes the fall too (at full damage from 3 blocks), so any fall that hurts the horse costs them more. */
	static final float RIDER_FALL_HURT_ALLOWANCE = 6.0F;
	static final float CONFIDENT_HEALTH = 0.8F;
	static final float CAUTIOUS_HEALTH = 0.4F;
	/**
	 * Down slopes, momentum carries the horse past each step before it lands; it keeps to a pace that lands within the
	 * fall it will take, less this margin for error (a plain step it can take is always fine).
	 */
	static final double FALL_MARGIN = 1.0;
	/** Gaps up to this wide (with safe ground at about the same height beyond) stay jumpable: no braking, no refusal. */
	static final float GAP_REACH = 4.0F;
	/** Refusing (a drop or hazard from a trot or faster, or a jump off a cliff): a snort and a head toss, at most this often. */
	static final int REFUSAL_COOLDOWN_TICKS = 60;
	static float REFUSAL_VOLUME = 0.5F;

	/**
	 * Ledges up to 2 blocks high: riding at one at a walk or trot, the horse jumps up it without stopping. It spots the
	 * ledge within LEDGE_REACH of its chest, sinks onto its haunches over the last LEDGE_CROUCH_TICKS, and bounds up it:
	 * at least LEDGE_BOUND blocks/tick forward through the air (no more than LEDGE_MAX_FORWARD), taking off one and a
	 * half to two blocks out, where that arc brings its chest to the face near the top of the arc (LEDGE_CROSS_HEIGHT of
	 * the LEDGE_CLEARANCE above the lip), and lands a stride onto the top, then walks on. Not at a canter or gallop (it slows for the wall first); never onto leaves or a
	 * lone log (it needs solid ground under LEDGE_SUPPORT of its body), and never over fences, walls or gates.
	 */
	public static boolean LEDGE_CLIMB = true;
	static final float LEDGE_HEIGHT = 2.0F;
	/** A ledge may have a thin layer on top (snow, a carpet) this thick and still be jumped onto. */
	static final float LEDGE_TOP_LAYER = 0.26F;
	/**
	 * From a trot up, a ledge the rider's line meets (and the horse is heading within LEDGE_LINE_ANGLE of that line) that
	 * it can jump, it doesn't go round: it slows to a trot by the time it is in reach and jumps it.
	 */
	static final float LEDGE_LINE_ANGLE = 30.0F;
	static final float LEDGE_REACH = 3.0F;
	static final float LEDGE_CLEARANCE = 0.3F;
	static final double LEDGE_SUPPORT = 0.6;
	/** Forward speed in the jump at least this, blocks/tick, and the run-up gives up after LEDGE_APPROACH_TICKS. */
	static final float LEDGE_MIN_FORWARD = 0.1F;
	static final float LEDGE_BOUND = 0.26F;
	/** It takes off where its chest gets to the face once the body is this share of the clearance above the lip. */
	static final float LEDGE_CROSS_HEIGHT = 0.75F;
	static final float LEDGE_MAX_FORWARD = 0.32F;
	static final int LEDGE_APPROACH_TICKS = 40;
	static final float LEDGE_CROUCH_TICKS = 3.0F;
	/** Crouching: the body sinks this far (blocks) and the nose lifts this much (degrees). */
	static final float LEDGE_CROUCH = 0.12F;
	static final float LEDGE_CROUCH_PITCH = 6.0F;
	static final float LEDGE_STAMINA_COST = 0.04F;
	static float CLIMB_SOUND_VOLUME = 0.35F;

	// ---- Visuals (client) ----

	/** Visual bank into turns: degrees per (deg/tick of turn x (blocks/tick)^2 of speed), capped. */
	static float LEAN_GAIN = 16.0F;
	static float LEAN_MAX = 15.0F;
	static final float LEAN_SMOOTHING = 0.4F;
	/** Where the front and hind hooves stand, blocks ahead of and behind the horse's centre. */
	public static final float FORE_HOOVES = 0.65F;
	public static final float HIND_HOOVES = 0.5F;
	/**
	 * Steps and slopes go in two beats, like a real horse: the forehand goes up (or down) first as the front hooves
	 * reach the step, then the hindquarters follow as the hind hooves get there. The front and the back of the body
	 * each ease toward the ground under their own hooves (averaged over a STEP_FOOTPRINT-long stretch) in two stages,
	 * so every change eases in and out with no overshoot: STEP_EASE of the way per stage per tick standing, plus
	 * STEP_EASE_PER_SPEED per block/tick of speed, up to STEP_EASE_MAX. The ground is read as far ahead as the easing
	 * lags (up to STEP_LEAD_MAX blocks), so each end moves as its hooves reach the step and slopes are followed without
	 * falling behind.
	 */
	static final float STEP_EASE = 0.22F;
	static final float STEP_EASE_PER_SPEED = 0.5F;
	static final float STEP_EASE_MAX = 0.45F;
	static final double STEP_FOOTPRINT = 0.5;
	static final double STEP_LEAD_MAX = 1.5;
	/** The body follows the ground down a step of up to this many blocks; past a bigger drop the hooves stay level. */
	static final double STEP_REACH = 1.25;
	/**
	 * Step after step (up or down a mountainside) the drawn body falls behind the physical one; past STEP_LAG blocks it
	 * catches up by up to STEP_CATCH_UP blocks a tick on top of its easing, so it never snaps. Only a body more than
	 * STEP_SNAP off (a teleport) is put straight where it is.
	 */
	static final double STEP_LAG = 0.8;
	static final double STEP_CATCH_UP = 0.12;
	static final float STEP_SNAP = 3.0F;
	/**
	 * A real horse doesn't lean far on a step: the body tilts toward PITCH_MAX degrees (about three quarters of it once
	 * the front is PITCH_RISE blocks above the back, levelling off beyond) and the hindquarters keep the weight, the body
	 * sitting BODY_RISE_UP of the way from the hind support's height toward the front's going up (BODY_RISE_DOWN going
	 * down). The front legs fold up onto a step to make up the rest, and reach down for one.
	 */
	static final float PITCH_MAX = 10.0F;
	static final float PITCH_RISE = 0.7F;
	/** The tilt then eases this much of the way per tick, so quick bumps at speed rock the body gently. */
	static final float TILT_EASE = 0.4F;
	static final float BODY_RISE_UP = 0.3F;
	static final float BODY_RISE_DOWN = 0.2F;
	/**
	 * Leg poses on a step, radians (and model pixels): the front legs fold up and forward onto a step (FORE_TUCK) or
	 * reach forward and down for one (FORE_REACH); the hind legs drive back as the hindquarters push up (HIND_DRIVE) or
	 * gather under the body going down (HIND_GATHER). Full pose when the front of the body is LEG_POSE_REACH blocks off
	 * the ground its hooves are going to, or the hindquarters rise at HIND_DRIVE_SPEED blocks/tick.
	 */
	public static final float FORE_TUCK_ANGLE = 0.85F;
	public static final float FORE_TUCK_LIFT = 3.0F;
	public static final float FORE_REACH_ANGLE = 0.35F;
	public static final float HIND_DRIVE_ANGLE = 0.5F;
	public static final float HIND_GATHER_ANGLE = 0.3F;
	static final float LEG_POSE_REACH = 0.5F;
	static final float HIND_DRIVE_SPEED = 0.12F;
	/**
	 * Climbing, the neck reaches forward/down by this share of the body's nose-up pitch (and the reverse downhill),
	 * keeping the head out of the rider's way, as real horses do.
	 */
	public static final float NECK_COUNTER_PITCH = 0.6F;
	static final float PITCH_SMOOTHING = 0.3F;
	/** Swimming out of the water, the body follows its path, scaled down. */
	static final float AIR_PITCH_SCALE = 0.4F;
	static final float AIR_PITCH_MAX = 12.0F;
	/** ...eased this much of the way per tick, so bobbing and climbing out of the water tilt the body gently. */
	static final float WATER_PITCH_EASE = 0.12F;
	/**
	 * Jumping (and any time in the air), the body tilts with its flight like a real horse's: nose up on takeoff as the
	 * front legs lift and the hind legs push (pivoting on the hind hooves), level over the top, nose down to land front
	 * feet first (pivoting on the front hooves). Degrees per degree of flight path, limits up and down, and response.
	 */
	static final float JUMP_PITCH_SCALE = 0.55F;
	static final float JUMP_PITCH_UP = 25.0F;
	static final float JUMP_PITCH_DOWN = 15.0F;
	static final float JUMP_PITCH_SMOOTHING = 0.5F;
	/** World-space follow rate for the body height afloat: turns climbing out of the water into a smooth rise. */
	static final float WATER_HEIGHT_SMOOTHING = 0.35F;
	/** Airborne, any leftover height offset fades this fast relative to the physics body (no lag in flight). */
	static final float AIR_OFFSET_DECAY = 0.5F;
	/** ...but by no more than this per tick, so a lag built up climbing out of water eases out instead of snapping. */
	static final float AIR_OFFSET_MAX_STEP = 0.05F;
	/**
	 * Legs in the air (a jump, or a fall bigger than a step): the run cycle winds down (AIR_STRIDE_STOP of the way a tick)
	 * and the legs ease into the shape of a jump (AIR_LEGS_IN a tick, eased in and out) and back into the stride on landing
	 * (AIR_LEGS_OUT a tick). Through the air they move in one floaty sweep: rising, the
	 * front legs fold up under the chest (FORE_AIR_TUCK radians forward, drawn up FORE_AIR_LIFT model pixels) and the
	 * hind legs push off behind (HIND_AIR_PUSH); coming down, the front legs reach forward and down for the ground
	 * (FORE_AIR_REACH) and the hind legs gather under the hindquarters (HIND_AIR_TUCK). Each pair is staggered
	 * (FORE_AIR_STAGGER, HIND_AIR_STAGGER: one leg ahead of the other) so both legs read, and kept under the chest and
	 * the hindquarters, nudged forward FORE_AIR_FORWARD and HIND_AIR_FORWARD pixels against the swing. A Minecraft leg is
	 * one block hung from its top, so every leg is also drawn up by AIR_LEG_HALF_DEPTH x sin of its swing: its top stays
	 * inside the body instead of a corner showing a gap. Rising or falling is the climb rate over AIR_LEG_RISE
	 * blocks/tick, eased in two stages by AIR_LEG_PHASE_EASE so the legs move smoothly from one shape to the next over the
	 * whole flight rather than snapping over at the top.
	 */
	public static final float AIR_STRIDE_STOP = 0.3F;
	static final float AIR_LEGS_IN = 0.2F;
	static final float AIR_LEGS_OUT = 0.25F;
	public static final float FORE_AIR_TUCK = 0.75F;
	public static final float FORE_AIR_LIFT = 2.5F;
	public static final float FORE_AIR_REACH = 0.4F;
	public static final float FORE_AIR_STAGGER = 0.3F;
	public static final float FORE_AIR_FORWARD = 1.0F;
	public static final float HIND_AIR_PUSH = 0.3F;
	public static final float HIND_AIR_TUCK = 0.25F;
	public static final float HIND_AIR_STAGGER = 0.25F;
	public static final float HIND_AIR_FORWARD = 1.5F;
	public static final float AIR_LEG_HALF_DEPTH = 2.0F;
	static final float AIR_LEG_RISE = 0.45F;
	static final float AIR_LEG_PHASE_EASE = 0.35F;
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
	/**
	 * Jumping, the rider folds forward over the neck (the jumping position): RIDER_JUMP_FOLD degrees in the air, and
	 * following RIDER_JUMP_FOLLOW of the horse's nose-up tilt as it takes off; coming down they sit up again.
	 */
	public static final float RIDER_JUMP_FOLD = 12.0F;
	public static final float RIDER_JUMP_FOLLOW = 0.6F;
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

	/**
	 * Pushing through leaves at the rider's chest or face (looked for LEAF_LOOK_NEAR and LEAF_LOOK_FAR ahead of the rider,
	 * at the eyes and LEAF_CHEST_BELOW_EYE below them, moving faster than LEAF_BRUSH_MIN_PACE of vanilla top speed): the
	 * branches push the rider back toward LEAF_PUSH_MAX degrees at full pace (on a spring, with a LEAF_PUSH_KICK shove
	 * as the face meets each new clump, never more than LEAF_PUSH_LIMIT), and the rider puts a hand up in front of their
	 * face (all the way for leaves at the face, LEAF_SHIELD_CHEST of the way for leaves at the chest), raised by up to
	 * LEAF_SHIELD_UP a tick and lowered by LEAF_SHIELD_DOWN.
	 */
	static final float LEAF_LOOK_NEAR = 0.3F;
	static final float LEAF_LOOK_FAR = 0.9F;
	static final double LEAF_CHEST_BELOW_EYE = 0.6;
	static final float LEAF_BRUSH_MIN_PACE = 0.15F;
	static final float LEAF_PUSH_MAX = 7.0F;
	static final float LEAF_PUSH_KICK = 3.0F;
	static final float LEAF_PUSH_LIMIT = 14.0F;
	static final float LEAF_PUSH_STIFFNESS = 0.25F;
	static final float LEAF_PUSH_DAMPING = 0.4F;
	static final float LEAF_SHIELD_CHEST = 0.5F;
	static final float LEAF_SHIELD_UP = 0.35F;
	static final float LEAF_SHIELD_DOWN = 0.06F;
	/**
	 * The hand against the leaves: the arm raised forward and up (radians, from hanging), swung in toward the face, and
	 * the head tucked down and turned a little away from the leaves.
	 */
	public static final float SHIELD_ARM_RAISE = 2.65F;
	public static final float SHIELD_ARM_INWARD = 0.55F;
	public static final float SHIELD_HEAD_TUCK = 0.3F;
	public static final float SHIELD_HEAD_TURN = 0.25F;

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
