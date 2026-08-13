# Task 014 — Deterministic Pad MIDI Generator

## Goal

Generate restrained sustained pad harmony that supports sections and later transitions without inventing harmony when MIDI analysis is uncertain.

## Dependencies

- Task 012 is accepted.
- Task 005 supplies chord/key confidence.
- Task 011 supplies typed pad parameters.

## Input and roles

Consume chord segments, key, confidence, tempo/meter, section duration, energy, density, and register. Initially support only:

```text
sustained_chords
```

Registers are allow-listed, initially `mid` and `mid_high`.

## Generation rules

- Write a full-timeline `midi/generated/pad.mid`.
- Generate triads or deliberately documented reduced voicings from supported chord symbols.
- Use deterministic inversions minimizing total voice movement between adjacent chords.
- Keep notes in the selected register and within legal MIDI range.
- Start/end events at analyzed harmonic boundaries, with a small documented release gap to prevent hanging notes.
- Density controls whether all chord segments or a deterministic subset are voiced; energy controls bounded velocity/voicing width.
- When chord confidence is below threshold, fall back only if key confidence and an explicit safe tonic-based policy permit it; otherwise output silence and a diagnostic.
- Do not infer new connecting chords in this task.
- Avoid same-channel/pitch overlaps and notes crossing unintended section boundaries.

## Tests

- Major, minor, and supported seventh/sus chord parsing if included.
- Unsupported/unknown chord handling.
- Inversion selection and deterministic minimum movement.
- Register bounds.
- Density/energy boundaries.
- Rests and changing chord durations.
- Low-confidence fallback and silence behavior.
- No hanging/overlapping notes at section boundaries.
- Deterministic event output.

Manual smoke test:

- Render pad for selected sections of the accepted project.
- Audition piano+bass with pad off/on.
- Confirm harmony matches, pad remains behind the piano, and section changes feel smoother without masking attacks.

## Acceptance criteria

- Pad output is inspectable deterministic MIDI.
- Harmony derives only from validated analysis and bounded fallback rules.
- Voice leading and register constraints are tested.
- Disabling pad preserves the accepted piano+bass output.
- No source MIDI or existing generated track is edited.

## Out of scope

- Transition chord insertion, arpeggios, sound design, or complex orchestration.
- Strings generation.

## Completion report

Report supported harmony/voicing rules, confidence thresholds, changed files, tests/build commands, artifacts, listening observations, assumptions, and unsupported chords.
