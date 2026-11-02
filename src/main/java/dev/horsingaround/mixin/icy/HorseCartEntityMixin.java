package dev.horsingaround.mixin.icy;

import com.llamalad7.mixinextras.sugar.Local;
import dev.horsingaround.ride.CartTrail;
import dev.horsingaround.ride.IcyCompat;
import dev.horsingaround.ride.Mounts;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Icy's carts, on both sides: behind a horse this mod rides the cart trails as a real cart does (CartTrail) instead of
 * swinging round the horse's middle, and over steps it rises with the horse as drawn and tilts as its wheels roll over
 * them, instead of a block at a time. Its heading and tilt are the ones Icy's mod draws the cart and seats its riders by.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.entity.HorseCartEntity", remap = false)
public abstract class HorseCartEntityMixin {
	@Shadow(remap = false)
	private float slackYaw;
	@Shadow(remap = false)
	private float tilt;
	/** (Last tick's: Icy's mod has moved the tilt on already by the end of settle.) */
	@Shadow(remap = false)
	private float tiltO;

	@Unique
	private final CartTrail.Axle horsingaround$axle = new CartTrail.Axle();

	@Inject(method = "settle", at = @At("TAIL"), remap = false)
	private void horsingaround$trail(final AbstractHorse horse, final Vec3 hitch, final CallbackInfo ci) {
		if (!Mounts.manages(horse)) {
			this.horsingaround$axle.lift();
			return;
		}
		final double length = IcyCompat.cartLength((Entity) (Object) this);
		this.slackYaw = CartTrail.follow(this.horsingaround$axle, hitch.x, hitch.z, horse.yBodyRot, length);
		// (Afloat, or in the air, it holds its tilt.)
		if (horse.onGround()) {
			this.tilt = CartTrail.tilt(this.horsingaround$axle, horse.level(), hitch.y + horsingaround$stepEase(horse, 1.0F), this.slackYaw, length, this.tiltO);
		} else {
			this.tilt = this.tiltO;
		}
	}

	/** Drawn, the hitch is where the horse is drawn: over a step the ride eases the horse's body up or down. */
	@ModifyArg(
		method = "gluedRenderPosition",
		at = @At(value = "INVOKE", target = "Licy/betterhorses/net/entity/HorseCartEntity;cartPosFor(DDDF)Lnet/minecraft/world/phys/Vec3;"),
		index = 1,
		remap = false
	)
	private double horsingaround$drawnHitch(final double y, @Local final AbstractHorse horse, @Local(argsOnly = true) final float partialTick) {
		return y + horsingaround$stepEase(horse, partialTick);
	}

	@Unique
	private static float horsingaround$stepEase(final AbstractHorse horse, final float partialTick) {
		final RideState ride = Mounts.manages(horse) ? RideStateHolder.managedRide(horse) : null;
		return ride == null ? 0.0F : ride.heightOffset(partialTick);
	}
}
