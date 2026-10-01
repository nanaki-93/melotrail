# Melotrail audio implementation queue

This is the only implementation queue for audio composition via MIDI, governed by
[PLAN-AUDIO.md](PLAN-AUDIO.md). The other workstream has its own
[TASKS-VIDEO.md](TASKS-VIDEO.md); its acceptance is not a prerequisite here.
Existing implementation and evidence are retained; the non-interactive policy
below removes UI-only gates and adjusts their dependent rows without claiming passes.
Dated evidence describes the original candidate, not a current-build pass.
Historical filenames in receipts describe their original locations, not active
planning authorities. No task, automation, commit, model download, inference or
paid request is authorized by this documentation split.

`CORE-01` is a queue-local baseline reference in each workstream. Its DONE state
comes from the same historical shared verification, not two separate runs or a
new pass. Recheck the current candidate on any authorized implementation run;
there is no dependency on the other queue's artistic or release gates.

## Non-interactive validation

All required tests must be headless; do not open or drive interactive windows.
Keep offscreen rendering/semantics, presentation-state, real-service integration,
MIDI/file checks and packaging inspection. Foreground capture, native-window
replay/resize, GUI install/startup smoke and manual UI/Logic walkthroughs are no
longer tasks or release dependencies. AC4-02 and AC5-05 are removed, not DONE;
their IDs are not reused. AC5-04/06 retain only non-interactive scope. Historical
receipts stay unchanged and cannot authorize a rerun. Listening and release
review of supplied artifacts remain human gates, not UI exercises. Follow
[Validation](docs/VALIDATION.md#non-interactive-validation), which supersedes older
interactive requirements in the owner references. Test sources/build wiring are
not deleted or changed by this planning update.

## How to use this queue

- Feature IDs match PLAN-AUDIO: CORE and AC1–AC5 (audio composition via MIDI).
- States: `TODO`, `RUNNING`, `REVIEW`, `DONE`, `WAITING_USER`, `BLOCKED`, `OPTIONAL`.
  `TODO` is a new verification/implementation obligation, not a claim that code
  is absent. AC1–AC4 reuse implemented features: inspect callers and run focused
  checks first. Record verification when no defect exists; do not invent a rewrite
  or force a code commit. Split actual defects into bounded feature-owned rows.
- A task is ready only when every listed dependency is DONE and required inputs
  and authorization exist. `—` means no task dependency. WAITING_USER needs a
  real decision/input, not another retry to rediscover its absence.
- After rechecking CORE, follow AC1 → AC4 technical verification and AC5 evidence
  preparation/release. Song intake and independent preparation can proceed during
  human waits. Video production order does not choose or block audio tasks.
- Only the coordinator updates status/integration. Default to one writer. Parallel
  agents need an explicit implementation request and disjoint file ownership.

## Execution and validation contract

1. Read AGENTS, PLAN-AUDIO, README, this row and its owner references. Inspect Git status,
   actual consumers/tests and the current candidate before editing. Preserve unrelated
   tracked/untracked changes, source media, current projects and accepted artifacts.
2. Select exact allowed files from the owners below before implementation. `core`
   means `src/main/kotlin/app/melotrail`; `ui` means
   `desktopApp/src/main/kotlin/app/melotrail/desktop`. Tests mirror those paths.
   New owners below are proposals, not claims that files already exist.
3. Keep one behavior/boundary per task. Split an oversized task here before expanding
   scope. No duplicate job ledger, planner/schema compatibility mode or extra plan,
   inventory, execution log or agent-prompt document.
4. Every implementation runs focused tests, `make test` and `make build` through
   a verified headless path, plus `git diff --check`. Inspect the task graph first;
   if defaults open a window, use a filtered headless invocation and record all
   exclusions rather than claiming a full-suite pass. Ordinary tests use owned
   fixtures/fakes, never models, hosted jobs or credentials. Fixes require a
   regression that fails on the old behavior; never weaken retained checks.
5. Use at most one initial implementation attempt plus two bounded repair attempts
   per admitted slice, then preserve the candidate and mark BLOCKED. A larger retry
   budget needs a new explicit run instruction. Usage interruptions resume the same
   stage; missing human evidence is not a code failure. Independent review examines
   the exact tested candidate, not a stale commit or historical receipt.
6. Commit only if the implementation run authorizes commits, and only task-owned
   changes plus the status update. Do not reset/stash the user's tree, move live
   branch refs, push or start a scheduler. Old scheduler task IDs are not aliases.
7. DONE records concise evidence beside the row: current build/input identity,
   commands with executed/cached outcomes, artifact location and limitations.
   Larger artifacts belong in ignored `build/` or selected external storage.
8. Human acceptance records reviewer/date, exact artifacts/versions and decision in
   [Validation](docs/VALIDATION.md).
   A changed engine, export policy or reviewed UI/media invalidates applicable evidence.

Focused JVM tests below are class selectors, run as
`./gradlew :test --tests '*ClassName'` or
`./gradlew :desktopApp:test --tests '*ClassName'` as indicated; combine selectors
for a task. Use the configured JDK 21 without editing toolchain files merely to
match a shell. Headless package inspection and artifact-based listening/release
review remain separate checks; ordinary tests do not award human acceptance.

## Feature CORE — Reproducible baseline

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| CORE-01 | Identify the exact current candidate and preserved WIP; run current focused/full MIDI, architecture and documentation checks. Record concrete blockers and distinguish installed/tooling issues from code failures. Do not replay a historical admission or delete local data. | — | DONE |

CORE-01 evidence (2026-09-24): historical shared verification tested HEAD
`7a91f1ff76d6fc305e3459a6c6f94cfaab159832` on branch `codex/pi-automation`,
with JDK Temurin 21.0.11, Gradle 8.14.3 and Kotlin plugins 2.2.21. Focused
architecture/documentation checks and fresh root/desktop
`./gradlew test --no-build-cache --rerun-tasks` passed (14 tasks executed);
`make test` and `make build` passed UP-TO-DATE and both diff checks passed.
The complete original shared receipt, including video-only tooling results and
log paths, is retained in [TASKS-VIDEO](TASKS-VIDEO.md#feature-core--reproducible-baseline).
Source identity: `build/core-01/repair-20260924T041944Z/integration/source-manifest-after.txt`.
This is historical engineering evidence, not musical, Logic, visual or release
approval. Current audio verification starts at AC1-01 after a baseline recheck.

**Owners:** build/Makefile configuration (inspect first, no default edits),
`TargetArchitectureRulesTest`, `DocumentationIntegrityTest`, current MIDI source/
test owners. Preserve unrelated video WIP, local environments, media and projects.
**Proof:** focused architecture/documentation and MIDI tests, `make test`,
`make build` and `git diff --check`. If isolation is needed, include task-owned
WIP without moving/deleting originals. No cleanup of `.venv*`, `sounds`,
`data/audio`, unknown caches or external evidence.

## Feature AC1 — Protected melody and musical authority

Existing implementation; verification and bounded defect correction only.

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| AC1-01 | Verify create/open/import, exact source preservation, fixed authority, explicit padding, section operations and unequal/sub-bar harmony. Confirm/cancel/reopen and stale-impact checks preserve previous artifacts; unsupported input fails with actionable guidance. | CORE-01 | TODO |

**Owners:** `core/application/MidiCoreSourceImport.kt`, `MidiCoreProjectLifecycle.kt`,
`MidiCoreArrangementExtent.kt`, `MidiCoreMusicalAuthority.kt`,
`MidiCoreAuthoritativeHarmony.kt`, `MidiCoreStructureTimeline.kt`; existing MIDI,
project and structure owners; `ui/MidiCoreAuthorityDrafting.kt`, `MidiCoreMidiPage.kt`,
`MidiCoreStructureHarmonyPage.kt`.
**Focused proof:** `MidiCoreSourceImportTest`, `MidiCoreArrangementExtentTest`,
`MidiCoreHarmonyTimelineTest`, `MidiCoreOccurrenceTimelineTest`; desktop
`MidiCoreAuthorityDraftingTest`, `MidiCoreStructureHarmonyPageTest`. Check actual
fixture coverage before adding tests. No silent quantization, source switch,
section-driven melody rearrangement or inferred harmony confirmation.

## Feature AC2 — Coherent whole-song arrangement

Existing implementation; musical quality is not yet human-accepted.

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| AC2-01 | Verify a confirmed per-occurrence plan drives Chords → Bass → Drums with melody-aware piano, metrical rhythm, repeated-section development and explicit rests. Same inputs replay identically; edits invalidate only used dependencies; cancel/retry preserves completed/accepted work. | AC1-01 | TODO |

**Owners:** `core/arrangement/core/`, `core/application/MidiCoreArrangementPlanProposal.kt`,
`MidiCoreArrangementPlanEdit.kt`, `MidiCoreArrangementDraft.kt` and their current
fingerprint/validation consumers.
**Focused proof:** `MidiCoreMelodyHarmonyAnalysisTest`, `MidiCoreChordGeneratorTest`,
`MidiCoreBassGeneratorTest`, `MidiCoreDrumGeneratorTest`, `MidiCoreArrangementDraftTest`,
`MidiCoreComparisonHarnessTest`. Include 4/4, 3/4, 6/8, dense/sustained melody,
sub-bar changes, boundary continuity, rests and unsupported grids/meters. Prepare
same-authority comparison MIDI with existing tooling; no musical score is assigned.

## Feature AC3 — Audition, repair and acceptance

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| AC3-01 | Exercise the implemented style → plan → full-draft playback path, scoped repair A/B, Use/Undo and reopen through headless use-case/presentation-state tests with an injected output adapter. Verify one player, fixed melody/loop position, locks, revision guards, failed-Apply retry and accepted-only export readiness. Record only reproduced defects for repair. | AC2-01 | TODO |

**Owners:** `core/application/MidiCoreArrangementStylePreview.kt`,
`MidiCoreMusicalRepair.kt`, `MidiCoreReviewAudition.kt`, `MidiCoreAcceptedSongAssembly.kt`,
`core/audition/`; `ui/MidiCoreArrangePage.kt`, `MidiCoreReviewPage.kt`,
`MidiCoreWorkspace.kt`, `MidiCoreWorkspaceShell.kt`.
**Focused proof:** root `MidiCoreArrangementStylePreviewTest`,
`MidiCoreMusicalRepairAlternativeRankerTest`, `MidiCoreArrangementPlanEditTest`,
`MidiCoreAcceptedSongAssemblyTest`; desktop
`MidiCoreArrangePageTest`, `MidiCoreReviewPageTest`, `MidiCoreFocusedWorkflowTest`.
Preserve the six repair intents, meaningful alternative limit, explicit rests and
three-action authority-ready draft path. These tests do not measure acoustic onset.

## Feature AC4 — Accepted MIDI export and Logic handoff

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| AC4-01 | Refresh the current MIDI package matrix and semantic export proof, with complete/separate roles, source expression, padding, rests, boundaries and immutable snapshots. Produce new Logic-ready packages tied to this build and validate them by semantic re-import without launching Logic. Do not claim interactive DAW compatibility. | AC3-01 | TODO |

**Owners:** `core/application/MidiCoreMidiPackageExporter.kt`, accepted assembly,
`core/midi/adapter/JdkMidiWriter.kt`, `ui/MidiCoreExportPage.kt`; existing Logic
matrix test/command and `docs/VALIDATION.md`.
**Focused proof:** `MidiCoreMidiPackageExporterTest`, `MidiCoreAcceptedSongAssemblyTest`,
`JdkMidiWriterTest`, `MidiCoreLogicMatrixTest`; desktop `MidiCoreExportPageTest`.
**Preparation:** `./gradlew prepareLogicMatrix -PlogicMatrixDirectory=build/logic-matrix/<new-run>`.
Only package preparation/semantic validation is required. The retained
[Logic procedure](docs/VALIDATION.md#logic-pro-procedure) is historical reference,
not an active task or release dependency.

## Feature AC5 — Musical usefulness and MIDI release

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| AC5-01 | User supplies five owned/licensed full-song current MIDI projects, at least three unseen, with settings, ownership and exposure declarations. Synthetic fixtures cannot fill this set. | — | WAITING_USER |
| AC5-02 | Freeze the supplied songs/settings before generation and export current comparisons/blank score forms from isolated copies. Keep development cases separate and preserve failed outputs. | AC3-01, AC5-01 | TODO |
| AC5-03 | Collect genuine piano+melody/full-song scores and exact bad bars from the supplied comparison artifacts against PLAN-AUDIO/Validation targets; no live app repair-timing exercise. Failed cases create bounded corrective rows and require fresh applicable evaluation. | AC5-02 | WAITING_USER |
| AC5-04 | Refresh six-page offscreen visual/semantics and service-performance evidence at all three sizes, without a native window or desktop capture. Retain failures and approved goldens; report offscreen evidence as such, not live usability or acoustic-onset proof. | AC3-01 | TODO |
| AC5-06 | Inspect a freshly built private package for bundled JVM, entrypoint, dependencies and absence of required video tools, models, worker, sound library or network. Do not launch the application or GUI installer; package inspection is not installed-startup proof. Preserve the user's installed app. | AC4-01, AC5-04 | TODO |
| AC5-07 | Reconcile final build/engine/export/UI identities with listening, semantic MIDI, offscreen visual and package-inspection evidence; record the actual MIDI release decision. Explicitly disclose that live UI usability, interactive Logic compatibility and installed GUI startup were not tested. | AC4-01, AC5-03, AC5-04, AC5-06 | WAITING_USER |

**Owners:** existing `MidiCoreMusicalEvaluation*`, comparison harness, desktop
offscreen visual/semantics, service-performance and package-inspection checks,
`docs/VALIDATION.md`, `README.md`.
**Proof:** use the [evaluation commands](docs/VALIDATION.md#frozen-musical-evaluation-commands-q01a),
`MidiCoreMusicalEvaluationTest`, `MidiCoreComparisonHarnessTest`; desktop
`MidiCorePinnedVisualTest`, `MidiCoreVisualReviewTest`, `MidiCoreResponsivenessTest`
only in their windowless modes. Package/receipt fixture checks may run without
starting an installed app; no live native-window/capture/installer selector is
required. Reuse current preparation tools, not an old artifact identity. Human
reviews concern supplied musical artifacts and release, not a live MIDI journey;
missing songs or scores do not block video development.

## Evidence and blocker handling

- This queue intentionally carries no old completed IDs or commit chronology.
  Existing capability ownership is summarized in PLAN-AUDIO; Validation retains
  source evidence without authorizing old work.
- For every reproduced failure, add a feature-scoped row with the failing case,
  exact owners, regression and dependent gate. Do not expand a verification row
  into an unbounded engine/UI rewrite.
- For missing user input, record the exact request and keep the gate WAITING_USER.
  For an unavailable host prerequisite, preserve diagnostics and mark the affected
  technical task BLOCKED. Select another independent task if one is ready.
- Do not auto-approve images, music, rights, model terms, provider spend or release.
  Approval belongs to the identified artifact revision, not the feature name.

## Reusable implementation prompt

```text
Implement or verify only <TASK-ID> from the current TASKS-AUDIO.md, after all its
listed dependencies and authorizations are satisfied. Read PLAN-AUDIO, README, AGENTS,
Architecture and the task's relevant MIDI/UI/Validation owner references.
Inspect the exact current candidate, callers and tests; preserve unrelated WIP.
Use <ALLOWED-FILES> and <CANDIDATE-PATH>. Reuse implemented behavior; do not replay
old task suites or recover unavailable historical archives. Split oversized work
with the coordinator before expanding ownership. Do not edit queue status, launch
other agents, commit, download models, run inference, upload or spend unless the
run explicitly authorizes that action. Keep MIDI authority/source/acceptance/export
safe and video storage/runtime independent. Add regression tests for defects.
Run focused tests and make test/build only through a verified headless path,
plus git diff --check. If defaults open a window, use a filtered headless invocation
and report exclusions. Do not open/drive UI, capture the desktop, start a GUI
installer or require a live Logic/user walkthrough. Human artifact review remains
separate; never label offscreen checks as interactive compatibility/usability proof.
Return the task, changed files, actual check results, evidence identity/paths,
remaining limitations and next blocker. Never turn technical tests into a musical,
visual, Logic or release approval. Preserve a failed candidate for bounded repair.
```
