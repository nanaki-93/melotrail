# Task 063 — Full Melotrail Rename and Migration

## Goal

Rename the entire local product and codebase from AI Music Workstation /
Personal AI Music Arranger to **Melotrail**, including the Kotlin namespace and
local repository directory, without breaking existing project files or losing
desktop preferences.

## Dependencies

- Current accepted baseline through Task 058.
- Tasks 059–062 are not prerequisites.

## Canonical identifiers

- Product/display name: `Melotrail`
- Gradle root project and desktop package name: `melotrail` / `Melotrail` as
  appropriate to the field
- Kotlin package and Gradle group: `app.melotrail`
- Local settings/log directory: `~/.melotrail`
- Local repository directory after the final move: `melotrail`

## Requirements

- Move all Kotlin main and test sources from `ai.music.workstation...` to
  `app.melotrail...`; update packages, imports, reflection/main-class strings,
  Spring configuration, test fixtures, and build scripts.
- Rename desktop window title, native package, dialogs, CLI banners/help,
  application configuration, resource names, docs, Makefile output, worker
  descriptions, sample-library display metadata, and plan prompt template.
- Rename icons whose filenames contain the old product name without changing
  the image unless a new approved asset is provided.
- New preferences and logs write only beneath `~/.melotrail`.
- On first launch, read the last-project and sound-library preferences from the
  old preference node only when the Melotrail value is absent, then persist the
  migrated value in the new node. Never delete the legacy node automatically.
- Keep existing project JSON readable. Do not rewrite a user's project solely
  because the application was renamed.
- Update scripts and documentation so they do not assume the old repository
  directory.
- After all in-repository checks pass, rename the local repository directory to
  `melotrail` and repeat path-sensitive smoke checks from its new location.
- Treat renaming a hosted Git repository or remote URL as a separately
  authorized external action. Document the required command/change if no such
  authority is available.
- Allow former identifiers only in explicit migration code/tests and historical
  compatibility documentation. Add a guard that rejects accidental new uses.

## Tests

- Compile and run all root and desktop tests after the namespace move.
- Test preference migration, new-wins precedence, malformed legacy values, and
  no automatic deletion.
- Test existing project fixtures before and after the rename.
- Test CLI, Spring main class, Compose main class, and native distribution
  configuration resolution.
- Run the old-name guard with an allow-list limited to migration evidence.
- Run a desktop launch and project-open smoke after the directory rename.

## Acceptance criteria

- The app launches and presents only Melotrail branding.
- Production Kotlin code uses `app.melotrail`.
- The checked-out directory is named `melotrail`.
- Existing projects and migrated desktop preferences still open.
- No active build, runtime, CLI, packaging, or documentation path relies on the
  former name.

## Out of scope

- A new logo or visual identity redesign.
- Destructive removal of legacy preferences or logs.
- Renaming a remote repository without explicit authorization and credentials.

