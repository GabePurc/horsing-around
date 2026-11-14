package dev.horsingaround.gametest.forge.mixin;

import net.minecraftforge.registries.GameData;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.20.2 {
/*import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraftforge.registries.ForgeRegistry;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/**
 * The server test's dedicated server runs inside the test client (ServerModLoaderMixin), so the two share the game's
 * registries. Forge 1.20.1's client, as it joins a server, puts the server's registry snapshot in place, and as it leaves,
 * puts the registries back as they were loaded: both rewrite, from the client's thread, the registries the server is
 * running on. The snapshot is of these same registries, so while that server runs there is nothing to put in place.
 * (Later Forge syncs registries the game's own way.)
 */
@Mixin(value = GameData.class, remap = false)
abstract class GameDataMixin {
	//? if <1.20.2 {
	/*@Inject(method = "injectSnapshot", at = @At("HEAD"), cancellable = true)
	private static void horsingaroundTest$sameRegistries(final Map<Identifier, ForgeRegistry.Snapshot> snapshot, final boolean injectFrozenData, final boolean isLocalWorld,
			final CallbackInfoReturnable<Multimap<Identifier, Identifier>> cir) {
		if (horsingaroundTest$dedicatedServerInHere()) {
			cir.setReturnValue(ArrayListMultimap.create());
		}
	}

	@Inject(method = "revertToFrozen", at = @At("HEAD"), cancellable = true)
	private static void horsingaroundTest$keepTheServersRegistries(final CallbackInfo ci) {
		if (horsingaroundTest$dedicatedServerInHere()) {
			ci.cancel();
		}
	}

	private static boolean horsingaroundTest$dedicatedServerInHere() {
		return ThreadingImpl.isServerRunning && ThreadingImpl.unsafeClientInstance != null && ThreadingImpl.unsafeClientInstance.getSingleplayerServer() == null;
	}
	*///?}
}
