<div align="center">

<img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/banner.png" alt="Horsing Around" width="100%">

<a href="https://buymeacoffee.com/blintzbug"><img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/button-coffee.png" alt="Buy me a coffee" height="40"></a>
<a href="https://modrinth.com/mod/fabric-api"><img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/button-fabric-api.png" alt="Requires Fabric API" height="40"></a>
<a href="https://github.com/GabePurc/horsing-around"><img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/button-source.png" alt="Source on GitHub" height="40"></a>
<a href="https://github.com/GabePurc/horsing-around/issues/new?template=bug_report.yml"><img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/button-bug.png" alt="Report a bug" height="40"></a>

</div>

I've always loved the horses in Red Dead Redemption 2, and riding in Minecraft never felt anything like them. This mod
is my go at changing that without leaving vanilla behind: same horses, same keys, nothing new on your screen. They just
move like animals now.

> **How it's made:** the code is written by AI, but every decision is made by me, a person partway through a computer
> science degree. I decide what the mod does, how each part of it should feel, and when it's good enough to release.
> This is not just a one shot prompt AI slop project, I am putting effort into making it as good as it can be, and AI is
> a great tool for doing exactly that.

### Gaits

Walk, trot, canter and gallop. Speed builds up and fades out instead of switching on and off, and a well-bred horse
still outruns the rest.

<img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/gaits.webp" alt="A horse picking up from a walk to a trot, a canter and a gallop" width="100%">

### Turning

The horse goes where you look. At a gallop it swings wide, at a walk it turns on the spot, and if you look hard to one
side it sits back and cuts round.

<img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/turn.webp" alt="Galloping through wide turns, horse and rider leaning in" width="100%">

### Stamina

Galloping uses stamina, shown where the jump bar used to be. Run it out and the horse drops to a canter, breathing hard,
until it gets its wind back.

<img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/stamina.webp" alt="Galloping the stamina bar down until the horse drops to a canter" width="100%">

### Jumping

Press jump and it jumps. There's no bar to charge, and it keeps its speed through the air.

<img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/jump.webp" alt="A galloping horse jumping, legs tucked" width="100%">

### Footing

From a trot it steers round trees and rocks and slows down for walls. It won't take a drop that would hurt it or walk
into lava, however hard you push. Ledges up to two blocks high it climbs. Fences you'll have to jump.

<img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/cliff.webp" alt="A galloping horse refusing to go over a cliff edge" width="100%">

### Water and trees

Horses swim with their heads up and climb out onto the far bank. Leaves slow you down instead of stopping you dead. At
speed you'll trample chickens and other small animals, but never players or your own pets.

<img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/river.webp" alt="Swimming a horse across a river and climbing out the far bank" width="100%">

### The rider

Horse and rider lean into turns. The rider keeps their feet in the stirrups and hands on the reins, folds forward over
a jump, draws a bow like an archer and puts an arm up when you crash through leaves. The hoofbeats change with the gait.

<img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/rider.webp" alt="Close on the rider at a canter, leaning into a turn and drawing a bow" width="100%">

### Camera

Getting on switches you to third person, with the camera just off your right shoulder and a crosshair in the middle
of the screen: arrows, swings and anything you throw go where it points. Draw a bow or raise a spyglass and the camera
leans in for the shot. Press O to swap shoulders, or centre the camera in the settings. It pulls back as you speed up
and smooths out the bumps. On foot, third person is Minecraft's own. In first person the view moves gently with the
saddle.

<img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/camera.webp" alt="Riding over the shoulder: an arrow into a target, a swap to the other shoulder, then first person" width="100%">

## Controls

No new keybinds.

| Key | On horseback |
| --- | --- |
| W | Walk on. Let go and the horse slows to a stop. |
| Sprint | Tap for the next gait up. |
| W, twice | Next gait up, and the horse keeps going with W let go until you press S. |
| S | Tap for the next gait down. Hold to stop hard, then back up. |
| Mouse | Steer. Standing still, you can look around and the horse stays put. |
| A / D | With W, ride at an angle to where you're looking. Alone, ride straight across it. |
| Jump | Jump, straight away. |
| O | Swap the camera to your other shoulder. |

## Which mounts

Horses, donkeys, mules, skeleton horses and zombie horses. Camels, llamas, pigs, striders, happy ghasts and nautiluses
ride exactly as they did before.

Making a horse mod? Add your horse to the entity type tag `horsingaround:managed` and it rides this way too.

## Servers

I made this for my family's server. Put it (and Fabric API) on the server and in each player's game. Players without
it can still join and ride as normal. On a server that doesn't have it, your horse rides like vanilla and the mod tells
you so the first time you get on.

## Settings

Horses ride the same for everyone. What you can change is how it looks and sounds to you: whether getting on switches to
third person, which shoulder the camera sits over (or centred), how far back and how high it sits, how much the view widens at speed, how much the first-person view sways, and how
loud the horse's breathing and snorting are. If motion bothers you, all of it turns down. Open the settings from
[Mod Menu](https://modrinth.com/mod/modmenu) or with the "Open horse settings" key.

<img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/settings.webp" alt="Moving the camera from the horse settings while the world carries on behind" width="100%">

## Other mods

I play with [Fresh Animations](https://modrinth.com/resourcepack/fresh-animations) (Entity Model Features + Entity
Texture Features), so that's what I test against first, and the rider follows the animated horse. If another mod also
changes how horses ride (Horseman's free camera) or how riders sit (Not Enough Animations), Horsing Around handles its
own horses and leaves the rest of that mod alone. Shoulder Surfing takes over the camera whenever it's on.

<details>
<summary><b>Every mod it's tested with</b></summary>

Automated test rides run alongside Sodium, Iris, Lithium, C2ME, FerriteCore, ImmediatelyFast, Entity Culling, More
Culling, Not Enough Animations, Emotecraft, Better Combat, First-person Model, Shoulder Surfing Reloaded, Camera
Overhaul, Freecam, Do a Barrel Roll, AppleSkin, Raised, Better Mount HUD, Jade, Xaero's Minimap, Horse Expert, Horseman,
Bareback Horse Riding and Mounts Stay Still.

</details>

Something not getting along? [Open an issue](https://github.com/GabePurc/horsing-around/issues/new?template=bug_report.yml).

## Requirements

Minecraft 1.20.1 to 26.3 (pick the file for your version), Fabric Loader 0.14.25 or newer (or whatever your Fabric API
needs), and Fabric API. Mod Menu is optional.

## Buy me a coffee, get a hat

The mod is free and will stay that way. If you'd like to support it, you can
[buy me a coffee](https://buymeacoffee.com/blintzbug), and you'll get a cowboy hat in game. Put your Minecraft username
in the coffee message and within a day or two it shows up in your horse settings, in any colour you like. Everyone on
your server who has the mod sees it. It's only cosmetic: your helmet is hidden under it but still protects you.

<div align="center">

<img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/hat.webp" alt="A rider in the supporters' cowboy hat at a canter" width="100%">

<a href="https://buymeacoffee.com/blintzbug"><img src="https://raw.githubusercontent.com/GabePurc/horsing-around/main/docs/media/button-coffee.png" alt="Buy me a coffee" height="40"></a>

</div>

## Privacy

At startup the mod downloads the supporter list (`supporters.json` in this repository) from GitHub to know who gets a
hat. Nothing about you is sent. If it can't connect, it uses the last list it saw.

## What's next

Horse perks and levelling, so the horse that's carried you across the map is better than one you tamed yesterday.

## License

[MIT](https://github.com/GabePurc/horsing-around/blob/main/LICENSE)
