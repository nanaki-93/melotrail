# MC-048I arrangement UX rubric

Status: `AWAITING_HUMAN` after automated preparation. This rubric is the only
place to record the required observed usability evidence; automated fixtures,
fake MIDI ports, and agent self-observation cannot substitute for it.

## Purpose and boundary

Use this rubric to evaluate the authority-complete Arrange and Review path:
choose a named style, hear its selected-section preview, create a complete
draft, listen to that draft, use it atomically, and repair an exception only
when needed. It validates the UX gates in root `PLAN.md` section 12. It is not
the MC-049 musical holdout: do not score musical quality here, do not reuse
these sessions as unseen holdout scores, and do not use development fixtures or
obsolete `data/audio/` material.

Record no participant name, contact detail, recording, or free-form personal
information. Use an anonymous session ID and a role only. At least five
observed sessions are required, and at least three must be labelled
`musician-non-implementer`.

## Session procedure

1. Prepare a license-safe authority-complete MIDI project with an immutable
   source, confirmed key/tempo/meter/structure/harmony, and no arrangement
   candidate selected for the participant.
2. Start timing after the participant is told: “Make and listen to a complete
   arrangement draft. Repair only if you think it needs it.” Do not explain
   hidden profile/pattern controls or direct the participant to a section.
3. Observe, without coaching, style selection, first sound, draft progress,
   cancellation/retry when exercised, page scrolling, navigation between
   Arrange/Review, and the active section/playback-target explanation.
4. Stop the first-draft timer when complete-draft playback begins. Exclude only
   deliberate listening time, and state any exclusion in the session note
   stored with the local evidence file (not in this public rubric).
5. Ask the participant to name the active section and playback target. Record
   only whether that explanation was correct. Record concise confusion labels,
   abandoned actions, and wrong-scope regenerations. A confusion seen in two
   sessions blocks acceptance until a focused fix and repeat observation pass.

## Required anonymized evidence

Create a local JSON file from the template and validate it:

```bash
python3 tools/measure_arrangement_ux.py --template > /secure/local/mc048i-sessions.json
python3 tools/measure_arrangement_ux.py --input /secure/local/mc048i-sessions.json
```

The JSON must include the fields below for each session. `projectAlias` and
`projectSha256` establish repeatability without exposing the participant or the
MIDI itself. The validator checks the gate mechanically; it does not make an
observation genuine. At least one of the five sessions must exercise
complete-draft cancellation/retry; all sessions must confirm draft progress,
scrolling visibility, navigation continuity, and preview/cancellation
immutability.

| Field | Requirement |
| --- | --- |
| `sessionId`, `date`, `participantRole` | Anonymous unique ID, ISO date, and musician/non-implementer status. |
| `projectAlias`, `projectSha256` | License-safe project alias and SHA-256, not a person’s identity. |
| `authorityComplete` | `true`. |
| `primaryActionsToFirstDraftListen` | At most 3. |
| `timeToFirstSoundMs`, `previewOnsetMs`, `timeToFirstDraftListenMs` | Non-negative milliseconds; median first-draft listen is at most 120,000 ms. |
| `draftProgressObserved`, `draftCancellationObserved` | Confirms progress and cancellable/retry behavior were observed where exercised. |
| `playerVisibleAfterScrolling`, `navigationContinuityObserved` | Confirms the persistent player and selected section/style/target/loop context. |
| `previewAndCancellationImmutable` | Confirms no project revision, candidate, acceptance, or source mutation. |
| `advancedControlsRequired` | Must be `false` to reach the first draft. |
| `abandonedActions`, `wrongScopeRegenerations` | Non-negative counts. |
| `activeSectionAndTargetExplained` | `true` only if the explanation was correct. |
| `confusions` | Concise labels; a label repeated by two participants is a blocker. |

## Automated preparation evidence

- `MidiCoreArrangementStylePreviewTest` measures the eight-request cold and
  warm plan-preparation p95 budgets. It measures deterministic in-memory
  preparation, not acoustic onset at a participant’s hardware; observed onset
  belongs in the session JSON.
- `MidiCoreWorkspaceTest`, `MidiCoreArrangePageTest`, and
  `MidiCoreReviewPageTest` cover latest-wins preview, cancellation/retry,
  player state, keyboard semantics, selected scope, explicit exceptions, and
  no-mutation behavior.
- `MidiCoreFocusedWorkflowTest` creates six ready-state wide and six ready-state
  compact fixtures through the real create/import/authority/preview/draft/use/
  undo/re-use/export workflow. `MidiCoreVisualRegressionTest` supplies six
  compact blocked-state fixtures.

## Decision record

| Gate | Result | Evidence |
| --- | --- | --- |
| Automated measurements and fixtures | Prepared | Record commands, p95 values, and SHA-256 fixture hashes in the execution log. |
| Five observed sessions / three independent musicians | Pending | Completed anonymized JSON and validator output. |
| Median first complete-draft listen ≤ 2 minutes | Pending | Validator summary. |
| No repeated confusion | Pending | Validator summary plus focused fix/retest links when needed. |
| MC-048I decision | `AWAITING_HUMAN` | Do not mark `DONE` until every row above passes. |
