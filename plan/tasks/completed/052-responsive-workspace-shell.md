# Task 052 — Responsive Workspace Shell

## Goal

Implement the reference-aligned desktop regions and supported breakpoints using
real existing panels.

## Dependencies

- Task 051 accepted.

## Requirements

- Wide 1440×900: compact top bar; left Parts/Preparation; center Structure /
  Arrangement; right Preview/Checklist/Status; persistent bottom transport/mix.
- Medium 1100×720: retain parts + main workspace and make secondary content
  deliberately accessible without clipping. Narrow <760dp: one logical vertical
  flow with reachable transport.
- Preserve scroll ownership, focus order, resize behavior, long names, operation
  dialogs, and all semantics. Do not duplicate a panel merely to place it.
- Use the Task 051 tokens and actual state only; omit reference image travel,
  scene, video, weather, and location areas.

## Tests

- Compose size/semantics tests and inspected screenshots at 1440×900, 1100×720,
  narrow, and HiDPI for empty, populated, and dependency-error fixtures.

## Acceptance criteria

- The workspace is usable without fullscreen at all supported sizes and is
  recognizably closer to the reference hierarchy.

## Out of scope

Timeline/mixer visual detail or engine changes.
