package dev.horsingaround.client.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the mixins into Entity Model Features (client/mixin/emf; only on the versions it has no animation hook for) only
 * when it is installed: without it they'd have nothing to patch (and the mixin audit would look for its classes).
 */
public final class EmfMixinPlugin implements IMixinConfigPlugin {
	/** The class they patch, looked up as a file: no mod's classes may be loaded this early. */
	private static final String EMF_CLASS = "traben/entity_model_features/models/parts/EMFModelPartRoot.class";

	private List<String> mixins = List.of();

	@Override
	public void onLoad(final String mixinPackage) {
		//? if >=1.20.2 && <1.21 {
		/*if (EmfMixinPlugin.class.getClassLoader().getResource(EMF_CLASS) != null) {
			this.mixins = List.of("EmfModelPartRootMixin");
		}
		*///?}
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
