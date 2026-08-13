# Task 012 Piano + Bass Quality Gate Report

## Status

Automated gate: passed with the local fake renderer.

Real listening gate: pending. `sfizz_render` is not installed/configured in
this workspace, so no real piano/bass SFZ render was produced or auditioned.
No copyrighted audio is checked in.

## Automated input and observations

`PianoBassQualityGateTest` builds a MIDI-first project with A and B parts,
structure `A A B B A`, 480 PPQ, and one-bar C-major / D-minor triad fixtures.
It renders to the explicit project format: PCM-24 WAV, 32,000 Hz, three
channels. The fake local renderer is deliberately used only in the test.

The test verifies these generated project artifacts:

- `song_plan.json` and `section_variations.json`
- `arrangement.json`
- `midi/generated/piano.mid` and `midi/generated/bass.mid`
- `stems/piano.wav` and `stems/bass.wav`
- `mix/dry.wav`
- `quality-gate.json`

It also verifies source-byte hashes, exact stage ordering, A1/A2/B1/B2/A3
instances, bass note starts on analyzed beat/chord boundaries, finite samples,
frame-count equality, the 0.95 dry-mix peak ceiling, deterministic resumption,
and invalidation after a clean-MIDI dependency becomes stale.

## Required real run

After installing/configuring a compatible local SFZ renderer and the optional
transcription runtime, run both workflows and record the resulting
`quality-gate.json` metadata and listening notes here:

```bash
make cli ARGS='quality-gate --project ./projects/direct-midi-song'
make cli ARGS='quality-gate --project ./projects/transcribed-piano-song'
```

Review source recognizability, A-A-B-B-A order, bass harmony and timing,
related repeated-section variation, attacks/tails, clipping, and piano/bass
balance. User musical acceptance remains required before Task 013 starts.
