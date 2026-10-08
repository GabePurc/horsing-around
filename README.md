# Horsing Around

> Your horse is not a boat with legs.

[Buy me a coffee](https://buymeacoffee.com/blintzbug) ☕

I've always loved the horses in Red Dead Redemption 2. The weight of them. The way a gallop builds and builds, the way
they lean into a turn, the way they flat-out refuse to throw themselves off a cliff just because you asked nicely. Then
I'd climb onto a horse in Minecraft and it would turn on a dime, stop on a dime, and walk face-first into the nearest oak
tree.

So I made this. Horsing Around makes riding in Minecraft feel like riding an actual animal, while still feeling like
Minecraft: no new items, no new keybinds, no new clutter on your screen. Same horse, same keys. It just has weight now.
And opinions.

## What it feels like

**It builds up.** Walk, trot, canter, gallop. Tap sprint to ask for more, tap S to ease back. Let go of W and your horse
rolls to a stop instead of slamming into invisible brakes. Hold S and it pulls up hard, then backs up. Every horse's own
speed still matters, so the champion you spent all week breeding is still the fastest one in the stable.

**It turns like it weighs half a ton.** Because it does. Your horse goes where you look, but at a gallop it swings wide
and at a walk it pivots in place. Look way off to the side and it sits back on its haunches and cuts round hard. Hold W
with A or D to ride at an angle to where you're looking, or A or D alone to ride straight across your view. Standing
still? Look around all you want. The horse stays put.

**It gets tired.** Galloping burns stamina, and the jump bar turns into a stamina bar. Push too hard and your horse drops
to a canter, blowing hard, until it gets its wind back. Pace yourself, partner.

**It jumps when you say jump.** Press the button, it jumps. No charging the bar. It carries all its speed into the air,
tucks its legs up, and reaches for the ground on the way down.

**It has a mind of its own (a little).** From a trot it weaves around trees and rocks, slows down for walls, and will
absolutely not gallop off a cliff or into lava. It'll snort and toss its head at you for even suggesting it. Ride
straight at a ledge up to two blocks high and it hops right up without breaking stride. Fences are still fences, though.
No cheating.

**It looks alive.** Horse and rider lean into every turn. Your rider actually sits the saddle: feet in the stirrups, hands
on the reins, rocking with the gait, folding over the neck on a jump, throwing a hand up to shield their face when you
crash through leaves. The hoofbeats change with the gait, so a gallop finally sounds like a gallop.

**The world pushes back.** Ride through leaves instead of getting wedged into every tree (it slows you down, branches
are branches). Wade into a river, swim across with your horse's head above the water, and haul yourself up the bank on
the far side. Gallop through a flock of chickens and... well, try not to. Players and your own pets are always safe.
Skeleton horses still stroll along the bottom of lakes like the spooky little guys they are.

**The camera rides along.** Climb on and the view swings into third person, sits a bit higher, pulls back as you pick up
speed, and smooths out the bumps. Prefer first person? The view gently moves with the saddle.

## Which mounts?

Horses, donkeys, mules, skeleton horses and zombie horses all get the full treatment. Camels, llamas, pigs, striders,
happy ghasts and nautiluses ride exactly the way they always have. I didn't touch them. And yes, zombie and skeleton
horsemen still saddle up and come for you.

Making a horse mod of your own? Add your horse to the entity type tag `horsingaround:managed` and it gets the new riding
too.

## Riding with friends

I built this with my family's server in mind. Put Horsing Around (and Fabric API) on the server and in everyone's game,
and you're off. Friends without the mod can still join and ride like normal. Hop onto a server that doesn't have it and
the mod politely steps aside, and lets you know the first time you climb onto a horse.

## Make it comfortable

Every horse rides the same for everyone, the way I tuned it. What you can change is how riding looks and sounds to
you, in [Mod Menu](https://modrinth.com/mod/modmenu) or with the "Open horse settings" key: whether the camera switches
to third person when you mount, how far back it sits, how much the view widens at speed, how much your view and hands
move with the stride in first person, and how loud the horse's breathing and snorting are. If fast motion bothers you,
you can turn all the movement right down.

## Plays nice with others

I built and tested this with [Fresh Animations](https://modrinth.com/resourcepack/fresh-animations) (Entity Model
Features + Entity Texture Features), because that's how I play. Your rider follows every bounce of the animated horse,
and it looks fantastic.

I also put it through automated test rides alongside a big pile of popular mods: Sodium, Iris, Lithium, C2ME,
FerriteCore, ImmediatelyFast, Entity Culling, More Culling, Not Enough Animations, Emotecraft, Better Combat,
First-person Model, Shoulder Surfing Reloaded, Camera Overhaul, Freecam, Do a Barrel Roll, AppleSkin, Raised, Jade,
Xaero's Minimap, Horse Expert, Horseman, Bareback Horse Riding and Mounts Stay Still. When another mod also changes how
horses ride (like Horseman's free camera) or how riders sit (like Not Enough Animations), Horsing Around takes the reins
on its own horses and leaves the rest of that mod alone. Shoulder Surfing gets the camera whenever it's switched on.

Found something that doesn't get along? [Open an issue](https://github.com/GabePurc/horsing-around/issues) and I'll
take a look.

## What's next

Horse perks and leveling are coming, so the horse that's carried you across the whole map is better than one you tamed
yesterday. This is my first public version, and I'm only getting started.

## Want a better camera on foot too?

Check out **Horsing Around: Over the Shoulder**, an over-the-shoulder camera for getting around on foot that also takes
over the riding camera when both are installed.

## Buy me a coffee? (and get a hat)

I make this in my spare time because I love it, and it'll always be free. If it's made your rides a little better and
you'd like to chip in, you can [buy me a coffee](https://buymeacoffee.com/blintzbug). It genuinely keeps me going, and
it means more horse features, sooner. No pressure at all, though: telling your friends about it helps just as much.

As a thank-you, supporters get a **cowboy hat** to wear in game. Put your Minecraft username in your coffee message and
within a day or two it'll appear in your horse settings: switch it on, pick any colour you like, and ride off into the
sunset. Everyone with the mod on your server sees it. It's purely cosmetic (your helmet still protects you, it's just
hidden under the hat), and it never gives anyone an advantage.

## Privacy

When the game starts, the mod downloads the list of supporters (`supporters.json` in this repository) from GitHub so it
knows who gets a hat. That's a plain download: nothing about you is sent anywhere. If it can't connect, it uses the last
copy it saw.

## Requirements

Minecraft 26.3, Fabric Loader 0.19.5 or newer, and Fabric API. Mod Menu is optional.

## License

[MIT](LICENSE). Take it apart, learn from it, build on it.
