# Task 081 — Reference Right Rail and Song Plan

## Goal

Restore the visual scene/player and AI Song Plan panels from `../UI.png` while
keeping images as deterministic local placeholders for now.

## Dependencies

- Task 079 accepted.
- Task 080 accepted for shared arrangement selection state.

## Requirements

- Replace the generic Scene card with the reference's large artwork/player
  composition. Use a bundled, deterministic placeholder illustration or
  gradient—not external imagery—and label it as placeholder through semantics.
- Restore change-scene affordance, now-playing title, location line, transport
  controls, elapsed/total time, and progress bar. Connect supported controls to
  the single existing playback session; otherwise render an accessible disabled
  control, never a fake player state.
- Rebuild AI Song Plan as the reference table: section badges, purpose, energy,
  instrument summary, deterministic waveform visualization, selected row, play
  affordance, regenerate/export actions, and empty/stale/failure states.
- Keep travel and weather strings visual-only, deterministic placeholders;
  never claim live location or weather data.
- Match the reference's right-rail geometry, stacking, borders, and density.

## Verification

- Add Compose tests for selected plan section, unavailable playback, empty
  plan, and stale plan.
- Verify one and only one playback owner/transport remains present.
- Capture a 1536 × 1024 right-rail overlay with `../UI.png` (4 px major-edge
  tolerance) and run `./gradlew :desktopApp:test :desktopApp:build`.

## Out of scope

Scene generation, remote images, live weather, map data, or export pipeline
changes.
