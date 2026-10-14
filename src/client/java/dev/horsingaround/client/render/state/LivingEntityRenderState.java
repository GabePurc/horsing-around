//? if <1.21.2 {
/*package dev.horsingaround.client.render.state;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;

// Before 1.21.2 entities are drawn straight from the entity, with no render state. This stands in for the state
// (see docs/porting.md): one per renderer, reused every frame, holding only what this mod's code reads of vanilla's
// LivingEntityRenderState, filled from the entity just before the renderer draws it.
public class LivingEntityRenderState {
	public double x;
	public double y;
	public double z;
	public float bodyRot;
	public float yRot;

	public void fill(final LivingEntity entity, final float partialTicks) {
		this.x = Mth.lerp((double) partialTicks, entity.xOld, entity.getX());
		this.y = Mth.lerp((double) partialTicks, entity.yOld, entity.getY());
		this.z = Mth.lerp((double) partialTicks, entity.zOld, entity.getZ());
		final float head = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
		this.bodyRot = solveBodyRot(entity, head, partialTicks);
		this.yRot = Mth.wrapDegrees(head - this.bodyRot);
	}

	// As 1.21.2's renderer (and, as local variables, this version's) works out the body's turn: a rider's body follows the
	// vehicle's, within what the head can turn from it.
	private static float solveBodyRot(final LivingEntity entity, final float head, final float partialTicks) {
		if (entity.getVehicle() instanceof LivingEntity vehicle) {
			float body = Mth.rotLerp(partialTicks, vehicle.yBodyRotO, vehicle.yBodyRot);
			final float turn = Mth.clamp(Mth.wrapDegrees(head - body), -85.0F, 85.0F);
			body = head - turn;
			if (Math.abs(turn) > 50.0F) {
				body += turn * 0.2F;
			}
			return body;
		}
		return Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
	}
}
*///?}
