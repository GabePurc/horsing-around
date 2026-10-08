package dev.horsingaround.client.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.horsingaround.HorsingAround;
import dev.horsingaround.net.HatPayload;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Draws a supporter's cowboy hat in their colour (the felt texture is grey, tinted by the colour). */
public final class HatLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "textures/entity/cowboy_hat.png");

	private final HatModel model;

	public HatLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> renderer, final EntityModelSet models) {
		super(renderer);
		this.model = new HatModel(models.bakeLayer(HatModel.LAYER));
	}

	@Override
	public void submit(
		final PoseStack poseStack, final SubmitNodeCollector collector, final int lightCoords, final AvatarRenderState state, final float yRot, final float xRot
	) {
		final int color = ((HatState) state).horsingaround$hatColor();
		if (color == HatPayload.NONE || state.isInvisible) {
			return;
		}
		collector.submitModel(
			this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0.0F),
			0xFF000000 | color, null, state.outlineColor
		);
	}
}
