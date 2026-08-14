# Task 033 — MIDI Cleanup Profiles and Musical Quality Review

## Goal

Improve the clean-MIDI stage without erasing expressive playing or inventing
musical content.

## Requirements

- Evolve the worker MIDI-clean request into versioned, named profiles:
  `conservative` (current-safe default), `transcription-safe`, and optional
  user-confirmed `tighten-timing`. Persist the exact profile/options and
  before/after MIDI metrics in preparation metadata.
- Keep current duplicate/short/low-velocity cleanup. Add deterministic handling
  for orphan note-offs, duplicate/retrigger collisions, malformed sustain
  controls, velocity outliers, and bounded soft quantization only when the
  selected profile permits it. Preserve tempo, time signatures, channels, and
  expressive timing by default.
- Add a quality review computed from clean MIDI and analysis: note count/rate,
  pitch range, polyphony, duration, velocity distribution, tempo confidence,
  silence, and warnings for suspicious transcription artifacts. It recommends a
  bounded profile/action; it does not change MIDI by itself.
- UI: show “raw → clean” summary, selected profile, warnings, a retry with an
  explicit profile, and the exact reason arrangement is unavailable. Keep piano
  preview tied to the selected clean-MIDI fingerprint.
- Maintain backward-compatible parsing for existing clean MIDI projects. A
  missing profile/report becomes “legacy/unknown,” not a failed project.

## Tests

- Existing MIDI-clean tests remain green; add fixtures for profile behavior,
  pedal/orphan/collision cases, timing preservation, explicit quantization,
  report migration, and malformed input.
- Application/desktop tests for warnings, retry selection, and no automatic
  timing change.

## Acceptance criteria

- Users can clean a weak transcription more effectively while seeing exactly
  what the selected profile may change.
- Conservative cleanup remains the no-surprises default.

## Out of scope

Generating missing notes/chords, free-form AI MIDI editing, piano-roll editing,
or automatic quantization of all imports.
