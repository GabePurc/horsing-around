# Testing

If a check enforces something wrong, fix the check and say so in the PR. When feel numbers change on purpose, update the
targets.

## Client tests (every version, Fabric and NeoForge)

`./gradlew :<version>:runClientGameTest` (or `:<version>-neoforge:`) runs these; all fail on server corrections ("moved wrongly",
meaning rubber-banding) and log warnings. They run on this project's harness (`src/gametest/.../harness`, from Fabric API's). Loader differences sit in `TestEnv` and `gametest/neoforge`, version ones in conditions
(shared ones in `Versions`; version-only mixins come from the mixin configs' plugins).

- `RideFeelTest` (`ride`): every mechanic in hand-built lanes, with simulated keys.
- `TerrainRideTest` (`terrain`): built natural terrain ridden as a player would, tracing any crash, hurt or dead end.
- `MountsTest` (`mounts`): every vanilla mount and mob riders.
- `ServerRideTest` (`server`): a real dedicated server accepts every move.
- `HatTest` (`hat`): the supporter hat.
- `SettingsTest` (`settings`): the settings screen, by mouse.

Full runs: `./gradlew runClientGameTestParallel --continue` (every build; `:<version>:` for one) splits them into shards (`testShards`
in `gradle/client-tests-common.gradle`, one alone with `runClientGameTest<Shard>`), two games at a time (`-PtestGames=N`), merges
reports into `build/run/clientGameTest/` (26.3; `clientGameTest-<version>[-neoforge]/` for the rest) and lists failed checks. Only 26.3 runs `gallery`, `store`, Icy's and Real Camera's tests (their packs are 26.3's).

### Choosing what runs

- `-Ptests=ride,terrain,...`; also `gallery`, `perf`, `store` (below), which default runs leave out.
- `-Pscenario=<part of a name>`: one terrain scenario, or some lanes of `climbs` or `ways`
  (`tree|wall|long|alongside|pillar|row|forest|streets|ledge`).
- `-Psections=<names>` for the ride test:

| Section | What |
| --- | --- |
| `core`, `cuts`, `stairs`, `picking`, `steps`, `ledges` | The main groups |
| `climbs` | 2-block climbs by angle, speed, start and surroundings; mountain staircases, terraces |
| `ways` | Going-round lanes and seeded forests, with every plan logged |
| `hurdles`, `straight`, `face`, `spam` | Fences; ledges met straight on; jumps at a wall; jump pressed repeatedly |
| `slopes`, `hill` | Slope and stair lanes; a gallop up a ragged hillside (also in `stairs`) |
| `slowstep` | Standing, stopping and single steps frame by frame, for snaps, at world tick 158 million |
| `drawn`, `legs` | Close shots: legs as drawn on slopes and steps; legs in a jump |
| `landings` | Jumps and drops, takeoff and landing tick by tick (`landingshots`: shots) |
| `idle`, `unseen` | Horses no one rides: level, grass, a lead, a step's edge; moved out of view (`performance` pack) |
| `exits`, `strafe` | Out of water onto banks; stopping after A or D alone |
| `hands`, `icons` | Rider's arms with items; shulker boxes with Entity Model Features |
| `rear` | A ridden horse rears; on a pack's model the seat stays on the measured saddle (also in `core`) |
| `leads` | Leads on the drawn horse and rider, in both views (also in `steps`) |

### Reading results

- Failures, across all reports: `grep -E 'FAIL|INCOMPLETE' build/run/clientGameTest/horsingaround-*-report.txt`.
  Screenshots: `build/run/clientGameTest/screenshots/`.
- F3+B draws the ridden horse's box, line, heading, plan and look-ahead findings (1.21.11 and newer).
- Test horses wear colour-coded legs (`src/gametest/debugpack`). `-PplainTextures` for real ones; `-PvanillaModel` for the plain
  model with no pack. `LegProbe` measures the legs as drawn: judge leg work by its numbers and close shots.
- `TestInput.pressKey` runs a game tick of its own. In a loop sampling every tick, use `holdKey` then `releaseKey`, or trace with
  `HorseTrace`; otherwise a tick goes missing.
- Test games are silent and run light: lowest settings, a 2 GB heap and (26.3) performance mods. `-PplainGame` drops the mods;
  `-PfullGraphics` keeps them normal.
- Test games don't wait for real time (`TestClock`): the clock counts drawn frames, 5.9 to a tick, so a run goes as fast as the
  machine draws and every tick gets the same frames. Frame-sampling checks need several frames a tick, not a whole number.
  `-PrealTime` runs at normal speed; perf, gallery and store always do. A game whose frames stop for 2 minutes logs its threads and exits.
- Lanes start from a horse with no AI until mounted (`lane()`): one that idled first sometimes starts off its line, failing checks far on.

## Other runs

- `./gradlew runGameTest`: the mod on a bare dedicated server (no client code), riding there.
- `-Ptests=gallery`: clean store screenshots.
- `-Ptests=perf`: load, not pass/fail: horses of every kind in a pen, a gallop round them for 90 s; server tick time, frame rate and
  plan times in `horsingaround-perf-report.txt`.
- `-Pprofile`, on any run: a Java Flight Recording, `horsingaround-profile.jfr` in the run folder. Samples with a `dev.horsingaround`
  frame are the mod's share.
- `-Ptests=store`: films the README's clips and stills in a generated world (`StoreMediaTest`, `FilmCamera`; `-Pscenario=gaits,river`
  for some), with ATMOSPHERICS for the sky. `scripts/store-media.sh` turns them into `docs/media/`, as MP4 and animated WebP. Every
  client test run clears `build/run/clientGameTest`, so encode before the next run. Stills may use shaders; the user wants clips
  without, which are easier to see. Modrinth uses the MP4s; GitHub and CurseForge the WebPs.
- `-Psmooth=<folder>`, any client test run: how sharply each drawn part (body, tilt, lean, camera, legs) changes pace, by
  section (`SmoothProbe`); `-PsmoothDump=<names>` adds the frames round the hardest.
- `-PshaderPack=<zip>`, any 26.3 client test run: Iris with that pack (its shadows come first only up to 1.21.11).

## Smoke test (every version)

`./gradlew :<version>:runSmoke` (`src/smoke`; plain `runSmoke` runs each in turn, about a minute each). A flat world, a saddled horse
through a stand, walk, gallop, jump and stop, then a rough course. It fails unless the log reports a pass, nothing names this mod in an
error, and the server corrected no move. Screenshots: `build/run/smoke/<version>/screenshots/`. Look at them with and without the pack:
most players run the plain model.

- `-PsmokeNoPack`: no Fresh Animations. `-PsmokePlain`: no EMF, ETF or pack.
- `-PsmokeWithoutMod` (Fabric only): this mod off; corrections only warn. It fails at the course, where a vanilla horse gets stuck.
- `-PsmokeWild=<seed>` (`-PsmokeWildAt=x,z` for a fixed start): a two-minute gallop over a real world, failing on any correction. Seed
  2145202720632702597 at 7,41 crosses a dip that once rubber-banded.
- `-PsmokeCloseUp` (`=150` nearly from the front; default 90 is the side): 1920x1080 at a narrow view, a shot every tick (`closeup-*.png`) and
  the neck's numbers logged, for anything that should stay attached.
- NeoForge and Forge: `:<version>-neoforge:runSmoke`, `:1.20.1-forge:runSmoke`. Forge's horse is one of Realistic Horse Genetics'
  (`-PnoGenetics` for vanilla's).
- `-PepicFight` (Forge, NeoForge 1.21.1): with Epic Fight, which draws the rider in a swing; `=vanilla` its other mode,
  `=attack` swings at a gallop.

## Compatibility

`./gradlew runCompat --continue` (26.3) runs tests in a production game per pack of popular mods (`compatPacks` in
`gradle/compat.gradle`, pinned by Modrinth version id because some mods reuse version numbers). Results: `build/run/compat/<pack>/`.
Add a pack mod when a popular one appears. `-PcompatSections`, `-PcompatTests` change what runs. Play with a pack: `./gradlew playCompat<Pack>`,
with worlds kept in `build/run/play/<pack>/`. Icy's boots straight onto a tamed horse pulling a cart, in the hills of a fresh world (`PlayScene`;
`-Pscene=wagon`, `horse`, or `none` to play normally; `-PsceneCheck=1` takes a screenshot there and quits).

The `icys` pack rides an American Paint (`-Dhorsingaround.horse`, `TestHorse`) through the horse tests, which must pass as on a vanilla horse;
`MountsTest` also checks a Friesian under both riding styles and the breeds' traits. `IcyFeaturesTest` (`-PcompatTests=icy`) rides Icy's gear on
this mod's riding; while working on carts, `-PcompatSections=` runs only some of it (`room,cart,stranded,hill,steep,jitter,fast,tight,drop,rough,ledge,jump,lean,reverse`).

The `realcamera` pack rides first person over steps in each of Real Camera's modes (`RealCameraTest`).

Icy's features with only screens or keys aren't tested. Check by hand in `playCompatIcys`, on this mod's riding:

- Command wheel (R), on a horse you look at and the one you ride.
- Whistle (P); pressed while riding, it opens the info screen.
- Roster (G): Set Active, Whistle, Send Home; a dead horse returns with Summon and rides as before.
- `/horse trust <player>` lets them ride and drive; `/horse untrust` bucks them off.
- Upgraded saddle held at a horse shows the stats panel. Stable Handbook pages turn. Icy's settings open from Mod Menu and save.
- Cart menu (Left Alt) while driving: switch model, Riders tab, Unload all.
- Breeding: golden carrots on a male and a female give a foal; two of one gender refuse.
