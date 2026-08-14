# Task 056 — Repository-Wide Bug and Cleanup Audit

## Goal

Create an evidence-based repository health report and bounded follow-up tasks;
do not mix the audit with speculative fixes or deletions.

## Dependencies

- Task 055 accepted.

## Requirements

- Write a dated `plan/quality-reports/056-repository-audit.md`. Each finding has
  ID, severity, area, reproduction/command, expected/actual, evidence/location,
  regression/pre-existing classification, risk, disposition, and task/status.
- Run/inspect root and desktop compile/tests/build, worker tests, CLI help/smokes,
  Spring API smokes, package contents, canonical fixture flows, dependencies,
  resource ownership, error handling, concurrency/leaks, CWD paths, source
  safety, dead/duplicate code, docs/scripts, and tracked generated artifacts.
- Inventory every legacy frontend file/reference: static tree, Python server,
  Makefile, README/plans, Spring SPA fallback, tests, assets, ports, packaging,
  and build output.
- Do not fix defects in this task. Create one numbered task file per verified
  P0/P1 or retirement-blocking defect, starting at 059, with a narrow contract.
  Use `plan/BUG_TASK_TEMPLATE.md`. Group only defects with the same proven root
  cause. Record P2/P3 as deferred unless the user explicitly promotes them.

## Tests

- This task is read-only except plan/report files. Record every command/result
  and confirm no project/source artifacts changed.

## Acceptance criteria

- The audit is reproducible, separates known failures, and makes every critical
  fix a small explicit task suitable for Qwen3-Coder-30B.

## Out of scope

Fixing findings, frontend deletion, or broad refactoring.
