# Task 031 — Guided Input Import and Inspection

## Goal

Replace the split import experience with a guided, validated input flow that
explains MIDI versus WAV/MP3 processing and creates an inspectable input report.

## Requirements

- Create one import sheet with source type, filename, file size, detected
  extension/container, part ID, role, intended behavior, and confirmation.
  Filter dialogs are convenience only; validate actual extension/header at the
  application-service boundary.
- MIDI path: preserve source, validate Standard MIDI, clean it, and offer
  analysis. Audio path: preserve source, preflight decoder + worker + optional
  Basic Pitch runtime, then show that this is currently a solo-piano
  transcription workflow before it begins.
- Add an engine-owned input inspection operation and a versioned JSON report.
  It must decode without changing source, measure duration/sample rate/channels,
  peak/RMS/DC, clipped-run evidence, leading/trailing silence, basic hum/noise
  indicators, and parser/decode warnings. It must use frames for multi-channel
  media and produce clear typed errors for corrupt/unsupported files.
- Add a `PartPreparation`/snapshot read model so the UI renders each stage:
  source preserved, inspected, cleaned audio selected/not selected,
  transcribed, clean MIDI, analyzed, and ready. Do not infer readiness solely
  from filenames.
- Show a concise recommended next action after import: inspect, prepare audio,
  transcribe, clean MIDI, analyze, or add to structure. A failed operation
  preserves the source/report and provides a safe retry.
- Preserve CLI compatibility by exposing the same service operation through an
  explicit CLI command/flag only when needed; do not alter its current default
  import semantics without documented migration.

## Tests

- Valid MIDI/WAV/MP3, bad extension, mismatched header, corrupt audio, empty
  media, multichannel input, and source-hash preservation.
- Atomic report publication/retry and snapshot readiness combinations.
- Import-sheet validation and useful prerequisite/error text.

## Acceptance criteria

- Selecting WAV or MP3 cannot appear to do nothing: the user sees the required
  transcription runtime, inspection progress, and exact outcome.
- Every successful audio inspection leaves a bounded, versioned report and no
  source file is modified.

## Out of scope

Support for arbitrary audio formats, automatic full-song stem separation, or
silent use of a remote transcription service.
