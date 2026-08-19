# Task 021 — Persistent structure occurrence identity

## Goal

Persist stable structure occurrence IDs and variation overlays while preserving
the current repeated-part structure experience.

## Why

Derived A1/A2 identities can change on reorder; arrangement, cohesion, preview,
and provenance require occurrence identity independent of list position.

## Dependencies

Tasks 002, 010, and 020.

## Existing Code

- v3 project `structure` list and structure application/UI controls
- current occurrence-label derivation and `SectionVariation.kt`
- cohesion boundary IDs and arrangement structure validation

## Changes

- Add `StructureOccurrence(id, partId, label, variationOverrides)` persistence.
- Migrate v3 lists during explicit v4 save using deterministic collision-safe IDs;
  repeating the same part yields distinct occurrence IDs.
- Update add/remove/reorder/duplicate/label/variation commands to use occurrence
  ID plus expected revision; reorder retains identity.
- Adapt read model, arrangement/cohesion inputs, preview labels, invalidation, and
  project validator through compatibility adapters.
- Invalidate only affected occurrence/boundary/project dependents, never part
  source processing.
- Update Structure UI semantics and keyboard reorder without a wholesale rewrite.

## Files

Project/store/migration/structure service, variation/cohesion adapters,
Workspace Structure state/UI, and fixtures/tests.

## API / Contracts

Structure DTO is ordered occurrences. Part ID is reference; position is not ID.

## UI

Repeated Verse entries keep distinct accessible labels/IDs across reorder/reload.

## Backend

Canonical service only.

## Python Worker

No change.

## Tests

Migration with repeats, insert/reorder/delete/duplicate, reload stability,
variation retention, exact boundary invalidation, missing part validation, UI.

## Acceptance

- Reordering does not rename/reidentify existing occurrences.
- Repeats reuse one selected part melody non-destructively.
- Legacy projects preserve visible order and can explicitly migrate.

## Out of Scope

Arrangement role redesign, AI structure generation, or section-duration editing.

