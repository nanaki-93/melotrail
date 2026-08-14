# Task 034 — Guided Song-Creation Workspace

## Goal

Turn the existing functional panels into a state-driven creation workspace that
makes the next safe song-making action obvious while retaining expert access to
the full workflow.

## Requirements

- Add a top-level creation stepper with `Project`, `Prepare`, `Structure`,
  `Arrange`, and `Mix & Master`. It is navigation/progress only, not a second
  project state store. Derive completion/blocked status from project,
  preparation, arrangement, readiness, and artifact snapshots.
- Make a “Next action” card the primary empty/blocked-state affordance. It must
  name the prerequisite, destination/action, and expected artifact; actions
  dispatch existing view-model intents only.
- Rework Parts into compact, selectable cards/rows that show source type,
  preparation state, musical role, bars/key/duration, warning count, preview
  state, and primary action. Selecting a part drives the preparation/preview
  inspector; do not duplicate data as mutable UI-only state.
- Rework Structure as clear occurrence chips (A1, A2, B1), a proportional
  overview, and selected-section inspector. Retain keyboard and drag reorder,
  duplicate/remove actions, atomic service save, stale-artifact warning, and
  no-empty-structure validation.
- Rework arrangement controls into an explicit decision path: bounded planner,
  style, selected logical instruments, generation status, reviewed draft,
  required Qwen approval, selected-section role/density/transition details, and
  a validated timeline. A draft never enables Build Song.
- Rework build status into a checklist whose failed/missing item is clickable
  and readable. It shows source selection (dry/LoFi/master), required worker /
  renderer / library readiness, stage progress, reusable artifacts, and last
  outcome.
- Preserve all view-model/service boundaries. Add selection to immutable UI
  state, not to canonical project JSON unless the user performs an existing
  project operation.

## Tests

- State-machine tests for every next-action outcome from new project to master,
  stale artifacts, Qwen approval, worker/library/renderer failures, and retry.
- Compose semantic tests for stepper, selection linkage, checklists, keyboard
  structure controls, and disabled Build Song reasons.
- Manual workflow using direct MIDI and prepared audio input at 1100×720 and
  1440×900.

## Acceptance criteria

- A new user can understand what must happen next without opening a separate
  document, while experienced users retain direct access to each safe stage.
- The UI presents a single truthful workflow and no operation can bypass its
  application-service preconditions.

## Out of scope

New DAW editing capabilities, scene/media features from the reference, or a
new navigation/DI framework.
