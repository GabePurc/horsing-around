# Completed steps

A short summary of what exists, by area. The code is the truth for current behaviour; "done" means built and tested at
the time, not finished forever (see `AGENTS.md`, "How to use these docs"). The detailed log up to 2026-10-14 is in git
history (`git show 46ab37f:completed_steps.md`), and the details of each change are in its PR. New entries: one bullet,
at most two lines, under the right area; details go in the PR, not here.

## Riding core

- Gaits (walk, trot, canter, gallop): sprint tap up, S tap down, release W to coast, hold S to brake then back up; key taps count as presses (sub-tick taps, toggle-sprint)
- Double-tap W: steps up a gait and locks the horse into riding on with W let go, until S (vanilla's 7-tick window; W's key repeat ignored)
- Per-gait momentum; weight-shift turning (head leads, then the horse commits and banks, then the body turns); grip-limited turning circle that widens with speed; tight turns scrub speed
- Steering: the horse heads where the rider looks, the view is never pulled; W+A/D rides 45 degrees off the view, A or D alone 90; standing still with no keys is free look
- Hard cuts: the further the view is off the horse's heading, the harder it cuts (sits back, more grip, slows to make it); only while riding on, letting go stands it up (`-Psections=cuts`, `strafe`)
- Stamina: gallop drain, exhaustion forces a canter until recovered; stamina bar takes the jump bar's slot (also with HUD mods such as Better Mount HUD); tired horse breathes and tosses its head
- Speed stays continuous through drops and landings; a wall hit above a canter sheds speed, and only a head-on hit counts as a crash
- Ridden collision box narrowed to 0.9 blocks, step height 1.125; the server and other clients keep the rider-reported heading

## Camera and first person

- Riding camera: third person on mount (restored on dismount), pulls back with speed, speed FOV, pitch eases to a riding angle when the mouse is idle; height follows in two eased stages; passes through leaves
- First person: eyes follow the smoothed saddle height and never pitch or roll with the horse; reduced bob, nod and hoof-strike shake with View Bobbing; hands and held items bob with the saddle
- Over-the-shoulder riding camera (default right; left, or off for centred; O swaps), folded in from the retired add-on: centre crosshair with targeting and server rotation aimed at it, leans in to aim, walls pull it in and it eases out; on foot third person stays vanilla
- Steps aside while Shoulder Surfing Reloaded drives the camera

## Jumping, ledges, 2-block climbs, hurdles

- Jump: instant on press with a landing buffer, power from speed, ground speed carried through the air, stamina cost, nose-up take-off and nose-down landing tilt; no mid-air double-height step-ups
- 2-block ledges: gather, heave up and walk on, from a walk to a gallop, any angle up to 60 degrees and with whatever is round it (corners, slabs, snow, stairs, diagonal terraces); walls of 3 and faces ridden along are left alone (`-Psections=climbs`)
- Hurdles: fences, walls and gates (1.125-1.6 tall) are jumped at any pace; lava or drops beyond are refused; jump spam at a ledge waits for the heave
- Legs, tail and rider take the jump's shape (front legs fold then reach, tail trails then flicks, rider folds over the neck then sits up)

## The horse has a say

- Look-ahead (`Awareness`): walls, drops beyond the safe fall distance and hazards (lava, fire, magma, cactus, berry bushes, powder snow, cobwebs, lit campfires, `horsingaround:horse_avoids`); jumpable gaps are passed over
- Planning a line: candidate ways played out with the horse's own steering and momentum, cheapest kept, replanned every other tick and committed to; goes round trees, rocks, rows, walls and tight streets at near full pace (`-Psections=ways`)
- Refusals: slows to a walk before walls, stops short of cliffs and lava with a snort and head toss, plants its feet at a gap's lip, won't jump off a cliff; an edge guard covers the last step
- Falls by health: takes a drop only if horse and rider both come out well, none below 40% health; on slopes it picks a pace that lands unhurt
- Hazards are obstacles (gone round or stopped for); a hazard the body is already in doesn't trap it
- Natural-terrain test (`TerrainRideTest`, `-Ptests=terrain`): forests, mountains, hills, taiga hazards, river, badlands ridden like a player; checks hazards, falls, dead ends, crashes, tilt smoothness and tick cost

## Body, legs and steps

- The body is a mass on its legs: front and hind ends each ease to the ground under their hooves, tilt is the line between them, forehand leads on steps, legs give on landing
- Each hoof sits on its own ground exactly (block grid, slabs, stairs, water as a floor), upright against slopes only, never in the ground; vanilla's one-piece legs, no knee joints
- Running up a step eases in without a pop; drawn tilt is capped per tick; no rearing against a face
- Leg animation speed tracks the gait so Fresh Animations shows the right cycle; legs take the shape of steps and jumps (vanilla model and Fresh Animations, every layer)
- Neck reaches with the tilt (Fresh Animations' "neck2"); armour and bridle follow the head
- Ground probes go through leaves to the ground, so bushes aren't read as steps; the drawn horse never stands on leaves
- Footing: in the air a hoof finds anything within a step, a shoulder caught on a corner slips past; snaps are measured in `slowstep` and `drawn` (legs as the renderer drew them, via `LegProbe`)
- Idle horses' legs are reset before posing on versions before 1.21.2 (shared model)

## Rider

- Seated pose pinned to the saddle through bank, pitch, steps, gait motion and Fresh Animations' sway; feet in the stirrups, hands on the reins; torso twists toward the look; seat bounce and a calm canter rock per gait
- Rider rides the horse's real animated body (Entity Model Features hook, or the same generated motion without it); inertia sways the rider back on surges and forward on braking
- Hands: a tool or weapon is held ready at the side, swings play from there; drawing a bow turns the rider side-on with the string hand to the cheek; crossbow held on the aim (`-Psections=hands`)
- Leaves at chest or face: the rider leans back and shields their face; the horse's neck stretches (and the rider folds less) so head and rider never clip
- Shulker boxes with Entity Model Features no longer crash the saddle tracker (`-Psections=icons`)

## Water and banks

- Wading keeps the horse's footing and slows with depth; swimming floats the back at the waterline, tires the horse, keeps the rider mounted
- Banks: climbs out of water onto a bank up to a block above the water layer in one smooth heave, higher banks refused; leaving water doesn't launch the body (`-Psections=exits`)

## Sounds and trampling

- Hoof sound from actual ground speed; tired breathing; soft leaf rustle with leaf bits; dirt scuffed in hard cuts
- Trampling: above a walk a ridden horse damages small creatures it runs through (scaling to 4 hearts at a gallop), never players or the rider's pets; decided on the server from speed over the last four ticks

## Settings and supporter hat

- Settings are comfort and preference only (user decision, 2026-10-08; nothing that changes how horses ride): third person on mount, camera distance and height, speed zoom-out, first-person saddle and hand motion, extra horse sound volume; `HorseConfig` JSON, vanilla-style screen from Mod Menu (optional)
- Polished menus in both mods: icon, description, tooltips, plain values; Buy Me a Coffee link in READMEs and Mod Menu
- Supporter cowboy hat (cosmetic only): UUIDs in `supporters.json` downloaded at startup and cached, colour relayed through the server (`HatRelay`, throttled to every half second); helmet hidden but worn (`HatTest`, `-Ptests=hat`); shape from the user's Blockbench model (`art/cowboy_hat.bbmodel`, rebuilt into `HatModel` and the texture by `scripts/hat-model.py`)

## Multiplayer and servers

- For servers that have the mod (user direction, 2026-10-07): the server sends a `horsingaround:rules` payload; without it, or on a protocol mismatch, the client rides vanilla and says so once
- `ServerRideTest` (`-Ptests=server`) joins a real dedicated server and rides everything with zero server corrections; `DedicatedServerTest` (`./gradlew runGameTest`) loads the mod on a bare server
- Sprint, back and jump presses are only drained while riding (`RiderKeys`); managed-horse cache counter is atomic

## Other mounts and mod compatibility

- The horse family is chosen by the entity type tag `horsingaround:managed` (horse, donkey, mule, skeleton and zombie horse; other mods can opt in); every other mount keeps vanilla controls with no per-tick work; mob riders keep their horses' box (`MountsTest`, `-Ptests=mounts`)
- Mixins: one per vanilla class, chainable result changes instead of cancelling injections, cosmetic hooks optional; an Entity Model Features API change logs a warning
- Works with Fresh Animations, EMF and ETF (dev runs load them); steering wins over Horseman's free camera, rider pose applied after Not Enough Animations
- `./gradlew runCompat --continue` runs production games with packs of popular mods (`compatPacks`, pinned by Modrinth version id); run before a release and after touching mixins

## Multiple Minecraft versions

- One `src/` and one jar per group of versions, 1.20.1 to 26.3, built with Stonecutter; version differences in `//?` comments and `stonecutter.gradle` (`docs/porting.md`)
- `./gradlew auditMixins` runs every version's jar on its own minimum Fabric Loader and a production client and server; older loaders supported per version (MixinExtras bundled below loader 0.16)
- In-game smoke test on every version (`./gradlew :<version>:runSmoke`; `-PsmokeNoPack`, `-PsmokePlain`, `-PsmokeWithoutMod`)
- Riding camera crash before 26.1 fixed (a mixin field initialiser dropped by Mixin); the audit now checks patched constructors set every field with a starting value

## Release, store and publishing

- MIT licence, README doubling as the Modrinth page (banner, buttons, looping clips and stills in `docs/media/`), gallery shots (`-Ptests=gallery`), store filming (`-Ptests=store`, `StoreMediaTest`, `scripts/store-media.sh`)
- Store clips re-shot for 0.4.0 like a film (30 fps, slow motion, moving camera set-ups, golden-hour sky); every frame drawn at
  its own moment on the game's timer, read back before the next and encoded as a keyframe, so legs and pace stay smooth
- Modrinth description plays the clips as MP4 video (`scripts/modrinth-description.sh`), GitHub's README as WebP; both framed
  in the logo's orange
- Every picture in the description is a clip (user request, 2026-10-20): rider, hat and the as-you-play shots (stamina bar,
  riding camera, settings, 16:9 with the HUD) replaced the stills, which went to the Modrinth gallery
- CI: GitHub Actions builds every push; a published release (tag `v<version>`) uploads each version's jar to Modrinth (`gradle/publish.gradle`, `publish.yml`, notes from `CHANGELOG.md`'s top entry)
- Bug reports: a GitHub issue form (`.github/ISSUE_TEMPLATE/bug_report.yml`) linked from the README and Mod Menu
- Builds on any machine: no machine-specific paths, JDK 25 picked through `gradle-daemon-jvm.properties`
- Code review fixes (2026-10-14): hat throttle, trampling speed window, supporters cache written safely and only on change
- Whole-codebase review (2026-10-14, PR #42): falls follow each version's vanilla rules, dismount and hurdle state resets, NaN guards, config and
  supporters hardening, shared helpers (`RidePoser`, one ground probe), release dry run on PRs; smoke and ride tests fail on server corrections

## Tests and tooling

- `./gradlew runClientGameTest` runs `RideFeelTest`, `TerrainRideTest`, `MountsTest` and `ServerRideTest`; `runClientGameTestParallel --continue` splits them into five light shards and merges the reports; all fail on server corrections and log warnings; test games run light at the default frame rate
- The running jump is traced every tick (`HorseTrace`): each tick starts where the last ended and the takeoff moves one step
  (a reported takeoff lurch was the store test missing the tick `pressKey` runs)
- Debug pack colours each horse leg part for screenshots (`-PplainTextures` turns it off); F3+B shows the steering overlay (box, line, heading, plan, look-ahead findings)
- Project docs (user request, 2026-10-14): `AGENTS.md` says how to use the docs (user direction first, intent kept, numbers are snapshots, fix what is wrong, ask only to reverse a user decision, change the feel noticeably or widen scope)
- Docs trimmed to save tokens (2026-10-14): `research.md` is a short summary with the history in `docs/design-log.md`; tests in `docs/testing.md`, releasing in `docs/releasing.md`; this file condensed

## Open / known limits

- Trotting down a slope of full blocks keeps hooves off the ground at times: a known limit (user decision, see `research.md`), tests set to what it does
- Not yet played by the user on any version but 26.3
- Skid stop is still open (MVP)
- Fabulously Optimized 1.20.1 ships Fabric Loader 0.14.23, older than MixinExtras' floor (0.14.25): out of reach
- Hoof soles meeting a bank's top mid-heave still jump up to 0.34 in a frame (logged by `exits`)
- "Walking down a staircase of stair blocks": hooves 90th percentile ~0.29-0.31 against a 0.300 limit (tight margin)
- Leads tied above the llama's and horse's heads on 26.x come from Fresh Animations, not this mod
