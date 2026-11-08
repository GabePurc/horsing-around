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

- Modrinth (project `Lc7S9t3K`) won't accept the mod and its page isn't public (user, 2026-11-03), yet `publish.yml` still
  uploads there and `scripts/store-descriptions.sh` still writes a description for it. Ask the user whether to drop it.
- The store gallery (`build/run/clientGameTest/gallery/`) may still need doing: check with the user.
- Only 26.3 has been played by the user; see the list in `porting.md`.
- Emotes (Emotecraft) and Better Combat attack animations on horseback: our rider pose runs after other mods' player-model
  hooks, so check they still play if players report otherwise.

## Flaky or tight checks

Each needs a look: a real fault, or a limit to set from what the stride itself does.

- "Trotting past a 2-block ledge that only catches the flank" failed 2 of 7 full runs on a release branch, always passing
  alone; the lane logs its path, so the next failure shows what happened.
- "All three mob riders still mounted": the skeleton ended up on foot in 3 of 20 full runs, once even with the riders 8 blocks apart behind
  glass, so probably not the spear.
- "Trotting up a slope of full blocks": in Icy's, median 0.116-0.135 against 0.12, failing over half of runs (Icy's trot lifts the hooves
  higher). Without Icy's it's 0.096-0.133, failing 2 of 28 runs of that shard.
- Icy's "up a 1-block step at a walk", no hoof in the step: -0.081 to -0.091 against -0.08 in about a quarter of runs.
- The nautilus in a pack rides little now and then: 0.54 in the horses pack; in Icy's 0.945 or 2.258, the low one more often on the frame
  clock (3 of 4 runs, against 0 of 2 at normal speed); about 3.7 with no pack. Limit 0.4, from 1.
- Icy's terrain "up a mountain", "the body tilts steadily": 12.7 against 10 in one full run, passing alone.
- Icy's cart "room", "the rider's seat is well clear of the horse's hindquarters": one breed's seat 0.4-1.5 instead of 1.9-2.1 in 3 of
  16 runs, a different breed each time; perhaps read before the rider settles on the bench (5 ticks after boarding).
- Icy's "galloping at a 9-block drop, healthy": once landed below with no health lost (target 0.5-1), passing on the rerun.
- Making the legs snap into their pose in the air (the blend in `AirLegs`) fails no check: the blend isn't really tested.

## Known issues

- Hoof soles meeting a bank's top mid-heave still jump up to 0.34 blocks in a frame (logged by `exits`).
- Galloping up a ragged hillside, the head can dip 0.31-0.39 blocks into a step for a moment (`hill`, limit 0.4).
- Icy's carts don't collide going forward: they pass through a trunk or cliff if the horse cuts close.

Outside this mod's control:

- Horseman crashes the client test's second in-process server (its server config isn't loaded there), so the horses pack
  skips the server test; a real server is unaffected.
- Fabulously Optimized on 1.20.1 ships Fabric Loader 0.14.23, older than MixinExtras' floor of 0.14.25.
- From 1.21 with Fresh Animations, a llama's lead is tied about a block above its head: Entity Model Features adds the pack's
  `render.leash_offset_*` (the head's own position) to vanilla's point instead of using it.
