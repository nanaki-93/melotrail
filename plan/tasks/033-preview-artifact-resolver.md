# Task 033 — Preview Artifact Resolver

## Goal

Resolve supported source and MIDI parts into validated, fingerprinted WAV
preview artifacts independently of playback and Compose.

## Dependencies

- Tasks 029 and 031 accepted.

## Requirements

- Introduce typed preview requests/results and stages: validate, decode or
  render, validate artifact, reuse/publish.
- Reuse a valid WAV source directly when safe. Decode MP3 to a fingerprinted
  PCM-24 monitor copy without modifying the source or using an MP3 extension.
- Render clean MIDI with the injected library/renderer, piano instrument,
  project format, and analyzed duration. Explain missing analysis/library/
  renderer precisely.
- Put monitor-only files under `previews/`, use source/config fingerprints,
  atomically publish, validate before reuse, and invalidate on relevant changes.

## Tests

- WAV reuse, MP3 decode cache, MIDI render cache, invalidation, corrupt cache,
  missing prerequisite, output format, and source SHA-256 preservation.

## Acceptance criteria

- Every supported part resolves to a validated WAV or a typed prerequisite/error;
  no playback state or UI code exists in this service.

## Out of scope

Transport controls, audio-device handling, input cleanup, or release artifacts.
