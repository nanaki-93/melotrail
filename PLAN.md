# Melotrail improvement plan

Updated: 2026-09-11. Status: implementation underway in bounded slices; musical acceptance pending.

This is the only roadmap. [TASKS.md](TASKS.md) owns implementation order and
status. It replaces the MIDI Core and UI task suites; their history stays in
Git. Existing working behavior is the starting point, not work to repeat.

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

TABI animation is a second, downstream workstream. Plan an **optional separate
video companion**, with its own asset library and jobs, consuming a finished
Logic Pro soundtrack. This recommendation keeps media generation and encoding
out of the MIDI application. The video backlog is concrete but conditional;
this planning request does not launch paid generations or publish anything.

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
7. Kotlin/JVM owns the app. No Python service, audio processing, model runtime,
   video encoder or publishing dependency belongs in the MIDI application.
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
  -> Optionally create a TABI video from the finished soundtrack
```

Six MIDI destinations remain: Project, MIDI, Structure & Harmony, Arrange,
Review, Export. A common song map, selected occurrence and persistent player
connect them. Draft playback does not require per-role acceptance. Export uses
only current accepted work.

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

## 9. TABI video direction

The existing asset prompt asks for a very large library; the old future plan
only promised still-image pan/zoom. Neither is an adequate first implementation
of the requested generative character animation.

Build one cohesive pilot before producing dozens of assets: approved TABI
identity, one train interior, day/dusk/night scenery, a few props and several
short restrained action loops. First compare the four pastel art directions in
[TABI video](docs/TABI_VIDEO.md#pastel-style-exploration-and-reusable-generation-brief)
against the newly supplied pastel references. The current visual direction is
simple, cozy and zen, with sparse artistic scenery and a limited palette;
TABI may be recolored to match. A chosen style still requires actual user review.
Use image-to-video with locked references for
blink/breath/write/look-out actions, and deterministic layered/parallax
composition for most of the duration. Reject identity drift and unstable loops;
do not ask a model to reinvent the entire character and train for every scene.

The companion's input is the musician's finished soundtrack and optionally an
immutable MIDI export manifest for scene suggestions. MIDI contains no final
sound. Preview and output must share timing, crop and transition calculations.
A bounce's lead-in/tail or changed tempo requires explicit video alignment.

The full design, job safety, generation-cost controls, commercial-use checks,
YouTube delivery considerations and pilot gates are in
[TABI video](docs/TABI_VIDEO.md). Long compilations, multiple providers, vertical
variants and direct upload wait until one full-song pilot is convincing.

## 10. Delivery order and acceptance

| Wave | Delivery | Exit evidence |
| --- | --- | --- |
| Foundation | F01, M01, then F02–F06 | Baseline MIDI regression pack; actual dependency map; small JVM-only build with no legacy runtime |
| Visible workspace | U01–U03 and U02 transport work | Real aligned MIDI lanes and compact persistent controls; input workflow intact |
| Musical preparation | M02–M03, U04 | Explainable melody/harmony context, explicit chord durations and safe song extent |
| Song arrangement | M04–M09, U05–U06 | Melody-compatible piano, deliberate section plan, coordinated roles and targeted repairs |
| Validation | U07, Q01–Q03 | Visual approval, improvement on frozen music cases, fresh Logic checks and clean build/install |
| Optional video | V01–V07 | Isolated companion, cost-bounded generation, identity-consistent pilot with finished soundtrack |

The user chose on 2026-09-11 to perform manual listening, scores, Logic tests
and visual/video review only after all unpaid engineering. TASKS parks these
gates as WAITING_USER and excludes them from automatic attempts. Complete
Q03a (clean build/startup), V07a (optional companion handoff), then Q03b (final
evidence and review handoff). Release and production authorizations stay pending. Never claim the current 5/10 result has improved before comparison.

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

**Remaining engineering order:** Q03a → V07a → Q03b. Reuse completed MIDI,
UI and companion implementations. Q03b refreshes artifacts against the integrated
code and presents one final manual-review entry point in Validation. User scores,
Logic playback and foreground compositor capture run afterward; ordinary automated
tests, builds, frame/semantic checks and independent code review remain per-task.

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
decisions. The selected unpaid companion work uses a separately built
`companion/` directory, owned media and provider fakes until actual generation
is budget-authorized. Asset/rights and paid-pilot gates apply to production media,
not to independent job/preview/encoder code. Final listening, visuals, Logic and
TABI pilot approvals remain real human decisions. Public upload is separate.

The scheduler notifies on integrated changes, new failures or required input and
stays quiet during unchanged activity. Local scheduled runs require the computer
and app to be running. See the [official automation documentation](https://learn.chatgpt.com/docs/automations?surface=app).
