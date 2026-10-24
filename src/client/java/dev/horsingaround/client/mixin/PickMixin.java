package dev.horsingaround.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.horsingaround.client.RideAim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
//? if <26.1
/*import net.minecraft.client.renderer.GameRenderer;*/
import org.spongepowered.asm.mixin.Mixin;

/**
 * Over the shoulder, the game picks what is under the centre crosshair (the block to mine, the mob to hit): its own
 * targeting runs with the rider's eyes turned toward it for the moment. The game picks in {@code Minecraft} from 26.1,
 * in {@code GameRenderer} before.
 */
//? if >=26.1 {
@Mixin(Minecraft.class)
//?} else
/*@Mixin(GameRenderer.class)*/
public abstract class PickMixin {
	@WrapMethod(method = "pick(F)V")
	private void horsingaround$pickAtCrosshair(final float partialTicks, final Operation<Void> original) {
		final Minecraft minecraft = Minecraft.getInstance();
		if (!RideAim.update(minecraft, partialTicks)) {
			original.call(partialTicks);
			return;
		}
		final LocalPlayer player = minecraft.player;
		final float yRot = player.getYRot();
		final float xRot = player.getXRot();
		final float yRotO = player.yRotO;
		final float xRotO = player.xRotO;
		player.setYRot(RideAim.yaw());
		player.setXRot(RideAim.pitch());
		player.yRotO = RideAim.yaw();
		player.xRotO = RideAim.pitch();
		try {
			original.call(partialTicks);
			final Entity picked = minecraft.crosshairPickEntity;
			if (picked != null && (picked == player.getVehicle() || picked.isPassengerOfSameVehicle(player))) {
				// From the rider's eyes the line toward the crosshair's target can pass through the horse's own head or neck: the
				// game would target the horse (showing the attack indicator over nothing). The block beyond, in reach, instead.
				//? if >=1.20.5 {
				final double reach = player.blockInteractionRange();
				//?} else
				/*final double reach = minecraft.gameMode.getPickRange();*/
				minecraft.crosshairPickEntity = null;
				minecraft.hitResult = player.pick(reach, partialTicks, false);
				RideAim.horsePicks++;
			}
		} finally {
			player.setYRot(yRot);
			player.setXRot(xRot);
			player.yRotO = yRotO;
			player.xRotO = xRotO;
		}
	}
}
