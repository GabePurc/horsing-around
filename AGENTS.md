# Horsing Around

Fabric mod for Minecraft 1.20.1 to 26.3 (Mojang names) that makes horse riding feel weighty and alive, inspired by
Red Dead Redemption 2 while staying close to vanilla. This project is **not** the Dungeon Defenders project referenced
by the home-level `AGENTS.md`; the doc names are the same but the content here is about horses.

Read before design-sensitive work:

- `research.md` — design fidelity: what RDR2 does, what the user asked for, and how it was translated to Minecraft.
- `mvp_requirements.md` — the original MVP scope and acceptance (shipped; kept as a record).
- `development_plan.md` — the backlog of planned and open work, and the release checklist.
- `completed_steps.md` — what is done; update it when work is finished.

How to use these docs (user direction, 2026-10-14): they are a record of what the user asked for and why, and of how
it was built at the time, not a cage. Most of them were written before the code they describe existed.

- What counts, in order: the user's latest direction; what the game actually does (play it, run the tests, measure);
  then the docs. The user's stated intent ("the horse must never become an annoyance", "stay close to vanilla") is the
  design target. Numbers, mechanisms, "Translation" notes, test targets and "known limits" are snapshots of one way it
  was done: the code (`RideTuning` for numbers) is the source of truth, and a better way is welcome.
- If something in the mod is wrong (a bug, a bad feel, a fragile or slow approach, a test that checks the wrong
  thing), fix it, even when a doc describes the current behaviour as intended. Then update the doc so it doesn't
  mislead the next agent. A doc that contradicts the code or a later direction is stale: correct it, don't follow it.
- Innovate: a new approach that serves the user's intent better is fine without asking. Ask the user only when a change
  would reverse something they explicitly decided (marked "user direction" or "user decision"), would change the feel
  noticeably, or widens scope (a new feature or system). Say what you'd change and why, and suggest the better option;
  the doc's wording is not a reason to leave something broken.
- The home-level `AGENTS.md`'s "call out conflicts and realign" means this here: a conflict with a stale doc is fixed
  in the doc; only a conflict with the user's own direction goes to the user.

Firm rules, because the user asked for them or because breaking them breaks players: nothing that changes how horses
ride is a setting; `RideCameraApi` is a contract with the add-on; per-tick paths stay allocation-free and unridden horses
cost nothing; every version keeps building and passing its audit; no machine-specific paths; the supporter hat stays
cosmetic; public text follows the `CHANGELOG.md` rules below. Everything else in these docs is guidance.

The user guides the *feel*; Claude is the sole developer. Feel numbers live in
`src/main/java/dev/horsingaround/ride/RideTuning.java` so tuning passes stay in one file.

The optional add-on Horsing Around: Over the Shoulder lives in its own repo, checked out next to this one as
`../Over the Shoulder`. It reaches this mod only through `dev.horsingaround.client.api.RideCameraApi` (and keeps a
compile-only copy of it), so treat that class's public signatures as a contract: change both repos together.

Player settings live in `HorseConfig` (JSON in the config folder) with a vanilla-style screen reachable from Mod Menu
(optional dependency; never required at runtime). They are only for comfort and personal preference (camera, first-person
motion, sound volume); nothing that changes how horses ride is a setting (user direction, 2026-10-08).

Every supported Minecraft version (1.20.1 to 26.3, one jar per group of versions) builds from the one `src/` with
Stonecutter; `src/` reads as 26.3 code (the main version, Java 25), older versions' code sits in `//?` comments. Read
`docs/porting.md` before touching version-specific code; a change to shared code must still build (`./gradlew build`)
and pass `./gradlew auditMixins` (every version's jar in a production client and server) on every version. The client
tests run on 26.3 only. Jars land in `versions/<version>/build/libs/`.

Build: `./gradlew build`. Play-test: `./gradlew runClient` (26.3; `./gradlew :<version>:runClient` for another) (loads Mod Menu, the Fresh Animations stack, and the
add-on if it has been built in `../Over the Shoulder`).
Builds must work on any machine: never commit machine-specific paths. Gradle picks JDK 25 through
`gradle/gradle-daemon-jvm.properties` (downloads one if needed); local JDK locations go in `~/.gradle/gradle.properties`.
Release-readiness work (Modrinth, compatibility with other mods) is planned in `development_plan.md`, Release.
Every release gets an entry at the top of `CHANGELOG.md`, written for players (no internal names, no Red Dead) so it can be
pasted straight into the Modrinth version page; the GitHub release notes say the same.
Releasing: bump `version` in `gradle.properties`, write the `CHANGELOG.md` entry, merge to main, then publish a GitHub
release tagged `v<version>` with the same notes. `.github/workflows/publish.yml` then builds every version, uploads each
jar to Modrinth as a version of its own (`gradle/publish.gradle`: game versions from `game_versions` in
`versions/<version>/gradle.properties`, Fabric API required, Mod Menu optional, notes from the changelog's top entry,
which must be that version's) and attaches the jars to the release. Running the workflow by hand (Actions > publish)
is a dry run that uploads nothing; locally `./gradlew publishModrinth -PpublishDryRun`.

Verify every gameplay change with `./gradlew runClientGameTest`, which runs four client tests (pick some with
`-Ptests=ride,terrain,mounts,server`, one terrain scenario with `-Pscenario=<part of its name>`, some ride sections with
`-Psections=core,cuts,stairs,picking,steps`; `legs` takes close shots of the legs in a jump, `slopes` and `face` run just
the slope-and-stairs lanes and the jump-at-a-wall lanes, `drawn` measures the legs exactly as drawn on slopes, stairs and
steps and shoots them up close, `slowstep` watches standing, stopping and single steps (walking, and running up one) frame by frame for snaps,
`exits` climbs out of water onto banks of a few heights, wading and swimming, `strafe` stops after riding with A or D alone,
`hands` poses the rider's arms with items, swings and a bow, with close shots, `climbs` sweeps 2-block climbs by angle (`-Pscenario=<part of a lane name>` for some),
speed, start and what is round them, plus noisy mountain staircases and diagonal terraces, `ways` rides just the
going-round lanes and seeded forests with every plan logged, `-Pscenario=tree|wall|long|alongside|pillar|row|forest|streets|ledge`
for one):
`RideFeelTest` rides a horse with simulated keys through every mechanic in hand-built lanes and writes
`build/run/clientGameTest/horsingaround-ride-report.txt`; `TerrainRideTest` rides procedurally built natural terrain
(forests, mountains, hills, hazards, river, badlands) like a player would and writes
`horsingaround-terrain-report.txt` with traces of any crash, hurt or dead end; `MountsTest` rides every vanilla mount
(only the horse family gets the new riding) and mob riders; `ServerRideTest` starts a real dedicated server with the
mod (the target setup: a family server) and checks the server accepts every move. All of them log server corrections
("moved wrongly" = rubber-banding) and warnings. Screenshots land in
`build/run/clientGameTest/screenshots/` (view them; with hitboxes on, F3+B, the ridden horse also draws its steering
and step state). In the client tests horses wear colour-coded test textures (`src/gametest/debugpack`: upper leg blue,
a white band, cannon orange, hoof black, sole magenta; `-PplainTextures` for real ones), and `LegProbe` measures the legs
as the renderer drew them: judge leg work by those numbers and close shots, not by formulas. Don't test only perfect cases: generated worlds are messy. When
feel numbers change on purpose, update the test targets too. Test targets record what was wanted when the test was
written: if a check enforces something wrong, fix the check (and say so in the PR), don't bend the mod to it. The flags
above may lag behind the build: `gradle/client-tests.gradle` and the tests themselves are the truth. `./gradlew runGameTest` loads the mod on a bare
dedicated server (no client code) and rides there. `-Ptests=gallery` takes clean store screenshots. `-Ptests=store` films
the README's clips and stills in a generated world (`StoreMediaTest`; `-Pscenario=gaits,river` for some; add
`-PshaderPack=<path to a shader pack zip>` to shoot with Iris and that pack: stills only, the user wants clips without
shaders, which are easier to see), and
`scripts/store-media.sh` turns them into `docs/media/`. The README is also the Modrinth description: its images load
from `raw.githubusercontent.com/.../main/docs/media/`, so they show only once merged to main.

Every version (not only 26.3) has an in-game smoke test, `./gradlew :<version>:runSmoke` (`src/smoke`, test only; plain
`runSmoke` runs them all one at a time): a flat world, a saddled horse ridden through a stand, walk, gallop, jump and stop
with simulated keys, screenshots in `build/run/smoke/<version>/screenshots/` (view them: side views of the rider, first
and third person, leashed mobs and a lead), and a pass/fail in the log. `-PsmokeNoPack` (no Fresh Animations pack) and
`-PsmokePlain` (no Entity Model Features, Entity Texture Features or pack) tell this mod's problems from the animation
stack's, and `-PsmokeWithoutMod` runs the same scene with this mod turned off. Look at the screenshots both with and
without the pack: most players run the plain horse model, and a bug there (idle horses' legs adrift before 1.21.2) only
showed without it. Run it after touching version-specific code; see `docs/porting.md`.

Test games run light (lowest settings, no sound, 20 frames a second, a 2 GB heap, and Sodium, Lithium, FerriteCore and
ImmediatelyFast loaded only into test games; `-PplainGame` drops the mods, `-PfullGraphics` keeps normal settings, and
the gallery always gets them). Prefer `./gradlew runClientGameTestParallel --continue` for full runs (user request,
2026-10-08): it splits the client tests into five shards (`testShards` in `build.gradle`; folders
`build/run/clientGameTest-<shard>/`), runs two such games at a time so the user's other work keeps room (`-PtestGames=N`
for more or fewer; the machine has 11 cores and 18 GB), then merges their reports into `build/run/clientGameTest/` and
lists any failed check. One shard alone: `./gradlew runClientGameTest<Shard>`. Leg and body easing runs on game time, so
results don't depend on frame rate. Apart from that runner, don't start a second test game yourself (two separate games,
even from different repos, made ride checks flaky).

Compatibility: `./gradlew runCompat --continue` runs the tests in a production game with packs of popular mods
(`compatPacks` in `build.gradle`); results in `build/run/compat/<pack>/`. Run it before a release and after touching
mixins. The mod is meant for servers that have it installed; on a server without it the client rides vanilla.
Dev runs load Entity Model Features, Entity Texture Features and the Fresh Animations pack (the user's target setup).

Code rules: per-tick paths must not allocate beyond what vanilla already does, and must not do work for horses that
are not being ridden. Prefer one mixin per vanilla class and keep logic in plain classes under `ride/`.

Supporter cowboy hat (user request, 2026-10-08): a cosmetic for people who buy the user a coffee
(https://buymeacoffee.com/blintzbug), never an item and never a gameplay advantage (Mojang's usage guidelines allow
cosmetics, not capes). Supporters are listed by Minecraft UUID in `supporters.json` at the repo root, which every client
downloads at startup (disclosed in the README); add one through a PR when the user asks. The hat colour goes through
the server (`HatRelay`); clients only draw hats for players on their own copy of the list. While the hat is on, the
player's helmet and head items aren't drawn (still worn). Code: `client/cosmetic/`, `net/HatPayload`, `HatTest`.
