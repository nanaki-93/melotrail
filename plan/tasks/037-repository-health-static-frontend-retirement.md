# Task 037 — Repository Health Audit and Static-Frontend Retirement

## Goal

Perform a bounded, evidence-based repository cleanup and bug audit, then remove
the deprecated static HTML/CSS/JavaScript frontend and its obsolete serving
path without removing the Compose desktop app, CLI, Spring JSON API, worker, or
canonical project artifacts.

## Dependencies

- Task 036 accepted: the Compose workflow, documentation, and current-OS
  package are usable without the legacy browser frontend.

## Required audit before changing code

- Record a dated `plan/quality-reports/` audit document. For every finding,
  include ID, severity, affected area, reproducible evidence, expected versus
  actual behavior, source location, suggested owner/task, and status. Separate
  pre-existing failures from defects introduced by this task.
- Inventory all tracked static frontend files and every reference to them,
  including `src/main/resources/static/`, `tools/frontend_server.py`, Makefile
  targets/help, README instructions, Spring static-resource/SPA fallback code,
  tests, icons/assets, ports, docs, scripts, CI, packaging, and build outputs.
  Use `rg` plus Gradle/task inspection; do not assume the known paths are the
  complete set.
- Re-run the root Kotlin tests, desktop tests/build, relevant worker tests, CLI
  help/smoke tests, and a local Spring API smoke. Classify every failure before
  touching it. Audit for dead/duplicate code, stale docs, invalid command names,
  source/artifact safety regressions, error swallowing, resource leaks,
  CWD-dependent paths, and dependency-prerequisite failures.
- Fix only bugs that are reproducible, safe, and directly needed for legacy
  retirement or a verified core workflow failure. Add a follow-up task for
  anything larger; do not mix speculative cleanup with deletion.

## Requirements

- Prove that the supported Compose desktop app and CLI do not invoke the static
  browser UI or its Python file server. Preserve the Spring `/api/**` JSON
  controllers if their tests, CLI, or documented local integrations require
  them; this task is not authorization to remove the backend API.
- Remove the complete deprecated frontend source tree only after the inventory:
  `src/main/resources/static/` HTML, CSS, JavaScript, browser-only assets, and
  browser test page. Remove `tools/frontend_server.py` when it has no remaining
  supported caller. Use a patch with explicit paths; do not delete broad
  directories until their inventory is recorded.
- Remove the Spring SPA fallback (`WebController` or its equivalent) that
  serves `static/index.html`; retain and test API routing/error handling. Choose
  either normal 404 or a small explicit API-only root response, document it,
  and add a focused integration test. Never redirect unknown project routes to
  deleted HTML.
- Remove obsolete `make frontend` variables, target, help text, and README
  instructions. Update local startup documentation so the primary supported
  workflow is `./gradlew :desktopApp:run` plus `make worker` when needed;
  describe Spring separately as API/development support only when retained.
- Remove stale static-web dependencies, test fixtures, comments, and package
  resources that are now unreachable. Do not remove shared API DTOs or service
  code merely because the old web UI used them; establish engine/CLI/API usage
  first.
- Add repository guard checks that fail if tracked legacy frontend files,
  `frontend_server.py`, `make frontend`, stale port-3000 instructions, or
  `static/index.html` SPA fallback references are reintroduced. Keep the check
  simple and local (Gradle/script test); it must not require a browser or
  network.
- Update `README.md`, `plan/PLAN_UI_AND_CREATION.md`, `plan/TASKS.md`,
  architecture/baseline/troubleshooting documents, and the audit report to say
  the browser UI was removed and to preserve only truthful, current commands.

## Tests and verification

- Full `./gradlew test`, `./gradlew :desktopApp:test :desktopApp:build`,
  relevant Python worker suite, and any changed CLI/API tests.
- Verify `make help` has no frontend target; supported desktop/worker/CLI
  commands still work; `./gradlew bootRun` exposes retained API endpoints; root
  and `/project/<id>` no longer serve stale HTML.
- Run `rg`-based guard/searches against tracked files to confirm no legacy
  frontend source/reference survives except intentional historical task/audit
  documentation. Verify JAR/package contents contain no deleted static assets.
- Launch the desktop app from the current-OS package and open/build/preview a
  representative project. Compare canonical project/artifact hashes before and
  after the cleanup.

## Acceptance criteria

- The repository has one supported UI: Compose Desktop. The HTML/CSS/JS frontend
  and its serving helper are removed, and no documented command points to port
  3000 or `index.html`.
- The Spring API, CLI, worker, desktop package, project compatibility, and
  canonical audio/MIDI safety guarantees remain verified.
- A dated audit report clearly records fixed defects, deferred defects, and
  pre-existing failures; no unrelated broad refactor is hidden in this task.

## Out of scope

Replacing Spring, deleting the API without an explicit product decision,
rewriting working engine/DSP code, deleting user project data, removing
`sounds/`, adding a new web frontend, or claiming untested OS support.
