# Mockup-faithful UI implementation tasks

Status: planned; execution not started

Parent authority: [PLAN.md](../../PLAN.md), implemented through
[UI redesign plan](UI_MOCKUP_REDESIGN_PLAN.md).

## Execution contract

- Execute UI-000 through UI-019 strictly in order, one task at a time, after
  MC-048H and before the final MC-048I observed sessions. Preserve MC-048I's
  prepared work and pending human gate; see the root plan's insertion rule.
- Every task depends on its predecessor unless explicitly stated otherwise.
  Do not reorder tasks, delegate parallel implementation, or start VID tasks.
- Representative owners below are relative to the repository. `desktop/` in
  this document means `desktopApp/src/main/kotlin/app/melotrail/desktop/`;
  `desktop tests` means `desktopApp/src/test/kotlin/app/melotrail/desktop/`.
  Resolve actual callers at execution time; filenames proposed with “new” are
  suggested owners, not claims that those files already exist.
- Per task: inspect -> characterize/test -> implement -> inspect screenshots
  where visible behavior changes -> validate -> update log -> review staged
  diff -> commit -> verify commit -> continue. Do not batch tasks together.
- Code tasks run focused forced-rerun tests, `make test`, `make build`, and
  `git diff --check`. Documentation-only exceptions are explicitly identified.
  Use existing verification wiring; add no Python tooling or runtime.
- Record whether a task changes workflow/export behavior. When it does, the
  applicable manual Logic Pro checks and evidence required by AGENTS,
  [DAW compatibility](../DAW_COMPATIBILITY.md), and
  [quality gates](../QUALITY_GATES.md) are part of that task's completion.
  Missing external/manual evidence means AWAITING_HUMAN, not an invented pass.
- Update owning visual/functional/architecture contracts with intentional
  behavior changes. Preserve safety and musical thresholds. Remove replaced
  target presentation code after its replacement is proven; do not remove
  unrelated legacy owners early.
- Each completed task gets exactly one commit with the subject below. A task
  awaiting manual evidence remains uncommitted. Never amend previous tasks,
  make empty completion commits, or fold unrelated existing work into one.
- Evidence belongs in [UI execution log](UI_MOCKUP_EXECUTION_LOG.md): baseline,
  changed/deleted owners, test commands/results, image paths/hashes, limitations,
  manual outcomes, subject and next task. The log and Git history jointly prove
  completion; use the next task entry to record the preceding commit hash.

## Ordered implementation

### UI-000 — Establish a safe redesign baseline

- **Depends on:** MC-048H DONE; root plan contains this insertion.
- **Inspect:** Git status/index/diffs/history, MC-048I preparation, Chords edits,
  both execution suites, nine original images, current generated captures.
- **Work:** record exact branch/HEAD and all pre-existing modified/untracked
  paths and overlapping hunks. Confirm which planning changes belong to this
  request; record the nine reference names, dimensions and SHA-256 values.
  Run baseline `make test` and `make build`. Capture or inventory the existing
  six-page screenshots with their provenance; label them unapproved baseline.
- **Safety:** do not commit MC-048I preparation or existing generator fixes by
  assumption. If later task edits cannot be isolated safely, record the exact
  conflict and ask the user to resolve ownership before that task. No stash,
  cleanup, checkout, reset, or broad staging.
- **Tests/evidence:** baseline commands, documentation links, `git diff --check`,
  reference manifest and preserved-change inventory. Existing failures are
  recorded and resolved by their owner before advancing, not disguised as UI work.
- **Done:** the baseline and safe commit boundaries are reproducible; the new
  planning suite is committed without unrelated changes.
- **Commit:** `ui: UI-000 establish mockup redesign baseline`.

### UI-001 — Freeze measurable visual targets

- **Inspect:** all nine PNGs at original size, current captures and visual spec,
  `WorkspaceTheme.kt`, shell/map geometry and reference-reader tests.
- **Work:** measure and record exact reference regions, colors, border/radius,
  typography estimates and layout landmarks. Extend the visual contract with
  six adapted page compositions at 1536 × 1024, 1280 × 900 and 720 × 900.
  Resolve the right-rail width per page rather than averaging contradictory
  legacy measurements. Fix the fidelity rubric, intentional MIDI substitutions,
  logical-pixel tolerances and screenshot comparator policy before coding.
- **Deliver:** versioned text/JSON measurements and reference manifest under
  `docs/plan/ui-evidence/` (new); reference-to-target region map including Review.
  Do not invent a seventh page or missing fifth reference image.
- **Tests:** documentation-only: link/inventory audit, unique mapping and
  complete reference list, `git diff --check`. No product tests needed unless
  their owners are changed.
- **Done:** every page has a concrete layout target and independent pass/fail
  criteria; unresolved artwork rights have a documented gradient fallback.
- **Commit:** `ui: UI-001 define measured visual targets`.

### UI-002 — Establish target typography, colors and assets

- **Inspect:** `WorkspaceTheme.kt`, `Melotrail.icns`, Compose icons dependency,
  all target/legacy token callers, `WorkspaceThemeTest.kt` and current font use.
- **Work:** establish one target palette/type/shape owner (new target theme file
  if needed), explicitly style all used typography slots and content colors,
  and introduce stable role/section colors. Separate dark primary button fill
  from bright accent/focus. Keep the existing app identity/icon; bundle a
  licensed font only if needed for repeatable fidelity. Use consistent vector
  icons with accessible names. Add the optional owned decorative rail asset
  only with recorded provenance; otherwise deliver the designed gradient.
- **Delete:** target references to misleading Piano/Pad/Strings role tokens;
  unused target palette duplication. Do not break still-compiled legacy callers
  or reintroduce a legacy runtime branch; their deletion remains MC-051.
- **Tests:** palette contrast matrix, typography metrics/fallback, role inventory,
  dark-content propagation, packaging of fonts/assets, theme showcase capture.
- **Done:** the target theme has no default black project-title text or inherited
  uncontrolled pill shapes, and fonts/icons render repeatably offline.
- **Commit:** `ui: UI-002 establish mockup visual foundations`.

### UI-003 — Build compact workstation primitives

- **Inspect:** repeated Card/Button/OutlinedButton/field/status helpers in all
  six pages, `WorkspacePageHeading`, keyboard/focus and busy/disabled semantics.
- **Work:** add reusable bordered panel, heading/action row, primary/secondary/
  icon button, nav item, metric tile, status badge, compact field/select row,
  table row, inline message, and disclosure primitives. Explicit 6–10-dp shapes,
  thin borders, coherent hover/focus/pressed/disabled states and 48-dp hit boxes.
  Implement only controls consumed by the following pages.
- **Delete:** replaced target helpers after switching a small proving surface;
  no second permanent component system or simulated controls.
- **Tests:** interactive semantics, Enter/Space, visible focus, disabled blocker,
  long text, control-state gallery, non-overlapping hit targets and radii.
- **Done:** subsequent pages can reproduce the reference density consistently
  without per-page Material defaults or shape overrides.
- **Commit:** `ui: UI-003 add compact workstation controls`.

### UI-004 — Rebuild the application shell

- **Inspect:** `MidiCoreWorkspaceShell.kt`, composition, navigation/page keys,
  shell tests, project/footer information and current generic context rail.
- **Work:** reference-like top band, project-summary sidebar, icon navigation,
  real local-app footer, adaptive main/inspector slots and dock slot. Use one
  route owner; keep six destinations. At wide widths offer actual page-owned
  contextual content, not duplicate instructions in a tall blank card. Preserve
  page scroll and navigation focus intentionally, keyed to project/destination;
  project close clears the correct UI-only state.
- **Delete:** giant numbered navigation pills, duplicate project/path/status
  blocks, unconditional generic inspector, black-on-dark title inheritance.
- **Tests:** exact route inventory, header readability, landmark bounds at three
  key sizes, navigation keyboard order, project switching, scroll preservation,
  single player owner and no hidden legacy destinations.
- **Done:** every page inhabits the reference-like shell, with genuine inspector
  space and an unobstructed dock, before individual pages are redesigned.
- **Commit:** `ui: UI-004 rebuild the desktop workspace shell`.

### UI-005 — Supply verified read-only MIDI visualization data

- **Inspect:** semantic MIDI model/reader, source and review audition preparation,
  draft/accepted assembly, candidate reports, workspace source/review state.
- **Work:** add a read-only application projection for protected source,
  selected candidate, draft and accepted role lanes, with authoritative timing,
  note/hit events and currentness/availability. Reuse verified loading and
  assembly without starting playback, saving projects or reading files in UI.
  Workspace owns loading/error/empty state and latest-request admission. Cache
  immutable projection data by actual source/candidate hashes and authority.
- **Delete:** any target placeholder data replaced by factual projection; never
  fabricate notes for missing/ungenerated scopes.
- **Tests:** each supported scope, stale/tampered/missing artifacts, cancellation,
  project switch, no device initialization, unchanged source/project/export
  bytes, and current draft versus accepted identity.
- **Done:** actual notes can be displayed while stopped and without accepting a
  draft; unsafe evidence is explicitly unavailable, not drawn as current.
- **Commit:** `ui: UI-005 expose verified MIDI visual evidence`.

### UI-006 — Observe the real playhead and format musical time

- **Inspect:** `MidiAuditionPort`, controller/output `positionTick()`, workspace
  collection, session replacement/loop logic and state-history retention.
- **Work:** one lifecycle-owned observation path samples the active MIDI output
  at a bounded UI rate (target up to 30 Hz), publishes session-bound position,
  and stops on pause/stop/close/loss. Keep high-frequency telemetry out of an
  unbounded history or project persistence. Add shared safe tick-to-bar/beat
  and elapsed/duration formatting using confirmed or explicitly labeled source
  tempo/meter. Never animate elapsed time independently of the MIDI output.
- **Tests:** fake-clock/output movement, loop wrap, pause/resume/seek, stale
  callbacks, supersession, completed end, repeated lifecycle, bounded history,
  no leak, and no writes. Include 3/4 and 6/8 time displays.
- **Done:** dock/map/lanes receive one truthful playhead, without musical event,
  generation, export or clock-ownership changes.
- **Commit:** `ui: UI-006 observe the live MIDI playhead`.

### UI-007 — Implement aligned section and MIDI-role timelines

- **Inspect:** `MidiCoreSongMap.kt`, UI-005 projection, UI-006 position,
  authoritative occurrence/chord boundaries and map tests.
- **Work:** create shared ruler/scale/viewport; short rectangular colored section
  blocks; aligned Melody/Chords/Bass/Drums lanes with real note rectangles and
  percussion hits; playhead/loop overlay; accessible section selection; Fit and
  bounded zoom/scroll only when implemented. Preserve exact proportional widths:
  handle short labels via tooltip/inspector, not minimum-width distortion of
  individual timeline cells. Separate a compact overview from detailed scrolling.
- **Delete:** 132-dp-high pill map buttons and duplicated incompatible timeline
  arithmetic. Retain stable occurrence tags and text equivalents for statuses.
- **Tests:** repeated names, unequal/one-bar sections, sub-bar chord windows,
  `[start,end)` boundary membership, final end, selected vs playing vs loop,
  missing/stale lanes, zoom transform, keyboard and dense-song clipping.
- **Done:** map and role cells line up with one authoritative axis; source notes
  cannot be dragged, quantized, edited, or regenerated through this surface.
- **Commit:** `ui: UI-007 render aligned MIDI song timelines`.

### UI-008 — Compact the persistent player

- **Inspect:** shell dock and options, all page audition actions, device/seek/
  mute/solo tests, typed target descriptions and position projection.
- **Work:** reference-like 72–88-dp wide transport with compact icon controls,
  target/section label, bar/beat and time, seek display, loop, available target
  chooser and Options. Compact size may use two rows without covering content.
  Keep friendly draft/style names in the dock, IDs in details. Output, boundary
  seek, mute/solo and recovery stay in one bounded expandable panel.
- **Delete:** full-width pill target/play rows and raw tick/UUID primary labels;
  no page-local transport or always-visible mixer-like role controls.
- **Tests:** every target and state, no output, options open, long names, keyboard,
  scrolled pages, short windows, session continuity and exactly one owner.
- **Done:** transport remains legible/visible at every supported width and
  releases substantial space for the musical workspace.
- **Commit:** `ui: UI-008 compact the persistent MIDI player`.

### UI-009 — Reconstruct the Project dashboard

- **Inspect:** `MidiCoreProjectPage.kt`, lifecycle/readiness/last-project
  preference, UI-005/007 projections and `01-dashboard-overview.png`.
- **Work:** current-project hero, factual metric tiles, section strip, read-only
  role overview, real readiness/quick actions in the right inspector, honest
  last-opened project row. Keep Create/Open focused when no project exists;
  place path/revision/reload/close in details without hiding recovery.
- **Delete:** three oversized repeated status/next-step cards and placeholder
  recent items or invented thumbnails, if any.
- **Tests:** create/open/reopen/legacy rejection, missing/partial/complete states,
  metrics from known tempo/PPQ, source suggestions versus confirmed key/BPM,
  next-action routing, keyboard and three-size captures.
- **Done:** dashboard visibly matches the reference's metric/map/lane/inspector
  hierarchy and never mistakes candidate inventory for accepted readiness.
- **Commit:** `ui: UI-009 reconstruct the project dashboard`.

### UI-010 — Reconstruct MIDI import and inspection

- **Inspect:** `MidiCoreMidiPage.kt`, file-dialog boundary, import/report
  hydration, UI-005 projection and `02-import.png`.
- **Work:** single-source import well, native chooser, immutable imported-source
  summary, note-lane preview, dense track table, findings inspector and source
  target selection. Implement one-file drop through the same safe import intent
  if advertising drag/drop; reject unsupported/multiple files and preserve the
  native chooser. Reopen must restore truthful findings, not lose them until
  another import; use the existing report through a verified read boundary.
- **Delete:** redundant source metadata cards and any instruction advertising an
  unavailable action. No audio, clean/process, delete-source, or replacement flow.
- **Tests:** SMF0/1, malformed/multi-track/channel cases, advisory/blocked state,
  import failure/cancel, source immutability, reopened findings, stopped preview,
  audition handoff and optional native-drop smoke.
- **Done:** file import and inspection resemble the reference without expanding
  the one-source contract or weakening automatic protection.
- **Commit:** `ui: UI-010 redesign MIDI import and inspection`.

### UI-011 — Reconstruct Structure & Harmony

- **Inspect:** `MidiCoreStructureHarmonyPage.kt`, drafting helpers/application
  services, invalidation preview and `03-structure.png`.
- **Work:** top section strip, compact global settings, ordered section table
  with name/bars/derived range/actions, and selected progression/chord-window
  inspector. Split rendering from drafting logic where useful. Keep totals,
  validation and save accessible; retain explicit authority confirmation and
  unchanged exact chord windows. Use move buttons as keyboard alternatives;
  do not add drag handles without implementing and testing drag behavior.
- **Delete:** tall nested three-step card stack and duplicated per-row controls;
  no second authority editor on other pages.
- **Tests:** add/duplicate/reorder/remove, mismatch, repeated labels, chromatic
  progression, invalid chord, unchanged sub-bar windows, unsaved navigation,
  invalidation-before-save, stale conflict, restart and three-size captures.
- **Done:** the section table and timeline dominate; authority remains exact
  and no stable ID or raw tick entry is required from the musician.
- **Commit:** `ui: UI-011 redesign structure and harmony editing`.

### UI-012 — Reconstruct Arrange as a musical workstation

- **Inspect:** `MidiCoreArrangePage.kt`, style catalog/preview, full-draft and
  scoped-regeneration intents, `04-arrange.png` and gallery details in `07`.
- **Work:** timeline/lane main surface, five compact style cards, top-right
  Create full draft, real selected-section inspector, progress/cancel/retry,
  three role statuses, Regenerate section and disclosed role repair. Main and
  inspector are side-by-side at wide size, not a renamed vertical stack. Keep
  the first-draft CTA in the initial viewport; selected styles remain visible.
- **Delete:** full-width style pills, repeated prose, offscreen primary actions,
  technical cache/seed messages from the main journey. Details retain evidence.
- **Tests:** one-action style preview; at most three primary actions to full-draft
  playback; two to section regeneration, three to role regeneration; rapid
  preview/latest wins; one-bar limitation; progress/retry/cancel; locks/stale
  evidence; advanced controls; three-size initial/scrolled captures.
- **Done:** the reference's timeline and inspector composition is recognizable
  while the deterministic whole-song-first workflow remains intact.
- **Commit:** `ui: UI-012 reconstruct the Arrange workstation`.

### UI-013 — Reconstruct whole-draft Review

- **Inspect:** `MidiCoreReviewPage.kt`, draft acceptance/undo, candidate lifecycle,
  diff and shared map; use `04-arrange.png` as the declared reference.
- **Work:** shared musical surface and inspector; explicit draft/accepted target,
  one primary Use this draft, secondary dock-target audition, current readiness
  and latest guarded undo. Candidate cards/diff/findings/lock/reject/restore are
  contextual exceptions. Repair returns to the same Arrange section/style.
- **Delete:** repeated large playback/use/status cards, unconditional action
  stacks, accidental duplicate transport and generic undo/redo affordances.
- **Tests:** hear unaccepted draft, atomic use/rollback, guarded undo/reaccept,
  blocked/locked/stale/tampered scopes, compare/reject/restore, targeted repair
  round trip, accepted-only Export, context/scroll/playhead continuity.
- **Done:** whole-draft listening/approval is the default; optional comparison
  remains available and never changes authoritative melody or candidate bytes.
- **Commit:** `ui: UI-013 reconstruct whole-draft review`.

### UI-014 — Reconstruct the MIDI Export page

- **Inspect:** `MidiCoreExportPage.kt`, exporter/snapshot/manifest/reveal actions,
  DAW guidance, `09-export.png` and export tests.
- **Work:** complete-song/role/manifest summary tiles for one package, compact
  readiness/file table, immutable destination row, prominent action/result,
  right authority/Logic checklist and disclosed hash details. Preserve actual
  collision/failure behavior and source privacy. No fake format, destination,
  role omission, overwrite or metadata editor.
- **Delete:** repeated raw digest/path/status walls from the primary surface;
  retain audit evidence in details, not hidden from access.
- **Tests:** partial/ready/stale acceptance, progress/cancel/retry, collision,
  failure recovery, safe reveal, immutable snapshots, exact five MIDI files plus
  manifest, guidance, reopen and three-size captures; semantic export re-import.
- **Done:** export looks like the reference's polished handoff screen and still
  publishes exactly the existing accepted-only MIDI package.
- **Commit:** `ui: UI-014 reconstruct the MIDI export screen`.

### UI-015 — Unify contextual panels and application states

- **Inspect:** player options, project details, authority confirmation dialog,
  blockers/notifications, all six pages and `10-settings.png` control styling.
- **Work:** consistent inspector/disclosure/dialog treatment for existing
  functions; one inline error/retry design; no-project, loading, cancelled,
  validation-rejected, stale, disk-error and device-loss states. Required
  confirmation stays explicit. Help/About surfaces contain real facts or
  checked-in guidance only; no inactive decorative gear/account controls.
- **Delete:** remaining inconsistent target dialogs/cards and duplicate status
  messages. Do not add a standalone Settings destination.
- **Tests:** focus enters/returns correctly, Escape, keyboard-only recovery,
  retained selections, blocking explanation near action, exactly one error and
  transport owner, long text and expanded compact panels.
- **Done:** failure/recovery states receive the same polish as the happy path
  and no mockup control advertises a nonexistent backend operation.
- **Commit:** `ui: UI-015 unify contextual panels and recovery states`.

### UI-016 — Harden resizing, accessibility and UI performance

- **Inspect:** all pages/components at reference, wide, compact and short sizes;
  text scaling, native display density, playhead and preview measurements.
- **Work:** correct overflow/truncation/focus, responsive tables/inspector/dock,
  keyboard timeline navigation, reduced motion, and page context restoration.
  Cache pure geometry by data/viewport, bound visible note drawing, and prevent
  the playhead from reloading artifacts/recomposing unrelated content. Keep
  all musical state outside presentation-only resize/motion decisions.
- **Tests:** six-page size/state matrix, 48-dp hit bounds, contrast, keyboard
  traversal, long/Unicode labels, large fixtures, no UI-thread file I/O,
  one observation subscription and no leaks; retain cold/warm preview budgets.
- **Evidence:** record reference hardware, actual frame/sample timings and
  preview p95. Do not describe code-preparation timing as acoustic onset.
- **Done:** target sizes work without hidden primary controls or fake progress,
  and existing responsiveness/immutability gates do not regress.
- **Commit:** `ui: UI-016 harden responsive and accessible layouts`.

### UI-017 — Enforce deterministic visual regression

- **Inspect:** visual/workflow tests, UI-001 metrics, all candidate screenshots,
  Gradle image-test wiring and legacy overlay helpers (without reviving them).
- **Work:** deterministic fixed-ID/clock/data/fonts/scale/scroll/position fixtures;
  versioned MIDI-only expected images under a new target test-resource owner;
  actual/expected/diff output; bounded comparison tolerances plus independent
  geometry/color/radius assertions. Create initial and scrolled captures and
  reference-side comparison artifacts. Keep old mockups design-only and out of
  runtime/test golden lookup. All six pages at three key sizes, plus relevant
  empty/blocked/busy/stale/error/expanded states.
- **Delete:** assertions treating PNG write success/dimensions as sufficient
  visual acceptance. Keep real-service E2E coverage in addition to fixed-state
  graphics fixtures. Remove nondeterministic paths/UUIDs from visible fixtures.
- **Tests:** identical repeated captures pass; deliberately shifted geometry,
  wrong colors and pill radii fail; baseline updates cannot happen implicitly;
  full regression and create/import/authority/preview/draft/use/undo/export E2E.
- **Done:** screenshot regressions actually fail, artifacts are inspectable,
  and the candidate visual baseline is ready for human approval in UI-019.
- **Commit:** `ui: UI-017 enforce visual fidelity regression gates`.

### UI-018 — Finalize the future Create Video feature contract

- **Inspect:** [future video proposal](FUTURE_VIDEO_CREATOR.md), `08-video-preview.png`,
  current MIDI export/authority contracts and legacy video deletion owners.
- **Work:** refine the future user journey, conceptual request/result contract,
  scene timing, soundtrack provenance, preview/export expectations, optional
  companion boundary, unsupported-state behavior and VID-000–VID-006 backlog.
  List decisions requiring approval before implementation. Preserve the mockup
  composition for that future feature; make the split from current MIDI export
  explicit. This is documentation, not a production function stub.
- **Delete:** conflicting planning statements suggesting video already exists;
  do not delete implementation or retain an old renderer “for later”.
- **Tests:** documentation-only: link audit, coherent scope/dependency/commit
  mapping, no task claiming current video support, `git diff --check`.
- **Done:** video has a buildable future specification and ordered backlog,
  while no video tab, service, dependency, DTO or fake button is added now.
- **Commit:** `ui: UI-018 specify the future video creator`.

### UI-019 — Obtain visual acceptance and hand back to MIDI Core

- **Inspect:** all UI task commits/evidence, reference-to-target comparison,
  final screenshots, pre-existing dirty-change inventory and manual gates.
- **Work:** run the complete automated suite/build and focused real-service
  workflow; show the six final reference-sized screens and compact samples
  beside their mapped originals; complete the five-dimension fidelity rubric
  with the user. Fix observed visual regressions with tests inside this task,
  rerun affected gates, and record explicitly accepted functional substitutions.
  Verify no audio/AI/mixer/library/video route or runtime was introduced.
- **Manual gate:** user confirms the graphics are sufficiently close; no
  dimension below 4/5, no clipping/illegible/overlapping primary content.
  Require any outstanding applicable Logic workflow checks. Mark
  AWAITING_HUMAN until evidence exists; do not create the task commit early.
- **Evidence:** final source and screenshot hashes, comparator/semantic/focus/
  timing results, reference comparison, user comments/fix/retest, accepted
  baseline revision, current limitations and exact next task.
- **Done:** UI-000–UI-019 are validated and committed; updated MC-048I fixture/
  session instructions identify this redesigned build. MC-048I still requires
  its genuine observed sessions, MC-049 still requires unseen musical holdouts,
  and no cleanup/video task has been executed by this UI sequence.
- **Commit:** `ui: UI-019 record mockup redesign acceptance`.

## Functional traceability

| Contract | UI tasks |
| --- | --- |
| F-PROJ-001–004 | UI-004, UI-009, UI-015–019 |
| F-MIDI-001–005 | UI-005, UI-007, UI-010, UI-017–019 |
| F-AUTH-001–005 | UI-007, UI-011, UI-016–019 |
| F-PLAY-001–005 | UI-005–008, UI-012–013, UI-015–019 |
| F-ARR-001–007 | UI-007, UI-012, UI-016–019 |
| F-REV-001–006 | UI-005, UI-007, UI-013, UI-016–019 |
| F-EXP-001–007 | UI-009, UI-014, UI-017–019 |
| F-UI-001–007 | UI-001–019, with UI-018 documentation-only |
| F-SYS-001–004 | UI-000, UI-004–006, UI-014–019 |
| Future video | UI-018 specifies it; only separately approved VID tasks implement it |

The only automatic continuation after a completed UI task is the next UI task.
After UI-019, stop with a handoff to the current MC-048I manual gate.
