# Task 028 — Spring API retain-or-delete migration

## Goal

Using Task 001 usage evidence, either adapt a demonstrably used Spring API to
canonical composition services or migrate/export required data and delete the
obsolete Spring product surface completely.

## Why

The current independent `model.Project`/`ProjectServiceAdapter` store would split
the product domain and cannot safely implement the new workflow in parallel.

## Dependencies

Tasks 005, 008, 010–012, 021, 027B, and Task 001 usage inventory. This optional
adapter must not block desktop MVP.

## Existing Code

- `server/api/ProjectController`, `AudioController`, `WorkerController`
- `service/ProjectServiceAdapter`, `model.Project`/`ProjectTrack`
- Spring configuration/CORS/storage and in-memory worker jobs/SSE
- canonical application service commands/queries

## Changes

- Record retain/delete decision and migration inventory/backups. “Deprecated but
  still present” is not an accepted outcome.
- If retained, define versioned REST DTOs and map controllers to canonical
  project handles/application commands; never expose internal DTOs/absolute paths.
- Replace REST-owned project mutation and in-memory job authority with canonical
  stage-run commands/snapshots; SSE may observe persisted runs.
- Define authentication/binding/CORS/path-confinement expectations before remote
  or multi-user support is claimed.
- If unused/obsolete, export or migrate required legacy data, verify recovery,
  then delete controllers, separate model/store, job wrapper, configuration,
  routes, dependencies, resources, tests, and support documentation in this task.
- Never dual-write stores or auto-import unidentified legacy data.

## Files

Spring controllers/services/DTO/config/tests, legacy export/migration tooling if
needed, README/API docs. Canonical domain remains unchanged except adapter DTOs.

## API / Contracts

Versioned REST endpoints mirror typed settings/harmony/part/stage/structure/build
commands, release/credits queries, and safe artifact downloads. Long work returns
run ID/status, not server memory job. REST never constructs attribution itself.

## UI

No Compose change.

## Backend

This task owns the Spring decision/adapter and legacy store disposition.

## Python Worker

Spring must not become a second worker orchestrator; canonical runner invokes it.

## Tests

DTO validation, path redaction/confinement, revision conflicts, run/SSE recovery,
legacy export/migration, CORS/binding policy, no dual-write.

If retained, verify audio/credits hash pairing and safe credits download.

## Acceptance

- There is one composition project authority.
- Existing legacy Spring data has a documented recoverable disposition.
- API support claims match tested deployment/security scope.
- The outcome is either one canonical adapter with supported callers or no Spring
  product surface at all; no frozen/deprecated implementation remains.

## Out of Scope

Cloud hosting, accounts/collaboration, public multi-tenant service.
