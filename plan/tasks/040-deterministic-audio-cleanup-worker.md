# Task 040 — Deterministic Audio Cleanup Worker

## Goal

Implement conservative, independently testable input repair operations without
adding AI selection or project/UI behavior.

## Dependencies

- Task 037 accepted.

## Requirements

- Add a typed worker cleanup command that accepts only schema-allowed operations
  and bounded parameters: DC removal, short-run clip repair, isolated declick,
  narrow 50/60 Hz hum removal, and gentle stationary-noise reduction.
- Reuse/harden existing repair primitives; do not create duplicate algorithms.
  Document evidence thresholds and apply only explicitly requested operations.
- Preserve duration (within documented DSP tail tolerance), sample rate,
  channels, and finite samples; write PCM-24 WAV atomically. Never normalize,
  remove silence/time, alter pitch/tempo, or overwrite input.
- Return before/after metrics, applied/skipped operations, warnings, and tool
  versions for report validation.

## Tests

- Clean no-op, each isolated defect/operation, combined bounded operations,
  mono/stereo/rates, duration/format, invalid settings, output failure, and input
  hash preservation. Include measurable and listening fixtures.

## Acceptance criteria

- Each allowed repair is conservative, measurable, deterministic, and usable
  independently through the worker boundary.

## Out of scope

AI advice, project report updates, UI, source separation, or transcription.
