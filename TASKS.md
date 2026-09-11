# Implementation tasks

Authority: [PLAN](PLAN.md). Updated: 2026-09-08. Task status is authoritative in the integration branch queue.
The old MC/UI/VID queues are retired. Reuse existing code and tests; do not
replay completed import, draft, acceptance, export or UI-foundation work.

## Execution contract

- One task produces one reviewable implementation commit. Suffixed rows are
  executable slices; the unsuffixed row implements the final slice and validates
  the combined parent contract. Aim for one behavior and a few production owners. Split an unexpectedly
  large task into suffixed subtasks here before editing; keep the parent pending
  until all children pass. Do not create another plan or log file.
- Read AGENTS, PLAN, this task and its owning references. Inspect actual callers
  and tests. For source paths below, `core` means
  `src/main/kotlin/app/melotrail`; `desktop` means
  `desktopApp/src/main/kotlin/app/melotrail/desktop`. Test owners mirror them.
- Dependencies in the queue are mandatory. The coordinator chooses the earliest
  ready mandatory task, skips an external wait, and records why. Optional tasks
  are excluded unless included in the implementation request.
- Default implementer: GPT-5.6 Terra. Use a fresh review context and the same
  task contract; reviewer approval requires evidence, not confidence language.
- Inspect `git status` first. Preserve unrelated work. Use `codex/` branches and
  isolated worktrees from the approved integration base; never reset the user's
  working tree. Commits must contain only task-owned changes.
- Every implementation task runs focused checks, `make test`, `make build`, and
  `git diff --check`. Fix every reproduced bug with a regression test. Existing
  failures are recorded with an owner, never hidden by skips or weakened checks.
- Core musical/export changes also prepare the relevant Logic matrix. The user
  can perform it as a batch at Q02; no release claim precedes that evidence.
- UI work captures relevant states at the three supported fixture sizes and
  inspects the actual images. MIDI engines keep deterministic event fixtures.
- Deletion includes exclusive tests, resources, config and callers. Extract only
  a live required helper before deleting its old owner. No compatibility mode.
- A done result states behavior, files removed, commands/results, evidence
  paths, commit, limitations and next task in at most eight lines. Update the
  queue, not a second narrative history. Put large reports in ignored `build/`.

States: TODO, RUNNING, REVIEW, WAITING_USER, BLOCKED, DONE, OPTIONAL.
Only the coordinator updates the queue when parallel workers are explicitly used.
A manual gate stays WAITING_USER until actual evidence arrives. It never blocks
an unrelated task. A failed dependent gate cannot be bypassed.

## Queue

| ID | Task | Depends on | State | Result / implementation commit |
| --- | --- | --- | --- | --- |
| F01 | Verify baseline and real dependency boundaries | — | DONE | f8013adfe12f; Preserved the staged F01 repair. It makes the architecture guard scan the real desktop root and verifies raw MIDI imports with an actual MidiCore page path. Static review found no reproduced issue. Default desktop startup delegates to the MIDI Core composition; its target graph has no worker/model/mixer/renderer construction. No queue edits or commits made.; test/build + fresh Terra review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-06T15-14-51-831Z-F01 |
| M01 | Freeze musical baseline and comparison harness | F01 | DONE | 7352ec8f050a; baseline fixture, deterministic comparison harness and review form; make test/build + fresh review passed; evidence runs/2026-09-06T17-31-17-824Z-M01 |
| F02 | Delete legacy desktop | M01 | DONE | b22cb8c09d58; Deleted the legacy desktop router, view model, pages, worker/audio/library composition, preferences migration, obsolete theme branches, and exclusive tests. Retained the MIDI Core launcher, composition, six-route shell, theme/primitives, and persistent MIDI player. Fixed the reproduced logger test to assert MIDI-only diagnostics. Next dependency-ready task: F03.; test/build + fresh Terra review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-06T20-44-18-740Z-F02 |
| F03 | Delete legacy application workflow | F02 | DONE | `596e605923f1`; legacy workflow/config entrypoints removed; `make test`, `make build`, diff check, and fresh coordinator review passed. |
| F04 | Delete obsolete musical generators and model paths | F03 | DONE | b520e0908aa7; Deleted all seven exclusive Qwen fixtures and the obsolete schema-V4 pending-run fixture. Added regression coverage preventing their return. No source MIDI, retained fixtures, Logic evidence, or UI/TABI references changed.; test/build + fresh Terra review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-06T22-23-31-708Z-F04 |
| F05 | Delete audio/worker runtime and finish schema/build cleanup | F04 | DONE | 9db46bd70a0f; Deleted obsolete audio/DSP/worker/Python runtime and exclusive tests, removed obsolete Make/Python wiring and root dependencies, retained MIDI storage/schema behavior, and added cleanup regression guards. Fixed the prior failure by removing empty retired directories from the worktree. Fresh diff review found no further issue. Next dependency-ready task: F06.; test/build + fresh Terra review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-06T23-17-34-111Z-F05 |
| F06 | Delete verified legacy data and measure repository reduction | F05 | DONE | `d6adb18ce53c`; 5,250,264 bytes removed (16.1%); `make test`, `make build`, diff check, Sol debug, and fresh Terra review passed. |
| A01 | Harden and verify bounded agent execution runner | F01 | DONE | a139773cdf8f; Requires a non-empty allowed-path policy for every allowlisted task before selection/admission. Added end-to-end coverage proving a now-next A01 with no policy creates no state, run directory, or worktree. Fresh diff review found no reproduced issue.; test/build + fresh Terra review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-06T15-42-09-677Z-A01 |
| A02 | Reduce runner overhead and recover retained work | A01 | DONE | 87963895d3a1; bounded evidence, saved repair sessions, three-task batches, disjoint M04/M06 workers, safe retained recovery; 39 runner regressions + application test/build, live CLI resume and fresh review passed. |
| U01 | Finish verified lanes and live timeline projection | F06 | DONE | `d983d2098d93`; factual shared lanes, real-position observation, stale evidence and device-loss lifecycle coverage; `make test`, `make build`, diff check, Sol debug, and fresh Terra review passed. |
| U02 | Compact shell, player and inspector | U01 | DONE | 7d661a7a8eb1; Fixed review-reproduced U02 gaps: Arrange/Review use one 332dp selected-section inspector column at ≥1440px, retain inline inspectors below that width, and Review draft controls use rectangular shapes. Added reference-width regression coverage. No commit made.; test/build + fresh Terra review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-07T10-37-44-445Z-U02 |
| U03 | Refine Project and MIDI import | U02 | DONE | Native one-file MIDI drop, factual Project/MIDI facts, consistent fractional BPM, scoped accepted/rejected findings and honest recovery; Sol High fixes plus `make test`, `make build`, diff check and fresh Terra review passed. |
| M02 | Derive melody context and harmony-tension evidence | M01, F06 | DONE | 07924ab04ef3; read-only melody/harmony context; fixed 6/8 accents, pickup slicing and PPQ25; 8 focused + 337 full tests, build and fresh review passed; recovered preserved candidate. Musical acceptance remains advisory. |
| M03 | Add explicit harmony durations and source extent | M02 | DONE | fa5bd0dfd3f5; Occurrence source audition now uses the confirmed arrangement end, retaining padded trailing silence without changing source melody events or bytes. Added padded-occurrence regression and MIDI contract note.; test/build + fresh review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-07T18-28-52-249Z-M03 |
| U04a | Expose compact chord-duration and source-end editing | U03, M03 | DONE | Selected-section durations and verified melody/source-end context; unique IDs after remove/add; confirmed-meter positions. 350 tests, build, diff check and fresh Terra review PASS; three sizes inspected. Evidence: ~/.codex/melotrail-terra/u04a-repair-evidence. Visual/Logic acceptance remains at U07/Q02. |
| M04a | Build bounded legal piano voicing choices | M03 | DONE | Bounded open/guide/reduced pool; continuous accepted bass-root coverage guards omission; single deterministic selection path. Engine v2 with safe retained-candidate retries; frozen v1 comparison repaired. 355 tests, build, diff check and fresh Terra review PASS. Evidence: ~/.codex/melotrail-terra/m04a-repair-evidence; listening/Logic pending Q01/Q02. |
| M04b | Rank piano voicings against protected melody | M04a | DONE | Engine v3 ranks M02 overlap, register and per-finding prominence, including held cross-chord suspensions. Validation record and frozen v1/current v3 MIDI pairs refreshed. 366 tests, focused checks, build and fresh Terra review PASS; Q01/Q02 listening/Logic pending. Evidence ~/.codex/melotrail-terra/m04b-repair-evidence. |
| M04 | Improve piano voicing against melody | M04b | DONE | Engine v4 bounded phrase lookahead and durable piano-boundary identity; verified retry/reopen and draft-use checks preserve prior artifacts. V4 snapshots and v1/v4 listening pack refreshed. 372 tests, focused checks, build and fresh Terra review PASS. Q01/Q02 human gates pending; short-section drum fixture recorded for M08. Evidence ~/.codex/melotrail-terra/m04-repair-evidence. |
| M05a | Anchor comping to meter and chord windows | M04 | DONE | Authored 4/4, 3/4 and 6/8 meter phase with exact harmony clipping; sustained offbeat revoicing retains bar attacks, unsupported meters reject explicitly. All real callers bind pattern identity. 379 tests, focused checks, build, Sol High repair and fresh Terra High review PASS. V1/v5 MIDI pack refreshed; Q01/Q02 pending. Evidence ~/.codex/melotrail-terra/m05a-repair-evidence. |
| M05 | Add phrase-aware, meter-aware comping | M05a | DONE | Versioned melody/CC64-aware support and answers, complete inter-phrase bar rests, phrase-end breathing room and preserved voicing across rest-only harmony. Early phrase ends, real publication/reopen/source preservation and six exact MIDI snapshots verified. 388 tests, focused checks, build and fresh Terra High review PASS. Comparison pack refreshed; Q01/Q02 pending. Evidence ~/.codex/melotrail-terra/m05-repair-evidence. |
| M06a | Persist versioned arrangement-plan records | M03 | DONE | 747e34385fcc; Added schema-v3 versioned arrangement-plan persistence, authority validation, role-scoped plan fingerprints, contract update, and reopen/malformed/no-op/protected-artifact regressions. No migration mode added.; test/build + fresh review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-07T22-48-30-296Z-M06a |
| M06b | Create and confirm arrangement-plan proposals | M06a | DONE | Real Arrange propose/confirm/cancel flow with session-held suggestions, readable intent summary, confirmed/reopened state and stale-authority guards. Source/drafts preserved. 363 tests, build, diff check and fresh Terra review PASS; three-size captures inspected. Evidence: ~/.codex/melotrail-terra/m06b-repair-evidence. Plan-edit invalidation and plan-driven generation remain M06/M07. |
| M06 | Persist a deliberate whole-song arrangement plan | M06b | DONE | b7b57ecf27a9; Fixed the stale-write race: confirmed plan edits now run load/verify/preview/save under the shared project write lock. Snapshot capture now shares that lock, and a concurrent acceptance/snapshot regression verifies stale concurrent writes are rejected rather than overwritten.; test/build + fresh review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-08T13-55-11-982Z-M06 |
| M07a | Resolve per-occurrence generation context | M05, M06 | DONE | e7949bd3e7b3; Prepared the M07a candidate for coordinator validation. It resolves role-scoped occurrence, neighbor, repeat, and Bass/Drums groove inputs; requires a confirmed plan through full-draft generation; and fixes over-invalidation from groove-only edits and whole-song piano-boundary hashes. Added regressions for deterministic fingerprints, Chords→Bass→Drums ordering, bounded invalidation, preserved unrelated acceptance, repeat ambiguity, and distant plan edits. TASKS remains untouched per coordinator ownership.; test/build + fresh revie |
| M07b | Represent planned rests in draft and acceptance | M07a | DONE | Schema-v4 typed rests through draft audition and atomic Use/Undo; revision-guarded unlock, inactive-role and rest-dependency guards, scoped invalidation and section repair. 401 tests, focused desktop/core checks, build, diff check and fresh Terra High review PASS. Evidence ~/.codex/melotrail-terra/m07b-recovery-evidence; M07 export and Q02 Logic evidence remain pending. |
| M07 | Generate drafts from plan, boundaries and explicit rests | M07b | DONE | Recovered accepted-rest assembly/export and readiness; verified all-song role omission and exact later Bass entry after intro rest. Materialized both Logic packages; current manifest v2 companion consumer aligned. Focused/full tests, native checks, build and fresh Terra review; evidence ~/.codex/melotrail-terra/m07-repair-evidence. Actual Logic playback remains Q02. |
| M08a | Coordinate bass support with chord and groove intent | M07 | DONE | Recovered shared groove/role fingerprints and repaired reduced-density held support, intro/outro kick ceilings and false pickup advisories. Focused regressions, 424 tests, build and Terra review passed. Evidence ~/.codex/melotrail-terra/m08a-repair-evidence; M08 full-song listening remains pending. |
| M08b | Shape drum fills and section transitions | M08a | DONE | acd724e30c9b; Addressed review feedback: a quiet next Intro/Outro now suppresses its incoming phrase fill and final-bar Bass-derived kick support. Added deterministic Intro and Outro pickup regressions; bumped Drum transition identity to v2 and updated contract notes.; test/build + fresh review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-10T10-40-00-057Z-M08b |
| M08 | Coordinate bass/drums and section transitions | M08b | DONE | Recovered terminal-rest/end-boundary comparison evidence. Explicit uncached focused Bass/Drums/ComparisonHarness execution, 431 tests, build and Terra review passed. Three frozen-baseline/current pairs retained at ~/.codex/melotrail-terra/m08-repair-evidence/comparison-packages; 30 MIDI and 6 manifest digests verified. Listening/Logic approval remains Q01/Q02. |
| M09a | Define scoped deterministic musical repair intents | M08 | DONE | ff881e53ed9c; Repaired the failing transition-scope regression. The invalidation planner canonically orders scopes by occurrence ID then role, so the expected list now matches the real deterministic caller output. Existing M09a implementation and documentation remain otherwise unchanged.; test/build + fresh review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-10T12-13-32-218Z-M09a |
| M09 | Add meaningful alternatives and targeted musical repair | M09a | DONE | Recovered bounded distinct alternatives and melody-inclusive A/B; repaired neighboring dependency generation, durable register identity, atomic batch/boundary validation and same-settings retries. Focused checks, 444 tests, build, diff check and fresh Terra High review passed. Evidence ~/.codex/melotrail-terra/m09-repair-evidence; Q01/Q02 listening/Logic pending. Arrange controls continue in U05b. |
| U04b | Edit sections and confirmed arrangement purpose | U04a, M06 | DONE | Section duplicate/split/move/remove and separately reviewed purpose/phrase confirmation. Repaired canonical test publication, invalid phrase input, section/plan write race and removed-rest evidence preservation. 410 tests, build, diff check and fresh Terra High review PASS; three-size controls inspected. Evidence ~/.codex/melotrail-terra/u04b-recovery-evidence; U04 layout and human visual/Logic gates pending. |
| U04 | Build compact Structure & Harmony editing | U04b | DONE | Recovered shared selection, unsaved impact and total recovery; fixed Compose test import/assertion, wrapped section actions and first-viewport name/save access. Unsaved sections cannot display another occurrence’s chords. 411 tests, build, three-size captures and fresh Terra review; evidence ~/.codex/melotrail-terra/u04-repair-evidence. |
| U05a | Make Arrange lanes and full-draft action dominant | U04, M07 | DONE | Compact five-style gallery/full-draft CTA, selected-section plan/progress, distinct draft/accepted rests and accepted-work precedence. Three-size captures and unclipped wide card bounds, combined test/build and Terra review passed. Evidence ~/.codex/melotrail-terra/u05a-implementation-evidence. |
| U05b | Wire bounded previews and contextual repair actions | U05a, M09 | DONE | Recovered six bounded repair actions and real one-bar loops; fixed saved-plan retry wording with Compose regression. Uncached focused checks, 445 tests/build, three-size visual inspection and fresh Terra review passed. Evidence ~/.codex/melotrail-terra/u05b-repair-evidence; full Arrange flow continues in U05, human gates pending. |
| U05 | Build timeline-first Arrange with plan and repairs | U05b | DONE | Recovered plan-aware ephemeral previews and three-action full-draft playback. Fixed intro fixture, post-confirm exception recovery, late-cancel transport/UI state and variable-precision history ordering; keyboard Enter verified. Uncached focused checks, 450 tests/build, three-size inspection and fresh Terra review passed. Evidence ~/.codex/melotrail-terra/u05-repair-evidence; human musical/Logic/visual gates pending. |
| U06a | Finish whole-song review and atomic decisions | U05 | DONE | Recovered exact Draft/Accepted identity, contextual melody playback, atomic mixed-rest Use/Undo/reuse and scoped blocker routing. Fixed confirmed-end test fixture and playback/candidate lane identity mismatch. Uncached focused checks, 456 tests/build, three-size inspection and fresh Terra High review passed. Evidence ~/.codex/melotrail-terra/u06a-repair-evidence; Logic/listening/visual gates pending; final Export handoff remains U06. |
| U06 | Finish whole-song Review and Logic export handoff | U06a | TODO | |
| U07a | Pin visual comparisons and accessibility regressions | U06 | TODO | |
| U07b | Measure responsiveness and prepare visual review | U07a | TODO | |
| U07 | Prove visuals, accessibility and responsiveness | U07b | TODO | |
| Q01a | Prepare frozen musical evaluation packages | U06, M09 | TODO | |
| Q01 | Evaluate musical improvement and fix failures | Q01a | TODO | |
| Q02a | Generate current Logic matrix and manifests | U06, M03, M07, M08 | TODO | |
| Q02 | Run the current Logic Pro matrix | Q02a | TODO | |
| Q03a | Prove clean native build and startup | F06, U06 | TODO | |
| Q03 | Prove clean install and obtain MIDI release decision | Q03a, U07, Q01, Q02 | TODO | |
| V01a | Prove an independently built companion boundary | F01 | DONE | Host ProRes/PCM encode, decoded preview timestamps and byte-preserved soundtrack proven; temporary-only outputs. Focused checks, Swift release build, 363 MIDI tests, absent-companion build/tests and fresh Terra review PASS. Evidence ~/.codex/melotrail-terra/v01a-repair-evidence. |
| V01 | Prove the isolated video/media boundary | V01a | WAITING_USER | Technical recovery: native Swift validation replaces stale Gradle check; provider/encoder choices and eight-request US$4.80 pre-tax pilot proposal recorded. Budget, rights and identity approvals remain pending. Evidence ~/.codex/melotrail-terra/video-recovery-evidence. |
| V02a | Implement immutable asset manifest and validation | V01a | DONE | Recovered Swift manifest; fixed typed-error compilation and no-overwrite snapshot publication. Native library regressions and MIDI test/build checked; production kit/rights approval remains V02. Evidence ~/.codex/melotrail-terra/video-recovery-evidence. |
| V02b | Import and inspect the pilot asset kit | V02a | DONE | Recovered local import/inspection; fixed infinite Git ancestry traversal and symlink write escape. Native owned-fixture regressions and MIDI test/build validated; production rights/identity remain V02. Evidence ~/.codex/melotrail-terra/v02b-repair-evidence. |
| V02 | Build the approved TABI asset library | V02b | BLOCKED | The candidate changes only the TABI specification. Its new section expressly labels the identity packet as proposed and WAITING_USER, with no production manifest, imported production files, rights evidence, or real approval record. V02 cannot satisfy its human-owned pilot-kit gate until the user provides rights/permitted-use statements and approves the identity, after which separate external production files must be imported, inspected, and approved in an immutable manifest.; preserved /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-09 |
| V03a | Implement resumable cost-bounded animation jobs | V02b | DONE | Recovered durable job coordinator with cross-process ledger locking and concurrent duplicate/budget/in-flight regressions. Native checks and MIDI test/build validated; no live provider request. Evidence ~/.codex/melotrail-terra/v03a-repair-evidence. |
| V03b | Implement one provider adapter and manual clip import | V03a | DONE | Recovered adapter and manual import; fixed staged MOV/MP4 probing and CLI encoder. Native fake-HTTP/owned-media regressions, MIDI test/build and fresh review checked. No live request or paid pilot. Evidence ~/.codex/melotrail-terra/v03b-repair-evidence. |
| V03 | Add one cost-bounded generative-animation adapter | V03b, V01, V02 | TODO | Selected for unpaid implementation; human/budget gates remain. |
| V04a | Plan deterministic soundtrack and scene timing | V03a | DONE | Recovered rational timing planner and fixed Swift assertions; validated decoded durations, pinned soundtrack changes, explicit alignment and CLI caller. Native debug/release regressions, 410 JVM tests, build and fresh Terra review passed. Evidence ~/.codex/melotrail-terra/v04a-repair-evidence. |
| V04 | Implement deterministic scene composition | V04a | DONE | ecfa75607cdd; Repaired all five reported Swift compile errors by evaluating throwing scene-resolution, frame, and file-read operations before passing their results to the non-throwing assertion autoclosure. Regression semantics remain unchanged.; test/build + fresh review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-09T12-00-00-939Z-V04 |
| V05a | Build real scene preview and soundtrack transport | V04 | DONE | fb33495b7612; Repaired preview synchronization within V05a scope. Public current-frame rendering now derives from the sole AVPlayer’s actual time, the CLI caller uses that snapshot, and regression coverage plays across a scene boundary while checking advancing frame/time pairs and shared frame-rate mapping. Changes remain uncommitted; TASKS was not edited.; test/build + fresh review passed; evidence /Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-09T12-16-36-129Z-V05a |
| V05b | Open a real companion editor window | V05a | DONE | Recovered reachable native editor; fixed loading-window ownership crash and clipped layout. Real release launch/selection/play/seek/close and error-window captures verified; native checks, 410 JVM tests, build and independent review passed. Evidence ~/.codex/melotrail-terra/v05b-repair-evidence. |
| V05c | Connect crop, motion and transition controls | V05b | DONE | Recovered real editing controls; fixed scroll-aware capture and whole-scene crop geometry. Native pixel oracle/parity, visible controls, preserved sources/player, 410 JVM tests, build and fresh Terra review verified. Evidence ~/.codex/melotrail-terra/v05c-repair-evidence. |
| V05d | Persist editor sessions and expose asset/job state | V05c | DONE | Repaired pinned save/reopen with source-safe storage, transactional same-player restore, truthful cost/unknown-progress state, and local Stop/Play/Restart. Native regressions and release captures, 410 JVM tests and build verified; fresh Terra review. Evidence ~/.codex/melotrail-terra/v05d-repair-evidence. |
| V05 | Validate the complete editor and compact keyboard flow | V05d | DONE | Recovered keyboard routing and stable inspector focus; responsive release captures at 1536×1024, 1280×900 and 720×900 with exact size/geometry checks. Native regression/release, 414 JVM tests, build and independent Terra review passed. Evidence: ~/.codex/melotrail-terra/v05-repair-evidence; production artistic approval remains human. |
| V06a | Implement bounded encoder process and output staging | V01a | DONE | Recovered native encoder process; repaired Swift compile errors, pre-launch cancellation, isolated input snapshots, process-group teardown, bounded diagnostics/callbacks, redaction and cleanup. Owned process regressions, native release and MIDI test/build checked. Episode codec/parity remains V06. Evidence ~/.codex/melotrail-terra/video-recovery-evidence. |
| V06 | Encode, validate and publish local video outputs | V06a, V05 | DONE | Recovered candidate; repaired Swift types, transparent-buffer noise, A/V backpressure, late cancellation and paired publication. Selected ProRes/PCM MOV proven at 320×180 mono/1s and 1920×1080 stereo/12s, with exact decoded PCM, frame/timeline probes and provenance. Native/release CLI, 433 MIDI tests, build and fresh Terra review passed; evidence ~/.codex/melotrail-terra/v06-repair-evidence. Full-song/production approval remains V07. |
| V07a | Add capability-checked optional Export handoff | V06, U06 | TODO | |
| V07 | Complete a TABI music-video pilot and optional handoff | V07a, V03, V02, Q03 | TODO | Selected for unpaid implementation; human/budget gates remain. |

## Foundation and removal

### F01 — Verify baseline and real dependency boundaries

**Outcome:** a reproducible foundation and exact removal map, using the current
working code rather than stale task status.
**Inspect:** both Gradle files, native packaging, `DesktopMain`,
`MidiCoreDesktopComposition`, `TargetArchitectureRulesTest`, existing core E2E,
visual-evidence provider and tests, and all source/test/resource imports.
**Work:** verify the pre-existing JDK/Kotlin changes and native startup; record
runtime/compiler/toolchain versions. Repair the architecture check so actual
root-level desktop owners are covered. Trace shared helpers and strongly
connected legacy consumers; generate a compact keep/extract/delete inventory
in build output. Verify the existing visual projection before extending it.
**Tests:** real create/import/authority/draft/use/undo/reopen/export workflow;
negative architecture fixtures at actual paths; package/startup smoke.
**Done:** focused MIDI flow runs without worker/network, all gates pass, every
legacy component has an exact deletion owner. No new runtime architecture yet.

### F02 — Delete legacy desktop

**Inspect:** `WorkspaceApp`, `WorkspaceViewModel`, `WorkspacePageRouter`,
`WorkspaceScreenTest`, `DesktopMain` legacy composition, preferences migration,
old playback/readiness/library helpers and shared theme/primitives.
**Work:** retain `MidiCoreDesktopEntrypoint` and live composition. Remove the
old router, view model, pages, factories and exclusive tests. Extract a shared
control only if a target caller needs it; remove legacy theme branches and
obsolete preferences migration. Replace image-reader assertions with target
behavior checks; preserve every UI and TABI reference image.
**Tests:** target entrypoint/composition, six-route inventory, theme/components,
keyboard navigation, current project open/reopen and no legacy route access.
**Done:** one desktop graph; no hidden audio page or old composition reachable
or retained for tests. New lanes and final styling can follow later.

### F03 — Delete legacy application workflow

**Inspect:** stage runner/registry, `BuildApplicationService`, old project and
arrangement services, preparation/enhancement/cohesion/critic/release services,
old CLI/config factories, orchestration tests and consumer map from F01.
**Work:** delete the rejected pipeline, its entrypoints and exclusive application
tests. Retain current `MidiCore*` use cases and only their proven live helpers.
Remove dead configurations and public adapters. Do not create a compatibility
facade. If old low-level tests still consume old models, assign those exact
remaining model files to F04/F05; do not extend or newly reference them.
**Tests:** current application workflow, cancellation, atomic acceptance/undo,
scoped stale admission, export re-import and entrypoint dependency scans.
**Done:** target services are the only application workflows; any temporary
remaining legacy leaf has a named imminent deletion task and no target consumer.

### F04 — Delete obsolete musical generators and model paths

**Inspect:** old non-core `arrangement` generators/planners, harmony helpers,
Qwen/client boundaries, critics, source repair/normalization/transposition,
Pad/Strings/Lead/FX, global humanization and melody-connection branches.
**Work:** prove required parser/pattern/voicing/artifact helpers already have
current owners, extract narrowly where necessary, then delete old implementations
and their exclusive tests. Remove optional-model configs/licenses and duplicate
musical schemas when their final consumers disappear.
**Tests:** M01 fixtures, current chord/bass/drum suites, authority/chromatic tests,
protected-source identity and no AI/network/source-mutation runtime.
**Done:** only current Chords/Bass/Drums generation remains; no “future” adapters
or old implementation used to keep an obsolete test compiling.

### F05 — Delete audio/worker runtime and finish schema/build cleanup

**Inspect:** `audio`, `dsp`, `model`, `worker`, renderer/mixer/SFZ/library/licensing,
commercial/release code, remaining legacy `Project`/stages, root resources,
Python tools, old live E2E and Gradle/Make/config/dependency consumers.
**Work:** delete the entire obsolete media/process boundary and exclusive tests;
remove schema-v4 readers, aliases and constructors once unused. Remove worker
requirements and tools; keep target storage/hash/logger behavior with one owner.
Reduce Make to help/test/check/build/desktop/clean; remove unused HTTP/JSON/audio
dependencies only after the consumer scan. Retain current serialization needs.
**Tests:** full current JVM workflow, absent worker/synth-output separation,
unsupported schema rejected without writes; clean target build/check.
**Done:** zero tracked Python source and no worker/audio-production or old-project
runtime; no dormant compatibility mode. Resolve dependency cycles as one tested
removal, or split this task before work rather than leaving a broken commit.

### F06 — Delete verified legacy data and measure reduction

**Inspect:** resolve repository root and exact targets including `sounds`,
`data/audio`, `.venv-worker`, old root media, caches and `App-pages.png` consumers.
**Work:** record target realpaths, bytes, tracked/ignored state, symlink behavior,
consumer absence and protected exclusions before deletion. Delete only verified
repository-owned obsolete material. Preserve all supplied UI/TABI/train images,
Logic evidence, owned MIDI fixtures and current user inputs/projects/snapshots.
Remove empty packages/resources and stale ignore entries. Do not rewrite Git.
**Tests:** no-reference scans, source/fixture digests, core workflow and clean
build. Review every apparent legacy scan exception; no blanket allowlists.
**Done:** publish file/line/disk/dependency deltas against PLAN, including the
40% production-line investigation target. No 10 GB sound-library prerequisite,
legacy sample/project payload or obsolete executable image reader remains.

## Musical workflow

### M01 — Freeze baseline and build comparison harness

**Inspect:** owned MIDI fixtures, candidate generation/draft tests, current
patterns, exporter, the user's 5/10 feedback and Validation's case taxonomy.
**Work:** build a Kotlin test/harness using owned small fixtures plus a full-song
fixture with repeated sections. Freeze source/authority/style/seed/engine IDs
and current output hashes before changes. Include sustained close melody/piano,
passing tones, low melody, sub-bar chords, repeated chorus, 3/4, 6/8 and endings.
Produce side-by-side MIDI packages and a compact review form in build output.
Accept optional user failure examples as development cases; never call them unseen.
**Tests:** harness determinism, source immutability, case manifest integrity,
invalid case rejection and reproducible semantic comparisons.
**Done:** later agents can compare baseline/new MIDI without legacy renderers;
5/10 remains the only supplied subjective result. Missing real songs does not
block synthetic development work or F02.

### M02 — Derive melody context and harmony-tension evidence

**Inspect:** protected melody model, harmony timeline, generation context and
current melody/register findings.
**Work:** pure versioned per-window context for active/sounding notes, accents,
rests, register, phrase hints and repetition. Weight interval tension by overlap
and beat prominence; distinguish held/accented tension from passing tones.
Define supported sustain/pitch-expression limitations. Return location, cause,
confidence and evidence; do not infer or save new harmony automatically.
**Tests:** held semitone versus passing tone, compound interval, suspension,
chromatic chord, pickup/rest, sustain beyond key release, ambiguous bend and
polyphony; identical inputs produce identical analysis, zero project writes.
**Done:** explain a specific melody/harmony issue at a bar/beat and expose a
usable read model; musical preference stays advisory.

### M03 — Add explicit harmony durations and source extent

**Inspect:** authority drafting/service/store, source end semantics, timeline,
fingerprints, candidate invalidation, writer and exporter boundary tests.
**Work:** persist/validate chord rows with explicit durations; losslessly seed
unchanged canonical windows from progression shorthand. Add last-note/source-end
facts and explicit trailing-silence padding to an arrangement end. Preserve
source events and byte identity. Update MIDI_CONTRACT and relevant schema once;
no general old-project migration. Explain unsupported schema before any write.
**Tests:** 3+1+2+2 beat harmony, odd PPQ, sub-bar windows, no-op edit exactness,
gap/overlap/overflow, non-bar note end, end-of-track silence, padding cancel,
reopen and scoped invalidation; export whole-song boundaries and source equality.
**Done:** services support musical duration editing without equal-slot ambiguity;
Q02's updated Logic fixtures are prepared, not falsely signed off.

### M04a — Build bounded legal piano voicing choices

**Scope:** Add open, guide-tone and reduced voicing candidates behind the existing generator. Preserve chord/slash identity, bass space and deterministic fallback. Test legal ranges, required tones, voice crossing and bounded candidate count.
**Inspect:** the M04 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M04b — Rank piano voicings against protected melody

**Scope:** Consume M02 overlap/accent/register evidence in deterministic voicing costs. Test sustained close clash versus passing tension and low melody; do not rewrite melody or harmony.
**Inspect:** the M04 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M04 — Improve piano voicing against melody

**Remaining parent slice:** Connect phrase-boundary continuity and stable lookahead to the new pool/ranker; generate the baseline/new listening pack and validate the complete M04 contract.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Inspect:** chord generator/validator, M01 failures and M02 context.
**Work:** bounded voicing pool with open/guide-tone/reduced choices, required
chord/slash semantics, melody-aware costs, stable tie-breaking and phrase
lookahead. Specify bounds and deterministic fallback; carry an explicit boundary
summary input without depending on an unrelated mutable accepted candidate.
**Tests:** accented close clash improves against baseline; passing/intentional
tension stays legal; guide-tone identity, low melody, bass space, voice crossing,
common-tone continuity, search limits and deterministic results across seeds.
**Done:** pool/ranking fixes measured failure cases, preserves all authority,
and generates a short baseline/new listening pack. Ask for milestone feedback
when useful; do not stall independent tasks or award a subjective score.

### M05a — Anchor comping to meter and chord windows

**Scope:** Implement authored 4/4, 3/4 and 6/8 phase and exact clipping at harmonic boundaries. Test offbeat changes, odd PPQ, short sections and compound accents.
**Inspect:** the M05 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M05 — Add phrase-aware, meter-aware comping

**Remaining parent slice:** Add activity/phrase-based support, answer and rest selection, version the changed patterns and compare audible development fixtures.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Inspect:** chord rhythm expansion, pattern catalog/tick grid and articulation.
**Work:** anchor pattern phase to meter/song bars, clip notes at harmony changes,
choose complete support/answer/rest patterns from melody activity and phrase
position. Define actual authored 4/4, 3/4 and 6/8 behavior and compound-meter
accents. Version any changed pattern/timing rules.
**Tests:** offbeat chord change keeps metrical phase; 3/4 downbeats, 6/8 grouping,
short sections, dense melody leaves room, phrase ending rests, valid swing/grid
rounding, no harmony-boundary overhang or source timing mutation.
**Done:** the same melody/chords can receive audibly distinct useful comping;
no forced 4/4 loop truncation masquerades as other-meter support.

### M06a — Persist versioned arrangement-plan records

**Scope:** Add purpose, phrase/repeat identity, role activity/settings, shared groove and boundary fields with exact persistence and fingerprints. Test reopen, malformed records, no-op identity and protected artifacts; do not add a migration mode.
**Inspect:** the M06 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M06b — Create and confirm arrangement-plan proposals

**Scope:** Add deterministic style-derived proposal and explicit confirmation use cases. Separate suggestion from saved authority and draft. Test no writes before confirmation, rename-independent purpose and cancellation.
**Inspect:** the M06 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M06 — Persist a deliberate whole-song arrangement plan

**Remaining parent slice:** Implement affected-scope preview and dependency invalidation for confirmed plan edits; prove locked/accepted work is preserved and every consumed input is versioned.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Inspect:** style catalog, project schema/fingerprint, occurrence identity,
M02 phrase/repeat suggestions and generation context.
**Work:** plan records store version, occurrence purpose, repeat family,
phrase groups, energy, role activity/density/register, shared groove intent and
entry/exit rules. Style produces an editable proposal; explicit confirmation
makes it arrangement authority. Never interpret a display label as the sole
purpose. Keep suggestion, confirmed plan and generated draft distinct.
**Tests:** intro/verse/chorus/bridge/outro differ by intended rules; repeat family
is related but bounded variation is possible; rename without purpose change;
reopen, no-op hashes, locked work and plan-edit invalidation previews.
**Done:** one versioned plan drives the full song and names every generation
input. Changing it never silently rewrites melody, chords or acceptances.

### M07a — Resolve per-occurrence generation context

**Scope:** Resolve plan, shared groove, bounded neighbor and repeat inputs in Chords→Bass→Drums order. Test deterministic fingerprints and precise invalidation; keep unrelated accepted scopes intact.
**Inspect:** the M07 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M07b — Represent planned rests in draft and acceptance

**Scope:** Make intentional rest distinct from missing/failed output through generation, audition and atomic use/undo. Test cancellation, retry, locked scopes and mixed candidate/rest batch acceptance.
**Inspect:** the M07 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M07 — Generate drafts from plan, boundaries and explicit rests

**Remaining parent slice:** Finish accepted-only assembly/export for rests, including all-song inactive-role omission and exact source/end boundaries; update contract and Logic fixtures.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Inspect:** draft orchestration, generation context/publication, retry, accepted
assembly, batch acceptance/undo, review/audition and export readiness.
**Work:** resolve per-occurrence plans; preserve Chords → Bass → Drums dependency
order, explicit groove input and bounded boundary/repeat context. Add planned
rest evidence so absence of a role in an intro is intentional and complete.
Hash every consumed dependency; allow scoped retry and exact affected-neighbor
invalidation. Rest/candidate selections use the same atomic acceptance boundary.
**Tests:** two identical seeds/versions, cancellation/retry, failure versus rest,
locked scopes, stale neighbor, repeated chorus, partial acceptance rejection,
all-song inactive role policy, source/role export origin/end and no overwrite.
**Done:** draft playback and export agree on accepted activity; missing output
cannot pass as silence. Update MIDI_CONTRACT and prepare Q02 fixtures.

### M08a — Coordinate bass support with chord and groove intent

**Scope:** Implement bounded bass approaches, chord space and shared kick intent without circular dependencies. Test low/held melody, resolution, deterministic fingerprints and 3/4/6/8.
**Inspect:** the M08 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M08b — Shape drum fills and section transitions

**Scope:** Use phrase and next-section intent for complete authored grooves and fills. Test one/two-bar sections, quiet intros, repeat variation and harmony-edge behavior.
**Inspect:** the M08 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M08 — Coordinate bass/drums and section transitions

**Remaining parent slice:** Integrate intentional endings and cross-role boundary regressions; generate full-song baseline/new comparison packages.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Inspect:** bass/drum generators, shared groove plan, current kick-support and
fill policies, M01 whole-song fixture.
**Work:** use planned purpose and shared kick/bass intent, bounded bass approaches
and chord-space constraints. Preserve authored drum groove completeness. Drive
fills by phrases and next-section intent; avoid a fill at every arbitrary chord
or section edge. Give repeated sections controlled variation and an intentional
ending. Inputs cannot form a circular accepted-role dependency.
**Tests:** bass/chord separation, approach resolution, kick coordination, held
melody, 3/4 and 6/8, two-bar/one-bar sections, quiet intro/rest, boundary fill,
last-note/end behavior and stable previous/next dependency fingerprints.
**Done:** assembled songs have demonstrable role coordination and transitions;
M01 comparison packages show the changes without extra melody editing.

### M09a — Define scoped deterministic musical repair intents

**Scope:** Map the six PLAN repair intents to bounded versioned settings and affected role/occurrence sets. Test precise invalidation, locked work and no accepted/source mutation.
**Inspect:** the M09 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### M09 — Add meaningful alternatives and targeted musical repair

**Remaining parent slice:** Rank up to three semantically distinct alternatives, reject cosmetic duplicates and wire real preview/apply intents with baseline context and honest no-result reasons.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Inspect:** candidate diff/lifecycle, style preview cache, workspace intents,
M04–M08 settings and validation findings.
**Work:** bounded repair intents from PLAN map to versioned settings and an
explicit role/occurrence or dependency set. Rank at most three semantically
distinct choices. Add same-position melody-inclusive A/B, clear impact labels,
rejection reason and same-scope retry. Acceptance remains explicit and atomic.
**Tests:** changing intent yields meaningful event/texture differences; identical
options deduplicate; unchanged unrelated accepted hashes; blocked/locked/stale
repair; audition cannot write state; no valid solution stops after bounded work.
**Done:** a musician can request “simplify piano” and hear/review that exact
change without seed hunting or global regeneration.

## UI/UX

### U01 — Finish verified lanes and live timeline projection

**Inspect:** existing `MidiCoreVisualEvidenceProjection`, its tests/caches,
workspace reducers, song-map geometry and audition position provider.
**Work:** finish and reuse source/candidate/draft/accepted read models; render
four factual lanes with one bar/chord/note/loop x-axis, fit/zoom and section
selection. Observe actual player position at a bounded cadence while playing;
stop observation on disposal/stop. No composable artifact reads or second clock.
**Tests:** real semantic events at exact x positions, read-only projection,
stale/missing/digest mismatch, clipping, zoom/scroll alignment, pause/seek/loop,
device loss and no observer/thread leak across navigation.
**Done:** real notes and moving playhead align; screenshot art is unnecessary.

### U02 — Compact shell, player and inspector

**Inspect:** shell frame, theme, primitives, player and page-local inspectors;
UI_GUIDELINE and current wide/compact captures.
**Work:** use existing tokens; collapse transport to the specified height,
compact rectangular controls and one contextual inspector. Expose real names
instead of internal IDs. Preserve six destinations, valid playhead/selection/
scroll, compact keyboard navigation, expandable device/role controls and errors.
**Tests:** player outside scroll on all pages, no duplicated inspector/transport,
short-window fit, keyboard/focus, contrast/hit targets and device recovery.
**Done:** first viewport reserves space for musical content; inspect images at
1536×1024, 1280×900 and 720×900, including scrolled Arrange/Review.

### U03 — Refine Project and MIDI import

**Inspect:** current pages, native file dialogs, import service and projections;
UI references 01 and 02.
**Work:** factual project metrics and next action, honest last-opened support,
one MIDI import well, compact source table/note lane and scoped findings.
Implement and test drop support before advertising it; file chooser remains
available. Add source/authority suggestion labels and recovery without replacement.
**Tests:** empty/ready/error/long name, source protected automatically, format
rejection explanations, keyboard chooser, real track counts and responsive layout.
**Done:** create/open/import/listen is concise and truthful; no audio queue,
cleaning options, fabricated history or source-replacement shortcut.

### U04a — Expose compact chord-duration and source-end editing

**Scope:** Use M03 services in a compact Structure & Harmony duration inspector with real melody/section context and explicit padding confirmation. Keep current plan behavior. Test unequal durations, cancel/save, non-bar source end and keyboard access; capture all three reference sizes.
**Inspect:** the U04 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U04b — Edit sections and confirmed arrangement purpose

**Scope:** Add compact duplicate/move/split/remove section rows and purpose/phrase suggestion confirmation. Test repeated identity, source immutability and keyboard alternatives.
**Inspect:** the U04 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U04 — Build compact Structure & Harmony editing

**Remaining parent slice:** Finish unsaved/affected-work preview, total-mismatch recovery and responsive shared-strip layout; inspect reference 03 and the three-size captures.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Inspect:** M03 duration/extent services, M06 plan/purpose, authority drafts and
existing structure page; reference 03.
**Work:** shared section strip; compact musical settings and section rows with
bar totals, duplicate/move/split/remove, explicit chord duration spans and
selected-section context. Surface phrase/purpose suggestions for confirmation.
Show unsaved state and affected work before save; separate arrangement-plan
edits from source structure. Keep keyboard alternatives to dragging.
**Tests:** unequal chord lengths persist, reorder leaves melody fixed, repeated
labels stay distinct, source padding explained, cancel/no-op edits, total
mismatch, scoped invalidation and first-viewport edit/save accessibility.
**Done:** structure and harmonic rhythm are visible over the real melody, not
hidden inside equal-slot text or multiple scrolling cards.

### U05a — Make Arrange lanes and full-draft action dominant

**Scope:** Build the reference 04 timeline composition, compact five-style gallery and selected-section plan/role inspector with factual rest/progress states. Test primary-action visibility and selection at all three sizes.
**Inspect:** the U05 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U05b — Wire bounded previews and contextual repair actions

**Scope:** Wire actual plan/repair services, latest-wins cancellation and one-bar looping. Test ephemeral versus persisted state, retry scope and no duplicate player.
**Inspect:** the U05 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U05 — Build timeline-first Arrange with plan and repairs

**Remaining parent slice:** Complete real-service ready-authority→draft playback within three actions, recovery and keyboard flow; inspect actual images and validate full/preview plan parity.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Inspect:** current Arrange/song map, M06 plan and M09 repair; references 04/07.
**Work:** dominant map/lanes, compact five-style gallery, top-right Create full
draft, selected-section plan/role inspector, per-scope real progress/cancel/retry,
planned rest states and meaningful repair intents. Support a one-bar occurrence
preview by looping its real content rather than demanding a two-bar source.
Use the same plan resolution for preview and full draft; label persisted versus
ephemeral work. Advanced pattern/profile details stay disclosed on demand.
**Tests:** full draft within three actions from ready authority, all visible
lanes and CTA, first/rapid/one-bar preview, latest-wins cancellation, plan
confirmation boundary, exceptions retain style/section and no double playback.
**Done:** the user can understand and hear the whole-song proposal and fix a
selected part without a section/role generation ladder.

### U06a — Finish whole-song review and atomic decisions

**Scope:** Use shared lanes for clear Draft/Accepted identity, contextual repair comparison and Play/Use/Undo. Test batch atomicity, rests, selection/loop continuity and exact blocker routing.
**Inspect:** the U06 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U06 — Finish whole-song Review and Logic export handoff

**Remaining parent slice:** Finish accepted-only Export summary, immutable result/reveal and concise Logic handoff; prove real-service import→repair→use→undo→reuse→export at all three sizes.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Inspect:** Review/Export pages, accepted assembly, batch undo and writer;
reference 04 for Review and 09 for Export.
**Work:** shared lanes, obvious Draft/Accepted identity, Play/Use/Undo and exact
blocker locations. Compare repairs with melody in context and route back without
losing selection. Export a truthful package summary, accepted-role/rest inventory,
files/result/reveal and concise Logic steps. No bitrate/mix/master controls.
**Tests:** draft is playable but not exportable; batch use/undo atomic; role
rest reflected accurately; accepted-only export, stale/missing/locked errors,
no overwrite, reopen and loop continuity between Arrange/Review.
**Done:** real-service import→draft→repair→use→undo→reuse→export passes at all
three sizes, with no project-file edits or fake settings required.

### U07a — Pin visual comparisons and accessibility regressions

**Scope:** Create deterministic actual/expected/diff image artifacts and independent geometry, color, focus and hit-bound checks. Prove comparator rejects shifted panels, wrong primary color/radius and missing lanes; do not auto-approve changed goldens.
**Inspect:** the U07 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U07b — Measure responsiveness and prepare visual review

**Scope:** Exercise large songs, long names, short windows and native density; measure the specified preparation/cancellation timings on a recorded machine. Prepare six-page image comparison and report unmeasured acoustic onset honestly.
**Inspect:** the U07 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### U07 — Prove visuals, accessibility and responsiveness

**Remaining parent slice:** Present the completed six-page evidence and record the actual user visual decision in Validation. If missing, record WAITING_USER; no new implementation is required merely to request this gate.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Inspect:** fixture writers, UI_GUIDELINE measurement JSON, complete workflow
and actual native font/density behavior.
**Work:** pin deterministic target-image baselines with actual/expected/diff
artifacts; independently check geometry, palette, focus and hit bounds. Baseline
updates are explicit reviewed changes. Add ready/blocked/error/progress states,
short-window resizing, contrast, keyboard completion and preview/full-song
performance measurements. Prepare six-page comparison for user visual review.
**Tests:** comparator fails shifted panel, wrong primary color/radius and removed
lane; font allowance cannot mask layout; long names/sections/large note counts;
no duplicate player/inspector or undisclosed UI-thread work.
**Done:** automated gates pass and genuine user visual decision is recorded.
Leave WAITING_USER if review is outstanding; Q01/Q02 preparation can continue.

## Product evidence

### Q01a — Prepare frozen musical evaluation packages

**Scope:** Build the comparison/evaluation export command and compact score forms with source/settings/version hashes. Freeze only supplied owned final cases; explicitly flag any missing three unseen songs and keep synthetic development cases separate. Test reproducibility and case integrity.
**Inspect:** the Q01 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### Q01 — Evaluate musical improvement and fix failures

**Remaining parent slice:** Collect genuine scores against Validation thresholds; record specific failed bars as bounded corrective queue work. Missing songs or ratings are WAITING_USER and do not block independent engineering.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Work:** prepare five varied full-song projects following Validation, with at
least three unseen at evaluation start. Freeze the set before generating final
outputs. Preserve original settings and label any tuned case as development.
Collect user scores/reasons for piano/melody fit, role interaction, structure,
overall usefulness and repair time; compare against M01 where available.
**Tests:** all semantic invariants plus focused regression for every reproduced
failure. Re-evaluate changed engines on all cases, retaining failed results.
**Done:** all declared musical thresholds pass with real ratings; otherwise
record failed bars/intents and add bounded corrective tasks. Never use automated
metrics or the old 5/10 feedback as evidence of a new 8/10 score.

### Q02a — Generate current Logic matrix and manifests

**Scope:** Prepare deterministic import/extent/harmony/rest/controller/ending packages and semantic re-import checks with build/source/output identity. Produce concise import/play/reopen instructions; never claim actual Logic playback.
**Inspect:** the Q02 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### Q02 — Run the current Logic Pro matrix

**Remaining parent slice:** Record real user Logic import/play/reopen results and exact versions against the frozen current packages, or WAITING_USER with the prepared artifacts.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Work:** regenerate frozen packages covering current import, padding, chord
windows, rests, patterns, expressive source and final boundaries. Record build,
source/output hashes and exact macOS/Logic versions. User imports complete and
individual-role files, checks timing/roles/tempo/controllers/drums/end/playback,
then saves/closes/reopens. Preserve the 2026-08-28 record as historical evidence.
**Tests:** writer/manifest semantic re-import and all current export failure cases.
**Done:** actual Logic results meet Validation; marker display may be a cosmetic
finding, musical corruption cannot. WAITING_USER is valid until evidence arrives;
an export test alone cannot complete this task.

### Q03a — Prove clean native build and startup

**Scope:** Run isolated clean install/package/startup and the six-page MIDI path without worker/model/sound library. Record build identity, reduction and any concrete install regressions; preserve source media and prior evidence.
**Inspect:** the Q03 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### Q03 — Prove clean install and obtain MIDI release decision

**Remaining parent slice:** Reconcile acceptance evidence with the final engine/UI/export versions, update README to shipped behavior and obtain the actual MIDI release decision.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Work:** use an isolated clean checkout of the integrated result; run test/build
and native install/startup without worker, sound library or model. Walk all six
pages and the full MIDI path, verify cleanup measurements and artifact identity.
Recheck that Q01/Q02/U07 evidence applies to the final engine/UI/export versions.
Update README to shipped behavior and keep only concise limitations/evidence.
**Done:** fresh gates, native smoke, user musical/UI decision and applicable Logic
matrix pass. Record final build and user decision in Validation. Do not delete
acceptance evidence or rewrite the source to obtain a pass.

## Optional automation

### A01 — Harden and verify bounded agent execution runner

**Scope:** developer tooling outside the shipped app; selected on 2026-09-06. Read PLAN §11. Harden the prepared Node/installed-CLI runner instead of building another runner.
**Work:** the dependency-free `tools/terra-runner.mjs` runner, one coordinator and writer,
fresh reviewer, worktree per task, stable integration branch, and a strict
result schema containing task/status/base/commit/tests/artifacts/blocker.
Use explicit output/worktree locations and task-specific permission settings.
Coordinator validates actual Git state and commands, never just model JSON.
**Controls:** project lock with owner/PID and safe recovery; dependency and
in-progress checks; maximum tasks, elapsed time and configured usage/spend cap;
at most two fix retries; cancellation/resume; preserve dirty unrelated work.
No automatic public push/merge/upload or paid media job without scoped policy.
**Tests:** dry-run queue selection, double start, crash/restart, reviewer failure,
false success report, changed integration base, missing human evidence, budget
exhaustion and interrupted worktree cleanup. Keep logs out of tracked docs.
**Done:** dry-run and one real low-risk task complete end-to-end; summary points
to reviewable changes. Scheduling is configured only for the requested cadence.

### A02 — Reduce runner overhead and recover retained work

**Work:** bound review evidence to the current candidate and explicit check logs;
keep transcripts out of prompts; resume implementation context for focused
repairs, with fresh reviews and Sol High escalation after two failed attempts.
Run bounded continuous batches with explicitly owned independent workers and
serialized revalidation/integration. Preserve interrupted candidates and actual
failure reasons; never reset budgets within a batch or recycle failures forever.
Recover the retained M02 candidate after fresh review. Install only tested runner
code and preserve the user's checked-out branch and staged changes.
**Tests:** original runner gates plus real-process fixtures for session reuse,
transcript isolation, bounded evidence, task/time/usage limits, retained retries,
overlapping ownership, actual worker concurrency and combined-tree validation.
**Done:** required checks and fresh reviews pass, installed script matches Git,
M02 is recovered, and the existing heartbeat uses the tested bounded policy.

## Optional TABI video companion

All V tasks use [TABI_VIDEO](docs/TABI_VIDEO.md). Build in a separate repository
or independently built/distributed companion chosen in V01. Do not put media
runtime into MIDI Core or reuse the old release/renderer branch. Shared commands
above apply to its equivalent tests/build; core tests apply to integration work.

### V01a — Prove an independently built companion boundary

**Scope:** Use the selected companion/ location with its own build. Prove a small owned-media encode/decode/preview sync spike, no MIDI runtime dependencies and exact local encoder/license facts. Research one current provider API without generating. Keep this a bounded spike, not a complete editor.
**Inspect:** the V01 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### V01 — Prove isolated video/media boundary

**Remaining parent slice:** Present exact provider/encoder choices, rights inputs and a bounded paid-pilot proposal. Record missing budget or product decision as WAITING_USER; technical subtasks may proceed with owned fixtures.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Work:** settle companion location and input/output presets; prove owned-media
preview, encode/decode, audio synchronization and native packaging. Inspect one
provider's current API/terms/credit limits without generation first. Record
provider/model constraints, encoder version/license/distribution and measured
resource costs. Request only still-missing generation-budget/media rights inputs
before dependent paid work; no new approval for already authorized scope.
**Tests:** absent companion leaves MIDI app build/install/export unchanged;
short local encode probes first/final frames, streams and duration.
**Done:** exact dependency choice and bounded pilot budget are reviewable; no
unverified codec or generative-continuity promise. No full app scaffold yet.

### V02a — Implement immutable asset manifest and validation

**Scope:** Add asset identity/digest/provenance/rights/geometry/approval records in the companion. Test missing/hash-changed media, dimensions and approved-version selection using small owned fixtures.
**Inspect:** the V02 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### V02b — Import and inspect the pilot asset kit

**Scope:** Implement local asset import and inspection for masks/alpha/pivots/layers/scene compatibility. Preserve original references and large media outside Git; expose unresolved TABI identity differences for review, without paid generation.
**Inspect:** the V02 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### V02 — Build approved TABI asset library

**Remaining parent slice:** Assemble the real owned pilot kit and record user approval of one coherent TABI/train identity. Missing production layers/rights/approval remain WAITING_USER.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Work:** reconcile character-sheet versus scene details once into an approved
identity bible; old “Moki” text never appears in output. Follow TABI_VIDEO's
pastel reference brief: compare four styles using the same train composition,
allow palette-matched TABI recoloring, and record the user's chosen direction
before production expansion. Style briefs are prepared; visual approval remains
pending. Create a manifest with
hashes, origin/rights/model/version, geometry/anchor/alpha/layer and approvals.
Start with the small pilot kit in TABI_VIDEO; import owned assets and generate
missing assets only within the authorized budget. Keep originals immutable.
**Tests:** missing/hash-mismatched assets, real transparency, dimensions/pivots,
scene compatibility, rights/provenance and approved-version selection.
**Done:** user approves one coherent character/train kit; separate files are
usable in composition, not merely a pretty collage or giant prompt output.

### V03a — Implement resumable cost-bounded animation jobs

**Scope:** Build provider-neutral job persistence, submission identity, budget admission and polling/cancel/restart with fake-provider contract tests. Reject unknown cost and do not issue live generation requests.
**Inspect:** the V03 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### V03b — Implement one provider adapter and manual clip import

**Scope:** Use the verified current API contract from V01a, secure credentials and explicit model/options. Test timeout, rate limits, uncertain submission, partial download and output digest quarantine without paid requests; support owned manual clips.
**Inspect:** the V03 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### V03 — Add one cost-bounded animation adapter

**Remaining parent slice:** Run only an explicitly budget-authorized short TABI animation batch; record real costs and human identity/loop approval. No authorization means WAITING_USER.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Work:** image-to-video jobs bind approved reference IDs, model/version,
parameters, prompt, seed when supported, budget and provider job ID. Persist
submission before polling; resume uncertain jobs by querying identity rather
than blindly resubmitting. Cache downloaded owned outputs by digest; quarantine
failed identity/loop clips. Provider seeds do not guarantee reproducible video.
**Tests:** timeout/rate limit/partial download, cancel/restart, duplicate request,
unknown-cost admission, budget/retry cap and redacted credentials/logs.
**Done:** several short TABI micro-actions retain identity and fixed train
geometry; failed takes are rejected with real costs recorded. Silent visuals
use the finished soundtrack later; no generated audio replaces the music.

### V04a — Plan deterministic soundtrack and scene timing

**Scope:** Resolve immutable finished soundtrack plus optional verified MIDI manifest to rational time/frame plans with explicit bounce offset/tail alignment. Test mismatches, rounding and no music/project writes using owned media.
**Inspect:** the V04 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### V04 — Implement deterministic scene composition

**Remaining parent slice:** Implement layered/parallax/window-mask motion, loop scheduling and crossfades from pinned assets; prove deterministic frames and exact soundtrack timeline with owned fixture clips.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Work:** immutable soundtrack + optional verified MIDI manifest become a
video-job plan. Use explicit bounce offset/tail alignment, rational tick-to-time
and frame rounding, layer/parallax motion, bounded loop schedules and scene
crossfades. Layer scrolling occurs behind the window mask. Reuse approved clips
without monotonously repeating the same episode.
**Tests:** unequal sections, rounding, overlaps, soundtrack longer/shorter,
changed tempo mismatch, stale assets, loop seams and no source/music writes.
**Done:** same accepted assets/job produce the same frame plan; the soundtrack
is neither trimmed/stretched nor remastered silently.

### V05a — Build real scene preview and soundtrack transport

**Scope:** Create a companion preview stage sharing output geometry/timing, with real playback/seek and one finished-soundtrack player. Test frame/audio alignment and crop/scene boundaries; no fake video frames.
**Inspect:** the V05 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### V05 recovery rules — shared by V05b, V05c, V05d and V05

The failed V05 candidate is preserved outside the checkout at
`/Users/marcoandreose/.codex/melotrail-terra/runs/2026-09-09T13-00-26-469Z-V05/worktree`.
Inspect its diff against `5fecbdefa3bfcf2c6b2cb4dd3f1f5e0d25306173` for
`companion/Sources/MelotrailTABICompanion/SceneEditor.swift`,
`SceneComposition.swift`, `ScenePreview.swift` and the regression executable.
Reuse only the helpers needed by the assigned slice, after inspecting their
consumers and tests. Do not apply the entire candidate, change its preserved
files, or replay the failed parent. The review failure was an unreachable editor
and missing rendered/layout evidence, not permission to replace V04/V05a.

Each slice must work through the actual companion window. A library API, JSON
command, screenshot mock or test-only caller alone cannot satisfy an editor UI
slice. Use native Swift/AppKit in the existing independent package; do not add
an application framework, MIDI dependency, audio renderer or provider call.
Use `docs/pictures/UI/08-video-preview.png`, `docs/UI_GUIDELINE.md` and
`docs/TABI_VIDEO.md` as design inputs. Native adaptations must preserve the
reference hierarchy, real preview and compact controls.

All four tasks run `node tools/companion-check.mjs`, `make test`, `make build`
and `git diff --check`, plus their focused verification below and fresh review.
Use the configured JBR for JVM checks. Extend the existing native regression
and check entrypoint for automated coverage; keep large captures/fixtures under
ignored build output. No new plan, prompt or execution-log document. Owned test
media permits engineering validation; production identity, rights and paid
pilot approvals remain V02/V03/V07 and must not be fabricated.

### V05b — Open a real companion editor window

**Dependencies:** V05a.
**Target files:** `companion/Package.swift` (new executable target),
`companion/Sources/MelotrailTABIEditor/main.swift` (new native entrypoint),
`companion/Sources/MelotrailTABICompanion/SceneEditorWindow.swift` (new window),
`companion/Sources/MelotrailTABICompanion/SceneEditor.swift` (recover only needed
session/selection helpers), `companion/Sources/MelotrailTABICompanion/ScenePreview.swift`,
`companion/Sources/MelotrailTABIRegression/main.swift`, `companion/README.md`.
**Work:** provide `melotrail-tabi-editor <composition-request.json>` using the
existing SceneCompositionRequest. Open a visible resizable native window with
real preview, rendered scene thumbnails, selection and play/pause/seek. Use
V05a's sole AVPlayer clock; clean observers/player on close. Show honest loading
and input errors. Establish the scene strip/preview/inspector layout with a
read-only selected-scene inspector; editing belongs to V05c.
**Verification:** launch the release executable with owned input, select a scene,
play across a boundary and seek; verify real frame/time advancement and one
soundtrack player, error handling and close cleanup. Capture the actual window.
**Done:** another person can launch and operate the preview from the documented
command; no inaccessible API counts as the delivered surface.

### V05c — Connect crop, motion and transition controls

**Dependencies:** V05b.
**Target files:** `companion/Sources/MelotrailTABICompanion/SceneEditorWindow.swift`,
`companion/Sources/MelotrailTABICompanion/SceneEditor.swift`,
`companion/Sources/MelotrailTABICompanion/SceneComposition.swift`,
`companion/Sources/MelotrailTABICompanion/ScenePreview.swift`,
`companion/Sources/MelotrailTABIRegression/main.swift`.
**Work:** connect selected-scene crop, supported layer motion and crossfade
controls to the existing shared composition/frame resolver. Recover preserved
edit helpers narrowly; reject invalid bounds visibly, retain prior valid edits,
and refresh thumbnails/preview. Keep the soundtrack digest, duration, frame
extent and sole player intact. Expose only effects the shared renderer supports.
**Verification:** drive real window controls on owned contrasting geometry,
assert rendered pixels/crop/motion at known frames and transition boundaries
match the shared resolver, including first/last frames and invalid inputs.
Checking only edited model fields is insufficient. Verify repeated edits do not
add players or alter source/asset bytes; capture before/after preview images.
**Done:** each visible control produces its intended rendered change and later
preview/export consumers use the same edited composition plan.

### V05d — Persist editor sessions and expose asset/job state

**Dependencies:** V05c.
**Target files:** `companion/Sources/MelotrailTABICompanion/SceneEditor.swift`,
`companion/Sources/MelotrailTABICompanion/SceneEditorWindow.swift`,
`companion/Sources/MelotrailTABICompanion/SceneEditorDocument.swift` (new current
session persistence if needed), `companion/Sources/MelotrailTABIEditor/main.swift`,
`companion/Sources/MelotrailTABIRegression/main.swift`, `companion/README.md`.
**Work:** save/reopen the current editor request and edits with pinned inputs in
companion-owned session storage, without overwriting soundtrack, MIDI projects,
assets or accepted output. Define one current schema, no migration. Detect stale
assets/soundtrack on reopen before playback. Surface real asset approval/identity
and persisted animation job cost/state/failure from existing owners; unknown
progress stays unknown. Provide preview cancel/restart and failure recovery in
the window; distinguish stopping preview from cancelling a provider job. No new
paid submission, provider cancel request or fabricated production approval.
**Verification:** use the visible save/open/cancel actions; reopen identical edits
and render matching frames, reject changed inputs and malformed sessions, recover
a failed open, and verify cancellation stops callbacks/audio without losing the
saved session. Owned ledger fixtures cover cost/failure/unknown progress labels.
**Done:** the editor session survives reopen and displays truthful actionable
state while preserving all musical and accepted artifacts.

### V05 — Validate the complete editor and compact keyboard flow

**Dependencies:** V05d. Reuse V05a–V05d; do not implement them again.
**Target files:** `companion/Sources/MelotrailTABICompanion/SceneEditorWindow.swift`,
`companion/Sources/MelotrailTABICompanion/SceneEditor.swift`,
`companion/Sources/MelotrailTABIRegression/main.swift`,
`companion/scripts/test.sh`, `docs/VALIDATION.md`, `docs/TABI_VIDEO.md`.
**Work:** finish keyboard focus/shortcuts, accessibility labels and compact layout
for the existing window, keeping text-field editing safe. Compare actual captures
against reference 08 at the three sizes prescribed by the UI guideline/validation
fixtures. Keep preview, scene strip and inspector reachable without clipped
controls; preserve output geometry when resizing the window.
**Verification:** automate real keyboard/selection/play/seek/edit/save/reopen paths,
inspect actual window captures, and prepare a multi-scene owned-media preview
that exercises crop, parallax, loops, transitions and exact final audio tail.
Prove scene-boundary/frame/audio alignment and source digests; retain measurements
and capture paths in `docs/VALIDATION.md`. An agent may validate technical UI
behavior, but production character consistency and artistic approval remain human.
**Done:** the complete reachable editor passes native/JVM checks and independent
review with actual rendered/layout evidence. V06 then consumes the shared edited
plan for encoding. No production pilot or paid generation is required to complete
this engineering parent.

### V06a — Implement bounded encoder process and output staging

**Scope:** Build safe argv, owned temp/output paths, progress/timeouts/cancel/disk errors and atomic new-output publishing in companion. Test Unicode/spaces, crashes and collision preservation with owned media.
**Inspect:** the V06 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### V06 — Encode and validate local outputs

**Remaining parent slice:** Probe first/final frames, streams, dimensions, duration, A/V sync and preview/output parity; produce a playable local file and compact provenance report.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Work:** bounded owned encoder process with safe arguments, progress, timeout,
cancel, disk-space/error handling; stage, probe and atomically publish to a new
output. Produce chosen video preset and a compact provenance/technical report.
No upload action. Only clean job-owned temporary files.
**Tests:** readable first/final frames, duration/frame rate/dimensions/streams,
soundtrack policy and A/V sync; spaces/Unicode filenames, cancellation, crash,
stale input, destination collision and preservation of previous complete output.
**Done:** playable video matches preview and keeps the exact soundtrack timeline;
no partial output is labeled complete. Core remains independently installable.

### V07a — Add capability-checked optional Export handoff

**Scope:** Wire optional companion launch consuming an immutable export manifest and separately supplied finished soundtrack. Test installed/absent companion, stale snapshots and no MIDI project writes; keep independent packaging.
**Inspect:** the V07 contract below and its relevant source/test owners.
**Done:** this slice works through its real caller, focused regressions and required
coordinator checks pass; leave later slices to their queue owners.

### V07 — Complete pilot and optional MIDI handoff

**Remaining parent slice:** Complete the real full-song TABI pilot using approved assets/music, record costs and user visual/sound/rights decisions, then prepare a local upload package. Public upload and monetization remain separate decisions.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Work:** finish one original full-song TABI episode with the owned music,
controlled scene evolution and approved character assets. Human review checks
identity, flicker, loop seams, pacing, sound and ending. Record actual generation
cost, retries, reuse, production time and outstanding rights/disclosure decisions.
Add an optional capability-checked launch from MIDI Export only if the companion
is installed; consume the immutable manifest without editing its project.
**Tests:** companion installed/absent, stale snapshot, pilot encode/decode,
A/V/scene checks, companion build/install and core test/build.
**Done:** user approves a usable video and local upload package. Publication,
channel eligibility and monetization are separate user/platform decisions;
there is no fabricated YouTube-readiness score or promised revenue.

## Configured automatic execution

The existing **Melotrail Terra autopilot** wakes every **20 minutes** and invokes
`advance` once. It runs up to three ready slices within 45 minutes and 500,000
reported non-cached input/output/reasoning tokens; up to 72 task/continuation
admissions per UTC day. Cached input is recorded separately. Model usage is
reported after a call, so an in-flight call may cross the admission limit.
Before admission, the heartbeat checks actual account limits and defers below
10% remaining. No credit purchase/reset is authorized.

One Terra High implementer works at a time. A concrete failure gets one focused
Terra retry, then one **Sol High** repair with the original task contract, current
`git diff`, and exact terminal/test/review errors. Checks and fresh Terra High
review are coordinator-owned. Every changed candidate needs focused checks,
`make test`, `make build` and `git diff --check` before review/integration.

A budget/deadline interruption is distinct from an implementation failure.
`advance` continues the preserved stage under a new bounded batch, retaining
prior usage and evidence. A ready candidate resumes validation/review, not
implementation. At most three such continuations are admitted per task. A
completed three-attempt failure, or exhausted continuation, is recorded BLOCKED
with its preserved candidate; the next wake selects independent ready work.
Never clear state, erase usage history, weaken a gate or recycle the same task
forever. A live lock or explicit pause causes no admission. A dead lock requires
verified PID/process-group recovery; a conflicting changed base preserves work.

Commits advance `codex/terra-batched-implementation`. The normal project checkout
uses **codex/terra-live** and is fast-forwarded after each successful integration
only when it is still on that branch and has no tracked edits. A dirty/diverged
checkout is preserved and reported; do not reset it. Restart `make desktop` to
see the new app. TASKS on the integration branch owns completion; runner status
owns the active stage. Workers are CLI processes, not Scheduled-tab subagents.

Local configuration/state: `~/.codex/melotrail-terra/`. The installed script must
match tested `tools/terra-runner.mjs`. Only the coordinator updates TASKS and
integrates. Successful dedicated worker worktrees are removed; failed candidates
and evidence are retained. No new execution-log document belongs in this repo.

```bash
node ~/.codex/melotrail-terra/terra-runner.mjs status
node ~/.codex/melotrail-terra/terra-runner.mjs dry-run
node ~/.codex/melotrail-terra/terra-runner.mjs advance
node ~/.codex/melotrail-terra/terra-runner.mjs pause
node ~/.codex/melotrail-terra/terra-runner.mjs resume
node --test tools/terra-runner.test.mjs tools/terra-throughput.test.mjs
```

Human musical ratings, visual/Logic decisions, asset rights/approval and paid
pilot budget remain gates. Their evidence-preparation children run automatically.
Unpaid companion plumbing uses owned fixtures and does not depend on a paid
pilot or final MIDI release. V01/V02/V03/V07 keep their real human gates; public
upload and paid generation are never inferred from a request to implement code.
Continue until the selected queue is complete or only blocked/external decisions
remain. Notify on actual integration, new failure, live-checkout sync failure or
required input. Local scheduled execution needs this computer and app running.

## Reusable agent prompt

```text
Implement only the assigned dependency-ready TASKS row and its explicit slice.
Read AGENTS, PLAN, README, Architecture and relevant task-owner references once.
Inspect current callers/tests. Reuse completed child tasks and the preserved
candidate; do not rebuild the whole parent feature. Do not edit PLAN/TASKS or
run other agents. Work within the coordinator's allowed paths, without commits.
Preserve source MIDI, authoritative settings, accepted candidates and exports.
No legacy compatibility, audio-production runtime or paid/public media actions.
Add regressions and call out exact focused test selectors. The host coordinator
runs Gradle/make, diff checks and fresh review; unavailable worker sandbox sockets
mean PENDING_COORDINATOR, not failure. UI work includes actual image inspection;
musical/export work prepares comparison/Logic evidence, never fictional ratings.
Return the runner's structured task/base/candidate/status/summary/tests/artifacts/
blocker result. WAITING_USER describes an actual missing human decision.
Repairs receive the same task, current diff and concrete errors, and remain scoped.
Never read model transcripts or recursively search execution directories; use
only the bounded current evidence packet and its named failed check logs.
```
