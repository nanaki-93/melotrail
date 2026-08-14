# Task 036 — Input Inspection Contract and Report

## Goal

Define a versioned, UI-neutral inspection contract for MIDI/WAV/MP3 inputs and
their project-local preparation report.

## Dependencies

- Task 032 accepted.

## Requirements

- Define request/result/error types and a versioned `report.json` schema with
  source-relative identity/fingerprint, detected container/format, duration,
  sample rate/channels, peak/RMS/DC, clip evidence, silence, hum/noise indicators,
  warnings, tool versions, and preparation status.
- Do not persist arbitrary external paths, model responses, or non-finite values.
  Specify migration behavior for missing/unknown report versions.
- Define exact project paths under `prepared/<part>/` and atomic publication;
  report inspection may not register a part or alter source.

## Tests

- Schema validation, JSON round-trip, path-boundary, fingerprint, non-finite
  rejection, unknown version, and backwards-compatibility fixtures.

## Acceptance criteria

- The report contract is stable and tested before worker/service implementation.

## Out of scope

Audio measurement implementation, import UI, cleanup, transcription, or MIDI
analysis.
