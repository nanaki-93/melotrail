# Task 054 — End-to-End Workflow and Compatibility

## Goal

Prove the improved services and desktop state work end to end before packaging
or deleting legacy code.

## Dependencies

- Tasks 029–053 accepted.

## Requirements

- Add fixture-backed flows for direct MIDI, clean WAV, noisy/clipped WAV, and
  MP3 through import/inspect, optional cleanup, fake transcription, MIDI cleanup,
  analysis, structure, deterministic arrangement, build, and preview.
- Assert source hashes, report/plan provenance, artifact format/compatibility,
  stale invalidation, atomic failure behavior, and no false success.
- Verify migration/read behavior for v1/v2/v3 projects, missing preparation
  reports, legacy clean MIDI, approved arrangements, mix, and release artifacts.
- Use fakes for optional model/renderer/audio device; standard tests stay offline.
  Record real local smoke results separately when dependencies exist.

## Tests

- Full focused integration suite plus root/desktop/worker tests affected by the
  new workflow.

## Acceptance criteria

- Supported old projects remain readable and all new input paths reach a valid
  master or precise recoverable failure without source mutation.

## Out of scope

Documentation/packaging, broad bug fixes, or legacy frontend deletion.
