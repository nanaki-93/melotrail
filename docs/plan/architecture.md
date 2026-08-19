# Architecture audit and target boundaries

## Audit scope and baseline

This audit covers README/build/configuration, Compose Desktop, Spring services,
the Python worker, audio/MIDI processing, structure,
arrangement, cohesion, rendering, mix/master/export, provenance, tests, and the
existing completed planning work.

The working tree contained unrelated in-progress UI, MIDI-fix, drum, cohesion,
and test changes while this plan was prepared. They were inspected as current
code but not modified. Baseline automated checks passed:

- `./gradlew test :desktopApp:test`
- `.venv/bin/python -m unittest discover -s worker/tests` (40 tests)

These checks do not close the real renderer/model/listening/manual gates recorded
in `docs/RELEASE_ACCEPTANCE.md`.

## Repository map

| Area | Primary files | Current responsibility | Decision |
| --- | --- | --- | --- |
| Canonical project | `arrangement/Project.kt`, `ProjectStore.kt`, `WorkflowArtifacts.kt` | v3 file-backed project, migration, references, hashes, invalidation | Extend to v4; retain compatibility |
| Project application | `application/ProjectApplicationService.kt`, `WorkflowReadModel.kt` | import, cleanup, selection, analysis, structure, readiness | Keep facade; extract capabilities as added |
| Desktop UI | `desktopApp/.../WorkspaceViewModel.kt`, `WorkspacePageRouter.kt`, `DesktopMain.kt` | supported responsive product UI | Adapt, do not replace shell/transport |
| Spring API | `server/api/*`, `service/ProjectServiceAdapter.kt`, `model/Project.kt` | optional API with separate old project store | Retain only as canonical adapter with users; otherwise migrate/export data and delete |
| Worker | `worker/main.py`, `registry.py`, `commands/*`, Kotlin `WorkerClient`/`WorkerProtocol` | synchronous local Python operations | Keep stateless and specialized |
| MIDI | `MidiAnalysis.kt`, `MidiAiFix.kt`, `MidiLoFiFeel.kt`, worker MIDI clean | analysis, cleanup, bounded AI edits, fixed lo-fi groove | Reuse primitives; split responsibilities |
| Structure | project structure list, desktop editor, `SectionVariation.kt` | repeated part ordering and derived occurrences | Persist occurrence identity in v4 |
| Arrangement | `GlobalSongPlanner.kt`, `DetailedArrangement.kt`, generators, registry, `ArrangementApplicationService.kt` | plans, fixed logical instruments, generated MIDI, render handoff | Generalize roles and metadata-driven instrument resolution |
| Cohesion | `CohesionApplicationService.kt`, `TransitionCohesion.kt` | reviewed boundary plans and bridges | Move after arrangement; preserve melody |
| Render/mix/build | `StemRenderingMixer.kt`, `MixApplicationService.kt`, `BuildApplicationService.kt` | stems, settings, dry mix, DSP, master, export | Reuse; insert humanization/profile policy |
| Provenance | `CommercialProvenanceService.kt`, `provenance/*` | commercial evidence plus older generic log | Expand commercial service into lineage |

## Current canonical flow

```text
source
 -> raw MIDI (direct or Basic Pitch)
 -> worker clean MIDI + quality approval
 -> cleaned or approved AI-fix selection
 -> original or fixed Lo-fi Feel selection
 -> analysis
 -> structure
 -> approved cohesion boundaries
 -> arrangement plan/detail/generated MIDI
 -> stems -> mix -> repair -> optional Bedroom Lo-fi DSP -> master/export
```

`SelectedMidiArtifactResolver` is the single current selection-precedence owner.
`WorkflowArtifactGraph` centralizes downstream invalidation. Both are strong
foundations and should be evolved rather than bypassed.

## Reusable components

- Atomic project writes, project-root path confinement, fingerprints, and v1/v2
  migration patterns.
- Immutable source/raw/clean/AI-fix/feel artifacts and approval references.
- MIDI parsing, PPQ/tempo/meter analysis, validation, quality reporting, and
  deterministic cleanup.
- Strict JSON AI-planner pattern: path-free plans, validator, deterministic
  applier, persisted draft/approval evidence.
- Responsive workspace shell, workflow cards, progress/error state, transport,
  and fakeable UI service boundaries.
- Stable repeated-part semantics, section-variation planning, detailed
  arrangement, deterministic bass/drum/pad/string generation.
- Cohesion boundary review, bridge hashes, approval, and compatibility checks.
- Instrument/sample registry, licensing, sfizz rendering, stem hashes, persisted
  mix settings, mastering/MP3 commands, release evidence.
- Offline fake-worker/model/renderer/audio tests.

## Obsolete or compatibility-only components

- `model.Project` and `ProjectServiceAdapter` are an independent track CRUD
  product model. If the API has supported users, migrate callers/data and delete
  the separate model/store as the canonical adapter lands. If it has no supported
  users, export any required data and delete the entire product surface in Task
  028. A frozen/deprecated copy is not an accepted state.
- Whole-occurrence `MelodyCohesion` edit behavior is superseded by reviewed
  transition-boundary cohesion. Map supported evidence, then delete its executable
  implementation/wiring/tests in Task 024; historical data does not require code.
- Duplicate/older workspace UI components should be removed only after router
  coverage demonstrates they are unreachable.
- The generic provenance log should not compete with commercial provenance and
  the new stage ledger. Task 027 migrates useful fields and deletes the old log,
  store, wiring, configuration, and exclusive tests in the same cutover.

Every compatibility component in this section is temporary only while it serves
a declared supported schema/caller. Its owning task must delete superseded runtime
code after migration; Git history, not dormant source, preserves implementation
history.

## Blocking technical debt

1. Project settings do not contain structured key, scale, meter, mood, profile,
   or harmony. Current style/role/key/chords are free strings or MIDI inference.
2. Current `Part` has a free `role`; current structure is a list of part IDs and
   derives occurrence labels, so identity may change after reorder.
3. The global stale set cannot express per-part stage/config dependencies or
   retryable failed runs.
4. AI fix combines technical correction with musical enhancement and sees
   isolated MIDI-inferred context rather than user harmony.
5. Arrangement uses fixed logical instruments and lo-fi prompt/default leakage.
   Registry v1 is secure/validated but requires exactly one engine entry for each
   logical name, so role, instrument identity, and SFZ selection are conflated.
6. Cohesion is an arrangement prerequisite, opposite the target pipeline.
7. Lo-fi MIDI feel is a fixed enum and build-time lo-fi audio processing is a
   fixed option rather than profile/mood policy.
8. Durable long-running state is absent. Spring's in-memory worker jobs are not
   used by Compose and cancellation does not stop the underlying worker request.
9. Application/UI classes are large. Extract seams only around new ownership;
   a broad refactor would increase migration risk.
10. `ProjectApplicationService.importPart` currently warrants a focused
    characterization review because nested MIDI/audio branching can make the
    audio publication path unclear. Fix only with a failing contract test.
11. Placeholder/unknown local-model identity or licensing must never be reported
    as complete commercial provenance.

## Target component ownership

| Component | Owns | Must not own |
| --- | --- | --- |
| Compose UI | intent, editing state, progress display, preview/approval controls | file paths, stage order, model/worker calls |
| Application services | commands/queries, validation, permissions, orchestration facade | UI rendering, Python algorithms |
| Stage runner | dependency/cache key, lock, status, retry, artifact publication | musical policy or user selection |
| Profile catalog | versioned defaults/constraints and policy parameters | arbitrary executable rules |
| Instrument catalog | stable IDs, roles/affinities/traits, engine/capability metadata, embedded license/library provenance, and admission validation | project creative decisions, legal guarantees, or silent substitution |
| Instrument resolver | deterministic filtering/scoring and explainable selection decisions | filesystem paths, rendering, or changing approved assignments |
| Deterministic MIDI processors | transforms and reports | silent selection/approval |
| AI planner ports | bounded proposal generation | file writing, shell/path control |
| Python worker | audio/model computation for a single command | durable workflow/project state |
| Arrangement | occurrence roles, density, instrumentation, musical layers | transition approval, final mix |
| Cohesion | boundary continuity after arrangement | destructive core-melody rewrite |
| Humanization | seeded microtiming/dynamics variation | nondeterministic hidden mutation |
| Renderer/mix/master | audio realization, balance, delivery | composition/harmony decisions |
| Provenance | append-only transformation/selection evidence | rights adjudication or DRM |

## Frontend/backend contracts

The primary frontend is in-process Compose and should consume immutable query
snapshots plus typed commands. The optional REST layer, if retained, exposes
the same DTOs and application commands. It must not serialize internal Kotlin
sealed classes blindly or expose absolute project paths.

Long operations return a run ID immediately. The UI observes persisted stage
snapshots through application queries/flows. This contract works in process and
can later map to REST/SSE without placing a network abstraction inside the core.

## Python worker endpoint assessment

| Endpoint/command | Reuse | Notes |
| --- | --- | --- |
| health | Yes | Include protocol/capability version later |
| inspect-input / cleanup | Yes | Conservative preparation; never source overwrite |
| transcribe | Yes, bounded | Solo-piano WAV/WAVE/MP3; publish validated MIDI |
| midi-clean | Yes | Deterministic profiles/report; map into Clean stage |
| analyze | Review | Avoid duplicating authoritative Kotlin musical analysis |
| apply_dsp / repair | Review and name precisely | Do not call composition changes “repair” |
| master | Yes | Keep separate from optional style processing |
| mp3 export/convert | Yes | Preserve format/config provenance |

No worker endpoint currently supplies explicit transposition, separated musical
correction, context-aware enhancement, or humanization. These are new contracts;
their default implementation belongs in Kotlin unless a Python dependency or
model makes a worker command materially better.

## Configuration assessment

Desktop preferences should retain local paths/devices (worker URL, model URL,
renderer, sound library). Project JSON should retain creative settings. Profile
definitions should be application resources with stable versioned IDs. Stage
manifests should snapshot resolved processing configuration. Secrets and machine
paths must not enter portable project/release provenance.

The configured sound-library root remains a machine-local preference. The
portable project stores stable selected instrument IDs plus registry/asset hashes;
the renderer resolves engine files locally. Library differences across machines
must produce explicit availability/hash diagnostics, never implicit substitution.

Registry v2 embeds the commercial-license/attribution and source-library version
snapshot in each instrument entry. The current separate `LICENSES.json` remains a
v1 migration/evidence input, not the v2 runtime authority. A versioned admission
policy rejects recognized NC terms, requires complete attribution for admitted
CC BY entries, prefers CC0 only after musical fit, and sends unknown/custom terms
to explicit review. Export credits are derived from the final used-stem release
manifest rather than scanning the configured library.

## Documentation and build assessment

- README's architecture/module descriptions must be aligned to shipped
  code during rollout.
- `Makefile` declarations and Gradle task configuration must not imply a missing
  or unsupported entry point.
- `docs/MIDI_IMPORT_PROCESS.md`, `TRACK_PROCESS_WORKFLOW.md`, commercial
  provenance docs, function inventory, troubleshooting, and release acceptance
  must be updated with the task that changes their behavior.
- The completed 110–117 plan is valuable historical context but its
  cohesion-before-arrangement pipeline is superseded by this roadmap.
