# Task 058 — Repository Guards and Final Documentation

## Goal

Prevent legacy frontend drift, reconcile all documentation, and close the
desktop-first implementation sequence with a full verification record.

## Dependencies

- Task 057 accepted.

## Requirements

- Add a simple offline guard test/check rejecting tracked legacy web source,
  `frontend_server.py`, `make frontend`, port-3000 user instructions, and SPA
  fallback references. Allow explicitly documented historical/audit references.
- Reconcile README, architecture, baseline annotations, plans, task index,
  Makefile help, troubleshooting, and package instructions with actual commands.
- Update the Task 056 audit statuses and create separate future tasks for any
  unresolved noncritical bugs; do not silently mark them fixed.
- Run the full verification matrix and record versions, commands, automated /
  manual results, optional-dependency skips, package contents, visual checks,
  canonical hashes, and remaining limitations in a dated quality report.

## Tests

- Guard self-test, full root/desktop/worker suites, CLI/API smokes, current-OS
  package launch, representative build/preview, and `git diff --check`.

## Acceptance criteria

- Documentation and repository contents agree; legacy frontend cannot return
  unnoticed; all remaining issues are explicit tasks or documented limitations.

## Out of scope

Implementing deferred bug tasks, cross-platform claims, or new features.
