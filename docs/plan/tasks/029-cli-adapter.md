# Task 029 — CLI support decision and service-backed adapter

## Goal

Decide whether a CLI has a real supported consumer and either keep it explicitly
retired or implement a thin adapter over canonical application services.

## Why

The source CLI is absent while stale references remain. Recreating its old direct
file/audio pipeline would duplicate artifact selection and business rules.

## Dependencies

Task 001 support inventory and stable canonical commands through Task 027. This
optional task does not block desktop workflow.

## Existing Code

- current `build.gradle.kts`, `Makefile`, `README.md` CLI references
- git-history command names only as compatibility research
- canonical typed application services and stage runner

## Changes

- Gather actual automation/compatibility need and record Retired or Supported.
- Retired path: remove broken build targets/claims and provide concise migration
  guidance; no placeholder executable.
- Supported path: add parser/entry point that invokes canonical commands/queries,
  supports human and machine-readable results, stage run/status/retry/select,
  deterministic exit codes, and safe errors.
- CLI must never write project JSON, infer selected filenames, call worker/model
  directly, or rebuild the removed `AudioPipeline` logic.
- Choose explicit historical command aliases only where tests/users justify them;
  do not promise blanket compatibility.

## Files

Conditional CLI source/tests and Gradle/Makefile/README/help docs, or documentation/
build cleanup for retirement.

## API / Contracts

CLI maps one-to-one to canonical DTO commands/queries and can poll persisted run
IDs. Machine output is versioned JSON and excludes secrets/absolute paths.

## UI

No Compose change.

## Backend

In-process application service composition; no REST dependency required.

## Python Worker

Only canonical stage runner may invoke it.

## Tests

Decision-specific build behavior, help, parse/error/exit codes, JSON schema,
service fake calls, run polling, no direct file/worker access, documentation checks.

## Acceptance

- Build/docs no longer advertise a nonexistent CLI.
- If supported, CLI and Compose produce identical project/run behavior.
- No second workflow/artifact resolver exists.

## Out of Scope

Interactive TUI, shell completion, remote API client, old monolith restoration.

