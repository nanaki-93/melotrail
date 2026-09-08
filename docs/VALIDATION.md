# Validation and evidence

Owner: automated checks, real listening/visual acceptance and Logic Pro evidence.
Implementation status is in [TASKS](../TASKS.md). The old participant/holdout
queues are superseded; their incomplete gates are not passes.

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
