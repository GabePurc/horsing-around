package dev.horsingaround.ride;

import static dev.horsingaround.ride.RideTuning.CART_AXLE_DROP;
import static dev.horsingaround.ride.RideTuning.CART_AXLE_EASE;
import static dev.horsingaround.ride.RideTuning.CART_AVERAGE_LEAST;
import static dev.horsingaround.ride.RideTuning.CART_AVERAGE_MOST;
import static dev.horsingaround.ride.RideTuning.CART_AVERAGE_PER_SPEED;
import static dev.horsingaround.ride.RideTuning.CART_AXLE_RISE;
import static dev.horsingaround.ride.RideTuning.CART_BACK_CLEAR;
import static dev.horsingaround.ride.RideTuning.CART_BED_CLEAR;
import static dev.horsingaround.ride.RideTuning.CART_BED_FRONT;
import static dev.horsingaround.ride.RideTuning.CART_BED_PAST;
import static dev.horsingaround.ride.RideTuning.CART_BED_RISE;
import static dev.horsingaround.ride.RideTuning.CART_BED_SIDE;
import static dev.horsingaround.ride.RideTuning.CART_BEHIND_LEAST;
import static dev.horsingaround.ride.RideTuning.CART_BEHIND_LESS;
import static dev.horsingaround.ride.RideTuning.CART_BEHIND_SCALE;
import static dev.horsingaround.ride.RideTuning.CART_DAMPER;
import static dev.horsingaround.ride.RideTuning.CART_DAMPER_GROUND;
import static dev.horsingaround.ride.RideTuning.CART_FALL_MOST;
import static dev.horsingaround.ride.RideTuning.CART_GIVE_DAMPING;
import static dev.horsingaround.ride.RideTuning.CART_GIVE_GAIN;
import static dev.horsingaround.ride.RideTuning.CART_GIVE_MAX;
import static dev.horsingaround.ride.RideTuning.CART_GIVE_SMOOTH;
import static dev.horsingaround.ride.RideTuning.CART_GIVE_SPRING;
import static dev.horsingaround.ride.RideTuning.CART_GRAVITY;
import static dev.horsingaround.ride.RideTuning.CART_HANG_LEAN;
import static dev.horsingaround.ride.RideTuning.CART_HITCH_SLACK;
import static dev.horsingaround.ride.RideTuning.CART_PACE_EASE;
import static dev.horsingaround.ride.RideTuning.CART_RAMP_REACH;
import static dev.horsingaround.ride.RideTuning.CART_RAMP_SLOPE;
import static dev.horsingaround.ride.RideTuning.CART_REST_RISE;
import static dev.horsingaround.ride.RideTuning.CART_ROLL_MAX;
import static dev.horsingaround.ride.RideTuning.CART_SUSPENSION;
import static dev.horsingaround.ride.RideTuning.CART_SWING_SLOW;
import static dev.horsingaround.ride.RideTuning.CART_TILT_MAX;
import static dev.horsingaround.ride.RideTuning.CART_WHEEL_BUMP;
import static dev.horsingaround.ride.RideTuning.CART_WHEEL_DROOP;
import static dev.horsingaround.ride.RideTuning.CART_WHEEL_STEP;
import static dev.horsingaround.ride.RideTuning.CART_WHEEL_TRAVEL_EASE;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Icy's Better Horses' carts behind a horse this mod rides move as real ones do (user, 2026-11-02 and 2026-11-06). Icy's
 * mod swings a cart round the horse's middle and tilts it as a whole. A cart's wheels roll but don't slide sideways: it
 * turns only as it is pulled along, so its rear axle follows the hitch (the horse's middle, where Icy's mod pivots the
 * cart) at the cart's length, cutting inside the horse's line on a bend. The shafts let it angle only so far off
 * the horse, less the faster it goes. Each wheel rolls on the ground under it, and each corner of the cart rides on its wheel with the weight it has:
 * it falls with nothing under it and lands on its suspension (see {@link #carry}), where the wheels would put it (see
 * {@link #level}), and the shafts follow the horse. A wagon has four corners; a two-wheeled cart two, and tilts towards the
 * hitch, as far as a cart tilts. It stands further back than Icy's mod has it, by the size of the horse ({@link #behind}), and
 * the shafts give a little as the horse pulls it on or slows.
 */
public final class CartTrail {
	/** Where a wheel's middle is across the cart, and how big it is, as Icy's models are built. */
	private static final double WHEEL_SIDE = 1.25;
	private static final double WHEEL_RADIUS = 0.71;
	public static final int FRONT_LEFT = 0;
	public static final int FRONT_RIGHT = 1;
	public static final int REAR_LEFT = 2;
	public static final int REAR_RIGHT = 3;
	/** Ticks a cart nobody is riding stays looked at once it stops moving. */
	private static final int SETTLE_TICKS = 40;
	private static final double GROUND_EPSILON = 0.01;
	/** How far apart in height the two wheels of an axle can be with the cart leaned as far as it goes; the two along it, by their distance. */
	private static final double ACROSS_REACH = 2.0 * WHEEL_SIDE * Math.sin(Math.toRadians(CART_ROLL_MAX));
	private static final double ALONG_REACH = Math.sin(Math.toRadians(CART_TILT_MAX));

	/** While a seat is being worked out to draw it, the partial tick; NaN otherwise. Render thread only. */
	public static float drawing = Float.NaN;

	private CartTrail() {
	}

	/** What the rest of the mod asks of a cart (the cart entity, through HorseCartEntityMixin). */
	public interface Lean {
		/** Whether this mod moves the cart: it is hitched to a horse this mod rides. */
		boolean horsingaround$rolling();

		/** How far it leans over, degrees, with its left side up positive. */
		float horsingaround$roll(float partialTick);

		/** Its heading as drawn, degrees. */
		float horsingaround$yaw(float partialTick);

		/** How far its wheels have turned rolling forward, radians. */
		double horsingaround$turned(float partialTick);

		/** How far it is drawn above where the horse is. */
		float horsingaround$rise(float partialTick);

		/** How far above the horse, as the horse is drawn: what a wagon's shafts make up, the two-wheeled cart hanging from the hitch. */
		float horsingaround$belowHitch(float partialTick);

		/** How much further behind the horse than Icy's mod has it the cart and its seats are, blocks. */
		double horsingaround$behind();

		/** How far up into the cart a wheel (FRONT_LEFT..REAR_RIGHT) is drawn, on its suspension, blocks. */
		float horsingaround$travel(int wheel, float partialTick);

		/** How far above what it rolls on (the ground, with its ramps) a wheel was left last tick, blocks. */
		float horsingaround$offRamp(int wheel);

		/** Its tilt as drawn, degrees, up at the front positive. */
		float horsingaround$tilt(float partialTick);

		/** How far behind the horse than Icy's mod has it the cart is drawn, the shafts' give included, blocks. */
		double horsingaround$behind(float partialTick);

		/** The same without the give: where it stands, for this horse. */
		double horsingaround$standing();

		/** The heading of the horse pulling it, as drawn, degrees. */
		float horsingaround$horseYaw(float partialTick);

		/** Whether a wall or anything else too tall to roll over stands right behind it. */
		boolean horsingaround$blockedBehind();
	}

	/** Where one cart's wheels are: its rear axle on the ground plan, and how high each wheel has rolled. */
	public static final class Axle {
		double x;
		double z;
		boolean placed;
		/** Heights, by FRONT_LEFT..REAR_RIGHT (a two-wheeled cart has only the rear ones); NaN till first found. */
		final double[] wheel = new double[4];
		private final double[] plane = new double[4];
		/** The wheels' heights as far as they can hold the body: one further below the others than it can lean or tilt doesn't count that far. */
		private final double[] held = new double[4];
		/** Working out what the body must clear this tick, see {@link #rest}: at once and gradually, in blocks up. */
		private double needNow;
		private double needSoon;
		/** How far up into the cart each wheel is, on its suspension. */
		private final float[] travel = new float[4];
		private final float[] travelO = new float[4];
		private final float[] off = new float[4];
		/** The cart's height, at its origin; NaN afloat. */
		double y = Double.NaN;
		double yO = Double.NaN;
		float roll;
		float rollO;
		double turned;
		double turnedO;
		/** How far behind where Icy's mod has it the cart stands, for this horse; and the shafts' give on top. */
		double behind;
		double give;
		double giveO;
		/** How much further back its origin goes as it tilts, so its wheels stay where they roll: tilted, it's shorter on the ground. */
		double shortened;
		double shortenedO;
		private double giveRate;
		private double speed;
		/** Each corner of the cart: how high it is, how fast it's going, and the height the wheel would hold it at, last tick. */
		private final double[] corner = new double[4];
		private final double[] cornerRate = new double[4];
		private final double[] heldLast = new double[4];
		/** The ground under each corner: averaged along the cart, and under the wheel itself (negative infinity if none, NaN a wall). */
		private final double[] surface = new double[4];
		private final double[] under = new double[4];
		/** How fast it's going, eased, for how far to average the ground. */
		private double pace;
		private double changeSmooth;
		/** What a walk along the ground (see {@link #walk}) added up, and the ramp a wheel has from either way. */
		private double walked;
		private double ramp;
		private double lastX;
		private double lastZ;
		private double lastY;
		private int stillTicks;
		/** (Its own: carts tick on the server's thread and the client's.) */
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		public Axle() {
			Arrays.fill(this.wheel, Double.NaN);
			Arrays.fill(this.corner, Double.NaN);
		}

		/** Next time, the axle starts straight behind the horse again, on the ground. */
		public void lift() {
			this.placed = false;
			afloat();
			this.roll = 0.0F;
			this.rollO = 0.0F;
			this.behind = 0.0;
			this.give = 0.0;
			this.giveO = 0.0;
			this.giveRate = 0.0;
			this.speed = 0.0;
			this.pace = 0.0;
			this.changeSmooth = 0.0;
			this.shortened = 0.0;
			this.shortenedO = 0.0;
		}

		/** Afloat, the wheels touch nothing: next time they find the ground afresh, and the cart comes level meanwhile. */
		public void afloat() {
			Arrays.fill(this.wheel, Double.NaN);
			Arrays.fill(this.corner, Double.NaN);
			Arrays.fill(this.cornerRate, 0.0);
			this.y = Double.NaN;
			this.yO = Double.NaN;
			this.rollO = this.roll;
			this.roll *= 0.85F;
			Arrays.fill(this.travel, 0.0F);
			Arrays.fill(this.travelO, 0.0F);
			Arrays.fill(this.off, 0.0F);
		}

		public boolean placed() {
			return this.placed;
		}

		public float roll(final float partialTick) {
			return Mth.lerp(partialTick, this.rollO, this.roll);
		}

		public float travel(final int wheel, final float partialTick) {
			return Mth.lerp(partialTick, this.travelO[wheel], this.travel[wheel]);
		}

		public float offRamp(final int wheel) {
			return this.off[wheel];
		}

		public double standing() {
			return this.behind;
		}

		/** How much further behind than Icy's mod has it the cart is drawn, with the shafts' give. */
		public double behind(final float partialTick) {
			return this.behind + Mth.lerp(partialTick, this.giveO, this.give) + Mth.lerp(partialTick, this.shortenedO, this.shortened);
		}

		public double behind() {
			return this.behind + this.give + this.shortened;
		}

		public void standBehind(final double behind) {
			this.behind = behind;
		}

		public double turned(final float partialTick) {
			return Mth.lerp(partialTick, this.turnedO, this.turned);
		}

		/** The cart's height as drawn; NaN afloat. */
		public double y(final float partialTick) {
			return Mth.lerp(partialTick, this.yO, this.y);
		}
	}

	/** How much further behind the horse than Icy's mod has it a cart stands, by the size of the horse (blocks). */
	public static double behind(final Entity horse) {
		return Math.max(CART_BEHIND_LEAST, horse.getBbHeight() * CART_BEHIND_SCALE - CART_BEHIND_LESS);
	}

	/** Moves the axle after the hitch, now at (hitchX, hitchZ), and returns the cart's heading (degrees, like an entity's yaw). */
	public static float follow(final Axle axle, final double hitchX, final double hitchZ, final float horseYaw, final double length) {
		float yaw = horseYaw;
		final double dx = hitchX - axle.x;
		final double dz = hitchZ - axle.z;
		final double distanceSq = dx * dx + dz * dz;
		// (Just hitched, or the horse jumped far in a tick, a teleport: straight behind it.)
		final boolean kept = axle.placed && distanceSq < 4.0 * length * length;
		if (kept && distanceSq > 1.0E-8) {
			yaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
			final float swing = Mth.wrapDegrees(yaw - horseYaw);
			if (Math.abs(swing) > CART_SWING_SLOW) {
				yaw = horseYaw + Math.copySign(CART_SWING_SLOW, swing);
			}
		}
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double x = hitchX + Mth.sin(rad) * length;
		final double z = hitchZ - Mth.cos(rad) * length;
		axle.turnedO = axle.turned;
		axle.giveO = axle.give;
		if (kept) {
			final double speed = (x - axle.x) * -Mth.sin(rad) + (z - axle.z) * Mth.cos(rad);
			axle.turned += speed / WHEEL_RADIUS;
			axle.pace += (Math.abs(speed) - axle.pace) * CART_PACE_EASE;
			axle.changeSmooth += (speed - axle.speed - axle.changeSmooth) * CART_GIVE_SMOOTH;
			final double wanted = Mth.clamp(axle.changeSmooth * CART_GIVE_GAIN, -CART_GIVE_MAX, CART_GIVE_MAX);
			axle.giveRate += (wanted - axle.give) * CART_GIVE_SPRING - axle.giveRate * CART_GIVE_DAMPING;
			axle.give += axle.giveRate;
			axle.speed = speed;
		} else {
			Arrays.fill(axle.wheel, Double.NaN);
			Arrays.fill(axle.corner, Double.NaN);
		}
		axle.x = x;
		axle.z = z;
		axle.placed = true;
		return yaw;
	}

	/**
	 * Rolls the wheels (after {@link #follow}) up or down to the ground under them, leans the cart by them and returns its
	 * tilt eased from {@code tilt} (degrees, up at the front positive).
	 *
	 * @param hitchY where the horse's middle is, as drawn
	 * @param rear   how far behind the hitch the rear axle is
	 * @param front  how far behind it a wagon's front axle is; 0 for a two-wheeled cart
	 * @param ridden whether anyone rides the horse (else, once everything stops, the wheels stop being looked at)
	 */
	public static float lean(
		final Axle axle, final Level level, final double hitchY, final float yaw, final double rear, final double front, final float tilt, final boolean ridden
	) {
		axle.rollO = axle.roll;
		axle.yO = axle.y;
		if (!ridden && axle.x == axle.lastX && axle.z == axle.lastZ && hitchY == axle.lastY) {
			if (++axle.stillTicks > SETTLE_TICKS) {
				return tilt;
			}
		} else {
			axle.stillTicks = 0;
			axle.lastX = axle.x;
			axle.lastZ = axle.z;
			axle.lastY = hitchY;
		}
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double sideX = Mth.cos(rad) * WHEEL_SIDE;
		final double sideZ = Mth.sin(rad) * WHEEL_SIDE;
		final double alongX = -Mth.sin(rad);
		final double alongZ = Mth.cos(rad);
		final boolean wagon = front > 0.0;
		if (wagon) {
			final double aheadX = alongX * (rear - front);
			final double aheadZ = alongZ * (rear - front);
			wheel(axle, FRONT_LEFT, level, axle.x + aheadX + sideX, axle.z + aheadZ + sideZ, hitchY, alongX, alongZ);
			wheel(axle, FRONT_RIGHT, level, axle.x + aheadX - sideX, axle.z + aheadZ - sideZ, hitchY, alongX, alongZ);
		}
		wheel(axle, REAR_LEFT, level, axle.x + sideX, axle.z + sideZ, hitchY, alongX, alongZ);
		wheel(axle, REAR_RIGHT, level, axle.x - sideX, axle.z - sideZ, hitchY, alongX, alongZ);
		// What the ground would hold it at, and each corner rides on that with the weight it has. A two-wheeled cart's front
		// goes by the ground under the horse, not the horse: it would shake with every step the horse takes.
		hold(axle, wagon, rear - front);
		if (wagon) {
			level(axle.held, axle.plane);
		} else {
			look(axle, FRONT_LEFT, level, axle.x + alongX * (rear + axle.behind), axle.z + alongZ * (rear + axle.behind), hitchY, hitchY, alongX, alongZ);
			axle.plane[FRONT_LEFT] = axle.surface[FRONT_LEFT];
			axle.plane[REAR_LEFT] = axle.held[REAR_LEFT];
			axle.plane[REAR_RIGHT] = axle.held[REAR_RIGHT];
			carry(axle, FRONT_LEFT, axle.plane[FRONT_LEFT], hitchY);
		}
		for (int i = wagon ? FRONT_LEFT : REAR_LEFT; i <= REAR_RIGHT; i++) {
			carry(axle, i, axle.plane[i], hitchY);
		}
		final double rearAt = (axle.corner[REAR_LEFT] + axle.corner[REAR_RIGHT]) * 0.5;
		final double pitch;
		final double across;
		if (wagon) {
			pitch = Mth.atan2((axle.corner[FRONT_LEFT] + axle.corner[FRONT_RIGHT]) * 0.5 - rearAt, rear - front);
			across = (axle.corner[FRONT_LEFT] + axle.corner[REAR_LEFT] - axle.corner[FRONT_RIGHT] - axle.corner[REAR_RIGHT]) * 0.25;
		} else {
			pitch = Mth.atan2(axle.corner[FRONT_LEFT] - rearAt, rear + axle.behind);
			across = (axle.corner[REAR_LEFT] - axle.corner[REAR_RIGHT]) * 0.5;
		}
		final float tilted = Mth.clamp((float) (pitch * Mth.RAD_TO_DEG), -CART_TILT_MAX, CART_TILT_MAX);
		axle.roll = Mth.clamp((float) (Mth.atan2(across, WHEEL_SIDE) * Mth.RAD_TO_DEG), -CART_ROLL_MAX, CART_ROLL_MAX);
		// Its height where its origin is, from its axles by their middle, whatever the tilt.
		final double restsAt = wagon ? (axle.corner[0] + axle.corner[1] + axle.corner[2] + axle.corner[3]) * 0.25 : rearAt;
		axle.y = restsAt + (wagon ? (rear + front) * 0.5 : rear) * Mth.sin(tilted * Mth.DEG_TO_RAD);
		axle.yO = Double.isNaN(axle.yO) ? axle.y : axle.yO;
		// It doesn't go into the ground: it comes up off what it would. Nor does it stay too far below the horse: the shafts bring it.
		final float restRoll = Mth.sin(axle.roll * Mth.DEG_TO_RAD);
		final float restTilt = Mth.sin(tilted * Mth.DEG_TO_RAD);
		axle.needNow = hitchY - CART_HITCH_SLACK - axle.y;
		axle.needSoon = 0.0;
		for (int i = wagon ? FRONT_LEFT : REAR_LEFT; i <= REAR_RIGHT; i++) {
			if (!Double.isNaN(axle.under[i]) && axle.under[i] != Double.NEGATIVE_INFINITY) {
				rest(axle, axle.under[i], i < REAR_LEFT ? front : rear, i % 2 == 0 ? WHEEL_SIDE : -WHEEL_SIDE, CART_WHEEL_BUMP, restTilt, restRoll, true);
			}
		}
		for (int spot = 0; spot < 5; spot++) {
			final double z = spot < 2 ? CART_BED_FRONT : spot < 4 ? rear + CART_BED_PAST : (CART_BED_FRONT + rear + CART_BED_PAST) * 0.5;
			final double side = spot == 4 ? 0.0 : spot % 2 == 0 ? CART_BED_SIDE : -CART_BED_SIDE;
			final double ground = ground(
				level, axle.pos, Mth.floor(axle.x + alongX * (rear - z) + Mth.cos(rad) * side), Mth.floor(axle.z + alongZ * (rear - z) + Mth.sin(rad) * side),
				axle.y - z * restTilt + side * restRoll, hitchY
			);
			if (!Double.isNaN(ground) && ground != Double.NEGATIVE_INFINITY) {
				rest(axle, ground, z, side, CART_BED_CLEAR, restTilt, restRoll, false);
			}
		}
		final double up = Math.max(Math.min(axle.needNow, CART_REST_RISE), Math.min(axle.needSoon, CART_BED_RISE));
		if (up > 0.0) {
			axle.y += up;
			for (int i = wagon ? FRONT_LEFT : REAR_LEFT; i <= REAR_RIGHT; i++) {
				axle.corner[i] += up;
				// (So it goes on coming up, instead of being pushed up again and again.)
				axle.cornerRate[i] = Math.max(axle.cornerRate[i], up * 0.5);
			}
		}
		axle.shortenedO = axle.shortened;
		axle.shortened = (wagon ? (rear + front) * 0.5 : rear) * (1.0F - Mth.cos(tilted * Mth.DEG_TO_RAD));
		// Each wheel makes up what's left between the ground under it and the body, as far as its suspension goes.
		final float sinTilt = Mth.sin(tilted * Mth.DEG_TO_RAD);
		final float sinRoll = Mth.sin(axle.roll * Mth.DEG_TO_RAD);
		final double origin = axle.y;
		System.arraycopy(axle.travel, 0, axle.travelO, 0, 4);
		for (int i = wagon ? FRONT_LEFT : REAR_LEFT; i <= REAR_RIGHT; i++) {
			final double body = origin - (i < REAR_LEFT ? front : rear) * sinTilt + (i % 2 == 0 ? WHEEL_SIDE : -WHEEL_SIDE) * sinRoll;
			final float wanted = (float) Mth.clamp(axle.wheel[i] - body, -CART_WHEEL_DROOP, CART_WHEEL_BUMP);
			axle.travel[i] += (wanted - axle.travel[i]) * CART_WHEEL_TRAVEL_EASE;
			axle.off[i] = (float) (body + axle.travel[i] - axle.wheel[i]);
		}
		return tilted;
	}

	/**
	 * What the wheels can hold of the body: one further below the others than the cart can lean or tilt is as far below as it can.
	 * So a wheel over a deep drop doesn't drag the cart down until the wheels still on the ground are pushed into it.
	 */
	private static void hold(final Axle axle, final boolean wagon, final double wheelbase) {
		System.arraycopy(axle.surface, 0, axle.held, 0, 4);
		holdPair(axle.held, REAR_LEFT, REAR_RIGHT, ACROSS_REACH);
		if (wagon) {
			holdPair(axle.held, FRONT_LEFT, FRONT_RIGHT, ACROSS_REACH);
			holdPair(axle.held, FRONT_LEFT, REAR_LEFT, wheelbase * ALONG_REACH);
			holdPair(axle.held, FRONT_RIGHT, REAR_RIGHT, wheelbase * ALONG_REACH);
		}
	}

	private static void holdPair(final double[] heights, final int one, final int other, final double reach) {
		final double high = Math.max(heights[one], heights[other]);
		heights[one] = Math.max(heights[one], high - reach);
		heights[other] = Math.max(heights[other], high - reach);
	}

	/**
	 * One place the body may not go below: {@code ground}, under a point of it this far behind the cart's origin and this far to
	 * its left, the point being {@code clear} above the feet of its wheels. Adds up how far it must come up to clear them all: at
	 * once, or gradually.
	 */
	private static void rest(
		final Axle axle, final double ground, final double z, final double side, final double clear, final float sinTilt, final float sinRoll, final boolean now
	) {
		final double need = ground - (axle.y - z * sinTilt + side * sinRoll + clear);
		if (now) {
			axle.needNow = Math.max(axle.needNow, need);
		} else {
			axle.needSoon = Math.max(axle.needSoon, need);
		}
	}

	/**
	 * Moves a corner of the cart this tick, its wheel holding it at {@code held}: on its suspension, which works against how far
	 * it is below that and how fast it's going against the ground. It only holds the cart up, so a corner can't go down
	 * faster than it would fall: with the ground dropping away it falls, and coming down on the ground it slows over a few ticks.
	 */
	private static void carry(final Axle axle, final int i, final double wheel, final double hitchY) {
		if (Double.isNaN(axle.corner[i])) {
			axle.corner[i] = wheel;
			axle.cornerRate[i] = 0.0;
			axle.heldLast[i] = wheel;
			return;
		}
		final double held = wheel;
		final double rate = axle.cornerRate[i];
		final double carried = rate + (held - axle.corner[i]) * CART_SUSPENSION + (held - axle.heldLast[i]) * CART_DAMPER_GROUND - rate * CART_DAMPER;
		axle.heldLast[i] = held;
		axle.cornerRate[i] = Math.max(Math.max(carried, rate - CART_GRAVITY), -CART_FALL_MOST);
		axle.corner[i] = Mth.clamp(axle.corner[i] + axle.cornerRate[i], hitchY - 8.0, hitchY + 8.0);
	}

	/**
	 * Where a rigid cart lies on its four wheels at these heights ({@code out}, the same order), as a plane: where it can
	 * touch all four, on them. Where it can't, two opposite corners are the high ones and it rests on those, and the other
	 * two make up the difference. If one of those is simply lower than the other, over a hole or an edge, it hangs in the
	 * air (user, 2026-11-06): the cart leans only CART_HANG_LEAN of the way towards it instead of following it down. Two
	 * wheels at one height and two at another aren't that: the plane goes through all four, so the cart leans all the way.
	 */
	public static void level(final double[] wheels, final double[] out) {
		System.arraycopy(wheels, 0, out, 0, 4);
		final double twist = wheels[FRONT_LEFT] + wheels[REAR_RIGHT] - wheels[FRONT_RIGHT] - wheels[REAR_LEFT];
		final double total = Math.abs(twist);
		if (total < 1.0E-6) {
			return;
		}
		final int one = twist > 0.0 ? FRONT_RIGHT : FRONT_LEFT;
		final int other = twist > 0.0 ? REAR_LEFT : REAR_RIGHT;
		final int higher = wheels[one] >= wheels[other] ? one : other;
		final int lower = higher == one ? other : one;
		final double share = Mth.lerp(Math.min((wheels[higher] - wheels[lower]) / total, 1.0), 0.5, CART_HANG_LEAN);
		out[higher] += total * share;
		out[lower] += total * (1.0 - share);
	}

	/** Whether anything too tall to roll over stands just behind the rear of the cart. */
	public static boolean blockedBehind(final Axle axle, final Level level, final double hitchY, final float yaw) {
		if (!axle.placed) {
			return false;
		}
		final float rad = yaw * Mth.DEG_TO_RAD;
		final double x = axle.x + Mth.sin(rad) * CART_BACK_CLEAR;
		final double z = axle.z - Mth.cos(rad) * CART_BACK_CLEAR;
		final double sideX = Mth.cos(rad) * WHEEL_SIDE;
		final double sideZ = Mth.sin(rad) * WHEEL_SIDE;
		return Double.isNaN(ground(level, axle.pos, Mth.floor(x), Mth.floor(z), hitchY, hitchY))
			|| Double.isNaN(ground(level, axle.pos, Mth.floor(x + sideX), Mth.floor(z + sideZ), hitchY, hitchY))
			|| Double.isNaN(ground(level, axle.pos, Mth.floor(x - sideX), Mth.floor(z - sideZ), hitchY, hitchY));
	}

	/**
	 * Rolls a wheel towards where it should be at (x, z); returns 1 if there is anything for it to roll on. In a wall it holds
	 * its height. It rolls on the ground as if each rise and drop along the cart had a ramp on it, so it starts up a step
	 * ahead of it, is at the top by the lip, and comes down the far side of a drop the same way.
	 */
	private static void wheel(
		final Axle axle, final int wheel, final Level level, final double x, final double z, final double hitchY, final double alongX, final double alongZ
	) {
		final double from = Double.isNaN(axle.wheel[wheel]) ? hitchY : axle.wheel[wheel];
		look(axle, wheel, level, x, z, from, hitchY, alongX, alongZ);
		final double under = axle.under[wheel];
		final boolean none = under == Double.NEGATIVE_INFINITY && axle.ramp == Double.NEGATIVE_INFINITY;
		final double to = none ? hitchY - 3.0 : Double.isNaN(under) ? under : Math.max(under, axle.ramp);
		if (Double.isNaN(axle.wheel[wheel])) {
			axle.wheel[wheel] = Double.isNaN(to) ? hitchY : to;
		} else if (!Double.isNaN(to)) {
			axle.wheel[wheel] += Mth.clamp((to - axle.wheel[wheel]) * CART_AXLE_EASE, -CART_AXLE_DROP, CART_AXLE_RISE);
		}
		// (Left behind, the horse having gone up or down far, it comes along.)
		axle.wheel[wheel] = Mth.clamp(axle.wheel[wheel], hitchY - 6.0, hitchY + 6.0);
	}

	/**
	 * Looks at the ground around (x, z), along the cart: what's right under it, its average over the stretch for this pace (into
	 * {@code surface}), and the ramp on it for a wheel there (into {@code ramp}).
	 */
	private static void look(
		final Axle axle, final int corner, final Level level, final double x, final double z, final double from, final double hitchY, final double alongX, final double alongZ
	) {
		final double under = ground(level, axle.pos, Mth.floor(x), Mth.floor(z), from, hitchY);
		axle.under[corner] = under;
		final double start = Double.isNaN(under) ? from : under == Double.NEGATIVE_INFINITY ? hitchY - 3.0 : under;
		final double stretch = Mth.clamp(axle.pace * CART_AVERAGE_PER_SPEED, CART_AVERAGE_LEAST, CART_AVERAGE_MOST);
		final double ahead = walk(axle, level, x, z, alongX, alongZ, from, hitchY, stretch, start);
		final double sum = axle.walked;
		axle.ramp = Math.max(ahead, walk(axle, level, x, z, -alongX, -alongZ, from, hitchY, stretch, start));
		axle.surface[corner] = (sum + axle.walked) / (2.0 * stretch);
	}

	/**
	 * Walks along the ground one way from (x, z), block by block. Returns the ramp on it there: how high the ground ahead holds a
	 * wheel at (x, z), lower by CART_RAMP_SLOPE for each block it is away (negative infinity if nothing does). Into {@code walked},
	 * the height of the ground added up over the first {@code stretch} blocks, starting at {@code start}.
	 */
	private static double walk(
		final Axle axle, final Level level, final double x, final double z, final double alongX, final double alongZ, final double from, final double hitchY,
		final double stretch, final double start
	) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		final int stepX = alongX > 0.0 ? 1 : -1;
		final int stepZ = alongZ > 0.0 ? 1 : -1;
		final double perX = alongX == 0.0 ? Double.MAX_VALUE : Math.abs(1.0 / alongX);
		final double perZ = alongZ == 0.0 ? Double.MAX_VALUE : Math.abs(1.0 / alongZ);
		double toX = alongX == 0.0 ? Double.MAX_VALUE : (alongX > 0.0 ? bx + 1.0 - x : x - bx) * perX;
		double toZ = alongZ == 0.0 ? Double.MAX_VALUE : (alongZ > 0.0 ? bz + 1.0 - z : z - bz) * perZ;
		double best = Double.NEGATIVE_INFINITY;
		double sum = 0.0;
		double at = 0.0;
		double height = start;
		final double furthest = Math.max(stretch, CART_RAMP_REACH);
		while (true) {
			final double away;
			if (toX < toZ) {
				away = toX;
				bx += stepX;
				toX += perX;
			} else {
				away = toZ;
				bz += stepZ;
				toZ += perZ;
			}
			final double end = Math.min(away, stretch);
			if (end > at) {
				sum += height * (end - at);
				at = end;
			}
			if (away >= furthest) {
				axle.walked = sum;
				return best;
			}
			final double top = ground(level, axle.pos, bx, bz, from, hitchY);
			if (top == Double.NEGATIVE_INFINITY) {
				height = hitchY - 3.0;
			} else if (!Double.isNaN(top)) {
				height = top;
				if (away < CART_RAMP_REACH) {
					best = Math.max(best, top - away * CART_RAMP_SLOPE);
				}
			}
		}
	}

	/**
	 * The top of the ground in a column, looked for from {@code from}, the height something there is at: up out of it if that's
	 * inside the ground, else down to 3 below the hitch (so an arch or a cave's roof overhead isn't taken for it). Negative
	 * infinity if there is none that far, NaN if it's over CART_WHEEL_STEP above that and the horse: a wall, not ground.
	 */
	private static double ground(final Level level, final BlockPos.MutableBlockPos pos, final int bx, final int bz, final double from, final double hitchY) {
		// (Resting on a block's top, or a hair under it, it is above that block: the one in question is the next.)
		int by = Mth.floor(from + GROUND_EPSILON);
		double top = solidTop(level, pos, bx, by, bz);
		if (top > from + GROUND_EPSILON) {
			while (true) {
				// (From the horse too: a wheel left behind must not take what the horse is already up on for a wall, and stay there.)
				if (top > Math.max(from, hitchY) + CART_WHEEL_STEP) {
					return Double.NaN;
				}
				final double above = solidTop(level, pos, bx, ++by, bz);
				if (Double.isNaN(above)) {
					return top;
				}
				top = above;
			}
		}
		final int bottom = Math.min(by, Mth.floor(hitchY - 3.0));
		while (Double.isNaN(top) && by > bottom) {
			top = solidTop(level, pos, bx, --by, bz);
		}
		return Double.isNaN(top) ? Double.NEGATIVE_INFINITY : top;
	}

	/** The top of a block something can rest on, NaN if it isn't one. Leaves aren't: the ride goes through them. */
	private static double solidTop(final Level level, final BlockPos.MutableBlockPos pos, final int bx, final int by, final int bz) {
		final BlockState state = level.getBlockState(pos.set(bx, by, bz));
		if (state.isAir() || state.is(BlockTags.LEAVES)) {
			return Double.NaN;
		}
		final VoxelShape shape = state.getCollisionShape(level, pos);
		return shape.isEmpty() ? Double.NaN : by + shape.max(Direction.Axis.Y);
	}
}
