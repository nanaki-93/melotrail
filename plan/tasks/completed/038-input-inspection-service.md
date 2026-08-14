# Task 038 — Input Inspection Application Service

## Goal

Integrate inspection with canonical project paths, atomic reports, and part
preparation snapshots.

## Dependencies

- Tasks 036–037 accepted.

## Requirements

- Add a typed application-service operation that verifies project/part/source,
  calls the inspection boundary, validates returned metrics, and atomically
  writes `prepared/<part>/report.json`.
- Extend project snapshots with preparation stages: source preserved, inspected,
  prepared audio, raw MIDI, clean MIDI, analyzed, ready, plus warnings. Derive
  from validated artifacts/report, not filenames alone.
- Preserve report/source on failure and support idempotent retry/fingerprint
  reuse. Reject stale reports when source fingerprint changes.
- Add an explicit CLI adapter only if needed for parity; do not change existing
  import defaults silently.

## Tests

- Atomic write/failure/retry, stale fingerprint, malformed worker output,
  snapshot combinations, legacy missing report, mutex, and source hash.

## Acceptance criteria

- Desktop/CLI-neutral code can inspect a part and reload truthful preparation
  state from project artifacts.

## Out of scope

Import dialog, cleanup, transcription changes, or visual presentation.
