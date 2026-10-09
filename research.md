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
  (A knee cut into each leg was tried on 2026-10-08 and taken out the same day, user direction: it strayed too far
  from vanilla. Legs are vanilla's one-piece legs again; see "Exact hooves, a body on its legs".)
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
- (Revised 2026-10-08, see "Hooves on the ground": the tilt follows the ground and the legs plant.) The weight stays on
  the hindquarters: the body sits 30% of the way up toward the front's height (20% going down).
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
  picks up again on landing. Vanilla model and Fresh Animations alike; everyone sees it. A Minecraft leg is one block
  hung from its top, so a leg swung far shows its top corner out of the body, as if detached (play-test, same day):
  each leg is drawn up into the body as far as its swing tips that corner out, and the swings are kept modest. Then
  (same day): legs sat too far back and too close together, and the pose jerked in and out: the front legs are kept
  under the chest and the hind legs under the hindquarters (nudged forward against the swing), each pair is staggered
  so both legs read, and in the air the legs move in one slow sweep across the whole flight (folded just after takeoff,
  reaching by the landing; at most ~4-5 degrees a tick), eased into from the stride over ~5 ticks with the stride winding
  down gently; landing hands back to the stride a little quicker.
- A 2-block ledge is bounded up: at least ~0.26 blocks a tick forward through the air, taking off about 1.5 blocks
  out so the chest reaches the face near the top of the arc, sailing over the lip and landing a stride onto the top.
- The rider folds forward in the air (12 degrees, plus following 60% of the horse's nose-up rise) and sits up again
  coming down.

Follow-up (play-test, same day): the horse sometimes refused 2-block ledges, and going up a block sometimes teleported
the view up it. Ledges built like generated worlds' (met at an angle, snow on top, bumps, leaves or a branch overhead,
from a step, slabs, at a canter) showed two causes: a layer of snow on top made "no room to land", and from a trot up
a ledge met at an angle was taken for a wall to go round, because a flank of the look-ahead met it before the centre
line did. Both fixed: a thin layer on top (up to a quarter block) counts as the ledge's top, and a wall is a ledge on
the rider's line if every line that met it met a ledge. The teleport came from runs of steps (up a mountainside): the
drawn body fell further behind each step until a reset meant for teleports snapped it 1.5 blocks; now past 0.8 blocks
behind it catches up smoothly (up to 0.12 blocks a tick extra) and only a real teleport (3+ blocks) snaps.

Then (same day): jumping up 2 blocks was abrupt where a step up came right where the horse would land, or starting
right at the face: the landing check counted only ground at the ledge's own height, so with a step just past the lip
the ledge read as "nothing to land on" until, with the face measured in quarter blocks, it passed by chance from close
in, and the horse popped nearly straight up from 0.4 blocks out. Ground up to a step higher now counts (it walks up it
after landing) and the face is measured to a few hundredths, so it takes off a stride out as usual. And the tail should
react to gravity: it now swings on a spring with the body's motion, trailing down as the horse launches, floating up and
streaming out behind in the air (keeping the lift the stride gave it), and flicking on landing.

And the rider's "hump" (the pelvis swinging forward and back at a canter) read as humping the horse; it should be what
really happens to a rider. A rider at a canter follows the horse's back with a small rock of the pelvis while the
upper body stays tall and quiet, and at a gallop rises into a half seat where the legs take the motion. The pelvis now
moves about a third as much (~3cm of hip travel at a canter, from ~9) and half that again at a gallop.

## Heaving up ledges, jumping at walls, camera height (user direction, 2026-10-08)

Play-test feedback: the 2-block jump still felt too snappy and unnatural; close to blocks in front, the horse sometimes
snorted and wouldn't jump even with space pressed; the riding camera sat too high (and needed a height setting); with
Better Mount HUD installed the stamina bar vanished; and Over the Shoulder should override anything this mod does to the
third-person view whenever it's active.

Reference: a horse getting up a bank as tall as its back doesn't pop up like a ball. It gathers itself (haunches down,
nose up), the hind legs drive the body up over a moment while the forehand reaches for the top, it slows as it gets
there, and the weight comes down onto the bank. The old jump left the ground at its full climbing speed in one tick (0.6
blocks/tick, 2.3 blocks up in 7 ticks) under full Minecraft gravity: a ball's arc.

Translation:

- The ledge jump is a heave: a longer, deeper gather (at least 5 ticks, haunches down 0.16, nose up 6 degrees, never
  sinking all at once even from a standstill at the face), the hind legs' push builds the climb over 3 ticks instead of
  one, and it rises under a little over half its weight, slowing toward the top (fastest climb ~0.4 blocks/tick, ~10
  ticks up instead of 7), then comes down onto the ledge under its full weight. It carries less forward speed (at least
  0.2 blocks/tick, from 0.26) and takes off about one and a half blocks out.
- Pressing jump at a ledge it can climb (in reach, even standing at its face) asks for the ledge jump, since a plain
  jump can't clear it; standing, it gathers where it is and goes.
- The safety look before a jump read a wall right in front as a bottomless drop (its ground profile was empty), so a
  jump pressed walking into a wall or a ledge it wouldn't climb was refused with a snort. A wall in front now means it
  comes down where it stands.
- The riding camera's pivot sits 0.2 above the rider's eyes (was 0.5), with a "Camera height" setting (-0.5 to 1.5
  blocks); its height follow eases in two stages so a takeoff or a step starts and stops softly.
- The stamina bar holds the jump bar's slot whenever stamina isn't full, also against mods that give it to the
  experience bar (Better Mount HUD shows experience unless jump is held, and our horses never charge a jump).
- While Over the Shoulder is on, this mod leaves the third-person view to it entirely: no placing the camera, no easing
  the pitch back to a riding angle (that moved the player's aim), and this mod's camera distance and height settings
  don't reach it (the add-on gets the designed riding distance and has its own settings; they're greyed out here, with
  a note). Its riding height came down too (0.35 above the eyes, was 0.7).

## Hooves on the ground, and no clipping (user direction, 2026-10-08)

Play-test feedback: going up stairs or 1-block slopes the horse's angle was wrong and its back legs floated; going down
it didn't lean forward and its legs floated. The user asked for inverse kinematics, or anything else that keeps the
hooves planted (except where they obviously shouldn't be) while keeping Fresh Animations' animation. Separately, in
jumps the horse's head went through the rider's; the user first asked for the rider to lean their head aside, then
decided against any sideways lean: horse and rider just shouldn't clip.

Causes: the body tilted at most ~10 degrees and sat with its weight on the hindquarters, so on a slope of one block per
block (45 degrees) the back of the body was far above the hind hooves' ground; downhill, ground more than 1.25 blocks
below the hooves was read as level, so the front saw no slope at all; stair blocks were read as full blocks. And Fresh
Animations' neck isn't vanilla's: it leaves vanilla's neck part empty and animates its own ("neck2", inside the body),
so the neck reaching forward on climbs (and the tired head toss) never reached the Fresh Animations model.

Both the vanilla horse and Fresh Animations have rigid one-piece legs (no knee joint), so "IK" here is: place the body
so every leg can reach, then swing each leg so its hoof lands on the ground actually under it.

- Tilt: along the line between the ground under the front and the hind hooves (95% of it, up to 40 degrees), eased and
  never more than 4 degrees a tick, pivoting at the leg joints so the hooves stay under them (pivoting at the ground,
  a 40-degree tilt slid the body half a block back).
- Height: as high as the body can sit with every leg still reaching its ground.
- Legs: stand upright against the tilt; each pair then finds its footing, trying swings forward and back (up to ~50
  degrees) for where its hoof meets the ground actually there (a swung leg reaches less far down), weighing a floating
  hoof more than one sunk into the ground, and big or sudden swings against it. On level ground nothing is read. On top
  of Fresh Animations' stride, so its animation is kept; each leg's top is drawn into the body so no gap shows.
- Ground: read at the hoof's exact spot (the low half of a stair is itself); down a slope the ground may fall away a
  block for every block along (plus half a block where the steps fall).
- Climbing, the neck reaches forward and down by 85% of the tilt (going down it comes up by 60%), so the head stays low
  and forward instead of the horse looking like it rears; on Fresh Animations this goes on its own neck.
- No clipping: when the horse's head would come within reach of the rider's head or chest (a jump, a steep climb), the
  horse stretches its neck further forward (up to 35 degrees, as a jumping horse does), and if that isn't enough the
  rider folds less over the neck (up to 30 degrees), only as far as needed, quickly in and easing back out.

## Knees and hurdles (user direction, 2026-10-08)

(The knees, the leg springs and the body's easing below were replaced the same day: see "Exact hooves, a body on its
legs". The hurdles stand.)

Play-test feedback on the hooves-on-the-ground version: at moments the legs detached from the body; horse armour no
longer followed the head; there was weirdness going down stairs and at walls and fences, and the horse should be able to
jump walls and fences while ridden. The user asked to try legs in two sections, with a knee, joined up by inverse
kinematics, still driven by Fresh Animations' animation.

- The detaching legs came from swinging a rigid one-piece leg up to ~90 degrees to find footing: its top swung out of
  the body. The armour came from the neck reach going only on the body layer's neck; Fresh Animations gives the armour
  and the bridle their own copy of the neck, animated the same way, so it goes on every layer now.
- Knees: the first time a leg is posed its box is cut into the upper leg (left on the leg part the model or the pack
  swings), the cannon (from 41% of the way down, on a child part that bends at the knee) and the hoof (its last 2
  pixels, as the horse texture paints it, bending at the fetlock), the texture cut with it, each piece capped and
  reaching a little into the one above so no gap opens on a bend. The upper leg also reaches 2.5 pixels further up into
  the body, so a big swing at the top shows no gap. Each hoof stands flat on the ground where it is drawn (the hoof
  turns at the fetlock to stay upright, as a horse's pastern lets it), the knee bent the way a horse's is (front knees
  jut forward, hind hocks back) and the leg turning at its top to suit (two-bone IK, top of the leg to fetlock).
- Pivots (found 2026-10-08 by measuring the legs as drawn, see Tooling): Fresh Animations pivots each leg near the
  hoof, not the hip (it animates the stride by moving the hoof and turning the leg about it). Everything that swings a
  leg (standing it upright on a slope, the knee, the jump's shape) turned the leg about the pack's pivot, so its top
  swung out of the body: the legs that "detached" and went wonky. Every swing now turns a leg about the top of its box
  and moves the pivot to keep the top where the pack put it, whatever the model's pivot.
- Each hoof's own ground: one of a pair can be on a step while the other is below it (on stairs, the stride puts them
  half a block apart), so each hoof reads the ground under its front, middle and back where the model is drawn (the
  render pose is kept from the frame), a moment ahead along its own motion so a swinging hoof clears a step's edge
  instead of catching on it, and comes up onto it quickly (down more gently). A hoof against a step's face it can't
  climb onto (more than 0.55 above) moves back off it onto the tread. Up is read off the drawn model's own transform,
  so the hoof rises straight up whatever tilts the body. The drawn body then fits itself: it comes up or down (slowly,
  never jolting) until the standing leg on the lowest ground is straight (a leg the pack has lifted mid-stride doesn't
  count), but never so low that a leg on higher ground would have to fold past what a knee can. A knee that would bend
  into the ground (a hock going down stairs, into the step behind) bends the other way. A step down is a short fall,
  not a jump: the hooves keep finding their ground and the legs don't take the jump's shape. All of it eases on game
  time, so it keeps pace with the horse at any frame rate. On level ground nothing is read and the pack's legs are
  untouched.
- Climbing smoothly (play-test, 2026-10-08: going up was hitchy): the drawn body tracks its height with an alpha-beta
  tracker (40% of the way each tick, carrying its climb), so up stairs and slopes it rises at an even rate instead of a
  pulse each step; its sharpest change in climb rate went from ~0.19 to ~0.08 blocks/tick a tick up stairs.
- Going down (play-test: a lot of phasing): walking down a slope of full blocks, the wide body stays on each block's
  edge until its middle is over the block two down, which read as a fall: the tilt let go and the hindquarters sank
  into the steps behind. A drop is a flight only when the ground under the hind hooves has gone too. On steep ground
  the stride is shorter (half the pack's swing at 30 degrees), as a horse picks its way up and down stairs.
- Known limit: on a slope of full blocks (a block up for every block along, 45 degrees), the stride still carries a
  hoof or a knee into the next block's face in roughly a fifth to a third of frames: a 0.76-block leg can't reach a
  1-block riser, so the hoof either stands short of it or touches it. Stairs (half-block steps) are clean going up and
  touch now and then going down.
- Jump pressed over and over at a 2-block ledge (play-test, 2026-10-08: it went really high, buggy): vanilla's
  charge-and-release jump still ran beside the ride's press-to-jump, firing the moment the horse touched ground after
  each release, and nothing stopped a jump straight off the top. Now the ride's jump is the only one, a press while riding
  at a ledge waits for its heave (from up to 5 blocks out) instead of hopping into its face, and up on top the horse
  settles for 0.7 seconds before it will jump again.
- Hurdles: pressing jump with a fence, a wall, a gate or anything else 1.125-1.6 blocks tall within 2.5 blocks of the
  chest (no deeper than 1.5, headroom over it, safe ground beyond) jumps 0.35 over it and far enough to clear it, at
  any pace, standing too, held at that speed in the air so meeting the face as it rises doesn't stop it. Lava or a
  drop it won't take beyond: it refuses. Ridden straight at one from a trot up, it doesn't go round it and slows only to
  a trot by the time it's in reach; without a jump it stops short with a snort. Pens still hold horses: nothing jumps a
  fence unless the rider asks.

## Staying vanilla

- No new HUD panels. Stamina lives in the vanilla horse jump bar, which charged jumping no longer needs.
- Speeds are multiples of each horse's own speed attribute, so breeding still matters. Canter is a little below vanilla
  top speed and gallop a little above, so long-distance travel time is similar to vanilla.
- Vanilla keys only: W/S/A/D, sprint, jump. No new keybinds for the core loop.
- Vanilla jumping (charge with space) is untouched.
- Camels and llamas keep their vanilla controls.

## Exact hooves, a body on its legs (user direction, 2026-10-08)

Play-test feedback on the knees: the legs clipped into the ground, snapped between bent and straight (on Fresh
Animations and vanilla alike), and landing a jump the horse sank into the ground and came back up; stepping up a block
it waited too long, so its chest went into the block. Everything was smoothed too much. The user's direction: make it
physically make sense first and smooth only if needed; the legs don't need smoothing, the body is smoothed by the legs
absorbing the force. At a walk the legs should go up a step one at a time, at speed nearly together. And no knee or
hoof joint: it strays too far from vanilla. References: slow-motion gallops (planted hooves stay put and straight,
swinging legs fold, the body glides).

Causes: each leg's height followed its ground on a spring, so a hoof was in the ground until the spring caught up; a
near-straight two-bone leg only shortens by bending a lot, so a hair of ground under a hoof flicked the knee (a dead
zone hid that, then let go of it all at once); on level ground the legs weren't posed at all, so the landing dip put
the hooves under; the forehand read the ground at the front hooves, but the chest is ahead of them and only ~0.7 above
them, so it reached a 1-block step before the body rose.

- Legs: vanilla's one-piece legs, never changed. Each stands upright against the ground's slope (turned about its top;
  not against the body's own sway with the gait or the breath, which put a hitch in every step), and its hoof goes
  where the ground under it is: over higher ground the leg draws up into the body (up to 0.35 blocks, for a moment
  0.55), as a horse folds at the shoulder, elbow and knee. The ground under the sole is worked out exactly, front to
  back (where the half-block grid crosses it): higher ground under part of the sole holds it up, coming in over the first
  ~0.06 blocks of it, so a hoof slipping onto a step's edge comes up onto it instead of flickering on and off it; a hoof
  in a step's face moves off it by exactly as far as it is in it. Measured at the middle of the sole (a planted leg rocks
  through upright every stride; the lowest corner switching there kinked every step).
- Lifting for what is ahead: a hoof in its swing (lifted by the gait) lifts toward ground rising ahead of it, by that
  ground's top less 1.5 for every block it still has to go (the arc of a swinging hoof over a step's edge); a hoof the
  body has lifted off the ground in front of a step is as free to; and moving on, any hoof takes the last bit of it (the
  animation's planted hooves slide a little, and would pop up a step's edge). Read from the gait's own lift and the
  horse's speed, never a hoof's speed frame by frame (that flickered with the animation). One leg at a time at a walk and
  nearly together at speed comes from the stride itself.
- A hoof lifts no faster than 10 blocks a second and a drawn-up leg straightens no faster than 4, but no hoof is ever left
  in the ground under it.
- Body: a mass carried on its legs. The front and the back each follow the ground under their own hooves on a
  critically damped spring (stiffer at speed), read as far ahead as the spring lags; that is the only smoothing. The legs
  push it up as hard as they need to, but nothing pulls it down faster than gravity (the game's own): stepping off a
  block, an end falls until its hooves meet the ground below and the legs take it up (play-test: it dropped as if
  gravity were a thousand times stronger). The forehand also rises to keep the chest clear of a step before the chest
  reaches it (worked out at the body's tilt). The tilt is simply the line between the two ends (no separate easing or
  rate cap, no tracker or catch-up on top). The body sits on its lower end, the legs at the other drawing up to fit, but
  no more than 0.3.
- Where the model is drawn, the body then rests on the hooves actually standing: it comes down onto them while every
  standing hoof is above its ground (falling no faster than gravity, so it doesn't snap down after a climb), comes up
  smoothly as a hoof on its way onto higher ground nears it or a leg is drawn up past 0.35, and never so low that the
  chest meets a step ahead.
- Hard stops: the legs give no more than 0.3 blocks under either pair (the body never sinks further); a leg that couldn't
  reach its ground even drawn up 0.55 raises the body at once. (Snapping the body up for anything less, and letting it
  settle back, fought frame by frame: on an edge the body crept down in a hitchy way.)
- Running off a drop bigger than a step at a canter or faster, the body carries over the edge level, in one piece, as a
  short leap (the forehand used to pitch down into it, ~30 degrees, while the hindquarters were still on the edge); at a
  walk it steps down front first.
- Landing: the body keeps coming down as fast as it fell and the legs take it up (they draw up into the body), so it
  sinks a little and comes back up with every hoof on the ground. The jump's shape eases out over the landing instead of
  being dropped, and any hoof it would leave in the ground comes up onto it.
- Every layer of the model (body, saddle, armour) gets exactly the same legs each frame, measured once (the other layers
  were posed as if no time had passed, so legs differed between layers; a layer without leg boxes now leaves the ride's
  state alone, and leg boxes are measured as built, before an armour layer grows them). The ridden horse's legs are
  always placed, so they never switch over to the model's own mid-stride.
- Measuring the snaps: a test watches a horse standing still, at the edge of a block, walking then stopping, and walking
  slowly up and down a single step, tick by tick and frame by frame at 120 frames a second, for any sudden change in the
  body or a leg (`-Psections=slowstep`).
- Known limit: on a slope of full blocks (a block up for every block along) the risers are taller than a leg can reach
  or draw up for, so the horse scrambles: a hoof is off the ground on its way up, or hanging over the next block down,
  more often than on stairs. Off the ground, never in it.

## Out of the water, and stopping after riding across the view (GitHub issues #15 and #16, 2026-10-09)

Play-test reports: riding out of water onto a bank, the horse and rider rose higher than they should and then snapped
down onto the ground; and riding with D (or A) alone and then letting go, the horse sank a little into the ground as it
stopped. Climbing out should look like stepping up the bank (rising only as far as the ground it lands on and settling
smoothly), and the body should stay at its normal height when stopping, whatever key moved it.

Causes, measured with new ride-test lanes (`-Psections=exits,strafe`):

- Wading out up a step, the physics box steps up a whole block in a tick while the drawn body eases after it; when the
  horse came out of the water the drawn body was handed that step as its upward speed, and flew about 2.6 blocks up
  until the teleport guard snapped it back. A wading or swimming horse leaving the water onto a slab or a block-high bank
  took the ballistic ledge jump instead (0.3 above the lip, then a drop of 0.16 in a tick as it landed).
- Letting go of A or D, the target heading swings back to the view, 90 degrees away: a hard cut, so the horse sat back on
  its haunches (body 0.12 lower) as it coasted to a stop and stood that way for a second.

Translation:

- The drawn body always carries its own speed into a step or a landing (not the physics box's).
- Wading, the hooves are on the bottom: steps and slopes as on dry land, so stepping out of the water up onto the bank is
  an ordinary step (forehand first). A bank too high to step up (up to a block above the water, as for swimming) is
  climbed in the same heave as out of deep water, never the ledge jump; the drawn body never shows the extra lift the
  heave takes to get the box over the lip (test: highest above the bank 0.00-0.03, was up to 2.6; no fall of more than
  0.03 in a tick settling onto it).
- Heaving out, a front hoof meeting the bank's top lifts onto it at a hoof's speed instead of popping up in one frame,
  and doesn't jerk the body up (a front sole's biggest jump in a frame 0.52 -> 0.34). Still open: the front soles still
  jump about 0.34 mid-heave, mostly forward and back, on Fresh Animations and the plain model alike; logged by the lanes.
- Hard cuts only while riding on (W, or A/D held): a horse let go of, or braked, comes round to the view as it stops but
  doesn't sit back for it, and stands up out of any cut as soon as it is let go of (test: lowest body stopping after D or
  A -0.001, was -0.12).
