# Task 014 — Melody Parts progress and recovery UI

## Goal

Replace endpoint-oriented import controls with section-aware part cards, automatic
stage progress, failures, required-input gates, and retry.

## Why

The musician needs a clear, trustworthy view of a non-destructive expensive
pipeline without seeing internal endpoints or paths.

## Dependencies

Task 013.

## Existing Code

- current Import destination/dialog, part cards, workspace progress/errors
- playback transport and MIDI/audio preparation previews
- `WorkspaceViewModel`, `WorkspacePageRouter`, `WorkflowReadModel`

## Changes

- Rename/adapt Import to Melody Parts and add name/section/attestation/file fields.
- After one Import action, show stage rail Importing/Extracting/Cleaning/
  Normalizing/Transposing/Correcting/Enhancing/Ready.
- Show persisted status after navigation/reopen, safe error details, Retry, and
  required source-key/quality/setup/harmony actions.
- Show source metadata without absolute paths and identify the artifact playing.
- Prevent conflicting edits/actions while a subject stage is Processing; do not
  imply cancellation unless supported.
- Add/remove/change-section flows with downstream impact confirmation.
- Split focused Parts components/state reducers from the large router/view model.

## Files

Desktop workspace state/intents/router/navigation, new Parts components, transport
labels, Compose tests, UI docs.

## API / Contracts

Consume immutable part/stage snapshots and import/retry/input-confirmation
commands. Never call worker/model or inspect files from composables.

## UI

Accessible textual states, failure ownership/action, compact and expanded cards,
wide/medium/narrow layouts, no color-only status.

## Backend

No new behavior beyond application command wiring.

## Python Worker

No direct UI connection.

## Tests

MIDI/audio import, every status/failure/retry, reopen progress, low-confidence
confirmation, unsupported media, duplicate action prevention, responsive/a11y.

## Acceptance

- A musician understands which representation exists and what action is needed.
- Successful upstream stages remain visibly complete after failure/retry.
- The normal flow needs one Import action, not manual per-endpoint actions.

## Out of Scope

Artifact A/B controls (Task 020), algorithm quality, batch import.

