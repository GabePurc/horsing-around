# Testing

Flags can lag behind the build: `gradle/client-tests.gradle`, `build.gradle` and the tests themselves are the truth. If a
check enforces something wrong, fix the check and say so in the PR. When feel numbers change on purpose, update the targets.

## Client tests (26.3)

`./gradlew runClientGameTest` runs these; all fail on server corrections ("moved wrongly", meaning rubber-banding) and log warnings.

- `RideFeelTest` (`ride`): every mechanic in hand-built lanes, with simulated keys. Report: `build/run/clientGameTest/horsingaround-ride-report.txt`.
- `TerrainRideTest` (`terrain`): built natural terrain ridden like a player would, with traces of any crash, hurt or dead end.
- `MountsTest` (`mounts`): every vanilla mount and mob riders.
- `ServerRideTest` (`server`): a real dedicated server accepts every move.
- `HatTest` (`hat`): the supporter hat.

Full runs: `./gradlew runClientGameTestParallel --continue` splits them into shards (`testShards` in `gradle/client-tests.gradle`,
one alone with `runClientGameTest<Shard>`), runs two games at a time (`-PtestGames=N`) to leave the machine room, merges reports
into `build/run/clientGameTest/` and lists failed checks. While iterating, run only the section that covers the change.

### Choosing what runs

- `-Ptests=ride,terrain,...`; also `gallery`, `perf`, `store` (below), which default runs leave out.
- `-Pscenario=<part of a name>`: one terrain scenario, or some lanes of `climbs` or `ways`
  (`tree|wall|long|alongside|pillar|row|forest|streets|ledge`).
- `-Psections=<names>` for the ride test:

| Section | What |
| --- | --- |
| `core`, `cuts`, `stairs`, `picking`, `steps`, `ledges` | The main groups |
| `climbs` | 2-block climbs by angle, speed, start and surroundings; noisy mountain staircases, diagonal terraces |
| `ways` | Going-round lanes and seeded forests, with every plan logged |
| `hurdles`, `straight`, `face`, `spam` | Fences; ledges met straight on; jumps at a wall; jump pressed repeatedly |
| `slopes`, `hill` | Slope and stair lanes; a gallop up a ragged hillside (also in `stairs`) |
| `slowstep` | Standing, stopping and single steps frame by frame, for snaps, at world tick 158 million |
| `drawn`, `legs` | Close shots: legs as drawn on slopes and steps; legs in a jump |
| `exits`, `strafe` | Out of water onto banks; stopping after A or D alone |
| `hands`, `icons` | Rider's arms with items; shulker boxes with Entity Model Features |
| `rear` | A ridden horse rears; on a pack's model the seat stays on the measured saddle (also in `core`) |

### Reading results

- A run's console output includes the game log, so send it to a file outside the repo and read only the tail. Failures, across
  all reports: `grep -E 'FAIL|INCOMPLETE' build/run/clientGameTest/horsingaround-*-report.txt`. Open a report only for a failure's
  context. Screenshots are in `build/run/clientGameTest/screenshots/`.
- F3+B draws the ridden horse's box, line, heading, plan and look-ahead findings (1.21.11 and newer).
- Test horses wear colour-coded legs (`src/gametest/debugpack`: upper leg blue, a white band, cannon orange, hoof black, sole
  magenta). `-PplainTextures` for real ones; `-PvanillaModel` for the plain model with no pack. `LegProbe` measures the legs as
  drawn: judge leg work by those numbers and close shots, not formulas.
- `TestInput.pressKey` runs a game tick of its own. In a loop sampling every tick, use `holdKey` then `releaseKey`, or trace with
  `HorseTrace`; otherwise a tick goes missing and looks like the horse moved two in one.
- Test games make no sound, and run light: lowest settings, a 2 GB heap and performance mods. `-PplainGame` drops the mods; `-PfullGraphics` keeps
  normal settings. Leg and body easing runs on game time.
- Test games don't wait for real time (`TestClock`): the game's clock counts drawn frames, 5.9 to a tick as in a game at its default limit,
  so a run goes as fast as the machine draws, and a tick gets the same frames however loaded the machine is. Checks that sample frames need
  several a tick (some broke at one), and a whole number would keep sampling the same moments of it. `-PrealTime` runs at normal speed, to
  tell a problem with this clock from a real one; perf, gallery and store always do. A game whose frames stop for 2 minutes logs its threads
  and exits.
- Test games work with their window in front, covered, on another workspace or minimized: no vsync, and no frame limit from the game, which would
  otherwise crawl out of sight on Ubuntu (under 1% of the speed, spinning in `glGetError`). On Linux they use X11, also from a Wayland session's shell.
- Lanes start from a horse with no AI until mounted (`lane()`): one that idled first now and then starts off its line, failing checks far on.

## Other runs

- `./gradlew runGameTest`: the mod on a bare dedicated server (no client code), riding there.
- `-Ptests=gallery`: clean store screenshots.
- `-Ptests=perf`: load, not pass/fail: horses of every kind in a pen, a gallop round them for 90 s; server tick time, frame rate and
  plan times in `horsingaround-perf-report.txt`.
- `-Pprofile`, on any run: a Java Flight Recording at `build/run/clientGameTest/horsingaround-profile.jfr`. Count samples with a
  `dev.horsingaround` frame for the mod's share.
- `-Ptests=store`: films the README's clips and stills in a generated world (`StoreMediaTest`, `FilmCamera`; `-Pscenario=gaits,river`
  for some), with ATMOSPHERICS for the sky. `scripts/store-media.sh` turns them into `docs/media/`, as MP4 and animated WebP. Every
  client test run clears `build/run/clientGameTest`, so encode before the next run. `-PshaderPack=<zip>` shoots stills with Iris; the
  user wants clips without shaders, which are easier to see. Modrinth uses the MP4s; GitHub and CurseForge the WebPs.

## Smoke test (every version)

`./gradlew :<version>:runSmoke` (`src/smoke`; plain `runSmoke` runs each in turn, about a minute each). A flat world, a saddled horse
through a stand, walk, gallop, jump and stop, then a rough course. It fails unless the log reports a pass, nothing names this mod in an
error, and the server corrected no move. Screenshots: `build/run/smoke/<version>/screenshots/`. Look at them with and without the pack:
most players run the plain model, and some bugs only show there.

- `-PsmokeNoPack`: no Fresh Animations. `-PsmokePlain`: no EMF, ETF or pack. These tell this mod's problems from the animation stack's.
- `-PsmokeWithoutMod`: the same scene with this mod off (Fabric only); corrections are vanilla's own and only warned about.
- `-PsmokeWild=<seed>` (`-PsmokeWildAt=x,z` for a fixed start): a two-minute gallop over a real world, failing on any correction. Seed
  2145202720632702597 at 7,41 crosses a dip that once rubber-banded for good.
- `-PsmokeCloseUp` (`=150` nearly from the front; default 90 is the side): 1920x1080 at a narrow view, a shot every tick (`closeup-*.png`) and
  the neck's numbers logged, for anything that should stay attached. With `-PsmokeWithoutMod` the task reports a failure at the course
  where a vanilla horse gets stuck.
- NeoForge: `./gradlew :<version>-neoforge:runSmoke`, results in `build/run/smoke/<version>-neoforge/`.

## Compatibility

`./gradlew runCompat --continue` runs tests in a production game per pack of popular mods (`compatPacks` in
`gradle/client-tests.gradle`, pinned by Modrinth version id because some mods reuse version numbers). Results: `build/run/compat/<pack>/`.
Add a pack mod when a popular one appears. `-PcompatSections`, `-PcompatTests` change what runs. Play with a pack: `./gradlew playCompat<Pack>`,
with worlds kept in `build/run/play/<pack>/`. Icy's boots straight onto a tamed horse pulling a cart, in the hills of a fresh world (`PlayScene`;
`-Pscene=wagon`, `horse`, or `none` to play normally; `-PsceneCheck=1` takes a screenshot there and quits, to check a scene without playing it).

The `icys` pack rides an American Paint (`-Dhorsingaround.horse`, `TestHorse`) through the horse tests, which must pass as on a vanilla horse;
`MountsTest` also checks a Friesian under both riding styles and the breeds' traits. `IcyFeaturesTest` (`-PcompatTests=icy`) rides Icy's gear on
this mod's riding; while working on carts, `-PcompatSections=` runs only some of it (`room,cart,hill,steep,jitter,fast,tight,drop,rough,ledge,jump,lean,reverse`).

The `realcamera` pack rides first person over steps in each of Real Camera's modes (`RealCameraTest`).

Icy's features with only screens or keys aren't tested. Check by hand in `playCompatIcys`, on this mod's riding:

- Command wheel (R), on a horse you look at and the one you ride.
- Whistle (P); pressed while riding, it opens the info screen.
- Roster (G): Set Active, Whistle, Send Home; a dead horse returns with Summon and rides as before.
- `/horse trust <player>` lets them ride and drive; `/horse untrust` bucks them off.
- Upgraded saddle held at a horse shows the stats panel. Stable Handbook pages turn. Icy's settings open from Mod Menu and save.
- Cart menu (Left Alt) while driving: switch model, Riders tab, Unload all.
- Breeding: golden carrots on a male and a female give a foal; two of one gender refuse.
