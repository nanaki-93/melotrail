# Task 028 — Canonical Spring API decision and adapter

## Goal

Using Task 001 usage evidence, either adapt the optional Spring API to canonical
composition services or explicitly deprecate/freeze it without data loss.

## Why

The current independent `model.Project`/`ProjectServiceAdapter` store would split
the product domain and cannot safely implement the new workflow in parallel.

## Dependencies

Tasks 005, 008, 010–012, 021, and Task 001 usage inventory. This optional adapter
must not block desktop MVP.

## Existing Code

- `server/api/ProjectController`, `AudioController`, `WorkerController`
- `service/ProjectServiceAdapter`, `model.Project`/`ProjectTrack`
- Spring configuration/CORS/storage and in-memory worker jobs/SSE
- canonical application service commands/queries

## Changes

- Record retain/deprecate decision and migration inventory/backups.
- If retained, define versioned REST DTOs and map controllers to canonical
  project handles/application commands; never expose internal DTOs/absolute paths.
- Replace REST-owned project mutation and in-memory job authority with canonical
  stage-run commands/snapshots; SSE may observe persisted runs.
- Define authentication/binding/CORS/path-confinement expectations before remote
  or multi-user support is claimed.
- If deprecated, freeze mutations, document/export existing legacy data, remove
  support claims only after recovery tooling/tests.
- Never dual-write stores or auto-import unidentified legacy data.

## Files

Spring controllers/services/DTO/config/tests, legacy export/migration tooling if
needed, README/API docs. Canonical domain remains unchanged except adapter DTOs.

## API / Contracts

Versioned REST endpoints mirror typed settings/harmony/part/stage/structure/build
commands and safe queries. Long work returns run ID/status, not server memory job.

## UI

No Compose change.

## Backend

This task owns the Spring decision/adapter and legacy store disposition.

## Python Worker

Spring must not become a second worker orchestrator; canonical runner invokes it.

## Tests

DTO validation, path redaction/confinement, revision conflicts, run/SSE recovery,
legacy export/migration, CORS/binding policy, no dual-write.

## Acceptance

- There is one composition project authority.
- Existing legacy Spring data has a documented recoverable disposition.
- API support claims match tested deployment/security scope.

## Out of Scope

Cloud hosting, accounts/collaboration, public multi-tenant service.

