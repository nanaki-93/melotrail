# AI Music Workstation

Kotlin 2.0 / Spring Boot application for AI-powered music creation and production.
The project is intentionally kept as **one Gradle module**. The Python worker remains a separate process.

## Prerequisites

- JDK 21
- Python 3.10+ (only needed for the worker)
- `make` (optional; you can use Gradle directly)

## Local API service

The Compose Desktop application is the supported product UI. The Spring process
is an optional local JSON API service; it does not serve a browser interface.
You can run it without Make:

```bash
./gradlew bootRun
```

## Compose Desktop workspace

The local desktop workspace uses the same typed Kotlin application services as
the CLI. It launches in-process and does not start Spring:

```bash
make desktop
```

Equivalent Gradle command: `./gradlew :desktopApp:run`.

Use **Create** or **Open project**, prepare/analyze parts, save the structure,
generate an arrangement, explicitly approve a Qwen draft, then use **Build
song**. Project files remain canonical: `project.json`, plans and arrangements,
generated MIDI, stems, mixes, and release artifacts are all kept under the
chosen project directory. Desktop settings retain only the last successfully
opened project path; they never store project or audio data.

The top navigation has five explicit destinations: **Project**, **Structure**,
**Arrange**, **Mix & Master**, and **Library**. Workflow-status badges report
progress but do not replace navigation. **Add Part** remains at the top of the
Parts panel, and project open/import/build results are shown in a dismissible
workspace banner so failures and recovery actions are never hidden in a panel.

The worker is required for audio import, repair, mastering, and optional MP3
export. Start it with `make worker`. Rendering and MIDI preview additionally
require a configured local SFZ renderer (`SFZ_RENDERER_PATH` or `sfizz_render`
on `PATH`). The app reports missing/disconnected dependencies and stale
artifacts without modifying the project. Local bounded diagnostic logs are
written under `~/.personal-ai-music-arranger/logs/`; they contain operation and
artifact metadata, not model responses or source content.

Transport shortcuts are Ctrl/Cmd+Space for play/pause, Ctrl/Cmd+Left/Right to
seek five seconds, and Ctrl/Cmd+K to stop. Structure rows provide keyboard
reachable earlier/later controls as an alternative to drag reordering.

### macOS package

On this development OS, produce a DMG with its bundled runtime using:

```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
```

The DMG is written under `desktopApp/build/compose/binaries/main/dmg/`. Open it,
drag **Personal AI Music Arranger** to Applications (or another local folder),
and launch it there. The package includes its Java runtime: Gradle, Spring, and
the repository working directory are not required to create or open a project.

The package does not bundle the local SFZ samples, renderer, Python worker, or
optional transcription runtime. Use the **Library** button to choose the
absolute folder containing the validated `sounds/` pack; that preference is
stored separately from project data. For a terminal launch, a validated
`MUSIC_SOUNDS_ROOT` is an alternative and takes precedence over the chooser.
Start the worker separately only for operations that need it, and configure the
renderer before MIDI preview or rendering. See
[`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md) for recovery steps.

This is an unsigned local macOS package—code signing and notarization are
intentionally not configured. Windows and Linux packages must be built and
tested natively on those operating systems before they can be claimed as
supported.

### Desktop workflow and local prerequisites

Use the desktop app as a guided sequence: create/open a project, import MIDI
or an eligible WAV/MP3 source, inspect/prepare it, clean/analyze MIDI, save the
structure, generate/review an arrangement, then build and audition validated
artifacts. The app never requires Spring.

- Direct MIDI is preserved under `source/` and cleaned before analysis.
- WAV/WAVE and MP3 input is accepted only for the optional **solo-piano**
  transcription workflow. Do not use it to claim reliable editable MIDI from
  vocals, full mixes, or arbitrary polyphonic material.
- Inspection is the default and creates a measured `prepared/<part>/report.json`
  without changing the source. Safe cleanup requires explicit confirmation,
  keeps the original immutable, and can create `decoded.wav` and `clean.wav`
  under that part's `prepared/` directory.
- Audio-source preview can work without a renderer. MIDI preview and rendering
  require a valid local library with samples plus an executable SFZ renderer.
  The readiness panel names the missing dependency and its recovery action.

The project directory remains canonical. Its inspectable artifacts include
`source/`, `prepared/`, `midi/raw/`, `midi/clean/`, `analysis/`, `previews/`,
plans and arrangements, generated MIDI, stems, `mix/`, and `output/`.
`output/master.wav` is the authoritative lossless release; MP3 is an optional
final conversion only. For worker, library, renderer, preview, and package
troubleshooting, use [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md).

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

This keeps `analysis/`, `arrangement.json`, `stems/bass.wav`, and the lossless
`mix/dry.wav` and `mix/repaired.wav` intermediates inside the project, then
writes the PCM-24 `master.wav` and `release.json` under `output/`. Add `--lofi`
to create `mix/lofi.wav` and master that explicit input; otherwise the repaired
dry mix is mastered. Use `--output-dir <directory>` to choose another output
directory, or `--dry-run` to validate without changing files. Master validation
requires the same sample rate and channel count and permits at most 50 ms of
worker latency or tail difference.

The build command is deterministic and local. `--no-ai` makes that choice
explicit; no model output is executed as code, commands, or paths.

### Optional final MP3 export

`output/master.wav` is always the authoritative lossless release artifact. MP3
is an explicit final conversion only; no repair, mixing, or mastering stage
writes MP3. With the optional local `lameenc` dependency installed, either
request it during a build or export an already validated master:

```bash
make cli ARGS='build --project ./projects/song-001 --no-ai --mp3 --mp3-bitrate 320'
make cli ARGS='export-mp3 --input ./projects/song-001/output/master.wav --output ./projects/song-001/output/song.mp3 --bitrate 320'
```

Supported bitrates are 128, 160, 192, 256, and 320 kbps. The exporter checks
that the input is `master.wav` in a RIFF/WAVE container, writes `song.mp3`
atomically, and rejects WAV data disguised as MP3. If `lameenc` is absent, a
`build --mp3` still completes with its valid master WAV; the standalone export
command reports that the explicitly requested MP3 could not be produced.

### LoFi A/B measurement

Compare two WAV files without changing either input. The default requires equal
sample rate, channel count, PCM format, and timeline. `--align` is diagnostic
only: it compares the shared frame range and never resamples.

```bash
make cli ARGS='compare ./projects/song-001/mix/dry.wav ./projects/song-001/mix/lofi.wav'
make cli ARGS='compare ./projects/song-001/mix/dry.wav ./projects/song-001/mix/lofi.wav --json'
```

The report uses a changed-frame tolerance of `1e-6` and Hann-windowed 2048-point
FFTs with a 1024-frame hop for deterministic spectral centroid and band-energy
metrics.

### Optional solo-piano transcription

The worker exposes local Basic Pitch transcription through its unified Python
3.11 environment. After selecting Python 3.11 (for example, with `pyenv local
3.11`), `make worker` installs and starts every worker capability. See
[`worker/README.md`](worker/README.md) for setup and runtime verification.

```bash
make worker
make cli ARGS='transcribe --input ./recordings/verse.wav --output ./projects/song-001/midi/raw/A.mid --instrument piano'
```

### Deterministic MIDI cleanup

Clean raw piano transcription before later MIDI analysis. Requests use cleanup
contract version 2. The default `conservative` profile removes only exact
duplicates, notes shorter than 50 ms, and note-on velocities below 8; it
preserves expressive timing, pedal controls, orphan note-offs, and retriggers.

`transcription-safe` additionally removes orphan note-offs and redundant CC64
pedal values, ends same-channel/pitch retriggers at the next start, and limits
retained velocity outliers to 12–120. `tighten-timing` includes those repairs
and requires an explicit `1/4`, `1/8`, `1/16`, or `1/32` grid plus a strength
strictly greater than 0.0 and at most 1.0. It is the only profile that can
quantize. Every response reports the profile, before/after note and event
counts, and each applied-change count.

```bash
make cli ARGS='midi-clean --input ./projects/song-001/midi/raw/A.mid --output ./projects/song-001/midi/clean/A.mid'
make cli ARGS='midi-clean --input ./projects/song-001/midi/raw/A.mid --output ./projects/song-001/midi/clean/A.mid --profile transcription-safe'
make cli ARGS='midi-clean --input ./projects/song-001/midi/raw/A.mid --output ./projects/song-001/midi/clean/A.mid --profile tighten-timing --quantize 1/16 --strength 0.4'
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

### Optional structured arrangement critique

Critique an approved version-3 arrangement once before rendering. The deterministic
critic is the default and creates an unchanged review draft without LM Studio.
It preserves the exact pre-critic approved JSON as `arrangement_v1.json`, writes
only `arrangement.draft.json`, and still requires explicit approval. A Qwen critic
may modify at most four sections and only complete existing instrument plans or
transition plans; any replacement still has to satisfy the reviewed
song plan and the complete normal v3 validator.

```bash
make cli ARGS='critic --project ./projects/song-001 --planner deterministic'
make cli ARGS='preview --project ./projects/song-001'
make cli ARGS='approve --project ./projects/song-001'
```

The critic receives only reviewed plan metadata, path-free MIDI analyses, the
validated arrangement, logical allow-lists, and compact section metrics. It never
receives source audio, file paths, renderer settings, commands, or executable data.

Existing version 1/2 arrangements remain available to the legacy audio
renderer. Version-3 generation supports bass, drums, pad, strings, and
transitions before full stem rendering.

Generate bounded, registry-mapped drum MIDI from an approved version-3
arrangement. The current generator supports 4/4 and 3/4 only and writes an
inspectable full-timeline artifact before any drum rendering.

```bash
make cli ARGS='generate drums --project ./projects/song-001'
make cli ARGS='generate pad --project ./projects/song-001'
make cli ARGS='generate strings --project ./projects/song-001'
```

Strings are written separately to `midi/generated/strings.mid`. Their roles are
bounded deterministic harmony, long notes, climax reinforcement, or a strictly
confidence- and source-space-gated simple countermelody; they never accept raw notes.

### Deterministic MIDI transitions

Generate the inspectable `midi/generated/transitions.mid` boundary artifact
after drums and pads. The current approved v3 `bridge` intent becomes a
bounded MIDI `build`; legacy `crossfade` remains an audio-renderer behavior and
adds no MIDI notes. The artifact records inserted bars in its timeline so later
MIDI/stem assembly can apply the offset once.

```bash
make cli ARGS='generate transitions --project ./projects/song-001'
```

The engine only uses deterministic drum-fill, bass-walk, and pad-sustain
gestures. It rejects cymbal transitions: the starter `sounds/` drum map has no
licensed cymbal sample, and it will never substitute a clap or hi-hat.

### Piano + bass quality gate

The narrow quality gate renders only source piano and generated bass from an
approved MIDI-first version-3 arrangement. It creates/reuses inspectable
`midi/generated/piano.mid`, `midi/generated/bass.mid`, `stems/piano.wav`,
`stems/bass.wav`, `mix/dry.wav`, and `quality-gate.json`. It intentionally does
not invoke transitions, repair, LoFi, mastering, or MP3 export.

```bash
make cli ARGS='quality-gate --project ./projects/song-001'
```

It requires the configured local SFZ renderer (for example
`SFZ_RENDERER_PATH=/path/to/sfizz_render`). The checked-in automated test uses
a fake renderer; the real command remains a manual listening gate.

The `mix` command preserves its legacy arrangement behavior. For a version-3
arrangement it publishes the already rendered `mix/dry.wav` as `mix/mix.wav`;
run `render` first. `build` renders or reuses version-3 stems automatically
before repair, optional LoFi, mastering, and optional MP3 export.

### Render all approved stems and the dry reference mix

After generating the active MIDI tracks (including transitions when a bridge
inserts bars), render the approved detailed arrangement to project-format
PCM-24 WAV stems and `mix/dry.wav`:

```bash
make cli ARGS='render --project ./projects/song-001'
```

The command requires the configured local SFZ renderer and never invokes
repair, LoFi, mastering, or MP3 export. It writes `stem-render.json` with
input/artifact fingerprints and the one uniform peak-safety gain applied to
the dry mix. A later `build` reuses these artifacts when their fingerprints
still match.

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
.venv/bin/python -m unittest discover -s worker/tests
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
| `make check-legacy-frontend` | Reject reintroduced browser-frontend files/references |
| `make run` | Start Spring Boot |
| `make desktop` | Start the Compose Desktop application |
| `make cli-help` | Show CLI help |
| `make cli ARGS="..."` | Run CLI |
| `make worker` | Start standalone Python worker on `:8081` |
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
| POST | `/mp3_export` | Export validated final WAV master to MP3 |
| POST | `/mp3_convert` | Convert MP3 to WAV |
| POST | `/transcribe` | Transcribe solo piano to MIDI |
| POST | `/midi-clean` | Conservatively clean a MIDI file |
| POST | `/inspect-input` | Validate and measure one MIDI/WAV/MP3 input without changing it |
| POST | `/cleanup` | Apply explicitly selected conservative WAV cleanup operations |

The Kotlin worker client maps each `WorkerCommand` directly to its endpoint.
There is no generic `/api/worker/command` request envelope between Kotlin and
Python anymore.

Install Python dependencies with:

```bash
make python-install
```
