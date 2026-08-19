# Baseline support matrix

This Task 001 inventory records the surfaces present in the baseline. It is a
compatibility and removal guide, not a promise that an optional dependency is
installed or that every endpoint is a product workflow.

| Surface | Status | Evidence and boundary |
| --- | --- | --- |
| Compose Desktop (`:desktopApp`) | Supported product UI | `DesktopMain` adapts typed Kotlin application services. It opens canonical file-backed `project.json` projects. |
| Root Kotlin application services | Supported local boundary | `DefaultProjectApplicationService` and `ProjectStore` own canonical project artifacts. Compose adapts these typed services directly. |
| Python worker | Supported optional local dependency | A separately started HTTP worker provides `/health`, `/analyze`, `/apply_dsp`, `/repair`, `/master`, `/mp3_export`, `/mp3_convert`, `/transcribe`, `/midi-clean`, `/inspect-input`, and `/cleanup`. Transcription is only the eligible solo-piano route. |
| Spring API | Retired in Task 028 | Task 001 found no supported caller. The obsolete routes, separate project store/configuration, and in-memory worker jobs were deleted; see [`SPRING_API_RETIREMENT.md`](SPRING_API_RETIREMENT.md) for the recoverable data disposition. |

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
