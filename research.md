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

## Staying vanilla

- No new HUD panels. Stamina lives in the vanilla horse jump bar, which charged jumping no longer needs.
- Speeds are multiples of each horse's own speed attribute, so breeding still matters. Canter is a little below vanilla
  top speed and gallop a little above, so long-distance travel time is similar to vanilla.
- Vanilla keys only: W/S/A/D, sprint, jump. No new keybinds for the core loop.
- Vanilla jumping (charge with space) is untouched.
- Camels and llamas keep their vanilla controls.
