# Task 094 — Import Visual Reconstruction

## Goal

Rebuild the focused Import destination to follow
`../../pictures/UI/02-import.png` while preserving the fully restored Task 091
workflow and all input/preparation safety boundaries.

## Dependencies

- Tasks 091–093 accepted.

## Requirements

- Reproduce the reference hierarchy with page heading/tabs where useful,
  distinct Audio and MIDI chooser cards, one shared drag/drop area, a dense
  imported-files table, selected-part preparation/context rail, and one clear
  next action.
- Audio chooser accepts only currently supported WAV/WAVE/MP3 solo-piano input;
  MIDI chooser accepts MID/MIDI. Both chooser and drop paths enter the same
  typed validation/import boundary and validate actual format, not extension
  alone.
- Keep source immutability and the current import form fields required by the
  application contract, including part ID, role, and rights claim. Present
  advanced/required fields in the existing import dialog or a compact staged
  surface without bypassing validation.
- Render each imported row with real filename, source type, measured size,
  analysis key/BPM/duration only when available, preparation status, preview
  availability, and accessible row actions.
- Keep Task 091's visible details/preparation surface. Row `More`/overflow,
  Repair Review, Analyze, Inspect, Transcribe, Apply MIDI Feel, Add to
  Structure, preview, failure, and retry actions must remain connected.
- Scope the page primary action to an explicit selected part. If nothing is
  selected, derive a deterministic next incomplete part and visibly identify
  it; never silently operate on a hidden previous selection.
- Show cleanup, transcription, and MIDI-repair controls only when relevant to
  the selected source/state. Do not reproduce decorative Normalize, Noise
  Reduction, bulk Process All, Clear All, or Delete controls without typed safe
  application contracts.
- Match reference table density, borders, iconography, purple selection, card
  treatment, rail proportions, and responsive behavior through shared
  components.
- On narrow layouts, stack chooser/table and move preparation into a full-height
  sheet. Preserve keyboard order, focus return, and one active page root.

## Verification

- Re-run all Task 091 interaction regressions against the reconstructed page.
- Compose tests cover empty project, empty import list, MIDI, audio, mixed
  multi-part, validating, preparation required, analysis required,
  approval-required, ready, stale, failed, long filename, and missing worker or
  renderer states.
- Test chooser preference, drop validation, unsupported/disguised files,
  cancel, failure recovery, selected row, focus return, and keyboard activation.
- Assert every enabled visible action emits a typed intent, opens a visible
  surface, or routes to Structure. Assert unsupported mockup-only actions are
  absent.
- Capture and overlay a deterministic 1536 × 1024 full-window fixture against
  `../../pictures/UI/02-import.png`; check shell, chooser, table, and context-rail
  geometry and document capability-driven differences.
- Run `./gradlew :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- The Import page follows the supplied mockup without sacrificing real
  workflow behavior.
- `⋮`, Analyze, repair, audio preparation, and continuation remain visibly
  functional for the correct part.
- No unsafe bulk/delete/cleanup behavior is invented.

## Out of scope

New worker APIs, arbitrary audio processing, source deletion, batch import,
new formats, or Structure page reconstruction.
