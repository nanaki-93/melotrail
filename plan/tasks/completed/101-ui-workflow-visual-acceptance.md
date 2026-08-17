# Task 101 — UI Workflow and Visual Acceptance

## Goal

Complete the cross-page consistency, full workflow, accessibility,
responsiveness, and deterministic visual-acceptance pass for Tasks 091–100 and
all references under `../../pictures/UI`.

## Dependencies

- Tasks 091–100 accepted.

## Requirements

- Audit the complete active UI for one project source of truth, one active page,
  one coherent navigation model, one global operation-feedback surface, one
  modal layer, and one shared playback session.
- Complete shared visual consistency for shell geometry, purple theme,
  typography, spacing, cards, tables, iconography, tabs, fields, controls,
  selected/hover/focus/disabled/error states, rails, transports, and status
  language.
- Audit all enabled controls. Each must dispatch a typed intent, open a visible
  actionable surface, or route to a real destination. Remove dead, decorative,
  duplicate, or unreachable actions.
- Audit every disabled control for a useful accessible reason. Prefer omitting
  unsupported capability claims over retaining decorative disabled UI.
- Run the complete supported workflow through composed UI with offline fakes
  and a manual local smoke where dependencies are available:
  create/open -> Import -> prepare/repair -> analyze -> add/reorder Structure ->
  generate/review/approve Arrange -> Mix/Build -> Preview -> WAV/optional MP3
  Export.
- Verify failures and cancellations at each boundary keep canonical state safe,
  preserve source files, show one recovery path, and never report false
  completion.
- Verify wide, medium, and narrow layouts at 100%, 125%, and 150% scale with
  empty, loading, populated, stale, failed, missing-dependency, approval,
  long-content, and many-item fixtures.
- Eliminate control overlap, clipping, inaccessible content, duplicate content,
  page-level horizontal scrolling, and unstable item identities. Bounded
  timelines, mixers, tabs, and chip rows may scroll internally.
- Complete keyboard/focus coverage for navigation, dialogs/sheets, import,
  details, structure editing, arrangement, sliders/toggles, playback, library,
  export, settings, confirmations, Escape, and focus return.
- Capture the complete window for every destination at 1536 × 1024; do not use
  cropped page roots as the only visual evidence.
- Compare each fixture with its matching `../pictures/UI/*.png` reference.
  Check matching major shell/rail/page/card/table/timeline/form/footer edges
  within 4 px, use a documented RGB tolerance for surfaces/borders, and review
  typography/icons/artwork/states by eye.
- Record every intentional difference caused by unsupported capabilities,
  truthful missing data, Melotrail branding, or unavailable approved artwork.
- Update current README/troubleshooting/golden-workflow documentation only where
  behavior, navigation, settings, or verification commands changed.
- Remove dead first-generation routing/layout/components/state only after
  active-page functional parity is covered. Do not remove still-used
  application behavior or unrelated user code.

## Verification

- Add a parameterized action audit or equivalent focused tests covering every
  page's enabled controls and visible surfaces.
- Run deterministic end-to-end UI tests for direct MIDI and eligible
  solo-piano audio paths, including the original `⋮` and Analyze regression.
- Run all route/state, application boundary, keyboard, semantics, and responsive
  tests introduced by Tasks 091–100.
- Generate and inspect full-window captures for Overview, Import, Structure,
  Arrange, Mix & Master, Library, Video Preview, Export, and Settings.
- Review final diffs for unrelated changes, source mutation, unsafe paths,
  false success, duplicate player/state owners, direct filesystem/worker calls
  from composables, CWD assumptions, and stale documentation.
- Run:

  ```bash
  ./gradlew :desktopApp:test
  ./gradlew test :desktopApp:test :desktopApp:build
  ```

- Run worker tests only if Tasks 091–100 changed worker code or its HTTP
  contract. Run packaging only if package resources, icons, fonts, or launch
  behavior changed, and report unverified optional dependencies explicitly.

## Acceptance criteria

- All nine reference destinations have deliberate, cohesive, responsive
  implementations and documented full-window visual review.
- Import overflow/details, repair, Analyze, audio preparation, continuation,
  and recovery are visibly functional end to end.
- Every enabled control works, every displayed value is truthful, and every
  unsupported reference capability is absent or explicitly unavailable.
- Supported workflow completion is based on validated canonical artifacts, not
  page state or stale files.
- Focused and full builds/tests pass, with pre-existing failures and optional
  unverified dependencies reported separately.

## Out of scope

Implementing capabilities deliberately excluded by Tasks 091–100, unrelated
backend refactors, deferred worker Tasks 059–062, cloud services, video export,
new DSP/codecs, or production claims for dependencies/platforms not verified.
