# Task 057 — Deprecated Static-Frontend Retirement

## Goal

Remove the complete deprecated HTML/CSS/JavaScript frontend and its serving path
after the Compose workflow and retirement audit are accepted.

## Dependencies

- Task 056 accepted.
- Every P0/P1 or retirement-blocking task created by Task 056 completed.

## Requirements

- Reconfirm Compose and CLI do not call the static frontend/Python server.
  Preserve Spring `/api/**`; removing the API requires a separate product task.
- Remove the inventoried `src/main/resources/static/` files with explicit patch
  paths, including HTML, CSS, JS, browser assets, and browser-only test page.
- Remove `tools/frontend_server.py`, obsolete `make frontend` variables/target/
  help, and Spring SPA fallback serving `static/index.html`.
- Choose/document/test normal 404 or explicit API-only root behavior. Unknown
  `/project/<id>` must not redirect to deleted HTML.
- Remove only confirmed browser-only code/references. Prove usage before removing
  shared API DTO/service code. Never delete projects, `sounds/`, worker, CLI, or
  Compose assets.

## Tests

- Root/desktop tests/build, relevant worker tests, Spring API/root route tests,
  Make help, tracked-reference search, JAR/package content inspection, and a
  packaged desktop smoke with project/source hash comparison.

## Acceptance criteria

- Compose Desktop is the only UI; no static frontend source/server/SPA fallback
  remains, while API/CLI/worker/desktop workflows stay verified.

## Out of scope

Repository-wide cleanup beyond the audited retirement set or replacing Spring.
