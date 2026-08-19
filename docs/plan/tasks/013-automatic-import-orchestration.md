# Task 013 — Automatic import orchestration

## Goal

Make one accepted part import preserve the source and automatically run all
currently eligible part stages through the next review/input gate.

## Why

The target workflow should not require separate endpoint-like buttons for import,
transcribe, clean, transpose, correct, and enhance.

## Dependencies

Tasks 010–012. Algorithm tasks 015–018 may initially register transparent
placeholder processors behind the final contracts.

## Existing Code

- `ProjectApplicationService.importPart`, transcribe/clean/analyze methods
- worker inspect/cleanup/transcribe/midi-clean commands
- source attestation/import evidence, MIDI quality approval
- current desktop import actions

## Changes

- Add an atomic `ImportSongPart` command for file, name, section, attestation,
  expected revision, and selected default enhancement intensity.
- Preserve source first, create part/manifest, then enqueue/run Extracted onward.
- Direct MIDI and eligible audio converge at validated Extracted artifact; audio
  may retain prepared-audio evidence.
- Stop with actionable input-required state for low-confidence source key,
  cleanup review, missing project harmony/setup, or unsupported media.
- Automatically continue after required user input/approval using the same runner.
- If any stage fails, retain the source and earlier completed outputs/status.
- Remove/de-emphasize separate normal-path action buttons only after parity tests.

## Files

Modify project facade/import service, processor registry, worker adapters,
workflow read model, and integration tests.

## API / Contracts

Import result returns part ID and first run ID/snapshot, not a final-file promise.
All paths are validated at the boundary and DTOs expose safe source metadata.

## UI

One Import intent starts the flow; Task 014 renders the detailed experience.

## Backend

Application runner owns sequence and persistence; no controller coupling.

## Python Worker

Reuse inspect/prepare/transcribe/midi-clean via versioned adapters and validate
published MIDI. Do not broaden audio scope.

## Tests

Direct MIDI, WAV, MP3, unsupported/full-mix evidence, low-confidence gate,
failure at each stage, retry, duplicate import command, immutable source, reopen.

## Acceptance

- One import starts automatic processing and persists progress stage by stage.
- Audio and MIDI share the same post-extraction graph.
- Enhancement failure cannot destroy/select over Corrected.

## Out of Scope

Batch import, stem separation, arbitrary recordings, or new AI algorithms.

