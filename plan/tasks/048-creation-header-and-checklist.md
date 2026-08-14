# Task 048 — Creation Header, Stepper, and Checklist

## Goal

Expose the creation-progress model as navigation/status without restructuring
all content panels at once.

## Dependencies

- Task 047 accepted.

## Requirements

- Add top project identity, five-stage stepper, compact dependency status, Build
  Song entry point, and a Next action/checklist card.
- Stepper is navigation/progress only and dispatches existing intents; it never
  writes completion state. Blocked items show reason and recovery destination.
- Keep current panels functional during this intermediate task. Support keyboard
  focus/order, semantics, long names, and operation-in-progress behavior.

## Tests

- Compose tests for every step state, next-action dispatch, blocked reason,
  current operation, Qwen draft, completed release, focus, and labels.

## Acceptance criteria

- A user can always see the current creation stage and next safe action while
  all existing workflows remain usable.

## Out of scope

Parts/structure redesign, arrangement/build panel redesign, or final theme.
