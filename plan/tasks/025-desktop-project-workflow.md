# Task 025 — Desktop Project, Parts, and Structure Workflow

## Goal

Let a user create/open a project, import/analyze parts, edit roles, preview readiness, and build the ordered song structure entirely in the desktop UI.

## Dependencies

- Task 024 shell and Task 022 project services.

## Requirements

- Implement create/open project dialogs and validation errors.
- Render parts with ID, source filename/type, role, bars, key, duration, and preparation/analysis status.
- Implement Add MIDI and Add Audio flows. Collect ID/role before starting; explain transcription requirements for audio.
- Provide explicit retry for failed transcription, cleanup, or analysis; never silently register an incomplete part.
- Edit only role in the MVP. Explain that part IDs are stable.
- Implement a structure editor with add, duplicate, remove, clear, and mouse drag reorder plus keyboard-accessible move-left/move-right actions.
- Show stable occurrence labels and proportional duration when analysis is available.
- Commit the complete structure through the shared service after a drop/action, refresh from disk, and mark downstream artifacts stale.
- Disable conflicting mutations during a running operation while keeping playback/inspection available where safe.
- Add actionable worker and renderer readiness indicators.

## Tests

- View-model state for open/create/import/analyze/edit and service failures.
- Compose UI tests for empty states, file-dialog cancellation, import progress, validation, and role edit.
- Reorder/duplicate/remove with mouse-independent semantics actions; persisted structure is reloaded after mutation.
- Existing files and source hashes remain unchanged except the intended project metadata/artifacts.

## Acceptance criteria

- A clean V2 project can be prepared for arrangement without using the CLI.
- Every mutation goes through `ProjectApplicationService` and is visible after reopening the project.
- Structure edits never leave unknown IDs or partially written JSON.
- The UI clearly distinguishes source type, prepared MIDI, analyzed, failed, and stale states.

## Out of scope

- Part rename/delete, waveform editing, MIDI-note editing, and automatic destructive cleanup of stale outputs.

