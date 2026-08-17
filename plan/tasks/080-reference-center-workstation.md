# Task 080 — Reference Center Workstation

## Goal

Make Song Structure, Arrangement, and Timeline reproduce the center column of
`../UI.png` using real structure, arrangement, mix, and timeline state.

## Dependencies

- Task 079 accepted.

## Requirements

- Rebuild the Song Structure card with section blocks, timing ruler, total
  duration, duplicate/clear actions, selected occurrence state, and truthful
  empty/blocked/stale states.
- Rebuild the Arrangement card around the selected section: energy indication,
  Edit Section action, all five instrument rows, volume/mute/solo controls, and
  transition in/out cards. Existing actions must continue to dispatch existing
  intents; unavailable controls must explain why.
- Rebuild the Timeline card with bar ruler, five colored instrument lanes,
  deterministic note/clip placeholders derived from the authoritative timeline,
  selection cursor, and the reference control strip. It must never imply a MIDI
  editor or generate a second timing source.
- Match reference card heights, padding, density, borders, instrument colors,
  and typography at the reference viewport. Add reusable visual primitives
  instead of duplicating layout code.
- Keep responsive behavior: medium retains the center workstation and narrow
  exposes the same information in one focused pane.

## Verification

- Add fixtures covering no structure, selected section, stale arrangement, and
  five-or-more structure occurrences.
- Add semantics and interaction tests for section selection, reordering,
  arrangement selection, and visible timeline lanes.
- Capture and overlay the center-column screenshot with `../UI.png`; major
  geometry must be within 4 px at 1536 × 1024.
- Run `./gradlew :desktopApp:test :desktopApp:build`.

## Out of scope

Introducing piano-roll editing or altering the authoritative song clock.
