# Project analysis: why four valid parts still produce a bad song

Analysis baseline: 2026-08-24

Scope: the current schema-v4 Kotlin/worker composition path, with emphasis on
the intro/verse/chorus/bridge end-to-end failure and
[`EnsembleCohesion.kt`](../../src/main/kotlin/app/melotrail/arrangement/EnsembleCohesion.kt).
This is the diagnostic basis for [`TASKS.md`](TASKS.md), not a second roadmap.

## Executive diagnosis

The project has strong artifact safety, typed authority, validation, and
workflow evidence, but those controls mostly answer “is this artifact current
and structurally valid?” They do not yet prove “are these four performances on
one beat grid, in one mode, harmonically compatible, monophonic, and used as the
same melody throughout the build?”

The poor result is therefore cumulative rather than one isolated Cohesion bug:

1. each source keeps most of its performed timing while project tempo metadata
   is replaced;
2. source durations, rather than canonical bar windows, determine later section
   offsets;
3. tonic-only transposition leaves mode differences unresolved;
4. scale/chord problems and polyphony are inspected more than repaired;
5. the assembled/connected canonical full melody is approved, but downstream
   stages still reconstruct piano from occurrence-local selected MIDI;
6. generic arrangement choices magnify those source inconsistencies;
7. Cohesion adds locally valid events using global role evidence and several
   intent fields that do not reach the renderer;
8. critics can be truncated, overridden, bypassed, or treated as current
   evidence without proving that critical composition defects are gone.

Audio-quality work cannot repair that chain. Mix, DSP, mastering, and better
samples will make the same musical conflicts more audible.

## What is already worth preserving

- Project setup, structure, key/mode, tempo/meter, and harmony are typed and can
  remain the musical authority.
- Source and derived artifacts are project-confined, hash-bound, and generally
  published without overwriting their inputs.
- Stage invalidation, approval evidence, deterministic planners/processors, and
  strict Qwen schemas provide useful control points.
- `SourceSong`, Melody Connection, Source Song Critic, generated-role
  validation, Full-Song Critic, seeded Humanization, rendering, production,
  and provenance are viable boundaries to strengthen rather than replace.
- Cohesion already runs after arrangement, matching the project invariant; its
  scope and inputs need correction, not relocation to an earlier stage.

## Root-cause matrix

| Area | Current implementation evidence | Musical consequence | Owning tasks |
| --- | --- | --- | --- |
| Beat and tempo | [`MidiNormalizer`](../../src/main/kotlin/app/melotrail/arrangement/MidiNormalization.kt) converts PPQ, optionally snaps events inside a small tolerance, and replaces tempo/meter meta events. It does not estimate beat/downbeat mapping or time-warp the performance. | A source performed at a different tempo or with a pickup remains early/late against the project pulse even though its file declares the project BPM. | QP-001–QP-003 |
| Occurrence bounds | [`MusicalAuthorityBuilder`](../../src/main/kotlin/app/melotrail/application/CanonicalMusicalAuthority.kt) takes analyzed `durationTicks`, rounds only the bar count upward, but advances the next `startTick` by the unrounded duration. | Bar labels and tick boundaries can disagree; every fractional-bar source can shift the following occurrence and its harmony phase. | QP-003 |
| Key/mode | [`MidiProjectKeyTransposer`](../../src/main/kotlin/app/melotrail/arrangement/MidiTransposition.kt) maps recognized source scale degrees to the corresponding project-mode degree when modes differ, preserves same-mode chromatic tonic movement, and reports unresolved chromatic fallback notes. Its versioned processor identity invalidates tonic-only stage cache entries. | A chromatic source note remains explicit evidence until the QP-006 occurrence-local fit authorizes it as an active chord tone or safely repairs/blocks it. | QP-004 complete; QP-006 complete |
| Harmony fitting | [`MidiHarmonyFitter`](../../src/main/kotlin/app/melotrail/arrangement/MidiHarmonyFitting.kt) consumes the QP-005 controller-materialized candidate and one authoritative occurrence timeline. It permits only short weak scale passing tones, active chord tones (including user-authored chromatic harmony), or evidenced ties/suspensions; it publishes hash-bound note/boundary evidence or blocks. | QP-010 rereads exact QP-006 eligibility and boundary evidence for each assembled note; connection-only notes must meet the same bounded exposed-tone policy. | QP-006 complete; QP-010 complete |
| Melody monophony | [`MidiMonophonicMelodyPreparer`](../../src/main/kotlin/app/melotrail/arrangement/MidiMonophonicMelodyPreparation.kt) materializes per-channel sustain/all-notes state, reports every source note/controller decision, and publishes one note-bearing source candidate. QP-006 derives its protected-anchor evidence only after the fitted output passes eligibility, then [`SourceSongAssembler`](../../src/main/kotlin/app/melotrail/arrangement/SourceSong.kt) writes one controller-free, globally monophonic full-melody track. | The source-song candidate now enforces cross-occurrence monophony and persists post-fit lineage/anchors; QP-008 must stop later stages rebuilding it from per-part MIDI. | QP-005–QP-007 complete; QP-008 |
| Canonical full melody | `SourceSongAssembler` publishes an immutable v2 conductor plus one full-melody track, section markers, occurrence/harmony/window/lineage/hash sidecar evidence, and one `FullSongGrooveMap`; source approval resolves the exact connected MIDI plus source/connection/report/approval sidecars. [`OccurrenceMidiArtifactResolver`](../../src/main/kotlin/app/melotrail/arrangement/OccurrenceMidiArtifactResolver.kt) clips only those sidecar windows. | Arrangement, Cohesion, criticism, humanization, preview, rendering, and release all bind to the approved connected hash; stale approval blocks instead of falling back to selected MIDI. | QP-007–QP-008 complete |
| Selection chain | [`SelectedMidiArtifactResolver`](../../src/main/kotlin/app/melotrail/arrangement/SelectedMidiArtifactResolver.kt) is the sole resolver for transposed -> corrected -> AI Fix -> Enhance -> Feel; Feel references content-addressed input/output/report/context/processor evidence and `NO_OP` returns the exact upstream hash. | A changed selected branch stales that branch and every downstream artifact; stale Feel blocks regeneration rather than allowing a competing stage-run choice. | QP-009 complete |
| Source approval | [`DefaultSourceSongCriticApplicationService`](../../src/main/kotlin/app/melotrail/application/SourceSongCriticApplicationService.kt) persists complete severity counts and exact source/connection/report hashes. It rejects hard window/lineage/monophony/key/anchor/tail failures and blocks unresolved source-groove discontinuities; only ordinary blockers can be recorded as `PRIVATE_AUDITION`. | Private-audition approvals remain explicitly experimental and `requireQualityCertifiedApproved` rejects them. A changed authority or canonical melody resolves a new report/approval path. | QP-010 complete |
| Arrangement intent | [`GlobalSongPlanner`](../../src/main/kotlin/app/melotrail/arrangement/GlobalSongPlanner.kt), [`SectionVariation`](../../src/main/kotlin/app/melotrail/arrangement/SectionVariation.kt), and [`DetailedArrangement`](../../src/main/kotlin/app/melotrail/arrangement/DetailedArrangement.kt) now bind a reviewed full-song groove-map digest plus profile/mood, section purpose, energy, density, register, articulation, bounded role groove policy, and pad/string continuity intent. Qwen schema examples are explicitly non-executable; flat copied energy/default output is rejected. | QP-012 must still apply those planning limits to generated MIDI, including active-span timing and actual cross-section voice-leading validation. | QP-011 complete; QP-012 |
| Generated roles | Current validators cover important integrity/range/harmony cases, but the ensemble admission path does not yet prove all roles share the canonical beat phase, avoid masking, and see every previously accepted role. | Individually plausible bass/drums/pad/strings can conflict as an ensemble or obscure the melody. | QP-012 |
| Whole-song critic | [`DeterministicFullSongCritic`](../../src/main/kotlin/app/melotrail/arrangement/FullSongCritic.kt) truncates issues before calculating aggregate counts. [`BuildApplicationService`](../../src/main/kotlin/app/melotrail/application/BuildApplicationService.kt) requires a current report and a resolved Enhance selection, but does not require zero blockers/critical issues; `BYPASS` and `NO_OP` are accepted. | A build can be technically successful while the stored summary understates problems or the composition remains musically blocked. | QP-014 |
| Listening evidence | Existing automated tests strongly cover files, hashes, schemas, invalidation, deterministic output, and error paths. Renderer-backed, loudness-matched musical A/B acceptance is not an ordinary hard gate. | Green tests do not refute the reported out-of-sync timing or terrible composition/vibe. | QP-001, QP-017 |

## Five additional operational-risk findings

The five reported risks are valid concerns, but two are partially implemented
already. The plan must close the actual gaps instead of duplicating working DSP
or replacing calibrated behavior with unverified constants.

### RISK-01 — Pedal-extended piano tails contaminate the next chord

[`midi_clean.py`](../../worker/commands/midi_clean.py) removes redundant CC64
state changes, but it does not materialize the effective sounding interval of a
note held by sustain. The Kotlin pairing and harmony checks likewise reason
primarily from written note-on/note-off events. A note can therefore sound into
the next chord even when its written pair appears to end safely.

Required correction: QP-005/QP-006 must interpret sustain controllers before
monophony and harmony analysis, then enforce a versioned boundary-release
policy at every authoritative chord and occurrence boundary. Incompatible
carried notes are released before the boundary; explicitly valid common-tone
ties or suspensions may survive only with typed evidence. Any inter-note gap is
derived from tempo/PPQ and calibrated fixtures, with 50 ms treated as a
candidate upper reference rather than a hard-coded subtraction.

### RISK-02 — Kick and bass have no interaction-aware low-end policy

[`ProductionStemMixer`](../../src/main/kotlin/app/melotrail/arrangement/ProductionStemMixer.kt)
can apply filters, EQ, and compression, but `MixPlan.defaults()` does not assign
complementary low-end processing and there is no kick-triggered bass sidechain.
The current low-end critic compares broad low-passed RMS values and emits a
warning; it does not locate coincident kick/bass energy or prove that corrective
processing improved it.

Required correction: QP-016 must add a typed low-end interaction plan using
approved drum MIDI as deterministic kick-trigger evidence, bounded bass ducking
inside a 2–4 dB policy with a 3 dB starting reference, recovery/pumping checks,
latency compensation, and calibrated sub-bass/spectral allocation. A 40 Hz bass
high-pass and 80 Hz kick cut are
starting hypotheses, not unconditional defaults: the selected instrument and
measured stem energy must justify the final profile.

### RISK-03 — Pad/string voice-leading state resets between sections

[`PadMidiGeneration`](../../src/main/kotlin/app/melotrail/arrangement/PadMidiGeneration.kt)
already selects the nearest inversion relative to the previous chord within a
generation request. QP-012 carries each role's final actual voicing into the
next request, scores ordered assignments with versioned total movement,
common-tone, and entry/exit evidence, and rejects an unplanned cross-section
octave jump. A declared register change may still enter or release voices.

Remaining correction: QP-013 may smooth a boundary only within these accepted
role/harmony constraints; it must not use a generic transition to hide a
generator reset.

### RISK-04 — Grid-locked accompaniment does not inherit source feel

[`DrumMidiGeneration`](../../src/main/kotlin/app/melotrail/arrangement/DrumMidiGeneration.kt)
used exact grid positions plus a fixed/configured swing value. QP-012 now
supplies the approved `FullSongGrooveMap` to bass and drum generation, replacing
that independent offset when source-feel evidence is available. Validation
requires the active map span, measures role phase against it, and rejects a
near-simultaneous piano flam. [`SeededHumanization`](../../src/main/kotlin/app/melotrail/arrangement/SeededHumanization.kt)
remains a later bounded stage and cannot make generated-role admission pass.

Required correction: QP-002/QP-003 must extract a confidence-scored
`SourceGrooveTemplate` after excluding pickups, tempo drift, and outliers. The
canonical grid remains authoritative; each template stores bounded deviations
by beat/subdivision. QP-007 assembles them into one occurrence-indexed,
reviewable `FullSongGrooveMap`; QP-012 applies its active span with role-specific
limits to bass and drums, rather than copying raw piano error or adding unrelated
jitter. QP-014 and QP-017 still need to prove phase coherence and include a
listening A/B.

### RISK-05 — Delivery true peak must be proven, not assumed

[`mastering.py`](../../worker/commands/mastering.py) already performs gated
loudness measurement, limiting, and four-times-oversampled true-peak
measurement. [`BuildApplicationService`](../../src/main/kotlin/app/melotrail/application/BuildApplicationService.kt)
also validates the selected mastering
profile, including ceiling and limiter-reduction constraints. Replacing this
with a simple `pyloudnorm` normalization call would regress the existing
design and would not itself create a true-peak limiter.

Required correction: QP-016 must bind those measurements to the exact selected
master and add representative local AAC/MP3 encode-decode previews followed by
true-peak remeasurement. If a preview exceeds the internal ceiling, the
pre-encode policy is adjusted or the release is blocked for review. This is a
regression proxy, not a simulation or guarantee of YouTube's transcoder.
The `-14 LUFS` and `-1 dBTP` values remain versioned Melotrail reference policy,
not official YouTube mandates.

| Risk | Primary tasks | Release proof |
| --- | --- | --- |
| Pedal/sustain tail collision | QP-005, QP-006, QP-010 | QP-017 |
| Kick/bass low-end overlap | QP-012, QP-016 | QP-017 |
| Cross-section pad/string jumps | QP-011–QP-013 | QP-017 |
| Shared source micro-timing | QP-002, QP-003, QP-007, QP-011, QP-012, QP-014 | QP-017 |
| Selected-master/lossy true peak | QP-016 | QP-017, QP-018 |

## Detailed Ensemble Cohesion analysis and QP-013 closure

`EnsembleCohesion.kt` is not the right place to repair source tempo, mode,
general harmony, or polyphony. Those defects must be gone before arrangement.
The class does, however, contain several issues that can make an already weak
arrangement worse.

### 1. Canonical melody authority is retained

QP-008 already cut Cohesion over to occurrence views clipped from the approved
connected full melody. `EnsembleTransitionContextFactory` binds each context to
that immutable hash and QP-013 continues to publish only derived role/bridge
MIDI. A missing or stale approval blocks Cohesion rather than rebuilding timing
from individual selected parts.

### 2. Boundary roles are now local

`TransitionBoundaryRoleEvidence` now persists sorted outgoing-active,
incoming-active, entering, exiting, continuing, and supported generated roles
for every saved adjacent pair. The validator accepts a bridge instrument only
when it is locally supported; a role inactive on both sides is rejected. Model
evidence carries the same local facts, so compatibility selection cannot widen
to an unrelated whole-song instrument.

### 3. `CONTINUITY` is explicit and non-destructive

The planner selects `CONTINUITY` only from a continuing local role and emits a
`NO_OP` placement with a zero-length note window. The renderer writes valid MIDI
metadata but no note pairs, and both legacy transition adapters expose no bridge
element for it. There is no `CONTINUITY -> DRUM_FILL` fallback.

### 4. Persisted intent is executable

The ambiguous `bars` field was removed from the Cohesion plan. The renderer uses
the bounded overlay window (`leadBeats`/`tailBeats`) and directly executes
rhythmic gesture, harmonic handoff, and energy contour. Audits retain the exact
rendered bridge-note evidence beside the reviewed plan.

### 5. Bridge material is constrained and improvement-gated

Bass bridges use only the active outgoing/incoming canonical roots rather than
chromatic interpolation; pitched bridges use their active canonical harmony.
Drum bridge hits are selected from the approved global groove-map points inside
the active boundary span. Sustained layers reuse an actual carried voicing when
available, and same-pitch overlays duck/split the existing note rather than
stacking duplicate attacks. The application runs the deterministic full-song
critic against both baseline and draft Cohesion role sets before approval and
rejects an increase in blocker or critical counts. Stage comparisons persist
actual before/after note changes plus complete critic aggregate/category deltas.

## Why “one full melody before arrangement” is the correct design

The proposed canonical artifact resolves several independent failure modes at
once:

- one global tick grid prevents per-section duration concatenation drift;
- one note-bearing track makes monophony measurable across tracks and channels;
- occurrence windows retain intro/verse/chorus/bridge/outro identity without
  splitting the musical truth back into independent files;
- one sidecar can bind every note to source, preparation decision, active chord,
  section occurrence, protected anchor, and approved hash;
- downstream consumers can take read-only occurrence views without rebuilding
  the song;
- a critic/listener can compare the exact melody that will be arranged and
  released.

The source parts remain immutable lineage inputs. “Aggregate” means publish a
new content-addressed candidate and structured sidecar, never concatenate into
or overwrite an imported source file.

## Correct responsibility boundaries

| Stage | Must own | Must not own |
| --- | --- | --- |
| Source preparation | beat/downbeat mapping, project-grid conformance, mode-aware transposition, monophony, scale/harmony fit, bounded evidence | arrangement roles, transitions, mix character |
| Full melody assembly/connection | global timeline, occurrence windows, protected anchors, boundary-local melody edits | accompaniment generation, project-harmony replacement |
| Arrangement | section purpose, role activity, density, register, groove, voicing, contrast | source timing repair or arbitrary melody replacement |
| Cohesion | actual adjacent-role handoff, bounded bridge/fill/tie/no-op behavior | whole-song rewrite, source key/harmony/polyphony repair |
| Critic/polish | complete deterministic findings and targeted non-worsening correction | silent bypass or unrestricted AI rewrite |
| Production/release | render/mix/master validation, listening, provenance, policy evidence | claiming rights clearance or guaranteed monetization |

## Delivery conclusion

The implementation order in [`TASKS.md`](TASKS.md) follows dependency order,
not UI order: establish measurable evidence, fix time, fix pitch/monophony,
publish and adopt one canonical melody, then improve arrangement and Cohesion.
Starting with Cohesion or mastering would optimize artifacts whose musical
authority is still wrong.

Release remains withheld until [`QUALITY_GATES.md`](QUALITY_GATES.md) and the
renderer-backed/listening evidence in QP-017 pass. YouTube readiness then adds
rights, originality, anti-template, disclosure, and human channel/content
review; it is never inferred from a technically successful build.
