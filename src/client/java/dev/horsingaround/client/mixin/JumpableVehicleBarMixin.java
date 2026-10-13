package dev.horsingaround.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if >=1.21.6 {
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.contextualbar.JumpableVehicleBar;
import net.minecraft.client.renderer.RenderPipelines;
//?} else {
/*import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.PlayerRideableJumping;
*///?}
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;


/** The horse jump bar becomes the stamina bar, drawn with the vanilla jump bar sprites. */
//? if >=1.21.6 {
@Mixin(JumpableVehicleBar.class)
public abstract class JumpableVehicleBarMixin implements ContextualBar {
//?} else {
/*@Mixin(Gui.class)
public abstract class JumpableVehicleBarMixin {
*///?}
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

	//? if >=1.21.6 {
	@WrapMethod(method = "extractBackground")
	private void horsingaround$staminaBar(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker, final Operation<Void> original) {
		if (!(this.minecraft.player.getVehicle() instanceof RideStateHolder holder) || !holder.horsingaround$managed()) {
			original.call(graphics, deltaTracker);
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
	}
	//?} else {
	/*@WrapMethod(method = "renderJumpMeter")
	private void horsingaround$staminaBar(final PlayerRideableJumping jumpable, final GuiGraphicsExtractor graphics, final int left, final Operation<Void> original) {
		if (!(this.minecraft.player.getVehicle() instanceof RideStateHolder holder) || !holder.horsingaround$managed()) {
			original.call(jumpable, graphics, left);
			return;
		}
		final RideState s = holder.horsingaround$ride();
		final int top = graphics.guiHeight() - 32 + 3;
		graphics.blitSprite(RenderType::guiTextured, s.exhausted ? EXHAUSTED_BACKGROUND : BACKGROUND, left, top, WIDTH, HEIGHT);
		final int filled = Mth.lerpDiscrete(s.stamina, 0, WIDTH);
		if (filled > 0) {
			// 1.21.5 has no public tinted partial sprite draw: the exhausted bar keeps its own background sprite, untinted.
			graphics.blitSprite(RenderType::guiTextured, PROGRESS, WIDTH, HEIGHT, 0, 0, left, top, filled, HEIGHT);
		}
	}
	*///?}
}
