package dev.horsingaround.smoke;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.function.Consumer;
//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//?} else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
*///?}
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.slf4j.Logger;

/**
 * Smoke test for the whole mod on any Minecraft version (./gradlew runSmoke): a client tick state machine that makes a
 * flat world, rides a horse through a stand, a walk, a gallop, a jump and a stop with simulated keys, and takes
 * screenshots from the side and as the rider, then of a row of leashed mobs and a cow on the player's own lead. Only
 * active with -Dhorsingaround.smoke=true. It logs "Horsing Around smoke test passed" or "... failed" and quits; the
 * Gradle task checks the log.
 *
 * <p>Vanilla draws the local player only through the camera it is the camera entity of, so the side views of the rider
 * are the player's own third-person camera turned a quarter turn: for each frame after a tick the player's view yaw is
 * 90 degrees off (the camera stands at the horse's side, at the distance the mod's riding camera keeps) and tipped down,
 * and before the next tick it is put back, so the ride itself never sees it. Views of anything else (the row of mobs) are from a
 * marker entity that is not in the world.
 */
//? if fabric {
public final class SmokeTest implements ClientModInitializer {
//?} else {
/*@Mod(value = "horsingaround_smoke", dist = Dist.CLIENT)
public final class SmokeTest {
*///?}
	private static final Logger LOGGER = LogUtils.getLogger();

	private static final String WORLD = "smoke";
	private static final long SEED = 20261014L;
	/** The whole run, in client ticks (a stuck phase fails instead of hanging). */
	private static final int MAX_TICKS = 9000;
	/** -PsmokeWithoutMod: the same scene with this mod turned off, to see what the game and the animation stack do alone. */
	private static final boolean WITHOUT_MOD = Boolean.getBoolean("horsingaround.smoke.withoutMod");
	/**
	 * -Dhorsingaround.smoke.wild=seed: instead of the run on the flat, a long ride over a normal world of that seed (a
	 * gallop with turns and jumps, wherever it leads), for the server corrections only natural ground brings out.
	 */
	private static final String WILD = System.getProperty("horsingaround.smoke.wild");
	private static final int WILD_TICKS = 2400;

	private static final int FOV = 70;
	/**
	 * The side views of the rider: a narrower view, tipped down so the hooves and the ground are in it. The riding camera
	 * keeps farther from a fast horse, so the view is wider when the horse stands and narrower at a gallop.
	 */
	private static final int SIDE_FOV_STILL = 74;
	private static final int SIDE_FOV_WALK = 64;
	private static final int SIDE_FOV_GALLOP = 52;
	private static final float SIDE_PITCH = 14.0F;
	/** Ticks a key stays down for a tap (the mod reads the click, vanilla the held key). */
	private static final int TAP_TICKS = 3;

	private enum Phase {
		TITLE, LOADING, BUILD, SETTLE, STAND, WALK, GALLOP, JUMP, STOP, RIDER, ROUGH, WILD, DISMOUNT, FENCE, LEAD, FINISH
	}

	private static final String[] SHOTS = {
		"01-stand.png", "02-walk.png", "03-gallop.png", "04-jump.png", "05-stopped.png", "06-first-person.png",
		"07-third-person-back.png", "08-fence-front.png", "09-fence-side.png", "10-lead-cow.png", "11-lead-llama.png", "12-lead-horse.png",
		"13-player-lead.png", "14-rough-slab.png", "15-rough-stairs.png", "16-rough-uneven.png", "17-rough-gallop.png"
	};

	private final Meter meter = new Meter();
	private Phase phase = Phase.TITLE;
	/** Ticks spent in the phase (counted from 0 in its first tick). */
	private int t;
	private int total;
	/** Counts down to quitting once the run is over (leaves the screenshots time to be written). */
	private int exitIn = -1;
	private Minecraft minecraft;
	private MinecraftServer server;
	private Entity horse;
	private Marker marker;
	/** Whether frames show the rider from the side (see the class comment), and the real view while they do. */
	private boolean sideView;
	private boolean viewTurned;
	/** The real yaw, head yaw and pitch (and last tick's) while the view is turned: the camera follows the head. */
	private final float[] realView = new float[6];
	private KeyMapping tapped;
	private int releaseAt;
	// Measurements across phases.
	private double walkSpeed;
	private double gallopSpeed;
	private double jumpHeight;
	private int airTicks;
	private int stillTicks;
	private int roughStage;
	// A job running on the server thread.
	private volatile boolean jobDone;
	private volatile Throwable jobError;

	//? if fabric {
	@Override
	public void onInitializeClient() {
		if (Boolean.getBoolean("horsingaround.smoke")) {
			ClientTickEvents.START_CLIENT_TICK.register(this::turnBack);
			ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		}
	}
	//?} else {
	/*public SmokeTest() {
		if (Boolean.getBoolean("horsingaround.smoke")) {
			NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, event -> turnBack(Minecraft.getInstance()));
			NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick(Minecraft.getInstance()));
		}
	}
	*///?}

	/** Before a tick the rider's view is the real one again. */
	private void turnBack(final Minecraft minecraft) {
		if (this.viewTurned) {
			this.setView(minecraft.player, this.realView[0], this.realView[1], this.realView[2], this.realView[3], this.realView[4], this.realView[5]);
			this.viewTurned = false;
		}
	}

	private void setView(final LocalPlayer player, final float yaw, final float yawOld, final float head, final float headOld, final float pitch, final float pitchOld) {
		player.setYRot(yaw);
		player.yRotO = yawOld;
		player.yHeadRot = head;
		player.yHeadRotO = headOld;
		player.setXRot(pitch);
		player.xRotO = pitchOld;
	}

	private void tick(final Minecraft minecraft) {
		this.minecraft = minecraft;
		if (this.exitIn >= 0) {
			if (this.exitIn-- == 0) {
				minecraft.stop();
			}
			return;
		}
		try {
			if (++this.total > MAX_TICKS) {
				throw new IllegalStateException("Timed out in phase " + this.phase);
			}
			if (this.tapped != null && this.total >= this.releaseAt) {
				this.tapped.setDown(false);
				this.tapped = null;
			}
			this.step(minecraft, this.t++);
			if (this.sideView && minecraft.player != null) {
				final LocalPlayer player = minecraft.player;
				this.realView[0] = player.getYRot();
				this.realView[1] = player.yRotO;
				this.realView[2] = player.yHeadRot;
				this.realView[3] = player.yHeadRotO;
				this.realView[4] = player.getXRot();
				this.realView[5] = player.xRotO;
				this.setView(player, this.realView[0] + 90.0F, this.realView[0] + 90.0F, this.realView[2] + 90.0F, this.realView[2] + 90.0F, SIDE_PITCH, SIDE_PITCH);
				this.viewTurned = true;
			}
		} catch (final Throwable error) {
			this.fail(minecraft, error);
		}
	}

	private void step(final Minecraft minecraft, final int t) {
		switch (this.phase) {
			case TITLE -> {
				// The title screen, once the start-up overlay is gone: out with the old world, in with a fresh one.
				if (Compat.screen(minecraft) instanceof TitleScreen && !Compat.overlayShown(minecraft)) {
					if (t >= 20) {
						deleteWorld(minecraft.gameDirectory.toPath().resolve("saves").resolve(WORLD));
						if (WILD == null) {
							Compat.createFlatWorld(minecraft, WORLD, SEED);
						} else {
							Compat.createWorld(minecraft, WORLD, Long.parseLong(WILD), WorldPresets.NORMAL);
						}
						this.next(Phase.LOADING);
					}
				} else {
					this.t = 0;
				}
			}
			case LOADING -> {
				this.server = minecraft.getSingleplayerServer();
				final boolean ready = minecraft.level != null && minecraft.player != null && this.server != null
					&& Compat.screen(minecraft) == null
					&& minecraft.level.getChunkSource().hasChunk(minecraft.player.blockPosition().getX() >> 4, minecraft.player.blockPosition().getZ() >> 4);
				if (!ready) {
					this.t = 0;
				} else if (t >= 60) {
					this.next(Phase.BUILD);
				}
			}
			case BUILD -> {
				if (t == 0) {
					this.job(WILD == null ? Scene::build : Scene::wild);
				} else if (this.jobFinished()) {
					this.next(Phase.SETTLE);
				}
			}
			case SETTLE -> {
				// The horse and the scene reach the client; the rider is on the horse.
				if (t >= 40) {
					final Entity vehicle = minecraft.player.getVehicle();
					if (!(vehicle instanceof AbstractHorse)) {
						throw new IllegalStateException("The player is not riding a horse: " + vehicle);
					}
					this.horse = vehicle;
					LOGGER.info("smoke: riding {} at {} {} {}, ground y {}", vehicle.getType(), fmt(vehicle.getX()), fmt(vehicle.getY()), fmt(vehicle.getZ()), Scene.groundY);
					this.next(WILD == null ? Phase.STAND : Phase.WILD);
				}
			}
			case STAND -> {
				if (t == 0) {
					this.camera(minecraft, minecraft.player, CameraType.THIRD_PERSON_BACK, SIDE_FOV_STILL, false);
					this.sideView = true;
					this.meter.reset();
				}
				this.meter.sample(this.horse);
				if (t == 32) {
					this.shoot(minecraft, SHOTS[0]);
				} else if (t >= 40) {
					this.report("stand", t);
					this.next(Phase.WALK);
				}
			}
			case WALK -> {
				if (t == 0) {
					minecraft.options.keyUp.setDown(true);
					minecraft.options.fov().set(SIDE_FOV_WALK);
					this.meter.reset();
				}
				this.meter.sample(this.horse);
				if (t == 48) {
					this.shoot(minecraft, SHOTS[1]);
				} else if (t >= 60) {
					this.report("walk", t);
					this.walkSpeed = this.meter.tail();
					if (this.walkSpeed < 0.03) {
						throw new IllegalStateException("The horse hardly moves at a walk: " + fmt(this.walkSpeed));
					}
					this.next(Phase.GALLOP);
				}
			}
			case GALLOP -> {
				if (t == 0) {
					minecraft.options.fov().set(SIDE_FOV_GALLOP);
					this.meter.reset();
				}
				// Three taps of the sprint key a few ticks apart, the way a player taps: walk to trot, canter and gallop.
				// (All three at once, the mod sometimes counted two.)
				if (t == 0 || t == 4 || t == 8) {
					this.tap(minecraft.options.keySprint, 1);
				}
				this.meter.sample(this.horse);
				if (t == 92) {
					this.shoot(minecraft, SHOTS[2]);
				} else if (t >= 100) {
					this.report("gallop", t);
					this.gallopSpeed = this.meter.tail();
					if (!WITHOUT_MOD && this.gallopSpeed < this.walkSpeed * 2.8) {
						throw new IllegalStateException("The horse never reached a gallop: " + fmt(this.gallopSpeed) + " against " + fmt(this.walkSpeed));
					}
					this.next(Phase.JUMP);
				}
			}
			case JUMP -> this.jump(minecraft, t);
			case STOP -> this.stop(minecraft, t);
			case ROUGH -> this.rough(minecraft, t);
			case WILD -> this.wild(minecraft, t);
			case RIDER -> {
				// The rider's own views, with the HUD.
				if (t == 0) {
					this.sideView = false;
					this.camera(minecraft, minecraft.player, CameraType.FIRST_PERSON, FOV, true);
				} else if (t == 12) {
					this.shoot(minecraft, SHOTS[5]);
					minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				} else if (t == 50) {
					this.shoot(minecraft, SHOTS[6]);
				} else if (t >= 54) {
					this.next(Phase.ROUGH);
				}
			}
			case DISMOUNT -> {
				if (t == 0) {
					this.job(Scene::leadSpot);
				} else if (t == 30) {
					this.jobFinished();
					this.job(Scene::leadCow);
				} else if (t > 30 && this.jobFinished() && minecraft.player.getVehicle() == null && t >= 60) {
					LOGGER.info("smoke: dismounted at {} {} {}", fmt(minecraft.player.getX()), fmt(minecraft.player.getY()), fmt(minecraft.player.getZ()));
					this.next(Phase.FENCE);
				}
			}
			case FENCE -> this.fence(minecraft, t);
			case LEAD -> {
				// The player's own third-person view, from behind: the cow on its lead is on the right.
				if (t == 0) {
					this.camera(minecraft, minecraft.player, CameraType.THIRD_PERSON_BACK, FOV, false);
				} else if (t == 30) {
					this.shoot(minecraft, SHOTS[12]);
				} else if (t >= 33) {
					this.next(Phase.FINISH);
				}
			}
			case FINISH -> {
				if (WILD != null) {
					LOGGER.info("Horsing Around smoke test passed: a wild ride of {} ticks", WILD_TICKS);
					this.exitIn = 10;
					return;
				}
				// Every screenshot is on disk (they are written in the background).
				int missing = 0;
				for (final String shot : SHOTS) {
					if (!new File(minecraft.gameDirectory, "screenshots/" + shot).isFile()) {
						missing++;
					}
				}
				if (missing == 0) {
					LOGGER.info(
						"Horsing Around smoke test passed: {} screenshots; speed in blocks per tick: walk {}, gallop {}; jump {} blocks high",
						SHOTS.length, fmt(this.walkSpeed), fmt(this.gallopSpeed), fmt(this.jumpHeight)
					);
					this.exitIn = 10;
				} else if (t >= 200) {
					throw new IllegalStateException(missing + " screenshots were not written");
				}
			}
		}
	}

	/** Galloping on: a tap of jump, a shot six ticks after the hooves leave the ground, then the landing. */
	private void jump(final Minecraft minecraft, final int t) {
		if (t == 0) {
			this.meter.reset();
			this.airTicks = -1;
			this.jumpHeight = 0.0;
			this.tap(minecraft.options.keyJump, 1);
		}
		this.meter.sample(this.horse);
		final double height = this.horse.getY() - Scene.groundY;
		if (!this.horse.onGround()) {
			this.airTicks++;
			this.jumpHeight = Math.max(this.jumpHeight, height);
			if (this.airTicks == 6) {
				this.shoot(minecraft, SHOTS[3]);
				LOGGER.info("smoke: jump, 6 ticks in the air: {} above ground, speed {}", fmt(height), fmt(this.meter.latest()));
			}
		} else if (this.airTicks > 6) {
			this.report("jump", t);
			this.next(Phase.STOP);
		} else if (t > 40 && WITHOUT_MOD) {
			this.next(Phase.STOP);
		} else if (t > 40) {
			throw new IllegalStateException("The horse did not jump (air ticks " + this.airTicks + ")");
		}
		if (this.airTicks > 80) {
			throw new IllegalStateException("The horse never came down");
		}
	}

	/**
	 * Over the rough course (see {@link Scene#rough}): walking up and down steps and stairs, cantering through leaves and
	 * over uneven ground, galloping over single steps, with side shots of the legs on the way; then a stop. It must get
	 * across (not stuck on a step), and the run fails on any move the server corrected (see gradle/smoke-test.gradle).
	 */
	private void rough(final Minecraft minecraft, final int t) {
		final double z = this.horse.getZ();
		this.meter.sample(this.horse);
		if (t == 0) {
			this.meter.reset();
			this.job(Scene::rough);
			this.camera(minecraft, minecraft.player, CameraType.THIRD_PERSON_BACK, SIDE_FOV_WALK, false);
			this.sideView = true;
			this.roughStage = 0;
			return;
		}
		if (this.roughStage == 0) {
			if (this.jobFinished()) {
				minecraft.options.keyUp.setDown(true);
				this.roughStage = 1;
			}
			return;
		}
		if (this.roughStage == 1 && z > Scene.ROUGH_START + 0.2) {
			this.shoot(minecraft, SHOTS[13]);
			this.roughStage = 2;
		} else if (this.roughStage == 2 && z > 117.6) {
			this.shoot(minecraft, SHOTS[14]);
			this.job(Scene::roughFar);
			this.roughStage = 3;
		} else if (this.roughStage == 3 && z > 126.0 && this.jobFinished()) {
			// Cantering from here.
			this.tap(minecraft.options.keySprint, 1);
			this.roughStage = 4;
		} else if (this.roughStage == 4 && t % 4 == 0 && this.tapped == null) {
			this.tap(minecraft.options.keySprint, 1);
			this.roughStage = 5;
		} else if (this.roughStage == 5 && z > 146.0) {
			this.shoot(minecraft, SHOTS[15]);
			minecraft.options.fov().set(SIDE_FOV_GALLOP);
			this.roughStage = 6;
		} else if (this.roughStage == 6 && z > 160.0 && this.tapped == null) {
			this.tap(minecraft.options.keySprint, 1);
			this.roughStage = 7;
		} else if (this.roughStage == 7 && z > 174.3) {
			this.shoot(minecraft, SHOTS[16]);
			this.roughStage = 8;
		} else if (this.roughStage == 8 && z > Scene.ROUGH_END) {
			this.report("rough", t);
			minecraft.options.keyUp.setDown(false);
			this.roughStage = 9;
		} else if (this.roughStage == 9 && this.meter.latest() < 0.01) {
			this.next(Phase.DISMOUNT);
		}
		if (t >= 2400) {
			throw new IllegalStateException("The horse got stuck on the rough course at z " + fmt(z) + " (stage " + this.roughStage + ")");
		}
	}

	/** The wild ride: a gallop, turning a little every few seconds and jumping now and then, wherever that leads. */
	private void wild(final Minecraft minecraft, final int t) {
		final LocalPlayer player = minecraft.player;
		if (t == 0) {
			this.camera(minecraft, player, CameraType.THIRD_PERSON_BACK, FOV, false);
			minecraft.options.keyUp.setDown(true);
			this.meter.reset();
		}
		if ((t == 4 || t == 8 || t == 12) && this.tapped == null) {
			this.tap(minecraft.options.keySprint, 1);
		} else if (t > 20 && t % 110 == 55 && this.tapped == null) {
			this.tap(minecraft.options.keyJump, 1);
		}
		if (t > 0 && t % 160 == 0) {
			final float yaw = player.getYRot() + (t % 480 == 0 ? -100.0F : 65.0F);
			this.setView(player, yaw, yaw, yaw, yaw, player.getXRot(), player.getXRot());
		}
		this.meter.sample(this.horse);
		if (t % 100 == 0) {
			LOGGER.info("smoke wild: t{} at {} {} {}, speed {}, onGround {}", t, fmt(this.horse.getX()), fmt(this.horse.getY()), fmt(this.horse.getZ()),
				fmt(this.meter.latest()), this.horse.onGround());
		}
		if (t >= WILD_TICKS) {
			minecraft.options.keyUp.setDown(false);
			this.report("wild", t);
			this.next(Phase.FINISH);
		}
	}

	/** Forward released: the horse rolls to a stop. */
	private void stop(final Minecraft minecraft, final int t) {
		if (t == 0) {
			minecraft.options.keyUp.setDown(false);
			minecraft.options.fov().set(SIDE_FOV_STILL);
			this.meter.reset();
			this.stillTicks = 0;
		}
		this.meter.sample(this.horse);
		this.stillTicks = this.meter.latest() < 0.01 ? this.stillTicks + 1 : 0;
		if (this.stillTicks == 10) {
			this.shoot(minecraft, SHOTS[4]);
		} else if (this.stillTicks >= 14) {
			this.report("stop", t);
			this.next(Phase.RIDER);
		} else if (t >= 300) {
			throw new IllegalStateException("The horse did not come to a stop");
		}
	}

	/**
	 * The row of leashed mobs: from the front, from the side, and the leads of the cow, the llama and the horse close up
	 * (the first two in profile, the horse from the front at an angle).
	 */
	private void fence(final Minecraft minecraft, final int t) {
		final double y = Scene.groundY;
		final double mobZ = Scene.ROW_MOB_Z;
		final double leadZ = (mobZ + Scene.ROW_Z) / 2.0;
		if (t == 0) {
			this.camera(minecraft, this.marker(minecraft), CameraType.FIRST_PERSON, 65, false);
			this.aim(0.5, y + 1.7, mobZ + 8.0, 0.5, y + 1.0, mobZ - 1.0);
		} else if (t == 12) {
			this.shoot(minecraft, SHOTS[7]);
			this.aim(13.5, y + 1.6, mobZ + 3.5, 0.5, y + 0.9, mobZ - 1.0);
		} else if (t == 24) {
			this.shoot(minecraft, SHOTS[8]);
			minecraft.options.fov().set(40);
			this.aim(-14.0, y + 1.1, leadZ, Scene.ROW_X[0] + 0.5, y + 0.9, leadZ);
		} else if (t == 36) {
			this.shoot(minecraft, SHOTS[9]);
			this.aim(15.0, y + 1.1, leadZ, Scene.ROW_X[4] + 0.5, y + 1.0, leadZ);
		} else if (t == 48) {
			this.shoot(minecraft, SHOTS[10]);
			this.aim(Scene.ROW_X[3] + 2.5, y + 1.6, mobZ + 3.0, Scene.ROW_X[3] + 0.5, y + 1.1, mobZ - 0.6);
		} else if (t == 60) {
			this.shoot(minecraft, SHOTS[11]);
		} else if (t >= 63) {
			this.next(Phase.LEAD);
		}
	}

	// ---- Camera ----

	/** Sets who the camera is, its perspective and field of view, and whether the HUD is up. */
	private void camera(final Minecraft minecraft, final Entity entity, final CameraType type, final int fov, final boolean hud) {
		minecraft.setCameraEntity(entity);
		minecraft.options.setCameraType(type);
		minecraft.options.fov().set(fov);
		Compat.setHudHidden(minecraft, !hud);
	}

	/** The marker the still views are taken from: not in the world, so nothing draws it. */
	private Marker marker(final Minecraft minecraft) {
		if (this.marker == null) {
			this.marker = Compat.newMarker(minecraft.level);
		}
		return this.marker;
	}

	/** A still camera at a point, looking at another. */
	private void aim(final double x, final double y, final double z, final double targetX, final double targetY, final double targetZ) {
		final double dx = targetX - x;
		final double dz = targetZ - z;
		final float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		final float pitch = (float) -Math.toDegrees(Math.atan2(targetY - y, Math.sqrt(dx * dx + dz * dz)));
		this.marker.setPos(x, y, z);
		this.marker.setOldPosAndRot();
		this.marker.setYRot(yaw);
		this.marker.setXRot(pitch);
		this.marker.yRotO = yaw;
		this.marker.xRotO = pitch;
	}

	private void shoot(final Minecraft minecraft, final String name) {
		Compat.screenshot(minecraft, name);
	}

	// ---- Keys ----

	/** A press the way the keyboard makes one: the click the mod and vanilla count, and the key held for a moment. */
	private void tap(final KeyMapping key, final int clicks) {
		for (int i = 0; i < clicks; i++) {
			KeyMapping.click(key.getDefaultKey());
		}
		key.setDown(true);
		this.tapped = key;
		this.releaseAt = this.total + TAP_TICKS;
	}

	// ---- Server jobs ----

	private void job(final Consumer<MinecraftServer> job) {
		this.jobDone = false;
		this.jobError = null;
		final MinecraftServer target = this.server;
		target.execute(() -> {
			try {
				job.accept(target);
			} catch (final Throwable error) {
				this.jobError = error;
			} finally {
				this.jobDone = true;
			}
		});
	}

	private boolean jobFinished() {
		if (!this.jobDone) {
			return false;
		}
		if (this.jobError != null) {
			throw new IllegalStateException("A job on the server failed", this.jobError);
		}
		return true;
	}

	// ---- Bookkeeping ----

	private void next(final Phase phase) {
		this.phase = phase;
		this.t = 0;
	}

	private void report(final String name, final int ticks) {
		LOGGER.info(
			"smoke {}: {} ticks, speed {} blocks/tick (last 10 ticks, peak {}), onGround {}, {} above ground, at {} {} {}",
			name, ticks, fmt(this.meter.tail()), fmt(this.meter.peak), this.horse.onGround(), fmt(this.horse.getY() - Scene.groundY),
			fmt(this.horse.getX()), fmt(this.horse.getY()), fmt(this.horse.getZ())
		);
	}

	private void fail(final Minecraft minecraft, final Throwable error) {
		LOGGER.error("Horsing Around smoke test failed in phase {}", this.phase, error);
		try {
			Compat.screenshot(minecraft, "failure.png");
		} catch (final Throwable ignored) {
			// (Nothing more to lose.)
		}
		this.exitIn = 30;
	}

	private static String fmt(final double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private static void deleteWorld(final Path world) {
		if (!Files.exists(world)) {
			return;
		}
		try {
			Files.walkFileTree(world, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(final Path directory, final IOException error) throws IOException {
					Files.delete(directory);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (final IOException error) {
			throw new IllegalStateException("Could not delete the old world " + world, error);
		}
	}

	/** Horizontal speed of an entity per tick, with the average of its last ten ticks. */
	private static final class Meter {
		private static final int TAIL = 10;
		private final double[] speeds = new double[TAIL];
		private int samples;
		private double peak;
		private double lastX;
		private double lastZ;
		private boolean primed;

		void reset() {
			this.samples = 0;
			this.peak = 0.0;
			this.primed = false;
		}

		void sample(final Entity entity) {
			if (this.primed) {
				final double dx = entity.getX() - this.lastX;
				final double dz = entity.getZ() - this.lastZ;
				final double speed = Math.sqrt(dx * dx + dz * dz);
				this.speeds[this.samples % TAIL] = speed;
				this.samples++;
				this.peak = Math.max(this.peak, speed);
			}
			this.lastX = entity.getX();
			this.lastZ = entity.getZ();
			this.primed = true;
		}

		double latest() {
			return this.samples == 0 ? 0.0 : this.speeds[(this.samples - 1) % TAIL];
		}

		double tail() {
			final int count = Math.min(this.samples, TAIL);
			double sum = 0.0;
			for (int i = 0; i < count; i++) {
				sum += this.speeds[i];
			}
			return count == 0 ? 0.0 : sum / count;
		}
	}
}
