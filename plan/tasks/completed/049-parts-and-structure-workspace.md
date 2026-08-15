# Task 049 — Parts and Structure Workspace

## Goal

Make source preparation and song structure clear, selectable, and linked.

## Dependencies

- Task 048 accepted.

## Requirements

- Render compact selectable part rows with type, role, preparation/readiness,
  bars/key/duration, warnings, preview, and primary next action.
- Link selected part to the preparation inspector without duplicating canonical
  data. Handle empty/long/many-part states.
- Render occurrence chips (A1/A2/B1), proportional overview, and selected-section
  details. Preserve drag and keyboard reorder, duplicate/remove/clear, atomic
  save, and stale-artifact warning.
- Do not add fake duration/music data or change structure semantics.

## Tests

- Compose/view-model tests for selection linkage, empty/many parts, preparation
  states, occurrence labels, reorder alternatives, atomic error, and stale state.

## Acceptance criteria

- Parts and their repeated structure occurrences are understandable and fully
  operable with mouse or keyboard.

## Out of scope

Arrangement/build redesign, theme overhaul, or timeline lanes.
