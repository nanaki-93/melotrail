# Task 053 — Timeline, Mixer, and Visual-State Completion

## Goal

Complete the reference-aligned music visualization using only validated project
and arrangement state.

## Dependencies

- Task 052 accepted.

## Requirements

- Render proportional structure sections and instrument lanes from validated
  durations/approved arrangement; selected part/section is linked across panels.
- Apply stable instrument accents to timeline and compact mixer. Preserve gain,
  pan, mute, solo, master controls, debounced service updates, and transport.
- Never fabricate waveform, notes, dB values, or unavailable artifacts. Render
  honest placeholder/next action when data is absent or stale.
- Capture deterministic reviewed screenshots for empty, ready, Qwen approval,
  building, success, and dependency failure at required sizes.

## Tests

- Proportional calculations, lane availability, selection linkage, stale/empty
  state, mixer semantics/behavior, and screenshot fixtures.

## Acceptance criteria

- The final Compose UI matches the reference’s dense studio hierarchy while
  every value comes from real state.

## Out of scope

Piano roll, waveform editor, automation, plugins, or reference scene imagery.
