# Changelog

Each version's notes are written to paste straight into the Modrinth version page.

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
