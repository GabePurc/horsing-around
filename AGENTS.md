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
(optional dependency; never required at runtime).

Build: `./gradlew build`. Play-test: `./gradlew runClient` (loads Mod Menu, the Fresh Animations stack, and the
add-on if it has been built in `../Over the Shoulder`).
Builds must work on any machine: never commit machine-specific paths. Gradle picks JDK 25 through
`gradle/gradle-daemon-jvm.properties` (downloads one if needed); local JDK locations go in `~/.gradle/gradle.properties`.
Release-readiness work (Modrinth, compatibility with other mods) is planned in `development_plan.md`, Release.

Verify every gameplay change with `./gradlew runClientGameTest`, which runs two client tests (pick one with
`-Ptests=ride` or `-Ptests=terrain`, one terrain scenario with `-Pscenario=<part of its name>`):
`RideFeelTest` rides a horse with simulated keys through every mechanic in hand-built lanes and writes
`build/run/clientGameTest/horsingaround-ride-report.txt`; `TerrainRideTest` rides procedurally built natural terrain
(forests, mountains, hills, hazards, river, badlands) like a player would and writes
`horsingaround-terrain-report.txt` with traces of any crash, hurt or dead end. Screenshots land in
`build/run/clientGameTest/screenshots/` (view them). Don't test only perfect cases: generated worlds are messy. When
feel numbers change on purpose, update the test targets too.
Dev runs load Entity Model Features, Entity Texture Features and the Fresh Animations pack (the user's target setup).

Code rules: per-tick paths must not allocate beyond what vanilla already does, and must not do work for horses that
are not being ridden. Prefer one mixin per vanilla class and keep logic in plain classes under `ride/`.
