package dev.horsingaround.gametest.mixin;

//? if <1.21.2 {
/*import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// A horse model's parts before 1.21.2, which has no root to find them under: for the leg probe. Tests only (registered by
// TestMixinPlugin on those versions).
@Mixin(HorseModel.class)
public interface HorseModelAccessor {
	@Accessor("body")
	ModelPart horsingaroundtest$body();

	@Accessor("headParts")
	ModelPart horsingaroundtest$headParts();

	@Accessor("leftFrontLeg")
	ModelPart horsingaroundtest$leftFrontLeg();

	@Accessor("rightFrontLeg")
	ModelPart horsingaroundtest$rightFrontLeg();

	@Accessor("leftHindLeg")
	ModelPart horsingaroundtest$leftHindLeg();

	@Accessor("rightHindLeg")
	ModelPart horsingaroundtest$rightHindLeg();

	@Accessor("tail")
	ModelPart horsingaroundtest$tail();
}
*///?}
