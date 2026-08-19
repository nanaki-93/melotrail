# Task 006 — Project Setup UI

## Goal

Add the first musician-facing workflow destination for structured project setup
and migration-required legacy projects.

## Why

The UI/domain contract must lead the implementation and make creative context
explicit before advanced processing is introduced.

## Dependencies

Task 005.

## Existing Code

- `desktop/.../WorkspaceViewModel.kt`, `WorkspacePageRouter.kt`, `WorkspaceApp.kt`
- `WorkspaceSection`, navigation shell, dialogs/cards/responsive tests
- `WorkflowReadModel` and project snapshot mapping

## Changes

- Add/rename primary Setup destination without breaking project picker, transport,
  secondary pages, or responsive shell.
- Render name, tonic, mode, BPM, time signature, Lo-fi profile, and mood controls
  from application option models.
- Show inline validation/profile recommendations and distinguish recommendation
  from core restriction.
- Preview affected downstream stages before confirming an existing-project
  creative setting change.
- For legacy projects, explain required settings and explicit v4 save/migration;
  never migrate merely by navigating.
- Split Setup composables/state mapping out of the already-large router/view model
  while keeping one workspace state owner.

## Files

Modify desktop workspace state/intents/router/navigation and Compose tests; add
focused Setup screen/components. Update UI docs/screenshots when available.

## API / Contracts

Consume Task 005 queries/commands only. No direct profile catalog or project-file
access from composables.

## UI

Keyboard accessible fields, clear saved/dirty/error state, profile/mood help, and
wide/medium/narrow layouts. Persist only on explicit action.

## Backend

No new backend behavior beyond service wiring.

## Python Worker

No change.

## Tests

Initial defaults, edit/save/error, invalidation confirmation, legacy setup state,
keyboard semantics, and all responsive breakpoints using fake services.

## Acceptance

- A musician can create/save valid Lo-fi project context without free key strings.
- Non-4/4 remains representable even if Lo-fi simple UI constrains choices.
- Navigation readiness points to Setup when context is missing.

## Out of Scope

Harmony, import processing, new profiles, or AI behavior.

