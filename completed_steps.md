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
- [x] Mod icons for both mods (pixel art, 64x64); dev run's Mod Menu set to the classic full-width "Mods" button

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

- [x] Legs in the air: the stride eases to a stop in a jump or a bigger fall and the legs take the jump's shape (front legs folded up climbing and reaching forward and down for the ground coming down, hind legs pushing off then gathered under), then the stride picks up on landing; vanilla model and Fresh Animations
- [x] 2-block ledges bounded up in an arc: at least ~0.26 blocks/tick forward in the air, taking off ~1.4 blocks out (was 0.5) so the chest meets the edge near the top of the arc, carrying 1.4 blocks forward on the way up (was 0.6) and landing a stride onto the top
- [x] Rider jumping position: folds forward over the neck in the air (with the horse's rise), sits up for the landing

## Repos (2026-10-06)

- [x] Split into two repos: Horsing Around (this one) and Horsing Around: Over the Shoulder (`../Over the Shoulder`); the add-on compiles against a compile-only copy of `RideCameraApi` and has its own camera test (12 checks, including riding when Horsing Around is built next door); this repo's `runClient` loads the add-on when built, its ride test runs without it

## Release readiness (user request, 2026-10-06)

- [x] Builds on any machine (both repos): no machine-specific paths; `gradle/gradle-daemon-jvm.properties` asks for JDK 25 and the foojay resolver downloads one if none is installed (any Java 17+ starts the wrapper); `.gitattributes` keeps `gradlew` LF and `gradlew.bat` CRLF; sibling-repo dev paths accept both the local folder names and the GitHub repo names; GitHub Actions builds every push on Linux and attaches the jars. Verified by building both repos from a clean copy with an empty Gradle home and only JDK 11/17 installed
- [ ] Compatibility pass and store metadata (plan in `development_plan.md`, Release)

## Tooling

- [x] Automated client ride test (`./gradlew runClientGameTest`, `-Ptests=ride`, `-Psections=core,cuts,stairs,picking,steps`): 244 checks covering legs in the air (the stride stops, front legs fold then reach for the ground), the 2-block ledge bound, hard cuts (20, 60, 90 and 150 degrees off at a gallop, against cuts switched off; standing pivot), A or D alone riding across the view, the rider pushed back and shielding their face in leaves, climbing out onto a 1-block bank and not a 2-block one, narrow 2-block ledges jumped at a trot and a gallop, a ledge catching the flank and a 2-block pillar gone round, a row of trunks threaded (the 12 shoulder-camera checks moved to the add-on repo), steps in two beats (up and down, walk and trot), footing (across a ditch at a gallop and a trot, past a trunk that catches the shoulder), a leafy 2-block ledge, the horse picking its way (tree detour at pace, long wall slow-down, beside a wall and along a cliff edge at full pace, cliff and lava refusal, safe drop, gap jumped and refused, ledge jump at a walk and from a gallop, fence left alone), gradual and smooth climbing out of water, wading pace, swimming (speed, head above water, rider kept, climbing out), saddle side-sway follow, rider inertia (surge and braking), Fresh Animations stirrups held, trampling (walk vs gallop), horse settings in Mod Menu and live apply, downhill speed continuity, landing surge, jump arc, huff rhythm, head toss on the FA model, mouse steering, A/D 45-degree offset without view pull, riding through leaves, free aim and look limit, aiming while turning, Fresh Animations body sync, staircase climbing (smoothness, pitch), weight-shift timing, gait animation speeds, first-person bob, mounting camera, free look, walk/gallop speed, spur timing, A/D turn rate and lean, running and standing jumps, stamina and exhaustion, coasting, braking, reversing, mouse steering, dismount; writes `build/run/clientGameTest/horsingaround-ride-report.txt` and screenshots (back, front and side views)
- [ ] Dedicated-server variant of the ride test (needs the user to accept the Minecraft EULA for the test server)
