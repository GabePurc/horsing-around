package dev.horsingaround.shoulder.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.horsingaround.shoulder.ShoulderCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the centre crosshair over the shoulder; aiming goes wherever it points. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
	private boolean horsingaroundShoulder$crosshair(final boolean firstPerson) {
		return firstPerson || ShoulderCamera.isActive(this.minecraft, this.minecraft.getCameraEntity());
	}
}
