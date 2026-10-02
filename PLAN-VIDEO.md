# Melotrail video feature plan

This is the only product roadmap for **video generation**.
[TASKS-VIDEO.md](TASKS-VIDEO.md) owns its executable steps, dependencies and status.
The independent MIDI/audio roadmap and queue are [PLAN-AUDIO.md](PLAN-AUDIO.md)
and [TASKS-AUDIO.md](TASKS-AUDIO.md). This documentation split preserves existing
software, task status, acceptance evidence, Git history and user data; it
starts no generation, rendering, spending or implementation run.

## Non-interactive validation

All required tests must run headlessly, without opening or driving an interactive
window. Remove live app captures/walkthroughs, native-window and GUI installer
smoke tests, and interactive Apple-editor checks from delivery gates. Retain
production-service/composition integration, offscreen UI/semantics, package
inspection and separately admitted headless render/decode/encode checks. The
Video UI remains a product requirement, not a reason to open it during tests.
Human decisions on supplied artwork, mockups and complete video artifacts remain
required; no live UI usability or editor compatibility is inferred. Preserve
historical receipts. Follow [Validation](docs/VALIDATION.md#non-interactive-validation);
this policy supersedes older interactive-test requirements in owner references.

## 1. Video product outcome

### Video generation: finished artwork and motion prompt to silent video

An independent Video workspace accepts externally finished scene artwork and
optional ready layers. It generates supported motion and delivers one continuous
**180–300 second video, default 180 seconds**, as **1920×1080 H.264 MP4, 30 fps,
square pixels and zero audio streams**. Music is added in the user's external
Apple editor; no MIDI project, soundtrack or export is required.

```text
Create/open Video project → import finished artwork → enter motion prompt
→ check capabilities/setup → preview → refine → render full scene → review/export
→ add finished music in an external editor
```

Neither workstream waits for the other's artistic acceptance. They share one
Kotlin/Compose application, not projects, musical timing or generation state.

**Production-first priority (clarified 2026-10-02):** preserve the two successful
videos' look while adding required TABI actions and at least three minutes of
Tokyo scenery. The user will double the finished segment to six minutes. The
blink-only recovery plan was too narrow and is superseded by the scope below.
Historical source recovery remains valid; wave/Blender experiments stay deferred.

### Current TABI train-series direction (2026-09-30)

The user now selects a recurring creative format: **TABI stays in the train;
each video has a different city outside the windows and a different sequence
of quiet in-cabin activities**. Tokyo is the first full pilot; Kyoto, Madrid,
Rome and other cities are future episode choices, not already prepared packs.
Reuse a validated cabin/camera, TABI identity, compatible neutral/action cutouts
and prop/occlusion geometry. Replace the exterior with separately finished,
city-specific far/middle/near scenery and author each episode's action order,
timing, quiet intervals and returns. A city name or changed seed does not
create new scenery or a new story. This is the user's series brief, not a
mandatory train/TABI preset or a restriction on generic Video projects.

### Selected route — TABI actions and a repeatable three-minute Tokyo film (2026-10-02)

**Required output:** a continuous 180-second/5,400-frame silent 1080p30 Tokyo
train film with TABI drinking, reading, gently breathing/moving the head and
watching outside. The user says the scenery should extend to **at least three
minutes**, so they can duplicate that segment into a six-minute video. Plan the
first deliverable at exactly 180 seconds and verify its repeat boundary.
These actions are required now, not optional improvements after a blink-only film.

The [30-second continuity reference](docs/pictures/video/tests/tabi-tokyo-continuity-30s-1080p.mp4)
and [20-second parallax reference](docs/pictures/video/tests/tokyo-parallax-20s-1080p.mp4)
anchor the character, train, warm illustration style, depth and scenery speed.
Preserve original media. Keep the existing local ComfyUI/controlled-motion services;
a new rig/backend is not presumed necessary. ComfyUI made the reference scenery;
Node/Canvas and FFmpeg made their blink/parallax motion and output.

1. **TABI actions:** prepare only missing scene-compatible inputs, then prove
   breathing/head/watch, drinking and reading in separate short continuous tests.
   Drink means lift the existing takeaway cup, sip and put it back; read means
   attend to the existing open notebook with visible eye/head/hand motion and a
   settled return. Keep headphones/outfit and hand–wrist–cuff–arm attachment
   consistent. Looking outside must be a character head/gaze change. Do not use
   pose cuts/crossfades, a camera pan or a static reading still as moving proof.
2. **Tokyo extension:** add matching landmarks/districts and quiet scenery for
   the entire 180 seconds at the reference speed. Reuse the existing depth planes,
   prepare new far/middle/near sections, and prove offscreen joins and the final
   return to the opening scenery. New art lives in the requested asset folders.
3. **Combine and scale:** after action and scenery proof, integrate only the
   necessary motion binding through existing video owners. Use a 60-second
   combination as an internal validation step, then deliver the full 180 seconds
   with all required actions, quiet intervals and clean prop/neutral returns.
4. **Repeat boundary:** review the end followed immediately by the start. Cup,
   notebook, hands, head, blink/breath phase, camera/depth positions and motion
   velocity must form a natural join. The user performs the 180s + 180s duplication
   externally. Do not create the initial 180 seconds by looping the old short clip.

The user authorizes creating missing TABI artwork under
`docs/pictures/video/tabi-assets/` and same-style Tokyo additions under
`docs/pictures/video/tabi-assets/scenario/`. Keep original assets; use new descriptive
filenames/subfolders. Reusable proof checks stay in the evidence owner. This is
external asset preparation, not in-app picture generation. Four initial built-in
imagegen calls produced drink/read/watch scene reference candidates and a Tokyo
Station panorama; they are still candidates, not animation-ready registered layers.

**Motion implementation limits:** the current compositor has blink, bounded
whole-subject breathing and masked planar head rotation. It has no demonstrated
continuous sip/read action; held `POSE_REPLACE` states do not supply it. Try the
selected local ComfyUI workflow first for isolated character motion where the
existing controls are insufficient. Protect the rigid cabin/scenery through valid
layers/compositing; whole-scene distortion fails. Prepare missing backing, moving
support, props and compatible masks explicitly. Prove one action before expanding
or adopting a new production motion representation. No unselected hosted fallback,
new model or resurrection of the cancelled wave is implied.

**Draft 180-second activity/scenery structure** (timings refine after motion proof):

| Time | TABI activity | Exterior direction |
| --- | --- | --- |
| 0–30s | Settle, breathe, small head motion and watch | Existing warm rooftops/Tokyo Tower direction |
| 30–60s | Reach, lift cup, sip, return cup, quiet travel | Tokyo Station/Marunouchi and connecting streets |
| 60–90s | Look down and read the table notebook | Ginza storefronts/clock-front district |
| 90–120s | Lift gaze and turn toward the window | Asakusa/Senso-ji and Sumida/Skytree views |
| 120–150s | Brief reading/drink variation with calm breathing | Ueno/Yanaka/cherry-lined neighbourhoods |
| 150–180s | Watch, return props and settle to opening state | Quiet scenery designed to reconnect to the opening |

These are illustrative content choices, not a validated geographic rail route or
already prepared scenery packs. At the old speed/placement, horizontal authored
extent lower bounds for 180 seconds are 5,467/10,173/14,420 pixels (far/middle/near),
before alpha, join/filtering and repeat-boundary margins. One new panorama cannot
establish that coverage. Do not slow, stretch or count empty padding as scenery.
The full three-minute film remains pending.
[TASKS-VIDEO](TASKS-VIDEO.md#required-actions-and-three-minute-tokyo-2026-10-02) owns execution.

### Route 1 — Rigged video proof before Melotrail integration

**Deferred on 2026-10-02.** The following is the retained earlier proposal, not
the active sequence or a request to finish the pending wave. Resume only after
an explicit future choice; the baseline route above takes precedence.

**User-selected direction (2026-10-01):** prepare one coherent reusable 2D/2.5D
character, author a small action repertoire, then translate scene descriptions
into supported action/timing/scenery instructions. Prove the process outside the
Melotrail workflow first. Blender is the installed **standalone proof candidate**;
this is not yet a permanent production-backend decision. This section supplies
the requested video plan within the sole roadmap; executable tasks remain in
[TASKS-VIDEO](TASKS-VIDEO.md#route-1--standalone-rig-proof-tasks).

```text
Existing TABI references → minimum coherent parts/hidden overlaps → reviewed rig
→ 5-second wave and human review → one additional action and review
→ description-to-action tests → 20–30-second reusable scene proof and review
→ minimal production bindings → 60-second proof → complete 180-second Tokyo film
→ different-city reuse proof → Melotrail workflow integration
```

#### Inputs and preparation

Start with `docs/pictures/video/tabi-assets/`, not a new character design:

| Existing source | Role in the proof | Remaining limitation |
| --- | --- | --- |
| `character-profile/tabi-character-profile.png` | Identity, proportions, outfit, colours and view references | Flattened reference sheet, not a layered character or rig |
| `scenario/tabi-quiet-ride-through-tokyo.png` | Original composition, atmosphere and prop reference | Flattened scene; does not reveal hidden body/cabin surfaces |
| `train-actions/28-breathing-rebuilt-cabin-review-candidate.png` and `48-matching-neutral-return-baseline-review.png` | Candidate cabin and the historically approved composed neutral baseline | Recheck selected support/occlusion; do not silently replace neutral 48 |
| `train-actions/45`–`47`, `51`–`59` and original action illustrations | Read-only appearance/gesture references and possible source pixels | Whole cutouts/independent drawings, not interchangeable articulated limb parts; their seven-pose movie is rejected |
| `scenario/tokyo-parallax-{far,middle,near}-5600x1080.png` | Candidate rigid depth-plane scenery for later combination | Width/alpha/joins must cover the chosen trajectory; not a ready full corridor |
| `emotions/`, `walking/`, `fits/`, `street-style/`, `country-outfits/` | Optional reference pool for a later selected need | No automatic new action, viewpoint, wardrobe transfer or city-motion capability |

First isolate only what the wave needs: fixed body/head/lower-contact pixels,
one consistent upper sleeve/forearm/cuff/hand assembly, shoulder/elbow/wrist
anchors, hidden joint overlaps, and exposed torso/cabin backing with foreground
occlusion. Do not cut a finished pose at visible seams and assume rotation fills
the holes. Hand opening may need a small registered drawing set; it must not
replace or redesign the whole arm. Keep one view and outfit; large turns,
walking, finger articulation, cloth simulation and automatic extraction are out
of the first proof.

Reuse existing pixels where suitable. The user permits image-generation-assisted
preparation for missing parts; identify the exact part/hidden surface first and
agree a finite call/correction budget before spending. Use Pi's image-generation
tool with the selected references, retain raw outputs and create new derivatives.
Measure actual alpha, scale and registration; review style/anatomy and the assembled
neutral before animation. Do not generate independent complete arms per keyframe,
redraw the cabin/head, infer a served model or renew exhausted historical budgets.
This is external asset preparation, not an in-app picture-generation feature.

#### Rig and description contract

Use a small textured 2D mesh/cutout rig in a fixed orthographic scene: unlit
materials, joint rotation with bounded deformation, stable UVs/texture, explicit
depth order and single-pass alpha. Do not add 3D lighting/shadows or change the
painted perspective. Author continuous motion curves with anticipation, ease,
small wrist beats and a settled return. Preserve limb length/volume and sleeve
pattern; no whole-arm swaps, crossfades, frame interpolation service or camera-pan
substitute. Pick and prove the installed renderer's actual alpha/colour behaviour;
the prior VSE still-flipping scene is not this rig. A changed neutral needs a new
explicit appearance decision, not a relaxed comparison.

A description chooses **available** actions and timing; it cannot invent artwork
or capabilities. For example, rest → wave twice → rest → a separately proved
second activity → neutral becomes absolute-frame events bound to the exact rig,
action version, allowed amplitude/speed, props and scene. First use a small explicit
vocabulary in a standalone script; preserve the original wording and show the
resolved interpretation. Reject ambiguous timing, unsupported actions/viewpoints,
missing props and conflicting channels. Agent/manual translation must be labelled
as such, not passed off as an implemented automatic prompt compiler. No new LLM,
provider or open-ended prompt-to-film promise is required for this test.

#### Proof gates and stop conditions

1. **Preparation:** source pins, missing-parts list, coherent neutral/extreme-pose
   stills and user appearance approval. Static approval is not motion approval.
2. **Five-second wave:** 150 continuously evaluated frames at 30 fps, neutral →
   lift → two small wrist beats → lower → the same neutral, with fixed cabin,
   head and lower contact. Review at normal speed and inspect joints frame by
   frame. A rig is viable only if the user accepts identity, silhouette, limb
   volume, texture, attachment, occlusion and cadence—not merely pixel tests.
3. **Reuse:** prove one additional quiet non-blink activity, then a 20–30-second
   combined scene with two different supported descriptions using unchanged
   approved rig/art. Demonstrate different timing/order without editing the rig,
   prompt rejection for missing capability, bounded scenery travel and clean
   returns. Full scenery-join/60-second coverage proof remains VG4-05.
4. **Scale:** after that short recipe is accepted, scope only the production
   contract/runner work needed for a measured, reviewed 60-second film and then
   the complete 180-second/5,400-frame Tokyo pilot. After full-film artifact review,
   prove a selected second-city script before app integration. Short success is
   not long-duration coverage, performance, artistic or app acceptance.

Every media gate uses a fresh, separately admitted bounded packet: exact inputs,
commands, output paths, per-stage/cumulative time, memory, disk, decoder traversals
and stop/retry policy. Count FFprobe frame scans and any import validation. Prove
source fidelity, actual sRGB-to-Rec.709 handling, decoded pixels and silent square-
pixel 1080p30 H.264 output separately; no blind retagging. Reuse valid lossless
frames after an encoding-only failure only under a new admission. Preserve all
failed footage and stop if the arm remains clipped/inconsistent; do not respond
by expanding a pose library, lowering checks or building the app anyway.

**Integration gate:** no Melotrail UI, app caller changes, production rig schema
or permanent backend adoption during the standalone proof. Existing
`VideoPoseSequence` is a 3–16-step held-cutout contract and cannot encode a rig by
renaming its controls. After proof/review, VG4-07/09 scope the minimum explicit
rig binding through existing video owners; later VG2-03/VG4-01/VG3/VG5 integrate the
proven recipe. Kotlin retains orchestration/storage, with lazy external rendering
and no second ledger. No MIDI dependency or mandatory TABI/train preset is added.

**Historical first task: VG2-12**, inspect/freeze the smallest kit and its bounded
preparation scope. Route selection and this plan do not launch asset generation,
rig implementation, native media, installations, paid work or commits.

## 2. Current implementation baseline

The original repository inspection was at `32cc13746`. The video baseline below
also reflects subsequent evidence in TASKS-VIDEO/TABI video: the production-first
review at `3ba34e0fb`, the train-series inspection at `a21706d02`, the
2026-10-01 rig experiment, and the 2026-10-02 recovery at HEAD `a672c6b42`
with preserved unrelated documentation/environment changes. These are observed
implementation/evidence identities, not a clean release or artistic approvals.

| Area | Present in the current tree | Remaining gap |
| --- | --- | --- |
| Video assets/runtime | Independent project/asset/prepared-scene/job stores, ready-artwork admission, owned ComfyUI API/runtime and pinned media supervision | Application integration and current end-to-end proof |
| Controlled motion | Durable controlled-media bridge/result import, reviewed 30-second blink/parallax reference and fixture-tested held `POSE_REPLACE` (manifest 4/tool 1.2.0); invocations ≤300 frames | Current-runtime reproduction and verified completed-chunk restart; rejected arm/rig experiments are deferred |
| Continuous planning | Exact-frame proposals, scoped dependencies and guarded proposal persistence | Required action-method binding, full scenery/repeat-boundary support and restart; app integration follows pilot/cross-city proof; no rig choice presumed |
| Production inputs | Approved blink/parallax direction, accepted original-scene mask and historical static neutral/pose appearances; six-second source-pixel proof | Required drink/read/breath/head/watch motion with compatible moving support/mattes, full 180-second scenery and repeat-boundary proof |
| Video UI and full output | No delivered `desktop/video` workspace, in-app moving preview or full-video export | Prove the production recipe first, then integrate it; `make video` still passes an unsupported `--video` option |
| Removed runtime | No active audio-production/worker or Swift companion application | Do not rebuild them; preserve external evidence and unrelated local data |

Important planning corrections:

- The bundled ComfyUI I2V preset accepts one composed image. VG2-25's external
  guided drinking proof also consumes a second approved pose through core guide
  nodes and the existing generic backend. The user accepts that exact drinking
  clip with its disclosed transition smearing and authorizes the reading test.
  This does not deliver integrated multi-image controls, independent layers,
  approval of unseen actions or four-minute coherence.
- `VideoShotPlanner` still models short unique/repeated shots. Replace its primary
  app flow with the persisted continuous-scene plan after the production workflow
  is proven; that caller cleanup is not a prerequisite for character experiments.
  Do not fill the source film by repeating short clips. The user may duplicate
  the completed three-minute segment externally after its repeat seam passes.
- ComfyUI and the controlled compositor are different execution stages. Reuse
  the verified durable media bridge rather than making ComfyUI claim compositor
  support. Whole-scene I2V distorted buildings/eyes; prefer stable supplied pixels,
  rigid scenery and separately validated character motion for the full pilot.
- Asset presence is not proof of alpha, pose alignment, scenery coverage, rights
  or approval. Inspect selected inputs from `docs/pictures/video/` recursively.
- Old build receipts do not certify the current dirty tree. Recheck the selected
  candidate without deleting environments, media, caches or user projects.

## 3. Product and safety rules

1. Preserve the independent MIDI workspace: six destinations and one persistent
   MIDI player, protected source/authority/candidates and accepted-only export.
   A top-level MIDI/Video switch is not a seventh MIDI page. Video needs no MIDI
   project, soundtrack, export or musical timing authority.
2. Video domain/application/adapters stay under `app.melotrail.video`; presentation
   stays under `desktop.video`. Construct optional runtimes lazily. Missing tools,
   models, credentials or network must not prevent MIDI startup, audition/export.
3. Video projects and job/output storage are outside MIDI roots, including symlink
   aliases. Preserve imported artwork and prior takes/exports; reject unsupported
   schemas before writes. No old-project migration or compatibility pipeline.
4. Reuse the existing local ComfyUI artwork/motion and controlled-compositor paths.
   The later Blender rig route is deferred; no automatic backend replacement.
   No automatic downloads, new models, cloud fallback, uploads or paid jobs.
   Hosted use needs explicit provider selection, disclosed inputs and a bounded
   authorized budget.
5. Persist job intent before execution; reconcile uncertain submissions, bound
   retries/resources/concurrency and cancel only owned work. Unknown progress,
   cost and capability stay unknown. AI seeds do not guarantee identical footage.
6. Tests establish integrity, not musical quality, artistic approval, rights or
   production readiness. Human decisions must identify the reviewed artifact/build.

Detailed ownership: [Architecture](docs/ARCHITECTURE.md),
[UI guideline](docs/UI_GUIDELINE.md), [Validation](docs/VALIDATION.md) and
[TABI video](docs/TABI_VIDEO.md). The [MIDI contract](docs/MIDI_CONTRACT.md)
remains the protected boundary for shared-shell changes.

## 4. Development foundation

### Feature CORE — Reproducible development baseline

1. Select the exact working candidate, preserving existing tracked and untracked
   changes. Separate task-owned video WIP from unrelated local environments/media.
2. Verify Kotlin 2.2.21/JDK 21, current architecture/documentation tests, focused
   feature tests, `make test`, `make build` and motion-tool tests.
3. Record actual current failures and their owners. Use an inventoried isolated
   checkout when necessary; never weaken a guard or delete data to obtain a pass.

**Exit:** an identified, reproducible baseline and a bounded next task. No new
scheduler, inference run, commit, installation or cleanup is authorized by this plan.

## 5. Video generation features

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
   For the TABI pilot tests, draw selected, hash-pinned source and action references
   from `docs/pictures/video/tabi-assets/scenario/` and the sibling TABI asset
   folders; require separate registered layers before independent character motion.
   Review/selection is optional refinement; unreviewed does not mean approved.
5. For the selected TABI series, prove the required drinking, reading,
   breathing/head movement and looking outside against the recovered visual
   baseline. Use scene-compatible assets and genuine continuous motion; static
   pose approval is not moving approval. Keep cup/book/headphones consistent.

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

Prove coherent offscreen scenery joins alongside the required action tests. Establish 60-second coverage before extending the
same method to the full 180-second pilot corridor. Do not generate a large untested
asset library. Missing full-length coverage blocks a full render, not an honestly
bounded shorter probe with its own validated inputs and identity.

1. Reuse the versioned continuous plan for exact frames, shared clock/seed,
   component reuse, required action schedule and rigid scenery in bounded chunks.
   Bind only the proven character-motion method through existing owners under
   VG4-07/09; no second ledger or automatic Blender adoption. After full-pilot and
   second-city review, integrate app callers and retire obsolete repeat planning.
2. Validate externally prepared scenery against the complete camera trajectory;
   derive rigid far/middle/near movement and occlusion from that trajectory. For
   the Tokyo pilot, plan distinct passing landmarks or district moments about
   every 5–10 seconds (roughly 18–36 over three minutes), with quieter painted
   travel between them. Keep the approved warm side-on style; do not stretch,
   paste obvious repeats or count transparent padding as authored scenery.
3. Join scenery offscreen and carry character/prop/blink/scenery state across
   chunks and verified restart. Schedule drinking, reading, breathing/head motion,
   window watching and quiet intervals across the pilot. Check each action's
   contact, silhouette, masks, entry and return. For this user-selected segment,
   prepare a last-to-first join with matching state and velocity at virtual frame
   5,400; delivered frame 5,399 must advance naturally into frame 0, not duplicate
   an endpoint as a freeze. The user may repeat the complete segment externally.
4. Recompute only dependent work when inputs change. Report fresh action footage,
   procedural motion and reused components without double-counting layered time.

**Exit:** exact 5,400/7,200/9,000-frame plans for 180/240/300 seconds at 30 fps,
with no hidden gaps, morphing buildings, visible seams, reverse-to-fill or freezes.
Do not fill those durations by looping short footage. The explicit full-segment
repeat boundary is checked for the user's later external six-minute edit. Missing
coverage requests more external artwork.

### Feature VG5 — Full-length rendering, review and silent export

1. Build only the bounded production runner needed by the pilot, reusing existing
   project/job/media ownership. Preserve absolute frames, shared state, temporal
   support and verified completed chunks across restart. No second ledger.
2. Start with verified chunk PNGs and one final numbered-image-sequence H.264
   encode. The pinned FFmpeg build lacks concat support; alternate assembly or
   tool changes require separate proof. Bound disk as well as memory, validate
   every frame/timestamp/join and atomically publish to a new filename.
3. Measure and review a real combined 60-second proof with scene/pose-compatible
   window and foreground mattes, then extend coverage and
   produce one complete 180-second pilot (5,400 frames) with moving depth-plane
   parallax, distinct passing views and all four required TABI activity groups
   inside the fixed cabin, with a verified last-to-first repeat boundary. Inspect the
   entire timeline, including quiet stretches and all joins; a repeated short
   loop or static still sequence does not qualify. Fixture checks remain mandatory
   but cannot substitute for the real film. Estimate preparation,
   composition, encoding and review separately; never extrapolate artistic quality.
4. After full-pilot artifact review, prepare one user-selected second
   city and an episode-specific scenery/motion brief. Review a separately admitted
   20–30-second reuse proof through the same runner, with no Tokyo special case,
   new backend or regenerated first film. City artwork and any new activity
   still require their own input/moving checks; this is not full-city coverage.
5. After that repeatability proof, expose the recipe through the approved app
   flow with full-cut review, join inspection, immutable export, credential-free
   provenance and Finder reveal.

**Exit:** the app reviews and exports a complete validated file. Changing the plan
invalidates readiness, not previous outputs. Native versus upscaled resolution
and any action-cadence conversion are disclosed; duplicated frames are not native motion.

### Feature VG6 — Packaging and real-video acceptance

1. Early check: produce three real 20–30-second clips through the production
   services wired to the app, invoked headlessly—base motion, changed motion with
   the same artwork, and replacement artwork with other compatible inputs fixed.
   Obtain actual user feedback on the supplied clips, not an app walkthrough.
2. Independently finish fixture-based assembly, package inspection, accessibility
   semantics and offscreen UI regressions while that decision is pending. Verify
   MIDI service composition without optional video runtimes, configured Video
   integration and bundled dependencies without launching an installed GUI.
3. After the early approach is accepted, produce one real 180-second result through
   that same production service graph with current selected artwork and a bounded
   authorized headless local run. For this TABI series, use the selected second
   city and its different action script after full corridor/contact/sequence
   readiness is proven. A short reuse clip cannot supply those full-film gates.
   Generic projects and other selected 180–300-second durations remain supported.
4. Have the user review the supplied whole cut and joins at normal speed for
   continuity, fidelity, prompt adherence and repetition. No interactive editor
   import/play test is required. Record a separate video release decision and
   the untested live UI/editor/installed-startup limitations.

**Exit:** actual production-service integration and real-video artifact evidence,
not just an API wrapper or synthetic encode, and not a claim of live GUI testing. The retained five-second/steam approvals and charcoal/stone v6 reference
remain narrowly scoped; they do not approve new clips. TABI, Tokyo, trains and
coffee are examples, never mandatory content or presets.

## 6. Step-by-step delivery order

**Current production order (clarified 2026-10-02): required character actions
and a complete repeatable three-minute Tokyo film.** Recovery is complete; a
blink-only baseline rerender is no longer the first deliverable.

| Step | Feature delivery | What unlocks next |
| --- | --- | --- |
| 1 | VG2-23 inspect/create scene-matched action and scenery references | Drink/read/watch candidates, preserved original style/props, explicit missing motion inputs |
| 2 | VG2-24/25/26 → VG2-27 required action proofs/review | Continuous breath/head/watch, cup lift/sip/return and reading/return with sound attachment/contact |
| 3 | VG4-05 then VG4-06 extend Tokyo scenery toward 180 seconds | Matching points of interest, quiet travel, depth/alpha/shutter coverage and loop-closing joins |
| 4 | VG4-07/09/02 → VG5-01/02 minimal proven-motion binding/runner | Absolute state, compatible action/scenery masks, completed-chunk restart and bounded encoding |
| 5 | VG5-03/05 combined 60-second check | All requested actions plus passing scenery work together; this is an internal scale check |
| 6 | VG5-06/07 full 180-second film and repeat-boundary review | Complete 5,400-frame silent film the user can duplicate to six minutes externally |
| 7 | Selected second-city reuse → app workflow → VG6 | Reusable controls and complete app output after the Tokyo recipe works |

The cancelled wave/Blender proof and standalone rig-description experiments stay
deferred. Required actions may use the selected local workflow without resuming
those packets. Fresh proof is needed before any permanent backend/contract choice.

[Audio verification and release](TASKS-AUDIO.md) remain independent MIDI
work; they do not block the video pilot. Human waits permit independent bounded
backend fixture work or MIDI work, not automatic promotion of deferred app tasks.
This order is not permission for parallel agents, inference or native rendering.
TASKS-VIDEO owns exact dependencies and current status. Every live experiment needs an
explicit bounded admission; the existing 900-second preview deadline is shared
across its chunks, never silently renewed. A full-production batch needs separately
authorized per-stage and cumulative time/storage limits, retaining native memory
and free-disk safeguards. External artwork preparation for this pilot does not add
in-app picture generation, change the provider or authorize new models.

## 7. Explicitly outside this delivery

- In-app image creation, outfit/style transfer, automatic semantic extraction,
  invented scenery and generative asset libraries.
- Short-clip repeat-to-fill, multi-scene/general timeline editing, vertical output,
  longer compilations, LoRA training or a provider marketplace.
- MIDI arrangement and its acceptance/release are independently scoped in
  [PLAN-AUDIO](PLAN-AUDIO.md), not video-delivery prerequisites.
- Audio rendering/mixing/mastering, soundtrack synchronization and public upload.
- A second Swift application, legacy project migration, broad filesystem cleanup,
  recovery of unavailable historical experiments or rewriting Git history.

A hosted video fallback is **optional and unselected**. Only a new explicit choice
after local evidence may activate it, with current terms, capabilities, disclosures
and a capped budget. No abandoned image-generation trial is a delivery prerequisite.

## 8. Completion policy

Each feature closes through bounded tasks in TASKS-VIDEO. Reuse existing consumers and
tests; add regression tests for every fixed bug. Run focused checks, `make test`
and `make build` only through a verified headless path, plus `git diff --check`.
If default wiring opens a window, use a filtered headless invocation and disclose
exclusions; do not report the original full suite as passed. Video changes also
run motion-tool checks and applicable explicitly admitted headless media probes.
Keep artifacts in ignored build output or selected external evidence storage,
not new planning/history documents.

This documentation split authorizes documentation and its validation only. It starts
no automation, agent run, model setup, inference, rendering, spending or
implementation commit. Future runs select
current task IDs explicitly; old scheduler state cannot choose or complete them.
