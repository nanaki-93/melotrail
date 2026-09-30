# Feature implementation queue

Authority: [PLAN.md](PLAN.md). This is the only implementation queue. It starts
from the current software, not from an empty application. There is no inherited
completed-task index, attempt diary, branch schedule or automatic execution state.
Historical evidence stays in the owning references and Git; it is not a second queue.

**This reset is planning-only.** No implementation task or automation is running
because of this document. No commits, model downloads, inference or paid requests
are authorized by the reset.

## Production-first priority (2026-09-28)

User decision, updated 2026-09-30: prioritize a complete video and a reusable
**TABI train series**: a new city exterior and a different sequence of quiet
activities for each episode. Tokyo remains the first 180-second pilot; Kyoto,
Madrid and Rome are future choices, not admitted jobs or ready scenery packs.
**Next creative slice to scope/admit is VG2-08's matching neutral/head/alpha kit**,
not the already-DONE VG2-06 breathing test and not Video UI. The held-pose control
is fixture-tested; actual artwork and readable motion remain blocked. Independent
fixture-only pose-schedule work can be scoped under VG4-07; no work is launched
by this documentation request.

Follow VG2-08/07 wave repair/review → VG2-09/10 one additional activity and review;
VG4-05 scenery join → VG4-07 pose-schedule representation → VG4-02 and VG5-01/02
minimal binding/execution/encoding → VG5-03/05 combined 60-second proof/review →
VG4-06 full corridor → VG5-06/07 complete Tokyo film/review. Fixture work may
proceed independently where its own dependencies allow it. The film needs
continuous far/middle/near travel, distinct Tokyo views about every 5–10 seconds,
quiet travel and at least two reviewed non-blink activities with clean returns.
Then VG5-08/09/10 prepares/tests/reviews one selected second city with a materially
different action script. Only after that short reuse proof resume VG4-01 app
integration, VG2-03 integration assessment and VG3/VG5 app controls. The later
VG6 full app export uses the selected second city after its full corridor is ready.
VG6 still requires fresh app/installed/full-video evidence; a harness pilot does
not close those gates. The earlier evidence's “next VG4-01” and app-first sequencing
statements are superseded only as scheduling instructions, not rewritten results.

Existing DONE rows stay DONE; no new proof or human gate is completed by this
update. Keep the four MP4s, accepted corrected mask, original art and sealed
receipts unchanged. Use new current-schema private projects/identities, never
migrate historical projects. No implementation, native/model job, download,
hosted request, resource-limit change or commit is authorized here.

## How to use this queue

- Feature IDs match PLAN: CORE, AC1–AC5 (audio composition via MIDI) and VG1–VG6
  (independent video generation). A task such as `VG2-01` is one bounded slice.
- States: `TODO`, `RUNNING`, `REVIEW`, `DONE`, `WAITING_USER`, `BLOCKED`, `OPTIONAL`.
  `TODO` means a new verification/implementation obligation, not that all code is
  absent. Existing preview WIP remains TODO until integrated and validated.
- A task is ready only when every listed dependency is DONE and its required
  inputs/authorization exist. `—` means no task dependency. WAITING_USER tasks
  require a real decision/input; never retry them merely to rediscover its absence.
- On an authorized run, recheck the current CORE baseline without replaying completed
  native experiments. Prefer the production-first sequence above, starting with
  VG2-08's next separately admitted input slice. Take the first ready row within
  that sequence, not the lowest task number; never rerun DONE VG2-06 as a shortcut.
  AC1–AC4 checks, AC5 preparation and independent bounded pilot-support fixtures
  remain useful during human waits; deferred app tasks do not bypass their new gates.
- After full-pilot and second-city reuse review, request VG3-01 design-process
  confirmation separately.
  Do not create mockups or production Video UI until its permission/approval gates
  pass. Approval of this production order is not design permission.
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
| VG1-02 | Admit measured transparent scenery overlays over opaque supplied backing, without counting transparent bounds as coverage. Keep JVM and Node full-trajectory checks aligned and prove depth/occlusion pixels with owned fixtures. | VG1-01, VG2-02 | DONE |
| VG1-03 | Reproduce and correct the reported window cutout around TABI's leaf/frond silhouette in a new preparation for future footage. Compare original pixels/mask at the edge, preserve subject pixels and unaffected regions, and add an edge regression. Do not alter or regenerate the approved 30-second video. | VG1-02 | DONE |

VG1-03 input (2026-09-28): user reports incorrect cropping around TABI's leaves
in the window, explicitly predating the latest run. Treat this as a known reported
mask defect, not a diagnosed renderer regression. First localize the exact edge
against original artwork and current window/foreground masks; no invented leaf
pixels or whole-scene regeneration. Owners: a new scratch preparation and focused
mask regression, selected derived assets only, this queue and TABI/Validation.
Production mask/import/occlusion owners may be inspected, but a production repair
requires a separately reproduced/scoped bug. Current MP4, all source artwork,
sealed receipts, takes and prior masks remain immutable. This is the next visual
preparation correction before admitting longer footage, not a request to remake
this video.

VG1-03 admission (2026-09-28): user says “ok, correct it”. New scratch owner:
`build/tabi-leaf-mask.ey9rmD/`; docs owners remain this queue, TABI and Validation.
Inspection reproduces an oversized polygon around the small right frond cluster:
it retains patches of original exterior above/between the leaves. The original
still-study script explicitly called that selection non-animation-ready. Correct
only this local contour, deriving synchronized window/foreground/occlusion alpha
and the new starting still from unchanged original RGB. Keep eye cutouts, scenery,
all unaffected pixels, old masks/art/receipts and all four videos unchanged.
One initial candidate plus at most two bounded local corrections; local masking,
static inspection, isolated production asset/prepared-scene import and regressions
only. No model jobs, video renders/encodes, new takes, downloads, production changes
or commits. This is a supplied-artwork preparation repair, not automatic extraction
or proof of corrected motion. Final still review remains human.

VG1-03 correction evidence: new source bundle
`build/tabi-leaf-mask.ey9rmD/prepared/`, with
[before/after stills](build/tabi-leaf-mask.ey9rmD/prepared/review/leaf-mask-comparison.png).
The coarse polygon both retained old exterior and clipped a middle frond's dark
outline. A local original-coordinate cubic contour changes1,164 window-mask pixels:
960 expose more scenery,204 restore foreground protection. All foreground/occlusion
RGB, eye assets/cutout, scenery planes and pixels outside that mask change remain
exact; window bounds do not expand. Synchronized window, foreground, occlusion and
starting composition are supplied together. Local mask antialiasing uses8x sampling
and a <=3/>=252 endpoint snap; no RGB painting or generated art.
Two mask regressions fail against the old assets. The first candidate also exposed
small alpha ringing and a scratch Pillow identity-affine premultiplication mismatch;
its files/logs remain retained. One correction preserves strict opaque witnesses
and uses a byte-preserving starting crop; all six scratch checks pass. Production
asset/prepared-scene import passed in a new private project in5.32s; zero jobs/takes,
rendering, encoding or model work. `package-proof.json` verifies imported hashes,
measured alpha and unaffected pixels. Focused/Node/full test/build/diff results are
in scratch final-gate logs. No production code or old video/artwork changed.
REVIEW for human inspection of these new stills; no corrected moving-video claim.
Use the new bundle only for a future explicitly admitted render, not to replace
or silently alter the existing 30-second take. Larger-duration/UI/release gates
remain separate. See [mask correction](docs/TABI_VIDEO.md#leaf-window-mask-correction-2026-09-28).

VG1-03 subsequent user decision (2026-09-28): “ok, we can continue with the next
step”, responding to the corrected-mask delivery, accepts that source bundle for
future work. Scoped event: `build/tabi-next-preflight.iMUH0P/mask-user-review.json`;
comparison SHA `1637fcf810064a5a254f98617718f62065a98c18f27fb95b34f5c3dba17a0f35`,
preparation SHA `d63fa5d92d9d2ff8276287373a7f911b91d09265c480e15655def9eabd37b99f`.
VG1-03 is DONE for this source preparation and its prior verified import/regressions.
This is acceptance to proceed, not a detailed per-edge report or corrected-motion
approval. Old PENDING/UNREVIEWED receipts, project selections and videos are unchanged;
no video uses this corrected mask yet. Full-duration/UI/release gates remain open.

VG1-02 admission (2026-09-28): the user authorized the detailed Tokyo three-plane
parallax proof. Inspection found that the importer rejects every non-opaque
scenery layer; merely removing that rejection would let transparent bounds hide
coverage gaps. Bounded owners: `VideoAnimationAssets.kt`, `VideoGenerationJob.kt`,
`tools/video-motion/scenery.cjs`, their importer/preparation/descriptor and Node
tests (including `VideoJobCoordinatorTest.kt`'s hand-written descriptors), this
queue, TABI and Validation. Preserve opaque backing and full-trajectory
coverage, native-size limits, masks, pins and immutable publications. No schema
migration, automatic extraction feature, provider change or commit is authorized.
Artwork preparation remains a separate, bounded ComfyUI experiment; this row does
not imply artistic acceptance or completion of VG2-03.

VG1-02 technical evidence (2026-09-28): HEAD `b208cc70068eda86c2a236f1cd242226b92915db`
plus the scoped source/test diff; earlier ComfyUI progress work and unrelated local
content were preserved. Two JVM assertions and one Node regression failed before
the change. The first full run exposed two hand-written coordinator descriptors
without measured alpha; those fixture counts were corrected and missing-measurement
rejection was explicitly tested, without weakening coverage. Focused importer,
preparation, coordinator, renderer/media, result-import, clip, architecture and
documentation checks passed (2 tasks executed/4 up-to-date). All 24 Node tests passed,
including actual imported three-plane alpha pixels and fixed foreground. `make test`
passed (2 executed/12 up-to-date), `make build` passed (14 up-to-date), and diff check
passed. Logs and final candidate evidence: `build/tabi-parallax.TQIWbn/`, notably
`fixture-repair-focused.log`, `fixture-repair-node.log`, and
`make-{test,build}-after-fixture-repair.log`; earlier failures remain retained.
`candidate-pins.json` identifies the final source/docs candidate. Final disposition
rechecks are retained as `final-make-test.log`, `final-make-build.log` and
`final-diff-check.log`.

The [prepared Tokyo layers](docs/TABI_VIDEO.md#layered-tokyo-preparation-and-blocked-parallax-render-2026-09-28)
were imported through production services, but the single native controlled attempt
failed **before launch**: 830,046,208 free bytes versus 2,147,483,648 required.
At that admission there was no five-second clip or moving-pixel approval. No
override or automatic retry occurred. The later user-authorized retry and VG2-05
repair below now provide a five-second clip, not artistic acceptance. The application
convenience compiler still supports one coverage plane; the typed three-plane
scratch harness is not integrated UI. VG2-03 stays WAITING_USER for actual motion
review and the later 20–30-second ladder; blink and full-duration gates remain open.
No commits or downloads.

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

Backend technical gates and the controlled 5/20/30-second ladder are recorded
below. VG2-03 remains REVIEW for integration assessment after full-pilot and
second-city reuse review;
its older WAITING_USER paragraphs are historical, not requests to repeat those
videos. The new priority is scene-compatible character motion beyond the approved
blink, with a visibly readable revised action under VG2-08 and its own VG2-07
review.

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| VG2-01 | Finish the durable controlled-render bridge: persisted intent/claim precedes the real media stage, with pinned dependencies and frame ranges, owned cancellation and restart reconciliation. No bypass of admission or second ledger; ComfyUI must not claim compositor support. | VG1-01 | DONE |
| VG2-02 | Finish fully decoded result import and persisted take review/selection. Bind output to the successful current attempt, scene and measured media facts; strip incidental audio and reject stale/corrupt/mismatched results. New/rejected takes never overwrite or silently replace selected ones. | VG2-01 | DONE |
| VG2-03 | After full-pilot and second-city reuse review, assess flat-I2V/controlled routing and close actual caller/capability gaps, reusing the recorded native ladder rather than rendering those same videos again. Keep the short I2V limit explicit. | VG2-02, VG5-07, VG5-10 | REVIEW |
| VG2-04 | Fix ComfyUI node-local progress being persisted as whole-job progress. Keep running work indeterminate until verified completion; cover node resets, reconnect and single immutable publication without weakening store invariants. | VG2-02 | DONE |
| VG2-05 | Bound controlled renderer resource lifetime between frames without changing pixels, cadence, shutter sampling or native limits; prove event-loop cancellation and a fresh five-second native run. | VG1-02, VG2-02 | DONE |
| VG2-06 | Prepare the smallest scene-matched character inputs and produce one separately admitted 5–10-second isolated movement beyond blinking, with the rest of the scene fixed. Preserve identity, clothes, props and aligned entry/return poses; retain the approved blink. Report missing layers/control support rather than substituting a pan or unrelated pose. | VG1-03, VG2-02 | DONE |
| VG2-07 | User reviews the revised VG2-08 moving artifact at normal speed for identity, eyes/gills, props, matte/contact, readable gesture/cadence and clean return. Existing breathing feedback requests repair, not acceptance. Record exact artifact/decision; no automatic selection or full-film approval. | VG2-08 | WAITING_USER |
| VG2-08 | Finish a new matching-head, consistent-single-pass-alpha neutral/midpoint/wave kit with planted contact, declared approved return baseline and preserved source art. The held-pose control is implemented/fixture-tested, not a real-kit pass. Complete actual-kit import/support/contact/return proof, then seek a fresh one-attempt 5–10-second render/decode/import admission. Reject head pops, mixed alpha policies, ghost fades and blink/breathing/pan substitutes. | VG2-06 | BLOCKED |
| VG2-09 | After wave review, select and finish only one additional quiet non-blink activity for the Tokyo pilot, sharing the compatible cabin/neutral/props. Validate needed supplied poses/layers, actual control limits, contact/occlusion and entry/return, then produce one separately admitted 5–10-second moving test. Scope any missing control first; do not generate a broad library or infer page-turn/drinking support. | VG2-07 | TODO |
| VG2-10 | User reviews that second activity's exact moving test and its compatibility with the wave/neutral kit. Accept or request specific repairs before the two-activity combined schedule; this is not full-film approval or automatic take selection. | VG2-09 | WAITING_USER |

**VG2-08 next input slice, not execution admission:**

1. Freeze approved 40-v2/41-v2/42-v2 and wave 36/37 as read-only appearance
   sources; retain the rejected common-head extraction and all old videos.
2. Obtain externally finished layers or a newly bounded artwork admission for
   a matching-head neutral and consistently authored alpha across the whole
   new pose kit. Preserve identity, clothes, lower contact, cabin and props.
   If the new neutral/composed appearance differs from approved 29 or another
   approved still, obtain an explicit new baseline decision; do not overwrite
   or silently redefine the old comparison, tolerance or approved image.
3. Validate the actual production single-pass pose stack, every supplied state,
   fixed feet/seat/table contact, seams, intermediate support, erased old hand,
   foreground/window/frond occlusion and neutral entry/return. Rejected missing
   plate, geometry, legacy-alpha and mismatched-head witnesses must remain
   failures. Reimport/reopen only a passing new current-schema kit.
4. Prepare exact new runtime/kit/destination pins and one bounded 5–10-second
   render/encode/decode/import proposal, then obtain separate admission. The
   exhausted earlier implementation/image budgets do not renew themselves.
   VG2-07 owns the resulting normal-speed readability/cadence decision.

VG2-09/10 reuse the existing import/preparation/controlled-media owners and small
new derived inputs. Looking toward the window or adjusting headphones are
possible briefs, not existing motion capabilities. Static reading/listening
poses alone do not count as activity proof. Select one motif before preparing
its assets; any reproduced code prerequisite gets its own exact-file slice here.

**Current evidence:** [TABI evidence/next step](docs/TABI_VIDEO.md#current-evidence-and-immediate-next-step-2026-09-30)
records manifest 4/tool 1.2.0, Node 31/31 and prior focused/full/build checks,
32 unchanged protected/approved pins, head/alpha failures and the no-render
boundary. This plan update closes no technical or human gate.

VG2-06 Step 1.1 candidate (2026-09-28; inspection only): gentle seated inhale/exhale
using the existing ≤2-pixel subject `breathing` control, **not yet motion-ready**.
Keep cheek and supporting hand in the same moving cutout; cabin, seat, table,
notebook, pen, lidded paper cup, window and scenery stay fixed. The intended support
is the union of that subject's neutral and vertically shifted silhouettes (including
filter/shutter fringe), excluding stationary props; entry and final pose must match
the fixed baseline within a declared pixel tolerance, with an actual interior
non-blink displacement. The accepted leaf/window bundle is a fixed composition,
not a separated subject or clean plate. A scene-aligned transparent whole-subject
cutout, opaque clean backing across its swept support and table/seat/window
foreground occlusion need preparation and measured alignment; foot/seat contact
must be checked. Seeded periodic breathing has no guaranteed neutral endpoints:
verify the exact seed/frame range before execution or scope a timing prerequisite.
The different drinking pose/handled mug and a camera pan are rejected. Pinned
source `01e4db85…2473e`, accepted preparation `d63fa5d9…d37b99f`, approved
blink `e5d3376c…5582c2` and four review MP4 hashes are recorded in
[TABI evidence](docs/TABI_VIDEO.md#vg2-06-protected-movement-candidate-2026-09-28).
No new take, selection or live job; Step 1.2 must measure the missing inputs.

VG2-06 Step 1.1 control-only fixture proof (2026-09-29): the existing compositor
supports a bounded 2 px whole-subject inhale/exhale with seed `3001`, control ID
`vg2-breathing`, default period 3.6s, and absolute frames **0–216** (217 frames,
30 fps). Declared before testing: each endpoint ≤0.02 px from neutral,
interior frame 54 ≥1.5 px, all frames within 0..2 px. The production-imported
unit-scale fixture tests each endpoint separately against an amplitude-zero
neutral PNG, every frame's travel and fixed pixels outside conservative swept
support, interior non-blink movement, absolute-frame chunk parity, and seed `73`
as a non-neutral rejection witness (~0.945 px at both endpoints). This proves
only timing/control behavior on owned art, **not** TABI's appearance, clean plate,
contact, occlusion, imported kit or scene-artwork readiness. No timing-control
production repair is required for this range; Step 1.2 artwork remains blocked.

VG2-06 Step 1.2 preflight (2026-09-28): **blocked on artwork, not a static-import pass**. Read-only executable probe `node build/vg2-character-input-a1/check-inputs.cjs` uses the pinned corrected preparation and four MP4s; its measurements are in ignored `build/vg2-character-input-a1/input-probe.json`. At 1920×1080 both fixed and finished images are fully opaque (2,073,600 pixels). At head (600,320), cheek/hand (720,540), coat (560,690) and tail (310,840), the putative fixed-scene and cabin-foreground backing still contains *exactly the character's visible RGB*. Opaque cabin occlusion and black eye-support at these witnesses are not subject mattes. Negative checks reject reuse of these painted pixels as a clean plate, transparent backing. Alignment rejection needs a real separated subject; there is no separated subject or erased-character backing in the accepted bundle; deriving hidden seat/cabin/window art from this flat picture would require newly finished artwork. **No artwork-job admission was supplied:** no subject/plate was fabricated, no new project/reference/prepared scene was imported and no moving-quality assertion is made. Request a bounded external-artwork preparation admission or supplied registered transparent subject and genuinely clean plate; then measure swept support, registration and occlusion and perform new current-schema import/reopen under this same step. Focused four-selector Gradle suite passed (1 executed/5 up-to-date); protected pins unchanged. Existing preparation and all videos remain untouched.

VG2-06 external-artwork follow-up (2026-09-29): the user explicitly requested
Codex-generated TABI artwork in `docs/pictures/video/tabi-assets/train-actions/`.
Five new 1920×1080 **candidate/guide** PNGs (13–17) cover an empty cabin, transparent
TABI, foreground extraction and two original-RGB derivatives; see
[TABI artwork candidates](docs/TABI_VIDEO.md#external-breathing-artwork-candidates-2026-09-29).
The served images were 1672×941 and resized; the opaque inpaint changes 65.4% of
pixels outside the candidate character matte and shifts the main window frame.
The foreground guide covers the subject with seat/window art; even the restricted
table/cup layer overlaps 37,613 subject pixels. A targeted plate correction and
localized composite were also visually rejected. Original, accepted preparation
and four MP4 pins remain unchanged. **Step 1.2 is still blocked on finishing and
validation of registered artwork**; these candidates are not imported or motion-
ready, and no render/media job, accepted take or human approval is claimed.

VG2-06 user artwork feedback (2026-09-29): `16` contained a piece of tabletop,
`17` contained TABI, and notebook pictures faced away from TABI. Non-destructive
`16-...-v2`/`17-...-v2` visible-region cuts and new `19`/`20` book-facing-TABI
scene/plate candidates are in `train-actions/`; old drafts are preserved. The
Pi-generated close-up changes only page interiors (30,162 pixels, zero outside
that region); tower tip now faces the near edge, base faces seated TABI. Manual
stationary geometry keeps source RGB, excludes measured old bleed witnesses and
keeps protected source/preparation/four MP4 pins unchanged. Scratch checks:
`build/vg2-character-art-a1/check-corrections.cjs`; static review stack there.
The generated clean plate still shifts the window and subject fringe/swept
support are unvalidated; **Step 1.2 remains BLOCKED for motion-ready inputs**.
Escalation recheck: the retained recipe reproduced the missing `16-...-v2` and
`17-...-v2` derivatives without changing protected inputs. The scratch check now
uses current derivative pixels rather than assuming superseded guide pixels are
immutable: `node build/vg2-character-art-a1/check-corrections.cjs` passes the
cutout/foreground witness and deliberately misaligned/missing-backing rejection
checks. At 1920×1080 with ±2 px vertical subject travel and a 3 px fringe,
338,677 pixels are in conservative swept support, but the candidate opaque
plate differs from the book-corrected scene at **1,703,062 pixels outside it**
(first mismatch 0,0). Evidence: ignored
`build/vg2-character-art-a1/escalation-check.json`; v2 cutout/foreground SHA-256
`c1e0e223…edc7367` / `f43bdb50…ac9cfe4`. These are diagnostic candidates,
not a registered clean plate or motion-ready layers. Scene-matched plate,
subject-edge/occlusion finishing and neutral-composite proof still require a
separately bounded artwork admission or supplied finished layers before preparing
and importing a new private project. No import, media job, take or artistic
acceptance is inferred.

VG2-06 bounded artwork-finishing continuation (2026-09-29): at the user's
request, the Pi image-generative tool inpainted a cropped cabin/seat behind TABI
and made one Tokyo extension **style study** using `panorama-style.png` and Tokyo
architecture references. New `train-actions/23` is the untouched model crop;
localized opaque backing `26` and neutral-aligned original-look subject `27`
are 1920×1080 candidates. The backing changes zero pixels outside the
conservative ±2 px/3 px fringe support (previous plate: 1,703,062 outside).
Scratch `build/vg2-character-art-a1/registered-v2-sweep-check.json` and ±2/0/+2
static stills report zero changed outside support; neutral still has 2,111 pixels
with RGB delta >8 and direct inspection finds ghost contours at seat/poster/window.
No swept-filtered motion, endpoint return or production import/reopen is proven;
**Step 1.2 remains BLOCKED**, not a valid moving-input admission. The three
accepted, hash-pinned 5600×1080 Tokyo far/middle/near parallax planes were copied
byte-identically into `scenario/` so future art can retain the selected rigid
parallax. The new opaque `tokyo-parallax-addition-study.png` is not separated or
joined and does not extend the 60/240-second corridor. No new media render/take,
accepted mask change, video approval or automatic move to VG4. Escalation's
static registration check now explicitly rejects the 2,111 neutral pixels above
threshold instead of treating zero outside-support changes as motion readiness;
Step 1.2 still needs a finished ghost-free backing/edge, swept checks and production
import/reopen. No additional artwork job is admitted by this check. Evidence/limits:
[TABI art study](docs/TABI_VIDEO.md#bounded-registered-plate-and-tokyo-parallax-artwork-study-2026-09-29).

VG2-06 Step 1.2 artwork-finishing attempt (2026-09-29): the user authorized
**one image-edit attempt and at most one targeted correction, no video render**.
Both were used to clean the empty-cabin crop and its silhouette-shaped seam.
Original generated crops and scratch composites remain in ignored
`build/vg2-character-art-a2/`; no previous candidate or accepted asset was
replaced. The corrected candidate is still visibly patch-shaped at the poster,
seat and window. Its neutral composite has **2,287** pixels with RGB delta >8
(earlier: 2,111); backing changes zero pixels outside the conservative support,
but that is insufficient. Scratch receipt: `corrected-measure.json`; first attempt:
`candidate-measure.json`. The source, accepted mask and four review MP4 pins were
rechecked unchanged. Focused four-selector Gradle, fixture generation, 27 Node
motion tests, `make test`, `make build` and both diff checks passed; logs are
under `build/vg2-character-art-a2/`. **BLOCKED**: the authorized artwork budget
is exhausted, no image was promoted or imported, and no video/media job ran. A separately
supplied artist-finished, scene-registered clean plate and matte, or a *new*
explicitly bounded finishing admission, is needed before swept/production proof.
Do not interpret another threshold-only check or a generated empty-seat picture
as completion. See [finishing attempt](docs/TABI_VIDEO.md#registered-cabin-artwork-finishing-attempt-2026-09-29).

VG2-06 fresh user authorization (2026-09-29): the user explicitly allowed another
image-generation attempt and, if needed, a full cabin redraw. One full-scene Pi
image edit was used; the reserved targeted correction was **not** used. This is a
**replacement-scene candidate**, not a successful fix of the original accepted
scene: model output 1672×941 was scaled to 1920×1080 and redraws 1,741,839
backing pixels outside old subject support. Scratch `build/vg2-character-art-a3/`
retains original output, opaque resized cabin, trimmed source-RGB TABI cutout,
new fixed table/cup occlusion, geometry-only new window mask, three static ±2/0
stills and measured checks. These stills change zero pixels outside their *new*
neutral subject support, but self-comparison to a newly composed neutral cannot
certify original-pixel restoration or moving quality. The refreshed parallax
still reuses the three pinned depth planes without altering their bytes. Review
images `train-actions/28`–`30` are unapproved candidates; see
[new cabin study](docs/TABI_VIDEO.md#new-full-cabin-replacement-study-2026-09-29).
The clean empty-seat backing looks substantially better than 26/27 at the old
ghost contour, but the cabin/city/prop drawing and window geometry have changed;
new-window matte, foot/seat contact, shutter-filtered sweep, neutral endpoints,
production import/reopen and user appearance approval remain unproven. Focused
four-selector Gradle, fixture generation, all 27 Node motion tests, `make test`,
`make build` and both diff checks passed; see ignored `build/vg2-character-art-a3/`
logs. Protected source, mask, depth-plane and four MP4 pins remain unchanged.
**Step 1.2 is still BLOCKED**; do not replace the accepted leaf bundle, parallax
result or old still reference with this new scene automatically. No video, take
or media job.

VG2-06 appearance-route gate recheck (2026-09-29): no later user decision in this request names and approves replacement appearance `train-actions/28`–`30` and its artifact hashes; permission for a redraw was not approval of its output. No supplied finished original-appearance registered cutout, ghost-free opaque plate and stationary occlusion were identified. Read-only `python3 build/vg2-character-gate-20260929/check-gate.py` verified all 20 protected pins and recorded ten **unselected** candidate hashes in `preflight.json`. Prior original-route 26/27 fails neutral edges (2,111 pixels over delta 8); the correction in `build/vg2-character-art-a2/corrected-measure.json` still fails (2,287); replacement a3 redraws 1,741,839 backing pixels outside the old support and its zero outside-*new*-support static shifts compare only against itself. No selected kit exists on which to run a full translated/filtered/shutter sweep, baseline-neutrality, contact or misregistration proof. **Step 2.1 is blocked on an explicit appearance choice and finished layers; Step 2.2 must not import these candidates.** No additional artwork or video work is authorized by this recheck.

VG2-06 subsequent appearance decision: after reviewing the proposed replacement route,
the user explicitly said “yes, i approve these” for the three identified review
stills `train-actions/28` (SHA-256 `922ff5afd87417c9ba653b327d4b00007670981c99d1d927c20f4ea596769e14`),
`29` (`2d3a8ca5e7b8b55828c44f080fd31efd0e825330560901f5eb1bdd1356ba7e7a`)
and `30` (`2322f28268921172f74dea3517a63b670e16205e3b98e8164287ddc2cbd2921b`).
Current SHA-256 checks match all three. This selects the **replacement appearance**
and its new neutral baseline, not original-scene fidelity or motion-ready layers.
Step 2.1 repair (same selected route): the initial in-memory sweep used direct
Canvas subject compositing. A separate production-compositor check reproduced a
neutral-edge defect in that *measurement*: `render.cjs` clips the subject to its
own alpha before drawing. The original a3 cutout differs from approved neutral
29 at 3,432 pixels by >8/255 (max 48); failed witness retained at
`build/vg2-character-approval-20260929/production-compositor-check.json`.
Without changing production or approved review stills, a new scratch subject
alpha derivative compensates that self-clip, retaining source RGB:
`build/vg2-character-approval-20260929/full-subject-alpha-compensated.png`
(SHA-256 `c8097fc7b39631c0c19bb359c1ed36079e8a965a0935dc3879fe9f87c6ad0865`).
A binary coverage mask derived from unchanged a3 foreground alpha is
`full-foreground-coverage.png` (SHA-256
`0dbc32a26c4fb0ff4f8f388e041d95e9f038509850f2772ade1545a4f07f81fa`).
`node build/vg2-character-approval-20260929/production-compositor-check.cjs --repaired`
then passes the unchanged approved 29 neutral at both endpoints: zero pixels
>8/255, maximum two levels; its receipt is `production-compositor-repaired.json`.
The **selected isolated fixed-scene kit** consumes 28 as opaque plate, 29 as
approved neutral, the new compensated subject, unchanged a3 foreground and its
new coverage mask. Still 30 and the provisional window/scenery matte are only
appearance references: no scenery or scenery shutter is consumed in this
isolated action. `node build/vg2-character-approval-20260929/measure.cjs`
checks all 217 fractional translations with the compositor's self-clip and
occluder-mask order: 328,526 conservative swept/filter-support pixels, all
frames opaque, zero changed pixels outside support, separately neutral first/
last frames (max two channel levels), and interior frame 54 changes 309,645
pixels with peak 1.99999998 px. Negative fixtures reject a missing/transparent
or painted-subject plate, 3-pixel misregistration and original-scene fidelity
inferred from support-only self-comparison (1,741,839 outside-support changes). A second negative actually stacks
subject over the painted neutral plate: 3,384 pixels exceed the declared
8/255 tolerance; a 3-px offset exceeds it at 133,695 pixels.
Receipt: `build/vg2-character-approval-20260929/measurement.json`. Five 1:1
`contact-repaired-{eyesGills,cheekHand,feetSeat,tableContact,windowEdge}.png`
strips show 0/1/2-pixel positions. On direct inspection the hand/cheek and
eyes/gills stay together, feet meet the seat without a visible ghost gap, and
stationary table, notebook, cup, window and poster remain intact. This is a
**technical input-kit pass for Step 2.1** at the fixed-scene range, not user
approval of moving footage. The 2-pixel relative foot/seat travel and largely
translucent subject source remain quality limits for moving review; neither
parallax nor provisional window matte is cleared for moving use. Recheck:
`node build/vg2-character-approval-20260929/production-compositor-check.cjs --repaired`
and `node build/vg2-character-approval-20260929/measure.cjs` passed;
`./gradlew :test --tests '*VideoAnimationAssetsTest' --tests '*VideoAssetImportTest'`
passed (1 executed/5 up-to-date); `make test` passed (1 executed/13
up-to-date); `make build` passed (14 up-to-date). Logs are in the same ignored
scratch root. Protected pins passed 20/20 before and after; diff check passed.
Step 2.2 may
import/reopen only this five-input kit in a new private project; no live video
render is admitted. The earlier missing-decision preflight is historical.

VG2-06 Step 2.2 private-project integrity check (2026-09-29):
`./gradlew -I build/vg2-character-import-20260929/compile-host.init.gradle verifyVg2KitImport`
imports the five selected, SHA-pinned images as distinct immutable references in
`build/vg2-character-import-20260929/final/project/` (current video-project schema 5,
revision 6). `final/import-receipt.json` records reopened original-byte equality,
1920×1080 image/alpha and unit transform facts, production look selection and
prepared descriptor SHA `22124d90f9c89a6f53cb2f161d81952cf7833a70250d43adfa1bbe4b841f0fe9`.
Stale revision, wrong coordinate space, malformed rotation, absent clean backing
and a changed original in a separate copied fixture reject with actionable results,
without appending another scene or take. Earlier `project/` holds a failed
unsupported-motion attempt and `verified/` an earlier passing diagnostic; neither
is the final project. No existing project, take, selection or MP4 was changed.

**Execution prerequisite discovered, not silently passed:** the selected subject
PNG is a 1920×1080 full-viewport transparent image. The production preparation
validator derives zero safe vertical travel from its declared full-viewport bounds.
Requesting `TRANSLATE_Y` −2..2 returns `UNSUPPORTED_MOTION`; the imported scene
therefore has only `IMAGE_TO_VIDEO`, not a usable breathing capability. The stored
scene is **integrity-only** with no requested motion. Before Step 3.1 can bind a
breathing request, prepare a *new*, losslessly cropped/repositioned subject
reference with ≥2 px viewport clearance, remeasure its neutral composite and
complete translated/filter sweep against the approved 29 baseline, then import
it under a new scene/version in a fresh private project. Do not alter the five
selected source bytes, pretend this rejected control was admitted, or change
production capability policy merely to bypass bounds. No live execution admission
is supplied. Focused five-selector Gradle passed (1 executed/5 up-to-date); the
private-project command passed (2 executed/2 up-to-date); `make test` passed
(1 executed/13 up-to-date), `make build` passed (14 up-to-date), and
`git diff --check` passed. All 20 protected pins matched again. Logs in
`build/vg2-character-import-20260929/`; this is integrity evidence only, not
motion-quality approval. See [private import](docs/TABI_VIDEO.md#vg2-06-private-project-integrity-check-2026-09-29).

VG2-06 Step 3.1 fixture-only gate (2026-09-29): read-only production-store
reopen of `build/vg2-character-import-20260929/final/project/` (revision 6,
scene descriptor `22124d90…0fe9`) confirms that the approved neutral reference
reopens but the scene exposes only `IMAGE_TO_VIDEO`. Production
`VideoScenePreparation.prepare` rejects control `vg2-breathing` / seed 3001 /
absolute frames 0–216 / one attempt: that capability cannot satisfy bounded
breathing. Receipt and reproducible check:
`build/vg2-character-request-20260929/` (`./gradlew -I
build/vg2-character-request-20260929/compile-host.init.gradle
verifyVg2RequestGate`). The denied-admission receipt there grants **zero** live
jobs/renders/encodes/decodes; no request was submitted or take imported. The
missing prerequisite is a losslessly cropped/repositioned subject with ≥2 px
clearance, fresh full production-compositor sweep against approved still 29,
new current-schema import/reopen and complete runtime pins. Separately obtain
bounded human admission for all live stages including decode; the earlier
appearance decision does not grant it. This is not Step 3.1 completion or VG2-06
technical footage. Focused five-selector Gradle passed (1 executed/5 up-to-date),
fixture-only check passed (2 executed/2 up-to-date), `make test` passed
(1 executed/13 up-to-date), `make build` passed (14 up-to-date), diff check
passed, and 20/20 protected hashes matched. Earlier fixture compile failed on
a nonexistent `UNSUPPORTED_MOTION` enum member; corrected to the production
`UNSUPPORTED_ACTION` and reran successfully. No other project/media was changed.

VG2-06 Step 3.1 escalation continuation: the earlier full-viewport limitation is
now repaired **in a new ignored private fixture**, without changing the selected
source or integrity-only project. `node build/vg2-character-request-20260929/crop-and-check.cjs`
creates `subject-cropped.png` (SHA-256 `7b44dcb9…a9ab92e`), bounding nonzero
alpha at (190,202)–(893,987) with five-pixel fringe: 714×796 at (185,197).
Re-expanded decoded RGBA matches the approved subject exactly (zero changed
pixels). All 217 sampled fixed-scene composites remain opaque and fixed outside
328,526 swept pixels; first/last differ from approved 29 by at most two channel
levels, interior frame 54 moves 1.99999998 px. Production importer reopened a
fresh schema-5 project at `build/vg2-character-request-20260929/cropped/project/`
(revision 6, descriptor `7d211dd3…3745a2`) with exact imported originals and
`motion-3:TRANSLATE_Y` range −197..87; stale, malformed, missing backing,
out-of-range and changed-asset fixtures reject without appending a take. The
production scene preparation compiles `vg2-breathing` at ±2 px, seed 3001,
frames 0–216, one proposed attempt; fingerprint `424e0e49…2d68d862c5e`.
These checks do **not** call `VideoClipGeneration.generate` or submit intent:
full runtime/tool pins and duplicate/occupied/failed-attempt negative fixtures
remain to be bound and checked. `admission.json` still says NOT_AUTHORIZED,
permits zero live operations; no native/media work, output or take exists.
Separate bounded live admission is an external prerequisite before Phase 3
execution. Recheck: focused five-selector suite passed (1 executed/5 up-to-date),
`node build/vg2-character-request-20260929/crop-and-check.cjs` passed,
`verifyVg2CroppedKit` passed once in the new project (2 executed/2 up-to-date),
`verifyVg2CroppedRequest` and the earlier denial fixture passed (each 2
executed/2 up-to-date); `make test` passed (1 executed/13 up-to-date),
`make build` passed (14 up-to-date), documentation test passed (1 executed/5
up-to-date), diff check passed, 20/20 protected hashes matched. Do not treat
crop/import/prepare as Step 3.1 completion.

Step 3.1 fixture-only durable boundary continuation: `build/vg2-character-request-20260929/BoundRequestHost.kt` reopens the cropped schema-5 project, resolves all 11 prepared artifacts plus 12 canonical runtime roles (including five Canvas loaded files and FFmpeg/ffprobe/manifest), and calls production `VideoClipGeneration.generate` with seed 3001, frames 0–216 and `maximumAttempts=1`. The isolated production `VideoJobStore` in `bound-fixture-v3/jobs/` records a single FAILED no-start attempt for request `vg2-breathing-cropped-fixture`; only a deny-only fake transport was called. Separate fixture storage tests changed pins, stale revision, duplicate identity/fingerprint, conflicting ID, occupied uncertain admission and failed-attempt no-retry/no-import. No native process or take exists. Full pin list and executable fingerprint are in `bound-check-v3.log`; this is **not** the live job ledger or authorization. `admission.json` remains NOT_AUTHORIZED with zero live operations: user must separately approve exact selected hashes/tools/destinations, job counts, stage and cumulative wall-time/storage budgets (including decode), pressure/swap and cancellation limits, and launch/verification commands before a real attempt. Step 3.1's *admission* gate remains blocked; Phase 3.2 cannot start. Verification: `verifyVg2BoundRequest -Pvg2FixtureRoot=build/vg2-character-request-20260929/bound-fixture-v3` passed (2 executed/2 up-to-date), focused five-selector Gradle passed (1 executed/5 up-to-date), `make test` passed (1 executed/13 up-to-date), `make build` passed (14 up-to-date), `git diff --check` passed, and 20/20 protected hashes matched `build/vg2-character-gate-20260929/preflight.json`; logs are `step31-*` in the request root. Two earlier fixture runs failed on harness-only path construction and an incorrect retry error-code assertion; their ledgers/logs are retained and no native process ran.

Step 3.1 admission-proposal continuation: ignored
`build/vg2-character-request-20260929/live-admission-proposal.json` identifies
the cropped scene, 23 verified prepared/runtime pins, separate fresh live
destinations, one 217-frame production `VideoClipGeneration.generate` attempt,
finite proposed stage/cumulative budgets and exact gated preflight/launch commands.
`LiveCharacterHost.kt` and `live-run.py` compile and pass read-only preflight;
`live-classpath-pins-v2.json` pins the JVM and 1,927 loaded class/JAR/resource
files. Three non-native regressions pass: the missing approved receipt denies
wrapper execution, direct-JVM bypass is refused, and pin/budget arithmetic
agrees. No live directory, job, renderer, encoder, decoder or take was created.
This is **not** final admission: `admission.json` remains NOT_AUTHORIZED, the
approved-receipt file is absent, and independent lossless-frame/decoded-media
verification and take-import commands are not yet implemented or pinned.
Preflight sampled NORMAL pressure and sufficient disk, but 1,724,186,624
free-memory bytes vs the proposed 3-GiB threshold; recheck before any separately
approved run, not a reason to lower limits. Do not execute the launch command
from a proposal.

VG2-06 Step 3.1 verification-command continuation (2026-09-29): ignored
`build/vg2-character-request-20260929/verify-live.py` now names separate
`dry-run`, `source`, `decode` and `import` commands in the proposal. The source
command checks all 217 numbered lossless PNGs against their production render
receipt, the approved neutral, the declared translated support and independent
entry/return/interior witnesses. The decoded command independently probes the
single silent H.264 stream, uniform frame PTS and geometry, extracts all 217
frames with pinned FFmpeg, and compares decoded pixels against each source PNG
with separately disclosed lossy-error bounds. The import command rechecks the
pixels/MP4, uses a separately compiled production `VideoResultImport` host for
one current successful durable attempt, and copies the validated MP4 only to
an absent review path; it cannot select or approve a take. The helper/source/
class hashes and exact commands are in `live-admission-proposal.json`;
`compile-host.init.gradle` owns the isolated import-host compilation. This is
**a pinned proposal, not live evidence or admission**: real source/decoded/take
verification has not run. Non-native dry-run and denial tests pass; the existing
`admission.json` still says NOT_AUTHORIZED, with no approved receipt, live ledger,
render, encode, decode, import or review copy. A separate exact-artifact human
admission and passing resource preflight are prerequisites; Step 3.1's admission
and VG2-06 footage remain open. Focused five-selector Gradle passed (1 executed/
5 up-to-date); import-host compilation passed (1 executed/2 up-to-date), seven
non-native guard tests passed, production read-only preflight and verifier dry-run
passed. `make test` passed (1 executed/13 up-to-date), `make build` passed
(14 up-to-date), and `git diff --check` passed. Logs:
`build/vg2-character-request-20260929/verify-{compile,focused,guards-final,dry-final,preflight,make-test,make-build,diff-check}.log`.
No real source frames/media were available for these verification commands;
software checks do not certify their future native outcome or motion quality.
See TABI video for command boundaries.

VG2-06 live-admission outcome (2026-09-29): the user approved the exact
`live-admission-proposal.json` after verification commands were pinned. A separate
ignored `live-admission-approved.json` binds proposal SHA `10b87a6f…45ccc9d`
and launch/verification helper hashes; historical `admission.json` is unchanged.
One invocation of `live-run.py run` passed production read-only project/pin
preflight, then **refused before JVM/native launch**: three consecutive NORMAL
samples with ≥3 GiB free memory could not be established within 120 seconds.
`build/vg2-character-request-20260929/live-attempt.log` retains the refusal.
A subsequent read-only sample showed 267,239,424 free bytes, NORMAL pressure,
zero swap and 316,008,382,464 free disk bytes. The live ledger/output/review
paths remain absent; no job/take or source/decode/import verification ran. Do not
lower the threshold, automatically repeat the launch or claim moving footage.
A new bounded launch decision is needed. VG2-06 remains TODO; VG2-07 still
awaits actual footage.

VG2-06 technical result (2026-09-29): after a fresh user-authorized retry
following the memory-only preflight refusal, exactly one controlled attempt
`attempt-0ce0c925-e02d-4001-9474-790cc7b8a02d` generated 217 source PNGs
and one sealed 7.233-second silent 1920×1080 H.264/30fps/square-pixel MP4
(SHA-256 `b73b6490a9fc4db98cd5b0581e46ac74e9a556f2941567253c778c0030af7bcb`).
The producer exited in ~104.7s, no retry or second render/encode occurred.
Initial independent verification refused before decode because the verifier
expected a range-named receipt while production wrote `render-receipt.json`.
A user-approved, newly pinned 180-second verification-only continuation
accepted exactly one of those production names. All 217 lossless source PNGs
matched the receipt; outside-support RGB error was zero, neutral endpoints
≤2/255, and interior frame 54 changed 309,645 pixels. Independent full decode
verified one silent video stream, H.264/yuv420p, 217 uniform 512-tick PTS at
1/15360, 30fps, square 1080p, with 203 distinct decoded RGB frames; maximum
per-frame mean RGB error versus source was 3.135/255 (lossy pixels are not
claimed exact). The first import host invocation failed before project mutation
on an incorrect argument count. After the user's request for the rendered
output, a separately pinned 120-second import-only attempt rechecked both pixel
proofs and completed production media validation/import (conversion NONE).
Private project revision 7 has one immutable UNREVIEWED, unselected take and
zero selections; no previous take/art was replaced. The requested absent-path
review copy is `docs/pictures/video/tests/vg2-breathing-cropped-217-20260929.mp4`
(11,201,396 bytes, same SHA as sealed output). Detailed logs/receipts are in
`build/vg2-character-request-20260929/live-217/` and the request root; failed
preflight and verification/import attempts remain retained. Static replacement
appearance approval and technical pixel checks do not establish normal-speed
motion/contact/edge approval: VG2-07 remains WAITING_USER, and no parallax,
scenery join, new blink footage or longer clip is inferred. Final current-tree
focused six-selector Gradle passed (1 executed/5 up-to-date), fixture generation
passed (1 executed/5 up-to-date), Node motion 28/28, three scratch receipt/import
argument regressions, `make test` (1 executed/13 up-to-date), `make build`
(14 up-to-date) and `git diff --check` passed. All 20 protected input hashes
remained unchanged. Logs: `build/vg2-character-request-20260929/final-*.log`.
No new motion-quality decision or additional render is inferred from these gates.

VG2-07 visibility feedback (2026-09-30): on the exact review MP4
`b73b6490…af7bcb`, the user reports that the motion is “really little,
almost invisible” and asks for more visible movement. This is a specific
quality repair request, **not** acceptance of the character take. Read-only
inspection confirms production breathing is capped at 4 px; this clip peaks
at 2 px. Static-only 0/2/4-px translations of the same pinned kit are retained
at `build/vg2-more-visible-20260930/` and preserve outside-support pixels;
they do not prove moving foot/seat contact, endpoints or user-visible quality.
Doubling whole-body travel moves the planted feet and remains only 4/1080
pixels, so do not claim it addresses the feedback. A clearly legible
upper-body/head action needs scene-registered action art/layers and a new
controlled request, with separate input validation and bounded render admission.
Old clip/take remain unselected and intact. VG2-07 stays open pending a revised
moving result and user review; no new output is authorized by this inspection.

VG2-07 gesture-preparation inspection (2026-09-30): after the user agreed to
prepare a visibly larger seated upper-body/head action, inspected `02`, `03`,
`06` and `10` in `train-actions/`: each is an opaque 1254×1254 white-backed
pose reference, not a transparent 1920×1080 scene-registered motion layer;
`06`/`10` change hand pose and remove the scene's cheek contact. The imported
cropped kit has only subject `TRANSLATE_X/Y`, no `ROTATE` capability or head
mask/pose. Current compositor head rotation is capped at 3° and clips the
rotated pixels to the *original* subject silhouette; it cannot invent frond,
neck or coat pixels outside that edge. Do not paste, rescale or claim these
references as production-ready. The next prep needs a new scene-aligned
upper-body/hand/face pose and neutral return with fixed seated lower body,
backing and foreground/occlusion, plus actual full-sweep/negative/contact
checks. The current brief permits inspection but contains no new bounded
image-generation/artwork request or live motion-job admission; those remain
separate. No new art, scene, job or take was published by this inspection.

VG2-07 new artwork candidates (2026-09-30): user explicitly authorized
image generation for this video and placement under `train-actions/`. Two Pi
image-edit calls used approved replacement neutral 29/empty cabin 28, then
the first raised-hand candidate plus neutral 29. The unchanged model originals
are retained in `build/vg2-visible-art-20260930/generated/` and copied to
`train-actions/31-wave-upper-body-model-candidate.png` (1672×941 opaque,
SHA `2331a8a1…5b5a04`) and `32-wave-isolated-model-candidate.png` (1672×941
RGBA, SHA `6b33a965…40ae13`). Model-reported sizes/quality are not native 1080p
registration. Rescaled 31 redraws 341,548 RGB pixels >8 outside the declared
upper-action rectangle. Directly stacking 32 over the approved plate moves the
lower contact silhouette at 138,587 pixels and shows translucent edges; both
are rejected as motion-ready layers. Scratch `assess.cjs`/`check-layer.cjs`
retain exact measurements and review stills. A source-pinned, review-only
localized composite preserves approved still 29 **exactly outside**
[180,180,980,760]: `train-actions/33-wave-seat-anchored-review-candidate.png`
(1920×1080, SHA `1191795d…17ffb8a`), with a visibly raised hand and fixed
seat/feet/props. This is a still for user appearance review, not an imported
pose, clean neutral transition, moving matte or controlled action. A checked
lower/upper join remains nonzero (13,756 changed pixels in the scratch band),
and the current renderer has no general alternate-arm-pose motion control.
No new video/model motion job, encode, take, project selection or live budget;
old clip/art and 20 protected pins remain intact. User appearance decision and
registered transition/control proof are still needed before a separately
admitted new render.

VG2-07 mouth-color feedback/correction (2026-09-30): user says the new wave
scene is okay but asks to remove TABI's red lipstick. Preserve 33 as review
history; a new local color-only derivative
`train-actions/34-wave-no-lipstick-review-candidate.png` (1920×1080, SHA
`027a31a9…b3c2`) reduces red/pink mouth fill at 1,478 pixels within
[748,473,825,509], preserves all dark smile ink, alpha and every pixel outside
the mouth rectangle exactly. No model rerun or scene redraw. An aligned-to-
viewport *guide*, not a registered animation input,
`train-actions/35-wave-isolated-no-lipstick-guide.png` (SHA `f8d14938…dddd1`)
repeats the local correction on the separately retained model cutout; source
31–33 remain unchanged. Scratch recipe and failing-old-color regression:
`build/vg2-visible-art-20260930/remove-{lipstick,layer-lipstick}.cjs` and
`test-lipstick.cjs`. User's scene response accepts the static composition
apart from the mouth color; it does not approve corrected 34, clean dynamic
contact, pose transitions or a new moving clip. No media job/import/selection.

VG2-08 appearance/input gate (2026-09-30): user explicitly approves the
**static** corrected 34 picture (“yes, the picture is correct now”) and asks
to proceed with video. `node build/vg2-visible-art-20260930/wave-preflight.cjs`
reopens the current prepared descriptor and compares pinned 34/35/neutral/crop:
34 changes zero pixels outside its approved action rectangle, but the
isolated guide 35 differs from neutral subject alpha at 138,587 pixels in the
lower-contact region; the current scene has zero poses and only I2V and
TRANSLATE_X/Y capabilities. `render.cjs` has no supported waving-arm control;
BLINK pose blend is eye-specific and clips to the old silhouette, bounded
breathing and head gesture cannot perform this action. Thus 34 is an appearance
reference, not an executable subject/pose, and neither the prior one-attempt
approval nor a static look decision admits a new job. No new private scene,
ledger, render, encode, decode, take or review MP4 was created. Finish the
registered artwork and test a narrow neutral→wave→neutral control behind
fixtures before asking for exact fresh tool/kit/destination/stage/cumulative
budgets and live permission. Existing take/review file remain untouched.

VG2-08 intermediate-art check (2026-09-30): with the user's earlier permission
to generate the necessary images and their new instruction to continue, one
in-between image-edit attempt plus one tighter position repair used neutral 29
and approved corrected wave 34 as references. Both opaque 1672×941 outputs
are held only under `build/vg2-visible-art-20260930/01a0edcb-399c-76dd-8eb0-242f7fae80cf/`
(SHA `1e04b2c7…1086a6` and `91c11bce…9f205f`); in both, the hand sits
near the final raised position, not a clearly distinct halfway arm pose.
They also redraw the full background and do not have registered alpha/lower
contact or an exact mouth/face match. Do not promote either to `train-actions/`,
import as a prepared pose, perform a cross-dissolve/morph that ghosts the
character, or launch media. The two bounded image attempts have not resolved
the blocking artwork dependency: obtain a scene-registered, separately
finished intermediate pose and raised-hand cutout (same neutral lower body,
body attachment, clean plate, masks and occlusion). Renderer work can then be
tested against those inputs, but cannot be declared production-ready from
these two opaque attempts. No new moving-quality or final-scene approval.

VG2-08 wave-candidate continuation (2026-09-30): the user responded “i like the
candidates, you can continue with it” to the new transparent wave review
layer `train-actions/36-wave-fixed-lower-alpha-review-candidate.png` (SHA
`e0d14660…e1b7`) and its scene preview `37-wave-fixed-lower-review-candidate.png`
(SHA `d503b6c6…44a`). This is permission to continue from their **static
look**, not acceptance of an unseen midpoint, motion, import or live render.
The Pi tool made two further *transparent* in-between studies; one has a
different-sized face/torso and a doubled join when placed in the cabin, the
other still raises the hand close to the final wave. A local arm warp visibly
distorts the face. Keep these rejected studies only in `.pi/generated-images/`
and ignored `build/vg2-visible-art-20260930/`, not as motion inputs. A pinned
static audit `node build/vg2-visible-art-20260930/check-fixed-wave.cjs` confirms
36 is 1920×1080 RGBA and its pixels at y≥760 equal the neutral cropped
subject exactly. In production-style subject self-alpha clip / fixed-occluder
order, zero pixels outside [180,180,980,760] differ from neutral by >8 and
zero lower pixels differ by >2; the simpler review stack 37 has 384 lower-edge
pixels >8. The production-style static also differs from approved wave 34 at
20,755 pixels >8, so 36/37 are review candidates, not exact pose registration.
There is still **no validated midpoint or neutral→wave→neutral control**. Keep
VG2-08 BLOCKED: no private pose import, job, encode, take, old-video replacement
or ghosted crossfade. Finish one clearly intermediate transparent pose sharing
the planted lower body, inspect seams/contact and the production-style stack,
then test a narrow control with fixtures before seeking a separate live admission.

VG2-08 correction after user inspection: the user reports that TABI's hand in
36/37 appears to have the **same position as 34**. Confirmed: the upper-hand
rectangle [835,430,960,570] in transparent 36 is byte-for-byte identical at
all decoded RGBA pixels to the wave guide 35 (zero changed pixels). Layer 36
only fixes the seated lower body; 37 is its cabin composite. Neither is a new
midpoint, a different wave position or progress on arm articulation. The
positive static-look response above does not override this observation or
unblock VG2-08. Do not promote 36/37 as a distinct action pose, start a
transition/render, or relabel a ghosted dissolve as a gesture. The next art
must show a demonstrably different registered halfway hand/arm while retaining
the exact planted lower body and unaffected cabin.

VG2-08 fresh halfway-art admission/result: the user explicitly requests the
missing wave assets after the above correction. Two Pi image edits (one initial
arm plus one sleeve-attachment correction) produce a genuinely different
**static midway candidate**, not a new video. New `train-actions/38` is the
untouched 1153×1364 RGBA source; `39` is the positioned arm-only guide;
`40-wave-halfway-seated-alpha-candidate-v2.png` is the 1920×1080 seated layer
(SHA `7039302f…252d11e`), `41-…-v2` its cabin review (`1e24238c…4f2e72c`),
and `42-…-v2` the neutral/midway/raised comparison (`022343e6…63d5619`).
Scratch `build/vg2-wave-assets-a1/publish-and-check.cjs` and `asset-proof.json`
verify 13,558 hand-region pixels >8 different from 36; sampled hand centroid
26.6 px left / 28.1 px lower. Lower RGBA y≥760, tested eyes/mouth/head and
all layer/scene pixels outside [748,435,980,750] remain exact. Old 35/36
identical-hand candidates fail the midpoint regression. The v2 stack removes
old fingertip residue; unversioned 40/41/42 and the failed common-head neutral
extraction remain unselected drafts, not animation inputs.
**BLOCKED remains correct:** user review, neutral-to-midpoint registration and
moving contact/return, production pose import and the narrow non-ghosting control
are still required; neutral 29 and wave 37 have different head/body poses.
This request admits external image preparation only, no live video job, encode,
decode, take mutation, automatic selection, old-art/video replacement or commit.
Full pins, prompt brief and limits: [TABI artwork continuation](docs/TABI_VIDEO.md).
Verification on HEAD `a21706d02` plus preserved documentation/art WIP: static
asset regression passed; 29/29 protected pins unchanged. Focused six-selector
JVM suite (including fixture generation) passed, 1 executed/5 up-to-date; Node
motion/scenery 28/28; `make test` passed, 1 executed/13 up-to-date (root tests
executed, desktop cached); `make build` passed, 14 up-to-date; diff check passed.
Logs and checks are in `build/vg2-wave-assets-a1/`. These technical passes do
not approve the new art or animate the candidate. No production Kotlin/renderer
changes or implementation commit.

VG2-08 subsequent static midpoint approval (project user,
2026-09-30T02:04:42Z recorded): “yes, it looks right”, responding to the
40-v2 transparent pose, 41-v2 cabin review and 42-v2 comparison delivered
above. All three full hashes were rechecked against TABI's recorded pins.
This accepts the **halfway pose's static appearance** for further work; it
supersedes the missing midpoint-look decision only. It does not approve the
unversioned drafts, 38/39 as standalone finished layers, moving transitions,
neutral return, a production take or any render budget. VG2-08 stays BLOCKED
on neutral-to-midpoint registration/contact and executable non-ghosting
transition/import proof; VG2-07 remains WAITING_USER for revised actual
footage. No image generation, runtime implementation, native job or selection
follows from this recording. Next scope: validate the pose kit and narrow
neutral→halfway→wave→neutral control before a separately admitted render.

VG2-08 non-live control slice admission: following that stated next step, the
user says “ok continue with it”. Scope: registered-pose validation and a bounded
absolute-frame supplied-pose replacement sequence, with explicit neutral entry
and return. No cross-dissolve, invented interpolation or claim of native 30-fps
articulation; held supplied poses have their actual cadence disclosed. Existing
blink behavior is retained. One initial implementation plus at most two bounded
repairs; no commit, image generation/model job, media render/encode/decode,
selected take or old project/artwork change. Owners: domain `VideoPreparedScene.kt`,
new `VideoPoseSequence.kt`, `VideoGenerationJob.kt`; application
`VideoAnimationAssets.kt`, `VideoScenePreparation.kt`; `tools/video-motion/render.cjs`,
`src/main/resources/video/motion-runtime.json`; focused tests
`VideoScenePreparationTest.kt`, `VideoMotionDescriptorFixtureTest.kt`,
`VideoJobCoordinatorTest.kt` and `tools/video-motion/render.test.cjs`; this queue,
TABI and Validation. New lossless pose derivatives/contact probes belong only in
`build/vg2-wave-control-a1/`. The admitted slice can finish technical fixture
work while motion/contact artistic review remains open; a real 5–10-second
preview still needs its own exact tool/kit/destination and resource admission.
Bounded repair 2 includes the runtime-version consumer `adapter/VideoMotionRenderer.kt`
and receipt fixtures `VideoMotionRendererTest.kt`/`VideoPreviewHostCheckTest.kt`:
the full test attempt exposed the adapter's old 1.1.0 receipt gate after the
compositor's 1.2.0 bump. Update that exact consumer, retain strict version
rejection and add an old/unknown-version regression; no compatibility mode.

VG2-08 non-live control result (2026-09-30): implemented `POSE_REPLACE` and
3–16 explicit absolute-frame held-pose steps (≤9000-frame span), neutral entry/
return, shared subject/placement/pivot and measured cutout/clean-plate admission.
Preparation and fingerprints consume every selected pose and its timing/review;
durable descriptors reject ambiguous subject controls. Canvas replaces the whole
cutout with its alpha applied once: no neutral-arm clipping, old hand underneath,
cross-dissolve or generated intermediate art. Legacy blink/breathing behavior is
unchanged. Runtime manifest 4/tool 1.2.0 and its strict Kotlin receipt consumer
agree. Production-imported **synthetic fixtures**, not the TABI kit, pass exact
return, newly exposed hand, fixed lower-body/contact, foreground occlusion,
translucent edge, source-pin and split-range tests. Old-owner witness rejects the
same sequence; current owner admits it. Initial focused test failure was a legacy
fixture assigning the same ID to both pose capabilities; bounded repair 1 fixes
that setup and preserves single-pass cutout alpha. The first full test command
timed out at 220 seconds and exposed the old receipt-version consumer; bounded
repair 2 corrects it and rejects 1.1.0/unknown receipts. Complete `make test`
(4m16s), `make build`, focused JVM tests and Node motion/scenery 31/31 pass.

**Actual artwork is still blocked, not a motion-input pass.** Static probes in
`build/vg2-wave-control-a1/static-kit-proof.json` preserve all 29 protected pins
and three approved v2 hashes. Halfway/wave lower source RGBA at y≥760 is exactly
neutral, and both single-pass cabin stacks match 41-v2/37 exactly. However,
neutral→halfway differs at 80,646 head/frond, 20,049 eye and 2,685 mouth-region
pixels above 8/255; halfway→wave changes none there. The supplied cropped neutral
also compensates for the legacy self-alpha clip: single-pass replacement differs
from approved 29 at 3,017 pixels above 8/255 (max 46), whereas legacy clipping
matches within two levels. Even the published wave/midpoint lower cabin pixels
differ from 29 at 384 pixels above 8/255 despite identical source lower RGBA.
Do not hide these differences by switching alpha policy per pose or blending
heads. The rejected common-head extraction stays rejected; static contact review
is `static-registration-contact-review.png` in that scratch root. **VG2-08 returns
to BLOCKED** pending a matching-head neutral and consistently authored alpha kit,
actual-kit production import/contact/return proof, then separate exact bounded
preview admission. VG2-07 remains WAITING_USER for revised footage. No additional
model/image job, real-artwork renderer/media job, historical project or accepted
take mutation, video replacement or commit occurred. Validation does not confer
artistic motion approval or native 30-fps articulation.

VG2-06 source-pool direction (historical input scope): use the already supplied
`docs/pictures/video/tabi-assets/scenario/` and sibling TABI asset folders to
prepare **new video tests**, not unrelated placeholder art. Start from
`scenario/tabi-quiet-ride-through-tokyo.png` (SHA `01e4db85…2473e`) and the
accepted corrected leaf/window preparation for original-scene identity. Reuse
the hash-pinned `scenario/tokyo-parallax-{far,middle,near}-5600x1080.png`
for rigid scenery; consult `train-actions/` for TABI identity/action references.
`01-drinking-coffee.png` has the wrong handled mug/pose for this isolated
breathing test. The subsequent user decision above selects `28`–`30` as the
replacement-appearance baseline, not as validated motion layers. Step 2.1 must
measure full filtered/shutter swept support, contact and negative cases against
that route. Only then import/reopen (Step 2.2)
and seek separate bounded admission for a 5–10-second test (Step 3); no live
video job follows merely from choosing a source directory. For the later VG4-05
scenery test, `scenario/tokyo-clockfront-near-v5/` has approved **static** look
only; check far authored coverage and the actual moving join before extending
or combining footage. Preserve source bytes, old MP4s, accepted mask and takes.
This was the pre-footage source-selection scope; the technical result above now closes VG2-06. VG2-07 remains WAITING_USER for the actual moving artifact.

VG4-05 independent scenery-art study while VG2-07 review remains pending
(2026-09-29): user requested more visibly varied exterior scenery using
`scenario/inspiration-tokyo/`. One Pi-generated 2172×724 opaque side-on panorama
and one targeted lateral-canal correction used the illustrated
`panorama-style.png`, protected far-plane palette, Asakusa gate and Ginza clock
photo references. The selected **study**, not an accepted depth layer or joined
extension, is `scenario/tokyo-sideon-district-variety-study.png` (SHA-256
`fadd6ada400114dc4c89857be0cc59970f286bf7e175d6c6c557f5fc9f5df879`).
Ignored `build/vg4-tokyo-variety-a1/` retains both model PNGs, three window-position
stills using the **unapproved** replacement cabin 28–30, the scratch recipe and
`static-preview-report.json`. Static window compositing changes zero pixels
outside the provisional mask, is opaque and has zero sample coverage gaps at
three selected positions. The first scratch Canvas-mask preview leaked 1,255
pixels outside the mask; exact-alpha composition corrected this scratch-only
study, not a production renderer. The distinct older blocks → bridge/canal →
cream clock-front sequence is visible in stills but is **not** a seamless handoff,
separated far/middle/near planes or sufficient 60/240-second coverage. Fixture
Gradle, 27 Node motion tests, `make test`, `make build` and both diff checks
passed; logs and protected-source/plane/four-MP4 hashes are in the same scratch.
Do not move VG4-05 to DONE or replace the protected planes; no video render,
new take, production import or human approval. See [district-variety still study](docs/TABI_VIDEO.md#side-on-tokyo-district-variety-study-2026-09-29).

VG4-05 follow-up **scratch-only** join preparation after the user's approval of
tiled depth sections (2026-09-29): `build/vg4-60s-a1/` holds a measured
coverage receipt, one generated candidate near cutout, a derived 6000×1080
near section and five window stills. The initial manual extraction from the
flat study was rejected (visible polygonal sky); the replacement cutout has
measured alpha and a native overlap preserving all old near pixels through
x=4127. User feedback identified a ghostly doubled façade at frame 1380:
the 90-pixel entrance alpha ramp was removed, and the candidate now starts
at an open bridge from x=4660 after a 532-pixel quiet near-depth pause. A
focused regression checks the formerly shaded region, opaque entrance pixels
and late new-art visibility; the rejected still remains in scratch. At the
candidate frame-810 handoff, three same-camera shutter samples differ only
by canvas re-rasterization (at most two 8-bit channel levels), with no
opaque-window gaps; sampled cabin stills change zero pixels outside the
*unapproved* window mask. **User review (2026-09-29):** after inspecting the
revised `window-frame-1380.png` (SHA-256
`47410cd5a6117f85d45848dfbbb0c1099b273413ca06cfa43730ddb748c43fbc`),
the user said “yes, this one is ok, i confirm the quality of the backgrounds”.
Record this as the initial **static background appearance** response only;
do not reinterpret it as approval of the earlier shaded still, character kit,
moving seam, entire corridor or video. **Follow-up review corrected this
response:** the user found a lower-right terrace incorrectly overlaid on the
middle-depth house in that same frame, so this still is not accepted as clean.
This is **not** a moving seam test,
character-art approval or coherent three-plane extension. The existing far
plane's earlier measured authored extent of 2500 px is still 82 px short
for 60 seconds, even though its opaque PNG has transparent-free padding past
that extent; the new near strip also needs shutter/alpha and moving-join
review. In response to the terrace report (2026-09-29), v2–v4 local-mask
attempts in ignored scratch removed one defect but left floating trees or a
hard building edge. A new source-island cutout (SHA-256
`d2caeacead907fd30dc304db32f9886890ae0e88b11287afe41763d645a3b1dd`)
yields the v5 6200×1080 near candidate (SHA-256
`c1114749dc6b1ae704d448231320c13ef3e3ba092b71ecab7394500b6fc9a1e0`),
preserving the earlier stills/bytes. Its first new alpha≥8 column is x=4956
and its last x=6019; the deeper streets carry the quiet gap. Static frame-1380
regression detects zero changed pixels in the reported 13,352-pixel window
area, no old/new entrance fade, and >318,000 distinct new-near window pixels
at frame1799. **User decision (2026-09-29):** after reviewing v5 stills, the
user said “the v5 version is ok” and requested saving it in `scenario/`.
The approved scope is v5 **static background quality/appearance**, not a
motion-tested source section. Preserve exact copies of the v5 section and its
editable source in [tokyo-clockfront-near-v5](docs/pictures/video/tabi-assets/scenario/tokyo-clockfront-near-v5/):
`tokyo-near-section-v5-candidate-6200x1080.png` and
`tokyo-clockfront-cutout-v5-source-2172x724.png` (the two SHA-256 pins above).
Do not replace the protected far/middle/near planes or the earlier reviewed
stills. This does not prove moving quality, 60-second far authored coverage or
180-second variety. Do not start VG4-06's 180/240-second corridor, render,
import or mark VG4-05 DONE while VG2-07 and the moving seam gate remain open.

VG2-06/07 scope: start with one compatible gentle movement/glance before cup or
hand choreography; a broad pose library is not required. Inspect supplied art
first. The drinking reference's handled mug and pose do not match the scene's
paper cup and hand-on-cheek pose. Moving head/body/gills need suitable clean cabin
pixels, aligned subject/pose layers and moving eye/foreground occlusion; the
accepted fixed-head mask alone is insufficient. Prepare only what the chosen
movement needs, outside the app, in a new derived bundle. ComfyUI and one-time
RealESRGAN asset finishing remain selected where separately authorized; do not
redraw the entire video with I2V or regenerate the approved 30-second result.

Owners: new ignored preparation/test harness and review artifact; current import,
prepared-scene, compositor and supervised job/media services are reused. A required
new control or reproduced defect must become a narrowly owned prerequisite here
before production edits; no generic app functionality or parallel job ledger.
Proof: input/alpha/alignment and unchanged-region checks, actual decoded movement
and source/encoded geometry/cadence, protected hashes, bounded resource receipts,
then VG2-07's artifact-specific human decision. Before live work, admit exact files,
job/correction counts, paths and cumulative/per-stage budgets; this row alone starts
no inference or render. Apply focused/full/build/diff and applicable motion checks.

VG2-03 bounded continuation (2026-09-28): the user likes the five-second parallax
(`3e104ea878eee9b4f4afbe76bb5c5b182495b44b4cb3dce156da33730a241aa9`) but finds it
slow and the background tower stylistically inconsistent. They requested a longer
test with more passing scenery. Prepare three wider ComfyUI plates, retaining the
illustrated architectural style and matching the tower's linework/shading. Target
20 seconds / 600 frames at 1080p30, approximately twice the previous travel speed,
with fixed TABI/cabin and no loop, stretching or frame interpolation. At most three
initial still generations plus one targeted artistic correction, three selected
RealESRGAN finishes and one controlled-media attempt; stop on a resource failure.
Existing runtime/model pins and pressure/swap guards remain. Controlled native
memory stays 2 GiB and wall time 900 seconds; a new duration-sized 7-GiB staging /
1-GiB output admission replaces the five-second request's 3-GiB staging allowance,
with the same 10-GiB free-disk reserve. It is not a bypass of a failed admission.
Owners for this evidence slice: ignored scratch wrappers/preparation, selected
new review artifacts, this queue, TABI and Validation. Inspect production owners
but change no production code without a separately scoped reproduced defect.
Scratch: `build/tabi-tokyo-long.58RtOJ/`. No commit, download, provider change,
character animation, integrated UI or full-duration acceptance is authorized.

VG2-03 continuation evidence: the [20-second review clip](docs/TABI_VIDEO.md#twenty-second-faster-tokyo-test-2026-09-28)
is rendered at 1920×1080 / 30 fps; SHA-256
`30364b7e3a50b88ce217504f5f7e7805c7fa07f1cd16e113f7f12be0e30d20f7`.
Three initial 3072×512 ComfyUI paintings, one admitted far correction and three
selected RealESRGAN x2 finishes were used. Preserve the clipped-antenna candidate,
first faulty roof matte and rejected non-inset keeper; a curated inset mask restores
original opaque roof pixels, with two scratch regressions. No production code changed.
One controlled attempt succeeded in 325,083 ms, using two 300-frame chunks with
matching continuation state. All 600 lossless frames preserve 1,351,577 fixed pixels;
all 600 decoded frames are distinct with uniform timestamps. Far/middle/near travel
is 480/960/1440 pixels, about 1.99× the previous speed. Renderer sampled peak is
494,387,200 bytes, under the unchanged 2-GiB native limit. The middle still job had
two WARNING samples and ~3.27 GiB host-global swap increase; no sampled CRITICAL.
The imported take remains UNREVIEWED. Review the new speed, tower, variety and
mattes; 30-second/recovery, integrated app, blink and full-duration/release gates
remain open. Focused/Node/matting/full test/build/diff logs and final source pins
are in `build/tabi-tokyo-long.58RtOJ/`; publication retains the gate receipt.
That completion admitted no further render by itself. Subsequent user feedback says
this 20-second test looks better; it becomes the direction/reference for the next
isolated test, not full-duration or release approval.

VG2-03 blink admission (2026-09-28): user authorized point 1, a 5–10-second isolated
blink, and cleaning the review directory to video results only. Archive the exact
563-file prior review tree without deletion to
`build/video-test-archive-2026-09-28/previous-tests/`; verify every byte, leave the
latest 20-second MP4 directly in `docs/pictures/video/tests/`, and update live docs
links. Preserve historical machine receipts and their former paths with a relocation
receipt, not edited provenance. No cleanup elsewhere or commits.
Scratch owner: `build/tabi-blink.0GtJyK/`, this queue, TABI/Validation and selected
review media. First prepare matching half/closed eye artwork via the selected local
ComfyUI route: at most two initial still jobs plus one artistic correction, two
selected RealESRGAN finishes, 3600 seconds cumulative owned-wrapper admission,
existing 48-GiB artwork/pressure/swap guards, 4-GiB staging and 10-GiB free reserve.
Keep the exact 20-second starting composition fixed except tightly bounded eyes.
The current compositor crossfades one aligned pose; it does not yet consume a
three-pose eyelid sequence. Inspect the artwork before claiming a natural blink;
if alignment/appearance or this motion limitation prevents a credible test, stop at
the pose-review/capability gate rather than inventing missing control. At most one
separately pinned 8-second controlled preview, 1080p30, 2-GiB native memory,
900 seconds, 4-GiB staging / 1-GiB output, only if the selected assets support it.
No scenery motion, head/body motion, whole-scene I2V, provider change, download,
production-code expansion or three-minute generation is authorized by this slice.

Blink disposition: three 1024×1024 ComfyUI still submissions completed (initial
closed, the admitted closed correction, initial half). The first closed pose added
unwanted lashes/shading; the half pose changed iris/gaze geometry and is not selected.
Only the corrected closed pose received RealESRGAN x2 finishing. Tightly masked,
boundary-colour-matched preparation preserves 2,064,229 non-eye pixels exactly in
the static composition. Pose art remains UNREVIEWED; this is not a matched three-pose
kit or a moving-frame proof. One typed eight-second open/closed blend attempt was
refused before native launch: `2147483648` required free bytes, `1619968000` available.
Attempt `attempt-74a82f7a-0b81-478a-9468-94249d8f565a` is FAILED; zero PNG frames,
no new MP4 and no take. No retry, memory-limit reduction, cache purge or unrelated
process termination followed. BLOCKED for memory admission/fresh authorization;
current renderer still lacks an articulated three-pose blink. Prepared eye-art
review and a separately admitted same-quality retry can proceed without regeneration.
All 563 archive hashes and the retained MP4 are verified; four scratch preparation/
archive/refusal checks, 27 Node checks and focused/full test/build/diff gates are
recorded in `build/tabi-blink.0GtJyK/`. Six artwork WARNING samples, no sampled
CRITICAL; maximum per-job sampled swap increase ~1.64 GiB (host-global). Owned
ComfyUI and render wrappers stopped; no production code or unrelated WIP changed.

VG2-03 fresh blink retry (2026-09-28): user explicitly requested “rety the blink
video.” Admit one new eight-second / 240-frame, 1080p30 controlled attempt using
byte-identical prepared open/closed eye art, controls and encoding. Scratch:
`build/tabi-blink-retry.gc18u7/`. Preserve the original FAILED attempt and all
archive/source bytes. No new ComfyUI or finishing work. Require three stable
NORMAL samples with at least 3 GiB free before launching the wrapper (maximum
120-second wait); the production 2-GiB memory admission/enforcement remains
unchanged. Keep 900-second native / 1020-second wrapper limits, 4-GiB staging,
1-GiB output and 10-GiB free reserve. Full decode review is separately bounded
at 2 GiB native memory, 3 GiB staging and 180 seconds per invocation. No automatic
retry after failure, unrelated process termination, quality reduction, production
change, commit or broader motion. Only a successful verified MP4 enters the review
folder; artistic/take acceptance and three-pose eyelid motion remain open.

Fresh blink retry evidence: one controlled attempt succeeded in 106,792 ms;
[8-second review video](docs/pictures/video/tests/tabi-blink-8s-1080p.mp4), SHA-256
`e5d3376c9abf01ee820ccf93176a7e28b6db586acdacca8ab02f6cc34b5582c2`.
240 fully decoded frames, silent H.264/yuv420p, 1920×1080, square pixels and uniform
30 fps. Seven source frames change (101–107), peak closure at frame 104 / 3.467s;
233 intentional open-eye holds are not new motion or duration-extension evidence.
All source frames preserve 2,064,229 pixels outside the eye support exactly.
Eight unique source frames; decoded uniqueness also reflects lossy compression.
Native Node sampled peak 302,628,864 bytes under the unchanged 2-GiB cap; all
55 samples NORMAL, no sampled swap growth, owned wrapper exited. No ComfyUI or
finishing submissions. Original art/failure/archive remain unchanged. The scratch
parity checker initially rejected new import metadata; its corrected comparison
and three checks verify unchanged pixels/controls/quality and reject changed inputs.
The one authorized render had already started; no second attempt or weaker guard.
Fresh focused JVM, 27 Node, scratch parity, full test/build and diff checks are
recorded in `build/tabi-blink-retry.gc18u7/`. Take remains UNREVIEWED; visible
intermediate pose ghosting reflects the open/closed crossfade, not articulated
three-pose eyelid motion. WAITING_USER for normal-speed blink review. No further
render, combined motion, full duration, application integration or release implied.

VG2-03 combined admission (2026-09-28): user says “ok, the blink is well made,
we can continue with the next step”, approving the exact eight-second blink above
for combination with the current parallax. Record a new scoped review event; do
not rewrite sealed UNREVIEWED receipts or automatically select any take. Next is
one 20-second / 600-frame combined test, not 30–60 seconds or three minutes.
Scratch: `build/tabi-combined.e6gsws/`; owners are its scratch harness/preparation,
new review MP4, this queue, TABI and Validation. Copy the existing scenery and eye
art exactly; cut only the binary eye support out of cabin foreground/occlusion
alpha so that the foreground cannot cover the blinking eyes. No new artwork,
resampling, ComfyUI/finishing, head/body/hand motion, provider change or production
code is admitted. Use the approved blink seed/control on an absolute 20-second
clock; preserve 480/960/1440-pixel scenery travel and three-sample shutter.
One attempt, two <=300-frame invocations; unchanged 2-GiB native memory,
900-second native / 1020-second wrapper, 7-GiB staging / 1-GiB output and 10-GiB
free reserve. Require three NORMAL >=3-GiB-free preflight samples in <=120 seconds.
Decode review: 2-GiB native, 3-GiB staging, <=180 seconds per invocation. Failures
stop, never retry automatically. Prove all non-eye pixels match prior parallax
frames and the first eight seconds of eyes match the approved blink; validate
absolute timing/chunk continuity and preserve prior evidence. No commits or
broader duration/integration/release approval.

Combined evidence: [20-second blink/parallax MP4](docs/pictures/video/tests/tabi-tokyo-blink-parallax-20s-1080p.mp4),
SHA-256 `7136e45800aebc577e4529a36cc3007b05117199db611e6a3a76a7edb686970f`.
One attempt succeeded in 331,992 ms, with two absolute 300-frame chunks and exact
scenery continuation. All 600 source and decoded frames are distinct; full decode
confirms silent 1920×1080 H.264/yuv420p, square pixels and uniform 30 fps. Every
source frame's 2,064,229 non-eye pixels exactly match the prior parallax frame;
the first 240 eye regions exactly match the approved blink. All 1,342,206 pixels
outside both moving regions stay fixed. Blink peaks: 3.467s, 10.467s, 19.4s; no
phase reset at the chunk boundary. Sampled Node peak 562,118,656 bytes, 166 NORMAL
samples, no sampled swap growth, unchanged 2-GiB enforcement and stopped wrapper.
No production change or new inference/finishing. Fresh focused JVM, 27 Node,
three preparation checks, full test/build/diff gates and preservation receipts are
in `build/tabi-combined.e6gsws/`. New take remains UNREVIEWED; WAITING_USER for
combined normal-speed review. Earlier blink approval stays scoped to its artifact;
30–60-second coverage/recovery, full duration, UI, rights and release remain open.

VG2-03 continuity admission (2026-09-28): user says “perfect, we can continue
with the next step”, approving the combined 20-second artifact above. Record this
as a new scoped human event, without rewriting sealed project/take receipts.
Scratch: `build/tabi-continuity.0lyIo2/`; same evidence/docs owners, no production
change or commit. Coverage inspection permits 30 seconds using unused supplied
near-plane pixels after a 480-pixel rightward starting reframe. This changes its
initial crop, not scale, pixels or speed; far/middle, cabin and blink stay unchanged.
Check all 900 frames × three shutter samples, no visible padding/wrap/loop; 60
seconds still needs more artwork. No ComfyUI or finishing jobs admitted.
One 900-frame controlled attempt: 2-GiB native cap, 900-second native / 1020-second
render wrapper, 7-GiB staging / 1-GiB output, 10-GiB free reserve; preflight three
NORMAL >=3-GiB-free samples in <=120 seconds. One additional <=240-second JVM
reopens the completed backend publication before coordinator success/take import,
with renderer/encode launch guards failing closed. This tests real process restart
at the completion boundary, NOT interrupted-chunk resume or abrupt crash recovery.
Current production media stage cannot resume partial rendering; VG5-01 remains open.
Verify unchanged frame/MP4/receipt hashes through restart and one attempt/one take.
Decode review stays 2-GiB native, 3-GiB staging, <=180 seconds per invocation.
Stop on resource failure; no automatic retry, unrelated process cleanup, longer
render, source regeneration, quality reduction or release approval.

VG2-03 continuity result (2026-09-28): published
`docs/pictures/video/tests/tabi-tokyo-continuity-30s-1080p.mp4`, SHA-256
`abb990dab8542e30113fc0fe9ee89de5275e5c7c79167916f2f5ae4d755c81ea`, as an
**UNREVIEWED draft with incomplete additional decoded-PNG review**. One attempt
completed in 504,265 ms before handoff. JVM 5988 exited with sealed media but no
coordinator success/take; fresh JVM 9882 reconciled that same attempt and imported
one unselected take in 11,865 ms. Renderer/encode launch guards were never invoked;
all 900 source PNGs, MP4 and completion receipt stayed hash-identical through restart.
No partial-render or abrupt-crash recovery was attempted or established.
All 900 source frames are distinct; 1,342,206 pixels outside eyes/window stay exact.
First600 eye regions match the approved combination. Top-window pixels match for
frames0–598; old599 clamps its final shutter sample, whereas the longer run correctly
continues. Retained failed comparison plus an endpoint regression explain that
specific difference, not a speed/quality change. Three absolute 300-frame chunks
continue exactly. Blink peaks: 3.467/10.467/19.4/23.733 seconds.
Production media validation and full900-frame timing/count probe passed: silent
1080p/30fps/H.264/yuv420p, SAR1:1, 30 seconds. **Additional PNG extraction timed out
at its unchanged180-second guard after893 readable frames.** All893 inspected
frames are distinct; encoded frames893–899 remain uninspected independently.
No automatic retry, cap increase, regeneration or rerender followed. Partial
artifacts and scratch metadata/log-name failures remain retained. Render/recovery
samples were all NORMAL (250/8), no sampled swap growth; Node peak564,723,712 bytes.
Independent software gates are in scratch final-gate logs. VG2-03 remains
WAITING_USER for normal-speed draft review and fresh authorization to finish the
remaining decoded-frame check under the same limits. Sixty seconds needs more
scenery; longer duration, mid-render recovery, UI integration and release remain open.
See [continuity/recovery evidence](docs/TABI_VIDEO.md#thirty-second-continuity-and-completion-recovery-2026-09-28).

VG2-03 tail-review admission (2026-09-28): user calls the 30-second result “a great
result”, reports the pre-existing leaf/window cutout issue, authorizes the next
step and explicitly says “don't regenerate this video”. Record positive scoped
feedback with that caveat; do not infer playback speed, release approval or mask
repair. Scratch `build/tabi-tail-review.rT83Xd/`; docs/evidence owners only, no
production change or commit. Complete the missing decoded-frame inspection with
one pinned read-only FFmpeg invocation: output-side seek to29s, frames870–899,
23 previously inspected overlaps plus7 missing frames. Assert overlap pixels and
900-frame union; retain all old files/timeout receipts. Same2-GiB memory/180-second
native deadline,215-second wrapper,3-GiB aggregate decoded staging INCLUDING the
retained prefix,10-GiB free reserve. Require three NORMAL >=3-GiB-free samples
within120 seconds. No new render, video encode, generation, mask change, new take,
automatic retry or increased guard. VG1-03 tracks the separate future edge repair.

VG2-03 tail-review result: the single extraction succeeded in9.885s (wrapper10.290s).
All23 overlap frames match the original decoded RGB exactly; the seven missing
frames complete900 distinct inspected decoded frames, without counting overlaps
twice. Full-video PSNRmin35.2817dB, RGB MAEmax2.8130, fixed-region temporal MAEmax2.6156.
New PNG staging67,031,353bytes; aggregate old+new2,023,819,474bytes, under3GiB.
Six NORMAL samples/no sampled swap growth; FFmpeg sampled peak69,795,840bytes.
The MP4 SHA remains `abb990dab8542e30113fc0fe9ee89de5275e5c7c79167916f2f5ae4d755c81ea`.
No video/artwork regeneration, new take, mask edit or prior-receipt rewrite. The
original timeout remains preserved historical evidence. Three scratch contract
checks reject shifted overlap, missing endpoint and repeated frames; focused,
Node/full test/build/diff logs are in `build/tabi-tail-review.rT83Xd/`.
VG2-03 is REVIEW for technical integration assessment, not waiting for another
copy of this decode or the same user feedback. The native controlled ladder is
now evidenced; the typed harness is not integrated UI or three-plane convenience
compilation. VG1-03's reported mask defect, longer unique scenery, mid-render
recovery, full duration and release remain open. Do not regenerate this video.

VG2-05 admission (2026-09-28): the user authorized a retry and a quality-preserving
resource solution if needed. The same-limit retry in
`build/tabi-parallax-retry.2Ixv0Z/` passed admission but stopped after 23 PNGs at
2,159,640,576 owned native bytes versus the unchanged 2,147,483,648-byte cap. No
MP4 or take exists for that attempt. Inspection found a synchronous whole-chunk
PNG loop, preventing event-loop/native-finalizer service between frames. Bounded
owners: `tools/video-motion/render.cjs`, its tests, this queue, TABI and Validation.
First prove the missing yield with a regression; then service the loop without
changing drawing, dimensions, timing, supplied layers or three-sample shutter.
Retain failed ledgers/artifacts and require exact overlapping PNG comparisons;
no safeguard bypass, downloads, inference, UI expansion or commits. One initial
implementation plus at most two bounded repairs; record a new one-attempt admission
before native validation rather than replaying either failed request.

VG2-05 technical evidence (2026-09-28): HEAD `b208cc700` plus the preserved WIP
and the scoped renderer/test diff. Two event-loop regressions failed before the
per-frame macrotask yield; a follow-up regression caught cancellation during the
last yield before receipt publication. The final guard and all 27 Node tests pass.
Focused renderer/media/coordinator/import/descriptor suites passed; full test/build
and diff gates passed before the first fixed run and are rerun for the final
publication/docs candidate. Exact source pins, logs and receipts:
`build/tabi-parallax-final.ScZPRv/` (earlier refusal in
`build/tabi-parallax-retry.2Ixv0Z/`, first success in
`build/tabi-parallax-fixed.cxfiro/`). No native limit, schema, drawing, shutter or
encoding policy changed. No new model submission or finishing was needed.

The initial repair succeeded in 81,384 ms. One further bounded run against the
final cancellation-guard source succeeded in 72,973 ms; both unreviewed takes and
both earlier failures remain intact. The final sampled Node peak was 448,675,840
bytes (about 428 MiB), below the unchanged 2-GiB native ceiling; all 38 samples
were NORMAL, with no sampled swap increase. Sampled RSS is not complete unified
memory or an exhaustive peak. All 150 PNGs match the first successful candidate
byte-for-byte; the first 23 also match the failed unmodified renderer. Across all
frames, 1,351,577 pixels outside the window mask exactly match the resized original.
Selected start/end patches measure 60/120/180-pixel far/middle/near travel. Full
MP4 decoding proves 150 distinct frames, uniform 30-fps timestamps, silent square-
pixel 1920×1080 H.264 and five seconds. H.264 remains lossy; exact pixel claims are
for source PNGs, not compressed video. Finder `.DS_Store` changes were separately
excluded as metadata; supplied art, prior media and failed ledgers stayed pinned.

[Review clip and evidence](docs/TABI_VIDEO.md#five-second-parallax-and-memory-repair-2026-09-28)
are published; SHA-256
`3e104ea878eee9b4f4afbe76bb5c5b182495b44b4cb3dce156da33730a241aa9`.
VG2-03 remains WAITING_USER for real motion/artistic review before longer continuity;
this background-only proof does not close its native 20/30-second/recovery ladder,
blink, integrated app/UI, rights or VG6 release gates. No implementation commit.

VG2-04 admission (2026-09-28): the user-requested detailed Tokyo artwork test
reproduced `PERSISTENCE_FAILED: Known video progress cannot become unknown or
move backwards` after persisting node progress 100. The wrapper stopped its owned
runtime with no output; retain `build/tabi-comfy-panorama-detail.1me8vzy2/` unchanged.
Bounded repair owners: `core/video/adapter/ComfyVideoClient.kt`,
`ComfyVideoClientTest.kt`, `LocalVideoBackendTest.kt`, this queue and the existing
TABI/Validation references. No scheduler, store/schema migration, resource-limit
relaxation, extra provider or implementation commit. Focused client/backend/store/
coordinator regressions and full test/build/diff gates precede one bounded local
artwork retry; preserve both attempts. This does not complete VG2-03 or visual gates.

VG2-04 technical evidence (2026-09-28): HEAD `b208cc700` plus the client/test repair,
with client source SHA-256
`aae0ddb1dbf7e5b9b6b1c1730ecda6288782ec2cd418d8640ee0b508362d99dc`.
Three selected assertions failed on the old client, including durable reconciliation
after 100 → 25 node progress; client/backend/store/coordinator/setup/architecture/docs
suites pass after the fix. `make test` passed (4 executed, 10 up-to-date), `make build`
passed (14 up-to-date), and diff check passed before the native retry. Logs:
`build/tabi-comfy-progress-fix.pC46Id/`. The identical graph/prompt/dimensions/seed
then completed once through current production services, with changed client source
and bytecode included in request pins. Published native PNG SHA-256
`16c276238e2c62d0869c815c51d343bc2f9b680bbdee934a15f246fc810d362a`;
receipt and preserved failed attempt are under
`build/video-test-archive-2026-09-28/previous-tests/tokyo-comfy-panorama-detailed-2026-09-28/`.
The original failed ledger is retained ACTIVE at its last observation, not rewritten
as completed; its runtime is stopped. No schema/store invariant was weakened and
no commit was made. This proves the narrow progress repair, not artwork approval,
parallax, integrated app delivery or other VG2/VG6 gates.

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

Step 2.3 — WAITING_USER: no native preview or media probe was launched. Still
required: explicit authorization for the sequential 150/600/900-frame native run
under the existing per-job 600-second / 4-GiB memory / 2-GiB staging / 2-GiB output
ceilings, and selected canonical tools-directory, Node-executable, Canvas-manifest
and absent output paths under `build/vg2/`. Historical installed paths are not a
new selection or authorization. Full native decode/motion/recovery receipts remain
missing; this gate and VG2-03 cannot close. Independent integration proceeds.

Step 3.1 / VG2-01 and VG2-02 technical disposition: candidate `8c7c40b34` passed
fresh generation/coordinator/project-store/import/review/local-backend/Comfy-client/
media-probe suites (123 tests; 6 Gradle tasks executed), `make test`, `make build`,
and diff check. Logs `build/vg2/vg02-continuation/3.1-*.log`; current full XML is
under `build/test-results/test/`. Current Step 1.4 controlled-stage checks and
Steps 2.1–2.2 production-composition/recovery checks establish persisted claims,
no-start/retry/cancellation containment, attempt-bound receipt confinement,
measured once-only publication and explicit review/selection. These two backend
rows are DONE; native preview delivery remains VG2-03, WAITING_USER. Flat I2V is
still exactly 129 frames at 25 fps (5.16 seconds), not long generative coherence.
No graph/profile, MIDI, artwork or prior evidence bytes were changed by this run.

Step 3.2: candidate `6b8fe566f` passed owned motion-fixture generation, both Node
motion suites (22/22, explicit `/opt/homebrew/bin/node`), fresh architecture and
documentation checks (6 executed), and uncached desktop tests (11 executed).
`make test` passed (1 executed, 13 up-to-date), `make build` passed (14 up-to-date),
and diff check passed. Logs `build/vg2/vg02-continuation/3.2-*.log`.
No optional video runtime was added to MIDI composition/startup, no new MIDI
player or Video UI was introduced, and no model inference or hosted activity ran.
Fixture motion and desktop tests are not visual, musical or release approval.

Step 3.3 — engineering integration PASS, overall VG02 WAITING_USER. Candidate
`db1f881eb` plus the final source manifest at
`build/vg2/vg02-continuation/source-manifest.json` identifies the combined input.
Final inspection strengthened native evidence against nonuniform timestamps and
wrong absolute blink phases (not merely average FPS or different endpoints);
matching regressions pass. Uncached full root tests passed (692 tests, 6 tasks
executed); uncached desktop tests passed (239 tests, 11 tasks executed). Fresh
motion fixtures and Node tests passed (22/22); `make test` passed (1 executed,
13 up-to-date), `make build` passed (14 up-to-date), and diff checks passed.
Logs `build/vg2/vg02-continuation/3.3-*.log`, archived root/desktop XML, synthetic
fixture bundles and SHA-256 receipt remain in that ignored evidence directory.
No native 5/20/30-second ladder ran; no corresponding native receipts exist to
compare or approve. VG2-01/02 are technically DONE; VG2-03 and overall VG02 stay
WAITING_USER at Step 2.3. No artistic, 1080p/full-duration, Logic, Video UI,
installed-app or release completion is claimed.

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
| VG3-01 | After full-pilot and second-city reuse review, obtain confirmation of Video-only design scope/process and permission to create flow artifacts. Do not redesign the six MIDI pages or change agent instructions without permission. | VG5-07, VG5-10 | WAITING_USER |
| VG3-02 | Prepare a reviewable feature flow covering empty/setup/create/open, finished-artwork import and motion setup, prompt/duration, generation/recovery, moving review and full-cut export at supported sizes. Reuse current visual primitives. | VG3-01, VG1-01 | TODO |
| VG3-03 | Record explicit approval naming the mockup revision/digest, reviewer/date, covered surfaces and deviations. Agent recommendation, browser rendering and old reference art are not approval. | VG3-02 | WAITING_USER |
| VG3-04 | Add the lazy app-level MIDI/Video shell, independent create/open and `--video` startup. Keep six MIDI destinations, retained tab state and one MIDI player; entering Video pauses it without losing position. Missing video setup leaves MIDI usable. | CORE-01, VG3-03, VG4-01 | TODO |
| VG3-05 | Wire finished-scene/optional-layer import, thumbnails, visible placement/anchors, motion prompt, duration and setup/capability guidance. Replacements/reopen preserve originals and unaffected selections; no JSON, outfit-transfer or Generate look UI. | VG1-01, VG3-04 | TODO |
| VG3-06 | Wire production Generate/Cancel/Retry/recovery and take review/selection. Show real stage/progress or unknown state; duplicate clicks, tab changes, late results and restart cannot lose selections or start duplicate work. | VG2-03, VG3-05 | TODO |
| VG3-07 | After the production pilot, finish review/integration of the existing bounded off-UI-thread decoder and one silent preview session. Preserve its WIP; verify accurate seek/frame-step, corrupt-file errors and owned-process teardown. | VG2-02, VG5-07 | TODO |
| VG3-08 | Expose moving take playback with play/pause/seek/frame-step and resize through that decoder. Prove actual frames in an app-window capture and stop preview on departure without creating a MIDI player. | VG3-06, VG3-07 | TODO |

VG3-07 integration check (2026-09-26, pending independent review): candidate
`27b670cc94e9d19e23d3913cd4a4ecaa5a18d438` plus uncommitted backend repair and this evidence note;
unrelated untracked `.venv-transcription-spike/`, `.venv-worker/`, `.venv/` and
`tools/__pycache__/` remain untouched. The combined decoder/session uses read-only
project artifact resolution, manifest/hash/build-pinned tools, bounded rational
presentation scans, PNG staging and pixel budgets, owned cancellation/teardown
and lazy construction. Focused root preview/media/import/store/architecture/docs
suites passed with six tasks executed; focused desktop MIDI startup/composition
passed with one executed and ten up-to-date. The motion fixture selector passed
(one executed, five up-to-date), and Node motion suites passed 22/22. `make test`
passed (two executed, twelve up-to-date), `make build` passed (fourteen up-to-date),
and `git diff --check` passed. Command logs are under ignored
`build/vg3/step-3.1/`; XML is under `build/test-results/test/` and
`desktopApp/build/test-results/test/`. This fixture-backed engineering evidence
is not native decoder proof. Review found prefix timing/decode work on each
seek/playback frame despite bounded outputs. The repair caches at most 512 sparse
timing anchors and a 256-frame recent window, seeks before FFmpeg input, selects
measured PTS and rejects mismatched showinfo timestamps. Fake 9,000-frame
backward seek/playback and wrong-PTS regressions pass; this is not a native speed
measurement. Final repair logs: `build/vg3/step-3.1-repair/{focused-final,desktop-final,motion-final,node-final,make-test-final,make-build-final,diff-check-final}.log`. A subsequent review found cold long seeks still rescanned the prefix: 8,997 required 30–60 metadata invocations. The uncommitted cold-seek repair reads a capped MP4 timing table (including bounded composition-offset reorder), identifies the decoded ordinal within a fixed neighborhood, and corroborates its PTS in one bounded local FFprobe window; unsupported/contradictory indexes fail closed. Fixture tests check near/far cold seek count, reordered samples, absent target timestamp and long playback. Current candidate results: `build/vg3/step-3.1-cold-repair/{focused-ctts-final,desktop-final,motion-final,node-final,make-test-final,make-build-final}.log` (root 6 executed; desktop 1 executed/10 up-to-date; motion 1 executed/5 up-to-date; Node 22/22; make test 2 executed/12 up-to-date; make build 14 up-to-date). These synthetic MP4 tables and injected process responses are not a measured native MP4 decoder result. An independent review found that rejecting all edit lists excluded the repository's real owned-motion.mp4 fixture and common identity-edited MP4s from cold seeking. The repair accepts only one full-duration, rate-one edit from media time zero after checking movie/track time scales and duration. Pure parsing of the unchanged owned MP4 fixture now confirms first/interior/last timing; a long synthetic identity-edited take confirms one-probe cold seeks and playback, while shifted/partial edits remain rejected. No native probe was launched. Updated verification logs: `build/vg3/step-3.1-identity-edit/{focused,desktop,motion,node,make-test,make-build,diff-check}.log`. Other unsupported edits and tables beyond 1 MiB fail closed rather than guessing frame identity.
Focused root passed (6 executed), desktop passed (1 executed/10 up-to-date),
motion passed (1 executed/5 up-to-date), Node passed (22/22), `make test`
passed (2 executed/12 up-to-date), `make build` passed (14 up-to-date).
`:videoMediaProbe` was not run without newly selected pinned tools, fresh output
and bounded authorization. VG2-03's native ladder,
VG3 design approval/UI, app-window moving capture, artistic acceptance and overall
VG3 completion remain open; the queue status awaits review.

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
| VG4-03 | Scope preparation dependencies to their declared consumed components; keep shared/unspecified dependencies global, reject incomplete or invalid declarations and preserve unaffected pending chunks and completed takes. Bounded prerequisite split from VG4-01. | VG2-02 | DONE |
| VG4-04 | Persist immutable continuous-plan proposals under the existing Video project lock, with exact source/descriptor pins, append-only versions, revision guards, confined publication and verified reopen. A saved proposal is not executable readiness or a render checkpoint. | VG2-02, VG4-03 | DONE |
| VG4-05 | Prepare a coherent Tokyo extension and scene/pose-compatible window/foreground/frond mattes; the accepted old fixed-head mask does not automatically fit the replacement kit. Prove one offscreen join in a separately admitted moving test, with supplied 60-second coverage at approved speed/scale, exact overlap, measured alpha/shutter margins and all selected visible window apertures accounted for. Record user seam review before DONE. | VG2-07, VG1-02 | TODO |
| VG4-07 | Add the missing held-pose episode-schedule representation/compilation through existing VideoAssembly/Planner/Store and preparation/descriptor owners, behind fixtures. Pin activity order, timing, holds, neutral returns, every consumed pose and control/version; preserve absolute chunk/state parity and scoped invalidation. Respect one sequence/3–16 steps/≤9000-frame span and forbidden simultaneous subject controls, or scope a bounded prerequisite before extending them. No new episode schema/ledger, UI, artwork or live run. | VG4-03, VG4-04 | TODO |
| VG4-02 | Bind the continuous plan/clock and the reviewed two-activity episode schedule to executable camera/depth/occlusion/subject/effect state for the runner, without app callers. Validate every frame/shutter of the requested proof range and full coverage before full render; carry support/state and reject changed inputs. A short-range pass is not full-plan readiness. | VG4-03, VG4-04, VG4-05, VG4-07, VG2-10 | TODO |
| VG4-06 | After the combined 60-second review, extend the proven scenery method to the full 180-second corridor. Plan distinct passing views about every 5–10 seconds with quieter authored scenery between; pin all selected far/middle/near tiles/joins and verify complete alpha/trajectory/shutter/occlusion coverage and bounded decoded-asset memory. Do not count padding or repeated fragments as scenery. | VG4-05, VG5-05 | TODO |
| VG4-01 | After full-pilot and second-city reuse review, integrate the proven exact-frame workflow into app callers. Retain shared clock/seed, episode-specific action schedule, component reuse and chunks; move required prompt/fingerprint/estimate behavior behind tests before retiring exclusive short-shot/repeat callers/fields/tests. Preserve historical assets. | VG2-02, VG4-03, VG4-04, VG4-07, VG5-07, VG5-10 | TODO |

Current scheduling: VG4-02 no longer waits for VG4-01's app integration. It is the
minimal executable binding required by the production pilot, reusing the existing
planner/store/renderer rather than creating another planner or persistence schema.
All target-duration fixture checks remain required. The 60-second probe has its
own exact range and pins; it neither changes the 180–300-second product contract
nor grants readiness to uncovered parts of a full plan. VG4-01 remains deferred
until VG5-07 and VG5-10; its earlier “next” statements below describe prior scheduling.

VG4-07 prerequisite finding (2026-09-30): `VideoAssemblyActionKind` currently
contains BLINK, BREATHING, HEAD_GESTURE, STEAM and SCENERY_TRAVEL, not a supplied
pose sequence; `VideoAssemblyAction` uses a scalar `value`. The new runtime
control therefore does not already supply a persisted/executable episode script.
Owners: existing `VideoAssembly.kt`, `VideoAssemblyPlanner.kt`, `VideoAssemblyStore.kt`,
`VideoScenePreparation.kt`, request/descriptor binding and their focused tests.
Keep one current versioned contract and reject unsupported versions without
migration; split representation from execution binding if needed. Prove 180/240/300s
fixtures, event/pose boundary parity, changed order/timing/pose invalidation,
unsupported/conflicting sequences and unchanged completed media. No new generic
timeline editor, background generator or second planner. This row is planned,
not admitted or implemented.

VG4-05/06 owners: selected derived scenery and an ignored production-service test
harness; current `VideoAnimationAssets`, `VideoScenePreparation`, prepared-scene
store, `VideoMotionRenderer` and `tools/video-motion/` are inspection/reuse owners.
Scope any needed code change here before editing. Start with a single coherent
join, not a speculative panorama library. Inspect unused supplied artwork first;
separately admit any ComfyUI preparation/RealESRGAN finishing. Do not infer seamless
joins from similar prompts. Keep exact shared overlap pixels, depth ordering,
opaque backing and approved travel; no visible wrap, stretch, reverse or slowdown.
Test newly exposed regions around the corrected silhouette. Frame chunking does
not itself bound asset memory: the current renderer loads all supplied images.
Use bounded tiles/selected assets and verify actual memory before native admission.
The selected input paths, counts and budgets are not yet admitted by these rows.

VG4-04 admission (2026-09-28): “go for it” authorizes the next persistence slice.
Reuse `VideoProjectStore`'s lock, CAS and descriptor publication, first extracting
its prepared-scene publisher behind existing tests rather than copying a second
persistence path. Add compact assembly records and a bounded `VideoAssemblyStore`
that verifies source bindings and full descriptor identity on save/reopen. New
current Video project schema 5; schema 4 and other unsupported projects reject
without migration or rewriting their artifacts. Assembly schema 1/planner 2 and
media/prepared-scene formats remain unchanged. No current real project is opened
for writing. Test exact schedules/chunks/fingerprints, independent reopen, stale
writers, record/descriptor/source tampering, collisions, symlinks/protected MIDI
roots, failed publication and exact orphan reuse. Owners: `VideoAssembly.kt`,
`VideoProject.kt`, `VideoProjectStore.kt`, new `VideoAssemblyStore.kt` and test,
`VideoProjectStoreTest.kt`, this queue, Architecture, TABI and Validation.
Evidence: `build/vg4-plan-store.7MaJjG/`. One implementation plus at most two repairs;
focused/full/build/Node/diff gates. No model/media job, take/selection change,
resource-limit increase, commit, UI, legacy planner removal or resume claim.

VG4-04 evidence: `VideoAssemblyStore` saves and independently reloads schema-1,
planner-2 proposals with exact prompt text, source pins, seed, clock, actions,
support ranges, dependency lists and work fingerprints. Compact `assemblyVersions`
records bind descriptor SHA, prepared scene, finished original and provenance.
Prepared-scene and assembly descriptors share one project lock/CAS/publisher.
The source verifier reloads actual prepared-scene/image facts, not just metadata
or filenames. Documents and descriptors have explicit current versions; assembly
descriptors are limited to 1 MiB. Existing project schema 4 is rejected, never
migrated or modified. No user/private historical project was saved.

Fourteen new store tests pass: 180/240/300-second proposal round trips; ordinary
save append-only guards; stale/duplicate and concurrent writes; injected publication
failure, immutable orphan collision/exact reuse; wrong source/project pins;
changed/missing sources, hash-consistent but false decoded image facts; descriptor
or record tampering, unsupported/missing versions, malformed/oversized bodies;
symlinks, unknown IDs and MIDI-root exclusion. Existing prepared-scene/project
and 30 planner tests pass after publisher extraction. Initial 13 new tests passed;
the decoded-image-facts case was then added, with no failed check or repair.
Final focused, 27 Node, `make test` (789 root +239 desktop, zero failures/errors/
skips), `make build` and diff gates pass. Logs in `build/vg4-plan-store.7MaJjG/`;
post-documentation rechecks and final source/protected hashes are recorded there.
This closes the split persistence slice, not VG4-01 application integration or
obsolete short-shot removal, VG4-02 continuous compilation, or VG5 checkpoints/
render/export. Accepted artwork, the corrected mask and all four MP4s stay unchanged;
no model/real media job, new take, resource-policy change or commit. Next ready
continuity task remains VG4-01. See
[persistence scope](docs/TABI_VIDEO.md#continuous-plan-proposal-persistence-2026-09-28).

VG4-03 admission (2026-09-28): user says “ho ahead with this.” after the longer-run
preflight. Inspection finds `VideoAssemblyPlanner` copies every preparation pin
into both global and action dependencies, so non-consuming chunks are invalidated.
Prepared-scene pins have no component ownership; do not guess it from IDs or drop
unknown tool pins. Add explicit planning-request ownership declarations, validated
against the prepared scene. Omitted declarations conservatively mean scene-global;
an explicit declaration set must cover every preparation dependency exactly once.
Keep resolved used pins in the existing assembly; no second schema/ledger or media
request change. Advance the planner semantic version for newly resolved work.
Owners: `VideoAssemblyPlanner.kt`, `VideoAssembly.kt`, `VideoAssemblyPlannerTest.kt`,
this queue, TABI and Validation; evidence `build/vg4-dependency-scope.s6Nk20/`.
One implementation plus at most two bounded repairs, before-failing regressions,
focused/full/build/Node/diff gates. No old artifact mutation, native/model job,
resource-policy change, commit, UI work or claim of checkpoint implementation.
VG4-01 persistence/integration and VG5 remain separate unfinished tasks.

VG4-03 evidence: explicit component ownership now narrows preparation pins to the
existing global/action consumed-component sets. Missing ownership stays global;
explicit declarations reject omitted/duplicate/unknown dependency IDs and empty,
duplicate, wrong-kind or dangling component keys. No dependency is guessed unused.
Shared/base-composition dependencies still affect every consuming chunk; pose and
effect dependencies follow existing support-frame/particle-tail rules. The full
pin (version, digest and optional artifact path) remains bound. Unused pose/scenery
preparation no longer stales unrelated work when its ownership is declared, and
completed takes remain retained, never silently rebound to a new plan.

Three new checks failed before dependency selection was repaired. A serialization
check also reproduced acceptance of missing version tags; assembly schema/planner
fields are now mandatory on the wire, with current planner semantics version 2
and schema 1. Older or missing planner identities reject; no migration, prepared-
scene schema change or video-request change. The initial version-rejection test
accidentally edited JSON with omitted defaults; its failure is retained and the
fixture now explicitly includes the field before tampering. All 30 planner tests
pass, covering scoped invalidation, canonical order, caller-list mutation, full
pin changes, shared defaults, invalid ownership and serialization.

Fresh focused checks passed (2 executed/4 up-to-date), Node 27/27, `make test`
(4 executed/10 up-to-date), `make build` (14 up-to-date) and diff check. Logs/XML:
`build/vg4-dependency-scope.s6Nk20/final-*`; post-documentation rechecks and final
source/protected-artifact hashes are retained alongside them. This closes only
VG4-03's planner boundary, not persisted plans, UI, execution checkpoints or native
longer output. Next ready video-continuity task is VG4-01; the approved mask, four
MP4s, original art and sealed prior receipts remain unchanged. No commit or model
job; runtime limits and current MIDI behavior were not modified.

User decision (2026-09-27): the user reports manually verifying VG2-03 but no
longer has its native run evidence. At the user's explicit request, VG4-01 now
depends on the completed VG2-02 backend boundary rather than VG2-03. This waives
the native-preview *admission dependency for VG4 only*, not its missing proof:
VG2-03 remains WAITING_USER/unverified, and no 150/600/900-frame native results,
paths, receipts or artistic approval are claimed. VG4 must still pass its own
focused/full technical gates; this waiver does not satisfy VG3-06 or other
VG2-03 dependencies. At the user's request the VG4 workflow plan's admission
rule is reconciled with this decision; its task checkboxes and validation gates
remain unchanged.

**Owners:** existing `core/video/domain/VideoAssembly.kt`,
`core/video/application/VideoAssemblyPlanner.kt`; current `VideoBrief.kt`,
`VideoPromptCompiler.kt`, project persistence, prepared-scene contracts,
`VideoMotionRenderer.kt`, `tools/video-motion/render.cjs`, `scenery.cjs`.
Replace `VideoShotPlanner.kt` and exclusive tests only after consumer analysis.
**Focused proof:** existing `VideoAssemblyPlannerTest`,
`VideoPromptCompilerTest`, `VideoMotionDescriptorFixtureTest`,
`VideoMotionRendererTest` and Node tests. Assert exact 5,400/7,200/9,000 frames,
rigid depth motion and offscreen scenery joins, no resets/wrap/reverse/freeze,
continuous blink/particle/random state and changed-pin rejection. Subtle-motion
reuse is allowed; whole-footage repeat-to-fill and false unique-seconds totals are not.
No full-render proof is claimed by planning 9,000 frames.

## Feature VG5 — Full rendering, review and export

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| VG5-01 | Finish the smallest supervised production runner over existing jobs/media services: absolute chunks of at most 300 frames, verified completion checkpoints, state/support continuity and restart without redoing completed chunks. Retain incomplete evidence, reconcile uncertain work, enforce cancellation and per-stage/cumulative budgets; no second ledger or UI. | VG4-02 | TODO |
| VG5-02 | Verify chunk PNGs and one final numbered-image-sequence encode using the pinned FFmpeg build; validate and atomically publish a new silent MP4 plus provenance. Full decode checks 1080p, H.264, square pixels, 30 fps, exact frames/timestamps and every boundary. Cover all product durations with owned fixtures; collisions/failures preserve old output. | VG5-01 | TODO |
| VG5-03 | Run one separately admitted real combined 60-second proof with validated scene/pose-compatible masks, two reviewed non-blink activities/returns and extended Tokyo scenery. Demonstrate restart after a verified completed chunk while later work remains; measure stages/memory/aggregate disk, inspect all action/scenery/chunk joins and preserve old artifacts. | VG5-02 | TODO |
| VG5-05 | User reviews the exact 60-second combination at normal speed for character fidelity/contact, activity readability/cadence/order, quiet intervals, frond/window edges, scenery seams and repetition. Record accept or specific repairs before full production. | VG5-03 | WAITING_USER |
| VG5-06 | With full corridor coverage, reviewed scene-compatible TABI action art/mattes and a newly bounded batch admission, produce one real continuous 180-second/5,400-frame silent 1080p30 pilot through the supervised runner. Keep the cabin fixed around moving far/middle/near parallax and a pinned episode schedule of at least two reviewed non-blink activities and deliberate neutral/rest intervals across the cut; inspect action intervals, passing views and quieter intervals. If the isolated test's art/control cannot support that schedule, prepare/review additional scene-compatible motion inputs before this batch, not a camera pan or motionless hold. Fully decode/verify it, retain native/output facts and honest component reuse, and publish only to a new destination. Not an app-delivery claim. | VG4-06, VG5-05, VG5-02 | TODO |
| VG5-07 | User watches the entire three-minute Tokyo pilot and all action/scenery/chunk joins, passing views and quiet intervals, then imports/plays it in the chosen Apple editor. Record artifact-specific recipe acceptance or repairs. This unlocks second-city preparation, not VG6 completion, rights or release. | VG5-06 | WAITING_USER |
| VG5-08 | After pilot review, user selects one next city and a materially different activity script. Prepare only its needed exterior/pose inputs as new pinned references/scene/plan, retaining compatible cabin/TABI/props and first-film bytes. Validate all visible exterior apertures, depth/overlap/shutter/contact and 20–30-second coverage; validate any new motif through its own small moving proof before combination. Broader artwork/control work needs separate bounded slices/admission. | VG5-07 | TODO |
| VG5-09 | Produce one separately admitted real 20–30-second second-city reuse proof through the same project/assembly/job/media owners. Change exterior plus activity choice/order/timing, disclose holds/reuse and fully decode/check joins/resources; no city-special-case backend, identical-film reseed or first-film regeneration. | VG5-08, VG5-02 | TODO |
| VG5-10 | User reviews that exact reuse clip for distinct city/scenery, materially different activity sequence, consistent TABI/cabin/props, contact/cadence and temporal quality. Record accept or repairs before app integration. A short pass does not approve a second full corridor/film or other cities. | VG5-09 | WAITING_USER |
| VG5-04 | After pilot/reuse review and app integration, wire full-cut review, duration/coverage/reuse/estimate facts, join inspection, destination, export result and reveal. Preview/output consume the same resolved plan; changes stale readiness without deleting old cuts. | VG3-08, VG4-01, VG5-07, VG5-10 | TODO |

VG5-08/09/10 are the explicit repeatability slice, not a bulk city/pose library.
Kyoto, Madrid and Rome have no selected motion-ready city pack in the current
scenario directory. Pick one, inspect existing references and supply/separately
admit only the missing finished art. Reuse compatible assets by immutable pin,
never rebind accepted takes. The new episode brief must materially change the
ordered activities and timing/quiet intervals, not only city label or seed;
include a different activity when needed to make the story distinct. Reading,
sipping and page-turning are creative ideas, not proven current controls.
The same steps apply to later full films: complete corridor/action readiness →
new exact batch budget → render/check/import → whole-film/editor review. For this
series, VG6-05/06 delivers the selected second city as the full app-based film;
the 20–30-second reuse clip is only its early method proof. Generic video projects
remain valid. Evidence/details live in TABI/Validation, not a new episode schema.

**Production-first boundary:** VG5-01/02 are direct pilot enablers, not permission
to implement the whole app. Reuse current project/job/coordinator/media ownership,
including persisted intent, pins, native supervision and no-overwrite publication;
use a thin ignored harness, not a permanent second scheduler or rendering backend.
The first assembly route retains verified chunk PNGs and encodes one immutable
numbered sequence. The pinned FFmpeg build lacks concat support. Do not silently
substitute a PATH binary, new distribution or repeated MP4 clips. Alternative
assembly needs a separately scoped, tested change. Keep the input/source copies
and encoder staging inside the aggregate disk budget, not just one active chunk.

The old 900-second ceiling is for a whole preview attempt, not each 300-frame
invocation. A longer batch must have newly authorized finite stage and cumulative
time/storage limits; this queue update raises none. Preserve the 2-GiB native
memory safeguard and 10-GiB free-disk reserve. Stop on resource failure without
automatic retry, quality substitution, cleanup or unrelated process termination.
Completed-output recovery is already proven; VG5-03 must establish the distinct
completed-chunk restart boundary. Partial-chunk and abrupt-crash recovery may be
claimed only when actually tested. Full-pilot scope formerly included in VG5-03
is now VG5-06/07, with an intervening 60-second user decision and full-artwork gate.

**Owners:** current motion/media/job/project owners; narrowly needed
`core/video/adapter/VideoEncoder.kt` / `core/video/application/VideoExport.kt`
helpers if existing owners cannot supply the boundary, plus an ignored supervised
pilot harness. Split oversized implementation slices before admission. New
`ui/video/VideoAssemblyPanel.kt`, `VideoExportPanel.kt` and app composition belong
only to deferred VG5-04. Human decisions/evidence: TABI video and Validation.
**Focused proof:** new `VideoEncoderTest`, `VideoExportTest`, `VideoExportFlowTest`;
extend `VideoMediaHostCheck` for the full-output proof. The existing
`:videoMediaProbe -PvideoToolsDirectory=<absolute-tools-directory>` proves only its
short fixture until extended. Verify actual capabilities of the narrow pinned
FFmpeg build; a required distribution/filter change is a separate bounded slice,
not permission to use arbitrary PATH tools. Include restart parity, dropped/
duplicated frames, audio-bearing source, truncated output and late cancellation.
VG5-04 must use the approved flow; no unapproved general timeline editor.

VG5-03 prerequisite inspection (2026-09-28), not admission or completion:
`build/tabi-next-preflight.iMUH0P/coverage-result.json` measures existing authored
extents at the unchanged camera speed480/599 pixels/frame. A60-second/1,800-frame
trajectory exceeds the near strip at frame919 (~30.63s) and far strip at frame1698
(56.6s). Keeping current placement requires at least2,117 additional prepared-scale
near pixels and82 far pixels; the middle strip has sufficient horizontal extent.
Even another starting reframe cannot make both existing strips long enough. These
are lower bounds before filtering/seam margins, not full alpha/coverage admission;
canvas padding does not supply new artwork.

The measured30-second attempt took504.265s. Simple doubling gives1,008.53s versus
the existing900s whole-attempt deadline, not a fresh deadline per300-frame chunk.
This is a risk estimate, not a measured60-second time or authorization to increase
limits. Completed-output recovery is proven; partial-chunk resume is not. Finish
VG4/VG5 dependencies and obtain coherent additional scenery plus separate bounded
render admission before VG5-03. No generation, render, encode, decode, import,
production change, limit increase or commit occurred in this read-only inspection.
The corrected source bundle remains available for future newly fingerprinted jobs.

## Feature VG6 — Installed delivery and real-video acceptance

These are later **app** gates. VG5-07 approves the preliminary production recipe,
not these app clips, installed behavior or release. Preserve all existing review
requirements; run them through the integrated app after the pilot-led workflow
has been implemented. TABI-in-a-train is the user's current series; Tokyo is the
first pilot and the selected second city supplies the later complete app film.
Neither is a mandatory preset or the only scenario the eventual app supports.

| ID | Step and completion condition | Depends on | State |
| --- | --- | --- | --- |
| VG6-01 | With selected usable artwork/rights and an explicitly bounded host run, prepare three real 20–30-second app clips: base motion, contrasting motion with fixed art, replaced art with other compatible settings fixed. Record requests, pins, resources and limitations, without hidden scripts/JSON. | VG3-08 | TODO |
| VG6-02 | User reviews all three clips at normal speed against supplied appearance and requested motion; record fidelity, temporal stability, object/effect/scenery coherence and actual decision. Failed local quality may inform an optional hosted proposal, not silent fallback. | VG6-01 | WAITING_USER |
| VG6-03 | Prove private installed-app MIDI startup/export without optional tools and configured Video preview/export/cleanup. Resolve explicit Python/Node/Canvas/FFmpeg paths, package/setup strategy and notices; include motion regressions in normal validation. | VG5-04 | TODO |
| VG6-04 | Complete owned-fixture UI/end-to-end checks: import → prompt → generate → cancel/retry/reopen → full review/export, all target durations and three viewports, missing-input failures, keyboard/accessibility/performance and unchanged MIDI. Prepare current final-review evidence. | VG6-03 | TODO |
| VG6-05 | After early approach acceptance, produce a real 3–5-minute app export from selected artwork/prompt under a new bounded run. For the selected TABI series, use the second city/different action script from VG5-10 only after its complete corridor/sequence/contact readiness; the short reuse clip is not full-film proof. Retain identities/resources/rejected takes/honest reuse; generic scenarios and all product durations remain supported. | VG6-02, VG6-04, VG5-10 | TODO |
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
