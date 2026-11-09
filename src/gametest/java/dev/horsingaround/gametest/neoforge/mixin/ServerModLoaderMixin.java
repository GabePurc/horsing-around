package dev.horsingaround.gametest.neoforge.mixin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.loading.ServerModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server test's dedicated server runs inside the test client, as on Fabric (the harness starts it through the game's
 * server main). The client loaded every mod already; NeoForge's server main would load them all again, which fails.
 */
@Mixin(value = ServerModLoader.class, remap = false)
abstract class ServerModLoaderMixin {
	@Inject(method = "load", at = @At("HEAD"), cancellable = true)
	private static void horsingaroundTest$loadedByTheClient(final boolean dataGen, final CallbackInfo ci) {
		if (FMLEnvironment.getDist() == Dist.CLIENT) {
			ci.cancel();
		}
	}
}
