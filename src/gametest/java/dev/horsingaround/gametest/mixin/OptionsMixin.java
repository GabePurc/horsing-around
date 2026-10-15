package dev.horsingaround.gametest.mixin;

import dev.horsingaround.gametest.LowSettings;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Light test games: low settings as the options load. Before the test framework's own injection (hence the priority below
 * its default 1000): that one snapshots the options, and restores the snapshot before every test, so ours has to be in it.
 */
@Mixin(value = Options.class, priority = 900)
abstract class OptionsMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void horsingaroundTest$lowSettings(final CallbackInfo ci) {
		LowSettings.apply((Options) (Object) this);
	}
}
