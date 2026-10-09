package dev.horsingaround.client.render;

import static dev.horsingaround.ride.RideTuning.*;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Ease;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwingAnimationType;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Rider pose on a horse: feet in the stirrups, hands on the reins bobbing with the saddle, and the torso turning
 * toward where the rider looks. A hand holding a tool or weapon comes off the reins and holds it a little out to the
 * side, ready; a swing starts from wherever the arm is held and comes back to it. Drawing a bow, the rider turns side-on
 * to the aim with the bow arm out along it, and the string hand comes back from the bow to the cheek as the draw charges
 * and flies back past it on release; a loaded crossbow is held square to the aim. A hand also leaves the reins to use an
 * item or hold another pose, and comes up in front of the face, head tucked, when pushing through leaves. Applied last in
 * the model's setup (for players after the player model and mods that pose riders on any horse, such as Not Enough
 * Animations). Left-handed riders get all of it mirrored.
 */
public final class RiderPose {
	/** Shoulder pivot to hip joint, model pixels. */
	private static final float HIP_DEPTH = 12.0F;
	/** Shoulder pivot to the grip of what the hand holds, along the arm, model pixels. */
	private static final float ARM_REACH = 10.0F;
	/** Shoulder pivot to the side of the body's middle, model pixels (scaled by age), as vanilla carries the shoulders round. */
	private static final float SHOULDER = 5.0F;
	/** Scratch; render thread only. */
	private static final Vector3f HAND = new Vector3f();
	private static final Vector3f ANCHOR = new Vector3f();

	private RiderPose() {
	}

	public static void apply(final HumanoidModel<?> model, final HumanoidRenderState state) {
		if (!state.isPassenger || !(state instanceof RidePoseState rider) || !rider.horsingaround$isRider()) {
			return;
		}
		final float legPitch = rider.horsingaround$legPitch();
		final float legRoll = rider.horsingaround$legRoll();
		model.rightLeg.xRot = -STIRRUP_LEG_FORWARD + legPitch;
		model.rightLeg.yRot = STIRRUP_TOE_OUT;
		model.rightLeg.zRot = STIRRUP_LEG_SPLAY + legRoll;
		model.leftLeg.xRot = -STIRRUP_LEG_FORWARD + legPitch;
		model.leftLeg.yRot = -STIRRUP_TOE_OUT;
		model.leftLeg.zRot = -STIRRUP_LEG_SPLAY + legRoll;

		// Pelvis slide: swing the torso from the shoulders (its pivot) and carry the hips, where the legs attach. The torso
		// is otherwise upright in the saddle (the whole-body lean is applied to the rider model as a whole).
		final float pelvis = rider.horsingaround$pelvis();
		model.body.xRot = pelvis;
		if (pelvis != 0.0F) {
			final float hipDrop = HIP_DEPTH * (Mth.cos(pelvis) - 1.0F);
			final float hipForward = HIP_DEPTH * Mth.sin(pelvis);
			model.rightLeg.y += hipDrop;
			model.rightLeg.z += hipForward;
			model.leftLeg.y += hipDrop;
			model.leftLeg.z += hipForward;
		}

		final float twist = rider.horsingaround$twist();
		final float handBob = rider.horsingaround$handBob();
		final boolean swinging = state.swingAnimation > 0.0F && state.currentSwing != null;
		final HumanoidArm swingArm = swinging ? state.currentSwing.hand().asArm(state.mainArm) : null;
		final SwingAnimationType swingType = swinging ? state.currentSwing.animation().type() : SwingAnimationType.NONE;
		final HumanoidArm usingArm = state.isUsingItem ? state.useItemHand.asArm(state.mainArm) : null;

		// A bow drawn, or just loosed: both hands on it.
		HumanoidArm bowArm = state.rightArmPose == HumanoidModel.ArmPose.BOW_AND_ARROW ? HumanoidArm.RIGHT
			: state.leftArmPose == HumanoidModel.ArmPose.BOW_AND_ARROW ? HumanoidArm.LEFT : null;
		final float release = bowArm == null ? rider.horsingaround$bowRelease() : 0.0F;
		if (release > 0.0F) {
			bowArm = bowHeld(state);
		}
		if (bowArm != null) {
			bow(model, state, rider, bowArm, twist, handBob, release);
			return;
		}
		if (state.rightArmPose.isTwoHanded() || state.leftArmPose.isTwoHanded()) {
			crossbow(model, state, twist);
			return;
		}

		// A spear's thrust is vanilla's own, torso and all; any other swing (a whack) is played on top of where the arm rests.
		final boolean stab = swingType == SwingAnimationType.STAB;
		final boolean whack = swingType == SwingAnimationType.WHACK;
		if (!stab) {
			final float swingYaw = whack ? swingYaw(state, swingArm) : 0.0F;
			shoulders(model, state, twist + swingYaw);
			model.rightArm.yRot += twist;
			model.leftArm.yRot += twist;
			arm(model, state, HumanoidArm.RIGHT, usingArm, whack && swingArm == HumanoidArm.RIGHT, swingYaw, twist, handBob);
			arm(model, state, HumanoidArm.LEFT, usingArm, whack && swingArm == HumanoidArm.LEFT, swingYaw, twist, handBob);
		} else {
			if (swingArm != HumanoidArm.RIGHT) {
				arm(model, state, HumanoidArm.RIGHT, usingArm, false, 0.0F, twist, handBob);
			}
			if (swingArm != HumanoidArm.LEFT) {
				arm(model, state, HumanoidArm.LEFT, usingArm, false, 0.0F, twist, handBob);
			}
		}

		// Leaves: the off hand (or the other, if that one is busy) comes up in front of the face, and the head tucks down
		// and turns a little away.
		final float shield = rider.horsingaround$shield();
		if (shield > 0.0F) {
			HumanoidArm arm = state.mainArm.getOpposite();
			if (arm == swingArm || arm == usingArm) {
				arm = arm.getOpposite();
			}
			if (arm != swingArm && arm != usingArm) {
				final boolean left = arm == HumanoidArm.LEFT;
				final ModelPart part = left ? model.leftArm : model.rightArm;
				final float inward = left ? SHIELD_ARM_INWARD : -SHIELD_ARM_INWARD;
				part.xRot = Mth.lerp(shield, part.xRot, -SHIELD_ARM_RAISE + handBob * 0.5F);
				part.yRot = Mth.lerp(shield, part.yRot, inward + twist);
				part.zRot = Mth.lerp(shield, part.zRot, 0.0F);
				model.head.xRot += SHIELD_HEAD_TUCK * shield;
				model.head.yRot += (left ? -SHIELD_HEAD_TURN : SHIELD_HEAD_TURN) * shield;
			}
		}
	}

	/**
	 * One arm where it rests: on the reins, or holding a tool or weapon out ready; with a whack on top if it is swinging.
	 * An arm using an item or in another of vanilla's poses (blocking, a spyglass, a horn...) keeps it.
	 */
	private static void arm(
		final HumanoidModel<?> model, final HumanoidRenderState state, final HumanoidArm side, final @Nullable HumanoidArm usingArm, final boolean whack,
		final float swingYaw, final float twist, final float handBob
	) {
		if (side == usingArm || !rest(model, state, side, twist, handBob)) {
			return;
		}
		if (whack) {
			// Vanilla's swing, played from where the arm rests, so it starts and ends there.
			final ModelPart part = side == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
			final float swing = state.swingAnimation;
			final float lift = Mth.sin(Ease.outQuart(swing) * Mth.PI);
			final float reach = Mth.sin(swing * Mth.PI) * -(model.head.xRot - 0.7F) * 0.75F;
			part.xRot -= lift * 1.2F + reach;
			part.yRot += swingYaw * 2.0F;
			part.zRot += Mth.sin(swing * Mth.PI) * (side == HumanoidArm.LEFT ? 0.4F : -0.4F);
		}
	}

	/**
	 * Puts an arm where it rests, if it rests on the reins (empty, or holding something that isn't a tool or weapon) or
	 * holds a tool or weapon out ready (in the main hand); false for any other pose.
	 */
	private static boolean rest(final HumanoidModel<?> model, final HumanoidRenderState state, final HumanoidArm side, final float twist, final float handBob) {
		final HumanoidModel.ArmPose pose = state.getArmPose(side);
		if (pose != HumanoidModel.ArmPose.EMPTY && pose != HumanoidModel.ArmPose.ITEM && pose != HumanoidModel.ArmPose.BOW_AND_ARROW) {
			return false;
		}
		final boolean left = side == HumanoidArm.LEFT;
		final ModelPart part = left ? model.leftArm : model.rightArm;
		if (side == state.mainArm && pose != HumanoidModel.ArmPose.EMPTY && weapon(state.getUseItemStackForArm(side))) {
			part.xRot = -READY_ARM_FORWARD + handBob;
			part.yRot = (left ? -READY_ARM_YAW : READY_ARM_YAW) + twist;
			part.zRot = left ? -READY_ARM_OUT : READY_ARM_OUT;
		} else {
			part.xRot = -REINS_ARM_FORWARD + handBob;
			part.yRot = (left ? REINS_ARM_INWARD : -REINS_ARM_INWARD) + twist * 0.5F;
			part.zRot = 0.0F;
		}
		return true;
	}

	/** A tool or a weapon (a sword, an axe, a pickaxe, a mace, a trident, a spear, a bow...), held out ready off the reins. */
	private static boolean weapon(final ItemStack stack) {
		return stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON) || stack.getItem() instanceof ProjectileWeaponItem;
	}

	/** The torso's turn in a whack, as vanilla swings it (mirrored for the left arm). */
	private static float swingYaw(final HumanoidRenderState state, final @Nullable HumanoidArm swingArm) {
		final float yaw = Mth.sin(Mth.sqrt(state.swingAnimation) * Mth.TWO_PI) * 0.2F;
		return swingArm == HumanoidArm.LEFT ? -yaw : yaw;
	}

	/** Turns the torso {@code yaw} radians (right positive) and carries the shoulders round with it, as vanilla does in a swing. */
	private static void shoulders(final HumanoidModel<?> model, final HumanoidRenderState state, final float yaw) {
		final float shoulder = SHOULDER * state.ageScale;
		model.body.yRot = yaw;
		model.rightArm.x = -Mth.cos(yaw) * shoulder;
		model.rightArm.z = Mth.sin(yaw) * shoulder;
		model.leftArm.x = Mth.cos(yaw) * shoulder;
		model.leftArm.z = -Mth.sin(yaw) * shoulder;
	}

	/** The hand holding a bow, if one does (the main hand first). */
	private static @Nullable HumanoidArm bowHeld(final HumanoidRenderState state) {
		final HumanoidArm main = state.mainArm;
		if (state.getUseItemStackForArm(main).getUseAnimation() == ItemUseAnimation.BOW) {
			return main;
		}
		return state.getUseItemStackForArm(main.getOpposite()).getUseAnimation() == ItemUseAnimation.BOW ? main.getOpposite() : null;
	}

	/** How far a bow is drawn after {@code ticks} of drawing, 0..1: the bow's own power curve (full at BOW_FULL_DRAW_TICKS). */
	public static float pull(final float ticks) {
		final float t = Math.max(ticks, 0.0F) / BOW_FULL_DRAW_TICKS;
		return Math.min((t * t + 2.0F * t) / 3.0F, 1.0F);
	}

	/**
	 * A bow drawn (or just loosed, {@code release} 1 to 0 through the follow-through): side-on to the aim, the bow arm's
	 * shoulder toward it and the arm out along it; the string hand from the arrow's nock at the bow back to the cheek on
	 * the bow's side as the draw charges. Loosed, the string hand flies back past the cheek and the arms come down to where
	 * they rest. The arms come up into the draw from where they rest, too.
	 */
	private static void bow(
		final HumanoidModel<?> model, final HumanoidRenderState state, final RidePoseState rider, final HumanoidArm bowArm, final float twist,
		final float handBob, final float release
	) {
		final boolean right = bowArm == HumanoidArm.RIGHT;
		// Toward the bow's side of the model (its x; the rider's right is negative).
		final float side = right ? -1.0F : 1.0F;
		final ModelPart bow = right ? model.rightArm : model.leftArm;
		final ModelPart string = right ? model.leftArm : model.rightArm;
		final float pull;
		final float up;
		final float flick;
		if (release > 0.0F) {
			pull = rider.horsingaround$bowPull();
			final float since = 1.0F - release;
			flick = Math.min(since / BOW_FLICK_SHARE, 1.0F);
			up = 1.0F - smoothstep((since - BOW_HOLD_SHARE) / (1.0F - BOW_HOLD_SHARE));
		} else {
			pull = pull(state.ticksUsingItem);
			up = smoothstep(state.ticksUsingItem / BOW_RAISE_TICKS);
			flick = 0.0F;
		}
		// Where each arm rests, to come up from (and go back down to).
		rest(model, state, bowArm, twist, handBob);
		rest(model, state, bowArm.getOpposite(), twist, handBob);
		final float restBowX = bow.xRot;
		final float restBowY = bow.yRot;
		final float restBowZ = bow.zRot;
		final float restStringX = string.xRot;
		final float restStringY = string.yRot;
		final float restStringZ = string.zRot;

		final float aimYaw = model.head.yRot;
		final float aimPitch = model.head.xRot;
		shoulders(model, state, Mth.lerp(up, twist, Mth.clamp(aimYaw + side * BOW_SIDE_ON, -BOW_TWIST_MAX, BOW_TWIST_MAX)));
		// The bow arm out along the aim, a little in toward the eye line.
		final float bowX = -Mth.HALF_PI + aimPitch;
		final float bowY = aimYaw + side * BOW_ARM_IN;
		// The arrow's nock, just back from the grip, and the anchor at the cheek (turned with the head).
		along(HAND, bowX, bowY, ARM_REACH - BOW_NOCK).add(bow.x, bow.y, bow.z);
		ANCHOR.set(side * BOW_ANCHOR_X, BOW_ANCHOR_Y, BOW_ANCHOR_Z).rotateX(aimPitch).rotateY(aimYaw).add(model.head.x, model.head.y, model.head.z);
		// The string hand from the nock toward the anchor as it draws, flying back past it when loosed.
		final float draw = Mth.length(ANCHOR.x - HAND.x, ANCHOR.y - HAND.y, ANCHOR.z - HAND.z);
		final float back = draw < 1.0E-3F ? 0.0F : BOW_FLICK * flick * pull / draw;
		final float tx = Mth.lerp(pull, HAND.x, ANCHOR.x) + (ANCHOR.x - HAND.x) * back - string.x;
		final float ty = Mth.lerp(pull, HAND.y, ANCHOR.y) + (ANCHOR.y - HAND.y) * back - string.y;
		final float tz = Mth.lerp(pull, HAND.z, ANCHOR.z) + (ANCHOR.z - HAND.z) * back - string.z;
		final float length = Mth.length(tx, ty, tz);
		final float stringX = -(float) Math.acos(Mth.clamp(ty / Math.max(length, 1.0E-3F), -1.0F, 1.0F));
		final float stringY = (float) Mth.atan2(-tx, -tz);
		bow.xRot = Mth.lerp(up, restBowX, bowX);
		bow.yRot = Mth.lerp(up, restBowY, bowY);
		bow.zRot = Mth.lerp(up, restBowZ, 0.0F);
		string.xRot = Mth.lerp(up, restStringX, stringX);
		string.yRot = Mth.lerp(up, restStringY, stringY);
		string.zRot = Mth.lerp(up, restStringZ, 0.0F);
	}

	/**
	 * {@code distance} pixels from an arm's pivot along the arm, posed at {@code xRot} then {@code yRot} (it hangs down
	 * unturned; the model's y is down and its front is -z), into {@code out}.
	 */
	private static Vector3f along(final Vector3f out, final float xRot, final float yRot, final float distance) {
		final float sinX = Mth.sin(xRot);
		return out.set(sinX * Mth.sin(yRot) * distance, Mth.cos(xRot) * distance, sinX * Mth.cos(yRot) * distance);
	}

	/**
	 * A crossbow: loaded, it is held square to the aim (vanilla's arms are along the head's aim already; the torso's turn
	 * carried on top of that pointed them past it); loading, in front of the turned torso.
	 */
	private static void crossbow(final HumanoidModel<?> model, final HumanoidRenderState state, final float twist) {
		if (state.rightArmPose == HumanoidModel.ArmPose.CROSSBOW_HOLD || state.leftArmPose == HumanoidModel.ArmPose.CROSSBOW_HOLD) {
			shoulders(model, state, Mth.clamp(model.head.yRot, -BOW_TWIST_MAX, BOW_TWIST_MAX));
		} else {
			shoulders(model, state, twist);
			model.rightArm.yRot += twist;
			model.leftArm.yRot += twist;
		}
	}

	private static float smoothstep(final float t) {
		final float x = Mth.clamp(t, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
