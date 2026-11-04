# Releasing

## Check the candidate

1. `./gradlew build auditMixins runGameTest`
2. `./gradlew runClientGameTestParallel --continue`
3. `./gradlew runCompat --continue`, then read `build/run/compat/<pack>/compat-findings.txt` and the reports.

## Publish

1. Bump `version` in `gradle.properties`.
2. Write an entry at the top of `CHANGELOG.md`.
3. Merge to main, then publish a GitHub release tagged `v<version>` with the same notes.

`.github/workflows/publish.yml` then builds every version, attaches the jars to the release and uploads each as its own version
(`gradle/publish.gradle`; notes from the changelog's top entry, which must be this version's). CurseForge (project 1758139, secret
`CURSEFORGE_TOKEN`) is the main store; for Modrinth, see `plan.md`.

Dry run: run the workflow by hand (Actions > publish), or locally `./gradlew publishModrinth publishCurseforge -PpublishDryRun`.
Ticking "curseforge" in the manual run uploads that branch's jars to CurseForge only, for a release that never reached it.

## Public text

- Changelog entries are for players and paste straight into store version pages: no internal names, no Red Dead.
- README: the pitch first, the note about AI last. The mention of Red Dead there is the author's own, in their own voice.
- Store descriptions come from the README (`scripts/store-descriptions.sh` writes them to `build/` for pasting). Media loads from
  `raw.githubusercontent.com/.../main/docs/media/`, so it shows only once merged to main.
