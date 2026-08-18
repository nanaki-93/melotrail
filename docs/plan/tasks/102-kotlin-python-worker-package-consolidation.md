# Task 102 — Kotlin Python-Worker Package Consolidation

## Goal

Make `app.melotrail.worker` the clear Kotlin boundary for all code that models,
calls, monitors, or maps work performed by the separate Python HTTP worker.

## Dependencies

- Task 101 accepted.

## Requirements

- Inventory Kotlin symbols that exist solely for the Python worker protocol:
  command/request/response schemas, HTTP client, health/readiness mapping, job
  submission/progress mapping, and worker-specific queue types. Keep desktop
  UI and Spring controller adapters outside this package.
- Move worker-owned classes currently under server service packages into a
  coherent `app.melotrail.worker` package layout (subpackages are allowed when
  they make ownership clearer). Update every import, Spring bean declaration,
  test, and document reference atomically.
- Define one authoritative worker command-to-endpoint mapping and one typed
  response/error contract. Remove only duplicate aliases or compatibility
  methods proven unused; retain a deprecated compatibility type temporarily if
  external code requires it and document its removal path.
- Keep protocol behaviour exact: endpoint names, JSON field names, timeout
  semantics, worker-not-running errors, and the fact that Kotlin never starts
  or manages the Python process.
- Keep all blocking/network work off the Compose UI dispatcher and avoid
  exposing filesystem paths or source content in readiness diagnostics.

## Tests

- Command endpoint/payload and response/error mapping tests for every supported
  Python endpoint.
- Worker job lifecycle/progress tests, including failure and cancellation
  states, after relocation.
- Root, desktop (if imports change), and Python worker test suites.

## Acceptance criteria

- A developer can locate all Kotlin Python-worker protocol and execution code
  from `app.melotrail.worker`; server code only adapts HTTP/SSE requests to that
  boundary.
- No duplicate worker transport or job implementation remains, and all existing
  supported endpoints work with unchanged payloads.

## Out of scope

- Rewriting the Python worker, adding process supervision, or changing worker
  HTTP endpoints.
