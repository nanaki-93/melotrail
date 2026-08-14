# Task 045 — MIDI Quality Report

## Goal

Compute and persist an auditable musical-quality summary for raw and clean MIDI.

## Dependencies

- Task 044 accepted.

## Requirements

- Define a versioned report with cleanup profile/options, fingerprints,
  note count/rate, pitch range, polyphony, duration, velocity distribution,
  tempo/time-signature preservation, timing changes, and bounded warnings.
- Derive recommendations from deterministic metrics only; they may suggest a
  profile/retry but never modify MIDI automatically.
- Integrate report publication with the application service atomically and expose
  it in `PartPreparation`. Missing reports in existing projects are
  `legacy/unknown`, not corruption.
- Invalidate/recompute when raw MIDI/profile changes and validate clean MIDI
  fingerprint before arrangement readiness.

## Tests

- Report calculations, warning thresholds, fingerprint invalidation, atomic
  failure, legacy projects, malformed reports, and snapshot readiness.

## Acceptance criteria

- Clean-MIDI quality and exact cleanup provenance survive reload and are not
  inferred from UI state.

## Out of scope

UI, AI note repair, quantization implementation, or arrangement changes.
