# Task 047 — Creation Progress and Next-Action Model

## Goal

Derive one truthful creation journey from canonical snapshots before changing
the workspace layout.

## Dependencies

- Tasks 032, 034, 039, 043, and 046 accepted.

## Requirements

- Define UI-neutral/read-model stages: Project, Prepare, Structure, Arrange, Mix
  & Master. Each has not-started/current/complete/blocked/stale state, reason,
  expected artifact, and allowed next intent.
- Derive from project/preparation/readiness/arrangement/mix/build artifacts; do
  not store completion flags or create a second project state model.
- Cover empty project, multiple parts, partial preparation, stale structure,
  Qwen draft approval, missing dependency, build failure, and completed release.
- Add selected part/section/artifact linkage to immutable UI state without
  persisting it as song data.

## Tests

- Table-driven state tests for every stage/transition, invalid combinations,
  stale artifacts, retry target, Qwen approval, and final master.

## Acceptance criteria

- Given the same canonical snapshot, the next action is deterministic and
  identifies its prerequisite and artifact.

## Out of scope

Stepper/cards/layout or new engine operations.
