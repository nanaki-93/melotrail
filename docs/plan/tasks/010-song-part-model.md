# Task 010 — Structured song parts and section types

## Goal

Evolve current `Part` into a persistent melody/performance `SongPart` contract
with name, structured section type, source evidence, and stage-manifest reference.

## Why

Free `role` strings and MIDI-only references cannot represent the requested
section-aware, multi-version import pipeline or reliable structure context.

## Dependencies

Tasks 002, 007–008.

## Existing Code

- `arrangement/Project.kt` `Part`, `MidiReferences`, source attestation/evidence
- `ProjectApplicationService.importPart`, update-role behavior
- desktop import dialog and current part cards
- `ProjectValidator` and legacy project fixtures

## Changes

- Add extensible `SectionTypeId` catalog with built-in intro/verse/chorus/bridge/
  outro and labels/profile support.
- Add part `name` separately from stable ID; replace new writes of free `role`
  with section type ID.
- Preserve original source and attestation/import evidence exactly.
- Add optional source-key evidence/confirmation and stage-manifest ref slots.
- Map recognized legacy roles; preserve unknown normalized IDs and surface an
  explicit unsupported-section warning rather than guessing.
- Keep `MidiReferences` dual-readable until stage-manifest migration is complete.
- Add typed create/update-name/update-section commands and precise invalidation.

## Files

Modify project/DTO/store/validator/application service and tests; add section
catalog/domain files and legacy fixtures.

## API / Contracts

`SongPartSummary` exposes IDs, name, section, source metadata, readiness, and safe
warnings, never absolute paths. Commands use expected revision.

## UI

Update state mapping/import metadata contract only; full progress UI is Task 014.

## Backend

Canonical project model only; legacy Spring `ProjectTrack` is not reused.

## Python Worker

No change.

## Tests

Legacy role mapping, unknown role preservation, section/name changes, source hash
immutability, persistence/reopen, invalidation, safe DTO path redaction.

## Acceptance

- Every new part has stable ID, name, section, immutable source metadata.
- Verse/Chorus/Bridge context lookup no longer depends on arbitrary display text.
- Legacy projects still open and retain artifacts.

## Out of Scope

Stage execution, structure occurrence migration, or audio algorithm changes.

