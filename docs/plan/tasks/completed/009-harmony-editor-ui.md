# Task 009 — Harmony editor UI

## Goal

Add a responsive Harmony destination where musicians add, edit, remove, and
reorder structured chords for Verse, Chorus, and Bridge.

## Why

User-selected harmony is a central authorship decision and the required context
for later melody processing/arrangement.

## Dependencies

Task 008.

## Existing Code

- workspace navigation/router/view model and reusable cards/dialogs
- structure reorder semantics/accessibility patterns
- Setup destination from Task 006

## Changes

- Add Harmony navigation/readiness and section tabs/cards.
- Render chord chips/rows using formatted symbols; edit tonic and quality in
  structured controls.
- Implement add/remove/move/edit with stable event IDs, keyboard alternatives,
  dirty/saving/error states, and revision-conflict refresh.
- Show section completeness and local validation. Confirm exact downstream
  invalidation for edits to an already processed project.
- Leave future duration/inversion/slash/extension fields out of simple MVP UI.
- Split focused screen/components/state mapping instead of enlarging one router
  function indefinitely.

## Files

Desktop navigation/state/intents/router, new Harmony composables, Compose tests,
and UI workflow documentation.

## API / Contracts

Consume Task 008 query/commands; list index is presentation only.

## UI

Wide/medium/narrow layouts, visible focus, screen-reader chord symbol plus edit
details, and reorder controls that do not rely on drag alone.

## Backend

No change beyond service injection.

## Python Worker

No change.

## Tests

Example progressions, all chord qualities, add/edit/remove/reorder, keyboard/
semantics, invalidation dialog, revision error, and responsive layout.

## Acceptance

- The example Verse/Chorus/Bridge progressions can be entered without text
  parsing.
- Saved order/IDs survive reload.
- Workflow readiness points clearly between Setup, Harmony, and Melody Parts.

## Out of Scope

Playback, AI chord generation, substitutions, or advanced voicing controls.

