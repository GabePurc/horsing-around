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

### Before 1.21.2: no render states

Up to 1.21.1 renderers draw the entity itself (`render(entity, ...)`) and models animate from it (`setupAnim(entity, ...)`);
the ride's render-side code is written around render states, so it keeps them:

- `client/render/state/` holds stand-ins (`LivingEntityRenderState`, `HumanoidRenderState`, `PlayerRenderState`,
  `EquineRenderState`) with only the vanilla fields this mod reads, and a `fill(entity, partialTicks)` that sets them as
  1.21.2's `extractRenderState` would. Each file is a whole-file `//? if <1.21.2 {` comment, so newer versions compile it
  as an empty file. `stonecutter.gradle` points `net.minecraft.client.renderer.entity.state.` at the package (one way), so
  the state-taking code (`RiderPose`, `Legs`, `GroundLegs`...) is the same on every version, and the state mixins
  (`LivingEntityRenderStateMixin`) simply target the stand-ins.
- `LivingEntityRendererMixin` keeps one stand-in per renderer (made once, of the type its model needs) and, as a ridden
  horse or its rider starts drawing (`render` HEAD), fills it, runs the same extraction body the newer versions run at
  `extractRenderState` TAIL, and publishes it in `LegacyRenderState` (cleared at `render` RETURN, so the first-person
  hands never see it). Other entities do no work. The rider's body and head turn, which a state carries in newer versions,
  are put into the renderer's own working with `@ModifyArg`s.
- The model mixins inject into the old `setupAnim(entity, ...)` and take the state from `LegacyRenderState.of(entity)`; the
  cowboy hat layer reads the player directly, and two extra mixins (`CustomHeadLayerMixin`, `HumanoidArmorLayerMixin`,
  listed in the mixin config only for these versions, see `hatMixins` in `build.gradle`) keep a helmet off a hatted head.
- Before 1.21 also: data pack tag folders are plural (`processResources` renames them in `build.gradle`), resource
  locations are made with a constructor, the options screens keep their own list (`HorseSettingsScreen`), the camera
  moves in doubles, and model colours are floats.
- Smaller differences: the player model's clothing layers and hat are parts of their own (copied from the body parts
  right after `setupAnim`, so the rider pose re-copies them), a horse model has no root part and a foal is the model drawn
  at half size (`LegacyRenderState.horseRoot`), keys come from a client `Input` view (`ride/Input`, a stand-in for the
  record 1.21.2 added), and the arm poses are fields of the model.

### Before 1.20.5: no payload types

There is no `CustomPacketPayload` + `StreamCodec` + `PayloadTypeRegistry` before 1.20.5. Fabric API's networking has
`FabricPacket` + `PacketType.create(id, reader)` there instead (1.20.2 to 1.20.4), and the payload classes in `net/` carry
both forms (a conditional on the class: the record's fields, and the codec written out as `write`/reader). The wire
format is the same on every version (the same fields in the same order). `ServerPlayNetworking`/`ClientPlayNetworking`
`send`/`canSend` take the packet or its `TYPE` on both, so the only call sites that differ are the registrations
(`HorsingAround`, `HorsingAroundClient`): receivers take `(packet, player, sender)` instead of `(payload, context)`, and
nothing is registered beforehand.

### Before 1.20.5: other differences

- Physics values vanilla keeps as attributes since 1.20.5 (gravity, safe fall distance, fall damage multiplier) come from
  `ride/Vanilla`; before, they are the fixed values vanilla used (0.08, 3 plus Jump Boost or 6 for a horse, and a half for a horse).
- A horse's collision box is narrowed through `LivingEntity.getDimensions` (no `getDefaultDimensions`, and the dimensions
  have no eye height or attachments yet); a rider's jump power is computed as vanilla's `executeRidersJump` did.
- No item components: "tool or weapon" in the rider's pose is an item class check (`TieredItem`, shears, trident, bow and crossbow).
- 1.20.2 and 1.20.1: the camera keeps no partial tick yet (nothing reads it there), lists have a top and a bottom of their
  own, `ConfirmLinkScreen.confirmLink` takes its arguments in another order, and a text component has no `withColor`.
- 1.20.1 only: no collision test for blocks alone (`Vanilla.noBlockCollision`, a one-way replacement in `stonecutter.gradle`),
  the stamina bar is cut from `icons.png` like the vanilla jump bar (no sprites, no `Mth.lerpDiscrete`), and Shoulder
  Surfing's perspective class sits in the other package.
- The settings screen places its own title, list and footer buttons by hand (`LegacyOptionsList`, a whole-file
  conditional): the vanilla options base class has no layout, and its list can't take widgets of our own.
- Inside a whole-file `/* */` conditional there is no room for another comment, so a difference inside one (the list's
  constructor) is a replacement in `stonecutter.gradle`.
- Rider pose: a trident now counts as a weapon on 1.20.5 to 1.21.4 too (it had no `WEAPON` component there); it is a tool/weapon
  on every version.

## Checking a version

- `./gradlew :<version>:build`: compiles and builds that version's jar (`./gradlew build` builds them all).
- `./gradlew :<version>:auditMixins`: the built jar in a production client and on a dedicated server of that version;
  both load every class the mixins patch (any injection that finds nothing fails) and quit. About 20 seconds; one game
  at a time. Logs in `build/run/audit/<version>/`.
- `./gradlew :<version>:runClient`: play that version (its game folder is `run/versions/<version>/`; plain
  `runClient` is the main version).
- `./gradlew :<version>:genSources`: decompiled Minecraft for looking things up
  (`.gradle/loom-cache/minecraftMaven/net/minecraft/*/<version>/*-sources.jar`).
- `./gradlew :<version>:runSmoke`: a client of that version (with Entity Model Features, Entity Texture Features and the
  Fresh Animations pack on, light settings) makes a flat world, builds a small scene and rides a saddled horse through a
  stand, walk, gallop, jump and stop with simulated keys, taking screenshots (the rider from the side, first person,
  third person from behind, a row of leashed mobs, a cow on the player's lead) into
  `build/run/smoke/<version>/screenshots/`. It fails unless the log says `Horsing Around smoke test passed`, nothing in
  it names this mod in an error, and the server corrected none of the rider's moves ("moved wrongly" and the like; with
  `-PsmokeWithoutMod` those are vanilla's own and only warned about). Plain `./gradlew runSmoke` runs every version, one game at a time (about a minute each).
  `-PsmokeNoPack` leaves the animation pack off, `-PsmokePlain` leaves Entity Model Features, Entity Texture Features and
  the pack off, to tell this mod's problems from the animation stack's; `-PsmokeWithoutMod` runs the same scene with this
  mod turned off (Fabric Loader's `fabric.debug.disableModIds`; the checks that need the mod are skipped). The test is `src/smoke` (client tick state
  machine, a source set Stonecutter processes like the others; `Compat` holds what differs between versions); it is not
  part of the jar. The side views of the rider turn the player's view a quarter turn between ticks (vanilla draws the
  local player only through its own camera), so in them the rider's head looks away from the camera.
- The ride, terrain, mounts and server tests run on 26.3 only. A change that touches version-specific code is checked
  by building and auditing every version, by the smoke test on every version, and by playing the versions it touches.

## Progress

Building and passing their audit: 26.3, 26.2, 26.1.2, 1.21.11, 1.21.10 (covers 1.21.9), 1.21.8 (covers 1.21.6 and
1.21.7; only built against 1.21.8, the others still to be played), 1.21.5, 1.21.4, 1.21.3 (covers 1.21.2; only built against 1.21.3),
1.21.1 (covers 1.21; built and audited only, not yet played), 1.20.6 (covers 1.20.5; built and audited only, not yet played),
1.20.4 (covers 1.20.3; built and audited only, not yet played), 1.20.2 (built and audited only, not yet played), 1.20.1 (covers 1.20; built and audited only, not yet played).

## Gaps on older versions

- 1.21.10 and older: no spears, so there is no spear stab pose for the rider (every swing is the plain one).
- 1.21.10 and older: the settings screen has no section headings in the vanilla style; each heading sits in a row of its own.
- 1.20.6 and older: Entity Model Features has no animation hook there (`EmfSaddleTracker` is not built), so on a horse that
  an animation pack animates the rider follows the generated gait motion, and the pack's neck and legs are not adjusted for
  the ride.
- 1.21.1 and older: a horse's leg box is found by its size alone (the faces of a box are private there; vanilla's and the
  animation packs' leg boxes are whole boxes anyway).
- 1.21.10 and older: the steering overlay for play-testing (F3+B) draws nothing (gizmos came in 1.21.11); not for players.

Not gaps, but different underneath: before 1.21.11 vanilla horses sink in deep water, so the ride floats every horse
but the skeleton horse (`Mounts.floats`); the rest of the swimming is the ride's own on every version.
