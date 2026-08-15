# Task 070 — Workflow State, Artifact Invalidation, and Project Migration

## Goal

Integrate the repaired playback/UI branch and the MIDI/cohesion branch into one
truthful Melotrail workflow with durable project state and exact stale rules.

## Dependencies

- Tasks 066 and 069 accepted.

## Requirements

- Replace the old coarse creation progress with a domain-derived workflow read
  model covering import/inspection, transcription, MIDI repair, optional Lo-fi
  Feel, analysis, structure, cohesion, arrangement, render, mix, master, and
  commercial export readiness.
- Do not reintroduce the removed workflow-status menu row. Show current state,
  prerequisites, and the next safe action contextually.
- Version the project schema for selected analysis MIDI, repair evidence, Lo-fi
  Feel selection/report, per-occurrence cohesion plan/results/approval, and
  commercial provenance references.
- Maintain backward-compatible readers for existing v1/current projects.
  Migrate in memory or by explicit atomic save; never partially rewrite a
  project during open.
- Define a dependency graph and centralized invalidation rules:
  - source/raw change invalidates repair and everything downstream;
  - repaired change invalidates lo-fi, analysis, structure-derived cohesion,
    arrangement, generated MIDI, stems, mixes, master, and release;
  - lo-fi selection/change invalidates analysis and everything downstream;
  - analysis change invalidates cohesion and everything downstream but retains
    the user's saved structure when its part IDs remain valid;
  - structure change invalidates cohesion and everything downstream;
  - cohesion change invalidates arrangement and everything downstream;
  - mix-only change invalidates dry/lo-fi audio, master, and release;
  - audio-texture option change invalidates only audio texture/master/release.
- Derive readiness from validated files and fingerprints, not flags alone.
- Ensure project switch, close, cancel, and failure terminate playback and
  operation sessions without deleting last-known-good artifacts.
- Update CLI/application services to follow the same canonical stages or state
  clearly when a desktop-only review step requires explicit approval.
- Update README, troubleshooting, worker, sound-library, and task-prompt docs to
  the final workflow and Melotrail terminology.

## Tests

- Exhaustive invalidation-matrix tests and stale fingerprint tests.
- Project migration fixtures from every supported schema version, including
  corrupt/partial new artifacts and old embedded clean-MIDI projects.
- Workflow state tests for blocked/current/review/stale/complete without a
  second navigation row.
- Project switch/close/cancel tests spanning playback and backend operations.
- CLI/desktop compatibility and end-to-end artifact graph tests.

## Acceptance criteria

- The UI always derives the same workflow truth as application services and the
  files on disk.
- Changing an upstream artifact invalidates exactly the required downstream
  artifacts and never source data or unrelated approved work.
- Existing projects open with an actionable migration state.
- No old workflow badge row returns.

## Out of scope

- Commercial license decisions and export manifest UI, implemented in Task 071.
- Cloud synchronization or multi-user collaboration.
- Automatic destructive cleanup of stale files.

