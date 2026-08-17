# Task 085 — Structure Page and Workflow Repair

## Goal

Reproduce the focused Structure page and fix the missing UI path from prepared
MIDI/transcribed audio into the canonical song structure.

## Dependencies

- Task 084 accepted.

## Requirements

- Render the reference hierarchy: eligible prepared-part palette, ordered
  structure strip, and compact section table with role, bars, edit,
  duplicate/remove, and reorder controls.
- A part is addable only when the existing canonical state derives
  `PartPrimaryAction.AddToStructure`. Stale, raw, approval-required, or
  unanalyzed parts cannot bypass that gate.
- Give every eligible palette item a visible keyboard-accessible control that
  dispatches `WorkspaceIntent.AddStructurePart(part.id)`.
- After successful MIDI preparation or eligible audio transcription/preparation,
  expose an Add to structure action and a Go to Structure route. A notification
  alone is not an acceptable continuation path.
- Add, repeated add, duplicate, move, remove, and clear must use the existing
  typed `saveStructure` application boundary and reload canonical project
  state. Do not treat a UI-only list as saved completion.
- Preserve stable occurrence identities and current stale-artifact rules.
  Retained downstream artifacts remain inspectable and are not deleted.
- Keep Structure selected after every in-page mutation. Explicit open/create is
  the only operation in this workflow that returns to Overview.
- Empty state copy is limited to “Choose a prepared part to start” plus the
  eligible palette/action.

## Verification

- Add a regression test reproducing the reported path: import MIDI -> Prepare
  MIDI/analysis -> click Add to structure -> canonical save contains the part.
- Add the equivalent eligible transcribed-audio path using fakes; tests remain
  offline.
- Tests cover repeated add, reorder, duplicate, remove, clear, stale
  invalidation, failure recovery, and rejection of ineligible parts.
- Compose tests assert the visible add control and its intent, one Structure
  page root, keyboard alternatives to reordering, and concise empty state.
- Capture and overlay a deterministic Structure golden against the numbered
  Structure region of `../pictures/App-pages.png`.
- Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Out of scope

Changing structure persistence format, arrangement generation, MIDI repair
algorithms, automatic deletion, or drag-only editing without keyboard parity.
