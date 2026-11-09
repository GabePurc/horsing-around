package dev.horsingaround.gametest.neoforge.mixin;

import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegistryManager;
import net.neoforged.neoforge.registries.RegistrySnapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The server test's dedicated server runs inside the test client (ServerModLoaderMixin), so the two share the game's
 * registries. NeoForge, as the client joins a server, applies the server's registry snapshot and freezes the registries
 * again, and as it leaves, puts back their loaded state: both reset the registries' tags to the few known at start-up,
 * taking the running server's tags away (the client joining it then can't read what the server sends). The snapshot is of
 * these same registries, so while that server runs there is nothing to apply.
 */
@Mixin(value = RegistryManager.class, remap = false)
abstract class RegistryManagerMixin {
	@Inject(method = "applySnapshot(Ljava/util/Map;Z)Ljava/util/Set;", at = @At("HEAD"), cancellable = true)
	private static void horsingaroundTest$sameRegistries(final Map<Identifier, RegistrySnapshot> snapshots, final boolean allowMissing, final CallbackInfoReturnable<Set<ResourceKey<?>>> cir) {
		if (dedicatedServerInHere()) {
			cir.setReturnValue(Set.of());
		}
	}

	@Inject(method = "revertToFrozen", at = @At("HEAD"), cancellable = true)
	private static void horsingaroundTest$keepTheServersRegistries(final CallbackInfo ci) {
		if (dedicatedServerInHere()) {
			ci.cancel();
		}
	}

	private static boolean dedicatedServerInHere() {
		return ThreadingImpl.isServerRunning && ThreadingImpl.unsafeClientInstance != null && ThreadingImpl.unsafeClientInstance.getSingleplayerServer() == null;
	}
}
