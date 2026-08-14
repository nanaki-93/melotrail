# Task 042 — Transcription Quality Gate

## Goal

Validate Basic Pitch output before it becomes the raw MIDI input to cleanup and
analysis, with failures attributed to the correct stage.

## Dependencies

- Tasks 038 and 041 accepted.

## Requirements

- Let the request explicitly select original/decoded/prepared audio using only a
  validated project artifact identifier, never an arbitrary model path.
- After transcription validate MIDI header/parser, nonempty note set, duration
  sanity against input, bounded note rate, piano pitch range, event timing, and
  finite/valid metadata. Preserve failed raw output in a diagnostic-safe location
  only when it passes path rules.
- Distinguish prerequisite, decode, cleanup-selection, model-runtime, inference,
  and output-validation errors in application state/logs.
- Update the preparation report with selected input fingerprint, engine/version,
  gate metrics, status, and warnings; publish raw MIDI atomically only on success.

## Tests

- Fake successful/empty/corrupt/overdense/out-of-range/duration-mismatch output,
  every stage error, original versus prepared selection, stale fingerprint, and
  source preservation.

## Acceptance criteria

- Invalid transcription cannot silently become canonical raw MIDI, and the user
  can identify the failing stage.

## Out of scope

Improving Basic Pitch itself, MIDI cleanup profiles, or UI.
