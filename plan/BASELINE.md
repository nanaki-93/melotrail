# Baseline and Guardrails

## Repository state — 2026-08-13

The application is a single Kotlin 2.0 / Spring Boot Gradle module with a
separate Python HTTP worker. The repository also contains a static frontend,
CLI, audio codecs/DSP, and Kotlin tests. No arranger implementation exists
yet.

Key entry points:

- Kotlin/Spring API: `src/main/kotlin/ai/music/workstation/server/Server.kt`
- CLI: `src/main/kotlin/ai/music/workstation/cli/CliMain.kt`
- Pipeline orchestration: `src/main/kotlin/ai/music/workstation/cli/AudioPipeline.kt`
- Python worker: `worker/main.py`
- Worker processing: `worker/commands/{analyze,dsp,repair,mastering,mp3_convert}.py`

The Gradle module uses Kotlin/JVM 21, Spring Boot, Kotlin serialization, and
OkHttp. The worker uses Python with `soundfile`, NumPy, SciPy, and librosa.

## Current audio pipeline

Run the worker before running the CLI:

```bash
make worker
make cli ARGS="--input PianoSong.mp3 --output output/master.wav"
```

The CLI's default processing flow is:

```text
input MP3 (when applicable)
  -> worker /mp3_convert -> temporary PCM_24 WAV
  -> worker /analyze
  -> worker /repair (DC offset and conservative clip repair)
  -> worker /master
  -> requested output WAV
```

LoFi is deliberately opt-in rather than part of the default. Adding
`--stages analyze,repair,lofi,master` invokes the existing Kotlin `DSPChain`
and writes a temporary WAV before worker mastering. The pipeline creates an
isolated temporary working directory and only writes the final master to the
user-specified output path.

The standalone worker exposes `GET /health` and these command-specific POST
endpoints: `/analyze`, `/apply_dsp`, `/repair`, `/master`, and `/mp3_convert`.
The Kotlin worker client maps its typed commands directly to those endpoints.

## Audio format observations

- MP3 input is decoded to a lossless PCM_24 WAV before subsequent processing.
- Repair, optional Python DSP, and mastering write PCM_24 WAV.
- The worker reads and writes the source sample rate and channel arrangement;
  it does not resample or force stereo.
- Kotlin `AudioBuffer` stores interleaved samples and computes length in
  frames, using the buffer's actual channel count.
- MP3 export is not implemented in the Kotlin exporter; it reports that an
  encoder library is required. Treat MP3 as final-export-only when it is added.

## Arranger placement

Add arranger code under the existing package root without moving current
pipeline code:

- `model/`: small project, part, structure, analysis, and arrangement data
  models.
- `arrangement/`: `ArrangementPlanner`, deterministic planner, validation,
  and later local-AI planner client.
- `cli/`: commands that import parts, define structure, plan, render, and
  invoke the existing processing path.
- `worker/`: analysis and rendering helpers only; the worker must not execute
  AI-generated code, commands, or paths.

Keep planned project data local to a project directory:

```text
project.json
parts/*.wav
analysis/*.json
arrangement.json
stems/*.wav
mix/*.wav
output/master.wav
```

## Validation results

Commands run on 2026-08-13:

```bash
./gradlew test
./gradlew build
python3 -m compileall -q worker
./gradlew cliRun --args='--help'
./gradlew cliRun --args='--input PianoSong.mp3 --output build/baseline-smoke.wav --dry-run'
```

- Python worker compilation succeeds.
- Kotlin test suite runs 136 tests; 135 pass and one pre-existing test fails:
  `DSPChainTest > bit depth reduction should quantize()` at
  `src/test/kotlin/ai/music/workstation/dsp/DSPChainTest.kt:66`.
- `./gradlew build` consequently fails in `:test` with that same assertion.
- The CLI help text is printed, but `CliParser.parse()` throws after printing
  help, so the help smoke command exits with status 1. This is documented as a
  pre-existing CLI behavior and is out of scope for this baseline task.
- The non-processing CLI dry-run smoke check succeeds and does not create
  `build/baseline-smoke.wav`.

## Guardrails

- Do not overwrite source audio.
- Preserve each input's actual sample rate and channel count; operate on
  frames for multi-channel data.
- Keep intermediate audio lossless. MP3 is only a separate final export.
- Treat AI output only as validated arrangement data; never execute generated
  code, commands, or arbitrary paths.
- Retain a deterministic planner as the fallback and test oracle.
