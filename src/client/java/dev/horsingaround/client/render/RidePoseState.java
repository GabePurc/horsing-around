package dev.horsingaround.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Implemented on {@code LivingEntityRenderState} by mixin: an extra world-space translate + rotate about a pivot, and
 * for riders, the limb pose inputs (reins, stirrups, torso twist).
 */
public interface RidePoseState {
	void horsingaround$clearPose();

	void horsingaround$setPose(float tx, float ty, float tz, float qx, float qy, float qz, float qw, float px, float py, float pz);

	void horsingaround$applyPose(PoseStack poseStack);

	/**
	 * @param legPitch radians to tip the legs (top-back positive) so they hold still against the saddle while the
	 *                 torso rocks, leans and pumps
	 * @param legRoll  same for roll (top-right positive)
	 */
	void horsingaround$setRider(float handBob, float legPitch, float legRoll, float twistRadians, float pelvis);

	boolean horsingaround$isRider();

	float horsingaround$handBob();

	float horsingaround$legPitch();

	float horsingaround$legRoll();

	float horsingaround$twist();

	/** Torso swing from the shoulders (radians, negative slides the pelvis forward). */
	float horsingaround$pelvis();

	/** Head toss for a horse that just ran out of stamina: neck yaw and roll, radians. */
	void horsingaround$setHeadShake(float yaw, float roll);

	float horsingaround$headShakeYaw();

	float horsingaround$headShakeRoll();

	/** Neck pitch (radians, nose down positive) countering the body's slope pitch; vanilla model only. */
	void horsingaround$setNeck(float pitch);

	float horsingaround$neckPitch();
}
