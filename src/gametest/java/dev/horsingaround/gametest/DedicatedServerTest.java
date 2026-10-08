package dev.horsingaround.gametest;

import dev.horsingaround.HorsingAround;
import dev.horsingaround.RiderBridge;
import dev.horsingaround.ride.RideState;
import dev.horsingaround.ride.RideStateHolder;
import dev.horsingaround.ride.RideTuning;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * On a dedicated server (no client code at all, like a family server): the mod loads, manages the horse family, and a
 * horse ridden by a server-side rider (so the server simulates the ride with the real ride controller) gallops and
 * tramples chickens in its way. Run with {@code ./gradlew runGameTest}.
 */
public final class DedicatedServerTest {
	/** A rider holding W. */
	private static final RiderBridge FORWARD = new RiderBridge() {
		private final Input forward = new Input(true, false, false, false, false, false, false);

		@Override
		public Input input(final Player rider) {
			return this.forward;
		}

		@Override
		public int spurPresses() {
			return 0;
		}

		@Override
		public int reinPresses() {
			return 0;
		}

		@Override
		public int jumpPresses() {
			return 0;
		}

		@Override
		public void onJump(final Player rider, final float power) {
		}
	};

	@GameTest(maxTicks = 100)
	public void gallopAndTrample(final GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		final Horse horse = helper.spawn(EntityTypes.HORSE, 3.5F, 1.0F, 0.5F);
		horse.setYRot(0.0F);
		horse.setTamed(true);
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		final Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
		rider.snapTo(horse.getX(), horse.getY(), horse.getZ(), 0.0F, 0.0F);
		rider.startRiding(horse, true, false);
		final List<Chicken> chickens = List.of(helper.spawn(EntityTypes.CHICKEN, 3.5F, 1.0F, 3.5F), helper.spawn(EntityTypes.CHICKEN, 3.5F, 1.0F, 5.5F));
		chickens.forEach(chicken -> chicken.setNoAi(true));

		helper.assertTrue(((RideStateHolder) horse).horsingaround$managed(), "the horse family is managed on a dedicated server");
		final RideState s = ((RideStateHolder) horse).horsingaround$ride();
		s.gait = RideTuning.GALLOP;
		s.speed = RideTuning.GAIT_SPEED[RideTuning.GALLOP];
		HorsingAround.bridge = FORWARD;
		helper.succeedWhen(() -> {
			helper.assertTrue(horse.getBbWidth() < 1.0F, "the ridden horse's box narrows on the server");
			helper.assertTrue(horse.getZ() > helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.0, 0.0, 2.0)).z, "the server rides the horse forward");
			for (final Chicken chicken : chickens) {
				helper.assertTrue(!chicken.isAlive() || chicken.getHealth() < chicken.getMaxHealth(), "a galloping horse tramples the chickens in its way");
			}
			HorsingAround.bridge = RiderBridge.NONE;
			rider.stopRiding();
		});
	}
}
