# Task 017 — Render All Stems and Produce the Dry Mix

## Goal

Render every approved MIDI track to compatible WAV stems and produce a deterministic, peak-safe `mix/dry.wav` without mastering.

## Dependencies

- Task 007 provides the production instrument renderer.
- Tasks 008 and 013–016 provide generated MIDI tracks.
- Task 015 provides the final timeline including inserted transitions.

## Required artifacts

When present in the arrangement:

```text
stems/piano.wav
stems/bass.wav
stems/drums.wav
stems/pad.wav
stems/strings.wav
mix/dry.wav
```

Do not keep using `mix/mix.wav` as the canonical new-pipeline filename. Legacy commands may continue to read it for old projects.

## Rendering orchestration

- Assemble/render the source piano MIDI across the complete section-instance timeline without changing source notes.
- Render each generated full-timeline MIDI using the registry and project `renderFormat`.
- Require matching sample rate, channel count, PCM-24 subtype, start frame, and expected frame count for every stem.
- Apply the documented renderer-tail policy consistently; transition insertions must be included in expected duration.
- Validate and atomically write each stem, retaining prior valid artifacts until replacement succeeds.
- Detect stale stems through relevant input/config fingerprints or regenerate them explicitly; do not silently mix an old stem against a new arrangement.

## Mixer behavior

Adapt `DeterministicStemMixer` rather than introduce a DAW engine. Continue to operate in frames and support:

- gain in dB;
- stereo pan;
- mute;
- timeline placement when required;
- mono/stereo conversion;
- explicit resampling only when importing a validated legacy stem;
- dry/reference modes.

For new MIDI-first stems, format mismatch is an error rather than a silent per-stem resample because the renderer is required to honor project format.

Replace final per-sample hard clipping with deterministic peak-safe handling:

1. sum in floating point;
2. measure predicted peak;
3. if peak exceeds the configured dry ceiling, apply one uniform gain to the complete mix;
4. report the applied adjustment;
5. validate finite samples and write PCM-24 WAV.

Do not place mastering, compression, limiting, or LoFi inside the mixer.

## Mix plan

Use deterministic defaults for gain/pan/mute. If arrangement v3 later contains AI mix suggestions, treat them as a separate validated optional structure and clamp them to documented ranges; do not require that extension in this task.

## Tests

- Piano only and each supported stem combination.
- Exact frame alignment and full duration including transitions.
- Project sample-rate/channel propagation.
- Mono-to-stereo handling and rejection of unsupported channels.
- Gain, pan, mute, and dry/reference behavior.
- Silent stem and no-active-track failures.
- Uniform peak reduction and no hard-clipped samples.
- Non-finite samples and malformed/wrong-format stems.
- Stale artifact detection and atomic replacement.
- Output starts at frame zero and source hashes remain unchanged.

Manual smoke test:

- Render/mix the complete project at its configured format.
- Inspect every stem and `mix/dry.wav` metadata.
- Audition stems solo and together for alignment, balance, transition duration, clipping, and unexpected noise.

## Acceptance criteria

- All active instruments exist as separate compatible lossless stems.
- `mix/dry.wav` is the canonical unprocessed reference.
- Mixer applies only deterministic balance/timeline/peak-safe operations.
- No stage silently assumes 48 kHz or confuses samples with frames.
- Mastering and LoFi remain downstream.

## Out of scope

- AI critic, LoFi tuning, mastering, MP3, automation curves beyond transition necessities, or general DAW features.

## Completion report

Report artifact metadata/fingerprints, changed files, tests/build/manual commands, applied mix gain, listening results, assumptions, and any alignment or renderer-tail limitations.
