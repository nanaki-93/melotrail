# Task 024 — Arrangement-aware cohesion

## Goal

Upgrade boundary cohesion to consume structured musical/arrangement context and
create reviewed continuity without rewriting the core melody.

## Why

Current bridges use simplified key/chord mapping and cannot coordinate actual
drums, bass, instruments, dynamics, or repeated-section variation.

## Dependencies

Task 023.

## Existing Code

- `CohesionApplicationService.kt`, `TransitionCohesion.kt`
- Qwen strict boundary plans, deterministic bridge MIDI, review/approval/hashes
- older `MelodyCohesion.kt` compatibility code
- current boundary preview UI and tests

## Changes

- Build boundary evidence from stable occurrences, selected melodies, structured
  key/harmony/tempo/meter, approved role/instrument/density/variation plans, and
  profile/mood transition policies.
- Replace natural-note-only key mapping and inferred triad strings.
- Extend code-owned transition vocabulary incrementally for pickups, fills, bass/
  chord motion, sustained texture, dynamics/automation, and continuity.
- Keep strict path-free planner and deterministic renderer/validator. Protect core
  melody; any exceptional melody edit uses a separate bounded approval flag.
- Validate exact `n - 1` boundary identity, timing/meter compatibility, collisions,
  range, harmony evidence, input/context/arrangement hashes.
- Update boundary/whole-sequence preview, approve/reject/retry, and provenance.
- Map any supported whole-occurrence evidence needed for project/release history,
  then delete `MelodyCohesion.kt`, its runtime wiring/prompts/UI/configuration, and
  tests that exist only for the superseded implementation in this task. Do not
  leave a dormant alternative after boundary cohesion is accepted.

## Files

Cohesion service/domain/planner/renderer, Build UI, profile transition policies,
legacy adapters, provenance/docs/tests.

## API / Contracts

Versioned boundary plan includes outgoing/incoming occurrence, arrangement and
context hashes, allowed intents/role actions, validation report, approval.

## UI

Boundary cards explain proposed role actions and support preview/review; whole
Build state shows exact missing/failed boundary.

## Backend

Local AI proposes only vocabulary choices; deterministic Kotlin creates artifacts.

## Python Worker

No new command.

## Tests

Structured sharp/flat keys, minor/meter cases, role-aware fills, repeated sections,
unsafe/malformed plan, hash mismatch, melody preservation, `n - 1`, failure retry.

## Acceptance

- Cohesion uses approved arrangement context and never assumes C/4/4.
- Core melody hashes are unchanged unless a separately approved bounded exception.
- Every current boundary is reviewable and lineage-complete.
- Whole-occurrence cohesion has no source, registration, route, action, or stale
  documentation remaining; historical evidence is readable as data.

## Out of Scope

Audio spectral crossfades, arbitrary automation DAW editing, full-song rewrite.
