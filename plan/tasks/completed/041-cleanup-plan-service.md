# Task 041 — Cleanup Plan and Provenance Service

## Goal

Select and apply a bounded cleanup plan from inspection evidence, then publish
validated project artifacts and provenance.

## Dependencies

- Tasks 038 and 040 accepted.

## Requirements

- Define versioned `InputCleanupPlan`: inspect-only/safe-cleanup mode, allowed
  operations, fixed-range settings, evidence, confidence, warnings, and selected
  transcription input artifact.
- Implement deterministic evidence-to-plan selection as the default/test oracle.
  An optional local model adapter may rank only supplied candidates in strict
  JSON; reject paths, commands, unknown operations/settings, or invalid ranges
  and fall back deterministically.
- Add an application-service apply operation that invokes the worker and
  atomically publishes `prepared/<part>/clean.wav` plus updated report. Validate
  format, metrics, fingerprints, and source immutability before success.
- Default to inspect-only unless measured evidence exists; actual safe cleanup
  still requires explicit user confirmation in the later UI task.

## Tests

- Deterministic threshold decisions, JSON validation/fallback, atomic failure,
  stale inspection, worker mismatch, provenance, idempotency, and source hash.

## Acceptance criteria

- Cleanup is schema-bounded, auditable, reversible, and independent of Compose.

## Out of scope

Cleanup UI, A/B controls, transcription quality gate, or arbitrary AI DSP.
