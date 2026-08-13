# Task 001 — Audio-to-MIDI Research Spike

## Goal

Select and prove one local, offline transcription solution for solo-piano WAV input before integrating transcription into the application.

## Why this task exists

The current arranger imports audio and generates supporting WAV stems directly. `PLAN_MIDI.md` changes the musical pipeline so audio must first become inspectable MIDI. Choosing a transcription engine without a controlled spike would couple the worker to a large dependency before its quality, licensing, and local runtime behavior are known.

## Mandatory workflow

1. Read `README.md`, `plan/AGENT_GUIDELINES.md`, and `plan/PLAN_MIDI.md`.
2. Inspect `worker/requirements.txt`, the worker command registry, the current MP3 conversion command, and existing audio fixtures.
3. Run `./gradlew test` and `python3 -m unittest discover -s worker/tests` before changing anything.
4. Record pre-existing failures separately. The current Python 3.14 environment is known to expose a `librosa`/`numba` cache failure; do not report it as a transcription regression.

## Dependencies

None. This is the first MIDI-first task.

## Deliverables

- A short research report under `plan/research/` containing:
  - engines evaluated;
  - exact versions and licenses;
  - offline/local operation confirmation;
  - supported Python versions;
  - installation and model download size;
  - CPU/RAM and elapsed time for the fixture;
  - note/timing/sustain observations;
  - the selected engine and reasons rejected alternatives were not selected.
- A minimal spike script under `tools/` that accepts one WAV input and one MIDI output. It must not use the HTTP worker or modify a song project.
- One small, legally usable solo-piano WAV fixture, or documented instructions for producing it locally without committing copyrighted source material.
- A generated MIDI result kept outside tracked source files unless it is deliberately added as a small test fixture.
- Reproducible setup instructions using a clean Python 3.12 virtual environment. Update broad Python compatibility claims only to versions actually verified.

## Evaluation requirements

Evaluate at least two viable offline approaches when practical. Score each on:

- recognizable pitch and onset timing;
- polyphonic piano support;
- repeated-note behavior;
- sustain-pedal handling;
- tempo preservation or derivation;
- valid Standard MIDI File output;
- deterministic inference for identical input/settings;
- local CPU support and optional accelerator requirements;
- dependency compatibility with the existing worker;
- commercial-use implications of code, model, and bundled assets.

The spike may resample internally if required by the selected model, but it must document that behavior and must not overwrite or rename the source WAV.

## Validation

Automated checks:

- The output exists, is non-empty, and has a valid MIDI header.
- A MIDI parser can open the file and find at least one note-on/note-off pair.
- Notes have positive durations and legal pitch/velocity values.
- Running the same input/settings twice produces equivalent musical events.

Manual checks:

- Inspect a piano-roll view or event listing.
- Audition the MIDI with an available local piano instrument if possible.
- Confirm the melody, chord movement, rough rhythm, and section duration are recognizable.

## Acceptance criteria

- One engine is explicitly selected for Task 002.
- `piano.wav -> piano.mid` works from a documented local command.
- The MIDI contains recognizable notes and timing, not merely a parseable empty file.
- Installation and execution work in the documented Python environment.
- Licensing is documented and compatible with personal use and possible later monetization.
- No application integration, project migration, or DSP change is included.

## Out of scope

- Full-song or multi-instrument transcription.
- Guitar, vocal, or drum transcription.
- Worker endpoints, Kotlin commands, MIDI cleanup, arrangement, or rendering.
- Fixing unrelated DSP or web functionality.

## Completion report

List evaluated engines, the selected engine/version, changed files, commands, automated results, manual musical observations, environment constraints, assumptions, and remaining transcription limitations.
