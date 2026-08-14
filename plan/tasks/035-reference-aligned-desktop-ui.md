# Task 035 — Reference-Aligned Desktop UI

## Goal

Apply the visual hierarchy of `plan/UI.png` to the guided workspace without
copying irrelevant travel/scene content or sacrificing accessibility.

## Requirements

- Create/extend named Compose theme tokens for charcoal backgrounds, elevated
  cards, teal action/focus state, subdued borders, typography, spacing, corner
  radius, and one stable accent per logical instrument. Remove ad-hoc colours
  from workspace composables where tokens can express intent.
- At wide widths, implement the reference-aligned layout: compact top bar;
  left Parts/Preparation; central Structure, Arrangement, Timeline; right
  Preview, Creation checklist, Status; persistent bottom Transport, Mixer, and
  Master bus. The right preview is music/artifact focused, not imagery.
- Use clear compact cards, icon-plus-text primary controls, consistent section
  headers, selection outlines, badges, status colours with text equivalents,
  and readable hover/focus/disabled states. Do not rely on colour alone.
- Render the timeline proportionally from validated duration/section data and
  instrument lanes from the approved arrangement. Avoid fake waveform, note,
  dB, location, weather, or image data. When an artifact is unavailable, render
  an honest placeholder and next action.
- Keep content usable at 1440×900 and 1100×720; use a deliberate medium layout
  rather than clipped cards. At narrow widths keep one logical vertical flow
  and a reachable transport. Respect HiDPI scaling, long localized-like names,
  keyboard focus order, semantics, and minimum hit targets.
- Capture deterministic screenshots/Compose UI test coverage for the empty,
  ready-to-arrange, Qwen-approval, building, success, and dependency-error
  states. Inspect the renders at required sizes before declaring the task done.

## Tests

- Compose tests for key semantics, focus, controls, responsive breakpoints, and
  non-colour status text.
- Screenshot/render comparison or reviewed golden images at 1440×900,
  1100×720, and HiDPI with stable fixtures.
- Manual keyboard-only and screen-reader-label checks.

## Acceptance criteria

- The workspace is recognizably closer to `UI.png`: compact dark studio shell,
  strong workflow hierarchy, clear structure/timeline, and persistent mix /
  transport, while every displayed value comes from real state.
- It remains legible and operational at the supported dimensions.

## Out of scope

Artwork generation, image/video panels, pixel-perfect duplication of the
reference, and changes to audio/arrangement semantics.
