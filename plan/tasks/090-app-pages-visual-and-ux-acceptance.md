# Task 090 — App Pages Visual and UX Acceptance

## Goal

Complete the concise-copy, responsive, accessibility, and visual acceptance
pass for every page in `../pictures/App-pages.png`.

## Dependencies

- Tasks 083–089 accepted.

## Requirements

- Audit all idle UI copy. Keep page title, at most one short subtitle, field
  labels, status chips, and the primary action. Move workflow explanations,
  artifact theory, placeholder details, and technical diagnostics to
  Details/help/accessibility or documentation.
- Keep failures visible with the failed action, concise cause, and one recovery
  action. Copy reduction must not hide dependency, stale-artifact, approval,
  format, path, or source-safety failures.
- Verify one navigation, one active page, one project selector/settings entry,
  one playback session, and one global feedback surface across all states.
- Finish shared tokens/components for the muted olive palette, typography,
  sidebar/header, cards, inputs, tables, navigation, states, and controls.
- Audit wide, medium, and narrow layouts at 100%, 125%, and 150% scale with
  long names, empty projects, operation failures, missing dependencies, stale
  artifacts, draft approval, and populated five-plus-section projects.
- Eliminate clipping, overlapping controls, duplicate content, and page-level
  horizontal scrolling. Bounded timeline/chip scrolling may remain internal.
- Every interactive control must be keyboard reachable and accessibly named;
  disabled controls must expose a useful reason.
- Document the deterministic screenshot/golden workflow for all pages. Fixtures
  must be independent of files, network, clock, audio device, renderer, worker,
  and model availability.

## Verification

- Regression tests assert exactly one active page and all required elements for
  Overview, Import, Structure, Arrange, Mix & Master, Video Preview, and Export.
- Keyboard checks cover navigation, import, preparation continuation,
  structure editing, planner selection, mix controls, playback, settings, and
  export.
- Capture every page and overlay it against its region in
  `../pictures/App-pages.png`; major shell/card/list/form/preview/timeline/footer
  edges must be within 4 px, followed by human review of type, icons, color,
  focus, hover, selected, disabled, error, and empty states.
- Run `./gradlew test :desktopApp:test :desktopApp:build` and record exact
  results with the golden assets.
- Run worker tests only if Tasks 083–089 changed worker code. Run packaging only
  if package resources or launch behavior changed.

## Out of scope

New music-generation behavior, arbitrary MIDI editing, new DSP/export/video
capabilities, cloud services, telemetry, remote artwork, live location/weather,
or deferred Tasks 059–062.
