# Task NNN — Short Verified Bug Name

Use this template only for a reproducible finding from Task 056. Allocate the
next unused number starting at 059 and create one task per root cause.

## Audit finding

- Finding ID:
- Severity: P0 / P1 / retirement-blocker
- Evidence/report link:
- Reproduction command or steps:
- Expected behavior:
- Actual behavior:

## Goal

State one observable corrected outcome.

## Dependencies

- Task 056 accepted.
- List only contracts/artifacts genuinely required by this fix.

## Root-cause boundary

Name the proven failing component and the smallest production boundary that may
change. List nearby components that must remain unchanged.

## Requirements

- Specify the minimal fix and retained compatibility/safety behavior.
- Add a regression test that fails before and passes after the fix.
- Do not combine cleanup or unrelated findings.

## Tests

- Focused regression command.
- Required module/full-suite commands based on the affected area.
- Manual reproduction only when automation cannot prove the outcome.

## Acceptance criteria

- The exact reproduction is fixed, regression coverage passes, and no unrelated
  behavior/artifact changes.

## Out of scope

List adjacent refactors and every other audit finding.
