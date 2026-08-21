# Melotrail — Canonical Musical Pipeline Alignment Plan

## 1. Purpose

Before expanding the product, align every MIDI-producing and MIDI-modifying stage
with one canonical musical authority, one dependency graph, and one auditable
approval flow. This plan corrects the currently implemented pipeline; it does
not introduce a second project format, artifact store, UI, or command-line
product.

The implementation contracts for this plan are Tasks 118–130 in
`docs/plan/tasks/`.

## 2. Verified baseline and alignment verdict

The repository is a Kotlin/JVM 21 Compose Desktop application with a separate,
stateless Python HTTP worker. The v4 project envelope is canonical and already
contains composition settings, structured section harmony, stable structure
occurrences, workflow references, and hash-bound artifacts.

The current design is aligned in these areas:

- `Project` and `ProjectStore` are the persisted source of truth.
- `SelectedMidiArtifactResolver` owns per-part MIDI selection precedence.
- AI arrangement is a planner; deterministic Kotlin generators create MIDI.
- AI Fix, per-track Enhance, Cohesion, and Humanization already retain useful
  reports, drafts, approvals, or deterministic edit evidence.
- The supported UI is Compose Desktop and the worker remains stateless.

The following gaps must be corrected:

- AI Fix re-infers harmony from corrected MIDI instead of receiving canonical
  project and occurrence harmony.
- Enhancement receives broad project context but does not consistently validate
  edits against the chord active at each note.
- `StageId` and project dependency metadata do not represent all supported
  stages or their actual order.
- Cohesion currently combines boundary transitions with whole-song `songEdits`.
- Humanization is deterministic timing, velocity, duration, and chord staggering;
  it is not full-song AI enhancement.
- Role validators and before/after evidence are incomplete and inconsistent.
- The old plan proposed duplicate numbered MIDI history and a CLI, both of which
  conflict with the canonical artifact and desktop-service architecture.
- There is no single deterministic reference-song integration fixture.

## 3. Canonical pipeline

The supported musical and production order is:

```text
source
  → extracted
  → cleaned
  → normalized
  → transposed
  → corrected
  → AI Fix
  → per-track AI Enhance
  → MIDI Feel
  → analyzed
  → structured
  → arranged
  → generated
  → boundary Cohesion
  → deterministic Full-Song Critic
  → optional AI Full-Song Enhance
  → optional seeded Humanization
  → rendered
  → mixed
  → audio texture
  → mastered
  → exported
```

Full-song AI enhancement is an explicit stage between Cohesion and
Humanization. It is not hidden inside either stage. Humanization runs after the
last note-level AI change so it never needs to be silently replayed.

## 4. Musical authority and projections

Create one deterministic musical-authority builder from canonical project data:

- declared key, scale/mode, tempo, and time signature;
- section-keyed chord progressions;
- stable structure occurrence IDs and bar/tick ranges;
- selected per-part MIDI artifacts and SHA-256 fingerprints;
- analyzed phrases, ranges, density, contour, energy, and melody anchors;
- approved arrangement and generated role artifacts when applicable.

Declared project data is authoritative. Analyzed data is descriptive. An
inference conflict may be reported, but must not replace declared key, meter,
tempo, or harmony.

Every stage receives a small immutable projection from this authority rather
than reconstructing its own competing context. Each projection includes a
schema/version identifier and an input hash. Repeated section occurrences keep
their own stable identity even when they share a section type or chord pattern.

The harmonic timeline must answer the active chord for a bar, tick, note, or
occurrence. It uses canonical meter and progression cycling and rejects invalid
or ambiguous occurrence bounds instead of guessing.

## 5. Stage responsibilities

### AI Fix

AI Fix repairs objective MIDI defects in the selected corrected input. It must
receive the canonical key, harmonic timeline, occurrence context, tempo, meter,
and melody anchors. Inferred harmony is diagnostic evidence only. Approved AI
Fix selection remains fingerprint-bound and bypassable.

### Per-track AI Enhance

Per-track Enhance improves an imported musical part within its existing
identity. It validates every pitch edit against the active chord and project
scale, protects melody anchors, enforces range and edit budgets, and publishes a
deterministic edit report. It cannot change structure or canonical settings.

### Arrangement and generation

The arrangement model produces a high-level song plan and detailed arrangement.
Kotlin generators remain the only MIDI executors. Inputs are projections of the
canonical authority; generated piano, bass, drums, pad, strings, and transition
roles are validated consistently before publication.

### Boundary Cohesion

Cohesion operates only in configured windows around adjacent occurrence
boundaries. Its plan may bridge, overlap, thin, lead into, or smooth those
transitions, but may not apply whole-song edits. The current `songEdits` contract
is removed in a new schema version. Old outputs remain inspectable evidence but
cannot satisfy the new approval contract.

### Deterministic Full-Song Critic

The critic analyzes the approved cohesive ensemble and never mutates MIDI. It
emits a versioned `FullSongCriticReport` with typed issues containing severity,
metric evidence, target occurrence/role, bar or tick window, and all input
hashes. Checks cover:

- melody preservation and anchor integrity;
- chord/scale consistency and voice collisions;
- instrument range and bass leaps;
- role density, contrast, and section energy;
- transition continuity and abruptness.

An existing arrangement-plan critic remains an earlier concern and must be named
or documented so it cannot be confused with this post-Cohesion critic.

### AI Full-Song Enhance

The model receives only canonical context, the critic report, exact target
windows, and an allow-listed operation vocabulary. It returns a strict,
versioned `FullSongEnhancementPlan`; Kotlin validates and applies it.

Allowed operations are chord revoicing, bass-leap simplification, density
reduction, collision removal, local timing/velocity/duration adjustment,
limited chord-clash correction, and transition-note adjustment. The stage may
not change structure, harmony, tempo, meter, duration, instrument assignment, or
unreported regions.

Per target, changed existing notes plus additions and deletions are capped at
5% of its note count; additions and deletions are each capped at 2%. Integer
budgets round down, so a zero budget permits no such operation. Melody anchors
cannot be deleted or pitch-shifted. Other melody pitch changes are limited to
two semitones and must remain valid for the active harmony.

The candidate is previewable, explicitly approvable, retryable, and bypassable.
If the critic reports no actionable issue, record a current, hash-bound no-op.
Humanization consumes the approved result or, after explicit bypass, the
approved Cohesion output.

### Humanization

Humanization remains a seeded deterministic processor. It may alter timing,
velocity, duration, chord staggering, and configured groove characteristics,
but not pitch, note count, tempo, meter, structure, or harmony.

## 6. Persistence, invalidation, and diagnostics

Extend durable stage and workflow identifiers without renaming existing wire
values. Add explicit identifiers for `ai-fixed`, `midi-feel`, `critiqued`,
`full-song-enhanced`, `humanized`, and `audio-textured`, and correct project
ordering so Arrangement and Generation precede Cohesion.

Every selected output is tied to its exact inputs, processor/model identity,
context version, report, and SHA-256. Changing any upstream selected artifact,
composition setting, harmony, structure occurrence, arrangement, or generator
output invalidates every dependent selection while retaining old files as
inspection evidence.

Use existing project-relative artifact storage and atomic publication. Do not
create numbered duplicate MIDI folders. Diagnostics are application-service
snapshots and persisted reports displayed through the existing desktop review
surfaces; no CLI or dedicated diagnostics product is added.

Comparison reports use stable, code-owned metrics where applicable: note count,
pitch/range, chord-fit, anchor preservation, timing, velocity, density, edit
budget, input/output hashes, warnings, and rejection reasons. No subjective
"sounds good" flag is persisted as workflow truth.

## 7. Validation and acceptance

All external model responses are strict JSON, schema-versioned, hash-bound, and
validated against allow-lists before deterministic application. Invalid,
malformed, stale, excessive, or out-of-scope plans fail safely without changing
the selected artifact.

Completion requires:

- one canonical authority and timeline used by all relevant stages;
- exact workflow ordering and dependency invalidation across MIDI stages;
- boundary-only Cohesion with no whole-song edit path;
- a deterministic critic and separate optional AI Full-Song Enhance stage;
- reusable melody identity and role validation evidence;
- approval/bypass precedence into Humanization and rendering;
- deterministic offline tests, including one end-to-end reference song;
- current planning, architecture, task-index, and prompt documentation.

The Kotlin baseline is `./gradlew test :desktopApp:test`; task completion also
requires `./gradlew :desktopApp:build`. Worker tests run only when worker code is
changed. Model boundaries are tested offline with fakes and fixtures.

## 8. Delivery sequence

Tasks 118–130 are ordered contracts. Tasks 118–121 establish evidence and shared
authority. Tasks 122–126 align existing stages. Tasks 127–128 add the separate
critic and full-song enhancement stages. Tasks 129–130 complete diagnostics,
integration evidence, compatibility cleanup, and release acceptance.

Do not implement a later task early unless its contract explicitly permits
parallel work. A task that replaces a runtime path must remove its superseded
project-owned implementation and exclusive tests after supported reads and
callers have migrated; Git history is the archive.
