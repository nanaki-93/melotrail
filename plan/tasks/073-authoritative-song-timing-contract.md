# Task 073 — Authoritative Song Timing Contract

## Goal

Reproduce the reported Lo-fi and arranged-MIDI synchronization defects with
deterministic fixtures, then define the single song-clock contract that later
generation, rendering, and UI work must consume.

## Dependencies

- Current repository baseline.
- Read `../PLAN.md` completely before implementation.

## Scope

This task owns diagnostic fixtures, timing-domain types, PPQ normalization,
section/transition boundary rules, timing serialization, and synchronization
reports. It must not fix Lo-fi source selection or refactor all MIDI generators;
those changes belong to Tasks 074 and 075.

## Requirements

- Capture a failing reproduction before editing for the known mismatch where
  analysis resolves selected Lo-fi MIDI but piano stem assembly reads repaired
  MIDI directly. Record the observed input identities, hashes, tempo, PPQ, and
  note-on times.
- Capture a failing reproduction for arranged-MIDI drift by comparing absolute
  musical and wall-clock note-on positions across piano, bass, drums, pad,
  strings, and transitions.
- Add reusable MIDI fixtures covering:
  - two parts with different PPQ values;
  - multiple tempo changes;
  - 4/4 and 3/4 meter;
  - straight eighth notes eligible for Lo-fi swing;
  - repeated occurrences of one part;
  - notes ending exactly at a section boundary;
  - all generated instruments and a one-bar transition;
  - enough repeated bars to expose cumulative drift.
- Introduce an immutable `SongTimeline` or equivalently named domain model with:
  - stable occurrence IDs and part IDs;
  - canonical PPQ and per-input PPQ conversion evidence;
  - section start/end ticks using explicit half-open ranges;
  - local-to-song tick conversion;
  - authoritative tempo and time-signature events;
  - explicit transition ranges and their tempo/meter ownership;
  - total ticks, seconds, and frames for a requested sample rate.
- Use musical positions as the source of truth. Convert ticks through the
  authoritative tempo map and convert to frames once; do not create later
  section starts by summing independently rounded audio durations.
- Define deterministic PPQ normalization:
  - use an exact common PPQ when it is at most 9,600;
  - otherwise use rational conversion with documented rounding;
  - record maximum tick and time error;
  - reject a conversion that exceeds the code-owned tolerance.
- Define boundary semantics centrally:
  - note-ons belong to `[sectionStart, sectionEnd)`;
  - note-offs may occur at `sectionEnd`;
  - transitions occupy their own explicit range;
  - an inserted bridge uses the incoming section's tempo and meter.
- Add a versioned, deterministic synchronization report containing input
  fingerprints, PPQ conversions, occurrence ranges, tempo/meter map, transition
  ranges, total duration/frames, and maximum alignment error.
- Keep diagnostic work read-only with respect to existing project artifacts.
  Test fixtures may write only inside their temporary project roots.

## Tests

- Unit/property tests for exact and rounded PPQ conversion, round trips,
  ordering, overflow, deterministic rounding, and tolerance rejection.
- Timeline tests for tempo changes, meter changes, repeated parts, transition
  insertion, silence, boundary note-offs, and frame conversion.
- Serialization/fingerprint tests proving identical input produces identical
  timeline and report content.
- Locally observe the known behavior with a red regression assertion before
  implementation. Land reusable fixtures and green timing-contract tests in
  this task; the desired Lo-fi end-to-end assertion lands with Task 074 so the
  repository is not handed off with a failing suite.
- Run `./gradlew test` after focused tests. Run desktop tests only if desktop
  code or fixtures change. Do not run worker tests unless worker code changes.

## Acceptance criteria

- One documented fixture deterministically exposes the current selected-MIDI
  mismatch and one exposes accumulated or boundary synchronization error.
- The repository has one reusable timing contract capable of representing all
  fixture sections and transitions.
- The timing contract converts mixed PPQ inputs deterministically and reports
  any bounded loss.
- Boundary and frame calculations have exhaustive focused tests.
- Existing source and derived artifacts are unchanged.
- All committed automated tests pass.

## Out of scope

- Fixing `StemRenderingMixer` to use Lo-fi MIDI.
- Integrating cohesion into the desktop workflow.
- Refactoring every MIDI generator to consume the new timeline.
- UI redesign, import simplification, audio DSP, or mastering changes.
