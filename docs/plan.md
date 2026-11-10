# Plan

A backlog, not a sequence: pick up what the user asks for, suggest reordering, and delete what the mod has moved past. New
features need the user's OK (see `AGENTS.md`); each feel change ends with the user play-testing it.

## Ideas

- Horse perks and levelling, so a horse you've ridden far beats one tamed yesterday (the README's "What's next").
- Skid stop and rear when braking hard from a gallop (the one item left from the original scope).
- Stumble or throw the rider on a hard crash.
- Terrain speed: slower on steep slopes, soft blocks and snow; faster on paths.
- Spook and rear near hostile mobs.
- Whistle to call your horse; brushing and feeding.
- Sync gait and stamina to the server, so stamina persists and other clients can see the gait.
- Gait-specific hoof cadence.
- Expose the gait to Fresh Animations (an EMF custom variable), if its speed-based legs don't read gaits clearly enough.

## To check

- Modrinth (project `Lc7S9t3K`) won't accept the mod (user, 2026-11-03), yet `publish.yml` still uploads there and
  `scripts/store-descriptions.sh` still writes its description. Ask the user whether to drop it.
- The store gallery (`build/run/clientGameTest/gallery/`) may still need doing: ask the user.
- Only 26.3 has been played by the user; see the list in `porting.md`.
- Emotes (Emotecraft) and Better Combat attack animations on horseback: our rider pose runs after other mods' player-model
  hooks, so check they still play if players report otherwise.

## Flaky or tight checks

Each needs a look: a real fault, or a limit to set from the stride itself.

- "All three mob riders still mounted": the skeleton ended up on foot in 3 of 20 full runs, once even 8 blocks apart behind glass, so
  probably not the spear.
- "Trotting up a slope of full blocks": median 0.116-0.135 against 0.12 in Icy's (its trot lifts the hooves higher), failing over
  half of runs; 0.096-0.133 without, failing 2 of 28. Its "never far above": 0.94-0.98 against 1.0; 1.20.4 went 1.004-1.005 in 2 of 3.
  Walking up, before 1.21.4: 0.110-0.121; up stair blocks, the 90th percentile 0.224-0.253 against 0.25.
- Once each in a parallel run, passing alone: 1.21.5 "down a mountain, layout 2" stuck under leaves; 1.21.8's chunk-render wait
  timed out; 1.21.5 trampled 0 of 4 chickens.
- Icy's "up a 1-block step at a walk", no hoof in the step: -0.081 to -0.091 against -0.08 in a quarter of runs.
- The nautilus in a pack rides little now and then (limit 0.4): 0.54 in the horses pack; in Icy's 0.945 or 2.258, the low one more
  often on the frame clock (3 of 4 runs, against 0 of 2 at normal speed); about 3.7 with no pack.
- Icy's cart "room", "the rider's seat is well clear of the horse's hindquarters": a different breed's seat 0.4-1.5 instead of
  1.9-2.1 in 3 of 16 runs; perhaps read before the rider settles (5 ticks after boarding).
- "Galloping at a 9-block drop, healthy": no health lost (target 0.5-1) once in Icy's, once on 26.2-neoforge, passing on reruns.
- The legs' blend into their pose in the air (`AirLegs`) isn't tested: making it snap fails no check.

## Known issues

- A hoof meeting a step's or a bank's top can still jump up to 0.34 blocks in a frame (`exits` logs it, `-Psmooth` ranks them).
- Galloping up a ragged hillside, the head can dip 0.31-0.39 blocks into a step for a moment (`hill`, limit 0.4).
- Icy's carts don't collide going forward: they pass through a trunk or cliff if the horse cuts close.
- Icy's breeds don't sink onto their legs landing from a 2-block drop walked off (`landings` fails, every run).

Outside this mod's control:

- Horseman crashes the client test's second in-process server (its server config isn't loaded there), so the horses pack
  skips the server test; a real server is unaffected.
- Fabulously Optimized on 1.20.1 ships Fabric Loader 0.14.23, older than MixinExtras' floor of 0.14.25.
- From 1.21 with Fresh Animations, a llama's lead is tied about a block above its head: Entity Model Features adds the pack's
  `render.leash_offset_*` (the head's own position) to vanilla's point instead of using it.
