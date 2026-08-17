# Task 075 — Cohesion and Synchronized Arrangement

## Goal

Connect per-occurrence Melody Cohesion to the supported workflow and make piano,
bass, drums, pad, strings, transitions, the visual timeline, and rendered stems
consume one authoritative song clock without drift.

## Dependencies

- Task 074 accepted.
- Read `../PLAN.md` and Tasks 073–074 completely before implementation.

## Scope

This task owns cohesion application services and workflow wiring, occurrence
MIDI resolution, shared-timeline adoption across arrangement/generation/render,
and synchronization reports. Broad visual reconstruction and simplified import
remain Tasks 076–077.

## Requirements

- Add typed application-service operations to generate, load, approve, reject,
  and regenerate Melody Cohesion.
- Use one bounded request/snapshot model for deterministic and Qwen planners.
  Deterministic mode may publish an automatically approved no-op/safe cohesion
  artifact; Qwen output remains a draft until explicit approval.
- Expose cohesion intents and immutable UI state through `WorkspaceViewModel`.
  Compose must not read or write cohesion files directly.
- Derive cohesion readiness from current fingerprints and approval state. The
  presence of `song_plan.json` alone must not mean cohesion is complete.
- Require one current cohesion result for every stable structure occurrence
  before detailed arrangement generation.
- Add a typed occurrence-MIDI resolver:
  - before approved cohesion, resolve the part's selected Original/Lo-fi MIDI;
  - after approval, resolve the current derived MIDI for that occurrence;
  - reject missing, stale, unapproved, mismatched, or shared-part fallback
    artifacts after cohesion approval.
- Preserve different approved edits for repeated occurrences of the same part.
- Use approved occurrence MIDI as the piano/source lane in the arranged song.
- Refactor bass, drums, pad, strings, transition generation, piano assembly,
  arrangement snapshots, visual timeline, and stem rendering to consume the
  Task 073 `SongTimeline`; remove their independent section-offset and timing
  concatenation.
- Normalize each occurrence to canonical PPQ once and write one non-conflicting
  authoritative tempo/meter map for every generated full-song MIDI.
- Create a persistent, auditable full-song piano MIDI under `midi/generated/`
  alongside the other instruments. Do not rely on an untracked temporary piano
  source as the only assembled evidence.
- Validate every full-song MIDI for canonical PPQ, total end tick, timing meta,
  occurrence/transition bounds, paired notes, collision safety, boundary
  positions, input hashes, and stable occurrence identity.
- Calculate authoritative total song frames once. Every rendered stem and mix
  must have that frame count; renderer tail handling may pad/truncate only under
  the existing explicit policy and may not mask shifted onsets.
- Include timeline identity and every selected/occurrence MIDI hash in generated
  MIDI reports, render fingerprints, stale checks, provenance, and release
  evidence.
- Preserve last-known-good approved artifacts on rejection, cancellation, or
  failed regeneration. Publish new artifacts atomically.
- Add a functional AI Song Plan review model containing purpose, energy,
  instruments, transition, selected occurrence, current/draft/stale state, and
  one regenerate/review action for Task 077 to render.

## Tests

- Cohesion service tests for deterministic/Qwen generation, strict validation,
  approval, rejection, regeneration, cancellation, stale hashes, rollback, and
  per-occurrence identity.
- Tests proving repeated source parts may have different approved occurrence
  MIDI and that the piano timeline consumes each correct artifact.
- Multi-part/mixed-PPQ synchronization tests at every section boundary for all
  five instruments and transitions, with maximum musical error of one canonical
  tick.
- Render click/impulse fixtures with maximum converted onset error of one audio
  frame and exact total stem/mix frame counts.
- Long repeated arrangements proving no cumulative drift.
- Coverage for no transition, crossfade, bridge, internal tempo changes, meter
  changes, boundary note-offs, section-start silence, and every instrument.
- Cache/stale tests proving a changed occurrence/timeline hash cannot reuse old
  generated MIDI or stems.
- View-model tests for cohesion review and arrangement gates.
- Run focused tests, then `./gradlew test :desktopApp:test :desktopApp:build`.

## Acceptance criteria

- Desktop users can generate and review cohesion through typed application
  services; Qwen drafts cannot bypass approval.
- Arrangement cannot consume missing, stale, rejected, or incomplete cohesion.
- Approved occurrence MIDI is audible in the piano stem and identified in the
  generated MIDI/render reports.
- Every generated instrument shares the same PPQ, tempo/meter map, section
  boundaries, transition ranges, and total song length.
- There is no cumulative drift in the long repeated fixture.
- All stems and the dry mix have exactly the authoritative frame count.
- Source/raw/repaired/Lo-fi and prior approved artifacts remain unchanged.

## Manual check

- Listen to straight and expressive multi-section fixtures with all five
  instruments. Check the first beat after every transition and the final bars,
  not only the opening.

## Out of scope

- Exact `UI.png` styling or responsive layout reconstruction.
- MIDI import-dialog simplification.
- Free-form piano-roll editing or unrestricted model-authored notes.
- New instruments, renderer engines, audio DSP, or mastering algorithms.
