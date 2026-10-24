package dev.horsingaround.client.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.EntityModel;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.EMFAnimationApi;

/**
 * Whether an animation pack (Fresh Animations, through Entity Model Features) animates a model: then the pack poses it,
 * and the plain model's own gait (see {@code Strides}) stands aside.
 */
public final class AnimationPacks {
	/** Entity Model Features is installed. */
	private static final boolean EMF = FabricLoader.getInstance().isModLoaded("entity_model_features");
	/** Set once a call didn't fit: every model is then taken as the pack's, as the safe side. */
	private static boolean unknown;

	private AnimationPacks() {
	}

	public static boolean animates(final Object model) {
		if (unknown) {
			return true;
		}
		if (EMF) {
			try {
				return Emf.animates(model);
			} catch (final LinkageError e) {
				unknown = true;
				LoggerFactory.getLogger("horsingaround").warn("Entity Model Features' API isn't one this version knows; horses keep its animation and not this mod's gaits", e);
				return true;
			}
		}
		return false;
	}

	/** Only loaded when Entity Model Features is. */
	private static final class Emf {
		static boolean animates(final Object model) {
			return model instanceof EntityModel<?> entityModel && EMFAnimationApi.isModelAnimatedByEMF(entityModel);
		}
	}
}
