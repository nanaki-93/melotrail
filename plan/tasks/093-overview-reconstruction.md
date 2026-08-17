# Task 093 — Overview Reconstruction

## Goal

Rebuild the Overview destination to follow
`../pictures/UI/01-dashboard-overview.png` using truthful canonical project,
workflow, arrangement, playback, and release-readiness state.

## Dependencies

- Task 092 accepted.

## Requirements

- Reproduce the reference hierarchy: current-project heading, compact metadata,
  summary cards, song-structure strip, track overview, preview/context rail,
  selected-section information, quick actions, and the shared transport.
- Derive section count/order/identity, track availability, duration, tempo, key,
  sample rate, channels/bit depth where available, arrangement state, and
  release readiness from current snapshots. Unknown values use a concise
  unavailable state and are never synthesized.
- Make the structure strip selectable using stable occurrence identity. Page
  selection is UI state only and must not rewrite canonical structure.
- Build track lanes from real logical instruments and current artifact state.
  Do not draw a waveform, meter, or note pattern as measured data unless the
  read model contains it. Use a visually consistent empty/stale lane otherwise.
- Use the single shared playback session for preview, play/pause, stop, seek,
  and volume. The Overview must not create a second player or clock.
- Quick actions route to Import, Structure, Arrange, Mix & Master, and Export.
  Availability and descriptions must reflect exact workflow prerequisites.
- Show a recent-project list only if a real local recent-project read model is
  available. Otherwise substitute current-project activity/next actions rather
  than hard-coded project names, timestamps, artwork, or durations.
- Keep open/create/settings actions in the shared shell. Avoid duplicate project
  controls inside the page.
- Match the reference spacing, density, cards, selected states, rails, and
  typography through shared Task 092 components.
- Provide concise empty-project, loading, stale, missing-dependency, playback
  failure, long-project-name, and populated multi-section states.

## Verification

- Compose tests assert the summary cards, structure strip, track overview,
  preview/context rail, section info, quick actions, and one shared transport.
- Test quick-action routing and stable section selection.
- Test empty, stale, failed, long-name, unknown-timing, and five-plus-section
  fixtures. Assert missing data is labeled unavailable and not represented as a
  real waveform/meter/value.
- Prove Overview and later Preview controls operate on the same playback state
  and intents.
- Capture a deterministic full-window fixture at 1536 × 1024 and overlay it on
  `../pictures/UI/01-dashboard-overview.png`. Check matching major edges within
  4 px and document truthful intentional differences.
- Repeat layout checks at medium/narrow widths and 100%, 125%, and 150% scale.
- Run `./gradlew :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Overview visibly matches the reference hierarchy and graphic language.
- Every displayed metric and state comes from a validated read model.
- Quick actions and playback controls work without duplicating application
  state or orchestration.

## Out of scope

Fabricated recent projects, waveform analysis, live meters, remote artwork,
weather/location, new project persistence, or page implementations assigned to
later tasks.
