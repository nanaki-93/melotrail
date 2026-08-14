# Task 055 — Documentation and Current-OS Package Smoke

## Goal

Document and verify the improved desktop workflow from a clean current-OS
package before retiring the browser UI.

## Dependencies

- Task 054 accepted.

## Requirements

- Update README/troubleshooting/sound-library docs for desktop start, worker,
  optional transcription runtime, library selection, renderer, import limits,
  cleanup modes/reports, preview, artifacts, and recovery.
- Package the current OS and launch outside repository CWD. Verify library
  discovery/selection, open/create, direct-MIDI flow, representative audio flow
  with available/fake boundary, preview, build, and errors.
- Check 1440×900, 1100×720, HiDPI, keyboard-only core flow, long names, empty
  project, and large structure. Record screenshots/manual results.
- Do not claim code signing, other OS, model, renderer, or sample support not
  actually verified.

## Tests

- Full root/desktop build/tests and current-OS packaging task; source/artifact
  hash/container checks for the smoke project.

## Acceptance criteria

- The packaged Compose app is documented and usable without Spring/static UI or
  repository CWD, subject only to clearly documented local dependencies.

## Out of scope

Static source deletion, code signing/notarization, or other OS packages.
