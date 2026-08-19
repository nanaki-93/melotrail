# Task 007 — Structured chord and progression domain

## Goal

Add structured, persistable section chord progressions with stable event identity
and all required MVP chord qualities.

## Why

Harmony must be editable, transposable, validated, and passed to processors; a
display string or inferred triad list is insufficient.

## Dependencies

Tasks 002–003.

## Existing Code

- `MidiAnalysis.kt` inferred `MidiChord`/key strings
- chord summaries in `MidiAiFix.kt`, `TransitionCohesion.kt`, planners/generators
- v4 harmony placeholder from Task 002

## Changes

- Implement `ChordQuality` values Major, Minor, Dominant7, Major7, Minor7,
  Major9, Minor9, Add9, Sus2, and Sus4 with interval definitions/formatting.
- Implement stable `ChordEvent` ID, root, quality, order, and reserved optional
  duration/bass/inversion/extension fields.
- Implement `ChordProgression` bound to extensible `SectionTypeId` and ordered
  event operations: add, edit, remove, move.
- Validate uniqueness/order/known executable qualities and project-key context
  without forbidding intentional chromatic chords.
- Add deterministic JSON and symbol formatting/parsing confined to adapters.

## Files

Create harmony domain/DTO/formatter files; modify v4 persistence/validator and
unit fixtures. Do not replace analysis evidence yet.

## API / Contracts

Structured DTO fields only. One-measure duration is an MVP default; optional
future fields round-trip but unsupported execution blocks clearly.

## UI

Provide root/quality option lists and formatted chord symbols for Task 009.

## Backend

No controller. Domain operations remain pure and application-independent.

## Python Worker

No change.

## Tests

All qualities/roots/enharmonic labels, JSON round-trip, stable reorder IDs,
chromatic chords, invalid IDs/duplicates, future optional-field round-trip.

## Acceptance

- Example Verse/Chorus/Bridge progressions round-trip structurally and format as
  expected.
- No new core code treats the progression as a pipe-delimited string.

## Out of Scope

Audition, substitutions, arbitrary extensions, harmonic AI, or duration UI.

