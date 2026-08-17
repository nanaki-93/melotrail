# Task 082 — Reference Footer and Visual Acceptance

## Goal

Complete the persistent footer workstation and prove that the assembled UI
matches `../../UI.png` at the reference viewport.

## Dependencies

- Tasks 079–081 accepted.

## Requirements

- Reconstruct the footer transport/waveform card, five channel strips, master
  meter, and master-bus controls with reference geometry and density.
- Preserve the single persistent playback session and existing keyboard
  shortcuts. Every visible slider, mute/solo, play, seek, and effect control
  must be functional or truthfully disabled and accessible.
- Render waveform, meters, and channel visualizations deterministically from
  available state; placeholders must be visibly placeholders and must not
  report signal or playback that does not exist.
- Add the documented screenshot/golden workflow to the repository. The wide
  1536 × 1024 fixture must be independent of local files, network, system
  clock, audio device, renderer, or worker availability.
- Audit 100%, 125%, and 150% scale, long project/part names, empty projects,
  failure banners, medium, and narrow layouts. Fix clipping, duplicate
  navigation, and page-level horizontal scrolling.

## Verification

- Full 1536 × 1024 overlay/diff against `../../UI.png`: all major panel edges and
  heights within 4 px, followed by human review of type/icons/colors.
- Regression tests assert one navigation row, selector, import actions,
  timeline, song plan, transport, mixer, master output, and feedback surface.
- Run `./gradlew test :desktopApp:test :desktopApp:build` and record the
  commands/results with the golden assets.

## Out of scope

New music-generation features or any external presentation service.
