# Validation and evidence

Owner: automated checks, real listening/visual acceptance and Logic Pro evidence.
Implementation status is in [TASKS](../TASKS.md). The old participant/holdout
queues are superseded; their incomplete gates are not passes.

## Final manual review

Per the user's 2026-09-11 decision, run listening scores, manual Logic tests,
foreground compositor capture and human visual/video review after all unpaid
engineering. Q03b refreshes the integrated evidence and supplies one review entry
point here. Until then U07, Q01–Q03 and production video approval gates stay
WAITING_USER; missing human evidence must not trigger automatic repair attempts.
Automated tests/builds, semantic checks, real-window frame replay and independent
code review continue for each implementation. Paid generation and production
asset use still require their explicit budget/rights/identity authorization.

## Required automated checks

```bash
make test
make build
git diff --check
```

Run focused suites for the task as well. `make build` includes Gradle check;
current document links and retained-reference integrity are JVM tests. No
function-by-function JSON inventory or Python documentation gate is required.
Report actual executed versus cached checks. A clean release check must run in
an isolated checkout and prove native install/startup with no legacy worker,
sound library, external model or network prerequisite.

| Boundary | Required evidence |
| --- | --- |
| Import | SMF 0/1, metadata defaults/maps, malformed input, velocity-zero note-off, unsafe pairing, polyphony, source bytes/digest unchanged |
| Authority | Exact PPQ/tempo/meter, chromatic chords, unequal/sub-bar windows, no gaps/overlaps, repeat identity, explicit extent/padding, scoped invalidation |
| Generation | Fixed-version determinism, legal ranges/harmony/boundaries, meter-aware patterns, melody space, complete grooves, bounded search, meaningful alternatives |
| Whole song | Plan intent across sections, neighbor/repeat dependencies, explicit rests versus failures, bass/kick coordination, ending and no global rewrite |
| State | Atomic writes, locks, no partial batch use/undo, stale async completion, scoped cancellation/retry, reopening, confined paths and artifact identity |
| Audition | Real synth/default endpoint, single session, play/pause/seek/loop/mute/solo, no stuck notes/resources, latest-wins preview, zero project writes |
| Export | Accepted-only snapshot, shared origin/end, channels/track names, allowed expression, manifest digests/privacy, semantic re-import, no overwrite |
| UI | Real-service six-page workflow, exact data in lanes, focus/keyboard, resize, image comparison failures and real user visual decision |
| Cleanup | Actual dependency paths scanned, no legacy routes/schema/audio/worker/Python runtime, verified data deletion and measured reduction |

Keep deterministic fixtures small and owned. Test actual outcomes/invariants;
source-text absence scans supplement behavior tests rather than replace them.
Every fixed bug receives a regression that would fail before the fix.

## Optional companion preflight

V01's companion checks are separate from the MIDI application. The coordinator
runs `node tools/companion-check.mjs`, dispatching the boundary check, native
regressions and Swift release build. No Gradle companion shim is needed. The
coordinator also runs normal MIDI `make test`/`make build`; the root settings do
not include the companion.
Record the actual macOS/Swift build, output directory, elapsed encode time and
bytes for any new encode; do not reuse the owned one-second fixture as full-song
evidence. The short probe must decode its
first/middle/final frames, one video and one audio stream, one-second duration,
and the shared PCM timeline.

The coordinator must also confirm that the regression's fresh temporary outputs
are outside the repository and that a failed/extra command-line path is rejected.
These checks prove only the declared local fixture configuration. A 1920x1080
master, stereo Logic bounce, H.264/AAC delivery, interactive playback, signed
distribution, provider submission and final A/V pilot require their assigned
V04–V07 evidence; no test substitutes for rights, budget or human visual review.

V02a native checks cover manifest reload, exact approved versions, missing/changed
media, dimension mismatches, duplicate/missing pins, traversal/symlink escapes and
no-overwrite manifest publication. Production rights/identity approval remains V02.

V02b native regressions cover immutable local imports, measured pixel alpha,
mask dimensions/type, normalized placement and scene/identity review findings.
A 30-second import watchdog catches nonterminating filesystem ancestry walks;
owned Git directory/file markers, bare repositories, symlink aliases with nonexistent children and
symlinked originals must reject before writes. Production asset approval remains V02.

V06a native regressions use real owned child processes: Unicode/spaced paths,
collision and source preservation, crash/disk errors, output limits, empty output,
timeout/cancel (including before launch), finite limits and staging cleanup.
Input-mutating encoders cannot change originals; parent-exit/timeout/cancel
fixtures stop real writing descendants. Noisy diagnostics/progress stay bounded,
blocked callbacks cannot stall supervision, and credentials are redacted. This tests
the process/publication boundary; V06 still owns episode codec/parity validation.

V06's native episode regressions encode owned 320×180 mono (one second) and
1920×1080 stereo (twelve seconds) compositions to ProRes 422/PCM MOV at 30fps.
Decode first/final frames, normalize
both images to sRGB RGB over black, and require mean absolute channel error ≤32
on the 0–255 scale. Verify one video/audio stream, dimensions/cadence, continuous
PCM timestamps, duration and A/V drift within one frame. Compare decoded
32-bit float PCM source/output sample digests exactly, retaining channel count
and sample rate in provenance. Transparent pixels must
not expose uninitialized encoder memory. Video and audio are fed in timeline
order, with output-size/disk/deadline/cancel checks through finalization.
Late cancellation, tiny timeout/output limits, insufficient disk, stale assets,
Unicode/spaced names and video-only/report-only collisions must preserve sources
and previous outputs and leave no completed failed delivery. Provenance records
the output/soundtrack/composition digests and exact asset pins; the report is
reserved before the video publication point. Set `MELOTRAIL_EPISODE_EVIDENCE` to
an external directory to retain fresh MOV/report pairs from the native check.
Repair evidence: `~/.codex/melotrail-terra/v06-repair-evidence/` (native log,
retained outputs and MIDI test/build logs). These owned technical
fixtures do not approve a full-song 1080p master, stereo Logic bounce, production
TABI identity, musical quality, rights or upload; those remain V07/user gates.

V03a uses fake providers only. Real concurrent processes and threads exercise identical
requests, independent requests, in-flight/cost rejection, preserved provider IDs
and directory aliases against one ledger; durable job and provider-call counts
must agree. Sequential checks cover unknown quotes, retries, cancellation,
uncertain submission and reopen. No paid request or production approval is made.

V03b checks use an injected HTTP transport and owned video bytes. Valid owned MOV/MP4
content must survive a `.tmp` staging name through quarantine and manual import;
invalid clips, HTTP partial/error responses, size/digest mismatches and repository
aliases reject without publishing staged files. Credentials remain absent from
output-host requests and errors. A custom URLProtocol exercises the concrete
URLSession collector: reject HTTPS downgrades before following them, reject
insecure final URLs, and abort declared or streamed oversize bodies. Matching
hash symlinks reject; oversized/invalid-ratio stills never reserve budget or call
HTTP. Source-swap fixtures prove preparation rejects changed bytes before ledger
creation and freezes verified upload bytes against later file replacement.
No live provider request is part of validation.

V04a native regressions use the owned one-second MOV as a finished-soundtrack
fixture plus a temporary digest-pinned fixed-tempo MIDI export manifest. They
prove repeatable rational section/frame ranges, explicit lead-in/tail, absent
manifest fallback, digest rejection, shorter/longer bounce and changed-tempo
mismatches, and byte preservation for the soundtrack, manifest, and a protected
project sentinel. Decoded rational validation, overlapping/gapped manifests and changed soundtrack
rejection are also covered; the read-only `plan-timing` CLI prints the shared JSON plan.
This is timing-plan evidence only: composition, preview,
encoder parity, and human A/V approval remain V04–V07 work.

V05b's release-window regression launches the native editor with an owned
composition request, selects a scene, plays across a boundary, seeks and closes.
It verifies player/observer cleanup and captures both the usable window and an
invalid-input window. Capture guards reject clipped inspector/transport/scene
controls. The Swift-owned loading window disables AppKit release-on-close;
this launch/close path covers the recovered release-only ownership crash.
Evidence: `~/.codex/melotrail-terra/v05b-repair-evidence`; fixtures are technical
media, not production TABI identity approval. Final human visual approval remains pending.

V05c exercises real crop/motion/crossfade controls and compares rendered pixels
at boundaries against the shared renderer. An independent bitmap oracle proves
crop moves the complete scene, including its mask, together. Minimum-window
capture verifies the inspector viewport and scrolls every editing field/button
fully into view. Before/after crop and motion PNGs are retained with the owned
fixture. Invalid edits preserve the accepted plan, source bytes and sole player.
Evidence: `~/.codex/melotrail-terra/v05c-repair-evidence`.

V05d drives native save/open and local Stop/Play/Restart controls. It compares
saved/reopened pixels at the same soundtrack frame, checks transactional restore
rollback on refresh failure, and retains the original player. Reopen rejects
changed soundtrack/asset bytes and malformed documents, then recovers after
repair. Save rejects unrelated files, protected inputs and symlink aliases.
Owned ledger fixtures distinguish actual/estimated costs and explicitly label
uncertain submission progress. Evidence:
`~/.codex/melotrail-terra/v05d-repair-evidence`; no provider requests or production
approvals are part of this check. V05 completes the technical keyboard/UI gate below.

V05's release-editor check uses the same owned multi-scene request at
1536×1024, 1280×900 and 720×900. It retains
`editor-1536x1024.png`, `editor-1280x900.png`, `editor-720x900.png`,
`editor-controls.png`, `editor-window.png` and `editor-observations.json` under
the fresh companion `.build/v05b-editor-evidence.*` output. The JSON records
each preview/inspector/scene-strip/transport rectangle, whether the compact
stacked layout applied, real keyboard frame seeking, the last preview frame and
the exact final audio-tail end frame. The release check drives scene selection, play and seeking; the native
controller regression drives crop/motion/crossfade editing, save/reopen and
field-safe keyboard handling; it also checks shared-resolver pixels at scene boundaries and source
digests. These are owned-fixture engineering measurements, not production TABI
identity, artistic, rights, listening or paid-pilot approval.
Repair evidence is retained at `~/.codex/melotrail-terra/v05-repair-evidence/release`.
The captures use exact content sizes in points (Retina PNGs use backing pixels),
with an evidence-only override of the screen-height cap. The adapted reference-08
geometry is checked within 8 points: 20-point side margins, a 280-point right
inspector at wide sizes, and a full-width 150-point inspector below the preview
at 720 points. Output frame dimensions remain unchanged on resize. Inspector
controls retain their native field editor during seeks; real typing and arrow
function-key modifiers are exercised. The owned one-second fixture ends at
frame boundary 30, with final preview frame 29 at 30 fps; it is not a production
TABI episode or a listening approval.

## Scheduled runner checks

For runner changes, run `node --test tools/terra-runner.test.mjs
tools/terra-throughput.test.mjs` in addition to the application gates. Fixtures
use real Git worktrees, commits and child processes with fake model/build
executables. They must prove shared task/time/token bounds, preserved retries,
implementation-session reuse, fresh review, bounded evidence, disjoint parallel
ownership, serial integration and validation of the combined candidate. A live
CLI smoke check must confirm persistence/resume when its argument wiring changes.
Keep full transcripts outside model input; review only the current candidate's
explicit completed check logs. Passing runner checks does not approve MIDI music,
Logic behavior or visual output.

Finding recovery additionally proves ordered partial repairs without premature
integration; one fresh session and fixed file scope per finding; independent
acceptance resolution; and preservation of the original implementation, diff,
check errors and usage history. Regressions reject no-progress loops, malformed
or omitted findings, scope/checkpoint changes, unauthorized/human recovery,
exhausted time/tokens and replay after an interrupted attempt. Evidence for A03
is retained at `~/.codex/melotrail-terra/finding-recovery-evidence`.

## Musical evaluation

The user's 2026-09-06 **5/10 average** is qualitative baseline feedback: timing
good, piano/melody fit inconsistent, full-song structure difficult. No case-level
scores or identified bad bars were supplied. Do not invent them.

M01 creates owned development fixtures: held/close melody-piano intervals,
short passing tones, expressive/pedaled melody, low register, dense/sparse phrases,
unequal harmonic rhythm, repeated chorus/bridge, 3/4, 6/8, pickup/silence and
final boundaries. Freeze authority/style/seed and baseline output hashes.
Use these to locate bugs; they cannot be renamed as unseen acceptance songs.

At each meaningful engine milestone, provide a short baseline/new MIDI pack
with the same melody, harmony, timing and preview/Logic instrument mapping.
Include isolated piano+melody and full arrangement loops at identified bars.
Record the instrument setup; differing patches or role levels can confound
judgment. Alternate/blind A/B ordering where practical, and retain failed takes.

Q01 evaluates five varied full-song projects, at least three unseen before the
final evaluation starts. User-owned or clearly licensed material only. Freeze
hashes and authority before generation. No tuning to the unseen set before its
first scores. If fixes are made from a failed case, reclassify it as development
and replenish the unseen minimum for the next final evaluation.

Score **1–10** for piano/melody fit, bass support, drums/groove, role interaction,
section development/transitions, usefulness of repair alternatives and overall
readiness to continue in Logic. Give a reason and exact bars for a low score.
Melody preservation is an automated exact invariant, not a taste rating.

Proposed release thresholds:

- Zero changed protected melody events and zero unresolved structural blockers.
- Median overall **and** piano/melody-fit scores at least 8/10; every song's
  overall and piano/melody-fit score at least 7/10.
- No core-role, interaction or structure score below 6/10; no severe clash,
  broken transition or timing fault requiring external MIDI repair to proceed.
- Median first-draft-to-Use time at most ten minutes, excluding authority entry
  and Logic instrument selection; record those excluded times separately.
- Same final engines pass all cases; averages cannot hide a failed song.

These thresholds are targets, not observed results. The user is the minimum
reviewer for this personal product; another musician's feedback is useful, not
a development blocker. A failure gets a scoped task and regression, not an
unbounded generator rewrite or lowered acceptance threshold.

Compact evidence record (JSON or table in ignored output; summarize real results
here at release): source/authority hashes, ownership, seen/unseen designation,
build/engine/style versions, seed, candidate/snapshot IDs, instrument mapping,
bar range, all scores/reasons, repair count/time, reviewer/date and decision.

### Frozen musical evaluation commands (Q01a)

`musicalEvaluation` is a local Kotlin command using the real draft → Use →
accepted-only exporter in newly created evaluation projects. It never confirms
analysis/style proposals or writes a supplied project. Prepare source, exact
authority and a confirmed arrangement plan in the app first. Freeze the complete
supplied set before generating final candidates; do not tune unseen songs before
their first ratings. New directories require an existing parent and must be
outside supplied projects, previous evaluations and frozen sets.

```bash
./gradlew musicalEvaluation --args="freeze '/path/to/request.json' '/path/to/new-frozen-set'"
./gradlew musicalEvaluation --args="export '/path/to/new-frozen-set' '<printed-sha256>' '/path/to/new-evaluation'"
./gradlew musicalComparison --args="'/path/to/new-development-comparison'"
```

The first command prints the SHA-256 of `frozen-set.json`; retain it separately
and pass that exact hash to export. Freeze copies the verified source/import
report, exact supplied project document and a current-schema input project
containing its original authority/plan without decisions or output history.
Optional baselines are copied from an explicitly named existing accepted export
snapshot of the supplied project. They must cover all active scopes with the
same source and confirmed authority, and retain a saved full draft binding those
exact candidates to the requested generation seed and style. Mismatched or
unverifiable baseline settings reject freeze/export; choose the original settings
or omit that baseline. Unavailable baselines are labeled, never regenerated by
an old engine. Source/project/settings hashes, compiled engine
and runtime-library hashes, catalog versions, build label, style, seed, loops,
instruments, ownership and exposure declarations bind the set. Export rejects
changed files, settings, versions, symlinks and existing destinations.

Request JSON has `schemaVersion: 1`, `evaluationId`, `buildIdentity` (record the
actual commit and whether it has uncommitted changes), and `cases`. Each entry
has `projectDirectory` (absolute or relative to the request file) and `case`:

Use `MidiCoreEvaluationCase` in
`src/main/kotlin/app/melotrail/application/MidiCoreMusicalEvaluationModel.kt`
for the exact case fields. Record actual ownership/exposure, full-song/synthetic/
tuned flags, variety, style/seed, inclusive piano+melody/full-arrangement bar loops,
one real patch/level for each of melody/chords/bass/drums, and an optional
`baselineSnapshotId`.

Classifications are
`FINAL_UNSEEN`, `FINAL_SEEN`, `DEVELOPMENT`; ownership is `USER_OWNED` or
`CLEARLY_LICENSED`. Synthetic, partial or tuned material must be development.
Identical note streams cannot count twice as final songs or appear in both sets,
including MIDI with changed metadata or channel wrappers. Development-only
variants remain allowed. The command validates declarations and integrity; it
cannot establish rights, full-song variety or unseen history on a human's behalf.

Output separates `final/<case>` and `development/<case>`. Each case contains
`current/`, optional byte-preserved `baseline/`, the isolated `project/` and blank
`scores.json`; copied frozen inputs stay under `frozen/`. `evaluation.json`
records package digests, candidate/snapshot IDs, role-generator versions and any
failed attempts. Source preservation and semantic re-import use the existing
exporter. The same frozen inputs/runtime produce identical MIDI, package
manifests and score templates; working-project candidate/draft timestamps are
provenance and may differ. The fixed package timestamp is not a listening date.

`review.md` gives piano+melody/full-song loops, the recorded instrument setup,
alternating disclosed A/B order and missing-song counts. Fill all seven 1–10
scores, reasons/exact bars, repair count/time, excluded authority/Logic setup
times, reviewer/date and decision in each score form. `scores` rates current;
`baselineScores` rates the supplied baseline, when available. Preparation's automatic
Use authorizes only its disposable copy and does not measure human review time
or approve the musician's arrangement. Exit code 0 means preparation ran, even
if songs/ratings are missing; 1 rejects a request or changed input, and 2 retains
generation failures in the published report. No failed case is silently omitted,
retried with another seed or replaced with a planned rest.

No owned final songs were supplied for this slice: **five final songs and all
three unseen songs remain missing**. The only bundled request is the empty
`src/test/resources/fixtures/q01-evaluation/empty-request.json`. The M01 command
uses its existing immutable baseline resources and synthetic fixtures, with
runtime hashes in `versions.json`; it cannot populate the final set. Reclassify
a tuned final case as development and replenish the unseen minimum for a new
evaluation. Real ratings remain Q01; actual Logic import/play/reopen remains Q02.

Q01a recovery checks and generated packets are retained at
`~/.codex/melotrail-terra/q01a-q02a-repair-evidence/`. Focused evaluation,
comparison, exporter and draft regressions, `make test`, `make build`, diff check
and independent review validate preparation. `q01-frozen/` and `q01-evaluation/`
record the empty supplied set; `q01-development/` contains synthetic M01 pairs.
After Q02a changed export filtering, `q01-current-frozen/`,
`q01-current-evaluation/` and `q01-current-development/` repeat preparation on
the combined implementation; earlier packets retain their original runtime identity.
No final-song freeze or human score is claimed.

## UI and performance acceptance

Follow [UI guideline](UI_GUIDELINE.md). Six ready pages plus relevant empty,
blocked/error/progress states at 1536×1024, 1280×900 and 720×900. Test short-window
resize and native scaling. Record actual/expected/diff paths, font/density,
geometry/contrast/focus results and the approved baseline change when applicable.
A comparator must demonstrably reject changed layout/colors/lanes/radii.

User review scores hierarchy, reference fidelity and usability at least 4/5 for
each page, with exact comments for deviations. Passing screenshots is not passing
musician comprehension. Record actual completion of create/import/context/style/
draft/repair/use/undo/reuse/export without project-file edits.

Targets measured on a recorded reference machine using a fixed 64-bar,
four-role, 4/4 fixture and an additional 3/4/6/8 smoke set:

- Authority-ready draft playback: at most three primary actions.
- Section repair: select section + action; role repair at most three actions.
- Preview plan preparation p95: cold ≤1,000 ms; warm ≤300 ms, at least 20 samples.
- Measure actual audible onset separately after output warm-up; target ≤300 ms
  warm / ≤1,000 ms cold. In-memory preparation is not acoustic-onset evidence.
- Full 64-bar draft generation target p95 ≤10 seconds; instrument separately
  from UI wait time. Capture event count, disk state and cancellation latency.
- Long work stays off the Compose event thread. The collapsed player remains
  visible and within its height budget; rapid previews never overlap sessions.

Targets missing on the reference machine require profiling and an explicit
budget decision with evidence. Agents must not claim measurements they did not run.

### U07b responsiveness and visual-review preparation

Recovery evidence: `~/.codex/melotrail-terra/u07b-repair-evidence/u07/`.
Open `visual-review/index.html` for all six pages: 66 unchanged U07a target/
actual/difference comparisons at three guideline sizes, original references,
and blank per-page scores. **Human visual approval remains U07; acoustic onset
is UNMEASURED** because the real audition controller uses a silent output adapter.
No golden, musical score or Logic result is changed by these checks.

Run on a graphical host with the configured JDK before `make test`, `make build`
and `git diff --check`; force fresh measurements with these desktop selectors:

```bash
./gradlew :desktopApp:test --rerun-tasks \
  --tests '*MidiCoreResponsivenessTest' --tests '*MidiCoreNativeResponsivenessTest' \
  --tests '*MidiCoreVisualReviewTest' --tests '*MidiCorePinnedVisualTest' \
  --tests '*MidiCoreFocusedWorkflowTest'
```

`responsiveness.json` retains 20 raw samples and nearest-rank p95 for cold/warm
preview preparation (targets 1,000/300 ms), 64-bar draft generation (10,000 ms),
UI completion and cancel-to-terminal latency. Cold means a fresh service graph
and preview cache, not flushed OS/JIT caches. Generation includes validation and
publication; UI completion includes IO/state delivery but excludes paint. An EDT
heartbeat records queue delay, and use-case/projection assertions reject UI-thread
work. Cancellation follows one published scope; source/candidates, acceptances
and export snapshots survive without publishing an incomplete draft. Separate
3/4 and 6/8 smoke cases explicitly confirm meter; rapid previews prove latest-wins
and one output session. Missed budgets fail after writing evidence.

`native/observations.json` records 28 Skia frame replays from a real window
with a 256-bar, 8,192-note, 64-occurrence song and long Unicode names. It requests
1280×720, 1024×768, 720×900 and back to wide; actual client size and density are
recorded. Only excess beyond usable display/frame bounds is reduced. Checks cover
one player/inspector, lane alignment, 48 dp transport controls, selection surviving
resize and scrolled/focused draft creation. Full-client layer bounds and image
size/header/footer/palette guards remain enforced. Replay verifies rendered frame
content; its `onscreenCompositorCapture` is explicitly `NOT_MEASURED`.

Actual desktop pixels require a separate fresh check on a visible, capturable desktop:

```bash
./gradlew :desktopApp:nativeDesktopCapture
```

This is mandatory during the final U07/Q03 manual visual/release review, independently of
`make test`. It uses Robot with the same guards, **without fallback**, writing only
`native-screen/`; failures retain `last-capture.png` and INCOMPLETE observations.
By the user's final-review order it is not a prerequisite for Q01a/Q02a/Q03a/
Q03b or other independent engineering; its failure is retained for final review. Earlier U07b
screen captures remain historical: subsequent scheduler runs captured wallpaper,
so they cannot establish current compositor success. Recovery evidence is in
`~/.codex/melotrail-terra/q01a-q02a-repair-evidence/`; the explicit screen check
still fails on this host, while real-window frame replay passes. Human visual
approval remains outstanding.

Reports include exact input-file hashes, Git identity and actual machine facts;
verify matching `inputTreeSha256` across timing, meter, native and review reports.
The repair host is Apple M5 Pro / 48 GiB, macOS 26.6.2, JDK 21.0.11, APFS,
with native density 2. Interrupted measurements remain INCOMPLETE. Consult the
retained raw reports for timings and actual display-constrained dimensions.

M08a repair evidence is retained at
`~/.codex/melotrail-terra/m08a-repair-evidence`. Reduced-density held-melody
regressions keep Bass attacks at ticks 0 and 960 across C/F windows at densities
0.25, 0.5 and 1.0 for moving and sustained-root patterns. An incompatible 0.01
ceiling produces a density finding; zero density stays silent. Core/desktop
checks also cover chord spacing, approach resolution, shared groove in 3/4 and
6/8, deterministic role fingerprints and the existing full-draft caller. Planned steady grooves preserve intro/outro
zero-support limits; pickup advisories share generator eligibility, including
restrained and compound-meter cases.
These are engineering checks. Full-song M08 comparison packages and Q02 Logic
listening remain separate pending work; no musical score is inferred.

M08b adds deterministic Drum checks for a two-bar phrase pickup into the next
section, a one-bar harmony edge without inferred fill, quiet current/next
Intro/Outro pickup suppression, and repeat-family whole-groove variation. The coordinator still
owns execution of these focused checks plus the required full test/build gates;
these automated checks do not establish a listening or Logic result.

U05a captures at `~/.codex/melotrail-terra/u05a-implementation-evidence`
show the reference-adapted four MIDI lanes, five style choices and full-draft
CTA at 1536×1024 and 1280×900. Tests check entire style-card bounds before
scrolling, including the 24-point gap above the persistent player; 720×900
checks selection and CTA reachability by scrolling. The selected-section
inspector distinguishes accepted planned rests from rests in an unaccepted draft,
and current accepted candidates retain precedence over a simultaneous draft rest.
Full workflow/preview repair behavior remains U05b/U05; these captures are
engineering evidence, not a recorded human visual approval.

M08's retained baseline/current comparison packages are at
`~/.codex/melotrail-terra/m08-repair-evidence/comparison-packages/review.md`.
An explicit uncached run of `MidiCoreComparisonHarnessTest`,
`MidiCoreBassGeneratorTest` and `MidiCoreDrumGeneratorTest` generated three
pairs: repeated chorus/bridge, 3/4 and 6/8. The coordinator verified all 30 MIDI
file hashes, six export-manifest hashes and matching protected-source hashes.
Frozen baseline metadata retains its historical authority hash; the harness
compares current-contract authority without rewriting the baseline files.
Each current package has `coordination.json` with terminal role states, planned
rests and exact role-generator versions. The four-bar case preserves melody
and all MIDI track end markers at tick 7680 while its final Bass/Drums scopes
are planned rests from tick 5760. Focused/full/build logs and digest verification
are retained beside the packages. Human A/B ratings and Logic playback remain
Q01/Q02; these fixtures do not establish musical acceptance.

M09 repair evidence is retained at `~/.codex/melotrail-terra/m09-repair-evidence`.
The real-service owned two-occurrence workflow previews without writes, repairs
all affected neighboring scopes, checks immutable piano-boundary hashes and
rejects unaccepted dependencies, tampered boundary metadata and colliding batch
history without partial writes. Complete Use commits once; lower-register
intent/offset identity survives publication, acceptance and reopen. Source and
prior candidate bytes remain identical. The retained project contains baseline
and repaired accepted-only export snapshots for equal-position comparison.
Core tests reject cosmetic alternatives and stale/rejected choices; the full
suite also covers single-scope repairs, limits, locks, cancellation and audition.
Retry coverage verifies one plan confirmation and identical generation settings
across repeated attempts after a no-result failure.
These checks do not score musical usefulness. For Q01/Q02, compare the snapshots
with identical Logic instruments, verify melody/role origins, piano continuity
at bar 2, lower-register intent and exact end at tick 3840, then save/reopen.
Arrange buttons and final UI flow remain U05b/U05; musical and Logic approvals
remain pending human evidence.

U05b recovery evidence is retained at `~/.codex/melotrail-terra/u05b-repair-evidence`.
Focused StylePreview, ArrangePage, Workspace and real-workflow checks execute
with `--rerun-tasks`; full test and build runs also force execution. The one-bar
fixture loops its real 1920-tick window without project writes. Three-size
Arrange captures show scoped repair preview, Apply/Cancel and the persistent
player. The failed-Apply regression distinguishes a saved plan from unaccepted
alternatives and dispatches the same repair on Retry. These are technical checks;
full Arrange completion remains U05 and human musical/Logic approval stays pending.

U05 recovery evidence is retained at `~/.codex/melotrail-terra/u05-repair-evidence`.
Uncached focused checks and full test/build execution cover a real three-action
ready-authority → playing draft path, keyboard Enter on Confirm plan & create,
plan-confirmation cache parity, changed-plan silence and protected melody.
The intro regression uses three separate occurrences, rather than mistaking one
three-bar section for an intro. Injected post-confirm generation failure retains
the saved plan and same draft retry; cancellation at preparation and playback
boundaries stops both the actual transport and displayed state. A deterministic
clock reproduces the intermittent repair-batch failure at mixed fractional
timestamp precision; both history lists now compare parsed instants. Batch
accept/reopen and draft-history schema roundtrips pass, while reversed
chronology remains rejected. Three-size
proposal/playing-draft captures were inspected. For Q02, audition the complete
draft before acceptance, then Use/export with identical Logic instruments and
verify melody preservation, planned rests, section boundaries and role alignment.
Musical/visual approval remains human evidence.

U06a recovery evidence is retained at `~/.codex/melotrail-terra/u06a-repair-evidence`.
Focused regressions cover exact Draft/Accepted identity including planned rests,
atomic mixed-draft Use/Undo/reuse, section/loop continuity, locked-scope routing
and melody-inclusive alternative playback. The two-section blocker fixture now
sets its confirmed arrangement end consistently. Candidate lanes require the
player's exact immutable candidate identity; changing selection cannot display
another candidate's notes or attribute its unbound error to the playing one.
Real-service Review captures at 1536×1024, 1280×900 and 720×900 were inspected.
For Q02, compare alternatives against the same protected melody and section loop,
then Use/Undo/reuse and verify accepted-only export in Logic. Musical, Logic and
visual approval remain pending human evidence; final Export handoff belongs to U06.

U06 candidate validation is **PENDING_COORDINATOR**. Focused desktop selectors:
`MidiCoreExportPageTest`, `MidiCoreFocusedWorkflowTest`, `MidiCoreReviewPageTest`,
`MidiCoreWorkspaceTest`, `MidiCoreArrangePageTest`, `MidiCoreWorkspaceShellTest`.
Retain the root `MidiCoreMidiPackageExporterTest`, `MidiCoreAcceptedSongAssemblyTest`
and `MidiCoreArrangementDraftTest` checks, then run the required full test/build
and diff checks. No U06 test pass or visual approval is claimed here.
The three-size real-service workflow now applies the Lower piano register repair,
creates the whole draft under that confirmed plan, uses/undoes/reuses it, exports,
reopens and publishes a second snapshot while comparing the first package's bytes.
The complementary two-section standard-style fixture covers accepted whole-song
Bass/Drums rests, inventory, atomic Use/Undo/reuse and omitted role files.
Transport uses a test port; these checks cannot establish audible playback quality.
After successful coordinator execution, captures and ready-to-import packages are
under `desktopApp/build/test-results/midi-core-focused-workflow/` and
`desktopApp/build/test-results/midi-core-export-rest/`, each with `reference-wide`,
`wide`, `compact` and a `logic-package/` per size. Inspect the actual PNGs beside
references 04/09; these output paths are preparation targets, not passed evidence.
For Q02, import each package at bar 1, compare complete-song and individual-file
alignment, verify the protected melody and exact ending, and verify Bass/Drums
are absent in the rest package. Check playback and Logic save/close/reopen with
fixed instrument choices. Record the real user's result before release.

## Logic Pro procedure

Rerun relevant cases when import/timing/expression, assembly, rests, export or
workflow handoff changes. GUI-only layout changes need UI validation, not a
fabricated DAW pass. GarageBand remains unverified/outside support.

Fixture set: SMF 0 melody, SMF 1 + meta track, short 1/2/3-bar songs, pickup and
initial silence, sub-bar/unequal chords, CC64/pitch expression, repeated sections,
3/4 and 6/8, optional padded end, inactive section/whole-song role, final notes
and fills at the exact end. Include the six historical fixtures below where
still valid. Update fixtures for changed contracts rather than reusing old
hashes as evidence of a new exporter.

### Q02a current matrix preparation

The coordinator runs the current command from the repository root, with a **new**
destination for each preparation:

```bash
./gradlew prepareLogicMatrix -PlogicMatrixDirectory=build/q02-logic-matrix/current-01
```

This test-classpath command calls the current import → explicit authority/padding
→ generation → acceptance → export services, reopens each owned project and
exports a second snapshot. It adds no production runtime or Logic automation.
Existing destinations and symlink ancestors are rejected; failures retain an
unpublished `.<destination>-incomplete-*` staging directory for inspection. No existing project,
source, accepted candidate, export or historical packet is replaced or cleaned.

The packet contains `matrix.json`, `review.md`, `SHA256SUMS` and 21 case folders:
20 current packages plus the expected rejection of the historical extra-note-track
input. Each positive case retains `source.mid`, its current `project/`, a portable
`package/` and `case.json`. The six historical source fixtures are byte-preserved;
short inputs receive explicit trailing padding. The historical
`smf1-reference-tracks.mid` contains a second note-bearing track and is now an
expected app rejection, with a separate supported meta-only-track probe.

Coverage includes velocity-zero note-off, 1/2/3 bars, pickup and initial silence, 3+1+2+2-beat and sub-bar
harmony, source end distinct from last note, explicit padding, 481 PPQ, whole-song
and introductory Bass rests, CC64/CC11/bend/channel-pressure/channel remapping,
release velocity, repeated occurrences, 3/4, 6/8, and an explicit final drum fill.
The brief sub-bar probe uses explicit sustained role patterns, preserving its
original C 0–240 / G7 240–1920 windows and 480-tick source. The pulsed style
produces an empty Chords selection for this short phrase; that failure is not
accepted as silence or repaired by moving the source/harmony. Its separate
regression requires nonempty sustained candidates at both exact chord starts.
The odd-PPQ package has explicitly accepted rests for all generated roles: import
and export retain 481 PPQ, but the generation grid cannot represent sixteenths.
It does not claim odd-PPQ pattern support. Bank-select CC0/32 and program hints
are omitted from arranged exports; supported expression and original bytes remain
protected. That policy is checked by the expressive channel-remapping case.

`matrix.json` binds the Git commit, binary diff and working input-tree hashes
(including untracked source), compiled classes, runtime jars/JDK, source and
selected-melody identity, confirmed authority/plan, catalogs, candidate versions,
settings/seeds, manifest hashes, MIDI byte hashes and semantic hashes. Every file
is covered by `SHA256SUMS`. MIDI/package manifests and the matrix are deterministic
for the same inputs/build; project lifecycle timestamps are observational and may
differ. The fixed export timestamp is a fixture setting, not a review date.
Re-import checks compare every note/expression field, track/channel order, conductor
tempo/meter/markers, individual-track EOTs, role presence and common origin/end.
Project reopen must preserve state and produce identical MIDI without changing
the first snapshot or source/candidate bytes.

Focused coordinator selectors: `MidiCoreLogicMatrixTest`,
`MidiCoreMidiPackageExporterTest`, `MidiCoreAcceptedSongAssemblyTest`,
`MidiCoreArrangementDraftTest`, `MidiCoreSourceImportTest`, and `JdkMidiWriterTest`,
followed by the required `make test`, `make build` and `git diff --check`.
The matrix test exercises the command and retains a fresh packet under
`build/test-results/q02-logic-matrix/run-*/packet/`; it checks repeatability and
detects corrupted notes/channels/controllers/end ticks, manifest/inventory changes,
collisions and failed publication. Recovery checks and current packages are retained at
`~/.codex/melotrail-terra/q01a-q02a-repair-evidence/`: `q02-focused.log`,
`q02-test.log`, `q02-build.log` and `q02-logic-matrix/`. Independent review
and automated semantic checks validate preparation; actual Logic remains pending.

Use `review.md` for concise complete-file and separate-role imports at bar 1,
instrument assignment, full/loop playback and Logic save/close/reopen. Verify
`SHA256SUMS` before and after review. Keep the packet immutable and record results
in a separate copy of the form, identifying `matrix.json` by SHA-256; attach actual
coordinator check evidence separately. Exact macOS/Logic versions, reviewer/date,
each import/play/reopen result, conditional actions and failure bars remain blank
until a human supplies them. Q02 owns that decision; Q02a does not inherit the
2026-08-28 approval or award a musical/release pass.

M03 requires the current Logic fixture preparation to include an unequal
3+1+2+2-beat harmony case, an odd-PPQ case, a source whose end-of-track trails
its last note, and an explicitly padded arrangement end. These fixtures are
prepared by automated contract coverage only; no Logic import/playback/reopen
result is claimed until a reviewer records it below.

M05 prepares frozen v1/current v5+comping-v1 MIDI pairs in
`build/m01-comparison` via `MidiCoreComparisonHarnessTest`. Engine
v5+comping-v1 retains M04 ranking of the bounded M04a voicing pool
with M02 overlap, register, per-finding metrical prominence, bounded phrase
lookahead, and an explicit authority-bound piano boundary input. It includes
held suspensions crossing onto a new chord’s downbeat. M05a adds meter-anchored,
harmony-clipped comping, authored 3/4 and 6/8 patterns and explicit
unsupported-meter rejection. M05 adds versioned phrase-aware support/answer/
inter-phrase whole-bar rests and final-beat suppression from protected melody
activity. Automated checks publish and reopen a three-bar candidate with a
silent middle bar, verify deterministic replay and unchanged source bytes,
and cover rest-only harmonic windows plus 3/4 and 6/8 bar phase. The generated
`review.md` is the short baseline/new listening pack; use identical MIDI
instruments and levels for each listed piano+melody and full-arrangement loop.
For Q02, import the complete and aligned role files for repeated-chorus/bridge,
3/4 and 6/8; verify protected melody, chord
boundaries and final notes, then compare the listed piano/melody loops with
identical Logic instruments and levels. The unscored `review.md` records hashes
and loop ranges. Listening scores and Logic results remain pending human evidence.
Automated state coverage cancels and reopens a multi-occurrence draft, rejects a
retained Chords candidate whose persisted boundary digest no longer matches the
preceding immutable MIDI, preserves its bytes, and publishes a distinct retry.
Completed-draft assembly and Use must also reject missing or mismatched boundary
evidence without changing acceptances.

The M04 boundary-retry regression uses `steady-road` for its three one-bar
occurrences. The same fixture with `open-sky`/seed 41 failed Drums role validation
in the first occurrence; short-section groove validation remains M08b/M08 work.
This does not count as a musical or Logic pass for that style.

1. Record app build, source/export manifest hashes, exact macOS and Logic version.
2. Import/open complete MIDI at song start; record adopt/retain tempo and meter.
3. Import each role file at the same origin. Check names, role/channel separation,
   expected omitted roles, initial silence, bars/beats and first/final notes.
4. Spot-check melody timing/pitch/velocity/expression against the protected source.
   Check no unexpected program/controller data; assign instruments and a drum kit.
5. Play full song plus loops/solo scopes: no stuck/truncated notes or wrong kit hits.
6. Check section markers (cosmetic display finding permitted), then save/close/
   reopen. Record PASS, CONDITIONAL PASS with exact action, or FAIL.

Timing/note/channel/role corruption is always a failure. A screenshot alone does
not establish full playback or reopen; a real reviewer report is needed.

M07b automated fixtures cover mixed candidate/rest draft audition, atomic Use/Undo,
revision-guarded unlock in both candidate/rest directions after plan edits,
stale-state preservation on Undo, locked-rest authority replacement, downstream
rest-dependency invalidation and acceptance guards, desktop section repair and
preview at three fixture sizes, cancellation/retry and piano-boundary reset
across an inactive occurrence. M07 adds an accepted all-song-rest export fixture:
the inactive role is absent from the complete/song-role files and snapshot,
listed as inactive in the manifest, while Melody and active role files retain
their source origin and exact arrangement end. Schema v4 rejects prior schema
versions before writes. Current Logic playback evidence remains pending; Q02
owns its matrix, including a real import check of this changed track count and
role-file omission. The complementary two-bar fixture accepts an introductory
Bass rest and enters at tick 1920 (bar 2), ending at tick 3840. Re-import compares
accepted notes in both `bass.mid` and `complete-song.mid`, checks every emitted
file's PPQ/end, and preserves source bytes. Ready-to-import packages are retained
at `~/.codex/melotrail-terra/m07-repair-evidence/logic-packages-verified/`:
`all-song-bass-rest` and `intro-bass-rest`. Import at song origin in Logic, verify
Bass is absent for the first case and silent for bar 1 in the second, and check
the exact final boundary. These checks are prepared, not human-passed. Companion
native validation also accepts current manifest v2 and preserves rejected v1
bytes; no project migration or soundtrack change is performed.

U04b automated checks cover section identity operations, explicit purpose/phrase
review and confirmation, invalid phrase entry, concurrent section/plan saves,
and source/candidate/accepted-rest preservation after section removal and reopen.
Section/purpose controls were inspected at 1536×1024, 1280×900 and 720×900.
Human visual/Logic gates remain pending.

## Retained historical Logic evidence

**2026-08-28: PASS**, user report for Logic Pro **12.3.1** on macOS **26.6.2
(25G83)**; export fixtures prepared from `adfe25a` (old MC-047) plus the matrix
preparation test. All six complete and per-role packages imported without error;
full playback and save/close/reopen passed. 120 BPM/4/4 and named role separation
are visible in the retained captures. Imported marker display is unassessed;
software-instrument output-channel UI was not independently captured.

This is historical compatibility evidence, not acceptance of future timing/rest
changes. The original package directories were ignored build output. Manifest
hashes below preserve the old record even if those packages are no longer local.

| Package directory | Source fixture (SMF) | Expected complete/role end tick | Manifest SHA-256 |
| --- | --- | ---: | --- |
| `smf0-melody` | `smf0-melody.mid` (0) | 480 | `140306ea5ab542406e9445cbc3db57cfb5142ad22ba1d4d3bb4993d0fb1a44ca` |
| `smf1-reference-tracks` | `smf1-reference-tracks.mid` (1) | 480 | `a9bc0bc7648062e2557f5c6206f8d8cbef592a9c31e3b88f5458474813eb278d` |
| `pickup-timing` | `pickup-timing.mid` (1) | 960 | `1bf0ab41456600a1f205ca1ba362aeb162647d3c673cc3b07e5769e8fb359740` |
| `sub-bar-harmony` | `sub-bar-harmony.mid` (1) | 1920 | `6d31ac90f677f85ad56c66b28c61c9697d53ebdaf19ebc11918267195caa001c` |
| `expressive-controller-pitch` | `expressive-controller-pitch.mid` (1) | 480 | `bec84753b254a6ab8480b6ad50116ce8515fcddba642087c6b096c97289f0d8f` |
| `complete-arrangement-boundary` | `final-boundary-note.mid` (0) | 1920 | `fc35d0c091ca5ecf060592e5e167cd7a02a6d978d41876ea81e6ece6a1c7e725` |


| Fixture | Screenshot | SHA-256 |
| --- | --- | --- |
| `smf0-melody` | [smf0-melody.png](checks/smf0-melody.png) | `8d3b92b762098825a5a4acd5c9bc21e13b4e202322ab8cabb5e66bf4cc7011bd` |
| `smf1-reference-tracks` | [smf1-reference-tracks.png](checks/smf1-reference-tracks.png) | `00bde2e596db606e37c0317155552e147c883c7ebbb33d9032d9484062d8b266` |
| `pickup-timing` | [pickup-timing.png](checks/pickup-timing.png) | `81b2232f0eccaca9a9475f7658ebb26f37237811a687affe55cf59f789805ef2` |
| `sub-bar-harmony` | [sub-bar-harmony.png](checks/sub-bar-harmony.png) | `8913576ef89b14a46396f49df3275a64afebadfb42db0eb21d34ab27f3a3db41` |
| `expressive-controller-pitch` | [expressive-controller-pitch.png](checks/expressive-controller-pitch.png) | `b8d9b703c9670f63f180a8616b7e9173c4961efbace1905310f8e64624faa384` |
| `complete-arrangement-boundary` | [complete-arrangement-boundary.png](checks/complete-arrangement-boundary.png) | `0bb012b3957ddfd6031169aac773f08b57bc140297b352b443d68355f23168b8` |


The historical package settings were PPQ 480, 120 BPM, 4/4, C major, marker
`1:Verse` at tick 0, complete track order Conductor/Melody/Chords/Bass/Drums and
channels 1/2/3/10. Very short historical fixtures are compatibility probes, not
current full-song musical acceptance cases.

## Current planning handoff and release record

2026-09-06: baseline `make test` passed with fresh root/desktop execution before
document consolidation. Final consolidation checks: `make test`, `make build`
and `git diff --check` PASS. Reports contain 782 root tests with no failures and
261 desktop tests with no failures and 10 existing skips; desktop execution was
cached in the final pass after its fresh baseline run. All 33 task dependencies
resolve without cycles; original UI/TABI/train and Logic images are byte-preserved.
Documentation changes now invalidate Gradle test caching. No generator/UI runtime
change, new Logic run, controlled listening result or video generation is claimed.

Release record remains **pending**: final commit/build; clean test/build/install;
measured repository reduction; final music set and ratings; six-page visual
approval; current Logic matrix; exact remaining limitations; reviewer/date.
Keep one concise record here rather than restoring the old task diaries.

### F06 cleanup measurement (2026-09-07)

F06 inspected only repository-owned candidates. `<repository-root>/sounds`
resolved as a regular, non-symlink directory containing two tracked obsolete
sound-library catalog files (253,314 bytes); its `sounds/` ignore rules were
stale. The tracked, non-ignored root `Piano Song n.17.mp4` resolved as a regular,
non-symlink, unreferenced legacy media file (5,001,765 bytes). Both targets were
removed: 3 files / 5,255,079 tracked bytes (5.01 MiB).

`<repository-root>/.kotlin` resolved as a regular, non-symlink directory with
nine tracked, non-ignored compiler error-cache logs (49,613 bytes). The configured
F06 coordinator path policy does not authorize `.kotlin/`, so those files were
inspected but retained and are not counted as cleanup. `data/audio` and
`.venv-worker` were absent at inspection, so no untracked sound-library,
audio-project, or virtual-environment data was claimed as deleted. The obsolete
audio, data, generic-cache, render, and sound-library ignore entries were
removed.

The F06 test, evidence, and ignore changes add 4,815 bytes, reducing tracked
worktree payload from 32,610,480 to 27,360,216 bytes (16.1%).

Against PLAN's 2026-09-06 tracked-source baseline, the current tree has 56
production Kotlin files / 20,415 lines versus 236 / 73,677 (180 files and
53,262 lines removed: 76.3% / 72.3%). It has 57 test Kotlin files / 12,855
lines versus 187 / 34,912, and zero Python files / lines versus 33 / 4,782.
The production-line reduction exceeds the 40% investigation target. The removed
legacy data has no production consumer; guards preserve supplied UI/TABI/train
references, Logic captures, and owned MIDI fixtures. F06 does not establish a
clean-install/native-startup result, a human listening decision, or a new Logic
run.

## Recovery notes

For toolchain failure, inspect the JDK requested by Gradle and the actual launcher;
do not restore worker setup. For rejected MIDI, follow the exact track/map/pairing
finding and preserve the original. For silent audition, choose the built-in
synth or an explicitly configured MIDI receiver in the persistent player's
options. Device failure must not mutate musical data.

For stale candidates, inspect the changed authority/dependency and regenerate
only affected scopes. For failed export, check accepted readiness, digests,
writable/new destination and semantic validation. Never rename an old candidate
to make it current or overwrite a snapshot. For bad musical fit, report bars,
melody+piano/full-song comparison, candidate IDs and audible reason rather than
only “in tempo”. Unsupported old audio projects are intentionally not migrated.

U04 recovery verifies the shared section/chord selection, unsaved impact and
bar-total recovery. Native Compose captures at 1536×1024, 1280×900 and 720×900
show first-viewport name editing and Save, with wrapped section actions reachable
by scroll and keyboard. Selecting an unsaved new occurrence shows save guidance
instead of another occurrence’s chord editor. The focused workflow and 411-test
suite pass; reference 03 and actual captures were inspected. Evidence:
`~/.codex/melotrail-terra/u04-repair-evidence/visual`. This is technical UI evidence,
not final visual approval or a new Logic playback decision.

### U07a visual regressions

U07a pins 66 full-page PNGs: all six production destinations at 1536×1024,
1280×900 and 720×900 in empty, partially accepted/stale and fully accepted states;
Arrange progress/retry plus scrolled ready Review decisions and Export results.
Three small control/panel goldens cover focus, primary fill, borders and corners.
These are technical regression baselines; U07 owns human visual approval.

The synthetic presentation fixtures hydrate imported MIDI, melody and authority
consistently. The separate ready fixture has five current accepted candidates,
one confirmed Bass rest, guarded batch Undo and a snapshot matching the accepted
selection. Tests require enabled Play/Undo/Publish/Reveal, accepted identity,
all snapshot files and visible scrolled decision/action bounds. Original stale
fixtures remain separate. Real musical-service behavior remains covered by the
focused workflow suite; synthetic fixtures do not establish Logic approval.

The renderer uses macOS 26.6.2/aarch64, Compose UI 1.11.0/Skiko 0.144.6, software
raster, density/font scale 1, fixed frame times/dates/seeds and stopped playback.
System Arial regular/bold files are read in place, never redistributed.
`visual/renderer.properties` pins OS, font, renderer-class and native digests.
An environment change fails explicitly rather than skipping or updating goldens.

`VisualImageComparator` compares every ARGB pixel with zero tolerance and no
masking, resizing or registration. Tests write actual/expected/diff PNGs and a
pixel-count report under `desktopApp/build/test-results/visual-shell`,
`visual-primary`, `visual-panel` and `visual-comparator`. Missing baselines fail
and retain actual captures. Tests never create or replace expected resources.
Baseline changes require explicit image inspection and independent checks.

Negative controls reject shifted panels, wrong primary fill, pill corners,
erased drum lanes, single RGB/alpha changes and dimension changes. Independent
checks cover shell/inspector geometry, four aligned 52 dp lanes, role colors,
one player, 48 dp navigation/transport targets, contrast and keyboard focus/
activation. The ready fixture's scrolled captures use un-clipped target positions
so an offscreen Export result is brought into view before its bounds are checked.

Recovery evidence: `~/.codex/melotrail-terra/u07a-repair-evidence`.
The initial new-baseline run deliberately failed for all 24 absent ready images;
state and geometry assertions passed before those images were explicitly
inspected and added. Existing baselines were retained. Focused/full test/build
results and technical image review are recorded with the final repair there.
U07b owns native-density, large-song and responsiveness measurements; U07 retains
the actual human visual decision.
