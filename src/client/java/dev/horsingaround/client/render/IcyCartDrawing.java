package dev.horsingaround.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.horsingaround.ride.CartTrail;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Icy's carts as drawn, behind a horse this mod rides. Its wheel animation jolts the cart's body and bed up and down a
 * fixed amount several times a second, more the faster it goes, and its riders with them; here the cart goes only where
 * its wheels put it (user: "smooth them out fully"), each wheel turning by how far it really rolled, backwards too, and
 * riding on its own suspension. It also leans over (CartTrail). Render thread only. These reach into Icy's mod and GeckoLib, and miss quietly if they change.
 */
public final class IcyCartDrawing {
	/** Bones the wheel animation moves, Icy's small cart's and its wagon's: all to be still, the wheels moving on their own. */
	private static final String[] STILL = {"cart", "cartbody", "cart body", "sides", "axle", "wheels", "back axle", "front axle", "backwheels", "front wheels"};
	/** The wheels, by CartTrail's FRONT_LEFT..REAR_RIGHT: in both models 2 and 3 are the rear pair, the wagon's 4 and 5 its front (the odd one on the left). */
	private static final String[] WHEELS = {"wheel5", "wheel4", "wheel3", "wheel2"};
	private static final int PIXELS = 16;
	/**
	 * What's hitched to the horse, in Icy's models (pixels, up and behind the cart's origin): the bone, its hinge's height and
	 * distance, and its far end's. Each swings on its hinge and stretches a little to keep that end at the same place on the
	 * horse, wherever the cart has got to (user, 2026-11-06: what's attached to the horse stays attached).
	 */
	private static final String[] SHAFTS = {"Horseleatherbrace", "leather holder", "wooden sides"};
	private static final float[][] SHAFT_AT = {{19.85F, 17.25F, 19.5F, -3.0F}, {24.0F, 17.0F, 24.5F, -3.0F}, {11.0F, 17.5F, 16.0F, -6.0F}};
	private static final float SHAFT_STRETCH_LEAST = 0.7F;
	private static final float SHAFT_STRETCH_MOST = 3.0F;
	/** How far a shaft's end has been short of where it should be on the horse since last reset (blocks, for tests). */
	public static double shaftMiss;
	private static final Quaternionf LEAN = new Quaternionf();

	private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
	private static final @Nullable Class<?> BONE = find(() -> Class.forName("com.geckolib.animation.state.BoneSnapshot"));
	/** (Object snapshots, String) Optional: a bone by name. */
	private static final @Nullable MethodHandle GET = find(() -> LOOKUP.findVirtual(Class.forName("com.geckolib.renderer.base.BoneSnapshots"), "get", MethodType.methodType(Optional.class, String.class))
		.asType(MethodType.methodType(Optional.class, Object.class, String.class)));
	private static final @Nullable MethodHandle SET_Y = setter("setTranslateY");
	private static final @Nullable MethodHandle SET_ROT_X = setter("setRotX");
	private static final @Nullable MethodHandle SET_ROT_Y = setter("setRotY");
	private static final @Nullable MethodHandle SET_SCALE_Z = setter("setScaleZ");
	/** (Object pass) Object: the cart's id, from what Icy's renderer gave the pass. */
	private static final @Nullable MethodHandle PASS_CART = find(() -> {
		final Field id = Class.forName("icy.betterhorses.net.client.render.HorseCartRenderer").getDeclaredField("CART_ID");
		id.setAccessible(true);
		final Class<?> ticket = Class.forName("com.geckolib.constant.dataticket.DataTicket");
		final MethodHandle data = LOOKUP.findVirtual(Class.forName("com.geckolib.renderer.base.RenderPassInfo"), "getOrDefaultGeckolibData", MethodType.methodType(Object.class, ticket, Object.class));
		return MethodHandles.insertArguments(data, 1, id.get(null), -1).asType(MethodType.methodType(Object.class, Object.class));
	});

	private IcyCartDrawing() {
	}

	/** Before the bones are drawn: the cart's only move is its wheels turning. */
	public static void bones(final int id, final Object snapshots) {
		final CartTrail.Lean cart = cart(id);
		if (cart == null || GET == null || SET_Y == null || SET_ROT_X == null || SET_ROT_Y == null || SET_SCALE_Z == null) {
			return;
		}
		final float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		// (Forward, the top of a wheel goes forward: the way GeckoLib's negative rotation turns a bone.)
		final float turned = (float) -(cart.horsingaround$turned(partialTick) % (Math.PI * 2.0));
		try {
			for (final String name : STILL) {
				pose(snapshots, name, 0.0F, 0.0F);
			}
			for (int i = 0; i < WHEELS.length; i++) {
				pose(snapshots, WHEELS[i], cart.horsingaround$travel(i, partialTick) * PIXELS, turned);
			}
			for (int i = 0; i < SHAFTS.length; i++) {
				shaft(snapshots, SHAFTS[i], SHAFT_AT[i], cart, partialTick);
			}
		} catch (final Throwable e) {
			// (GeckoLib changed: the cart bounces as Icy's mod has it.)
		}
	}

	private static void pose(final Object snapshots, final String name, final float y, final float rotX) throws Throwable {
		final Optional<?> bone = (Optional<?>) GET.invokeExact(snapshots, name);
		if (bone.isPresent()) {
			final Object snapshot = bone.get();
			SET_Y.invokeExact(snapshot, y);
			SET_ROT_X.invokeExact(snapshot, rotX);
		}
	}

	/** Swings a shaft on its hinge, in the cart's own frame, so its far end is where it is on a level horse straight ahead. */
	private static void shaft(final Object snapshots, final String name, final float[] at, final CartTrail.Lean cart, final float partialTick) throws Throwable {
		final Optional<?> bone = (Optional<?>) GET.invokeExact(snapshots, name);
		if (bone.isEmpty()) {
			return;
		}
		final double hingeY = at[0] / PIXELS;
		final double hingeZ = at[1] / PIXELS;
		final double endY = at[2] / PIXELS;
		final double endZ = at[3] / PIXELS;
		// Where that end belongs from the cart's origin, in the world: on the horse's line, as far behind its middle as it is level.
		final float yaw = cart.horsingaround$yaw(partialTick) * Mth.DEG_TO_RAD;
		final float horseYaw = cart.horsingaround$horseYaw(partialTick) * Mth.DEG_TO_RAD;
		final double behind = cart.horsingaround$behind(partialTick);
		final double onHorse = cart.horsingaround$standing() + endZ;
		final double worldX = Mth.sin(horseYaw) * onHorse - Mth.sin(yaw) * behind;
		final double worldZ = -Mth.cos(horseYaw) * onHorse + Mth.cos(yaw) * behind;
		final double worldY = cart.horsingaround$belowHitch(partialTick) + endY;
		// In the cart's frame: turned (+x its right, +z behind), then untilted, then unleaned.
		final double turnedX = -(worldX * Mth.cos(yaw) + worldZ * Mth.sin(yaw));
		final double turnedZ = worldX * Mth.sin(yaw) - worldZ * Mth.cos(yaw);
		final float tilt = cart.horsingaround$tilt(partialTick) * Mth.DEG_TO_RAD;
		final double untiltedY = worldY * Mth.cos(tilt) + turnedZ * Mth.sin(tilt);
		final double z = -worldY * Mth.sin(tilt) + turnedZ * Mth.cos(tilt);
		final float roll = cart.horsingaround$roll(partialTick) * Mth.DEG_TO_RAD;
		final double x = turnedX * Mth.cos(roll) - untiltedY * Mth.sin(roll);
		final double y = turnedX * Mth.sin(roll) + untiltedY * Mth.cos(roll);
		// From the hinge, against how it lies at rest.
		final double dy = y - hingeY;
		final double dz = z - hingeZ;
		final double length = Math.sqrt(x * x + dy * dy + dz * dz);
		final double restY = endY - hingeY;
		final double restLength = Math.sqrt(restY * restY + (endZ - hingeZ) * (endZ - hingeZ));
		final float stretch = (float) Mth.clamp(length / restLength, SHAFT_STRETCH_LEAST, SHAFT_STRETCH_MOST);
		shaftMiss = Math.max(shaftMiss, Math.abs(length - restLength * stretch));
		final Object snapshot = bone.get();
		SET_Y.invokeExact(snapshot, 0.0F);
		SET_ROT_X.invokeExact(snapshot, (float) (Math.asin(Mth.clamp(dy / length, -1.0, 1.0)) - Math.asin(restY / restLength)));
		SET_ROT_Y.invokeExact(snapshot, (float) Mth.atan2(-x, -dz));
		SET_SCALE_Z.invokeExact(snapshot, stretch);
	}

	/** Once Icy's renderer has turned and tilted the cart (its +x now to the cart's right, +z behind): the lean over. */
	public static void lean(final Object pass, final PoseStack poseStack) {
		if (PASS_CART == null) {
			return;
		}
		try {
			final CartTrail.Lean cart = cart((Integer) (Object) PASS_CART.invokeExact(pass));
			if (cart != null) {
				final float roll = cart.horsingaround$roll(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
				if (roll != 0.0F) {
					poseStack.rotateAround(LEAN.rotationZ(-roll * Mth.DEG_TO_RAD), 0.0F, 0.0F, 0.0F);
				}
			}
		} catch (final Throwable e) {
			// (GeckoLib or Icy's mod changed: it doesn't lean.)
		}
	}

	private static CartTrail.@Nullable Lean cart(final int id) {
		final net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
		return level != null && id >= 0 && level.getEntity(id) instanceof CartTrail.Lean cart && cart.horsingaround$rolling() ? cart : null;
	}

	private static @Nullable MethodHandle setter(final String name) {
		return find(() -> {
			final MethodHandle set = LOOKUP.findVirtual(BONE, name, MethodType.methodType(BONE, float.class));
			return MethodHandles.dropReturn(set).asType(MethodType.methodType(void.class, Object.class, float.class));
		});
	}

	/** A lookup into Icy's mod or GeckoLib, which may throw if they changed. */
	private interface Find<T> {
		T find() throws ReflectiveOperationException;
	}

	private static <T> @Nullable T find(final Find<T> lookup) {
		try {
			return lookup.find();
		} catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}
}
