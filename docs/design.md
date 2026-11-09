# Design

What each part should feel like, and the shape of how it works now. "User" marks what the user asked for: keep the
intent, and raise it with them before reversing it. "Now" and any number describe one way of doing it, so a better way is
welcome. Current numbers are in `RideTuning`; why things are this way is in `design-log.md`.

Vanilla horses feel static because of instant top speed and stops, a body snapped to the camera, one speed, strafing, a
gallop clip after five steps whatever the speed, no stamina and no visual weight.

## Riding

- Gaits (walk, trot, canter, gallop): sprint taps step up, S taps step down, releasing W coasts to a stop, S held brakes
  hard then backs up. A double tap of W (user, 2026-10-18) steps up too and rides on with W let go, until S. Speeds are
  multiples of each horse's own speed attribute, so breeding matters; canter a little below vanilla top speed, gallop a
  little above.
- Momentum: gradual acceleration and coasting, harder braking. Roughly 2 s of spurring to a full gallop; from one, about
  2 s to coast to a stop and under 1 s braking. A head-on wall hit at speed sheds momentum; a graze slips past.
- Turning (user): the horse shifts its weight before it turns; turn rate is grip-limited, so a gallop turn is wide. Now: a
  weight commitment leads, head first, and the bank follows (about 15 degrees at most, low for blocky models).
- Steering (user): the horse heads where the rider looks and never pulls the view. Standing still is free look. W+A/D
  ride 45 degrees off the view; A or D alone ride across it at the gait. No strafing.
- Hard cuts (user): the further the rider turns the view, the harder the horse cuts, sitting back and slowing, then
  galloping on. Only while ridden on, and never from the horse's own avoidance.
- Stamina: galloping drains it (about 14 s on a full bar); exhausted, the horse drops to a canter until recovered; jumps
  cost a little. Shown in the vanilla jump bar's slot, also against HUD mods that move that slot.
- Vanilla keys only for the core loop. The horse family (`horsingaround:managed` tag: horse, donkey, mule, skeleton and
  zombie horse, and other mods can opt in) gets the new riding. Every other mount rides as in vanilla with no per-tick
  work (user, 2026-10-07), and mob riders and vanilla quirks stay (skeleton horses walking lake bottoms).

## Camera

- Third person on mount (user: RDR2-style): behind and a bit above, pulls back with speed, glides over bumps, eases to a
  slight downward riding angle when the mouse is idle, never fights the mouse for yaw.
- Comfort beats realism (user): gentle, low-passed first-person bob, no shake, level horizon.
- Over the shoulder by default (user, 2026-10-20): off the right shoulder (setting: right, left, or off for centred; O
  swaps), looking exactly where the rider looks, with the centre crosshair; targeting and the rotation the server hears
  point at what is under it (`RideAim`). It leans in to aim and leaves the pitch alone. Only while riding: on foot, third
  person is vanilla.

## Jumping

- Press to jump, no charge (RDR2); speed sets how far it carries, and ground speed carries through the air (at least 85%).
- Jumps look like jumps (user): front up and legs folded on takeoff, a sweep through the flight, front legs reaching for
  the landing; the rider folds forward and sits up to land; the tail reacts. Not leaning back too far (user, 2026-11-02).
- 2-block ledges (user): ridden straight at, the horse heaves up in its stride (gather, push, slow toward the top),
  slowing to a trot first from a canter or gallop, at any approach angle and in any neighbourhood. Never fences, walls or
  leaves; a ledge that only catches the flank is gone round.
- Hurdles: fences and walls are jumped only when the rider presses jump, so pens still hold horses.

## The horse has a say (user: "super important" that it is never an annoyance)

- It helps: steers round trees and rocks from a trot, keeps the rider's line, slows for walls with no way round, refuses
  drops that would hurt it (more cautious when hurt) and hazards (snort and head toss). A fall costing a heart or two is
  fine when healthy; mountains are slopes, taken at a pace that lands safely.
- It stays out of the way: no steering at a walk; walls alongside and cliff edges untouched; safe drops, water landings
  and jumpable gaps left to the rider.
- Ways through (user): an efficient line that keeps the rider's intent and loses the least momentum, and stays cheap.
  Tight streets: no swaying, no scraping. Now: candidate paths played out with the horse's real steering and momentum,
  replanned every other tick, only while something is in the way, with no allocation (`Awareness.plan`).
- Ridden, the box narrows to about the body's width so it threads 1-block gaps.
- Tests must stand up to messy generated terrain, not just perfect lanes (user).

## Body, legs and steps

- Stay vanilla (user, 2026-10-08): vanilla's one-piece legs, never a knee or extra joint (tried and removed); never replace
  the model or its animation; only add transforms on top, so Fresh Animations keeps working. Keep vanilla animation inputs
  truthful.
- Physically right first (user): hooves on the ground actually under them, legs drawing up into the body over higher
  ground; the body is a mass carried on its legs, never falling faster than gravity; the tilt is the line between the ground
  under the front and the hind hooves.
- Hooves move smoothly (user, 2026-10-22: avoid clipping "in a smooth and realistic way"). Now: a hoof moves to its height
  on a short spring and looks ahead along its own path, so it is already up when it reaches higher ground.
- Steps in two beats (user): front legs up first, then the hind; one leg at a time at a walk, nearly together at speed; at
  a run a step comes in through the stride without a pop.
- No clipping between horse and rider (user): the horse reaches its neck forward, then the rider folds less.
- Known limits: on a slope of full blocks a leg can't reach a 1-block riser, so the horse scrambles; trotting down one, a
  hoof hangs a tick or two after each landing (user, 2026-10-10: kept unless a better way turns up).

## Rider

- Never looks detached (user): hips ride the animated body, feet in stirrups, hands on reins. Resists the horse's lean and
  stays upright, but goes up with a rearing horse's back (user, 2026-11-02). Bounces with the gait; a small pelvis rock at
  a canter, a half seat at a gallop, not a hump.
- Tools and weapons are held low and ready in the main hand, the other on the reins; swings start and end there.
- The bow is drawn like an archer's (side-on, bow arm to the target, string hand to the cheek); crossbow square to the
  aim; first person and on foot left to vanilla.
- Leaves at the face: the rider leans back from the push and puts a hand up.

## World

- Leaves don't stop a ridden horse; they slow it a little. It never stands on leaves.
- Water: slows with depth while it touches the bottom; out of its depth it swims, head up. From deep water it climbs only
  banks up to a block above the water, in one heave; wading out is an ordinary step.
- Trampling: at speed it tramples small creatures, never players or the rider's pets, harder the faster it goes.
- Hoof sounds follow real ground speed; a tired horse breathes.

## Multiplayer and other mods

- For servers with the mod on both sides (user: a family server). On a server without it the client rides vanilla and says
  so once; players without it can still join and ride vanilla. Other players see the horse's real heading, not the rider's
  camera. Every move must be accepted by the server (no rubber-banding).
- A plain install needs only the loader and, on Fabric, Fabric API. Mod Menu and the animation stack stay optional.
- Compatible with popular mods: no crashes, log errors or silently disabled features, theirs or ours (`runCompat`).
- Realistic Horse Genetics (user, 2026-11-07: "needs to be" compatible; it's Forge 1.20.1 only, hence that build): its horses
  ride as vanilla's, and its model gets what vanilla's does, at the horse's own size.

## Icy's Better Horses (user, 2026-10-31)

It reworks horses too, so with both installed the server picks whose riding horses use (user: the server, not each
player). On Icy's setting, this mod leaves horses alone.

On this mod's setting, the default:

- An Icy breed rides as a vanilla horse of its stats and passes the same tests (user, 2026-11-01). Icy's riding that would
  change that is off (gaits, auto-ride, swim boost, mid-ride speed swings); bond, tack, carts and models stay.
- Its speed abilities return on this mod's gaits and stamina (user, 2026-11-01, `BreedTraits`): the Quarter Horse's burst
  from a standstill, the Thoroughbred's faster gallop after 5 s flat out (tiring faster), the Arabian's endurance. Its
  charge is the trample, bashing hostile mobs as hard as Icy's.
- Its models are treated like an animation pack: the rider rides their measured motion, only this mod's lean banks them,
  the ride's legs, neck, tail and stirrups go on them, and the ride tilts on legs measured from the model.
- Its gear works (user, 2026-11-02): a cart's riders sit on its bench, left to Icy's pose. A cart turns as a real one does
  and the horse pulls it (`CartTrail`): wheels don't slide sideways, the shafts hold the horse close, so it turns wide, tighter
  the slower it goes (user), makes no hard cuts and starts and stops slower. Backing up, the rig comes round to where the rider looks and stops at
  walls. In a working Glidewing Harness no drop is refused and legs keep still in the air. In horseshoes powder snow is ground.
- A cart lies on its wheels (user, 2026-11-06): each rolls on the ground under it and the cart leans by them, across too. A
  wagon goes on its four, a two-wheeled cart on its two, so a horse's jump doesn't lift them; it tilts towards the hitch, only as
  far as a cart tilts on a hill (42 degrees), and the shafts reach for the horse below or above it. Three wheels down,
  the fourth stays in the air and the cart leans only slightly towards it; two a block below the other two lean it all the
  way. Rises and drops along the cart roll as if they had ramps on, up to two blocks, so it doesn't fly up a ledge (user).
  The body never goes into the ground: it comes up off what it would, and a wheel over a deep drop doesn't drag it down (user).
  The third-person view backs off so all of a wagon is in view (user).
- A cart has weight, not a rigid link or a float (user, 2026-11-06). Each corner rides on suspension: with nothing under it, it
  falls at a horse's gravity; landing, it compresses and settles, the wheel going up into the cart. The body follows the ground
  averaged along the cart, over more of it the faster it goes, so it rides over hills at speed instead of hopping at each block
  (user); the wheels take each block. Icy's wheel animation no longer jolts the bed or riders. The horse comes up against
  the shafts softly, and they give a little as it pulls or slows. The cart stands further back by the horse's size, so the
  rider isn't in its hindquarters (user). The shafts swing and stretch to stay on the horse whatever the cart does (user).
- Its jump clip poses the legs fore and aft only: across the body, it swung the hind legs sideways for a moment (user).
- Its free-look key shares Left Ctrl with sprint, so it does nothing here.
- Icy's mod has no switch for its riding, so the hooks reach into its code and miss quietly when it changes (`runCompat` catches it).

## Settings and the hat

- Settings are comfort and preference only (see `AGENTS.md`), reached from Mod Menu, the NeoForge Mods screen or a key, and
  should look polished and at home in Minecraft.
- The supporter cowboy hat (user, 2026-10-08) is for people who buy the user a coffee (https://buymeacoffee.com/blintzbug).
  `hats/` lists claimed supporters by UUID in `supporters.json`, which clients download (disclosed in the README). The
  colour goes through the server (`HatRelay`); clients draw hats only for players on their own copy of the list. While worn,
  head items aren't drawn but still protect. Shape: `art/cowboy_hat.bbmodel`, built into the code by `scripts/hat-model.py`.
