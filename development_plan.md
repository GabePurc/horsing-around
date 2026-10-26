# Development plan

A backlog of open work and ideas, not a sequence that must be followed: pick up what the user asks for, and suggest
reordering or dropping items when the mod has moved past them. What exists is in `completed_steps.md` (phases 0 and 1,
most of 2 and 3, and the release work are done). Each feel change ends with a play-test where the user reports how it
feels; tuning happens in `RideTuning.java`.

## Polish (from the old phase 2)

- Sync gait and stamina to the server (custom payload) so stamina persists and other clients can see the gait.
- Gait-specific hoof cadence with Fresh Animations: its planted hooves slide (about 1.5 blocks a footfall at a walk);
  quicker legs were tried and rejected (user, 2026-10-23), so only something that keeps the legs' pace is open. (The
  plain model's procedural legs don't slide, 2026-10-26.)
- Plain model: hooves on stairs (a pair off the ground together a little more than with vanilla's swing); the canter and
  gallop lead following the turn (always the left now).
- Legs into tree trunks and boulders the narrowed ridden box brushes past (sideways; seen in the terrain test's leg numbers).
- The user saw the horse sink into flat ground or standing (2026-10-23, on the first stride build); not reproduced in tests.
- Expose gait to Fresh Animations (an EMF custom variable) if its speed-based legs don't read gaits clearly enough.
- (Dropped, user direction 2026-10-08: a config file for feel numbers. Settings stay comfort and preference only.)

## Drama at speed (old phase 3, MVP item 9 still open)

- Skid stop with rear when braking hard from a gallop.
- Stumble or throw the rider on a hard crash.
- Terrain: slow on steep slopes, soft blocks, snow; faster on paths.

## The horse has a mind (post-MVP)

- Spook and rear near hostile mobs.
- Bonding level that improves stamina and unlocks moves.
- Whistle to call your horse.
- Brushing and feeding.

## Release

Requirements in `mvp_requirements.md` (Release); procedure in `docs/releasing.md`.

How to check a release candidate:

1. `./gradlew build runGameTest` (bare dedicated server) and `./gradlew runClientGameTestParallel --continue` (ride,
   terrain, mounts, dedicated-server ride).
2. `./gradlew runCompat --continue`: each pack of popular mods in a production game. Look at
   `build/run/compat/<pack>/compat-findings.txt` and the reports.

Still open:

- Publishing: Horsing Around is out (0.1.0 to 0.5.2 through CI, Modrinth project `Lc7S9t3K`; CurseForge
  project 1758139 from 0.5.2, awaiting approval on 2026-10-21); the gallery
  (`build/run/clientGameTest/gallery/`) may still need doing, so check with the user.
- When a pack mod updates or a new popular mod appears, add it to `compatPacks` in `build.gradle` (pin the Modrinth
  version id, not the version number: some mods reuse numbers across Minecraft versions).
- Known outside our control: Horseman crashes the second in-process server of the client test (its server config
  isn't loaded there), so the horses pack skips the server test; a real server is unaffected.
- Emotes (Emotecraft) and Better Combat attack animations while riding a managed horse: our rider pose is applied after
  other mods' player-model hooks, so check those still play on horseback if players report otherwise.
- Intermittent ride check: "trotting past a 2-block ledge that only catches the flank" failed 2 of 7 full ride runs on
  the release branch and 0 of 3 on main, while passing every time on its own; the lane logs its path, so the next
  failure shows what the horse did.
