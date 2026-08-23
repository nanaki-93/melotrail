# Melotrail

Kotlin 2.0 music workstation. The root Kotlin module contains shared
application services; the Compose Desktop product lives in the `:desktopApp`
Gradle subproject. The Python worker remains a separate process.

## Prerequisites

- JDK 21
- Python 3.10+ (only needed for the worker)
- `make` (optional; you can use Gradle directly)

## Compose Desktop workspace

The local desktop workspace uses typed Kotlin application services. It launches
in-process:

```bash
make desktop
```

On the first Melotrail launch, a missing desktop preference is migrated from
the former `ai.music.workstation` preference node and retained there; the new
value is then stored under the Melotrail node. Project files are not rewritten
as part of this compatibility migration.

Equivalent Gradle command: `./gradlew :desktopApp:run`.

Use **New Project** or **Open Project**, then follow the desktop happy path:
**Melody Parts**, which automatically prepares its supported stages, optional **AI Fix** and
**Enhance**, **Structure**, **Arrangement**, boundary-only **Cohesion**, deterministic
**Critic**, optional **Full-Song Enhance**, then deterministic **Humanization** before
using **Build song**. Project files remain canonical: `project.json`, plans and arrangements,
generated MIDI, stems, mixes, and release artifacts are all kept under the
chosen project directory. Desktop settings retain only the last successfully
opened project path; they never store project or audio data.

The top navigation keeps the guided stages visible: **Setup**, **Project**, **Melody Parts**,
**Structure**, **Arrange**, and **Mix & Master**. **Library**, **Video Preview**,
**Export**, and **Settings** are available from the labelled **More** menu. The
workspace derives the current stage, prerequisites, and next safe action from
validated artifacts; it does not add a workflow-status navigation row. Project
open/import/build results are shown in a dismissible workspace banner so
failures and recovery actions are never hidden in a panel.

Each workspace page keeps one current workflow action and its blocked-state
recovery visible. Labelled **More options** disclosures contain alternate
workflow pages, planner/instrument choices, listening/build settings, release
filename/format choices, library filters, timeline evidence, and runtime/build
details. These disclosures do not change project data or hide inspectable
evidence.

The worker is required for audio import, repair, mastering, and optional MP3
export. Start it with `make worker`. Rendering and MIDI preview additionally
require a configured local SFZ renderer (`SFZ_RENDERER_PATH` or `sfizz_render`
on `PATH`). The app reports missing/disconnected dependencies and stale
artifacts without modifying the project. Local bounded diagnostic logs are
written under `~/.melotrail/logs/`; they contain operation and
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
drag **Melotrail** to Applications (or another local folder),
and launch it there. The package includes its Java runtime: Gradle and the
repository working directory are not required to create or open a project.

The package does not bundle the local SFZ samples, renderer, Python worker, or
optional transcription runtime. Use the shell **More** menu, then **Settings**, to choose the
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

A hosted Git repository rename is intentionally not part of this local change.
With separately authorized provider access, rename the hosted repository and
then update a local checkout with `git remote set-url origin <new-url>`.

### Desktop workflow and local prerequisites

Use the desktop app as a guided sequence: create or open a project, explicitly save its
musical Setup (name, key, tempo, meter, Lo-fi profile, and mood), then import MIDI or an eligible WAV/MP3
source, convert audio to MIDI when needed, clean then deterministically normalize MIDI, keep it or review an
optional bounded AI-fix and per-track Enhance drafts, select optional Lo-fi Feel, analyze MIDI,
then save the structure, generate/review an arrangement, then generate/review boundary-only
Cohesion, run the deterministic Critic, explicitly approve, bypass, or record a no-op for
Full-Song Enhance, then select deterministic Humanization or bypass before building and
auditioning validated artifacts.

- Direct MIDI is preserved under `source/` and copied as immutable evidence under `midi/raw/`. Worker **Clean MIDI** repairs invalid events; Kotlin **Normalize MIDI** then publishes deterministic `midi/normalized/` evidence and a hash-bound report. Melotrail records detected source-key confidence; below its fixed gate, confirm the source key explicitly before **Transpose to project key** publishes a separate `midi/transposed/` artifact and report.
- WAV/WAVE and MP3 input is accepted only for the optional **solo-piano**
  transcription workflow. Do not use it to claim reliable editable MIDI from
  vocals, full mixes, or arbitrary polyphonic material. A successful audio
  transcription always preserves raw MIDI and immediately applies the bounded
  deterministic transcription cleanup profile before analysis can proceed.
- Inspection is the default and creates a measured `prepared/<part>/report.json`
  without changing the source. Safe cleanup requires explicit confirmation,
  keeps the original immutable, and can create `decoded.wav` and `clean.wav`
  under that part's `prepared/` directory.
- Audio-source preview can work without a renderer. MIDI preview and rendering
  require a valid local library with samples plus an executable SFZ renderer.
  The readiness panel names the missing dependency and its recovery action.

The project directory remains canonical. Its inspectable artifacts include
`source/`, `prepared/`, `midi/raw/`, `midi/clean/`, `midi/normalized/`, `midi/normalization/`, `analysis/`, `previews/`,
plans and arrangements, generated MIDI, `midi/humanized/` edit evidence, stems, `mix/`, and `output/`.
`output/master.wav` is the authoritative lossless release; MP3 is an optional
final conversion only. For worker, library, renderer, preview, and package
troubleshooting, use [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md).
For the direct-MIDI and eligible-audio routes, their validation, and the
required cleanup review, see [MIDI import process](docs/MIDI_IMPORT_PROCESS.md).
For the user-facing stage order, prerequisites, artifacts, and stale-artifact
recovery, see [Track process workflow](docs/TRACK_PROCESS_WORKFLOW.md).

### Workflow state and migration

Only the current canonical project schema v4 shape opens. Older and superseded
documents fail without conversion or writes. Missing creative setup is surfaced as a typed
setup requirement and is never inferred on open. Readiness comes from validated files and
available fingerprints, never a completion flag alone. Changes to source/raw MIDI, selected
MIDI, analysis, structure, arrangement, Cohesion, Critic, Full-Song Enhance, or selected
Humanization seed/config mark only their documented descendants stale. Stale artifacts remain
inspectable evidence; regenerate them instead of deleting, copying, or treating them as
release-ready.

## Testing

```bash
make test                              # Kotlin unit/integration tests
make worker-test                       # Python worker tests
make build                             # Full Gradle build
```

The automated tests use fakes or fixtures for worker, renderer, model, and
audio-device boundaries. All processing stages use WAV/PCM-24 intermediates
and preserve the source sample rate and channels.

## Make targets

| Command | Purpose |
|---|---|
| `make build` | Build the application |
| `make test` | Run tests |
| `make worker-test` | Run offline Python worker tests |
| `make check` | Run Gradle verification |
| `make desktop` | Start the Compose Desktop application |
| `make worker` | Start standalone Python worker on `:8081` |
| `make python-install` | Install Python dependencies |
| `make clean` | Clean Gradle outputs |

## Project structure

```text
melotrail/
├── src/
│   ├── main/
│   │   ├── kotlin/app/melotrail/
│   │   │   ├── application/    # Typed local use cases
│   │   │   ├── arrangement/    # Canonical project artifacts
│   │   │   ├── preparation/    # Safe import/cleanup boundaries
│   │   │   └── worker/         # Worker integration
│   └── test/                    # All Kotlin tests
├── desktopApp/                  # Compose Desktop product UI
├── worker/                      # Python worker (separate process)
├── Makefile
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

## Technology

- Kotlin 2.0.x / JVM 21
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

The `app.melotrail.worker` boundary owns the Kotlin command schemas, direct
endpoint/payload mapping, typed response/error mapping, and health/readiness.
Kotlin never starts or manages the Python process.

Install Python dependencies with:

```bash
make python-install
```
