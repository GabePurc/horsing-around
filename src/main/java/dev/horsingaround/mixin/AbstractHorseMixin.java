package dev.horsingaround.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.horsingaround.HorsingAround;
import dev.horsingaround.RiderBridge;
import dev.horsingaround.ride.Mounts;
import dev.horsingaround.ride.RideController;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.ride.RideTuning;
import dev.horsingaround.ride.Trample;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The ride itself. Every hook leaves vanilla (and other mods' hooks) running for mounts this mod doesn't manage, and
 * changes results rather than cancelling methods where it can. The physics hooks that live on the classes that declare
 * those methods are in the entity and living entity mixins. Applied after other mods' horse hooks (priority), so on the
 * horses this mod manages its riding wins over theirs (another mod's free camera turning the horse, say), while their
 * other features keep working.
 */
@Mixin(value = AbstractHorse.class, priority = 1500)
public abstract class AbstractHorseMixin extends Animal implements RideStateHolder {
	@Unique
	private static final Vec3 FORWARD = new Vec3(0.0, 0.0, 1.0);
	@Unique
	private static final Vec3 BACKWARD = new Vec3(0.0, 0.0, -1.0);
	@Unique
	private static final float MOVING = 1.0E-3F;

	@Shadow
	protected boolean canGallop;
	@Shadow
	protected int gallopSoundCounter;
	@Shadow
	protected float playerJumpPendingScale;
	@Shadow
	protected boolean allowStandSliding;

	@Unique
	private final RideState horsingaround$ride = new RideState();
	@Unique
	private boolean horsingaround$managed;
	@Unique
	private int horsingaround$managedGeneration = -1;

	protected AbstractHorseMixin(final EntityType<? extends Animal> type, final Level level) {
		super(type, level);
	}

	@Shadow
	public abstract boolean isStanding();

	@Shadow
	protected abstract void playJumpSound();

	@Shadow
	protected abstract @Nullable SoundEvent getAngrySound();

	@Override
	public RideState horsingaround$ride() {
		return this.horsingaround$ride;
	}

	@Override
	public boolean horsingaround$managed() {
		final int generation = Mounts.generation();
		if (this.horsingaround$managedGeneration != generation) {
			this.horsingaround$managedGeneration = generation;
			this.horsingaround$managed = Mounts.manages(this);
		}
		return this.horsingaround$managed;
	}

	@Override
	public boolean horsingaround$playerRidden() {
		return this.horsingaround$managed() && this.getControllingPassenger() instanceof Player;
	}

	@Override
	public @Nullable SoundEvent horsingaround$angrySound() {
		return this.getAngrySound();
	}

	/** First ridden hook each tick: advance the ride simulation and hand vanilla a forward/back-only input. */
	@ModifyReturnValue(method = "getRiddenInput", at = @At("RETURN"))
	private Vec3 horsingaround$riddenInput(final Vec3 vanilla, @Local(argsOnly = true) final Player controller) {
		if (!this.horsingaround$managed() || !this.isLocalInstanceAuthoritative()) {
			return vanilla;
		}
		final RideState s = this.horsingaround$ride;
		final RiderBridge bridge = HorsingAround.bridge;
		RideController.tick(
			(AbstractHorse) (Object) this, s, controller, bridge.input(controller), bridge.spurPresses(), bridge.reinPresses(), bridge.jumpPresses()
		);
		if (s.pendingJump > 0.0F) {
			// Vanilla's tickRidden launches the jump later this same tick.
			this.playerJumpPendingScale = s.pendingJump;
			this.allowStandSliding = true;
			bridge.onJump(controller, s.pendingJump);
			s.pendingJump = 0.0F;
		}
		if (this.onGround() && this.playerJumpPendingScale == 0.0F && this.isStanding() && !this.allowStandSliding) {
			return Vec3.ZERO;
		}
		final float sidestep = s.sidestep();
		if (s.speed > MOVING && Math.abs(sidestep) > 0.01F) {
			// Strafe input is positive to the left.
			return new Vec3(-sidestep, 0.0, 1.0);
		}
		return s.speed > MOVING ? FORWARD : s.speed < -MOVING ? BACKWARD : Vec3.ZERO;
	}

	@ModifyReturnValue(method = "getRiddenSpeed", at = @At("RETURN"))
	private float horsingaround$riddenSpeed(final float attributeSpeed) {
		return this.horsingaround$managed() && this.isLocalInstanceAuthoritative() ? attributeSpeed * Math.abs(this.horsingaround$ride.speed) : attributeSpeed;
	}

	/**
	 * The simulating side steers by the ride heading and keeps the head level instead of nodding with the camera.
	 * Everyone else keeps the rotation the rider's client reported, instead of vanilla's snap to the rider's camera.
	 * Wraps the whole method so that, on the horses this mod rides, other mods that turn the horse themselves (a free
	 * camera that slowly swings the horse round, say) don't fight the steering; every other mount is untouched.
	 */
	@WrapMethod(method = "getRiddenRotation")
	private Vec2 horsingaround$riddenRotation(final LivingEntity controller, final Operation<Vec2> original) {
		if (!(controller instanceof Player) || !this.horsingaround$managed()) {
			return original.call(controller);
		}
		return this.isLocalInstanceAuthoritative() ? new Vec2(0.0F, this.horsingaround$ride.heading()) : new Vec2(this.getXRot(), this.getYRot());
	}

	/** The head and neck lead into a turn before the body follows. */
	@Inject(method = "tickRidden", at = @At("TAIL"))
	private void horsingaround$headLead(final Player controller, final Vec3 riddenInput, final CallbackInfo ci) {
		if (this.horsingaround$managed() && this.isLocalInstanceAuthoritative()) {
			this.yHeadRot = this.getYRot() + this.horsingaround$ride.headLead();
		}
	}

	/**
	 * Takeoff carries the horse's ground speed into the air. On the ground a tick's travel is the stored velocity plus
	 * that tick's push, but only the stored part survives into the air, and vanilla then applies ground friction to
	 * the takeoff tick as well. So take off airborne, with the push folded into the velocity (less the air push travel
	 * adds this tick), plus a small extra forward kick instead of vanilla's fixed lunge.
	 */
	@WrapMethod(method = "executeRidersJump")
	private void horsingaround$jump(final float amount, final Vec3 input, final Operation<Void> original) {
		if (!this.horsingaround$managed()) {
			original.call(amount, input);
			return;
		}
		final Vec3 movement = this.getDeltaMovement();
		double x = movement.x;
		double z = movement.z;
		final RideState s = this.horsingaround$ride;
		if (s.hurdleForward > 0.0F) {
			// Over a hurdle: as fast as clears it (the push of this tick's move comes on top).
			final float yaw = s.hurdleYaw * Mth.DEG_TO_RAD;
			final float forward = Math.max(s.hurdleForward - this.getSpeed() * RideTuning.AIR_CONTROL, 0.0F);
			x = -Mth.sin(yaw) * forward;
			z = Mth.cos(yaw) * forward;
		} else if (input.z > 0.0) {
			final float yaw = this.getYRot() * Mth.DEG_TO_RAD;
			final float push = this.getSpeed() * (1.0F - RideTuning.AIR_CONTROL) + RideTuning.JUMP_FORWARD_BOOST * amount;
			x -= Mth.sin(yaw) * push;
			z += Mth.cos(yaw) * push;
		}
		this.setDeltaMovement(x, Math.max(Math.max(this.getJumpPower(amount), RideTuning.JUMP_MIN_VELOCITY), s.jumpLift), z);
		s.jumpLift = 0.0F;
		this.setOnGround(false);
		this.horsingaround$ride.jumpedOff = true;
		this.needsSync = true;
	}

	/** Vanilla rears the horse when a jump starts. Just play the sound. */
	@WrapMethod(method = "handleStartJump")
	private void horsingaround$startJump(final int jumpScale, final Operation<Void> original) {
		if (this.horsingaround$managed()) {
			this.playJumpSound();
		} else {
			original.call(jumpScale);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void horsingaround$tick(final CallbackInfo ci) {
		final RideState s = this.horsingaround$ride;
		final boolean managed = this.horsingaround$managed();
		final boolean narrow = managed && this.getControllingPassenger() instanceof Player;
		if (narrow != s.narrow) {
			s.narrow = narrow;
			this.refreshDimensions();
		}
		if (!managed) {
			return;
		}
		if (this.getControllingPassenger() == null) {
			RideController.tickUnridden(s);
		}
		if (this.level().isClientSide()) {
			RideController.tickVisual((AbstractHorse) (Object) this, s);
		} else if (this.level() instanceof ServerLevel level) {
			Trample.tick((AbstractHorse) (Object) this, s, level);
		}
	}

	/**
	 * Vanilla plays the gallop clip after five steps of riding regardless of speed. Pick the clip from actual ground
	 * speed instead, which every side can see without syncing gait.
	 */
	@Inject(method = "playStepSound", at = @At("HEAD"))
	private void horsingaround$gaitSound(final BlockPos pos, final BlockState blockState, final CallbackInfo ci) {
		if (!this.canGallop || !this.isVehicle() || !this.horsingaround$managed()) {
			return;
		}
		final double dx = this.getX() - this.xo;
		final double dz = this.getZ() - this.zo;
		final double gallopSpeed = this.getAttributeValue(Attributes.MOVEMENT_SPEED) * (RideTuning.TERMINAL_VELOCITY_FACTOR * RideTuning.GALLOP_SOUND_SPEED);
		if (dx * dx + dz * dz < gallopSpeed * gallopSpeed) {
			this.gallopSoundCounter = 0;
		} else if (this.gallopSoundCounter < 5) {
			this.gallopSoundCounter = 5;
		}
	}
}
