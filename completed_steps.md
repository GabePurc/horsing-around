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

## Repos (2026-10-06)

- [x] Split into two repos: Horsing Around (this one) and Horsing Around: Over the Shoulder (`../Over the Shoulder`); the add-on compiles against a compile-only copy of `RideCameraApi` and has its own camera test (12 checks, including riding when Horsing Around is built next door); this repo's `runClient` loads the add-on when built, its ride test runs without it

## Release readiness (user request, 2026-10-06)

- [x] Builds on any machine (both repos): no machine-specific paths; `gradle/gradle-daemon-jvm.properties` asks for JDK 25 and the foojay resolver downloads one if none is installed (any Java 17+ starts the wrapper); `.gitattributes` keeps `gradlew` LF and `gradlew.bat` CRLF; sibling-repo dev paths accept both the local folder names and the GitHub repo names; GitHub Actions builds every push on Linux and attaches the jars. Verified by building both repos from a clean copy with an empty Gradle home and only JDK 11/17 installed
- [ ] Compatibility pass and store metadata (plan in `development_plan.md`, Release)

## Tooling

- [x] Automated client ride test (`./gradlew runClientGameTest`): 107 checks (the 12 shoulder-camera checks moved to the add-on repo) covering the horse picking its way (tree detour at pace, long wall slow-down, beside a wall and along a cliff edge at full pace, cliff and lava refusal, safe drop, gap jumped and refused, ledge jump at a walk and from a gallop, fence left alone), gradual and smooth climbing out of water, wading pace, swimming (speed, head above water, rider kept, climbing out), saddle side-sway follow, rider inertia (surge and braking), Fresh Animations stirrups held, trampling (walk vs gallop), horse settings in Mod Menu and live apply, downhill speed continuity, landing surge, jump arc, huff rhythm, head toss on the FA model, mouse steering, A/D 45-degree offset without view pull, riding through leaves, free aim and look limit, aiming while turning, Fresh Animations body sync, staircase climbing (smoothness, pitch), weight-shift timing, gait animation speeds, first-person bob, mounting camera, free look, walk/gallop speed, spur timing, A/D turn rate and lean, running and standing jumps, stamina and exhaustion, coasting, braking, reversing, mouse steering, dismount; writes `build/run/clientGameTest/horsingaround-ride-report.txt` and screenshots (back, front and side views)
- [ ] Dedicated-server variant of the ride test (needs the user to accept the Minecraft EULA for the test server)
