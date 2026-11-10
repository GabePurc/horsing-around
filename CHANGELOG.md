# Changelog

Each version's notes are written to paste straight into the store version pages.

## 0.7.0

Horsing Around now runs on Forge for Minecraft 1.20.1 and works with Realistic Horse Genetics. Landings have weight behind them, riding is smoother all round, and the settings have a new screen.

### New
- **Forge.** Horsing Around now runs on Forge for Minecraft 1.20.1, with Forge 47.1.0 or newer. Open the settings from the Mods screen.
- **Works with Realistic Horse Genetics** (Forge, Minecraft 1.20.1). Its horses, donkeys and mules ride with this mod, keeping their own look, and their hooves find the ground at whatever size their genes give them.

### Changed
- **Landings:** your horse takes its weight as it touches down after a jump or a drop. Its body sinks onto its legs and comes back up without a bounce, and its hindquarters come down after its forelegs. Walking off a drop, it lands front first.
- **Smoother riding:**
  - Your horse gathers speed over a moment as it takes off, instead of leaving the ground all at once.
  - It tilts into slopes, bumps and jumps smoothly instead of snapping, and its nose no longer swings down in a single moment at the top of a standing jump.
  - Its legs no longer jump into a pose now and then.
  - You no longer sit up with a jolt at every jump at a gallop, and you sway more softly in the saddle.
- **Settings:** a new screen, drawn as a tack room, with the options in tabs: Camera, Comfort, Cowboy hat, and Compatibility when Icy's Better Horses is installed.
  - In a world, the Camera tab leaves the world clear behind it, so you see the camera move as you change it.
  - On the Cowboy hat tab, a swatch shows the colour you're mixing.

### Fixed
- **Held keys:** where your system repeats a held key, holding sprint no longer takes your horse up gait after gait, holding S no longer drops gaits as if tapped over and over, and holding jump no longer jumps again on every landing.
- **Drops,** on Minecraft 1.21.5 and newer: your horse minds your health as well as its own. It no longer walks off a drop that's harmless to it but would hurt you badly, and the lower your health, the smaller the drops it takes with you.
- **Jumping just after a landing:** if you press jump while your horse is still recovering from a landing, it no longer walks into danger or off a drop before it jumps.
- **A horse with no gravity** (from a command or another mod) climbs onto a ledge instead of getting stuck at its face.
- **Leads:** a lead tied to your horse stays on its neck, and one you hold stays in your hand, as your horse steps up and down, tilts on slopes and leans into turns. In first person, the end in your hand no longer jumps in your view on a step.
- **With Entity Culling:** a horse that moved while you weren't looking (a leashed horse pulled down a step behind you, say) no longer floats above the ground or stands sunk into it when you look back.
- **With a shader pack** (Iris, or Oculus on Forge), on Minecraft 1.21.11 or older: horses' legs fit stairs and slopes properly.
- **Riding over the shoulder,** on Minecraft older than 26.3: you hit and use what's under the crosshair, not a block beside it.
- **On Minecraft older than 1.21.5:** your horse no longer runs a little slow and jumps short.
- **On Minecraft older than 1.20.5:** your horse's hooves find steps and slopes whichever way the camera faces.
- **With Fresh Animations:**
  - On Minecraft 1.20.1 to 1.20.6, your horse's legs, tail and stirrups fit the ground, and you move with the pack's own gait.
  - On Minecraft 1.21.2 and 1.21.3, horses no longer float a little above the ground.

## 0.6.1

Fixes the crash on launch in 0.6.0 (Fabric, Minecraft 1.20.1 to 1.21.11), and makes carts from Icy's Better Horses feel much more real.

### Changed
- **Carts and wagons** (with Icy's Better Horses):
  - They tilt with the ground under their wheels, and roll up and down steps of one or two blocks instead of bouncing off them.
  - They feel heavy: they land softly after a drop and stay steady over hills, even at speed.
  - They stay properly hitched to the horse, sit a bit further back from big horses, and turn tighter at low speed.
  - Backing up with a cart is steady, and your horse stops if something blocks the cart.
  - When you drive a wagon, the camera pulls back far enough to show the whole wagon.
- **Camera:** riding in third person, glass behind you no longer pushes the camera in close. It now works the same as on foot.
- **Supporter hats:** a new supporter's hat appears without restarting the game.

### Fixed
- **Fabric, Minecraft 1.20.1 to 1.21.11:** the game no longer crashes on launch.
- **Fabric:** if your Fabric API is too old, the game now tells you so when it starts, instead of crashing later.
- **With Real Camera:** in first person, your view no longer dips into your horse when it steps up or down a block.
- **With Fresh Animations:**
  - You stay in the saddle when your horse rears, instead of floating behind it.
  - On Minecraft older than 1.21.5, the bridle, reins and stirrups stay in place on stairs, steps and jumps.
  - On Minecraft older than 1.21, your horse's head no longer pops up in front of your face on stairs.
- **With Icy's Better Horses:**
  - Your horse's back legs no longer kick out sideways when it jumps.
  - When a horse pulling a cart dies, the cart stays solid where you see it, instead of half a block away.
  - If a future Icy's update changes something this mod uses, only that part stops working, instead of the game crashing.
  - **Servers:** the settings file, `config/horsingaround.json`, is now created when the server first starts, so you can set `ridingStyle` without making the file yourself. A mistyped value shows a warning in the server log.

## 0.6.0

### New
- **NeoForge.** Horsing Around now runs on NeoForge too, for Minecraft 1.21.1, 26.1.2, 26.2 and 26.3.
- **Works with Icy's Better Horses.** With both installed, choose whose riding horses use under **Riding** in the settings. On a server, the server picks (`ridingStyle` in its `config/horsingaround.json`).
  - **Horsing Around** (the default): every breed rides with this mod's weight, gaits and stamina, and stands on its own legs on slopes. Icy's bond, tack, carts, combat and rearing all still work; its own gaits, auto-ride and swim boost step aside.
  - **Icy's Better Horses**: Icy's own riding, and this mod leaves horses alone.
- With Horsing Around's riding on Icy's breeds:
  - The Quarter Horse picks up speed twice as fast from a standstill, the Thoroughbred finds an extra turn of speed after a few seconds flat out on level ground (and tires faster while it lasts), and the Arabian tires more slowly and recovers faster. A strong bond makes each stronger.
  - Trampling bashes hostile mobs of any size as hard as Icy's charge would, and the Toggle Horse Charge key turns it off.
  - A Glidewing Harness makes any drop safe: your horse gallops off cliffs and jumps gaps it would otherwise refuse.
  - Horseshoes carry a horse over powder snow instead of sinking in it.
  - Carts roll like real carts: they follow behind, cut inside bends and stay put while the horse turns on the spot. The horse in the shafts feels the cart's weight, turns wide, and draws it smoothly up and down slopes.

### Changed
- Jumps lean back a little less on takeoff.
- Hooves lift ahead of higher ground and set down softly, so walking up and down steps looks smooth.

### Fixed
- On Minecraft 1.21.1, galloping across a short dip no longer rubber-bands your horse back on a server.
- Your rider rises with a rearing horse instead of its back coming up through them (with Fresh Animations or Icy's Better Horses).
- Legs keep moving in worlds that have been running for a very long time.
- Legs no longer flutter when your horse drops past a slope.
- Galloping up a ragged hillside, your horse rides it as one slope instead of nodding into each step.
- Your horse's body no longer jumps up after climbing two blocks.
- Over the shoulder, the crosshair never targets the horse you're riding.

## 0.5.2

### Changed
- Small performance improvements: horses take a little less work to draw each frame, and the checks of the ground under their hooves are quicker. Riding looks and feels exactly the same.
- With over 50 horses around you while you gallop, the mod uses under 1% of each frame.

## 0.5.1

### Changed
- The supporter cowboy hat has a new look: a creased crown, a dark band and a brim that curls up at the sides. Every hat colour works as before.

## 0.5.0

### New
- **Over-the-shoulder riding camera.** In third person on horseback, the camera now sits just off your right shoulder with a crosshair in the middle of the screen: arrows, swings and anything you throw go where it points. Draw a bow or raise a spyglass and it leans in for the shot. Press **O** to swap shoulders, or choose right, left or centred under **Over the shoulder** in the settings. On foot, third person is Minecraft's own.

### Changed
- Over the shoulder, your view stays where you leave it, since that's where you're aiming. The centred camera still eases back to a riding angle when you leave the mouse alone.
- After a wall pushes the riding camera in, it now glides back out instead of jumping.
- Horsing Around: Over the Shoulder is now built in. Remove it from your mods folder: the game won't start with both installed.

## 0.4.0

### New
- **Double-tap W to ride on without holding it.** Your horse steps up a gait, just like tapping sprint, and keeps going at that pace with W let go. Double-tap again or tap sprint to go faster. Press S to take back control: let go and it eases to a stop, or hold S to pull up. Sprint works exactly as before.

## 0.3.2

### Fixed
- On Minecraft 1.21.4 and older, your horse no longer takes drops that hurt it or you. It now judges falls by those versions' own fall damage, where a harmless drop is 6 blocks, not 8.
- A horse standing on a magma block can walk off it again.
- Getting off mid-jump, mid-climb or mid-hurdle no longer leaves your horse stuck in it, and a hurdle that lands in deep water stops pushing the horse along.
- A horse with no speed (Slowness, or a changed speed attribute) no longer breaks its stride, braking or trampling.
- Leaving your horse in a gap only a ridden horse fits through no longer squeezes it into the walls.
- Galloping drains stamina only when you're going faster than a canter, and swimming tires the horse at the swimming rate.
- The riding camera no longer moves your view while the game is paused, or while you aim a bow or look through a spyglass. The spyglass keeps its normal zoom on horseback.
- Sprint, jump and back presses are no longer swallowed while you're on foot.
- Trampling damage stays even on a laggy server.
- With an animation pack at a low frame rate, the horse's animation no longer plays twice as fast. Foals on 1.21.1 and older now get the new leg movement too.
- The settings file is saved safely, and a broken one is kept as `horsingaround.json.bad` instead of being lost. The hat colour sliders keep their hue and richness, and you can pick a colour swatch with the keyboard.
- The 1.20.2, 1.21.4 and 1.21.5 downloads no longer claim to work on later patch versions they weren't built for.
- Servers are protected against a client flooding them with hat changes.

## 0.3.1

### Fixed
- The mod no longer refuses to start with "Incompatible mods found" on an older Fabric Loader. It now needs only the Fabric Loader your version's Fabric API needs (0.14.25 or newer on 1.20.1 and 1.20.2, 0.15 on 1.20.4 to 1.21.1), so it works in modpacks that ship an older loader, such as Fabulously Optimized on 1.20.2, 1.20.4 and 1.21.3.
- On Minecraft 1.20.1 to 1.21.11 the game no longer crashes the moment the riding camera starts.
- On Minecraft 1.20.1 to 1.21.1, horses nobody is riding no longer have their legs drift off into the air after you've ridden another horse, and a horse's head no longer stays tilted after a head toss (with the plain horse model, without an animation pack).

## 0.3.0

### New
- **Every Minecraft version from 1.20.1 to 26.3.** Horsing Around now comes in a download for each: 1.20–1.20.1, 1.20.2, 1.20.3–1.20.4, 1.20.5–1.20.6, 1.21–1.21.1, 1.21.2–1.21.3, 1.21.4, 1.21.5, 1.21.6–1.21.8, 1.21.9–1.21.10, 1.21.11, 26.1–26.1.2, 26.2 and 26.3. Pick the one for your game.
- On older versions a few things are missing because the game doesn't have them yet: no spear stab before spears exist (1.21.10 and older), and on 1.20.6 and older the rider follows the horse's own gait rather than a Fresh Animations horse's animation.

### Fixed
- With a version of Shoulder Surfing Reloaded the mod doesn't know, the riding camera now carries on without it instead of crashing.

## 0.2.1

### Fixed
- Galloping through a forest, your horse no longer looks like it hops up on top of bushes. It pushes through them with its hooves on the ground.
- Landing a drop on a steep mountainside no longer snaps your horse's body into its tilt. It leans into the slope smoothly.
- Riding up against a wall or a tall ledge, your horse no longer rears its front up the face of it.
- Hooves no longer dip into the ground when your horse lands a jump or climbs out of the water onto a bank.

## 0.2.0

### Fixed
- **Crash with shulker boxes.** With Entity Model Features and a resource pack that animates shulker boxes, having a shulker box in your hotbar or hand crashed the game.
- Jumping at a wall no longer gets you a snort and a refusal.
- Mashing jump at a 2-block ledge no longer makes your horse hop into the face of it. It waits for the climb and settles on top before it'll jump again.
- The stamina bar shows up with HUD mods that take over the jump bar's spot, like Better Mount HUD.

### New
- **Fences, walls and gates.** Ride at one (or press jump in front of it) and your horse clears it, at any pace or from a standstill. It won't go over if there's lava or a big drop on the other side, and pens still keep horses in.
- **Hooves on the ground.** On slopes, stairs and steps every hoof stands on the ground under it. Your horse steps up and down smoothly, sinks a little as it lands a jump and comes back up, and stops putting its chest into steps and its hooves into the ground.
- **A real heave up 2-block ledges.** Your horse gathers itself, pushes off and lands on top, instead of popping straight up.
- Climbing, your horse stretches its neck forward, and you and your horse stop clipping into each other.
- With Fresh Animations, horse armour and the bridle follow the head.

### Camera
- The riding camera sits a bit lower, and there's a new **Camera height** setting.
- With the Over the Shoulder add-on installed, the add-on has the third-person view all to itself.

### Good to know
- Works with Over the Shoulder 0.1.0.
- Same as before: install it on the server and on every player's game.

## 0.1.0

First test build.
