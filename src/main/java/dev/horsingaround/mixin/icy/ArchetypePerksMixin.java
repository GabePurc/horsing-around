package dev.horsingaround.mixin.icy;

import dev.horsingaround.ride.IcyCompat;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Icy's western and other classes speed a ridden horse up on roads (stone, gravel, paths...), swinging its speed as the
 * ground under it changes. This mod's riding sets its gaits from that speed, so on the horses it rides the road bonus
 * stays off (see SpeedAbilitiesMixin); the rest of the class perks (step height, mass, healing) stay.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.feature.breed.ArchetypePerks", remap = false)
public abstract class ArchetypePerksMixin {
	private static final Identifier ROAD = Identifier.fromNamespaceAndPath(IcyCompat.MOD_ID, "archetype/path");

	@Inject(method = "updatePath", at = @At("HEAD"), cancellable = true)
	private void horsingaround$noRoadBonus(final AbstractHorse horse, @Coerce final Object data, @Coerce final Object archetype, final CallbackInfo ci) {
		if (horse.getControllingPassenger() == null || RideStateHolder.managedRide(horse) == null) {
			return;
		}
		//? if >=1.21 {
		final AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(ROAD);
		}
		//?}
		ci.cancel();
	}
}
