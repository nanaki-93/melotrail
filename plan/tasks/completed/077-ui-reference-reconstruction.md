# Task 077 — Exact UI Reference Reconstruction

## Goal

Replace the current section-dependent card stack with the stable workstation
composition shown in `../../UI.png`, backed by real application state and the
simplified workflows from Tasks 074–076.

## Dependencies

- Tasks 075 and 076 accepted.
- Read `../../PLAN.md` and Tasks 074–076 completely before implementation.
- Before coding, confirm whether the mockup's scene artwork, Video Concept,
  Current Location, weather, and destination are functional local project
  metadata or visual-only deterministic placeholders. Do not infer live network
  behavior. If the product decision is still unavailable, stop this task and
  report the specific blocker.

## Reference contract

- `../../UI.png` is the exact wide-screen reference at 1536 × 1024 and 100% scale.
- Match hierarchy, panel geometry, proportions, density, typography, colors,
  borders, radii, icons, selection states, transport, and mixer.
- Real data and truthful disabled/empty states take precedence over copying
  fake values from the image.

## Requirements

- Measure and document design tokens from the reference: major coordinates,
  column widths, header/footer heights, gaps, padding, radii, border opacity,
  typography scale, icon sizes, control heights, and instrument colors.
- Implement the wide-screen regions:
  - left rail: brand, Scenes/Parts, part rows, Add Part, Import MIDI, Import
    Audio, and the confirmed presentation-metadata treatment;
  - top center/right: five navigation destinations, selected project control,
    project actions, settings/theme affordances where supported;
  - center: Song Structure, selected Arrangement section, and instrument
    Timeline;
  - right: confirmed scene/presentation region and functional AI Song Plan;
  - footer: one persistent transport/waveform, five channel strips, master
    strip, and master-bus controls.
- Keep the wide workstation stable. Navigation selects/focuses an editing region
  or mode; it must not replace the three columns with unrelated panel sets.
- Render every music control from current domain/application state. A control
  visible in the target must work or be truthfully disabled with one accessible
  reason. Do not fabricate transport, waveform, meter, weather, status, scene,
  or export success.
- Use local vector resources or an approved bundled icon set instead of Unicode
  control glyphs. Every icon-only control needs an accessible name, focus state,
  and selected/pressed semantics.
- Split the 1,481-line `WorkspaceApp.kt` into bounded components for shell/
  header, parts, structure, arrangement, timeline, scene/plan, transport,
  mixer, dialogs, and tokens. Do not move application orchestration or file
  access into those composables.
- Remove duplicated commands and labels, including the duplicated Role line.
  Remove the separate status card; use one dismissible operation banner that
  does not destabilize the reference geometry.
- Preserve one persistent playback session. Contextual play buttons only select
  a source and delegate to that transport.
- Implement deterministic empty, loading, blocked, stale, failure, and selected
  states without machine paths, network data, or false-success artifacts.
- Implement responsive behavior:
  - wide: exact reference at 1536 × 1024 and proportional support down to the
    agreed wide breakpoint;
  - medium: keep center timeline and transport visible, with side content in
    drawers/panes rather than horizontal page scrolling;
  - narrow: one focused pane, direct navigation to all actions, and persistent
    compact transport.
- Preserve Ctrl/Cmd transport shortcuts, keyboard structure reordering, logical
  focus order, visible focus, 48 dp hit targets, accessible names, and status
  cues that do not depend on color alone.

## Tests and visual verification

- Add deterministic UI fixtures for populated, empty, loading, blocked, stale,
  failure, and selected states.
- Capture goldens at 1536 × 1024 and agreed medium/narrow viewports. Goldens
  must not depend on user files, machine paths, clock, network, worker,
  renderer, model, or audio device.
- Add a documented overlay/diff workflow comparing the 1536 × 1024 golden to
  `../../UI.png`.
- Wide visual acceptance requires major panel edges and heights within 4 px of
  measured reference coordinates and colors within documented token tolerance.
  Typography and icons require human review in addition to pixel diff.
- Test 100%, 125%, and 150% UI scaling, minimum window size, long names, empty
  projects, five-plus structure occurrences, and error banners.
- Compose semantics tests assert exactly one navigation row, project selector,
  Import MIDI, Import Audio, timeline, AI plan, persistent transport, mixer,
  master output, and global feedback surface.
- Keyboard tests cover import, part preparation, structure movement,
  arrangement review, transport, mixer access, and build.
- Run `./gradlew :desktopApp:test :desktopApp:build`, then the root suite.

## Acceptance criteria

- At 1536 × 1024, the running workspace is recognizably the same composition as
  `UI.png` and satisfies the documented geometry tolerances.
- Wide navigation does not swap away the workstation's primary columns.
- All visible music controls are functional or truthfully disabled.
- Import/preparation presents one clear next action and no duplicate retries.
- There is one transport and one playback owner.
- Medium and narrow layouts expose every supported action without horizontal
  page scrolling or clipped panels.
- Visual, semantics, keyboard, scaling, and responsive checks pass.

## Out of scope

- Live weather, maps, travel APIs, cloud imagery, or network scene generation.
- New music algorithms, variable Lo-fi controls, or arrangement timing changes.
- A full DAW/piano-roll editor.
- A new generic plugin system or telemetry.
