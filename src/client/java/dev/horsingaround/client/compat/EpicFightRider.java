package dev.horsingaround.client.compat;

//? if <1.21.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import dev.horsingaround.Platform;
import dev.horsingaround.client.render.LegacyRenderState;
import dev.horsingaround.client.render.RidePoseState;
import dev.horsingaround.ride.RideTuning;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
*///?}

/**
 * Epic Fight (Forge 1.20.1 and NeoForge 1.21.1) draws a player in its own mode itself, from the loader's event before a
 * living entity is drawn, and then calls the game's drawing off. The game's renderer is where a rider is moved onto the
 * saddle as drawn, so Epic Fight's rider stayed where the game seats it, upright: through the horse's back in a rear, above
 * it in a jump. The rider's move is put on the event's pose instead while the event is handled, so Epic Fight's model sits
 * on the saddle and leans as the rider does.
 *
 * <p>Its limbs are Epic Fight's own, so a rider is left to the game's drawing (reins, stirrups, the bow) whenever Epic Fight
 * has no action of its own playing, a swing or a hit: the loaders' {@code EpicFightModel} answer its question, as Epic
 * Fight itself does for the Player Animator mod.
 */
public final class EpicFightRider {
	//? if <1.21.2 {
	/*public static final boolean LOADED = Platform.isModLoaded("epicfight");
	private static boolean moved;

	private EpicFightRider() {
	}

	// Whether {@code player} is a rider this mod poses, being drawn now.
	public static boolean posed(final Entity player) {
		return LegacyRenderState.of(player) instanceof RidePoseState pose && pose.horsingaround$isRider();
	}

	// Before anyone handles the event (listened for at the highest priority; the other at the lowest).
	public static void before(final PoseStack poseStack) {
		final LivingEntityRenderState rider = LegacyRenderState.riderState();
		if (rider != null) {
			poseStack.pushPose();
			// (Its riding pose is made for the height the game seats a rider at: not sunk into the saddle as this mod's is.)
			poseStack.translate(0.0F, RideTuning.RIDER_SEAT_DROP, 0.0F);
			((RidePoseState) rider).horsingaround$applyPose(poseStack);
			moved = true;
		}
	}

	// After everyone has, whether or not the game's drawing was called off (it moves the rider itself).
	public static void after(final PoseStack poseStack) {
		if (moved) {
			poseStack.popPose();
			moved = false;
		}
	}
	*///?}
}
