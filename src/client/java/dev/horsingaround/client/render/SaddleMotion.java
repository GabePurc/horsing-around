package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import dev.horsingaround.ride.RideState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How the saddle is moving this frame relative to the horse's resting pose. When the horse's model animates its own
 * body (Fresh Animations through Entity Model Features, or Icy's Better Horses' models), this is measured from the
 * animated model so the rider rides the real motion. Otherwise it is generated here from the leg cycle and applied to
 * the horse as well, so both move together. Reused per caller; render thread only.
 */
public final class SaddleMotion {
	/** Frames drawn (counted as each frame's camera is placed): motion measured in an earlier frame than the last is stale. */
	private static int frame;
	/** Vanilla horse legs cycle once per 2pi of (limb swing x 0.6662). */
	private static final float VANILLA_LEG_PHASE = 0.6662F;
	private static final float VANILLA_WATER_LEG_PACE = 0.2F;
	/** The measured rest pose's filter time constant, seconds. */
	private static final float BASELINE_SECONDS = 0.8F;
	private static final Quaternionf ROTATION = new Quaternionf();
	private static final Vector3f CURRENT = new Vector3f();
	private static final Vector3f REST = new Vector3f();

	/** Saddle height change, blocks. */
	public float lift;
	/** Saddle shift along the horse's heading, blocks. */
	public float forward;
	/** Saddle shift to the horse's right, blocks (the side-to-side sway of a walk or trot). */
	public float side;
	/** Saddle pitch, degrees, nose up positive. */
	public float pitch;
	/** Saddle roll, degrees, right positive. */
	public float roll;
	/** Generated here, so the horse model must be moved by it too. */
	public boolean synthetic;
	/** Leg-animation speed, 0..1. */
	public float limbSpeed;
	/** 0..1 share of the gallop (vs canter) cycle. */
	public float gallop;
	/** 0..1 how much of the motion is a canter or gallop. */
	public float run;
	/** 0..1 how much the horse is moving at all. */
	public float moving;

	/** Once a frame, before anything is drawn. */
	public static void nextFrame() {
		frame++;
	}

	public static int frame() {
		return frame;
	}

	public SaddleMotion compute(final LivingEntity horse, final RideState s, final float partialTicks) {
		final float limbSpeed = horse.walkAnimation.speed(partialTicks);
		final float run = Mth.clamp((limbSpeed - RUN_ANIMATION_SPEED + 0.05F) * 10.0F, 0.0F, 1.0F);
		final float gallop = run * Mth.clamp((limbSpeed - GALLOP_ANIMATION_SPEED + 0.03F) * 20.0F, 0.0F, 1.0F);
		this.limbSpeed = limbSpeed;
		this.gallop = gallop;
		this.run = run;
		this.moving = Math.min(limbSpeed * 5.0F, 1.0F);

		// (Measured this frame or the last: by frames, not time, so a hitch or a low frame rate doesn't make a horse a pack
		// animates look unanimated, and get the vanilla model's motion on top of the pack's.)
		if (frame - s.animatedFrame <= 1) {
			this.synthetic = false;
			this.lift = s.animatedLift - s.animatedLiftBase;
			this.forward = s.animatedForward - s.animatedForwardBase;
			this.side = s.animatedSide - s.animatedSideBase;
			this.pitch = s.animatedPitch - s.animatedPitchBase;
			this.roll = s.animatedRoll - s.animatedRollBase;
			return this;
		}

		this.synthetic = true;
		final float canter = run - gallop;
		final float trot = Mth.clamp((limbSpeed - TROT_ANIMATION_SPEED + 0.02F) * 10.0F, 0.0F, 1.0F) * (1.0F - run);
		final float walk = (1.0F - trot - run) * this.moving;
		// (In water vanilla's legs swing at a fifth of the pace; the body keeps time with them.)
		final float phase = horse.walkAnimation.position(partialTicks) * VANILLA_LEG_PHASE * (horse.isInWater() ? VANILLA_WATER_LEG_PACE : 1.0F);
		final float twice = Mth.cos(phase * 2.0F);
		final float once = Mth.cos(phase - Mth.PI / 3.0F);
		final float stance = horse.onGround() ? 1.0F : 0.0F;
		this.lift = stance * ((SADDLE_LIFT_WALK * walk + SADDLE_LIFT_TROT * trot) * twice
			+ (SADDLE_LIFT_CANTER * canter + SADDLE_LIFT_GALLOP * gallop) * once);
		this.pitch = stance * ((SADDLE_PITCH_WALK * walk + SADDLE_PITCH_TROT * trot) * Mth.sin(phase * 2.0F)
			+ (SADDLE_PITCH_CANTER * canter + SADDLE_PITCH_GALLOP * gallop) * Mth.sin(phase));
		this.forward = 0.0F;
		this.side = 0.0F;
		this.roll = 0.0F;
		return this;
	}

	/**
	 * Records how an animated model moved the saddle this frame, from its body part after the animation ran: then no
	 * motion is generated for the horse, and the rider rides this one. The saddle sits at (saddleX, saddleY, saddleZ),
	 * model pixels in the body's parent space at rest.
	 */
	public static void measure(final RideState s, final ModelPart body, final float saddleX, final float saddleY, final float saddleZ) {
		final PartPose initial = body.getInitialPose();
		final float lx = saddleX - initial.x();
		final float ly = saddleY - initial.y();
		final float lz = saddleZ - initial.z();
		ROTATION.rotationZYX(body.zRot, body.yRot, body.xRot).transform(CURRENT.set(lx, ly, lz)).add(body.x, body.y, body.z);
		ROTATION.rotationZYX(initial.zRot(), initial.yRot(), initial.xRot()).transform(REST.set(lx, ly, lz)).add(initial.x(), initial.y(), initial.z());

		// Model space is Y-down with the head toward -Z, in pixels; the horse's left is +X.
		final long now = System.nanoTime();
		final float lift = -(CURRENT.y - REST.y) / 16.0F;
		final float forward = -(CURRENT.z - REST.z) / 16.0F;
		final float side = -(CURRENT.x - REST.x) / 16.0F;
		final float pitch = -(body.xRot - initial.xRot()) * Mth.RAD_TO_DEG;
		final float roll = -(body.zRot - initial.zRot()) * Mth.RAD_TO_DEG;
		if (now - s.animatedAt > 1_000_000_000L) {
			s.animatedLiftBase = lift;
			s.animatedForwardBase = forward;
			s.animatedSideBase = side;
			s.animatedPitchBase = pitch;
			s.animatedRollBase = roll;
		} else {
			final float k = 1.0F - (float) Math.exp(-(now - s.animatedAt) * 1.0E-9 / BASELINE_SECONDS);
			s.animatedLiftBase += (lift - s.animatedLiftBase) * k;
			s.animatedForwardBase += (forward - s.animatedForwardBase) * k;
			s.animatedSideBase += (side - s.animatedSideBase) * k;
			s.animatedPitchBase += (pitch - s.animatedPitchBase) * k;
			s.animatedRollBase += (roll - s.animatedRollBase) * k;
		}
		s.animatedLift = lift;
		s.animatedForward = forward;
		s.animatedSide = side;
		s.animatedPitch = pitch;
		s.animatedRoll = roll;
		s.animatedAt = now;
		s.animatedFrame = frame;
	}
}
