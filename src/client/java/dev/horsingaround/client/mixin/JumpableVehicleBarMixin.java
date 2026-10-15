package dev.horsingaround.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
//? if >=1.21.6
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
	//? if >=1.20.2 {
	@Unique
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("hud/jump_bar_background");
	@Unique
	private static final Identifier EXHAUSTED_BACKGROUND = Identifier.withDefaultNamespace("hud/jump_bar_cooldown");
	@Unique
	private static final Identifier PROGRESS = Identifier.withDefaultNamespace("hud/jump_bar_progress");
	//?} else {
	/*// (Before 1.20.2 the bars are parts of one texture.)
	@Unique
	private static final Identifier ICONS = Identifier.withDefaultNamespace("textures/gui/icons.png");
	*///?}
	@Unique
	private static final int EXHAUSTED_TINT = 0xFFFF7A6E;
	//? if <1.21.2 {
	/*// EXHAUSTED_TINT, as the colour setting of the old sprite draw takes it.
	@Unique
	private static final float EXHAUSTED_TINT_RED = 1.0F;
	@Unique
	private static final float EXHAUSTED_TINT_GREEN = 0x7A / 255.0F;
	@Unique
	private static final float EXHAUSTED_TINT_BLUE = 0x6E / 255.0F;
	*///?}
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
		final RideState s = RideStateHolder.managedRide(this.minecraft.player.getVehicle());
		if (s == null) {
			original.call(graphics, deltaTracker);
			return;
		}
		final int left = this.left(this.minecraft.getWindow());
		final int top = this.top(this.minecraft.getWindow());
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, s.exhausted ? EXHAUSTED_BACKGROUND : BACKGROUND, left, top, WIDTH, HEIGHT);
		final int filled = Mth.lerpDiscrete(s.stamina, 0, WIDTH);
		if (filled > 0) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS, WIDTH, HEIGHT, 0, 0, left, top, filled, HEIGHT, s.exhausted ? EXHAUSTED_TINT : -1);
		}
	}
	//?} elif >=1.21.2 {
	/*@WrapMethod(method = "renderJumpMeter")
	private void horsingaround$staminaBar(final PlayerRideableJumping jumpable, final GuiGraphicsExtractor graphics, final int left, final Operation<Void> original) {
		final RideState s = RideStateHolder.managedRide(this.minecraft.player.getVehicle());
		if (s == null) {
			original.call(jumpable, graphics, left);
			return;
		}
		final int top = graphics.guiHeight() - 32 + 3;
		graphics.blitSprite(RenderType::guiTextured, s.exhausted ? EXHAUSTED_BACKGROUND : BACKGROUND, left, top, WIDTH, HEIGHT);
		final int filled = Mth.lerpDiscrete(s.stamina, 0, WIDTH);
		if (filled > 0) {
			// (No public tinted partial sprite draw yet: the whole bar tinted, cut to the filled part, as vanilla's partial draw does.)
			graphics.enableScissor(left, top, left + filled, top + HEIGHT);
			graphics.blitSprite(RenderType::guiTextured, PROGRESS, left, top, WIDTH, HEIGHT, s.exhausted ? EXHAUSTED_TINT : -1);
			graphics.disableScissor();
		}
	}
	*///?} elif >=1.20.2 {
	/*@WrapMethod(method = "renderJumpMeter")
	private void horsingaround$staminaBar(final PlayerRideableJumping jumpable, final GuiGraphicsExtractor graphics, final int left, final Operation<Void> original) {
		final RideState s = RideStateHolder.managedRide(this.minecraft.player.getVehicle());
		if (s == null) {
			original.call(jumpable, graphics, left);
			return;
		}
		final int top = graphics.guiHeight() - 32 + 3;
		graphics.blitSprite(s.exhausted ? EXHAUSTED_BACKGROUND : BACKGROUND, left, top, WIDTH, HEIGHT);
		final int filled = Mth.lerpDiscrete(s.stamina, 0, WIDTH);
		if (filled > 0) {
			// The sprite draw of this version takes its colour from the graphics, so the exhausted bar is tinted as in newer ones.
			if (s.exhausted) {
				graphics.setColor(EXHAUSTED_TINT_RED, EXHAUSTED_TINT_GREEN, EXHAUSTED_TINT_BLUE, 1.0F);
			}
			graphics.blitSprite(PROGRESS, WIDTH, HEIGHT, 0, 0, left, top, filled, HEIGHT);
			if (s.exhausted) {
				graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
			}
		}
	}
	*///?} else {
	/*@WrapMethod(method = "renderJumpMeter")
	private void horsingaround$staminaBar(final PlayerRideableJumping jumpable, final GuiGraphicsExtractor graphics, final int left, final Operation<Void> original) {
		final RideState s = RideStateHolder.managedRide(this.minecraft.player.getVehicle());
		if (s == null) {
			original.call(jumpable, graphics, left);
			return;
		}
		final int top = graphics.guiHeight() - 32 + 3;
		graphics.blit(ICONS, left, top, 0, 84, WIDTH, HEIGHT);
		if (s.exhausted) {
			graphics.blit(ICONS, left, top, 0, 74, WIDTH, HEIGHT);
		}
		// (Mth.lerpDiscrete, which later versions use, isn't there yet.)
		final int filled = Mth.floor(s.stamina * (WIDTH - 1)) + (s.stamina > 0.0F ? 1 : 0);
		if (filled > 0) {
			if (s.exhausted) {
				graphics.setColor(EXHAUSTED_TINT_RED, EXHAUSTED_TINT_GREEN, EXHAUSTED_TINT_BLUE, 1.0F);
			}
			graphics.blit(ICONS, left, top, 0, 89, filled, HEIGHT);
			if (s.exhausted) {
				graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
			}
		}
	}
	*///?}
}
