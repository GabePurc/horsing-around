package dev.horsingaround.client.mixin;

import dev.horsingaround.client.KeyPresses;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts each time a key goes down from up. Vanilla clicks a key for the OS key repeat as it does for a press, so the
 * ride, which goes by presses (see RiderKeys), can't tell a held key from one tapped over and over by its clicks alone.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements KeyPresses {
	@Shadow
	private boolean isDown;

	@Unique
	private int horsingaround$downs;

	// (Required: without it a key tapped again before a tick saw it up would count once.)
	@Inject(method = "setDown", at = @At("HEAD"), require = 1)
	private void horsingaround$countDown(final boolean down, final CallbackInfo ci) {
		if (down && !this.isDown) {
			this.horsingaround$downs++;
		}
	}

	@Override
	public int horsingaround$takeDowns() {
		final int downs = this.horsingaround$downs;
		this.horsingaround$downs = 0;
		return downs;
	}
}
