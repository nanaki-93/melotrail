# AI Music Workstation

Kotlin 2.0 / Spring Boot application for AI-powered music creation and production.
The project is intentionally kept as **one Gradle module**. The Python worker remains a separate process.

## Prerequisites

- JDK 21
- Python 3.10+ (only needed for the worker)
- `make` (optional; you can use Gradle directly)

## Run the application

The application has three independent processes:

1. Python worker — audio/AI processing on port `8081`
2. Kotlin/Spring API — application API on port `8080`
3. Python frontend server — static pages on port `3000`

For local development, run these in three terminals:

```bash
make worker
make run
make frontend
```

Then open:

```text
http://127.0.0.1:3000/index.html
```

You can also serve the frontend through Spring Boot at `http://localhost:8080/`.
Do not open `src/main/resources/static/index.html` directly with `file://`.

You can run Spring Boot without Make:

```bash
./gradlew bootRun
```

## CLI

Show CLI help:

```bash
make cli-help
```

Run the CLI with arguments:

```bash
make cli ARGS="--help"
make cli ARGS="<your CLI arguments>"
```

Equivalent Gradle command:

```bash
./gradlew cliRun --args="<your CLI arguments>"
```

Build a complete local arranger project (requires the Python worker):

```bash
./gradlew cliRun --args="build --project ./projects/demo --no-ai"
```

This keeps `analysis/`, `arrangement.json`, `stems/bass.wav`, and `mix/mix.wav`
inside the project, then writes lossless `repair.wav`, `lofi.wav`, and
`master.wav` under `output/`. Use `--output-dir <directory>` to choose another
output directory, or `--dry-run` to validate without changing files.

The build command is deterministic and local. `--no-ai` makes that choice
explicit; no model output is executed as code, commands, or paths.

### Optional solo-piano transcription

The worker exposes local Basic Pitch transcription only after its optional
runtime is installed in a separate Python 3.11 environment. It is intentionally
not part of the normal worker dependencies or test suite. See
[`worker/README.md`](worker/README.md) for setup and the Task 001 runtime gate.

```bash
make worker
make cli ARGS='transcribe --input ./recordings/verse.wav --output ./projects/song-001/midi/raw/A.mid --instrument piano'
```

### Deterministic MIDI cleanup

Clean raw piano transcription before later MIDI analysis. Defaults only remove
exact duplicates, notes shorter than 50 ms, and note-on velocities below 8;
they do not quantize expressive timing.

```bash
make cli ARGS='midi-clean --input ./projects/song-001/midi/raw/A.mid --output ./projects/song-001/midi/clean/A.mid'
make cli ARGS='midi-clean --input ./projects/song-001/midi/raw/A.mid --output ./projects/song-001/midi/clean/A.mid --quantize 1/16 --strength 0.4'
```

### MIDI-first project input

New arranger projects store an explicit PCM-24 render format and preserve each
original import under `source/`. Direct MIDI is cleaned before registration;
audio requires explicit transcription and produces both raw and clean MIDI.

```bash
make cli ARGS='project create ./projects/song-001 --sample-rate 44100 --channels 2'
make cli ARGS='part add ./projects/song-001 --id A --file ./inputs/verse.mid --role verse'
make cli ARGS='part add ./projects/song-001 --id B --file ./inputs/chorus.wav --role chorus --transcribe'
```

Version-1 projects remain readable. Their original `parts/` files are not
moved during migration; a v2 project is written only after every registered
part has a valid clean-MIDI reference.

### MIDI analysis and sound-library licenses

For v2 projects, `part analyze` reads the registered clean MIDI locally and
writes a distinct versioned musical analysis under `analysis/`; v1 projects
continue to use the existing audio worker analysis.

```bash
make cli ARGS='part analyze ./projects/song-001 --id A'
make cli ARGS='licenses ./projects/song-001 --commercial'
```

The starter library is rooted at `sounds/` (override only with
`MUSIC_SOUNDS_ROOT`). Its MIDI channels are human-readable one-based values:
drum channel 10 becomes MIDI API channel 9. See [`sounds/README.md`](sounds/README.md)
for the required local sample-copy setup after a fresh checkout.

### Global song-planning workflow

`arrange` first creates standalone, reviewable `song_plan.json` and
`section_variations.json` artifacts for the whole user-controlled structure.
It requires a v2 MIDI-first project with a versioned MIDI analysis for every
part. The plan contains only section purpose, energy, logical instrument
progression, transition intent, and ending behavior; the variation artifact
adds stable repeated-section identities plus bounded roles/densities. Neither
artifact contains notes, paths, renderer settings, or executable behavior.

```bash
# Every MIDI-first part must have musical metadata first.
make cli ARGS='part analyze ./projects/song-001 --id A'

make cli ARGS='arrange --project ./projects/song-001 --planner deterministic --instruments piano,bass,pad --style "warm lo-fi"'
# Inspect song_plan.json and section_variations.json before a later detailed-arrangement stage.
```

Expand those reviewed artifacts into the MIDI-first version 3 `arrangement.json`.
The deterministic planner writes an approved document directly; Qwen writes only
`arrangement.draft.json`, which must be approved explicitly. Version 3 contains
bounded instrument roles and pattern controls, never MIDI notes or render paths.

```bash
make cli ARGS='arrange-detail --project ./projects/song-001 --planner deterministic'
make cli ARGS='arrange-detail --project ./projects/song-001 --planner qwen'
make cli ARGS='approve --project ./projects/song-001'
```

Existing version 1/2 arrangements remain available to the current audio
renderer. MIDI generation from version 3 roles is introduced by later tasks.

The existing `render`/`build` workflow continues to use its compatible
`arrangement.json` artifacts. A detailed MIDI-first arrangement is a later,
separate stage; generating a global song plan never approves or overwrites one.

### Optional local Qwen planning

Qwen is optional and makes exactly one strict JSON-only request for the global
song plan. Start an OpenAI-compatible LM Studio server, then configure its
endpoint and model:

```bash
export LM_STUDIO_CHAT_COMPLETIONS_URL=http://127.0.0.1:1234/v1/chat/completions
export QWEN_MODEL=qwen
./gradlew cliRun --args="arrange --project ./projects/demo --planner qwen --structure 'A A B B' --instruments piano,bass"
```

For repeatable local runs, use `--planner deterministic`. Qwen responses are
parsed as strict JSON and validated against the exact structure, generated
instance identities, MIDI analysis bounds, and instrument/enum allow-lists.

## Testing

```bash
make test                              # Kotlin unit/integration tests
python3 -m unittest discover -s worker/tests
make build                             # Full Gradle build
```

The end-to-end smoke path is `build --project … --no-ai`; it requires the
worker running in another terminal (`make worker`). All processing stages use
WAV/PCM-24 intermediates and preserve the source sample rate and channels.

## Make targets

| Command | Purpose |
|---|---|
| `make build` | Build the application |
| `make test` | Run tests |
| `make check` | Run Gradle verification |
| `make run` | Start Spring Boot |
| `make cli-help` | Show CLI help |
| `make cli ARGS="..."` | Run CLI |
| `make worker` | Start standalone Python worker on `:8081` |
| `make frontend` | Start Python frontend server on `:3000` |
| `make python-install` | Install Python dependencies |
| `make clean` | Clean Gradle outputs |

## Project structure

```text
ai-music-workstation/
├── src/
│   ├── main/
│   │   ├── kotlin/ai/music/workstation/
│   │   │   ├── audio/          # Audio processing
│   │   │   ├── cli/            # CLI
│   │   │   ├── dsp/            # DSP effects
│   │   │   ├── model/          # Domain model
│   │   │   ├── queue/          # Worker queue/client
│   │   │   ├── worker/         # Worker integration
│   │   │   └── server/         # Spring Boot API
│   │   └── resources/
│   │       ├── static/         # Web application
│   │       └── application.properties
│   └── test/                    # All Kotlin tests
├── worker/                      # Python worker (separate process)
├── Makefile
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

## Configuration

Environment variables supported by the server include:

- `SERVER_PORT` (default `8080`)
- `SERVER_HOST` (default `localhost`)
- `WORKER_BASE_URL` (default `http://localhost:8081`)
- `PROJECT_STORAGE_PATH` (default `data/projects`)
- `AUDIO_STORAGE_PATH` (default `data/audio`)

## Technology

- Kotlin 2.0.x / JVM 21
- Spring Boot 3.5.x
- Kotlinx Serialization and Coroutines
- Python worker

## License

MIT


## Local development architecture

The Python worker is now a standalone HTTP service. Kotlin does not spawn or
stop Python processes.

Python exposes one endpoint per operation:

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Worker health |
| POST | `/analyze` | Analyze audio |
| POST | `/apply_dsp` | Python DSP integration |
| POST | `/repair` | Repair audio |
| POST | `/master` | Master audio |
| POST | `/mp3_convert` | Convert MP3 to WAV |
| POST | `/transcribe` | Transcribe solo piano to MIDI |
| POST | `/midi-clean` | Conservatively clean a MIDI file |

The Kotlin worker client maps each `WorkerCommand` directly to its endpoint.
There is no generic `/api/worker/command` request envelope between Kotlin and
Python anymore.

### Run locally

Use three terminals:

```bash
make worker
make run
make frontend
```

Then open `http://127.0.0.1:3000/index.html`.

The services use:

- Python worker: `127.0.0.1:8081`
- Kotlin/Spring API: `127.0.0.1:8080`
- Static frontend: `127.0.0.1:3000`

The frontend development server is only a static file server. API requests are
sent to the Kotlin server on port 8080.

Install Python dependencies with:

```bash
make python-install
```
