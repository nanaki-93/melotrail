# Task 027 — Desktop Mix, Build, and Playback

## Goal

Complete the useful desktop workflow with persisted mix controls, real local playback, structured Build Song progress, and auditioning of dry, LoFi, and master artifacts.

## Dependencies

- Task 026 approved arrangement workflow and Task 023 mix/build services.

## Requirements

- Implement five bounded channel strips with gain dB, pan, mute, and solo; render only active/available instruments.
- Persist settings through `MixApplicationService`; debounce slider commits and provide a reset-to-engine-defaults action.
- Re-mix existing stems without MIDI regeneration or stem rendering and retain peak safety/format validation.
- Add Build Song options for LoFi and optional MP3, plus readiness validation before start.
- Show stage count/name, determinate progress where known, artifact path, reuse status, cancel-at-boundary, and exact failure.
- Implement `JvmAudioPlayer` for validated local audio artifacts with play, pause/resume, stop, seek, output volume, duration, and end-of-stream state.
- Add Dry, LoFi, and Master source buttons; disable absent or stale artifacts with an explanation.
- Add part preview: decode supported audio source or render/reuse a fingerprinted clean-MIDI piano preview through the shared preview service.
- Close lines, streams, coroutines, and file handles when switching source/project or exiting.
- Playback remains a monitor path and never writes or modifies canonical release artifacts.

## Tests

- Mix-setting validation, debounce/state reconciliation, mute/solo logic, reset, and stem-only re-mix.
- Build readiness, full fake pipeline stage ordering, progress/reuse, cancellation, worker down, renderer missing, model failure, and optional MP3 unavailable.
- Fake-player view-model tests and JVM player tests for state, seek bounds, EOF, format propagation, and cleanup.
- Compose UI tests cover source selection, disabled reasons, transport state, mix controls, and build errors.
- Manual direct-MIDI and transcribed-audio build; audition dry, LoFi, and master.

## Acceptance criteria

- Build Song produces or reuses the canonical lossless chain and a validated `master.wav`.
- Mix changes go through the engine service and do not re-render stems.
- Available output artifacts can be played, paused, sought, stopped, and switched safely.
- Missing dependencies/artifacts are explicit; no operation reports false success.

## Out of scope

- Low-latency live DAW monitoring, recording, plugins, automation curves, per-section mix automation, and destructive normalization.

