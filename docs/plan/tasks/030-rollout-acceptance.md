# Task 030 — End-to-end rollout, documentation, and release acceptance

## Goal

Prove the composition workflow from Setup through Export for new and migrated
projects, complete documentation, and record automated plus manual acceptance.

## Why

Individual contracts do not prove musician usability, migration safety, audio
quality, packaging, or truthful support claims.
It also does not prove that superseded implementations were removed after each
cutover; this final gate must reject a release that carries dead product code.

## Dependencies

Tasks 001–027B and the chosen outcome of optional Task 028.

## Existing Code

- root/desktop/worker test suites and end-to-end fake collaborators
- `docs/RELEASE_ACCEPTANCE.md`, MIDI/workflow/troubleshooting/commercial docs
- function documentation inventory/checks, packaging tasks, UI reference image

## Changes

- Add offline end-to-end fixtures for direct MIDI and eligible WAV/MP3 through
  Setup, Harmony, automatic part processing, repeated structure, arrangement,
  cohesion, seeded humanization, render, mix, style bypass/on, master/export.
- Cover failed Enhancement retry/bypass, low-confidence source key, upstream
  settings/harmony changes, cache reuse, restart recovery, and lineage closure.
- Cover registry v1 and v2, at least two validated candidates for one role,
  explainable profile/mood/character selection, user pin, no-candidate failure,
  approved no-re-resolution, cross-machine missing-ID/hash diagnostics, and
  explicit substitution.
- Cover CC0-only, mixed CC0/CC BY, missing attribution, NC/unknown license
  admission, final-mix usage filtering, deduplicated deterministic credits,
  credits hash/filename, and registry changes after release approval.
- Exercise explicit v1/v2/v3 migration and historical build export; verify source
  and existing artifact hashes before/after.
- Update README, architecture tree, Makefile/build commands, UI/workflow/MIDI/
  worker/troubleshooting/commercial docs, function inventory, and screenshots.
- Complete wide/medium/narrow visual/a11y review and packaged new/legacy smoke.
- With configured real dependencies, run Basic Pitch, Qwen, sfizz, playback and
  listening A/B matrix; record device/version/artifacts/results honestly.
- Update release acceptance with supported OS/dependency/capability limits.
- Audit every replacement/migration task's deletion criteria. Search old class/
  function/route/action/config/artifact labels, dependency-injection registrations,
  build tasks, resources, dependencies, and feature flags. Delete every obsolete
  implementation and its exclusive tests/docs instead of merely recording it.
- For each remaining compatibility reader, record the exact supported persisted
  schema/external contract, active caller, owner, removal condition, and fixture.
  Delete any adapter without all five; permanently disabled/commented-out code and
  duplicate runtime implementations automatically fail acceptance.
- Run clean compilation/package startup after deletions to catch reflection,
  serialization, service-loader, resource, and configuration references that
  textual searches may miss.

## Files

Cross-module integration/fixture/UI/worker tests, documentation, screenshots,
packaging/release record, and every obsolete source/config/resource/dependency/
test discovered by the deletion audit. Production fixes are limited to acceptance
defects and dead-code removal with focused replacement/migration tests.

## API / Contracts

Freeze/version the shipped project, stage, worker, REST (if retained), and
release-manifest contracts. Document compatibility and unsupported cases.

## UI

Verify the full musician workflow, recovery/action language, transport artifact
identity, responsiveness, keyboard/focus/screen reader, and no endpoint jargon.

## Backend

Verify one canonical project authority, atomic recovery, path confinement, and
restart behavior in packaged configuration.

## Python Worker

Run unit schemas plus live supported commands under documented Python/dependency
versions; unavailable optional dependencies remain explicit blockers/limits.

## Tests

Run clean root/desktop/worker/docs/package suites, end-to-end fixtures, lineage
tamper checks, installed-app smokes, and the recorded manual matrix.

Also run named-symbol/path/label searches for every removed subsystem, inspect
routes and dependency-injection/application composition roots, verify build and
runtime configuration keys, and start the packaged app. Tests must exercise only
canonical paths; deleting tests solely tied to removed behavior is required, while
replacement and supported-schema migration coverage remains.

## Acceptance

- New and explicitly migrated projects reach Export through the target sequence.
- Original/Cleaned/Corrected/Enhanced are preserved and enhancement is bypassable.
- Failures resume at the right stage and identical reruns reuse valid artifacts.
- Lo-fi behavior comes from a profile and optional texture is genuinely optional.
- Planners use controlled sound characteristics rather than filenames, while
  approved renders use the exact stable instrument IDs/assets in their lineage.
- Every commercial-ready audio export has a matching usage-derived credits file;
  NC instruments never enter the selectable catalog and CC BY attribution is
  complete without listing unused/CC0 instruments.
- Release record contains evidence for every claimed environment/capability and
  withholds approval for any incomplete manual gate.
- No superseded project-owned source, wrapper, route, registration, disabled flag,
  configuration, dependency, resource, UI control, exclusive test, or stale
  documentation remains.
- Every remaining compatibility reader is demonstrably live for a declared
  supported schema/contract and has a documented owner/removal condition; no
  duplicate runtime writer/orchestrator/processor is accepted.

## Out of Scope

Additional genres, cloud publication, DRM, collaboration, or deferred Future
features from root `PLAN.md`. Dead-code deletion is explicitly in scope.
