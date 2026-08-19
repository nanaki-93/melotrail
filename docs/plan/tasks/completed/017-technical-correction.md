# Task 017 — Technical correction stage

## Goal

Create a conservative Correct stage distinct from cleanup/normalization and
creative enhancement, with reasoned bounded edits and selectable output.

## Why

The current AI fix mixes technical repair and musical invention. Users need a
trustworthy corrected baseline that enhancement can always bypass to.

## Dependencies

Tasks 012, 015–016, and harmony/context availability from 008.

## Existing Code

- `MidiAiFix.kt` strict plan, validation, deterministic application, approvals
- worker MIDI clean and `MidiQualityReport`
- MIDI analysis/range/chord evidence
- AI-fix references/selection compatibility

## Changes

- Define `TechnicalCorrectionProcessor`, plan/report/edit categories, confidence,
  and strict limits. Allowed goals: clear detection errors, collisions, range,
  broken duration/velocity, strongly unsupported notes.
- Forbid decorative note addition, phrase redesign, arbitrary harmony changes,
  or project-setting mutation.
- Implement an initial deterministic rules processor; reuse safe plan validator/
  applier primitives from `MidiAiFix` where they fit.
- Consume full structured context for validation, not inferred isolated key/chords.
- Persist a CORRECTED artifact/report; warnings or low-confidence edits require
  explicit approval or remain unchanged by policy.
- Map legacy approved AI-fix to inspectable compatibility evidence without
  claiming it met the new correction contract.
- Move reusable strict-plan validation/application primitives into canonical
  correction/enhancement components. Once Task 019 cuts over enhancement, delete
  the old combined AI-fix service, prompt, registrations, selection branches, and
  tests that exist only for that superseded runtime behavior.

## Files

Add correction port/config/processor/report; refactor reusable `MidiAiFix` plan
code, registry/references/readiness/tests and migration mapping.

## API / Contracts

Strict path-free correction plan with input/context hashes and code-owned
operations. Output selection becomes the baseline for Enhancement.

## UI

Parts details show correction summary/warnings; compare controls are Task 020.

## Backend

Deterministic Kotlin implementation first; any future model remains an injected
planner whose output uses the same validator/applier.

## Python Worker

No new command.

## Tests

Allowed/forbidden edits, reason/confidence, invalid/malformed plan, context/hash
mismatch, source preservation, legacy mapping, deterministic result, failure.

## Acceptance

- Correction cannot perform enhancement-only operations.
- Corrected remains valid/selectable when enhancement fails or is Off.
- Every correction is explained and bounded.

## Out of Scope

Creative phrase edits, passing notes, contour/repetition improvements.
