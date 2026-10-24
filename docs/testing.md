# Testing

Read the part you need. The flags here may lag behind the build: `gradle/client-tests.gradle`, `build.gradle` and the
tests themselves are the truth.

## Client tests (26.3)

`./gradlew runClientGameTest` runs four client tests. All of them fail on server corrections ("moved wrongly" =
rubber-banding) and log warnings.

- `RideFeelTest` rides a horse with simulated keys through every mechanic in hand-built lanes and writes
  `build/run/clientGameTest/horsingaround-ride-report.txt`.
- `TerrainRideTest` rides procedurally built natural terrain (forests, mountains, hills, hazards, river, badlands) like a
  player would and writes `horsingaround-terrain-report.txt` with traces of any crash, hurt or dead end.
- `MountsTest` rides every vanilla mount (only the horse family gets the new riding) and mob riders.
- `ServerRideTest` starts a real dedicated server with the mod (the target setup: a family server) and checks the server
  accepts every move.

Picking what runs:

- `-Ptests=ride,terrain,mounts,server`; `-Pscenario=<part of its name>` for one terrain scenario.
- `-Psections=core,cuts,stairs,picking,steps` for ride sections. Also:
  - `legs`: close shots of the legs in a jump.
  - `slopes`, `face`: just the slope-and-stairs lanes, and the jump-at-a-wall lanes.
  - `drawn`: the legs exactly as drawn on slopes, stairs and steps, shot up close.
  - `slowstep`: standing, stopping and single steps (walking, and running up one), frame by frame, for snaps.
  - `exits`: out of water onto banks of a few heights, wading and swimming.
  - `strafe`: stopping after riding with A or D alone.
  - `hands`: the rider's arms with items, swings and a bow, with close shots.
  - `climbs`: 2-block climbs by angle, speed, start and what is round them, plus noisy mountain staircases and diagonal
    terraces (`-Pscenario=<part of a lane name>` for some).
  - `ways`: the going-round lanes and seeded forests with every plan logged
    (`-Pscenario=tree|wall|long|alongside|pillar|row|forest|streets|ledge` for one).

Full runs: prefer `./gradlew runClientGameTestParallel --continue` (user request, 2026-10-08). It splits the client tests
into five shards (`testShards` in `build.gradle`; folders `build/run/clientGameTest-<shard>/`), runs two games at a time
so the user's other work keeps room (`-PtestGames=N`; the machine has 11 cores and 18 GB), merges the reports into
`build/run/clientGameTest/` and lists any failed check. One shard: `./gradlew runClientGameTest<Shard>`. Apart from that
runner, don't start a second test game yourself (two separate games, even from different repos, made ride checks flaky).

While iterating, run only the section that covers the change; full runs at checkpoints.

Reading results:

- Read the report's failures and summary, not the whole report.
- Screenshots land in `build/run/clientGameTest/screenshots/`. With hitboxes on (F3+B) the ridden horse also draws its
  steering and step state. View only the shots that matter.
- Horses wear colour-coded test textures (`src/gametest/debugpack`: upper leg blue, a white band, cannon orange, hoof
  black, sole magenta; `-PplainTextures` for real ones). `LegProbe` measures the legs as the renderer drew them: judge
  leg work by those numbers and close shots, not by formulas.
- Don't test only perfect cases: generated worlds are messy. When feel numbers change on purpose, update the test
  targets too. If a check enforces something wrong, fix the check (and say so in the PR).
- `TestInput.pressKey` runs a game tick of its own after the press (screenshots can run a few too). In a loop that
  samples or films every tick, press with `holdKey` then `releaseKey` instead, or trace every tick with `HorseTrace`;
  otherwise the press tick goes missing and looks like the horse moved two ticks in one.

Test games run light (lowest settings, no sound, the default frame rate the frame-sampled checks are tuned at (20
fps broke some), a 2 GB heap, and Sodium, Lithium, FerriteCore and
ImmediatelyFast loaded only into test games). `-PplainGame` drops the mods; `-PfullGraphics` keeps normal settings (the
gallery always gets them). Leg and body easing runs on game time, so results don't depend on frame rate.

## Other runs

- `./gradlew runGameTest`: loads the mod on a bare dedicated server (no client code) and rides there.
- `-Ptests=gallery`: clean store screenshots.
- `-Ptests=perf`: load, not pass/fail (`PerfTest`, not in full runs): 56 horses of every kind in a pen, the player
  galloping circles round them for 90 s; logs server tick time, frame rate and plan times to
  `horsingaround-perf-report.txt`. With `-Pprofile` any run records a Java Flight Recording (1 ms CPU samples) to
  `build/run/clientGameTest/horsingaround-profile.jfr`: count the samples with a `dev.horsingaround` frame (the
  mod's share) and see which of its methods they go through. Last measured (2026-10-20):
  about 3% of the client thread's Java samples (~70 us a frame) and 0.1-0.3% of the server's.
- `-Ptests=store`: films the README's clips and stills in a generated world (`StoreMediaTest`; `-Pscenario=gaits,river`
  for some), with ATMOSPHERICS loaded for the sky. Clips are widescreen at 30 frames a second, slowed down where a shot
  asks; the ones shown as you play (the stamina bar, the riding camera, the settings) are 16:9 with the HUD, and while a
  clip records a menu doesn't pause the game and draws a pointer. Each frame is drawn at its own moment on the game's own timer (the mod's legs and Fresh Animations read the
  partial tick from it), and while a clip records only its frames draw the world, so frame-to-frame animation moves with
  them alone. `FilmCamera` moves the camera between set-ups on a smooth path, never below the ground or grass, craning up
  when something would block the horse. The log gives each clip's sharpest camera change of speed and how its legs moved
  (`<clip>-legs.csv`: the soles against the body, a line a frame). Every client test run clears `build/run/clientGameTest`, so encode the frames before
  the next run. `-PshaderPack=<path to a shader pack zip>` shoots with Iris and that pack, stills only (the user wants clips
  without shaders, which are easier to see). `scripts/store-media.sh` turns them into `docs/media/`: each clip as MP4 and as
  animated WebP, framed in the logo's orange. The README (on GitHub) shows the WebPs; the Modrinth description is the
  README with the clips as video, because large animated images stutter in a browser where video doesn't; CurseForge
  drops video, so its description keeps the WebPs (`scripts/store-descriptions.sh` writes both to `build/` for pasting). Both load from
  `raw.githubusercontent.com/.../main/docs/media/`, so they show only once merged to main.
  `-Pscenario=seat` (only by name, not for the store) films the rider side on for review: the gaits and a hard stop, the
  same through the riding camera, a jump and a drop off a 3-block ledge, each with `<clip>-rider.csv` (stand, brace,
  settle and what drives them, a line a frame).

## Smoke test (every version)

`./gradlew :<version>:runSmoke` (`src/smoke`, test only; plain `runSmoke` runs every version, one at a time): a flat
world, a saddled horse ridden through a stand, walk, gallop, jump and stop, then over a rough course (steps, stairs,
leaves, uneven ground, single steps at a gallop) with simulated keys, screenshots in
`build/run/smoke/<version>/screenshots/` (side views of the rider, first and third person, leashed mobs and a lead), and a
pass/fail in the log; it also fails if the server corrected any of the rider's moves (with `-PsmokeWithoutMod` those
are vanilla's own and only warned about). Run it after touching version-specific code; see `docs/porting.md`.

- `-PsmokeNoPack`: no Fresh Animations pack. `-PsmokePlain`: no Entity Model Features, Entity Texture Features or pack.
  These tell this mod's problems from the animation stack's.
- `-PsmokeWithoutMod`: the same scene with this mod turned off.
- `-PsmokeWild=<seed>` (and `-PsmokeWildAt=x,z` for a fixed start; spawn is spread at random): instead, a two-minute
  gallop with turns and jumps over a normal world of that seed, failing on any server correction. Natural ground brings
  out corrections the course doesn't (seed 2145202720632702597 at 7,41 crosses a dip that once rubber-banded for good).
- NeoForge with Sinytra Connector (by hand, no task yet): the mod jar and a Loom-remapped jar of the `smoke` source set
  run in a NeoForge game with Connector, Forgified Fabric API, Entity Model Features and Entity Texture Features, with
  `-Dhorsingaround.smoke=true`. Connector caches remapped jars in `<game>/.cache/connector`: clear it after replacing a
  jar of the same name, or the old one runs.
- Look at the screenshots both with and without the pack: most players run the plain horse model, and a bug there
  (idle horses' legs adrift before 1.21.2) only showed without it.

## Compatibility

`./gradlew runCompat --continue` runs the tests in a production game with packs of popular mods (`compatPacks` in
`build.gradle`); results in `build/run/compat/<pack>/`. Run it before a release and after touching mixins.
