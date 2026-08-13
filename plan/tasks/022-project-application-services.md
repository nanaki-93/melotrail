# Task 022 — Project Application Services and CLI Parity

## Goal

Extract project, part, analysis, and structure orchestration from the CLI into typed Kotlin application services that can be called by both CLI and a later desktop UI.

## Dependencies

- Existing `Project`, `ProjectStore`, MIDI preparation, analysis, worker client, and CLI tests.
- No Compose dependency is introduced in this task.

## Requirements

- Add UI-neutral request/result types for create, open, import part, analyze part, update part role, and save structure.
- Add a `ProjectSnapshot` read model containing part summaries and artifact/readiness state.
- Preserve source copying, MIDI cleanup/transcription, analysis validation, relative-path safety, V1 readability, V2 format rules, and atomic `project.json` updates.
- Add an atomic complete-list structure update. Unknown IDs are rejected; an empty draft may be saved but cannot be arranged or built.
- Keep part IDs immutable. Support role editing only; do not rename or delete source artifacts.
- Inject worker/MIDI preparation boundaries instead of constructing them inside use-case methods.
- Migrate the corresponding CLI handlers to call these services. CLI parsing and human-readable output remain in `cli`.
- Avoid a generic command bus, DI framework, or Compose types.

## Tests

- Create/open valid and invalid projects.
- MIDI and audio imports with fake preparation worker; source remains unchanged on success/failure.
- Analysis updates the canonical reference and snapshot.
- Role update and structure reorder/duplicate/remove persist atomically.
- Unknown part IDs, escaping paths, conflicting mutations, and invalid project versions fail clearly.
- CLI adapter and direct service calls produce equivalent canonical project artifacts.
- Existing Kotlin tests and build remain green.

## Acceptance criteria

- No project/part/structure use case needed by the desktop app remains private inside `ArrangementProjectCommands`.
- CLI behavior and artifact formats remain compatible.
- The service layer has no dependency on CLI, Compose, Spring controllers, or static-web DTOs.
- `ProjectSnapshot` can populate filename/type, role, bars, duration, key, structure occurrence labels, and readiness without UI filesystem access.

## Out of scope

- Compose UI, playback, part deletion/renaming, and arrangement/build extraction.
- Changing MIDI cleanup, transcription, analysis algorithms, or project JSON versions without necessity.

