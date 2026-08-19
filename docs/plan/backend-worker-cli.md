# Application services, optional backend, worker, and CLI

## Canonical application boundary

Compose is the current supported frontend, but its commands/queries must remain
UI-neutral. Add capability services behind `ProjectApplicationService` facade as
the relevant task lands:

- composition settings and profile catalog;
- harmony editing;
- part/source management;
- stage-run orchestration/query/selection;
- structure occurrences;
- arrangement/cohesion/humanization;
- build/mix/master/export/provenance.

Do not split every existing method up front. Extract an interface when new code
needs a clear owner and retain facade delegation for compatibility.

## Command/query conventions

- Commands accept project identity/root through a validated project handle, not
  arbitrary nested absolute paths.
- Mutation requests include expected project/revision where lost updates matter.
- Long-running commands return `StageRunId`; progress is queried/observed from
  persisted snapshots.
- DTOs use stable IDs, structured musical primitives, project-relative artifact
  identifiers, safe error codes, and versioned schemas.
- Internal file/model/worker types do not leak into UI/REST contracts.
- Application services own permissions, path confinement, locking, atomic writes,
  and invalidation.

## Python worker

The worker remains a stateless computation service. Extend protocol negotiation
so `/health` reports command/schema versions/capabilities. Commands validate
request/result, accept unique output paths prepared by Kotlin, and never decide
which artifact is selected.

Retain synchronous HTTP initially: the Kotlin stage runner supplies durability
and runs calls off the UI thread. Do not combine this with the Spring-only
in-memory job store. If cancellation/resumable model execution becomes necessary,
design a real worker job protocol later and prove process/request cancellation.

Every new/changed command requires matching Kotlin `WorkerProtocol`, Python
schema validation, success/error fixtures, timeout behavior, and versioned
capability checks.

## Optional Spring API

Current REST project CRUD uses `model.Project` and `ProjectServiceAdapter`, an
independent data store. Target options, decided with usage evidence:

1. Retain: controllers translate versioned REST DTOs into canonical application
   commands/queries and a configured registry resolves project handles.
2. Deprecate: make the unsupported status explicit, freeze mutation endpoints,
   provide any necessary data export, then remove in a later release.

Never dual-write canonical and legacy project JSON. Do not auto-import a legacy
REST store without inventory, mapping, backup, and tests. If REST remains local,
review CORS/binding/path exposure before claiming multi-user or remote support.

SSE may expose persisted stage snapshots if REST is retained. The REST layer
does not own jobs and restart recovery remains the stage runner's responsibility.

## CLI

The current repository has stale README/Gradle references but no CLI source.
First make support claims truthful. Then decide whether automation has a real
consumer.

If retained/reintroduced, the CLI is a thin adapter over the same application
commands/queries and supports machine-readable results plus stage-run polling.
It must not reconstruct the removed monolithic file/audio pipeline, infer
current artifacts independently, or write project JSON directly.

Potential later commands are project inspect/migrate, settings/harmony/part
commands, stage run/status/retry/select, structure/arrange/build, mix/master,
export, and provenance verify. Exact command compatibility is not promised until
historical usage and tests justify it.

## Configuration ownership

| Configuration | Owner/location |
| --- | --- |
| key/harmony/tempo/meter/mood/profile/creative choices | portable project |
| resolved per-run policies/seed/model version | stage record |
| worker/model URL, renderer, sound-library path, audio device | desktop/local preferences |
| profile definitions | versioned application resources/catalog |
| secrets | local environment/secret store, never project/provenance/logs |

Consolidate parallel config classes opportunistically as adapters move onto the
canonical boundary; do not make configuration refactoring a prerequisite for
the first UI milestone.

