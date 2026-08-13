# Task 013 — Deterministic Drum MIDI Generator

## Goal

Add musically bounded drum MIDI and simple fills after the piano+bass architecture has passed its quality gate.

## Dependencies

- Task 012 is explicitly accepted.
- Task 006 provides the validated drum note map.
- Task 011 provides typed drum role parameters.

The current `sounds/drums/drums.sfz` maps kick 36, snare 38, clap 39, closed hat 42, and open hat 46. Use the validated Task 006 registry map; do not assume a crash/cymbal note exists.

## Input and roles

Consume tempo map, time signature, section duration, energy, density, transition intent, and typed drum parameters. Support only:

```text
minimal
soft_lofi
standard_groove
half_time
build
```

Use allow-listed snare placement such as `beats_2_4`, `beat_3`, or `none`, as defined by the v3 arrangement schema.

## Generation rules

- Write a full-timeline `midi/generated/drums.mid` on the configured drum channel.
- Resolve named hits through the registry note map; never hard-code an asset path.
- Place hits in beat-relative ticks and respect non-4/4 meters where supported.
- Convert density into explicit deterministic pattern subdivision/omission rules.
- Apply bounded swing only to appropriate off-beat subdivisions and never reorder events.
- Use energy for bounded velocities and pattern activity.
- `fillLastBar` creates a simple allow-listed fill only inside the final bar of that section.
- Never use uncontrolled random timing, per-hit random gain, or audible noise generation in MIDI composition.
- Prevent duplicate simultaneous hits unless the pattern explicitly layers different named drums.
- Validate pitches, channels, ticks, velocities, and section boundaries before atomic write.

## Tests

- Exact events for every pattern.
- Registry note-map resolution and missing-hit failure.
- 4/4, 3/4, and one explicitly unsupported meter error.
- Density and energy boundary behavior.
- Swing at zero and maximum allowed value.
- Half-time snare placement.
- Last-bar fill placement and no spill into the next section.
- Repeated sections with varying patterns.
- Deterministic output and no source/bass MIDI changes.

Manual smoke test:

- Generate/render drums for the accepted piano+bass project.
- Audition drums alone and in context.
- Confirm soft transients, correct meter, restrained fills, and no rhythm drift.

## Acceptance criteria

- Drums exist as inspectable MIDI before rendering.
- All roles and hits are schema/registry allow-listed.
- Output is deterministic and section-aligned.
- Fills are simple, bounded, and optional.
- The accepted piano+bass behavior remains unchanged when drums are disabled.

## Out of scope

- Sample synthesis, procedural audio noise, advanced grooves, or learned drumming.
- Pad, strings, or transition-engine implementation.

## Completion report

Report patterns and note map, changed files, tests/build commands, generated artifacts, listening observations, assumptions, and remaining groove limitations.
