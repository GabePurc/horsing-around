package dev.horsingaround.gametest.forge.mixin;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.loading.ServerModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server test's dedicated server runs inside the test client, as on Fabric (the harness starts it through the game's
 * server main). The client loaded every mod already; Forge's server main would load them all again, which fails.
 */
@Mixin(value = ServerModLoader.class, remap = false)
abstract class ServerModLoaderMixin {
	@Inject(method = "load", at = @At("HEAD"), cancellable = true)
	private static void horsingaroundTest$loadedByTheClient(final CallbackInfo ci) {
		if (FMLEnvironment.dist == Dist.CLIENT) {
			ci.cancel();
		}
	}
}
