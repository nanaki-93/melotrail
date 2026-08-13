# Compose Desktop UI Plan

## Goal

Add a local Kotlin Compose Desktop application on top of the existing arranger without creating a second implementation of project, arrangement, MIDI, rendering, mixing, or mastering behavior.

The CLI remains supported as an adapter and automation interface. The reusable Kotlin application services become the engine used by both entry points:

```text
                     application services
                    /                    \
             CLI adapter             Desktop adapter
                    \                    /
              domain + stores + worker + DSP
```

The desktop app must call typed Kotlin services in-process. It must not launch CLI commands, parse CLI output, call the Spring HTTP API, or reimplement file and audio rules in composables.

## Repository findings

- The project uses Kotlin/JVM 2.0.0 and JDK 21 in one root Gradle module.
- `ArrangementProjectCommands.kt` currently contains most arranger use cases, argument parsing, dependency construction, progress text, and presentation in one object of roughly 2,000 lines.
- The domain, stores, MIDI analysis/generation, stem renderer/mixer, worker client, DSP, and release validation already exist and should be reused.
- V2 `project.json` stores parts and structure, but the CLI does not currently expose an atomic structure-update operation.
- MIDI analysis already supplies the bars, duration, and inferred key needed by the parts list.
- A version-3 detailed arrangement supplies section identities, active instruments, energy, roles, and transitions needed by the timeline.
- `AudioPlayer` is an interface only; `PlaybackController` does not yet load or mix files and is not ready for the desktop UI.
- Version-3 `build` renders existing generated MIDI but does not itself generate every selected instrument. The UI needs a shared high-level build orchestrator, not a UI-only sequence.
- `./gradlew test` passed before this plan was written on 2026-08-14.

## Product scope

### MVP workspace

The first desktop release is one project workspace with these functional regions:

1. Project header: open/create project, song name, worker/renderer readiness, Build Song.
2. Parts: source filename, role, bars, key, analysis state, edit metadata, preview, import MIDI, import audio.
3. Structure: section chips with stable occurrence labels such as A1/A2/B1, add, duplicate, remove, and drag reorder.
4. AI arrangement: deterministic/Qwen planner selector, style prompt, five bounded instruments, generate, validate, review, approve.
5. Song timeline: proportional sections and one lane per logical instrument; selected-section details show role, density/energy, and transitions.
6. Mix and transport: gain, pan, mute, solo, dry/LoFi/master selection, play/pause/stop, seek, and current time.
7. Operation status: stage progress, current artifact, actionable error, and retained diagnostic paths.

### Visual direction

Use `plan/UI.png` as the visual direction: dark charcoal surfaces, teal primary actions, restrained per-instrument colors, compact cards, and a dense desktop layout. Preserve the simpler workflow of the text mockup. The travel image, location/weather, video concept, and scene-generation controls in the reference image are not part of this music engine and are excluded.

Start with a three-pane layout at wide widths and collapse to a scrollable two/one-column layout at smaller window sizes. Target 1440×900, remain usable at 1100×720, and do not require full-screen mode.

## Architecture

```text
desktopApp (Compose Desktop JVM application)
  App / theme / workspace composables
  WorkspaceViewModel
  DesktopFileDialogs
  JvmAudioPlayer
                |
                | typed requests, results, snapshots, progress events
                v
root engine module
  ProjectApplicationService
  ArrangementApplicationService
  MixApplicationService
  BuildApplicationService
                |
       +--------+---------+----------------+
       |                  |                |
  domain/planners    project stores    worker/render/DSP
       ^
       |
  CLI parsers + CLI presenters
```

Create `desktopApp` as a JVM application subproject depending on the existing root engine module. This keeps Compose and packaging dependencies out of the CLI/server runtime and follows JetBrains' current recommendation to separate runnable platform apps from shared logic. Do not split the existing engine into many new modules during this UI work.

### Application-service rule

Application services own orchestration and side effects. CLI and desktop code may:

- parse or collect user input;
- call one typed service operation;
- render the result/progress;
- request an explicit follow-up operation such as approval.

They may not directly write `project.json`, copy sources, generate MIDI, invoke the worker, render stems, or construct release metadata.

Use small services instead of a generic command bus:

```kotlin
interface ProjectApplicationService {
    fun open(root: Path): ProjectSnapshot
    fun create(request: CreateProjectRequest): ProjectSnapshot
    suspend fun importPart(request: ImportPartRequest, progress: ProgressSink): ProjectSnapshot
    suspend fun analyzePart(root: Path, partId: String, progress: ProgressSink): ProjectSnapshot
    fun updatePart(request: UpdatePartRequest): ProjectSnapshot
    fun saveStructure(root: Path, partIds: List<String>): ProjectSnapshot
}

interface ArrangementApplicationService {
    suspend fun generate(request: GenerateArrangementRequest, progress: ProgressSink): ArrangementSnapshot
    fun approve(root: Path): ArrangementSnapshot
    fun load(root: Path): ArrangementSnapshot
}

interface MixApplicationService {
    fun load(root: Path): MixSnapshot
    suspend fun apply(request: ApplyMixRequest, progress: ProgressSink): MixSnapshot
}

interface BuildApplicationService {
    suspend fun build(request: BuildSongRequest, progress: ProgressSink): BuildResult
}
```

Names may be adjusted during extraction, but requests/results must remain UI-agnostic and must not contain Compose types or CLI strings.

### Project snapshot

Expose a read model assembled from the canonical project files and artifacts:

```text
ProjectSnapshot
  root, name, render format
  PartSummary[]
    id, role, source name/type, analysis status, bars, duration, key
  structure[]
    index, part id, occurrence, instance id, duration
  readiness
    worker, renderer, analyses, plan, approval, generated MIDI, stems, mixes, master
```

The files remain the source of truth. Refresh the snapshot after every successful mutation; do not maintain a desktop-only project database.

### Long-running work and errors

- Execute file, worker, rendering, and DSP operations on `Dispatchers.IO`.
- Keep Compose state updates on the UI dispatcher.
- Allow one mutating operation per project at a time with a service-level mutex.
- Emit structured progress with operation, stage index/count, message, and optional artifact path.
- Cancellation is cooperative at safe stage boundaries for the MVP. Never interrupt an atomic file replacement.
- Convert known validation, dependency, worker, model, render, and I/O failures to structured application errors while preserving the cause for logs.
- Never report success until output validation has completed.

## Interaction contracts

### Open/create

- Opening selects a directory containing `project.json` and validates it before replacing the current workspace.
- Creating selects an empty/new directory plus sample rate and channel count; PCM-24 remains fixed.
- Invalid projects stay unopened and show a specific validation error.

### Import and edit parts

- Import MIDI preserves `source/`, runs MIDI cleanup, registers the part, and optionally analyzes it.
- Import WAV/MP3 requires transcription, then cleanup and analysis. Show worker/model prerequisites before starting.
- Part IDs are immutable after import in the MVP. Edit changes the musical role only; file rename/removal is deferred to avoid unsafe cascading edits.
- MIDI preview renders the clean MIDI through the registered piano instrument into a fingerprinted preview WAV. Audio-source preview uses a decoded local source when supported.

### Structure

- Dragging changes only an in-memory draft.
- Drop commits the complete ordered part-ID list atomically through `saveStructure`.
- Duplicate, insert, and remove use the same operation.
- Reject empty structure when generating/building, and reject unknown part IDs at the service boundary.
- Changing structure marks existing plans, generated MIDI, stems, mixes, and release outputs as stale; it does not silently delete them.

### Arrangement

- Piano is mandatory source material and appears checked but disabled.
- Other instruments are chosen only from `bass`, `drums`, `pad`, and `strings`.
- Generate Arrangement creates the global song plan and variations, then the detailed arrangement.
- Deterministic mode produces an approved arrangement. Qwen produces a validated draft and requires explicit review/approval.
- The timeline reads validated artifacts only. It does not infer missing plans in the UI.
- The UI never exposes arbitrary model JSON, paths, notes, commands, or instrument names as executable input.

### Build Song

The shared build operation performs or reuses the exact required stages:

```text
validate project/readiness
 -> generate required bass/drums/pad/strings MIDI
 -> generate transitions when required
 -> render/reuse stems
 -> apply persisted mix settings and write dry mix
 -> repair
 -> optional LoFi
 -> master.wav
 -> optional song.mp3
 -> release.json
```

If an approved arrangement or required analysis is missing, the operation stops with an actionable prerequisite. It must not create an implicit Qwen approval.

### Mix and playback

- Persist the five bounded track settings in a versioned `mix/settings.json` using logical instrument names only.
- Re-mixing existing compatible stems must not re-render MIDI or stems.
- Keep the engine's peak ceiling and PCM-24 validation.
- Preview choices resolve only to validated local artifacts: `mix/dry.wav`, `mix/lofi.wav`, and `output/master.wav`.
- Implement a real JVM `AudioPlayer` adapter with play/pause/stop/seek/volume state. Playback is monitoring only and never changes release files.
- If an artifact is absent or stale, disable playback and explain which operation creates it.

## Compose state model

Use one screen-level `WorkspaceViewModel` with immutable state and explicit intents. Do not introduce a navigation framework or dependency-injection framework for one workspace.

```text
WorkspaceUiState
  project: ProjectSnapshot?
  arrangement: ArrangementSnapshot?
  mix: MixSnapshot?
  selection: selected part/section/artifact
  drafts: structure/style/instruments/mix controls
  operation: idle/running/succeeded/failed
  playback: stopped/playing/paused + position/duration
  dialogs and notification
```

Use `StateFlow`, a `SupervisorJob`, and injected interfaces. Composables render state and send intents; they do not call stores, worker clients, or filesystem APIs.

## Delivery sequence

| Task | Result | Gate |
|---|---|---|
| [022](tasks/completed/022-project-application-services.md) | Typed project/part/structure services and CLI parity | Existing CLI behavior preserved |
| [023](tasks/completed/023-arrangement-build-services.md) | Typed arrangement/mix/build orchestration and progress | CLI and service produce equivalent artifacts |
| [024](tasks/completed/024-compose-desktop-foundation.md) | Separate Compose Desktop app, theme, shell, and state wiring | Desktop launches without starting Spring |
| [025](tasks/completed/025-desktop-project-workflow.md) | Open/create/import/analyze/edit parts and drag structure | A project can be prepared entirely in UI |
| [026](tasks/completed/026-desktop-arrangement-workflow.md) | Planner controls, approval flow, plan table, song timeline | Valid arrangement can be generated/reviewed |
| [027](tasks/027-desktop-mix-build-playback.md) | Mix controls, playback, progress, end-to-end Build Song | Valid master can be built and auditioned |
| [028](tasks/028-desktop-hardening-packaging.md) | UI tests, accessibility, recovery, current-OS package | Local release candidate passes smoke checklist |

Implement one task at a time using `plan/PROMPT_TEMPLATE.md`. Do not begin Compose screens before Tasks 022–023 establish the shared application boundary.

## Test strategy

- Application-service tests use temporary project directories and fake worker/renderer/player dependencies.
- Retain existing CLI parser and command tests; add parity tests that call the service and CLI adapter against equivalent fixtures and compare canonical artifacts, not console wording.
- View-model tests use `kotlinx-coroutines-test` and assert state transitions, stale states, progress, and errors.
- Compose UI tests use semantics tags for core controls and verify importing, structure reorder, planner validation, approval, mix updates, and disabled prerequisites.
- Audio tests verify format propagation, frame counts, seek boundaries, line cleanup, and no source modification.
- Manual visual checks cover 1100×720, 1440×900, and a HiDPI display.
- Manual audio checks audition a direct-MIDI project and a transcribed-audio project through dry, LoFi, and master outputs.
- Package only the current OS in the MVP; do not claim cross-platform installers without testing on each OS.

## Definition of done

- `./gradlew cliRun --args="..."` remains supported.
- `./gradlew :desktopApp:run` opens the local desktop workspace without Spring or the static frontend.
- Both adapters call the same typed application services.
- A user can create/open a project, import and analyze parts, save a structure, generate/approve an arrangement, build a master, adjust a persisted mix, and audition available outputs.
- Every existing project artifact and safety rule remains canonical and inspectable.
- Source audio/MIDI is never overwritten, intermediate audio remains lossless, and MP3 remains a separate final export.
- The standard automated suite does not require live Qwen, a real SFZ renderer, or the optional transcription model; fakes/fixtures cover those boundaries.

## Explicit non-goals

- Replacing or removing the CLI.
- Calling the CLI as a subprocess from Compose.
- Starting a second Spring/Web UI implementation.
- Waveform or piano-roll editing, arbitrary MIDI-note editing, automation curves, plugins, or a general DAW engine.
- Scene artwork, video concepts, location/weather panels, publishing, cloud services, accounts, databases, or collaboration.
- Hiding AI approval, worker failures, missing sample libraries, or missing renderer configuration.

## Dependency note

As of 2026-08-14, JetBrains lists Compose Multiplatform 1.11.0 as the latest stable release and notes that it uses Kotlin language/API 2.2, while this repository is on Kotlin 2.0.0. Task 024 must pin and verify one compatible Kotlin/Compose pair instead of copying an unverified version into the build. Prefer the current stable pair if the engine's full tests pass after the Kotlin upgrade; otherwise use the newest compatible Compose release and record the temporary constraint.

Primary references:

- https://github.com/JetBrains/compose-multiplatform/releases
- https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/

