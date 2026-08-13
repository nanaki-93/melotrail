# Task 020 — Reuse and Validate the Mastering Pipeline

## Goal

Feed the completed dry or LoFi mix through the current repair/mastering pipeline and produce a validated `output/master.wav` without using mastering to compensate for arrangement defects.

## Dependencies

- Task 017 provides `mix/dry.wav`.
- Task 019 optionally provides `mix/lofi.wav`.
- Existing worker repair/master commands are the required implementation base.

## Input precedence

Make the source explicit rather than silently choosing:

- use `mix/lofi.wav` when the project build enables LoFi;
- otherwise use `mix/dry.wav`;
- expose/report the chosen input in build progress and release metadata.

If repair is retained before mastering, write an inspectable lossless intermediate such as `mix/repaired.wav`; do not place generic intermediate audio outside the project unless `--output-dir` explicitly requests it.

## Functional requirements

- Reuse `RepairCommand`, `MasterCommand`, and existing settings unless an integration defect is proven.
- Keep worker stage boundaries and errors explicit.
- Require the input and all intermediates to be valid lossless WAV.
- Never overwrite `mix/dry.wav`, `mix/lofi.wav`, stems, MIDI, or source files.
- Propagate actual sample rate and channel count; do not silently force 48 kHz/stereo.
- Write the master atomically as PCM-24 WAV.
- Validate duration against input within documented latency/tail tolerance.
- Validate finite samples, RIFF/WAVE container, PCM subtype, peak ceiling, and non-empty audio.
- Record mastering settings, input artifact/fingerprint, sample format, peak, and target loudness in release metadata.
- Preserve the existing `--dry-run`, output-directory, deterministic/no-AI build behavior.

Do not alter arrangement, MIDI generators, mixer balance, or LoFi during this task. If input is clipped or badly balanced, fail/report the upstream problem rather than applying progressively stronger mastering.

## Tests

- Dry-without-LoFi and LoFi input paths.
- Repair enabled/disabled if supported by the build contract.
- Worker request fields and stage order using fakes.
- Worker health, repair, and mastering failure propagation.
- Sample-rate/channel/duration preservation.
- PCM-24 WAV container validation and non-`.mp3` output.
- Peak ceiling and non-finite/empty/malformed output rejection.
- Source/intermediate overwrite prevention and atomic replacement.
- Dry-run creates/changes no project files.

Manual smoke test:

- Run both dry and LoFi masters on the same completed project.
- Inspect metadata and listen for balanced loudness, no pumping/clipping, retained transients, and no attempt to hide arrangement problems.

## Acceptance criteria

- `output/master.wav` is a valid, non-empty PCM-24 WAV derived from the explicitly reported mix.
- Existing working mastering DSP is reused, not rewritten.
- Actual sample rate/channels and musical duration are preserved.
- Failures are stage-specific and cannot overwrite source/intermediate artifacts.
- Mastering remains separate from mixing and MP3 export.

## Out of scope

- New mastering algorithms, streaming-service publishing, automatic genre mastering, or AI parameter generation.

## Completion report

Report chosen input/settings, changed files, tests/build/manual commands, input/output metadata and loudness/peak observations, assumptions, and remaining mastering limitations.
