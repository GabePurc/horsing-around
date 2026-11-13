package dev.horsingaround.gametest.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A dedicated server unloads every mod's configs as it exits, the game being over. The server test's runs inside the test
 * client (ServerModLoaderMixin), which plays on: the next world it opens would find its configs gone.
 */
@Mixin(DedicatedServer.class)
abstract class DedicatedServerMixin {
	@WrapOperation(method = "onServerExit", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/config/ConfigTracker;forceUnload()V", remap = false))
	private void horsingaroundTest$keepTheClientsConfigs(final Operation<Void> original) {
		// (Never needed: the tests' games are clients.)
	}
}
