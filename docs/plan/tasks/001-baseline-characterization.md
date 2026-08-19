# Task 001 — Baseline characterization and support-contract reconciliation

## Goal

Lock the reusable v3 behavior and make build/documentation claims match the
actual Compose, Spring, and worker surfaces before schema work.

## Why

Migration is unsafe if direct audio/MIDI import, selection precedence, or legacy
project behavior is only assumed. Stale module/entry-point documentation also
prevents a clear compatibility promise.

## Dependencies

None.

## Existing Code

- `README.md`, `Makefile`, `build.gradle.kts`, `settings.gradle.kts`
- `arrangement/ProjectStore.kt`, `SelectedMidiArtifactResolver.kt`
- `application/ProjectApplicationService.kt`, especially `importPart`
- `server/api/*`, `service/ProjectServiceAdapter.kt`, `model/Project.kt`
- `InstrumentRegistry.kt`, `sounds/instruments.json`, `sounds/LICENSES.json`,
  `LocalSoundLibraryInventory.kt`, render/provenance registry consumers
- `worker/README.md`, `worker/main.py`, Kotlin worker protocol/client
- existing root, desktop, and worker tests; `docs/RELEASE_ACCEPTANCE.md`

## Changes

- Add characterization tests for v1/v2/v3 no-rewrite open, direct MIDI import,
  WAV/MP3 transcription publication, immutable source/raw/clean artifacts, and
  selected-MIDI precedence/reversal.
- Exercise both branches in `importPart`; fix nested branching only if a new test
  demonstrates the audio route is unreachable or incorrect.
- Inventory which Spring endpoints and config paths are used; record an explicit
  supported/obsolete status. Preserve or migrate required data, but require Task
  028 to delete an obsolete product surface rather than freeze it in place.
- Remove obsolete source-less entry-point claims and build targets from README,
  Makefile, and Gradle configuration; do not preserve them as future scope.
- Correct the README module/architecture summary and document the exact baseline
  worker capabilities and solo-piano limitation.
- Inventory unreachable/duplicate UI, services, routes, registrations, build
  tasks, configuration, and resources. When characterization proves an item has
  no supported caller/data obligation, delete it and its exclusive tests/docs in
  this task rather than labeling it deprecated for later cleanup.
- Characterize registry v1's exact-five-logical-key invariant, path/SFZ/WAV/
  separate license-reference validation, UI-safe inventory, renderer lookup,
  commercial hashing/attribution behavior,
  and current arrangement/stem identity before Task 022B changes the schema.

## Files

Likely modify `README.md`, `Makefile`, `build.gradle.kts`, relevant application
tests, worker docs/tests, and documentation inventory. Production changes are
limited to a test-proven import defect or removal of a broken build task.

## API / Contracts

No new product API. Produce a checked support matrix for Compose, Spring REST,
and worker commands. Preserve retained worker request/result schemas.

## UI

No redesign. Confirm which router/shell is active and which legacy composables
are reachable.

## Backend

Characterize, but do not yet unify, the canonical file-backed project and the
separate Spring project store.

## Python Worker

Run command-schema/fixture tests and document `/health`, inspection, cleanup,
transcription, MIDI clean, DSP, master, and MP3 capabilities.

## Tests

Run `./gradlew test :desktopApp:test`, Python worker tests, documentation checks,
and any retained build/check targets. Add a regression test for each defect fixed.
Include registry-loader security/schema, inventory redaction, renderer descriptor,
commercial dependency/attribution characterization, and the exact relationship
between v1 `instruments.json` and `LICENSES.json` with the existing starter library.

## Acceptance

- Baseline behavior has explicit automated evidence.
- Supported docs/build targets resolve to real code.
- No baseline-only obsolete entry point or proven-unreachable duplicate remains.
- No source/project artifact is modified by characterization.
- Unverified live renderer/model/audio behavior remains labeled unverified.

## Out of Scope

v4 models, UI redesign, registry v2, Spring migration, or new worker capabilities.
