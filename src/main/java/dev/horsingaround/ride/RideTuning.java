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
	static final float DECEL_COAST = 0.03F;
	/** Dropped a gait while still moving forward. */
	static final float DECEL_REIN = 0.035F;
	/** Back key held while moving: hard stop (~0.8s from a gallop). */
	static final float DECEL_BRAKE = 0.07F;
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
	static final float LEAVES_SPEED_FACTOR = 0.75F;
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
	 * hoof on anything within AIR_STEP of its hooves and carries on, instead of stopping dead against it: enough for the
	 * far edge of a ditch, but not so much that a plain jump plus a step reaches the top of a 2-block face (a jump pressed
	 * in a corner went on up a 3-block wall that way, step by step).
	 */
	public static final float RIDDEN_STEP_HEIGHT = 1.125F;
	public static final float AIR_STEP = 0.6F;
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
	static final float TURN_RATE_STILL = 6.0F;
	/**
	 * Sideways grip, m/s^2. Real horses top out around 6; a bit more keeps Minecraft terrain playable. Max turn rate is
	 * grip / speed, so the turning circle widens with speed: ~2 blocks at a walk, ~4 trot, ~8 canter, ~14 gallop.
	 */
	static final float LATERAL_GRIP = 9.0F;
	/**
	 * The horse heads where the rider looks. A/D don't move the view: they set the horse this many degrees left or
	 * right of it, so the rider can ride at an angle while looking or aiming straight at something.
	 */
	static final float STEER_OFFSET = 45.0F;
	/** A or D alone (no W or S) ride the horse on at its gait, this many degrees left or right of the view: across it. */
	static final float ACROSS_OFFSET = 90.0F;
	/** Fraction of the heading error the horse tries to correct each tick. */
	static final float TURN_GAIN = 0.3F;
	/**
	 * How fast the horse commits its weight to a turn (fraction of a full turn per tick). Turn rate grows with the
	 * square of this commitment, so the body comes around ~0.3s after the head at a walk and ~0.2s at a gallop
	 * (about half a stride).
	 */
	static final float WEIGHT_SHIFT_STILL = 0.09F;
	static final float WEIGHT_SHIFT_GALLOP = 0.18F;
	/** Max change in turn rate per tick, as a fraction of the current max turn rate. */
	static final float TURN_ACCEL = 0.25F;
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
	 * steering, so the horse still shifts its weight before it turns. Always on (not a setting); the ride test switches it
	 * off to compare against a plain turn.
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
	public static final float STAMINA_DRAIN_GALLOP = 1.0F / (28 * 20);
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
	static final float JUMP_POWER_STILL = 0.55F;
	static final float JUMP_POWER_RUNNING = 0.8F;
	/**
	 * Every jump takes off at least this fast (blocks/tick, about 1.25 blocks high, like a player's jump), so a weak
	 * horse or a standing jump still clears a block.
	 */
	public static final float JUMP_MIN_VELOCITY = 0.42F;
	static final float JUMP_POWER_EXHAUSTED = 0.75F;
	/** Extra forward push on takeoff at full power, blocks/tick (vanilla 0.4). */
	public static final float JUMP_FORWARD_BOOST = 0.1F;
	public static final float JUMP_STAMINA_COST = 0.04F;
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
	public static final boolean AVOID_DANGER = true;
	/** How far ahead the horse looks: this many ticks of travel, within limits (blocks). */
	static final float LOOK_AHEAD_TICKS = 20.0F;
	static final float LOOK_AHEAD_MIN = 2.0F;
	static final float LOOK_AHEAD_MAX = 12.0F;
	/**
	 * Ways through: when the rider asks for a trot or more (never at a walk, so the rider has precise control) and
	 * something is on the rider's line within PATH_AHEAD_FACTOR of the look-ahead (PATH_AHEAD_MIN to PATH_AHEAD_MAX
	 * blocks), the horse plans its way through the way a rider picks a line. It plays out candidate ways with its own
	 * steering and momentum at the pace it is going (the ground's grip as vanilla moves it, so it drifts wide through a
	 * turn), each turning off the line by one of PATH_ANGLES degrees for one of PATH_HOLD_TICKS and then heading back for
	 * the line (aiming PATH_BACK_TICKS of travel ahead on it, PATH_BACK_MIN to PATH_BACK_MAX blocks), its body checked
	 * every PATH_CHECK blocks with PATH_CLEAR to spare round the box (from a block on, growing to it over PATH_CLEAR_GROW
	 * blocks, so pressed against a wall it can still set off), never more than PATH_REACH off the line or
	 * PATH_LONGEST times the distance along it; the best is then fine-tuned. It takes the cheapest: PATH_COST_OFF_LINE per
	 * block off the line (counted up to PATH_OFF_LINE_CAP) per block gone, PATH_COST_TURN per block gone with its weight
	 * fully in a turn (what costs a horse its pace), PATH_COST_TIGHT each check that finds something within PATH_MARGIN of
	 * the box, and at the end PATH_COST_END per block (up to the cap) and PATH_COST_END_TURN per degree off the line. A way
	 * that meets something costs PATH_COST_MEET and PATH_COST_SHORT per block short, and counts only with room to stop
	 * before it (PATH_COMMIT to spare). Changing sides costs up to PATH_COST_SWITCH (more the further over it is); the way
	 * it is already on gets PATH_COST_KEEP off, so it doesn't dither. Only when no way at its pace is clear and what the
	 * best meets is within PATH_SLOW_ROOM stopping distances does it try the PATH_SLOWER paces (PATH_COST_PACE for all of
	 * the pace). With nothing that leaves room to stop, it brakes along the way that gets furthest along the line (if
	 * PATH_PARTIAL_GAIN further than straight on), else on the line. Past what it went round, with nothing on the line, it
	 * heads straight back onto it, until within LINE_SNAP and LINE_SNAP_ANGLE of it; a rider who turns more than
	 * LINE_FORGET_ANGLE away has picked a new line, and while the rider is turning the line moves with the horse. A 2-block ledge or a hurdle the rider rides straight at isn't gone
	 * round: it is theirs to jump.
	 */
	static final float[] PATH_ANGLES = {0.0F, 4.0F, 8.0F, 13.0F, 20.0F, 30.0F, 45.0F, 60.0F, 80.0F};
	static final int[] PATH_HOLD_TICKS = {4, 10, 20, 35};
	static final float PATH_BACK_TICKS = 12.0F;
	static final float PATH_BACK_MIN = 3.0F;
	static final float PATH_BACK_MAX = 8.0F;
	static final float PATH_CHECK = 0.7F;
	static final float PATH_REACH = 10.0F;
	static final float PATH_AHEAD_MIN = 6.0F;
	static final float PATH_AHEAD_MAX = 18.0F;
	static final float PATH_AHEAD_FACTOR = 1.5F;
	static final float PATH_LONGEST = 1.6F;
	static final int PATH_POINTS = 64;
	static final float PATH_COMMIT = 1.0F;
	static final float PATH_COST_SHORT = 1.5F;
	static final float PATH_COST_MEET = 20.0F;
	static final float PATH_SLOW_ROOM = 2.0F;
	static final float PATH_CLEAR = 0.2F;
	static final float PATH_CLEAR_GROW = 2.0F;
	static final float PATH_MARGIN = 0.35F;
	static final float PATH_COST_OFF_LINE = 0.15F;
	static final float PATH_OFF_LINE_CAP = 2.5F;
	static final float PATH_COST_TURN = 0.3F;
	static final float PATH_COST_TIGHT = 0.4F;
	static final float PATH_COST_END = 2.0F;
	static final float PATH_COST_END_TURN = 0.02F;
	static final float PATH_COST_SWITCH = 2.0F;
	static final float PATH_COST_KEEP = 3.0F;
	static final float[] PATH_SLOWER = {0.75F, 0.5F};
	static final float PATH_COST_PACE = 20.0F;
	static final float PATH_PARTIAL_GAIN = 1.5F;
	static final float LINE_SNAP = 0.15F;
	static final float LINE_SNAP_ANGLE = 1.0F;
	static final float LINE_FORGET_ANGLE = 25.0F;
	/**
	 * The line is kept only once the rider's view has turned no more than LINE_VIEW_STEP degrees a tick for
	 * LINE_SETTLE_TICKS and the horse has come round to within LINE_SETTLE_ANGLE of where it is asked to go, turning no
	 * faster than LINE_SETTLE_TURN degrees a tick; then it is kept until the view swings again.
	 */
	static final float LINE_VIEW_STEP = 4.0F;
	static final int LINE_SETTLE_TICKS = 8;
	static final float LINE_SETTLE_ANGLE = 5.0F;
	static final float LINE_SETTLE_TURN = 1.0F;
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
	/**
	 * Hurdles: pressing jump with a fence, wall, gate or anything else HURDLE_LOW to HURDLE_HEIGHT blocks tall within
	 * HURDLE_REACH of the chest (no deeper than HURDLE_DEPTH, with room to land beyond and no further down than the horse
	 * will fall), the horse jumps HURDLE_CLEARANCE over it, carrying forward far enough to clear it with HURDLE_MARGIN to
	 * spare, at any pace, standing too; held in the air at that speed until it lands. A jump pressed too far out for
	 * HURDLE_MAX_FORWARD blocks/tick to carry it over waits (it is held for a moment anyway) until it is close enough.
	 * Ridden straight at one from a trot up, the horse slows only to a trot by the time it is in reach and doesn't go
	 * round it; if the rider doesn't jump, it stops short with a snort. Nothing it would land on that hurts it.
	 */
	/** A hurdle on the rider's line is theirs to jump (not gone round) only if met within this of square. */
	static final float HURDLE_LINE_ANGLE = 45.0F;
	static final double HURDLE_LOW = 1.125;
	static final double HURDLE_HEIGHT = 1.6;
	static final float HURDLE_REACH = 2.5F;
	static final float HURDLE_DEPTH = 1.5F;
	static final double HURDLE_CLEARANCE = 0.35;
	static final float HURDLE_MARGIN = 0.3F;
	static final float HURDLE_MAX_FORWARD = 0.55F;

	static final int REFUSAL_COOLDOWN_TICKS = 60;
	static float REFUSAL_VOLUME = 0.5F;

	/**
	 * Ledges up to 2 blocks high: riding at one at a walk or trot, the horse jumps up it without stopping, and pressing
	 * jump at one (even standing at its face) asks for the same jump. It spots the ledge within LEDGE_REACH of its
	 * chest, sinks onto its haunches over at least LEDGE_CROUCH_TICKS, and heaves itself up it: the push of the hind legs
	 * builds the climb over LEDGE_THRUST_TICKS (no sudden pop), the body carries on up under LEDGE_LIFT_GRAVITY of its
	 * weight (a slower, weightier rise than a plain jump) to LEDGE_CLEARANCE above the lip, and comes down onto the top
	 * under its full weight. It moves at least LEDGE_BOUND blocks/tick forward through the air (more if it was going
	 * faster, up to LEDGE_MAX_FORWARD), taking off about one and a half blocks out, where its chest reaches the face once
	 * the body is LEDGE_CROSS_HEIGHT of the clearance above the lip, and lands a stride onto the top, then walks on. Not
	 * at a canter or gallop (it slows for the wall first); never onto leaves or a lone log (it needs solid ground under
	 * LEDGE_SUPPORT of its body), and never over fences, walls or gates.
	 */
	public static final boolean LEDGE_CLIMB = true;
	static final float LEDGE_HEIGHT = 2.0F;
	/** A ledge may have a thin layer on top (snow, a carpet) this thick and still be jumped onto. */
	static final float LEDGE_TOP_LAYER = 0.26F;
	/**
	 * From a trot up, a ledge the rider's line meets (and the horse is heading within LEDGE_LINE_ANGLE of that line) that
	 * it can jump, it doesn't go round: it slows to a trot by the time it is in reach and jumps it.
	 */
	static final float LEDGE_LINE_ANGLE = 30.0F;
	/** A face met more than LEDGE_FACE_ANGLE off square (riding along it, glancing it) isn't climbed. */
	static final float LEDGE_FACE_ANGLE = 70.0F;
	static final float LEDGE_REACH = 3.0F;
	/**
	 * Riding at a ledge, pressing jump asks for its jump from as far as LEDGE_ASKED_REACH (instead of a plain jump into its
	 * face); and landing on top, the horse settles for LEDGE_SETTLE_TICKS before it will jump again (pressing jump over
	 * and over doesn't bounce it straight off the top).
	 */
	static final float LEDGE_ASKED_REACH = 5.0F;
	static final int LEDGE_SETTLE_TICKS = 14;
	public static final float LEDGE_CLEARANCE = 0.3F;
	static final double LEDGE_SUPPORT = 0.6;
	/** Forward speed in the jump at least this, blocks/tick, and the run-up gives up after LEDGE_APPROACH_TICKS. */
	static final float LEDGE_MIN_FORWARD = 0.1F;
	static final float LEDGE_BOUND = 0.2F;
	/** It takes off where its chest gets to the face once the body is this share of the clearance above the lip. */
	static final float LEDGE_CROSS_HEIGHT = 0.5F;
	static final float LEDGE_MAX_FORWARD = 0.3F;
	static final int LEDGE_APPROACH_TICKS = 40;
	static final float LEDGE_CROUCH_TICKS = 5.0F;
	/** Crouching: the body sinks this far (blocks) and the nose lifts this much (degrees). */
	static final float LEDGE_CROUCH = 0.16F;
	static final float LEDGE_CROUCH_PITCH = 6.0F;
	/** The heave: ticks the hind legs' push takes to build the climb, and the share of gravity that slows the rise. */
	static final int LEDGE_THRUST_TICKS = 3;
	static final float LEDGE_LIFT_GRAVITY = 0.56F;
	static final float LEDGE_STAMINA_COST = 0.04F;
	static float CLIMB_SOUND_VOLUME = 0.35F;

	// ---- Visuals (client) ----

	/** Visual bank into turns: degrees per (deg/tick of turn x (blocks/tick)^2 of speed), capped. */
	static final float LEAN_GAIN = 16.0F;
	static final float LEAN_MAX = 15.0F;
	static final float LEAN_SMOOTHING = 0.4F;
	/** Where the front and hind hooves stand, blocks ahead of and behind the horse's centre. */
	public static final float FORE_HOOVES = 0.65F;
	public static final float HIND_HOOVES = 0.5F;
	/**
	 * Steps and slopes go in two beats, like a real horse: the forehand goes up (or down) first as the front hooves reach
	 * the step, then the hindquarters follow as the hind hooves get there. The body is a mass carried on its legs: the
	 * front and the back each follow the ground under their own hooves (averaged over a STEP_FOOTPRINT-long stretch) on a
	 * critically damped spring of STEP_FREQUENCY radians a tick standing, plus STEP_FREQUENCY_PER_SPEED per block/tick of
	 * speed, up to STEP_FREQUENCY_MAX (stiffer legs at speed). The ground is read as far ahead as the spring lags (up to
	 * STEP_LEAD_MAX blocks), so each end moves as its hooves reach the step. The forehand also reads the ground
	 * CHEST_AHEAD blocks ahead and rises to keep the underside of the chest (BELLY_HEIGHT above the hooves) clear of a
	 * step before it gets there.
	 */
	static final float STEP_FREQUENCY = 0.45F;
	static final float STEP_FREQUENCY_PER_SPEED = 0.5F;
	static final float STEP_FREQUENCY_MAX = 0.6F;
	static final double STEP_FOOTPRINT = 0.5;
	static final double STEP_LEAD_MAX = 4.5;
	/** At a run a step up comes in over this many ticks per block/tick of speed (a smooth pick-up of the climb), read that much further ahead. */
	static final float STEP_RAMP_PER_SPEED = 5.0F;
	static final double CHEST_AHEAD = 0.85;
	static final double BELLY_HEIGHT = 0.62;
	/**
	 * The body follows the ground down a step of up to STEP_REACH blocks; past a bigger drop the hooves stay level. At
	 * RUN_OFF_SPEED blocks/tick or faster, running off such a drop carries the body over the edge level, in one piece.
	 */
	static final double STEP_REACH = 1.25;
	static final float RUN_OFF_SPEED = 0.3F;
	/** A drawn body more than STEP_SNAP off (a teleport) is put straight where it is. */
	static final float STEP_SNAP = 3.0F;
	/**
	 * The legs give LEG_GIVE blocks: below that under the ground actually under either pair of hooves, they push that end
	 * of the body up, by no more than LEG_PUSH blocks a tick (a hard stop all at once snapped the tilt; the legs draw up
	 * into the body meanwhile). Touching down, the body keeps coming down as fast as it fell and the legs take it up: it
	 * sinks a little and comes back up. Nothing pulls the body down faster than gravity (the horse's own).
	 */
	static final double LEG_GIVE = 0.3;
	static final double LEG_PUSH = 0.15;
	/** The body sits on the lower end, the legs at the other drawing up by no more than LEG_SIT blocks to fit. */
	static final double LEG_SIT = 0.3;
	/**
	 * On steps, stairs and slopes, up or down, the hooves stay on the ground. The body tilts along the line between the
	 * front and the back (SLOPE_SHARE of it, up to SLOPE_PITCH_MAX degrees), pivoting at the leg joints (LEG_LENGTH above
	 * the hooves), and sits on the lower end. The legs stand upright (they counter LEG_UPRIGHT of the body's tilt) on top
	 * of whatever animates them (vanilla or Fresh Animations), so the stride stays.
	 */
	static final float HOOF_SPAN = FORE_HOOVES + HIND_HOOVES;
	static final float SLOPE_SHARE = 0.95F;
	static final float SLOPE_PITCH_MAX = 40.0F;
	/**
	 * The body as drawn tilts no faster than PITCH_RATE_MAX degrees a tick on its legs, its jump tilt easing out included
	 * (touching down on a slope, the forehand still falling, and the flight's tilt letting go, together snapped it).
	 */
	static final float PITCH_RATE_MAX = 9.0F;
	public static final float LEG_LENGTH = 0.625F;
	public static final float LEG_UPRIGHT = 1.0F;
	/**
	 * Legs are vanilla's one-piece legs (see {@code Legs}). Where the model is drawn, each hoof finds its own ground (see
	 * {@code GroundLegs}) exactly, with no easing, and a hoof over higher ground draws its leg up into the body by up to
	 * LEG_DRAW_MAX blocks to stand on it (a leg that would need more raises the body at once, which settles back onto its
	 * legs on a spring of FIT_TIME seconds, by up to FIT_UP_MAX blocks). Ground more than LEG_RISE_MAX above a hoof is a step's face
	 * it is up against: the hoof swings back off it onto the tread, looking up to LEG_FACE_SEARCH more half its depth back.
	 * Where the model hasn't been drawn yet, a pair of hooves draws up onto higher ground by at most LEG_LIFT_RATE blocks a
	 * tick.
	 */
	public static final float LEG_DRAW_MAX = 0.35F;
	/**
	 * A hoof lifts no faster than HOOF_LIFT_SPEED blocks a second (drawing its leg up), and a drawn-up leg straightens no
	 * faster than LEG_STRAIGHTEN_SPEED; but a hoof is never left in the ground under it.
	 */
	public static final float HOOF_LIFT_SPEED = 10.0F;
	public static final float LEG_STRAIGHTEN_SPEED = 4.0F;
	public static final float LEG_RISE_MAX = 0.55F;
	public static final int LEG_FACE_SEARCH = 5;
	public static final float FIT_TIME = 0.12F;
	/** The body comes down onto its legs no faster than it falls: gravity, blocks a second a second (the game's 0.08 a tick a tick). */
	public static final float FIT_GRAVITY = 32.0F;
	public static final float FIT_UP_MAX = 0.6F;
	/**
	 * The body also comes down onto its legs, by up to FIT_DOWN_MAX blocks, while every standing hoof is above its ground
	 * (a leg the stride has lifted more than PACK_LIFT blocks, or one over ground more than HANG_REACH below, a drop,
	 * isn't standing), but never so low that the chest meets a step ahead.
	 */
	public static final float FIT_DOWN_MAX = 0.6F;
	public static final float PACK_LIFT = 0.06F;
	public static final double HANG_REACH = 0.6;
	static final float LEG_LIFT_RATE = 0.15F;
	/**
	 * A hoof in its swing (lifted by the gait) lifts to clear ground rising ahead of it, the way a swinging hoof arcs over a
	 * step's edge: up to that ground's top less RAMP_SLOPE for every block it still has to go, for ground no more than
	 * RAMP_HIGHEST above it (so within RAMP_REACH blocks, where that comes to nothing); all of that once the gait has it
	 * PACK_LIFT up, less below that. A planted hoof none.
	 */
	public static final double RAMP_SLOPE = 1.5;
	public static final double RAMP_HIGHEST = 1.5;
	public static final double RAMP_REACH = RAMP_HIGHEST / RAMP_SLOPE;
	/**
	 * Moving on (fully at SLIDE_SPEED blocks/tick), a hoof on the ground takes the last of that lift, with a slope of
	 * SLIDE_RAMP_SLOPE (the animation's planted hooves slide a little, and would pop up a step's edge). Higher ground
	 * under part of a sole holds it up fully once EDGE_BLEND blocks of it are under the sole, less below that.
	 */
	public static final float SLIDE_SPEED = 0.05F;
	public static final double SLIDE_RAMP_SLOPE = 6.0;
	public static final double EDGE_BLEND = 0.06;
	/** Moving on, a hoof the body has lifted off the ground lifts as a swinging one would, fully once HOOF_FREE blocks up. */
	public static final float HOOF_FREE = 0.1F;
	/** On ground tilting STRIDE_STEEP_TILT radians or more, the legs swing only STRIDE_STEEP of the animation's stride (eased in up to there). */
	public static final float STRIDE_STEEP = 0.5F;
	public static final float STRIDE_STEEP_TILT = 0.52F;
	/**
	 * Taking the body's tilt off the legs swings their tops out of the body; each leg is drawn up LEG_HALF_DEPTH model
	 * pixels x sin of that swing so no gap shows at the hip or shoulder (the body is lowered to match, so the hooves
	 * still reach).
	 */
	public static final float LEG_HALF_DEPTH = 2.0F;
	/**
	 * Climbing (or taking off), the neck reaches forward and down by NECK_COUNTER_UP of the body's nose-up tilt, so the
	 * head stays low and forward as a climbing horse carries it (and out of the rider's way) instead of rearing up;
	 * going down, it comes up by NECK_COUNTER_DOWN of the nose-down tilt.
	 */
	public static final float NECK_COUNTER_UP = 0.85F;
	public static final float NECK_COUNTER_DOWN = 0.6F;
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
	/** Taking off, the tilt it had on the ground eases out by at most this much a tick (degrees). */
	static final float AIR_PITCH_RELEASE = 2.5F;
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
	/** Till the legs are AIR_GROUNDED of the way into the jump's shape (a step down is a short fall), each hoof still finds its ground. */
	public static final float AIR_GROUNDED = 0.5F;
	static final float AIR_LEG_RISE = 0.45F;
	static final float AIR_LEG_PHASE_EASE = 0.35F;
	/**
	 * The tail hangs off the rump on its own weight, on a spring (TAIL_SPRING, TAIL_DAMPING a tick; a little bouncy): it
	 * trails down as the horse rises and floats up as it falls (TAIL_DRAG radians per block/tick of climb), goes light
	 * when the horse is weightless and heavy when it is thrown up or caught (TAIL_WEIGHT radians at most), and flicks on
	 * landing. In the air it also keeps the lift the stride gave it (TAIL_STREAM at full speed), as the stride stops.
	 * Limits TAIL_MAX_UP and TAIL_MAX_DOWN.
	 */
	public static final float TAIL_DRAG = 1.2F;
	public static final float TAIL_WEIGHT = 0.3F;
	public static final float TAIL_STREAM = 0.7F;
	static final float TAIL_SPRING = 0.25F;
	static final float TAIL_DAMPING = 0.3F;
	static final float TAIL_MAX_UP = 0.9F;
	static final float TAIL_MAX_DOWN = 0.6F;

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
	public static final float RIDER_BANK_FOLLOW = 0.3F;
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
	/**
	 * Room between horse and rider: when the horse's head comes up toward the rider (a jump, a steep climb), the horse
	 * stretches its neck forward (up to NECK_REACH_MAX degrees, as a jumping horse does), and if that isn't enough the
	 * rider folds less over the neck (up to SIT_BACK_MAX degrees), each only as far as keeps the middle of the horse's
	 * head HEAD_ROOM from the middle of the rider's head and CHEST_ROOM from their chest (blocks), tried in ROOM_STEPS
	 * steps; quickly in (ROOM_IN_SECONDS), easing back (ROOM_OUT_SECONDS). The horse's head is NECK_BASE_FORWARD /
	 * NECK_BASE_UP from the ground under its centre to the base of its neck, and HEAD_FORWARD / HEAD_UP from there to the
	 * middle of its head; the rider's head and chest are RIDER_HEAD_ABOVE_SEAT and RIDER_CHEST_ABOVE_SEAT above the point
	 * the rider leans about.
	 */
	public static final float NECK_BASE_FORWARD = 0.75F;
	public static final float NECK_BASE_UP = 1.25F;
	public static final float HEAD_FORWARD = 0.2F;
	public static final float HEAD_UP = 0.6F;
	public static final float RIDER_HEAD_ABOVE_SEAT = 1.15F;
	public static final float RIDER_CHEST_ABOVE_SEAT = 0.6F;
	public static final float HEAD_ROOM = 0.6F;
	public static final float CHEST_ROOM = 0.5F;
	public static final float NECK_REACH_MAX = 35.0F;
	public static final float SIT_BACK_MAX = 30.0F;
	public static final int ROOM_STEPS = 7;
	public static final float ROOM_IN_SECONDS = 0.05F;
	public static final float ROOM_OUT_SECONDS = 0.35F;
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
	 * A tool or weapon in the main hand (a sword, an axe, a pickaxe, a mace, a trident, a spear, a bow) is held off the
	 * reins, a little out to the side and ready: the arm forward, out from the body and turned out a little, radians. The
	 * other hand keeps the reins; a swing is played from there and comes back to it.
	 */
	public static final float READY_ARM_FORWARD = 0.4F;
	public static final float READY_ARM_OUT = 0.4F;
	public static final float READY_ARM_YAW = 0.55F;
	/**
	 * Drawing a bow: the rider turns side-on to the aim, BOW_SIDE_ON radians past facing it (the bow arm's shoulder toward
	 * it), and no further than BOW_TWIST_MAX from square in the saddle; the bow arm out along the aim, BOW_ARM_IN in toward
	 * the eye line. The string hand comes from the arrow's nock (BOW_NOCK pixels back from the grip) to the anchor at the
	 * cheek on the bow's side (head pixels out, up and forward) as the draw charges, along the bow's own power curve (full
	 * at BOW_FULL_DRAW_TICKS); the arms come up into the draw from where they rest over BOW_RAISE_TICKS. Loosed, the string
	 * hand flies BOW_FLICK pixels back past the cheek over the first BOW_FLICK_SHARE of BOW_RELEASE_TICKS, the bow held up
	 * for BOW_HOLD_SHARE of it, and both arms come down to where they rest by the end.
	 */
	public static final float BOW_SIDE_ON = 0.6F;
	public static final float BOW_TWIST_MAX = 1.3F;
	public static final float BOW_ARM_IN = 0.1F;
	public static final float BOW_NOCK = 2.0F;
	public static final float BOW_ANCHOR_X = 2.5F;
	public static final float BOW_ANCHOR_Y = -1.5F;
	public static final float BOW_ANCHOR_Z = -4.5F;
	public static final float BOW_FULL_DRAW_TICKS = 20.0F;
	public static final float BOW_RAISE_TICKS = 4.0F;
	public static final float BOW_FLICK = 3.0F;
	public static final float BOW_FLICK_SHARE = 0.2F;
	public static final float BOW_HOLD_SHARE = 0.3F;
	public static final float BOW_RELEASE_TICKS = 10.0F;
	/**
	 * Loose hands lag the body: they drop as the horse lands and lift as it rises (radians per block of saddle lift,
	 * applied against the lift).
	 */
	public static final float HAND_BOB = 2.0F;
	/**
	 * At a canter the rider's seat follows the horse's back: the pelvis rocks a little forward and back with the stride
	 * while the shoulders stay quiet (torso swing from the shoulders, radians per block of saddle lift; ~3cm of hip travel
	 * at a canter). At a gallop riders rise into a half seat and the legs take the motion, so the hips move less still
	 * (PELVIS_GALLOP_SHARE of it). (Play-test: 1.7 with no gallop easing read as humping the horse.)
	 */
	public static final float PELVIS_SLIDE = 0.6F;
	public static final float PELVIS_GALLOP_SHARE = 0.5F;
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
	/**
	 * How far the riding camera sits back, at a standstill and at a full gallop, as designed; the player's camera distance
	 * setting scales it (CAMERA_DISTANCE_SCALE) for this mod's own camera only. A camera add-on gets the designed values.
	 */
	public static final float CAMERA_DISTANCE_STILL = 4.0F;
	public static final float CAMERA_DISTANCE_GALLOP = 5.5F;
	public static float CAMERA_DISTANCE_SCALE = 1.0F;
	/** Low value = the camera falls behind as the horse speeds up and catches up as it slows. */
	public static final float CAMERA_DISTANCE_SMOOTHING = 0.08F;
	/** Pivot height above the rider's eyes, blocks (the player's camera height setting). */
	public static final float CAMERA_HEIGHT_DEFAULT = 0.2F;
	public static float CAMERA_HEIGHT = CAMERA_HEIGHT_DEFAULT;
	/**
	 * Vertical follow rate, in two stages so the camera's climb starts and stops softly: filters out the jolt of stepping
	 * up blocks and of a jump's takeoff, without falling far behind.
	 */
	public static final float CAMERA_HEIGHT_SMOOTHING = 0.6F;
	/** Further than this from the rider (a teleport), the camera's height jumps straight there. */
	public static final double CAMERA_HEIGHT_SNAP = 4.0;
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
	public static final boolean RIDE_THROUGH_LEAVES = true;
	/** A horse moving faster than a walk tramples small creatures in its path. */
	public static final boolean TRAMPLE = true;
	/** Trampling starts above this share of vanilla top speed (a walk is 0.35, a trot 0.6). */
	static final float TRAMPLE_MIN_SPEED = 0.5F;
	/** Damage at a full gallop, half-hearts; scales down to nothing at TRAMPLE_MIN_SPEED. */
	static final float TRAMPLE_DAMAGE_MAX = 8.0F;
	/** Knock aside strength at a full gallop. */
	static final float TRAMPLE_KNOCKBACK = 0.6F;
	/** "Small" means no wider or taller than this, blocks (chickens, rabbits, cats, foxes, frogs, baby animals...). */
	static final float TRAMPLE_MAX_WIDTH = 0.8F;
	static final float TRAMPLE_MAX_HEIGHT = 1.0F;

	private RideTuning() {
	}

	/** The neck's counter to the body's tilt (radians, forward/down positive) for a tilt of {@code pitch} radians. */
	public static float neckCounter(final float pitch) {
		return pitch * (pitch > 0.0F ? NECK_COUNTER_UP : NECK_COUNTER_DOWN);
	}

	/** 0 at a standstill, 1 at full gallop. */
	public static float gallopFraction(final float speed) {
		return Math.min(Math.abs(speed) * (1.0F / GAIT_SPEED[GALLOP]), 1.0F);
	}
}
