# Task 018 — Enhancement context and intensity contracts

## Goal

Define the context-rich, non-destructive Enhancement port, Off/Subtle/Balanced/
Creative policies, validation budgets, and transparent MVP placeholder.

## Why

UI/data contracts must precede sophisticated AI, and enhancement must never be a
generic “make this lo-fi” call over isolated MIDI.

## Dependencies

Tasks 004–008, 010–012, and 017.

## Existing Code

- `MidiAiFix.kt` model port/strict JSON/applier patterns
- Qwen model adapters and model provenance/license types
- profile/mood resolved snapshot and structured section harmony

## Changes

- Implement deterministic `MusicalProcessingContext` serialization/hash with
  project key/scale, section chords, BPM, meter, mood/profile versions/parameters,
  section/part identity, corrected input hash, intensity, seed, pipeline version.
- Define intensity policies and bounded operation/edit/identity-distance limits.
  Off selects Corrected and makes no processor/model call; Subtle is default.
- Define planner/result/validator/applier ports and edit report schema.
- Register a transparent deterministic no-op or extremely conservative fixture
  processor for MVP, labeled as such. It may create no artifact for Off.
- Add selection/invalidation/cache rules and unknown profile/model failure behavior.
- Require model identity/version/license fields for any non-placeholder AI run.

## Files

Add context/enhancement domain/application ports/policies/tests; update profile
fields, stage registry/artifact graph/readiness and UI DTOs.

## API / Contracts

Versioned strict plan echoes subject/input/context hashes and contains only
code-owned musical edit operations/numbers. No paths/prompts-as-contract.

## UI

Expose intensity options, current selection, and honest capability label; Task
020 supplies comparison UX.

## Backend

Stage runner handles Enhance. Placeholder is injectable and deterministic.

## Python Worker

No command required for MVP.

## Tests

Context completeness/hash, intensity bounds/order, Off no-call, default Subtle,
cache invalidation by every relevant context field, unknown versions, no-op truth.

## Acceptance

- Every enhancement request contains complete structured musical context.
- Off reliably resolves Corrected and preserves old Enhanced evidence.
- MVP does not claim advanced AI behavior it does not implement.

## Out of Scope

Live model quality, arbitrary prompt editing, or full-song generation.

