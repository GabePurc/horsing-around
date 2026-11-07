# Horsing Around

A Minecraft mod (Fabric and NeoForge 1.20.1 to 26.3, Forge 1.20.1, Mojang names) that makes horse riding feel weighty and
alive. Red Dead Redemption 2 is the feel target; vanilla is the style boundary. The user guides the feel; Claude is the sole
developer.

## How to decide

What counts, in order: the user's latest direction; what the game actually does (play it, test it, measure it); the docs.

The docs record what the user asked for and why, not how it must be done. Intent ("the horse must never become an
annoyance", "stay close to vanilla") is the target. Numbers, mechanisms, test targets and known limits are snapshots:
the code is the truth, and a better way is welcome.

- Something wrong (a bug, a bad feel, a fragile or slow approach, a test checking the wrong thing)? Fix it, even if a doc
  calls it intended, then correct the doc.
- Innovate without asking. Ask first only if a change would reverse an explicit user decision (marked "user" in the
  docs), noticeably change the feel, or widen scope (a new feature or system). Say what you'd change and why.

## Firm rules

Everything else here is guidance; these hold because the user asked for them or because breaking them breaks players.

- Nothing that changes how horses ride is a setting (user, 2026-10-08). Settings (`HorseConfig`) are comfort and
  preference only: camera, first-person motion, sound volume. One exception (user, 2026-10-31): with Icy's Better Horses
  installed, the server picks whose riding horses use (`ride/IcyCompat`).
- Per-tick paths allocate nothing beyond what vanilla does, and do no work for horses nobody is riding.
- Every version keeps building and passing its audit, on any machine: no machine-specific paths in the repo (a local JDK
  goes in `~/.gradle/gradle.properties`).
- The supporter hat (user, 2026-10-08) stays cosmetic: never an item or a gameplay advantage. Mojang's usage guidelines
  allow cosmetics, not capes. `hats/` adds supporters by itself; to add one by hand, run `supporters.yml`.
- Public text (README, changelog, store descriptions) follows `docs/releasing.md`.
- Every change, however small, starts from a GitHub issue (user, 2026-11-06): reuse one that fits or open one, assigned to
  `@me`. Branch as `<n>-<slug>` from a just-fetched origin/main, not local main; in an app worktree, sync with main first.
  The PR says `Closes #<n>`.

## Docs

Read only what the task needs, and only the relevant section (`grep -n '^## ' <file>`).

| File | What it is | When |
| --- | --- | --- |
| `docs/design.md` | What each part should feel like, and how it works now | Before design-sensitive work |
| `docs/design-log.md` | Why: requests, references, what was tried and dropped | Before reworking an area |
| `docs/plan.md` | Open work, ideas, known issues | Picking up work; add or remove items as they change |
| `docs/testing.md` | Every test, flag and how to read results | Running or changing tests |
| `docs/porting.md` | Version-specific code | Before touching it |
| `docs/releasing.md` | Release steps, rules for public text | Before a release or public text |

## Keeping docs cheap (user, 2026-11-04)

Docs exist to save time and credits. A line that costs more to read than it saves gets cut, and this is part of finishing
any change.

- Change a doc only if something in it stopped being true, the user gave a new direction, or a lesson would otherwise be
  learned twice. Prefer replacing or deleting to adding. No logs of what was done: that's git and the PR.
- Say a thing once, in one doc, and point to it. Leave out what the code, tests or git answer in one search.
- Hit something stale, wrong or bloated in a doc while working? Fix it in the same change.
- `scripts/check-docs.sh` (also in CI) enforces the file list, size budgets and dead references. Over budget means cut.

## Code

Feel numbers: `ride/RideTuning`, so tuning stays in one file. Prefer one mixin per vanilla class, with the logic in plain
classes. Don't read the big files whole (`RideFeelTest`, `Awareness`, `RideController`, `RideTuning`): search for the name,
then read that range.

- `src/main/.../ride/`: the ride itself (`RideController`, `Awareness` for the horse's look-ahead and planning, `Footing`, `Mounts`).
- `src/main/.../net/`: payloads. `src/client/.../render/`: legs, rider and body posing. `src/client/.../cosmetic/`: the hat.
- `src/gametest/`, `src/smoke/`: tests.

## Build and test

All versions build from the one `src/` with Stonecutter. `src/` reads as 26.3 code (Java 25); older versions' code sits in
`//?` comments. Gradle picks the JDK. Jars land in `versions/<version>/build/libs/`.

While developing (user, 2026-11-05), test only what you're changing: `./gradlew :26.3:build`, then `./gradlew runClientGameTest`
narrowed with `-Ptests=`/`-Psections=`, or `./gradlew :<version>:runSmoke` for version-specific code.

Before merging, run the real tests:

- `./gradlew build auditMixins` (every jar in a production client and server).
- `./gradlew runClientGameTestParallel --continue`.
- Version-specific code changed: `./gradlew :<version>:runSmoke`.
- Mixins changed: `./gradlew runCompat --continue`.

Play: `./gradlew :26.3:runClient` (plain `runClient` matches every version and fails). Dev runs load Mod
Menu, Entity Model Features, Entity Texture Features and Fresh Animations, the user's target setup.

Don't start a second test game yourself: two games at once make ride checks flaky. Keep test output out of the conversation:
send a run to a file, read its tail, then the failures (`docs/testing.md`). View only the screenshots that matter.
