# Task 005 — MIDI Musical Analysis

## Goal

Extract deterministic musical facts and conservative derived harmony from each clean MIDI part, then persist a versioned analysis for planning and generation.

## Dependencies

- Task 004 guarantees a validated clean-MIDI reference for MIDI-first parts.

## Existing behavior to preserve

Legacy audio `PartAnalysis` and the worker `/analyze` endpoint remain available for v1 projects and audio diagnostics. Do not silently reinterpret audio-analysis JSON as MIDI analysis.

## Analysis contract

Create a distinct versioned MIDI analysis model containing at least:

```json
{
  "version": 1,
  "partId": "A",
  "ppq": 480,
  "durationTicks": 15360,
  "durationSeconds": 23.6,
  "tempoMap": [{"tick": 0, "bpm": 82.0}],
  "timeSignatures": [{"tick": 0, "numerator": 4, "denominator": 4}],
  "bars": 8,
  "beats": 32,
  "noteCount": 184,
  "pitchRange": {"min": 48, "max": 76},
  "velocity": {"min": 24, "max": 108, "mean": 67.4},
  "noteLengthBeats": {"min": 0.12, "max": 4.0, "mean": 0.73},
  "noteDensity": 0.32,
  "rhythmicDensity": 0.41,
  "melodicRange": 28,
  "energy": 0.28,
  "key": {"tonic": "A", "mode": "minor", "confidence": 0.71},
  "chords": [
    {"startTick": 0, "endTick": 1920, "symbol": "Am", "confidence": 0.68}
  ]
}
```

Separate observed facts from inferred values through nullable fields and confidence scores. Do not fabricate a key or chord when confidence is below the documented threshold.

## Functional requirements

- Parse all note-bearing tracks and retain channel distinctions when calculating statistics.
- Pair note-on/note-off safely and reject or report unclosed/invalid notes.
- Respect the complete tempo map when converting ticks to seconds.
- Respect declared time signatures; do not silently assume 4/4 when another signature exists.
- If tempo or time signature is missing, use an explicit documented MIDI fallback and mark it as inferred.
- Calculate bars across meter changes or reject unsupported mid-bar meter changes with a clear error.
- Infer key from duration/velocity-weighted pitch-class evidence using a deterministic algorithm.
- Infer chords over musical windows aligned to bars/beats; include `unknown`/null rather than forcing weak harmony.
- Normalize density and energy to finite `0.0..1.0` values using documented formulas.
- Store `analysis/<partId>.json` atomically and update the project reference only after validation.

## CLI

Use the existing part-oriented workflow:

```bash
music-cli part analyze ./projects/song-001 --id A
```

For v2 projects this analyzes clean MIDI. For v1 projects it retains the existing audio-analysis behavior until migrated.

## Tests

Use known-event fixtures for:

- a single-tempo 4/4 piano phrase;
- tempo changes;
- 3/4 or 6/8 input;
- multiple tracks/channels;
- overlapping notes and rests;
- empty/no-note MIDI;
- missing tempo/time-signature events;
- clear major/minor harmony;
- ambiguous harmony returning low confidence/unknown;
- exact density/energy calculation;
- JSON round trip and project-reference update.

Manual smoke test:

- Analyze the clean transcription fixture.
- Compare reported duration, pitches, tempo, key, and chord movement to the source and piano roll.

## Acceptance criteria

- Observed values match fixture events exactly within documented floating-point tolerance.
- Derived values are deterministic and confidence-qualified.
- The global planner and generators can consume the JSON without reading source audio.
- Invalid MIDI produces a clear boundary error and no partial analysis reference.
- Existing legacy audio analysis remains functional.

## Out of scope

- AI-based music analysis.
- Editing/cleaning notes.
- Inferring full song structure or changing user order.

## Completion report

Report formulas and confidence thresholds, changed files, fixture coverage, tests/build commands, real-song observations, assumptions, and known analysis limitations.
