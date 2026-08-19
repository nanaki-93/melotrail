# Task 012 — Persistent stage runner and recovery

## Goal

Implement the single application-owned orchestrator for stage locking, caching,
status, execution, atomic publication, recovery, retry, and downstream chaining.

## Why

Compose must not coordinate worker/model calls, and Spring's in-memory job wrapper
does not provide durable desktop recovery or real cancellation.

## Dependencies

Task 011.

## Existing Code

- project-root mutex and typed services in `ProjectApplicationService.kt`
- `WorkerClient`/`WorkerProtocol`, model/renderer fakeable ports
- artifact temp/validation/atomic publication patterns
- `WorkflowReadModel`

## Changes

- Define `StageProcessor` port, eligible-stage/dependency registry, normalized
  cache-key builder, and `StageRunner` commands/query snapshots.
- Persist Processing before work and Completed/Failed afterward; publish unique
  outputs atomically only after validation.
- On project open, recover stale Processing attempts as interrupted failures and
  make them retryable.
- Make run/retry idempotent and reject/converge duplicate concurrent requests by
  project/subject/stage/cache key.
- Chain automatic stages until a user-input/review gate, failure, or readiness
  boundary. Persist after every stage.
- Implement cancellation only as unsupported/stop-after-current initially unless
  the called dependency supports verified cancellation.
- Map snapshots into workflow readiness without UI polling file existence.

## Files

Add stage-runner/application/processor registry files and tests; wire facade,
project open recovery, read model, and desktop composition root.

## API / Contracts

`RunStage`, `RetryStage`, `GetStageRuns`, `ObserveStageRuns`; returns run ID and
safe current snapshot. Processor result contains temp output/report metadata only.

## UI

No full page; expose observable status/progress/retryability for Task 014.

## Backend

Kotlin owns durability. Do not route Compose through `WorkerJobService`.

## Python Worker

Existing synchronous calls remain; runner records timeout/error/capability info.

## Tests

Success/cache hit/input or config change, failure/retry, crash recovery, duplicate
concurrency, timeout, invalid output, atomic failure, chain stop/restart, reopen.

## Acceptance

- Restart/reopen never loses completed work or treats partial output as complete.
- Retrying a failed stage does not rerun unchanged upstream stages.
- UI thread never executes worker/model/renderer work.

## Out of Scope

Distributed queues, remote workers, true cancellation, or stage algorithms.

