# Task 032 — Non-destructive Audio Cleanup and Transcription Quality Gate

## Goal

Give audio imports a conservative, selectable cleanup pass before transcription
and analysis, with evidence, A/B listening, and no destructive “AI magic.”

## Requirements

- Add a typed worker/service command that consumes an inspected WAV or decoded
  working copy and atomically publishes `prepared/<part>/clean.wav` plus an
  updated report. It must retain actual sample rate/channel count and output
  PCM-24 WAV.
- Reuse and harden the existing worker repair primitives rather than create a
  second DSP stack: DC-offset removal, short-run clipping repair, isolated
  declicking, narrow 50/60 Hz hum removal, and gentle stationary-noise
  reduction. Apply each only when measured evidence exceeds documented,
  testable thresholds.
- Define a schema-only `InputCleanupPlan`: mode, allowed operations,
  fixed-range settings, evidence, confidence, warnings, and chosen input
  artifact. A deterministic planner is mandatory. An optional local AI advisor
  may rank those bounded candidates using report metrics, but cannot emit paths,
  commands, arbitrary DSP values, or source changes; invalid advice falls back
  to deterministic selection.
- Present Inspect only and Safe cleanup choices, a plain-language operation
  summary, warnings about destructive limits, original/clean A/B preview, and
  an explicit choice of which artifact feeds transcription. Default to the
  original unless a measured issue is found and the user confirms cleanup.
- Add a transcription quality gate after Basic Pitch: validate MIDI container,
  nonempty note result, duration sanity against source, note-rate bounds, pitch
  range, and cleanup success. Explain whether failure came from decode, cleanup,
  model runtime, inference, or MIDI validation. Preserve raw MIDI for diagnosis.
- Write before/after metrics/fingerprints and selected-mode provenance without
  storing source paths outside the project or raw model response.

## Tests

- Mono/stereo and varied sample-rate fixtures; no-op clean audio; DC offset;
  isolated clips/clicks; hum; stationary noise; too-short audio; and NaN/invalid
  decoder output.
- Operation threshold, parameter-bound, atomic-write, source-preservation,
  fallback-plan, and report-provenance tests.
- Fake transcription tests for every quality-gate failure and success.
- Manual A/B listening fixtures; assert duration, sample rate, channels, and
  PCM-24 output are preserved.

## Acceptance criteria

- The product can improve supported noisy/clipped input safely while retaining
  the original and making every change inspectable and reversible.
- It never claims to restore unrecoverable audio, remove arbitrary background
  music, or create reliable MIDI from unsupported material.

## Out of scope

Cloud enhancement, source separation, vocal isolation, pitch/time correction,
loudness normalization, and automatic destructive cleanup without consent.
