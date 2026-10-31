package dev.horsingaround;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the mixins into Icy's Better Horses (mixin/icy, client/mixin/icy; see ride/IcyCompat) only when it is
 * installed: without it they'd have nothing to patch (and the mixin audit would look for its classes). Their injections
 * aren't required, as Icy's mod changes often: a hook that stops fitting brings back that part of Icy's riding instead
 * of crashing the game, and the Icy compat test shows it.
 */
public final class IcyMixinPlugin implements IMixinConfigPlugin {
	/** One of Icy's classes, looked up as a file: no mod's classes may be loaded this early. */
	private static final String ICY_CLASS = "icy/betterhorses/net/BhGears.class";

	private List<String> mixins = List.of();

	@Override
	public void onLoad(final String mixinPackage) {
		if (IcyMixinPlugin.class.getClassLoader().getResource(ICY_CLASS) == null) {
			return;
		}
		if (!mixinPackage.endsWith(".client.mixin.icy")) {
			this.mixins = List.of("BhGearsMixin", "SwimBoostMixin");
		} else if (MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.SERVER) {
			this.mixins = List.of("HorseAutodriveControllerMixin", "HorseGearControllerMixin");
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
