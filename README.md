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

### Optional local Qwen planning

Qwen is optional and only plans validated `arrangement.json` data. Start an
OpenAI-compatible LM Studio server, then configure its endpoint and model:

```bash
export LM_STUDIO_CHAT_COMPLETIONS_URL=http://127.0.0.1:1234/v1/chat/completions
export QWEN_MODEL=qwen
./gradlew cliRun --args="arrange --project ./projects/demo --planner qwen --structure 'A A B B' --instruments source,bass"
```

For repeatable local runs, use `--planner deterministic` (or the `build`
command with `--no-ai`) instead. Qwen responses are parsed as strict JSON and
validated against the project structure and allowed instruments.

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
