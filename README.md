# Horsing Around

Horse riding that feels like riding a horse. Inspired by Red Dead Redemption 2, kept close to vanilla: no new items,
no new keys, no new HUD panels. Your horse has weight, momentum, a gait, a stamina bar and a mind of its own.

## What changes

**Gaits and momentum.** Walk, trot, canter and gallop. Tap sprint to ask for the next gait and S to drop one. Let go of
W and the horse eases to a stop; hold S and it pulls up hard, then backs up. Speeds come from each horse's own speed
stat, so breeding still matters.

**Steering with weight.** The horse heads where you look, turning at a rate that depends on its speed: a gallop turn is
wide, a walk turn is tight. Turn your view far off and it sits back on its haunches and cuts round. W with A or D rides
45 degrees off your view, A or D alone rides across it, so you can look one way and ride another. Standing still you can
look around freely.

**Stamina.** Galloping tires the horse. The jump bar becomes its stamina bar; run it dry and the horse drops to a canter,
blowing hard, until it gets its wind back.

**Jumping.** Jumps happen when you press jump, no charging, and carry the horse's speed into the air. The legs fold up
and reach for the ground like a real jump.

**A horse with a say.** From a trot it picks its way round trees and rocks, slows for walls, and refuses to run off a
drop that would hurt it or into lava, fire or cactus (with a snort and a toss of the head). Ride straight at a ledge up to
two blocks high and it jumps up in its stride. Fences stay fences.

**Body language.** Horse and rider lean into turns. The rider sits the saddle through every gait, feet in the stirrups
and hands on the reins, sways with surges, folds forward over jumps, and puts a hand up to push through leaves. Hoof
sounds match the real gait.

**Out in the world.** Ride through leaves (slower) instead of getting stuck in every tree. Wade, swim with the head
above water, and climb out up a bank. Gallop through small animals and they get trampled (never players or your pets).
Skeleton horses still walk along the bottom of lakes, as in vanilla.

**Riding camera.** Mounting switches to a third-person camera that sits a little higher, pulls back as you speed up,
and smooths out the bumps. Prefer first person? The view gently follows the saddle.

## Mounts

Horses, donkeys, mules, skeleton horses and zombie horses get the new riding. Camels, llamas, pigs, striders, happy
ghasts and nautiluses ride exactly as in vanilla. Zombie and skeleton horsemen still ride their horses.

Other mods' horses can join in through the entity type tag `horsingaround:managed`.

## Multiplayer

Install it on the server and on each player's game. Riding is simulated on your own game and checked by the server, so
both need the mod for it to switch on; players without it can still join and ride as in vanilla. On a server that
doesn't have the mod, it switches itself off and tells you so the first time you mount up.

## Settings

Everything is adjustable in game through [Mod Menu](https://modrinth.com/mod/modmenu) (or an "Open horse settings" key
you can bind): speed, acceleration, turning, stamina, jump height, camera, lean, leaves and trampling, sounds. Settings
are saved in `config/horsingaround.json`. On a server, the server's own settings decide whether horses ride through
leaves and trample.

## Compatibility

Made for and tested with [Fresh Animations](https://modrinth.com/resourcepack/fresh-animations) (Entity Model
Features + Entity Texture Features): the rider follows the animated horse. Tested alongside Sodium, Iris, Lithium,
C2ME, FerriteCore, ImmediatelyFast, Entity Culling, More Culling, Not Enough Animations, Emotecraft, Better Combat,
First-person Model, Shoulder Surfing Reloaded (its camera takes over while it's on), Camera Overhaul, Freecam,
Do a Barrel Roll, AppleSkin, Raised, Jade, Xaero's Minimap, Horse Expert, Horseman, Bareback Horse Riding and Mounts
Stay Still. Where another mod also changes how horses ride or how riders sit (Horseman's free camera, Not Enough
Animations' riding pose), this mod's riding wins on the horses it rides; everything else those mods do keeps working.

Want an over-the-shoulder camera? See the add-on **Horsing Around: Over the Shoulder**.

## Requirements

Minecraft 26.3, Fabric Loader 0.19.5 or newer, and Fabric API. Mod Menu is optional.

## License

[MIT](LICENSE)
