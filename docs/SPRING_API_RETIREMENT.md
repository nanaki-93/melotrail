# Spring API retirement

## Decision

Task 028 deletes the Spring JSON API. Task 001's checked support matrix found
no supported caller for its health, project, audio, configuration, worker-job,
or SSE routes. Compose Desktop directly uses the typed canonical application
services and file-backed project artifacts; retaining Spring would keep a
second, incompatible project authority.

## Removed surface

- Spring Boot build plugins, dependencies, `bootRun`, server configuration, and
  application properties.
- All REST routes, DTOs, CORS/SSE handling, upload/storage service, and
  server-only tests.
- The `model.Project`/`ProjectTrack` JSON CRUD store and the in-memory
  `WorkerJobService` wrapper. The direct, typed `WorkerClient` boundary remains.

## Legacy data disposition and recovery

The former store locations were `data/projects/`, `data/audio/`, and
`data/config/server-config.json`. They are not canonical Melotrail projects or
artifacts and are never auto-imported. This retirement does not delete or write
those paths.

Before manually removing legacy data from an existing checkout, create a
read-only archive outside the repository, record SHA-256 hashes for every
included file, extract it to a fresh directory, and compare the hashes. Keep
that archive with the checkout's prior commit so the old REST format can be
inspected or exported deliberately from Git history. Do not copy a legacy JSON
file into a canonical project directory or treat it as `project.json`.

For this checkout's Task 028 inventory, `data/projects/`, `data/audio/`, and
`data/config/` contain no legacy records; no backup or migration was required.
The ignored `data/.DS_Store` is not application data.

## Support boundary

There is no REST API, remote binding, authentication, CORS, multi-user, or SSE
support claim. The separate local Python worker continues to expose only its
documented HTTP command endpoints and is started independently when needed.
