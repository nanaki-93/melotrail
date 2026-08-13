# Task 003 — Deterministic MIDI Cleanup

## Goal

Convert raw transcription output into stable, inspectable clean MIDI without destroying expressive piano timing.

## Dependencies

- Task 002 produces valid raw piano MIDI.

## Public contracts

Add:

```text
POST /midi-clean
```

Request:

```json
{
  "path": "/project/midi/raw/A.mid",
  "outputPath": "/project/midi/clean/A.mid",
  "quantize": "1/16",
  "strength": 0.4,
  "minNoteMs": 50,
  "minVelocity": 8,
  "normalizeVelocity": false,
  "cleanSustain": false
}
```

`quantize` is optional. When absent, note timing must not be quantized. `strength` is constrained to `0.0..1.0`; `0.0` leaves timing unchanged and `1.0` snaps fully to the selected grid.

Add CLI behavior equivalent to:

```bash
music-cli midi-clean --input raw.mid --output clean.mid
music-cli midi-clean --input raw.mid --output clean.mid --quantize 1/16 --strength 0.4
```

## Cleanup rules

Use the MIDI library selected and pinned for the worker. All transformations must be deterministic and operate in ticks while using the file's tempo map to evaluate millisecond thresholds.

Apply operations in this order:

1. Parse and validate tracks, ticks-per-quarter, channel, pitch, velocity, and event ordering.
2. Normalize velocity-zero note-on events to note-off semantics in the internal representation.
3. Remove exact duplicate notes with the same channel, pitch, start, and end.
4. Remove notes shorter than `minNoteMs`.
5. Remove notes with note-on velocity below `minVelocity`.
6. Resolve same-channel/same-pitch overlap by ending the earlier note at the later note's start; remove it if this makes its duration non-positive.
7. Optionally clean invalid or redundant sustain-controller changes without deleting valid expressive sustain.
8. Optionally normalize velocity within documented bounds.
9. Optionally move note start/end timing toward the selected grid by the requested strength.
10. Write legal, ordered note events while preserving tempo, time-signature, program, channel, track-name, and other safe metadata.

Never make aggressive quantization the default. Do not collapse multi-track MIDI into one track unless the input is already effectively single-track and the behavior is explicitly documented.

## Response and reporting

Return output path and before/after statistics:

- input/output note count;
- duplicates removed;
- short/low-velocity notes removed;
- overlaps repaired;
- quantized note count;
- preserved tempo/time-signature event counts.

Write atomically after reparsing and validating the result.

## Tests

Create small programmatic or checked-in fixtures for:

- duplicate notes;
- very short and low-velocity notes;
- overlapping repeated piano notes;
- velocity-zero note-off representation;
- multiple channels and programs;
- sustain controllers;
- tempo changes;
- no quantization, partial quantization, and full quantization;
- invalid grid, strength, threshold, and corrupted MIDI input;
- deterministic byte/event output across repeated runs.

Test the Kotlin command mapping and CLI validation without requiring transcription.

Manual smoke test:

- Clean the raw Task 002 output with defaults.
- Compare raw and clean piano rolls.
- Audition both and confirm cleanup removes obvious artifacts without making expressive timing mechanically rigid.

## Acceptance criteria

- Default cleanup changes only documented artifacts and does not quantize timing.
- Partial quantization follows the mathematical strength definition.
- Essential musical metadata survives round trip.
- Output reparses and contains only legal, positive-duration notes.
- Source and raw MIDI remain untouched.
- Existing worker and Kotlin tests still pass.

## Out of scope

- Chord/key analysis.
- Arrangement-aware editing.
- Humanization or random timing changes.
- Audio rendering.

## Completion report

Report cleanup defaults, changed files, tests/build commands, before/after statistics on the real fixture, listening observations, assumptions, and known unsupported MIDI events.
