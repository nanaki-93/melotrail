# Melotrail feature plan

This is the only product roadmap. [TASKS.md](TASKS.md) contains the executable
steps, dependencies and current status. This clean baseline replaces the previous
planning queues and execution history; it does not reset working software,
acceptance evidence, Git history or user data.

## 1. Two independent product outcomes

### Audio composition: protected melody to a complete MIDI arrangement

Melotrail helps a musician arrange an existing melody with Chords, Bass and Drums.
The musician confirms the musical authority, hears a full draft, repairs specific
passages and exports accepted MIDI. **Logic Pro owns instruments, audio rendering,
mixing, mastering and the finished soundtrack.** Audio import/transcription and
in-app audio production are not part of this delivery.

```text
Create/open → import melody → confirm settings, structure and chord durations
→ confirm arrangement plan → generate/listen → repair → Use → export MIDI
→ finish the sound in Logic Pro
```

### Video generation: finished artwork and motion prompt to silent video

An independent Video workspace accepts externally finished scene artwork and
optional ready layers. It generates supported motion and delivers one continuous
**180–300 second video, default 240 seconds**, as **1920×1080 H.264 MP4, 30 fps,
square pixels and zero audio streams**. Music is added in the user's external
Apple editor; no MIDI project, soundtrack or export is required.

```text
Create/open Video project → import finished artwork → enter motion prompt
→ check capabilities/setup → preview → refine → render full scene → review/export
→ add finished music in an external editor
```

Neither workstream waits for the other's artistic acceptance. They share one
Kotlin/Compose application, not projects, musical timing or generation state.

## 2. Current implementation baseline

The repository was inspected at `32cc13746`, including the existing uncommitted
video work. These are code observations, not new release or artistic approvals.

| Area | Present in the current tree | Remaining gap |
| --- | --- | --- |
| MIDI intake and authority | Protected SMF import, explicit chord windows, source-end padding, section/plan confirmation and confined storage | Revalidate the current build and fix only reproduced failures |
| Arrangement | Melody/harmony analysis, bounded piano voicing/comping, authored 4/4, 3/4 and 6/8 patterns, coordinated roles, plan/rest/boundary fingerprints | Real full-song musical improvement remains unscored |
| MIDI workspace | Six pages, verified note lanes, style preview, full-draft playback, targeted repair, atomic Use/Undo and one player | Current foreground capture, usability and listening decisions |
| MIDI export | Immutable accepted-only complete/role files, semantic re-import and evaluation/Logic preparation commands | Current human Logic import/play/save/reopen and release decision |
| Video assets/runtime | Independent project/asset/prepared-scene/job stores, ready-artwork admission, owned ComfyUI API/runtime and pinned media supervision | Application integration and current end-to-end proof |
| Controlled motion | `VideoMotionRenderer.kt` and `tools/video-motion/` render bounded absolute-frame ranges; trajectories support up to 9,000 frames, invocations up to 300 | Durable preview orchestration, encoding/publication and restartable full output |
| Preview work in progress | Local changes include `VideoClipGeneration.kt`, `VideoResultImport.kt`, media facts/store changes and tests | Preserve and review these changes; passing focused tests alone does not complete the production path |
| Video UI and full output | No `desktop/video` workspace, continuous assembly/export service or in-app moving preview | Implement these features; `make video` passes an unsupported `--video` option today |
| Removed runtime | No active audio-production/worker or Swift companion application | Do not rebuild them; preserve external evidence and unrelated local data |

Important planning corrections:

- MIDI needs verification and acceptance, not another importer, generator rewrite
  or six-page redesign. The reported **5/10** remains the qualitative baseline.
- ComfyUI accepts one composed image for the measured I2V route. It does not prove
  independently controllable layers, complex actions or four-minute coherence.
- `VideoShotPlanner` still models short unique/repeated shots. Replace its primary
  flow with a continuous-scene plan; do not expose whole-clip repeat-to-fill.
- ComfyUI and the controlled compositor are different execution stages. The
  existing local backend rejects controlled-motion requests; the durable media
  bridge must be completed rather than merely advertising a capability.
- Asset presence is not proof of alpha, pose alignment, scenery coverage, rights
  or approval. Inspect selected inputs from `docs/pictures/video/` recursively.
- Old build receipts do not certify the current dirty tree. Recheck the selected
  candidate without deleting environments, media, caches or user projects.

## 3. Product and safety rules

1. Preserve original MIDI bytes and protected melody events. Confirmed tempo,
   meter, key, structure and exact chord durations remain authoritative.
   Chromatic harmony is valid; analysis and suggestions never auto-confirm.
2. Generate deterministically from the same inputs, settings, versions and seed.
   Fingerprint every consumed plan, upstream, neighboring and repeated-section
   dependency. Invalidate only the declared affected scopes.
3. Draft playback is allowed before acceptance; MIDI export is accepted-only.
   Missing/failed work is not a planned rest. Candidates, source files and export
   snapshots are immutable; Use/Undo are atomic reference changes.
4. Keep Project, MIDI, Structure & Harmony, Arrange, Review and Export, with one
   persistent MIDI player. A top-level MIDI/Video switch is not a seventh MIDI page.
5. Video domain/application/adapters stay under `app.melotrail.video`; presentation
   stays under `desktop.video`. Construct optional runtimes lazily. Missing tools,
   models, credentials or network must not prevent MIDI startup, audition/export.
6. Video projects and job/output storage are outside MIDI roots, including symlink
   aliases. Preserve imported artwork and prior takes/exports; reject unsupported
   schemas before writes. No old-project migration or compatibility pipeline.
7. Try the selected local ComfyUI and controlled-motion path first. No automatic
   downloads, new models, cloud fallback, uploads or paid jobs. Hosted use needs
   explicit provider selection, disclosed inputs and a bounded authorized budget.
8. Persist job intent before execution; reconcile uncertain submissions, bound
   retries/resources/concurrency and cancel only owned work. Unknown progress,
   cost and capability stay unknown. AI seeds do not guarantee identical footage.
9. Tests establish integrity, not musical quality, artistic approval, rights or
   production readiness. Human decisions must identify the reviewed artifact/build.

Detailed ownership: [Architecture](docs/ARCHITECTURE.md),
[MIDI contract](docs/MIDI_CONTRACT.md), [UI guideline](docs/UI_GUIDELINE.md),
[Validation](docs/VALIDATION.md) and [TABI video](docs/TABI_VIDEO.md).

## 4. Shared foundation

### Feature CORE — Reproducible development baseline

1. Select the exact working candidate, preserving existing tracked and untracked
   changes. Separate task-owned video WIP from unrelated local environments/media.
2. Verify Kotlin 2.2.21/JDK 21, current architecture/documentation tests, focused
   feature tests, `make test`, `make build` and motion-tool tests.
3. Record actual current failures and their owners. Use an inventoried isolated
   checkout when necessary; never weaken a guard or delete data to obtain a pass.

**Exit:** an identified, reproducible baseline and a bounded next task. No new
scheduler, inference run, commit, installation or cleanup is authorized by this plan.

## 5. Audio composition features

### Feature AC1 — Protected melody and musical authority

**Reuse:** MIDI import, project lifecycle, extent, structure and harmony services.

1. Verify supported SMF 0/1, one note-bearing track/channel and fixed tempo/meter;
   explain unsupported files without mutation.
2. Verify source note end versus file end, explicitly confirmed trailing padding,
   exact section totals and unequal/sub-bar chord durations at source PPQ.
3. Verify draft edits, affected-scope previews, confirmation, locks and reopening.
   Reordering accompaniment sections must not reorder the melody.

**Exit:** the musician can establish exact authority safely. Unsupported meters
remain valid authority, but generation outside authored 4/4, 3/4 and 6/8 is rejected
explicitly. This feature is implemented; the queue verifies it and scopes defects.

### Feature AC2 — Coherent whole-song arrangement

**Reuse:** melody context, arrangement-plan proposals and Chords → Bass → Drums.

1. Verify read-only overlap/sustain/register evidence and advisory harmony tension.
2. Confirm purpose, energy, role activity, groove, phrase/repeat and boundary intent.
3. Generate complete drafts with bounded melody-aware piano, metrical comping,
   coordinated bass/drums, intentional rests and endings.
4. Recheck deterministic replay, neighboring dependency invalidation and exact retry.

**Exit:** every required scope has a valid candidate or explicit rest, with source
and harmony unchanged. Full-song quality is assessed in AC5, not inferred here.

### Feature AC3 — Listen, repair and accept

**Reuse:** style preview, persistent transport, repair alternatives and batch Use.

1. Audition the melody, style and complete draft through the same player, before Use.
2. Compare scoped repairs at the same bar position with the protected melody:
   more melody space, simpler/lower piano, smoother transitions, less bass movement
   and calmer drums.
3. Keep at most three meaningful alternatives or one coherent dependency-spanning
   repair set. Cancel/retry without losing accepted work; apply settings only once.
4. Use the chosen draft/repair atomically, Undo safely and reopen unchanged artifacts.

**Exit:** the existing first-draft path takes at most three primary actions after
musical authority is ready; targeted repair never becomes a hidden whole-song edit.

### Feature AC4 — Accepted MIDI export and Logic handoff

1. Revalidate current accepted-only complete-song and aligned role MIDI exports,
   deliberate role omissions, common origin/end, expression and manifest hashes.
2. Prepare fresh current-build Logic packages using the existing matrix command.
3. Have the user import complete/separate files at song start, assign instruments,
   play, save, close and reopen in the recorded Logic/macOS versions.

**Exit:** technical checks and the applicable real Logic matrix pass. No render,
mixer, sound-library, soundtrack or video handoff enters the MIDI export page.

### Feature AC5 — Musical usefulness and MIDI release

1. Obtain five owned/licensed full-song projects, at least three unseen; freeze
   inputs/settings before final generation using the existing evaluation harness.
2. Compare piano+melody and complete arrangements with identical instrument mapping;
   record real scores, bad bars, repair count/time and failed results.
3. Refresh six-page visual/performance evidence and actual foreground captures;
   obtain usability approval rather than treating technical goldens as sign-off.
4. Prove clean native install/startup, then reconcile evidence with final versions
   and obtain a separate MIDI release decision.

**Targets:** median overall and piano/melody-fit ≥8/10; each song ≥7/10 on both;
no core-role/interaction/structure score below 6/10; no severe unresolved fault;
zero protected melody changes; median draft-to-Use review ≤10 minutes. Use the
full procedure and performance/visual targets in Validation. Failed cases create
small corrective tasks, not lowered thresholds. Listening, Logic and visual
review remain end-of-engineering human gates; preparation can proceed now.

## 6. Video generation features

### Feature VG1 — Finished artwork and explicit local setup

**Reuse:** independent project/asset stores, scene looks, ready-asset validation,
prompt compilation and pinned local runtime/media setup.

1. Import one finished PNG/JPEG scene; optionally add transparent character/pose
   layers, clean backgrounds, extended scenery, foregrounds, masks and anchors.
2. Validate actual decoded content, alpha, geometry, occlusion, pose alignment and
   motion capabilities. Preserve originals and show exactly what a job consumes.
3. Keep motion prompts exact; distinguish implemented controls from advisory text.
   Changed appearance requires replacement artwork, not in-app restyling.
4. Inspect explicit ComfyUI, Node/Canvas and FFmpeg paths/versions/terms. Missing
   setup and missing artwork are separate actionable states, not download triggers.

**Exit:** requests either have usable, pinned motion inputs or explain exactly what
is missing. A flat scene can enter I2V without falsely claiming regional control.

### Feature VG2 — Recoverable preview generation and immutable takes

1. Review and finish the existing preview WIP. Bridge the controlled compositor
   into the single durable job system; keep ComfyUI I2V as its separate local stage.
2. Bind scene/control/runtime pins and absolute frame ranges to attempts. Handle
   duplicate clicks, cancellation, late results and restart without duplicate work.
3. Render/probe actual output, strip incidental audio and import a new immutable
   take with measured native/output geometry and cadence. Never auto-select it.
4. Produce five-second and 20–30-second previews through the production use cases.
   Review/selection is optional refinement; unreviewed does not mean approved.

**Exit:** real moving previews can be generated, reopened, rejected and replaced
without scripts or a second job ledger. Do not call a camera pan character action.

### Feature VG3 — Independent Video workspace

1. Confirm design scope/process and approve a feature-level flow mockup before
   production UI. Reuse the existing theme/primitives and reference 08; no MIDI
   redesign. Cover setup, create/open, inputs, jobs, review and export together.
2. Add the top-level switch and `--video` route. Video works from an empty launch;
   entering it pauses MIDI with position retained, without creating another player.
3. Expose finished-artwork import, visible layer/anchor setup, motion prompt,
   duration and missing-capability guidance. No JSON, code coordinates or node editor.
4. Wire Generate/Cancel/Retry/recovery and take selection to production services.
5. Decode actual frames off the UI thread with bounded buffering; support
   play/pause/seek/frame-step, one silent preview session and predictable teardown.

**Exit:** a keyboard-accessible app flow at 1536×1024, 1280×900 and 720×900; no
synthetic preview, fictional progress, soundtrack prerequisite or image-generation UI.

### Feature VG4 — One continuous scene, exact duration

1. Replace the primary short-shot planner with a versioned continuous motion plan:
   exact frames, shared clock/seed, component reuse, supported occasional actions
   and bounded chunks. Retain useful prompt/fingerprint/estimate behavior once.
2. Validate externally prepared scenery against the complete camera trajectory;
   derive rigid depth-layer movement and occlusion from that trajectory.
3. Join scenery outside the visible region and carry subject/effect/scenery state
   across chunks. Preserve blink phase, particle age and random sequence on resume.
4. Recompute only dependent work when inputs change. Report fresh action footage,
   procedural motion and reused components without double-counting layered time.

**Exit:** exact 5,400/7,200/9,000-frame plans for 180/240/300 seconds at 30 fps,
with no hidden gaps, morphing buildings, visible wraps, reverse-to-fill, freezes
or whole-clip loops. Missing coverage requests more external artwork.

### Feature VG5 — Full-length rendering, review and silent export

1. Render/encode bounded chunks from the resolved plan, with durable checkpoints,
   temporal support frames where needed and exact trimming/continuous timestamps.
2. Validate the full silent H.264 output: decode, stream count, codec, square pixels,
   dimensions, cadence, duration and first/last/chunk/scenery-join frames.
3. Measure a 60-second continuity/resource run before a complete 180–300-second
   owned-fixture export. Estimate setup, action generation, composition and encoding
   separately; never hold all 1080p frames in RAM or extrapolate artistic quality.
4. Expose the same full-cut plan in the existing preview, highlight joins and export
   atomically to a new filename with credential-free provenance and Finder reveal.

**Exit:** the app reviews and exports a complete validated file. Changing the plan
invalidates readiness, not previous outputs. Native versus upscaled resolution
and any action-cadence conversion are disclosed; duplicated frames are not native motion.

### Feature VG6 — Installed delivery and real-video acceptance

1. Early check: produce three real 20–30-second app clips—base motion, changed motion
   with the same artwork, and replacement artwork with other compatible inputs fixed.
   Obtain actual user feedback on appearance, requested motion and temporal quality.
2. Independently finish fixture-based assembly, packaging, accessibility and UI
   regressions while that decision is pending. Prove MIDI works without any optional
   video runtime; prove configured Video works in a private installed application.
3. After the early approach is accepted, produce one real 3–5-minute result through
   the app with current selected artwork and a bounded authorized local run.
4. Have the user watch the whole cut and joins at normal speed, assess continuity,
   fidelity, prompt adherence and repetition, then import/play it in the chosen
   Apple editor. Record a separate video release decision and remaining limitations.

**Exit:** actual end-to-end product evidence, not just an API wrapper or synthetic
encode. The retained five-second/steam approvals and charcoal/stone v6 reference
remain narrowly scoped; they do not approve new clips. TABI, Tokyo, trains and
coffee are examples, never mandatory content or presets.

## 7. Step-by-step delivery order

| Step | Feature delivery | What unlocks next |
| --- | --- | --- |
| 1 | CORE baseline | An identified current candidate for both workstreams |
| 2 | VG1 → VG2; request VG3 design decisions independently | Finish preserved preview WIP first; backend work need not wait for UI approval |
| 3 | AC1 → AC2 → AC3 → AC4 technical verification | Current usable MIDI workflow and fresh Logic packet; useful independent work during video/design waits, not a rebuild |
| 4 | AC5 evidence preparation | Frozen songs when supplied, current UI/performance/install evidence; human gates stay separate |
| 5 | VG3 application flow and early VG6 review | Real asset-and-prompt app clips; expose failures early |
| 6 | VG4 → VG5 | Exact continuous plans and complete silent app exports; independent of early artistic waiting |
| 7 | VG6 installed/UI proof and full real-video review | Accepted video workflow and editor handoff |
| 8 | AC5 MIDI release / VG6 video release | Independent decisions tied to the tested final versions |

This is a dependency order, not permission for parallel agents. Video backend
work can start after CORE while MIDI inputs/reviews or UI decisions are pending.
The unfinished preview work is the first video implementation priority. TASKS
owns exact admission order and all blocking dependencies; no calendar estimates
are invented before current rendering and review costs are measured.

## 8. Explicitly outside this delivery

- In-app image creation, outfit/style transfer, automatic semantic extraction,
  invented scenery and generative asset libraries.
- Whole-clip repeat-to-fill, multi-scene/general timeline editing, vertical output,
  longer compilations, LoRA training or a provider marketplace.
- Audio import/transcription, generated melody edits, unrestricted AI music,
  extra musical roles, multiple MIDI sources, tempo/meter maps or a piano-roll editor.
- Audio rendering/mixing/mastering, soundtrack synchronization and public upload.
- A second Swift application, legacy project migration, broad filesystem cleanup,
  recovery of unavailable historical experiments or rewriting Git history.

A hosted video fallback is **optional and unselected**. Only a new explicit choice
after local evidence may activate it, with current terms, capabilities, disclosures
and a capped budget. No abandoned image-generation trial is a delivery prerequisite.

## 9. Completion policy

Each feature closes through bounded tasks in TASKS. Reuse existing consumers and
tests; add regression tests for every fixed bug. Run focused checks, `make test`,
`make build` and `git diff --check`; video changes also run motion-tool checks and
applicable explicitly admitted native/media probes. Keep artifacts in ignored build
output or selected external evidence storage, not new planning/history documents.

The reset authorizes documentation only. It starts no automation, agent run,
model setup, inference, spending or implementation commit. Future runs select
current task IDs explicitly; old scheduler state cannot choose or complete them.
