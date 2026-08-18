# Task 112 — Deterministic MIDI Cleaning Boundary

## Goal

Make one mandatory, deterministic **Clean MIDI** stage produce structurally
safe MIDI and current quality evidence before any musical AI option.

## Dependencies

- Tasks 110 and 111 accepted.

## Requirements

- Consolidate overlapping “clean MIDI” and “repair MIDI” concepts into one
  application-service boundary and one user-facing stage. Retain compatibility
  adapters only where needed for supported project reads or worker protocol.
- Apply only code-owned technical corrections, such as malformed note-pair
  handling, bounded short-note/velocity filtering, sustain normalization, and
  collision safety already supported by the worker contract. Do not invent
  melody, harmony, tempo, or expressive phrases here.
- Always write a new cleaned MIDI artifact and a versioned quality report tied
  to the raw-MIDI hash. Never mutate source or raw MIDI.
- Preserve the current review/approval gate when measured quality evidence
  requires human review. Make approval bind to exact raw, cleaned, options, and
  report fingerprints.
- Expose raw-versus-cleaned A/B preview and a concise diff/quality summary.
- Mark the approved cleaned MIDI as the default base selected for subsequent
  stages and invalidate every dependent optional fix, feel, analysis, and song
  artifact when it changes.
- Remove duplicate UI actions and stale terminology only after all callers and
  tests use the canonical boundary.

## Tests

- Characterize current cleanup behavior before consolidation, then cover every
  allowed option, malformed input, output round-trip, note collision, quality
  threshold, approval, rejection, staleness, and retry path.
- Assert deterministic byte/logical output for identical input and options.
- Assert source/raw hashes are unchanged and failed output is never selected.
- Update worker protocol, application service, view-model, and UI tests for the
  single **Clean MIDI** action.

## Acceptance criteria

- Every part must have current, approved cleaned MIDI before it may choose an AI
  fix or Lo-fi Feel, and the stage contains no model-dependent behavior.

## Out of scope

- Musical note correction, style transformation, bridge generation, or audio
  cleanup.
