# Design log

Why things are the way they are: what the user asked for, the references behind it, and what was tried and dropped, so it
isn't tried again by accident. By topic; this isn't a description of the current code. Mechanisms and numbers here are
history, and a lesson can be overturned by a better idea. New entries: what was asked and why, plus anything dropped.
The longer per-request version, with old numbers and measurements: `git show 7a50edf:docs/design-log.md`.

## References

RDR2, and why each thing works:

- Gaits, spur to step up: speed is a choice you make, not a key you hold.
- Momentum: the horse has mass. Turning radius grows with speed: gallops commit you to a line.
- Not glued to the camera: free look, and the horse feels separate from you.
- Stamina: pacing and care for the horse. Hard stops, skids, rearing: drama at speed. Crashes: consequences.
- Lean and head bob: an animal, not a vehicle. Gait-specific hoof sounds: rhythm sells speed.
- Bonding, brushing, calling: attachment. Fear of predators, rearing: the horse has a mind.
- Camera: a chase camera that pulls back with speed, glides over bumps, settles to a slightly downward angle when left
  alone. Jump: press and it goes, carrying by speed, no meter. First person: level horizon.

Real horses and riders:

- Turn order: head and neck first (about 30 degrees), a side-step, the lean, then the body pivots on the front feet. RDR2 adds
  input lag on purpose.
- Bank follows atan(v^2 / (g r)): about 13 degrees at a trot and 20 at a canter on small circles. Sideways grip tops out
  around 5.5-8 m/s^2, so circles widen with speed and horses slow into turns.
- Stride rates: walk 0.85 Hz, trot 1.43, canter about 1.7, gallop 2.0-2.2.
- Cutting and reining horses turn hard by sitting back and pivoting on the hindquarters, with far more grip and a large
  cost in speed.
- A jump: front legs fold under the chest as the hinds push, hinds gather over the top, front legs unfold to land one after
  the other. Riders fold forward over the neck and sit up to land.
- A bank as tall as its back: the horse gathers (haunches down, nose up), the hinds drive it up over a moment, it slows toward
  the top. It doesn't pop up like a ball. From deep water, forehand on the bank first, then the heave.
- Slow-motion gallops: planted hooves stay put and straight, swinging legs fold, the body glides.
- Riders keep shoulders and eyes level. RDR2 riders tilt with the horse; here the user chose upright. Seat bounce: walk
  3-5 cm, trot 8-10 cm per beat, canter about 10 cm, a crouched gallop about 6 cm. At a canter the pelvis rocks a little
  while the upper body stays quiet; at a gallop the rider rises into a half seat.
- Mounted archers and swordsmen: reins in one hand, the weapon low and out from the body. An archer stands side-on, bow arm
  straight at the target, drawing to an anchor at the jaw or cheek; the draw hand flies back past it on release.

## Riding, steering, turning

- 2026-10-06, user: the horse shifts its weight before it turns; the horse always follows the mouse and never pulls the
  view; A/D offset it 45 degrees so you can ride at an angle while aiming straight.
- 2026-10-07, user: movement should be more dynamic. Hard cuts were first asked for as D pressed while looking that way, then
  revised the same day: they come from how far the rider turns the view, gradually, with speed, lean and weight adapting.
- 2026-10-07, user: D alone rides 80-90 degrees right, instead of letting the horse stop, so the rider can run forward, at
  45 and at 90 at full speed.
- 2026-10-18, user: a double tap of W raises the speed and locks the horse on until S; sprint works as before. Trap: vanilla
  also clicks W for OS key repeat, and its held state lags the click by a tick, so count clicks made while W was up.
- Issue #16: letting go of A or D swings the target heading 90 degrees, which read as a hard cut, so the horse sat down as
  it stopped. Hard cuts now need the horse ridden on.

## Camera

- 2026-10-06, user: comfort beats realism; first-person bob gentle, no shake. Over the shoulder should work like a third-person
  shooter: centred crosshair, aim goes where it points. Shipped first as an add-on.
- 2026-10-08, user: the camera sat too high. Easing the pitch moved the player's aim, so it's off over the shoulder.
- 2026-10-20, user: the add-on folds into this mod as a riding-only camera setting. The mod declares the old add-on
  incompatible so a leftover copy gets a message rather than a crash.

## Jumps, ledges, hurdles

- 2026-10-06, user: 2-block ledges jumped smoothly in the stride, a little before the wall, never onto leaves.
- 2026-10-07, user: riding straight at a 2-block jump should jump it, and it only goes round otherwise; it felt like a fight.
  Jumps kept the run animation; a jumping horse reaches its front legs for the ground. It should look accurate and Minecrafty.
- 2026-10-08, user: the ledge jump still felt snappy. A launch at full climbing speed under Minecraft's gravity is a ball's
  arc, so it became a heave: a longer gather, a push building over ticks, rising under part of its weight.
- 2026-10-08, user: walls and fences should be jumpable while ridden; pens must still hold horses, hence only on a jump press.
- Issue #24, user: climbs were unreliable depending on angle and neighbours, making mountains frustrating. Wanted any 2-block
  rise from any direction, tested by sweep rather than patched case by case. Traps found: a single straight-ahead landing spot
  fails in corners and on ragged treads, so the landing is found by carrying the box over the lip; blocks taller than a ledge
  must not be skipped when finding the top (a 3-block wall read as a ledge); a full step of in-air footing plus a jump reaches a
  2-block top ("twice as high"), so in-air steps are small.
- Vanilla's charge-and-release jump running beside the ride's made jump spam go really high; the ride's is now the only one.
- Traps: a wide landing search on the "is it on the rider's line" check made the horse stop steering clear of flank ledges,
  so that check stays straight on. A wall right in front read as a bottomless drop, so jumps there were refused.

## The horse has a say

- 2026-10-06, user: alive and helpful, not blind, and never an annoyance ("super important"). Mountains are slopes, so momentum
  mustn't carry it into a hurting fall. Take a way round rather than plant its face. A fall costing a heart or two is fine
  unless low on health. Tests must stand up to messy terrain.
- 2026-10-07, user: going round one thing shouldn't lead into the next; dodging over-corrected and should keep as straight a line
  as it can.
- 2026-10-09, user: it slowed a lot for things it could go round. Wanted an efficient path that keeps the rider's line and loses
  least momentum, and stays cheap. Dropped: aiming past the nearest corner one obstacle at a time (late and steep, braking for
  things beyond its turn, wedging in forests). Replaced by playing out candidate paths with real steering and momentum.
- 2026-10-09, user (a medieval city): it swayed in tight streets. Traps: turning was mistaken for drifting off the line, so it
  steered against the rider (the line now holds only once the view is still); room to spare made every way hit the wall beside
  it after a corner (it now grows from nothing); fences were read as whole blocks; a fence ridden along counted as a jump.
- A pathfinding visualiser would show nothing, since a ridden horse doesn't use vanilla pathfinding. Hence the F3+B overlay.

## Legs, body, steps

This area was reworked most. Read it before changing it.

- 2026-10-07, user: steps felt linear. A real horse gets front legs up, then back legs, and doesn't lean much. The first version,
  springs with a hard tilt cap, snapped and felt erratic on natural terrain.
- 2026-10-08, user: legs floated on stairs and slopes; asked for IK or anything that plants hooves and keeps Fresh Animations.
  The horse's head went through the rider's; first asked for the rider to lean aside, then decided against: they just shouldn't clip.
- 2026-10-08, tried and removed the same day: two-section legs with a knee and two-bone IK (user's experiment). They clipped, snapped
  between bent and straight, and strayed from vanilla. A near-straight two-bone leg only shortens by bending a lot, so a hair of
  ground flicked the knee.
- 2026-10-08, user's direction after that: physically right first, smooth only if needed; legs absorb the force, so the body is
  smoothed by them. At a walk legs go up one at a time, nearly together at speed. No knee or hoof joint.
- Dropped along the way: alpha-beta height tracking and catch-up on top of the body; a separate tilt cap and easing (over-smoothed,
  chest went into steps); springs on hoof height where the hoof sat in the ground until caught up; swinging a rigid leg up to
  90 degrees for footing (its top left the body); pulling the body down faster than gravity.
- Fresh Animations traps: it leaves vanilla's neck empty and animates its own ("neck2"), with armour and bridle on copies; it
  pivots each leg near the hoof, so turn legs about the top of the box and move the pivot; its stride comes from leg-animation speed.
- Every model layer must get exactly the same legs each frame, measured once.
- 2026-10-09, user: running up a block looked snappy. The read-ahead target crossed the edge within a tick. A step at a run now
  comes in over a few ticks.
- 2026-10-10, user decision: keep the hoof hanging after each landing trotting down full blocks. Only a physics step-down would
  remove it. Starting each end down early helped walking slightly but doubled leg snaps.
- 2026-10-22, user: "legs jump up really quickly to avoid clipping"; avoid it smoothly and realistically. Dropped: a hoof following
  a moving height (it passed a step edge's flicker through), looking as far ahead when planted (animations slide planted hooves
  forward, so they hovered before every step), setting down as slowly as lifting (it hovered).
- 2026-10-31, user: go back to before the recent leg and animation work and keep the fixes. Removed or never merged: stepping legs
  near a step, the body-tilt rework, the medieval rider seat, procedural legs for the plain model, legs on bones, an always-seated
  rider (#66-#70). Kept on branches: `stride-climb`, `rider-seat`, `gait-cycles`, `footfall-legs`, `no-standing`.

## Rider

- 2026-10-06, user: stay upright against the lean, bounce with the horse, never look detached.
- 2026-10-07, user: the canter's pelvis swing read as humping the horse; it should be what really happens to a rider. Leaves should
  push the rider back and bring a hand up, since going through them felt like phasing.
- Issues #17, #18: hold a tool or weapon relaxed at the side, swings starting and returning there; a believable bow draw. First
  tries laid the sword along the neck or held it like a lance. Blocky and vanilla in style; first person untouched.

## Water

- 2026-10-06, user: speed loss with depth; out of its depth it swims, head up.
- 2026-10-07, user: it climbed banks three blocks out of the water; one at most.
- Issue #15: leaving water launched the body about 2.6 blocks, because the drawn body took the physics box's step as its own speed.
  The drawn body always carries its own speed.

## Multiplayer and versions

- 2026-10-22, a NeoForge report and the user's play: on 1.21.1 a gallop across a dip got the client a hoof down in the air while the
  server had no such footing, and it rejected every later move. 26.3's server trusts the client's ground contact, which hid it.
  The server now checks ridden moves with the same footing. Natural ground finds what built lanes don't, hence `-PsmokeWild`.

## Settings

- 2026-10-08, user: nothing about how horses ride is a setting. The switches for the horse's behaviours and a planned config file
  for feel numbers went with that decision.
