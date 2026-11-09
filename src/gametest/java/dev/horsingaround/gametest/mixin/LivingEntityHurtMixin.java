package dev.horsingaround.gametest.mixin;

//? if fabric && <1.21 {
/*import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import dev.horsingaround.gametest.TestEnv;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Test-only, on Fabric before 1.21 (Fabric API has no after-damage event there): what a living thing on the server took
// from a hit, as the health it lost, for TestEnv.afterDamage. Registered by TestMixinPlugin on those versions.
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {
	@Inject(method = "hurt", at = @At("HEAD"))
	private void horsingaroundtest$before(final DamageSource source, final float amount, final CallbackInfoReturnable<Boolean> cir, @Share("health") final LocalFloatRef health) {
		health.set(((LivingEntity) (Object) this).getHealth());
	}

	@Inject(method = "hurt", at = @At("RETURN"))
	private void horsingaroundtest$after(final DamageSource source, final float amount, final CallbackInfoReturnable<Boolean> cir, @Share("health") final LocalFloatRef health) {
		final LivingEntity entity = (LivingEntity) (Object) this;
		final float taken = health.get() - entity.getHealth();
		if (!entity.level().isClientSide && taken > 0.0F) {
			for (final TestEnv.Hurt listener : TestEnv.HURT) {
				listener.hurt(entity, source, taken);
			}
		}
	}
}
*///?}
