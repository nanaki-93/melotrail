# Task 044 — MIDI Cleanup Profiles

## Goal

Provide deterministic named MIDI-clean profiles without silently destroying
expressive performance timing.

## Dependencies

- Task 042 accepted.

## Requirements

- Version the cleanup request and define `conservative` (default),
  `transcription-safe`, and explicit `tighten-timing` profiles with documented
  bounded options.
- Retain duplicate/short/low-velocity cleanup. Add deterministic orphan note-off,
  retrigger collision, sustain-control, and velocity-outlier handling only where
  the selected profile allows it. Quantization requires explicit tighten-timing
  selection and bounded grid/strength.
- Preserve tempo/time signatures/channels, valid note boundaries, and expressive
  timing under the conservative default. Return exact before/after counts and
  applied changes.
- Keep old request parsing compatible where practical and reject ambiguous
  combinations.

## Tests

- Existing cleanup suite plus one fixture per profile/repair, timing preservation,
  pedal/orphan/collision cases, explicit quantization, malformed options, and
  deterministic output.

## Acceptance criteria

- The default remains no-surprises; stronger cleanup is named, explicit, and
  fully reported.

## Out of scope

MIDI quality UI, inventing notes/chords, or piano-roll editing.
