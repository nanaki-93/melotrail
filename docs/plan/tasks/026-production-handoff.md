# Task 026 — Render, mix, profile processing, and master handoff

## Goal

Feed approved humanized role MIDI into existing render/mix/master/export services
and move fixed lo-fi DSP behind optional profile policy.

## Why

Working production behavior should be preserved, but it must consume the new
pipeline and stop defining lo-fi primarily as post-mix effects.

## Dependencies

Task 025.

## Existing Code

- `StemRenderingMixer`/sfizz renderer and instrument registry
- `MixApplicationService.kt`, `BuildApplicationService.kt`
- current worker repair/master/MP3 operations and `LOFIPresets`
- release/build artifacts and desktop Mix/Master/Export UI

## Changes

- Define immutable render manifest with occurrence/arrangement/cohesion/
  humanization hashes, role/instrument/assets, format, and expected stems.
- Render role stems while mapping legacy stem aliases/mix settings and surfacing
  unresolved assignments/licenses.
- Retain persisted gain/pan/mute/solo and deterministic dry mix with exact input/
  settings hashes.
- Put Bedroom Lo-fi/audio DSP under optional versioned profile style-processing
  policy; bypass selects Dry Mix and future profiles may define none.
- Audit mandatory worker “repair”; rename/test a precise conditioning role or
  remove it from the normal sequence with compatibility evidence.
- Keep Master separate and record input/output measurements/config/version.
- Implement stage-specific caching/retry: render, mix, style, master, export.
- Update Mix/Master/Export UI labels/readiness and comparisons.

## Files

Rendering/mix/build services, artifact graph, profile DSP policy, worker protocol
if renamed, desktop pages, migration/docs/tests.

## API / Contracts

Versioned render/mix/style/master/export manifests and stage commands use artifact
IDs/hashes, not inferred filenames.

## UI

Preserve stem controls; show role/instrument identity and optional Profile Texture
bypass. Do not market texture as the composition style itself.

## Backend

Application services own sequence/cache; renderer/worker remain injected ports.

## Python Worker

Retain master/MP3 and validated DSP commands; version any renamed conditioning
contract and expose capability in health.

## Tests

Legacy mix settings, exact handoff hashes, missing assets/renderer, partial stem
retry, mix-only invalidation, style bypass, master/export formats, worker errors.

## Acceptance

- Target sequence is Cohesion -> Humanization -> Render -> Mix -> optional Style
  -> Master -> Export.
- Disabling lo-fi texture leaves a complete musical arrangement.
- Working mix/master functionality and legacy outputs remain accessible.

## Out of Scope

New mastering AI, DAW plugins, cloud render, new codecs beyond supported formats.

