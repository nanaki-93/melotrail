# Task 039 — Guided MIDI/WAV/MP3 Import Dialog

## Goal

Replace split import dialogs with one clear, validated Compose flow.

## Dependencies

- Tasks 032 and 038 accepted.

## Requirements

- Show chosen filename/type/size, part ID, role, detected support, intended stages,
  prerequisites, and confirmation. File filters are hints; service validation is
  authoritative.
- Clearly state that WAV/MP3 transcription currently supports solo piano and
  requires the worker/optional Basic Pitch runtime. Do not claim full-mix,
  vocals, or arbitrary polyphonic support.
- MIDI flow preserves, cleans, then offers analysis. Audio flow preserves then
  inspects; subsequent preparation/transcription is explicit.
- Display per-stage progress and exact decode/inspection/cleanup/model/MIDI
  errors with safe retry. Cancellation leaves no registered partial part unless
  the underlying completed atomic stage is valid and shown.

## Tests

- Dialog validation, file cancellation, valid types, mismatched/unsupported
  source, dependency combinations, progress, retry, and duplicate part ID.

## Acceptance criteria

- Choosing WAV/MP3/MIDI always produces a visible next step or precise error;
  it never appears to do nothing.

## Out of scope

Cleanup controls, transcription quality logic, or visual shell redesign.
