package dev.horsingaround.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** When the locator bar is competing for the slot, show the stamina bar whenever stamina is not full. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@ModifyReturnValue(method = "willPrioritizeJumpInfo", at = @At("RETURN"))
	private boolean horsingaround$prioritizeStamina(final boolean original) {
		return original
			|| this.minecraft.player.getVehicle() instanceof RideStateHolder holder
				&& holder.horsingaround$managed()
				&& holder.horsingaround$ride().stamina < 1.0F;
	}
}
