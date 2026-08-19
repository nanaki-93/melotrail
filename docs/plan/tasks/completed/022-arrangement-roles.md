# Task 022 — Profile-independent arrangement roles and sound intent

## Goal

Separate musical arrangement roles and desired sound character from concrete
instrument identities, engine files, and free style strings.

## Why

Current PIANO/BASS/DRUMS/PAD/STRINGS names simultaneously act as roles,
instruments, registry keys, generators, and stems. A reusable arranger must first
describe what a layer does and how it should sound.

## Dependencies

Tasks 004, 008, 018, and 021.

## Existing Code

- `GlobalSongPlanner.kt`, `SectionVariation.kt`, `DetailedArrangement.kt`
- `ArrangementApplicationService.kt`, `Arrangement.kt`
- bass/drum/pad/string generators
- desktop arrangement draft/instrument selection

## Changes

- Define stable roles Melody, Harmony, Bass, Drums, CounterMelody, Texture, and
  Ambience with musical/generator capabilities independent of engine assets.
- Define a controlled `InstrumentIntent`/selection-request input containing role,
  profile/mood refs, section/purpose, desired attack/tone/articulation traits,
  hard performance capabilities/license policy, and optional user-pinned stable
  instrument ID. Trait IDs are versioned vocabulary, not arbitrary prose.
- Replace new `style: String` planner/draft inputs with structured profile/mood
  context and resolved sound-selection criteria from Task 004.
- AI/deterministic planners may propose only roles and controlled sound intents;
  their schemas reject paths, SFZ/sample filenames, engine arguments, and unknown
  traits.
- Feed structured harmony/meter/key and stable occurrences to planners/generators.
- Make generators accept role plans and capability requirements; actual verified
  instrument capabilities are supplied after Task 022B resolves an instrument.
- Map current logical PIANO/BASS/DRUMS/PAD/STRINGS to role compatibility aliases
  so existing plans/stems remain readable.
- Update Arrange UI contract to distinguish Role, Desired Character, Suggested/
  Pinned Instrument, and user ownership, without implementing catalog resolution.
- Mark the fixed logical-instrument runtime enum/branches for deletion in Task
  022B after v1 IDs are mapped to role/stable-instrument data. Do not maintain both
  planner vocabularies after registry cutover.

## Files

Arrangement domain/services/planners/generators, profile sound-intent definitions,
desktop Arrange state/contracts, compatibility adapters, docs/tests.

## API / Contracts

Arrangement plan uses context/occurrence hashes, role IDs, controlled sound
intents, optional pinned stable IDs, user constraints, and schema version. It
never contains an engine path.

## UI

Role enablement and controlled desired-character controls; show that instrument
resolution is a separate suggestion/user-choice step. Retain energy/density/
variation review and responsive behavior.

## Backend

Application service remains authority. Deterministic/AI planners are injected and
validated before any registry lookup.

## Python Worker

No change.

## Tests

Role validation, structured profile/mood/section context, controlled trait schema,
path/filename/unknown-trait rejection, user-pinned ID preservation, legacy logical
role mapping, structured harmony/meter inputs, and no core lo-fi string branch.

## Acceptance

- `Lo-fi + Nostalgic + Verse + Bass + Soft` is a structured sound request, not a
  filename or prompt-only string.
- A fixture profile can request different roles/traits without project schema or
  planner redesign.
- Existing logical plans/stems remain readable through role aliases.

## Out of Scope

Registry v2 loading/ranking, sound assets, rendering, plugin engines, or generator
quality rewrite.
