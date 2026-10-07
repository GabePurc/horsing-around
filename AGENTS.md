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

Two mods live here: Horsing Around (root project, `build/libs/`) and the optional add-on Horsing Around: Over the
Shoulder (`shoulder-cam/`, `shoulder-cam/build/libs/`). The add-on must keep working without the core mod; it only
reaches the core through `dev.horsingaround.client.api.RideCameraApi`, guarded by `isModLoaded("horsingaround")`.

The add-on's player settings live in `shoulder-cam/.../config/ShoulderConfig.java` (JSON in the config folder) with a
vanilla-style screen reachable from Mod Menu (optional dependency; never required at runtime).

Build: `./gradlew build`. Play-test: `./gradlew runClient` (loads both mods, Mod Menu, and the Fresh Animations stack).
Gradle is pinned to Homebrew JDK 25 via `org.gradle.java.home` in `gradle.properties`.

Verify every gameplay change with `./gradlew runClientGameTest` (`src/gametest/.../RideFeelTest.java`). It rides a
horse with simulated keys and writes `build/run/clientGameTest/horsingaround-ride-report.txt` plus screenshots in
`build/run/clientGameTest/screenshots/` (view them). When feel numbers change on purpose, update the test targets too.
Dev runs load Entity Model Features, Entity Texture Features and the Fresh Animations pack (the user's target setup).

Code rules: per-tick paths must not allocate beyond what vanilla already does, and must not do work for horses that
are not being ridden. Prefer one mixin per vanilla class and keep logic in plain classes under `ride/`.
