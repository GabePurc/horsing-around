package dev.horsingaround.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.horsingaround.client.render.RidePoseState;
import dev.horsingaround.ride.RideState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements RidePoseState {
	/** Scratch rotation; entity submission happens on the render thread only. */
	@Unique
	private static final Quaternionf ROTATION = new Quaternionf();

	@Unique
	private boolean horsingaround$posed;
	@Unique
	private boolean horsingaround$rider;
	@Unique
	private float horsingaround$handBob;
	@Unique
	private float horsingaround$legPitch;
	@Unique
	private float horsingaround$legRoll;
	@Unique
	private float horsingaround$neck;
	@Unique
	private boolean horsingaround$legsHere;
	@Unique
	private float horsingaround$legTilt;
	@Unique
	private @Nullable RideState horsingaround$ride;
	@Unique
	private float horsingaround$foreLeg;
	@Unique
	private float horsingaround$hindLeg;
	@Unique
	private float horsingaround$tail;
	@Unique
	private float horsingaround$airLegs;
	@Unique
	private float horsingaround$airRise;
	@Unique
	private float horsingaround$pelvis;
	@Unique
	private float horsingaround$twist;
	@Unique
	private float horsingaround$shield;
	@Unique
	private float horsingaround$bowRelease;
	@Unique
	private float horsingaround$bowPull;
	@Unique
	private float horsingaround$shakeYaw;
	@Unique
	private float horsingaround$shakeRoll;
	@Unique
	private float horsingaround$tx;
	@Unique
	private float horsingaround$ty;
	@Unique
	private float horsingaround$tz;
	@Unique
	private float horsingaround$qx;
	@Unique
	private float horsingaround$qy;
	@Unique
	private float horsingaround$qz;
	@Unique
	private float horsingaround$qw;
	@Unique
	private float horsingaround$px;
	@Unique
	private float horsingaround$py;
	@Unique
	private float horsingaround$pz;

	@Override
	public void horsingaround$clearPose() {
		this.horsingaround$posed = false;
		this.horsingaround$rider = false;
	}

	@Override
	public void horsingaround$setRider(
		final float handBob, final float legPitch, final float legRoll, final float twistRadians, final float pelvis, final float shield,
		final float bowRelease, final float bowPull
	) {
		this.horsingaround$pelvis = pelvis;
		this.horsingaround$shield = shield;
		this.horsingaround$bowRelease = bowRelease;
		this.horsingaround$bowPull = bowPull;
		this.horsingaround$rider = true;
		this.horsingaround$handBob = handBob;
		this.horsingaround$legPitch = legPitch;
		this.horsingaround$legRoll = legRoll;
		this.horsingaround$twist = twistRadians;
	}

	@Override
	public boolean horsingaround$isRider() {
		return this.horsingaround$rider;
	}

	@Override
	public float horsingaround$handBob() {
		return this.horsingaround$handBob;
	}

	@Override
	public float horsingaround$legPitch() {
		return this.horsingaround$legPitch;
	}

	@Override
	public float horsingaround$legRoll() {
		return this.horsingaround$legRoll;
	}

	@Override
	public void horsingaround$setNeck(final float pitch) {
		this.horsingaround$neck = pitch;
	}

	@Override
	public float horsingaround$neckPitch() {
		return this.horsingaround$neck;
	}

	@Override
	public void horsingaround$setLegs(final boolean here, final float tilt, final float fore, final float hind) {
		this.horsingaround$legsHere = here;
		this.horsingaround$legTilt = tilt;
		this.horsingaround$foreLeg = fore;
		this.horsingaround$hindLeg = hind;
	}

	@Override
	public boolean horsingaround$legsHere() {
		return this.horsingaround$legsHere;
	}

	@Override
	public float horsingaround$legTilt() {
		return this.horsingaround$legTilt;
	}

	@Override
	public void horsingaround$setRide(final @Nullable RideState ride) {
		this.horsingaround$ride = ride;
	}

	@Override
	public @Nullable RideState horsingaround$rideState() {
		return this.horsingaround$ride;
	}

	@Override
	public float horsingaround$foreLeg() {
		return this.horsingaround$foreLeg;
	}

	@Override
	public float horsingaround$hindLeg() {
		return this.horsingaround$hindLeg;
	}

	@Override
	public float horsingaround$shield() {
		return this.horsingaround$shield;
	}

	@Override
	public float horsingaround$bowRelease() {
		return this.horsingaround$bowRelease;
	}

	@Override
	public float horsingaround$bowPull() {
		return this.horsingaround$bowPull;
	}

	@Override
	public void horsingaround$setTail(final float lift) {
		this.horsingaround$tail = lift;
	}

	@Override
	public float horsingaround$tail() {
		return this.horsingaround$tail;
	}

	@Override
	public void horsingaround$setAirLegs(final float air, final float rise) {
		this.horsingaround$airLegs = air;
		this.horsingaround$airRise = rise;
	}

	@Override
	public float horsingaround$airLegs() {
		return this.horsingaround$airLegs;
	}

	@Override
	public float horsingaround$airRise() {
		return this.horsingaround$airRise;
	}

	@Override
	public float horsingaround$pelvis() {
		return this.horsingaround$pelvis;
	}

	@Override
	public float horsingaround$twist() {
		return this.horsingaround$twist;
	}

	@Override
	public void horsingaround$setHeadShake(final float yaw, final float roll) {
		this.horsingaround$shakeYaw = yaw;
		this.horsingaround$shakeRoll = roll;
	}

	@Override
	public float horsingaround$headShakeYaw() {
		return this.horsingaround$shakeYaw;
	}

	@Override
	public float horsingaround$headShakeRoll() {
		return this.horsingaround$shakeRoll;
	}

	@Override
	public void horsingaround$setPose(
		final float tx, final float ty, final float tz, final float qx, final float qy, final float qz, final float qw, final float px, final float py, final float pz
	) {
		this.horsingaround$posed = true;
		this.horsingaround$tx = tx;
		this.horsingaround$ty = ty;
		this.horsingaround$tz = tz;
		this.horsingaround$qx = qx;
		this.horsingaround$qy = qy;
		this.horsingaround$qz = qz;
		this.horsingaround$qw = qw;
		this.horsingaround$px = px;
		this.horsingaround$py = py;
		this.horsingaround$pz = pz;
	}

	@Override
	public void horsingaround$applyPose(final PoseStack poseStack) {
		if (this.horsingaround$posed) {
			poseStack.translate(this.horsingaround$tx, this.horsingaround$ty, this.horsingaround$tz);
			poseStack.rotateAround(
				ROTATION.set(this.horsingaround$qx, this.horsingaround$qy, this.horsingaround$qz, this.horsingaround$qw),
				this.horsingaround$px,
				this.horsingaround$py,
				this.horsingaround$pz
			);
		}
	}
}
