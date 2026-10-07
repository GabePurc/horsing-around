# Development plan

Each phase ends with a play-test where the user reports how it feels; tuning happens in `RideTuning.java`.

## Phase 0: project setup

Fabric 26.3 project, Gradle wrapper, JDK 25, design docs.

## Phase 1: core feel (first playable)

Gaits, momentum, turning inertia, free look, A/D steering, stamina with HUD bar, lean into turns, speed-based hoof
sounds, wall-hit momentum loss, multiplayer heading fix.

## Phase 2: tuning and polish

- Play-test passes on the numbers.
- Config file so numbers can change without rebuilding (optionally Mod Menu screen).
- Sync gait and stamina to the server (custom payload) so stamina persists and other clients can see gait.
- Gait-specific hoof cadence and breathing when tired.
- Expose gait to Fresh Animations (EMF custom variable) if FA's speed-based legs don't read gaits clearly enough.

Done early at the user's request (phase 1b): RDR2 camera, instant momentum jumps with the jump bar as stamina,
Fresh Animations compatibility, automated ride test.

## Phase 3: drama at speed

Done early at the user's request (phase 1g): the horse picks its way (detours, slowing for walls, refusing cliffs and
hazards with a snort and head toss) and jumps up 2-block ledges.

- Skid stop with rear when braking hard from a gallop.
- Stumble or throw the rider on a hard crash.
- Terrain: slow on steep slopes, soft blocks, snow; faster on paths.

## Phase 4: the horse has a mind (post-MVP)

- Spook and rear near hostile mobs.
- Bonding level that improves stamina and unlocks moves.
- Whistle to call your horse.
- Brushing and feeding.

## Release: Modrinth readiness (before the first public version)

Requirements in `mvp_requirements.md` (Release). Build portability is done (`completed_steps.md`, Release). Remaining,
from a code audit on 2026-10-06:

**Install mixes and servers**
- Leaves: a vanilla server rejects a horse moving through leaves (it re-runs the move with normal collisions), so a
  client-only install would rubber-band in forests. Register a network channel; the client rides through leaves only
  when the server has it. Gameplay rules (leaves, trampling) come from the server's config, feel from the client's.
- Server gametest (`runGameTest`, no EULA needed) to prove the mod loads and tramples on a dedicated-server
  environment with no client classes.
- Join a vanilla server with the mod client-only and check riding, jumping and water for rubber-banding.

- The ridden horse's collision box is narrowed (0.9 vs vanilla 1.4) on both sides when the server has the mod; on a
  server without it the server keeps the wide box, so squeezing through tight gaps could rubber-band. Only narrow
  when the server has the mod (same network-channel check as leaves).

**Mixin hygiene** (so neither we nor other mods lose features silently)
- `AbstractHorseMixin` adds overrides of `updateWalkAnimation`, `shouldTravelInFluid` and `getFlyingSpeed`;
  `LeavesBlockMixin` adds `getCollisionShape`. If another mod adds the same method, Mixin skips one with only a log
  warning. Move these to chainable injections (MixinExtras `@ModifyReturnValue` / `@WrapMethod`) on the declaring
  class, with an early cheap type check.
- Cancelling HEAD injections (`getRiddenInput`, `getRiddenRotation`, `executeRidersJump`, `handleStartJump`, camera
  `alignWithEntity`, `JumpableVehicleBar.extractBackground`, add-on camera) stop other mods' hooks in those methods
  while riding. Switch to `@WrapMethod` / return-value modifiers where possible.
- `defaultRequire: 1` crashes the game if another mod removes an injection target. Keep it for core physics; make
  cosmetic hooks (pose, camera bob, HUD, FA stirrups) `require = 0` with a logged fallback. The add-on's
  `lambda$useItem$0` target is a compiler-generated name; target something stable.
- Modded horses: every `AbstractHorse` subclass except camels and llamas gets the new controls. Use an entity type tag
  (`horsingaround:managed`, vanilla horse, donkey, mule, skeleton and zombie horse by default) so mods and modpacks opt in.

**Other mods to test with and, where needed, detect and back off from**
- Performance: Sodium, Iris (shaders), Lithium (collision optimisations vs our leaves rule), FerriteCore, ModernFix,
  ImmediatelyFast, Entity Culling, C2ME.
- Player animation: Not Enough Animations (its own riding pose), Player Animator / Emotecraft, Better Combat,
  First-person Model, Figura. Our rider pose may fight theirs; yield when they are animating.
- Cameras: Shoulder Surfing Reloaded, Better Third Person, Freecam, Do a Barrel Roll, Replay Mod. Our riding camera
  (and the add-on) must step aside when another camera mod is driving.
- HUD: mods that redraw the mount bars (stamina bar) and AppleSkin / Raised style offsets.
- Horses and mounts: mods that also change horse controls or speed (same mixin targets), and mods adding horse types.
- Models: EMF packs other than Fresh Animations (our part lookups by name must quietly skip missing parts).

**Automated compatibility run**
- Loom production run (`ClientProductionRunTask`) of the built jars plus a pack of popular mods fetched from Modrinth,
  running the ride test and failing on crashes, mixin conflict warnings or errors in the log.

**Store**
- Choose a license (both mods currently say All-Rights-Reserved), fill in authors and links in `fabric.mod.json`,
  description, screenshots; publish from CI.
