# Task 072 — Release acceptance record

Review date: 2026-08-17  
Status: **NOT APPROVED FOR RELEASE**

This is an evidence record, not a release declaration. The automated and
packaging checks below passed, but the required live renderer/model workflow,
listening matrix, visual comparison, accessibility review, and interactive
new/legacy-project package smoke remain incomplete. Do not claim support for
those environments until the listed manual gates are completed.

## Repository and policy review

- Re-read the official YouTube [AI-use disclosure guidance](https://support.google.com/youtube/answer/14328491)
  and [channel monetization policies](https://support.google.com/youtube/answer/1311392?hl=en)
  on 2026-08-17. The former explicitly lists AI-generated music among examples
  that require disclosure when realistic; the latter evaluates channel-level
  originality/authenticity and does not guarantee monetization.
- Reviewed `pictures/UI/example.png`. It establishes the intended dark desktop workspace,
  five top-level destinations, hierarchy, and persistent transport reference.
  No application screenshots were captured in this non-interactive run, so no
  wide/medium/narrow visual comparison is signed off.

## Automated evidence

| Command | Result |
| --- | --- |
| `./gradlew clean test :desktopApp:test :desktopApp:build` | Passed. Kotlin root/CLI tests and desktop Compose tests passed from a clean build. Compiler warnings only; no test failure. |
| `.venv-worker/bin/python -m unittest discover -s worker/tests` | Passed: 34 tests under Python 3.11.16. Fixture/dependency warnings only. |
| `./gradlew :desktopApp:test --tests app.melotrail.desktop.LocalDesktopOperationLoggerTest` | Passed. Regression test for diagnostic path/source-name redaction. |
| `./gradlew :desktopApp:test :desktopApp:build :desktopApp:packageDistributionForCurrentOS` | Passed. Native macOS DMG built with its runtime. |
| `make check-legacy-frontend` | Passed. The optional Spring service has no browser frontend fallback. |

Existing offline fixture coverage is the evidence for the following bounded
flows; all collaborators are fake or deterministic and make no network calls:

| Required representative case | Evidence |
| --- | --- |
| Direct MIDI / Original Feel; direct MIDI / fixed 80-BPM Lo-fi Feel | `ProjectApplicationServiceTest` verifies immutable source/raw/clean MIDI, separate Lo-fi derived MIDI, and restoring cleaned MIDI. |
| WAV inspection, explicit cleanup, transcription | `EndToEndWorkflowCompatibilityTest` exercises clean and noisy WAV fixtures through the application services. |
| MP3 decode/transcription path | The same compatibility test covers an MP3 fixture; worker input-inspection and transcription tests cover the Python boundary. Live optional decoder/model inference was not run. |
| Repeated occurrences with distinct cohesion edits | `MelodyCohesionTest` verifies distinct bounded results for `A1` and `A2`, source preservation, atomic approval, and unsafe-plan rejection. |
| Deterministic and Qwen arrangement paths | `ArrangementApplicationServiceTest`, `GlobalSongPlannerTest`, and `DetailedArrangementTest` cover deterministic approval, explicit Qwen drafts, strict JSON, and unsafe-field rejection. |
| Commercial-ready and blocked cases | `CommercialProvenanceTest`, `ModelLicenseTest`, and cohesion commercial tests cover deterministic evidence and unresolved/blocked dependency cases. |

The tests verify canonical-artifact validation, stale-state handling, atomic
publication, and source/raw/cleaned immutability at their boundaries. They do
not substitute for a live full-project hash manifest taken before and after an
actual renderer/worker run.

## Packaging and diagnostic-log evidence

- The native macOS DMG was built successfully. Its rebuilt SHA-256 was
  `778407fe36eeb3528d3444dbbc92bb60f24f3f8ca7e558e4c50d8cb7f6a653db`.
- A copy installed into an isolated temporary location started successfully
  with the bundled Java runtime. This is a process-start smoke only; it did not
  interact with a project picker or a project window.
- The smoke found a release-blocking diagnostic leak: desktop logs wrote an
  absolute project path. Task 072 fixes it by recording only the fixed artifact
  class (`midi`, `wav`, `mp3`, `json`, `directory_or_extensionless`, or `other`).
  The rebuilt installed-package startup record contains no absolute path,
  source name, secret marker, or model-response marker.
- A non-destructive scan found no secret/model-response markers in the current
  project artifacts. An earlier diagnostic log containing the pre-fix absolute
  path remains inspectable evidence; it is not treated as a successful release
  artifact.

## Manual acceptance matrix — still required

| Gate | Result / recovery action |
| --- | --- |
| Source and prepared WAV playback | Not run. Configure the worker and complete explicit inspection/cleanup on a solo-piano fixture. |
| Raw, cleaned, and Lo-fi MIDI renders | Not run. Install/configure a validated local `sfizz_render`, then capture pre/post source/raw/cleaned hashes. |
| Cohesion boundary, dry mix, audio texture, and master playback | Not run. Run the renderer-backed full workflow and listen on a real output device using the one transport. |
| Listening A/B environment | A built-in 44.1-kHz, two-channel MacBook Pro speaker output was detected, but no listening test was performed. Record device, OS, output level, listener, and pass/fail for each artifact. |
| Wide, medium, narrow screenshots | Not run. Compare all three against `pictures/UI/example.png`; record intentional visual differences and repair clipping, duplication, scrolling, spacing, hierarchy, or color regressions. |
| Keyboard, focus, screen-reader labels, contrast | Automated Compose tests cover semantic status text/icons, focusable structure controls, shortcuts, and layout breakpoints. A real keyboard-only and assistive-technology pass remains required. |
| Installed-package new and legacy project open | Startup smoke passed only. In the installed app, create a new project and open a legacy v1/v2 fixture without rewriting it; record the result. |

## Unavailable or unverified dependencies

- The approved local sound pack is present (25 sample WAV files), but no
  `sfizz_render` executable was available on `PATH`; renderer-backed MIDI
  preview, stems, mixes, and master listening were not verified.
- No local Qwen/LM Studio request was made. Offline strict-schema fixture tests
  passed, but no model output or model license was accepted for this release.
- MP3 worker coverage passed offline. An end-to-end optional MP3 export and a
  real solo-piano MP3 transcription have not been signed off.
- macOS packaging was built and process-started here. Windows and Linux were
  not built or tested and are not supported by this record.

## Sign-off

| Role | Decision | Date |
| --- | --- | --- |
| Engineering acceptance | **Withheld** — automated checks and package startup pass; manual gates above remain. | 2026-08-17 |
| Audio/listening owner | Pending. | — |
| Visual/accessibility owner | Pending. | — |
| Release owner | Pending; do not publish or upload. | — |

Deferred work is limited to completing the stated manual checks with configured
local dependencies and recording their evidence. No cloud publication,
telemetry, sample download, or unrelated refactor is authorized by this task.
