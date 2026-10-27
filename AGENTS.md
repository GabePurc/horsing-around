# Horsing Around

Fabric mod for Minecraft 1.20.1 to 26.3 (Mojang names) that makes horse riding feel weighty and alive, inspired by
Red Dead Redemption 2 while staying close to vanilla.

The user guides the *feel*; Claude is the sole developer. Feel numbers live in
`src/main/java/dev/horsingaround/ride/RideTuning.java` so tuning passes stay in one file.

## Docs, and reading them cheaply

Read only what the task needs, and only the relevant section (`grep -n '^## ' <file>`, then `offset`/`limit`):

- `research.md`: the design in short (what each part should feel like, and how it works now). Read it before
  design-sensitive work.
- `docs/design-log.md`: the full history behind it, per request. Only the section you need, never whole.
- `completed_steps.md`: a short list of what exists, by area. Add one bullet (two lines at most) when work is finished.
- `development_plan.md`: open work, ideas not built yet, the release checklist.
- `mvp_requirements.md`: the original MVP scope (shipped; a record).
- `docs/testing.md`: every test, flag and how to read results. `docs/porting.md`: before version-specific code.
  `docs/releasing.md`: before a release.

Keep them short as they grow: a doc entry says what and why in a few lines; measurements, causes and step-by-step
detail go in the PR (the record of each change) or a code comment. Don't copy the same detail into several docs.

## How to use these docs (user direction, 2026-10-14)

They record what the user asked for and why, and how it was built at the time, not a cage. Most were written before
the code they describe existed.

- What counts, in order: the user's latest direction; what the game actually does (play it, run the tests, measure);
  then the docs. The user's stated intent ("the horse must never become an annoyance", "stay close to vanilla") is the
  design target. Numbers, mechanisms, test targets and "known limits" are snapshots of one way it was done: the code
  (`RideTuning` for numbers) is the source of truth, and a better way is welcome.
- If something in the mod is wrong (a bug, a bad feel, a fragile or slow approach, a test that checks the wrong
  thing), fix it, even when a doc describes the current behaviour as intended, then update the doc. A doc that
  contradicts the code or a later direction is stale: correct it, don't follow it.
- Innovate: a new approach that serves the user's intent better is fine without asking. Ask the user only when a change
  would reverse something they explicitly decided (marked "user direction" or "user decision"), would change the feel
  noticeably, or widens scope (a new feature or system). Say what you'd change and why.

Firm rules, because the user asked for them or because breaking them breaks players:

- Nothing that changes how horses ride is a setting (user direction, 2026-10-08). Player settings (`HorseConfig`, JSON
  in the config folder, vanilla-style screen from Mod Menu, an optional dependency) are only comfort and preference:
  camera, first-person motion, sound volume.
- Per-tick paths must not allocate beyond what vanilla already does, and must not do work for horses that are not
  being ridden. Prefer one mixin per vanilla class; keep logic in plain classes under `ride/`.
- Every version keeps building and passing its audit (below). Builds work on any machine: never commit
  machine-specific paths (local JDK locations go in `~/.gradle/gradle.properties`).
- The supporter hat stays cosmetic (below). Public text (README, changelog, mod descriptions) follows
  `docs/releasing.md`.

Everything else is guidance.

## Build and versions

Every supported Minecraft version (1.20.1 to 26.3, one jar per group of versions) builds from the one `src/` with
Stonecutter; `src/` reads as 26.3 code (Java 25), older versions' code sits in `//?` comments. A change to shared code
must still build (`./gradlew build`) and pass `./gradlew auditMixins` (every version's jar in a production client and
server). Jars land in `versions/<version>/build/libs/`. Gradle picks JDK 25 through `gradle/gradle-daemon-jvm.properties`.

Play-test: `./gradlew :26.3:runClient` (`./gradlew :<version>:runClient` for another). Dev runs load Mod Menu, Entity
Model Features, Entity Texture Features and the Fresh Animations pack (the user's target setup), and on 26.3 the dev tools
(`src/devtools`): F7 saves the last 30 s of riding, F8 draws the plain model's leg bones over the horses, and hitches
are logged to `run/devtools/` (see `docs/testing.md`).

## Testing

Verify every gameplay change with the client tests (26.3): `./gradlew runClientGameTest`, narrowed with
`-Ptests=...`/`-Psections=...` while iterating, and `./gradlew runClientGameTestParallel --continue` for full runs (don't
start a second test game yourself). Touching version-specific code: `./gradlew :<version>:runSmoke`. Touching mixins or
before a release: `./gradlew runCompat --continue`. Every flag and how to read results: `docs/testing.md`. Read
reports' failures and summaries rather than whole logs, and view only the screenshots that matter.

The mod is meant for servers that have it installed; on a server without it the client rides vanilla.

## Supporter cowboy hat (user request, 2026-10-08)

A cosmetic for people who buy the user a coffee (https://buymeacoffee.com/blintzbug), never an item and never a
gameplay advantage (Mojang's usage guidelines allow cosmetics, not capes). Supporters are listed by Minecraft UUID in
`supporters.json` at the repo root, which every client downloads at startup (disclosed in the README); add one through a
PR when the user asks. The hat colour goes through the server (`HatRelay`); clients only draw hats for players on their
own copy of the list. While the hat is on, the player's helmet and head items aren't drawn (still worn). Code:
`client/cosmetic/`, `net/HatPayload`, `HatTest`.
