# Melotrail — UI Reconstruction and Import Workflow Repair Plan

## Goal

Rebuild the Compose Desktop shell and every user-facing page to follow the
individual 1536 × 1024 references under `plan/pictures/UI/`, while preserving
Melotrail's existing application boundaries and restoring the Import actions
that became unreachable after the single-page router refactor.

The finished UI must provide a coherent dark-purple workstation layout,
faithful page hierarchy, responsive behavior, keyboard access, and a working
path from import through preparation, analysis, structure, arrangement, build,
preview, and supported audio export.

This is a desktop presentation and interaction plan. Canonical project files,
typed Kotlin application services, worker validation, source immutability,
atomic publication, renderer validation, and the shared playback session remain
authoritative.

## References

Use each image as the visual source of truth for its destination:

| Destination | Reference |
| --- | --- |
| Overview | `pictures/UI/01-dashboard-overview.png` |
| Import | `pictures/UI/02-import.png` |
| Structure | `pictures/UI/03-structure.png` |
| Arrange | `pictures/UI/04-arrange.png` |
| Mix & Master | `pictures/UI/06-mix-master.png` |
| Library | `pictures/UI/07-library.png` |
| Video Preview | `pictures/UI/08-video-preview.png` |
| Export | `pictures/UI/09-export.png` |
| Settings | `pictures/UI/10-settings.png` |

There is no `05` reference. Do not invent a missing destination.

The mockups control visual hierarchy, proportions, spacing, typography,
colors, selected states, card/table structure, and responsive priorities. They
do not by themselves authorize new DSP, video-generation, cloud, telemetry,
download, codec, project-format, or worker behavior.

## Planning assumptions and open decisions

Implementation may begin with the Import workflow repair while these visual
decisions remain open:

1. Retain the **Melotrail** product name and replace the mockups' “AI Music
   Workstation” text unless an explicit branding change is requested.
2. Render only controls backed by current typed services. Unsupported controls
   are omitted when they would imply a capability; a disabled control is used
   only when it is important for explaining the layout or a known future
   action, and must have an accessible reason.
3. Do not extract scenic artwork from a flattened screenshot for release. Use
   a bundled approved source asset when provided; until then, use one
   deterministic local placeholder that preserves the reference geometry.
4. “Library” becomes a distinct destination, but initially represents only
   validated local instruments/samples and sound-library readiness. It does
   not imply a store, downloads, favorites, or remote content.
5. “Video Preview” remains a local visual paired with the shared audio playback
   session. Video rendering/export controls remain absent until a real typed
   service and validated output contract exist.

If any assumption changes, update this plan and the affected task contract
before implementation.

## Repository findings

### Current implementation

- Tasks 083–090 introduced the single-page router and first-pass versions of
  Overview, Import, Structure, Arrange, Mix & Master, Video Preview, and Export.
- The current page captures under `desktopApp/build/reports/task-090-*-capture.png`
  are structurally sparse compared with the new page references. They lack the
  reference shell density, purple visual language, project rail, full tables,
  page-specific right rails, artwork treatment, and most shared components.
- `WorkspaceSection` has no Library destination. The prior Library value was
  converted into Video Preview, while sound-library configuration remains a
  settings dialog.
- `WorkspaceScreen` composes `WorkspacePageRouter`; the older
  `StableWorkspaceShell`, `MidiQualityReviewPanel`, and
  `AudioPreparationPanel` implementations remain in source but are no longer
  part of the active Import page.
- Navigation and ordinary mutations already preserve the selected destination.
  Project create/open intentionally returns to Overview.
- The application layer already exposes typed operations for project import,
  MIDI preparation/repair, analysis, audio inspection/cleanup/transcription,
  structure persistence, arrangement, mix/build, playback, sound-library
  readiness, and WAV/optional MP3 release export.

### Confirmed Import regression

The Import failure is a routing/presentation regression rather than a missing
application service:

- The imported-row `⋮` button dispatches `ShowPartDetails(partId)`.
- `ShowPartDetails` only sets `selectedPartId` and
  `partDetailsExpanded = true`.
- The active page router does not compose a details drawer/dialog or the old
  MIDI/audio preparation panels, so the click produces no visible result.
- `ReviewRepair` and `FixIssue` dispatch the same now-invisible action.
- A repaired/current MIDI part with no MIDI analysis falls through to
  `FixIssue`; `PartPrimaryAction` has no `Analyze` case even though
  `WorkspaceIntent.AnalyzePart(partId)` and the application service still
  exist.
- Existing Compose tests only assert emitted intents. They do not run the
  screen with the real view model and assert that a visible, actionable surface
  appears, which allowed the regression to pass.

## Non-negotiable UX and architecture rules

1. **One active destination.** Exactly one page root is composed. Dialogs,
   drawers, global feedback, and shared playback do not count as pages.
2. **One shell.** Wide pages share the same top bar and project/navigation rail.
   Medium and narrow layouts adapt that shell without duplicating navigation.
3. **One source of truth.** Page state is derived from `WorkspaceUiState` and
   canonical snapshots. Composables never infer completion from local flags.
4. **One playback session.** Overview, rows, Structure, Arrange, Mix, Library,
   and Preview may control the same playback owner; none creates an independent
   player or clock.
5. **Every visible action works.** A click must either perform a typed intent,
   open a visible surface, route to a destination, or be truthfully disabled
   with an accessible reason.
6. **No mock functionality.** Do not present fabricated waveforms, levels,
   timing, analysis, recent projects, library inventory, metadata, or output
   estimates as measured state.
7. **Safe external input.** File drops, chooser results, names, destinations,
   and library roots continue through validated application/file-dialog
   boundaries.
8. **Responsive and accessible.** No page-level horizontal scrolling. Bounded
   timelines, mixers, tabs, and chip rows may scroll internally. All actions
   remain keyboard reachable and have useful semantics.
9. **Preserve user changes.** The current worktree contains user-owned changes
   under `plan/`; implementation must stage only explicit task files if a
   commit is requested.

## Target information architecture

```text
WorkspaceScreen
├── AppTopBar
│   ├── Melotrail brand
│   ├── wide destination navigation
│   └── help, settings, and account/local-mode area
├── WorkspaceBody
│   ├── ProjectRail
│   │   ├── current project summary
│   │   ├── destination navigation at workflow widths
│   │   └── deterministic local artwork/footer
│   ├── one routed WorkspacePage
│   └── optional page-specific ContextRail
├── GlobalOperationFeedback
└── modal layer
    ├── project create/open/import forms
    ├── imported-part details/preparation
    ├── confirmations
    └── compact dialogs that do not merit a destination
```

Target destinations:

```text
OVERVIEW
IMPORT
STRUCTURE
ARRANGE
MIX_MASTER
LIBRARY
VIDEO_PREVIEW
EXPORT
SETTINGS
```

Settings may be presented as a routed page on wide/medium layouts and as a
full-height sheet on narrow layouts, but it must have one stable typed state
and one semantics root.

## Shared visual system

Measure reusable values directly from the 1536 × 1024 references and store
them in `MusicWorkspaceTokens` or focused theme/component files.

### Tokens

- background, elevated surface, selected surface, border, divider, disabled,
  text-primary, text-secondary, purple accent, accent gradient, success,
  warning, error, and per-track colors;
- top-bar height, project-rail width, context-rail width, page insets, content
  gaps, card radii, border thickness, table row height, input height, and
  minimum hit target;
- display, page title, section title, body, label, metadata, and numeric text
  styles;
- wide, medium, and narrow breakpoints based on content fit rather than only
  the existing first-pass values.

### Shared components

- app logo/wordmark and top navigation item;
- project summary and workflow navigation item;
- page header and section header;
- elevated card and outlined card;
- primary, secondary, destructive, icon-only, and overflow actions;
- tabs and segmented controls;
- status badge, format badge, and track badge;
- labeled slider/toggle/select/text field;
- dense data-table header and row;
- preview artwork frame and compact shared transport;
- empty, loading, error, and unavailable states;
- context rail and full-height responsive sheet.

Use real vector icons from one Compose-compatible icon family or bundled local
SVG resources. Remove text glyphs such as `⋮`, `✎`, `⧉`, and `×` once their
equivalent accessible icon buttons are available.

## Page specifications

### Overview

Follow `01-dashboard-overview.png`:

- top navigation plus project/navigation rail;
- current project heading and truthful compact metadata;
- summary cards for sections, tracks, duration, tempo, and key only when those
  values exist in current snapshots;
- canonical song-structure strip with stable occurrence identity;
- track overview derived from real arrangement/stem state; use a clearly empty
  lane instead of a fabricated waveform;
- preview/context rail using the shared playback session;
- recent-project area only if a real local recent-project model exists;
  otherwise use a concise current-project activity/next-actions section;
- quick actions route to Import, Structure, Arrange, Mix & Master, and Export.

Do not fabricate multiple projects, dates, weather, location, duration,
waveforms, or audio levels to match the screenshot.

### Import

Follow `02-import.png` while preserving the actual preparation contract:

- distinct MIDI and eligible solo-piano audio chooser cards; both use the same
  validated import workflow and accept only their supported formats;
- one drag/drop target routed through the same validation as chooser input;
- a dense imported-files table showing preview availability, filename,
  detected type, measured analysis metadata when available, preparation status,
  and per-row actions;
- a page context rail containing only real preparation options/status for the
  selected part;
- a visible part-details sheet/dialog for repair reports, MIDI feel, audio
  inspection, cleanup choice, transcription input, preview, and recovery;
- one state-derived next action for each row and a clear primary action for the
  selected part.

Required action-state mapping:

| Canonical state | Primary action |
| --- | --- |
| raw MIDI missing current repair | Prepare MIDI |
| repair approval required | Review repair; open visible details |
| repair current, MIDI analysis missing/stale | Analyze; dispatch `AnalyzePart(partId)` |
| eligible audio not inspected | Inspect audio |
| inspected audio awaiting transcription | Select input / Transcribe solo piano |
| MIDI feel choice differs | Apply and re-analyze |
| current MIDI analysis | Add to Structure or route to Structure |
| validation/warning blocks progress | Open visible details with cause and recovery |

Do not add mockup actions such as Clear All, Delete, Normalize Audio, or Process
All unless the application layer has an explicit safe contract for them.

### Structure

Follow `03-structure.png`:

- canonical occurrence strip across the top;
- prepared-part palette and Add Section action;
- compact section table with occurrence, role/name, bars/duration when known,
  edit, duplicate, remove, and earlier/later controls;
- selected-section context rail and truthful song summary;
- shared preview controls when a valid part/arrangement artifact exists;
- keyboard reordering as a complete alternative to drag-and-drop.

All mutations continue through `saveStructure`. Repeated parts remain allowed,
and occurrence identity must remain stable. “Suggest Structure” is omitted
unless a bounded application service is introduced by a separate task.

### Arrange

Follow `04-arrange.png`:

- arrangement header, planner action, and undo/redo only if backed by a real
  draft history;
- tabs/sections for the currently supported planner, instruments, style,
  intensity, transitions, and review state;
- structure-aligned track/timeline visualization derived from current
  arrangement snapshots;
- page context rail for supported settings and logical instruments;
- deterministic and Qwen paths remain distinct, with explicit approval for
  Qwen drafts;
- primary Generate Arrangement action reflects exact missing prerequisites;
- approval/review is visible on the same destination.

Do not fabricate MIDI notes, waveforms, AI suggestions, undo history, optional
instruments, or arrangement parameters that are not in the typed model.

### Mix & Master

Follow `06-mix-master.png`:

- channel-strip layout for real logical/rendered stems with gain, pan, mute,
  solo, grouping only when supported, and measured/zero-signal meters;
- bounded horizontal mixer scrolling at smaller widths, never whole-page
  horizontal scrolling;
- preview/context rail with current playback source and supported master/build
  choices;
- Listen/Mix/Master selection remains a view over existing playback/build
  intents;
- Lo-Fi texture, MP3 option, reset, master volume, and Build Song remain
  connected to current typed state.

Advanced EQ, compression, saturation, reverb, sends, automation, LUFS, peak,
and reference-track controls are omitted or shown only after real application
models and measured values exist. The mockup is not evidence that those
features work.

### Library

Follow `07-library.png` as a local validated asset browser:

- introduce a distinct `LIBRARY` destination;
- list only instruments/samples discovered through the validated sound-library
  locator and registry;
- support search, category/type filters, grid/list presentation, selection,
  and details only over inventory actually available in the read model;
- show readiness, license/source details, and missing-sample recovery;
- preview only when a validated preview boundary exists.

Do not expose Add Item, Download, favorite, storage quota, pagination count, or
Insert to Project until typed application contracts exist. A missing library
shows configuration and recovery rather than fake catalog entries.

### Video Preview

Follow the hierarchy of `08-video-preview.png` without claiming video support:

- large deterministic local artwork/placeholder area;
- one compact shared audio transport;
- section/occurrence timeline derived from canonical structure and known
  durations;
- selected section and playback position remain synchronized in UI state;
- page copy clearly calls this a local visual preview.

Aspect ratio, resolution, frame rate, animation, scene generation,
transitions, camera capture, fullscreen, and video export remain omitted or
truthfully unavailable until separately implemented and validated.

### Export

Follow `09-export.png` using only current release capabilities:

- audio-only mode is selected and authoritative;
- WAV is always available when the current master passes validation;
- MP3 appears only when the optional exporter is available;
- derive sample rate, channels, bit depth, duration, master freshness, and
  release summary from the inspected release artifact;
- validate filename and destination through typed boundaries;
- publish atomically and report success only after output validation;
- use the context rail for preview, truthful summary, and actionable missing or
  stale prerequisites.

Hide video/audio-and-video, FLAC, unsupported resampling, metadata editing,
loudness normalization, dithering, stem export, fade-out, and estimated export
time/file size unless backed by real contracts and measurements.

### Settings

Follow `10-settings.png` as a real settings destination:

- provide tabs/cards only for currently stored, validated settings;
- move sound-library root selection, refresh, readiness, renderer/worker/audio
  dependency status, and recovery into the appropriate sections;
- show actual app version/build/platform information from local runtime/build
  metadata when available;
- keep destructive reset/clear actions explicit and confirm them;
- preserve preference migration and keep project/audio data out of settings.

Do not expose telemetry, crash upload, update checks, community links,
cloud/AI-model downloads, autosave, backup, language, theme, or audio-device
controls unless their behavior and persistence are implemented in a separate
task. Disabled decorative toggles are not acceptable.

## Delivery sequence

Create one numbered contract under `plan/tasks/` for each package before
implementation. Use `PROMPT_TEMPLATE.md` with exactly one selected contract at
a time. The proposed next numbers assume no other task is added first.

### Task 091 — Import action and details recovery

- Add `Analyze` to the canonical `PartPrimaryAction` mapping.
- Make repaired-but-unanalyzed MIDI dispatch `AnalyzePart(partId)`.
- Replace the invisible `partDetailsExpanded` path with one visible routed
  details sheet/dialog shared by row overflow, repair review, and fix/recovery.
- Adapt the existing MIDI-quality and audio-preparation controls into that
  surface without moving orchestration into composables.
- Add end-to-end Compose/view-model interaction tests proving that `⋮`, repair,
  analysis, audio inspection/transcription, MIDI feel, retry, and dismissal are
  visibly actionable.

This task is the blocking functional repair and must land first.

### Task 092 — Shared shell, design tokens, icons, and responsive frame

- Implement the top bar, project rail, context rail, purple theme, shared
  components, typography, and breakpoints.
- Add `LIBRARY` and `SETTINGS` destinations with compatibility-safe routing.
- Keep exactly one page root and one navigation surface at every width.
- Establish deterministic local artwork handling without extracting a release
  asset from flattened mockups.

### Task 093 — Overview reconstruction

- Rebuild Overview against `01-dashboard-overview.png` using real project,
  workflow, structure, arrangement, playback, and release-readiness state.
- Connect quick actions and remove fabricated/sparse placeholder content.

### Task 094 — Import visual reconstruction

- Rebuild the chooser cards, imported table, selected-part context rail, and
  responsive states against `02-import.png`.
- Preserve the repaired Task 091 action behavior and validation boundaries.

### Task 095 — Structure reconstruction

- Rebuild occurrence strip, palette, section table, preview/context rail, and
  summary against `03-structure.png`.
- Retain canonical saves and keyboard reorder coverage.

### Task 096 — Arrange reconstruction

- Rebuild the timeline/settings/review page against `04-arrange.png` using only
  validated arrangement data and supported planner options.

### Task 097 — Mix & Master reconstruction

- Rebuild channel strips, supported master controls, preview rail, and build
  action against `06-mix-master.png`.
- Clearly distinguish measured meters from zero-signal/unavailable state.

### Task 098 — Local Library destination

- Introduce a local registry-backed Library page against `07-library.png`.
- Keep unavailable store/download/edit actions out of the product UI.

### Task 099 — Video Preview and Export reconstruction

- Rebuild the truthful local preview against `08-video-preview.png`.
- Rebuild supported audio export against `09-export.png`.
- Preserve one playback session and current release-export validation.

### Task 100 — Settings destination

- Rebuild supported local settings/readiness against `10-settings.png`.
- Preserve preference migration and remove decorative unsupported controls.

### Task 101 — Full visual, responsive, accessibility, and workflow acceptance

- Complete cross-page copy/component consistency.
- Capture full-shell deterministic fixtures at 1536 × 1024.
- Verify wide, medium, and narrow layouts at 100%, 125%, and 150% scale.
- Exercise the complete import-to-export workflow and all keyboard paths.
- Remove or explicitly quarantine dead first-generation layout code only after
  the active pages have equivalent functional coverage.

Do not combine Tasks 091–101 into one change. Every task must leave the desktop
module buildable and independently reviewable.

## Testing strategy

### Interaction regression tests

- Clicking an imported row or `⋮` selects exactly that part and opens a visible
  details surface.
- Dismissing details leaves the Import page active and preserves project state.
- Current repaired MIDI without analysis shows Analyze and dispatches
  `AnalyzePart` for the correct ID.
- Repair approval opens visible evidence and the approval button reaches the
  view model.
- MIDI repair retry, MIDI feel apply/re-analysis, audio inspection, cleanup
  confirmation, transcription-input selection, transcription, preview, and
  recovery are reachable from the active UI.
- Ready analysis offers Add to Structure or a route to Structure and writes
  only through the canonical structure service.
- Tests use the real `WorkspaceViewModel` with fakes where intent-only tests
  would miss a composition regression.

### Router and state tests

- Every destination composes one matching page root and zero other page roots.
- Navigation preserves project, selected part/occurrence, drafts, feedback,
  readiness, and shared playback.
- Explicit create/open lands on Overview; ordinary mutations preserve the
  active page.
- Library and Settings routing do not migrate or rewrite project artifacts.
- Details/sheets have deterministic open, dismiss, focus-return, and Escape
  behavior.

### Page tests

- Cover empty, loading, ready, stale, failed, missing-dependency, long-content,
  approval-required, and operation-in-progress states for every page.
- Assert that every visible enabled action emits a typed intent or opens a
  visible UI surface.
- Assert unsupported mockup-only actions are absent or accessibly unavailable.
- Verify lists/tables preserve stable item identity and selection.
- Verify no unbounded page-level horizontal scroll at supported widths.

### Visual acceptance

- Capture the complete window rather than only a cropped page root.
- Use deterministic in-memory fixtures independent of filesystem contents,
  network, clock, worker, model, renderer, and audio device.
- Capture each destination at 1536 × 1024 and compare it with its corresponding
  `pictures/UI` reference.
- Check major top-bar, rail, page, context-rail, card, table, timeline, and
  footer edges within 4 px where the product contains the same region.
- Use a documented RGB tolerance for surfaces and borders; review typography,
  icons, artwork, hover, focus, selected, disabled, error, empty, and long-text
  states manually.
- Repeat layout review at medium/narrow widths and 100%, 125%, and 150% scale.
- Record intentional differences caused by truthful capability constraints.

### Commands

Use the smallest focused test during each implementation task, then run:

```bash
./gradlew :desktopApp:test
./gradlew test :desktopApp:test :desktopApp:build
```

Run Python worker tests only if a task changes worker code or its HTTP contract.
Run packaging only if package resources, icons, fonts, or launch behavior
change.

## Definition of done

- All nine reference destinations have a deliberate, cohesive implementation.
- The shell, purple graphic language, spacing, cards, tables, controls, and
  responsive structure visibly follow the individual mockups.
- Import `⋮`, repair review, analysis, audio preparation, and next-step actions
  are visible and functional after import.
- Every enabled control is connected to a typed intent and produces visible,
  truthful feedback.
- No unsupported mockup feature is presented as working.
- There is one canonical project state, one active page, one navigation
  surface, one playback session, and one global operation-feedback surface.
- Full desktop tests and build pass, and deterministic full-window captures
  have documented overlay/manual-review results.
- Dead legacy layout code is removed only after active-page functional parity
  is proven.

## Out of scope

- New composition/generation algorithms or arbitrary MIDI/DAW editing.
- Advanced DSP, mastering meters, resampling, new codecs, or stem export added
  only to imitate the mockups.
- Video generation, scene generation, transitions, or video export.
- Cloud services, telemetry, crash uploads, remote content, stores, downloads,
  update services, accounts, or collaboration.
- A new project database/format or open-time rewriting of canonical projects.
- Deletion or mutation of source audio/MIDI, silent cleanup, normalization,
  pitch/tempo changes, or fabricated analysis/level data.
- Deferred worker Tasks 059–062 unless explicitly promoted.
