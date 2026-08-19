# Task 008 — Harmony application service

## Goal

Provide typed commands/queries for Verse, Chorus, Bridge, and future section
progressions with revision safety and dependency-aware invalidation.

## Why

Harmony editing and musical processors need one authoritative service boundary;
UI must not mutate progression lists or decide stale stages.

## Dependencies

Tasks 005 and 007.

## Existing Code

- `ProjectApplicationService.kt`, project mutex/store/snapshot
- `WorkflowArtifactGraph`, `WorkflowReadModel`
- current analysis/planner consumers of inferred chord summaries

## Changes

- Add list/get/create/update/delete/reorder progression event commands.
- Seed empty Verse/Chorus/Bridge progression slots for new Lo-fi projects without
  inventing chords; define required-section completeness separately.
- Use expected project/harmony revision and return updated immutable snapshot.
- Produce structured validation errors and an invalidation preview. Harmony
  changes stale correction/enhancement/arrangement and later stages according to
  declared context dependencies, but not source/extract/clean.
- Add context-query function resolving a part's exact section progression.
- Update readiness to require valid harmony only for stages that consume it.

## Files

Add harmony application service/DTOs; wire facade/store/read model/artifact graph
and application tests.

## API / Contracts

Commands reference progression/event IDs, never list indices alone. Query returns
ordered structured events plus revision and validation/completeness.

## UI

Supply editor state and invalidation confirmation data for Task 009.

## Backend

Canonical service only. Optional REST mapping is Task 028.

## Python Worker

No change.

## Tests

CRUD/reorder, revision conflicts, section lookup, persistence/reopen, exact stale
propagation, intentional chromatic harmony, missing progression readiness.

## Acceptance

- Verse/Chorus/Bridge are separately editable and additional section IDs work.
- Downstream processors can request structured section harmony.
- No UI/file mutation bypass exists.

## Out of Scope

Chord suggestions/audition, progression generation, processor prompt changes.

