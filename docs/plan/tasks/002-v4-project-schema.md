# Task 002 — v4 project schema and migration scaffold

## Goal

Add a v4 persistence envelope, pure v3-to-v4 migration, manifest references, and
atomic explicit-save path without yet changing musician-facing behavior.

## Why

All later settings, harmony, part, occurrence, and run contracts need one
canonical versioned aggregate and safe legacy behavior.

## Dependencies

Task 001.

## Existing Code

- `arrangement/Project.kt`, `ProjectStore.kt`, `ProjectValidator`
- v1/v2/v3 DTOs and migration tests
- `WorkflowArtifacts.kt` and project-relative fingerprint/reference helpers

## Changes

- Introduce v4 DTO/envelope fields for composition settings, harmony, evolved
  parts, structure occurrences, and stage/provenance manifest refs. Fields may
  initially be incomplete/empty until their owning tasks land.
- Implement pure v1/v2/v3-to-v4 mappings. Missing creative settings become a
  typed `setupRequired`/validation state, never inferred defaults on open.
- Keep open read-only; add/extend an explicit migrate/save command that writes
  temp files, validates, atomically publishes, and retains recovery evidence.
- Permit pending/failed run references without requiring nonexistent outputs;
  completed artifact refs remain strictly validated.
- Preserve unknown legacy data where practical through typed compatibility data
  or explicit warnings, not silent discard.

## Files

Modify `arrangement/Project.kt`, `ProjectStore.kt`, validators, fixtures, and
store/application tests. Add v4 fixture resources.

## API / Contracts

Add schema version 4, a migration result containing warnings/setup requirements,
and an explicit migration/save result. Do not expose DTO internals to UI.

## UI

Only expose a queryable “legacy project requires setup/migration” state; actual
Setup editing is Task 006.

## Backend

Canonical file-backed storage only. Spring legacy JSON is not auto-migrated.

## Python Worker

No change.

## Tests

Golden v1/v2/v3 reads, no rewrite-on-open hash checks, explicit v4 write/reopen,
unknown fields/roles, missing files, failed-stage refs, atomic failure recovery.

## Acceptance

- Existing fixtures open unchanged.
- Explicit migration produces valid v4 and preserves selected/source hashes.
- Opening never writes.
- Incomplete creative settings are visible and block only dependent new stages.

## Out of Scope

Implementing musical primitives, profile rules, UI, or processing manifests.

