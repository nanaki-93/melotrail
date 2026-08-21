# Task 119 — Canonical musical authority and harmonic timeline

## Goal

Provide one deterministic, read-only authority from which all stage-specific
musical contexts are projected. Declared project settings remain authoritative;
analysis is descriptive evidence.

## Dependencies

Task 118.

## Public contracts

- Add an immutable `CanonicalMusicalAuthority` containing schema version,
  project key/scale, tempo, meter, occurrence timeline, harmonic timeline,
  selected part artifacts, analyzed facts, melody evidence references, and a
  deterministic context SHA-256.
- Add `MusicalAuthorityBuilder.build(projectRoot)` at the application boundary.
  It must load through `ProjectStore`, resolve part inputs only through
  `SelectedMidiArtifactResolver`, and validate approved arrangement/generated
  references when requested by a downstream projection.
- Add `HarmonicTimeline` queries for occurrence plus bar, absolute tick, and note
  interval. Results include the stable occurrence ID, section type, chord, bar,
  and tick bounds.
- Add small serializable projection DTOs/builders for part repair/enhancement,
  arrangement/generation, Cohesion, and whole-song analysis. DTOs contain only
  the data required by that consumer plus schema version and context hash.

## Required behavior

- Build occurrence bounds from canonical structure order, canonical meter, and
  the selected/analyzed duration evidence already owned by the project.
- Cycle a section progression deterministically by bar. Reject missing harmony,
  invalid meter, zero-length occurrences, overlapping bounds, and an unresolved
  selected artifact; never infer a replacement.
- Preserve declared key, tempo, meter, and harmony when analyzed values differ.
  Expose conflicts as bounded diagnostic entries in the authority.
- Canonicalize map/list ordering and integer tick/bar representations before
  hashing. Hashes must not depend on absolute paths, locale, wall-clock time, or
  JSON property iteration order.
- Keep the authority derived and non-persisted. Persist only the compact stage
  projections/reports required as evidence; do not add a database or second
  project envelope.

## Tests

- Repeated section types retain distinct occurrence identities and bounds.
- Progressions cycle correctly through occurrences longer than the progression.
- Chord queries at bar/tick boundaries are half-open and deterministic.
- Declared/analyzed key conflicts retain the declared key and emit a diagnostic.
- Changing key, meter, tempo, harmony, structure, analysis, or a selected MIDI
  hash changes the authority hash; absolute project relocation does not.
- Invalid or stale inputs fail with safe, actionable errors.

Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Relevant consumers can obtain canonical context without independently reading
  or inferring project harmony.
- Equivalent projects produce byte-equivalent projections and hashes.
- No existing selected-artifact precedence or supported v4 read changes.

## Exclusions

Do not migrate consumers in this task beyond minimal compilation adapters; Tasks
122–128 own their cutovers.
