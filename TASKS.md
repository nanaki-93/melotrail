# Implementation tasks

Authority: [PLAN](PLAN.md). Task status is authoritative in this queue.
Completed specifications are pruned; open task states and dependencies are unchanged.
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
- Configured implementer: Terra High (`gpt-5.6-terra`, `high`), with an initial
  attempt and at most two fresh-agent retries. If those fail, escalate to Sol High
  (`gpt-5.6-sol`, `high`) for one attempt and at most two fresh-agent retries.
  Every retry/escalation includes the full task, preserved candidate and concrete
  failure context. Keep fresh Sol High review; see the bounded policy below.
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
A manual gate stays WAITING_USER until actual evidence arrives. Per the user's
2026-09-11 decision, perform all listening scores, Logic import/play/reopen,
foreground desktop capture and MIDI visual review at the end of unpaid
MIDI engineering. The newer video request introduces an early V24 visual
checkpoint; independent engineering continues while that decision is pending. Do not admit these rows to automatic worker/retry/review runs merely
to rediscover missing evidence. Automated tests, builds and technical review still
run for every implementation; code failures retain their normal recovery policy.
Production rights, identity and spending authorization remain required before
production media use or paid generation; they do not block owned-fixture code.

MIDI engineering is complete; U07/Q01/Q02/Q03 retain their real manual gates.
The user's 2026-09-13 request retires the Swift V01–V07 workstream and its four
production gates. Their code/evidence remain historical until V30–V31 remove
exclusive runtime owners. **V10–V33 are the video queue**, including suffixed slices;
old completion does not count toward the replacement. V24/V33 require visual
feedback, and V25 is an optional hosted fallback. The configured policy below
uses Terra High plus two retries, then Sol High plus two retries, fresh Sol High
review and one local commit per validated implementation task. It supersedes
retired autopilot policies. Reuse completed ComfyUI integration; do not resume
the superseded Draw Things production-adapter contract.
Q03b's [MIDI review](docs/VALIDATION.md#final-manual-review) remains available.

The current video contract is PLAN §9, revised by the user on 2026-09-16:
**externally finished scene image + optional character/background layers + motion
prompt → complete 3–5 minute silent video**. Image generation, outfit/style
transfer and automatic inspiration synthesis are deferred. Import ready pictures,
validate their motion capabilities, generate supported video motion through the
existing local API/compositor, and export. No MIDI/audio dependency or required
TABI/Tokyo preset. The continuous-scene target, subtle-motion reuse and prohibition
on whole-clip repeat-to-fill remain. The user selected a finished scene as the
primary upload, with optional separate character/background layers.

V18b1 and V18b are OPTIONAL and unselected, not successfully completed. Their
historical evidence is unavailable; it is not an active verification dependency. V18b2 now owns ready-asset validation; V18
imports/selects finished artwork without generative preparation. V24/V33 still
need actual moving-video/user evidence. Neither accepting outside artwork nor
passing image tests closes those gates. This planning update leaves the scheduler
paused and authorizes no new inference or model setup.

The user confirmed **`docs/pictures/video/` and all its subfolders** as the
source asset collection for video work. Select suitable inputs from that tree,
record their paths/digests and preserve originals. Existing inspiration-only
labels remain meaningful: availability does not make every image a finished
scene, ready layer, licensed production input or approved moving result. Validate
actual dimensions, alpha, alignment, masks/poses and scenery coverage for each
requested motion; report missing inputs rather than invent artwork. Ordinary
technical tests use small owned fixtures; real video checks use selected supplied
assets and fresh output/evidence paths.

The unavailable V18b1 archive at
`~/.codex/melotrail-video-sequential/evidence/V18b1/updated-assets-f96a7f3`
is retired evidence, not a delivery dependency. The user approved dropping its
recovery, historical hash reconciliation and reviews requiring its missing
contents. No archive recovery or V18b1 pass is claimed. V19r2 closes the bounded
current-tree probe-isolation check; V19r3 instead validates the current revision.
Preserve legitimate local/media/ComfyUI probes and any unexpected files. Do not
search for old archives, regenerate failed image trials or delete unknown owners.
Normal-checkout architecture/build checks remain limited by restored legacy data;
preserve that data and validate an inventoried isolated candidate instead.

## Available work and TODO order

**Next dependency-ready TODO: V19r3 (fresh current-revision validation).**
No product implementation is admitted until it passes. This scope update does
not restart the scheduler, authorize inference or approve a visual/design gate.

Next actions:
1. **V19r3**: validate the current revision and task-owned diff in isolation,
   using current assets and fresh evidence. No old candidate/hash reconciliation
   or missing-archive review is required.
2. **V19**: implement controlled previews after V19r3 passes.
3. **V20a** can proceed independently after user confirmation of design scope and
   process; **V20b** requires explicit approval of the resulting flow.

All 13 TODOs are listed below. Unfinished prerequisites are shown here; the queue
retains the complete dependency lists. Order follows the earliest-ready rule,
not a requirement to wait for UI approval before independent backend work.

| TODO | Implementation scope | Unfinished prerequisites |
| --- | --- | --- |
| `V19r3` | Fresh current-revision isolated validation and review | None |
| `V19` | Controlled previews and immutable takes | V19r3 |
| `V20` | Video tab and independent create/open | V19r3, V20b |
| `V21` | Finished-scene upload and motion setup | V20 |
| `V22` | Generation, take selection, cancellation and retry | V19, V20, V21 |
| `V23` | Actual moving-video playback | V19, V22 |
| `V26` | Continuous 180/240/300-second scene planning | V19 |
| `V27` | Bounded full-length encoding and silent MP4 validation | V19, V26 |
| `V28` | In-app full-cut review and export | V23, V26, V27 |
| `V29` | Installed-app proof and optional-runtime isolation | V20, V23, V28 |
| `V30` | Remove MIDI soundtrack/companion handoff | V28, V29 |
| `V31` | Delete Swift companion and replace launch wiring | V29, V30 |
| `V32` | Complete UI tests and final review packet | V22, V23, V28, V31 |

Human gates remain U07/Q01/Q02/Q03, V20a/V20b and V24/V33. V24 does not
block independent assembly/packaging work; V33 requires its actual decision.
V18b1/V18b/V25 remain OPTIONAL and unselected, outside the available work.

## Queue

| ID | Task | Depends on | State | Result / implementation commit |
| --- | --- | --- | --- | --- |
| U07 | Prove visuals, accessibility and responsiveness | U07b, Q03b | WAITING_USER | Final manual review deferred by user until engineering ends. Q03b reconciles current six-page evidence; foreground compositor capture and genuine visual scores remain required, including the recorded capture limitation. Prior failed candidate preserved. |
| Q01 | Evaluate musical improvement and fix failures | Q01a, Q03b | WAITING_USER | Final listening deferred by user until engineering ends. Five owned/licensed full songs (three unseen) and genuine scores remain required. Preserve Q01 candidate and Q01a outputs; do not retry missing scores as code failures. |
| Q02 | Run the current Logic Pro matrix | Q02a, Q03b | WAITING_USER | Final manual Logic import/play/save/reopen deferred by user until engineering ends. Q02a packages are prepared; Q03b refreshes final build identity and instructions. No Logic pass claimed. |
| Q03 | Prove clean install and obtain MIDI release decision | Q03b, U07, Q01, Q02 | WAITING_USER | Final MIDI release decision waits for the end-of-engineering manual review. No release approval inferred from automatic checks. |
| V18b1 | Prove local multi-reference image conditioning | V17, V18a2 | OPTIONAL | Deferred by user on 2026-09-16: app no longer creates picture assets. Fidelity proof failed; not DONE. Historical attempt/check reports are unauthenticated because the reported archive is unavailable (V19r2); no further retries, archive searches or failed-task commit. |
| V18b | Prove automated reference-conditioned preparation | V17, V18a, V18a2, V18b1 | OPTIONAL | Deferred generative preparation; outside current delivery and excluded unless explicitly reselected. |
| V19r2 | Record unavailable historical evidence and verify active probe isolation | — | DONE | User retired missing-archive recovery/review. Current src tree has none of the four deferred reference-image owners; build.gradle.kts has no videoReferenceImageProbe registration and retains videoLocalProbe, videoMediaProbe and comfyVideoProbe. No historical authentication or V18b1 success claimed. |
| V19r3 | Validate the current revision in an isolated checkout | V19r1, V19r2 | TODO | Fresh base/diff/asset identities and checks required; old candidate hashes, receipts and missing-archive reviews are retired admission requirements, not transferred PASS results. Use selected docs/pictures/video/ assets, owned technical fixtures and new evidence. Preserve restored local data. |
| V19 | Generate controlled previews and preserve takes | V12, V15, V16, V17, V18, V19b, V19r3 | TODO | Revised 2026-09-14; real prepared-scene pipeline through the existing job boundary; blocked until V19r3. |
| V20a | Prepare Video-flow design alignment | — | WAITING_USER | Obtain design-process/scope confirmation, then prepare a reviewable Video-workspace flow; no production UI work. |
| V20b | Obtain explicit Video-flow approval | V20a | WAITING_USER | A genuine reviewer must approve a specific artifact revision and all required Video-flow surfaces. |
| V20 | Add Video tab and independent create/open | V13, V16, V19r3, V20b | TODO | Planned 2026-09-13; not implemented; blocked until isolated admission and approved flow. |
| V21 | Wire finished-scene upload and motion setup | V14, V15, V17a, V18a, V18, V20 | TODO | Finished scene primary; optional ready layers/poses/masks, visual anchors and motion prompt; no outfit/style synthesis UI. |
| V22 | Wire generation look selection and retry | V17, V18, V19, V20, V21 | TODO | Planned 2026-09-13; not implemented. |
| V23 | Play actual generated video in the tab | V12, V19, V22 | TODO | Planned 2026-09-13; not implemented. |
| V24 | Review video generated from finished artwork | V18, V22, V23 | WAITING_USER | Three real 20–30s app clips: base motion, changed motion with same art, replacement finished art. Earlier checkpoint/steam approval preserved; image synthesis no longer a gate. |
| V25 | Add selected hosted fallback | V11, V16, V18, V19, V21 | OPTIONAL | Only after local evidence and explicit user selection. |
| V26 | Plan one continuous scene to exact duration | V15, V18a, V19, V26a | TODO | Revised 2026-09-14; replace the old primary short-shot/repeat planner after V26a. |
| V27 | Encode and validate silent MP4 | V12, V19, V26 | TODO | Planned 2026-09-13; not implemented. |
| V28 | Expose full-cut review and export | V23, V26, V27 | TODO | Planned 2026-09-13; use the approved Video flow or obtain explicit approval for uncovered/materially changed surfaces. |
| V29 | Prove installed app and runtime isolation | V20, V23, V28 | TODO | Planned 2026-09-13; not implemented. |
| V30 | Remove MIDI soundtrack companion handoff | V28, V29 | DONE | Removed the optional probe/action and handoff-only tests; preserved MIDI snapshot publication, reveal and Logic guidance. Executed out of dependency order at user request; V28/V29 remain TODO. |
| V31 | Delete Swift companion and launch wiring | V29, V30 | BLOCKED | Removed clean tracked Swift owners and launcher scripts without touching external evidence. A pre-existing edit in `companion/Sources/MelotrailTABIRegression/main.swift` and ignored `.build/` are preserved. `make video` routes to `--video`, but V20 has not implemented this option, so launch proof and full removal remain blocked. |
| V32 | Verify complete UI and prepare evidence | V22, V23, V28, V31 | TODO | Planned 2026-09-13; not implemented. |
| V33 | Accept complete prompted video and editor handoff | V24, V32 | WAITING_USER | Actual visual evidence/decision required; do not auto-admit. |

## Completed dependency index

Completed implementation specifications and per-attempt logs have been removed.
These compact rows retain task identities and dependency resolution; they are not
work to repeat. Original results/commits remain in Git history. Evidence needed
by pending gates remains in [Validation](docs/VALIDATION.md#final-manual-review)
and [TABI video](docs/TABI_VIDEO.md), with current admission notes below.

- Q03b: MIDI review packet `~/.codex/melotrail-terra/final-review-2026-09-13`,
  implementation `c20aecf583`; U07/Q01/Q02/Q03 remain pending.
- V17: measured short ComfyUI video, not full-length acceptance. Evidence:
  `~/.codex/melotrail-video-sequential/evidence/V17/production-adapter-20260914/host-4`.
- V18/V19a/V19b: finished-artwork admission and controlled subject/scenery motion
  implemented; artistic approval and application integration remain pending.
  Evidence under `~/.codex/melotrail-video-sequential/evidence/`:
  `V18/finished-artwork-20260916`, `V19a/controlled-motion-20260916`,
  `V19b/coherent-scenery-20260916`.
- V19r1: restored inventory-equivalent `.venv-worker`, `sounds`, `data/audio`;
  retained copies and `manifest.txt` at
  `~/.melotrail-preserved-local-data/2026-09-22-step-1-1/`. Preserve both copies.
  Normal-checkout architecture checks remain limited by this restored data.
- V26a: commit `0ad854c825effc74695ebb01287447970d355fc7`; checked trajectories
  support 9,000 frames while each invocation remains limited to 300 frames.
  Exact-commit isolated test/build/diff checks passed at
  `/tmp/melotrail-step21-0ad854c`; this does not close V19r3.

| ID | Task | Depends on | State | Result / implementation commit |
| --- | --- | --- | --- | --- |
| F01 | Verify baseline and real dependency boundaries | — | DONE | Retained dependency; history in Git. |
| M01 | Freeze musical baseline and comparison harness | F01 | DONE | Retained dependency; history in Git. |
| F02 | Delete legacy desktop | M01 | DONE | Retained dependency; history in Git. |
| F03 | Delete legacy application workflow | F02 | DONE | Retained dependency; history in Git. |
| F04 | Delete obsolete musical generators and model paths | F03 | DONE | Retained dependency; history in Git. |
| F05 | Delete audio/worker runtime and finish schema/build cleanup | F04 | DONE | Retained dependency; history in Git. |
| F06 | Delete verified legacy data and measure repository reduction | F05 | DONE | Retained dependency; history in Git. |
| A01 | Harden and verify bounded agent execution runner | F01 | DONE | Retained dependency; history in Git. |
| A02 | Reduce runner overhead and recover retained work | A01 | DONE | Retained dependency; history in Git. |
| A03 | Recover concrete review findings as bounded subtasks | A02 | DONE | Retained dependency; history in Git. |
| U01 | Finish verified lanes and live timeline projection | F06 | DONE | Retained dependency; history in Git. |
| U02 | Compact shell, player and inspector | U01 | DONE | Retained dependency; history in Git. |
| U03 | Refine Project and MIDI import | U02 | DONE | Retained dependency; history in Git. |
| M02 | Derive melody context and harmony-tension evidence | M01, F06 | DONE | Retained dependency; history in Git. |
| M03 | Add explicit harmony durations and source extent | M02 | DONE | Retained dependency; history in Git. |
| U04a | Expose compact chord-duration and source-end editing | U03, M03 | DONE | Retained dependency; history in Git. |
| M04a | Build bounded legal piano voicing choices | M03 | DONE | Retained dependency; history in Git. |
| M04b | Rank piano voicings against protected melody | M04a | DONE | Retained dependency; history in Git. |
| M04 | Improve piano voicing against melody | M04b | DONE | Retained dependency; history in Git. |
| M05a | Anchor comping to meter and chord windows | M04 | DONE | Retained dependency; history in Git. |
| M05 | Add phrase-aware, meter-aware comping | M05a | DONE | Retained dependency; history in Git. |
| M06a | Persist versioned arrangement-plan records | M03 | DONE | Retained dependency; history in Git. |
| M06b | Create and confirm arrangement-plan proposals | M06a | DONE | Retained dependency; history in Git. |
| M06 | Persist a deliberate whole-song arrangement plan | M06b | DONE | Retained dependency; history in Git. |
| M07a | Resolve per-occurrence generation context | M05, M06 | DONE | Retained dependency; history in Git. |
| M07b | Represent planned rests in draft and acceptance | M07a | DONE | Retained dependency; history in Git. |
| M07 | Generate drafts from plan, boundaries and explicit rests | M07b | DONE | Retained dependency; history in Git. |
| M08a | Coordinate bass support with chord and groove intent | M07 | DONE | Retained dependency; history in Git. |
| M08b | Shape drum fills and section transitions | M08a | DONE | Retained dependency; history in Git. |
| M08 | Coordinate bass/drums and section transitions | M08b | DONE | Retained dependency; history in Git. |
| M09a | Define scoped deterministic musical repair intents | M08 | DONE | Retained dependency; history in Git. |
| M09 | Add meaningful alternatives and targeted musical repair | M09a | DONE | Retained dependency; history in Git. |
| U04b | Edit sections and confirmed arrangement purpose | U04a, M06 | DONE | Retained dependency; history in Git. |
| U04 | Build compact Structure & Harmony editing | U04b | DONE | Retained dependency; history in Git. |
| U05a | Make Arrange lanes and full-draft action dominant | U04, M07 | DONE | Retained dependency; history in Git. |
| U05b | Wire bounded previews and contextual repair actions | U05a, M09 | DONE | Retained dependency; history in Git. |
| U05 | Build timeline-first Arrange with plan and repairs | U05b | DONE | Retained dependency; history in Git. |
| U06a | Finish whole-song review and atomic decisions | U05 | DONE | Retained dependency; history in Git. |
| U06 | Finish whole-song Review and Logic export handoff | U06a | DONE | Retained dependency; history in Git. |
| U07a | Pin visual comparisons and accessibility regressions | U06 | DONE | Retained dependency; history in Git. |
| U07b | Measure responsiveness and prepare visual review | U07a | DONE | Retained dependency; history in Git. |
| Q01a | Prepare frozen musical evaluation packages | U06, M09 | DONE | Retained dependency; history in Git. |
| Q02a | Generate current Logic matrix and manifests | U06, M03, M07, M08 | DONE | Retained dependency; history in Git. |
| Q03a | Prove clean native build and startup | F06, U06 | DONE | Retained dependency; history in Git. |
| Q03b | Prepare the final manual-review handoff | Q03a | DONE | Retained dependency; history in Git. |
| V10 | Align video contracts and queue guards | — | DONE | Retained dependency; history in Git. |
| V11a | Validate local profile and prepare bounded probe requests | V10 | DONE | Retained dependency; history in Git. |
| V11 | Prove one local generation workflow | V11a | DONE | Retained dependency; history in Git. |
| V12a | Supervise bounded owned media processes | V10 | DONE | Retained dependency; history in Git. |
| V12 | Prove video-only media runtime | V12a | DONE | Retained dependency; history in Git. |
| V13 | Persist independent video projects | V10 | DONE | Retained dependency; history in Git. |
| V14 | Import reference assets | V13 | DONE | Retained dependency; history in Git. |
| V15 | Compile asset prompts and shot proposal | V13, V14 | DONE | Retained dependency; history in Git. |
| V16 | Persist bounded recoverable jobs | V13 | DONE | Retained dependency; history in Git. |
| V17a | Pin ComfyUI setup and own its local server | V11, V12a | DONE | Retained dependency; history in Git. |
| V17c | Expose a verified owned ComfyUI connection | V17a | DONE | Retained dependency; history in Git. |
| V17b | Connect recoverable ComfyUI API jobs | V17a, V17c, V14, V16 | DONE | Retained dependency; history in Git. |
| V17 | Verify the selected ComfyUI adapter on this host | V17a, V17b, V12 | DONE | Retained dependency; history in Git. |
| V18a1 | Requalify desktop visuals on macOS 27 | V17 | DONE | Retained dependency; history in Git. |
| V18a | Persist prepared scenes and motion capabilities | V13, V14, V15, V18a1 | DONE | Retained dependency; history in Git. |
| V18a2 | Bind character, outfit and scenery inspiration | V14, V15, V18a | DONE | Retained dependency; history in Git. |
| V18b2 | Validate externally prepared animation assets | V14, V18a | DONE | Retained dependency; history in Git. |
| V18 | Import finished looks and prepare motion inputs | V14, V15, V16, V18a, V18b2 | DONE | Retained dependency; history in Git. |
| V19a | Generalize controlled subject motion and effects | V12, V18a | DONE | Retained dependency; history in Git. |
| V19b | Render coherent scenery with continuous time | V19a | DONE | Retained dependency; history in Git. |
| V19r1 | Restore displaced local data or authorize its retained relocation | — | DONE | Retained dependency; history in Git. |
| V26a | Remove the full-trajectory duration limit | V19b | DONE | Retained dependency; history in Git. |

## UI/UX

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
**Done:** automated gates, fresh `:desktopApp:nativeDesktopCapture` and genuine user visual decision pass. Frame replay cannot substitute for compositor evidence.
Leave WAITING_USER if review is outstanding; Q01/Q02 preparation can continue.

## Product evidence

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

### Q03 — Prove clean install and obtain MIDI release decision

**Remaining parent slice:** Reconcile acceptance evidence with the final engine/UI/export versions, update README to shipped behavior and obtain the actual MIDI release decision.
The original contract below is the overall acceptance checklist. Reuse completed
children; do not reimplement them or expand this task to the whole workstream.

**Work:** use an isolated clean checkout of the integrated result; run test/build
and native install/startup without worker, sound library or model. Walk all six
pages and the full MIDI path, verify cleanup measurements and artifact identity.
Recheck that Q01/Q02/U07 evidence applies to the final engine/UI/export versions.
Update README to shipped behavior and keep only concise limitations/evidence.
**Done:** fresh gates including `:desktopApp:nativeDesktopCapture`, native smoke, user musical/UI decision and applicable Logic
matrix pass. Record final build and user decision in Validation. Do not delete
acceptance evidence or rewrite the source to obtain a pass.

## Integrated generation from assets and a prompt — V10–V33

### Execution and acceptance contract for this workstream

Implement only the selected row after its listed dependencies are DONE. Each
row below supplies exact repository-relative target files (new paths are marked
new), inputs, rules, verification and an observable result. The coordinator owns
TASKS/status, shared-file integration and any allowed-path configuration. Agents
must not expand scope or revive V01–V07. The user has now authorized sequential
execution through the scheduler below; do not launch parallel task writers.

Every code slice runs its focused command below, then `make test`, `make build`
and `git diff --check`. Ordinary tests use owned media/fake providers and never
install a model, submit a cloud job or depend on paid credentials. Model/media
host probes are separate explicit commands, with measured evidence under ignored
`build/video/` or another retained owned directory. A missing native prerequisite
is reported as unavailable, never as a passing/skipped production proof.

Existing checkout baseline: the prior Makefile task's `make test` and `make build`
each reported 334 root tests with one failure in
`TargetArchitectureRulesTest.legacy data payloads and their application consumers
remain removed`, caused by pre-existing `.venv-worker`, `data/audio` and `sounds`.
These are unrelated untracked data; do not delete them or weaken the assertion.
Use a clean isolated checkout for implementation verification and preserve this
checkout. Current tracked Makefile/README/companion-README edits are intentional.

The revised local route is ComfyUI preparation plus controlled composition.
V17a/V17c/V17b/V17 replace the unimplemented production adapter, reusing the measured
external installation. Finished artwork now supplies the visual inputs; V18b
automatic synthesis is deferred. LTX I2V does not prove independent layer control.
Do not build both Draw Things and ComfyUI production adapters. No silent cloud
fallback. V25 stays OPTIONAL; if later selected, revise exact dependencies and
capability gaps here before activation. V24 needs real acceptable visual evidence
through the selected route; V33 depends on that decision. A failed preparation
trial blocks its dependent production path, not independent fixture/UI work.

A 3–5 minute deliverable is mandatory. A short test, image gallery, API wrapper,
manual JSON/CLI workflow or working synthetic encode cannot close V33. Show
component reuse and real chunk/scenery joins. The user explicitly allows subtle
motion reuse, not whole-clip repeat-to-fill. Build one continuous-scene flow;
do not implement the superseded unique/repeated-short-shot modes as primary UI.
Audio sync and public upload are
excluded. Required human decisions cannot be inferred from source scans or tests.

Parallel writers require an explicit future user request and dependency-ready
rows with disjoint exact file lists and no shared resource/build edits. The
coordinator serializes Gradle/build-file, app-shell, documentation, runner and
removal changes. Default to one writer and a fresh reviewer; workers do not spawn
other workers. If a row exceeds a focused pass, the coordinator splits it here
before expanding file ownership. No fixed token budget is created by this plan.

### V18b1 — Prove local multi-reference image conditioning

**Status: OPTIONAL, unselected as of 2026-09-16.** The user moved picture creation
outside the app. The requirements below describe the deferred experiment, not an
active prerequisite or permission for more inference. Preserve its failed proof.

**Target files:** `src/main/resources/video/comfyui/reference-image-api.json` (new),
`src/main/resources/video/comfyui/reference-image-profile.json` (new),
`src/test/kotlin/app/melotrail/video/VideoReferenceImageHostCheck.kt` (new),
`src/test/kotlin/app/melotrail/video/VideoReferenceImageHostCheckTest.kt` (new),
`build.gradle.kts` (opt-in probe registration only), and `docs/TABI_VIDEO.md`
(compact measured preparation capability/limitations only).
**Inputs / dependencies:** V17, V18a2. User authorized the pinned three-file
FLUX.2 klein 4B FP8/Qwen3-4B/FLUX2 VAE setup on 2026-09-16; all publisher hashes
are verified in `~/.codex/melotrail-video-sequential/evidence/V18b/klein-setup-20260916`.
**Implementation rules:** Reuse current owned ComfyUI runtime/client and media
boundaries for an opt-in image probe; no replacement production adapter or model
download on ordinary startup/tests. Pin graph, installed source/model hashes,
input bytes/roles, prompt, settings and output receipts. Use actual image slots
for character identity/pose/expression, outfit, scenery and optional style.
A bounded two-pass profile may prepare the subject then compose scenery/style;
its intermediate output and all transitive source pins must remain recorded.
Proposed limits are at most two character/pose references plus one outfit in the
subject pass, then subject output plus one scenery and one style; missing groups
and one complete-scene reference remain supported where measured. Reject extra
or unsupported references before upload/inference; do not silently discard,
collage or substitute filenames/text for conditioning. Keep original prompt exact
in the receipt and record any stage instructions separately. Validate no-overwrite
owned output paths and all source/model pins before startup; cancellation/timeout
must stop only owned inference/server work. Do not change the proven LTX profile.
The original approved twelve-attempt trial is exhausted and retained unchanged.
On 2026-09-16 the user authorized another Astra repair and a fresh trial of at
most 24 additional local image attempts using assets from `f96a7f364` and selected
charcoal/stone v6 as style. Its separate ledger covers base, changed subject,
changed scenery, outfit-only, pose/expression-only and single complete-scene
cases plus bounded diagnostics; failed attempts consume that allowance. Start at a
bounded still resolution and measure Apple MPS precision support, runtime,
pressure/swap and output quality rather than inferring them from CUDA figures.
No new models/nodes, paid requests or source-specific pixel preparation. If the
installed precision cannot run, retain the exact failure and propose the smallest
repair/setup change; do not silently substitute another model. Keep model loading
out of ordinary tests. This slice proves image reference consumption and bounded
composition only, not semantic layers, animation, UI or final artistic approval.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoReferenceImageHostCheckTest'`; real host: `./gradlew :videoReferenceImageProbe -PvideoReferenceImageRequest=/absolute/path/to/owned-request.json` (register this task), plus `make test`, `make build`, and `git diff --check`.
**Done:** six comparison cases have real image outputs and exact role bindings,
with outfit/pose/scenery changes assessed while other pins remain fixed; every
source stays byte-exact, limits/recovery/cleanup are exercised, and actual supported
capacities/limitations are recorded. A concrete failed native profile is retained
as failure evidence and does not count as a completed capability proof.

### V18b — Prove automated reference-conditioned scene preparation

**Status: OPTIONAL and unselected.** Deferred by the 2026-09-16 external-artwork
scope. It is not a dependency of V18, V19, V21 or V24. Retain earlier evidence;
do not run, download an extractor or expand the failed reference trial.
**Target files:** `src/main/resources/video/comfyui/scene-prepare-api.json` and
`scene-prepare-profile.json` (future only),
`src/test/kotlin/app/melotrail/video/VideoScenePreparationHostCheck.kt` (future),
`build.gradle.kts`, `docs/TABI_VIDEO.md`.
**Inputs / dependencies:** V17, V18a, V18a2, V18b1; a new explicit user choice to
restore image synthesis and a bounded preparation/setup specification first.
**Implementation rules:** If later reselected, define measured conditioning and
extraction slices in this queue before implementation. Earlier attempts prove
neither independent role control nor semantic extraction; do not mark this DONE
because externally supplied artwork bypasses those functions.
**Verification command:** Future reactivation must name focused and bounded native
checks before admission; no executable current task is authorized.

### V19r3 — Validate the current revision in an isolated checkout

**Scope:** Current base revision plus task-owned changes, an external isolated
candidate, selected current assets and fresh identity/check evidence. No recovery
or review of missing historical assets, candidate hashes or receipts.
**Rules:** Create a detached isolated candidate without changing user branches or
refs. Record current base, dirty-diff identity, copied task-owned untracked-file
hashes and explicit exclusions. Preserve `.venv-worker`, `sounds`, `data/audio`,
build products and workflow state in the normal checkout; exclude them from the
candidate by inventory, never deletion. Include current tracked video assets;
pin selected inputs from `docs/pictures/video/` recursively. Use small owned
fixtures for technical regressions and validate actual capability before real
asset use. Do not infer alpha, poses, scenery coverage or artistic approval from
file presence. Missing motion inputs need actionable findings, not old archives.
Do not weaken architecture tests, alter `.gitignore` or certify an older HEAD.
**Verification:** In the verified candidate run `./gradlew :test --no-build-cache --rerun-tasks --tests 'app.melotrail.architecture.TargetArchitectureRulesTest' --tests 'app.melotrail.documentation.DocumentationIntegrityTest'`,
`make test`, `make build` and `git diff --check`. Record candidate/input identities,
executed versus cached results and any normal-checkout limitation separately.
**Done:** Fresh checks and current-candidate technical review pass. No inherited
PASS, historical identity authentication or V24/V33 visual approval is claimed.

### V20a — Prepare Video-flow design alignment

**Scope:** The new Video workspace and shared application boundary only; `.mockups/flows/video-workspace/` and shared design-system assets only after explicit process permission; this row's evidence.
**Rules:** Obtain required scope/design-process confirmations before creating artifacts. Reuse `WorkspaceTheme`, `WorkstationPrimitives`, `WorkspaceShellFrame`, `docs/UI_GUIDELINE.md`, and `docs/pictures/UI/08-video-preview.png` as composition input; do not redesign six MIDI pages or treat the reference as approval. The reviewable flow must cover create/open, finished-scene import, optional ready-layer setup, prompt/duration, generation/cancel/retry/recovery, moving preview/take review, continuous assembly and silent export at supported viewports.
**Verification:** Verify navigator links, shared asset references, required states and supported viewports; run the documentation integrity test, `make test`, `make build`, and `git diff --check`.
**Done:** A reviewable artifact path and digest are recorded. Without required confirmations, remain WAITING_USER.

### V20b — Obtain explicit Video-flow approval

**Scope:** The reviewable Video-flow artifact, its shared design-system assets, and this row's approval record.
**Rules:** Obtain a genuine reviewer decision naming the approved artifact revision/digest, reviewer, date, covered surfaces and accepted deviations. Browser rendering, agent recommendation, MIDI goldens and prior TABI artistic approval are not approval. Material changes require renewed review.
**Verification:** Recompute the approved artifact digest, verify recorded paths/links, and run `git diff --check`.
**Done:** Explicit approval covers V20–V23 and planned review/export surfaces; otherwise remain WAITING_USER.

### V19 — Generate controlled previews and preserve independent takes

**Target files:** `src/main/kotlin/app/melotrail/video/application/VideoClipGeneration.kt`
(new), `src/main/kotlin/app/melotrail/video/adapter/VideoResultImport.kt` (new),
`src/main/kotlin/app/melotrail/video/adapter/VideoMotionRenderer.kt` (new),
`src/main/kotlin/app/melotrail/video/domain/VideoGenerationJob.kt`,
`src/main/kotlin/app/melotrail/video/adapter/LocalVideoBackend.kt`,
`src/test/kotlin/app/melotrail/video/VideoClipGenerationTest.kt` (new),
`src/test/kotlin/app/melotrail/video/VideoMotionRendererTest.kt` (new).
**Inputs / dependencies:** V12, V15, V16, V17, V18, V19b, V19r3.
**Implementation rules:** Generate video validates imported finished-artwork motion inputs and coordinates
bounded controlled-render requests under the existing durable job admission,
ownership and cancellation contract. Extend typed job inputs for motion frame
ranges; do not create a second job ledger. The external compositor is a media
stage, not another generative provider. Resolve its pinned runtime without a
development PATH assumption; reuse V12a supervision and V12 encoding/probing.
Preserve actual look/component review status and exact reference/motion settings.
Decode/probe actual outputs before they are reviewable, retain native/upscaled
resolution and cadence, and strip any incidental source audio. Import new takes
immutably; reject stale/malformed results without replacing selections. Support
5-second and 20–30 second previews using the same renderer used for full output.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoClipGenerationTest' --tests 'app.melotrail.video.VideoMotionRendererTest'`; `node --test tools/video-motion/render.test.cjs tools/video-motion/scenery.test.cjs`.
**Done:** an actual prepared-scene preview can be generated, reopened, cancelled,
rejected and replaced through production services without manual scripts or lost work.

### V20 — Add the Video tab and independent create/open flow

**Target files:** `desktopApp/src/main/kotlin/app/melotrail/desktop/DesktopMain.kt`,
`desktopApp/src/main/kotlin/app/melotrail/desktop/MidiCoreDesktopComposition.kt`,
`desktopApp/src/main/kotlin/app/melotrail/desktop/MelotrailAppShell.kt` (new),
`desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoDesktopComposition.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoWorkspace.kt`
(new), `desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoWorkspaceTest.kt`
(new), `desktopApp/src/test/kotlin/app/melotrail/desktop/MelotrailAppShellTest.kt`
(new), `desktopApp/src/test/kotlin/app/melotrail/desktop/MidiCoreDesktopCompositionTest.kt`.
**Inputs / dependencies:** V13, V16, V19r3, V20b.
**Implementation rules:** One window, app-level MIDI/Video tabs above the existing
MIDI shell; preserve its six destinations and one MIDI player. Video create/open
is available with no MIDI project. Keep its state when switching tabs/projects;
load video dependencies lazily. Entering Video pauses MIDI, retaining position.
Parse an explicit `--video` startup option without breaking native startup-check
arguments; render a real independent empty/setup state. Close video-owned work
cleanly while preserving persisted jobs. No Swift launch.
**Verification command:** `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.MelotrailAppShellTest' --tests 'app.melotrail.desktop.video.VideoWorkspaceTest' --tests 'app.melotrail.desktop.MidiCoreDesktopCompositionTest'`.
**Done:** `./gradlew :desktopApp:run --args='--video'` opens the tab directly;
MIDI still works when every optional video tool is absent.

### V21 — Wire finished-scene upload, motion brief and setup

**Target files:** `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoAssetsPanel.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoBriefPanel.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoSetupPanel.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoWorkspace.kt`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoInputFlowTest.kt` (new).
**Inputs / dependencies:** V14, V15, V17a, V18a, V18, V20.
**Implementation rules:** Primary chooser/drag-drop accepts a finished PNG/JPEG
scene, with optional ready Character/poses, Background/scenery and advanced
foreground/mask inputs. These are exact assets, not inspiration or Outfit-transfer
slots. Show thumbnails, selection/replacement/removal, dimensions/alpha and exact
consumed inputs. Retain optional context without claiming it conditions a job.
Offer visible layer placement, anchor and motion setup only when relevant; no
hand-edited JSON, source coordinates or hidden manual script. Missing assets or
coverage explain which externally prepared picture is needed. Preserve unchanged
layers and earlier selections across replacement, cancellation and reopen.
Provide exact free-form motion prompt, supported camera/character/effect/scenery
controls, duration 3–5 minutes and a short-preview action. Do not expose Generate
look or automatic outfit/style synthesis. Distinguish advice from enforced
controls and one-image I2V from measured controlled motion. Explicit setup uses
V17a's pinned video runtime; no image-model prerequisite or automatic download.
**Verification command:** `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.video.VideoInputFlowTest'`.
**Done:** finished scene plus optional ready layers and motion settings can be
supplied in-app at all supported sizes; wrong/missing motion inputs fail visibly
without losing source selections or implying a complete generation capability.

### V22 — Wire look selection, generation and retry controls

**Target files:** `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoGenerationPanel.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoTakeGallery.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoWorkspace.kt`,
`desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoDesktopComposition.kt`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoGenerationFlowTest.kt`
(new).
**Inputs / dependencies:** V17, V18, V19, V20, V21. If the local route was
rejected and the user selected hosted generation, the coordinator replaces V17
with V25 in this row and its queue entry before admission. No fake-only completion.
**Implementation rules:** Connect the primary Generate video action from assets
and a prompt through production preparation and controlled rendering. Keep imported-look selection and per-component controls in optional refinements; no storyboard or keyframe
approval is required for a draft. Wire Cancel, Retry and Keep/reject take controls. Show
preparation/render stages, selected references, local estimate or hosted quote, actual job states
and errors. Concrete backend is supplied by V17 or V25; fake-only wiring cannot
be presented as completed integration. Handle tab changes, restart, missing tools,
late results and duplicate clicks without lost selections or duplicate jobs.
**Verification command:** `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.video.VideoGenerationFlowTest'`.
**Done:** GUI actions dispatch through production use cases and expose real results;
no API/CLI-only delivery, fictional percentages or synthetic production previews.

### V23 — Play actual generated video inside the tab

**Target files:** `src/main/kotlin/app/melotrail/video/adapter/VideoPreviewDecoder.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoPreview.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoTakeGallery.kt`,
`src/test/kotlin/app/melotrail/video/VideoPreviewDecoderTest.kt` (new),
`desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoPreviewTest.kt` (new).
**Inputs / dependencies:** V12, V19, V22.
**Implementation rules:** Decode actual file frames through the proven media
boundary with bounded buffers and off-UI-thread work. Play/pause/seek/frame-step
and resize accurately; show unavailable/corrupt-file states. Measure actual frame
progress, timing, CPU/memory and process cleanup; no preview made from a still.
Suppress all source audio. Retain one preview session, stop it on tab departure,
and never interfere with MIDI state or start another MIDI player.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoPreviewDecoderTest'`; `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.video.VideoPreviewTest'`.
**Done:** moving frames, seeking and clean teardown are proved with owned media
and an actual app-window capture.

### V24 — Review real asset-and-prompt generation before claiming product fit

**Target files:** `docs/TABI_VIDEO.md`, `docs/VALIDATION.md` (compact evidence and
actual decision only; generated media stays outside tracked documentation).
**Inputs / dependencies:** V18, V22, V23. One working route from V17 or explicitly
chosen V25; user-selected source references, local setup and, only for cloud, a concrete
authorized budget. WAITING_USER until real evidence/feedback exists.
**Implementation rules:** From the app import externally finished artwork and
optional ready layers, then enter a motion prompt. Produce three real 20–30 second
clips: base motion, a contrasting motion prompt with the same art, and replacement
finished scene/layer with other compatible settings held fixed. Preserve supplied
appearance and validate requested motion; no generated look or outfit-transfer
comparison is required. Ready cutouts/poses/masks are allowed; use the primary UI
for placement/anchors with no hidden source edits or hand-authored JSON.
Include supported character action, source-anchored effects where requested,
scenery coherence and absence of obvious reset/repetition in the review. The
approved steam and five-second checkpoint are scoped comparison references,
not this gate's decision. No predefined location,
prop, action or motion is required. Compare reference fidelity and prompt
adherence as well as actual motion/temporal quality. Run the
PLAN §9.7 checklist at normal speed; retain source/request/result identities,
measured local resources or billed cost, and actual user feedback. Do not mark
pass from mocks, provider marketing or file decodability. If local fails, record
specific reasons and propose a small capped Runway comparison; user selection
can activate V25 while this gate remains waiting. Avoid repeatedly re-running a
missing-input gate. Independent assembly/packaging tasks remain runnable.
**Verification command:** `./gradlew :desktopApp:run --args='--video'`; manual
review of the three generated clips against PLAN §9.7.
**Done:** the user accepts reference fidelity and prompt-driven generation through
one actual backend; otherwise keep the gate open with concrete failed criteria.

### V25 — Add the explicitly chosen hosted fallback (optional)

**Target files:** `src/main/kotlin/app/melotrail/video/adapter/RunwayVideoBackend.kt`
(new), `src/main/kotlin/app/melotrail/video/adapter/VideoProviderCredentials.kt`
(new), `src/test/kotlin/app/melotrail/video/RunwayVideoBackendTest.kt` (new),
`desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoSetupPanel.kt`,
`desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoDesktopComposition.kt`,
`docs/TABI_VIDEO.md` (selected hosted profile only).
**Inputs / dependencies:** V11, V16, V18, V19, V21. OPTIONAL: activate only after
local evidence and explicit user selection. No dependency on V24 completion.
**Implementation rules:** Reverify official Runway endpoints/models/limits/terms/
prices. Support reference-conditioned still generation and image-to-video through
one backend; typed capabilities, narrow uploads, bounded downloads, digest pinning,
429/backoff, auth expiry, retention, cancellation and ambiguous-submit handling.
Use secure process configuration/credential storage; no secrets in project files,
logs or snapshots. Show mode, upload disclosure and reviewable capped quote.
Fake HTTP regressions are unpaid; a live request requires the concrete pilot
budget. Manual provider-site downloads cannot substitute for the app workflow.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.RunwayVideoBackendTest'`; `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.video.VideoGenerationFlowTest'`.
**Done:** the selected hosted service works through the existing tab and job port,
with no automatic local-to-cloud fallback. If local passes, leave this task OPTIONAL.

### V26 — Plan one continuous scene to exact duration

**Target files:** `src/main/kotlin/app/melotrail/video/domain/VideoAssembly.kt`
(new), `src/main/kotlin/app/melotrail/video/application/VideoAssemblyPlanner.kt`
(new), `src/main/kotlin/app/melotrail/video/domain/VideoBrief.kt`,
`src/main/kotlin/app/melotrail/video/application/VideoPromptCompiler.kt`,
`src/main/kotlin/app/melotrail/video/application/VideoShotPlanner.kt` (retire),
`src/test/kotlin/app/melotrail/video/VideoShotPlannerTest.kt` (retire),
`src/test/kotlin/app/melotrail/video/VideoPromptCompilerTest.kt`,
`src/test/kotlin/app/melotrail/video/VideoAssemblyPlannerTest.kt` (new).
**Inputs / dependencies:** V15, V18a, V19, V26a.
**Implementation rules:** Replace the old primary unique/reused-short-shot
proposal with one prepared-scene timeline: exact frame count, a global motion
clock/seed, bounded chunk ranges, occasional supported actions and the selected
component-reuse policy. Remove exclusive retired planner fields/callers/tests
only after replacing their useful prompt/fingerprint/estimate behavior. No
parallel compatibility planner or multi-scene editor. Resolve scenery coverage
against the full camera path; gaps request more externally prepared scenery rather than a hidden
loop/freeze. Preserve exact state across chunk boundaries and fingerprint every
consumed layer, pose, motion setting and workflow. Changes invalidate only affected
work. Separate fresh action footage, procedural motion and repeated components
without double-counting overlapping layer durations or calling them unique AI
seconds. Reusing a subtle motion never authorizes a whole-clip repeat. Produce
exact 180/240/300s plans at 30 fps, including any trimmed boundary support frames.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoAssemblyPlannerTest' --tests 'app.melotrail.video.VideoPromptCompilerTest'`.
**Done:** complete plans have correct durations and validated coverage; missing
assets, reset state and unselected whole-footage reuse cannot yield a ready plan.

### V27 — Encode and validate silent MP4 outputs

**Target files:** `src/main/kotlin/app/melotrail/video/application/VideoExport.kt`
(new), `src/main/kotlin/app/melotrail/video/adapter/VideoEncoder.kt` (new),
`src/test/kotlin/app/melotrail/video/VideoExportTest.kt` (new),
`src/test/kotlin/app/melotrail/video/VideoMediaHostCheck.kt`.
**Inputs / dependencies:** V12, V19, V26.
**Implementation rules:** Same resolved assembly for review/output; normalize
explicitly to 1920×1080 H.264, square pixels and 30 fps, with
zero audio streams. Record native/upscaled resolution; strip model audio rather
than adding an audio editor. Full decode, cadence/duration/stream verification,
first/last/join-frame checks, cancellable bounded encode and no-overwrite atomic
publication with adjacent provenance. Preserve accepted clips and earlier exports.
Write only into owned staging/new output paths; malformed inputs never count
as a successful export.
Render/encode sequential chunks from V26's absolute frame ranges with bounded
memory/disk, checkpoint and resume. Preserve temporal state and continuous timestamps;
never accumulate the full frame batch in RAM. Run a real 60-second continuity/
resource check before the full 180–300 second owned encode and update the runtime
estimate from measured data. Full decode includes chunk boundaries and scenery
section joins. Do not silently change cadence or assume the narrow pinned FFmpeg
distribution supports untested filters/encoders; extend it only in a separately
scoped queue slice if the actual pipeline requires that.
**Verification command:** `./gradlew :test --tests 'app.melotrail.video.VideoExportTest'`; `./gradlew :videoMediaProbe -PvideoToolsDirectory=/absolute/path/to/tools`.
**Done:** a complete owned 180–300s assembly decodes at the exact target duration
with no audio, and cancellation/collisions preserve prior outputs.

### V28 — Expose full-cut review and export in the application

**Target files:** `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoAssemblyPanel.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoExportPanel.kt`
(new), `desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoWorkspace.kt`,
`desktopApp/src/main/kotlin/app/melotrail/desktop/video/VideoDesktopComposition.kt`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoExportFlowTest.kt` (new).
**Inputs / dependencies:** V23, V26, V27. Use the V20b-approved flow, or obtain explicit approval for every uncovered or materially changed review/export surface before implementation.
**Implementation rules:** Expose duration, supported occasional actions, scenery
coverage, subtle-motion reuse and real preparation/render estimates for one
continuous scene. Do not require a clip-reordering timeline or reuse-to-fill mode.
Build a local silent review cut using the resolved plan and play it in the existing
preview. Surface chunk/scenery joins for inspection, then choose an output path,
export, show real result
facts and reveal in Finder. No soundtrack, MIDI or JSON prerequisite. Changing
assembly invalidates review/export readiness but preserves earlier files.
**Verification command:** `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.video.VideoExportFlowTest'`.
**Done:** the UI produces and plays a complete silent file; exporting is not a
separate terminal command.

### V29 — Prove packaged app startup and optional-runtime isolation

**Target files:** `desktopApp/build.gradle.kts`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/MidiCoreNativeInstallCheck.kt`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/MidiCoreNativeInstallCheckTest.kt`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoInstalledAppCheck.kt`
(new), `src/test/kotlin/app/melotrail/architecture/TargetArchitectureRulesTest.kt`,
`docs/VALIDATION.md` (native video evidence only).
**Inputs / dependencies:** V20, V23, V28.
**Implementation rules:** Ship the selected media distribution/setup strategy
with exact notices and required jpackage modules; models remain external and
explicitly installed. Prove clean MIDI start/export with no video tools/models/
credentials and no network. Prove Video setup, real owned playback/encode with
configured tools and cleanup on app close. Register `videoInstalledSmoke` for a
private installed copy, never the user's installed app. Extend boundary tests
without weakening existing MIDI/source protection. No development PATH or Swift
package dependency in the installed launch path.
Verify the pinned ComfyUI Python runtime and external motion tool's Node/library
runtime resolve from their explicit setup paths and stay behind the lazy video
boundary; absent setup is recoverable. Run the motion tool's focused checks in
the normal project validation path and include dependency/license notices.
**Verification command:** `./gradlew :desktopApp:videoInstalledSmoke -PvideoInstallDirectory=/absolute/path/to/new-install-evidence`; `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.MidiCoreNativeInstallCheckTest'`.
**Done:** installed-window and media evidence is tied to the actual build, and
missing optional runtime cannot break MIDI use.

### V30 — Remove the soundtrack/MIDI companion handoff

**Target files:** `src/main/kotlin/app/melotrail/application/MidiCoreExportHandoff.kt`
(delete), `desktopApp/src/main/kotlin/app/melotrail/desktop/MidiCoreCompanionLauncher.kt`
(delete), `desktopApp/src/test/kotlin/app/melotrail/desktop/MidiCoreCompanionLauncherTest.kt`
(delete), `desktopApp/src/main/kotlin/app/melotrail/desktop/MidiCoreExportPage.kt`,
`desktopApp/src/main/kotlin/app/melotrail/desktop/MidiCoreDesktopComposition.kt`,
`desktopApp/src/test/kotlin/app/melotrail/desktop/MidiCoreExportPageTest.kt`,
`src/test/kotlin/app/melotrail/application/MidiCoreMidiPackageExporterTest.kt`.
**Inputs / dependencies:** V28, V29.
**Implementation rules:** Trace and remove the optional executable probe/launch,
Open in TABI action, soundtrack guidance and exclusive tests. Keep MIDI package
validation and regression assertions that remain useful; do not change manifest
format or musical export behavior. Video is reached from the app tab without
an accepted MIDI package. Never touch saved MIDI projects/export snapshots.
**Verification command:** `./gradlew :test --tests 'app.melotrail.application.MidiCoreMidiPackageExporterTest'`; `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.MidiCoreExportPageTest'`.
**Done:** MIDI export has no Swift/handoff consumer and its original safety tests
still pass.

### V31 — Delete the Swift companion and replace its launch/check wiring

**Target files:** `companion/` (delete tracked package/source/tests/scripts/resources),
`tools/companion-check.mjs` (delete), `tools/companion-check.test.mjs` (delete),
`Makefile`, `.gitignore`, `README.md`, `docs/ARCHITECTURE.md`, `docs/TABI_VIDEO.md`,
`docs/VALIDATION.md`, `src/test/kotlin/app/melotrail/architecture/TargetArchitectureRulesTest.kt`.
**Inputs / dependencies:** V29, V30.
**Implementation rules:** Enumerate exact repository-owned companion files,
remaining consumers/symlinks and exclusions before deleting. Transfer only needed
job/process safety assertions into already implemented Kotlin tests; no retained
Swift shim/archive/migration. Preserve all supplied references, Logic evidence,
MIDI artifacts and external user media, sessions and installed applications.
Remove only verified owned generated caches after retaining needed evidence;
no broad workspace cleanup. Update make video to invoke the same app's --video
route without VIDEO_REQUEST/VIDEO_JOBS, and remove Swift build/test requirements
from active run instructions. Old evidence is historical. Installed runner config
is coordinator-owned and must drop companion checks when a future run is enabled.
**Verification command:** `make -n video`; `./gradlew :test --tests 'app.melotrail.architecture.TargetArchitectureRulesTest' --tests 'app.melotrail.documentation.DocumentationIntegrityTest'`; `rg -n 'melotrail-tabi-editor|MELOTRAIL_TABI_EXECUTABLE|companion-check|VIDEO_REQUEST' Makefile src/main desktopApp/src/main tools` (no active hits).
**Done:** no repository Swift app or runtime consumer remains, and make video
starts the integrated tab. Unrelated user files are preserved.

### V32 — Verify the complete UI path and prepare final visual evidence

**Target files:** `desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoEndToEndTest.kt`
(new), `desktopApp/src/test/kotlin/app/melotrail/desktop/video/VideoVisualTest.kt`
(new), `desktopApp/src/test/resources/visual/video/` (new reviewed technical
baselines), `docs/VALIDATION.md`, `README.md`.
**Inputs / dependencies:** V22, V23, V28, V31.
**Implementation rules:** End-to-end tests start from an empty tab, import a
finished scene with optional ready layers, enter a motion prompt and Generate
video with no image-generation/keyframe approval stage. Refine motion, cancel/retry,
restart/reopen, render continuous 180/240/300s plans and export. Use real owned
media decode/encode; fake backends are explicitly test-only. Prove no JSON/audio/
MIDI prerequisite, no image-model startup dependency and unchanged source hashes.
Test flat-image supported I2V and prepared-layer capabilities separately; missing
poses, alpha, masks, anchors or scenery must not silently downgrade requested
motion. Replace finished scene/character/scenery inputs without mutating other
selections, with role/capacity/conflict checks. Exercise visible geometry setup,
component-scoped regeneration and no whole-clip repeat-to-fill,
no unintended network in local mode and no false completion. Capture empty/setup/
ready/progress/failure/full-review/export at 1536×1024, 1280×900, 720×900 and inspect
the actual images before adding baselines. Preserve existing MIDI visual tests.
Record measured playback/UI performance and exact remaining product gates; do
not call owned fixtures a successful generation trial.
**Verification command:** `./gradlew :desktopApp:test --tests 'app.melotrail.desktop.video.VideoEndToEndTest' --tests 'app.melotrail.desktop.video.VideoVisualTest'`; `make test`; `make build`; `git diff --check`.
**Done:** technical behavior is proved through the real UI and current native
runtime, with a concrete review packet for V33.

### V33 — Accept a real complete prompted video and editor handoff

**Target files:** `docs/VALIDATION.md`, `docs/TABI_VIDEO.md`, `README.md`
(actual current result/limitations and user decision only).
**Inputs / dependencies:** V24, V32. User-selected references, selected local setup
or an explicitly authorized hosted budget; WAITING_USER until real evidence exists.
**Implementation rules:** Starting from externally finished scene artwork, optional
ready layers and a motion prompt in the app, create one matching 3–5 minute silent video. Content
and actions are determined by that prompt; Tokyo/train/coffee is only an optional
example and is not a release requirement. Record component reuse and actual
generated/composed media without false unique-footage totals,
actual generation resources/cost, rejected takes, model/tool versions and source/
output digests. The user watches the entire cut and joins, confirms reference
fidelity, prompt adherence, continuous scenery, occasional character action,
temporal coherence and acceptable repetition/quality,
then imports/plays
the export in their chosen Apple editor. Audio placement/sync and YouTube upload
are outside scope. Report every failed criterion and add only bounded corrective
work to this queue; no self-awarded artistic approval or inferred monetization.
**Verification command:** `make video`; review the final exported MP4 at normal
speed and import/play it in the user's chosen Apple editor.
**Done:** a real full-length result meets the user's visual criteria and the app
workflow is accepted, or the gate stays pending with explicit failed criteria.

## Configured automatic execution

The user authorized sequential execution on 2026-09-13 and revised the model
and retry policy on 2026-09-16. The existing Melotrail heartbeat runs as **Melotrail video sequential implementation**, attached
to the current planning/implementation conversation, with **20-minute** wakes.
It uses native collaboration agents. Do not invoke the retired Terra CLI runner,
resume its pause file, reset its retained state or start a second automation.
The old runner source remains subject to V10/V31's compatibility/removal checks;
its historical model/retry/commit policy does not control this run.

Execution checkout: `~/.codex/melotrail-video-sequential/worktree`, branch
`codex/video-generation-sequential`. This clean worktree starts from the current
tracked code and accepted PLAN/TASKS/launcher changes; unrelated untracked media
and environments remain in the normal checkout. Scheduler bookkeeping stays in
`~/.codex/melotrail-video-sequential/state.json`, outside the product repository.
This state records the current task, base, phase, agent ID, repair count, evidence
paths and commit receipt; TASKS remains the only implementation queue.

The user authorized continuous sequential execution on 2026-09-16. Keep the
coordinator active through bounded agent waits, validation, review, retry and
commit stages. After verifying each task commit and receipt, immediately recheck
usage, Git state and dependencies and admit the next eligible task in the same
execution, without waiting for a scheduled wake. Twenty-minute heartbeats are
recovery wakeups for an interrupted or idle coordinator, not delays between tasks.
Do not end merely after dispatching an agent or completing one task. Preserve
state before unavoidable interruptions and never duplicate active work on recovery.
Continue until eligible work is complete, retries are exhausted, usage requires
deferral, the user stops execution, or only external decisions remain.
Select the earliest dependency-ready TODO, excluding V24/V33,
all unselected OPTIONAL rows (including V18b1, V18b and V25) and the
completed/retired MIDI/video queues. A missing human
or setup decision blocks its own task, while later independent ready engineering
may continue immediately after checking dependencies. Never retry a WAITING_USER
row without new evidence.
Read live queue status rather than hardcoding completed IDs. One worker/reviewer
runs at a time; no concurrent task, test suite or repair. Inspect live agents,
state, Git status and recent commits before admitting work. Resume an interrupted
stage and preserve its candidate; do not repeat completed work or reset retries.

1. Launch one fresh **Terra High** agent (`gpt-5.6-terra`, reasoning `high`,
   `fork_turns: none`) with the exact row, base commit, explicit worktree path,
   authorized files and required evidence. The coordinator owns task status and
   commits; workers must not spawn other agents, commit or edit PLAN/TASKS.
2. After the writer finishes, run the row's focused checks, `make test`,
   `make build` and `git diff --check` in the execution checkout. Do not edit
   documentation or source during validation because matrix evidence fingerprints
   build inputs. Run the row's actual native/media checks when applicable.
3. Concrete implementation, test or review failures consume the current model's
   bounded attempts. Terra High gets one initial attempt and up to two retries;
   after its second retry fails, escalate the preserved task to **Sol High**
   (`gpt-5.6-sol`, reasoning `high`) for one escalation attempt and up to two
   retries. This is at most six writer attempts per task. Use a fresh agent with
   `fork_turns: none` for every retry/escalation; do not restart from a blank tree.
   Include the full original task and acceptance criteria, allowed files, base,
   preserved candidate/diff and hashes, exact failed command and exit code,
   relevant error output, review findings, previous fixes and unresolved issues
   in the new agent's context. Include evidence paths with the concrete failure
   summary, not paths alone. Persist the model tier, attempt index (1–3), agent
   identity and failure history before dispatch; retain them across wakes and
   interruptions. New findings do not reset attempts. Usage interruptions and
   missing external decisions do not consume a code retry. No automatic Astra
   escalation is configured by this policy.
4. Revalidate each changed candidate with focused checks, test/build and relevant
   host/media checks. Once checks pass, a fresh **Sol High** agent reviews the
   exact tested diff read-only against the row and PLAN. Review is separate from
   writer attempts. Concrete review defects feed the same current-tier retry
   allowance; do not reset it or give reviewers a separate implementation budget.
5. After validation and review pass, the coordinator records DONE plus real
   evidence in TASKS, stages only task-owned changes and that status update,
   inspects the staged diff and creates **one local commit** with the row ID in
   its subject. The implementation and DONE update belong to the same commit;
   there is no separate queue-status commit. Record and verify the hash in the
   external receipt before selecting another row. Do not put the future commit's
   own hash in its contents. On interruption, find an existing task commit before
   retrying; a pending commit must finish before further work.
6. Fast-forward the normal checkout on `codex/terra-live` only if its tracked
   files are clean, it remains on that branch and its HEAD is an ancestor of the
   verified task commit. Never reset, stash or discard user edits. If synchronization
   cannot proceed, retain the implementation branch and report its commit/path.

If Sol High's second retry fails, preserve the candidate and exact task/failure
context, mark the row BLOCKED without claiming completion, and pause the heartbeat
with an actionable failure report. Further attempts need new user authorization.
A transient usage interruption resumes the same stage when available; do not
treat it as a code repair or start a substitute model. Check
current account usage before admission and defer below 10% remaining in any
available relevant limit. No credit purchase/reset is authorized.

No commit is created for a failed task. On a missing external decision, preserve
any unfinished changes and inspect whether they can stay isolated before working
on an independent row; use a dedicated task worktree if necessary. Do not mix
partial work into another task's commit. When all eligible work is DONE or only
external decisions remain, notify once with completed commit hashes and pending
gates, then pause this heartbeat. V24/V33 require real user review; V25 requires
explicit provider selection and a bounded paid budget. Native model setup/download
choices remain as specified in V11; ordinary build dependency resolution and
owned-fixture tests may proceed. No public push, publication or YouTube upload.

Stay quiet while state is unchanged. Notify on a validated task commit, a new
material failure, skipped live synchronization, required input or queue completion.
Local scheduled execution requires this computer and the Codex app to be running.

## Reusable agent prompt

```text
Implement only the assigned dependency-ready TASKS row and its explicit slice.
For MIDI retain the Q03b handoff; for video follow PLAN section 9 and V10–V33.
Do not revive V01–V07. V24 is the early real-video checkpoint; V33 is final
acceptance. Never retry WAITING_USER rows merely to rediscover missing feedback.
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
Return task ID, base, candidate status, summary, changed files, tests, artifacts
and the precise blocker when present. Reviews identify concrete findings with
file/line references and verification needed to resolve them. WAITING_USER describes an actual missing human decision.
Repairs receive the same task, current diff and concrete errors, and remain scoped.
Never read model transcripts or recursively search execution directories; use
only the bounded current evidence packet and its named failed check logs.
```
