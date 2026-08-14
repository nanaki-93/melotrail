# Task 051 — Workspace Theme Tokens

## Goal

Create the stable visual vocabulary needed to approach `plan/UI.png` without a
large layout rewrite in the same task.

## Dependencies

- Task 050 accepted.

## Requirements

- Inspect `plan/UI.png` and define named tokens for charcoal backgrounds,
  elevated cards, teal primary/focus, borders, typography, spacing, radii, state
  colors, and piano/bass/drums/pad/strings accents.
- Apply tokens to shared primitives and a small representative set of existing
  controls; remove only directly duplicated ad-hoc styling.
- Provide hover/focus/pressed/disabled/error states with text/non-color cues,
  contrast, minimum hit targets, and HiDPI behavior.
- Add a deterministic theme showcase or fixture screen used only by tests/dev.

## Tests

- Theme/primitives Compose tests, semantics, contrast-oriented assertions where
  feasible, and reviewed screenshots for light-independent dark theme/HiDPI.

## Acceptance criteria

- Later layout tasks can use named tokens consistently; no business behavior
  changes.

## Out of scope

Full workspace layout, timeline redesign, or audio behavior.
