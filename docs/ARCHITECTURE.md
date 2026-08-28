# MIDI Core architecture

Status: target architecture; implementation in progress after task approval

Authority: component ownership and dependency direction

## 1. Architectural goal

Melotrail is one local Kotlin/JVM desktop application. It reads and writes MIDI,
stores explicit musical authority, generates deterministic accompaniment
candidates, lets the user review them in Compose Desktop, and exports a package
for Logic Pro. GarageBand is unverified and outside the supported boundary.

The architecture is intentionally smaller than the current repository. There
is no service boundary, Python process, audio representation, renderer, mixer,
mastering chain, publishing system, or general-purpose DAW abstraction.

## 2. Context

```text
Musician
  -> Melotrail Compose Desktop
       -> local project/artifact store
       -> Standard MIDI reader/writer
       -> local MIDI audition output
       -> deterministic arrangement engine
       -> DAW MIDI export package
  -> Logic Pro
       -> instrument choice, recording, editing, mixing, mastering, release
```

Melotrail owns arrangement intent and evidence. The DAW owns sound production.

## 3. Dependency direction

```text
desktop UI
  -> application use cases
      -> domain contracts
          <- MIDI adapter
          <- project-store adapter
          <- audition adapter
          <- optional constrained-planner adapter
```

Rules:

- Domain code does not depend on Compose, filesystems, HTTP, or a MIDI device.
- Application use cases coordinate domain services and ports; they do not parse
  raw MIDI messages or draw UI.
- Adapters translate external data into domain records and back.
- The desktop layer observes application state and sends intents; it does not
  mutate project files directly.
- Optional AI implements a port and cannot bypass validation or authority.
- No layer calls a Python worker or audio executable.

## 4. Target components

### 4.1 Project kernel

Responsibilities:

- create, open, validate, save, and close projects;
- own schema version and atomic persistence;
- preserve source identity and artifact confinement;
- resolve current authority, candidate acceptance, and export snapshots;
- invalidate derived state when an authority hash changes; and
- reject unsupported legacy audio projects with a clear message.

Key records:

- `ProjectId`
- `ProjectMetadata`
- `SourceMidi`
- `SelectedMelodyTrack`
- `ProjectAuthority`
- `SectionDefinition`
- `SectionOccurrence`
- `ChordEvent`
- `CandidateRecord`
- `AcceptedCandidateRef`
- `ExportSnapshot`

The implementation may choose different Kotlin type names, but every semantic
record must have one owner and a stable serialized contract.

### 4.2 MIDI core

Responsibilities:

- inspect SMF headers and tracks;
- parse format 0 and 1 PPQ sequences;
- pair note-on/note-off events safely;
- preserve supported meta, controller, pitch, and channel information;
- expose a canonical, immutable semantic event model;
- select or extract the protected melody view;
- compare semantic event streams;
- write deterministic SMF files; and
- re-import generated output for validation.

All Java MIDI usage is wrapped here. Arrangement engines consume musical events,
not `javax.sound.midi` objects.

### 4.3 Musical authority

Responsibilities:

- validate tempo, meter, key, and mode;
- parse and realize authoritative chord symbols;
- represent chord duration in beats/ticks, including sub-bar changes;
- build a gap-free ordered occurrence timeline;
- map melody and generated events to occurrences and chord windows; and
- derive advisory scale/chord compatibility without replacing authority.

Occurrence duration is explicit. It is never inferred independently by several
services from raw melody length.

### 4.4 Arrangement engine

Responsibilities:

- create deterministic candidates for chords, bass, and drums;
- consume an immutable authority snapshot and accepted dependency context;
- apply complete curated pattern variants;
- validate range, timing, harmony, collision, density, and role-specific rules;
- return candidate plus evidence without writing project state; and
- support generation by one section occurrence and role.

Role engines are separate but share timing, harmony, seed, pattern, and
validation primitives. They must not create their own project-key or harmony
interpretation.

### 4.5 Candidate review

Responsibilities:

- list alternatives by role and occurrence;
- provide semantic differences and validation findings;
- accept, reject, lock, and restore candidate references;
- assemble the currently accepted song view;
- identify stale candidates after authority changes; and
- guarantee that regeneration never overwrites an artifact.

Acceptance is pointer movement in project state, followed by an atomic save. It
is not a destructive MIDI rewrite.

### 4.6 MIDI audition

Responsibilities:

- play, pause, stop, seek, loop, mute, and solo MIDI views;
- audition source, candidate, occurrence, role, and complete arrangement;
- select a supported local output when necessary;
- clean up sequencer/device resources deterministically; and
- report unavailable devices without corrupting project state.

Preview timbre and loudness are non-authoritative. The audition adapter does not
write audio files and is not used as release evidence.

### 4.7 DAW export

Responsibilities:

- capture an immutable export snapshot;
- assemble conductor and role tracks;
- apply the documented channel, marker, controller, and naming policy;
- write complete and per-role MIDI files plus one manifest;
- semantic-reimport every generated MIDI file;
- stage output and publish it atomically; and
- refuse silent overwrite.

DAW patch names are suggestions in the manifest. Export does not depend on
Logic Pro being installed.

### 4.8 Compose Desktop

Responsibilities:

- present six focused destinations;
- expose current authority and blocking findings;
- dispatch user intents to application use cases;
- render candidate and audition state;
- preserve useful keyboard and accessibility behavior; and
- avoid direct filesystem, generator, or MIDI-device ownership.

Target destinations:

- Project
- MIDI
- Structure & Harmony
- Arrange
- Review
- Export

## 5. Suggested package boundaries

```text
app.melotrail.project
app.melotrail.midi
app.melotrail.music
app.melotrail.structure
app.melotrail.arrangement
app.melotrail.review
app.melotrail.audition
app.melotrail.export
app.melotrail.application
app.melotrail.desktop
```

This is a dependency map, not permission for a bulk file move. Extract one
behavior behind tests, switch callers, and delete its old owner. Empty or
one-type packages are acceptable temporarily only while an active task is
completing the extraction.

## 6. Project storage

Target logical layout:

```text
project-root/
  project.json
  source/
    original.mid
  candidates/
    chords/<occurrence-id>/<candidate-id>.mid
    bass/<occurrence-id>/<candidate-id>.mid
    drums/<occurrence-id>/<candidate-id>.mid
  reports/
    import.json
    candidates/<candidate-id>.json
  exports/
    <snapshot-id>/
      complete-song.mid
      melody.mid
      chords.mid
      bass.mid
      drums.mid
      manifest.json
```

Rules:

- Paths are project-relative and confined beneath the project root.
- Source and candidate files are immutable after their digest is recorded.
- JSON writes use temporary files and atomic replacement where supported.
- Export directories are immutable; a new export receives a new snapshot ID.
- A missing referenced artifact is an error, not permission to select another
  file implicitly.
- Old schema-v4 audio project roots are rejected and may be deleted during the
  explicit cleanup phase. They are never auto-migrated.

## 7. State and invalidation

Derived work binds to an authority hash containing at least:

- source MIDI digest and selected melody identity;
- tempo and meter;
- key and mode;
- occurrence order and exact boundaries;
- chord events and durations;
- role settings and generator version; and
- relevant accepted dependency candidate IDs.

When any member changes, affected candidates become stale but remain
inspectable until cleanup. They cannot be exported as current. Invalidation is
dependency-aware: changing chorus harmony does not invalidate an unrelated
verse candidate.

## 8. Determinism and concurrency

- Seeds are explicit inputs and serialized in candidate records.
- Event and track ordering is stable before writing.
- Floating calculations that influence discrete MIDI output are rounded by one
  documented policy.
- Only one project-state write transaction is active at a time.
- Generation can run off the UI thread, but completion is admitted only if its
  authority hash still matches current state.
- Cancellation cannot leave a partially current candidate or export.

## 9. Error model

Errors are classified as:

- blocking input/authority errors;
- recoverable device or filesystem errors;
- candidate validation rejection;
- stale-result rejection;
- export compatibility failure; or
- internal invariant failure.

The UI presents the problem, affected scope, and next safe action. It never
silently repairs authoritative harmony or replaces accepted work.

## 10. AI boundary

Qwen is absent from the deterministic MVP. A later adapter may return a
constrained plan containing only allowed pattern IDs, density/energy choices,
or bounded role settings. Deterministic code validates and applies that plan.

The model cannot:

- replace project key or harmony;
- emit unrestricted MIDI events directly into the project;
- edit the protected source melody;
- accept its own output;
- write project state; or
- become required for opening, arranging, reviewing, or exporting a project.

## 11. Transition architecture

The current repository contains an audio-era schema, stage graph, Python worker,
HTTP integrations, render/mix/release services, and UI pages. They do not become
adapters in this architecture.

The migration uses a vertical-slice cutover:

1. characterize reusable behavior;
2. implement the target contract;
3. route the focused UI/use case to it;
4. prove the replacement; and
5. delete the old owner, tests, dependencies, docs, and assets in the same
   cleanup task.

`CLEANUP_SCOPE.md` is authoritative for disposition.
