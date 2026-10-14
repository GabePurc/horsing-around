//? if <1.21.2 {
/*package dev.horsingaround.client.render.state;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

// Stands in for vanilla's HumanoidRenderState before 1.21.2 (see LivingEntityRenderState).
public class HumanoidRenderState extends LivingEntityRenderState {
	public float attackTime;
	public HumanoidArm attackArm = HumanoidArm.RIGHT;
	public HumanoidArm mainArm = HumanoidArm.RIGHT;
	public InteractionHand useItemHand = InteractionHand.MAIN_HAND;
	public boolean isPassenger;
	public boolean isUsingItem;
	// A baby is drawn by scaling the whole model here, so the shoulders keep their size in the model's own pixels.
	public final float ageScale = 1.0F;

	@Override
	public void fill(final LivingEntity entity, final float partialTicks) {
		super.fill(entity, partialTicks);
		this.attackTime = entity.getAttackAnim(partialTicks);
		this.mainArm = entity.getMainArm();
		this.attackArm = entity.swingingArm == InteractionHand.MAIN_HAND ? this.mainArm : this.mainArm.getOpposite();
		this.useItemHand = entity.getUsedItemHand();
		this.isPassenger = entity.isPassenger();
		this.isUsingItem = entity.isUsingItem();
	}
}
*///?}
