# Task 008 — Deterministic Bass MIDI Generator

## Goal

Replace the oscillator-based bass generation spike with deterministic bass composition written to an inspectable MIDI artifact.

## Dependencies

- Task 005 provides key, chord, tempo, meter, bars, and confidence-qualified analysis.
- Task 007 can render bass MIDI through the registry-selected instrument.

## Existing code to adapt

- Preserve the generation boundary represented by `InstrumentStemGenerator`, but separate composition from rendering.
- Retire `BassStemGenerationAdapter` and `DeterministicTestBassRenderer` from production use only after equivalent tests cover the new MIDI path.
- Do not change source-piano MIDI or working DSP.

## Input model

Define a validated `BassGenerationRequest` containing:

- section instance/index and full-timeline start tick;
- PPQ, tempo map, time signatures, and section length;
- key and chord segments with confidence;
- energy and density in `0.0..1.0`;
- role: `root`, `root_fifth`, `octave`, `sustained`, or `simple_walking`;
- movement: `static`, `rising`, `falling`, or `balanced`;
- register: initially `low` only;
- syncopation in a conservative documented range;
- MIDI channel/program from the registry or rendering policy.

No input may contain arbitrary note events from Qwen.

## Generation rules

- Use analyzed chord roots when chord confidence meets the documented threshold.
- Fall back to the analyzed key tonic when chords are weak but key confidence is sufficient.
- Produce silence and a diagnostic when harmony is insufficient; never guess a random root.
- Implement each role with explicit beat-relative patterns.
- Convert density into fewer/more pattern events without per-run randomness.
- Use energy to select bounded velocity and octave emphasis, not arbitrary gain modulation.
- Apply syncopation only as a deterministic offset bounded within the current beat/bar.
- Keep pitches in a documented bass range, transpose by octaves when necessary, and reject impossible values.
- Prevent same-pitch overlaps, zero/negative durations, hanging notes, and notes crossing section boundaries unless the contract explicitly permits a final release.
- Preserve the project's tempo/meter map in the generated Standard MIDI File.
- Combine all arranged sections into one full-timeline `midi/generated/bass.mid` so repeated instances can differ.
- Write atomically after reparsing and validating events.

## Tests

- Exact note events for every role over known chords.
- Major/minor roots and accidentals.
- Chord-confidence and key-confidence fallbacks.
- Low-confidence silence behavior.
- Density `0.0`, low, medium, and `1.0`.
- Energy-to-velocity bounds.
- Rising/falling/balanced movement.
- Syncopation stays in legal boundaries.
- 3/4 and 4/4 section lengths.
- Repeated sections with different requests.
- No overlaps, hanging notes, illegal values, or timeline overflow.
- Equivalent event output across repeated identical runs.

Manual smoke test:

- Generate bass for the clean piano fixture and render it using Task 007.
- Audition bass alone and with the piano reference.
- Confirm roots/chord changes align and the bass does not dominate.

## Acceptance criteria

- The generator writes real MIDI under `midi/generated/` before audio rendering.
- Actual notes come from deterministic code, not Qwen output.
- All five initial bass roles are implemented and tested.
- Harmony uncertainty produces a conservative fallback or silence.
- The production path no longer depends on a fixed 55 Hz/36-note oscillator stem.
- Source MIDI and analysis files remain unchanged.

## Out of scope

- Bass sample rendering changes beyond using Task 007.
- Drum, pad, strings, or transition generation.
- Improvisational or learned bass generation.

## Completion report

Report pattern definitions and ranges, changed files, generated MIDI path, tests/build commands, listening observations, assumptions, and remaining musical limitations.
