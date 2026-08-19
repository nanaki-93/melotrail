# Baseline support matrix

This Task 001 inventory records the surfaces present in the baseline. It is a
compatibility and removal guide, not a promise that an optional dependency is
installed or that every endpoint is a product workflow.

| Surface | Status | Evidence and boundary |
| --- | --- | --- |
| Compose Desktop (`:desktopApp`) | Supported product UI | `DesktopMain` adapts typed Kotlin application services. It opens canonical file-backed `project.json` projects and does not start Spring. |
| Root Kotlin application services | Supported local boundary | `DefaultProjectApplicationService` and `ProjectStore` own canonical project artifacts. Compose adapts these typed services directly. |
| Python worker | Supported optional local dependency | A separately started HTTP worker provides `/health`, `/analyze`, `/apply_dsp`, `/repair`, `/master`, `/mp3_export`, `/mp3_convert`, `/transcribe`, `/midi-clean`, `/inspect-input`, and `/cleanup`. Transcription is only the eligible solo-piano route. |
| Spring `/health` | Retained optional local JSON API | A process-health response only; Compose does not require it. |
| Spring `/api/config` | Obsolete duplicate configuration surface | `ConfigService` writes `data/config/server-config.json`, which is separate from desktop preferences and does not configure the running `ServerConfig`. Do not add callers; Task 028 owns removal/migration. |
| Spring `/api/projects` and `/api/audio` | Obsolete duplicate storage surfaces | Their model and files under `data/projects`/`data/audio` are not canonical Melotrail projects or artifacts. Do not migrate product data into them; Task 028 owns removal/migration. |
| Spring `/api/worker` jobs, control, and SSE | Obsolete compatibility surface | It wraps the legacy in-memory worker job queue rather than the current typed desktop worker boundary. The Python worker lifecycle remains external. Task 028 owns removal/migration. |

## Baseline artifact guarantees

`ProjectStore.read` and the file-backed application-service `open` path read
v1, v2, and v3 project files without rewriting `project.json`. V2-to-v3 is an
explicit atomic migration. Direct MIDI preserves immutable `source/` and
`midi/raw/` evidence; WAV/WAVE/MP3 enters the guarded transcription route and
does not create cleaned MIDI implicitly. Selected MIDI resolves from cleaned
evidence, optionally through an approved AI-fix base and then the separate
Lo-fi derivative; invalid or stale selected artifacts fail instead of silently
falling back.

The registry v1 contract is exactly the logical keys `piano`, `bass`, `drums`,
`pad`, and `strings`. Its loader validates project-contained relative SFZ and
WAV paths, sample formats, license references, one-based drum channel 10,
and the bounded drum map. UI inventory exposes only validated, path-safe
metadata; commercial provenance uses the loaded registry and license evidence.

## Verification evidence

- `ProjectStoreWorkflowMigrationTest` and `ProjectApplicationServiceTest` cover
  read-only v1/v2/v3 opens and failed migration preservation.
- `UnifiedImportApplicationServiceTest` covers direct MIDI, WAV/WAVE/MP3
  transcription publication, source/raw immutability, output validation, and
  retry behavior with offline fakes.
- `SelectedMidiArtifactResolverTest`, `InstrumentRegistryTest`,
  `LocalSoundLibraryInventoryTest`, and commercial-provenance tests cover
  selected-artifact ordering and registry/loading boundaries.
- Worker command-schema tests are offline. Renderer, Basic Pitch inference,
  audio device, and package behavior remain unverified until their local
  dependencies and manual gates are run.
