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
- "Galloping straight at a narrow 2-block ledge with a way round" once ended 0.95 blocks off the line (target 0.3) under heavy
  machine load, having started 0.6 off.
- "Walking down a staircase of stair blocks": hooves' 90th percentile 0.23-0.32 against 0.3, failing only with other tests.
- Icy's "trotting up a slope of full blocks" median 0.116-0.135 against 0.12 (Icy's trot lifts the hooves higher).
- The horses pack's nautilus once rode 0.54 blocks (target 1).

## Known issues

- Hoof soles meeting a bank's top mid-heave still jump up to 0.34 blocks in a frame (logged by `exits`).
- Galloping up a ragged hillside, the head can dip about 0.2 blocks into a step for a moment (`hill`, limit 0.3).

Outside this mod's control:

- Horseman crashes the client test's second in-process server (its server config isn't loaded there), so the horses pack
  skips the server test; a real server is unaffected.
- Fabulously Optimized on 1.20.1 ships Fabric Loader 0.14.23, older than MixinExtras' floor of 0.14.25.
- Leads tied above the llama's and horse's heads on 26.x come from Fresh Animations.
