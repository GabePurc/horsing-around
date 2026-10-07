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

- Skid stop with rear when braking hard from a gallop.
- Stumble or throw the rider on a hard crash.
- Terrain: slow on steep slopes, soft blocks, snow; faster on paths.

## Phase 4: the horse has a mind (post-MVP)

- Spook and rear near hostile mobs.
- Bonding level that improves stamina and unlocks moves.
- Whistle to call your horse.
- Brushing and feeding.
