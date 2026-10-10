# Completed steps

## Phase 0: project setup

- [x] Fabric mod project for Minecraft 26.3 (Loom 1.18, Fabric API 0.162.0+26.3, Gradle 9.7.1, JDK 25)
- [x] Design docs: `research.md`, `mvp_requirements.md`, `development_plan.md`

## Phase 1: core feel (verified in-world by the automated ride test; awaiting user play-test)

- [x] Gaits (walk, trot, canter, gallop): sprint tap up, S tap down, release W to coast, hold S to brake then back up
- [x] Key taps counted as presses, so sub-tick taps and toggle-sprint both work
- [x] Momentum: per-gait acceleration and deceleration
- [x] Turning inertia: speed-limited turn rate toward the camera, free look when standing still, no strafing
- [x] A/D steering is horse-led; the camera swings around behind the horse
- [x] Stamina: gallop drain, exhaustion forces canter until 30%
- [x] Lean into turns for horse and rider
- [x] Hoof sound chosen from actual ground speed
- [x] Wall hits above a canter shed half the speed
- [x] Server and observing clients keep the rider-reported heading instead of snapping to the rider's camera
- [x] Horse head no longer pitches with the rider's camera
- [ ] First user play-test and tuning pass

## Phase 1b: camera, jumping, Fresh Animations (user request, 2026-10-06)

- [x] RDR2-style riding camera: auto third person on mount (restored on dismount), higher pivot, pulls back with speed, smoothed vertical, FOV widening past a trot, pitch eases to a riding angle when the mouse is idle
- [x] Jumping: instant on press with an 8-tick landing buffer, power from speed (0.45 standing to 1.0 at canter), no rearing, ground speed carried through the air, 6% stamina per jump, weaker when exhausted
- [x] Jump bar replaced by the stamina bar (vanilla sprites; red on the cooldown background when exhausted); shown over the locator bar whenever stamina is not full
- [x] Fresh Animations compatibility: EMF 3.3.11 + ETF 7.2.5 + FA 1.10.5 load alongside the mod with no errors; lean and gait speeds drive FA's animations
- [x] Dev runs (`runClient`, `runClientGameTest`) load EMF, ETF and FA automatically

## Phase 1c: weight and body motion (user request, 2026-10-06)

- [x] Weight-shift turning: head leads first (up to 30 degrees at a walk, 15 at a gallop, ~0.1s), the horse commits its weight and banks, then the body turns (turn rate = max x commitment^2, acceleration-limited; ~0.3s behind the head at a walk, ~0.2s at a gallop); below a canter it side-steps slightly into the turn; tight turns scrub up to 12% speed
- [x] Grip-limited turning circle: max turn rate = 9 m/s^2 / speed (capped at 120 deg/s), so radius grows with speed: ~2 blocks walk, ~4 trot, ~8 canter, ~14 gallop
- [x] Terrain following (visual): ground probed under front and back hooves (1.3 apart); body pitches with slopes and steps (max 25 degrees), rises early as the front hooves climb, and block step-ups render as a smooth climb (max 0.35 blocks/tick vs physics 1.0); in the air the body follows its flight path
- [x] Rider stays upright: hips ride the saddle through bank and pitch, torso follows 30% of the bank, leans forward uphill (half the horse's pitch) and stays vertical downhill
- [x] Rider seat motion per gait from measured riding data (bounce walk 4cm/trot 8cm/canter 9cm/gallop 6cm, trunk rocking, hip sway), forward fold 5 degrees moving and 18 at a gallop, phased off the horse's leg cycle
- [x] First-person: eyes follow the smoothed height and never pitch or roll with the horse; 80% of the seat bounce, a nod from the rocking, and a little hoof-strike shake at a gallop when View Bobbing is on; third-person camera takes 30% of the bounce
- [x] Leg animation speed tracks the gait (walk 0.35, trot 0.6, canter 0.85, gallop 1.0) so Fresh Animations shows its walk, trot and gallop cycles at the right gaits (vanilla showed a trot at a walk and a gallop at a trot)

## Phase 1d: rider life, free aim, comfort, shoulder camera (user request, 2026-10-06)

- [x] Steering (revised by user 2026-10-06): the horse always heads where the rider looks; A/D set it 45 degrees left/right of the view without moving the view (ride at an angle while aiming); the view is never pulled; standing still with no keys leaves free look
- [x] Rider rides the horse's real animated body: with Entity Model Features (Fresh Animations) the saddle's motion is read from the animated model every frame; without it, the same generated motion is applied to horse and rider so they stay in step
- [x] Rider pose: feet in the stirrups, hands on the reins bobbing with the saddle (a hand leaves the reins to swing, use an item, or draw a two-handed pose), torso twists up to 40 degrees toward where the rider looks, hips stay square to the horse
- [x] First-person comfort: view bob reduced and low-passed (no sharp edges, no shake), nod reduced; hands and held items bob with the saddle, more at speed; third-person bounce reduced; FOV boost 5%
- [x] Stamina: ~28s of galloping (was 14), jump cost 4%
- [x] Over-the-shoulder camera add-on (`shoulder-cam/`, mod id `horsingaround_shoulder`, separate jar): right-shoulder framing on foot, tighter when aiming a bow/crossbow/trident/spyglass, riding framing that pulls back with speed; smooth framing transitions; wall avoidance (snaps in, eases out); camera always looks exactly where the player looks and orbits the head (level shoulder offset, world-up height) (fixed choppiness and odd orbit, 2026-10-06); crosshair fixed at screen centre with camera-based aiming like most shoulder-cam games: targeting picks what is under the crosshair and the rotation reported to the server (movement and use-item packets) points there, so arrows and thrown items fly to the crosshair, while the player's view is untouched; swap shoulder (O) and toggle (unbound) keys; works standalone, and with Horsing Around takes over the riding camera through `RideCameraApi`

## Phase 1e: forests (user request, 2026-10-06)

- [x] Leaves don't block a player-ridden horse or its rider (collision removed only for them; everything else collides as normal); pushing through leaves holds the horse to 75% of its gait speed with a soft rustle (the rabbit hop sound) and leaf bits
- [x] Riding cameras (core and shoulder add-on) pass through leaves instead of snapping in

## Phase 1f: downhill, jumps, tired horse (user request, 2026-10-06)

- [x] Speed stays continuous through drops and landings: the ground push is folded into the velocity on leaving the ground and taken back out on landing (downhill steps went 0.42 -> 0.27 -> 0.47 blocks/tick before, now a steady 0.42; jump landings surged 41%, now ~4%)
- [x] Jumps: lower, less floaty arc (~2.1 blocks running, ~0.6 standing), a small weight dip on touchdown, riding camera follows jumps more tightly
- [x] Tired horse: below 20% stamina (or exhausted) while running it breathes audibly every other stride on the down of the stride, softly (volume 0.12-0.3); running out of stamina triggers a head toss (Fresh Animations' shake motion, applied through the EMF hook on the FA model and on the vanilla model otherwise) with one breath

## Shoulder camera settings (user request, 2026-10-06)

- [x] Player settings saved to `config/horsingaround_shoulder.json`, applied live: enabled, shoulder side, on-foot distance/offset/height/vertical follow, aiming distance/offset, riding distance/offset/height/bounce, transition speed; reset to defaults
- [x] Vanilla-style settings screen, opened from Mod Menu's "Configure" button (optional integration, Mod Menu 21.0.0 for 26.3) or an unbound "Open camera settings" key
- [x] Mod icons for both mods (pixel art, 64x64; this mod's replaced 2026-10-09 by the user's 128x128 horse-head render); dev run's Mod Menu set to the classic full-width "Mods" button

## Settings, trampling, seat (user request, 2026-10-06)

- [x] Horse settings saved to `config/horsingaround.json`, applied live through `HorseConfig.apply()` into `RideTuning`: speed, acceleration, turning grip, turn response, A/D angle, gallop stamina (seconds), recovery, jump height and cost, third person on mount, camera distance, speed FOV, view bob, hand bob, horse lean, rider lean, ride through leaves, leaves slowdown, trampling and its damage, horse sound volume; vanilla-style screen in Mod Menu (and an unbound "Open horse settings" key)
- [x] Trampling: above a walk (half of vanilla top speed) a ridden horse damages small creatures it runs through (no wider than 0.8 or taller than 1.0 blocks: chickens, rabbits, cats, foxes, frogs, baby animals, silverfish...), scaling to 4 hearts at a full gallop with a knock aside; never players or the rider's own pets; decided on the server from per-tick ground covered
- [x] Seated rider: thighs forward ~43 degrees and wrapped round the barrel with toes out (reads as sitting instead of standing), rider sunk 0.12 blocks into the saddle, a slight relaxed forward lean at rest

## Rider polish (user request, 2026-10-06)

- [x] Feet stay in the stirrups: legs are counter-rotated against the torso's lean, rocking, pump and counter-bank so they hold still relative to the horse; Fresh Animations' stirrups are swung forward 0.62 rad to meet the seated leg and held against the body's pitch (found by name through a ModelPart children accessor, cached per model)
- [x] Rider "hump" (revised): at a canter/gallop only the pelvis slides forward and back with the stride, by swinging the torso from the shoulders and carrying the hip joints (legs) with it; shoulders and head stay put
- [x] Seat pinned to the saddle: the rider's seat point (vanilla attachment point) is carried through the horse's bank, pitch, step smoothing and gait motion, and every rider lean pivots there, so the pelvis no longer slides on the saddle when the horse banks or climbs (before, the feet-level origin was carried, drifting the seat up to ~0.1 blocks)
- [x] Seat follows the saddle's side-to-side sway too (Fresh Animations rolls and shifts the body at a walk/trot, ~0.04 blocks): the EMF hook now measures the saddle's sideways shift, the rider is carried with it, and the legs roll with the saddle so the feet stay in the stirrups
- [x] Rider inertia: the body sways back as the horse surges and forward as it brakes (250 degrees per block/tick^2, spring-damped, max 12 degrees)
- [x] Hands now drop as the horse lands and lift as it rises (inertia), instead of the reverse
- [x] Slopes: body pitch capped at 15 degrees (was 25), neck reaches forward/down by 60% of the pitch (vanilla model in setupAnim, Fresh Animations in the EMF hook after the pack), rider uphill lean halved; the rider no longer clips into the horse's head on climbs

## Water (user request, 2026-10-06)

- [x] Wading: a ridden horse that can touch the bottom keeps its footing (vanilla water physics bypassed) and slows with depth, up to 70% at chest depth (test: 48% of dry pace in 1-block-deep water)
- [x] Swimming: out of its depth the horse floats with its back at the waterline and head above water (buoyant settle, with hysteresis so it doesn't flicker at the threshold), swims at ~0.11 blocks/tick, climbs out up a bank gradually (rise builds to 0.14 blocks/tick and carries on until the hooves are on the bank; rendered height and nose pitch eased) when pushing against it or pressing jump, and tires (40s of stamina); the rider stays mounted

## Phase 1g: the horse has a say (user request, 2026-10-06)

- [x] Look-ahead (`Awareness`): three ground-following lines ~1s ahead (2-12 blocks) find walls, drops beyond the horse's safe fall distance (all three lines) and hazards (lava, fire, magma, cactus, berry bushes, powder snow, cobwebs, lit campfires, `horsingaround:horse_avoids` tag); jumpable gaps (safe ground within 4 blocks) are passed over
- [x] Detours round trees and rocks from a trot (up to 40 degrees, only if the detour actually passes the obstacle, eased in and out, kept through to the end): a trunk dead ahead at a gallop is passed at 90% pace without touching it; never at a walk, never along a wall it can't get round
- [x] Slows to a walk before walls and walks right up to them; stops short of cliffs and lava (within ~0.3 blocks) with a snort and head toss from a trot or faster; won't jump off a cliff; plants its feet at the lip of a gap if the rider doesn't jump; last-resort edge guard on the predicted next position
- [x] Not in the way: full pace galloping beside a wall and along a cliff edge with no detour, safe drops (4 blocks) and gaps (jumped) untouched
- [x] Ledge jumps: riding at a ledge up to 2 blocks at a walk or trot, the horse halts, gathers itself for 5 ticks (haunches down, nose up) and jumps up in an arc (~8 ticks in the air, peaks ~0.37 above the lip, ~0.6 blocks forward on the way up); slows from a gallop first; never fences, walls or gates; 3-block walls are left alone
- [x] Settings: "Horse picks its way" and "Jump up 2-block ledges" toggles (with tooltips)

## Phase 1h: picking its way, revised from play-testing (user request, 2026-10-06/07)

- [x] Goes round obstacles when there is a way: looks along the obstacle (12 blocks either side) for the nearest clear way past, heads there (up to 85 degrees), slows to make the turn and to come back round, keeps to it until clear (no corner cutting), then heads where the rider looks; slows to a walk at a wall with no way round
- [x] Slopes: simulates the flight over every step down ahead and keeps a pace that lands without any hurt, braking before the first step (test: 45-degree and 63-degree mountainsides, longest fall 6.6 blocks, horse unhurt)
- [x] Falls by health: drops costing up to 2 hearts (horse) and 3 (rider, who takes the fall too) are taken when both are healthy, none below 40% health (test: a healthy horse takes a 9-block drop for half a heart, a hurt one refuses it)
- [x] Hazards are obstacles (gone round or stopped for, jumpable by the rider with ground beyond); the guard covers side-steps and drops off steps, and slides along a hazard instead of freezing; a hazard the body is already in doesn't block it walking out
- [x] Ledge jumps in the stride: spotted 2.5 blocks out, brief crouch while still walking, takeoff about a stride from the face, arc that never meets the face, walks on from the top; never onto leaves or a lone log (needs ground for its ~2-block body length)
- [x] Jumps tilt like a real horse's (nose up pivoting on the hind hooves, nose down landing on the front ones; up to 25 / 15 degrees); minimum jump ~1.25 blocks (standing jumps clear a block)
- [x] Ridden collision box narrowed to 0.9 blocks (vanilla 1.4), threads 1-block gaps between trunks; look-ahead uses the real box, widened on diagonals
- [x] Water: deep water takes the way off quickly; swimming into a bank isn't a crash
- [x] Natural-terrain test (`TerrainRideTest`, `-Ptests=terrain`, `-Pscenario=<name>`): procedurally built forests (two mixed layouts, dense dark oak), mountains (two down, one up), rolling hills, taiga with berry bushes / powder snow / campfires / cactus / lava, a river crossing and badlands terraces, ridden by a player-like rider (heads for a target, looks for the most open way when stuck); checks no hazard damage, falls within allowance and none while hurt, never in lava, arrival, pace, crashes, dead ends, and ride tick cost (0.06 ms average, 0.25 ms 99th percentile)

## Phase 1i: steps in two beats, and footing (user request, 2026-10-07)

- [x] Steps and slopes in two beats: the front and back of the body each ease toward the ground under their own hooves, so the forehand goes up (or down) a step first and the hindquarters follow; read ahead by the easing's own lag, so each end moves as its hooves reach the step and slopes don't fall behind
- [x] Smooth (play-test revision, 2026-10-07: the first spring version snapped to full tilt in a tick and dipped nose-down after the push): two-stage easing with no overshoot, quicker with speed, and the tilt eased once more so quick bumps at speed rock the body gently; tilt levels off softly toward 10 degrees (about 6 on a block at a walk) with the weight on the hindquarters; the tilt climbing out of water is eased too (test: on a step the tilt changes at most ~1-1.2 degrees a tick and the height ~0.1-0.14 blocks a tick, the forehand leads by ~6 ticks at a walk, 4 at a trot; across all the natural-terrain rides the tilt never changes more than 2.6 degrees a tick, was up to 11)
- [x] Legs on a step: the front legs fold up and forward onto a step (or reach down for one) and the hind legs drive back as the hindquarters rise, on the vanilla model and on Fresh Animations (body, saddle and armour layers)
- [x] Footing: in the air (off a drop, or a jump a little short) the horse gets a hoof on anything within a step of its hooves instead of stopping dead (test: galloping and trotting across a 1-block-deep ditch keep 98-99% of their pace and their gait); a shoulder caught on a corner by up to 0.3 blocks slips past it (test: walking into a trunk that overlaps the body by 0.1)
- [x] Crashes only on a head-on hit (more than 65% of the travel stopped); before, any touch at speed (a scrape along a trunk, the corner of a block) halved the speed and dropped to a trot (natural-terrain test: 3 crashes before, 0 after)
- [x] Ridden step height 1.125, so a full block is a step from a path, farmland, mud or soul sand, or onto snow; the look-ahead uses the same height
- [x] 2-block ledges with leaves on top are jumped through the leaves (test lane added)
- [x] Steering overlay for play-testing: with hitboxes shown (F3+B) the ridden horse draws its box, the rider's line, its heading, its detour, what the look-ahead found (wall, danger, gap, ledge), the ground carrying its front and back, and gait/speed/blocked share
- [x] Ride test: `-Psections=<core,stairs,picking,steps>` runs only some sections; step lanes log a per-tick trace and take side shots from a fixed camera beside the lane with the overlay on

## Phase 1j: hard cuts, straighter dodging, ledges on the line, banks (user request, 2026-10-07)

- [x] Hard cuts from the view (revised the same day from an A/D-press trigger): the further the rider looks off the horse's heading, the harder it cuts round, smoothly from 40 degrees off (none) to 100 (full): sits back on its haunches, takes up to 2.2x grip and twice the top turn rate, commits its weight faster, and slows to the speed at which that grip brings it round in about half a second, easing out as it comes round; scuffs up dirt cutting hard from a trot; "Hard cuts" toggle in the settings; how hard it is cutting shows on the steering overlay (test: 90 degrees at a gallop in 15 ticks at 68% pace vs 38 ticks with cuts off, banking 14 degrees; 150 degrees in 20 ticks at 46%; a 20-degree look keeps pace with no cut; A/D riding at an angle unchanged; standing still is still free look, with W held a 150-degree pivot in 18 ticks)
- [x] Straighter dodging: the way round is planned with the horse's own steering model (least angle that gets it clear in time, given its weight-shift lag), aims past the obstacle on a line grazing its near corner (dogleg beside the near face where that line is blocked in crowded forests), finds the least move-over to 1/8 block (margin 0.2, was 0.4 in whole blocks), and heads back as soon as its momentum carries it clear (test: a trunk at a gallop passed 1.6 blocks off the line at 92% pace, was 3.8 at 67%; a pillar at a trot 1.8; a ledge catching the flank 0.6; natural terrain: mixed forest ridden in 68 blocks for 61, was 151)
- [x] Things in a row: the way round prefers a lane clear through what lies behind the obstacle (5 blocks past it or to the end of the look-ahead, up to 2 blocks further over than the nearest way past), so going round one doesn't lead into the next; nose to a big trunk with no line out at an angle, it steps aside along it (test: a row of three trunks threaded at 91% pace without touching, taking the lane past all three instead of the side that leads into the second; natural terrain: every forest arrives with no dead ends: mixed 266 and 176 ticks, dense dark oak 263, were 472, out of time, and 365)
- [x] Ledges on the rider's line: from a trot up, a 2-block ledge the rider rides straight at (and can land on) is jumped, not gone round; from a canter or gallop the horse slows only to a trot (was a walk); a ledge that only catches the flank or a pillar with nothing to land on is gone round
- [x] Banks: out of deep water the horse climbs only onto a bank no more than a block above the water's block layer (higher banks can't be climbed; jump no longer lifts it in open water), in one smooth heave of about a second (eased rise, pressing forward, nose up mid-heave then level; no snap step-up at the lip) (test: 1-block bank climbed in 17 ticks, rise at most 0.18 blocks and tilt 1.8 degrees a tick; 2-block bank refused); natural-terrain river banks rebuilt like generated rivers' (mostly level with the water or a block above, some two)

## Phase 1k: riding across the view, leaves you can feel (user request, 2026-10-07)

- [x] A or D alone ride the horse on at its gait 90 degrees left or right of the view instead of letting it stop (W+A/D still 45); "A/D alone angle" setting (test: D alone at a gallop holds 90 degrees right at full pace, A alone 90 left, W+A back to 45, D alone from a standstill walks off to the right, the view never moves)
- [x] Leaves you can feel: pushing through leaves at the rider's chest or face, the rider leans back from the push (toward 7 degrees at full pace, on a spring, with a shove at each new clump) and puts the off hand up in front of their face, head tucked and turned away; everyone sees it (test: hand fully up and a 6-degree push in a stand of leaves, both gone once clear; front and side shots of the rider coming out of the leaves)

## Phase 1l: jumps that look like jumps (user request, 2026-10-07)

- [x] Legs in the air: the stride eases to a stop in a jump or a bigger fall and the legs take the jump's shape (front legs folded up climbing and reaching forward and down for the ground coming down, hind legs pushing off then gathered under), then the stride picks up on landing; vanilla model and Fresh Animations; play-test fixes: legs drawn up into the body as far as their swing would show a gap at the hip or shoulder (they looked detached), kept under the chest and hindquarters with each pair staggered (they sat too far back and together), and moved in one smooth sweep over the whole flight, eased in from the stride (they jerked between poses; test: at most 0.07 rad a tick in front, 0.09 behind)
- [x] 2-block ledges bounded up in an arc: at least ~0.26 blocks/tick forward in the air, taking off ~1.4 blocks out (was 0.5) so the chest meets the edge near the top of the arc, carrying 1.4 blocks forward on the way up (was 0.6) and landing a stride onto the top
- [x] Rider jumping position: folds forward over the neck in the air (with the horse's rise), sits up for the landing
- [x] Ledge refusals fixed (play-test): snow (or a carpet) on top no longer reads as "no room to land", and a ledge ridden at an angle from a trot up is jumped instead of gone round; why a ledge is turned down is recorded (`Awareness.ledgeRejection`) and a test rides 16 ledges like generated worlds' (angles, snow, grass, bumps, leaves and branches overhead, steps, slabs, canter, standstill)
- [x] No abrupt jump where a step comes right after the lip (play-test): ground a step above the ledge's top counts as somewhere to land, and the face is measured to a few hundredths, so the horse takes off a stride out (1.25 blocks, was 0.37 and nearly straight up); test ledges with a step where it lands, at a walk and a trot, and a staircase of 2-block steps
- [x] Tail on a spring with the horse's motion: trails down as it launches, floats up and streams out behind in the air, flicks on landing (vanilla model and Fresh Animations' tail); test: -0.21 rad at launch, +0.9 coming down, settled after landing
- [x] Calmer canter seat (play-test: the pelvis swing read as humping): the pelvis rocks ~3cm with the stride (was ~9), half that in a gallop's half seat
- [x] No more pops up steps (play-test): up a run of steps the drawn body caught up with a 1.5-block snap; now it catches up smoothly past 0.8 blocks behind and only snaps for a real teleport; natural-terrain rides check the drawn horse and the camera never pick up their rise sharply (worst 0.25 blocks/tick a tick, was 1.25)

## Repos (2026-10-06)

- [x] Split into two repos: Horsing Around (this one) and Horsing Around: Over the Shoulder (`../Over the Shoulder`); the add-on compiles against a compile-only copy of `RideCameraApi` and has its own camera test (12 checks, including riding when Horsing Around is built next door); this repo's `runClient` loads the add-on when built, its ride test runs without it

## Release readiness (user request, 2026-10-06)

- [x] Builds on any machine (both repos): no machine-specific paths; `gradle/gradle-daemon-jvm.properties` asks for JDK 25 and the foojay resolver downloads one if none is installed (any Java 17+ starts the wrapper); `.gitattributes` keeps `gradlew` LF and `gradlew.bat` CRLF; sibling-repo dev paths accept both the local folder names and the GitHub repo names; GitHub Actions builds every push on Linux and attaches the jars. Verified by building both repos from a clean copy with an empty Gradle home and only JDK 11/17 installed
- [x] Modded servers (user direction 2026-10-07: the mod is for servers that have it, like the family server): the server tells each modded client it runs the mod and its rules (`horsingaround:rules` payload with a protocol number and the leaves setting, resent when a host changes settings); until then, on servers without the mod, or with a mismatched protocol, the client rides vanilla and says so once on mounting. Verified by `ServerRideTest`: a real dedicated server joined like a family member, riding gallop, running jump, a leafy hedge, steps, a 2-block ledge, a ditch, deep water and trampling with zero server corrections and client and server agreeing on the horse's position; then joined again without the mod (vanilla riding, still no corrections). `DedicatedServerTest` (`./gradlew runGameTest`) loads the mod on a bare dedicated server with no client code and rides and tramples with the real ride controller
- [x] Every ridable mob (user request 2026-10-07): the horse family is chosen by the entity type tag `horsingaround:managed` (horse, donkey, mule, skeleton and zombie horse; other mods' horses can opt in); camels, camel husks, llamas and every other mount keep vanilla controls with no riding camera, box, pose or HUD changes (render state of unmanaged horses fully cleared, no per-tick work for them). Skeleton horses walk the bottom of lakes again as in vanilla (only `can_float_while_ridden` horses swim). Mob riders (zombie and skeleton horsemen, a husk on a camel husk) keep their horses' full box and stay mounted. Verified by `MountsTest` (`-Ptests=mounts`, 125 checks): each horse type gallops at its own top speed, jumps and restores the camera; camel (and its dash), camel husk, pig, strider, happy ghast, nautilus, zombie nautilus, llama, boat and minecart ride as in vanilla; the switch-off when the server's rules are missing
- [x] Mixin hygiene: physics hooks moved from overrides on `AbstractHorse` to chainable result changes on the classes that declare them (`LivingEntity` walk animation, fluid travel, air control, step height; `Entity.move` footing; `BlockBehaviour` leaves collision); cancelling HEAD injections replaced by return-value changes and `@WrapMethod` (ridden input and rotation, jumps, riding camera, stamina bar); cosmetic client hooks are optional (`defaultRequire: 0`), core ones still required; an Entity Model Features API change logs a warning instead of crashing
- [x] Other mods: the riding camera steps aside while Shoulder Surfing Reloaded drives the camera (and switches perspective through its API); on the horses this mod rides, its steering wins over other mods that turn the horse (Horseman's free camera) and the rider pose is applied after mods that pose riders on any horse (Not Enough Animations), while their other features keep working
- [x] Automated compatibility runs (`./gradlew runCompat --continue`, or `runCompatPerformance`, `runCompatAnimation`, `runCompatCamera`, `runCompatHorses`): the built jars in a production game (Loom `ClientProductionRunTask`) with packs of popular 26.3 mods pinned by Modrinth version id, running the mounts, dedicated-server and core ride tests, failing on failed checks, mixin conflicts or warnings naming this mod (`build/run/compat/<pack>/`)
- [x] Store metadata: MIT license (both repos, shipped in the jars), author GabePurc, source and issue links (GitHub), README written for the Modrinth page, gallery shots (`-Ptests=gallery`)

## Settings and store polish (user request, 2026-10-08)

- [x] Settings are comfort and preference only: third person when mounting, camera distance, speed zoom-out, first-person saddle and hand motion, and the volume of the mod's extra horse sounds. Everything that changed how horses ride (speed, turning, stamina, jumps, hard cuts, picking its way, ledges, leaves, trampling, lean) is fixed in `RideTuning` and old config entries are ignored. Verified by the ride test (a setting applies live; gallop speed unchanged)
- [x] Polished settings menus in both mods: the mod icon in the title, a short description, tan section headings, a tooltip on every option, plain values ("Off", percentages, "2.8 blocks")
- [x] READMEs rewritten in the user's own voice; Buy Me a Coffee link in both READMEs and as a Mod Menu link in both mods

## Store page (user request, 2026-10-08)

- [x] README (also the Modrinth description) dressed up like popular mod pages: a banner built from the user's icon art, link buttons in the same sunset colours (Buy me a coffee, Requires Fabric API, source, report a bug), a looping clip (30 frames a second, no shaders, user direction) or a still with shaders for each feature (walk to gallop, wide turns, the stamina bar, a jump at a gallop, refusing a canyon edge, swimming a river), the user's own Modrinth gallery shots, the settings screen and the supporter hat; the long list of tested mods folded away. Media in `docs/media/` (13 MB), loaded through raw.githubusercontent.com, which Modrinth's image filter allows
- [x] Store filming (`-Ptests=store`, `StoreMediaTest`): scouts a generated world (seed "mustang") for each shot's ground from the terrain noise, checks the best few routes on the generated ground (no water, gentle steps, a clear view from the camera's side), and films with a test-only tracking camera (`FilmCamera`, a gametest mixin). Clip frames are drawn by the test at exact moments (30 a second, two in every three ticks) and saved once the picture is back from the graphics card; the gametest screenshot call runs a varying number of ticks while it waits, which made clips speed up and slow down. `-PshaderPack=<zip>` adds Sodium and Iris with that pack, for the stills (Complementary Reimagined, matching the user's screenshots); `scripts/store-media.sh` encodes the frames into looping WebP

## Supporter hat (user request, 2026-10-08)

- [x] Cowboy hat for supporters (Buy Me a Coffee): a cosmetic, not an item; switched on and coloured (12-colour palette plus hue, richness and brightness) in the horse settings, where non-supporters get a "Get a cowboy hat: buy me a coffee" button instead. Supporters are listed by UUID in `supporters.json`, downloaded at startup and cached for offline play; the hat colour goes through the server to everyone on it, and clients only draw hats for listed players. While it's on, the helmet and head items aren't drawn (still worn). Model: creased crown, band in a darker shade, brim curled at the sides; grey felt texture tinted by the colour. Verified by `HatTest` (`-Ptests=hat`: drawn in the chosen colour, helmet hidden but worn, relayed by the server, another supporter's hat drawn, gone when switched off, never for non-supporters) and `ServerRideTest` (relayed by a dedicated server)

## Play-test fixes: HUD mods, jumping at walls, a heave up ledges, camera height (user request, 2026-10-08)

- [x] Stamina bar with HUD mods: the bar takes the jump bar's slot whenever stamina isn't full, also when a mod hands the slot to the experience bar (Better Mount HUD shows experience unless jump is held, and our horses never charge a jump). Verified in survival by the ride test, and with Better Mount HUD 1.3.1 in the camera compatibility pack
- [x] No more snort when jumping at a wall: the safety look before a jump read a wall right in front as a bottomless drop and refused (reproduced by the new test on the old code: snort, no jump); pressing jump at a 2-block ledge it can climb, even standing at its face or a block out, asks for the ledge jump (it gathers where it stands and goes)
- [x] 2-block ledges heaved up instead of popped: a longer, deeper gather (at least 5 ticks), the hind legs' push builds the climb over 3 ticks (0.13 blocks in the takeoff tick, was 0.6), it rises under a little over half its weight to 0.3 above the lip (fastest 0.33-0.35 blocks/tick, ~10 ticks up, was 7) and comes down onto the top under full weight (16 ticks in the air, was ~10); less forward speed in the air (0.2 blocks/tick), taking off ~1.7 blocks out; no mid-air step-up pop while it climbs a face it is pressed against; taking off, the ground tilt (slope plus crouch) eases out by at most 2.5 degrees a tick while the jump tilt takes over
- [x] Riding camera: pivot 0.2 above the rider's eyes (was 0.5) with a "Camera height" setting (-0.5 to 1.5 blocks); the height follow eases in two stages so takeoffs and steps start and stop softly
- [x] Over the Shoulder has the third-person view to itself while it is on: this mod stops placing the camera and easing the pitch back (that moved the aim), its camera distance and height settings don't reach the add-on (greyed out in the settings, with a note; `RideCameraApi.distance` is the designed distance), and the add-on's riding height came down to 0.35 (was 0.7; settings files still on the old default move to the new one)

## Hooves on the ground, and no clipping (user request, 2026-10-08)

- [x] Slopes and stairs: the body tilts along the ground under its front and hind hooves (up to 40 degrees, at most 4 a tick), pivoting at the leg joints, and sits as high as every leg can reach (never sinking the whole body toward a drop: the lower legs reach down instead); downhill it now sees the slope (ground up to a block per block below was read as level past 1.25) and leans forward; stair blocks and slabs are read at the hoof's exact spot; water is a floor a wade below its surface
- [x] Legs (vanilla and Fresh Animations, on top of its stride): stand upright against the tilt, and each pair finds its footing, trying swings forward and back for where its hoof meets the ground actually under it (no reading on level ground)
- [x] Climbing, the neck reaches forward and down with the tilt (85%, so the horse doesn't look like it rears); on Fresh Animations this now goes on its own neck ("neck2"; vanilla's neck part is empty there), which also makes the tired head toss show on it
- [x] No clipping between horse and rider: when the horse's head would come within reach of the rider's head or chest, the horse stretches its neck forward (up to 35 degrees) and, if that isn't enough, the rider folds less over the neck (up to 30 degrees); no sideways lean (user direction)

## Knees and hurdles (user request, 2026-10-08)

- [x] Legs turned about their tops whatever the model's pivot (Fresh Animations pivots at the hoof: swinging about it was what made legs detach and go wonky), in standing upright on slopes, the knees and the jump's shape
- [x] (Replaced 2026-10-09 by one-piece legs, see below.) Jointed legs: each leg box cut into upper leg, cannon and hoof (texture and all, capped, the upper leg reaching further up into the body); each hoof stands flat on the ground under it where it is drawn (front knees jut forward, hind hocks back), reads a moment ahead along its own motion, moves back off a step's face it can't climb, and the drawn body fits so the standing leg on the lowest ground is straight
- [x] Horse armour and bridle follow the head on Fresh Animations (the neck reach and head toss go on every layer's neck)
- [x] (Body model replaced 2026-10-09, see below.) Play-test fixes: smoother climbing (the drawn body tracks its height and carries the climb), walking down a slope of full blocks no longer reads as falls (the tilt holds, no hindquarters in the steps), shorter strides on steep ground, step faces judged where the hoof is
- [x] Jump spam at a 2-block ledge: vanilla's release jump no longer fires on landing, a press waits for the heave, and the horse settles on top before jumping again
- [x] Hurdles: jump fences, walls and gates (1.125-1.6 tall, within 2.5 blocks, safe landing) at any pace or standing; refuses lava or drops beyond; slows to a trot, or stops with a snort without a jump; pens still hold horses

## Exact hooves, a body on its legs (user request, 2026-10-08/09)

- [x] Knee and hoof joints taken out (user direction: too far from vanilla): legs are vanilla's one-piece legs again,
  never changed (only measured, as built, so every model layer agrees); a leg shortens by drawing up into the body
- [x] Each hoof on its own ground exactly, every frame, no easing: the ground under the whole sole worked out where the
  block grid crosses it (higher ground coming in over its first ~0.06 blocks, so a hoof never flickers on a step's edge),
  backing off a step's face by exactly as far as it is in it; measured at the middle of the sole (the lowest corner kinked
  every step); legs stand upright against the ground's slope only (not the gait's or the breath's sway, which hitched
  every step); a hoof lifts no faster than 10 blocks/s and a leg straightens no faster than 4, never leaving a hoof in the
  ground
- [x] Lifting for what is ahead: a hoof in its swing (from the gait's own lift), one the body has lifted off the ground,
  and moving on any hoof for the last bit (the animation's planted hooves slide), never from a hoof's speed frame by frame
- [x] The body as a mass on its legs: each end on a critically damped spring to the ground under its hooves, read ahead
  only going up; it falls no faster than gravity when its hooves step off (going down a block was far too fast); the
  forehand rises to keep the chest clear of a step before it gets there (stepping up no longer puts the chest in the
  block); the legs give 0.3 and push the body up at most 0.15 a tick past that; landing keeps the fall's speed and the
  legs take it up (no more sinking into the ground on landing); the tilt is the line between the ends, no extra easing;
  running off a drop at a canter or faster carries the body over level, as a short leap
- [x] The body rests on its standing hooves (comes down onto them under gravity, up smoothly as a leg draws past 0.35,
  at once only for a leg that couldn't reach at all), never so low the chest meets a step ahead
- [x] Snaps found and fixed: model layers (body, saddle, armour) posed their legs differently in a frame; a layer without
  leg boxes let the body settle; the ridden horse's legs switching to the model's own on flat ground; a hard stop and a
  settle spring fighting frame by frame (on a block's edge the body crept down, hitchy)
- [x] Tests: `-Psections=slowstep` watches standing still, at a block's edge, walking then stopping, and slow single
  steps up and down, tick by tick and frame by frame at 120 fps (no jumps standing, at an edge or stopping; on a single
  step no leg jumps more than ~0.08 a frame, was 0.44); step tests check the chest clears the step and no hoof or leg is
  in it; the running jump checks no hoof sinks on landing; natural terrain checks the tilt steadily (99th percentile, at
  most 10 degrees a tick) and at worst, landings included (18)

## Crash fix: shulker boxes with Entity Model Features (user report, 2026-10-09)

- [x] The game crashed (a player's, on Fabulously Optimized with Fresh Animations) when Entity Model Features animated a
  shulker box drawn as an item, in the hotbar or in hand: EMF calls every animation hook even with no entity behind the
  model, handing it no state, and the saddle tracker assumed there was one. It now leaves anything without a state
  alone. Verified by `RideFeelTest` (`-Psections=icons`, part of `core`): shulker boxes in the hotbar and in hand while
  riding, with a test-only shulker box animation in the debug pack (Fresh Animations 1.10.5 has none); the old code
  crashed there with the player's error

## Out of the water, and stopping after A/D (GitHub issues #15 and #16, 2026-10-09)

- [x] Leaving water no longer launches the horse: stepping out of a wade, the drawn body took the box's one-block step as
  its upward speed and flew ~2.6 blocks up before snapping back; now it carries its own speed. Wading uses the ground's
  steps (stepping out onto a bank is an ordinary step, forehand first), a bank too high to step up is climbed in the same
  heave as out of deep water (no ledge jump in water), and the drawn body never rises above the bank it ends up on.
  Verified by `-Psections=exits` (wading and swimming out onto banks level with the water, a slab and a block above it):
  highest above the bank 0.00-0.03 (was up to 2.6), at most 0.03 down in a tick settling. Front hooves meeting the bank's
  top mid-heave lift at a hoof's speed (the biggest sole jump in a frame 0.52 -> 0.34; the rest is still open, logged)
- [x] Stopping after riding with A or D alone no longer sinks the body: letting go made a 90-degree hard cut back to the
  view, so the horse sat on its haunches as it stopped; hard cuts are now only while riding on, and letting go stands it
  up out of one. Verified by `-Psections=strafe` (D, A, D at a trot, against W): lowest body -0.001 (was -0.12)

## The rider's hands: a weapon held ready, and a bow drawn (GitHub issues #17 and #18, 2026-10-09)

- [x] A tool or weapon in the main hand (anything with a tool or weapon component, modded ones too, plus bows and
  crossbows) comes off the reins and is held low at the rider's side, blade forward and turned away from the neck; the
  other hand keeps the reins; food, blocks and other items stay in the rein hand. Any swing (vanilla's whack) is played
  from wherever the arm rests and comes back to it, and the torso's turn in it adds to the rider's look twist; a spear's
  thrust stays vanilla's. Left-handed riders mirrored
- [x] Drawing a bow, the rider turns side-on to the aim, the bow arm out along it and the string hand coming back from the
  bow to the cheek as the draw charges (the bow's own power curve); the arms come up into the draw from rest, and once
  loosed the string hand flies back past the cheek and the arms come down over half a second. A loaded crossbow is held
  square to the aim (the look twist used to be added on top of vanilla's aim, pointing the arms past it)
- [x] Riders only; the bow on foot is left to vanilla. Verified by `-Psections=hands` (part of `core`): 39 checks on the
  player model as drawn (reins, ready pose for sword, axe and pickaxe, bread on the reins, swings start and end where held,
  left-handed, the bow's arm and torso on the aim, string hand within 0.4 pixels of the cheek at full draw and 5-6 nearer
  than at the nock, back to rest after the loose with no jump, the crossbow on the aim) and close shots from three sides
  through the test's film camera (a fixed camera entity can't show your own player)

## Reliable 2-block climbs (GitHub issue #24, 2026-10-09)

- [x] Any 2-block rise is climbed from any angle up to 60 degrees and whatever is round it: the ledge check looks for a
  landing straight on, then up to a block to either side and nearer or further in, then straight up the face, and heads
  the heave toward the spot it found; anything taller than a ledge where the body meets it is a wall (a 3-block wall
  beside a rise was heaved up as if it were 2); a face ridden along or glanced (more than 70 degrees off square) isn't a
  climb; the quick look reads every block between a step and a ledge's height (a slab at the foot hid the face)
- [x] No more double-height jumps: in the air a hoof put down steps up only 0.6 (was a full 1.125, so a plain jump plus a
  step reached a 2-block top, and in a corner stepped on up the 3-block wall beside it)
- [x] Verified by `-Psections=climbs` (now part of the default run, in the `steps` shard): 38 lanes, 144 checks: angles
  0-60 at a walk and trot, cantering straight and at 30, pressed against the face or a block out (riding at it and
  pressing jump), walls 2 and 3 tall beside it and both sides, inside and outside corners, a block on top, a slab at the
  foot, stairs along the lip, snow, the reported corner (riding and stuck-then-pressing jump), 3-block faces and riding
  along a face (both stay below), and three noisy mountain staircases (all reach the top, longest stall 8 ticks); every
  climb peaks 0.30 over the lip (snow 0.43)
- [x] Diagonal rises stacked with one-block treads (a saw-tooth edge across the grid, as on mountainsides) are climbed:
  the landing is found by carrying the lifted box toward each spot through the world, stopping at what it meets (so it
  settles into the pocket of a one-block tread), and a climbable ledge in front counts as ground for the body's length.
  Verified: 66 climb lanes, including diagonal faces met square and at angles and diagonal terraces 1, 2, 3 and 6 deep,
  five rises, all up with one heave a rise, 0.30 over the lip

## Ways through: planning a line (play-test feedback, 2026-10-09)

- [x] The horse plans its way through what is on the rider's line instead of dodging one thing at a time: candidate
  ways played out with its own steering and momentum, the cheapest kept (least turning, nearest the rider's line, ending
  back on it, clear as far as it looks or with room to stop), replanned every other tick, committed to so it doesn't
  dither; slower paces only when needed; back onto the rider's line once past; no hard cut from its own avoidance;
  ~110-170 us a plan, no allocation
- [x] Verified by `-Psections=ways`: wall with a way round 89% of a gallop at its slowest (was 50%), back on the line
  within 0.3 (was 1.8-4.6 off), three seeded forests and staggered trunks at 92-99% pace with no touches (one forest
  wedged the old detour at 11%); tree, pillar, trunks in a row, long wall, beside a wall and ledge cases still pass
- [x] Tight streets (city play-test): no sway when the rider changes direction (the line is kept only once the view
  holds and the horse has come round), ways can set off from beside a wall, fences and walls read by their real shape,
  a fence is the rider's to jump only when ridden at fairly square, no step-aside lurch; verified by a tight-streets
  test (3-wide streets, 2-wide alleys, fences, a step up and down): no touching, no swings, ~70% pace (old detour:
  287 ticks scraping alleys at 36%)

## Running up a step without the pop (play-test feedback, 2026-10-09)

- [x] At a run a step up eases in over a few ticks and is read further ahead, so the body comes up onto it through the
  stride instead of kicking up with a flick of the nose. Verified by new cantering and galloping lanes in
  `-Psections=slowstep`: gallop sharpest change in the climb 0.152 -> 0.019 blocks/tick a tick, nose flick 8.9 -> 0.7
  degrees/tick a tick, most tilt 21 -> 10 degrees; canter 0.066 -> 0.040, 4.8 -> 1.9, 15 -> 14

## Leaves underfoot (play-test report, 2026-10-10)

- [x] Riding through a forest the horse no longer looks like it rides up on top of bushes: the ground the body, steps
  and every hoof are drawn on skipped nothing, so a 1-2 block bush the horse was pushing through read as a step and the
  drawn horse popped up about 0.7 blocks onto it and tilted. Those ground probes (and the look for a bank to climb out
  of water onto) now go through leaves to the ground under them, as the horse itself does. Verified by a new ride lane
  (`picking`: galloping through 1- and 2-block bushes: never above the ground, drawn within 0.03 of it, no tilt) and a
  new terrain check (never standing on leaves, client or server: 0 ticks in every scenario); terrain tilt smoothness
  now passes in mixed forest 1 (11.3 -> 6.9 degrees), the dense dark-oak forest (10.6 -> 8.9) and the taiga (14.2 ->
  7.3)

## Tooling

- [x] Automated client ride test (`./gradlew runClientGameTest`, `-Ptests=ride`, `-Psections=core,cuts,stairs,picking,steps`): checks covering 19 messy 2-block ledges, legs in the air (the stride stops, front legs fold then reach for the ground, at most ~0.1 rad a tick), the 2-block ledge bound, hard cuts (20, 60, 90 and 150 degrees off at a gallop, against cuts switched off; standing pivot), A or D alone riding across the view, the rider pushed back and shielding their face in leaves, climbing out onto a 1-block bank and not a 2-block one, narrow 2-block ledges jumped at a trot and a gallop, a ledge catching the flank and a 2-block pillar gone round, a row of trunks threaded (the 12 shoulder-camera checks moved to the add-on repo), steps in two beats (up and down, walk and trot), footing (across a ditch at a gallop and a trot, past a trunk that catches the shoulder), a leafy 2-block ledge, the horse picking its way (tree detour at pace, long wall slow-down, beside a wall and along a cliff edge at full pace, cliff and lava refusal, safe drop, gap jumped and refused, ledge jump at a walk and from a gallop, fence left alone), gradual and smooth climbing out of water, wading pace, swimming (speed, head above water, rider kept, climbing out), saddle side-sway follow, rider inertia (surge and braking), Fresh Animations stirrups held, trampling (walk vs gallop), horse settings in Mod Menu and live apply, downhill speed continuity, landing surge, jump arc, huff rhythm, head toss on the FA model, mouse steering, A/D 45-degree offset without view pull, riding through leaves, free aim and look limit, aiming while turning, Fresh Animations body sync, staircase climbing (smoothness, pitch), weight-shift timing, gait animation speeds, first-person bob, mounting camera, free look, walk/gallop speed, spur timing, A/D turn rate and lean, running and standing jumps, stamina and exhaustion, coasting, braking, reversing, mouse steering, dismount; writes `build/run/clientGameTest/horsingaround-ride-report.txt` and screenshots (back, front and side views)
- [x] Legs as drawn (`-Psections=drawn`): a test-only probe measures the ridden horse's legs exactly as the model renderer drew them (after Fresh Animations and this mod posed them): each hoof against the ground under it, each leg's top against the body, which way each knee juts; on flat ground (the pack's own baseline), up and down slopes and stairs, and standing on stairs and a step, with close shots against a white wall
- [x] Light, parallel test games: lowest settings, 20 fps, 2 GB heap, Sodium/Lithium/FerriteCore/ImmediatelyFast in test games only; `runClientGameTestParallel --continue` runs five shards two at a time (`-PtestGames=N`) and merges the reports
- [x] Test colours: in the client tests horses wear colour-coded textures (`src/gametest/debugpack`: upper leg blue, knee white, cannon orange, hoof black, sole magenta) so screenshots show what each piece of each leg is doing; `-PplainTextures` turns them off, the store gallery never uses them
- [x] Dedicated-server ride test (`-Ptests=server`; the user accepted the Minecraft EULA for the local test server on 2026-10-07) and a bare dedicated-server game test (`./gradlew runGameTest`); every client test logs server corrections ("moved wrongly") and warnings, and one failing test no longer stops the others (`TestSummary` fails the run at the end)
