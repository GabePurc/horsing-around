package dev.horsingaround.gametest.mixin;

import dev.horsingaround.gametest.LowSettings;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Light test games: low settings as the options load, after the test framework's own (hence the priority). */
@Mixin(value = Options.class, priority = 1500)
abstract class OptionsMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void horsingaroundTest$lowSettings(final CallbackInfo ci) {
		LowSettings.apply((Options) (Object) this);
	}
}
