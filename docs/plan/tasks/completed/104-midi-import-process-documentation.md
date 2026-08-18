# Task 104 — MIDI Import-Process Documentation

## Goal

Explain the MIDI import process precisely, including when an audio file is
transcribed into MIDI and the review points that protect the original source.

## Dependencies

- Task 102 accepted.

## Requirements

- Add a dedicated `../../..` guide and link it from the README, import UI help,
  and the track-process guide.
- State accepted direct-MIDI inputs (`.mid`/`.midi`) and validation: extension
  and actual Standard MIDI container, playable events, supported format, and
  project-local path confinement.
- Explain the two distinct routes:
  1. Direct MIDI is copied as immutable source evidence, publishes raw MIDI,
     then proceeds to explicit repair.
  2. Eligible WAV/WAVE/MP3 solo-piano audio is inspected, optionally receives
     explicitly approved conservative preparation, is transcribed into
     immutable raw MIDI, then proceeds to the same repair route.
- Explain raw, repaired, and optional Lo-fi Feel MIDI artifacts; make clear
  that repair/feel never overwrite the original and that repaired MIDI requires
  quality evidence and approval before analysis.
- Document expected errors and recovery: unsupported/corrupt input, unavailable
  transcription runtime, invalid model output, stale quality evidence, and
  unavailable renderer for preview. Avoid promising transcription quality.
- Use a short stage diagram and one terminology table rather than duplicating
  internal code comments.

## Tests

- Verify links and examples against `../../../../worker/README.md`, `../../../../README.md`,
  `InputInspectionContract`, and `TranscriptionQualityGate`.
- Review exact artifact names and paths against application-service tests.

## Acceptance criteria

- The guide makes it unambiguous when a user should import MIDI versus audio,
  what becomes editable MIDI, and why repair/approval is a separate step.

## Out of scope

- New MIDI formats, batch import, cloud transcription, or changes to model
  quality.
