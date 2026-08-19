# Task 015 — Deterministic MIDI normalization

## Goal

Separate a deterministic Normalize stage from existing event cleanup and produce
a complete, reproducible transform report.

## Why

Transcription/event repair and input canonicalization have different policies;
later transpose/correction needs a stable, profile-aware but non-creative input.

## Dependencies

Tasks 012–013 and 003–004.

## Existing Code

- worker `commands/midi_clean.py` profiles/reports
- `MidiQualityReport.kt`, JDK MIDI analysis/timing utilities
- PPQ normalization/timeline logic in arrangement code
- existing raw/clean artifact selection

## Changes

- Define Clean versus Normalize processor contracts and reports. Keep worker MIDI
  clean for invalid/duplicate/orphan/retrigger/control cleanup.
- Implement Kotlin normalization for deterministic event ordering, PPQ/tick
  conversion policy, conservative timing grid, velocity/range policy, tempo/meter
  conformance, and report.
- Exclude swing, creative quantization, pitch correction, and humanization.
- Resolve profile/mood normalization bounds through typed context, with safe
  neutral defaults and config hash.
- Register processors in automatic pipeline and map legacy clean output without
  fabricating a normalization run until explicitly executed.

## Files

Add MIDI normalization processor/config/report/tests; update registry, artifact
graph, worker clean adapter, readiness/docs.

## API / Contracts

Completed output is NORMALIZED with input hash, exact event changes, policy/
processor version, and warnings. No UI file paths.

## UI

Parts rail may group Cleaning/Normalizing while details distinguish reports.

## Backend

Stage runner invokes worker Clean then Kotlin Normalize.

## Python Worker

Retain MIDI-clean schema unless a version field/report mapping is required; add
capability negotiation for the supported profile version.

## Tests

PPQ variants, tempo/meter maps, duplicate timestamps, velocity/range boundaries,
drum channels, expressive off-grid notes, determinism, source non-overwrite.

## Acceptance

- Same input/config/version produces identical output/report/hash.
- Normalize contains no pitch creation/deletion or hidden groove.
- Existing worker-clean behavior remains characterized.

## Out of Scope

Transpose, technical note correction, enhancement, or humanization.

