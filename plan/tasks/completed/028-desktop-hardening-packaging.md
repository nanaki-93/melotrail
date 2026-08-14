# Task 028 — Desktop Hardening and Current-OS Packaging

## Goal

Turn the functional desktop workflow into a reliable local release candidate with recovery, accessibility, complete tests, documentation, and a verified package for the current OS.

## Dependencies

- Tasks 024–027 functional desktop application.

## Requirements

- Add keyboard focus order, labels/content descriptions, minimum hit targets, keyboard transport shortcuts, and structure-reorder alternatives.
- Add unsaved-draft confirmation, operation-in-progress close confirmation, safe project switching, and restoration of the last successfully opened project path as a preference only.
- Keep canonical song state in project artifacts; desktop preferences must not become a second project store.
- Add structured local logs with operation/stage and diagnostic artifact paths, excluding sensitive or arbitrary model content.
- Handle worker disconnect, renderer configuration changes, stale artifacts, corrupted optional UI preferences, and audio-device loss without crashing the workspace.
- Finalize app name, icon, version, bundled JRE modules, and native distribution for the current OS.
- Document development run, worker/renderer prerequisites, packaging, project workflow, artifact locations, limitations, and troubleshooting.
- Keep the old static web UI unchanged unless separately deprecated; this task
  does not remove it. It is separately deprecated and scheduled for audited
  retirement in Task 037.

## Verification

- Run full Kotlin and worker tests plus all desktop unit/UI tests.
- Run CLI/desktop artifact parity smoke tests.
- Run the application from a clean Gradle build and from the packaged current-OS artifact.
- Check 1100×720, 1440×900, HiDPI, keyboard-only core flow, long project/part names, empty projects, and large structures.
- Build and audition one deterministic project end to end; exercise a fixture-backed Qwen draft/approval flow.
- Verify source hashes and WAV/MP3 container/format rules after the packaged-app smoke test.

## Acceptance criteria

- The packaged application launches on the current development OS and can open a project without Gradle or Spring.
- Core operations are keyboard reachable and have useful semantics.
- A recoverable dependency or artifact failure does not corrupt the project or terminate the application.
- CLI remains supported and parity-tested.
- Documentation states that other OS packages require native testing on those operating systems.

## Out of scope

- Code signing/notarization unless credentials are explicitly supplied, auto-update, telemetry, cloud distribution, and claiming untested Windows/Linux/macOS support.
