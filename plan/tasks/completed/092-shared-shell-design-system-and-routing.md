# Task 092 — Shared Shell, Design System, and Routing

## Goal

Build the reusable dark-purple application shell and component system required
by all nine references in `../../pictures/UI`, including distinct Library and
Settings destinations and responsive one-page routing.

## Dependencies

- Task 091 accepted.
- `../../PLAN_UI.md` is the governing UI reconstruction plan.

## Requirements

- Measure the 1536 × 1024 references and add reusable tokens for the top bar,
  project rail, optional context rail, content insets, gaps, cards, tables,
  inputs, controls, typography, borders, purple selected/accent states, status
  colors, and per-track colors. Do not scatter page-specific magic numbers.
- Implement one shared wide shell with:
  - Melotrail wordmark/brand treatment;
  - top destination navigation;
  - project summary and workflow rail;
  - one routed page slot;
  - optional page-owned context rail;
  - help/settings/local-mode area;
  - one global feedback surface and one modal layer.
- Add typed `LIBRARY` and `SETTINGS` destinations. Preserve compatibility with
  existing navigation state without changing or migrating project artifacts.
  Interim page bodies are allowed until Tasks 098 and 100.
- Ensure exactly one navigation surface is composed for the active breakpoint.
  Wide may use the reference top bar plus a project/workflow rail only when
  their roles are distinct; do not render duplicate controls for the same
  destination in conflicting states.
- Define responsive behavior from content fit:
  - wide: top bar, project rail, page, and optional context rail;
  - medium: compact project/navigation rail and collapsible context rail;
  - narrow: one navigation chooser and stacked page regions/sheets.
- Keep exactly one page root at all breakpoints. Navigation must preserve
  project, selected part/occurrence, drafts, readiness, global feedback, and the
  shared playback session.
- Introduce shared components for navigation, page/section headers, cards,
  tabs, segmented controls, status/format badges, fields, icon actions, dense
  tables, preview artwork, transport, and empty/loading/error states.
- Use one Compose-compatible vector icon family or bundled local SVG resources.
  Replace text glyph actions only when semantics and minimum hit targets are
  preserved.
- Retain Melotrail branding unless the user explicitly approves a rename.
- Add a deterministic local artwork slot. Do not extract a production asset
  from the flattened reference screenshots; render a truthful local
  placeholder until an approved source asset exists.
- Do not add backend behavior for decorative reference controls.

## Verification

- Compose tests iterate every destination and assert one matching page root,
  zero other page roots, one global feedback root, and the correct navigation
  surface for wide, medium, and narrow widths.
- Navigation tests prove state and playback preservation and prove explicit
  project create/open is the only normal operation that returns to Overview.
- Semantics tests cover unique accessible names, selected states, disabled
  reasons, minimum hit targets, keyboard traversal, Escape behavior, and
  collapsed navigation.
- Theme/component tests cover normal, hover/focus where testable, selected,
  disabled, success, warning, and error states.
- Capture deterministic full-shell fixtures at 1536 × 1024 plus medium and
  narrow widths. Record measured shell/token values and intentional differences
  in `UI_REFERENCE_TOKENS.md` or a replacement document.
- Run `./gradlew :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Every page can be implemented without recreating shell, rail, field, table,
  or action styling locally.
- Library and Settings are independently routable.
- The active window contains one page, one coherent navigation model, one
  feedback surface, and no page-level horizontal scrolling.

## Out of scope

Final page bodies, production scenic artwork without an approved asset, product
renaming, new settings behavior, remote assets, accounts, or backend features
suggested only by the mockups.
