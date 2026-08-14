# Task 036 — Creation Workflow Release Gate

## Goal

Prove the improved UI and input-to-song pipeline work as one local, recoverable
workflow and prepare the current-OS package/documentation for real use.

## Requirements

- Add end-to-end fixture tests for direct MIDI, clean WAV, noisy/clipped WAV,
  and MP3: import/inspect, optional safe cleanup, transcription fake boundary,
  MIDI clean/analyze, structure, deterministic arrangement, build, and preview.
  Assert source hashes, report provenance, artifact compatibility, and no false
  success at each dependency failure.
- Run/manual-test the real local worker and renderer only when available.
  Record missing optional Basic Pitch/runtime or asset prerequisites as such;
  tests must use fakes and remain runnable offline.
- Test packaged-app startup from outside the repository root and confirm
  sound-library discovery/selection recovery, preview, build error presentation,
  and README instructions on the current OS.
- Update README, `sounds/README.md`, troubleshooting, and the desktop workflow
  documentation: start worker, install optional transcription environment,
  configure renderer/library, import limitations, cleanup modes, report paths,
  preview behavior, and recovery steps. Keep claims proportionate to verified
  support.
- Review migrations for existing v1/v2/v3 projects, legacy previews, absent
  reports, and existing mix/release artifacts. No migration may overwrite source
  files or silently invalidate an approved arrangement.
- Run full Kotlin, desktop, worker, package, smoke, and visual checks; identify
  pre-existing failures separately and inspect the final diff for task leakage.

## Acceptance criteria

- A user can follow the documented local path from a supported input to an
  auditioned master, or receives a precise recovery instruction before any
  unsafe/partial operation.
- The packaged current-OS app does not depend on repository CWD for a useful
  sound-library error/recovery path.

## Out of scope

Code signing/notarization without credentials, cross-platform support claims,
remote services, or replacing the CLI. Static-frontend retirement is deferred
to Task 037 so it can be audited and removed as one complete change.
