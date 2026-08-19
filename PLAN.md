# Melotrail composition builder roadmap

Status: planning only  
Plan baseline: 2026-08-19  
Current persisted project schema: v3  
Proposed next persisted project schema: v4

## Product goal

Evolve Melotrail from a track-processing and lo-fi arrangement workstation into
a human-led, AI-assisted song composition builder. The musician remains the
author of melody, harmony, form, tempo, meter, instruments, and arrangement
choices. Deterministic processing and bounded AI may clean, correct, suggest,
arrange, connect, humanize, mix, and master those choices, but must not silently
replace them.

Lo-fi is the first built-in `CompositionProfile`, not a collection of assumptions
embedded throughout the application. Additional profiles must be addable through
profile data and profile-owned strategies without redesigning the project model,
workflow, or UI.

## Design principles

1. Preserve authorship: source recordings and every accepted musical stage are
   immutable, addressable artifacts.
2. Separate correction from enhancement: technical validity and conservative
   repair are not the same operation as creative melodic suggestions.
3. Put musical context in contracts: key, scale, harmony, meter, tempo, mood,
   profile, section, and intensity travel together to musical processors.
4. Prefer deterministic code: AI proposes bounded plans; code validates and
   applies them. Randomized transforms store a seed.
5. Make change traceable: keep source -> extracted -> cleaned/normalized ->
   transposed -> corrected -> enhanced lineage and allow bypass/comparison.
6. Resume at stage boundaries: a failed enhancement must not discard a valid
   transcription, and unchanged upstream artifacts must be reusable.
7. UI first: establish the project, harmony, part, stage, and profile contracts
   before implementing sophisticated musical AI.
8. Migrate incrementally: retain the useful file-backed application services,
   MIDI tools, rendering, mix/master, and provenance work; do not introduce a
   second product domain or rewrite working components without evidence.
9. Profile composition, not just effects: lo-fi character should primarily come
   from harmony, arrangement, groove, and performance. Tape, vinyl, filtering,
   and noise remain optional finishing texture.
10. Delete obsolete code: when a replacement is accepted, remove the superseded
    implementation, registrations, routes, feature flags, configuration, tests
    that only protect removed behavior, and stale documentation in the same task
    or migration phase. Git history is the archive; the repository is not.

## Obsolete-code policy

Project-owned deprecated/dead code is not a supported end state. A temporary
compatibility reader or adapter is allowed only while it actively migrates a
currently supported persisted schema or external contract. It must have a named
owner, explicit removal condition, and a downstream deletion task. It must not
become an indefinite second runtime path.

Before deletion, the implementing task must:

1. prove the code is superseded or unreachable using call-site, route, dependency-
   injection, build-task, configuration, and persisted-data inventory;
2. migrate any user data/artifact references atomically and retain immutable
   evidence/backups where required;
3. switch all supported callers and fixtures to the canonical replacement;
4. delete the old source plus exclusive tests, DTOs, registrations, resources,
   configuration keys, dependencies, build targets, UI controls, and docs;
5. run repository searches and clean builds proving no stale symbol/path/feature-
   flag references remain.

Do not keep commented-out implementations, permanently disabled flags, duplicate
services “for safety,” or deprecated wrappers without an active supported caller.
Historical artifacts may remain as data/evidence; they do not justify retaining
unused code. A legacy schema reader that is still required by the documented
support policy is live migration code, not dead code; when that support window
ends, its removal is part of the same policy change.

## Current architecture summary

### Supported product path

- Kotlin 2.2/JVM 21 and Spring Boot form the root Gradle module; Compose Desktop
  is a second Gradle module and is the supported user interface.
- `ProjectApplicationService` and the v3 file-backed `arrangement.Project` are
  the canonical desktop project boundary. Project-relative artifacts, hashes,
  validation, atomic writes, and downstream invalidation already exist.
- `WorkspaceViewModel`, `WorkspacePageRouter`, `WorkflowReadModel`, and typed
  application services drive the Compose workflow.
- The Python worker is a synchronous local HTTP command host for inspection,
  audio preparation, Basic Pitch transcription, MIDI cleanup, DSP, mastering,
  and MP3 operations. Kotlin owns orchestration and persistence.
- Arrangement already has song planning, occurrence variation, detailed plans,
  deterministic MIDI generators, sound-library resolution, stem rendering, and
  approval gates.
- Cohesion already creates reviewable transition plans and deterministic bridge
  artifacts, but it currently runs before arrangement.
- Mix/build already preserve stems, persist mix settings, render a dry mix,
  optionally apply a fixed lo-fi audio effect, master, and export.
- Commercial provenance already hashes important sources, dependencies, and
  outputs, but it is not yet a general per-stage lineage ledger.

### Parallel or stale surfaces

- The Spring `model.Project`/`ProjectServiceAdapter` and REST controllers use a
  separate track-centric project model and store. They are not the canonical
  Compose project path and must not become a second composition implementation.
- Configuration is split across desktop preferences, root config classes, and
  the optional Spring service. Consolidation should follow ownership boundaries
  instead of preceding the UI milestone.
- Some older whole-melody cohesion and legacy UI code remains alongside the
  active boundary-cohesion/router path. Migration tasks must move supported data/
  callers and then delete those superseded implementations and exclusive tests;
  they are not allowed to survive as dormant alternatives.

See [the architecture audit](docs/plan/architecture.md) for file-level findings
and reuse/retirement decisions.

## Target architecture

```text
Compose UI
  -> UI-neutral application services and workflow read models
    -> canonical v4 file-backed composition project
      -> stage runner + artifact graph + provenance records
        -> deterministic Kotlin musical processors
        -> local model planning ports
        -> Python worker for audio/model workloads
        -> renderer, mix, master, and export services

Optional Spring API
  -> the same application services and DTO contracts
```

The v4 project remains the authoritative, portable aggregate. Large evidence
and run history live in project-relative manifests rather than inflating
`project.json`. The project stores settings, part/structure identity, selected
artifacts, approvals, and manifest references.

## Domain model

The next schema adds these concepts without discarding existing v3 data:

- `CompositionSettings`: name, `MusicalKey`, tempo, `TimeSignature`, `MoodId`,
  and `CompositionProfileId`.
- `MusicalKey`: a structured tonic/pitch class plus `ScaleMode`; display labels
  such as “Eb major” are derived.
- `CompositionProfile`: stable ID/version plus supported defaults, constraints,
  mood mappings, arrangement roles, instrument-selection criteria,
  groove/humanization parameters, enhancement policies, and optional
  style-processing choices.
- `MoodDefinition`: stable ID and typed parameter modifiers, not prompt text.
- `SectionTypeId`: an extensible stable identifier with built-in verse, chorus,
  bridge, intro, and outro definitions.
- `ChordProgression` and `ChordEvent`: structured root and quality. MVP qualities
  are major, minor, dominant 7, major 7, minor 7, major 9, minor 9, add9, sus2,
  and sus4. Duration defaults to one measure; inversion, slash bass, arbitrary
  extensions, and substitutions are reserved fields/later features.
- `SongPart`: evolves the current `Part`; it keeps persistent identity, source
  attestation/evidence, section type, and selected artifact lineage.
- `StructureOccurrence`: persistent occurrence ID, part ID, label, and variation
  overrides. Repetition references one part rather than copying its melody.
- `MusicalProcessingContext`: an immutable snapshot of project key/scale,
  section harmony, tempo/meter, mood/profile versions, section identity,
  intensity, seed, and pipeline version.
- `StageRunRecord` and `ArtifactRef`: status, inputs, outputs, hashes,
  configuration fingerprint, processor/model identity, seed, timestamps, and
  failure evidence.
- `ArrangementRole`: melody, harmony, bass, drums, counter-melody, texture, and
  ambience, resolved separately from concrete sound-library instruments.
- `InstrumentDefinition`: stable instrument ID/name plus eligible roles,
  profile/mood affinities, typed sonic characteristics, an engine descriptor,
  verified capabilities, embedded commercial-license metadata, and source-library
  provenance. File paths remain private to the validated sound-library boundary.
- `InstrumentSelectionRequest` and `InstrumentSelectionDecision`: musical search
  criteria and the deterministic, explainable resolution to a validated installed
  instrument. Approved arrangements persist the selected ID and registry snapshot
  hash rather than re-running selection at render time.

Detailed invariants and migration mappings are in
[domain-model.md](docs/plan/domain-model.md).

## Target pipeline

```text
Project Setup
  -> Harmony
  -> Import Melody Parts
  -> Extract/Convert
  -> Clean and Normalize
  -> Transpose
  -> Correct
  -> Enhance (optional/bypassable)
  -> Song Structure
  -> Arrange
  -> Cohesion
  -> Humanization
  -> Render
  -> Mix
  -> Optional Profile Style Processing
  -> Master
  -> Export
```

Logical artifact kinds are `source`, `extracted`, `cleaned`, `normalized`,
`transposed`, `corrected`, `enhanced`, `arranged`, `cohesive`, `humanized`,
`rendered`, `dry_mix`, `style_processed`, `mastered`, and `exported`. Migration
also defines `release_credits` as the hash-paired human-readable attribution
artifact beside an export. Migration does not require immediately renaming
established v3 files: artifact type and
lineage are authoritative, while physical paths may retain compatible names.

Each stage computes a cache key from input hashes, normalized configuration,
processor version, context version, and seed/model details. It persists
`PROCESSING` before work and either `COMPLETED` with a validated new artifact or
`FAILED` with recoverable evidence. Pending/failed records never claim a missing
artifact. Downstream invalidation follows explicit dependencies.

## UI workflow

Reuse the existing responsive workspace shell, navigation, transport, project
picker, cards, dialogs, and view-model/application-service separation. Adapt
the primary musician-facing destinations to:

1. Setup
2. Harmony
3. Melody Parts
4. Structure
5. Arrange
6. Build (cohesion, humanization, render)
7. Mix & Master
8. Export

Technical part stages belong in each part card as a progress rail, not as eight
top-level screens. Import starts the eligible pipeline automatically. A part
card exposes current stage, retry, warnings, source/cleaned/corrected/enhanced
preview choices, and enhancement intensity. Advanced evidence is available in
details without turning the normal UI into an endpoint console.

Milestone one uses deterministic/no-op implementations behind the new stage
contracts where necessary. It must make project setup, harmony editing,
structured part assignment, automatic progress, recovery, and downstream
readiness understandable before advanced AI is added.

## Backend and application-service changes

- Split the oversized project service by capability behind one facade only as
  each new contract is introduced; do not perform a speculative rewrite.
- Add typed settings, harmony, part, pipeline, structure, and selection commands
  with immutable snapshots for the UI.
- Add a persistent stage runner that is the only owner of automatic import
  orchestration, locks, status transitions, cache/retry behavior, and artifact
  publication.
- Extend `WorkflowArtifactGraph` and `WorkflowReadModel` from coarse global stale
  markers to part/context-aware dependency decisions.
- Make arrangement planning independent of approved cohesion. Cohesion will
  consume the approved arrangement/occurrence context and produce continuity
  artifacts before humanization/render.
- Adapt the optional Spring API later as a transport adapter over canonical
  services if it has supported users. Otherwise migrate/export required data and
  delete the separate controllers/model/store/configuration/tests in Task 028.
  Do not dual-write or leave a frozen deprecated service in the repository.

## Python worker changes

Keep the worker narrow and stateless. It should continue to accept typed,
versioned commands and return validated results while Kotlin persists durable
job state and lineage.

- Reuse inspection, cleanup, transcription, MIDI-clean, DSP, master, and MP3
  commands after verifying real capability and error behavior.
- Add worker commands only when a workload genuinely belongs in Python (for
  example a future melody model). Deterministic MIDI transposition, plan
  validation/application, artifact selection, and workflow orchestration stay
  in Kotlin.
- Replace absolute-path trust at exposed transport boundaries with caller-side
  project-root confinement and command-level validation.
- Keep live Basic Pitch limitations visible: initial audio-to-MIDI support is
  eligible solo-piano WAV/WAVE/MP3, not arbitrary mixed-song transcription.

## MIDI processing and AI enhancement

MVP processing definitions are deliberately distinct:

- Extract/convert: validate direct MIDI or transcribe eligible audio.
- Clean: event integrity, orphan/duplicate/retrigger cleanup, invalid durations,
  and format validation.
- Normalize: conservative PPQ/timing/velocity/range conventions with a report.
- Transpose: deterministic movement from detected or user-confirmed source key
  to the authoritative project key; low-confidence detection requires review.
- Correct: conservative technical/detection corrections, with no phrase
  invention and a bounded edit report.
- Enhance: optional melodic suggestions informed by the complete musical
  context. `OFF` selects corrected; `SUBTLE`, `BALANCED`, and `CREATIVE` use
  increasing, validated change budgets. The initial default is `SUBTLE`.

The existing AI-fix implementation is useful evidence and bounded-edit
infrastructure, but currently mixes correction and enhancement and infers key
and chords from the isolated MIDI. It will be split behind correction and
enhancement ports. Existing approved artifacts remain readable as legacy
selections during migration.

An enhancement model returns strict, path-free proposals. Deterministic code
validates context identity, input hash, operation vocabulary, range, timing,
polyphony, harmony, edit budget, and identity-distance limits before writing a
draft artifact. The original, corrected, and enhanced versions remain A/B
previewable and selectable.

## Arrangement, cohesion, and humanization

- Keep existing song planning, occurrence variation, detailed plans, MIDI
  generators, validated registry loading, renderer, and approval gates.
- Replace free style strings and fixed logical instruments with profile IDs,
  arrangement roles, and a separate role-to-instrument assignment.
- Evolve the current v1 registry—which requires exactly one `piano`, `bass`,
  `drums`, `pad`, and `strings` entry—into a versioned catalog that can contain
  multiple instruments for the same role. Entries use stable IDs such as
  `karoryfer-fashionbass`; names and engine files are metadata, not planner IDs.
- AI/deterministic arrangers request role plus controlled musical traits (for
  example Lo-fi + Nostalgic + Verse + Bass + Soft). A code-owned resolver filters
  validated installed instruments by role, engine/capabilities, and license
  policy, then scores profile affinity, mood affinity, sonic characteristics,
  and user preferences with a stable ID tie-break. The planner never sees or
  returns `.sfz` paths.
- User-pinned instruments override suggestions when compatible. Once an
  arrangement is approved, its stable instrument IDs, criteria, candidate-score
  evidence, registry version/hash, asset hashes, embedded license snapshot, and
  source-library provenance are frozen.
  A missing instrument on another machine blocks render or requests an explicit
  substitution; it must never silently choose a different sound.
- Registry admission is commercial-first. A versioned policy catalog validates
  the declared license identifier against `commercialUse` and attribution fields:
  CC0 is preferred by default, supported CC BY instruments remain eligible with
  mandatory complete attribution metadata, and recognized NonCommercial licenses
  are rejected before they can be selected. Unknown/custom terms require explicit
  review rather than trusting a boolean.
- Persist structure occurrence IDs so variations survive reorder and repetition.
- Reverse the current dependency: arrangement establishes roles, density,
  instrumentation, and occurrence variation; cohesion then uses that context to
  connect boundaries. Cohesion must not rewrite the core melody.
- Extend cohesion gradually from reviewed transition bridges to fills, pickups,
  bass/chord motion, instrument continuity, automation, dynamics, and repeated-
  section variation.
- Add an explicit seeded humanization stage after cohesion and before render.
  It may vary timing, velocity, note length, chord staggering, swing, drums, and
  bass within profile/mood bounds, and always records its seed/configuration.

## Mix, master, and export

Retain the current stem renderer, per-role gain/pan/mute/solo settings, dry mix,
worker master, MP3 export, and release checks. Change the handoff so the renderer
consumes approved humanized occurrence MIDI. Resolve arrangement roles to stem
identity without losing backward-compatible logical stem names. Rendering
resolves the approved stable instrument ID through the local validated registry;
only that renderer adapter receives the engine-specific SFZ path.

Move fixed Bedroom Lo-fi DSP behind an optional profile style-processing policy.
The default composition should remain musically recognizable with that effect
disabled. Mastering remains a distinct final loudness/format stage.

Every release export also produces a deterministic sibling credits artifact such
as `my-song-credits.txt`. It is generated from the exact instrument stems included
in the final mix/master lineage—not from installed candidates or unused arrangement
roles—and contains only the deduplicated attribution blocks required by those
instruments. CC0 instruments are not listed. A CC0-only release records that no
instrument attribution is required. Missing required attribution blocks the
commercial-ready export; private project work remains available.

## Provenance and reproducibility

Expand `CommercialProvenanceService` rather than introduce DRM. For every stage
and release preserve:

- source attestation and immutable source hash;
- user-entered settings, harmony, structure, and arrangement choices;
- instrument-selection requests/decisions, registry snapshot/version, selected
  stable IDs, engine/asset hashes, capability evidence, embedded license snapshots,
  source-library versions, and explicit substitutions;
- input/output artifact IDs and hashes;
- normalized processor configuration and pipeline version;
- AI provider/model/version/license when applicable;
- prompt-template/context schema version, not necessarily private chain-of-
  thought or unrestricted raw model output;
- random seed and deterministic applier version;
- timestamps, status, approval/selection, and generated outputs;
- the exact used-instrument set, normalized/deduplicated attribution blocks, and
  credits-file hash paired with each exported master.

A release manifest references the exact selected lineage. Provenance failures
block a “commercial-ready” claim but never delete the musician's work.

The initial policy follows the documented distinction that CC0 permits commercial
reuse without required attribution, CC BY requires credit, and Creative Commons
NonCommercial terms do not permit commercial use without separate permission:
[CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/),
[Creative Commons license conditions](https://creativecommons.org/share-your-work/use-remix/cc-licenses/).
This metadata and validation are audit aids, not legal advice or a rights guarantee.

## Testing strategy

- Characterization tests lock v3 reads, current selection precedence, source
  immutability, rendering/mix behavior, and reusable worker contracts.
- Domain tests cover enharmonic tonic handling, modes, meter, chord qualities,
  progression edits, profile/mood resolution, and validation.
- Migration fixtures cover v1/v2/v3 -> v4 without rewrite-on-open; explicit save
  writes v4 atomically and preserves hashes/legacy selections.
- Stage-runner contract tests cover success, failure, crash recovery, retry,
  cache hits, configuration changes, stale propagation, and concurrent actions.
- Processor tests use MIDI fixtures and assert invariants plus edit/lineage
  reports, not only file existence.
- License/credits tests cover registry admission, exact final used-instrument
  lineage, attribution filtering/deduplication, and audio/credits hash pairing.
- UI tests cover wide/medium/narrow setup, harmony, parts, progress, failure,
  retry, bypass/A-B selection, keyboard access, and readiness.
- End-to-end offline tests use fake worker/model/renderer/audio devices. A manual
  acceptance matrix remains required for real transcription, renderer, model,
  listening, accessibility, and packaged legacy/new project flows.

The baseline captured for this plan passes `./gradlew test :desktopApp:test` and
40 Python worker tests in the available `.venv`; real dependency/manual gates
remain explicitly unverified.

## Migration strategy

1. Add v4 DTOs and pure migration without changing v3 open behavior.
2. Add profile/mood/harmony/settings contracts and UI with placeholder stage
   implementations.
3. Migrate parts and persisted structure occurrences while mapping existing
   `role`, MIDI references, AI-fix selections, and Lo-fi Feel.
4. Introduce the generic stage manifest alongside the existing artifact graph;
   dual-read legacy references, but write only the canonical v4 representation
   after explicit migration/save. Once supported fixtures migrate, delete the
   superseded runtime selection/storage path; retain only the minimum pure schema
   reader required by the declared project-version support window.
5. Migrate the v1 one-entry-per-logical-instrument registry through a read adapter;
   preserve current `piano`/`bass`/`drums`/`pad`/`strings` IDs and map them to
   roles without renaming files or breaking old mix/stem references. Materialize
   each legacy `LICENSES.json` record into the v2 instrument license snapshot while
   retaining the original registry/license hashes as evidence.
6. Move arrangement before cohesion behind compatibility adapters, invalidate
   downstream artifacts once, and require reapproval where input hashes change.
7. Add humanization and update render/mix/master handoffs.
8. Adapt a demonstrably used optional transport; otherwise migrate/export any
   required data and delete it. Delete every superseded path immediately after
   fixture migration and caller cutover prove it is unused.

No migration silently overwrites an original or derived artifact. Opening a
legacy project remains read-only; conversion occurs on an explicit save/migrate
action with recovery backup/atomic publication.

## Scope by horizon

### MVP — UI and contract milestone

- v4 composition settings, structured key/scale/meter, profile and mood IDs.
- Built-in versioned Lo-fi profile and initial structured mood definitions.
- Structured verse/chorus/bridge harmony editor with required chord qualities.
- Structured song parts and persistent structure occurrences.
- Automatic resumable import stage runner with visible progress and retry.
- Deterministic clean/normalize/transpose/correction contracts.
- Enhancement off/subtle/balanced/creative selection with a deterministic mock
  or conservative existing-engine adapter; corrected remains selectable.
- Updated readiness/navigation and compatibility migration.
- No advanced generative composition model.

### Later — complete musical pipeline

- Context-aware bounded AI enhancement with A/B approval.
- Profile-independent role-based arrangement with a metadata-rich Instrument
  Registry and deterministic, explainable instrument resolution.
- Arrangement-aware cohesion and seeded humanization.
- Updated rendering, profile style processing, mix/master, release provenance.
- Commercial-license admission and usage-based `*-credits.txt` export.
- Canonical Spring transport only if still valuable.

### Future — intentionally deferred

- Additional composition profiles and profile authoring/import.
- Advanced chord durations, inversions, slash chords, extensions, substitutions,
  modulations, tempo maps, and meter changes.
- Polyphonic/full-mix transcription, stem separation, collaboration, cloud
  generation, plugin hosting, or rights-management/DRM.
- Generating complete songs without substantive musician decisions.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Two project models diverge further | Declare the file-backed v4 aggregate canonical; make every transport an adapter. |
| Temporary migration code becomes permanent dead code | Every adapter/reader has an owner, removal condition, and deletion acceptance criteria; Task 030 fails on superseded runtime paths. |
| Deletion removes access to user data | Migrate/export atomically first, preserve artifact evidence/backups, and delete code only after supported-version fixtures reopen successfully. |
| Current workflow requires cohesion before arrangement | Migrate dependency ownership in an isolated task with compatibility fixtures and one-time invalidation. |
| Stage status references outputs that do not yet exist | Store run status separately; only completed records may carry validated output refs. |
| AI changes obscure authorship | Keep immutable branches, explicit intensity/approval, bounded edit reports, and quick bypass. |
| Source-key detection transposes incorrectly | Store confidence/evidence and require user confirmation below a threshold. |
| Profile abstraction becomes a generic rules engine | Use typed, versioned policy fields needed by current processors; defer arbitrary scripting. |
| Global stale flags cause excessive reruns | Compute dependencies from input/config hashes at part, occurrence, and release scope. |
| Long operations race UI actions | One persistent stage orchestrator owns locks and status; UI commands are idempotent. |
| Existing lo-fi assumptions leak into core code | Move defaults/prompts/instrument-selection criteria/DSP choices into the Lo-fi profile task by task. |
| Instrument affinity tags become arbitrary prompt strings | Use controlled IDs/enums and weighted affinities; keep free text display-only. |
| Resolver changes alter an approved song's timbre | Persist selected instrument ID, registry/resolver versions, criteria, scores, and hashes; never re-resolve an approved arrangement implicitly. |
| Installed libraries differ across machines | Treat unavailable selected IDs as an explicit render blocker and require reviewed substitution rather than silent fallback. |
| Declared SFZ capabilities are inaccurate | Verify detectable capabilities/assets during registry load and distinguish declared from verified metadata. |
| A registry falsely marks a noncommercial license as commercial | Validate normalized license IDs against a versioned allow/reject/review policy; semantic conflicts reject admission. |
| Required attribution is incomplete or credits include unused libraries | Require a ready-to-publish attribution block at admission and derive credits only from the final release's used-stem lineage. |
| Credits change after a library update | Freeze license/provenance snapshots in the approved assignment/release manifest and generate credits from that snapshot. |
| Provenance model metadata is incomplete | Block commercial-ready status when model identity/license/hash is unknown; do not fabricate defaults. |
| Automated tests overstate audio quality | Keep structural tests and require renderer/model/listening acceptance evidence. |

## Implementation phases

| Phase | Outcome | Tasks |
| --- | --- | --- |
| 0. Baseline and migration guardrails | Existing contracts characterized; schema/API truth made explicit | 001–002 |
| 1. Composition foundation | v4 settings, profiles, moods, project setup UI | 003–006 |
| 2. Harmony and parts | Structured chord editing and section-aware persistent parts | 007–010 |
| 3. Resumable part pipeline | Stage ledger, orchestration, clean/normalize/transpose/correct/enhance contracts and comparison UX | 011–020 |
| 4. Form and musical build | Persistent occurrences, role/sound intent, registry resolution, arrangement-aware cohesion, humanization | 021–025, including 022B |
| 5. Production and evidence | Render/mix/master handoff, provenance, usage-based credits, release verification | 026–027B, 030 |
| 6. Optional adapter | Spring API consolidation decision/adapter | 028 |

The ordered, implementation-ready specifications are indexed in
[docs/plan/tasks/README.md](docs/plan/tasks/README.md).

The sound-library catalog, resolver, compatibility, and provenance contract is
specified in [instrument-registry.md](docs/plan/instrument-registry.md) and split
between Tasks 022, 022B, and 027B so role intent, asset/license admission, and
release-credit rendering remain independent.
