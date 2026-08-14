# Task 034 — Preview View-Model State

## Goal

Represent preview preparation and playback as explicit immutable desktop state.

## Dependencies

- Tasks 031–033 accepted.

## Requirements

- Add selected part/artifact plus `checking`, `preparing`, `ready`, `starting`,
  `playing`, `paused`, `stopped`, and `failed` preview states.
- Orchestrate resolver then player on the proper dispatchers. Handle cancellation,
  rapid source changes, project switch, retry, EOF, and player failure without
  stale callbacks updating the new project.
- Include actionable disabled/failure reason, elapsed/duration, and selected
  source identity. Never use a generic success notification as preview state.
- Keep filesystem/render/decode orchestration in services; the view model sends
  typed requests and reflects results.

## Tests

- State-transition tests for WAV/MP3/MIDI success, each prerequisite failure,
  device failure, cancellation, source race, retry, seek, and close.

## Acceptance criteria

- The UI can render the complete truthful preview lifecycle from state alone.

## Out of scope

Visual redesign or changes to resolver/player mechanics.
