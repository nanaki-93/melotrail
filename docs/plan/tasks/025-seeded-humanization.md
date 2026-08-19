# Task 025 — Seeded humanization stage

## Goal

Add a deterministic, profile/mood/role-aware Humanization stage after Cohesion
and before Render, with bypass, regeneration, and edit evidence.

## Why

Human feel is musical processing, not hidden randomness. Reproducibility requires
an explicit configuration, seed, bounds, and artifact.

## Dependencies

Tasks 022–024.

## Existing Code

- `MidiLoFiFeel.kt` fixed 80 BPM/swing artifact
- deterministic MIDI timing/generator/timeline utilities
- profile/mood parameter resolution and stage runner

## Changes

- Define humanization config for timing, velocity, duration, chord staggering,
  swing, drum and bass behavior, per-role/profile/mood bounds, and seed.
- Implement deterministic transform over cohesive arrangement MIDI. MVP forbids
  pitch changes and note creation/deletion.
- Protect bar/section anchors, minimum durations, ordering/collisions, tempo/meter,
  and playable ranges. Record exact edits and per-role summary.
- Add bypass selection (uses Cohesive input), approve/current selection, and
  “new variation” that creates/stores a new seed and artifact.
- Map existing Lo-fi Feel as a legacy groove preset/evidence; do not rewrite old
  files or automatically double-apply swing.
- Invalidate Render onward on selected seed/config/input change.

## Files

Add humanization domain/processor/application service, profile policies, artifact
graph/read model, Build UI controls, migration/docs/tests.

## API / Contracts

Run request contains build/context/input hashes, normalized resolved config, seed,
processor version. Result contains HUMANIZED artifact and edit report.

## UI

Build shows profile default, amount/seed, bypass, regenerate, compare, warnings,
and exact selection. Advanced raw timing numbers can stay in details.

## Backend

Kotlin deterministic processor through stage runner.

## Python Worker

No change.

## Tests

Seed determinism/difference, per-role bounds, anchor protection, no pitch/count
change, collision/min-duration, bypass, Lo-fi Feel compatibility, invalidation.

## Acceptance

- Same input/config/seed/version produces identical output/report/hash.
- Humanization cannot silently mutate melody identity.
- No double swing occurs for migrated Lo-fi Feel projects.

## Out of Scope

Machine-learned performance generation or nondeterministic live variation.

