# Task 096 — Arrange Reconstruction

## Goal

Rebuild the Arrange destination to follow
`../../pictures/UI/04-arrange.png` using only validated structure, analysis,
planner, arrangement, transition, instrument, review, and playback state.

## Dependencies

- Task 095 accepted.

## Requirements

- Reproduce the reference hierarchy: Arrange header and primary generation
  action, supported tabs/sections, structure-aligned arrangement timeline,
  logical track lanes, shared transport, settings/instruments context rail, and
  arrangement review/summary area.
- Keep deterministic and Qwen planner choices mapped to existing bounded
  planner enums and services. Qwen output remains a draft requiring explicit
  approval; the UI must never treat generation as approval.
- Render logical instruments from the supported registry/model. Instrument
  toggles, style, intensity, and any transition controls must map to typed draft
  fields and existing allow-lists. Do not add values visible only in the
  reference.
- Derive structure sections, repeated identities, arrangement sections,
  logical tracks, current/stale state, and known timing from canonical
  snapshots. Do not fabricate notes, MIDI blocks, waveforms, generated tracks,
  transition content, or AI suggestions.
- Generate Arrangement is enabled only when structure, per-part MIDI analyses,
  cohesion, sound-library/renderer requirements applicable to generation, and
  other current prerequisites are satisfied. Show one concise blocking reason
  and make detailed diagnostics available on demand.
- Show deterministic approval state or Qwen review/approval in the same page.
  Preview Draft, Approve, regenerate/revise where already supported, and
  failure recovery must remain typed operations.
- Add undo/redo only if there is a real bounded draft-history model. Otherwise
  omit those mockup controls.
- Use the one shared playback session for preview. Navigation and draft edits
  do not create another player or report a rendered artifact.
- Preserve page selection, draft fields, selected arrangement section,
  feedback, and playback through ordinary operations and failures.
- Match reference density, timeline alignment, track colors, selected states,
  controls, context rail, and responsive behavior via shared components.

## Verification

- View-model/application tests cover deterministic generation, Qwen draft,
  approval, stale inputs, missing analyses/cohesion, invalid instruments,
  preserved draft selection, failure/retry, and canonical refresh.
- Compose tests cover empty project, missing prerequisites, ready deterministic,
  ready Qwen, generating, draft review, approved, stale, failed, long content,
  and missing optional dependency states.
- Interaction tests prove planner, instrument, style, intensity, section
  selection, generation, preview, and approval controls dispatch the expected
  typed intents for current state.
- Assert unsupported undo/redo, fake AI suggestion, track, and transition
  controls are absent.
- Verify timeline/track alignment with repeated sections and internal bounded
  scrolling at medium/narrow widths.
- Capture and overlay a full 1536 × 1024 fixture against
  `../../pictures/UI/04-arrange.png`; document data/capability-driven differences.
- Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Arrange follows the reference structure without inventing generated data or
  bypassing approval.
- All generation and review actions preserve bounded planner/application
  contracts.
- The page remains usable and truthful in empty, blocked, draft, stale, and
  approved states.

## Out of scope

New arrangement algorithms, arbitrary MIDI editing, executing model output,
new instruments/transitions, fake undo history, or Mix page implementation.
