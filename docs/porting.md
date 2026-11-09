# Building for several Minecraft versions

One jar per group of versions, 1.20.1 to 26.3, all from the one `src/` with [Stonecutter](https://stonecutter.kikugie.dev/). 26.3 is the
main version: `src/` reads as 26.3 code, and the compat runs exist only there.

## Layout

- `settings.gradle` lists the versions; each is a project `:<version>` with its numbers in `versions/<version>/gradle.properties`.
- `build.gradle` runs per version. `loom-back-compat` picks Fabric Loom: plain for 26.x (unobfuscated), remapping with Mojang names before,
  so dependencies use `modImplementation` and friends. Before 26 the mixin annotation processor makes refmaps (`loom.mixin.useLegacyMixinAp`)
  and `build.gradle` names them in the mixin configs, because Loom's own step adds nothing on a clean configuration-cached build, which is
  every GitHub build. `checkRefmaps` fails a jar without them. Without one the game crashes with intermediary names in "could not find any targets".
- Java: 25 for 26.x, 21 from 1.20.5, 17 before. `src/` must compile at Java 17 (no `switch` patterns, `_`, `getFirst()`/`getLast()`, `Math.clamp`).
- `fabric.mod.json` and the mixin configs get `${minecraft}` and `${java}` per version. Below loader 0.16, MixinExtras is bundled.
- NeoForge: `<version>-neoforge` projects (26.3, 26.2, 26.1.2, 1.21.1) build the same `src/` with `build.neoforge.gradle`. Code for one loader
  sits in `fabric/` or `neoforge/` packages or behind `//? if neoforge`; common code goes through `Platform`. Mojang names, so no refmap.
  `neoforge.mods.toml` is its `fabric.mod.json`. Client tests are Fabric's only.
- Forge: `1.20.1-forge` (Realistic Horse Genetics' version), also `build.neoforge.gradle`, with ModDevGradle's legacy add-on; `forge/`
  packages or `//? if fabric {} elif neoforge {} else {}`; `mods.toml`. It runs on SRG names: the mixin annotation processor writes
  refmaps (any name it can't map fails the compile), the manifest lists the mixin configs, `checkRefmaps` checks both in `reobfJar`'s
  jar (the release one). The jar carries MixinExtras and a `pack.mcmeta` (Forge warns about a mod without one). Its dev games load Realistic
  Horse Genetics (`-PnoGenetics` not) but not EMF or ETF, whose Forge jars name SRG names with no refmap. Its audit runs dev classes, as
  NeoForge's does: after changing mixins or Forge calls, check the release jar by hand in a game Forge's installer makes (`--installServer`,
  `--installClient`), run with `-Dhorsingaround.audit=true`.

## Version-specific code

```java
//? if >=26.2 {
victim.knockback(power, x, z, source, damage);
//?} else
/*victim.knockback(power, x, z);*/
```

- Conditions name the version the newer code needs, reading new to old:
  `//? if >=26.3 { ... //?} elif >=1.21.9 { /* ... */ //?} else { /* ... */ //?}`. A closing `*/` sits right before the next `//?`.
- Don't switch the active version to edit (those tasks rewrite `src/`). Write the other branches by hand and let each version's build check
  them; `versions/<version>/build/generated/stonecutter/` shows what a version compiles.
- A class or package that was only renamed goes in `stonecutter.gradle` as a string replacement. Replacements also run backwards on newer
  versions, so the new text mustn't occur in `src/` for them; where it could, use a regex whose way back is `(?!)`.
- Replacements whose matches overlap don't both apply: one is silently dropped. Anchor one with a lookbehind so they match apart.
- Inside a whole-file `/* */` conditional there's no room for another comment, so a difference there is a replacement too.
- The tests (`src/gametest`) follow the same rules. A test mixin only some versions have is a whole-file conditional that its config's
  plugin adds (`getMixins`), since a config can't list a class a version doesn't compile.
- A mixin whose target changed gets its `@Mixin` target, method names and parameters in conditionals.
- Mixin can drop a field initialiser on older versions (it crashed the riding camera before 26.1); the audit checks patched constructors
  set every field that has a starting value.
- Prefer code that is the same everywhere when that costs nothing, and keep seams small: a difference used in several places goes in one
  small helper.
- Behaviour stays the same on every version. Where an old version lacks something vanilla added later, that branch just doesn't do it; list it
  under Gaps.

### Before 1.21.2: no render states

Up to 1.21.1 renderers draw the entity itself and models animate from it, but the ride's render code is written around render states, so it
keeps them:

- `client/render/state/` holds stand-ins (`LivingEntityRenderState`, `HumanoidRenderState`, `PlayerRenderState`, `EquineRenderState`) with only
  the fields this mod reads, each a whole-file `//? if <1.21.2 {` comment. `stonecutter.gradle` points `net.minecraft.client.renderer.entity.state.`
  at the package (one way), so state-taking code is the same everywhere.
- `LivingEntityRendererMixin` keeps one stand-in per renderer. As a ridden horse or its rider starts drawing, it fills it, runs the same extraction
  the newer versions run, and publishes it in `LegacyRenderState` (cleared at `render` RETURN, so first-person hands never see it). The rider's body
  and head turn go in through `@ModifyArg`s. Other entities do no work.
- Model mixins inject into the old `setupAnim(entity, ...)` and read `LegacyRenderState.of(entity)`. The hat layer reads the player directly, and
  `CustomHeadLayerMixin` and `HumanoidArmorLayerMixin` (only these versions, `hatMixins` in `build.gradle`) keep a helmet off a hatted head.
- Idle horses' legs are reset before posing, because the model is shared.
- Smaller: clothing layers and hat are separate parts, re-copied by the rider pose; a horse model has no root part and a foal is the model at half size
  (`LegacyRenderState.horseRoot`); keys come through `ride/Input`; arm poses are model fields.

### Before 1.21

Data pack tag folders are plural (`processResources` renames them), resource locations use a constructor, options screens keep their own list
(`HorseSettingsScreen`), the camera moves in doubles, and model colours are floats.

### Before 1.20.5

- No payload types. Fabric API has `FabricPacket` + `PacketType.create(id, reader)` from 1.20.2 to 1.20.4, and classes in `net/` carry both forms. The wire
  format is the same. Only the registrations differ (`HorsingAround`, `HorsingAroundClient`): receivers take `(packet, player, sender)`, nothing registered first.
- Physics attributes (gravity, safe fall distance, fall damage multiplier) come from fixed values in `ride/Vanilla`.
- The horse's box is narrowed through `LivingEntity.getDimensions`; a rider's jump power is computed as vanilla's `executeRidersJump` did.
- No item components: "tool or weapon" is an item class check.
- The settings screen lays itself out by hand (`LegacyOptionsList`, a whole-file conditional).
- 1.20.2 and older: the camera has no partial tick, lists have their own top and bottom, `ConfirmLinkScreen.confirmLink` takes other argument order, and
  text components have no `withColor`.
- 1.20.1 only: no block-only collision test (`Vanilla.noBlockCollision`), the stamina bar is cut from `icons.png`, and Shoulder Surfing's perspective class is in
  another package.

## Checking a version

- `./gradlew :<version>:build`; `./gradlew build` builds them all. Jars: `versions/<version>/build/libs/`.
- `./gradlew :<version>:auditMixins`: the jar in a production client and dedicated server, on that version's minimum Fabric Loader; any injection that finds
  nothing fails. About 20 s, one game at a time per loader. Logs in `build/run/audit/<version>/`. NeoForge's audit runs the mod's dev classes, not the jar.
- `./gradlew :<version>:runSmoke`: see `testing.md`.
- `./gradlew :<version>:runClient`: play it (game folder `run/versions/<version>/`; NeoForge `run/neoforge/<version>/`, and `runServer` too).
- `./gradlew :<version>:genSources`: decompiled Minecraft (`.gradle/loom-cache/minecraftMaven/net/minecraft/*/<version>/*-sources.jar`).

A change to version-specific code is checked by building and auditing every version, the smoke test and client tests on each, and playing
the ones it touches.

## Status

Build and pass the audit: 1.20.1 Forge, 26.3, 26.2, 26.1.2, 1.21.11, 1.21.10 (covers 1.21.9), 1.21.8 (1.21.6-7), 1.21.5, 1.21.4, 1.21.3 (1.21.2), 1.21.1 (1.21), 1.20.6
(1.20.5), 1.20.4 (1.20.3), 1.20.2, 1.20.1 (1.20). A jar covering several is built against the named one. Played by the user: only 26.3.

## Gaps on older versions

- 1.21.10 and older: no spears, so no spear stab pose. The settings screen's section headings each take a row of their own. No F3+B steering overlay (gizmos came in 1.21.11).
- 1.21.1 and older: a horse's leg box is found by its size alone (box faces are private there).

Different underneath, not gaps (each handled where named):
- Before 1.21.11 vanilla horses sink in deep water, so the ride floats all but the skeleton horse (`Mounts.floats`).
- Before 1.21.5 vanilla slows a ridden horse 2% a tick on its rider's client: the ride takes that off (`LivingEntityMixin`).
- Before 1.20.5 the pose stack entities are drawn with starts with the camera's turn: `LivingEntityRendererMixin` takes it off the drawn pose.
- Entity Model Features 3.0 (1.20.2-1.20.6) has no animation hook: the ride runs after the pack by wrapping the model's animation
  (`EmfAfterPack`, `client/mixin/emf/`).
- 1.21.2-1.21.3 scale a horse as they draw it; Fresh Animations' files for 1.21.2 on lift the root to where 1.21.4's scaled model
  has it, so its horses float there: `EmfRide` sets them back down.
