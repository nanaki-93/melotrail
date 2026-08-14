# Task 030 — Desktop Sound-Library Settings

## Goal

Let a desktop user select, validate, retain, and clear a local sound-library
root without storing project or audio data in preferences.

## Dependencies

- Task 029 accepted.

## Requirements

- Extend the desktop preference boundary with an optional absolute library root;
  corrupt or missing values must be ignored safely.
- Add a folder chooser and view-model intents/state for select, validate, save,
  clear, and refresh. Persist only after the complete registry validates.
- Environment configuration remains authoritative and read-only in the UI; show
  why selection is disabled when it is present.
- Display the resolved path, discovery source, validation error, and concise
  `sounds/README.md` recovery guidance.

## Tests

- Preference round-trip/corruption, environment precedence, invalid selection,
  last-known-good preservation, clear, cancellation, and view-model state.
- Compose semantics test for choose/clear and visible validation feedback.

## Acceptance criteria

- A user can recover from a missing library without changing launch directory
  or editing project files.

## Out of scope

Other dependency checks, preview, build, or packaging policy.
