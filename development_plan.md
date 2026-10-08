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
hazards with a snort and head toss) and jumps up 2-block ledges. Also early (phase 1j): hard cuts.

- Skid stop with rear when braking hard from a gallop.
- Stumble or throw the rider on a hard crash.
- Terrain: slow on steep slopes, soft blocks, snow; faster on paths.

## Phase 4: the horse has a mind (post-MVP)

- Spook and rear near hostile mobs.
- Bonding level that improves stamina and unlocks moves.
- Whistle to call your horse.
- Brushing and feeding.

## Release: Modrinth readiness (before the first public version)

Requirements in `mvp_requirements.md` (Release). Done (see `completed_steps.md`, Release readiness): builds on any
machine; modded-server handshake (clients switch off on servers without the mod; the user's target is servers with
the mod, not vanilla servers); every vanilla mount checked; mixin hygiene; Shoulder Surfing step-aside; automated
compatibility runs; license and store metadata.

How to check a release candidate:

1. `./gradlew build runGameTest` (bare dedicated server) and `./gradlew runClientGameTest` (ride, terrain, mounts,
   dedicated-server ride).
2. `./gradlew runCompat --continue`: each pack of popular mods in a production game. Look at
   `build/run/compat/<pack>/compat-findings.txt` and the reports.
3. The add-on: `./gradlew runClientGameTest` in `../Over the Shoulder`.

Still open:

- Publishing: create the Modrinth projects (client required, server required for Horsing Around; client only for the
  add-on), upload the jars from `build/libs/`, gallery from `build/run/clientGameTest/gallery/`. Optionally publish
  from CI once the projects exist (needs a Modrinth token as a GitHub secret).
- When a pack mod updates or a new popular mod appears, add it to `compatPacks` in `build.gradle` (pin the Modrinth
  version id, not the version number: some mods reuse numbers across Minecraft versions).
- Known outside our control: Horseman crashes the second in-process server of the client test (its server config
  isn't loaded there), so the horses pack skips the server test; a real server is unaffected.
- Emotes (Emotecraft) and Better Combat attack animations while riding a managed horse: our rider pose is applied after
  other mods' player-model hooks, so check those still play on horseback if players report otherwise.
- Intermittent ride check: "trotting past a 2-block ledge that only catches the flank" failed 2 of 7 full ride runs on
  the release branch (the horse hopped onto the ledge, 1.1-1.3 blocks off its line) and 0 of 3 on main, while passing
  every time on its own. The physics paths are unchanged and no server corrections were logged; the lane now logs its
  path, so the next failure shows what the horse did.
