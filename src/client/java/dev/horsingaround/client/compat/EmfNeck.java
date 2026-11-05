//? if <1.21 {
/*package dev.horsingaround.client.compat;

import dev.horsingaround.client.render.AnimatedHorseModel;
import dev.horsingaround.ride.RideStateHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

// The ride's neck on a model an animation pack animates, before 1.21 (where the rest of EmfSaddleTracker isn't built): the
// reach on climbs and the head toss, on the pack's own neck, so the head clears the rider; vanilla's, which gets them in
// AbstractEquineModelMixin, is left empty by Fresh Animations. Called once the pack has set the neck, once each time it
// does, for each layer's model: EmfNeckHook or EmfNeckAfterPack.
public final class EmfNeck {
	private boolean looked;
	private @Nullable ModelPart neck;
	private @Nullable ModelPart bridleNeck;

	public void apply(final ModelPart root, final Object entity) {
		if (!(entity instanceof RideStateHolder holder) || !holder.horsingaround$managed()) {
			return;
		}
		if (!this.looked) {
			this.looked = true;
			this.neck = AnimatedHorseModel.find(root, "neck2");
			// (Where Fresh Animations hangs the bridle and reins at these versions, a copy of the neck it sets from "neck2".)
			this.bridleNeck = AnimatedHorseModel.find(root, "headpiece_neck");
		}
		if (this.neck == null) {
			return;
		}
		final float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		AnimatedHorseModel.neck(this.neck, holder.horsingaround$ride(), ((Entity) holder).tickCount + partialTicks, partialTicks);
		if (this.bridleNeck != null) {
			this.bridleNeck.xRot = this.neck.xRot;
			this.bridleNeck.yRot = this.neck.yRot;
			this.bridleNeck.zRot = this.neck.zRot;
		}
	}
}
*///?}
