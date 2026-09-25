# Feature implementation queue

Authority: [PLAN.md](PLAN.md). This is the only implementation queue. It starts
from the current software, not from an empty application. There is no inherited
completed-task index, attempt diary, branch schedule or automatic execution state.
Historical evidence stays in the owning references and Git; it is not a second queue.

**This reset is planning-only.** No implementation task or automation is running
because of this document. No commits, model downloads, inference or paid requests
are authorized by the reset.

## How to use this queue

- Feature IDs match PLAN: CORE, AC1–AC5 (audio composition via MIDI) and VG1–VG6
  (independent video generation). A task such as `VG2-01` is one bounded slice.
- States: `TODO`, `RUNNING`, `REVIEW`, `DONE`, `WAITING_USER`, `BLOCKED`, `OPTIONAL`.
  `TODO` means a new verification/implementation obligation, not that all code is
  absent. Existing preview WIP remains TODO until integrated and validated.
- A task is ready only when every listed dependency is DONE and its required
  inputs/authorization exist. `—` means no task dependency. WAITING_USER tasks
  require a real decision/input; never retry them merely to rediscover its absence.
- On an authorized run, start with CORE-01. Then prefer VG1-01 and VG2-01/02/03 to
  finish the existing backend WIP. AC1–AC4 checks and AC5 preparation are independent
  useful work while design or real-video inputs wait. Within either track, take the
  first ready mandatory row. Human waits do not block unrelated ready tasks.
- Request VG3-01 design-process confirmation separately. Do not create mockups or
  production Video UI until the corresponding permission/approval dependencies pass.
- AC1–AC4 reuse implemented features. First run their focused checks and inspect
  current callers. If no defect exists, record verification; do not invent a rewrite
  or force a code commit. Split any real defect into a bounded task under its feature.
- Only the coordinator updates status/integration. Default to one writer. Parallel
  agents require an explicit implementation request and disjoint file ownership.

## Execution and validation contract

1. Read AGENTS, PLAN, README, this row and its owner references. Inspect Git status,
   actual consumers/tests and the current candidate before editing. Preserve unrelated
   tracked/untracked changes, source media, current projects and accepted artifacts.
2. Select exact allowed files from the owners below before implementation. `core`
   means `src/main/kotlin/app/melotrail`; `ui` means
   `desktopApp/src/main/kotlin/app/melotrail/desktop`. Tests mirror those paths.
   New owners below are proposals, not claims that files already exist.
3. Keep one behavior/boundary per task. Split an oversized task here before expanding
   scope. No duplicate job ledger, planner/schema compatibility mode or extra plan,
   inventory, execution log or agent-prompt document.
4. Every implementation runs its focused tests, `make test`, `make build` and
   `git diff --check`. Run real native/media checks where required. Ordinary tests
   use owned fixtures/fakes, never models, hosted jobs or credentials. Fixes require
   a regression that fails on the old behavior; never weaken checks to get green.
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
   [Validation](docs/VALIDATION.md), with video details in [TABI video](docs/TABI_VIDEO.md).
   A changed engine, export policy or reviewed UI/media invalidates applicable evidence.

Focused JVM tests below are class selectors, run as
`./gradlew :test --tests '*ClassName'` or
`./gradlew :desktopApp:test --tests '*ClassName'` as indicated; combine selectors
for a task. Use the configured JDK 21 without editing toolchain files merely to
match a shell. Generate the owned motion fixtures before running Node tests:

```bash
./gradlew :test --tests '*VideoMotionDescriptorFixtureTest'
MELOTRAIL_MOTION_FIXTURE_ROOT="$PWD/build/video-motion-fixtures" \
  node --test tools/video-motion/render.test.cjs tools/video-motion/scenery.test.cjs
```

These checks use the explicitly configured Node/Canvas runtime, not model inference.

## Feature CORE — Reproducible baseline

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| CORE-01 | Identify the exact current candidate and preserved WIP; run current focused/full checks and motion tests. Record concrete blockers and distinguish installed/tooling issues from code failures. Do not replay a historical admission or delete local data. | — | DONE |

CORE-01 evidence (2026-09-24): candidate root `/Users/marcoandreose/DEV/lab/melotrail`, branch `codex/pi-automation`, tested HEAD `7a91f1ff76d6fc305e3459a6c6f94cfaab159832` (runner checkpoint commits included in the tested content). The content-addressed source-input manifest at `build/core-01/repair-20260924T041944Z/integration/source-manifest-after.txt` identifies the final tested candidate (including this disposition); its digest and post-gate comparison are retained in the ignored integration receipt. Preserved unrelated untracked local data: `.venv-transcription-spike/`, `.venv-worker/`, `.venv/`, and `tools/__pycache__/`; no tracked source modifications were present. JDK Temurin 21.0.11, Gradle 8.14.3, Kotlin plugins 2.2.21, `/opt/homebrew/bin/node` 25.8.2, npm 11.14.1 and Canvas 0.1.80 were verified.

Focused architecture/documentation and preview suites, production fixture generation and both Node motion suites passed (Node: 22/22); fresh `./gradlew test --no-build-cache --rerun-tasks` passed root and `:desktopApp` (14 tasks executed). `make test` and `make build` passed with tasks UP-TO-DATE; `git diff --check` and `git diff --cached --check` passed. Logs/reports: ignored `build/core-01/step-1.1-20260924T035114Z-23206/`, `build/core-01/step-1.2/`, `build/core-01/architecture-step-2.1/`, `build/core-01/step-2.2-preview/`, `build/core-01/step-2.3-20260924T040537Z-30439/`, and `build/core-01/integration-20260924T041500Z/`. These technical checks do not establish integrated preview delivery, artistic/video approval, MIDI listening, Logic approval or release readiness. Next ready task: VG1-01.

**Owners:** build/Makefile configuration (inspect first, no default edits),
`TargetArchitectureRulesTest`, `DocumentationIntegrityTest`, current source/test
owners. Preserve existing `VideoClipGeneration.kt`, `VideoResultImport.kt`, their
test and changes in `LocalVideoBackend.kt`, `VideoMediaProbe.kt`,
`VideoProjectStore.kt`, `VideoProject.kt`, `LocalVideoBackendTest.kt`.
**Proof:** focused architecture/documentation and preview tests, full gates and
Node checks. If isolation is needed, include task-owned dirty/untracked source
and inventory exclusions without moving/deleting the original files. No cleanup
of `.venv*`, `sounds`, `data/audio`, unknown caches or external evidence.

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
| AC3-01 | Walk the implemented style → plan → full-draft playback path, scoped repair A/B, Use/Undo and reopen. Verify one player, fixed melody/loop position, locks, revision guards, failed-Apply retry and accepted-only export readiness. Record only reproduced defects for repair. | AC2-01 | TODO |

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
| AC4-01 | Refresh the current Logic matrix and semantic export proof, with complete/separate roles, source expression, padding, rests, boundaries and immutable snapshots. Produce new packages and blank human forms tied to this build. | AC3-01 | TODO |
| AC4-02 | User imports, plays, saves/closes/reopens the applicable matrix in Logic; record exact versions, hashes, alignment/controllers/drums/endings and all failures. No old compatibility pass substitutes for current evidence. | AC4-01 | WAITING_USER |

**Owners:** `core/application/MidiCoreMidiPackageExporter.kt`, accepted assembly,
`core/midi/adapter/JdkMidiWriter.kt`, `ui/MidiCoreExportPage.kt`; existing Logic
matrix test/command and `docs/VALIDATION.md`.
**Focused proof:** `MidiCoreMidiPackageExporterTest`, `MidiCoreAcceptedSongAssemblyTest`,
`JdkMidiWriterTest`, `MidiCoreLogicMatrixTest`; desktop `MidiCoreExportPageTest`.
**Preparation:** `./gradlew prepareLogicMatrix -PlogicMatrixDirectory=build/logic-matrix/<new-run>`.
AC4-02 uses the [Logic procedure](docs/VALIDATION.md#logic-pro-procedure).

## Feature AC5 — Musical usefulness and MIDI release

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| AC5-01 | User supplies five owned/licensed full-song current MIDI projects, at least three unseen, with settings, ownership and exposure declarations. Synthetic fixtures cannot fill this set. | — | WAITING_USER |
| AC5-02 | Freeze the supplied songs/settings before generation and export current comparisons/blank score forms from isolated copies. Keep development cases separate and preserve failed outputs. | AC3-01, AC5-01 | TODO |
| AC5-03 | Collect genuine piano+melody/full-song scores, exact bad bars and repair times against PLAN/Validation targets. Failed cases create bounded corrective rows and require fresh applicable evaluation. | AC5-02 | WAITING_USER |
| AC5-04 | Refresh six-page technical visual/performance evidence at all three sizes and actual foreground capture on a capturable host. Retain failures, do not replace approved goldens automatically or relabel frame replay as desktop capture. | AC3-01 | TODO |
| AC5-05 | User completes the MIDI journey and reviews hierarchy, fidelity, keyboard/resize behavior and audible response; record real per-page decisions and acoustic-onset measurement or its explicit unresolved limitation. | AC5-04 | WAITING_USER |
| AC5-06 | Prove the current MIDI app installs/starts from a fresh private DMG copy with bundled JVM and no video tools, models, worker, sound library or network. Preserve the user's installed app. | AC4-01, AC5-04 | TODO |
| AC5-07 | Reconcile final build/engine/export/UI identities with music, Logic, visual and installation evidence; record the actual MIDI release decision and limitations. | AC4-02, AC5-03, AC5-05, AC5-06 | WAITING_USER |

**Owners:** existing `MidiCoreMusicalEvaluation*`, comparison harness, desktop
visual/responsiveness/install checks, `docs/VALIDATION.md`, `README.md`.
**Proof:** use the [evaluation commands](docs/VALIDATION.md#frozen-musical-evaluation-commands-q01a),
`MidiCoreMusicalEvaluationTest`, `MidiCoreComparisonHarnessTest`; desktop
`MidiCorePinnedVisualTest`, `MidiCoreVisualReviewTest`, `MidiCoreResponsivenessTest`,
`MidiCoreNativeResponsivenessTest`, `MidiCoreNativeInstallCheckTest`.
Run `:desktopApp:nativeDesktopCapture` and
`:desktopApp:nativeInstallSmoke -PnativeInstallDirectory=<new-absolute-directory>`
on the appropriate host. Reuse the existing preparation tools, not an old artifact
identity. Manual MIDI reviews stay at the end of unpaid engineering; missing songs
or scores do not block video development.

## Feature VG1 — Finished artwork and explicit local setup

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| VG1-01 | Verify current project/import/look/preparation/setup contracts for a finished scene and optional ready layers. Establish flat-I2V versus controlled-motion capabilities, actual alpha/geometry/coverage and exact consumed pins. Missing input/setup gets a specific remedy; no synthesis/download is triggered. | CORE-01 | DONE |

**Owners:** `core/video/application/VideoProjectLifecycle.kt`, `VideoAssetImport.kt`,
`VideoAnimationAssets.kt`, `VideoSceneLooks.kt`, `VideoScenePreparation.kt`,
`VideoPromptCompiler.kt`; domain prepared-scene and adapter import/store/setup owners.
**Focused proof:** `VideoProjectStoreTest`, `VideoAssetImportTest`,
`VideoAnimationAssetsTest`, `VideoSceneLooksTest`, `VideoScenePreparationTest`,
`LocalVideoSetupTest`. Use owned fixtures first; selected real sources come from
`docs/pictures/video/` and subfolders, preserving inspiration-only labels and rights.
No old failed-image archive is required. Fresh host execution requires a separately
admitted request with current pins and bounded resources.

VG1-01 evidence (2026-09-24): tested source candidate HEAD `6815543197c12b61f36d070fd1db317d848f0a3f`. At test start, `TASKS.md` was modified and unrelated untracked `.venv-transcription-spike/`, `.venv-worker/`, `.venv/`, `tools/__pycache__/` were preserved; there were no staged changes or submodules. Final disposition-only diff is in `TASKS.md`. JDK Temurin 21.0.11; Node 25.8.2/npm 11.14.1 with repository-pinned Canvas 0.1.80. Generated Node fixture set `build/video-motion-fixtures/fixture-set.json` contains unit-scale, nonunit-scale, effect-only-static, opaque-head-black-alpha and wide-scenery. The finished-v1/v2 replacement test is instead `VideoAssetImportTest.replacement artwork receives a new immutable identity and earlier bundle survives reopen`; IDs `finished-v1` and `finished-v2` are confirmed by `src/test/kotlin/app/melotrail/video/VideoAssetImportTest.kt:335-354`, exercised in the passing focused suite. No `retry-reference` fixture exists in the current test sources or reports; that previously asserted fixture identity is withdrawn, not claimed. JVM importer fixture selector `VideoMotionDescriptorFixtureTest` passed and its JUnit XML is `build/test-results/test/TEST-app.melotrail.video.VideoMotionDescriptorFixtureTest.xml` (1 test, 0 failures/errors); it generates the five Node fixture cases, not the replacement test. Archived fixture evidence: `build/vg1/step-3.1-20260924T062554Z/motion-fixture-evidence.tar`, SHA-256 `d94bc26563ddd754a60125fd8f039f3f8573225b262ed851e5128f7c8fd46ee5` (verified against archived bytes).

Fresh verification passed: full selectors `./gradlew :test --no-build-cache --rerun-tasks --tests '*VideoProjectStoreTest' --tests '*VideoAssetImportTest' --tests '*VideoAnimationAssetsTest' --tests '*VideoPreparedSceneStoreTest' --tests '*VideoSceneLooksTest' --tests '*VideoScenePreparationTest' --tests '*VideoPromptCompilerTest' --tests '*LocalVideoSetupTest' --tests '*VideoMotionRendererTest' --tests '*VideoMediaProcessTest' --tests '*LocalVideoBackendTest' --tests '*VideoClipGenerationTest' --tests '*TargetArchitectureRulesTest' --tests '*DocumentationIntegrityTest'` (6 executed); `./gradlew :test --tests '*VideoMotionDescriptorFixtureTest'` (1 executed, 5 up-to-date); `MELOTRAIL_MOTION_FIXTURE_ROOT="$PWD/build/video-motion-fixtures" node --test tools/video-motion/render.test.cjs tools/video-motion/scenery.test.cjs` (22/22). `make test` passed (root and `:desktopApp`, 1 executed/13 up-to-date); `make build` passed (14 up-to-date); `git diff --check` passed. Logs/exit files are under ignored `build/vg1/step-3.1-20260924T062554Z/`. Escalation recheck after correcting the archive citation: `build/vg1/escalation-20260924T063623Z/` (focused 6 executed; fixture generator 1 executed/5 up-to-date; Node 22/22; `make test` 1 executed/13 up-to-date; `make build` 14 up-to-date; `git diff --check` passed). The original archived fixture bytes still hash to the corrected value above. No defect reproduced. No real model/media inference, artistic/rights/UI/production-generation/release approval is claimed; preview WIP and unrelated user data remain untouched.

## Feature VG2 — Durable previews and immutable takes

Partial implementation/WIP exists. Preserve it and complete the missing production
connections; do not assume fake-backend tests prove a working renderer job.

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| VG2-01 | Finish the durable controlled-render bridge: persisted intent/claim precedes the real media stage, with pinned dependencies and frame ranges, owned cancellation and restart reconciliation. No bypass of admission or second ledger; ComfyUI must not claim compositor support. | VG1-01 | TODO |
| VG2-02 | Finish fully decoded result import and persisted take review/selection. Bind output to the successful current attempt, scene and measured media facts; strip incidental audio and reject stale/corrupt/mismatched results. New/rejected takes never overwrite or silently replace selected ones. | VG2-01 | TODO |
| VG2-03 | Route flat-image I2V and prepared controlled motion through production services with honest capability/limit reporting. Prove five-second and 20–30-second controlled previews use the full-output renderer; keep the measured short I2V limit explicit, not a promise of long generative coherence. | VG2-02 | TODO |

VG02 continuation — Step 1.1 verified on `dff7f4dc5` plus the preserved preview
candidate: narrow owned-process/resource seams retain real admission and claims;
the fixture now requires a durable FAILED result rather than a deadline escape.
Focused host-check/controlled-stage tests passed (6 tasks executed), `make test`
passed (1 executed, 13 up-to-date), `make build` passed (14 up-to-date), and
`git diff --check` passed. Logs: `build/vg2/vg02-continuation/1.1-*.log`.
Earlier CORE/VG1 evidence and VG2 commits are preserved; VG2 rows remain open
pending current combined-candidate and native proof. No opt-in probe was run.

Step 1.2: full synthetic 150/600/900-frame production imports, immutable
artwork/pins/claims/measurements, ordering and reconstructed exact reimport pass.
The complete-path test exposed a disk sampler race with removed publication staging
names; the sampler now tolerates only missing entries, not aliases or other I/O
failures. Focused suites passed (6 executed), `make test` and `make build` passed,
`git diff --check` passed; logs `build/vg2/vg02-continuation/1.2-*.log`.
Synthetic process facts prove wiring only, not native rendering or decoding.

Step 1.3: production-composition cancellation during the second guarded import
passes: active budget monitoring cancels the owned child, keeps its finite memory
ceiling, preserves first-take/artwork hashes and project state, retains failure
evidence, and starts neither the 900-frame job nor a retry. Focused suites (6
executed), `make test`, `make build`, and diff check passed; logs
`build/vg2/vg02-continuation/1.3-*.log`.

Step 1.4: reviewed combined candidate `5106d2382` including preserved registration,
preflight, budget-before-admission, per-job disk baselines, monitor-through-import,
finite child ceilings and the production backend identity. All five focused suites
passed fresh (6 executed); `make test`, `make build`, and diff check passed.
Logs: `build/vg2/vg02-continuation/1.4-*.log`. Step 3.3's fixture wiring gate is
complete; VG2-03 stays open for native proof. This is coordinator technical review,
not independent human or artistic approval.

Step 2.1: opt-in preview check now independently measures source/published clips,
retains decoded first/middle/final and both-side chunk-boundary PNGs, compares the
subject region to absolute rendered frames, and checks a seeded authored blink
phase rather than unequal endpoints. The owned fixture uses contrasting pose art
and seed 73 (seed 42's first blink falls after five seconds). Missing, invalid,
wrong-size and discontinuous samples reject. Fresh focused media/motion/host
suites (6 executed), `make test`, `make build`, and diff check passed; logs
`build/vg2/vg02-continuation/2.1-*.log`. These are synthetic wiring checks; native
frame inspection and human motion review are not claimed.

Step 2.2: every preview now reconstructs project/job/coordinator/stage/import
services, reconciles and exact-reimports with a native-launch-rejecting process
boundary; project, ledger, takes, selections and revisions remain identical.
A reproduced gap required a narrow `VideoResultImport.kt` repair: controlled
exact replay verifies persisted provenance, current attempt, prepared pins and
source/published digests without another decode job. Initial import and flat-I2V
measurement behavior remain unchanged. Regressions reject same-size/restored-time
source tampering and noncurrent attempt IDs while preserving published takes.
Fresh host/coordinator/import/generation suites (6 executed), full test/build and
diff checks passed; logs `build/vg2/vg02-continuation/2.2-*.log`.

**Owners by slice:**
- VG2-01: existing/WIP `core/video/application/VideoClipGeneration.kt`,
  `VideoJobCoordinator.kt`, `core/video/domain/VideoGenerationJob.kt`,
  `core/video/adapter/VideoMotionRenderer.kt`, `LocalVideoBackend.kt`; add a narrowly
  owned media-stage adapter if needed, not another generative provider.
- VG2-02: WIP `core/video/adapter/VideoResultImport.kt`, `VideoMediaProbe.kt`,
  `VideoProjectStore.kt`, `core/video/domain/VideoProject.kt`; existing take selections
  and project revision guards. Native versus output facts must be measured/bound,
  not trusted merely because the caller supplied them.
- VG2-03: generation/preparation callers and tests; keep the existing ComfyUI graph
  and owned runtime pins unchanged unless a separate approved task changes them.

**Focused proof:** `VideoClipGenerationTest`, `VideoMotionRendererTest`,
`VideoJobCoordinatorTest`, `VideoProjectStoreTest`, `LocalVideoBackendTest`,
`ComfyVideoClientTest`; add result-import regressions. Run Node tests and real
owned-fixture render/probe checks with configured tools. Prove duplicate admission,
crash/reopen, cancel during validation, stale revision, digest mismatch, rejected
selection and no-overwrite behavior. Model inference is an explicit host check,
not a test dependency or permission implied by this planning reset.

## Feature VG3 — Independent Video workspace

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| VG3-01 | Obtain confirmation of Video-only design scope/process and permission to create flow artifacts. Do not redesign the six MIDI pages or change agent instructions without permission. | — | WAITING_USER |
| VG3-02 | Prepare a reviewable feature flow covering empty/setup/create/open, finished-artwork import and motion setup, prompt/duration, generation/recovery, moving review and full-cut export at supported sizes. Reuse current visual primitives. | VG3-01, VG1-01 | TODO |
| VG3-03 | Record explicit approval naming the mockup revision/digest, reviewer/date, covered surfaces and deviations. Agent recommendation, browser rendering and old reference art are not approval. | VG3-02 | WAITING_USER |
| VG3-04 | Add the lazy app-level MIDI/Video shell, independent create/open and `--video` startup. Keep six MIDI destinations, retained tab state and one MIDI player; entering Video pauses it without losing position. Missing video setup leaves MIDI usable. | CORE-01, VG3-03 | TODO |
| VG3-05 | Wire finished-scene/optional-layer import, thumbnails, visible placement/anchors, motion prompt, duration and setup/capability guidance. Replacements/reopen preserve originals and unaffected selections; no JSON, outfit-transfer or Generate look UI. | VG1-01, VG3-04 | TODO |
| VG3-06 | Wire production Generate/Cancel/Retry/recovery and take review/selection. Show real stage/progress or unknown state; duplicate clicks, tab changes, late results and restart cannot lose selections or start duplicate work. | VG2-03, VG3-05 | TODO |
| VG3-07 | Implement bounded off-UI-thread frame decoding and one silent preview session, with accurate seek/frame-step, corrupt-file errors and owned-process teardown. This backend slice can proceed while design review waits. | VG2-02 | TODO |
| VG3-08 | Expose moving take playback with play/pause/seek/frame-step and resize through that decoder. Prove actual frames in an app-window capture and stop preview on departure without creating a MIDI player. | VG3-06, VG3-07 | TODO |

**Owners by slice:**
- Design: proposed `.mockups/flows/video-workspace/` and shared `.mockups/design-system/`,
  following [UI guideline](docs/UI_GUIDELINE.md). Use standalone HTML/CSS/JS,
  shared tokens/components and a navigator; link the approved artifact here before
  implementation. No artifact or approval currently exists.
- Shell: `ui/DesktopMain.kt`, `MidiCoreDesktopComposition.kt`; new
  `MelotrailAppShell.kt`, `video/VideoDesktopComposition.kt`, `video/VideoWorkspace.kt`.
- Inputs: new `ui/video/VideoAssetsPanel.kt`, `VideoBriefPanel.kt`, `VideoSetupPanel.kt`
  and workspace state/intents. No automatic model installation.
- Jobs: new `ui/video/VideoGenerationPanel.kt`, `VideoTakeGallery.kt` and composition.
- Decode: new `core/video/adapter/VideoPreviewDecoder.kt`, reusing media supervision.
- Playback: new `ui/video/VideoPreview.kt` plus gallery/workspace lifecycle.

**Focused proof:** new `MelotrailAppShellTest`, `VideoWorkspaceTest`,
`VideoInputFlowTest`, `VideoGenerationFlowTest`, `VideoPreviewDecoderTest`,
`VideoPreviewTest`; retain `MidiCoreDesktopCompositionTest` and MIDI visual tests.
`make video` becomes functional only after VG3-04. Capture empty/ready/blocked/
progress/error states at 1536×1024, 1280×900 and 720×900; inspect actual images.
No production surface may outrun VG3-03 approval; material changes need renewed review.

## Feature VG4 — Continuous scene planning

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| VG4-01 | Persist a versioned exact-frame continuous plan with shared clock/seed, action schedule, component reuse and chunk ranges. Replace short-shot/repeat planning after moving required prompt/fingerprint/estimate behavior behind tests; remove exclusive obsolete callers/fields/tests, not historical assets. | VG2-03 | TODO |
| VG4-02 | Validate full-duration camera/depth/occlusion/scenery coverage and compile continuous subject/effect/scenery state across chunks, including resume and boundary support frames. Missing coverage blocks readiness; edits invalidate only affected work. | VG4-01 | TODO |

**Owners:** new `core/video/domain/VideoAssembly.kt`,
`core/video/application/VideoAssemblyPlanner.kt`; current `VideoBrief.kt`,
`VideoPromptCompiler.kt`, project persistence, prepared-scene contracts,
`VideoMotionRenderer.kt`, `tools/video-motion/render.cjs`, `scenery.cjs`.
Replace `VideoShotPlanner.kt` and exclusive tests only after consumer analysis.
**Focused proof:** new `VideoAssemblyPlannerTest`, existing
`VideoPromptCompilerTest`, `VideoMotionDescriptorFixtureTest`,
`VideoMotionRendererTest` and Node tests. Assert exact 5,400/7,200/9,000 frames,
rigid depth motion and offscreen scenery joins, no resets/wrap/reverse/freeze,
continuous blink/particle/random state and changed-pin rejection. Subtle-motion
reuse is allowed; whole-footage repeat-to-fill and false unique-seconds totals are not.
No full-render proof is claimed by planning 9,000 frames.

## Feature VG5 — Full rendering, review and export

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| VG5-01 | Add bounded chunk rendering/encoding and durable checkpoint/resume over the resolved plan. Use explicit tool paths, continuous timestamps and exact trim ranges; handle cancel, restart, disk/time limits and changed inputs without retaining all frames in RAM. | VG4-02 | TODO |
| VG5-02 | Validate and atomically publish a complete silent MP4 plus provenance to a new destination. Full decode checks 1920×1080, H.264, square pixels, 30 fps, exact duration, stream count and every boundary; collisions/failures preserve previous output. | VG5-01 | TODO |
| VG5-03 | Run real 60-second continuity/resource proof, then a complete owned 180–300-second encode. Retain native/output geometry/cadence, chunk/scenery-seam evidence and separate stage timings/disk/memory; update estimates from measurement. | VG5-02 | TODO |
| VG5-04 | Wire full-cut review, duration/coverage/reuse/estimate facts, join inspection, destination, export result and reveal. Preview/output consume the same resolved plan; changes stale readiness without deleting old cuts. | VG3-08, VG5-03 | TODO |

**Owners:** new `core/video/adapter/VideoEncoder.kt`,
`core/video/application/VideoExport.kt`; current motion/media/job/project owners;
new `ui/video/VideoAssemblyPanel.kt`, `VideoExportPanel.kt`, composition/workspace.
**Focused proof:** new `VideoEncoderTest`, `VideoExportTest`, `VideoExportFlowTest`;
extend `VideoMediaHostCheck` for the full-output proof. The existing
`:videoMediaProbe -PvideoToolsDirectory=<absolute-tools-directory>` proves only its
short fixture until extended. Verify actual capabilities of the narrow pinned
FFmpeg build; a required distribution/filter change is a separate bounded slice,
not permission to use arbitrary PATH tools. Include restart parity, dropped/
duplicated frames, audio-bearing source, truncated output and late cancellation.
VG5-04 must use the approved flow; no unapproved general timeline editor.

## Feature VG6 — Installed delivery and real-video acceptance

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| VG6-01 | With selected usable artwork/rights and an explicitly bounded host run, prepare three real 20–30-second app clips: base motion, contrasting motion with fixed art, replaced art with other compatible settings fixed. Record requests, pins, resources and limitations, without hidden scripts/JSON. | VG3-08 | TODO |
| VG6-02 | User reviews all three clips at normal speed against supplied appearance and requested motion; record fidelity, temporal stability, object/effect/scenery coherence and actual decision. Failed local quality may inform an optional hosted proposal, not silent fallback. | VG6-01 | WAITING_USER |
| VG6-03 | Prove private installed-app MIDI startup/export without optional tools and configured Video preview/export/cleanup. Resolve explicit Python/Node/Canvas/FFmpeg paths, package/setup strategy and notices; include motion regressions in normal validation. | VG5-04 | TODO |
| VG6-04 | Complete owned-fixture UI/end-to-end checks: import → prompt → generate → cancel/retry/reopen → full review/export, all target durations and three viewports, missing-input failures, keyboard/accessibility/performance and unchanged MIDI. Prepare current final-review evidence. | VG6-03 | TODO |
| VG6-05 | After early approach acceptance, produce a real 3–5-minute app export from selected artwork/prompt under an explicitly bounded run. Retain source/output identities, stage resources/cost, rejected takes and honest component reuse; no synthetic media substitutes. | VG6-02, VG6-04 | TODO |
| VG6-06 | User watches the entire cut/joins, accepts fidelity, prompt adherence, continuous motion and repetition, then imports/plays it in the chosen Apple editor. Record the final video release decision; music placement and publication remain external. | VG6-05 | WAITING_USER |

**Owners:** VG6-01/05 use existing production services, not one-off product scripts;
VG6-03 owns `desktopApp/build.gradle.kts`, native install checks, proposed
`ui` test owner `video/VideoInstalledAppCheck.kt`, architecture rules and notices;
VG6-04 owns new `VideoEndToEndTest`, `VideoVisualTest` and reviewed technical
resources; decisions/evidence summaries live in `docs/VALIDATION.md`,
`docs/TABI_VIDEO.md`, `README.md`.
**Proof:** register and run a bounded `:desktopApp:videoInstalledSmoke` with a new
private install destination (this command does not exist yet); run the named
E2E/visual suites, full gates and actual app captures. Synthetic fixtures establish
technical integrity only. VG6-02 does not block VG4/VG5 or installed/UI engineering.
A full-generation failure creates a scoped corrective task and keeps acceptance open.

## Optional feature — Explicitly selected hosted video fallback

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| VG-OPT-01 | Only after measured local limitations and explicit provider/budget selection: scope one hosted video adapter through existing jobs/UI. Recheck current capabilities, terms, upload/privacy disclosures and capped cost; prove fake HTTP safety before any separately authorized live comparison. | VG2-03, VG3-06 | OPTIONAL |

This is unselected and excluded from automatic admission. Before activation, split
adapter, credential/upload handling and bounded live proof into exact-file slices
here. Reuse durable uncertain-submit reconciliation, one in-flight policy,
quarantined downloads and immutable takes. Do not revive hosted picture generation
or quote historical prices as a current budget. Local success does not require it.

## Evidence and blocker handling

- This queue intentionally carries no old completed IDs or commit chronology.
  Existing capability ownership is summarized in PLAN; Validation/TABI retain
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
Implement or verify only <TASK-ID> from the current TASKS.md, after all its
listed dependencies and authorizations are satisfied. Read PLAN, README, AGENTS,
Architecture and the task's relevant MIDI/UI/Validation/TABI owner references.
Inspect the exact current candidate, callers and tests; preserve unrelated WIP.
Use <ALLOWED-FILES> and <CANDIDATE-PATH>. Reuse implemented behavior; do not replay
old task suites or recover unavailable historical archives. Split oversized work
with the coordinator before expanding ownership. Do not edit queue status, launch
other agents, commit, download models, run inference, upload or spend unless the
run explicitly authorizes that action. Keep MIDI authority/source/acceptance/export
safe and video storage/runtime independent. Add regression tests for defects.
Run focused tests, make test, make build, git diff --check and applicable bounded
motion/native checks, or name exactly which checks require the coordinator host.
Return the task, changed files, actual check results, evidence identity/paths,
remaining limitations and next blocker. Never turn technical tests into a musical,
visual, Logic or release approval. Preserve a failed candidate for bounded repair.
```
