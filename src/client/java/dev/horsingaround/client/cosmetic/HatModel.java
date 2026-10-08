package dev.horsingaround.client.cosmetic;

import dev.horsingaround.HorsingAround;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.resources.Identifier;

/**
 * The supporters' cowboy hat: a creased crown with a band, and a brim curled up at the sides. Built on the player model
 * (like vanilla's Deadmau5 ears) with only the hat under the head, so it follows the head however the player is posed.
 * Head pixels: the head is 8 wide, from y -8 (top) to 0, and faces -z.
 */
public final class HatModel extends PlayerModel {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(HorsingAround.MOD_ID, "cowboy_hat"), "main");

	public HatModel(final ModelPart root) {
		super(root, false);
	}

	public static LayerDefinition createLayer() {
		final MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
		final PartDefinition head = mesh.getRoot().clearRecursively().getChild("head");
		head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -11.0F, -4.5F, 9.0F, 5.0F, 9.0F), PartPose.ZERO);
		// The crease: two ridges along the top with a groove between, pinched in at the front.
		final CubeListBuilder ridge = CubeListBuilder.create().texOffs(36, 0).addBox(0.0F, -12.0F, -3.5F, 3.5F, 1.0F, 8.0F);
		head.addOrReplaceChild("left_ridge", ridge, PartPose.offset(-4.0F, 0.0F, 0.0F));
		head.addOrReplaceChild("right_ridge", ridge, PartPose.offset(0.5F, 0.0F, 0.0F));
		head.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 14).addBox(-4.5F, -7.0F, -4.5F, 9.0F, 1.0F, 9.0F, new CubeDeformation(0.12F)), PartPose.ZERO);
		head.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 25).addBox(-5.5F, -6.0F, -7.5F, 11.0F, 0.5F, 15.0F), PartPose.ZERO);
		// The sides of the brim curl up.
		head.addOrReplaceChild("right_curl", CubeListBuilder.create().texOffs(0, 41).addBox(0.0F, -0.25F, -7.5F, 3.0F, 0.5F, 15.0F),
			PartPose.offsetAndRotation(5.5F, -5.75F, 0.0F, 0.0F, 0.0F, -0.55F));
		head.addOrReplaceChild("left_curl", CubeListBuilder.create().texOffs(0, 41).addBox(-3.0F, -0.25F, -7.5F, 3.0F, 0.5F, 15.0F),
			PartPose.offsetAndRotation(-5.5F, -5.75F, 0.0F, 0.0F, 0.0F, 0.55F));
		return LayerDefinition.create(mesh, 64, 64);
	}
}
