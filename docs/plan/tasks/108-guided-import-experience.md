# Task 108 — Guided Import Experience

## Goal

Turn the crowded import detail experience into a short, step-by-step process
that exposes only the next necessary choice while preserving safe import and
provenance behaviour.

## Dependencies

- Tasks 103, 104, 106, and 107 accepted.

## Requirements

- Present a single import entry point with a clear first step: choose or drop
  one MIDI or eligible solo-piano audio source. Explain the route after file
  inspection instead of making users choose processing options first.
- Organize the interaction as visible steps: (1) select source, (2) inspect
  and validate, (3) confirm source/provenance information required by policy,
  and (4) show exactly one next workflow action. The UI must make the current
  step, completion, error, and recovery readable.
- Derive the initial part ID safely from the validated filename and assign a
  sensible default role; keep role editing after import. Do not expose manual
  ID/role fields in the normal confirmation dialog unless collision recovery
  makes them necessary.
- Move Lo-fi MIDI Feel, audio cleanup configuration, repair profile selection,
  and deep preparation diagnostics out of initial import. Reach them from a
  secondary “Details” action only when their stage is current; preserve their
  typed intents and backend validation.
- Collapse redundant dual chooser cards, tabs, and duplicate primary actions
  into one responsive surface. Keep drag-and-drop and file browsing equivalent
  and accessible.
- Maintain direct MIDI versus audio transcription safeguards, source-rights
  attestation, immutable artifact publication, stale-state handling, and
  truthful readiness/failure feedback.
- Add contextual help links to the workflow and MIDI import documents.

## Tests

- View-model tests for each step, type detection, inferred ID collision,
  validation failure, provenance requirement, cancellation, and one-next-action
  selection.
- Compose tests for keyboard-only browsing, drop/browse parity, no-project
  state, MIDI path, audio path, detail disclosure, and responsive layout.

## Acceptance criteria

- A user can import a normal source without seeing advanced processing options
  or typing metadata that the app can derive safely, and can still recover from
  a validation/provenance problem without losing the selected source.

## Out of scope

- Batch import, source deletion, arbitrary audio transcription, or removal of
  mandatory provenance/legal evidence.
