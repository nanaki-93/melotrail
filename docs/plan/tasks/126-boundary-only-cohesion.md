# Task 126 — Boundary-only Cohesion

## Goal

Restrict Cohesion to adjacent structure-occurrence transitions and remove its
current whole-song enhancement responsibility.

## Dependencies

Tasks 120, 121, 124, and 125.

## Contract migration

- Introduce the next `TransitionCohesionInput`/plan schema version with only
  boundary plans and boundary edits. Remove `songEdits` from prompts, DTOs,
  validators, appliers, reports, counters, application snapshots, and UI copy.
- A boundary is identified by stable outgoing/incoming occurrence IDs and uses
  the last canonical bar of the outgoing occurrence plus the first canonical bar
  of the incoming occurrence. Tick windows are half-open and clipped to each
  occurrence. Songs with fewer than two occurrences produce a current no-op.
- Permit only the existing typed transition operations that bridge, overlap,
  thin, anticipate, or smooth the named boundary. Every operation must name its
  boundary and affected melody occurrence or generated role.
- Apply the shared melody identity, current intensity budgets, canonical harmony,
  role ranges, and generated-role validators. Current limits remain in force;
  this task may make them stricter only where necessary to enforce the window.
- Persist input/context/arrangement/generated-artifact hashes, exact edits,
  validation evidence, model/processor identity, output hashes, and approval.

## Compatibility and invalidation

- Legacy plans/reports with `songEdits` remain readable as historical evidence
  if required by the supported schema window, but they are always stale under
  the boundary-only contract and cannot be re-approved.
- Regeneration writes new canonical derived paths atomically and never overwrites
  source, arranged, generated, or legacy Cohesion evidence.
- Any structure, harmony, arrangement, generated-role, boundary-policy, or
  selected-input change invalidates Cohesion and all descendants.
- Delete `SongEnhancementTarget`, `SongEnhancementEdit`, whole-song applier paths,
  and exclusive tests once no supported reader needs their runtime behavior.

## Tests

- Two and repeated-occurrence songs produce the exact adjacent boundary set.
- First/last tick and one-tick-outside window cases.
- Whole-song edit JSON is rejected by the new strict schema.
- Anchor, harmony, role, budget, and stale-hash failures are atomic.
- Single-occurrence no-op and legacy stale-read behavior.
- Approved output contains no changed note outside boundary windows.

Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- No supported Cohesion path can perform an arbitrary whole-song edit.
- Every Cohesion mutation names one adjacent boundary and lies inside its window.
- Cohesion remains previewable and explicitly approvable.

## Exclusions

Do not implement whole-song criticism or enhancement in this task.
