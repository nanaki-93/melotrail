# Melotrail improvement plan

Updated: 2026-09-14. Status: MIDI engineering complete with manual acceptance
pending; video foundations V10–V16 implemented, ComfyUI integration and complete
video delivery planned, not implemented.

This is the only roadmap. [TASKS.md](TASKS.md) owns implementation order and
status. It replaces the MIDI Core and UI task suites; their history stays in
Git. Existing working behavior is the starting point, not work to repeat.

**Current video direction (2026-09-14):** use the promising local ComfyUI and
controlled-animation tests as the basis for the remaining work. Recommend a thin
Video tab in Melotrail, a pinned local ComfyUI workflow for visual preparation,
and a small replaceable external compositor for controlled motion. The user
selected **one continuous 3–5 minute scene**, changing scenery and occasional
character actions, and authorized reuse of subtle motions without an obvious
repeated whole clip. Section 9 and TASKS V10–V33, including new suffixed slices,
supersede the earlier short-shot assembly proposal. Existing V10–V16 results
remain valid foundations, not proof of the new creative pipeline. The approved
steam and earlier checkpoint remain preserved; the latest five-second example
is a starting point, not full-length or in-app acceptance.

The user authorized restarting the scheduler with this revision on 2026-09-14.
The execution checkout is reconciled to this ComfyUI plan before resumption.
Execution remains Sol High implementation, Astra High repairs for concrete
failures, and one local commit per validated task. TASKS owns that policy. Music
and synchronization remain in the user's external Apple editor; public upload
and hosted generation are outside this planning action.

## 1. Product decision

Make a small, local Kotlin/Compose MIDI arranger that helps a musician turn an
existing melody into a coherent song, then finish its sound in Logic Pro.
Prioritize melody-compatible piano, deliberate song development, and a compact
musical workspace. More generated notes and more options are not success metrics.

The user's 2026-09-06 feedback is the current product baseline:

- Average generated-track quality: **5/10**.
- MIDI timing is consistently good.
- Creating a clean full song remains difficult.
- Piano accompaniment sometimes feels wrong against the melody despite timing alignment.

This is useful qualitative feedback, not a completed controlled listening test.
No offending project, bar range, candidate identity, or individual role scores
were supplied in this request. Do not invent them or translate 5/10 into a pass
on the old 1–5 rubric. Capture reproducible examples while improving the system.

The former five-participant and ten-project gates no longer block development
or legacy removal. Replace them with automated comparison preparation and final manual listening,
visual and Logic release checks in [Validation](docs/VALIDATION.md). Human listening
still determines musical acceptance; passing tests cannot award that acceptance.

TABI video is an independent creative workspace inside the same Kotlin/Compose
application. It needs no MIDI project, export, song or soundtrack. Its assets,
generation jobs and outputs stay separate from MIDI data. The core function is
**uploaded assets + a user-written prompt → generated video**. TABI riding a
train through Tokyo and drinking coffee was an example, not a required scenario,
preset, action list or acceptance gate. Content comes from each user request.

## 2. What exists and what needs improvement

Repository inspected at `1c37de2`, with pre-existing modifications to the root
and desktop Gradle files. They select Kotlin 2.2.21 and JDK 21; the committed
configuration selected JDK 25. Preserve these edits. Resolve the supported
build/toolchain choice from actual native build evidence in F01.

The baseline `make test` passed on 2026-09-06, including fresh root/desktop test
execution. Generated wide and compact workflow captures were inspected along
with all nine UI references, the TABI character sheet and train scene.

| Area | Observed implementation | Consequence / next work |
| --- | --- | --- |
| Import and storage | `midi/domain`, MIDI adapters, `project`, and `MidiCoreSourceImport` preserve source identity and validate MIDI | Reuse; improve explanations and musical preparation, without a new importer |
| Draft workflow | `MidiCoreArrangementDraft` generates all roles, records immutable candidates, supports retry, batch use and guarded undo | Preserve; introduce a deliberate song plan before role generation |
| Section intent | `MidiCoreArrangementStyleCatalog` creates role policies with no purpose; the draft service passes the same policy to every occurrence | Intro/chorus/bridge-specific generator branches are not meaningfully driven by the primary style path; wire explicit per-occurrence intent |
| Piano voicing | `MidiCoreChordGenerator.selectVoicing` filters exact protected-anchor collisions, scores local voice movement and selects among three ranked choices | Add melody-aware interval/duration scoring and bounded phrase lookahead; local correctness is insufficient |
| Musical findings | `MidiCoreRoleValidator` reports close pitches/register pressure mostly as advisories | Findings need to influence candidate ranking and offer focused repair, while deliberate tension remains valid |
| Rhythm | Chord rhythm restarts from each chord-window start; catalog steps are sixteenth-based | Keep metrical phase across sub-bar harmony; author and test 3/4 and 6/8 instead of truncating 4/4 intent |
| Continuity | Chord previous-voicing state starts empty per occurrence; dependency context is occurrence-scoped | Add explicit boundary summaries, repeated-section relationships and phrase development |
| Harmony entry | `MidiCoreAuthorityDrafting` distributes progression symbols into equal slots, preserving unchanged exact windows | Add visible chord durations; a correct chord at the wrong beat can still sound wrong |
| UI | Theme, primitives and shell are implemented through the old UI-004 commit; visual projection code also exists | Verify and finish those owners; do not rebuild them because the old log header says “not started” |
| Layout | Fresh test captures show tall pill controls, stacked forms, a roughly 200-pixel player and duplicate context | Put aligned note lanes and the next action in the first viewport; consolidate inspectors |
| Visual tests | `MidiCoreVisualRegressionTest` writes images and checks size/presence | Add approved target-image comparisons that actually fail on regressions |
| Boundaries | Architecture test checks a `desktop/target/` path while active pages live directly under `desktop/` | Fix coverage before removal; check actual production paths and imports |
| Legacy | Default entrypoint uses MIDI Core, but `DesktopMain.kt` still contains worker/mastering composition; old services, tests and media remain | Delete by consumer/dependency analysis, never retain old-project compatibility |

These code observations suggest causes of mediocre arrangements; they do not
prove the cause of a particular unheard passage. M01 freezes comparable outputs
and M02–M09 test the hypotheses with both semantic evidence and listening.

### Size baseline

Tracked files before this documentation change: 236 production Kotlin files /
73,677 lines; 187 Kotlin test files / 34,912 lines; 33 Python files / 4,782 lines;
33 Markdown files / 9,720 lines. Local disk also contains approximately 10 GB
under `sounds`, 303 MB under `data/audio`, and 521 MB under `.venv-worker`.
These are measured inventories, not claims that every file can be deleted.

The source and documentation counts exclude generated/ignored files. Disk usage
includes ignored material. Keep those measures separate from Git history size
and packaged application size.

## 3. Product boundaries

Preserve these invariants through every task:

1. Source MIDI bytes and the protected melody's note timing, pitch and velocity
   are immutable. Supported expression follows the MIDI contract.
2. Confirmed tempo, meter, key, structure and chord durations are authoritative.
   Chromatic chords are valid; key compatibility remains advisory.
3. Suggestions remain drafts until confirmed. Generation never edits harmony
   or the source to improve its own score.
4. Candidates and export snapshots are immutable. Acceptance changes references
   atomically; targeted regeneration keeps previous work recoverable.
5. Same authority, settings, engine versions and seed produce identical semantic
   MIDI and validation. AI is unnecessary for the musical workflow.
6. Logic Pro owns instruments, rendering, mixing and mastering. GarageBand is
   outside the supported destination claim.
7. Kotlin/JVM owns the app, including video orchestration and Compose controls.
   Video may invoke configured local inference and video-only media tools, or
   an explicitly selected hosted API. These stay outside the MIDI graph. Do not
   build another Melotrail Swift app, audio renderer, mixer or uploader.
8. No maintenance or migration of obsolete audio projects. Current MIDI project
   safety must survive schema changes; unsupported versions fail before writing.

Optional melody edits, unrestricted AI music, extra generated roles, multiple
source files, tempo/meter maps and a general piano-roll editor remain outside
this delivery. Finish the four-lane Melody/Chords/Bass/Drums workflow first.

## 4. Target musician journey

```text
Create/open project
  -> Import and hear the protected melody
  -> Confirm source extent, musical settings, sections and chord durations
  -> Inspect melody/harmony tension and accept a suggested song arrangement plan
  -> Preview a style with the melody
  -> Create and hear a complete draft
  -> Mark a problem section/role and choose a musical repair
  -> Use the draft, with undo available
  -> Export verified complete-song and role MIDI files
  -> Finish instruments and sound in Logic Pro
  -> Separately generate silent TABI visuals in the Video tab
  -> Combine visuals and finished music in the user's external editor
```

Six MIDI destinations remain: Project, MIDI, Structure & Harmony, Arrange,
Review, Export. A common song map, selected occurrence and persistent player
connect them. Draft playback does not require per-role acceptance. Export uses
only current accepted work.

An application-level **MIDI / Video** tab switch makes video reachable from an
empty launch. Video owns its project selection and silent preview; it adds no
step to the six-destination MIDI arrangement/export pipeline.

The principal product improvement is **plan the song once, listen to the whole
song, repair specific musical problems**. A batch of unrelated section patterns
is not yet a coherent song proposal.

## 5. Better MIDI preparation and authority

### Import and source extent

Keep the single SMF 0/1, single note-bearing track/channel contract. Display
actual notes, pitch range, expression, fixed tempo/meter suggestions and source
length. Distinguish blocking corruption from supported unusual music. Show
exact corrective instructions for extra tracks, changing maps and unsafe note
pairing. Do not quantize, transpose or clean the protected source silently.

Report last note end separately from file end-of-track. Offer an explicit
**pad arrangement with silence to the next bar** when the source has a partial
final bar; store the confirmed arrangement end separately from the source end.
Preserve the original and every event. Do not add leading time or infer a pickup
offset silently. Extending/repeating melody material is outside this change.
The existing strict whole-bar rule remains until M03 changes all dependent
contracts and tests together.

### Structure and chord durations

Provide a compact table with section name, musical purpose, bar count, derived
bar range and repeat family. Duplicate, move, split and remove operate on the
structure draft and show affected accepted work before save. Reordering sections
changes accompaniment boundaries over the fixed source timeline; it does not
reorder the source melody. Label that behavior clearly.

Use one chord row per selected section: symbol + duration in beats/bars + a
visual span over the melody. Preserve supported exact sub-bar windows. Example:
Cmaj7 for 3 beats, Am7 for 1 beat, Dm7 for 2 beats, G7 for 2 beats. The old
progression shorthand can seed rows, but duration is explicit after conversion.
No raw tick or internal ID fields in the normal editor. Persist the canonical
windows once, with gap/overlap and total checks.

Detect possible phrases/repetitions from melody rests, duration, accents and
contour. Present bar-boundary suggestions with reasons and allow correction;
never save inferred structure or chords automatically. The full-song source
contract means Melotrail is arranging an existing melody timeline, not creating
missing melody sections.

## 6. Musical engine improvements

### 6.1 Melody context and explainable harmony tension

Derive a read-only context per beat, bar, phrase and chord window containing
active/sustained notes, accents, register, note activity, rests, repeated motifs
and protected anchors. Account for supported sustain when assessing sounding
overlap; flag unknown pitch-bend interpretation instead of asserting exact
acoustic consonance. Keep raw protected events unchanged.

Score melody/chord relationships over actual overlap duration and metrical
weight. Distinguish sustained accented tension from a short passing or neighbor
note. Consider compound intervals and octave placement, not only exact pitch
matches. A major seventh or suspension is not automatically an error.

Show “Piano crowds the sustained melody at bar 12, beat 3” with a highlighted
span and audition target. If the conflict is in authoritative harmony itself,
explain it and link to its editor; offer suggestions only for explicit review.
Do not call a single numeric heuristic a musical-quality verdict.

### 6.2 Piano that supports the melody

Retain the chord parser, range constraints and proven voicing enumeration.
Generate a bounded candidate pool with inversions, open spacing, guide tones,
optional root omission when bass supplies it, and deliberate rests. Preserve
required chord identity/slash bass semantics. Reduced voicings are legitimate
choices, not only a last-resort collision fallback.

Rank with versioned, explainable costs for melody tension, register crowding,
voice crossing, voice movement, spacing, unnecessary doubling and low-end
separation. Use dynamic programming or a small beam across a phrase, then carry
an explicit boundary summary to the next section. Stable ordering resolves ties;
a seed varies similarly useful choices without selecting an avoidably bad one.
Bound search width/time and expose a typed failure if no legal proposal exists.

Comping rhythm follows the song's metrical grid and melody phrase: sustain under
busy passages, answer rests, leave breathing room at phrase ends, and maintain
phase when harmony changes mid-bar. One shared timing policy handles clipping,
articulation, swing and rounding. Humanization must be bounded MIDI intent and
must never move protected source events or cross harmony/section limits.

### 6.3 One explicit arrangement plan for the song

Add a versioned arrangement plan above the scoped generators. It references
confirmed occurrences and stores per-occurrence purpose, energy, role activity,
density, register preference, phrase grouping, repeat-family variation and
entry/exit intent. It also defines shared rhythmic intent for bass and drums.
This is arrangement authority with explicit confirmation, separate from analysis
suggestions and from immutable generated-note candidates.

A style proposes a plan; the user can hear and adjust it before committing to a
new draft. Musical section purpose is an explicit value, never guessed from a
label such as “B”. Suggested behavior for a typical source:

| Purpose | Accompaniment intent |
| --- | --- |
| Intro | Establish motif and space; bass/drums may enter later |
| Verse | Lower piano activity under the melody; steady restrained groove |
| Pre-chorus | Controlled increase and a phrase-end lead-in |
| Chorus | Fuller register/voicing and groove; melody still leads |
| Repeated chorus | Preserve recognizable rhythm, vary one dimension deliberately |
| Bridge | Contrast through texture/register or half-time feel, respecting meter |
| Outro | Reduce layers and create an intentional ending without cutting the source |

These are editable style defaults, not compulsory rules for every composition.
Every required role scope has either a candidate or an explicit planned rest.
A silent intro is complete work; a failed generator returning no notes is not.
Do not confuse playback mute/solo with an arrangement rest or export omission.

### 6.4 Deterministic coordination and alternatives

Resolve the arrangement plan first, then retain the Chords → Bass → Drums
publication dependency order. Shared groove intent exists before either bass
or drums generates notes; final drums can additionally use the generated bass.
Avoid a circular “accepted bass requires accepted drums” dependency.

Carry only bounded previous/next-section musical summaries and repeat-family
references. Include every used input in the scoped fingerprint. An edit may
invalidate the selected scope plus explicitly dependent neighboring scopes;
show that affected set. It must not silently invalidate the entire song or
pretend a boundary-dependent neighbor is unchanged.

Generate at most three meaningfully different options for comparison (for
example sparse/open, held/connected, or rhythmic/answering). Do not fill a
candidate list with velocity-only duplicates. A/B audition uses the same melody,
bar position, role balance and instrument mapping.

### 6.5 Musical repair controls

Expose bounded intents: **leave more melody space**, **simplify piano**,
**lower piano register**, **smooth the transition**, **reduce bass movement**,
and **calmer drums**. Each maps to documented settings, a scope and deterministic
candidate generation. Explain what changes; retain the source, harmony and
accepted references. Show a before/after loop and only apply after the musician
chooses it. If no valid result exists, return a precise reason rather than
retrying seeds indefinitely.

Keep profile IDs, patterns, seed and scoring details available in an inspector.
Normal repair starts from the audible problem, not a technical dropdown.

## 7. UI/UX delivery

[UI guideline](docs/UI_GUIDELINE.md) consolidates the previous visual guideline,
measured reference targets and page mapping. Preserve all supplied images as
design inputs. Reuse the implemented theme, shell and primitives where sound.

The reference's main value is composition: compact header and navigation,
bordered panels, colored section blocks, aligned musical lanes, restrained
violet actions, a contextual inspector and a compact transport. Finish that
composition instead of applying another dark theme to long forms.

Use verified MIDI projections in Project/MIDI/Arrange/Review. One shared tick-to-x
geometry aligns bars, chords, notes, roles, loop and the real playback position.
No random note decorations, audio waveforms or fake video frames. Lanes are
read-only; selection, zoom, fit and loop controls do not edit melody notes.

The first Arrange viewport at 1280 × 900 must contain the song map, four role
lanes, style choice and main draft action. The collapsed player targets 80 dp
wide / at most 112 dp compact, with expandable advanced options. Only one
selected-section inspector owns the decision; avoid today's repeated context.

Use an explicit state vocabulary: not generated, planned rest, preparing, draft,
accepted, stale, needs attention. Give each text/icon treatment. Show useful
empty/error/progress states, keyboard focus and next actions. Keep scrolling,
selection and valid playback stable between Arrange and Review.

Visual acceptance requires 1536 × 1024, 1280 × 900 and 720 × 900 fixtures,
reference-side review, pinned target goldens, geometry/accessibility checks and
real image differences. Baseline regeneration cannot automatically approve a
new design. The user assesses fidelity and clarity after seeing the six pages.

## 8. Legacy removal and repository budget

Start after the existing MIDI workflow and retained dependencies are mapped;
do not wait for musical release acceptance. Do not rewrite proven MIDI code
for cleanup or make a bulk `*Midi*` keep/delete decision.

| Scope | Action |
| --- | --- |
| `WorkspaceApp`, `WorkspaceViewModel`, `WorkspacePageRouter`, old page/support branches | Remove after target UI consumers and shared primitives are separated |
| Legacy composition in `DesktopMain.kt` | Keep the real MIDI entrypoint; remove worker/mastering/release factories and adapters |
| Old application stage graph, schema-v4 model, compatibility constructors/typealiases | Remove consumers, then models; retain no migration mode |
| Old AI planners, critics, enhancement/cohesion passes, source mutation, Pad/Strings/extra roles | Keep only proven helpers already required by current Chords/Bass/Drums; remove old owners/tests |
| `audio`, `dsp`, rendering/mixing/mastering, sound-library/licensing, commercial/release runtime | Delete code, exclusive tests/resources/config and unused dependencies |
| `worker`, Kotlin worker HTTP adapters, Python tools/environments | Remove after no MIDI path uses them; target build/test needs JVM only |
| `sounds`, `data/audio`, old bundled media and caches | Resolve exact repository-owned targets, check consumers and protected inputs, record bytes, then delete |
| UI mockups, TABI/train references, owned MIDI fixtures, Logic captures | Retain; these are design/testing assets, not obsolete runtime |

Never delete an external project, unresolved path, workspace root, selected MIDI
input, accepted candidate or export snapshot. Ignored legacy data is not
recoverable from Git: verify ownership and exclusions explicitly. Do not run
`git clean -fdx`, rewrite Git history, or install old Python dependencies to
make their obsolete tests pass.

Finish with one source of truth per behavior, no dead adapters or legacy test
exceptions, a minimal Makefile and reviewed Gradle dependencies. Target zero
Python source/runtime and zero legacy audio sample/project bytes in the repo.
Use a **40% reduction in production Kotlin lines** as an investigation target
from the baseline, not permission to delete useful functionality. Report actual
file/line/disk/dependency/package deltas and explain retained exceptions.

Keep nine active root/docs Markdown files at this planning handoff; worker-local
setup documentation disappears with its owner. PLAN and TASKS may be detailed;
other references should stay short and non-overlapping. Runtime-generated
reports and benchmark artifacts belong in ignored build output or user storage,
not an expanding checked-in execution diary.

## 9. Video generation from assets and a prompt

### 9.1 Outcome and mismatch

The Swift package opens a prepared `SceneCompositionRequest`, reads a finished
soundtrack and approved layers, previews their composition and offers a separate
CLI encode. It lacks the requested upload → prompt → AI video journey. Do not
port that product workflow to Kotlin. An encoder or synthetic demo does not
establish that generation from assets and a prompt works.

Deliver a complete **180–300 second silent video**, default **240 seconds**,
for adding music in an external Apple editor and later publishing on YouTube.
No soundtrack selection, MIDI timing, beat detection, lip sync, music generation,
mixing or public upload. The only delivered creative application is Melotrail.

### 9.2 Visible workflow

1. Open **Video** from the normal window and create/open a video project.
2. Add pictures in three separate, optional upload areas: **Character — moves &
   expressions**, **Outfit**, and **City / scenery**. Each accepts multiple PNG/JPEG
   references through file selection or drag-and-drop. Character references cover
   identity and examples of poses/expressions; outfit references guide clothing
   and accessories; city references guide the environment. Keep supplementary
   **Style** and **Complete scene** inputs available without adding required steps.
   Display consumed references and preserve originals. Keep chosen character,
   outfit and style references when changing the scenery, and vice versa.
3. Write a free-form prompt describing the desired video. Optional controls for
   actions, motion, style and guidelines refine it; a named scenario or preset
   is never required. Set total duration (3–5 min). No JSON, masks, node graph,
   manual cutouts or pre-cut animation is required for the normal workflow.
4. Click **Generate video**. The app prepares a scene through ComfyUI, derives
   usable layers and motion controls, then renders the full continuous scene in
   bounded chunks. A short preview can be requested first. Optional look/motion
   review is a refinement, not a mandatory manual rigging or storyboard step.
   Automatic intermediate choices remain unreviewed until the user reviews them.
5. Play the draft in the tab. Keep the look while adjusting character motion,
   environment speed or effects; regenerate only affected preparations/chunks.
   Review the full silent result. Earlier versions survive edits. Show actual
   progress, cancellation/recovery, component reuse and any missing scenery.
6. Export a new silent MP4 and reveal it in Finder. Adding music in the user's
   chosen Apple editor is the next step outside Melotrail.

Reuse the current theme and reference 08's broad preview composition. At
1280×900 the next action and preview are visible; at 720×900 uploads, prompt,
generation and review remain keyboard-reachable. Put technical IDs/hashes/model
internals in details. Setup explains missing tools/models; after setup, normal
creation does not require a terminal or operating another creative app.

### 9.3 General reference and prompt contract

The supplied assets and prompt are the content authority. Preserve recognizable
subjects and the requested reference characteristics while allowing the scene,
actions, camera, environment and style changes the prompt asks for. Character,
background and style references are optional roles, not a mandatory TABI kit.
Conflicting instructions or references require a visible choice rather than a
silent override. Do not force headphones, a cup, a seated pose or pastel style.

The 2026-09-15 upload specification separates three kinds of inspiration:

| Upload area | Intended influence | Preserve independently |
| --- | --- | --- |
| Character — moves & expressions | Recognizable subject, silhouette, pose and facial-expression examples | Identity is retained when outfit or scenery changes; pose pictures do not guarantee supported animation |
| Outfit | Selected clothing, colors, accessories and outfit details | Do not copy a reference model's face/body or incidental background; outfit selection supersedes incidental clothing in other references |
| City / scenery | Buildings, landmarks, streets, landscape and environmental mood | Do not replace the selected character, outfit or visual style with people/clothing/style from scenery pictures |

All three groups are optional and support multiple images within the measured
workflow capacity. A single reference plus a prompt still works where supported.
Use generic roles rather than requiring TABI or a city. Retain the selected style
reference (including the approved banner style when chosen) across outfit/scenery
changes. Explicit prompt/reference disagreements and incompatible outfits require
a visible resolution; no silent averaging or arbitrary last-upload-wins behavior.
Moving/removing a reference changes the current request binding, not original
bytes or accepted versions. Show duplicate-role/capacity/unsupported-role issues
before generation; do not silently drop images or treat filenames as conditioning.

Reusable guidelines address reference fidelity, object coherence, continuity
where requested, temporal stability and avoiding unintended visual artifacts.
Camera movement, background movement, pace and action come from the prompt or
explicit optional settings. A static background can be correct for one request
and incorrect for another; evaluate generated motion against the actual request.

Expose a small set of executable constraints: preserve subject appearance,
allowed character motions and their intensity, camera movement, environment pace
and direction, and effects anchored to visible objects. Keep free-form guidance
separate from controls the selected workflow actually enforces. Do not promise
that a negative prompt guarantees correct anatomy, prevents all drift or locates
a cup. Unsupported actions must be reported with an actionable alternative;
silently replacing a requested action with breathing does not satisfy it.

The Tokyo train/coffee idea may appear as optional example text or a test input.
It must not become a required preset, hardcoded scene model, default prompt
injection or release criterion. For that example, passing scenery and coffee
actions matter only because the example prompt requests them. Any other supported
assets and prompt must use the same main workflow, without a separate custom mode.

### 9.4 ComfyUI preparation and controlled motion

The **M5 Pro / 48 GB** host has now run local trials. Draw Things tuning produced
short LTX video; subsequent ComfyUI trials improved the images, but generated
character motion and scenery coherence remained unsatisfactory. The promising
five-second checkpoint combines ComfyUI-derived artwork with a separate Node
Canvas compositor for blinking, breathing, head movement, steam and window
scenery. Its masks, eye replacements and coordinates were prepared manually.
It does not prove automatic rigging, multi-reference scene preparation or a
five-minute ComfyUI inference.

| Option | What it provides | Tradeoff / decision |
| --- | --- | --- |
| Use ComfyUI directly | Immediate access to workflows and manual experimentation | More node-graph/file work; long video still needs preparation and composition |
| Thin Melotrail tab + ComfyUI + controlled compositor | Separate asset uploads, prompt constraints and recoverable local production | Recommended; requires a small reusable motion tool and automatic preparation proof |
| Generative video only | Ask one model to animate the whole image | Current local tests drift or freeze; not the selected quality strategy |

Use **one ComfyUI local generation adapter**, replacing the planned Draw Things
production adapter. Retain completed probe evidence without shipping two active
generators. Kotlin owns projects, validation, orchestration and UI; ComfyUI owns
the pinned visual workflows; an external, versioned motion tool owns controlled
frame composition. Adapt the useful tested compositor after making its inputs
generic. Do not port it into Kotlin, restore the old Python worker, build a
general timeline editor, or describe this as eliminating all custom code.

ComfyUI already documents image uploads, workflow submission, history and
WebSocket progress. Use a dedicated app-owned loopback server: its interrupt
endpoint stops the current execution, so it is unsafe as a per-job cancellation
operation on an unowned shared server. Persist attempt identity before submission,
reconcile uncertain submissions and verify actual outputs. A disconnected socket
is not completion or cancellation. Check workflow/node/model versions before use.
[ComfyUI API](https://docs.comfy.org/development/comfyui-server/comms_routes),
[execution messages](https://docs.comfy.org/development/comfyui-server/comms_messages).

The first creative prerequisite is an automated, reference-conditioned prepared
scene: subject layers, usable eye/head poses or landmarks, clean background fill,
effect source anchors, occlusion masks and sufficient scenery for the requested
travel. Prove both a changed scenario and a changed subject using the same input
contract. Also compare outfit-only and pose/expression-reference-only changes,
holding other selections fixed. Verify the outfit changes without changing subject
identity or scenery, and that supplied pose/expression examples influence supported
prepared outputs. Record each selected role's actual workflow image binding and
capacity; a role unsupported by the chosen profile must be reported before a job.
A reference collage or passing asset filenames as text is not proof
that all selected references condition generation. Prefer maintained ComfyUI nodes
where they work; pin and validate their actual outputs. At most two bounded
preparation profiles are tried before reporting a concrete capability gap.

The installed LTX-2.3 Q4 profile, Gemma encoder, tiled decode and spatial upscaler
are evidence for short generative shots, not a complete image/layer-preparation
stack. Select that stack in V18b with current commercial-use terms and host
measurements. New downloads still use an explicit setup choice. Keep models,
Python/Node runtimes and outputs outside Git/MIDI, one inference at a time, and
normal creation offline after setup. Pin tested memory controls rather than
assuming FP8, tiling or quantization removes all memory/quality risks.

Measure preparation cost once per scene, optional action generation, composition
seconds per frame, encoding time and peak memory/swap separately. Estimate a
3–5 minute output from these actual stages; the old estimate of 30–60 independent
diffusion takes no longer describes the chosen approach. Optional hosted V25
remains unselected and requires an explicit service choice, reviewed upload and
capped budget; this revision authorizes no cloud jobs.

### 9.5 Producing 3–5 minutes

The user selected a **continuous scene with evolving scenery and occasional
character actions**. Reusing small motion patterns is explicitly allowed; replaying
the whole five-second video to fill the duration is not. Separate motion-pattern
reuse from whole-footage reuse in storage and UI. Do not label composed frames as
fresh diffusion footage or add overlapping layer durations into a false total.

Prepare the look and reusable elements once, then render a continuous world and
character timeline. Use seeded, varied intervals for supported blinks and subtle
gestures; retain breathing and the approved steam behavior where requested.
More complex actions such as drinking or page-turning require suitable guided
poses/action takes and their own evidence. They are not automatically covered by
the successful calm-motion test. Preserve free-form prompts without inventing a
story/action parser or silently ignoring actions the workflow cannot perform.

For moving scenery, derive rigid near/middle/far layer movement from one camera
trajectory and depth relationships, respecting perspective, occlusion and shutter
blur. Prepare enough coherent scenery for the whole travel distance, in bounded
sections; join sections outside the visible region. No house morphing, visible
texture wrapping, repeated short-city reset or unrequested direction reversal.
Numerical speed alone is not proof of realistic train motion. The Tokyo example
tests this requirement; other environments use their requested motion instead.

Render in bounded chunks using absolute frame numbers, shared motion state and
seeds. Resuming a chunk cannot reset a blink, particle age, scenery position or
random sequence. Include any required boundary frames for blur/temporal effects,
then trim to exact output frame ranges. Do not retain all 5,400–9,000 1080p frames
in RAM. Detect inadequate scenery coverage before rendering; generate more or
report the gap rather than stretch, freeze, reverse or silently loop material.

Use a measured ladder: preserve the approved **5-second baseline**, produce a
**20–30 second** reusable-scene test, then a **60-second** sustained-motion and
chunk-seam check, finally the actual **180–300 second** app export. The 60-second
run updates the full-render estimate; it does not establish full-length success.
A multi-scene editor and longer compilations are deferred, not alternate modes
that must be built before this continuous-scene workflow works.

### 9.6 Architecture and data

Add an app-level tab boundary above `MidiCoreWorkspaceShell`; retain its six
MIDI destinations. Construct video services lazily in a separate composition.
Missing models/credentials/media tools cannot prevent MIDI startup or export.
Video works without a MIDI project. Entering Video pauses MIDI audition without
losing its position; silent video preview creates no MIDI player.

New domain/application/adapters live under `app.melotrail.video`, and presentation
under `app.melotrail.desktop.video`. MIDI project/application code must not import
video; only the app composition root coordinates both workspaces. A new video
project schema owns references, scene looks, immutable takes, selection/assembly,
job attempts and export snapshots. Store it in a chosen video directory outside
MIDI project/export paths. No Swift session/ledger or old-project migration.

Add a versioned prepared-scene descriptor with immutable layer/pose/mask pins,
validated coordinate spaces, depth/occlusion relationships, effect anchors and
the workflow's supported motions. Persist the continuous motion plan, component
reuse choice and chunk frame ranges. The existing V15 short-shot proposal is
superseded in the primary flow by V26's continuous plan; preserve its useful
prompt/fingerprint behavior while removing exclusive retired planner consumers.

Import copies selected media, checks decoded content/geometry/limits and pins
digests. Resize or remove metadata only in derived upload copies. Fingerprints
include all consumed assets, prompt versions, models/workflows and settings.
They identify requests; AI output is not guaranteed deterministic even with seeds.

Persist attempts before local launch or hosted submission. Recover across tab
switches and app restart. Interrupted local inference is recoverable work, not
a completed take. Ambiguous hosted submissions require reconciliation; never
blindly retry a possibly charged request. Bound attempts, disk/memory/time and
concurrency. Cancel only the app's own jobs/processes, not another app's shared
queue. Unknown progress/cost stays unknown. New takes never overwrite selections.

Use one pinned **FFmpeg/ffprobe** distribution for video-only decode/assembly/
validation, invoked from Kotlin. V12 proves macOS support, packaging and exact
codec/preview behavior using owned fixtures. Use argument arrays, bounded pipes
and job-local temporary directories; reap owned processes on cancellation.
Record distribution/build options/notices and model terms. No new Swift host,
audio pipeline or port of the old soundtrack compositor.
[FFmpeg documentation](https://ffmpeg.org/ffmpeg.html),
[distribution considerations](https://ffmpeg.org/legal.html).

### 9.7 Delivery and real acceptance

Output: landscape **1920×1080 H.264 MP4, 30 fps**, square pixels, **zero audio
streams**, and the selected 180–300 second duration. The latest controlled tests
already use this cadence; render motion at that cadence rather than relabeling
lower-rate frames. Record native asset/action resolution, cadence and explicit
conversions; upscaling is not native 1080p generation. MP4/H.264 and preserving
the produced frame rate follow the platform's published recommendations.
[YouTube encoding guidance](https://support.google.com/youtube/answer/1722171?hl=en).

Validate the full export's decodability, stream count, dimensions, frame cadence
and duration before publishing to a new filename. Preserve accepted takes;
keep failed partials out of the results gallery. Export provenance contains no
credentials. Test actual import/playback in the user's chosen Apple editor;
music placement, synchronization and public upload remain outside scope.

**V24 early visual checkpoint:** one prepared scene and three real 20–30 second clips: a
base assets/prompt case, a materially different prompt with the same assets, and
a changed-reference case that changes only the outfit while retaining character,
city/scenery and style selections. Check all supplied groups' fidelity, visible
compliance with the requested scene/action/motion/style, coherent objects and
temporal stability; changed-scenery and pose/expression evidence remains required
in the preparation proof.
Check derived masks/poses, effect anchoring, depth and absence of obvious repeat
resets at normal speed, not only selected still frames. TABI identity is checked
when TABI references are supplied. No location, prop,
action or camera mode is mandatory. Obtain actual user feedback before claiming
this approach works. Unpaid storage/UI/assembly
work can proceed while a human decision is pending; synthetic media never counts
as that decision. Local failure triggers a reviewable hosted comparison proposal.

**V33 final checkpoint:** start with only user-chosen reference assets and a
free-form prompt in Melotrail, then generate, review, compose and export one
real 3–5 minute video matching that request. The user reviews the full cut,
reference fidelity, prompt adherence, repetition and joins at normal speed.
Technical tests prove integrity; visual quality and channel suitability require
actual review. A contrasting prompt must work through the same primary flow.

For the current TABI example, preserve the banner's requested palette/style,
the controlled-motion checkpoint and the specifically approved coffee steam.
These approvals are scoped to those components. The latest train test is a
starting point; it does not close V24 or V33. Retained sources and receipts are
under `~/.codex/melotrail-video-sequential/evidence/V17/` (controlled-motion,
controlled-motion-v2 and train-depth-v3 experiments dated 2026-09-14) and
`~/Library/Application Support/MelotrailVideo/checkpoints/2026-09-14-controlled-motion-v1`.

Commercial model permission and YouTube monetization are separate questions.
Keep exact model/node/license receipts and recheck changed components during
setup. Episode concepts, scenery and creative development should materially vary;
reusing small self-created motions is not a guarantee of eligibility, and no
arbitrary percentage of unique frames establishes it. The platform evaluates
originality and repetitive/mass-produced content across the channel. Music rights,
final editing, disclosure and publication are handled outside Melotrail.
[YouTube monetization policy](https://support.google.com/youtube/answer/1311392?hl=en).

### 9.8 Removal and boundaries

V30–V31 remove `companion/`, exclusive tests/scripts/resources and the MIDI Export
companion handoff after an integrated owned-fixture path works. Preserve original
TABI/UI/train references, Logic evidence, MIDI projects/exports and external
user media/saved outputs. Do not delete installed applications or external
sessions as repository cleanup. No retained Swift bridge or compatibility mode.
Replace the temporary Swift Makefile target with `make video` opening the same
Melotrail Video tab, without a JSON argument.

V10–V33 are the new queue. V24/V33 need human evidence. V25 is an optional hosted
adapter activated only if the user chooses it after local evaluation. Missing
model setup, credentials, paid budget or visual decisions block their exact
gate, not independent engineering. Long compilations, vertical formats, general
timelines, LoRA training and multiple simultaneously supported providers are
outside this first delivery.

## 10. Delivery order and acceptance

| Wave | Delivery | Exit evidence |
| --- | --- | --- |
| Foundation | F01, M01, then F02–F06 | Baseline MIDI regression pack; actual dependency map; small JVM-only build with no legacy runtime |
| Visible workspace | U01–U03 and U02 transport work | Real aligned MIDI lanes and compact persistent controls; input workflow intact |
| Musical preparation | M02–M03, U04 | Explainable melody/harmony context, explicit chord durations and safe song extent |
| Song arrangement | M04–M09, U05–U06 | Melody-compatible piano, deliberate section plan, coordinated roles and targeted repairs |
| Validation | U07, Q01–Q03 | Visual approval, improvement on frozen music cases, fresh Logic checks and clean build/install |
| Video replacement | V10–V33 | Reference assets + free-form prompt → complete silent video; measured local trial; Swift removal |

The user chose on 2026-09-11 to perform manual listening, scores, Logic tests
and visual/video review only after all unpaid engineering. TASKS parks these
MIDI gates as WAITING_USER and excludes them from automatic attempts. Q03a and
Q03b retain completed MIDI evidence. The new video request replaces the old V
gates and adds an early visual checkpoint at V24. Release and production
authorizations stay pending. Never claim the musical 5/10 result has improved
before comparison.

Proposed release targets: median overall and piano/melody-fit scores at least
8/10 across five varied songs, every song at least 7/10, zero melody mutation,
zero severe unresolved arrangement/timing faults, and median first-draft-to-use
review time at most ten minutes. These are new targets, not achieved results.
First-draft playback should need at most three actions after authority is ready.
See Validation for sample selection, failures, timings and Logic evidence.

## 11. Automatic delivery workflow

The bottlenecks found on 2026-09-08 were concrete: broad tasks consumed their
budget before review; a budget stop looked like a code failure and replayed
implementation; completed commits advanced a branch while the normal checkout
kept older files. Success means tested code visible in the runnable project,
not merely more agent activity.

**Delivery unit:** one TASKS row is a bounded slice, normally one user behavior
or one domain boundary with a few production owners. The 29 new suffixed rows
split the remaining large features. Each unsuffixed row finishes its remaining
slice and checks the combined parent acceptance criteria. Dependencies remain
explicit; completed children are reused. No extra task or execution-log documents.

**MIDI engineering complete; video replacement pending:** reuse integrated MIDI
and UI behavior. Q03b remains the MIDI review entry point. V10–V33 replace the
Swift workstream; old V completion does not satisfy them. This planning update
does not start/reconfigure the existing heartbeat. A later implementation run
must select the new queue and exact allowed paths. Automated tests, builds,
frame/semantic checks and independent code review remain per-task.

**Execution:** the existing 20-minute heartbeat runs the tested local runner's
`advance` command. One Astra Extra High writer implements; the host coordinator runs
focused checks, test/build and diff checks; a fresh Astra Extra High reviewer inspects
the exact tested tree. A failed attempt gets one Astra retry, then Sol High
receives the original task, current diff and concrete terminal/test/review errors
for one repair. If the final review leaves up to three concrete code findings,
recover them as ordered, exact-file subtasks on the preserved candidate. Each
gets a fresh Astra session and a bounded budget; fresh review must verify its
acceptance condition. Stop on no progress or exhausted recovery, retain human
gates, and never grow a recursive repair chain. Integrate only when the whole
parent passes. TASKS defines numeric bounds, paths, control commands and the
reusable worker prompt.

**Resume correctly:** budget/deadline stops preserve the candidate and its
stage. A completed implementation continues at validation/review in the next
bounded batch, with prior token usage retained in history. It does not consume
a code-repair attempt just because time or quota ran out. Continuations are
bounded; exhausted failures become BLOCKED with evidence while independent
ready slices continue. Locks, explicit pause, quota and scope are enforced;
no reset loop or unbounded promise to finish everything in one run.

**Make improvements visible:** integrate into the un-checked-out
`codex/terra-batched-implementation` branch, then fast-forward the normal
`codex/terra-live` checkout only if its tracked files are clean and it has not
diverged. Preserve existing edits and report a skipped sync. Never move a
checked-out branch ref without updating its files/index. Restart `make desktop`
to run the latest integrated app. Remove successful dedicated worker worktrees;
keep unresolved work recoverable.

**Finish engineering without faking acceptance:** Q01a/Q02a/U07a–b/Q03a/Q03b prepare
musical, Logic, visual and clean-install evidence independently of the final user
decisions. New video plumbing uses owned fixtures and fake backends in ordinary
tests; real local inference is a separate bounded host check. V24/V33 retain
actual visual decisions and a hosted pilot retains its spending authorization.
Local model quality, user listening and TABI identity cannot be approved by tests.

The scheduler notifies on integrated changes, new failures or required input and
stays quiet during unchanged activity. Local scheduled runs require the computer
and app to be running. See the [official automation documentation](https://learn.chatgpt.com/docs/automations?surface=app).
