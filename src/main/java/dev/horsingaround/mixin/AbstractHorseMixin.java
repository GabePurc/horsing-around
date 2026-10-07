package dev.horsingaround.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.horsingaround.HorsingAround;
import dev.horsingaround.RiderBridge;
import dev.horsingaround.ride.Footing;
import dev.horsingaround.ride.RideController;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.ride.RideTuning;
import dev.horsingaround.ride.Trample;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorse.class)
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
	private final boolean horsingaround$managed = !((Object) this instanceof Camel) && !((Object) this instanceof Llama);

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
		return this.horsingaround$managed;
	}

	@Override
	public @Nullable SoundEvent horsingaround$angrySound() {
		return this.getAngrySound();
	}

	/** First ridden hook each tick: advance the ride simulation and hand vanilla a forward/back-only input. */
	@Inject(method = "getRiddenInput", at = @At("HEAD"), cancellable = true)
	private void horsingaround$riddenInput(final Player controller, final Vec3 selfInput, final CallbackInfoReturnable<Vec3> cir) {
		if (!this.horsingaround$managed || !this.isLocalInstanceAuthoritative()) {
			return;
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
			cir.setReturnValue(Vec3.ZERO);
		} else {
			final float sidestep = s.sidestep();
			if (s.speed > MOVING && Math.abs(sidestep) > 0.01F) {
				// Strafe input is positive to the left.
				cir.setReturnValue(new Vec3(-sidestep, 0.0, 1.0));
			} else {
				cir.setReturnValue(s.speed > MOVING ? FORWARD : s.speed < -MOVING ? BACKWARD : Vec3.ZERO);
			}
		}
	}

	@ModifyReturnValue(method = "getRiddenSpeed", at = @At("RETURN"))
	private float horsingaround$riddenSpeed(final float attributeSpeed) {
		return this.horsingaround$managed && this.isLocalInstanceAuthoritative() ? attributeSpeed * Math.abs(this.horsingaround$ride.speed) : attributeSpeed;
	}

	/**
	 * The simulating side steers by the ride heading and keeps the head level instead of nodding with the camera.
	 * Everyone else keeps the rotation the rider's client reported, instead of vanilla's snap to the rider's camera.
	 */
	@Inject(method = "getRiddenRotation", at = @At("HEAD"), cancellable = true)
	private void horsingaround$riddenRotation(final LivingEntity controller, final CallbackInfoReturnable<Vec2> cir) {
		if (!this.horsingaround$managed || !(controller instanceof Player)) {
			return;
		}
		cir.setReturnValue(this.isLocalInstanceAuthoritative()
			? new Vec2(0.0F, this.horsingaround$ride.heading())
			: new Vec2(this.getXRot(), this.getYRot()));
	}

	/** The head and neck lead into a turn before the body follows. */
	@Inject(method = "tickRidden", at = @At("TAIL"))
	private void horsingaround$headLead(final Player controller, final Vec3 riddenInput, final CallbackInfo ci) {
		if (this.horsingaround$managed && this.isLocalInstanceAuthoritative()) {
			this.yHeadRot = this.getYRot() + this.horsingaround$ride.headLead();
		}
	}

	/**
	 * Leg animation speed tracks the gait instead of vanilla's distance x 4, which maxes out at a slow trot. Keeps
	 * cadence believable and lets Fresh Animations pick the matching walk, trot or gallop cycle.
	 */
	@Override
	protected void updateWalkAnimation(final float distance) {
		if (this.horsingaround$managed && this.getControllingPassenger() instanceof Player) {
			final float topSpeed = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * RideTuning.TERMINAL_VELOCITY_FACTOR;
			this.walkAnimation.update(Math.min(distance / topSpeed * RideTuning.ANIMATION_SPEED_FACTOR, 1.0F), 0.4F, this.isBaby() ? 3.0F : 1.0F);
		} else {
			super.updateWalkAnimation(distance);
		}
	}

	/**
	 * Takeoff carries the horse's ground speed into the air. On the ground a tick's travel is the stored velocity plus
	 * that tick's push, but only the stored part survives into the air, and vanilla then applies ground friction to
	 * the takeoff tick as well. So take off airborne, with the push folded into the velocity (less the air push travel
	 * adds this tick), plus a small extra forward kick instead of vanilla's fixed lunge.
	 */
	@Inject(method = "executeRidersJump", at = @At("HEAD"), cancellable = true)
	private void horsingaround$jump(final float amount, final Vec3 input, final CallbackInfo ci) {
		if (!this.horsingaround$managed) {
			return;
		}
		final Vec3 movement = this.getDeltaMovement();
		double x = movement.x;
		double z = movement.z;
		if (input.z > 0.0) {
			final float yaw = this.getYRot() * Mth.DEG_TO_RAD;
			final float push = this.getSpeed() * (1.0F - RideTuning.AIR_CONTROL) + RideTuning.JUMP_FORWARD_BOOST * amount;
			x -= Mth.sin(yaw) * push;
			z += Mth.cos(yaw) * push;
		}
		this.setDeltaMovement(x, Math.max(this.getJumpPower(amount), RideTuning.JUMP_MIN_VELOCITY), z);
		this.setOnGround(false);
		this.horsingaround$ride.jumpedOff = true;
		this.needsSync = true;
		ci.cancel();
	}

	/** Vanilla rears the horse when a jump starts. Just play the sound. */
	@Inject(method = "handleStartJump", at = @At("HEAD"), cancellable = true)
	private void horsingaround$startJump(final int jumpScale, final CallbackInfo ci) {
		if (this.horsingaround$managed) {
			this.playJumpSound();
			ci.cancel();
		}
	}

	/**
	 * A player-ridden horse handles water itself (ride controller): it keeps its footing while wading and floats
	 * when swimming, instead of vanilla's water physics, which stop it dead in any depth.
	 */
	@Override
	protected boolean shouldTravelInFluid(final FluidState fluidState) {
		if (this.horsingaround$managed && this.getControllingPassenger() instanceof Player && this.isInWater() && !this.isInLava()) {
			return false;
		}
		return super.shouldTravelInFluid(fluidState);
	}

	/**
	 * Footing: a ridden horse in the air (off a drop, or a jump a little short) moves as if it had a hoof down, so
	 * vanilla's step-up puts it onto anything within a step of its hooves that it clips, instead of the collision
	 * stopping it dead. The move itself works out the real ground contact again. Then {@link Footing} slips a shoulder
	 * caught on a corner past it, and records how much of the move a collision took.
	 */
	@Override
	public void move(final MoverType type, final Vec3 delta) {
		final RideState s = this.horsingaround$ride;
		if (type != MoverType.SELF || !s.narrow || !this.isLocalInstanceAuthoritative()) {
			super.move(type, delta);
			return;
		}
		// (Not while heaving out of the water: that rises smoothly onto the bank by itself.)
		if (!this.onGround() && !this.isInWater() && s.bankTicks == 0) {
			((EntityAccessor) (Object) this).horsingaround$setOnGroundFlag(true);
		}
		final double x = this.getX();
		final double z = this.getZ();
		super.move(type, delta);
		Footing.afterMove((AbstractHorse) (Object) this, s, delta, x, z);
	}

	/** Ridden, a full block is a step even from a path, farmland or mud, or onto snow. */
	@Override
	public float maxUpStep() {
		final float step = super.maxUpStep();
		return this.horsingaround$ride.narrow ? Math.max(step, RideTuning.RIDDEN_STEP_HEIGHT) : step;
	}

	/** Vanilla air control is half of what is needed to hold ground speed, so every jump bled momentum. */
	@Override
	protected float getFlyingSpeed() {
		return this.horsingaround$managed && this.getControllingPassenger() instanceof Player ? this.getSpeed() * RideTuning.AIR_CONTROL : super.getFlyingSpeed();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void horsingaround$tick(final CallbackInfo ci) {
		final RideState s = this.horsingaround$ride;
		final boolean narrow = this.horsingaround$managed && this.getControllingPassenger() instanceof Player;
		if (narrow != s.narrow) {
			s.narrow = narrow;
			this.refreshDimensions();
		}
		if (this.getControllingPassenger() == null) {
			RideController.tickUnridden(s);
		}
		if (this.level().isClientSide()) {
			RideController.tickVisual((AbstractHorse) (Object) this, s);
		} else if (this.horsingaround$managed && this.level() instanceof ServerLevel level) {
			Trample.tick((AbstractHorse) (Object) this, s, level);
		}
	}

	/**
	 * Vanilla plays the gallop clip after five steps of riding regardless of speed. Pick the clip from actual ground
	 * speed instead, which every side can see without syncing gait.
	 */
	@Inject(method = "playStepSound", at = @At("HEAD"))
	private void horsingaround$gaitSound(final BlockPos pos, final BlockState blockState, final CallbackInfo ci) {
		if (!this.horsingaround$managed || !this.canGallop || !this.isVehicle()) {
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
