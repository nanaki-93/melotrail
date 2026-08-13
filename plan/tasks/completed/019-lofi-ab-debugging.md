# Task 019 — LoFi A/B Measurement and Debugging

## Goal

Measure whether LoFi processing materially changes the dry mix, then tune only the ineffective stage necessary to make the result audibly different but still musical.

## Dependencies

- Task 017 produces `mix/dry.wav`.
- Task 018 is complete or deliberately disabled through deterministic pass-through.
- The existing `LOFIPresets`, `DSPChain`, and Bedroom LoFi build path remain the implementation base.

## CLI contract

Add:

```bash
music-cli compare mix/dry.wav mix/lofi.wav
```

The command validates both inputs and reports at least:

- sample rate, channels, and frame counts;
- RMS for A and B plus absolute/dB delta;
- peak for A and B plus delta;
- mean absolute and maximum sample difference;
- changed-frame ratio above a documented numerical tolerance;
- null/difference-signal RMS;
- spectral centroid and low/mid/high band-energy differences;
- duration or alignment mismatch.

Support a machine-readable JSON option for regression tests while keeping concise human-readable output.

## Comparison rules

- Operate in frames and preserve channel meaning.
- Require matching format/timeline by default; allow an explicit diagnostic-only alignment mode if needed, never silently resample.
- Reject empty, malformed, non-finite, or incompatible audio.
- Metrics must be deterministic and must not modify inputs.
- Use a documented FFT window/hop and aggregate method for spectral metrics.

## Debugging workflow

1. Create `mix/lofi.wav` from `mix/dry.wav` using the current Bedroom LoFi preset.
2. Capture metrics and listen blind/A-B at matched perceived level where practical.
3. Isolate one DSP effect at a time using the existing chain: tone/sample-rate/bit-depth/saturation/wow-flutter/noise/etc.
4. Identify which stage is inaudible, bypassed, mis-scaled, or inappropriate for actual sample rate/channels.
5. Change the smallest preset/processor behavior needed.
6. Re-run metrics and listening before touching another effect.

Do not change five effects together. Keep `mix/dry.wav` as the immutable reference.

## Audio constraints

- Never assume 48 kHz.
- Do not add noise unless the selected LoFi preset explicitly requests it.
- Avoid per-sample random gain modulation.
- Random/noise-producing effects must use the project's deterministic seed mechanism and remain bounded.
- Preserve duration, sample rate, channels, and PCM-24 lossless output.
- An audible difference is not permission for obvious distortion, clipping, channel imbalance, DC offset, or masking.

## Tests

- Identical files yield zero/near-zero differences.
- Known gain, one-sample, spectral-filter, and channel differences produce expected metric direction/magnitude.
- Mono/stereo and 44.1/48/96 kHz fixtures.
- Mismatched timeline/format rejection.
- Non-finite/malformed input rejection.
- JSON output stability.
- LoFi regression: deterministic result, preserved format/duration, no clipping, and difference above a conservative floor without exceeding a distortion ceiling.
- Dry file hash remains unchanged.

Manual smoke test:

- Compare and blind-listen to dry vs LoFi on the complete arrangement.
- Record whether warmth, bandwidth, saturation, motion, and optional texture are audible and musical.
- Also level-match enough to distinguish tone/texture from simple loudness change.

## Acceptance criteria

- The compare command produces actionable deterministic metrics.
- LoFi is measurably and audibly different on the real dry mix.
- The change remains musical, bounded, lossless, and format-preserving.
- Only evidence-supported DSP/preset changes are made.
- Dry reference remains untouched.

## Out of scope

- Mastering, arrangement repair, new effect frameworks, or using LoFi to hide musical problems.

## Completion report

Report before/after metrics, isolated effect findings, changed files, tests/build/manual commands, listening assessment, assumptions, and remaining LoFi issues.
