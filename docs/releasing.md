# Releasing

Release-readiness work (Modrinth, compatibility with other mods) and the release-candidate checklist are in
`development_plan.md`, Release.

1. Bump `version` in `gradle.properties`.
2. Write an entry at the top of `CHANGELOG.md`, for players: no internal names, no Red Dead, so it can be pasted
   straight into the Modrinth and CurseForge version pages. The GitHub release notes say the same.
3. Merge to main, then publish a GitHub release tagged `v<version>` with the same notes.

`.github/workflows/publish.yml` then builds every version, uploads each jar to Modrinth and CurseForge (project
1758139, secret `CURSEFORGE_TOKEN`) as a version of its own
(`gradle/publish.gradle`: game versions from `game_versions` in `versions/<version>/gradle.properties`, Fabric API
required, Mod Menu optional, notes from the changelog's top entry, which must be that version's) and attaches the jars to
the release. Running the workflow by hand (Actions > publish) is a dry run that uploads nothing; locally
`./gradlew publishModrinth publishCurseforge -PpublishDryRun`. Ticking "curseforge" there uploads that branch's jars to
CurseForge only, for a release that never reached it.
