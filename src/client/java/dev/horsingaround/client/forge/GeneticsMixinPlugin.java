package dev.horsingaround.client.forge;

import dev.horsingaround.ride.Genetics;
import java.util.List;
import java.util.Set;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the mixins into Realistic Horse Genetics (client/mixin/rhg) only when it is installed, and on a client: without
 * it they'd have nothing to patch (and the mixin audit would look for its classes). Their injections aren't required: a
 * hook that stops fitting a changed version of it leaves its horses' legs to its own model instead of crashing the game,
 * and the audit (which runs with it) shows it.
 */
public final class GeneticsMixinPlugin implements IMixinConfigPlugin {
	private List<String> mixins = List.of();

	@Override
	public void onLoad(final String mixinPackage) {
		// (Mods are known by now, though none is loaded: its mod file, not its classes.)
		if (LoadingModList.get().getModFileById(Genetics.MOD_ID) != null && MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.SERVER) {
			this.mixins = List.of("HorseGeneticModelMixin");
		}
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return this.mixins;
	}

	@Override
	public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
	}
}
