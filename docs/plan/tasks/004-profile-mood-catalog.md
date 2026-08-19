# Task 004 — Composition profile and mood catalog

## Goal

Create typed, versioned `CompositionProfile` and `MoodDefinition` catalogs with
one built-in Lo-fi profile and initial moods.

## Why

Lo-fi defaults, prompts, swing, instruments, enhancement tolerance, and DSP must
not remain scattered conditionals. Mood must affect parameters, not only prose.

## Dependencies

Task 003.

## Existing Code

- free `style` fields in `GlobalSongPlanner`, `DetailedArrangement`, desktop draft
- `MidiLoFiFeel.kt`, `LOFIPresets`, fixed build texture option
- `InstrumentRegistry.kt` and logical instrument definitions

## Changes

- Define stable profile/mood refs and resolver/catalog interfaces.
- Bundle versioned `lofi` data: UI defaults/constraints, supported meters/moods,
  role/instrument suggestions, groove/humanization bounds, correction/enhancement
  tolerance, cohesion vocabulary, and optional style-processing policy.
- Add Warm, Nostalgic, Melancholic, Dreamy, Relaxed, and Dark typed mood modifier
  definitions; missing values are neutral.
- Implement deterministic profile-plus-mood resolution and clamping; expose a
  normalized resolved snapshot/hash.
- Reject duplicate IDs/versions, invalid ranges, unsupported references, and
  unavailable capability claims. Do not include future genres.

## Files

Create profile domain/catalog/resource files and tests; wire the catalog in
`DesktopMain.kt`/application composition root without changing pages yet.

## API / Contracts

Queries list profile/mood summaries and resolve a typed parameter snapshot.
Persist refs, while every run snapshots resolved hash/version.

## UI

Provide labels/descriptions/defaults/constraints for Task 006. Do not render UI.

## Backend

Catalog is application-owned and injectable/fakeable. Avoid environment-specific
paths in profile resources.

## Python Worker

No direct dependency. Worker receives resolved numeric parameters only when a
future command needs them.

## Tests

Catalog validation, resolution/clamping, deterministic hash, unknown version,
neutral modifiers, one-profile inventory, and no core `style == lofi` branch.

## Acceptance

- Lo-fi is selected by ID/version and resolves typed policies.
- Mood changes at least a minimal set of testable parameters.
- Adding a fixture profile requires catalog data/strategies, not schema changes.

## Out of Scope

Profile authoring/downloads, additional real profiles, or processor rewrites.

