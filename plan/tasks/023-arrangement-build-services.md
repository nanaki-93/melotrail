# Task 023 — Arrangement, Mix, and Build Application Services

## Goal

Extract typed arrangement, generation, rendering, mixing, and release orchestration so CLI and Compose can invoke the same engine operations with structured progress and errors.

## Dependencies

- Task 022 application boundary and project snapshots.
- Existing planners, validators, MIDI generators, renderer, mixer, worker client, DSP, and release checks.

## Requirements

- Add typed requests/results for global+detailed arrangement generation, draft preview/approval, mix loading/apply, and full song build.
- Preserve deterministic and Qwen planner behavior. Qwen must always produce a validated draft requiring explicit approval.
- Expose an arrangement snapshot with section identity, part, purpose, energy, active instruments, roles/densities, transitions, duration, approval, and staleness.
- Add structured progress events instead of printing inside engine orchestration. CLI renders these events as text.
- Add structured application errors for prerequisites, validation, worker, model, renderer, artifact, and I/O failures.
- Add one service-level mutation lock per project and cooperative cancellation at stage boundaries.
- Make full Build Song generate/reuse all required instrument MIDI and transitions before render, then mix, repair, optional LoFi, master, optional MP3, and release metadata.
- Persist logical per-instrument gain/pan/mute/solo settings in versioned `mix/settings.json`; re-mix compatible stems without re-rendering them.
- Preserve PCM-24, actual sample rate/channels, peak safety, atomic publication, source hashes, inspectable intermediates, and MP3-final-only rules.
- Migrate arrangement/build CLI handlers to the services and retain CLI commands.

## Tests

- Deterministic and fixture-backed Qwen planning, draft approval, and stale-artifact detection.
- Required MIDI generation selection from the approved arrangement, including transitions.
- Build stage ordering, progress, resume/reuse, cancellation boundary, and exact failure category.
- Mix settings validation/persistence and stem-only re-mix.
- Direct service and CLI adapter produce equivalent canonical arrangement, stem, mix, and release artifacts with fakes.
- Existing full Kotlin tests/build remain green; no live model or renderer is required.

## Acceptance criteria

- No Compose screen will need to reproduce a CLI command sequence.
- CLI does not call private orchestration methods that bypass the shared services.
- Build Song either produces a validated `output/master.wav` plus `release.json` or reports the exact failed prerequisite/stage.
- Qwen approval cannot be bypassed by either adapter.

## Out of scope

- Compose UI, JVM playback, DSP changes, arbitrary mix automation, and general job queues.

