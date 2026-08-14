# Task 043 — Audio Preparation and A/B UI

## Goal

Expose inspection, safe-cleanup consent, original/prepared A/B preview, and
transcription input selection in the selected-part UI.

## Dependencies

- Tasks 035, 039, 041, and 042 accepted.

## Requirements

- Show inspection metrics/warnings in plain language, deterministic cleanup
  recommendation, exact allowed operations, and inspect-only versus safe-cleanup
  choice. Never preselect destructive-looking behavior without evidence.
- Require explicit confirmation to apply cleanup and explicit selection of the
  transcription input. Show source/derived identity without external paths.
- Provide original/clean A/B using the preview service/player, with clear active
  source and equal monitor volume behavior; A/B never changes release files.
- Show progress and retry for cleanup/transcription, quality-gate outcome, and
  next Analyze action. Preserve access to the original at all times.

## Tests

- Compose/view-model tests for no-issue, recommendation, consent, rejection,
  A/B, input selection, stale report, worker/model failures, and retry.
- Manual A/B on clean/noisy/clipped fixtures when audio output exists.

## Acceptance criteria

- A user understands what cleanup will change, can compare it, and chooses what
  feeds transcription without risking the source.

## Out of scope

New DSP algorithms, arbitrary parameter editor, or global visual redesign.
