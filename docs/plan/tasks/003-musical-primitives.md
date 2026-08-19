# Task 003 — Structured musical primitives

## Goal

Implement reusable tonic/key/scale, tempo, and time-signature domain values with
validation, deterministic serialization, and musician-friendly formatting.

## Why

Free strings and implicit 4/4/C-major assumptions cannot support safe
transposition, harmony, profiles, context-aware processing, or future genres.

## Dependencies

Task 002.

## Existing Code

- `arrangement/MidiAnalysis.kt` tempo/meter/key inference structures
- MIDI tempo/time-signature mapping utilities
- current free-string key/style fields in legacy Spring/arrangement plans

## Changes

- Add `PitchClass` with chromatic identity and preferred enharmonic spelling.
- Add versionable `ScaleModeId` with executable Major and Natural Minor behavior.
- Add `MusicalKey`, positive finite `Tempo`, and valid `TimeSignature` types.
- Derive display symbols; parsing is confined to import/API adapters.
- Add scale pitch membership/interval helpers without quantizing notes.
- Persist these primitives in the v4 composition placeholder and context-ready
  serialization. Unknown future mode IDs are preserved but cannot execute.

## Files

Create a focused music-domain package; modify v4 DTO/mappers/validators and unit
tests. Adapt analysis only to convert inferred evidence, not project authority.

## API / Contracts

Stable DTOs contain tonic chromatic value, spelling, mode ID, BPM, numerator,
and denominator. Labels like `Eb major` are output fields/formatters, not IDs.

## UI

No screen yet; provide ordered tonic/mode/time-signature option models.

## Backend

Application validators use the new types. Legacy string parsing stays at adapter
boundaries with explicit failure/warning.

## Python Worker

No change; worker results may later map into source-key evidence.

## Tests

All pitch spellings/enharmonics, major/minor membership, JSON round-trip, invalid
BPM/meter, non-4/4 valid meters, unknown mode preservation, display formatting.

## Acceptance

- `C#` and `Db` compare chromatically while retaining preferred spelling.
- Core types support non-4/4.
- No core processor consumes an arbitrary key string introduced by this task.

## Out of Scope

Modes beyond Major/Natural Minor behavior, tempo/meter maps, chords, or transpose.

