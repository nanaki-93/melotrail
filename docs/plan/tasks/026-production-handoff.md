# Task 026 — Render, mix, profile processing, and master handoff

## Goal

Feed approved humanized role MIDI into existing render/mix/master/export services
and move fixed lo-fi DSP behind optional profile policy.

## Why

Working production behavior should be preserved, but it must consume the new
pipeline and stop defining lo-fi primarily as post-mix effects.

## Dependencies

Tasks 022B and 025.

## Existing Code

- `StemRenderingMixer`/sfizz renderer and instrument registry
- `MixApplicationService.kt`, `BuildApplicationService.kt`
- current worker repair/master/MP3 operations and `LOFIPresets`
- release/build artifacts and desktop Mix/Master/Export UI

## Changes

- Define immutable render manifest with occurrence/arrangement/cohesion/
  humanization hashes, role/stable-instrument assignments, registry/selection
  decision hashes, verified capability/assets, embedded license/source-library
  snapshots, format, and expected stems.
- Render role stems while mapping legacy stem aliases/mix settings and surfacing
  unresolved assignments/licenses.
- Resolve only the approved stable instrument ID to an engine descriptor inside
  the validated renderer boundary. Never re-run instrument scoring or silently
  substitute because the local registry changed; missing or hash-mismatched assets
  block Render with an explicit substitution/recovery action.
- Retain persisted gain/pan/mute/solo and deterministic dry mix with exact input/
  settings hashes.
- Put Bedroom Lo-fi/audio DSP under optional versioned profile style-processing
  policy; bypass selects Dry Mix and future profiles may define none.
- Audit mandatory worker “repair”; rename/test a precise conditioning role or
  if it has no distinct supported responsibility, delete its call path, command,
  protocol/schema, configuration, tests, and docs in this task. Do not leave an
  unused worker operation after removal from the normal sequence.
- Keep Master separate and record input/output measurements/config/version.
- Implement stage-specific caching/retry: render, mix, style, master, export.
- Persist the resolved final used-stem set after mute/solo/inclusion decisions for
  Task 027/027B. Do not generate credits by scanning installed instruments.
- Update Mix/Master/Export UI labels/readiness and comparisons.
- Delete superseded fixed Bedroom Lo-fi build branches, old stem/instrument
  handoff code, duplicate cache/invalidation paths, and exclusive tests after the
  profile-policy/stable-ID handoff passes migration fixtures.

## Files

Rendering/mix/build services, artifact graph, profile DSP policy, worker protocol
if renamed, desktop pages, migration/docs/tests.

## API / Contracts

Versioned render/mix/style/master/export manifests and stage commands use artifact
IDs/hashes plus stable instrument/registry identities, not inferred filenames.

## UI

Preserve stem controls; show role/instrument identity and optional Profile Texture
bypass. Do not market texture as the composition style itself.

## Backend

Application services own sequence/cache; renderer/worker remain injected ports.

## Python Worker

Retain master/MP3 and validated DSP commands; version any renamed conditioning
contract and expose capability in health.

## Tests

Legacy registry/mix settings, exact instrument/selection/asset handoff hashes,
approved no-re-resolution, missing/mismatched assets/renderer, explicit
substitution invalidation, partial stem retry, mix-only invalidation, style
bypass, final used-stem identity, master/export formats, worker errors.

## Acceptance

- Target sequence is Cohesion -> Humanization -> Render -> Mix -> optional Style
  -> Master -> Export.
- Disabling lo-fi texture leaves a complete musical arrangement.
- Working mix/master functionality and legacy outputs remain accessible.
- A clean repository search shows no obsolete repair/style/handoff runtime path.

## Out of Scope

New mastering AI, DAW plugins, cloud render, new codecs beyond supported formats.
