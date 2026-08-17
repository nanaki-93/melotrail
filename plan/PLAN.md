# Melotrail — App Pages UI/UX Implementation Plan

## Goal

Rebuild the Compose Desktop interface around the page designs in
`plan/pictures/App-pages.png` so that navigation shows one focused destination
at a time, prepared parts can always be added to the canonical song structure,
and normal workflows use concise labels and actions instead of persistent
instructional prose.

This plan changes presentation and desktop interaction only. Existing typed
application services, canonical project artifacts, source immutability,
playback ownership, worker validation, renderer validation, and atomic writes
remain authoritative.

## Reference interpretation

`App-pages.png` is the visual source of truth for hierarchy, density, spacing,
colors, typography, selected states, and control placement. It depicts these
destinations:

1. **Overview** — song sections, track overview, video preview, selected-section
   summary, and the shared transport.
2. **Import** — file drop/chooser surface and imported-file list.
3. **Structure** — eligible section palette, ordered song sequence, and compact
   editable section table.
4. **Arrange** — deterministic/Qwen choice, instrument selection, style,
   intensity, and one generation action.
5. **Mix & Master** — compact channel mixer, master preset/effect controls,
   audition mode, and render action.
6. **Library / Video Preview** — the reference's focused local video-preview
   page. Sound-library configuration moves to the settings action/drawer so it
   remains available without competing with the music workflow.
7. **Export** — output format, quality, sample rate, filename, destination, and
   a truthful export summary.

The Export page is opened by the Overview Export action and is shown as the
selected sidebar destination while open. The reference artwork is treated as
visual direction, not evidence of a live scene, location, weather feed, or
exported video. Runtime artwork must be a bundled, approved local asset or an
explicitly labelled deterministic placeholder.

## Repository findings

- `WorkspaceSection` currently has only Project, Structure, Arrange,
  Mix & Master, and Library. Import and Export are dialogs/actions rather than
  focused destinations, and Project currently acts as an overloaded catch-all.
- `StableWorkspaceShell` renders parts, Structure, Arrangement, Timeline,
  presentation, and song-plan panels together in wide and medium layouts.
  Selecting a destination changes emphasis or adds a panel; it does not route
  to a single page.
- The structure domain path already exists:
  `WorkspaceIntent.AddStructurePart` calls `saveStructure`, which delegates to
  `ProjectApplicationService.saveStructure` and reloads the canonical project.
  View-model tests cover add, duplicate, move, and remove operations.
- No current composable dispatches `AddStructurePart`. After MIDI preparation
  reports `READY_FOR_STRUCTURE`, the message tells the user to add the part,
  but neither the part row nor Structure panel offers that action. This is the
  reported workflow break.
- `opened(...)` always returns navigation to `WorkspaceSection.PROJECT`, even
  after ordinary mutations such as saving structure or editing a role. Page
  selection must be preserved for in-page operations and reset only for an
  explicit create/open flow.
- Idle panels contain repeated explanations of artifact stages, unavailable
  features, placeholder behavior, and build internals. Important safety and
  recovery information is diluted by persistent instructional copy.
- The worktree already contains unrelated user-owned changes under `plan/`.
  Implementation must preserve them and stage explicit files only if a later
  request asks for a commit.

## UX rules

1. **One destination, one page.** The content area renders exactly one page
   selected by `WorkspaceSection`. Dialogs, transient feedback, and the shared
   playback session do not count as additional pages.
2. **One playback owner.** Overview and Video Preview may expose transport
   controls, but both adapt the same `PlaybackSession`; they never create a
   second player or independent clock.
3. **One primary action per state.** Import, preparation, structure,
   arrangement, render, and export pages each emphasize one safe next action.
4. **Canonical state decides availability.** Buttons are enabled only from
   validated project/readiness snapshots. A disabled control has a concise
   accessible reason, not a paragraph in the visible layout.
5. **Short copy by default.** Use a heading, optional one-line subtitle, field
   labels, status chips, and action labels. Put advanced explanations in a
   Details/help surface. Keep errors and recovery actions visible.
6. **No invented capabilities.** Unsupported video, location, weather,
   mastering parameters, or export formats remain visibly unavailable or are
   omitted. The mockup does not authorize new worker, renderer, or DSP APIs.
7. **No page-level horizontal scrolling.** Each page adapts to wide, medium,
   and narrow windows. Only bounded controls such as a timeline or chip row may
   scroll internally when necessary.
8. **Keyboard and accessibility parity.** Every page, list, reorder action,
   slider, toggle, menu, and primary CTA remains keyboard reachable with a
   useful accessible name and state.

## Navigation and shell

Replace the current stable three-column composition with a small router over
typed page composables:

```text
WorkspaceScreen
├── WorkspaceChrome
│   ├── Overview: horizontal reference navigation
│   └── Workflow pages: compact left sidebar navigation
├── one WorkspacePage selected from WorkspaceSection
├── one global operation-feedback surface
└── dialogs/settings drawer
```

Use these typed destinations:

```text
OVERVIEW
IMPORT
STRUCTURE
ARRANGE
MIX_MASTER
VIDEO_PREVIEW
EXPORT
```

Sound-library settings remain a typed settings surface reached from the gear
control. Preserve a compatibility mapping from the existing `PROJECT` and
`LIBRARY` values if saved UI state or tests depend on them; project artifacts
must not be migrated for a navigation-only change.

Navigation acceptance:

- Exactly one navigation component is present for the active shell.
- Exactly one page root is composed and exposed in semantics.
- Clicking Structure removes Import, Arrange, Mix, Overview, Video Preview,
  and Export page roots from the composition.
- Back/forward page changes do not reset project, selected part, structure,
  draft, playback, feedback, or validated readiness state.
- Ordinary page mutations keep the current destination. Explicit project open
  or create lands on Overview.

## Page specifications

### Overview

- Reproduce the large top composition from the reference: project title and
  compact metadata, section strip, five-lane track overview, right-side video
  preview, selected-section summary, and bottom transport.
- Derive every section, duration, lane, selected state, and transport value
  from existing snapshots. Unknown duration or signal is shown as unavailable,
  never fabricated.
- Keep one Export CTA that routes to Export. The project selector/settings
  actions stay in the shell and do not create duplicate page controls.
- Remove the old simultaneous Parts, Structure, Arrangement, Timeline, Scene,
  Song Plan, mixer, and master-bus card stack.

### Import

- Match the reference two-column content: one file drop/chooser card and one
  imported-files card. Drag/drop and Browse use the same validated file-dialog
  boundary and import contract.
- Retain actual-format validation for MIDI, WAV/WAVE, and MP3. State the
  solo-piano limitation once beside supported formats; move technical worker
  details to an error/recovery surface.
- Each imported item shows name, type/size, preparation status, and a compact
  overflow/details action. It does not expose every internal artifact stage.
- After a successful import, make the state-derived primary action visible:
  Prepare MIDI, inspect/transcribe supported audio, review repair, or continue
  to Structure.

### Structure

- Reproduce the reference hierarchy:
  1. eligible prepared-part palette;
  2. ordered structure row;
  3. compact section table with role, bars, edit, duplicate/remove, and reorder
     affordances.
- A part is eligible when `primaryPartAction(part)` is `AddToStructure`, meaning
  its selected MIDI and analysis are current. Ineligible parts remain visible
  only when a concise status/action helps the user finish preparation.
- Every eligible palette item has a visible keyboard-accessible action that
  dispatches `WorkspaceIntent.AddStructurePart(part.id)`.
- Adding, duplicating, removing, or moving an occurrence writes through the
  existing typed `saveStructure` path. Never maintain a competing UI-only
  structure as completion state.
- Repeated parts are allowed and retain stable occurrence identities. Empty
  structure shows the palette and one short instruction: “Choose a prepared
  part to start.”
- After successful MIDI preparation, expose both “Add to structure” and a
  “Go to Structure” route. Do not rely on a notification as the only path.
- Preserve Structure as the active page after every successful structure
  mutation. Existing downstream artifacts become stale through the current
  application rules and remain inspectable.

### Arrange

- Match the two planner cards, instrument toggle grid, and compact arrangement
  settings row from the reference.
- Deterministic and Qwen controls map only to existing bounded planner enums.
  Qwen never bypasses draft approval.
- Generate Arrangement is enabled only when canonical structure, analyses, and
  cohesion prerequisites are current. Put the one-line missing prerequisite
  near the disabled CTA; detailed diagnostics belong in help/details.
- Show draft review/approval in the same page without composing Structure or
  Timeline as separate editor pages underneath it.

### Mix & Master

- Match the reference split: five logical mix rows on the left and preset/
  filter controls on the right, followed by Listen/Mix/Master mode controls and
  the primary Render/Build action.
- Existing gain, pan, mute, solo, Lo-Fi build option, playback source, and Build
  Song intents remain authoritative. Unsupported effect knobs are omitted or
  truthfully disabled with accessible reasons.
- Meters show measured state only when the UI model contains measured levels;
  otherwise render a clearly zero-signal placeholder.
- Master remains a release operation backed by validated artifacts; page
  navigation or preview never reports a successful render.

### Library / Video Preview

- Reproduce the large focused artwork/preview, timeline, and compact transport
  shown in the reference. Use deterministic local visual state and the shared
  playback session.
- Camera/change/fullscreen controls are shown only if implemented; otherwise
  they are disabled and accessible without persistent explanatory paragraphs.
- Sound-library selection and readiness move to the settings drawer opened by
  the gear control. Required recovery actions remain available there.

### Export

- Reproduce the form-and-summary layout from the reference.
- Populate only supported choices. WAV is the authoritative lossless export;
  MP3 appears only when the existing optional exporter is available. Do not
  advertise unsupported video or codecs.
- Validate filename and destination through typed application/file-dialog
  boundaries and project-relative rules where applicable. Export publication
  remains atomic and output is validated before success.
- The summary is derived from the current master and release metadata. Missing
  or stale artifacts disable Export Song with one concise recovery action.

## Content reduction

Apply this copy hierarchy consistently:

| Surface | Visible copy | Deferred copy |
| --- | --- | --- |
| Page header | title plus one short subtitle at most | workflow explanation |
| Empty state | one sentence plus one CTA | artifact graph and prerequisites |
| Disabled action | short nearby reason or accessible description | technical diagnostics |
| Success | outcome and resulting artifact name | internal stages already completed |
| Failure | failed action, cause, and one recovery action | logs/details |
| Advanced settings | current value and label | safety explanation in Details/help |

Remove repeated sentences about local-only operation, immutable sources,
placeholder visuals, build internals, stale-artifact theory, and unavailable
features from normal idle layouts. Preserve those guarantees in code, tests,
documentation, accessibility descriptions, contextual help, and error states.

## Visual system

- Measure page and crop geometry directly from the 1536 × 1024
  `App-pages.png`; record reusable values in `MusicWorkspaceTokens` rather than
  scattering local constants.
- Add page-shell tokens for sidebar/header width, content margins, card radii,
  row height, input height, navigation states, table density, and the muted
  olive primary accent used by the new reference.
- Reuse one component family for page headers, navigation items, cards,
  segmented controls, field rows, status chips, compact icon buttons, and
  primary/secondary actions.
- Long project/file/role names use ellipsis with full accessible text. Controls
  never overlap at 100%, 125%, or 150% scale.
- Wide layout follows the reference. Medium uses the same single page with
  reduced columns. Narrow stacks that page's regions and collapses the sidebar
  into a single navigation control; it never composes other destinations.

## Delivery sequence

### Phase 1 — Page router and chrome

- Introduce the destination model and one-page router.
- Build Overview and workflow-page chrome from shared tokens.
- Preserve state across navigation and ordinary mutations.
- Add regression tests proving one navigation and one active page.

### Phase 2 — Import and Structure workflow repair

- Build focused Import and Structure pages.
- Restore the missing visible `AddStructurePart` dispatch.
- Add the post-prepare Continue/Add action.
- Preserve Structure selection across save/duplicate/move/remove.
- This phase must land before visual-only Arrange/Mix work because it fixes the
  reported blocking workflow defect.

### Phase 3 — Arrange and Mix & Master

- Adapt existing planner, instrument, mix, build, and playback intents to the
  reference pages.
- Remove duplicate panels and technical idle copy.
- Keep approval and artifact-readiness behavior unchanged.

### Phase 4 — Video Preview, settings, and Export

- Build the local deterministic Video Preview page.
- Move sound-library configuration to settings without weakening recovery.
- Build the supported Export page over existing release/export services.

### Phase 5 — Copy, responsiveness, and visual acceptance

- Complete the prose audit and advanced help/details surfaces.
- Verify all pages at wide, medium, narrow, 100%, 125%, and 150% scale.
- Capture deterministic page goldens and perform overlay/human review against
  the corresponding regions of `App-pages.png`.

Each phase must remain buildable and receive focused tests before the next
phase begins. Do not combine the full redesign into one unreviewable change.

## Test plan

### View-model and application tests

- Navigation changes preserve project, selection, drafts, readiness, feedback,
  and playback session.
- Explicit open/create selects Overview; mutation completion preserves the
  current page.
- Import -> Prepare MIDI -> Add to Structure saves the correct part ID through
  the canonical service.
- Audio import follows inspect/optional cleanup/transcribe/prepare before it is
  eligible for Structure.
- Add, repeated add, duplicate, reorder, remove, and clear remain deterministic
  and invalidate only documented descendants.
- Stale or unprepared parts cannot bypass eligibility through a UI intent.

### Compose tests

- Every destination renders exactly one matching page root and zero page roots
  for all other destinations.
- Overview contains section strip, track overview, preview, section info, and
  one shared transport.
- Import contains chooser/drop target, imported list, and one primary action.
- Structure contains eligible palette, sequence, section table, and a working
  Add to Structure control.
- Arrange contains planner selection, instrument choices, settings, and one
  generation CTA.
- Mix contains five channels, master controls, playback mode, and one render
  CTA.
- Video Preview and Export expose only truthful supported actions.
- Empty project, long names, operation failure, stale artifacts, missing
  dependency, medium, and narrow fixtures retain navigation and feedback with
  no duplicate page or page-level horizontal scrolling.
- Keyboard tests cover navigation, file chooser, structure add/reorder/remove,
  planner selection, sliders/toggles, playback, and export.

### Visual acceptance

- Create deterministic fixtures independent of files, network, clock, audio
  device, renderer, worker, and model availability.
- Capture each destination at its reference viewport/crop.
- Compare major page, sidebar, card, list, form, preview, timeline, and footer
  edges to `App-pages.png` with a maximum 4 px variance.
- Use a documented RGB tolerance for pixel differences, then review type,
  icons, artwork treatment, focus, hover, selected, disabled, error, empty, and
  long-content states by eye.

### Commands

```bash
./gradlew :desktopApp:test
./gradlew test :desktopApp:test :desktopApp:build
```

Run worker tests only if a later implementation phase changes worker code or
its HTTP contract. Run packaging only when a phase explicitly changes package
resources or launch behavior.

## Definition of done

- Selecting a destination composes only that page.
- A successfully prepared/analyzed MIDI part has an obvious, working path into
  the canonical structure; the same is true for eligible transcribed audio.
- The UI matches the page hierarchy and graphic language of `App-pages.png` at
  the reference viewport and remains usable at supported scales and widths.
- Normal pages are concise; technical explanations are available on demand,
  while failures and recovery remain explicit.
- There is one project source of truth, one playback session, one navigation
  surface, one active page, and one global feedback surface.
- Focused and full desktop checks pass, with actual golden and manual-review
  results recorded.

## Out of scope

- New music-generation algorithms, arbitrary MIDI editing, or a DAW piano roll.
- Cloud services, telemetry, remote artwork, live location/weather, or external
  presentation services.
- A new project database/format, automatic source rewrites, or deletion of
  stale artifacts.
- New DSP controls, export codecs, video rendering, or sample/model downloads
  added only because they appear in the mockup.
- Deferred worker Tasks 059–062 unless a later user request explicitly promotes
  one of them.
