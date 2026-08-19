# Task 011 — Processing artifact and stage-run manifest

## Goal

Persist generic artifact lineage and per-stage Pending/Processing/Completed/Failed
records alongside the existing workflow references.

## Why

Automatic expensive processing must resume/retry safely and distinguish status
from output existence. Coarse stale flags and stage-specific fields do not scale.

## Dependencies

Tasks 002 and 010.

## Existing Code

- `WorkflowArtifact`, `WorkflowArtifactGraph`, fingerprinted references
- `MidiReferences`, AI-fix/feel/cohesion/arrangement/build references
- atomic store/hash/path confinement helpers
- `CommercialProvenanceService` and older generic provenance log

## Changes

- Define versioned `ArtifactRef`, `StageId`, `StageSubject`, `StageRunRecord`,
  safe failure, processor/model identity, configuration/context hashes, seed,
  timestamps, reports, approvals/selections.
- Define logical part stages source/extracted/cleaned/normalized/transposed/
  corrected/enhanced/analyzed and project stages through export.
- Select an atomic manifest layout (immutable per-run JSON plus index preferred,
  or equivalently safe versioned JSON); document compaction/recovery.
- Completed-only output invariant; pending/failed runs may not reference a
  selectable output. Validate hashes and project-relative paths.
- Add compatibility mapper from v3 raw/clean/AI-fix/feel references without
  renaming/deleting artifacts.
- Extend dependency graph to subject/config inputs while retaining legacy reads.

## Files

Add workflow-run domain/store/validator/migration tests; adapt project manifest
refs, resolver compatibility, and artifact graph.

## API / Contracts

Query run history/current stage summary and exact artifact lineage. Mutations are
internal until Task 012. Serialize stable schema/version and safe error codes.

## UI

No screen; supply progress-ready summaries without technical paths/raw model data.

## Backend

Canonical project manifest store only.

## Python Worker

No change; worker response metadata can be recorded by future runner.

## Tests

All statuses, completed invariant, tampered/missing outputs, atomic write failure,
v3 mapping, unknown stages/versions, cache-key normalization, subject dependencies.

## Acceptance

- Failed Enhancement can coexist with valid Corrected output.
- Every selectable artifact has a completed validated producing record.
- Existing v3 artifact selection remains behaviorally identical.

## Out of Scope

Executing stages, retry orchestration, UI, or provenance reports.

