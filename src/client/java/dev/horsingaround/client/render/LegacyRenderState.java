//? if <1.21.2 {
/*package dev.horsingaround.client.render;

import dev.horsingaround.client.render.state.EquineRenderState;
import dev.horsingaround.client.render.state.HumanoidRenderState;
import dev.horsingaround.client.render.state.LivingEntityRenderState;
import dev.horsingaround.client.render.state.PlayerRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

// Before 1.21.2 models are posed straight from the entity, so the stand-in render state of the entity being drawn (see
// LivingEntityRenderState) is published here for the model and layer hooks to find. Render thread only; the renderer's
// mixin sets it as a ridden horse or its rider starts drawing and clears it when drawing ends.
public final class LegacyRenderState {
	private static @Nullable Entity entity;
	private static @Nullable LivingEntityRenderState state;

	// A model has no root part before 1.21.2, and a foal is the same model drawn at half size (see AgeableListModel): these
	// stand in for the root's place and size in the legs' frame, as the root of a 1.21.2 model is.
	private static final ModelPart ADULT_ROOT = new ModelPart(List.of(), Map.of());
	private static final ModelPart FOAL_ROOT = new ModelPart(List.of(), Map.of());

	static {
		FOAL_ROOT.y = 10.0F;
		FOAL_ROOT.xScale = 0.5F;
		FOAL_ROOT.yScale = 0.5F;
		FOAL_ROOT.zScale = 0.5F;
	}

	private LegacyRenderState() {
	}

	public static ModelPart horseRoot(final boolean foal) {
		return foal ? FOAL_ROOT : ADULT_ROOT;
	}

	// The stand-in state for what this renderer's model draws (made once per renderer).
	public static LivingEntityRenderState create(final EntityModel<?> model) {
		if (model instanceof PlayerModel) {
			return new PlayerRenderState();
		}
		if (model instanceof HumanoidModel) {
			return new HumanoidRenderState();
		}
		return model instanceof HorseModel ? new EquineRenderState() : new LivingEntityRenderState();
	}

	public static void set(final Entity drawn, final LivingEntityRenderState filled) {
		entity = drawn;
		state = filled;
	}

	public static void clear() {
		entity = null;
		state = null;
	}

	// The state of what is being drawn now, if it is one this mod poses.
	public static @Nullable LivingEntityRenderState state() {
		return state;
	}

	// The state of {@code animated}, if it is the entity being drawn now and one this mod poses (a model is only animated
	// for what it draws, so anything else, such as the first-person hands, finds nothing).
	public static @Nullable LivingEntityRenderState of(final Entity animated) {
		return animated == entity ? state : null;
	}

	// The state of the rider being drawn now, if it is one (what the turn of the body and head are taken from).
	public static @Nullable LivingEntityRenderState riderState() {
		return state instanceof RidePoseState pose && pose.horsingaround$isRider() ? state : null;
	}
}
*///?}
