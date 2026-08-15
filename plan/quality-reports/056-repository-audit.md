# Task 056 — Repository-Wide Bug and Cleanup Audit

Date: 2026-08-15
Scope: Kotlin engine, Compose Desktop, Python worker, CLI, Spring API, local
package, canonical artifacts, legacy browser frontend, repository hygiene.

## Result and follow-up gate

No verified P0, P1, or retirement-blocking defect exists outside the explicit
Task 057 retirement contract. Therefore this audit creates no Task 059+ files.
Task 057 already owns the complete, deliberate static-frontend removal listed
below; it is not a surprise audit finding to be folded into an unrelated fix.

P2/P3 findings are recorded as deferred. No source, project, audio, worker, or
static-frontend file was modified by this audit. Task 058 assigned the deferred
items to future contracts 059–062; Task 057 completed the retirement scope.

## Reproducible verification record

| Surface | Command / inspection | Result | Classification |
|---|---|---|---|
| Root Kotlin tests + Compose tests | `./gradlew test :desktopApp:test` | Pass (all tasks up-to-date after Task 055 verification). | Current pass |
| Root + desktop builds | `./gradlew build :desktopApp:build` | Pass. | Current pass |
| Current-OS package | `./gradlew :desktopApp:packageDistributionForCurrentOS` | Pass; DMG and `.app` exist under `desktopApp/build/compose/binaries/main/`. | Current pass |
| Compatibility fixture flow | `./gradlew :test --tests 'ai.music.workstation.application.EndToEndWorkflowCompatibilityTest' --rerun-tasks` | Pass; fake boundaries cover direct MIDI, clean/noisy WAV, MP3, source hashes, atomic failures, stale invalidation, and v1/v2/v3 reads. | Current pass |
| Worker tests, shell interpreter | `python -m unittest discover -s worker/tests` | Fails before collection: Python 3.12.0 lacks `numpy` and `mido`. | Pre-existing local environment issue |
| Worker tests, documented virtualenv | `.venv/bin/python -m unittest discover -s worker/tests` | Pass: 34 tests. Librosa/mpg123 warnings occur only on deliberately tiny/crafted fixtures. | Current pass |
| CLI public surface | `./gradlew cliRun --args='--help'` | Exit 0; legacy and MIDI-first commands are listed. | Current pass; wording drift below |
| Spring API isolation smoke | `SERVER_PORT=18080 PROJECT_STORAGE_PATH=<temp> AUDIO_STORAGE_PATH=<temp> java -jar build/libs/ai-music-workstation-0.1.0.jar`; `curl http://localhost:18080/health`, `/api/config`, `/project/audit-route` | `/health` returns `{"status":"ok"}`; `/api/config` returns 200; SPA route serves `static/index.html`. Temporary storage only. | Current legacy behavior |
| Source/artifact ownership | `git status --short`; fixture and package inspections | Only pre-existing user plan changes plus this report are present; no tracked build/package output except Gradle wrapper JAR. | Current pass |

The initial API probe used `/api/health` and the first jar-name attempt used
`0.0.1-SNAPSHOT`; both are audit-harness mistakes, corrected to `/health` and
`build/libs/ai-music-workstation-0.1.0.jar`. They are not product findings.

## Findings

### AUD-056-01 — Default shell Python cannot run the worker test suite

| Field | Evidence |
|---|---|
| Severity | P2 |
| Area | Worker developer setup / test command |
| Reproduction | `python --version && python -m unittest discover -s worker/tests` |
| Expected | The selected interpreter provides pinned worker dependencies. |
| Actual | Python 3.12.0 cannot import `numpy` or `mido`; all seven discovered modules fail before tests run. |
| Evidence/location | Audit command output; `Makefile` intentionally uses `.venv/bin/python` for `make worker`. `.venv/bin/python` runs all 34 tests successfully. |
| Classification | Pre-existing local environment configuration, not a source regression. |
| Risk | A contributor can run the wrong interpreter and misdiagnose healthy worker code. |
| Disposition | Deferred P2 documentation/setup clarity. |
| Task/status | Future Task 059; not active without explicit promotion. |

### AUD-056-02 — CLI help describes a retired worker mode

| Field | Evidence |
|---|---|
| Severity | P2 |
| Area | CLI help / documentation drift |
| Reproduction | `./gradlew cliRun --args='--help'` |
| Expected | Help describes the active HTTP worker configuration consistently with README and `WorkerClient`. |
| Actual | The legacy option says `--worker-url` is “deprecated” and “no longer used, process-based worker,” while current CLI/desktop services use `WORKER_BASE_URL` and HTTP worker clients. |
| Evidence/location | `CliMain` help output; `ArrangementProjectCommands.kt` and desktop readiness use `WORKER_BASE_URL`. |
| Classification | Verified pre-existing documentation/interface drift. |
| Risk | Confusing recovery/setup guidance; no demonstrated artifact corruption. |
| Disposition | Deferred P2; do not alter CLI behavior in this audit. |
| Task/status | Future Task 060; not active without explicit promotion. |

### AUD-056-03 — Legacy `/api/config` does not report process environment overrides

| Field | Evidence |
|---|---|
| Severity | P2 |
| Area | Spring API configuration adapter |
| Reproduction | Start the jar with temporary `SERVER_PORT`, `PROJECT_STORAGE_PATH`, and `AUDIO_STORAGE_PATH`; request `GET /api/config`. |
| Expected | If this legacy API is retained, its configuration response should either reflect active server configuration or explicitly identify its separate persisted configuration. |
| Actual | The server listens on the supplied temporary port, but `/api/config` returns its independent literal defaults (`8080`, `data/projects`, `data/audio`). |
| Evidence/location | `ConfigService.defaults()` and its separate `data/config/server-config.json`, versus `application.properties`/`ServerConfig`. |
| Classification | Verified pre-existing legacy-API behavior. |
| Risk | Misleading API configuration display/update semantics; Compose and CLI do not use this adapter. |
| Disposition | Deferred P2. The API was preserved during Task 057. |
| Task/status | Future Task 061; not active without explicit promotion. |

### AUD-056-04 — Static frontend is fully present and bundled with the desktop package

| Field | Evidence |
|---|---|
| Severity | Retirement scope (already contracted) |
| Area | Browser frontend, Spring fallback, scripts, documentation, package contents |
| Reproduction | `find src/main/resources/static -type f`; inspect `tools/frontend_server.py`, `Makefile`, `WebController.kt`, README, plans, and packaged app `Contents/app`. |
| Expected | The deprecated frontend remains only until its controlled retirement gate. |
| Actual | 24 tracked static files (about 256 KiB), Python static-server helper, Make target/port-3000 documentation, `/project/{id}` SPA fallback, static test page, and packaged root-engine JAR resources remain. |
| Evidence/location | `src/main/resources/static/`, `tools/frontend_server.py`, `Makefile`, `README.md`, `src/main/kotlin/ai/music/workstation/server/config/WebController.kt`, package contents. |
| Classification | Intentional pre-retirement state, not an unplanned regression. |
| Risk | Desktop package carries obsolete UI and users can start two divergent frontends. |
| Disposition | Completed by Task 057 while preserving JSON API controllers; Task 058 adds the drift guard and reconciles docs. |
| Task/status | Completed; no new blocker. |

### AUD-056-05 — CWD-relative paths remain in legacy/server development fallbacks

| Field | Evidence |
|---|---|
| Severity | P3 |
| Area | Legacy CLI/Spring storage and development discovery |
| Reproduction | `rg -n 'Path.of("sounds")|Paths.get("data|127.0.0.1:8081' src/main/kotlin desktopApp/src/main/kotlin -g '*.kt'` |
| Expected | Packaged desktop paths use an injected absolute library root; configurable server storage may retain documented local defaults. |
| Actual | Desktop runtime uses the validated locator/settings boundary, but legacy `AudioPipeline` hardcodes the default worker URL and Spring adapters default to CWD-relative `data/` paths. `SoundLibraryLocator` retains a documented development-only `sounds` fallback. |
| Evidence/location | `SoundLibraryLocator.kt`, `AudioPipeline.kt`, `ProjectServiceAdapter.kt`, `AudioService.kt`, `ConfigService.kt`. |
| Classification | Mixed intentional compatibility/default behavior; no packaged-desktop failure reproduced. |
| Risk | Legacy launchers can use unexpected local storage or worker endpoint. |
| Disposition | Deferred P3; do not widen Task 057 beyond frontend removal. |
| Task/status | Future Task 062; not active without explicit promotion. |

## Safety, resource, and compatibility review

- Canonical project flows are backed by the passing compatibility fixture; source
  hashes, PCM artifacts, atomic failure behavior, stale invalidation, and legacy
  v1/v2/v3 reads are covered with offline fakes.
- The package contains the Java runtime and app resources but no bundled local
  sound pack, renderer, worker process, or transcription model. Task 055’s
  smoke verified explicit library selection/override and truthful missing-worker
  feedback from outside repository CWD.
- Worker, renderer, model, and audio-output availability remain runtime gates;
  none is claimed ready by this audit.
- No tracked generated build, DMG, or application output was found; the only
  matching tracked binary is the intentional Gradle wrapper JAR.
- The legacy static assets are the only material dead/duplicate UI surface
  identified. Their removal is intentionally deferred to Task 057; no API,
  project data, sound library, or worker deletion is authorized in this task.

## Legacy frontend retirement inventory for Task 057

1. `src/main/resources/static/`: `index.html`, `test-store.html`, icon, five
   CSS files, and 17 JavaScript files/components/views (24 files total).
2. `tools/frontend_server.py`; `Makefile` frontend host/port variables, target,
   and help text; README local frontend instructions and port-3000 references.
3. Spring welcome-page/static-resource handling and
   `server/config/WebController.kt` `/project/{id}` fallback. Keep `/api/*`
   controllers and the root server application.
4. Static references in plans/README that describe the deprecated UI; preserve
   historical planning context only where it remains accurate after retirement.
5. Packaged root-engine JAR resources, which will cease to carry static files
   once the tracked tree is removed and package smoke is repeated.

## Audit limitations

- No real SFZ renderer, Basic Pitch runtime, or production audio device was
  installed. Their failure/recovery boundaries were inspected and fake-boundary
  tests passed; musical listening and real rendering remain unclaimed.
- The worker’s normal shell interpreter is intentionally not repaired in this
  read-only task. The configured virtualenv is the reliable test baseline.
- This report does not delete or modify the legacy frontend; Task 057 remains
  the earliest permitted deletion task.
