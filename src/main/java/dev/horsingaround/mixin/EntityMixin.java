package dev.horsingaround.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.horsingaround.ride.Footing;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Footing: a ridden horse in the air (off a drop, or a jump a little short) moves as if it had a hoof down, so
 * vanilla's step-up puts it onto anything within a step of its hooves that it clips, instead of the collision stopping
 * it dead. The move itself works out the real ground contact again. Then {@link Footing} slips a shoulder caught on a
 * corner past it, and records how much of the move a collision took. Only on the side simulating the ride.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Shadow
	private boolean onGround;

	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
	private Vec3 horsingaround$beforeMove(final Vec3 delta, @Local(argsOnly = true) final MoverType type) {
		if (type == MoverType.SELF && (Object) this instanceof RideStateHolder holder) {
			final RideState s = holder.horsingaround$ride();
			final Entity horse = (Entity) (Object) this;
			if (s.narrow && horse.isLocalInstanceAuthoritative()) {
				// (Not while heaving out of the water or climbing up a ledge: those rise smoothly onto the top by themselves,
				// and a step-up partway would pop the horse up.)
				if (!this.onGround && !horse.isInWater() && s.bankTicks == 0 && !s.climbingLedge() && s.hurdleForward == 0.0F) {
					this.onGround = true;
					s.airFooting = true;
				}
				s.moving = true;
				s.moveFromX = horse.getX();
				s.moveFromZ = horse.getZ();
				s.moveDelta = delta;
			}
		}
		return delta;
	}

	@Inject(method = "move", at = @At("RETURN"))
	private void horsingaround$afterMove(final MoverType type, final Vec3 delta, final CallbackInfo ci) {
		if ((Object) this instanceof RideStateHolder holder && holder.horsingaround$ride().moving) {
			final RideState s = holder.horsingaround$ride();
			s.moving = false;
			s.airFooting = false;
			Footing.afterMove((AbstractHorse) (Object) this, s, s.moveDelta, s.moveFromX, s.moveFromZ);
		}
	}
}
