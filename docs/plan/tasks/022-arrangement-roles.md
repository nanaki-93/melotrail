# Task 022 — Profile-independent arrangement roles

## Goal

Separate musical arrangement roles from concrete instruments and replace free
style strings/fixed lo-fi assumptions with resolved profile/mood context.

## Why

Fixed PIANO/BASS/DRUMS/PAD/STRINGS naming and lo-fi prompts make future genres
and alternate instrumentation expensive to add.

## Dependencies

Tasks 004, 008, 018, and 021.

## Existing Code

- `GlobalSongPlanner.kt`, `SectionVariation.kt`, `DetailedArrangement.kt`
- `ArrangementApplicationService.kt`, `InstrumentRegistry.kt`
- bass/drum/pad/string generators and sound-library/license validation
- desktop arrangement draft/instrument selection

## Changes

- Define roles Melody, Harmony, Bass, Drums, CounterMelody, Texture, Ambience
  with stable IDs/capabilities.
- Model role-to-instrument assignments separately; instrument entries retain
  playable range, renderer data, asset hash/license, and supported roles.
- Map current logical instruments/stems through compatibility aliases so existing
  arrangement/mix projects remain readable.
- Replace new `style: String` planner/draft inputs with profile/mood refs/resolved
  context. Move lo-fi density, drum-role, prompt, and instrument suggestions into
  the Lo-fi profile.
- Feed structured harmony/meter/key and stable occurrences to planners/generators.
- Update Arrange UI to select roles and instruments distinctly and mark profile
  suggestions versus user choices.

## Files

Arrangement domain/services/planners/generators/registry, profile definitions,
desktop Arrange UI/state, migration adapters, docs/tests.

## API / Contracts

Arrangement request/result uses context hash, occurrence IDs, role definitions,
instrument assignment IDs, user constraints, and plan schema version.

## UI

Role enablement and instrument assignment controls; retain licensing/readiness,
energy/density/variation review and responsive behavior.

## Backend

Application service remains authority. Deterministic/AI planners are injected.

## Python Worker

No change.

## Tests

Legacy mapping/mix aliases, role/instrument validation, profile suggestions,
user override persistence, structured harmony/meter inputs, no lo-fi core branch.

## Acceptance

- A fixture profile can recommend different roles/instruments without schema/UI
  redesign.
- Existing logical stems/mix settings migrate or show explicit review.
- Arrangement honors musician-selected roles/instruments.

## Out of Scope

New instrument assets/profiles, plugin hosting, generator quality rewrite.

