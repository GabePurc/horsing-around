# Building for several Minecraft versions

Horsing Around ships one jar per group of Minecraft versions, 1.20.1 to 26.3, all built from the one `src/` with
[Stonecutter](https://stonecutter.kikugie.dev/). 26.3 is the main version: the code in `src/` reads as 26.3 code, and
the client tests and compatibility runs only exist there.

## Layout

- `settings.gradle` lists the versions that build (`versions '26.3', ...`). Each is a Gradle project `:<version>`
  whose numbers (Minecraft, its range, Fabric API, optional integrations) are in `versions/<version>/gradle.properties`.
  Every supported version already has a properties file; a version joins the build once it compiles and passes its
  audit.
- `build.gradle` runs once per version. `loom-back-compat` picks Fabric Loom: plain for 26.x (unobfuscated), with
  remapping and Mojang names before that, so dependencies use `modImplementation`/`modCompileOnly`/`modLocalRuntime`.
- Obfuscated versions (before 26) need the mixin annotation processor for refmaps (`loom.mixin.useLegacyMixinAp` in
  `build.gradle`); without a refmap the audit fails with intermediary names in the "could not find any targets" error.
- Java: 25 for 26.x, 21 from 1.20.5, 17 before. `src/` must compile at Java 17 (no `switch` patterns, `_`,
  `getFirst()`/`getLast()`, `Math.clamp`...).
- `fabric.mod.json` and the mixin configs get `${minecraft}` (the range) and `${java}` filled in per version.
- Jars: `versions/<version>/build/libs/horsing-around-<mod version>+<minecraft version>.jar`.

## Version-specific code

Code for other versions sits in Stonecutter comments, newest first, the active (26.3) branch uncommented:

```java
//? if >=26.2 {
victim.knockback(power, x, z, source, damage);
//?} else
/*victim.knockback(power, x, z);*/
```

- Conditions name the version the newer code needs (`>=1.21.2`), so a chain reads from new to old:
  `//? if >=26.3 { ... //?} elif >=1.21.9 { /* ... */ //?} else { /* ... */ //?}`. Inactive branches are wrapped in
  `/* ... */` (a closing `*/` sits right before the next `//?`).
- Don't switch the active version to edit (the "Set active project" tasks rewrite `src/`); write the other branches
  by hand and let each version's build check them (`versions/<version>/build/generated/stonecutter/` shows what a
  version compiles).
- A replacement in `stonecutter.gradle` also runs the other way on the newer versions, so its new-version text must
  not occur in `src/` for them (e.g. 26.3 has its own `PlayerRenderState`); where it could, make it one way with a
  regex whose way back is `(?!)` (never matches). Diffing a version's `build/generated/stonecutter/` against `src/`
  shows only the intended differences.
- Prefer code that is the same on every version when it costs nothing (e.g. `Mth.sqrt(x * x + y * y + z * z)`
  instead of a `Mth.length` overload only some versions have).
- Keep the seams small: a difference used in several places goes in one small private helper with the conditional
  inside, not in every caller.
- A class or package that was only renamed goes in `stonecutter.gradle` as a string replacement, not in comments.
- A mixin whose target class changed gets its `@Mixin` target, method names and parameter lists in conditionals.
- Behaviour stays the same on every version; where an old version lacks something vanilla only added later (a spear's
  stab before spears existed), that branch just doesn't do it. List such gaps below.
- The code rules still apply on every version: no allocation in per-tick paths beyond vanilla's, no work for horses
  nobody rides.

## Checking a version

- `./gradlew :<version>:build`: compiles and builds that version's jar (`./gradlew build` builds them all).
- `./gradlew :<version>:auditMixins`: the built jar in a production client and on a dedicated server of that version;
  both load every class the mixins patch (any injection that finds nothing fails) and quit. About 20 seconds; one game
  at a time. Logs in `build/run/audit/<version>/`.
- `./gradlew :<version>:runClient`: play that version (its game folder is `run/versions/<version>/`; plain
  `runClient` is the main version).
- `./gradlew :<version>:genSources`: decompiled Minecraft for looking things up
  (`.gradle/loom-cache/minecraftMaven/net/minecraft/*/<version>/*-sources.jar`).
- The ride, terrain, mounts and server tests run on 26.3 only. A change that touches version-specific code is checked
  by building and auditing every version, and by playing the versions it touches.

## Progress

Building and passing their audit: 26.3, 26.2, 26.1.2, 1.21.11, 1.21.10 (covers 1.21.9), 1.21.8 (covers 1.21.6 and
1.21.7; only built against 1.21.8, the others still to be played), 1.21.5, 1.21.4, 1.21.3 (covers 1.21.2; only built against 1.21.3).
Before 1.21.2 entity rendering has no render states (the leg and rider-pose code needs a design for that), and before
1.20.5 networking has no payload types.

## Gaps on older versions

- 1.21.10 and older: no spears, so there is no spear stab pose for the rider (every swing is the plain one).
- 1.21.10 and older: the settings screen has no section headings in the vanilla style; each heading sits in a row of its own.
- 1.21.5: the stamina bar does not turn red when the horse is exhausted (there is no public tinted sprite draw yet); it
  still switches to the cooldown sprite.
- 1.21.10 and older: the steering overlay for play-testing (F3+B) draws nothing (gizmos came in 1.21.11); not for players.

Not gaps, but different underneath: before 1.21.11 vanilla horses sink in deep water, so the ride floats every horse
but the skeleton horse (`Mounts.floats`); the rest of the swimming is the ride's own on every version.
