package dev.horsingaround.client.mixin;

import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.contextualbar.JumpableVehicleBar;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The horse jump bar becomes the stamina bar, drawn with the vanilla jump bar sprites. */
@Mixin(JumpableVehicleBar.class)
public abstract class JumpableVehicleBarMixin implements ContextualBar {
	@Unique
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("hud/jump_bar_background");
	@Unique
	private static final Identifier EXHAUSTED_BACKGROUND = Identifier.withDefaultNamespace("hud/jump_bar_cooldown");
	@Unique
	private static final Identifier PROGRESS = Identifier.withDefaultNamespace("hud/jump_bar_progress");
	@Unique
	private static final int EXHAUSTED_TINT = 0xFFFF7A6E;
	@Unique
	private static final int WIDTH = 182;
	@Unique
	private static final int HEIGHT = 5;

	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void horsingaround$staminaBar(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker, final CallbackInfo ci) {
		if (!(this.minecraft.player.getVehicle() instanceof RideStateHolder holder) || !holder.horsingaround$managed()) {
			return;
		}
		final RideState s = holder.horsingaround$ride();
		final int left = this.left(this.minecraft.getWindow());
		final int top = this.top(this.minecraft.getWindow());
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, s.exhausted ? EXHAUSTED_BACKGROUND : BACKGROUND, left, top, WIDTH, HEIGHT);
		final int filled = Mth.lerpDiscrete(s.stamina, 0, WIDTH);
		if (filled > 0) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS, WIDTH, HEIGHT, 0, 0, left, top, filled, HEIGHT, s.exhausted ? EXHAUSTED_TINT : -1);
		}
		ci.cancel();
	}
}
