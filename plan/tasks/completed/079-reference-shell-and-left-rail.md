# Task 079 — Reference Shell and Left Rail

## Goal

Restore the reference workspace shell at 1536 × 1024 and make the header and
left rail match `../../UI.png` without changing application workflows.

## Requirements

- Keep the existing stable three-column workstation; do not reintroduce
  section-dependent page replacement in wide mode.
- Reconstruct the header: brand block, five-icon navigation, selected-project
  field, save/settings/theme controls, and the reference spacing and selected
  treatment. Controls unsupported by the application must be visible only when
  truthfully disabled with an accessible reason.
- Reconstruct the left rail: `SCENES / PARTS`, compact Add Part action, five
  visual part rows, MIDI/audio import controls, and selected/playing states.
  Map all row data and actions to `WorkspaceUiState`; do not invent analysis or
  playback success.
- Replace the generic `Presentation` card with separate deterministic visual
  placeholder cards matching the reference's Video Concept, Current Location,
  and Next Destination regions. No network, weather, map, or travel API may be
  introduced.
- Use the reference measurements in `UI_REFERENCE_TOKENS.md`; add missing
  typography, icon, divider, and row-height tokens rather than local magic
  numbers.
- Preserve all existing semantics tags and add accessible names for every
  icon-only control.

## Verification

- Add Compose fixtures for populated, empty, selected, and unavailable states.
- Capture a deterministic 1536 × 1024 screenshot and compare the header and
  left-rail edges to `../../UI.png` (maximum 4 px major-edge variance).
- Run `./gradlew :desktopApp:test :desktopApp:build`.

## Out of scope

Changing import, preparation, playback, or project persistence behavior.
