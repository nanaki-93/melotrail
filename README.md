# AI Music Workstation

Kotlin 2.0 / Spring Boot application for AI-powered music creation and production.
The project is intentionally kept as **one Gradle module**. The Python worker remains a separate process.

## Prerequisites

- JDK 21
- Python 3.10+ (only needed for the worker)
- `make` (optional; you can use Gradle directly)

## Run the application

From the project root:

```bash
make run
```

Then open:

```text
http://localhost:8080/
```

Do **not** open `src/main/resources/static/index.html` directly with `file://`. The frontend uses ES modules and API calls and must be served over HTTP by Spring Boot.

You can also run it without Make:

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

## Make targets

| Command | Purpose |
|---|---|
| `make build` | Build the application |
| `make test` | Run tests |
| `make check` | Run Gradle verification |
| `make run` | Start Spring Boot |
| `make cli-help` | Show CLI help |
| `make cli ARGS="..."` | Run CLI |
| `make worker` | Start Python worker |
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
