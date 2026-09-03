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
- `ArrangementDraftRecord`
- `AcceptedCandidateRef`
- `ArrangementDraftAcceptanceHistory`
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
- verify exactly one note-bearing track/channel and extract its protected view
  atomically with source import;
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
- accept musician-authored occurrence lengths as whole-bar counts whose total
  exactly matches the source, then resolve them to canonical ticks;
- map melody and generated events to occurrences and chord windows; and
- derive advisory scale/chord compatibility without replacing authority.

Occurrence duration is explicit in whole bars. Contiguous tick boundaries are
derived once from PPQ and confirmed meter; the required total comes from the
immutable source end, never from competing service-specific inference.

### 4.4 Arrangement engine

Responsibilities:

- create deterministic candidates for chords, bass, and drums;
- consume an immutable authority snapshot and accepted or validated upstream
  draft dependency context;
- apply complete curated pattern variants;
- validate range, timing, harmony, collision, density, and role-specific rules;
- return candidate plus evidence without writing project state; and
- support generation by one section occurrence and role.

The engine also owns a versioned, four-to-six-item MIDI-only arrangement-style
catalog. An application preview adapter may resolve one style to all three
pure role engines for a two-to-four-bar occurrence loop. That adapter may read
verified source evidence and hold a bounded in-memory cache keyed by
authority/style/occurrence/seed, but must not publish candidates or artifacts,
save project state, invoke a model, render audio, or create a second playback
session.

Role engines are separate but share timing, harmony, seed, pattern, and
validation primitives. They must not create their own project-key or harmony
interpretation.

The full-draft application orchestrator runs all required scopes in deterministic
Chords -> Bass -> Drums order. It retains each valid scoped candidate through
the normal immutable publication boundary, permits cancellation between scopes,
and only writes one draft record after the full ordered reference set validates.
Unaccepted upstream draft dependencies are explicit candidate evidence, never
implicit acceptance pointers.

### 4.5 Candidate review

Responsibilities:

- list alternatives by role and occurrence;
- provide semantic differences and validation findings;
- accept, reject, lock, and restore candidate references;
- assemble and audition a complete persisted draft before acceptance;
- atomically use a complete draft after revalidating every reference, retaining
  the prior acceptance set for restoration;
- assemble the currently accepted song view;
- identify stale candidates after authority changes; and
- guarantee that regeneration never overwrites an artifact.

Single acceptance is pointer movement in project state, followed by an atomic
save. Complete-draft acceptance performs all pointer movement and batch history
publication in one save or none; neither path is a destructive MIDI rewrite.

### 4.6 MIDI audition

Responsibilities:

- play, pause, stop, seek, loop, mute, and solo MIDI views;
- audition source, candidate, occurrence, all-role style preview, role, and
  complete draft, role, and complete accepted arrangement;
- open an audible JVM synthesizer as the managed default endpoint;
- select a supported external local receiver when requested;
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

Export assembles only current accepted references. A complete draft is audible
review evidence but can never be selected by the exporter directly.

DAW patch names are suggestions in the manifest. Export does not depend on
Logic Pro being installed.

### 4.8 Compose Desktop

Responsibilities:

- present six focused destinations;
- expose current authority and blocking findings;
- dispatch user intents to application use cases;
- render primary style selection, full-draft progress/retry, advanced scoped
  correction, candidate, and audition state;
- preserve useful keyboard and accessibility behavior; and
- avoid direct filesystem, generator, or MIDI-device ownership.

The desktop owns a small visual system and reusable presentation primitives,
not a second domain model. Tokens and components may express responsive shell
layout, panel hierarchy, state treatment, role identity, MIDI timeline/event
rendering, and accessibility focus. They consume the same immutable workspace
state as the pages; they must not invent audio waveforms, video previews,
instrument libraries, mixer state, or hidden navigation destinations. The
authoritative visual contract is `docs/MIDI_WORKSPACE_VISUAL_SPEC.md`.

Musician-facing authority drafting uses BPM, named whole-bar sections, and one
progression string per saved occurrence. The desktop converts those inputs into
the canonical tempo, stable identities, contiguous tick ranges, and chord
windows required by application services; internal IDs and tick arithmetic are
not exposed as editing controls.

Arrange and Review are a guided presentation over the existing scoped
application use cases, not new domain owners. Their shared song map derives
bar-proportional occurrence blocks, duplicate-safe labels, harmony summaries,
and textual per-role status from the persisted authority, candidates,
acceptances, and drafts. Selecting a block records the workspace selection and
sets the existing persistent player loop; neither the map nor a page owns a
second timeline or playback session. Arrange derives acceptance progress and
the next unused deterministic seed from the project, then can create one
complete style draft or expose targeted correction. Its selected-section
inspector supplies keyboard-accessible previous/next navigation and keeps
profile/pattern controls behind the advanced local-repair disclosure. Review
uses the same selected section/map context for complete-draft playback and its
atomic **Use this draft** decision, reports known batch blockers at their
scope, and retains comparison/lifecycle evidence behind a selected-section
disclosure. A separate application service can undo only the latest unchanged
draft-acceptance batch by restoring its captured prior pointers atomically;
candidate artifacts and acceptance history remain immutable. After generation
or a lifecycle mutation, the workspace reducer rehydrates the persisted project
and reloads affected evidence before publishing success state, so the UI never
depends on a manual refresh.

The workspace shell, rather than a destination page, owns the one live MIDI
transport presentation. Pages only prepare or select musical views. The dock
is outside page scroll containers and projects the single audition-port state;
project close and authority transitions stop and clear stale selected views.

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

- source MIDI digest and protected melody identity;
- tempo and meter;
- key and mode;
- occurrence order and exact boundaries;
- chord events and durations;
- role settings and generator version; and
- relevant accepted or validated upstream draft dependency candidate IDs.

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
- Cancellation cannot leave a partially current candidate, draft, acceptance
  batch, or export. Completed immutable scoped candidates may remain available
  for an explicit retry of the same incomplete draft.

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
