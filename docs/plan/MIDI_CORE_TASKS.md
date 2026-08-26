# MIDI Core implementation and cleanup tasks

Status: ready for serial execution

Task range: MC-000 through MC-060

Scope: deterministic MIDI Core MVP, focused Compose Desktop UI, acceptance,
and complete removal of the superseded audio product

## 1. Authority

These are the only executable Melotrail implementation tasks. They implement
the root `PLAN.md` and the contracts in:

- `docs/ARCHITECTURE.md`
- `docs/FUNCTIONAL_SPEC.md`
- `docs/MIDI_CONTRACT.md`
- `docs/DAW_COMPATIBILITY.md`
- `docs/CLEANUP_SCOPE.md`
- `docs/QUALITY_GATES.md`

Do not execute tasks or prompts recovered from Git history. Pad generation,
Qwen, melody connection, variable tempo/meter, multiple source files, DAW
automation, and enhanced preview instruments are not mandatory work.

## 2. Baseline observed on 2026-08-26

- Approximately 53.6K production Kotlin lines and 4.2K worker Python lines.
- 31 application-service files and 66 files in the current arrangement package.
- A 3.6K-line workspace view model and 2.9K-line page router.
- A Python worker with 47 passing legacy tests.
- About 303 MB of ignored repository-local audio-project data.
- About 10 GB of ignored local sound-library data plus two tracked sound
  metadata files.
- About 15 MB of tracked legacy UI reference images.
- The root build still depends on a Python documentation-inventory checker,
  OkHttp, and Jackson.

These figures are inventory evidence, not targets to preserve. MC-059 records
the final reduction.

## 3. Execution contract

### 3.1 Ordering

- Execute tasks strictly in numeric order.
- Only one task may be `IN_PROGRESS` in the execution log.
- A task begins only after every earlier task is `DONE`, except a task marked
  `AWAITING_HUMAN` must be resolved before continuing past its phase gate.
- Do not batch several tasks into an unreviewable rewrite.

### 3.2 Task completion

For every task:

1. inspect the named current owners and their tests;
2. add or update focused regression tests;
3. implement the smallest complete target behavior;
4. switch target callers;
5. delete superseded code owned by that task when the replacement is live;
6. run the listed focused tests and `make test` unless the task explicitly says
   documentation/manual only;
7. run `make build` at every phase gate;
8. update `MIDI_CORE_EXECUTION_LOG.md` with commands, results, deletions, and
   commit; and
9. create one task commit named `midi-core: MC-NNN <imperative summary>`.

Do not mark a task done with failing tests, an unrecorded deletion, a hidden
legacy route, a TODO standing in for required behavior, or a compatibility
branch that contradicts the target architecture.

### 3.3 Existing changes

- Preserve unrelated user changes.
- Do not reset, checkout, clean, or overwrite a dirty worktree.
- MC-000 establishes whether the approved documentation baseline is already
  committed. If not, commit only those approved planning changes; do not absorb
  unrelated files.

### 3.4 Destructive work

The user has approved deletion of repository-owned old audio projects, Python,
sound libraries, bundled rejected-product media, obsolete code, tests, docs,
and fixtures. Destructive tasks must still:

- resolve and record exact targets first;
- remain inside the repository;
- never use an unresolved environment variable, glob, home directory, or
  workspace root as a recursive deletion target; and
- report whether tracked material is recoverable from Git and whether ignored
  generated/local data is not.

### 3.5 Manual gates

MC-009, MC-048, MC-049, and MC-060 require human evidence. The executing agent
must prepare everything it can, then request only the specific manual action.
It must not fabricate a DAW result, listening score, or sign-off.

## 4. Phase gates

- **G0 Documentation ready:** MC-000.
- **G1 MIDI compatibility proven:** MC-001–MC-009.
- **G2 MIDI project kernel complete:** MC-010–MC-019.
- **G3 Import-to-export vertical slice complete:** MC-020–MC-030.
- **G4 Focused desktop workflow complete:** MC-031–MC-040.
- **G5 Product behavior accepted:** MC-041–MC-049.
- **G6 Legacy product removed:** MC-050–MC-059.
- **G7 MVP complete:** MC-060.

## 5. Ordered tasks

## Phase 0 — Planning baseline

### MC-000 — Freeze the approved MIDI Core baseline

- **Depends on:** none.
- **Contracts:** all authoritative docs; Quality Gate 2.
- **Inspect:** repository status, recent history, root/docs links, current build
  commands, and this task suite.
- **Work:** verify there is one active plan; initialize the execution log with
  baseline branch/commit/status and inventory metrics; record any unrelated
  user changes that must be preserved.
- **Delete:** no implementation files. Remove only a newly discovered competing
  active plan after consolidating any unique target information.
- **Tests:** documentation-link audit and `git diff --check`.
- **Validation:** `make test`, `make build`.
- **Evidence:** baseline commit, status, counts, link audit, test/build results.
- **Commit:** `midi-core: MC-000 freeze execution baseline`.
- **Done when:** the approved docs and planning suite are committed, indexed,
  internally consistent, and the log identifies a clean execution baseline.

## Phase 1 — Standard MIDI compatibility and safety

### MC-001 — Establish owned Standard MIDI fixtures

- **Depends on:** MC-000.
- **Contracts:** F-MIDI-001, F-MIDI-004, MIDI Contract 2 and 5, Quality Gate 3.
- **Inspect:** existing MIDI test helpers and every checked-in MIDI fixture.
- **Work:** create small, license-safe deterministic fixtures for SMF 0, SMF 1,
  pickup timing, sub-bar harmony, controller/pitch data, velocity-zero note-off,
  final-boundary notes, multi-track references, and bounded malformed cases.
  Record fixture purpose and SHA-256 digests.
- **Delete:** duplicate or opaque fixture bytes that have no provenance or
  target assertion.
- **Tests:** a fixture-integrity test verifies headers, digests, and intent.
- **Validation:** focused fixture tests and `make test`.
- **Evidence:** fixture manifest and hashes.
- **Commit:** `midi-core: MC-001 add owned MIDI fixtures`.
- **Done when:** every later MIDI contract has a stable fixture and no fixture
  depends on old audio-project data.

### MC-002 — Characterize reusable MIDI and artifact behavior

- **Depends on:** MC-001.
- **Contracts:** F-MIDI-005, F-PROJ-004, F-SYS-004.
- **Inspect:** current Java MIDI users, project-store confinement/atomic-write
  behavior, hash helpers, stable IDs, generated-role writers, and their tests.
- **Work:** add characterization tests for behavior worth retaining: note/event
  extraction, tempo/meter/track-name handling, deterministic hashes, confined
  paths, atomic save recovery, and immutable candidate publication. Record a
  keep/extract/delete decision for each inspected owner in the log.
- **Delete:** no current owner until its target replacement is active.
- **Tests:** new characterization suite plus current affected tests.
- **Validation:** `make test`.
- **Evidence:** named behaviors and owning test methods.
- **Commit:** `midi-core: MC-002 characterize reusable MIDI safety`.
- **Done when:** later extraction can distinguish intentional behavior from
  audio-era incidental behavior.

### MC-003 — Enforce target dependency boundaries

- **Depends on:** MC-002.
- **Contracts:** Architecture 3 and 5; F-SYS-001, F-SYS-002.
- **Inspect:** root and desktop Gradle modules and current package dependencies.
- **Work:** introduce target package boundaries and an architecture test that
  prevents Compose/filesystem/HTTP/MIDI-device dependencies in domain code,
  prevents Compose from parsing raw MIDI, and confines `javax.sound.midi` to the
  MIDI/audition adapter boundary. Add ports only when used by the next tasks.
- **Delete:** empty legacy packages that truly contain no files; do not move all
  source mechanically.
- **Tests:** architecture/dependency rule tests.
- **Validation:** focused tests, `make test`, `make build`.
- **Evidence:** allowed dependency diagram and rule-test output.
- **Commit:** `midi-core: MC-003 enforce target boundaries`.
- **Done when:** violations fail tests and no placeholder service boundary or
  Python port is introduced.

### MC-004 — Implement the immutable semantic MIDI model

- **Depends on:** MC-003.
- **Contracts:** F-MIDI-001–F-MIDI-005; MIDI Contract 4.
- **Inspect:** existing `MidiNote`-like records and event-ordering logic.
- **Work:** implement immutable sequence, track, note, controller, pitch-bend,
  channel-pressure, tempo, time-signature, name, marker/text, and unsupported-
  event records with source identity and deterministic ordering keys. Define
  project PPQ/rational-beat conversion and one deterministic rounding policy.
- **Delete:** new duplicate note/event models created during this task; keep old
  runtime models only until migrated.
- **Tests:** value invariants, ordering, overflow, rational conversion, and
  rounding boundaries.
- **Validation:** focused tests and `make test`.
- **Evidence:** model contract and ordering golden output.
- **Commit:** `midi-core: MC-004 add semantic MIDI model`.
- **Done when:** domain/generator code can represent MIDI without importing Java
  MIDI classes.

### MC-005 — Implement the Standard MIDI reader and track inspector

- **Depends on:** MC-004.
- **Contracts:** F-MIDI-001–F-MIDI-003; MIDI Contract 2–5.
- **Inspect:** all current `MidiSystem` readers and track-analysis heuristics.
- **Work:** implement one adapter for SMF 0/1 PPQ parsing, stable source-event
  identity, note pairing, velocity-zero note-off, metadata/controller capture,
  unsupported-event findings, and per-track/channel summaries. Reject format 2
  and SMPTE before project mutation.
- **Delete:** target-path direct Java MIDI readers replaced by the adapter; old
  runtime readers remain only if still compiled by old callers.
- **Tests:** all MC-001 fixtures, ambiguous same-pitch overlap, orphan/unclosed
  notes, empty tracks, multi-channel melody facts, and truncated files.
- **Validation:** focused tests and `make test`.
- **Evidence:** deterministic inspection snapshots.
- **Commit:** `midi-core: MC-005 add Standard MIDI reader`.
- **Done when:** one read path produces the complete semantic/inspection model
  and does not repair source bytes.

### MC-006 — Implement blocking and advisory MIDI validation

- **Depends on:** MC-005.
- **Contracts:** F-MIDI-004, F-SYS-004; MIDI Contract 7.
- **Inspect:** current MIDI quality, monophony, transcription, normalization,
  analysis, and generated-role validators.
- **Work:** implement typed finding codes, scopes, severity, actionable message,
  and stable ordering. Encode exactly the blocking/advisory boundary from the
  MIDI contract, including fixed tempo/meter and selected-melody rules.
- **Delete:** no target validator may call audio/transcription quality gates.
- **Tests:** table-driven classification tests proving polyphony/chromatic notes
  are not blockers and malformed/unsupported timing is blocked.
- **Validation:** focused tests and `make test`.
- **Evidence:** finding-code catalog and fixture reports.
- **Commit:** `midi-core: MC-006 classify MIDI findings`.
- **Done when:** every import result is accepted, rejected, or awaiting explicit
  authority without vague exceptions.

### MC-007 — Implement the deterministic Standard MIDI writer

- **Depends on:** MC-006.
- **Contracts:** F-EXP-002, F-EXP-003; MIDI Contract 9–12.
- **Inspect:** current bass/drum/pad/string/full-song writers.
- **Work:** implement one writer for SMF format 1, conductor metadata, stable
  role track ordering, song end, section markers, channel remapping, controller
  policy, program/SysEx omission, and role files with conductor plus one track.
- **Delete:** target-path ad hoc writer helpers after their semantic tests move
  to the new writer.
- **Tests:** deterministic bytes/semantic events, channel 1/2/3/10 policy,
  marker/name sanitization, boundary note-offs, and no forbidden messages.
- **Validation:** focused tests and `make test`.
- **Evidence:** golden writer outputs and hashes.
- **Commit:** `midi-core: MC-007 add deterministic MIDI writer`.
- **Done when:** all target MIDI output can use one documented writer.

### MC-008 — Prove semantic re-import and a minimal export bundle

- **Depends on:** MC-007.
- **Contracts:** F-EXP-001–F-EXP-006; Quality Gate 3 persistence/export.
- **Inspect:** current export atomic-write/collision behavior without retaining
  audio encoding.
- **Work:** add semantic comparison, generated-file re-import, minimal immutable
  export snapshot, staging directory, manifest draft, collision refusal, and a
  test-only bundle containing conductor, melody, chords, bass, and drums.
- **Delete:** no audio export dependency may enter this path.
- **Tests:** semantic equality, digest mismatch, interrupted staging, existing
  destination, relative manifest paths, and aligned role files.
- **Validation:** focused tests, `make test`, `make build`.
- **Evidence:** fixture export bundle and validation report.
- **Commit:** `midi-core: MC-008 prove MIDI export round trip`.
- **Done when:** automated G1 behavior passes and one bundle is ready for both
  DAWs.

### MC-009 — Complete the early Logic Pro and GarageBand spike

- **Depends on:** MC-008.
- **Contracts:** F-EXP-007; DAW Compatibility 5–6.
- **Inspect:** the MC-008 bundle and current installed DAW/macOS versions.
- **Work:** prepare exact import steps and evidence form; ask the user to import
  complete and role files in Logic Pro and GarageBand; record pass, conditional
  pass, or fail for track names/count, tempo, meter, channels, markers, first/
  last boundaries, instrument assignment, and stuck notes. Fix deterministic
  export defects and repeat before closing the task.
- **Delete:** no code based on an unverified DAW assumption.
- **Tests:** rerun MC-008 automated tests after any fix.
- **Validation:** manual G1 matrix and `make build`.
- **Evidence:** exact versions, fixture hashes, user actions, results, and
  screenshots when available.
- **Commit:** `midi-core: MC-009 record DAW compatibility spike`.
- **Done when:** both DAWs pass or have an accepted, documented conditional pass.
  This task cannot be marked done from automated evidence alone.

## Phase 2 — MIDI project kernel

### MC-010 — Define the MIDI-only project schema

- **Depends on:** MC-009.
- **Contracts:** F-PROJ-001–F-PROJ-003; Architecture 4.1 and 6.
- **Inspect:** current schema-v4 project/envelope/validator only for reusable
  confinement and atomic-save behavior.
- **Work:** define a new explicitly discriminated MIDI Core schema version 1
  containing metadata, source MIDI identity, selected melody, musical
  authority, candidate records, acceptances/locks, and export snapshots. Keep
  DTO validation separate from domain records.
- **Delete:** no migration DTO or dual-write field. Old schema remains readable
  only long enough to return the unsupported-project classification.
- **Tests:** encode/decode, required fields, unknown/invalid versions, path
  confinement, and stable serialized golden document.
- **Validation:** focused tests and `make test`.
- **Evidence:** schema fixture and field ownership map.
- **Commit:** `midi-core: MC-010 define MIDI project schema`.
- **Done when:** no audio/render/part-stage field exists in the target schema.

### MC-011 — Implement the target artifact store

- **Depends on:** MC-010.
- **Contracts:** F-PROJ-004, F-SYS-004; Architecture 6.
- **Inspect:** current project store, immutable-source publication, SHA-256, and
  recovery evidence.
- **Work:** implement confined paths, original source storage, immutable
  candidate/report/export paths, digest verification, temporary/atomic JSON
  writes, and failure recovery under the target layout.
- **Delete:** target code must not depend on workflow-stage artifact enums.
- **Tests:** traversal/symlink escape, missing/mismatched artifacts, partial
  write, collision, immutable republish, and recovery from last known-good file.
- **Validation:** focused tests and `make test`.
- **Evidence:** target tree fixture and fault-injection results.
- **Commit:** `midi-core: MC-011 add MIDI artifact store`.
- **Done when:** every target artifact operation is confined, digest-bound, and
  crash-safe.

### MC-012 — Implement create, open, save, and legacy rejection

- **Depends on:** MC-011.
- **Contracts:** F-PROJ-001–F-PROJ-004.
- **Inspect:** current project application service and desktop project dialogs.
- **Work:** add focused project use cases for create/open/save/close; validate
  before mutation; classify old schema/audio projects as unsupported without
  migration or worker startup; return UI-ready problems.
- **Delete:** no target caller may open through schema-v4 application services.
- **Tests:** create/reopen, corrupted/missing artifacts, atomic save failure,
  old-project rejection, and no writes during rejection.
- **Validation:** focused tests and `make test`.
- **Evidence:** project lifecycle snapshots and legacy-rejection result.
- **Commit:** `midi-core: MC-012 add project lifecycle`.
- **Done when:** a new target project reopens identically and old projects fail
  safely without side effects.

### MC-013 — Implement immutable MIDI source import

- **Depends on:** MC-012.
- **Contracts:** F-MIDI-001, F-MIDI-002, F-MIDI-004, F-MIDI-005.
- **Inspect:** current direct-MIDI import path for safe copying only; do not
  retain audio inspection, clean, normalize, transpose, or transcription.
- **Work:** validate extension/header, inspect source, copy bytes immutably,
  verify digest, write import report/track summaries, and atomically bind the
  source record. Failed import leaves project unchanged.
- **Delete:** target import has no generic input-container/audio branch.
- **Tests:** supported fixtures, renamed non-MIDI file, source mutation during
  copy, duplicate import, failure cleanup, and original-byte preservation.
- **Validation:** focused tests and `make test`.
- **Evidence:** source/report artifacts and before/after project hashes.
- **Commit:** `midi-core: MC-013 import immutable MIDI source`.
- **Done when:** one MIDI source is the sole project input and no preparation
  stage is required.

### MC-014 — Implement protected melody selection and view

- **Depends on:** MC-013.
- **Contracts:** F-MIDI-003, F-MIDI-005; Musical invariants.
- **Inspect:** current melody identity/anchor behavior for reusable semantic
  assertions, not mutation stages.
- **Work:** select exactly one track/channel, reject unsupported multi-channel
  expressive melody, derive an immutable channel-1 export view, preserve source
  controllers under policy, and compute protected anchors/identity digest.
- **Delete:** no automatic clean/monophonic/AI-fix/connection step in target
  melody selection.
- **Tests:** track/channel selection, format-0 resolution, controller mapping,
  source immutability, selection change, and unsupported MPE-like input.
- **Validation:** focused tests and `make test`.
- **Evidence:** melody identity/digest and semantic diff showing no source edit.
- **Commit:** `midi-core: MC-014 protect selected melody`.
- **Done when:** every derived arrangement can identify the exact immutable
  melody authority.

### MC-015 — Implement fixed tempo, meter, key, and mode authority

- **Depends on:** MC-014.
- **Contracts:** F-AUTH-001, F-AUTH-002.
- **Inspect:** musical primitives, composition settings, source-key evidence,
  and current harmony types.
- **Work:** extract/reuse validated tempo, time signature, pitch spelling, key,
  and mode primitives; support imported suggestions plus explicit confirmation;
  block tempo/meter maps; serialize authority without render/audio settings.
- **Delete:** target composition settings must not include production profile,
  sound library, render format, or model configuration.
- **Tests:** boundaries, missing metadata confirmation, unsupported maps,
  enharmonic spelling, chromatic compatibility advisory, and reopen.
- **Validation:** focused tests and `make test`.
- **Evidence:** authority JSON and validation findings.
- **Commit:** `midi-core: MC-015 add core musical authority`.
- **Done when:** one confirmed tempo/meter/key/mode controls all timing and
  harmonic consumers.

### MC-016 — Implement exact section occurrence timelines

- **Depends on:** MC-015.
- **Contracts:** F-AUTH-003; Architecture 4.3 and 7.
- **Inspect:** current structure occurrences, song timeline, MIDI time mapping,
  and canonical-authority occurrence construction.
- **Work:** implement stable section definition/occurrence IDs, explicit start
  and duration in musical position/ticks, ordered insert/duplicate/move/remove,
  contiguous coverage validation, pickup policy, and deterministic markers.
- **Delete:** no duration inference from analysis files or raw melody end.
- **Tests:** repeated labels, sub-bar/pickup boundaries, gaps/overlaps, exact
  ticks across supported meters, mutations, and serialization.
- **Validation:** focused tests and `make test`.
- **Evidence:** timeline fixture with marker/tick map.
- **Commit:** `midi-core: MC-016 add exact occurrence timeline`.
- **Done when:** one timeline is the only section/tick authority.

### MC-017 — Implement duration-aware authoritative harmony

- **Depends on:** MC-016.
- **Contracts:** F-AUTH-004; MIDI Contract 8.
- **Inspect:** chord domain, formatter, harmony application service, and current
  harmonic timeline.
- **Work:** extract/reuse chord parsing/realization; bind chord events with exact
  durations to section/occurrence windows; support sub-bar and chromatic chords;
  expose syntax/realizability blockers and key compatibility advisories.
- **Delete:** template/analysis fallback that silently replaces a valid
  authoritative chord.
- **Tests:** slash/extension/chromatic chords, sub-bar coverage, gaps/overlaps,
  repeated sections, invalid symbols, transposition independence, and reopen.
- **Validation:** focused tests and `make test`.
- **Evidence:** harmonic timeline golden output.
- **Commit:** `midi-core: MC-017 add authoritative harmony`.
- **Done when:** every song tick resolves to exactly the user-approved chord
  window required by generation.

### MC-018 — Implement authority hashes and dependency-aware invalidation

- **Depends on:** MC-017.
- **Contracts:** F-AUTH-005, F-REV-005; Architecture 7–8.
- **Inspect:** current stale workflow artifacts, context hashes, project
  mutation coordinator, and arrangement fingerprints.
- **Work:** define canonical authority serialization/hash; record source,
  melody, timing, structure, harmony, settings, generator, and accepted
  dependency inputs; compute affected role/occurrence invalidation before
  mutation; reject stale async completion.
- **Delete:** no global “mark everything current/stale” shortcut where scoped
  dependency information exists.
- **Tests:** each authority dimension, chorus-only changes, dependency role
  changes, stale concurrent generation, preview of impact, and no artifact
  deletion.
- **Validation:** focused tests and `make test`.
- **Evidence:** invalidation matrix and hash fixtures.
- **Commit:** `midi-core: MC-018 add scoped invalidation`.
- **Done when:** stale work is precise, explainable, and cannot become current.

### MC-019 — Implement candidate, acceptance, lock, and export-snapshot records

- **Depends on:** MC-018.
- **Contracts:** F-REV-001–F-REV-005, F-EXP-001, F-SYS-003.
- **Inspect:** current arrangement state, accepted track evidence, artifact
  references, and release lineage only for generic safe patterns.
- **Work:** add immutable candidate/report records, status, stable ID, role,
  occurrence, seed/version/profile/pattern, authority hash, file digest,
  accepted reference, rejection, lock, prior acceptance history, and immutable
  export snapshot records.
- **Delete:** no release/commercial/audio lineage field in the target records.
- **Tests:** state transitions, digest/authority checks, lock enforcement,
  rejection, restore prior acceptance, stale snapshot, serialization, and
  collision-free IDs.
- **Validation:** focused tests, `make test`, `make build`.
- **Evidence:** lifecycle state-machine tests and schema fixture.
- **Commit:** `midi-core: MC-019 add candidate lifecycle records`.
- **Done when:** G2 passes and no accepted work is represented by a mutable
  filename alone.

## Phase 3 — Complete import-to-export vertical slice

### MC-020 — Establish shared generation context and curated patterns

- **Depends on:** MC-019.
- **Contracts:** F-ARR-004, F-ARR-006, F-ARR-007; Architecture 4.4.
- **Inspect:** musical pattern library, section policy, arrangement harmony
  context, density/space maps, and composition profiles.
- **Work:** define one immutable generation request containing authority
  snapshot, occurrence/chord windows, protected melody notes, accepted
  dependency context, performance profile, pattern ID, generator version, and
  explicit seed. Extract only the bass, chord-rhythm, drum-groove, and fill
  catalog behavior needed by the target.
- **Delete:** transition, strings, AI planner, sound-library, and production-
  profile choices from the target context. Do not yet delete a legacy owner
  still compiled by old callers.
- **Tests:** context hash stability, allowed pattern IDs, representable tick
  grids, occurrence scoping, and deterministic seed behavior.
- **Validation:** focused tests and `make test`.
- **Evidence:** context fixture and extracted pattern inventory.
- **Commit:** `midi-core: MC-020 define generation context`.
- **Done when:** all three core roles can consume one authority representation
  without reading project files or analysis sidecars.

### MC-021 — Implement target role validation

- **Depends on:** MC-020.
- **Contracts:** F-ARR-005; Quality Gate 3 generation.
- **Inspect:** generated-role validation, bass quality validation, low-end
  interaction, melody collision, and range/density checks.
- **Work:** implement common candidate validation plus chord, bass, and drum
  policies for occurrence bounds, positive duration, representable ticks,
  channel/range, harmony windows, protected melody collisions, density, and
  allowed event types. Return typed findings and rejection without publication.
- **Delete:** target validators for piano/pad/strings/transitions and render-
  dependent checks.
- **Tests:** one failing/passing case per policy, chromatic authority, hard vs
  advisory collision, and rejected artifact admission.
- **Validation:** focused tests and `make test`.
- **Evidence:** role-policy matrix.
- **Commit:** `midi-core: MC-021 validate core roles`.
- **Done when:** every generated core candidate has one deterministic report
  before storage or acceptance.

### MC-022 — Implement the chord/keys accompaniment generator

- **Depends on:** MC-021.
- **Contracts:** F-ARR-001, F-ARR-004–F-ARR-007.
- **Inspect:** deterministic pad generation, chord rhythm patterns, voicing,
  voice leading, and current pad tests.
- **Work:** extract a `Chords` role generator using authoritative chord windows,
  complete curated rhythm variants, bounded register/spacing/inversion, voice
  leading, melody-space policy, performance intent, and deterministic seed.
  Output semantic notes only.
- **Delete:** target naming and behavior that promises an audio pad. Preserve
  old pad adapter only until legacy callers are removed in MC-055.
- **Tests:** chord tones/extensions, inversions, sub-bar changes, repeated
  sections, register, melody space, deterministic alternatives, and rejection.
- **Validation:** focused tests and `make test`.
- **Evidence:** per-pattern semantic golden files.
- **Commit:** `midi-core: MC-022 generate chord candidates`.
- **Done when:** a valid occurrence can produce at least two useful, distinct,
  deterministic chord candidates.

### MC-023 — Implement the bass generator and performance profiles

- **Depends on:** MC-022.
- **Contracts:** F-ARR-002, F-ARR-004–F-ARR-007.
- **Inspect:** deterministic bass generator, bass pattern catalog, bass quality
  validator, phrase/melody activity, and low-end interaction.
- **Work:** adapt bass generation to use only target authority and accepted
  context. Implement sustained/sub-like and muted/plucked performance profiles,
  bounded roots/fifths/octaves/approaches, voice continuity, phrase boundaries,
  and deterministic alternatives.
- **Delete:** analysis-confidence harmony substitution, instrument-renderer
  assumptions, and generic stem-adapter behavior from the target path.
- **Tests:** all profiles/patterns, slash chords, approach-tone legality,
  movement/range/leaps, melody collision, phrase boundary, determinism, and
  invalid-candidate fallback/rejection.
- **Validation:** focused tests and `make test`.
- **Evidence:** profile/pattern golden candidates.
- **Commit:** `midi-core: MC-023 generate bass candidates`.
- **Done when:** bass candidates obey exact authoritative chord windows and do
  not require analysis or sound-library data.

### MC-024 — Implement the complete-variant drum generator

- **Depends on:** MC-023.
- **Contracts:** F-ARR-003–F-ARR-006; MIDI Contract 10.
- **Inspect:** deterministic drum generation, curated grooves/fills, current
  density decimation, kick additions, and GM mappings.
- **Work:** implement complete authored groove variants for supported energy
  levels, phrase-boundary fills, occurrence purpose, accepted bass kick intent,
  deterministic velocity shaping, allowed GM pitches, and channel 10 output.
  Density selects a complete variant rather than deleting arbitrary steps.
- **Delete:** decimation and context code that mutates an authored pattern into
  an incomplete groove; remove renderer-specific drum maps from target logic.
- **Tests:** each full variant, fill placement, no cross-boundary hits, kick/bass
  context, GM pitches/channel, deterministic velocities, and no arbitrary step
  deletion.
- **Validation:** focused tests and `make test`.
- **Evidence:** complete groove/fill golden sequences.
- **Commit:** `midi-core: MC-024 generate drum candidates`.
- **Done when:** drums form coherent complete variants and pass target role
  validation without a sound library.

### MC-025 — Implement candidate generation and immutable publication use cases

- **Depends on:** MC-024.
- **Contracts:** F-ARR-001–F-ARR-006, F-SYS-004.
- **Inspect:** current arrangement application service candidate staging and
  project mutation coordinator.
- **Work:** add one application use case to generate a requested role/
  occurrence alternative off the UI thread, validate it, publish MIDI/report
  immutably, then atomically append its candidate record only if authority is
  still current. Support cancellation and collision-free retry.
- **Delete:** target generation must not generate all roles/song, render stems,
  approve automatically, or mutate accepted references.
- **Tests:** each role, cancellation before/after publication, stale completion,
  validation rejection, disk failure, concurrent requests, and immutable files.
- **Validation:** focused tests and `make test`.
- **Evidence:** generated candidate tree and state transition log.
- **Commit:** `midi-core: MC-025 publish generated candidates`.
- **Done when:** generation is scoped, safe, and independent from acceptance.

### MC-026 — Implement candidate review mutations

- **Depends on:** MC-025.
- **Contracts:** F-REV-001–F-REV-005, F-ARR-006.
- **Inspect:** current arrangement-state incremental acceptance and approval
  references.
- **Work:** implement list/compare/accept/reject/lock/unlock/restore/regenerate
  commands with optimistic project revision, authority/digest revalidation,
  prior acceptance history, and exact affected-scope invalidation.
- **Delete:** automatic approval and mutable “current output path” selection in
  target use cases.
- **Tests:** full state machine, wrong occurrence/role, stale/digest mismatch,
  locked replacement, restore previous, targeted regeneration, and concurrent
  revision conflict.
- **Validation:** focused tests and `make test`.
- **Evidence:** state-machine test matrix.
- **Commit:** `midi-core: MC-026 review arrangement candidates`.
- **Done when:** the user is the only authority that changes accepted pointers.

### MC-027 — Implement semantic candidate diff and accepted-song assembly

- **Depends on:** MC-026.
- **Contracts:** F-REV-001, F-REV-006; MIDI Contract 8–10.
- **Inspect:** current arrangement state context, occurrence clipping, full-song
  assembly, and stage comparison only for reusable event comparison.
- **Work:** implement event-level candidate diff summaries and assemble the
  protected melody plus current accepted role candidates at exact occurrence
  positions. Detect gaps, duplicate role scope, stale records, digest mismatch,
  overflow, and role/channel policy before producing a review sequence.
- **Delete:** no source-song connection, cohesion, critic, humanization, or
  rendering step in assembly.
- **Tests:** repeated occurrences, pickup, sub-bar changes, role silence policy,
  stale/missing candidates, diff additions/removals/changes, channel mapping,
  and source identity preservation.
- **Validation:** focused tests and `make test`.
- **Evidence:** assembled semantic golden sequence and melody comparison.
- **Commit:** `midi-core: MC-027 assemble accepted song`.
- **Done when:** accepted state produces one deterministic review sequence
  without rewriting source/candidates.

### MC-028 — Implement minimal local MIDI audition

- **Depends on:** MC-027.
- **Contracts:** F-PLAY-001–F-PLAY-004, F-SYS-001.
- **Inspect:** existing playback session state and lifecycle patterns; do not
  reuse audio player/renderer behavior.
- **Work:** define audition port/session state and a JVM MIDI sequencer/output
  adapter for play, pause, stop, seek, loop, scope selection, mute, and solo.
  Ensure all-notes-off/resource cleanup and recoverable unavailable-device
  errors. Keep preview timbre non-authoritative.
- **Delete:** no WAV preparation, matched loudness, renderer, sound-library,
  codec preview, or audio playback dependency in the target path.
- **Tests:** fake adapter state machine, seek/loop boundaries, mute/solo, rapid
  start/stop, superseded sessions, device loss, cleanup, and no project writes.
- **Validation:** focused tests and `make test`.
- **Evidence:** session-state traces and leak/resource assertions.
- **Commit:** `midi-core: MC-028 add MIDI audition`.
- **Done when:** source/candidate/occurrence/role/full arrangement can be
  auditioned through one non-audio contract.

### MC-029 — Implement the vertical MIDI package exporter

- **Depends on:** MC-028.
- **Contracts:** F-EXP-001–F-EXP-006, F-SYS-003; MIDI Contract 9–14.
- **Inspect:** MC-008 spike and generic atomic/collision patterns from old export.
- **Work:** capture current export snapshot, assemble complete and core role
  files, write manifest minimum fields/instrument suggestions, semantic-reimport
  every file, stage atomically, publish a new snapshot directory, and record
  validation results. Refuse missing/stale/unaccepted roles and silent overwrite.
- **Delete:** no WAV/MP3/export-format/master/commercial option in the target
  service.
- **Tests:** complete success, each blocker, deterministic file ordering/hashes,
  manifest portability, failed staging, collision, optional-role omission, and
  re-opened snapshot.
- **Validation:** focused tests and `make test`.
- **Evidence:** complete fixture package and manifest.
- **Commit:** `midi-core: MC-029 export MIDI package`.
- **Done when:** a kernel project can export the exact package contract without
  desktop or legacy runtime involvement.

### MC-030 — Prove the kernel vertical slice end to end

- **Depends on:** MC-029.
- **Contracts:** F-PROJ-001 through F-EXP-006; G3.
- **Inspect:** all target use cases and test composition root.
- **Work:** add a JVM end-to-end test that creates a project, imports SMF,
  selects melody, confirms authority, defines occurrences/harmony, generates at
  least two alternatives per role, rejects/accepts/locks, reopens, assembles,
  auditions through a fake port, exports, and semantic-reimports the package.
  Include one failure/recovery path and regeneration immutability assertion.
- **Delete:** no legacy service may be used by this test.
- **Tests:** the new E2E plus all target package tests.
- **Validation:** `make test`, `make build`.
- **Evidence:** project/export tree, source/candidate/export hashes, test output.
- **Commit:** `midi-core: MC-030 prove vertical slice`.
- **Done when:** G3 passes entirely without Python, audio, old schema, or DAW
  installation.

## Phase 4 — Focused Compose Desktop workflow

### MC-031 — Compose the desktop from target services

- **Depends on:** MC-030.
- **Contracts:** Architecture 3 and 4.8; F-SYS-001.
- **Inspect:** desktop service composition, preferences, file dialogs, operation
  logger, and current service graph.
- **Work:** build a small target composition root wiring project, import,
  authority, generation, review, audition, and export use cases. Retain useful
  desktop dialogs/preferences/logging only after removing worker/sound/audio
  settings from their contracts. Make target composition the default entrypoint.
- **Delete:** no target composition of worker, renderer, mix, release, Qwen, or
  sound-library services. Old graph may remain unreachable until MC-050/MC-051.
- **Tests:** composition smoke test and absence of network/worker construction.
- **Validation:** focused desktop tests and `make test`.
- **Evidence:** service graph and startup test.
- **Commit:** `midi-core: MC-031 compose target desktop`.
- **Done when:** the default app can start with target services only.

### MC-032 — Implement focused workspace state and intents

- **Depends on:** MC-031.
- **Contracts:** F-UI-002, F-UI-003, F-UI-005; F-SYS-004.
- **Inspect:** operation feedback, creation progress, workspace state/intents,
  coroutine dispatchers, retry/cancel, and restart hydration.
- **Work:** introduce a target workspace state/view model containing only
  project, MIDI import/selection/findings, authority, candidate/review,
  audition, export, operation, dialog, and actionable blocker state. Route all
  mutation through application use cases; admit async results by project
  revision/authority hash.
- **Delete:** no audio preparation, source song, cohesion, mix, library, video,
  commercial, model, or master state in the target model.
- **Tests:** intent routing, busy/cancel/retry, stale completion, restart
  hydration, unsaved authority draft, and blocker explanations.
- **Validation:** focused desktop tests and `make test`.
- **Evidence:** state/intent inventory and test traces.
- **Commit:** `midi-core: MC-032 add focused workspace state`.
- **Done when:** target UI behavior no longer depends on the giant legacy state
  model.

### MC-033 — Replace navigation with six destinations

- **Depends on:** MC-032.
- **Contracts:** F-UI-001, F-UI-004.
- **Inspect:** shell frame, workspace sections/destinations/tags, keyboard and
  accessibility behavior.
- **Work:** implement Project, MIDI, Structure & Harmony, Arrange, Review, and
  Export navigation with stable semantics, current-project context, responsive
  layout, and no hidden legacy route. Treat device/export preferences as small
  contextual dialogs rather than top-level Settings.
- **Delete:** Mix/Master, Library, Video Preview, Release, old Overview/Import/
  Harmony route duplication, and Settings navigation from target shell.
- **Tests:** six destinations, keyboard traversal, selected state, narrow/wide
  layout, unavailable-page blockers, and absence of legacy labels/routes.
- **Validation:** focused UI tests and `make test`.
- **Evidence:** semantic-tree snapshots.
- **Commit:** `midi-core: MC-033 focus workspace navigation`.
- **Done when:** only the six target destinations are reachable.

### MC-034 — Implement the Project page

- **Depends on:** MC-033.
- **Contracts:** F-PROJ-001–F-PROJ-004, F-UI-002–F-UI-005.
- **Inspect:** existing project setup/dialog components and file dialogs.
- **Work:** add create/open/recent/current project actions, authority/readiness
  summary, project location, safe error/recovery messages, unsupported legacy
  explanation, and navigation to the next incomplete target step.
- **Delete:** render format, sound profile, source-part count, commercial, and
  worker readiness controls from the target page.
- **Tests:** create/open/reopen, old-project rejection, invalid path, save error,
  recent project, keyboard/accessibility, and blocker navigation.
- **Validation:** focused UI tests and `make test`.
- **Evidence:** page semantics/screenshots and lifecycle tests.
- **Commit:** `midi-core: MC-034 build Project page`.
- **Done when:** a user can establish/reopen the target project without seeing
  audio-era setup.

### MC-035 — Implement the MIDI page

- **Depends on:** MC-034.
- **Contracts:** F-MIDI-001–F-MIDI-005, F-PLAY-001–F-PLAY-004.
- **Inspect:** current import dialog, track presentation, MIDI preview actions,
  and file dialogs only for reusable interaction behavior.
- **Work:** implement one-file MIDI import, source facts, track/channel table,
  melody selection, blocking/advisory findings, source digest/immutability
  evidence, and MIDI transport/loop. Explain unsupported formats/maps/MPE.
- **Delete:** audio import toggle, provenance attestation, inspect/clean/
  transcribe/normalize/transpose/AI-fix/enhance/MIDI-feel controls from target UI.
- **Tests:** import flow, selection, format-0/1 tracks, findings, source audition,
  device failure, async/cancel, semantics, and no old action labels.
- **Validation:** focused UI tests and `make test`.
- **Evidence:** page semantics/screenshots and imported fixture state.
- **Commit:** `midi-core: MC-035 build MIDI page`.
- **Done when:** source import through protected melody approval is fully usable
  from the desktop.

### MC-036 — Implement the Structure & Harmony page

- **Depends on:** MC-035.
- **Contracts:** F-AUTH-001–F-AUTH-005.
- **Inspect:** harmony editor, project setup musical controls, structure list,
  occurrence mutations, and current conflict handling.
- **Work:** implement tempo/meter/key/mode confirmation, section definitions,
  occurrence order/length editing, exact bar/beat/tick feedback, duration-aware
  chord editor, coverage/findings, chromatic advisories, invalidation preview,
  and source/occurrence audition.
- **Delete:** analysis-derived duration, song-part roles, variation instrument
  overrides, source-key correction/transposition, and template fallback from
  target UI.
- **Tests:** all authority mutations, sub-bar chords, repeated sections,
  gaps/overlaps, chromatic chords, invalidation confirmation, stale edit
  conflict, accessibility, and restart.
- **Validation:** focused UI tests and `make test`.
- **Evidence:** authority editing screenshots and persisted fixture.
- **Commit:** `midi-core: MC-036 build Structure and Harmony page`.
- **Done when:** the user can author the complete generation authority without
  editing JSON.

### MC-037 — Implement the Arrange page

- **Depends on:** MC-036.
- **Contracts:** F-ARR-001–F-ARR-007, F-UI-002–F-UI-004.
- **Inspect:** current arrange controls and progress feedback only for reusable
  UI patterns.
- **Work:** implement occurrence/role selection, allowed performance profiles/
  patterns, seed/alternative request, candidate list/status, validation summary,
  targeted regeneration, async progress/cancel, and navigation to review.
- **Delete:** planner/model selector, instrument catalog, optional strings/pad,
  cohesion, render-stem, build-song, and auto-approval controls from target UI.
- **Tests:** each core role, blockers, multiple alternatives, cancellation,
  rejected candidate, stale result, targeted scope, accessibility, and no legacy
  controls.
- **Validation:** focused UI tests and `make test`.
- **Evidence:** page semantics/screenshots and candidate IDs.
- **Commit:** `midi-core: MC-037 build Arrange page`.
- **Done when:** generation is understandable, scoped, and never silently
  accepts output.

### MC-038 — Implement the Review page and integrated audition

- **Depends on:** MC-037.
- **Contracts:** F-REV-001–F-REV-006, F-PLAY-001–F-PLAY-004.
- **Inspect:** current comparison/approval/preview components only for reusable
  presentation patterns.
- **Work:** implement alternative comparison, semantic diff, findings, accept/
  reject/lock/unlock/restore, current arrangement assembly, occurrence/role/full
  audition, mute/solo/loop/seek, stale explanations, and targeted return to
  Arrange.
- **Delete:** source-song approval, cohesion boundary review, critic, full-song
  enhancement, humanization, dry/lo-fi/master A/B, and matched-audio preview from
  target UI.
- **Tests:** full candidate state machine through UI, audition scopes, locks,
  stale/missing artifacts, keyboard/semantics, device errors, and restart.
- **Validation:** focused UI tests and `make test`.
- **Evidence:** page semantics/screenshots and accepted-state hashes.
- **Commit:** `midi-core: MC-038 build Review page`.
- **Done when:** the user can make every arrangement approval decision in one
  focused view.

### MC-039 — Implement the Export page

- **Depends on:** MC-038.
- **Contracts:** F-EXP-001–F-EXP-007, F-UI-002–F-UI-004.
- **Inspect:** current destination/file-dialog, status/recovery, and clipboard
  patterns; do not reuse audio release options.
- **Work:** show export readiness/blockers, snapshot summary, exact complete/
  role filenames, authority/candidate hashes, instrument suggestions, output
  destination, overwrite collision decision, progress/cancel, validation result,
  reveal-folder action, and Logic/GarageBand import guidance.
- **Delete:** audio format/quality/sample-rate, master preview, credits,
  commercial evidence, and Mix/Master recovery from target UI.
- **Tests:** ready/blocked/stale states, destination/collision, success/failure,
  cancellation, manifest summary, DAW guidance, accessibility, and reopen.
- **Validation:** focused UI tests and `make test`.
- **Evidence:** page semantics/screenshots and exported fixture package.
- **Commit:** `midi-core: MC-039 build Export page`.
- **Done when:** the user can create and understand the exact DAW MIDI package.

### MC-040 — Prove the focused desktop workflow

- **Depends on:** MC-039.
- **Contracts:** F-UI-001–F-UI-005; G4.
- **Inspect:** all target UI tests and desktop entrypoint.
- **Work:** add an end-to-end Compose test for create/import/select/authority/
  arrange/review/reopen/export using target services and fake audition. Add
  focused visual fixtures only for the six target pages and a desktop smoke
  checklist for real file dialogs/device output.
- **Delete:** this test must not instantiate a legacy service or depend on old
  screenshots/audio/sound libraries.
- **Tests:** fresh target JVM and desktop suites.
- **Validation:** `make test`, `make build`, manual desktop smoke without DAW.
- **Evidence:** E2E output, six-page semantics, screenshots, smoke record.
- **Commit:** `midi-core: MC-040 prove desktop workflow`.
- **Done when:** G4 passes and default navigation completes the whole target
  workflow without an old route.

## Phase 5 — Musical depth, hardening, and acceptance

### MC-041 — Harden chord alternatives and voicing

- **Depends on:** MC-040.
- **Contracts:** F-ARR-001, F-ARR-004–F-ARR-007; musical rubric.
- **Inspect:** target chord generator outcomes across the three development
  fixtures and reusable pad/chord rhythm tests.
- **Work:** complete the approved chord-rhythm catalog, improve voice-leading,
  common-tone retention, inversions, spacing, register, phrase-boundary
  behavior, melody space, section development, and distinct alternative policy.
  Keep changes deterministic and parameter-bounded.
- **Delete:** unused pad/transition rhythm variants and any generator fallback
  that changes authoritative harmony.
- **Tests:** pattern completeness, movement limits, chord-window correctness,
  alternative distinctness, repeated-section development, and regressions from
  listening findings.
- **Validation:** focused tests, `make test`, three-fixture listening review.
- **Evidence:** before/after candidate hashes and rubric notes.
- **Commit:** `midi-core: MC-041 harden chord accompaniment`.
- **Done when:** every development fixture has at least two valid chord
  alternatives with no rubric score below 3.

### MC-042 — Harden bass profiles and musical motion

- **Depends on:** MC-041.
- **Contracts:** F-ARR-002, F-ARR-004–F-ARR-007; musical rubric.
- **Inspect:** target bass results, retained bass patterns, voice continuity,
  slash chords, melody activity, and phrase boundaries.
- **Work:** refine sustained/sub-like and muted/plucked profiles, note lengths,
  velocity/articulation intent, root/approach/octave choices, direction,
  repetition control, section development, and deterministic alternatives.
- **Delete:** profile names/logic that promise an exact rendered timbre or depend
  on analysis confidence/instrument files.
- **Tests:** profile contrast, chord correctness, approaches, leap/range,
  repetition, phrase/section context, melody space, and deterministic regression.
- **Validation:** focused tests, `make test`, three-fixture listening review.
- **Evidence:** profile candidates and rubric notes.
- **Commit:** `midi-core: MC-042 harden bass arrangements`.
- **Done when:** both performance profiles are musically distinguishable and no
  development bass score is below 3.

### MC-043 — Harden drum grooves, fills, and energy

- **Depends on:** MC-042.
- **Contracts:** F-ARR-003–F-ARR-007; musical rubric.
- **Inspect:** target complete grooves/fills across section purposes and accepted
  bass contexts.
- **Work:** refine authored low/medium/high energy variants, kick/snare/hat
  coherence, velocity hierarchy, phrase fills, transition restraint, repeated-
  section development, and bass-aware kick choices without editing accepted
  bass.
- **Delete:** arbitrary hit decimation/addition and renderer/sample-map
  assumptions from pattern policy.
- **Tests:** complete patterns, energy contrast, fill limits, GM map/channel,
  bass context, cross-boundary safety, and deterministic regression.
- **Validation:** focused tests, `make test`, three-fixture listening review.
- **Evidence:** groove candidates and rubric notes.
- **Commit:** `midi-core: MC-043 harden drum arrangements`.
- **Done when:** all development grooves sound complete and no drum score is
  below 3.

### MC-044 — Harden ensemble interaction without a rewrite stage

- **Depends on:** MC-043.
- **Contracts:** F-ARR-005, F-REV-006; musical invariants and rubric.
- **Inspect:** target assembly, accepted dependency context, reusable space/
  density/low-end evidence, and development fixture collisions.
- **Work:** add deterministic pre-generation context and validation for melody/
  chord register space, chord/bass separation, kick/bass intent, aggregate
  density, and section energy. Regenerate only the selected role/occurrence;
  never add a post-arrangement cohesion or global polish rewrite.
- **Delete:** target references to cohesion, critic, global planner, or whole-song
  mutation as a quality remedy.
- **Tests:** interaction windows, accepted dependency changes, scoped
  invalidation, dense/sparse fixtures, no melody mutation, and targeted-only
  repair.
- **Validation:** focused tests, `make test`, complete development-song review.
- **Evidence:** ensemble findings and targeted candidate replacements.
- **Commit:** `midi-core: MC-044 harden ensemble interaction`.
- **Done when:** core roles coexist within hard policies and quality fixes remain
  scoped.

### MC-045 — Harden audition lifecycle and desktop behavior

- **Depends on:** MC-044.
- **Contracts:** F-PLAY-001–F-PLAY-004; Quality Gate 5.
- **Inspect:** target fake/real adapters, supported macOS MIDI devices, session
  resources, and UI transport.
- **Work:** finalize output selection, pause/resume/seek/loop precision, mute/
  solo rebuild, tempo/meter use, session replacement, all-notes-off, shutdown,
  device loss/recovery, and actionable UI errors. Document non-authoritative
  timbre and supported fallback behavior.
- **Delete:** remaining target coupling to `JvmAudioPlayer`, WAV paths, renderer,
  volume-matched audio, or sound library.
- **Tests:** stress repeated lifecycle, resource/thread cleanup, fake clock
  timing, device exceptions, app close, and no source/project mutation.
- **Validation:** focused tests, `make test`, manual local MIDI-output smoke.
- **Evidence:** stress-test result and device smoke record.
- **Commit:** `midi-core: MC-045 harden MIDI audition`.
- **Done when:** Quality Gate 5 passes without an audio artifact or service.

### MC-046 — Harden export, manifest, and failure recovery

- **Depends on:** MC-045.
- **Contracts:** F-EXP-001–F-EXP-006; MIDI Contract 9–14.
- **Inspect:** target export packages from all development fixtures and MC-009
  conditional DAW findings.
- **Work:** finalize manifest schema, portable paths, filenames, marker labels,
  controller/program policy, instrument suggestions, end boundaries, role
  enablement, deterministic hashes, staging recovery, collision UX, incomplete-
  export cleanup, and reopen/history behavior.
- **Delete:** any remaining master/release/codec/credits format abstraction from
  target export.
- **Tests:** golden manifest, privacy/path redaction, all failure injection,
  deterministic reproduction, semantic re-import, conditional DAW instructions,
  and previous snapshot preservation.
- **Validation:** focused tests, `make test`, `make build`.
- **Evidence:** final development packages/manifests and recovery test results.
- **Commit:** `midi-core: MC-046 harden MIDI export`.
- **Done when:** automated persistence/export gates pass for all fixtures.

### MC-047 — Add bounded malformed-input and property coverage

- **Depends on:** MC-046.
- **Contracts:** F-MIDI-004, F-SYS-004; Quality Gate 3.
- **Inspect:** parser/writer/authority/store/generator boundary tests and known
  failure classes.
- **Work:** add deterministic property/fuzz-style generators with strict size/
  time bounds for MIDI chunks/events, note pairing, rational timing, project
  JSON, path references, authority timelines, candidate state transitions, and
  semantic writer-reader round trips. Persist minimized regression fixtures for
  discovered bugs.
- **Delete:** flaky random seeds, unbounded corpus/network access, and tests that
  accept crashes as expected behavior.
- **Tests:** bounded property suite with fixed reported seeds and time budget.
- **Validation:** focused property suite repeated, `make test`, `make build`.
- **Evidence:** seeds/case counts/runtime and any minimized regressions.
- **Commit:** `midi-core: MC-047 harden malformed input handling`.
- **Done when:** malformed bounded input returns typed failure and valid
  generated input round-trips semantically.

### MC-048 — Complete the final Logic Pro and GarageBand matrix

- **Depends on:** MC-047.
- **Contracts:** F-EXP-007; DAW Compatibility; Quality Gate 6.
- **Inspect:** final export packages for every DAW fixture and MC-009 results.
- **Work:** prepare hashed packages and exact checklist; ask the user to run the
  complete/role import matrix in current Logic Pro and GarageBand versions;
  record versions, tempo import behavior, tracks/names/channels, boundaries,
  markers, instruments, playback, and required actions. Fix and repeat any
  implementation failure.
- **Delete:** no unsupported DAW claim or workaround branch.
- **Tests:** all export/semantic tests after any fix.
- **Validation:** both final manual matrices and `make build`.
- **Evidence:** completed DAW records, hashes, reviewer/date, screenshots when
  available.
- **Commit:** `midi-core: MC-048 verify destination DAWs`.
- **Done when:** both DAWs pass or have an explicitly accepted conditional pass.

### MC-049 — Complete holdout musical acceptance

- **Depends on:** MC-048.
- **Contracts:** Quality Gates 7–8; all F-ARR/F-REV functions.
- **Inspect:** ten or more unseen, user-approved/license-safe MIDI projects and
  the frozen rubric. Do not tune constants using future holdouts after scoring.
- **Work:** prepare/import each project, preserve melody/harmony authority,
  generate/review alternatives, measure review time, export accepted snapshot,
  and ask the user/listener to record per-role/overall scores and reasons. Fix
  only reproducible targeted defects, add regression tests, regenerate affected
  candidates, and repeat failed cases.
- **Delete:** no failed holdout may be removed merely to raise the score; no
  manual MIDI editing may conceal a timing/harmony defect.
- **Tests:** regressions for every code fix plus full automated suite.
- **Validation:** mandatory rubric thresholds and `make test`, `make build`.
- **Evidence:** holdout IDs/hashes, snapshot IDs, scores, timing, notes, reviewer.
- **Commit:** `midi-core: MC-049 record musical acceptance`.
- **Done when:** every Quality Gate 8 threshold passes. This task cannot be
  completed from agent-generated subjective scores.

## Phase 6 — Cutover and complete legacy removal

### MC-050 — Remove the legacy schema, workflow graph, and application services

- **Depends on:** MC-049.
- **Contracts:** Cleanup Scope 5.4; F-SYS-002.
- **Inspect:** all current application/arrangement owners and target callers;
  produce a referenced-file/deletion list before editing.
- **Work:** switch every remaining runtime caller to target project/import/
  authority/generation/review/export use cases; delete schema-v4 project/store/
  validator, workflow artifacts/stages/runs/read model, source-song pipeline,
  part-stage APIs, build/stage orchestration, analysis-driven authority, and old
  monolithic application services once their extracted target behavior is live.
- **Delete:** obsolete application tests and fixtures with their owners; retain
  only target tests and truly shared utilities.
- **Tests:** compile-time owner scan, target lifecycle/E2E, no legacy schema
  decode/write, and unsupported old-project result.
- **Validation:** focused tests, `make test`, `make build`.
- **Evidence:** deleted owner list, retained/extracted map, source/test counts.
- **Commit:** `midi-core: MC-050 remove legacy workflow core`.
- **Done when:** no runtime stage graph, schema-v4 branch, song-part pipeline, or
  old application façade remains.

### MC-051 — Remove the legacy desktop UI and visual fixtures

- **Depends on:** MC-050.
- **Contracts:** Cleanup Scope 5.5; F-UI-001.
- **Inspect:** default desktop source graph, UI tests, tags, screenshots, theme
  measurements, preferences, and dialogs.
- **Work:** reduce/replace workspace app/router/view model/shell to target code;
  delete audio player, melody-parts/workflow presentation, runtime readiness,
  sound-library settings, legacy setup/state/intents/dialogs/routes, live audio
  E2E, and all tests that assert removed behavior. Replace useful visual tests
  with the six target fixtures from MC-040.
- **Delete:** tracked old page images and theme constants measured from them
  after confirming no target test references them.
- **Tests:** target screen/view-model/entrypoint/accessibility/visual suite and
  absence searches for all removed page labels/tags.
- **Validation:** focused UI tests, `make test`, `make build`, desktop smoke.
- **Evidence:** deleted UI/fixture list, six-page semantic tree, new image sizes.
- **Commit:** `midi-core: MC-051 remove legacy desktop UI`.
- **Done when:** no hidden route, state, intent, tag, screenshot, or test for the
  old product remains.

### MC-052 — Remove audio, DSP, mastering models, and their tests

- **Depends on:** MC-051.
- **Contracts:** Cleanup Scope 5.2; F-SYS-002.
- **Inspect:** audio/dsp/model packages, all imports, resources, configuration,
  and application/arrangement users.
- **Work:** delete audio buffers/decoders/exporters/player/resampler, FLAC/MP3/
  WAV code, DSP effects/chains/presets, mastering/export-format/DSP settings,
  audio comparison/preparation/mix services, audio-specific errors/config, and
  their tests/fixtures.
- **Delete:** entire empty audio/dsp/obsolete model packages and any remaining
  waveform/codec/loudness terminology in active runtime.
- **Tests:** compilation, target tests, and scans for audio APIs/extensions in
  production code excluding explicit non-goal docs.
- **Validation:** `make test`, `make build`.
- **Evidence:** deleted files/dependencies and package/line-count delta.
- **Commit:** `midi-core: MC-052 remove audio and DSP`.
- **Done when:** no production class can read, create, process, play, or export
  audio.

### MC-053 — Remove rendering, mixing, sound libraries, and licensing runtime

- **Depends on:** MC-052.
- **Contracts:** Cleanup Scope 5.2–5.3; F-SYS-002.
- **Inspect:** sfizz renderer, stem render/mix classes, instrument registry/
  resolver, sound-library locator/inventory, licensing/model registry, config,
  desktop preferences, tests, and tracked sound metadata.
- **Work:** delete renderer/stem/mix/production classes and services, renderer
  process invocation, sound-library validation/selection, instrument file
  resolution, model/sound licenses, runtime capabilities/settings, and tests.
  Preserve only MIDI performance-profile/instrument-suggestion metadata under
  target ownership.
- **Delete:** tracked sound metadata/docs only after target suggestions no longer
  import them; ignored library bytes are deleted safely in MC-057.
- **Tests:** target generator/audition/export tests and scans for sfizz/SFZ/
  renderer/sound-root/sample/master/mix dependencies.
- **Validation:** `make test`, `make build`.
- **Evidence:** deleted owners/config and dependency scan.
- **Commit:** `midi-core: MC-053 remove sound production runtime`.
- **Done when:** choosing a DAW instrument is metadata/user guidance, never a
  Melotrail renderer or local sample dependency.

### MC-054 — Remove Python, worker boundaries, and HTTP wiring

- **Depends on:** MC-053.
- **Contracts:** F-SYS-001, F-SYS-002; Cleanup Scope 5.1.
- **Inspect:** complete worker tree, Kotlin worker package, preparation worker
  boundaries, automatic import/inspection services, queues/config/errors,
  requirements, tools/tests, Gradle dependencies, Make targets, and docs.
- **Work:** verify any useful MIDI timing/import behavior was extracted; delete
  Python production/tests/tools/requirements/README, Kotlin worker client/
  protocol/commands/responses, audio/transcription preparation boundaries,
  HTTP/health/retry configuration, worker tests, and now-empty packages.
- **Delete:** OkHttp and Jackson when no remaining target import proves a need;
  remove worker/python/live-E2E Make variables/targets in MC-058 if not required
  for this commit to build.
- **Tests:** no `.py`/requirements/worker source, no worker/HTTP symbols,
  no network construction at startup, and target import/E2E.
- **Validation:** `make test`, `make build`; `make worker-test` must no longer
  exist and is not run.
- **Evidence:** tracked deletion list and source/dependency scans.
- **Commit:** `midi-core: MC-054 remove Python worker`.
- **Done when:** JVM build/test/runtime neither installs nor invokes Python or an
  HTTP worker.

### MC-055 — Remove AI, cohesion, critics, melody mutation, and extra roles

- **Depends on:** MC-054.
- **Contracts:** Cleanup Scope 5.4; post-MVP exclusions.
- **Inspect:** all Qwen/global planner/AI-fix/enhancement/cohesion/critic/full-
  song/humanization/connection/signature/transition/strings/pad owners, adapters,
  reports, project fields, UI remnants, tests, and JSON dependencies.
- **Work:** confirm chord behavior was extracted from pad; delete unrestricted
  and optional AI planners, AI fix, source-song connection, global cohesion,
  critic/enhancement, humanization, signature motif, transition, strings, old
  pad role/adapters, optional generation, and their application services/tests.
  Simplify pattern catalogs and validation to Chords/Bass/Drums only.
- **Delete:** model/license/config dependencies used only by removed planners.
- **Tests:** target core-role suite, pattern catalog exactness, no model/network/
  optional-role symbols, and protected melody identity.
- **Validation:** `make test`, `make build`.
- **Evidence:** deleted feature matrix and target role inventory.
- **Commit:** `midi-core: MC-055 remove non-MVP generation`.
- **Done when:** deterministic Chords/Bass/Drums are the only generated MVP
  roles and no source melody mutation pipeline remains.

### MC-056 — Remove commercial release, provenance policy, video, and publishing

- **Depends on:** MC-055.
- **Contracts:** Cleanup Scope 5.3 and 5.6; F-SYS-003.
- **Inspect:** commercial package/tests/docs, release export/review/similarity,
  quality-review evidence, video UI remnants, provenance/licensing fields, and
  transitional tests/docs.
- **Work:** delete commercial/YouTube/policy/rights/monetization/release-
  similarity/selected-master/video/publishing code, tests, docs, UI tags, and
  dependencies. Retain only the small target technical manifest provenance.
- **Delete:** commercial provenance and compatibility-retirement documents when
  their executable readers are gone.
- **Tests:** target manifest privacy/provenance, no commercial/video/publishing
  symbols/routes, and documentation-link audit.
- **Validation:** `make test`, `make build`.
- **Evidence:** deleted code/test/doc list and remaining manifest fields.
- **Commit:** `midi-core: MC-056 remove release publishing scope`.
- **Done when:** Melotrail exports MIDI evidence only and makes no release/
  platform-policy claim.

### MC-057 — Delete repository-owned audio projects, sounds, media, and caches

- **Depends on:** MC-056.
- **Contracts:** Cleanup Scope 2; user-approved data disposition.
- **Inspect:** resolve and record the repository absolute path; enumerate exact
  tracked/ignored sizes and consumers for `data/audio`, `sounds`, bundled root
  audio/video, old worker virtual environments/caches, Kotlin error logs, and
  old UI images. Confirm none is target input/evidence and no target source/test
  references it.
- **Work:** delete only the verified repository-local targets. Known baseline
  candidates include ignored old audio-project data, ignored sound-library
  bytes, tracked sound metadata, bundled rejected-product media, `.venv-worker`,
  worker caches, obsolete Kotlin error logs, and old UI fixtures not already
  removed. Update ignore rules only after inventory.
- **Delete:** exact recorded paths; never a home/workspace root, external user
  project, unresolved variable, or broad glob.
- **Tests:** absence/consumer scans and target fixture integrity.
- **Validation:** `make test`, `make build`, disk-usage before/after.
- **Evidence:** exact targets, tracked-vs-ignored recovery note, bytes/files
  removed, and absence scan.
- **Commit:** `midi-core: MC-057 remove obsolete project assets`.
- **Done when:** no repository-owned old audio project, local sample library,
  bundled rejected-product media, or obsolete cache remains.

### MC-058 — Simplify build, dependencies, ignore rules, and documentation

- **Depends on:** MC-057.
- **Contracts:** F-SYS-002; Cleanup Scope 5.6 and 6.
- **Inspect:** root/desktop Gradle, Makefile, settings, ignore rules, resources,
  docs index, transition docs, function inventory/checker, and all active links.
- **Work:** reduce Make targets to help/build/test/check/desktop/clean as useful;
  remove Python documentation coverage and its inventory/JSON/tests/tool; remove
  unused OkHttp/Jackson/audio/worker dependencies; simplify configuration/
  environment docs; delete transition import/workflow/compatibility/retirement
  docs; update README/AGENTS/PLAN/Troubleshooting to shipped MIDI Core; index
  task/log/prompt as implementation history without adding competing plans.
- **Delete:** empty packages/resources, worker/live-E2E commands, audio/sound/
  model ignore entries that no longer serve the target, and all dangling links.
- **Tests:** documentation links, Gradle dependency report review, Make help,
  absence scans, and clean target test/build commands.
- **Validation:** `make test`, `make check`, `make build`.
- **Evidence:** final build graph, Make help, dependency delta, docs audit.
- **Commit:** `midi-core: MC-058 simplify build and docs`.
- **Done when:** build and active docs describe only the JVM MIDI product and no
  target verification invokes Python.

### MC-059 — Complete dead-code, API, package, and reduction audit

- **Depends on:** MC-058.
- **Contracts:** Cleanup Scope 6–7; G6.
- **Inspect:** every production/test/resource file, dependency, public/internal
  declaration, package, Make/Gradle target, configuration key, environment
  variable, documentation link, and tracked/ignored top-level artifact.
- **Work:** remove unreachable/unused/duplicate compatibility code, empty
  packages, deprecated target aliases, old terminology, stale tests/resources,
  and dependencies. Enforce target architecture rules and record final file/
  line/disk metrics against MC-000.
- **Delete:** no legacy branch is retained “for later”; Git history is the
  archive. Do not delete a target behavior merely to improve metrics.
- **Tests:** architecture tests, compiler/tests, dependency scans, route/role/
  file-extension symbol scans, and documentation links.
- **Validation:** `make test`, `make check`, `make build`, desktop smoke.
- **Evidence:** final inventory, allowed exceptions with target justification,
  and reduction report.
- **Commit:** `midi-core: MC-059 finish legacy removal`.
- **Done when:** G6 passes and the only production paths are target project,
  MIDI, music/structure, arrangement/review, audition, export, application, and
  desktop support.

## Phase 7 — Final evidence and handoff

### MC-060 — Prove the MVP from a clean checkout and obtain sign-off

- **Depends on:** MC-059.
- **Contracts:** every functional requirement; Quality Gate 10; G7.
- **Inspect:** execution log, all phase evidence, DAW matrices, holdout rubric,
  known limitations, repository status, and final commits.
- **Work:** create an isolated temporary clean checkout/archive at the final
  commit; run target test/check/build; execute the kernel and desktop E2E;
  verify fixture/export hashes; smoke the six-page desktop; confirm cleanup
  searches; assemble the final evidence summary and known limitations; ask the
  user for final product sign-off. Update root plan status only after sign-off.
- **Delete:** temporary checkout after recording results; no evidence or failure
  may be hidden.
- **Tests:** complete fresh automated suite and all required manual evidence.
- **Validation:** clean `make test`, `make check`, `make build`, desktop smoke,
  Logic/GarageBand records, holdout thresholds, cleanup scan, user sign-off.
- **Evidence:** final commit, commands/results, DAW/holdout/UI/cleanup summaries,
  limitations, reviewer/date, and signed decision.
- **Commit:** `midi-core: MC-060 complete MVP evidence`.
- **Done when:** every Quality Gate 10 item exists, user sign-off is recorded,
  plan status is complete, and the execution log has no TODO/failed/pending task.

## 6. Functional traceability

- **Project:** F-PROJ-001–004 -> MC-010–013, MC-031–034, MC-050, MC-060.
- **MIDI source:** F-MIDI-001–005 -> MC-001–008, MC-013–014, MC-035, MC-047.
- **Authority:** F-AUTH-001–005 -> MC-015–018, MC-036.
- **Audition:** F-PLAY-001–004 -> MC-028, MC-035, MC-038, MC-045.
- **Arrangement:** F-ARR-001–007 -> MC-020–025, MC-037, MC-041–044.
- **Review:** F-REV-001–006 -> MC-019, MC-025–027, MC-038, MC-044.
- **Optional melody connection:** F-REV-007 is post-MVP and intentionally not
  implemented; current mutation code is removed in MC-055.
- **Export:** F-EXP-001–007 -> MC-007–009, MC-019, MC-029, MC-039, MC-046,
  MC-048.
- **UI:** F-UI-001–005 -> MC-031–040, MC-051.
- **System:** F-SYS-001–004 -> all phases, especially MC-003, MC-011–013,
  MC-025, MC-028–030, MC-047, MC-050–060.

## 7. Completion rule

MC-000 through MC-060 are mandatory. A task is not complete because its code
compiles; its deletion, focused tests, full required gate, evidence, and commit
must all be recorded. Optional features require a new user-approved plan after
MC-060 and may not be smuggled into this sequence.
