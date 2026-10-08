# Horsing Around

Fabric mod for Minecraft 26.3 (Java 25, Mojang names) that makes horse riding feel weighty and alive, inspired by
Red Dead Redemption 2 while staying close to vanilla. This project is **not** the Dungeon Defenders project referenced
by the home-level `AGENTS.md`; the doc names are the same but the content here is about horses.

Read before design-sensitive work:

- `research.md` — design fidelity: what RDR2 does, and how we translate it to Minecraft.
- `mvp_requirements.md` — MVP scope and acceptance.
- `development_plan.md` — build order and staging.
- `completed_steps.md` — what is done; update it when work is finished.

The user guides the *feel*; Claude is the sole developer. Feel numbers live in
`src/main/java/dev/horsingaround/ride/RideTuning.java` so tuning passes stay in one file.

The optional add-on Horsing Around: Over the Shoulder lives in its own repo, checked out next to this one as
`../Over the Shoulder`. It reaches this mod only through `dev.horsingaround.client.api.RideCameraApi` (and keeps a
compile-only copy of it), so treat that class's public signatures as a contract: change both repos together.

Player settings live in `HorseConfig` (JSON in the config folder) with a vanilla-style screen reachable from Mod Menu
(optional dependency; never required at runtime). They are only for comfort and personal preference (camera, first-person
motion, sound volume); nothing that changes how horses ride is a setting (user direction, 2026-10-08).

Build: `./gradlew build`. Play-test: `./gradlew runClient` (loads Mod Menu, the Fresh Animations stack, and the
add-on if it has been built in `../Over the Shoulder`).
Builds must work on any machine: never commit machine-specific paths. Gradle picks JDK 25 through
`gradle/gradle-daemon-jvm.properties` (downloads one if needed); local JDK locations go in `~/.gradle/gradle.properties`.
Release-readiness work (Modrinth, compatibility with other mods) is planned in `development_plan.md`, Release.

Verify every gameplay change with `./gradlew runClientGameTest`, which runs four client tests (pick some with
`-Ptests=ride,terrain,mounts,server`, one terrain scenario with `-Pscenario=<part of its name>`, some ride sections with
`-Psections=core,cuts,stairs,picking,steps`; `legs` takes close shots of the legs in a jump, `slopes` and `face` run just
the slope-and-stairs lanes and the jump-at-a-wall lanes, `knees` measures the legs exactly as drawn on slopes, stairs and
steps and shoots them up close):
`RideFeelTest` rides a horse with simulated keys through every mechanic in hand-built lanes and writes
`build/run/clientGameTest/horsingaround-ride-report.txt`; `TerrainRideTest` rides procedurally built natural terrain
(forests, mountains, hills, hazards, river, badlands) like a player would and writes
`horsingaround-terrain-report.txt` with traces of any crash, hurt or dead end; `MountsTest` rides every vanilla mount
(only the horse family gets the new riding) and mob riders; `ServerRideTest` starts a real dedicated server with the
mod (the target setup: a family server) and checks the server accepts every move. All of them log server corrections
("moved wrongly" = rubber-banding) and warnings. Screenshots land in
`build/run/clientGameTest/screenshots/` (view them; with hitboxes on, F3+B, the ridden horse also draws its steering
and step state). In the client tests horses wear colour-coded test textures (`src/gametest/debugpack`: upper leg blue,
knee white, cannon orange, hoof black, sole magenta; `-PplainTextures` for real ones), and `LegProbe` measures the legs
as the renderer drew them: judge leg work by those numbers and close shots, not by formulas. Don't test only perfect cases: generated worlds are messy. When
feel numbers change on purpose, update the test targets too. `./gradlew runGameTest` loads the mod on a bare
dedicated server (no client code) and rides there. `-Ptests=gallery` takes clean store screenshots.

Test games run light (lowest settings, no sound, 20 frames a second, a 2 GB heap, and Sodium, Lithium, FerriteCore and
ImmediatelyFast loaded only into test games; `-PplainGame` drops the mods, `-PfullGraphics` keeps normal settings, and
the gallery always gets them). `./gradlew runClientGameTestParallel --configuration-cache` runs the client tests in three
such games at once (ride split in two, the rest in a third; folders `build/run/clientGameTest-<shard>/`), then merges
their reports into `build/run/clientGameTest/` and lists any failed check. Apart from that runner, run one game at a
time (two separate test games at once, even from different repos, made ride checks flaky).

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
