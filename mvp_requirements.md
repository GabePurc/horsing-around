# MVP requirements

The MVP is done when riding a horse, donkey or mule feels clearly weightier and more alive than vanilla, using only
vanilla keys, with no crashes in singleplayer and on a dedicated server with the mod on both sides.

## In scope

1. **Gaits**: walk, trot, canter, gallop. Sprint tap steps up, S tap steps down, releasing W eases to a stop, S held
   brakes then backs up slowly.
2. **Momentum**: gradual acceleration per gait, gradual coasting stop, harder braking.
3. **Turning inertia**: horse turns toward the camera at a speed-limited rate; free look while standing still; A/D
   steer horse and camera together; no strafing.
4. **Stamina**: galloping drains; exhaustion forces a canter until recovered; small vanilla-style indicator.
5. **Body language**: horse and rider lean into turns.
6. **Sounds**: hoof clip matches actual speed.
7. **Collisions**: running into walls at speed sheds momentum.
8. **Multiplayer correctness**: other players see the horse's real heading, not the rider's camera.
9. **Skid stop / rear**: hard braking from a gallop skids and can rear.
10. **Config**: feel numbers adjustable without recompiling.
11. **Camera**: RDR2-style third-person riding camera (see `research.md`).
12. **Jumping**: instant, momentum-based jumps; the jump bar shows stamina.
13. **Fresh Animations**: works with EMF + ETF + Fresh Animations without visual or log errors.

## Out of scope for MVP

Bonding, whistle/calling, brushing and feeding, predator fear, new models or animations, new items, saddlebags,
camel or llama changes.

## Acceptance

- From a standstill, reaching a full gallop takes roughly 2 seconds of spurring.
- From a gallop, releasing W stops in about 2 seconds; holding S stops in under 1 second.
- A gallop turn is visibly wider than a walk turn.
- A full stamina bar lasts roughly 14 seconds of galloping.
- A running jump keeps at least 85% of ground speed in the air.
- `./gradlew runClientGameTest` passes.
- No per-tick allocations beyond vanilla's for ridden horses; no work at all for unridden horses beyond a cheap reset.

## Release (Modrinth) requirements

User direction (2026-10-06): the mods will be published on Modrinth for anyone to install. Before the first release:

1. **Any machine**: both repos build from a fresh clone on Windows, macOS and Linux with only a Java 17+ runtime for
   the Gradle launcher; Gradle finds or downloads JDK 25 itself. CI builds every push.
2. **Plain Fabric install**: needs only Fabric Loader and Fabric API. Mod Menu, EMF/ETF and Fresh Animations stay
   optional; the add-on works with or without Horsing Around.
3. **Every install mix works**: mod on both sides, client only (vanilla or unmodded servers: no rubber-banding, no
   kicks), server only (vanilla clients ride normally). Features that need the server's agreement switch off when the
   server lacks the mod.
4. **Compatible with basically any mod**: no crashes, no log errors, and no silently disabled features (ours or
   theirs) alongside popular Fabric mods, checked by an automated run with a mod pack (see `development_plan.md`,
   Release). Doesn't have to hold in dev runs before then.
5. **Store-ready metadata**: license, authors, links (source, issues), description, icon, screenshots.
