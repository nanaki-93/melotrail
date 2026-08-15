# Task 058 — Repository Guards and Final Verification

Date: 2026-08-15
Host: macOS 26.6.1 (Apple Silicon)
Toolchain: Eclipse Temurin JDK 21.0.11, Gradle 8.14.3, Python virtualenv 3.12.14

## Result

Accepted. The Compose Desktop application is the documented product UI, the
retired browser frontend has an offline regression guard, and the retained
Spring service is documented as a local JSON API rather than a UI host.

## Guard and documentation reconciliation

- `tools/check_no_legacy_frontend.sh` rejects tracked `src/main/resources/static/`
  files, `tools/frontend_server.py`, browser launch targets, port-3000
  instructions, and SPA/static fallback references (including a catch-all
  Spring resource handler) in active source and user documentation. Historical
  plan/audit records remain intentionally outside the scan.
- `make check-legacy-frontend` and `make check` both passed. The latter runs
  the guard before Gradle `check`.
- README, troubleshooting, baseline and product plans, Make help, package
  guidance, and the Task 056 audit now describe the desktop-first/API-only
  state. The task index contains a pre-existing user-staged Task 054 move and
  was deliberately not modified or committed by this task.
- `git ls-files` and the packaged application jars contain no retired static
  frontend entries. `git diff --check` passed.

## Automated verification

| Command | Result |
| --- | --- |
| `bash tools/check_no_legacy_frontend.sh` | Passed. |
| `make check-legacy-frontend` | Passed. |
| `make check` | Passed; guard then Gradle verification. |
| `./gradlew build :desktopApp:build` | Passed. |
| `./gradlew :test --tests 'ai.music.workstation.application.EndToEndWorkflowCompatibilityTest' --rerun-tasks` | Passed. Fake worker, renderer, and audio-device boundaries exercise representative build, preview, source-hash, and compatibility flows offline. |
| `./gradlew cliRun --args='--help'` | Passed. |
| `.venv/bin/python -m unittest discover -s worker/tests` | Passed: 34 tests. Fixture-level librosa/mpg123 warnings were emitted. |
| `./gradlew :desktopApp:packageDistributionForCurrentOS` | Passed; produced the macOS DMG and `.app`. |

The Kotlin compilation emitted the pre-existing warnings recorded in Task 056,
including deprecated `toChar`, logger parameter-name differences, and unchecked
provenance serializer casts. They did not fail the build.

## API and package smoke

The packaged Compose app was launched from a fresh temporary working directory
with `MUSIC_SOUNDS_ROOT` set to the repository's validated local `sounds/`
directory. The process started, then was quit normally. SHA-256 values for
`projects/my-song/project.json` and `projects/my-song/midi/phrase1.mid` matched
before and after launch.

The Spring Boot jar was started with isolated temporary project/audio storage:

| Endpoint | Observed result |
| --- | --- |
| `GET /health` | 200, `{"status":"ok"}` |
| `GET /` | 404 |
| `GET /project/retired-browser-route` | 404 |
| `GET /api/worker/health` | 200 |

The packaged application jars were inspected with `jar tf`; no `static/` or
`BOOT-INF/classes/static/` entry remained.

## Manual visual check

Task 058 does not change Compose UI code. The current package launch above was
successful; the applicable 1440x900, 1100x720, and HiDPI visual/interactivity
record remains [Task 055's package-smoke report](055-documentation-and-package-smoke.md).
That checked the desktop workspace, responsive layout, keyboard focus, disabled
worker recovery, and non-repository-CWD launch.

## Deferred work and limitations

Task 056's noncritical findings are explicit deferred contracts outside the
active queue:

- `plan/future-tasks/059-worker-test-environment-contract.md`
- `plan/future-tasks/060-cli-worker-help-accuracy.md`
- `plan/future-tasks/061-api-config-runtime-truthfulness.md`
- `plan/future-tasks/062-legacy-cwd-defaults.md`

They are not P0, P1, or retirement blockers and require explicit promotion
before implementation. Invoking the shell's bare `python -m unittest` remains
an environment failure because it lacks the worker dependencies; the supported
virtualenv command above passes.

This verification does not claim a real SFZ renderer, Basic Pitch runtime,
audio output device, signing/notarization, or non-macOS package support. No
source audio, MIDI, project artifact, sound-library asset, or worker algorithm
was modified by Task 058. The only source change removes the obsolete
catch-all Spring resource handler so a later `classpath:/web/` addition cannot
become an accidental browser fallback.
