# Task 091 — Import Action and Details Recovery

## Goal

Restore every post-import action that became visually unreachable after the
single-page router refactor, including imported-row overflow/details, MIDI
repair review, MIDI analysis, audio preparation, and recovery actions.

## Dependencies

- Task 090 accepted.
- `../PLAN_UI.md` is the governing UI reconstruction plan.

## Problem statement

- The Import row overflow button dispatches `ShowPartDetails(partId)`, which
  only sets `selectedPartId` and `partDetailsExpanded`.
- The active `WorkspacePageRouter` does not compose the older MIDI-quality or
  audio-preparation panels, so the state changes without any visible result.
- `ReviewRepair` and `FixIssue` use the same invisible path.
- A part with current repaired MIDI and no current MIDI analysis falls through
  to `FixIssue` because `PartPrimaryAction` has no Analyze case, despite the
  existing typed `AnalyzePart(partId)` intent and service.
- Current UI tests assert emitted intents but do not prove that handling those
  intents creates a visible and actionable surface.

## Requirements

- Add an explicit `Analyze` state to the canonical part-primary-action model.
  Derive it only when repaired/selected MIDI is current and MIDI analysis is
  absent or stale. Dispatch `WorkspaceIntent.AnalyzePart(partId)`.
- Replace the invisible boolean-only details path with one explicit visible UI
  state, preferably a typed `WorkspaceDialog.PartDetails(partId)` or an
  equivalent typed sheet state. The selected part ID must be part of that state
  rather than inferred from an unrelated row.
- Make the imported-row overflow action open that visible surface for the
  clicked part. Review Repair and Fix Issue must open the same surface at the
  relevant section or expose an equivalent visible recovery action.
- Adapt the existing `MidiQualityReviewPanel` and `AudioPreparationPanel`
  behavior into the active Import flow. Reuse their typed intents and view-model
  orchestration; do not copy worker, filesystem, preview, or analysis logic into
  composables.
- The details surface must show only controls relevant to the selected source
  and canonical state:
  - MIDI repair report/status, approval, retry profile, and warnings;
  - repaired/Lo-Fi MIDI Feel selection and explicit apply/re-analysis;
  - audio inspection status, conservative cleanup choice/confirmation,
    transcription input, and transcription quality-gate action;
  - source/repaired/prepared preview controls when their validated
    prerequisites are available;
  - current error cause plus one safe retry/recovery action.
- Add explicit close/dismiss behavior, Escape handling, focus entry, and focus
  return to the invoking row/action. Dismissal must keep Import selected and
  must not reset project, selection, readiness, or playback.
- While a mutating operation is active, disable conflicting actions and expose
  the reason accessibly. Keep one global operation-feedback surface.
- Preserve actual-format validation, source immutability, project-relative
  derived paths, cancellation, atomic publication, and canonical refresh after
  successful mutations.
- Remove `partDetailsExpanded` and dead state only when all active behavior has
  an explicit replacement. Do not delete legacy panels until later pages no
  longer depend on them and coverage proves parity.

## Verification

- Unit-test `primaryPartAction` for raw/stale MIDI, approval-required repair,
  current repaired MIDI without analysis, current analysis, pending MIDI Feel,
  eligible audio before inspection, inspected audio, warnings, and legacy
  invalid states.
- With a real `WorkspaceViewModel` and fake services, click `⋮` and assert that
  the matching part-details root becomes visible. Dismiss it and assert focus
  return plus preserved Import navigation.
- Prove current repaired MIDI without analysis renders Analyze and calls the
  analysis service with the correct project root and part ID.
- Prove repair review opens visible evidence and its approval/retry controls
  reach the existing typed intents.
- Prove audio inspection, cleanup confirmation, transcription-input selection,
  transcription, MIDI Feel apply/re-analysis, preview, failure, and retry are
  reachable from composed UI.
- Add a multi-part regression fixture proving actions never operate on a
  previously selected or wrong row.
- Keep intent-only tests where useful, but do not use them as the only evidence
  for a control that opens UI.
- Run `./gradlew :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Clicking an imported row overflow control always creates visible feedback.
- A correctly repaired but unanalyzed MIDI part has a working Analyze action.
- Every state-derived Import action either performs work, opens an actionable
  visible surface, routes to Structure, or is disabled with a reason.
- The full fix remains inside existing application and safety boundaries.

## Out of scope

Visual reconstruction of the full Import mockup, new cleanup algorithms,
worker endpoint changes, deleting imported sources, bulk processing, or new
analysis formats.
