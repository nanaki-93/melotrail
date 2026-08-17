# Task 088 — Video Preview and Settings

## Goal

Build the focused local Video Preview page from `../pictures/App-pages.png` and
move sound-library configuration into the shell settings surface.

## Dependencies

- Task 087 accepted.

## Requirements

- Reproduce the focused preview artwork, timeline, compact transport, title,
  and reference control placement with deterministic local state.
- Use the existing single `PlaybackSession` for play/pause, stop, seek, and
  volume. The page must not create a video/audio clock or second player.
- Treat scene art and video as a visibly local placeholder unless a validated
  supported artifact exists. Never imply live location, weather, remote art,
  video generation, or successful export.
- Show camera/change/fullscreen controls only when implemented; otherwise keep
  them disabled and accessibly named without explanatory idle paragraphs.
- Move current sound-library chooser, validation status, dependency details,
  and recovery actions behind the shell gear/settings surface. The validated
  locator remains the only library boundary; do not depend on process CWD or
  create another instrument tree.
- Settings must remain reachable from every destination without becoming a
  second page root or resetting navigation/playback state.

## Verification

- Compose tests cover no artifact, selected artifact, playing, paused, failed,
  dependency-unavailable, and long-title states.
- Tests prove Overview and Video Preview controls target the same playback
  session and that only one playback owner exists.
- Settings tests cover choose, clear, valid/invalid library, renderer/sample
  recovery, dismissal, and preservation of the active page.
- Capture and overlay a deterministic Video Preview golden against the numbered
  Video Preview region of `../pictures/App-pages.png`.
- Run `./gradlew :desktopApp:test :desktopApp:build`.

## Out of scope

Video generation/rendering, remote imagery, live location/weather/maps,
automatic downloads, a second sound library, or new playback infrastructure.
