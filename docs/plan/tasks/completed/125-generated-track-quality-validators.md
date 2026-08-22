# Task 125 — Generated-track quality validators

## Goal

Validate every deterministic arrangement role through one typed reporting
boundary before generated MIDI can become current.

## Dependencies

Task 124.

## Public contracts

- Add `RoleValidationReport` with role, occurrence/aggregate target, input and
  output hashes, validator policy version, metrics, bounded warnings, violations,
  and pass/fail result.
- Add `GeneratedRoleValidator` implementations for piano, bass, drums, pad,
  strings, and transitions. Reuse and migrate `PianoBassQualityGate`; do not keep
  two active validators for the same invariant.
- Add a versioned, code-owned `RoleValidationPolicy`. Instrument pitch bounds
  come from the resolved Instrument Registry; arrangement density/activation
  bounds come from the approved detailed arrangement.

## Validation rules

- All roles: readable PPQ MIDI, matched note events, velocity 1–127, positive
  durations, occurrence bounds, preserved tempo/meter, deterministic ordering,
  no exact duplicate notes, and non-empty output when the role is activated.
- Piano, pad, and strings: sustained notes of at least half a beat must be chord
  tones; shorter non-chord tones must remain in scale and resolve by step to a
  chord tone by the next beat.
- Bass: notes sounding on a canonical beat must be chord tones; leaps above one
  octave are warnings and leaps above nineteen semitones are violations unless
  the immediately following note resolves by step in the opposite direction.
- Drums: notes use the registered kit map and percussion channel, stay inside
  occurrence bounds, and respect arrangement density; harmony rules do not apply.
- Transitions: every note lies inside the boundary window supplied by its plan.
- Aggregate checks reject missing activated roles and duplicated occurrence/role
  artifacts. Cross-role collision/contrast remains Task 127 critic evidence.

## Tests

Add focused valid/invalid fixtures for every rule and role, including exact
half-beat, octave, nineteen-semitone, occurrence-end, density, and registered
range boundaries. Verify deterministic JSON/report ordering and that a failed
report prevents artifact selection.

Run `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Every generated artifact has one current passing validation report.
- A generator cannot publish or approve invalid output.
- Existing piano/bass checks have one owner after migration.

## Exclusions

Do not repair failed generation automatically or ask AI to validate MIDI.
