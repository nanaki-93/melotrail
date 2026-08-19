# Task 005 — Composition settings application contract

## Goal

Persist and query project name, key/scale, BPM, meter, profile, and mood through
typed application commands with exact downstream invalidation previews.

## Why

Setup is the creative authority for every later context. UI must not mutate JSON
or guess which artifacts become stale.

## Dependencies

Tasks 002–004.

## Existing Code

- `ProjectApplicationService.kt`, project snapshots/readiness/mutex
- `WorkflowArtifactGraph`, `WorkflowReadModel`
- desktop service composition in `DesktopMain.kt`

## Changes

- Add `GetCompositionSettings`, `PreviewSettingsChange`, and
  `UpdateCompositionSettings` commands/results behind the project facade.
- Validate profile/mood compatibility and profile UI constraints while retaining
  core musical validity beyond Lo-fi defaults.
- Compute field-sensitive invalidation: name-only none; key from transpose;
  tempo/meter from declared normalize/arrange dependencies; profile/mood from
  affected policies.
- Persist a decision revision/hash and resolved profile/mood snapshot reference.
- Update readiness so missing settings block dependent stages with actionable
  reasons, without preventing source inspection/export of historical outputs.

## Files

Modify/create application settings service/DTOs, project mapper, artifact graph,
read model, facade wiring, and tests.

## API / Contracts

Typed command includes expected revision and all settings. Preview returns exact
affected stage/subject summaries. Result returns immutable updated snapshot.

## UI

Provide UI-ready option/validation models and invalidation confirmation data.

## Backend

Canonical service only; no Spring controller in this task.

## Python Worker

No change.

## Tests

Valid/invalid updates, optimistic revision, persistence/reopen, profile/mood
compatibility, exact stale propagation, and no invalidation for display name.

## Acceptance

- One application command is the only settings mutation path.
- Settings survive reopen and contribute deterministic context/revision hashes.
- Creative changes never silently reuse incompatible downstream results.

## Out of Scope

Setup UI, harmony, processor execution, REST/CLI adapters.

