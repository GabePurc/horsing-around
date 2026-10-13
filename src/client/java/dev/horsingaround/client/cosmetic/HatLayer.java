package dev.horsingaround.client.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.horsingaround.HorsingAround;
import dev.horsingaround.net.HatPayload;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
//? if >=1.21.9
import net.minecraft.client.renderer.SubmitNodeCollector;
//? if <1.21.9
/*import net.minecraft.client.renderer.MultiBufferSource;*/
//? if <1.21.2
/*import net.minecraft.client.player.AbstractClientPlayer;*/
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Draws a supporter's cowboy hat in their colour (the felt texture is grey, tinted by the colour). */
//? if >=1.21.2 {
public final class HatLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
//?} else
/*public final class HatLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {*/
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "textures/entity/cowboy_hat.png");

	private final HatModel model;

	//? if >=1.21.2 {
	public HatLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> renderer, final EntityModelSet models) {
	//?} else
	/*public HatLayer(final RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer, final EntityModelSet models) {*/
		super(renderer);
		this.model = new HatModel(models.bakeLayer(HatModel.LAYER));
	}

	//? if >=1.21.9 {
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
			//? if >=26.3 {
			0xFF000000 | color, null, state.outlineColor
			//?} else
			/*0xFF000000 | color, null, state.outlineColor, null*/
		);
	}
	//?} elif >=1.21.2 {
	/*@Override
	public void render(
		final PoseStack poseStack, final MultiBufferSource buffers, final int lightCoords, final AvatarRenderState state, final float yRot, final float xRot
	) {
		final int color = ((HatState) state).horsingaround$hatColor();
		if (color == HatPayload.NONE || state.isInvisible) {
			return;
		}
		this.model.setupAnim(state);
		this.model.renderToBuffer(
			poseStack, buffers.getBuffer(RenderTypes.entityCutout(TEXTURE)), lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0.0F), 0xFF000000 | color
		);
	}
	*///?} elif >=1.21 {
	/*@Override
	public void render(
		final PoseStack poseStack, final MultiBufferSource buffers, final int lightCoords, final AbstractClientPlayer player, final float limbSwing,
		final float limbSwingAmount, final float partialTicks, final float ageInTicks, final float headYaw, final float headPitch
	) {
		final int color = Hats.colorFor(player);
		if (color == HatPayload.NONE || player.isInvisible()) {
			return;
		}
		// The head, as it is posed this frame (a rider's included).
		this.getParentModel().copyPropertiesTo(this.model);
		this.model.renderToBuffer(
			poseStack, buffers.getBuffer(RenderTypes.entityCutout(TEXTURE)), lightCoords, LivingEntityRenderer.getOverlayCoords(player, 0.0F), 0xFF000000 | color
		);
	}
	*///?} else {
	/*@Override
	public void render(
		final PoseStack poseStack, final MultiBufferSource buffers, final int lightCoords, final AbstractClientPlayer player, final float limbSwing,
		final float limbSwingAmount, final float partialTicks, final float ageInTicks, final float headYaw, final float headPitch
	) {
		final int color = Hats.colorFor(player);
		if (color == HatPayload.NONE || player.isInvisible()) {
			return;
		}
		// The head, as it is posed this frame (a rider's included).
		this.getParentModel().copyPropertiesTo(this.model);
		// (Colours are fractions of 1 here.)
		this.model.renderToBuffer(
			poseStack, buffers.getBuffer(RenderTypes.entityCutout(TEXTURE)), lightCoords, LivingEntityRenderer.getOverlayCoords(player, 0.0F),
			(color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F
		);
	}
	*///?}
}
