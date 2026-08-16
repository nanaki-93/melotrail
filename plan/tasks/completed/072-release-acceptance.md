# Task 072 — End-to-End Release Acceptance

## Goal

Prove the complete Melotrail workflow, UI, playback, artifact safety, commercial
evidence, and packaged desktop application before declaring the milestone done.

## Dependencies

- Task 071 accepted.

## Requirements

- Build representative project fixtures for:
  - direct MIDI with Original Feel;
  - direct MIDI with 80 BPM Lo-fi Feel;
  - WAV solo-piano inspection/cleanup/transcription;
  - MP3 solo-piano decode/transcription when the optional decoder is available;
  - repeated structure occurrences receiving different cohesion edits;
  - deterministic and Qwen/model cohesion/arrangement paths;
  - commercial-ready and intentionally blocked rights/license cases.
- Exercise the full workflow from new project through final master and provenance
  report without direct filesystem intervention.
- Verify every source/raw/repaired hash remains immutable through lo-fi,
  cohesion, arrangement, mix, master, and export.
- Verify playback for source, prepared, raw MIDI render, repaired MIDI render,
  lo-fi MIDI render, cohesion boundaries, dry mix, lo-fi audio texture, and
  master on a real supported audio device.
- Compare UI screenshots at wide, medium, and narrow sizes against `../../UI.png`;
  document intentional differences and fix unintentional hierarchy, spacing,
  color, clipping, duplication, or scrolling regressions.
- Verify semantic colors plus text/icons for information, warning, loading,
  success, stale state, and error; test keyboard-only and screen-reader labels.
- Run offline automated suites for root Kotlin, desktop Compose, Python worker,
  CLI, and packaging guards. Run native packaging on each claimed OS rather
  than extrapolating support.
- Inspect the installed package from the renamed `melotrail` directory and open
  both a new project and a legacy project.
- Review logs and reports for leaked source content, model responses, secrets,
  unsafe absolute paths, and stale success claims.
- Re-check current official YouTube policy pages and record review date in the
  release documentation.
- Produce a signed-off acceptance report listing commands, results, manual
  listening environment, unavailable optional dependencies, known limitations,
  and deferred work.

## Tests and commands

- `./gradlew clean test :desktopApp:test :desktopApp:build`
- The complete worker test suite in the documented supported Python environment.
- CLI end-to-end and project migration fixtures.
- Native package build and install smoke on each supported target.
- Manual audio-device listening matrix and A/B checks.
- Visual, keyboard, focus, accessible-name, contrast, and responsive-layout
  acceptance checks.

Exact commands may change after Task 063 renames modules/scripts; update this
contract's command examples as part of that rename without weakening coverage.

## Acceptance criteria

- All automated suites pass from a clean checkout in the renamed directory.
- All available artifacts in the listening matrix are audible and controlled by
  the single transport; unavailable dependencies are reported truthfully.
- No source/raw/repaired artifact changes unexpectedly.
- The UI has no workflow-status second menu or duplicate controls and remains
  recognizably aligned with `../../UI.png`.
- Backend work and all severities are visible and semantically styled.
- AI Cohesion can produce approved bounded transformations and rejects unsafe
  plans without corrupting last-known-good work.
- The packaged Melotrail app opens new and legacy projects.
- Commercial-ready exports include complete, current evidence while blocked
  cases remain visibly blocked.

## Out of scope

- Fixing unrelated future tasks 059–062 unless separately promoted.
- Claiming support for an OS, audio device, model, decoder, or renderer not
  actually tested.
- Publishing the application or uploading generated content.

