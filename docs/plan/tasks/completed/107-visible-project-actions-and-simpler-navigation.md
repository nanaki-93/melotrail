# Task 107 — Visible Project Actions and Simpler Navigation

## Goal

Make starting a project unmistakable and reduce top-level navigation/actions to
the choices needed for the current workflow.

## Dependencies

- Task 106 accepted.

## Requirements

- Replace the icon-only create control with a visible, labelled **New Project**
  action in the desktop header. Keep the existing create dialog, validation,
  and keyboard/focus behaviour intact.
- Keep opening/choosing a project as a clearly distinct action. When no project
  is open, make the two project actions and their next explanatory message the
  dominant available controls.
- Remove or relocate non-actionable header controls (for example disabled Save
  and fixed-theme controls) and redundant shortcuts. Do not remove an action
  that is the only accessible route to a supported capability.
- Reduce visible workspace navigation to the essential guided stages. Less-used
  Library, Video Preview, Settings, diagnostics, and detail surfaces may remain
  reachable through a compact overflow/context route with clear labels; never
  strand keyboard users.
- Preserve stable test tags where practical; deliberately renamed tags require
  a migration note and updated tests.

## Tests

- View-model and Compose tests for no-project, project-open, mutation-in-flight,
  keyboard focus order, and accessible names.
- Wide, medium, and narrow visual checks confirming the New Project action is
  visible without relying on a tooltip.

## Acceptance criteria

- A first-time user can find and activate **New Project** immediately, while
  the header contains no disabled or duplicate controls presented as choices.

## Out of scope

- Changing create-project data model defaults or adding project templates.
