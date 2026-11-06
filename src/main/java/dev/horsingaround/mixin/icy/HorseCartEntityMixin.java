package dev.horsingaround.mixin.icy;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.horsingaround.ride.CartTrail;
import dev.horsingaround.ride.IcyCompat;
import dev.horsingaround.ride.Mounts;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Icy's carts, on both sides: behind a horse this mod rides the cart moves as CartTrail has it, instead of swinging round
 * the horse's middle and tilting as a whole. Icy's mod draws the cart and seats its riders by its heading, tilt and lift,
 * so setting those moves them all; only the lean across is new, drawn by HorseCartRendererMixin and RidePoser.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.entity.HorseCartEntity", remap = false)
public abstract class HorseCartEntityMixin implements CartTrail.Lean {
	@Shadow(remap = false)
	private float slackYaw;
	@Shadow(remap = false)
	private float slackYawO;
	@Shadow(remap = false)
	private float tilt;
	/** (Last tick's: Icy's mod has moved the tilt on already by the end of settle.) */
	@Shadow(remap = false)
	private float tiltO;

	@Shadow(remap = false)
	public abstract AbstractHorse boundHorse();

	@Shadow(remap = false)
	public abstract boolean isPlaced();

	@Shadow(remap = false)
	public abstract Vec3 gluedRenderPosition(float partialTick);

	@Unique
	private final CartTrail.Axle horsingaround$axle = new CartTrail.Axle();
	/** (Apart from the axle: the box is made while the entity is being built, before the axle is.) */
	@Unique
	private float horsingaround$boxBehind;

	@Inject(method = "settle", at = @At("TAIL"), remap = false)
	private void horsingaround$trail(final AbstractHorse horse, final Vec3 hitch, final CallbackInfo ci) {
		if (!Mounts.manages(horse)) {
			this.horsingaround$axle.lift();
			return;
		}
		final Entity cart = (Entity) (Object) this;
		final double length = IcyCompat.cartLength(cart);
		final double behind = CartTrail.behind(horse);
		this.horsingaround$axle.standBehind(behind);
		this.horsingaround$boxBehind = (float) behind;
		this.slackYaw = CartTrail.follow(this.horsingaround$axle, hitch.x, hitch.z, horse.yBodyRot, length + behind);
		if (horse.isInWater() && !horse.onGround()) {
			// (Afloat, Icy's mod has it level and on the water.)
			this.horsingaround$axle.afloat();
			return;
		}
		this.tilt = CartTrail.lean(
			this.horsingaround$axle, horse.level(), hitch.y + horsingaround$stepEase(horse, 1.0F), this.slackYaw, length, IcyCompat.cartFrontAxle(cart), this.tiltO,
			horse.getControllingPassenger() != null
		);
	}

	/** The cart stands further behind the horse: drawn there... */
	@ModifyReturnValue(method = "gluedRenderPosition", at = @At("RETURN"), remap = false)
	private Vec3 horsingaround$drawnBehind(final Vec3 at, final float partialTick) {
		if (at == null || !horsingaround$rolling()) {
			return at;
		}
		final double behind = this.horsingaround$axle.behind(partialTick);
		final float yaw = horsingaround$yaw(partialTick) * Mth.DEG_TO_RAD;
		return at.add(Mth.sin(yaw) * behind, 0.0, -Mth.cos(yaw) * behind);
	}

	/** ...with its bench, where Icy's mod seats and draws the driver from, and from where it holds the reins... */
	@ModifyReturnValue(method = "name=/^benchSeatOffset$/ desc=/IFFF/", at = @At("RETURN"), remap = false)
	private static Vec3 horsingaround$benchBehind(final Vec3 seat, final AbstractHorse horse, final int index, final float yaw, final float tilt, final float lift) {
		final CartTrail.Lean cart = IcyCompat.cartLean(horse);
		// (Being drawn, as far behind as the cart is drawn: a tick's distance apart, the rider shook against it.)
		final double behind = cart == null ? 0.0 : Float.isNaN(CartTrail.drawing) || !horse.level().isClientSide() ? cart.horsingaround$behind() : cart.horsingaround$behind(CartTrail.drawing);
		final float rad = yaw * Mth.DEG_TO_RAD;
		return behind == 0.0 ? seat : seat.add(Mth.sin(rad) * behind, 0.0, -Mth.cos(rad) * behind);
	}

	/** ...and where it can be clicked and bumped into. */
	@ModifyVariable(method = "makeBoundingBox", at = @At("HEAD"), argsOnly = true, remap = false)
	private Vec3 horsingaround$boxBehind(final Vec3 pos) {
		final float rad = ((Entity) (Object) this).getYRot() * Mth.DEG_TO_RAD;
		return this.horsingaround$boxBehind == 0.0F ? pos : pos.add(Mth.sin(rad) * this.horsingaround$boxBehind, 0.0, -Mth.cos(rad) * this.horsingaround$boxBehind);
	}

	/** Icy's mod draws the cart and the seats on it this high above the horse: this mod's rise goes on top of its float. */
	@ModifyReturnValue(method = "renderLift", at = @At("RETURN"), remap = false)
	private float horsingaround$drawnLift(final float lift, final float partialTick) {
		return lift + horsingaround$rise(partialTick);
	}

	@Inject(method = "renderSeat", at = @At("HEAD"), remap = false)
	private void horsingaround$seatDrawn(final Entity passenger, final float partialTick, final CallbackInfoReturnable<Vec3> cir) {
		CartTrail.drawing = partialTick;
	}

	/** A seat leans over with the cart, about the cart's long axis, on the ground under its middle. */
	@Inject(method = "renderSeat", at = @At("RETURN"), cancellable = true, remap = false)
	private void horsingaround$leanSeat(final Entity passenger, final float partialTick, final CallbackInfoReturnable<Vec3> cir) {
		CartTrail.drawing = Float.NaN;
		final float roll = horsingaround$roll(partialTick) * Mth.DEG_TO_RAD;
		final Vec3 base = roll == 0.0F ? null : gluedRenderPosition(partialTick);
		if (base == null) {
			return;
		}
		final Vec3 seat = cir.getReturnValue();
		//? if >=1.20.5 {
		final Vec3 riding = passenger.getVehicle() == null ? Vec3.ZERO : passenger.getVehicleAttachmentPoint(passenger.getVehicle());
		//?} else
		/*final Vec3 riding = Vec3.ZERO;*/
		final float yaw = horsingaround$yaw(partialTick) * Mth.DEG_TO_RAD;
		final double leftX = Mth.cos(yaw);
		final double leftZ = Mth.sin(yaw);
		final double left = (seat.x + riding.x - base.x) * leftX + (seat.z + riding.z - base.z) * leftZ;
		final double up = seat.y + riding.y - base.y;
		final double moveLeft = left * (Mth.cos(roll) - 1.0F) - up * Mth.sin(roll);
		final double moveUp = left * Mth.sin(roll) + up * (Mth.cos(roll) - 1.0F);
		cir.setReturnValue(seat.add(leftX * moveLeft, moveUp, leftZ * moveLeft));
	}

	@Override
	public boolean horsingaround$rolling() {
		return this.horsingaround$axle.placed() && !isPlaced();
	}

	@Override
	public float horsingaround$roll(final float partialTick) {
		return this.horsingaround$axle.roll(partialTick);
	}

	@Override
	public float horsingaround$yaw(final float partialTick) {
		return Mth.rotLerp(partialTick, this.slackYawO, this.slackYaw);
	}

	@Override
	public double horsingaround$turned(final float partialTick) {
		return this.horsingaround$axle.turned(partialTick);
	}

	@Override
	public float horsingaround$rise(final float partialTick) {
		final AbstractHorse horse = horsingaround$rolling() ? boundHorse() : null;
		if (horse == null) {
			return 0.0F;
		}
		// (On its own wheels; afloat, as the horse is drawn.)
		final double y = this.horsingaround$axle.y(partialTick);
		return Double.isNaN(y)
			? horsingaround$stepEase(horse, partialTick) - (float) this.horsingaround$axle.behind(partialTick) * Mth.sin(Mth.lerp(partialTick, this.tiltO, this.tilt) * Mth.DEG_TO_RAD)
			: (float) (y - Mth.lerp(partialTick, horse.yo, horse.getY()));
	}

	@Override
	public float horsingaround$belowHitch(final float partialTick) {
		final AbstractHorse horse = horsingaround$rolling() ? boundHorse() : null;
		return horse == null ? 0.0F : horsingaround$stepEase(horse, partialTick) - horsingaround$rise(partialTick);
	}

	@Override
	public float horsingaround$travel(final int wheel, final float partialTick) {
		return this.horsingaround$axle.travel(wheel, partialTick);
	}

	@Override
	public float horsingaround$offRamp(final int wheel) {
		return this.horsingaround$axle.offRamp(wheel);
	}

	@Override
	public float horsingaround$tilt(final float partialTick) {
		return Mth.lerp(partialTick, this.tiltO, this.tilt);
	}

	@Override
	public double horsingaround$behind(final float partialTick) {
		return this.horsingaround$axle.behind(partialTick);
	}

	@Override
	public double horsingaround$standing() {
		return this.horsingaround$axle.standing();
	}

	@Override
	public float horsingaround$horseYaw(final float partialTick) {
		final AbstractHorse horse = boundHorse();
		return horse == null ? horsingaround$yaw(partialTick) : Mth.rotLerp(partialTick, horse.yBodyRotO, horse.yBodyRot);
	}

	@Override
	public double horsingaround$behind() {
		return this.horsingaround$axle.behind();
	}

	@Override
	public boolean horsingaround$blockedBehind() {
		final AbstractHorse horse = boundHorse();
		return horse != null && CartTrail.blockedBehind(this.horsingaround$axle, horse.level(), horse.getY(), this.slackYaw);
	}

	@Unique
	private static float horsingaround$stepEase(final AbstractHorse horse, final float partialTick) {
		final RideState ride = Mounts.manages(horse) ? RideStateHolder.managedRide(horse) : null;
		return ride == null ? 0.0F : ride.heightOffset(partialTick);
	}
}
