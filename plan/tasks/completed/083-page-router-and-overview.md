# Task 083 — Page Router and Overview

## Goal

Replace the multi-panel workstation composition with typed single-page routing
and reproduce the Overview page in `../../pictures/App-pages.png`.

## Dependencies

- Task 082 accepted.
- `../../PLAN.md` is the governing UI/UX plan.

## Requirements

- Define the desktop destinations Overview, Import, Structure, Arrange,
  Mix & Master, Video Preview, and Export. Navigation changes UI selection
  only; they must not rewrite project artifacts or reset workflow state.
- Compose exactly one page root for the selected destination. Keep one global
  feedback surface and one dialog/settings layer outside the page router.
- Use the reference horizontal navigation for Overview and compact left
  navigation for workflow pages. There must never be two navigation surfaces
  in the same composition.
- Explicit project open/create lands on Overview. Normal mutations preserve the
  active destination, selected part/section, drafts, readiness, feedback, and
  the single playback session.
- Rebuild Overview from real state: project title/metadata, song-section strip,
  five-lane track overview, local video placeholder, selected-section summary,
  Export route, and the shared transport.
- Unknown timing, note, waveform, scene, or signal data must be visibly
  unavailable rather than synthesized as real state.
- Add reusable page-shell/navigation tokens measured from
  `../../pictures/App-pages.png`; do not scatter page geometry constants.
- Later destinations may use concise truthful interim page bodies, but may not
  render the former simultaneous workstation columns.

## Verification

- Compose tests select every destination and assert exactly one navigation and
  one active page root, with all other page roots absent.
- View-model tests prove navigation preserves state and ordinary mutation
  completion no longer returns to Project/Overview.
- Overview tests assert section strip, track overview, preview, section info,
  Export action, one transport, and truthful empty/stale states.
- Capture a deterministic Overview fixture and compare its major geometry with
  the large Overview region in `../../pictures/App-pages.png` (maximum 4 px
  major-edge variance).
- Run `./gradlew :desktopApp:test :desktopApp:build`.

## Out of scope

Final Import, Structure, Arrange, Mix, Video Preview, or Export page content;
new playback, generation, rendering, or export behavior.
