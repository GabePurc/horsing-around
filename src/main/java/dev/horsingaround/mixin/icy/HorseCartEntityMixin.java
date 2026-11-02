package dev.horsingaround.mixin.icy;

import dev.horsingaround.ride.CartTrail;
import dev.horsingaround.ride.IcyCompat;
import dev.horsingaround.ride.Mounts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Icy's carts, on both sides: behind a horse this mod rides the cart trails as a real cart does (CartTrail) instead of
 * swinging round the horse's middle. Its heading is the one Icy's mod draws the cart and seats its riders by.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.entity.HorseCartEntity", remap = false)
public abstract class HorseCartEntityMixin {
	@Shadow(remap = false)
	private float slackYaw;

	@Unique
	private final CartTrail.Axle horsingaround$axle = new CartTrail.Axle();

	@Inject(method = "settle", at = @At("TAIL"), remap = false)
	private void horsingaround$trail(final AbstractHorse horse, final Vec3 hitch, final CallbackInfo ci) {
		if (!Mounts.manages(horse)) {
			this.horsingaround$axle.lift();
			return;
		}
		this.slackYaw = CartTrail.follow(this.horsingaround$axle, hitch.x, hitch.z, horse.yBodyRot, IcyCompat.cartLength((Entity) (Object) this));
	}
}
