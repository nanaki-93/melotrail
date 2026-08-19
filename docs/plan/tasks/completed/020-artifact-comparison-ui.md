# Task 020 — Artifact comparison, bypass, and retry UX

## Goal

Let users preview, compare, select, bypass, and retry Original/Cleaned/Corrected/
Enhanced part representations without destructive replacement.

## Why

Traceability is only useful if musicians can hear/understand changes and retain
control over which version feeds the song.

## Dependencies

Tasks 014 and 017–018. Task 019 enriches but is not required.

## Existing Code

- desktop transport/playback and MIDI preview preparation
- selected MIDI resolver and current AI-fix/feel approval UI patterns
- stage snapshots/artifact refs from Tasks 011–012

## Changes

- Add per-part version selector with only available/validated artifacts and exact
  stage/run labels.
- Implement A/B transport actions that identify artifact/hash and prevent stale
  cache audio from being mislabeled.
- Add enhancement intensity/Generate or rerun, approve/reject when required,
  bypass to Corrected, and retry failed stage.
- Show concise edit/quality report and downstream impact before selection change.
- Preserve rejected/stale drafts in evidence details but never list them as
  current-ready choices.
- Ensure source audio versus MIDI-render comparison is labeled honestly.

## Files

Desktop part-detail components/state/intents/transport integration/tests;
application selection/approval DTO mapping if missing.

## API / Contracts

Select/approve/reject/retry commands include part, artifact/run, input/context
hash, and expected revision. Application resolver owns precedence.

## UI

Keyboard-accessible A/B, visible current selection and AI Off state, clear stale/
failed badges, responsive details panel/dialog.

## Backend

No direct file selection or renderer calls from UI; services prepare preview.

## Python Worker

No direct UI use.

## Tests

Availability rules, exact transport label, Off/bypass, selection invalidation,
approval races/hash mismatch, failed retry, rejected/stale invisibility, a11y.

## Acceptance

- User can return to Corrected without deleting Enhanced.
- UI never implies Original audio and derived MIDI are the same representation.
- Selected artifact and downstream stale state survive reopen.

## Out of Scope

Waveform/piano-roll diff editor or manual note editing.

