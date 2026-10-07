# Research: RDR2 horses → Minecraft

Design fidelity reference. RDR2 is the feel target; vanilla Minecraft is the style boundary.

## Why vanilla horses feel static

- Instant acceleration (top speed in ~5 ticks) and instant stops.
- The horse's body is locked to the camera: it snaps to wherever you look, even when standing still.
- One speed. Holding W is always full speed; there is no sense of gait.
- Horses strafe sideways with A/D, which no horse can do.
- Hoof sounds switch to the gallop clip after five steps regardless of actual speed.
- No stamina, so speed has no cost and no decisions.
- No visual weight: no lean, no change in posture with speed.

## What makes RDR2 riding feel good

| RDR2 behaviour | Why it matters | Minecraft translation |
| --- | --- | --- |
| Four gaits (walk, trot, canter, gallop), spur to step up | Speed is a choice you make, not a key you hold | Sprint key taps step up a gait; S taps rein down |
| Momentum: gradual acceleration and slowdown | Horse has mass | Speed ramps toward a per-gait target each tick |
| Turning radius grows with speed | Gallops commit you to a line | Turn rate limited by speed; horse follows the camera with lag |
| Horse is not glued to the camera | Free look, horse feels separate from you | Standing still, the camera looks freely; moving, the horse turns toward it |
| Stamina drains while galloping, horse tires | Pacing, care for the horse | Gallop drains stamina; at zero, horse drops to canter until recovered |
| Hard stops, skids, rearing | Drama and control at speed | S held brakes hard; later: skid stop and rear at gallop |
| Crashing into obstacles at speed | Consequences | Wall hits shed momentum; later: stumble or throw the rider |
| Body language: lean into turns, head bob | Reads as an animal, not a vehicle | Model and rider lean into turns |
| Gait-specific hoof sounds | Rhythm sells speed | Clip chosen from real ground speed |
| Bonding, brushing, feeding, calling the horse | Attachment | Later phases: bond level, whistle, care actions |
| Fear of predators, rearing and bucking | The horse has a mind | Later: spook on hostile mobs, rear |

## Camera

RDR2's horse camera is a third-person chase camera: centered behind and a bit above the horse, it pulls back as the
horse speeds up, glides over bumps instead of jolting, settles back to a slightly downward riding angle when you stop
touching it, and swings around behind the horse when you steer with the stick. Steering is relative to the camera.

Translation: switch to third person on mount (back to first person on dismount). Pivot 0.5 above the rider's eyes,
distance 4.5 rising to 6.5 at a gallop with a slow follow so the camera falls back on acceleration. Vertical position
is smoothed. FOV widens slightly past a trot. Pitch eases to 10° down after 2s of no vertical mouse movement while
moving. Mouse steering leads (the horse turns toward the camera); A/D steering is horse-led and the camera swings
behind it. The camera never fights the mouse for yaw.

## Jumping

Vanilla: hold space to charge, the horse rears while charging, then lunges a fixed 0.4 blocks/tick forward and loses
half its speed in the air. RDR2: press and the horse jumps immediately; how far it carries depends on how fast it was
going; no charge meter.

Translation: jump fires on press (or on landing if pressed up to 8 ticks early). Power scales from 0.45 at a standstill
to full at a canter. Small forward push, and air control raised so ground speed carries through the jump. No rearing.
Each jump costs 6% stamina; an exhausted horse jumps at 75% power. The vanilla jump bar becomes the stamina bar
(vanilla sprites; red-tinted on the cooldown background when exhausted).

## Animations: Fresh Animations

Fresh Animations (FA) is a resource pack that needs the Entity Model Features (EMF) and Entity Texture Features (ETF)
mods. It replaces vanilla entity animation with its own, driven by vanilla values such as limb swing speed, so gait
speeds feed straight into its leg cycles. Compatibility rules for this mod:

- Never replace the horse model or its animation; only add whole-body transforms (lean) on the render pose stack.
- Keep vanilla animation inputs truthful (speed, onGround, ridden state).
- Dev runs and the ride test load EMF, ETF and FA so every change is checked against the target look.

## Body motion and weight

User direction (2026-10-06): the horse must shift its weight before it turns; riders resist the lean and stay upright;
riders bounce with the horse; slopes and steps must be smooth with the body pitching to the ground; first-person
view stays level (the rider holds their head straight) but bobs with the gait.

Reference research (RDR2 footage and GDC material, equine biomechanics papers; summary 2026-10-06):

- Turn order in RDR2 and real horses: head/neck bends first (~30 degrees), side-step, lean, then the body pivots on the
  front feet. RDR2 adds input lag deliberately.
- Bank angle follows atan(v^2 / (g r)); measured ~13 degrees at a trot and ~20 at a canter on small circles. We
  stylize lower (max 15) because blocky models read exaggerated.
- Sideways grip tops out around 5.5-8 m/s^2, so turning circles widen with speed and horses slow into turns.
- Stride rates: walk 0.85 Hz, trot 1.43 Hz, canter ~1.7 Hz, gallop 2.0-2.2 Hz. Fresh Animations' cycles at our gait
  speeds land on ~0.9 / 1.5 / 2.2 / 2.4 Hz.
- Rider: real riders keep shoulders and eyes level and sit centred (supports the user's "stay upright"); RDR2 riders
  tilt mostly with the horse. We follow the user: torso takes 30% of the bank. Uphill riders lean forward, downhill
  they stay vertical. RDR2 riders crouch 20-30 degrees forward at a gallop.
- Seat bounce: walk 3-5cm, trot 8-10cm per beat (twice per stride), canter ~10cm with fore-aft rock, crouched gallop
  rider ~6cm once per stride.
- RDR2 first person keeps the horizon level (no roll); head bob default is strong with a Reduced option. Third-person
  camera ignores the horse's roll and takes a fraction of the bob.

Implementation notes:

- Turning is second order: a weight commitment (-1..1) moves at a speed-dependent rate; the head leads by up to 20
  degrees and the bank follows the commitment; turn rate = max rate x commitment^2, acceleration-limited.
- All body posing is visual (render pose stack), so physics, collisions and servers are unaffected.
- Fresh Animations derives walk/trot/gallop from leg-animation speed (0.4 / 0.8 when ridden), so ridden horses feed
  it a speed equal to the fraction of vanilla top speed.

## Controls and camera (user direction, 2026-10-06)

- Steering (clarified): the horse always follows the mouse; A/D offset the horse 45 degrees from the view without
  moving it, so you can ride at an angle while aiming straight. The view is never pulled by the horse.
- Shoulder camera aiming works like most third-person shooters: centred crosshair, aim goes where it points.
- Forests: leaves don't stop a ridden horse; they slow it a little.
- Trampling: a horse at speed tramples small creatures; harmless at a walk, more damage the faster it goes.
- Water: speed loss scales with depth while the horse can touch the bottom; out of its depth it swims, head up.
- Both mods expose their feel numbers in Mod Menu.
- Comfort beats realism for the camera: first-person bob is gentle and low-passed; no camera shake.
- Third person should be RDR2-style over the shoulder, shipped as a separate add-on mod that plugs into this one.
- The rider must never look detached: hips ride the horse's actual animated body, feet in stirrups, hands on reins.

## The horse has a say (user direction, 2026-10-06)

The horse should feel alive and help the rider, not run blindly into trees or off cliffs, and it must never become an
annoyance ("super important"). RDR2 horses steer round trees and rocks on their own at speed, pull up and refuse at
cliff edges, and won't leap off a drop. Follow-ups from play-testing (2026-10-06/07): mountains are slopes, not sheer
drops, so momentum must not carry the horse into a hurting fall; where there is a way round something the rider runs
at, the horse should take it rather than plant its face in it; a fall costing a heart or two is fine unless the horse
is low on health (more cautious then); 2-block ledges should be jumped smoothly in the stride, a little before the
wall, never onto leaves; jumps should tilt like a real horse's (front up, push off behind, land front first); the
ridden horse's box should match its body. Tests must stand up to messy generated terrain, not just perfect lanes.

Translation (`Awareness`, tuning in `RideTuning`):

- Look-ahead: three lines (centre and flanks at the body's edge, widened for diagonal headings because the collision
  box doesn't turn) follow the ground along the path, ~1s of travel (2-12 blocks), recording the ground profile.
  Walls = too high to step or no headroom; danger = a fall it won't take under all three lines, or a hazard (lava,
  fire, magma, cactus, berry bushes, powder snow, cobwebs, lit campfires, the `horsingaround:horse_avoids` tag).
- Going round: when the rider asks for a trot or more, the horse looks along the obstacle (up to 12 blocks either
  side) for the nearest place the rider's line is clear past it, heads there (up to 85 degrees off), slows as much as
  it needs to make the turn and to come back round, and returns to the rider's line once clear with room to spare. A
  wall with no way round in reach: it slows to a walk and walks up to it. Never at a walk.
- Falls: any fall that doesn't hurt (vanilla horses take half damage past 6 blocks, so up to ~8) is fine; a sheer drop
  that costs the horse up to 2 hearts and the rider (who takes the fall with it) up to 3 is taken when both are
  healthy; below 40% health, none. Hazards are gone round or stopped for (snort and head toss from a trot); the rider
  can still jump one with ground beyond; never a cliff.
- Slopes: momentum carries a horse past each step down before it lands, so it simulates the flight over every step
  on the profile and keeps a pace that lands without any hurt, braking before the first step (it can't brake in the
  air). A slope is taken at a pace, never at a cost.
- Last resort guard (on the ground, and during a drop off a step but not a jump): the next tick's travel, side-step
  included, may not enter a hazard (it slides along it instead) or carry it off a fall it won't take.
- Ledges up to 2 blocks (never fences, walls, gates, leaves or a lone log: it needs solid ground for its ~2-block body
  length): jumped in the stride from a walk or trot, taking off about a stride out, a brief crouch, an arc that clears
  the lip by ~0.3, then it walks on. From a canter or gallop it slows first.
- Jumps tilt with the flight: nose up taking off (pivoting on the hind hooves), level over the top, nose down landing
  (pivoting on the front hooves). Every jump takes off at least as high as a player's (~1.25 blocks).
- Ridden, the collision box narrows from vanilla's 1.4-block square to 0.9 (about the body's width), so it threads
  1-block gaps between trees.
- Not annoying: no steering at a walk; riding beside walls and along cliff edges is untouched; safe drops, water
  landings and jumpable gaps are left to the rider; both behaviours can be switched off.

## Steps in two beats, and footing (user direction, 2026-10-07)

Play-test feedback: going up and down a block the lean was too linear and unsatisfying. A real horse gets its front
legs up on the block, then its back legs; it doesn't lean much, it bends its front knees to get the front legs up.
And the box clipping 1-block-tall things while riding stopped all the horse's speed. Also: 2-block ledges should be
jumped even with leaves on top; seeing where the horse is trying to steer helps play-testing.

Translation (`RideController.steps`, `Footing`, tuning in `RideTuning`):

- The front and the back of the body each follow the ground under their own hooves (front ~0.65 ahead, hind ~0.5
  behind): the forehand goes up a step first, then the hindquarters; going down, the front reaches down first.
- Smooth above all (play-test, same day: the first version, on springs with a hard tilt cap, snapped to full tilt in
  a tick and dipped nose-down after the push, which felt erratic on natural terrain). Each end eases in two stages, so
  every change starts and stops softly with no overshoot, quicker with speed; the ground is read as far ahead as the
  easing lags, so each end still moves as its own hooves reach the step and slopes are followed without falling
  behind. The tilt levels off softly toward 10 degrees (about 6 on a single block at a walk, less at speed) and is
  eased once more so quick bumps at speed rock the body rather than jolt it: at most ~1-2.5 degrees a tick anywhere.
  At a gallop the two beats run together, as they would.
- The weight stays on the hindquarters: the body sits 30% of the way up toward the front's height (20% going down).
  The front legs fold up and forward onto the step (or reach down for it), the hind legs drive back as the
  hindquarters rise. A block is about as tall as a Minecraft horse's legs, so with rigid model legs the front hooves
  pass through the edge of a step for a moment; the body hides it from the riding camera.
- Footing: in the air (off a drop, or a jump a little short) the horse gets a hoof on anything within a step of its
  hooves and carries on; vanilla's step-up only works with the hooves down, so meeting the far side of a dip in the
  air stopped it dead. A shoulder caught on a corner (up to 0.3 blocks) slips past it. Only a head-on hit (more than
  65% of the travel stopped) is a crash; before, any touch at speed halved the speed and dropped to a trot (vanilla's
  "minor collision" test only exists for players). Ridden, a full block is a step even from a path, farmland, mud or
  soul sand (step height 1.125).
- Steering overlay: with hitboxes shown (F3+B), the ridden horse draws the rider's line, its heading, its detour, what
  its look-ahead found and the ground carrying its front and back. A pathfinding visualizer would show nothing: a
  ridden horse doesn't use vanilla pathfinding.

## Hard cuts, straighter lines, ledges on the line, banks (user direction, 2026-10-07)

Play-test feedback: movement should be more dynamic, with hard cuts. First asked for as D (or A) pressed while looking
that way; revised the same day: the cut should come from how far the rider turns the view, gradually, the horse
slowing a little to cut harder and adapting its speed, lean and weight shift to turn at the radius the rider asks for
(before, looking far to the side kept the same speed and turning circle). Also: it looked at one obstacle at a time;
with several in a row, going round one shouldn't lead it into the next. Dodging trees sometimes over-corrects: the horse should keep as
straight a line toward where the rider looks as it can while dodging what is in front of it. Riding straight at a
2-block jump it should jump it, and only go round otherwise; it felt like a fight because it steered away from it.
Getting out of water still felt odd, and it climbed banks up to three blocks out of the water: one block at most.

Reference: cutting and reining horses turn hard by sitting back on their hocks and pivoting on the hindquarters
(the rollback), with far more sideways grip than in a relaxed turn and at a large cost in speed. Horses leave deep
water by getting their forehand onto the bank first and heaving the rest up after it.

Translation (`RideController.tick`, `Awareness.detour`, `Awareness.bank`; tuning in `RideTuning`):

- Hard cuts from the view: how hard the horse cuts grows smoothly with how far off its heading the rider looks, from
  nothing at 40 degrees (so riding at an angle with A/D, and ordinary steering, keep the weight-then-turn order) to
  all of it at 100. Cutting, it sits back (haunches down, nose up a few degrees), takes up to 2.2 times its grip and
  twice its top turn rate, commits its weight up to twice as fast, and slows, braking harder, to the speed at which
  that grip brings it round in about half a second; it eases out of the cut as it comes round and gallops on. The turn
  radius therefore follows the look: 90 degrees at a gallop comes round in ~0.75s at ~68% pace (a plain turn takes
  1.9s), 150 in ~1s at ~45%, banking up to the full 15 degrees. Cutting hard from a trot up scuffs up the ground.
  Standing still, looking round is still free look; with W held the horse pivots on its haunches. A/D keep riding at
  an angle. ("Hard cuts" can be switched off.)
- Straighter dodging: the horse plans its way round with its own steering model. It looks for the least it has to move
  over (to 1/8 block, 0.2 to spare either side), aims past the obstacle on a line that grazes its near corner (or, where
  that line meets something else in a crowded forest, doglegs out beside the near face), then simulates how its
  weight-shift turning will carry it and picks the least angle that gets it clear by the time its chest gets there. It
  heads back for the rider's line as soon as its momentum will carry it clear, so it doesn't swing wide. (Before, it
  aimed at the near corner in whole blocks, so it swerved late, steeply and twice as far as needed.)
- Things in a row: the way round one obstacle is better a lane that stays clear through what lies behind it (5 blocks
  past it, or to the end of the look-ahead); a lane up to 2 blocks further over than the nearest way past is taken
  over it, so the horse threads a row of trunks instead of dodging one into the next. Nose to a big trunk, where every
  line out at an angle clips it, it steps aside along it and comes round (the body slides along the face).
- Ledges on the line: from a trot up, a ledge the rider's centre line meets and the horse can jump (room to land its
  body length on top) isn't gone round: the horse slows only to a trot by the time it is in reach and jumps it. A ledge
  that only catches its flank, or a pillar with nothing to land on, it goes round.
- Banks: a swimming horse climbs out only onto a bank whose top is no more than a block above the top of the water's
  block layer (1.125, so a snow layer still counts); higher banks it can't climb. The climb is one heave of about a
  second: the body rises to the top eased in and out while pressing forward, nose up mid-heave as the forehand gets
  onto the bank, levelling as the hindquarters come up. Wading, the 2-block ledge jump from the bottom makes the same
  one-block limit.

## Riding across the view, and leaves you can feel (user direction, 2026-10-07)

Play-test feedback: holding just D should move the horse 80-90 degrees to the right (A to the left) instead of letting
it stop, so the rider can run forward, 45 degrees left/right and 80-90 degrees left/right at full speed. In third
person the rider should react to leaves: the leaves push the rider back slightly and they hold a hand up to keep them
off their face; going through leaves felt like phasing through them.

Translation:

- A or D alone (no W or S) ride the horse on at its gait 90 degrees left or right of the view (setting "A/D alone
  angle", 45-120); with W, 45 as before; letting go of everything still eases it to a stop. Swinging from straight
  ahead to across the view at a gallop is a 90-degree turn, so it is a hard cut like any other (it slows to make it,
  then gallops on).
- Leaves at the rider's chest or face (looked for just ahead of the rider, a few block reads a tick, only for ridden
  horses, on every client so everyone sees it): the rider leans back from the push (toward 7 degrees at full pace, on a
  spring, with a shove as their face meets each new clump) and puts the off hand up in front of their face (the main
  hand if the off hand is busy), head tucked and turned a little away; the hand comes up in about 3 ticks and down
  about 0.8s after they are clear.

## Jumps that look like jumps (user direction, 2026-10-07)

Play-test feedback: in a jump the run animation kept going; a jumping horse extends its front legs to meet the ground.
And jumping up 2 blocks looked physically wrong; it should look accurate and Minecrafty at once.

Reference: a jumping horse folds its front legs up under its chest as it takes off while the hind legs push, gathers
its hind legs under it over the top, and unfolds its front legs forward and down to land on them one after the other.
Up a bank it takes off a good stride out and carries forward over the edge; riders fold forward over the neck in the
air (the jumping position) and sit up for the landing. A 2-block ledge here (taller than a Minecraft horse's back) was
jumped from half a block off the face, nearly straight up: with Minecraft's strong gravity the launch reaches the lip
in about 4 ticks, so it popped up beside the wall and hung.

Translation:

- In the air (a jump or a fall bigger than a step) the stride eases to a stop and the legs take the jump's shape,
  blended in and out over a few ticks: climbing, front legs folded up and forward, hind legs pushing back; coming
  down, front legs reaching forward and down (one a little ahead) and hind legs gathered under the body. The stride
  picks up again on landing. Vanilla model and Fresh Animations alike; everyone sees it.
- A 2-block ledge is bounded up: at least ~0.26 blocks a tick forward through the air, taking off about 1.5 blocks
  out so the chest reaches the face near the top of the arc, sailing over the lip and landing a stride onto the top.
- The rider folds forward in the air (12 degrees, plus following 60% of the horse's nose-up rise) and sits up again
  coming down.

## Staying vanilla

- No new HUD panels. Stamina lives in the vanilla horse jump bar, which charged jumping no longer needs.
- Speeds are multiples of each horse's own speed attribute, so breeding still matters. Canter is a little below vanilla
  top speed and gallop a little above, so long-distance travel time is similar to vanilla.
- Vanilla keys only: W/S/A/D, sprint, jump. No new keybinds for the core loop.
- Vanilla jumping (charge with space) is untouched.
- Camels and llamas keep their vanilla controls.
