# Task 117 — Arrangement Compatibility and End-to-End Rollout

## Goal

Keep Arrangement behavior intact while making it consume approved Cohesion
boundaries, then align the desktop workflow, documentation, migrations, and
release checks with the cleaned process.

## Dependencies

- Task 116 accepted.

## Requirements

- Preserve existing Arrangement ownership of section roles, energy, allowed
  instruments, density, variations, generated accompaniment, review/approval,
  and build inputs.
- Replace any independent Arrangement transition choice that conflicts with
  Cohesion. Arrangement may reference the approved boundary decision but must
  not silently regenerate or override it.
- Make MIDI/stem generation and rendering place the approved Cohesion bridge at
  the correct shifted timeline boundary and keep source/selected part MIDI
  unchanged. Include boundary hashes in build freshness evidence.
- Update the desktop happy path and labels to exactly: Import, Convert to MIDI
  when needed, Clean MIDI, optional AI Fix, optional Lo-fi Feel, Structure,
  Cohesion, Arrangement. Show one primary next action and keep previews,
  evidence, optional choices, and recovery in contextual details.
- Update `README.md`, `docs/TRACK_PROCESS_WORKFLOW.md`,
  `docs/MIDI_IMPORT_PROCESS.md`, troubleshooting, and relevant function
  documentation so terminology, artifacts, prerequisites, and stale recovery
  match shipped behavior.
- Complete any explicit project migration introduced by Task 110 and verify old
  approved artifacts are not mistaken for current AI-fix/Cohesion evidence.
- Exercise direct-MIDI, WAV, and MP3 routes end to end, both choices at each
  optional branch, repeated Structure occurrences, AI/model failure, bridge
  review, Arrangement approval, build, mix/master, and export prerequisites.
- Preserve the distinction between Lo-fi MIDI Feel and post-mix Lo-fi audio
  texture throughout the product.

## Tests

- Add compatibility tests proving existing valid Arrangement plans retain their
  non-transition semantics and new plans consume exact approved boundary IDs
  and hashes.
- Test build timing with zero, one, and multiple inserted bridges, including
  repeated parts and tempo/meter changes.
- Add view-model and Compose tests for the ordered stage labels, optional skips,
  blocked/retry states, approvals, keyboard access, and wide/medium/narrow
  layouts.
- Run `./gradlew test`, `./gradlew :desktopApp:test :desktopApp:build`, and
  `.venv/bin/python -m unittest discover -s worker/tests` when worker code was
  affected. Record optional model/renderer/audio-device checks honestly.
- Perform source-hash checks and a manual end-to-end A/B listening smoke for
  cleaned, AI-fixed, Lo-fi, hard-join, and approved-bridge outputs.

## Acceptance criteria

- Both import routes follow the documented pipeline, optional stages are truly
  optional and reversible, every adjacent boundary is supplied by approved
  Cohesion, and existing Arrangement capabilities continue without a competing
  transition owner.

## Out of scope

- New Arrangement instruments, mastering redesign, browser UI, cloud services,
  telemetry, or unrelated refactors.
