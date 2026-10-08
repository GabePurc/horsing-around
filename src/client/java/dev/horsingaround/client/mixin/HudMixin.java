package dev.horsingaround.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.PlayerRideableJumping;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** The stamina bar (in the jump bar's slot) shows whenever stamina is not full, whatever else wants the slot. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	/** Over the locator bar. */
	@ModifyReturnValue(method = "willPrioritizeJumpInfo", at = @At("RETURN"))
	private boolean horsingaround$prioritizeStamina(final boolean original) {
		return original || horsingaround$staminaShowing(this.minecraft.player);
	}

	/**
	 * Over the experience bar, for HUD mods that hide the jump bar while riding unless jump is held (Better Mount HUD):
	 * our horses never charge a jump, so without this the stamina bar would only flash up while space is down.
	 */
	@WrapOperation(
		method = "nextContextualInfoState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;jumpableVehicle()Lnet/minecraft/world/entity/PlayerRideableJumping;")
	)
	private @Nullable PlayerRideableJumping horsingaround$staminaVehicle(final LocalPlayer player, final Operation<PlayerRideableJumping> original) {
		final PlayerRideableJumping vehicle = original.call(player);
		return vehicle == null && horsingaround$staminaShowing(player) ? player.jumpableVehicle() : vehicle;
	}

	@Unique
	private static boolean horsingaround$staminaShowing(final LocalPlayer player) {
		return player.getVehicle() instanceof RideStateHolder holder && holder.horsingaround$managed() && holder.horsingaround$ride().stamina < 1.0F;
	}
}
