# Task 032 — Runtime Readiness Model

## Goal

Replace the coarse worker/renderer status with typed dependency readiness that
can explain exactly which operations are available.

## Dependencies

- Tasks 029–031 accepted.

## Requirements

- Model worker reachability, transcription runtime, sound library/registry,
  sample completeness, renderer executable/version, and audio output separately.
- Each dependency has stable status, short detail, optional recovery action, and
  non-sensitive diagnostic data. Unknown/checking is not treated as ready.
- Derive capability checks for audio import, source preview, MIDI preview,
  arrangement render, and Build Song. Keep this derivation outside composables.
- Update the desktop readiness service/header/status panel and refresh behavior.
  Disable only operations needing the unavailable dependency and show why.

## Tests

- Every dependency/capability combination, refresh failure, stale result, and
  view-model enable/disabled reason.
- Compose test for checking, ready, partial, and failed states.

## Acceptance criteria

- Missing `sounds/`, Basic Pitch, renderer, worker, or audio device produces a
  distinct actionable state before a long operation starts.

## Out of scope

Changing those dependencies, preview artifact resolution, or import workflow.
