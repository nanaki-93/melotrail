# Task 016 — Project-key transposition

## Goal

Add a deterministic, reportable stage that transposes normalized part melody
from confirmed source key into the musician-selected project key.

## Why

Imported parts must share the chosen tonal center, but incorrect automatic key
detection can damage authorship and must not be hidden.

## Dependencies

Tasks 003, 005, 010, 012, and 015.

## Existing Code

- `MidiAnalysis.kt` inferred key evidence
- MIDI note/range/timing utilities and selected artifact resolver
- no current explicit transpose artifact/stage

## Changes

- Define source-key evidence with detected key, confidence, algorithm version,
  and optional user-confirmed override.
- Add confirmation application command and readiness gate below a tested
  confidence threshold.
- Implement chromatic tonic interval transposition preserving timing/duration/
  velocity/controllers/tempo/meter and leaving percussion channels unchanged.
- Add explicit octave-fold/range policy; report every fold/out-of-range warning.
- Analyze scale/chord fit as evidence only; do not quantize expressive chromatic
  notes into the scale.
- Store source/project key, interval, policy, report, hashes, and processor version.
- Invalidate Transpose onward on project/source key change.

## Files

Add transposition processor/evidence/report and commands; modify analysis mapping,
stage registry/readiness/Parts state and tests.

## API / Contracts

`ConfirmSourceKey` and Transpose processor consume structured `MusicalKey`.
Result is a new TRANSPOSED artifact; display spelling remains metadata.

## UI

Task 014 card shows detected key/confidence and confirmation picker when needed.

## Backend

Kotlin deterministic processor; no model call.

## Python Worker

No change.

## Tests

All chromatic intervals/enharmonics, major/minor, octave bounds, drum preservation,
low-confidence gate, confirmation change invalidation, deterministic hashes.

## Acceptance

- Project key is authoritative and original/normalized MIDI remains unchanged.
- Low-confidence detection never silently transposes.
- Report makes every pitch movement reproducible.

## Out of Scope

Mode conversion, modulation, scale quantization, harmonic reharmonization.

