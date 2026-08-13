# Task 007 — MIDI to SFZ to WAV Rendering

## Goal

Prove that validated MIDI and a registry-selected SFZ instrument can produce a correctly formatted, timeline-aligned lossless WAV stem.

## Dependencies

- Task 006 provides safe instrument lookup and licensing metadata.
- The asset prerequisite is already satisfied by `../../../sounds/piano/piano.sfz`, `../../../sounds/bass/bass.sfz`, and their local sample files.
- A compatible SFZ renderer is not currently available on PATH and must be selected/configured by this task.

## Existing sound baseline

Read `../../SOUND_LIBRARY_BASELINE.md`. Use `../../../sounds` as the default registry root and exercise the existing starter piano and bass. Do not download or invent replacement instruments as part of renderer integration.

The source samples are mono, 44.1 kHz, PCM-16. That describes the sampler inputs only; the rendered stem must still match the project's explicit sample rate/channel layout and PCM-24 lossless output contract.

## Existing code to reuse

- Existing WAV/audio format models and decoders for output verification.
- Project `renderFormat` from Task 004.
- Worker HTTP pattern if the offline renderer is hosted by Python.

The current `DeterministicTestBassRenderer` and direct tone/noise methods are test/legacy implementations. Do not extend them into a custom synthesizer.

## Interface

Add a Kotlin rendering boundary conceptually equivalent to:

```kotlin
interface InstrumentRenderer {
    suspend fun render(
        midi: Path,
        instrument: InstrumentName,
        output: Path,
        format: RenderFormat,
        expectedFrames: Long
    ): RenderResult
}
```

`RenderResult` must include output path, sample rate, channels, bit depth, frame count, duration, peak, and renderer identity/version.

Select and install/configure one local SFZ renderer, preferring `sfizz_render`/sfizz when it supports the existing SFZ subset and required offline output. Document its version and license. Implement its adapter with structured arguments, never a shell command string. Support an environment/config override such as `SFZ_RENDERER_PATH` without allowing Qwen to influence it.

## Functional requirements

- Resolve the logical instrument through the registry immediately before rendering.
- Resolve piano and bass to the existing `../../../sounds/piano/piano.sfz` and `../../../sounds/bass/bass.sfz`; do not hard-code those paths outside the registry test fixture.
- Validate MIDI and registry data before launching the renderer.
- Render to a uniquely named temporary WAV and atomically replace the requested output only after verification.
- Request the project's explicit sample rate and channel count; never assume 48 kHz.
- Write/convert to PCM-24 lossless WAV when the renderer cannot directly produce that subtype.
- Enforce `expectedFrames`: pad trailing silence or trim only beyond a small documented renderer tail policy. Never shift the musical start frame.
- Reject non-finite samples, malformed WAV headers, wrong format, empty output, and unsafe output/source identity.
- Capture bounded stdout/stderr and return a clear setup/render/validation error.
- Preserve MIDI, SFZ, samples, and all source files.

## Tests

Unit tests with a fake renderer process/boundary:

- exact arguments and registry-resolved path;
- sample-rate/channel propagation;
- successful atomic output;
- process failure and timeout;
- malformed, empty, wrong-rate, wrong-channel, clipped/non-finite, too-short, and too-long WAV;
- output cannot overwrite MIDI, source, registry, or SFZ files;
- no final partial output after failure.

Integration tests use the existing starter piano/bass assets when the configured renderer is present. They must skip with a clear renderer-unavailable reason, not an asset-unavailable reason, when the audited local sample inventory exists.

Manual gate:

```text
piano.mid + piano.sfz -> piano.wav
bass.mid + bass.sfz -> bass.wav
```

Inspect WAV metadata and listen for correct pitch, timing, instrument selection, clean start, and complete tail.

## Acceptance criteria

- Piano and bass MIDI render locally through the same interface.
- The existing `../../../sounds` piano and bass are the instruments heard in the manual gate.
- Output matches project sample rate, channels, PCM-24 subtype, and expected timeline.
- Instrument paths come only from the registry.
- Unit tests require no installed SFZ renderer or sample library.
- The direct test oscillator is not made the production renderer.

## Out of scope

- Bass/drum/pad/string composition.
- A VST host, DAW, sampler implementation, automatic sample download, or replacement of the existing starter pack.
- Mixing or mastering.

## Completion report

Report renderer/version/configuration, changed files, unit/integration/build commands, WAV metadata, listening observations, license status, assumptions, and remaining renderer limitations.
