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
5. the assembled/connected song is approved, but downstream stages reconstruct
   piano from occurrence-local selected MIDI;
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
| Key/mode | [`MidiProjectKeyTransposer`](../../src/main/kotlin/app/melotrail/arrangement/MidiTransposition.kt) computes one signed tonic interval and applies it to every non-drum note. Scale-fit values are report evidence only. | G major to C natural minor, for example, moves the tonic but does not map major scale degrees to C minor; E/A/B-type degrees can remain major-mode clashes. | QP-004 |
| Harmony fitting | [`SourceSongCritic`](../../src/main/kotlin/app/melotrail/arrangement/SourceSongCritic.kt) accepts a note when it is either in the active chord or anywhere in the project scale. It reports other notes but does not publish a repaired melody. | Exposed scale tones can lean against the chord indefinitely, while invalid notes survive until an override or later stage. Section-specific harmony is evidence, not yet an enforced prepared-melody contract. | QP-006, QP-010 |
| Melody monophony | [`SourceSongAssembler`](../../src/main/kotlin/app/melotrail/arrangement/SourceSong.kt) copies every source track into a new occurrence-local track. The critic pairs notes but has no global simultaneous-note invariant. | Transcription chords, octave doubles, overlapping voices, and note-bearing auxiliary tracks remain in what users perceive as the melody. | QP-005, QP-007 |
| Canonical full melody | `SourceSongAssembler` does create one full-timeline file and sidecar, and Melody Connection publishes an approved connected candidate. However [`OccurrenceMidiArtifactResolver`](../../src/main/kotlin/app/melotrail/arrangement/OccurrenceMidiArtifactResolver.kt) falls back to per-part selected MIDI. | The melody that was connected, criticized, and approved is not guaranteed to be the piano melody later arranged, humanized, rendered, and exported. | QP-007–QP-008 |
| Selection chain | [`SelectedMidiArtifactResolver`](../../src/main/kotlin/app/melotrail/arrangement/SelectedMidiArtifactResolver.kt) selects approved Enhance before evaluating the selected Feel branch; its stage-run `ENHANCED` compatibility mapping is also labeled as `LOFI_FEEL`. | Enhance + Feel is not a composed chain. A user can review one branch while another reaches analysis/assembly. | QP-009 |
| Source approval | [`DefaultSourceSongCriticApplicationService`](../../src/main/kotlin/app/melotrail/application/SourceSongCriticApplicationService.kt) permits every current blocking issue to be explicitly overridden and arrangement only requires matching approval evidence. | This is useful for private audition, but the same state is not separated from a quality-certified path. | QP-010 |
| Arrangement intent | [`LocalQwenArrangementPlanner`](../../src/main/kotlin/app/melotrail/arrangement/LocalQwenArrangementPlanner.kt) sends bounded metadata and validates structure, but its prompt contains a concrete constant JSON arrangement example. Deterministic detail derives several controls mechanically; for example drum swing is fixed to zero. | Safe schema output can still be generic, flat, or template-like across intro/verse/chorus/bridge. Structural validity is stronger than musical direction. | QP-011 |
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
generation request. The adapter creates independent requests per section, so
that state is reset at intro/verse/chorus/bridge boundaries. String generation
has the same request-local previous-voicing limitation.

Required correction: QP-011/QP-012 must carry accepted global voicing state
across occurrence requests and validate voice identity, range, common tones,
and total semitone motion at every boundary. QP-013 may smooth a remaining
boundary only within those accepted role/harmony constraints; it must not use a
generic transition to hide a generator reset.

### RISK-04 — Grid-locked accompaniment does not inherit source feel

[`DrumMidiGeneration`](../../src/main/kotlin/app/melotrail/arrangement/DrumMidiGeneration.kt)
uses exact grid positions plus a fixed/configured swing value; deterministic
detail currently selects zero swing. [`SeededHumanization`](../../src/main/kotlin/app/melotrail/arrangement/SeededHumanization.kt)
applies bounded independent variation, but no stage derives and shares a robust
micro-timing deviation vector from the approved piano performance. This can
produce audible flams even when all roles share the same nominal BPM and bar
grid.

Required correction: QP-002/QP-003 must extract a confidence-scored
`SourceGrooveTemplate` after excluding pickups, tempo drift, and outliers. The
canonical grid remains authoritative; each template stores bounded deviations
by beat/subdivision. QP-007 assembles them into one occurrence-indexed
`FullSongGrooveMap`, and QP-011/QP-012 apply its active span with role-specific
limits to piano, bass, and drums, rather than copying every raw piano error or
adding unrelated jitter. QP-014 and QP-017 prove phase coherence and include a
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

## Detailed Ensemble Cohesion analysis

`EnsembleCohesion.kt` is not the right place to repair source tempo, mode,
general harmony, or polyphony. Those defects must be gone before arrangement.
The class does, however, contain several issues that can make an already weak
arrangement worse.

### 1. It reads the wrong melody authority

`EnsembleTransitionContextFactory` resolves every section through
`SelectedMidiArtifactResolver`, then builds boundary melody evidence from that
part file. It does not read the approved connected full-melody artifact. The
same per-part reconstruction continues through `OccurrenceMidiArtifactResolver`
and Humanization.

Effect: connection edits and the exact approved song timeline can disappear
after approval, while every hash check still passes against a different but
internally current artifact.

Required correction: QP-008 must cut all of these callers over to occurrence
views clipped from one approved canonical full melody. Cohesion should fail if
that hash/approval is missing or stale.

### 2. Supported instruments are global, not boundary-local

The context factory gathers every generated instrument used anywhere in the
arrangement into one sorted `supportedInstruments` list. Every boundary then
receives the same list. The validator only checks that a chosen bridge
instrument appears in that global list.

Effect: a bridge can introduce drums, bass, pad, or strings at a boundary where
the role is active on neither side. This creates arbitrary handoffs rather than
continuity between the actual outgoing and incoming ensemble.

Required correction: QP-013 must persist active, exiting, entering, and allowed
roles per boundary and validate the action against those local sets.

### 3. `CONTINUITY` becomes a drum fill

The planner maps `CONTINUITY` to the first globally supported role in the order
drums, bass, pad, strings. The deterministic pattern map then maps
`BridgeType.CONTINUITY` to `DRUM_FILL`. The separate transition adapters in
`MidiTransitionEngine.kt` and `DetailedArrangement.kt` also translate
`CONTINUITY` into `BridgeElement.DRUM_FILL`.

Effect: asking to preserve continuity can add a fill, commonly on drums, even
when the musically correct behavior is a held common tone, a continued bass
figure, or no new note.

Required correction: continuity must sustain/tie a role that is already active
at that boundary, or publish an explicit no-op. It must never have a fixed drum
fallback.

### 4. Persisted intent and rendered MIDI diverge

The plan records `bars`, `harmonicHandoff`, `rhythmicGesture`, and
`energyContour`. The deterministic bridge renderer uses energy and lead/tail
beats, plus a pattern selected from `bridgeType`; it does not execute the
declared harmonic-handoff or rhythmic-gesture field. `bars` does not directly
own the rendered overlay duration when explicit lead/tail beats are present.

Effect: reviewable JSON can claim a step-to-incoming pickup or sustained
handoff while the MIDI renderer produces a generic pattern with different
musical behavior.

Required correction: QP-013 must either make every declared field executable
and test its MIDI effect or remove that field from the plan contract.

### 5. Harmonic validation is incomplete for bridge material

Bass-walk intermediate pitch classes are calculated by chromatic interpolation
between roots. Chord/pad patterns are derived from boundary harmony, but the
merged result is not admitted through one complete post-merge validator that
proves beat phase, active harmony, masking, density, range, and melody identity
together.

Effect: a transition can be locally well-formed MIDI while adding chromatic
bass motion or density that clashes with the active harmonic span and melody.

Required correction: bridge generation must use the canonical harmonic
timeline and the same ensemble validator as ordinary generated roles; approval
must require non-worsening critic metrics.

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
