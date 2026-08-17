# Task 095 — Structure Reconstruction

## Goal

Rebuild the Structure destination to follow
`../pictures/UI/03-structure.png` while retaining canonical structure saves,
stable occurrence identity, and complete keyboard editing.

## Dependencies

- Task 094 accepted.

## Requirements

- Reproduce the reference hierarchy: page header, canonical occurrence strip,
  eligible prepared-part/Add Section area, section table, selected-section
  context rail, truthful song summary, preview area, and concise help/recovery.
- A part is eligible only when current canonical state derives
  `PartPrimaryAction.AddToStructure`. Raw, stale, approval-required,
  unanalyzed, or otherwise invalid parts cannot bypass that gate.
- Add, repeated add, duplicate, remove, clear, edit role/name where supported,
  and earlier/later reorder must use the existing typed structure/application
  boundary and reload the canonical project after success.
- Preserve stable repeated-occurrence identities. Never use row position alone
  as persistent identity, and never treat a UI-only draft as saved completion.
- Display duration, bars, key, BPM, time signature, and summary values only when
  current read models provide them. Clearly label unknown or mixed values.
- Use the single playback session for valid part/arrangement preview. Preview
  selection must not mutate structure.
- Provide drag reordering only as an enhancement. Earlier/later keyboard
  controls remain a complete alternative and expose disabled boundary reasons.
- Keep Structure selected after every in-page mutation. Failures retain the
  attempted context and use the global feedback/retry surface.
- Match the reference strip/table density, row selection, colored occurrence
  states, rail layout, controls, and responsive stacking via shared components.
- Omit Suggest Structure unless a separate bounded application service and
  validation contract is explicitly added.

## Verification

- Application/view-model tests cover add, repeated add, duplicate, remove,
  clear, edit, reorder, canonical refresh, stable identity, downstream stale
  invalidation, save failure, and retry.
- Regression tests reproduce Import/Analyze -> Add to Structure for direct MIDI
  and eligible transcribed audio using offline fakes.
- Compose tests cover empty, eligible/ineligible palette, selected occurrence,
  mixed/unknown metadata, long names, many sections, stale, mutating, and failed
  states.
- Keyboard tests cover add, selection, edit, duplicate, remove confirmation if
  applicable, and earlier/later reorder including first/last boundaries.
- Assert one Structure page root and no page-level horizontal scrolling; only
  the bounded occurrence strip may scroll.
- Capture and overlay the full 1536 × 1024 fixture against
  `../pictures/UI/03-structure.png` and document intentional truthful
  differences.
- Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Structure matches the mockup hierarchy and remains a real editor over the
  canonical saved project.
- Every occurrence operation is deterministic, accessible, and refreshes from
  persisted truth.
- Unsupported AI suggestion behavior is not implied.

## Out of scope

Automatic structure generation, a new project format, drag-only editing,
arbitrary MIDI editing, or Arrange implementation.
