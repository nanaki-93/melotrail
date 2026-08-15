# Task 050 — Arrangement and Build Workspace

## Goal

Clarify the arrangement review/approval and build lifecycle using existing typed
services and the new progress model.

## Dependencies

- Task 049 accepted.

## Requirements

- Present planner, bounded style/instruments, generation progress, Qwen draft
  review, explicit approval, and selected-section role/density/transition data.
- A draft or stale arrangement never enables Build Song. Show the exact missing
  analysis/structure/approval/dependency and action to resolve it.
- Present build options, nine-stage progress, current artifact, reuse, safe
  cancellation, last result, and dry/LoFi/master availability.
- Keep all orchestration in application services/view model; composables only
  render state and dispatch intents.

## Tests

- Deterministic/Qwen flows, invalid draft, approval, stale plan, missing
  prerequisite, build progress/reuse/cancel/failure/success, and disabled reasons.

## Acceptance criteria

- Arrangement and build are a single understandable gated path with no implicit
  AI approval or false completion.

## Out of scope

New planner/build algorithms, theme, responsive shell, or static frontend.
