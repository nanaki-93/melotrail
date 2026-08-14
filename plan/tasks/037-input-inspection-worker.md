# Task 037 — Input Inspection Worker

## Goal

Implement deterministic MIDI/WAV/MP3 validation and audio measurements behind a
typed worker boundary without registering projects or cleaning inputs.

## Dependencies

- Task 036 accepted.

## Requirements

- Add a worker command/endpoint and Kotlin command mapping for inspection.
  Validate extension and actual MIDI/audio container; reject mismatches, corrupt,
  empty, unsupported, or non-finite content with typed stage errors.
- MP3 is decoded to a temporary lossless working file. Measure in frames and
  preserve actual rate/channels; do not resample or force stereo.
- Produce all metrics required by Task 036 with documented deterministic
  thresholds/algorithms and bounded output. No cleanup is applied.
- The worker returns data only; the application service owns project-relative
  paths and report publication.

## Tests

- Valid/corrupt/mismatched MIDI, WAV, MP3; mono/stereo/multichannel; varied rates;
  silence, DC, short clipping, hum/noise fixtures; temporary cleanup and errors.
- Python worker and Kotlin command serialization tests.

## Acceptance criteria

- Inspection returns reproducible bounded metrics or a precise typed failure and
  never modifies the input.

## Out of scope

Project integration, cleanup, transcription, or UI.
