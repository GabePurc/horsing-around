# Research: RDR2 horses → Minecraft

The design in short: what the user wants each part to feel like, and the shape of how it works now. RDR2 is the feel
target; vanilla Minecraft is the style boundary. Intent (marked "user") is the thing to keep; the "now" notes are one
way of doing it, and a better way is welcome (see `AGENTS.md`, "How to use these docs"). Current numbers are in
`RideTuning`. The full history, with references, causes and measurements per request, is `docs/design-log.md`: read
only the section you need (`grep -n '^## ' docs/design-log.md`).

## Why vanilla horses feel static

Instant top speed and stops; the body snaps to the camera; one speed; strafing; the gallop clip after five steps
whatever the speed; no stamina; no visual weight.

## Riding

- Gaits (walk, trot, canter, gallop): sprint taps step up, S taps step down, releasing W coasts to a stop, S held
  brakes hard then backs up. A double tap of W (user, 2026-10-18) steps up too and locks the horse into riding on with W
  let go, until S. Speeds are multiples of each horse's own speed attribute (breeding matters); canter a
  little below vanilla top speed, gallop a little above.
- Momentum: gradual acceleration and coasting, harder braking; a head-on wall hit at speed sheds momentum, a graze
  slips past.
- Turning (user): the horse shifts its weight before it turns; turn rate is grip-limited, so a gallop turn is wide.
  Now: a weight commitment leads, head first, bank follows (max ~15 degrees, stylised low for blocky models).
- Steering (user): the horse always heads where the rider looks; the view is never pulled by the horse. Standing still
  is free look. W+A/D ride 45 degrees off the view; A or D alone ride across it (90) at the gait. No strafing.
- Hard cuts (user): the further the rider turns the view, the harder the horse cuts, sitting back and slowing to make
  the tighter turn, then galloping on. Only while ridden on, and never from the horse's own avoidance.
- Stamina: galloping drains (~14 s on a full bar); exhausted, it drops to a canter until recovered; jumps cost a little.
  Shown in the vanilla jump bar's slot (also against HUD mods that move that slot).
- Vanilla keys only (W/S/A/D, sprint, jump); no new keybinds for the core loop. Camels, llamas and every other mount
  keep vanilla controls; the horse family (`horsingaround:managed` tag) gets the new riding.
- How horses ride is not a setting (user, 2026-10-08): settings are only comfort and preference (camera, first-person
  motion, extra sound volume).

## Camera

- Third person on mount (user: RDR2-style): behind and a bit above, pulls back with speed, glides over bumps, eases to
  a slight downward riding angle when the mouse is idle, never fights the mouse for yaw. Comfort beats realism: gentle,
  low-passed first-person bob, no shake, level horizon.
- Over the shoulder by default (user, 2026-10-20; was a separate add-on): the riding camera sits off the right shoulder
  (setting: right, left, or off for centred; O swaps), looks exactly where the rider looks, and shows the centre
  crosshair; targeting and the rotation the server hears point at what is under it (`RideAim`). It leans in to aim and
  leaves the pitch alone (centred, the pitch still eases back). Only while riding: on foot, third person is vanilla.

## Jumping

- Press to jump (RDR2), no charge; how far it carries comes from speed; ground speed carries through the air.
  Vanilla's charge-and-release jump no longer runs on managed horses.
- Jumps look like jumps (user): front up and legs folded on takeoff, a sweep through the flight, front legs reaching
  for the landing; the rider folds forward in the air and sits up to land; the tail reacts to the motion.
- 2-block ledges (user): ridden straight at, the horse heaves up in its stride (gather, push, slow toward the top),
  slowing to a trot first from a canter or gallop; any approach angle and neighbourhood climbs (`-Psections=climbs`).
  Never fences, walls or leaves; a ledge that only catches the flank is gone round.
- Hurdles: fences and walls are jumped only when the rider presses jump (pens still hold horses).

## The horse has a say (user: "super important" that it is never an annoyance)

- It helps the rider: steers round trees and rocks from a trot, keeps the rider's line, slows for walls with no way
  round, refuses drops that would hurt it (more cautious when hurt) and hazards (snort and head toss). A fall costing a
  heart or two is fine when healthy; mountains are slopes, taken at a pace that lands safely.
- Never in the rider's way: no steering at a walk; walls alongside and cliff edges untouched; safe drops, water
  landings and jumpable gaps left to the rider.
- Ways through (user): an efficient line that keeps the rider's intent and loses the least momentum, and stays cheap.
  Now: candidate paths are played out with the horse's real steering and momentum, replanned every other tick, only
  while something is in the way, with no allocation (`Awareness.plan`). Tight streets: no swaying, no scraping.
- Ridden, the box narrows to about the body's width so it threads 1-block gaps.
- Tests must stand up to messy generated terrain, not just perfect lanes.

## Body, legs and steps

- Stay vanilla (user, 2026-10-08): vanilla's one-piece legs, never a knee or extra joint (tried and removed); never
  replace the model or its animation; only add transforms on top, so Fresh Animations (EMF + ETF) keeps working. Keep
  vanilla animation inputs truthful.
- Physically right first (user): hooves planted on the ground actually under them (not in it, not floating), legs
  drawing up into the body over higher ground; the body is a mass carried on its legs, never falling faster than
  gravity; the tilt is the line between the ground under the front and the hind hooves.
- Hooves move smoothly (user direction, 2026-10-22: legs "jump up really quickly to avoid clipping"; avoid clipping in a
  smooth and realistic way): a hoof moves to its height on a short spring (quicker setting down than lifting) and looks
  ahead along its own path so it is already up when it reaches higher ground; it may settle under a pixel into the
  ground for a moment, never more.
- Steps in two beats (user): front legs up first, then the hind; one leg at a time at a walk, nearly together at
  speed; at a run a step comes in through the stride without a pop.
- No clipping between horse and rider (user): the horse reaches its neck forward, then the rider folds less.
- Judge legs by `LegProbe` measurements and close shots, not formulas.
- Known limits: on a slope of full blocks a leg can't reach a 1-block riser, so the horse scrambles; trotting down one,
  a hoof hangs a tick or two after each landing (user decision 2026-10-10: kept as is, unless a better way turns up).

## Rider

- Never looks detached (user): hips ride the animated body, feet in stirrups, hands on reins; resists the horse's
  lean and stays upright; bounces with the gait.
- Rides like a real person, medieval rather than modern racing (user, 2026-10-23): long stirrups and straight legs (a
  Minecraft leg has no knee anyway: it turns about the foot in the stirrup); sits the walk and trot, a light seat at a
  canter (a small pelvis rock, not a hump), standing in the stirrups at a gallop, upright and a little forward (not a
  jockey's crouch), the legs taking the bounce. Weight shows: leans back from a surge; braces the legs forward and sits
  back pulling up, going downhill and coming down out of a jump or off a drop; gives at the hips and knees landing;
  rises into a steep climb or a ledge heave. All on springs: nothing snaps.
- Tools and weapons are held low and ready in the main hand, the other on the reins; swings start and end there.
- The bow is drawn like an archer's (side-on, bow arm to the target, string hand to the cheek); crossbow square to
  the aim; first person and on-foot left to vanilla.
- Leaves at the face: the rider leans back from the push and puts a hand up.

## World

- Leaves don't stop a ridden horse; they slow it a little. It never stands on leaves.
- Water: speed loss with depth while it touches the bottom; out of its depth it swims, head up. Out of deep water it
  climbs only banks up to a block above the water, in one heave; wading out is an ordinary step.
- Trampling: at speed it tramples small creatures (never players or the rider's pets), more damage the faster it goes.
- Hoof sounds follow real ground speed; a tired horse breathes.

## Multiplayer and other mods

- Other players see the horse's real heading, not the rider's camera. For servers with the mod (the user's family
  server); on a server without it the client rides vanilla. Every server move must be accepted (no rubber-banding).
- Compatible with popular mods (no crashes, log errors or silently disabled features), checked by `runCompat`.

## Ideas not built yet

Skid stop and rear from a hard gallop stop; stumble or throw the rider on a hard crash; terrain speed (slopes, soft
blocks, snow, paths); spook near hostile mobs; bonding; whistle; brushing and feeding. See `development_plan.md`.
