//? if <1.21.4 {
/*package dev.horsingaround.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;

// Implemented on HumanoidModel by mixin: before 1.21.4 the arm poses aren't in the render state, the model works them out.
public interface ArmPoseSource {
	HumanoidModel.ArmPose horsingaround$armPose(HumanoidRenderState state, HumanoidArm arm);
}
*///?}
