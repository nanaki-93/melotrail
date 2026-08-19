# Task 030 — End-to-end rollout, documentation, and release acceptance

## Goal

Prove the composition workflow from Setup through Export for new and migrated
projects, complete documentation, and record automated plus manual acceptance.

## Why

Individual contracts do not prove musician usability, migration safety, audio
quality, packaging, or truthful support claims.

## Dependencies

Tasks 001–027 and the chosen outcomes of optional Tasks 028–029.

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
- Exercise explicit v1/v2/v3 migration and historical build export; verify source
  and existing artifact hashes before/after.
- Update README, architecture tree, Makefile/build commands, UI/workflow/MIDI/
  worker/troubleshooting/commercial docs, function inventory, and screenshots.
- Complete wide/medium/narrow visual/a11y review and packaged new/legacy smoke.
- With configured real dependencies, run Basic Pitch, Qwen, sfizz, playback and
  listening A/B matrix; record device/version/artifacts/results honestly.
- Update release acceptance with supported OS/dependency/capability limits.

## Files

Cross-module integration/fixture/UI/worker tests, documentation, screenshots,
packaging/release record. Production fixes are limited to acceptance defects with
focused regression tests.

## API / Contracts

Freeze/version the shipped project, stage, worker, REST/CLI (if retained), and
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

## Acceptance

- New and explicitly migrated projects reach Export through the target sequence.
- Original/Cleaned/Corrected/Enhanced are preserved and enhancement is bypassable.
- Failures resume at the right stage and identical reruns reuse valid artifacts.
- Lo-fi behavior comes from a profile and optional texture is genuinely optional.
- Release record contains evidence for every claimed environment/capability and
  withholds approval for any incomplete manual gate.

## Out of Scope

Additional genres, cloud publication, DRM, collaboration, or deferred Future
features from root `PLAN.md`.

